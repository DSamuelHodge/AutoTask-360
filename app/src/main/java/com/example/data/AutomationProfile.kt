package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "automation_profiles")
data class AutomationProfile(
    @PrimaryKey val id: String,           // stable, e.g. cos-battery-advisory
    val name: String,
    val description: String = "",
    val isEnabled: Boolean = false,
    val triggerType: String,              // SMS, BATTERY, WIFI, SCREEN, BLUETOOTH, NOTIFICATION, TIME, MANUAL, BOOT, CALL
    val triggerConfigJson: String,        // structured filter rules, {} = any
    val conditionsJson: String = "{}",    // runtime state gates
    val actionsJson: String,              // ordered array of {type, params}
    val cooldownMs: Long = 0L,
    val priority: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastTriggeredAt: Long = 0L,
    // --- Local trust contract provenance (#19) ---
    // Tracks origin of agent-authored policy changes. Defaults to "local" for non-agent writes.
    val createdBy: String = "local",
    val modifiedBy: String = "local",
    val sourceSurface: String = "local",
    val reason: String? = null
)
