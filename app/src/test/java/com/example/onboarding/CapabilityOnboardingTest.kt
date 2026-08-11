package com.example.onboarding

import com.example.data.AutomationProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CapabilityOnboardingTest {

    private fun profile(triggerType: String, actionsJson: String = "[]") = AutomationProfile(
        id = "test",
        name = "test",
        triggerType = triggerType,
        actionsJson = actionsJson
    )

    @Test
    fun smsProfileRequiresSendSmsRuntimePermission() {
        val reqs = requiredCapabilitiesFor(profile("SMS", """[{"type":"SEND_SMS"}]"""))
        val sms = reqs.single { it.capability == "SMS" }
        assertEquals(CapabilityRequirement.Kind.RUNTIME_PERMISSION, sms.kind)
        assertEquals(android.Manifest.permission.SEND_SMS, sms.androidPermission)
        assertNull(sms.settingsAction)
        assertNull(repairActionFor("SMS"))
    }

    @Test
    fun dndProfileRequiresNotificationPolicySpecialAccess() {
        val reqs = requiredCapabilitiesFor(profile("TIME", """[{"type":"DND"}]"""))
        val dnd = reqs.single { it.capability == "DND" }
        assertEquals(CapabilityRequirement.Kind.SPECIAL_ACCESS, dnd.kind)
        assertEquals(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, dnd.settingsAction)
        assertEquals(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, repairActionFor("DND"))
    }

    @Test
    fun locationProfileRequiresFineLocationRuntimePermission() {
        val reqs = requiredCapabilitiesFor(profile("LOCATION"))
        val loc = reqs.single { it.capability == "LOCATION" }
        assertEquals(CapabilityRequirement.Kind.RUNTIME_PERMISSION, loc.kind)
        assertEquals(android.Manifest.permission.ACCESS_FINE_LOCATION, loc.androidPermission)
        assertNull(repairActionFor("LOCATION"))
    }

    @Test
    fun batteryProfileRequiresIgnoreBatteryOptimizationSpecialAccess() {
        val reqs = requiredCapabilitiesFor(profile("BATTERY"))
        val batt = reqs.single { it.capability == "BATTERY" }
        assertEquals(CapabilityRequirement.Kind.SPECIAL_ACCESS, batt.kind)
        assertEquals(
            android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
            batt.settingsAction
        )
        assertEquals(
            android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
            repairActionFor("BATTERY")
        )
    }

    @Test
    fun triggerAndActionsAreBothConsidered() {
        val reqs = requiredCapabilitiesFor(
            profile("SMS", """[{"type":"DND"},{"type":"LOCATION"}]""")
        )
        val caps = reqs.map { it.capability }.toSet()
        assertTrue(caps.contains("SMS"))
        assertTrue(caps.contains("DND"))
        assertTrue(caps.contains("LOCATION"))
        assertFalse(caps.contains("CAMERA"))
    }

    @Test
    fun unknownCapabilityHasNoRepairAction() {
        assertNull(repairActionFor("NONSENSE"))
    }
}
