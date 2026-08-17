package com.example.domain

object RunStatuses {
    const val QUEUED = "QUEUED"
    const val RUNNING = "RUNNING"
    const val WAITING = "WAITING"
    const val SUCCESS = "SUCCESS"
    const val PARTIAL = "PARTIAL"
    const val FAILED = "FAILED"
    const val CANCELLED = "CANCELLED"
    const val SKIPPED = "SKIPPED"

    fun isTerminal(status: String): Boolean = status == SUCCESS ||
        status == PARTIAL ||
        status == FAILED ||
        status == CANCELLED ||
        status == SKIPPED

    fun isIncomplete(status: String): Boolean = !isTerminal(status)
}

object StepStatuses {
    const val PENDING = "PENDING"
    const val RUNNING = "RUNNING"
    const val OK = "OK"
    const val SKIPPED = "SKIPPED"
    const val FAILED = "FAILED"
    const val WAITING = "WAITING"
    const val CANCELLED = "CANCELLED"
}

data class AutomationRun(
    val runId: String,
    val eventId: String,
    val profileId: String,
    val profileName: String,
    val profileRevision: Long,
    val triggerType: String,
    val correlationId: String,
    val status: String,
    val currentStepIndex: Int = 0,
    val attempt: Int = 1,
    val maxAttempts: Int = 5,
    val skippedReason: String = "",
    val error: String = "",
    val actionsJson: String = "[]",
    val createdAt: Long,
    val updatedAt: Long,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val timeoutAt: Long? = null,
    val wakeAt: Long? = null,
    val retryOfRunId: String? = null,
    val durationMs: Long = 0L
)

data class StepRun(
    val stepRunId: String,
    val runId: String,
    val stepIndex: Int,
    val type: String,
    val status: String,
    val detail: String = "",
    val attempt: Int = 1,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val continuationJson: String? = null
)

data class RunSnapshot(
    val run: AutomationRun,
    val steps: List<StepRun>
)

class RunNotFoundException(val runId: String) : RuntimeException("Run not found: $runId")

class EventDispatchException(message: String) : IllegalStateException(message)
