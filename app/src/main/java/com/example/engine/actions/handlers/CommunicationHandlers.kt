package com.example.engine.actions.handlers

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import com.example.engine.CapabilityProvider
import com.example.engine.StepResult
import com.example.engine.actions.ActionHandler
import com.example.engine.actions.ActionRequest
import com.example.engine.actions.capabilityBlocked
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Sends SMS the same way Termux:API does (default subscription + multipart)
 * and waits for the sent-intent. Enqueue-and-return-OK was lying: G63 runs
 * reported SUCCESS in ~150ms with no radio ack.
 *
 * [send] is injectable for tests. Production waits up to [sentTimeoutMs]
 * for the modem result. Radio failures are not [com.example.domain.StepRetryPolicy]
 * retryable (see detail prefixes `sms_`).
 */
class SendSmsActionHandler(
    private val send: suspend (Context, String, String) -> SmsSendResult = { context, number, text ->
        withContext(Dispatchers.IO) {
            SmsTransport.sendAndAwait(context, number, text, SENT_TIMEOUT_MS)
        }
    }
) : ActionHandler {
    override val type: String = "SEND_SMS"

    override fun capabilityDenial(context: Context, params: JSONObject): String? {
        val granted = CapabilityProvider.permissionSummary(context)["send_sms_granted"] == true
        return if (granted) null else capabilityBlocked("SEND_SMS", "android.permission.SEND_SMS not granted")
    }

    override suspend fun execute(request: ActionRequest): StepResult {
        val number = firstFilled(
            request,
            paramKeys = listOf("number", "to", "phone"),
            payloadKeys = listOf("number", "to", "phone")
        )
        val text = firstFilled(
            request,
            paramKeys = listOf("text", "message", "body"),
            payloadKeys = listOf("text", "message", "body", "smsBody")
        )
        if (number.isBlank() || text.isBlank()) {
            return StepResult(request.stepIndex, type, "FAILED", "SMS number and text required")
        }
        return try {
            when (val result = send(request.context, number, text)) {
                is SmsSendResult.Sent ->
                    StepResult(request.stepIndex, type, "OK", "SMS sent to $number")
                is SmsSendResult.RadioError ->
                    StepResult(
                        request.stepIndex,
                        type,
                        "FAILED",
                        "sms_send_failed:${result.code} ${result.reason} ($number)"
                    )
                is SmsSendResult.Timeout ->
                    StepResult(
                        request.stepIndex,
                        type,
                        "FAILED",
                        "sms_radio_timeout ($number)"
                    )
            }
        } catch (e: Exception) {
            StepResult(request.stepIndex, type, "FAILED", "sms_send_failed:${e.localizedMessage}")
        }
    }

    private fun firstFilled(
        request: ActionRequest,
        paramKeys: List<String>,
        payloadKeys: List<String>
    ): String {
        for (key in paramKeys) {
            val value = request.substitute(request.params.optString(key, "")).trim()
            if (value.isNotBlank()) return value
        }
        val payload = request.event.payload
        for (key in payloadKeys) {
            val value = payload[key]?.toString()?.trim().orEmpty()
            if (value.isNotBlank()) return value
        }
        return ""
    }

    companion object {
        const val SENT_TIMEOUT_MS = 20_000L
        const val ACTION_SMS_SENT = "com.example.autotask.SMS_SENT"
    }
}

sealed class SmsSendResult {
    data object Sent : SmsSendResult()
    data class RadioError(val code: Int, val reason: String) : SmsSendResult()
    data object Timeout : SmsSendResult()
}

