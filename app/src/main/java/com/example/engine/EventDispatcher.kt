package com.example.engine

import com.example.data.AutomationProfile
import com.example.data.ExecutionLog
import com.example.domain.AutomationRun
import com.example.domain.DefinitionCompiler
import com.example.domain.EventDispatchException
import com.example.domain.EventEnvelope
import com.example.domain.InvalidAutomationException
import com.example.domain.RunSnapshot
import com.example.domain.RunStatuses
import com.example.domain.toAutomationEvent
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DispatchResult(
    val event: EventEnvelope,
    val runs: List<RunSnapshot>,
    val logs: List<ExecutionLog>,
    val plannedProfiles: List<AutomationProfile>,
    val deduplicated: Boolean
)

class EventDispatcher(
    private val store: RunStore,
    private val coordinator: RunCoordinator,
    private val loadEnabled: suspend (String) -> List<AutomationProfile>,
    private val loadProfile: suspend (String) -> AutomationProfile?,
    private val deviceState: () -> Map<String, Any?> = { emptyMap() },
    private val insertLog: suspend (ExecutionLog) -> Long = { 0L },
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val maxIncompleteEvents: Int = RunCoordinator.MAX_INCOMPLETE_EVENTS,
    private val onDispatch: (DispatchResult) -> Unit = {}
) {
    private val mutex = Mutex()

    suspend fun dispatch(
        event: EventEnvelope,
        targetProfileId: String? = null,
        executeNow: Boolean = true,
        evaluateCooldown: Boolean = true,
        evaluateTriggerConfig: Boolean = true
    ): DispatchResult = mutex.withLock {
        dispatchLocked(event, targetProfileId, executeNow, evaluateCooldown, evaluateTriggerConfig)
            .also { onDispatch(it) }
    }

    private suspend fun dispatchLocked(
        event: EventEnvelope,
        targetProfileId: String?,
        executeNow: Boolean,
        evaluateCooldown: Boolean,
        evaluateTriggerConfig: Boolean
    ): DispatchResult {
        val existingById = store.getEvent(event.eventId)
        if (existingById != null) {
            val runs = store.listRunsForEvent(event.eventId).map { run ->
                RunSnapshot(run, store.listSteps(run.runId))
            }
            return DispatchResult(existingById, runs, emptyList(), emptyList(), deduplicated = true)
        }
        event.idempotencyKey?.let { key ->
            store.findEventByIdempotencyKey(key)?.let { existing ->
                val runs = store.listRunsForEvent(existing.eventId).map { run ->
                    RunSnapshot(run, store.listSteps(run.runId))
                }
                return DispatchResult(existing, runs, emptyList(), emptyList(), deduplicated = true)
            }
        }
        event.dedupeKey?.let { key ->
            store.findEventByDedupeKey(event.type, key)?.let { existing ->
                val runs = store.listRunsForEvent(existing.eventId).map { run ->
                    RunSnapshot(run, store.listSteps(run.runId))
                }
                return DispatchResult(existing, runs, emptyList(), emptyList(), deduplicated = true)
            }
        }
        if (store.incompleteEventCount() >= maxIncompleteEvents) {
            throw EventDispatchException("event queue is full ($maxIncompleteEvents incomplete events)")
        }

        store.insertEvent(event)
        val now = clock()
        val automationEvent = event.toAutomationEvent()
        val targeted = !targetProfileId.isNullOrBlank()
        val planned = if (targeted) {
            listOfNotNull(loadProfile(targetProfileId))
        } else {
            loadEnabled(event.type).sortedByDescending { it.priority }
        }

        if (planned.isEmpty() && event.type != "MANUAL") {
            return DispatchResult(event, emptyList(), emptyList(), emptyList(), deduplicated = false)
        }

        val snapshots = mutableListOf<RunSnapshot>()
        val logs = mutableListOf<ExecutionLog>()
        for (profile in planned) {
            val run = createRun(event, profile, now)
            store.insertRun(run)
            val compiledOk = try {
                DefinitionCompiler.getOrCompile(profile)
                true
            } catch (_: InvalidAutomationException) {
                false
            }
            if (!compiledOk) {
                val skipped = skip(run, "invalid_definition", now)
                snapshots.add(skipped)
                logs.add(recordLog(skipped, event))
                continue
            }

            val match = Matcher.evaluate(
                profile = profile,
                event = automationEvent,
                deviceState = deviceState(),
                nowMs = now,
                evaluateCooldown = evaluateCooldown && !targeted,
                evaluateTriggerConfig = evaluateTriggerConfig && !targeted
            )
            if (!match.isMatch) {
                val skipped = skip(run, match.skippedReason.ifBlank { "not_matched" }, now)
                snapshots.add(skipped)
                if (match.skippedReason != "config_mismatch") {
                    logs.add(recordLog(skipped, event))
                }
                continue
            }

            val executed = if (executeNow) coordinator.execute(run) else RunSnapshot(run, emptyList())
            snapshots.add(executed)
            if (RunStatuses.isTerminal(executed.run.status)) {
                logs.add(recordLog(executed, event))
            }
        }
        return DispatchResult(event, snapshots, logs, planned, deduplicated = false)
    }

    private fun createRun(event: EventEnvelope, profile: AutomationProfile, now: Long): AutomationRun {
        return AutomationRun(
            runId = idFactory(),
            eventId = event.eventId,
            profileId = profile.id,
            profileName = profile.name,
            profileRevision = profile.revision,
            triggerType = event.type,
            correlationId = event.correlationId,
            status = RunStatuses.QUEUED,
            actionsJson = profile.actionsJson,
            createdAt = now,
            updatedAt = now,
            maxAttempts = RunCoordinator.MAX_ATTEMPTS
        )
    }

    private suspend fun recordLog(snapshot: RunSnapshot, event: EventEnvelope): ExecutionLog {
        val log = toLog(snapshot, event)
        val id = insertLog(log)
        return if (id > 0L) log.copy(id = id) else log
    }

    private suspend fun skip(run: AutomationRun, reason: String, now: Long): RunSnapshot {
        val skipped = run.copy(
            status = RunStatuses.SKIPPED,
            skippedReason = reason,
            finishedAt = now,
            updatedAt = now
        )
        store.updateRun(skipped)
        return RunSnapshot(skipped, emptyList())
    }

    companion object {
        fun toLog(snapshot: RunSnapshot, event: EventEnvelope): ExecutionLog {
            val results = org.json.JSONArray()
            snapshot.steps.forEach { step ->
                results.put(
                    org.json.JSONObject()
                        .put("step", step.stepIndex)
                        .put("type", step.type)
                        .put("status", step.status)
                        .put("detail", step.detail)
                )
            }
            return ExecutionLog(
                profileId = snapshot.run.profileId,
                profileName = snapshot.run.profileName,
                triggerType = event.type,
                status = snapshot.run.status,
                skippedReason = snapshot.run.skippedReason,
                actionsResultJson = results.toString(),
                durationMs = snapshot.run.durationMs,
                timestamp = snapshot.run.finishedAt ?: snapshot.run.updatedAt,
                runId = snapshot.run.runId
            )
        }
    }
}
