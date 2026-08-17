package com.example.engine

import android.content.Context
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.example.data.AutoTaskDatabase
import com.example.data.AutoTaskRepository
import com.example.data.ExecutionLog
import com.example.data.RoomRunStore
import com.example.data.RoomScheduleStore
import com.example.domain.EventEnvelope
import com.example.domain.GeoPoint
import com.example.domain.RunSnapshot
import com.example.domain.RunStatuses
import com.example.domain.ScheduleFire
import com.example.domain.ScheduleRegistration
import com.example.service.AndroidScheduleDriver
import com.example.service.WorkManagerWakeScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AutoTaskEngine private constructor(
    private val context: Context
) {
    val repository = AutoTaskRepository(context.applicationContext)
    val executor = ActionExecutor(context.applicationContext, repository)
    val runStore: RunStore = RoomRunStore(AutoTaskDatabase.getInstance(context))
    val coordinator = RunCoordinator(
        store = runStore,
        runner = StepRunner { profile, event, stepIndex, type, params ->
            executor.executeStep(stepIndex, type, params, profile, event)
        },
        wakeScheduler = WorkManagerWakeScheduler(context.applicationContext),
        onTerminal = { run, _, profile, _ ->
            if (run.status == RunStatuses.SUCCESS ||
                run.status == RunStatuses.PARTIAL ||
                run.status == RunStatuses.FAILED
            ) {
                repository.profileDao.updateLastTriggeredAt(profile.id, run.finishedAt ?: run.updatedAt)
            }
        },
        loadProfile = { repository.getProfileById(it) }
    )
    val dispatcher = EventDispatcher(
        store = runStore,
        coordinator = coordinator,
        loadEnabled = { repository.profileDao.getEnabledProfilesForTrigger(it) },
        loadProfile = { repository.getProfileById(it) },
        deviceState = { collectCurrentDeviceState() },
        insertLog = { repository.insertLog(it) }
    )
    val scheduleManager = ScheduleManager(
        store = RoomScheduleStore(AutoTaskDatabase.getInstance(context)),
        driver = AndroidScheduleDriver(context.applicationContext),
        loadProfiles = { repository.profileDao.getAllProfiles() },
        location = { lastKnownLocation() },
        onFire = { fire -> ingestScheduleFire(fire) }
    )
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val startTimeMs = System.currentTimeMillis()
    var isRunning = true
        private set

    companion object {
        @Volatile
        private var INSTANCE: AutoTaskEngine? = null

        fun getInstance(context: Context): AutoTaskEngine {
            return INSTANCE ?: synchronized(this) {
                val instance = AutoTaskEngine(context)
                INSTANCE = instance
                instance
            }
        }
    }

    init {
        repository.onProfileMutated = { profileId -> syncSchedule(profileId) }
        scope.launch {
            repository.seedDefaultRecipesIfNeeded()
            coordinator.recoverIncomplete()
            scheduleManager.reconcile("startup")
        }
        // One-shot location warm-up: the LocationManager only serves
        // getLastKnownLocation to apps that have (recently) been active
        // consumers. A single passive request makes the cached fix visible to
        // /v1/location (used by the brain's travel/commute inference).
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val ok = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (ok && (lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) ||
                        lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER))) {
                val locListener = object : android.location.LocationListener {
                    override fun onLocationChanged(location: android.location.Location) {}
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                val provider = if (lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                    android.location.LocationManager.NETWORK_PROVIDER
                } else {
                    android.location.LocationManager.GPS_PROVIDER
                }
                scope.launch {
                    kotlinx.coroutines.delay(1000)
                    try {
                        lm.requestLocationUpdates(provider, 0L, 0f, locListener, android.os.Looper.getMainLooper())
                        kotlinx.coroutines.delay(3000)
                        lm.removeUpdates(locListener)
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun processEvent(event: AutomationEvent): List<ExecutionLog> {
        if (!isRunning) return emptyList()
        val targetProfileId = event.payload["profileId"]?.toString()
        val targeted = event.type == "MANUAL" && !targetProfileId.isNullOrBlank()
        return dispatch(
            EventEnvelope.create(
                type = event.type,
                payload = event.payload,
                source = "internal",
                occurredAt = event.timestamp
            ),
            targetProfileId = if (targeted) targetProfileId else null
        ).logs
    }

    suspend fun dispatch(
        event: EventEnvelope,
        targetProfileId: String? = null,
        executeNow: Boolean = true
    ): DispatchResult {
        if (!isRunning) {
            return DispatchResult(event, emptyList(), emptyList(), emptyList(), deduplicated = false)
        }
        return dispatcher.dispatch(
            event = event,
            targetProfileId = targetProfileId,
            executeNow = executeNow
        )
    }

    suspend fun getRun(runId: String): RunSnapshot? {
        val run = runStore.getRun(runId) ?: return null
        return RunSnapshot(run, runStore.listSteps(runId))
    }

    suspend fun listRuns(limit: Int, profileId: String? = null): List<RunSnapshot> {
        return runStore.listRuns(limit.coerceIn(1, 500), profileId).map { run ->
            RunSnapshot(run, runStore.listSteps(run.runId))
        }
    }

    suspend fun cancelRun(runId: String): RunSnapshot = coordinator.cancel(runId)

    suspend fun retryRun(runId: String): RunSnapshot {
        val retry = coordinator.retry(runId)
        return coordinator.execute(retry)
    }

    suspend fun resumeRun(runId: String): RunSnapshot = coordinator.execute(runId)

    suspend fun listSchedules(): List<ScheduleRegistration> = scheduleManager.list()

    suspend fun getSchedule(profileId: String): ScheduleRegistration? = scheduleManager.get(profileId)

    suspend fun reconcileSchedules(reason: String): List<ScheduleRegistration> =
        scheduleManager.reconcile(reason)

    suspend fun deliverSchedule(scheduleId: String, scheduledFor: Long): ScheduleFire? =
        scheduleManager.deliver(scheduleId, scheduledFor)

    suspend fun syncSchedule(profileId: String) {
        val profile = repository.getProfileById(profileId)
        if (profile == null) {
            scheduleManager.unschedule(profileId)
        } else {
            scheduleManager.syncProfile(profile)
        }
    }

    private suspend fun ingestScheduleFire(fire: ScheduleFire) {
        dispatch(
            EventEnvelope.create(
                type = fire.triggerType,
                payload = fire.payload,
                source = "scheduler",
                occurredAt = fire.scheduledFor,
                receivedAt = fire.firedAt,
                dedupeKey = fire.dedupeKey,
                correlationId = fire.deliveryId
            ),
            targetProfileId = fire.profileId
        )
    }

    private fun lastKnownLocation(): GeoPoint? {
        return try {
            val granted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return null
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
            val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            loc?.let { GeoPoint(it.latitude, it.longitude) }
        } catch (_: Exception) {
            null
        }
    }

    private fun collectCurrentDeviceState(): Map<String, Any?> {
        val state = mutableMapOf<String, Any?>()
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            state["isScreenOn"] = powerManager?.isInteractive ?: false

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            state["ringerMode"] = when (audioManager?.ringerMode) {
                android.media.AudioManager.RINGER_MODE_SILENT -> "silent"
                android.media.AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
                else -> "normal"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return state
    }

    fun setRunningState(running: Boolean) {
        isRunning = running
    }

    fun getUptimeMs(): Long {
        return System.currentTimeMillis() - startTimeMs
    }

    fun shutdown() {
        isRunning = false
        executor.shutdown()
    }
}

private fun String.isNull_or_Empty(): Boolean = this.trim().isEmpty()
