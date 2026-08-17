package com.example.engine.actions.handlers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import com.example.engine.CapabilityProvider
import com.example.engine.StepResult
import com.example.engine.actions.ActionHandler
import com.example.engine.actions.ActionRequest
import com.example.engine.actions.capabilityBlocked
import org.json.JSONObject

class LaunchAppActionHandler : ActionHandler {
    override val type: String = "LAUNCH_APP"

    override suspend fun execute(request: ActionRequest): StepResult {
        val pkg = request.params.optString("packageName", "")
        if (pkg.isBlank()) {
            return StepResult(request.stepIndex, type, "FAILED", "packageName parameter missing")
        }
        val launchIntent = request.context.packageManager.getLaunchIntentForPackage(pkg)
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            request.context.startActivity(launchIntent)
            StepResult(request.stepIndex, type, "OK", "Launched package $pkg")
        } else {
            StepResult(request.stepIndex, type, "FAILED", "App package $pkg not installed")
        }
    }
}

class OpenSettingsActionHandler : ActionHandler {
    override val type: String = "OPEN_SETTINGS"

    override suspend fun execute(request: ActionRequest): StepResult {
        val screen = request.params.optString("screen", "").lowercase()
        val intentAction = when (screen) {
            "wifi" -> android.provider.Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> android.provider.Settings.ACTION_BLUETOOTH_SETTINGS
            "accessibility" -> android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS
            "battery" -> android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS
            "display" -> android.provider.Settings.ACTION_DISPLAY_SETTINGS
            else -> android.provider.Settings.ACTION_SETTINGS
        }
        val settingsIntent = Intent(intentAction).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        request.context.startActivity(settingsIntent)
        return StepResult(request.stepIndex, type, "OK", "Opened settings: $screen")
    }
}

class FlashlightActionHandler : ActionHandler {
    override val type: String = "FLASHLIGHT"

    override fun capabilityDenial(context: Context, params: JSONObject): String? {
        val granted = CapabilityProvider.permissionSummary(context)["camera_granted"] == true
        return if (granted) null else capabilityBlocked("FLASHLIGHT", "android.permission.CAMERA not granted")
    }

    override suspend fun execute(request: ActionRequest): StepResult {
        val on = request.params.optBoolean("on", true)
        val cameraManager = request.context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull()
        return if (cameraId != null) {
            cameraManager.setTorchMode(cameraId, on)
            StepResult(request.stepIndex, type, "OK", "Flashlight set to $on")
        } else {
            StepResult(request.stepIndex, type, "FAILED", "Camera flashlight not found")
        }
    }
}

class ClipboardActionHandler : ActionHandler {
    override val type: String = "CLIPBOARD"

    override suspend fun execute(request: ActionRequest): StepResult {
        val textToCopy = request.substitute(request.params.optString("text", ""))
        val clipboard = request.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("AutoTask", textToCopy))
        return StepResult(request.stepIndex, type, "OK", "Copied to clipboard: $textToCopy")
    }
}
