package com.example

import com.example.application.AutomationCommandFacade
import com.example.data.AutomationProfile
import com.example.data.ExecutionLog
import com.example.server.EventRequestParser
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutomationCommandFacadeTest {

    @Test
    fun profileSerializationIsSharedByAdapters() {
        val profile = AutomationProfile(
            id = "profile-1",
            name = "Battery",
            triggerType = "BATTERY",
            triggerConfigJson = "{\"level\":20}",
            conditionsJson = "{}",
            actionsJson = "[{\"type\":\"NOTIFY\"}]"
        )

        val json = AutomationCommandFacade.profileToJson(profile)

        assertEquals("profile-1", json.getString("id"))
        assertEquals(20, json.getJSONObject("triggerConfigJson").getInt("level"))
        assertEquals(1, json.getJSONArray("actionsJson").length())
        assertEquals(0L, json.getLong("lastTriggeredAt"))
    }

    @Test
    fun eventSerializationPreservesDryRunAndPlan() {
        val profile = AutomationProfile(
            id = "profile-1",
            name = "Manual",
            isEnabled = true,
            triggerType = "MANUAL",
            triggerConfigJson = "{}",
            conditionsJson = "{}",
            actionsJson = "[]"
        )
        val request = EventRequestParser.parse(
            JSONObject("""{"type":"MANUAL","profileId":"profile-1","dryRun":true}""")
        )

        val json = AutomationCommandFacade.eventResultToJson(
            com.example.application.EventCommandResult(request, listOf(profile), emptyList())
        )

        assertTrue(json.getBoolean("dryRun"))
        assertEquals(1, json.getInt("profilesMatched"))
        assertEquals(0, json.getInt("logsGenerated"))
        assertEquals("profile-1", json.getJSONArray("plannedProfiles").getJSONObject(0).getString("id"))
        assertEquals(0, json.getJSONArray("results").length())
    }

    @Test
    fun logSerializationUsesCanonicalFields() {
        val log = ExecutionLog(
            id = 9L,
            profileId = "profile-1",
            profileName = "Manual",
            triggerType = "MANUAL",
            status = "SUCCESS",
            actionsResultJson = "[{\"status\":\"OK\"}]",
            durationMs = 12L,
            timestamp = 99L
        )

        val json = AutomationCommandFacade.logToJson(log)

        assertEquals(9L, json.getLong("id"))
        assertEquals("SUCCESS", json.getString("status"))
        assertEquals(1, (json.get("actionsResultJson") as JSONArray).length())
    }
}
