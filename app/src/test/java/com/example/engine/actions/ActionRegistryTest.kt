package com.example.engine.actions

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AutomationProfile
import com.example.data.AutoTaskRepository
import com.example.domain.AutomationSchema
import com.example.engine.ActionExecutor
import com.example.engine.AutomationEvent
import com.example.engine.CapabilityPolicy
import com.example.engine.StepResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ActionRegistryTest {

    @Test
    fun everySchemaActionHasARegisteredHandler() {
        val registry = ActionRegistry.standard()
        AutomationSchema.actions.keys.forEach { type ->
            assertNotNull("missing handler for $type", registry.handler(type))
            assertEquals(type, registry.metadata(type)?.type)
        }
    }

    @Test
    fun handlersDefaultToFailClosedResumeExceptSafeAndDedupeTypes() {
        val registry = ActionRegistry.standard()
        assertTrue(registry.handler("LOG")!!.safeToReenter)
        assertTrue(registry.handler("WAIT")!!.safeToReenter)
        assertTrue(registry.handler("TOAST")!!.safeToReenter)
        assertTrue(registry.handler("SEND_SMS")!!.safeToReenter)
        assertTrue(registry.handler("SEND_SMS")!!.dedupesByEffectId)
        assertTrue(registry.handler("HTTP")!!.dedupesByEffectId)
        assertFalse(registry.handler("CAMERA")!!.safeToReenter)
        assertFalse(registry.handler("CAMERA")!!.dedupesByEffectId)
        assertFalse(registry.handler("WIFI_ACTION")!!.safeToReenter)
    }

    @Test
    fun addingAnActionIsRegistrationNotACentralSwitch() {
        val extra = object : ActionHandler {
            override val type: String = "TEST_PING"
            override suspend fun execute(request: ActionRequest): StepResult {
                return StepResult(request.stepIndex, type, "OK", "pong")
            }
        }
        val registry = ActionRegistry(ActionRegistry.standardHandlers() + extra)
        assertNotNull(registry.handler("TEST_PING"))
        assertEquals("low", registry.metadata("TEST_PING")?.risk)
    }

    @Test
    fun sendSmsIsDeniedWithoutCapability() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val reason = CapabilityPolicy.require(context, "SEND_SMS", JSONObject())
        assertNotNull(reason)
        assertTrue(reason!!.contains("SEND_SMS"))
    }

    @Test
    fun dndIsDeniedWithoutCapability() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val reason = CapabilityPolicy.require(context, "DND", JSONObject())
        assertNotNull(reason)
        assertTrue(reason!!.contains("DND"))
    }

    @Test
    fun silentAudioIsDeniedWithoutDnd() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val reason = CapabilityPolicy.require(
            context,
            "AUDIO",
            JSONObject().put("ringerMode", "silent")
        )
        assertNotNull(reason)
        assertTrue(reason!!.contains("AUDIO:silent"))
    }

    @Test
    fun normalAudioDoesNotRequireDnd() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val reason = CapabilityPolicy.require(
            context,
            "AUDIO",
            JSONObject().put("ringerMode", "normal")
        )
        assertNull(reason)
    }

    @Test
    fun logHandlerWritesASuccessStep() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val executor = ActionExecutor(context, AutoTaskRepository(context))
        val result = executor.executeStep(
            stepIndex = 0,
            type = "LOG",
            params = JSONObject().put("message", "hello {{profileName}}"),
            profile = testProfile(),
            event = AutomationEvent(type = "MANUAL")
        )
        assertEquals("OK", result.status)
        assertTrue(result.detail.contains("hello Test"))
    }

    @Test
    fun httpHandlerFailsWithoutUrl() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val executor = ActionExecutor(context, AutoTaskRepository(context))
        val result = executor.executeStep(
            stepIndex = 0,
            type = "HTTP",
            params = JSONObject(),
            profile = testProfile(),
            event = AutomationEvent(type = "MANUAL")
        )
        assertEquals("FAILED", result.status)
        assertTrue(result.detail.contains("URL"))
    }

    @Test
    fun unknownActionFailsWithoutTouchingASwitch() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val executor = ActionExecutor(context, AutoTaskRepository(context))
        val result = executor.executeStep(
            stepIndex = 3,
            type = "NOT_A_REAL_ACTION",
            params = JSONObject(),
            profile = testProfile(),
            event = AutomationEvent(type = "MANUAL")
        )
        assertEquals("FAILED", result.status)
        assertTrue(result.detail.contains("Unknown action type"))
    }

    @Test
    fun handlerTimeoutIsReportedAsFailure() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val slow = object : ActionHandler {
            override val type: String = "SLOW"
            override val timeoutMs: Long = 50L
            override suspend fun execute(request: ActionRequest): StepResult {
                delay(500L)
                return StepResult(request.stepIndex, type, "OK", "too late")
            }
        }
        val executor = ActionExecutor(
            context,
            AutoTaskRepository(context),
            ActionRegistry(listOf(slow))
        )
        val result = executor.executeStep(
            stepIndex = 0,
            type = "SLOW",
            params = JSONObject(),
            profile = testProfile(),
            event = AutomationEvent(type = "MANUAL")
        )
        assertEquals("FAILED", result.status)
        assertEquals("handler_timeout", result.detail)
    }

    @Test
    fun cancelledHandlerDoesNotComplete() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val hang = object : ActionHandler {
            override val type: String = "HANG"
            override val timeoutMs: Long = 5_000L
            override suspend fun execute(request: ActionRequest): StepResult {
                delay(5_000L)
                return StepResult(request.stepIndex, type, "OK", "should not finish")
            }
        }
        val executor = ActionExecutor(
            context,
            AutoTaskRepository(context),
            ActionRegistry(listOf(hang))
        )
        val job = async {
            executor.executeStep(
                stepIndex = 0,
                type = "HANG",
                params = JSONObject(),
                profile = testProfile(),
                event = AutomationEvent(type = "MANUAL")
            )
        }
        delay(20L)
        job.cancel()
        try {
            withTimeout(1_000L) { job.await() }
            throw AssertionError("cancelled handler should not complete")
        } catch (_: CancellationException) {
        }
    }

    @Test
    fun policyStubsFailClosedWithNotImplemented() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val executor = ActionExecutor(context, AutoTaskRepository(context))
        listOf(
            "POWER_SAVE",
            "WIFI_ACTION",
            "BLUETOOTH_ACTION",
            "AIRPLANE_MODE_ACTION",
            "HOTSPOT",
            "NFC_ACTION",
            "KILL_APP",
            "CAMERA"
        ).forEach { type ->
            val result = executor.executeStep(
                stepIndex = 0,
                type = type,
                params = JSONObject(),
                profile = testProfile(),
                event = AutomationEvent(type = "MANUAL")
            )
            assertEquals(type, "SKIPPED", result.status)
            assertEquals(type, "not_implemented", result.detail)
        }
    }

    @Test
    fun sendSmsExecuteIsSkippedWhenCapabilityIsMissing() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val executor = ActionExecutor(context, AutoTaskRepository(context))
        val result = executor.executeStep(
            stepIndex = 0,
            type = "SEND_SMS",
            params = JSONObject().put("number", "+15551212").put("text", "hi"),
            profile = testProfile(),
            event = AutomationEvent(type = "MANUAL")
        )
        assertEquals("SKIPPED", result.status)
        assertTrue(result.detail.contains("SEND_SMS"))
    }

    private fun testProfile() = AutomationProfile(
        id = "handler-test",
        name = "Test",
        isEnabled = true,
        triggerType = "MANUAL",
        triggerConfigJson = "{}",
        actionsJson = "[]"
    )
}
