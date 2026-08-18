package com.example.engine

import android.content.Context
import com.example.data.AutomationProfile
import com.example.data.AutoTaskRepository
import com.example.domain.EffectRecord
import com.example.domain.StepStatuses
import com.example.engine.actions.ActionRegistry
import com.example.engine.actions.ActionRequest
import com.example.engine.actions.ActionServices
import com.example.engine.actions.ActionSupport
import com.example.engine.actions.TextToSpeechController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class StepResult(
    val step: Int,
    val type: String,
    val status: String,
    val detail: String = ""
)

/**
 * Compatibility coordinator for action execution. Device behavior lives in
 * [com.example.engine.actions.ActionHandler] implementations registered on
 * [ActionRegistry]. Adding an action means registering a handler, not editing
 * a central `when` switch here.
 */
class ActionExecutor(
    private val context: Context,
    private val repository: AutoTaskRepository,
    private val registry: ActionRegistry = ActionRegistry.standard(),
    private val ledger: RunStore? = null,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        fun finalStatusFor(results: List<StepResult>): String {
            if (results.any { it.status == "INDETERMINATE" }) return "INDETERMINATE"
            val hasFailures = results.any { it.status != "OK" }
            return when {
                hasFailures && results.any { it.status == "OK" } -> "PARTIAL"
                hasFailures -> "FAILED"
                else -> "SUCCESS"
            }
        }
    }

    private val tts = TextToSpeechController(context.applicationContext)
    private val httpClient = lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }
    private val services = ActionServices(httpClient = httpClient, tts = tts)

    suspend fun executeActions(
        profile: AutomationProfile,
        event: AutomationEvent
    ): Pair<String, List<StepResult>> = withContext(Dispatchers.IO) {
        val results = mutableListOf<StepResult>()

        if (profile.actionsJson.isBlank() || profile.actionsJson.trim() == "[]") {
            return@withContext Pair("SUCCESS", emptyList())
        }

        try {
            val jsonArray = JSONArray(profile.actionsJson)
            for (i in 0 until jsonArray.length()) {
                val actionObj = jsonArray.getJSONObject(i)
                val type = actionObj.optString("type", "").uppercase()
                val paramsObj = actionObj.optJSONObject("params") ?: JSONObject()
                results.add(executeStep(i, type, paramsObj, profile, event))
            }
        } catch (e: Exception) {
            results.add(StepResult(0, "PARSE_ERROR", "FAILED", e.localizedMessage ?: "Invalid actions JSON"))
            return@withContext Pair("FAILED", results)
        }

        Pair(finalStatusFor(results), results)
    }

    suspend fun executeStep(
        stepIndex: Int,
        type: String,
        params: JSONObject,
        profile: AutomationProfile,
        event: AutomationEvent,
        effectId: String? = null
    ): StepResult {
        val handler = registry.handler(type)
            ?: return StepResult(stepIndex, type, "FAILED", "Unknown action type $type")
        handler.capabilityDenial(context, params)?.let { reason ->
            return StepResult(stepIndex, type, "SKIPPED", reason)
        }
        if (handler.dedupesByEffectId && !effectId.isNullOrBlank()) {
            val remembered = ledger?.getEffect(effectId)
            if (remembered != null && remembered.status == StepStatuses.OK) {
                return StepResult(stepIndex, type, StepStatuses.OK, remembered.detail)
            }
        }
        val request = ActionRequest(
            context = context,
            repository = repository,
            profile = profile,
            event = event,
            stepIndex = stepIndex,
            type = type,
            params = params,
            substitute = { ActionSupport.substitute(it, profile, event) },
            services = services,
            effectId = effectId
        )
        val result = try {
            withTimeout(handler.timeoutMs) {
                handler.execute(request)
            }
        } catch (_: TimeoutCancellationException) {
            StepResult(stepIndex, type, "FAILED", "handler_timeout")
        } catch (e: Exception) {
            StepResult(stepIndex, type, "FAILED", e.localizedMessage ?: "Action execution exception")
        }
        if (handler.dedupesByEffectId && !effectId.isNullOrBlank() && result.status == StepStatuses.OK) {
            ledger?.putEffect(
                EffectRecord(
                    effectId = effectId,
                    type = type,
                    status = StepStatuses.OK,
                    detail = result.detail,
                    stepIndex = stepIndex,
                    completedAt = clock()
                )
            )
        }
        return result
    }

    fun shutdown() {
        tts.shutdown()
    }
}
