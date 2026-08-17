package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.AutomationRun

@Entity(
    tableName = "automation_runs",
    indices = [
        Index("eventId"),
        Index("profileId"),
        Index("status"),
        Index("retryOfRunId")
    ]
)
data class AutomationRunEntity(
    @PrimaryKey val runId: String,
    val eventId: String,
    val profileId: String,
    val profileName: String,
    val profileRevision: Long,
    val triggerType: String,
    val correlationId: String,
    val status: String,
    val currentStepIndex: Int,
    val attempt: Int,
    val maxAttempts: Int,
    val skippedReason: String,
    val error: String,
    val actionsJson: String,
    val createdAt: Long,
    val updatedAt: Long,
    val startedAt: Long?,
    val finishedAt: Long?,
    val timeoutAt: Long?,
    val wakeAt: Long?,
    val retryOfRunId: String?,
    val durationMs: Long
) {
    fun toDomain(): AutomationRun = AutomationRun(
        runId = runId,
        eventId = eventId,
        profileId = profileId,
        profileName = profileName,
        profileRevision = profileRevision,
        triggerType = triggerType,
        correlationId = correlationId,
        status = status,
        currentStepIndex = currentStepIndex,
        attempt = attempt,
        maxAttempts = maxAttempts,
        skippedReason = skippedReason,
        error = error,
        actionsJson = actionsJson,
        createdAt = createdAt,
        updatedAt = updatedAt,
        startedAt = startedAt,
        finishedAt = finishedAt,
        timeoutAt = timeoutAt,
        wakeAt = wakeAt,
        retryOfRunId = retryOfRunId,
        durationMs = durationMs
    )

    companion object {
        fun from(run: AutomationRun) = AutomationRunEntity(
            runId = run.runId,
            eventId = run.eventId,
            profileId = run.profileId,
            profileName = run.profileName,
            profileRevision = run.profileRevision,
            triggerType = run.triggerType,
            correlationId = run.correlationId,
            status = run.status,
            currentStepIndex = run.currentStepIndex,
            attempt = run.attempt,
            maxAttempts = run.maxAttempts,
            skippedReason = run.skippedReason,
            error = run.error,
            actionsJson = run.actionsJson,
            createdAt = run.createdAt,
            updatedAt = run.updatedAt,
            startedAt = run.startedAt,
            finishedAt = run.finishedAt,
            timeoutAt = run.timeoutAt,
            wakeAt = run.wakeAt,
            retryOfRunId = run.retryOfRunId,
            durationMs = run.durationMs
        )
    }
}
