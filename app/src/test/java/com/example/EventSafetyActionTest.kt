package com.example

import androidx.room.Room
import com.example.data.AutoTaskDatabase
import com.example.data.AutomationProfile
import com.example.data.AutoTaskRepository
import com.example.engine.ActionExecutor
import com.example.engine.AutomationEvent
import com.example.engine.ExecutionPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EventSafetyActionTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var db: AutoTaskDatabase
    private lateinit var repository: AutoTaskRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AutoTaskDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AutoTaskRepository(context)
    }

    @After
    fun tearDown() {
        ExecutionPolicy.agentWritesEnabled = true
        ExecutionPolicy.executionEnabled = true
        db.close()
    }

    private fun executor() = ActionExecutor(context, repository)

    private fun profileWith(actionsJson: String): AutomationProfile = AutomationProfile(
        id = "p1",
        name = "P1",
        isEnabled = true,
        triggerType = "MANUAL",
        triggerConfigJson = "{}",
        conditionsJson = "{}",
        actionsJson = actionsJson,
        cooldownMs = 0L,
        priority = 0
    )

    @Test
    fun dryRunDoesNotExecuteSideEffectsAndReturnsDryRunStatus() {
        val profile = profileWith("""[{"type":"SEND_SMS","params":{"number":"+15551234567","text":"hi"}}]""")
        val (status, results) = executor().executeActions(
            profile,
            AutomationEvent(type = "MANUAL", payload = mapOf("dryRun" to true)),
            dryRun = true
        )

        assertEquals("DRY_RUN", status)
        assertEquals(1, results.size)
        assertEquals("DRY_RUN", results.first().status)
        assertEquals("would execute SEND_SMS", results.first().detail)
    }

    @Test
    fun dryRunSkipsCallActionWithoutDialing() {
        val profile = profileWith("""[{"type":"CALL","params":{"number":"+15551234567"}}]""")
        val (status, results) = executor().executeActions(
            profile,
            AutomationEvent(type = "MANUAL", payload = emptyMap()),
            dryRun = true
        )

        assertEquals("DRY_RUN", status)
        assertEquals("would execute CALL", results.first().detail)
    }

    @Test
    fun highRiskActionSkippedWhenAgentWritesLocked() {
        ExecutionPolicy.agentWritesEnabled = false
        val profile = profileWith("""[{"type":"CALL","params":{"number":"+15551234567"}}]""")
        val (status, results) = executor().executeActions(
            profile,
            AutomationEvent(type = "MANUAL", payload = emptyMap())
        )

        assertEquals("SKIPPED", results.first().status)
        assertTrue(results.first().detail.contains("blocked"))
        assertFalse(ExecutionPolicy.isHighRiskAllowed())
    }

    @Test
    fun highRiskActionAllowedByDefaultAutonomy() {
        val profile = profileWith("""[{"type":"SEND_SMS","params":{"number":"+15551234567","text":"hi"}}]""")
        val (status, results) = executor().executeActions(
            profile,
            AutomationEvent(type = "MANUAL", payload = emptyMap())
        )

        assertFalse(results.first().status == "SKIPPED")
        assertTrue(ExecutionPolicy.isHighRiskAllowed())
    }
}
