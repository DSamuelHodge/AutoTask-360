package com.example.engine

import com.example.data.AutomationProfile
import com.example.data.ExecutionLog
import com.example.domain.ActionStep
import com.example.domain.AutomationRun
import com.example.domain.DefinitionCompiler
import com.example.domain.EventDispatchException
import com.example.domain.EventEnvelope
import com.example.domain.InvalidAutomationException
import com.example.domain.RunNotFoundException
import com.example.domain.RunSnapshot
import com.example.domain.RunStatuses
import com.example.domain.StepResumePolicy
import com.example.domain.StepRetryPolicy
import com.example.domain.StepRun
import com.example.domain.StepStatuses
import com.example.domain.toAutomationEvent
import org.json.JSONObject
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

fun interface StepRunner {
    suspend fun run(
        profile: AutomationProfile,
        event: AutomationEvent,
        stepIndex: Int,
        type: String,
        params: JSONObject,
        effectId: String
    ): StepResult
}

interface WakeScheduler {
    fun schedule(runId: String, wakeAtEpochMs: Long)
    fun cancel(runId: String)
}

object NoOpWakeScheduler : WakeScheduler {
    override fun schedule(runId: String, wakeAtEpochMs: Long) = Unit
    override fun cancel(runId: String) = Unit
}

