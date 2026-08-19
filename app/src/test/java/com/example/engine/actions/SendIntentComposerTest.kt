package com.example.engine.actions

import android.content.Intent
import com.example.engine.actions.handlers.SendIntentComposer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SendIntentComposerTest {

    @Test
    fun viewDataUriStillWorks() {
        val intent = SendIntentComposer.compose(
            JSONObject().put("data", "https://example.com")
        )
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://example.com", intent.dataString)
    }

    @Test
    fun googleVoiceSharePinsPackageAndText() {
        val intent = SendIntentComposer.compose(
            JSONObject()
                .put("action", Intent.ACTION_SEND)
                .put("package", "com.google.android.apps.googlevoice")
                .put("mimeType", "text/plain")
                .put("extraText", "{{text}}")
                .put("extraPhone", "{{number}}"),
            substitute = { raw ->
                when (raw) {
                    "{{text}}" -> "joke"
                    "{{number}}" -> "+16147079498"
                    else -> raw
                }
            }
        )
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("com.google.android.apps.googlevoice", intent.`package`)
        assertEquals("text/plain", intent.type)
        assertEquals("joke", intent.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("+16147079498", intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun sendWithoutTextOrDataFails() {
        SendIntentComposer.compose(
            JSONObject()
                .put("action", Intent.ACTION_SEND)
                .put("package", "com.google.android.apps.googlevoice")
                .put("mimeType", "text/plain")
        )
    }
}
