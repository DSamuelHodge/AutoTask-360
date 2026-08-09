package com.example.server

import org.json.JSONObject

data class ParsedEventRequest(
    val triggerType: String,
    val payload: Map<String, Any?>,
    val targetProfileId: String?,
    val dryRun: Boolean
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

        return ParsedEventRequest(
            triggerType = triggerType,
            payload = payloadMap,
            targetProfileId = targetProfileId,
            dryRun = json.optBoolean("dryRun", json.optBoolean("dry_run", false))
        )
    }

    private fun firstNonBlank(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }?.trim()
    }
}
