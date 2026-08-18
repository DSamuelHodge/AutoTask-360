package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.StepRun

@Entity(
    tableName = "step_runs",
    indices = [
        Index("runId"),
        Index(value = ["runId", "stepIndex"], unique = true)
    ]
)
data class StepRunEntity(
    @PrimaryKey val stepRunId: String,
    val runId: String,
    val stepIndex: Int,
    val type: String,
    val status: String,
    val detail: String,
    val attempt: Int,
    val startedAt: Long?,
    val finishedAt: Long?,
    val continuationJson: String?,
    val effectId: String? = null
) {
    fun toDomain(): StepRun = StepRun(
        stepRunId = stepRunId,
        runId = runId,
        stepIndex = stepIndex,
        type = type,
        status = status,
        detail = detail,
        attempt = attempt,
        startedAt = startedAt,
        finishedAt = finishedAt,
        continuationJson = continuationJson,
        effectId = effectId
    )

    companion object {
        fun from(step: StepRun) = StepRunEntity(
            stepRunId = step.stepRunId,
            runId = step.runId,
            stepIndex = step.stepIndex,
            type = step.type,
            status = step.status,
            detail = step.detail,
            attempt = step.attempt,
            startedAt = step.startedAt,
            finishedAt = step.finishedAt,
            continuationJson = step.continuationJson,
            effectId = step.effectId
        )
    }
}
