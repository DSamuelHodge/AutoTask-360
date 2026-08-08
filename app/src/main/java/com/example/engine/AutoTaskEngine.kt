package com.example.engine

import android.content.Context
import com.example.data.AutoTaskRepository
import com.example.data.ExecutionLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

class AutoTaskEngine private constructor(
    private val context: Context
) {
    val repository = AutoTaskRepository(context.applicationContext)
    val executor = ActionExecutor(context.applicationContext, repository)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()

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
        scope.launch {
            repository.seedDefaultRecipesIfNeeded()
        }
    }

    suspend fun processEvent(event: AutomationEvent): List<ExecutionLog> = mutex.withLock {
        if (!isRunning) return emptyList()

        val enabledProfiles = repository.profileDao.getEnabledProfilesForTrigger(event.type)
        val now = System.currentTimeMillis()
        val logsWritten = mutableListOf<ExecutionLog>()

        // Sort by priority descending
        val sortedProfiles = enabledProfiles.sortedByDescending { it.priority }

        if (sortedProfiles.isEmpty() && event.type != "MANUAL") {
            // No profiles registered for this trigger type
            return emptyList()
        }

        // Handle MANUAL trigger if fired directly with a specific profileId in payload
        val targetProfileId = event.payload["profileId"]?.toString()
        val targetProfiles = if (event.type == "MANUAL" && !targetProfileId.isNullOrEmpty()) {
            val prof = repository.getProfileById(targetProfileId)
            if (prof != null) listOf(prof) else sortedProfiles
        } else {
            sortedProfiles
        }

        for (profile in targetProfiles) {
            val matchResult = Matcher.evaluate(
                profile = profile,
                event = event,
                deviceState = collectCurrentDeviceState(),
                nowMs = now
            )

            if (!matchResult.isMatch) {
                val skippedLog = ExecutionLog(
                    profileId = profile.id,
                    profileName = profile.name,
                    triggerType = event.type,
                    status = "SKIPPED",
                    skippedReason = matchResult.skippedReason,
                    actionsResultJson = "[]",
                    durationMs = 0L,
                    timestamp = now
                )
                repository.insertLog(skippedLog)
                logsWritten.add(skippedLog)
                continue
            }

            // Execute Actions
            val startTime = System.currentTimeMillis()
            val (status, stepResults) = executor.executeActions(profile, event)
            val duration = System.currentTimeMillis() - startTime

            // Format step results JSON
            val resultsArray = JSONArray()
            stepResults.forEach { res ->
                val obj = JSONObject()
                obj.put("step", res.step)
                obj.put("type", res.type)
                obj.put("status", res.status)
                obj.put("detail", res.detail)
                resultsArray.put(obj)
            }

            // Write execution log
            val log = ExecutionLog(
                profileId = profile.id,
                profileName = profile.name,
                triggerType = event.type,
                status = status,
                skippedReason = "",
                actionsResultJson = resultsArray.toString(),
                durationMs = duration,
                timestamp = now
            )

            repository.insertLog(log)
            logsWritten.add(log)

            // Update lastTriggeredAt timestamp
            repository.profileDao.updateLastTriggeredAt(profile.id, now)
        }

        return logsWritten
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
