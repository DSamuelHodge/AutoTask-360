package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.EffectRecord

@Entity(
    tableName = "effect_records",
    indices = [Index("completedAt")]
)
data class EffectRecordEntity(
    @PrimaryKey val effectId: String,
    val type: String,
    val status: String,
    val detail: String,
    val runId: String?,
    val stepIndex: Int?,
    val completedAt: Long
) {
    fun toDomain(): EffectRecord = EffectRecord(
        effectId = effectId,
        type = type,
        status = status,
        detail = detail,
        runId = runId,
        stepIndex = stepIndex,
        completedAt = completedAt
    )

    companion object {
        fun from(record: EffectRecord) = EffectRecordEntity(
            effectId = record.effectId,
            type = record.type,
            status = record.status,
            detail = record.detail,
            runId = record.runId,
            stepIndex = record.stepIndex,
            completedAt = record.completedAt
        )
    }
}