internal object SmsTransport {
    fun sendAndAwait(context: Context, number: String, text: String, timeoutMs: Long): SmsSendResult {
        val app = context.applicationContext
        val token = System.nanoTime().toString()
        val action = "${SendSmsActionHandler.ACTION_SMS_SENT}.$token"
        val latch = CountDownLatch(1)
        val code = AtomicInteger(Int.MIN_VALUE)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                code.set(resultCode)
                latch.countDown()
            }
        }
        val filter = IntentFilter(action)
        if (Build.VERSION.SDK_INT >= 33) {
            app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            app.registerReceiver(receiver, filter)
        }
        return try {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    0
                }
            val sentIntent = PendingIntent.getBroadcast(
                app,
                token.hashCode(),
                Intent(action).setPackage(app.packageName),
                flags
            )
            val manager = smsManager(app)
            val parts = manager.divideMessage(text)
            if (parts.size <= 1) {
                manager.sendTextMessage(number, null, text, sentIntent, null)
            } else {
                val sentIntents = ArrayList<PendingIntent>(parts.size)
                repeat(parts.size) { sentIntents.add(sentIntent) }
                manager.sendMultipartTextMessage(number, null, parts, sentIntents, null)
            }
            val acked = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            if (!acked) return SmsSendResult.Timeout
            interpret(code.get())
        } finally {
            try {
                app.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }

    fun smsManager(context: Context): SmsManager {
        val subId = try {
            SmsManager.getDefaultSmsSubscriptionId()
        } catch (_: Exception) {
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val base = context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
            return if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                try {
                    base.createForSubscriptionId(subId)
                } catch (_: Exception) {
                    SmsManager.getDefault()
                }
            } else {
                SmsManager.getDefault()
            }
        }
        @Suppress("DEPRECATION")
        return if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            SmsManager.getSmsManagerForSubscriptionId(subId)
        } else {
            SmsManager.getDefault()
        }
    }

    fun interpret(resultCode: Int): SmsSendResult = when (resultCode) {
        Activity.RESULT_OK -> SmsSendResult.Sent
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> SmsSendResult.RadioError(resultCode, "generic_failure")
        SmsManager.RESULT_ERROR_NO_SERVICE -> SmsSendResult.RadioError(resultCode, "no_service")
        SmsManager.RESULT_ERROR_NULL_PDU -> SmsSendResult.RadioError(resultCode, "null_pdu")
        SmsManager.RESULT_ERROR_RADIO_OFF -> SmsSendResult.RadioError(resultCode, "radio_off")
        SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> SmsSendResult.RadioError(resultCode, "limit_exceeded")
        SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED -> SmsSendResult.RadioError(resultCode, "short_code_not_allowed")
        SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> SmsSendResult.RadioError(resultCode, "short_code_never_allowed")
        else -> SmsSendResult.RadioError(resultCode, "result_$resultCode")
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
        val intent = try {
            SendIntentComposer.compose(request.params, request.substitute)
        } catch (e: IllegalArgumentException) {
            return StepResult(request.stepIndex, type, "FAILED", e.message ?: "invalid SEND_INTENT")
        }
        return try {
            request.context.startActivity(intent)
            StepResult(request.stepIndex, type, "OK", "Sent intent: ${SendIntentComposer.describe(intent)}")
        } catch (e: Exception) {
            StepResult(
                request.stepIndex,
                type,
                "FAILED",
                "SEND_INTENT failed: ${e.localizedMessage ?: e.javaClass.simpleName}"
            )
        }
    }
}

internal object SendIntentComposer {
    fun compose(params: JSONObject, substitute: (String) -> String = { it }): Intent {
        val action = substitute(params.optString("action", "")).ifBlank { Intent.ACTION_VIEW }
        val pkg = substitute(params.optString("package", "")).trim()
        val mime = substitute(
            params.optString("mimeType", params.optString("type", ""))
        ).trim()
        val rawData = substitute(params.optString("data", "")).trim()
        val scheme = substitute(params.optString("scheme", "")).trim()
        val target = substitute(params.optString("target", "")).trim()
        val extraText = substitute(
            params.optString("extraText", params.optString("text", ""))
        ).trim()
        val extraPhone = substitute(
            params.optString("extraPhone", params.optString("number", ""))
        ).trim()
        val data = when {
            rawData.isNotBlank() -> rawData
            scheme.isNotBlank() -> "$scheme://$target"
            else -> ""
        }
        val isShare = action == Intent.ACTION_SEND || action == Intent.ACTION_SENDTO
        if (data.isBlank() && !isShare) {
            throw IllegalArgumentException("SEND_INTENT requires 'data' or 'scheme'+'target'")
        }
        if (isShare && extraText.isBlank() && data.isBlank()) {
            throw IllegalArgumentException("SEND_INTENT SEND/SENDTO requires extraText or data")
        }
        val intent = Intent(action)
        if (data.isNotBlank()) {
            intent.data = Uri.parse(data)
        }
        if (mime.isNotBlank()) {
            if (intent.data != null) {
                intent.setDataAndType(intent.data, mime)
            } else {
                intent.type = mime
            }
        }
        if (pkg.isNotBlank()) {
            intent.setPackage(pkg)
        }
        if (extraText.isNotBlank()) {
            intent.putExtra(Intent.EXTRA_TEXT, extraText)
            intent.putExtra("sms_body", extraText)
        }
        if (extraPhone.isNotBlank()) {
            intent.putExtra(Intent.EXTRA_PHONE_NUMBER, extraPhone)
            intent.putExtra("address", extraPhone)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }

    fun describe(intent: Intent): String {
        val pkg = intent.`package`.orEmpty()
        val data = intent.dataString.orEmpty()
        val type = intent.type.orEmpty()
        return listOf(intent.action.orEmpty(), pkg, type, data).filter { it.isNotBlank() }.joinToString(" ")
    }
}
