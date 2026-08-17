package com.example.engine.actions.handlers

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import com.example.engine.CapabilityProvider
import com.example.engine.StepResult
import com.example.engine.actions.ActionHandler
import com.example.engine.actions.ActionRequest
import com.example.engine.actions.capabilityBlocked
import org.json.JSONObject

class AudioActionHandler : ActionHandler {
    override val type: String = "AUDIO"

    override fun capabilityDenial(context: Context, params: JSONObject): String? {
        if (!params.optString("ringerMode").equals("silent", ignoreCase = true)) return null
        val ready = CapabilityProvider.permissionSummary(context)["dnd_ready"] == true
        return if (ready) null else capabilityBlocked("AUDIO:silent", "DND access required for ringerMode=silent")
    }

    override suspend fun execute(request: ActionRequest): StepResult {
        val modeStr = request.params.optString("ringerMode", "normal").lowercase()
        val audioManager = request.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val targetMode = when (modeStr) {
            "silent" -> AudioManager.RINGER_MODE_SILENT
            "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        if (targetMode == AudioManager.RINGER_MODE_SILENT &&
            !CapabilityProvider.isNotificationPolicyAccessGranted(request.context)
        ) {
            return StepResult(request.stepIndex, type, "SKIPPED", "Notification policy access missing for AUDIO ringerMode=silent")
        }
        try {
            audioManager.ringerMode = targetMode
        } catch (e: SecurityException) {
            return StepResult(request.stepIndex, type, "SKIPPED", "Audio mode change blocked by Android policy: ${e.localizedMessage}")
        }
        if (request.params.has("volume")) {
            val vol = request.params.optInt("volume", 7)
            val stream = when (request.params.optString("stream", "ring").lowercase()) {
                "media" -> AudioManager.STREAM_MUSIC
                "alarm" -> AudioManager.STREAM_ALARM
                "notification" -> AudioManager.STREAM_NOTIFICATION
                else -> AudioManager.STREAM_RING
            }
            try {
                audioManager.setStreamVolume(stream, vol.coerceIn(0, audioManager.getStreamMaxVolume(stream)), 0)
            } catch (_: Exception) {
            }
        }
        return StepResult(request.stepIndex, type, "OK", "Audio adjusted (ringerMode=$modeStr)")
    }
}

class DndActionHandler : ActionHandler {
    override val type: String = "DND"

    override fun capabilityDenial(context: Context, params: JSONObject): String? {
        val ready = CapabilityProvider.permissionSummary(context)["dnd_ready"] == true
        return if (ready) null else capabilityBlocked("DND", "Do Not Disturb access not granted")
    }

    override suspend fun execute(request: ActionRequest): StepResult {
        val enabled = request.params.optBoolean("enabled", true)
        if (!CapabilityProvider.isNotificationPolicyAccessGranted(request.context)) {
            return StepResult(
                request.stepIndex,
                type,
                "SKIPPED",
                "Notification policy access missing for DND; grant special access or provision via device-owner/Dhizuku policy"
            )
        }
        val nm = request.context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val policy = request.params.optString("policy", "priority")
        nm.setInterruptionFilter(dndFilterFor(enabled, policy))
        return StepResult(request.stepIndex, type, "OK", "DND set to $enabled ($policy)")
    }

    private fun dndFilterFor(enabled: Boolean, policy: String): Int {
        if (!enabled) return NotificationManager.INTERRUPTION_FILTER_ALL
        return when (policy.lowercase()) {
            "all" -> NotificationManager.INTERRUPTION_FILTER_ALL
            "none" -> NotificationManager.INTERRUPTION_FILTER_NONE
            "alarms" -> NotificationManager.INTERRUPTION_FILTER_ALARMS
            else -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
        }
    }
}

class BrightnessActionHandler : ActionHandler {
    override val type: String = "BRIGHTNESS"

    override fun capabilityDenial(context: Context, params: JSONObject): String? =
        writeSettingsDenial(context, type)

    override suspend fun execute(request: ActionRequest): StepResult {
        val level = request.params.optInt("level", 128)
        return try {
            android.provider.Settings.System.putInt(
                request.context.contentResolver,
                android.provider.Settings.System.SCREEN_BRIGHTNESS,
                level.coerceIn(0, 255)
            )
            StepResult(request.stepIndex, type, "OK", "Brightness set to $level")
        } catch (e: Exception) {
            StepResult(request.stepIndex, type, "FAILED", "Write settings permission missing: ${e.message}")
        }
    }
}

class ScreenTimeoutActionHandler : ActionHandler {
    override val type: String = "SCREEN_TIMEOUT"

    override fun capabilityDenial(context: Context, params: JSONObject): String? =
        writeSettingsDenial(context, type)

    override suspend fun execute(request: ActionRequest): StepResult {
        val sec = request.params.optInt("seconds", 30)
        return try {
            android.provider.Settings.System.putInt(
                request.context.contentResolver,
                android.provider.Settings.System.SCREEN_OFF_TIMEOUT,
                sec * 1000
            )
            StepResult(request.stepIndex, type, "OK", "Screen timeout set to ${sec}s")
        } catch (e: Exception) {
            StepResult(request.stepIndex, type, "FAILED", "Write settings permission missing: ${e.message}")
        }
    }
}

class RotationActionHandler : ActionHandler {
    override val type: String = "ROTATION"

    override fun capabilityDenial(context: Context, params: JSONObject): String? =
        writeSettingsDenial(context, type)

    override suspend fun execute(request: ActionRequest): StepResult {
        val auto = request.params.optBoolean("auto", true)
        return try {
            android.provider.Settings.System.putInt(
                request.context.contentResolver,
                android.provider.Settings.System.ACCELEROMETER_ROTATION,
                if (auto) 1 else 0
            )
            StepResult(request.stepIndex, type, "OK", "Auto-rotation set to $auto")
        } catch (e: Exception) {
            StepResult(request.stepIndex, type, "FAILED", "Write settings permission missing: ${e.message}")
        }
    }
}

class PolicyStubActionHandler(override val type: String) : ActionHandler {
    override suspend fun execute(request: ActionRequest): StepResult {
        return StepResult(request.stepIndex, type, "OK", "Action $type dispatched (system policy level)")
    }
}

private fun writeSettingsDenial(context: Context, type: String): String? {
    val ready = CapabilityProvider.permissionSummary(context)["device_settings_ready"] == true
    return if (ready) null else capabilityBlocked(type, "WRITE_SETTINGS (modify system settings) not granted")
}
