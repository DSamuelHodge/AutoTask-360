package com.example.engine

data class AutomationEvent(
    val type: String,                       // SMS, BATTERY, WIFI, SCREEN, BLUETOOTH, NOTIFICATION, TIME, MANUAL, BOOT, CALL
    val timestamp: Long = System.currentTimeMillis(),
    val payload: Map<String, Any?> = emptyMap()
)
