package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.domain.RetentionLimits
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RetentionSweeperTest {

    private lateinit var db: AutoTaskDatabase

    @Before
    fun openDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AutoTaskDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun prunesOldTerminalRunsAndKeepsIncomplete() = runBlocking {
        val now = 1_000_000L
        val old = now - RetentionLimits.DEFAULT.terminalRunMaxAgeMs - 1
        insertRun("old-success", "evt-old", "SUCCESS", old)
        insertStep("old-success", old)
        insertEvent("evt-old", old)
        insertRun("live", "evt-live", "RUNNING", old)
        insertStep("live", old)
        insertEvent("evt-live", old)

        val report = RetentionSweeper(db, RetentionLimits.DEFAULT).prune(now)

        assertEquals(1, report.deletedRuns)
        assertNull(db.runDao().getById("old-success"))
        assertNotNull(db.runDao().getById("live"))
        assertEquals(1, db.stepDao().listForRun("live").size)
        assertEquals(0, db.stepDao().listForRun("old-success").size)
        assertNull(db.eventDao().getById("evt-old"))
        assertNotNull(db.eventDao().getById("evt-live"))
    }

    @Test
    fun trimsLogsToNewestCap() = runBlocking {
        val now = 10_000L
        repeat(6) { index ->
            db.logDao().insertLog(
                ExecutionLog(
                    profileId = "p",
                    profileName = "P",
                    triggerType = "MANUAL",
                    status = "SUCCESS",
                    actionsResultJson = "[]",
                    durationMs = 1,
                    timestamp = now - index
                )
            )
        }
        val report = RetentionSweeper(
            db,
            RetentionLimits(logMaxAgeMs = 1_000_000L, logMaxRows = 2)
        ).prune(now)
        assertEquals(4, report.deletedLogs)
        assertEquals(2, db.logDao().getLogCount())
    }

    private suspend fun insertEvent(eventId: String, at: Long) {
        db.eventDao().insert(
            EventEnvelopeEntity(
                eventId = eventId,
                type = "MANUAL",
                source = "test",
                occurredAt = at,
                receivedAt = at,
                correlationId = eventId,
                payloadJson = "{}"
            )
        )
    }

    private suspend fun insertRun(runId: String, eventId: String, status: String, at: Long) {
        db.runDao().insert(
            AutomationRunEntity(
                runId = runId,
                eventId = eventId,
                profileId = "p",
                profileName = "P",
                profileRevision = 1,
                triggerType = "MANUAL",
                correlationId = eventId,
                status = status,
                currentStepIndex = 0,
                attempt = 1,
                maxAttempts = 5,
                skippedReason = "",
                error = "",
                actionsJson = "[]",
                createdAt = at,
                updatedAt = at,
                startedAt = at,
                finishedAt = if (status == "RUNNING") null else at,
                timeoutAt = null,
                wakeAt = null,
                retryOfRunId = null,
                durationMs = 0
            )
        )
    }

    private suspend fun insertStep(runId: String, at: Long) {
        db.stepDao().upsert(
            StepRunEntity(
                stepRunId = "$runId-0",
                runId = runId,
                stepIndex = 0,
                type = "LOG",
                status = "OK",
                detail = "",
                attempt = 1,
                startedAt = at,
                finishedAt = at,
                continuationJson = null,
                effectId = "e-$runId"
            )
        )
    }
}
