package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "execution_logs")
data class ExecutionLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val profileId: String,
    val profileName: String,
    val triggerType: String,
    val status: String,                   // SUCCESS, PARTIAL, FAILED, SKIPPED
    val skippedReason: String = "",        // cooldown_active, condition_not_met, config_mismatch, profile_disabled
    val actionsResultJson: String,        // JSON array of step execution details
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis(),
    @androidx.room.ColumnInfo(defaultValue = "")
    val runId: String = ""
)
