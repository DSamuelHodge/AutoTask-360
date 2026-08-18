package com.example.domain

import com.example.data.AutomationProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileSearchTest {

    private val catalog = listOf(
        profile("cos-sms-send", "CoS SMS Send", "Send an outbound SMS", "MANUAL", """[{"type":"SEND_SMS"}]""", 90),
        profile("cos-informed-notify", "CoS Informed Notify", "Notify speak and SMS", "MANUAL", """[{"type":"NOTIFICATION"},{"type":"SPEAK"},{"type":"SEND_SMS"}]""", 80),
        profile("cos-morning-brief", "CoS Morning Briefing", "Daily briefing", "TIME", """[{"type":"SPEAK"},{"type":"NOTIFICATION"},{"type":"SEND_SMS"}]""", 70),
        profile("cos-evening-winddown", "CoS Evening Wind-Down", "Device prepared for rest", "TIME", """[{"type":"DND"},{"type":"NOTIFICATION"}]""", 60),
        profile("cos-deep-link", "CoS Deep Link", "Open a URL", "MANUAL", """[{"type":"OPEN_URL"}]""", 50),
        profile("cos-meeting-mode", "CoS Meeting Mode", "Silence and DND", "MANUAL", """[{"type":"DND"},{"type":"AUDIO"}]""", 40)
    )

    @Test
    fun blankQueryReturnsEveryProfileInInputOrder() {
        val result = ProfileSearch.filter(catalog, ProfileListQuery())
        assertEquals(catalog.map { it.id }, result.map { it.id })
    }

    @Test
    fun qSmsDoesNotReturnEveryProfile() {
        val result = ProfileSearch.filter(catalog, ProfileListQuery(q = "sms"))
        val ids = result.map { it.id }
        assertTrue(ids.contains("cos-sms-send"))
        assertTrue(ids.contains("cos-informed-notify"))
        assertTrue(ids.contains("cos-morning-brief"))
        assertFalse(ids.contains("cos-evening-winddown"))
        assertFalse(ids.contains("cos-deep-link"))
        assertFalse(ids.contains("cos-meeting-mode"))
        assertEquals("cos-sms-send", ids.first())
        assertTrue(result.size < catalog.size)
    }

    @Test
    fun qTextUsesSmsAlias() {
        val ids = ProfileSearch.filter(catalog, ProfileListQuery(q = "text")).map { it.id }
        assertTrue(ids.contains("cos-sms-send"))
        assertFalse(ids.contains("cos-evening-winddown"))
    }

    @Test
    fun actionTypeIsExact() {
        val ids = ProfileSearch.filter(catalog, ProfileListQuery(actionType = "SEND_SMS")).map { it.id }
        assertEquals(listOf("cos-sms-send", "cos-informed-notify", "cos-morning-brief"), ids)
    }

    @Test
    fun triggerTypeAndIdFilters() {
        val byTrigger = ProfileSearch.filter(catalog, ProfileListQuery(triggerType = "TIME"))
        assertEquals(listOf("cos-morning-brief", "cos-evening-winddown"), byTrigger.map { it.id })

        val byId = ProfileSearch.filter(catalog, ProfileListQuery(id = "cos-sms-send"))
        assertEquals(listOf("cos-sms-send"), byId.map { it.id })
    }

    @Test
    fun multiTokenAndDoesNotBroadMatch() {
        val ids = ProfileSearch.filter(catalog, ProfileListQuery(q = "morning brief")).map { it.id }
        assertEquals(listOf("cos-morning-brief"), ids)
    }

    @Test
    fun unknownQueryIsEmpty() {
        assertTrue(ProfileSearch.filter(catalog, ProfileListQuery(q = "xyzzy")).isEmpty())
    }

    @Test
    fun limitCapsRankedResults() {
        val result = ProfileSearch.filter(catalog, ProfileListQuery(q = "sms", limit = 1))
        assertEquals(listOf("cos-sms-send"), result.map { it.id })
    }

    private fun profile(
        id: String,
        name: String,
        description: String,
        trigger: String,
        actions: String,
        priority: Int
    ) = AutomationProfile(
        id = id,
        name = name,
        description = description,
        isEnabled = true,
        triggerType = trigger,
        triggerConfigJson = "{}",
        conditionsJson = "{}",
        actionsJson = actions,
        priority = priority
    )
}
