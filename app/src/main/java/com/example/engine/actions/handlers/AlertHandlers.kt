package com.example.engine.actions.handlers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.engine.CapabilityProvider
import com.example.engine.StepResult
import com.example.engine.actions.ActionHandler
import com.example.engine.actions.ActionRequest
import com.example.engine.actions.capabilityBlocked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class NotificationActionHandler : ActionHandler {
    override val type: String = "NOTIFICATION"

    override fun capabilityDenial(context: Context, params: JSONObject): String? {
        val granted = CapabilityProvider.permissionSummary(context)["post_notifications_granted"] == true
        return if (granted) null else capabilityBlocked("NOTIFICATION", "android.permission.POST_NOTIFICATIONS not granted")
    }

    override suspend fun execute(request: ActionRequest): StepResult {
        val title = request.substitute(request.params.optString("title", "AutoTask Alert"))
        val text = request.substitute(request.params.optString("text", "Automation triggered"))
        val priority = request.params.optString("priority", "normal")
        showNotification(request.context, title, text, priority)
        return StepResult(request.stepIndex, type, "OK", "Notification posted: $title")
    }

    private fun showNotification(context: Context, title: String, text: String, priority: String) {
        val channelId = "autotask_execution_channel"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "AutoTask Action Notifications", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val importance = when (priority.lowercase()) {
            "high" -> NotificationCompat.PRIORITY_HIGH
            "low" -> NotificationCompat.PRIORITY_LOW
            else -> NotificationCompat.PRIORITY_DEFAULT
        }
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(importance)
            .setAutoCancel(true)
        nm.notify((System.currentTimeMillis() % 100000).toInt(), builder.build())
    }
}

class SpeakActionHandler : ActionHandler {
    override val type: String = "SPEAK"
    override val timeoutMs: Long = 5_000L

    override suspend fun execute(request: ActionRequest): StepResult {
        val textToSpeak = request.substitute(request.params.optString("text", "AutoTask automation executed"))
        request.services.tts.waitUntilReady()
        return if (request.services.tts.speak(textToSpeak)) {
            StepResult(request.stepIndex, type, "OK", "Spoke: $textToSpeak")
        } else {
            StepResult(request.stepIndex, type, "SKIPPED", "TextToSpeech engine initializing or unavailable")
        }
    }
}

class ToastActionHandler : ActionHandler {
    override val type: String = "TOAST"

    override suspend fun execute(request: ActionRequest): StepResult {
        val toastText = request.substitute(request.params.optString("text", "AutoTask Triggered"))
        val duration = if (request.params.optString("duration", "short") == "long") {
            Toast.LENGTH_LONG
        } else {
            Toast.LENGTH_SHORT
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(request.context.applicationContext, toastText, duration).show()
        }
        return StepResult(request.stepIndex, type, "OK", "Toast shown: $toastText")
    }
}

class VibrateActionHandler : ActionHandler {
    override val type: String = "VIBRATE"

    override suspend fun execute(request: ActionRequest): StepResult {
        val durationMs = request.params.optLong("durationMs", 500L)
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = request.context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            request.context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!vibrator.hasVibrator()) {
            return StepResult(request.stepIndex, type, "SKIPPED", "No vibration hardware present")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
        return StepResult(request.stepIndex, type, "OK", "Vibrated for ${durationMs}ms")
    }
}
