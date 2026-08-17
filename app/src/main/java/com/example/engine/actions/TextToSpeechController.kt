package com.example.engine.actions

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.util.Locale

class TextToSpeechController(context: Context) {
    private var tts: TextToSpeech? = null
    @Volatile
    var ready: Boolean = false
        private set

    init {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.US
                    ready = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun waitUntilReady(timeoutMs: Long = 2000L) {
        if (ready) return
        try {
            withTimeout(timeoutMs) {
                while (!ready) {
                    delay(50L)
                }
            }
        } catch (_: TimeoutCancellationException) {
        }
    }

    fun speak(text: String): Boolean {
        val engine = tts
        if (!ready || engine == null) return false
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "AutoTaskTTS")
        return true
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
