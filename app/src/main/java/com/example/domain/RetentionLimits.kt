package com.example.domain

/**
 * How long AutoTask keeps durable history. Incomplete runs
 * (`QUEUED` / `RUNNING` / `WAITING`) are never pruned.
 */
data class RetentionLimits(
    val terminalRunMaxAgeMs: Long = DEFAULT_MAX_AGE_MS,
    val eventMaxAgeMs: Long = DEFAULT_MAX_AGE_MS,
    val logMaxAgeMs: Long = DEFAULT_MAX_AGE_MS,
    val logMaxRows: Int = DEFAULT_LOG_MAX_ROWS
) {
    companion object {
        const val DEFAULT_MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000
        const val DEFAULT_LOG_MAX_ROWS = 500
        val DEFAULT = RetentionLimits()
    }
}

data class RetentionReport(
    val deletedRuns: Int = 0,
    val deletedSteps: Int = 0,
    val deletedEvents: Int = 0,
    val deletedLogs: Int = 0,
    val deletedEffects: Int = 0
)
