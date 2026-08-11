package com.example

import androidx.room.Room
import com.example.data.AutoTaskDatabase
import com.example.data.AutomationProfile
import com.example.engine.AutoTaskEngine
import com.example.engine.AutomationEvent
import com.example.server.EventRequestParser
import org.json.JSONObject
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
class EventRoutingTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var repository: AutoTaskRepository

    @Before
    fun setUp() {
        Room.inMemoryDatabaseBuilder(context, AutoTaskDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AutoTaskEngine.getInstance(context).repository
    }

    private fun profile(id: String, name: String, enabled: Boolean): AutomationProfile =
        AutomationProfile(
            id = id,
            name = name,
            description = "",
            isEnabled = enabled,
            triggerType = "MANUAL",
            triggerConfigJson = "{}",
            conditionsJson = "{}",
            actionsJson = "[{\"type\":\"LOG\",\"params\":{\"message\":\"$id ran\"}}]",
            cooldownMs = 0L,
            priority = 0
        )

    @Test
    fun manualWithProfileIdTargetsOnlyThatProfile() {
        repository.upsertProfile(profile("X", "Profile X", enabled = true))
        repository.upsertProfile(profile("Y", "Profile Y", enabled = true))

        val request = EventRequestParser.parse(
            JSONObject("""{"triggerType":"MANUAL","profileId":"X"}""")
        )
        assertEquals("X", request.targetProfileId)

        val event = AutomationEvent(type = request.triggerType, payload = request.payload)
        val logs = AutoTaskEngine.getInstance(context).processEvent(event)

        val executedIds = logs.map { it.profileId }.toSet()
        assertEquals(setOf("X"), executedIds)
        assertFalse("Profile Y must NOT execute", executedIds.contains("Y"))
    }

    @Test
    fun manualWithoutProfileIdBroadcastsToAllEnabledManualProfiles() {
        repository.upsertProfile(profile("X", "Profile X", enabled = true))
        repository.upsertProfile(profile("Y", "Profile Y", enabled = true))
        repository.upsertProfile(profile("Z", "Profile Z", enabled = false))

        val request = EventRequestParser.parse(
            JSONObject("""{"triggerType":"MANUAL"}""")
        )
        assertTrue(request.targetProfileId == null)

        val event = AutomationEvent(type = request.triggerType, payload = request.payload)
        val logs = AutoTaskEngine.getInstance(context).processEvent(event)

        val executedIds = logs.map { it.profileId }.toSet()
        assertEquals(setOf("X", "Y"), executedIds)
        assertFalse(executedIds.contains("Z"))
    }
}
