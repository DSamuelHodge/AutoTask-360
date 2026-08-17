package com.example.engine.actions.handlers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import com.example.engine.CapabilityProvider
import com.example.engine.StepResult
import com.example.engine.actions.ActionHandler
import com.example.engine.actions.ActionRequest
import com.example.engine.actions.capabilityBlocked
import org.json.JSONObject

class SendSmsActionHandler : ActionHandler {
    override val type: String = "SEND_SMS"

    override fun capabilityDenial(context: Context, params: JSONObject): String? {
        val granted = CapabilityProvider.permissionSummary(context)["send_sms_granted"] == true
        return if (granted) null else capabilityBlocked("SEND_SMS", "android.permission.SEND_SMS not granted")
    }

    override suspend fun execute(request: ActionRequest): StepResult {
        val number = request.substitute(request.params.optString("number", ""))
        val text = request.substitute(request.params.optString("text", ""))
        if (number.isBlank() || text.isBlank()) {
            return StepResult(request.stepIndex, type, "FAILED", "SMS number and text required")
        }
        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                request.context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(number, null, text, null, null)
            StepResult(request.stepIndex, type, "OK", "SMS sent to $number")
        } catch (e: Exception) {
            StepResult(request.stepIndex, type, "FAILED", "SMS send failed: ${e.localizedMessage}")
        }
    }
}

class CallActionHandler : ActionHandler {
    override val type: String = "CALL"

    override fun capabilityDenial(context: Context, params: JSONObject): String? {
        val granted = CapabilityProvider.permissionSummary(context)["call_phone_granted"] == true
        return if (granted) null else capabilityBlocked("CALL", "android.permission.CALL_PHONE not granted")
    }

    override suspend fun execute(request: ActionRequest): StepResult {
        val num = request.substitute(request.params.optString("number", ""))
        if (num.isBlank()) {
            return StepResult(request.stepIndex, type, "FAILED", "Call phone number is required")
        }
        val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        request.context.startActivity(callIntent)
        return StepResult(request.stepIndex, type, "OK", "Dialer opened for $num")
    }
}

class OpenUrlActionHandler : ActionHandler {
    override val type: String = "OPEN_URL"

    override suspend fun execute(request: ActionRequest): StepResult {
        val url = request.substitute(request.params.optString("url", ""))
        if (url.isBlank()) {
            return StepResult(request.stepIndex, type, "FAILED", "URL is required")
        }
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        request.context.startActivity(browserIntent)
        return StepResult(request.stepIndex, type, "OK", "Opened URL: $url")
    }
}

class SendIntentActionHandler : ActionHandler {
    override val type: String = "SEND_INTENT"

    override suspend fun execute(request: ActionRequest): StepResult {
        val rawData = request.params.optString("data", "")
        val scheme = request.params.optString("scheme", "")
        val target = request.params.optString("target", "")
        val data = when {
            rawData.isNotBlank() -> rawData
            scheme.isNotBlank() -> "$scheme://$target"
            else -> ""
        }
        if (data.isBlank()) {
            return StepResult(request.stepIndex, type, "FAILED", "SEND_INTENT requires 'data' or 'scheme'+'target'")
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(data)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        request.context.startActivity(intent)
        return StepResult(request.stepIndex, type, "OK", "Sent intent: $data")
    }
}
