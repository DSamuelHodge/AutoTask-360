package com.example.server

import org.json.JSONObject

data class ParsedEventRequest(
    val triggerType: String,
    val payload: Map<String, Any?>,
    val targetProfileId: String?,
    val dryRun: Boolean,
    val eventId: String? = null,
    val source: String = "api",
    val occurredAt: Long? = null,
    val dedupeKey: String? = null,
    val correlationId: String? = null,
    val idempotencyKey: String? = null
)

object EventRequestParser {
    fun parse(json: JSONObject): ParsedEventRequest {
        val triggerType = firstNonBlank(
            json.optString("type", ""),
            json.optString("triggerType", ""),
            json.optString("trigger_type", "")
        )?.uppercase() ?: "MANUAL"

        val payloadObj = json.optJSONObject("payload") ?: JSONObject()
        val payloadMap = mutableMapOf<String, Any?>()
        payloadObj.keys().forEach { key ->
            val normalizedKey = if (key == "profile_id") "profileId" else key
            payloadMap[normalizedKey] = payloadObj.get(key)
        }

        val targetProfileId = firstNonBlank(
            json.optString("profileId", ""),
            json.optString("profile_id", ""),
            payloadMap["profileId"]?.toString()
        )
        if (targetProfileId != null) {
            payloadMap["profileId"] = targetProfileId
        }

        val occurredAt = when {
            json.has("occurredAt") -> json.optLong("occurredAt")
            json.has("occurred_at") -> json.optLong("occurred_at")
            else -> null
        }

        return ParsedEventRequest(
            triggerType = triggerType,
            payload = payloadMap,
            targetProfileId = targetProfileId,
            dryRun = json.optBoolean("dryRun", json.optBoolean("dry_run", false)),
            eventId = firstNonBlank(json.optString("eventId", ""), json.optString("event_id", "")),
            source = firstNonBlank(json.optString("source", "")) ?: "api",
            occurredAt = occurredAt,
            dedupeKey = firstNonBlank(json.optString("dedupeKey", ""), json.optString("dedupe_key", "")),
            correlationId = firstNonBlank(json.optString("correlationId", ""), json.optString("correlation_id", "")),
            idempotencyKey = firstNonBlank(
                json.optString("idempotencyKey", ""),
                json.optString("idempotency_key", "")
            )
        )
    }

    private fun firstNonBlank(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }?.trim()
    }
}
