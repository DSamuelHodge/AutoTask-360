package com.example.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.domain.AutomationSchema
import com.example.engine.actions.ActionRegistry
import org.json.JSONObject
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActionRegistryInstrumentedTest {
    @Test
    fun schemaActionsAreRegisteredOnDevice() {
        val registry = ActionRegistry.standard()
        AutomationSchema.actions.keys.forEach { type ->
            assertNotNull("missing handler for $type", registry.handler(type))
        }
    }

    @Test
    fun privilegedSmsIsGatedByLiveCapabilityState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val denial = CapabilityPolicy.require(context, "SEND_SMS", JSONObject())
        val granted = CapabilityProvider.permissionSummary(context)["send_sms_granted"] == true
        if (granted) {
            assertTrue(denial == null)
        } else {
            assertNotNull(denial)
            assertTrue(denial!!.contains("SEND_SMS"))
        }
    }
}