class RunCoordinator(
    private val store: RunStore,
    private val runner: StepRunner,
    private val wakeScheduler: WakeScheduler = NoOpWakeScheduler,
    private val onTerminal: suspend (AutomationRun, List<StepRun>, AutomationProfile, AutomationEvent) -> Unit = { _, _, _, _ -> },
    private val loadProfile: suspend (String) -> AutomationProfile? = { null },
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val stepTimeoutMs: Long = DEFAULT_STEP_TIMEOUT_MS,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val mayReenter: (String) -> Boolean = { StepResumePolicy.mayReenter(it) },
    private val maxStepAttempts: Int = StepRetryPolicy.DEFAULT_MAX_ATTEMPTS,
    private val retrySleeper: suspend (Long) -> Unit = { delay(it) }
) {
    suspend fun execute(runId: String): RunSnapshot {
        val run = store.getRun(runId) ?: throw RunNotFoundException(runId)
        return execute(run)
    }

    suspend fun execute(run: AutomationRun): RunSnapshot {
        if (RunStatuses.isTerminal(run.status)) {
            return RunSnapshot(run, store.listSteps(run.runId))
        }
        if (run.status == RunStatuses.CANCELLED) {
            return RunSnapshot(run, store.listSteps(run.runId))
        }

        val profile = loadProfile(run.profileId)
            ?: return finish(run, RunStatuses.FAILED, error = "profile_not_found")
        val event = store.getEvent(run.eventId)?.toAutomationEvent()
            ?: AutomationEvent(type = run.triggerType)

        val now = clock()
        val steps = resolveSteps(profile, run)
        var index = run.currentStepIndex
        var current = run

        if (run.status == RunStatuses.WAITING) {
            val wakeAt = run.wakeAt
            if (wakeAt != null && wakeAt > now) {
                wakeScheduler.schedule(run.runId, wakeAt)
                return RunSnapshot(run, store.listSteps(run.runId))
            }
            completeWaitStep(run, index, now)
            index += 1
            current = run.copy(
                status = RunStatuses.RUNNING,
                currentStepIndex = index,
                startedAt = run.startedAt ?: now,
                wakeAt = null,
                updatedAt = now
            )
            store.updateRun(current)
        } else {
            current = run.copy(
                status = RunStatuses.RUNNING,
                startedAt = run.startedAt ?: now,
                updatedAt = now
            )
            store.updateRun(current)
        }

        while (index < steps.size) {
            val latest = store.getRun(current.runId) ?: current
            if (latest.status == RunStatuses.CANCELLED) {
                cancelRemaining(latest, steps, index, now)
                return snapshot(latest.copy(status = RunStatuses.CANCELLED, finishedAt = now, updatedAt = now, durationMs = duration(latest, now)))
                    .also { persistTerminal(it, profile, event) }
            }
            if (latest.timeoutAt != null && now >= latest.timeoutAt) {
                return fail(latest, steps, index, "timeout", now, profile, event)
            }

            val spec = steps[index]
            val type = spec.type.uppercase()
            val existing = store.listSteps(current.runId).firstOrNull { it.stepIndex == index }
            if (existing != null && (existing.status == StepStatuses.OK || existing.status == StepStatuses.SKIPPED)) {
                index += 1
                continue
            }
            if (existing?.status == StepStatuses.INDETERMINATE) {
                return finishIndeterminate(current, existing, now, profile, event)
            }
            if (existing?.status == StepStatuses.RUNNING && !mayReenter(type)) {
                val marked = existing.copy(
                    status = StepStatuses.INDETERMINATE,
                    detail = "resume_indeterminate",
                    finishedAt = now
                )
                store.upsertStep(marked)
                return finishIndeterminate(current, marked, now, profile, event)
            }

            val effectId = existing?.effectId?.takeIf { it.isNotBlank() } ?: idFactory()
            var stepAttempt = existing?.attempt?.takeIf { it > 0 } ?: 1
            var stepRecord = StepRun(
                stepRunId = existing?.stepRunId ?: idFactory(),
                runId = current.runId,
                stepIndex = index,
                type = type,
                status = StepStatuses.RUNNING,
                attempt = stepAttempt,
                startedAt = existing?.startedAt ?: now,
                effectId = effectId
            )
            store.upsertStep(stepRecord)
            current = current.copy(status = RunStatuses.RUNNING, currentStepIndex = index, updatedAt = now)
            store.updateRun(current)

            if (type == "WAIT") {
                val durationMs = waitDurationMs(spec)
                if (durationMs <= 0L) {
                    store.upsertStep(
                        stepRecord.copy(
                            status = StepStatuses.OK,
                            detail = "Waited 0ms",
                            finishedAt = now
                        )
                    )
                    index += 1
                    continue
                }
                val wakeAt = now + durationMs
                store.upsertStep(
                    stepRecord.copy(
                        status = StepStatuses.WAITING,
                        detail = "Waiting ${durationMs}ms",
                        continuationJson = JSONObject().put("durationMs", durationMs).put("wakeAt", wakeAt).toString()
                    )
                )
                current = current.copy(
                    status = RunStatuses.WAITING,
                    currentStepIndex = index,
                    wakeAt = wakeAt,
                    updatedAt = now
                )
                store.updateRun(current)
                wakeScheduler.schedule(current.runId, wakeAt)
                return RunSnapshot(current, store.listSteps(current.runId))
            }

            var result: StepResult
            while (true) {
                result = try {
                    withTimeout(stepTimeoutMs) {
                        runner.run(profile, event, index, type, spec.params.toJsonObject(), effectId)
                    }
                } catch (_: TimeoutCancellationException) {
                    StepResult(index, type, StepStatuses.FAILED, "step_timeout")
                } catch (e: Exception) {
                    StepResult(index, type, StepStatuses.FAILED, e.localizedMessage ?: "step_exception")
                }
                if (result.status != StepStatuses.FAILED) break
                if (stepAttempt >= maxStepAttempts || !StepRetryPolicy.retryable(result.status, result.detail)) {
                    break
                }
                val sleepMs = StepRetryPolicy.backoffMs(stepAttempt)
                retrySleeper(sleepMs)
                stepAttempt += 1
                stepRecord = stepRecord.copy(attempt = stepAttempt, status = StepStatuses.RUNNING)
                store.upsertStep(stepRecord)
            }

            store.upsertStep(
                stepRecord.copy(
                    status = result.status,
                    detail = result.detail,
                    attempt = stepAttempt,
                    finishedAt = clock()
                )
            )
            if (result.status == StepStatuses.FAILED) {
                return fail(current, steps, index + 1, result.detail, clock(), profile, event)
            }
            index += 1
        }

        val recorded = store.listSteps(current.runId)
        val status = ActionExecutor.finalStatusFor(
            recorded.map { StepResult(it.stepIndex, it.type, it.status, it.detail) }
        )
        val finishedAt = clock()
        val finished = current.copy(
            status = status,
            currentStepIndex = steps.size,
            finishedAt = finishedAt,
            updatedAt = finishedAt,
            wakeAt = null,
            durationMs = duration(current, finishedAt)
        )
        store.updateRun(finished)
        val snap = RunSnapshot(finished, recorded)
        persistTerminal(snap, profile, event)
        return snap
    }

    suspend fun cancel(runId: String): RunSnapshot {
        val run = store.getRun(runId) ?: throw RunNotFoundException(runId)
        if (RunStatuses.isTerminal(run.status)) {
            return RunSnapshot(run, store.listSteps(runId))
        }
        val now = clock()
        wakeScheduler.cancel(runId)
        val steps = store.listSteps(runId).toMutableList()
        steps.filter { it.status == StepStatuses.PENDING || it.status == StepStatuses.RUNNING || it.status == StepStatuses.WAITING }
            .forEach { step ->
                val updated = step.copy(status = StepStatuses.CANCELLED, detail = "cancelled", finishedAt = now)
                store.upsertStep(updated)
            }
        val cancelled = run.copy(
            status = RunStatuses.CANCELLED,
            error = "cancelled",
            finishedAt = now,
            updatedAt = now,
            wakeAt = null,
            durationMs = duration(run, now)
        )
        store.updateRun(cancelled)
        return RunSnapshot(cancelled, store.listSteps(runId))
    }

    suspend fun retry(runId: String): AutomationRun {
        val original = store.getRun(runId) ?: throw RunNotFoundException(runId)
        if (!RunStatuses.isTerminal(original.status)) {
            throw EventDispatchException("run $runId is not terminal")
        }
        if (original.attempt >= original.maxAttempts) {
            throw EventDispatchException("run $runId exceeded maxAttempts ${original.maxAttempts}")
        }
        val now = clock()
        val retry = original.copy(
            runId = idFactory(),
            status = RunStatuses.QUEUED,
            currentStepIndex = 0,
            attempt = original.attempt + 1,
            skippedReason = "",
            error = "",
            createdAt = now,
            updatedAt = now,
            startedAt = null,
            finishedAt = null,
            wakeAt = null,
            retryOfRunId = original.runId,
            durationMs = 0L
        )
        store.insertRun(retry)
        return retry
    }

    suspend fun recoverIncomplete(): List<RunSnapshot> {
        val now = clock()
        val recovered = mutableListOf<RunSnapshot>()
        for (run in store.listIncompleteRuns()) {
            when (run.status) {
                RunStatuses.WAITING -> {
                    if (run.wakeAt != null && run.wakeAt > now) {
                        wakeScheduler.schedule(run.runId, run.wakeAt)
                        recovered.add(RunSnapshot(run, store.listSteps(run.runId)))
                    } else {
                        recovered.add(execute(run))
                    }
                }
                RunStatuses.QUEUED, RunStatuses.RUNNING -> recovered.add(execute(run))
            }
        }
        return recovered
    }

    private suspend fun completeWaitStep(run: AutomationRun, index: Int, now: Long) {
        val existing = store.listSteps(run.runId).firstOrNull { it.stepIndex == index }
        if (existing != null) {
            store.upsertStep(
                existing.copy(
                    status = StepStatuses.OK,
                    detail = existing.continuationJson?.let { "Waited until $now" } ?: "Wait completed",
                    finishedAt = now
                )
            )
        }
    }

    private suspend fun fail(
        run: AutomationRun,
        steps: List<ActionStep>,
        remainingFrom: Int,
        error: String,
        now: Long,
        profile: AutomationProfile,
        event: AutomationEvent
    ): RunSnapshot {
        cancelRemaining(run.copy(status = RunStatuses.FAILED), steps, remainingFrom, now, mark = StepStatuses.PENDING)
        val recorded = store.listSteps(run.runId)
        val status = ActionExecutor.finalStatusFor(
            recorded.map { StepResult(it.stepIndex, it.type, it.status, it.detail) }
        ).let { if (it == RunStatuses.SUCCESS) RunStatuses.FAILED else it }
        val finished = run.copy(
            status = status,
            error = error,
            finishedAt = now,
            updatedAt = now,
            wakeAt = null,
            durationMs = duration(run, now)
        )
        store.updateRun(finished)
        val snap = RunSnapshot(finished, recorded)
        persistTerminal(snap, profile, event)
        return snap
    }

    private suspend fun finishIndeterminate(
        run: AutomationRun,
        step: StepRun,
        now: Long,
        profile: AutomationProfile,
        event: AutomationEvent
    ): RunSnapshot {
        val finished = run.copy(
            status = RunStatuses.INDETERMINATE,
            currentStepIndex = step.stepIndex,
            error = "resume_indeterminate",
            finishedAt = now,
            updatedAt = now,
            wakeAt = null,
            durationMs = duration(run, now)
        )
        store.updateRun(finished)
        val snap = RunSnapshot(finished, store.listSteps(run.runId))
        persistTerminal(snap, profile, event)
        return snap
    }

    private suspend fun finish(run: AutomationRun, status: String, error: String = ""): RunSnapshot {
        val now = clock()
        val finished = run.copy(
            status = status,
            error = error,
            finishedAt = now,
            updatedAt = now,
            durationMs = duration(run, now)
        )
        store.updateRun(finished)
        return RunSnapshot(finished, store.listSteps(run.runId))
    }

    private suspend fun cancelRemaining(
        run: AutomationRun,
        steps: List<ActionStep>,
        fromIndex: Int,
        now: Long,
        mark: String = StepStatuses.CANCELLED
    ) {
        for (i in fromIndex until steps.size) {
            val existing = store.listSteps(run.runId).firstOrNull { it.stepIndex == i }
            if (existing == null && mark != StepStatuses.PENDING) {
                store.upsertStep(
                    StepRun(
                        stepRunId = idFactory(),
                        runId = run.runId,
                        stepIndex = i,
                        type = steps[i].type,
                        status = mark,
                        detail = "cancelled",
                        finishedAt = now
                    )
                )
            }
        }
    }

    private suspend fun persistTerminal(snapshot: RunSnapshot, profile: AutomationProfile, event: AutomationEvent) {
        if (!RunStatuses.isTerminal(snapshot.run.status)) return
        onTerminal(snapshot.run, snapshot.steps, profile, event)
    }

    private suspend fun snapshot(run: AutomationRun): RunSnapshot {
        store.updateRun(run)
        return RunSnapshot(run, store.listSteps(run.runId))
    }

    private fun resolveSteps(profile: AutomationProfile, run: AutomationRun): List<ActionStep> {
        return try {
            DefinitionCompiler.getOrCompile(profile).definition.steps
        } catch (_: InvalidAutomationException) {
            emptyList()
        }.ifEmpty {
            // Fall back to the snapshot captured when the run was created.
            try {
                DefinitionCompiler.compile(
                    profile.copy(actionsJson = run.actionsJson)
                ).definition.steps
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    private fun waitDurationMs(step: ActionStep): Long {
        val raw = step.params.fields["durationMs"]?.asLongOrNull() ?: 1000L
        return raw.coerceIn(0L, MAX_WAIT_MS)
    }

    private fun duration(run: AutomationRun, now: Long): Long {
        val start = run.startedAt ?: run.createdAt
        return (now - start).coerceAtLeast(0L)
    }

    companion object {
        const val DEFAULT_STEP_TIMEOUT_MS = 30_000L
        const val MAX_WAIT_MS = 24L * 60L * 60L * 1000L
        const val MAX_ATTEMPTS = 5
        const val MAX_INCOMPLETE_EVENTS = 100
    }
}
