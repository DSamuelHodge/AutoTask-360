package com.example.engine.actions

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AutomationProfile
import com.example.data.AutoTaskRepository
import com.example.engine.AutomationEvent
import com.example.engine.actions.handlers.SendSmsActionHandler
import com.example.engine.actions.handlers.SmsSendResult
import com.example.engine.actions.handlers.SmsTransport
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SendSmsActionHandlerTest {

    @Test
    fun missingNumberOrTextFailsWithoutSending() = runBlocking {
        val sent = mutableListOf<Pair<String, String>>()
        val handler = SendSmsActionHandler { _, number, text ->
            sent.add(number to text)
            SmsSendResult.Sent
        }
        val result = handler.execute(request(number = "", text = "hi", payload = emptyMap()))
        assertEquals("FAILED", result.status)
        assertTrue(result.detail.contains("required"))
        assertTrue(sent.isEmpty())
    }

    @Test
    fun payloadAliasesFillNumberAndText() = runBlocking {
        val sent = mutableListOf<Pair<String, String>>()
        val handler = SendSmsActionHandler { _, number, text ->
            sent.add(number to text)
            SmsSendResult.Sent
        }
        val result = handler.execute(
            request(
                params = JSONObject(),
                payload = mapOf("to" to "+15551212", "message" to "hello")
            )
        )
        assertEquals("OK", result.status)
        assertEquals(listOf("+15551212" to "hello"), sent)
        assertTrue(result.detail.contains("+15551212"))
    }

    @Test
    fun radioErrorIsFailedAndDoesNotLookLikeSuccess() = runBlocking {
        val handler = SendSmsActionHandler { _, _, _ ->
            SmsSendResult.RadioError(Activity.RESULT_CANCELED, "generic_failure")
        }
        val result = handler.execute(request(number = "+15551212", text = "hi"))
        assertEquals("FAILED", result.status)
        assertTrue(result.detail.startsWith("sms_send_failed:"))
    }

    @Test
    fun radioTimeoutIsFailed() = runBlocking {
        val handler = SendSmsActionHandler { _, _, _ -> SmsSendResult.Timeout }
        val result = handler.execute(request(number = "+15551212", text = "hi"))
        assertEquals("FAILED", result.status)
        assertTrue(result.detail.contains("sms_radio_timeout"))
    }

    @Test
    fun interpretMapsOkAndNoService() {
        assertEquals(SmsSendResult.Sent, SmsTransport.interpret(Activity.RESULT_OK))
        val error = SmsTransport.interpret(android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE)
        assertTrue(error is SmsSendResult.RadioError)
        assertEquals("no_service", (error as SmsSendResult.RadioError).reason)
    }

    private fun request(
        number: String = "{{number}}",
        text: String = "{{text}}",
        params: JSONObject = JSONObject().put("number", number).put("text", text),
        payload: Map<String, Any?> = mapOf("number" to "+15551212", "text" to "hi")
    ): ActionRequest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val profile = AutomationProfile(
            id = "cos-sms-send",
            name = "CoS SMS Send",
            description = "",
            isEnabled = true,
            triggerType = "MANUAL",
            triggerConfigJson = "{}",
            conditionsJson = "{}",
            actionsJson = """[{"type":"SEND_SMS","params":{"number":"{{number}}","text":"{{text}}"}}]""",
            cooldownMs = 0L,
            priority = 1,
            createdAt = 0L,
            updatedAt = 0L
        )
        val event = AutomationEvent(type = "MANUAL", payload = payload)
        return ActionRequest(
            context = context,
            repository = AutoTaskRepository(context),
            profile = profile,
            event = event,
            stepIndex = 0,
            type = "SEND_SMS",
            params = params,
            substitute = { ActionSupport.substitute(it, profile, event) },
            services = ActionServices(httpClient = lazy { okhttp3.OkHttpClient() }, tts = TextToSpeechController(context))
        )
    }
}
