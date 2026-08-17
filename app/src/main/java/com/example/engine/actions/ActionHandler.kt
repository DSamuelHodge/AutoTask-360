package com.example.engine.actions

import android.content.Context
import com.example.data.AutomationProfile
import com.example.data.AutoTaskRepository
import com.example.domain.AutomationSchema
import com.example.engine.AutomationEvent
import com.example.engine.StepResult
import okhttp3.OkHttpClient
import org.json.JSONObject

data class ActionMetadata(
    val type: String,
    val risk: String,
    val autonomy: String,
    val requirements: List<String>,
    val timeoutMs: Long
)

class ActionServices(
    val httpClient: Lazy<OkHttpClient>,
    val tts: TextToSpeechController
)

data class ActionRequest(
    val context: Context,
    val repository: AutoTaskRepository,
    val profile: AutomationProfile,
    val event: AutomationEvent,
    val stepIndex: Int,
    val type: String,
    val params: JSONObject,
    val substitute: (String) -> String,
    val services: ActionServices
)

/**
 * One device or flow capability. New actions register a handler; they do not
 * add a branch to [com.example.engine.ActionExecutor].
 */
interface ActionHandler {
    val type: String
    val timeoutMs: Long
        get() = 30_000L

    fun metadata(): ActionMetadata {
        val descriptor = AutomationSchema.action(type)
        return ActionMetadata(
            type = type,
            risk = descriptor?.risk ?: "low",
            autonomy = descriptor?.autonomy ?: "autonomous_allowed",
            requirements = descriptor?.requirements ?: emptyList(),
            timeoutMs = timeoutMs
        )
    }

    fun capabilityDenial(context: Context, params: JSONObject): String? = null

    suspend fun execute(request: ActionRequest): StepResult
}

fun capabilityBlocked(action: String, reason: String): String =
    "capability '$action' blocked: $reason"
