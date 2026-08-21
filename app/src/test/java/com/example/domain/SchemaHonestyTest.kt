package com.example.domain

import androidx.test.core.app.ApplicationProvider
import com.example.application.AutomationCommandFacade
import com.example.data.PolicySeeder
import com.example.engine.CapabilityProvider
import com.example.engine.SchemaProvider
import com.example.server.KtorServerConfig
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SchemaHonestyTest {

    private val overclaimedTriggers = listOf(
        "APP_LAUNCH",
        "PACKAGE_CHANGED",
        "OUTGOING_CALL",
        "NOTIFICATION_REMOVED",
        "DREAMING",
        "CUSTOM_INTENT",
        "SHAKE",
        "PROXIMITY",
        "LIGHT",
        "STEP",
        "FOREGROUND_APP"
    )

    private val stubActions = listOf(
        "POWER_SAVE",
        "WIFI_ACTION",
        "BLUETOOTH_ACTION",
        "AIRPLANE_MODE_ACTION",
        "HOTSPOT",
        "NFC_ACTION",
        "KILL_APP",
        "CAMERA"
    )

    @Test
    fun overclaimedTriggersArePolicyReady() {
        overclaimedTriggers.forEach { type ->
            val descriptor = AutomationSchema.trigger(type)
            assertNotNull(type, descriptor)
            assertEquals("$type should be policy-ready", "policy-ready", descriptor!!.state)
        }
    }

    @Test
    fun deliveredCoreTriggersStayDeliveryReady() {
        listOf(
            "TIME",
            "SCHEDULE",
            "BATTERY",
            "SMS",
            "SCREEN",
            "NOTIFICATION",
            "MANUAL",
            "BOOT",
            "WIFI",
            "HEADSET",
            "USB",
            "CALL",
            "INCOMING_CALL"
        ).forEach { type ->
            assertEquals(type, "delivery-ready", AutomationSchema.trigger(type)!!.state)
        }
    }

    @Test
    fun unusedConfigKeysAreNotAdvertised() {
        assertFalse(AutomationSchema.trigger("WIFI")!!.configByName.containsKey("signalStrength"))
        assertFalse(AutomationSchema.trigger("INCOMING_CALL")!!.configByName.containsKey("contactName"))
        assertFalse(AutomationSchema.trigger("INCOMING_CALL")!!.configByName.containsKey("isUnknown"))
        assertFalse(AutomationSchema.trigger("HEADSET")!!.configByName.containsKey("hasMicrophone"))
        assertFalse(AutomationSchema.trigger("USB")!!.configByName.containsKey("deviceClass"))
        assertFalse(AutomationSchema.trigger("NOTIFICATION")!!.configByName.containsKey("priority"))
    }

    @Test
    fun droppedWifiSignalStrengthIsRejectedOnCompile() {
        val error = try {
            DefinitionCompiler.compile(
                JSONObject()
                    .put("id", "wifi-signal")
                    .put("name", "Wifi")
                    .put("triggerType", "WIFI")
                    .put("triggerConfig", JSONObject().put("signalStrength", 3))
                    .put("actions", JSONArray().put(JSONObject().put("type", "LOG")))
            )
            fail("expected InvalidAutomationException")
            throw IllegalStateException("unreachable")
        } catch (e: InvalidAutomationException) {
            e
        }
        assertTrue(error.errors.any { it.message.contains("signalStrength") || it.message.contains("unknown field") })
    }

    @Test
    fun droppedHeadsetHasMicrophoneIsRejectedOnCompile() {
        val error = try {
            DefinitionCompiler.compile(
                JSONObject()
                    .put("id", "headset-mic")
                    .put("name", "Headset")
                    .put("triggerType", "HEADSET")
                    .put("triggerConfig", JSONObject().put("connected", true).put("hasMicrophone", true))
                    .put("actions", JSONArray().put(JSONObject().put("type", "LOG")))
            )
            fail("expected InvalidAutomationException")
            throw IllegalStateException("unreachable")
        } catch (e: InvalidAutomationException) {
            e
        }
        assertTrue(error.errors.any { it.message.contains("hasMicrophone") || it.message.contains("unknown field") })
    }

    @Test
    fun stubActionsArePolicyReadyInSchemaJson() {
        val actions = JSONObject(SchemaProvider.getSchemaJson()).getJSONObject("actionTypes")
        stubActions.forEach { type ->
            assertEquals(type, "policy-ready", actions.getJSONObject(type).getString("state"))
        }
        assertEquals("delivery-ready", actions.getJSONObject("LOG").getString("state"))
        assertEquals("delivery-ready", actions.getJSONObject("SEND_SMS").getString("state"))
    }

    @Test
    fun seederDoesNotShipInertLightSensorRecipe() {
        val ids = PolicySeeder.getStarterProfiles().map { it.id }
        assertFalse(ids.contains("cos-light-sensor-brightness"))
        assertTrue(ids.isNotEmpty())
    }

    @Test
    fun catalogVersionTwoKeepsPersistedVersionOneCompilable() {
        val compiled = DefinitionCompiler.compile(
            JSONObject()
                .put("id", "v1-battery")
                .put("name", "Low battery")
                .put("schemaVersion", 1)
                .put("triggerType", "BATTERY")
                .put("triggerConfig", JSONObject().put("levelBelow", 20))
                .put("actions", JSONArray().put(JSONObject().put("type", "LOG")))
        )
        assertEquals(1, compiled.definition.schemaVersion)
        assertEquals(2, AutomationSchema.CURRENT_VERSION)
    }

    @Test
    fun bootDescriptionCoversStartupAndPackageReplace() {
        val boot = AutomationSchema.trigger("BOOT")!!
        assertTrue(boot.source.contains("BOOT_COMPLETED"))
        assertTrue(boot.source.contains("MY_PACKAGE_REPLACED"))
        assertTrue(boot.description.contains("startup"))
        assertTrue(boot.description.contains("replaced"))
    }

    @Test
    fun cameraSchemaMatchesCapabilityRiskAndAutonomy() {
        val camera = AutomationSchema.action("CAMERA")!!
        assertEquals("high", camera.risk)
        assertEquals("confirm_required", camera.autonomy)
        assertEquals("policy-ready", camera.state)
    }

    @Test
    fun cameraCapabilityIsNotReadyWhileTheActionIsAStub() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val camera = JSONObject(CapabilityProvider.getCapabilitiesJson(context))
            .getJSONObject("actions")
            .getJSONObject("CAMERA")
        assertFalse(camera.getBoolean("ready"))
        assertTrue(camera.getString("notes").contains("not_implemented") || camera.getString("notes").contains("policy-ready"))
    }

    @Test
    fun statusMapUsesCommandUrlNotRelayTarget() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val snapshot = KtorServerConfig.getSnapshot(context)
        val status = AutomationCommandFacade.getInstance(context).statusMap()
        assertEquals(snapshot.baseUrl, status["command_url"])
        assertFalse(status.containsKey("relay_target"))
    }
}
