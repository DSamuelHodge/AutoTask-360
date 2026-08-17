package com.example.engine.actions.handlers

import android.content.Context
import com.example.engine.CapabilityProvider
import com.example.engine.StepResult
import com.example.engine.actions.ActionHandler
import com.example.engine.actions.ActionRequest
import com.example.engine.actions.capabilityBlocked
import org.json.JSONObject

class CameraActionHandler : ActionHandler {
    override val type: String = "CAMERA"

    override fun capabilityDenial(context: Context, params: JSONObject): String? {
        val granted = CapabilityProvider.permissionSummary(context)["camera_granted"] == true
        return if (granted) null else capabilityBlocked("CAMERA", "android.permission.CAMERA not granted")
    }

    override suspend fun execute(request: ActionRequest): StepResult {
        return StepResult(request.stepIndex, type, "OK", "Action $type dispatched (system policy level)")
    }
}
