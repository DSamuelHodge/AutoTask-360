package com.example.data

import com.example.domain.RetentionLimits
import com.example.domain.RetentionReport

class RetentionSweeper(
    private val database: AutoTaskDatabase,
    private val limits: RetentionLimits = RetentionLimits.DEFAULT
) {
    suspend fun prune(nowMs: Long = System.currentTimeMillis()): RetentionReport {
        val runCutoff = nowMs - limits.terminalRunMaxAgeMs
        val eventCutoff = nowMs - limits.eventMaxAgeMs
        val logCutoff = nowMs - limits.logMaxAgeMs
        val deletedRuns = database.runDao().deleteTerminalOlderThan(runCutoff)
        val deletedSteps = database.stepDao().deleteOrphans()
        val deletedEvents = database.eventDao().deleteOrphansOlderThan(eventCutoff)
        val deletedByAge = database.logDao().deleteOlderThan(logCutoff)
        val deletedByCount = database.logDao().trimToNewest(limits.logMaxRows)
        return RetentionReport(
            deletedRuns = deletedRuns,
            deletedSteps = deletedSteps,
            deletedEvents = deletedEvents,
            deletedLogs = deletedByAge + deletedByCount
        )
    }
}
