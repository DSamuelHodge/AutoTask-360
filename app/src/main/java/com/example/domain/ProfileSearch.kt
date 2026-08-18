package com.example.domain

import com.example.data.AutomationProfile
import org.json.JSONArray

/**
 * Query for [listProfiles](AutomationCommandFacade.listProfiles).
 *
 * Blank query returns every profile (legacy dump). Any set field filters.
 * `q` / free-text is AND across tokens, ranked, and capped.
 */
data class ProfileListQuery(
    val q: String? = null,
    val id: String? = null,
    val actionType: String? = null,
    val triggerType: String? = null,
    val enabled: Boolean? = null,
    val limit: Int? = null
) {
    val isUnfiltered: Boolean
        get() = q.isNullOrBlank() &&
            id.isNullOrBlank() &&
            actionType.isNullOrBlank() &&
            triggerType.isNullOrBlank() &&
            enabled == null
}

/**
 * Client-side profile resolve. Lives in the facade so REST and MCP share it.
 *
 * Matching is tight on purpose: spoken aliases map to **one** action type
 * (sms → SEND_SMS). Situation words like "travel" or "meeting" are **not**
 * expanded to action bundles — that is what made every profile match.
 */
object ProfileSearch {
    const val DEFAULT_FILTER_LIMIT = 20
    const val MAX_LIMIT = 100

    private val ACTION_ALIASES: Map<String, List<String>> = mapOf(
        "sms" to listOf("SEND_SMS"),
        "text" to listOf("SEND_SMS"),
        "message" to listOf("SEND_SMS"),
        "url" to listOf("OPEN_URL"),
        "link" to listOf("OPEN_URL"),
        "deeplink" to listOf("OPEN_URL"),
        "browser" to listOf("OPEN_URL"),
        "call" to listOf("CALL"),
        "notify" to listOf("NOTIFICATION"),
        "notification" to listOf("NOTIFICATION"),
        "alert" to listOf("NOTIFICATION"),
        "speak" to listOf("SPEAK"),
        "tts" to listOf("SPEAK"),
        "say" to listOf("SPEAK"),
        "clipboard" to listOf("CLIPBOARD"),
        "copy" to listOf("CLIPBOARD"),
        "paste" to listOf("CLIPBOARD"),
        "launch" to listOf("LAUNCH_APP"),
        "kill" to listOf("KILL_APP"),
        "wifi" to listOf("WIFI_ACTION"),
        "bluetooth" to listOf("BLUETOOTH_ACTION"),
        "hotspot" to listOf("HOTSPOT"),
        "airplane" to listOf("AIRPLANE_MODE_ACTION"),
        "flight" to listOf("AIRPLANE_MODE_ACTION"),
        "nfc" to listOf("NFC_ACTION"),
        "flashlight" to listOf("FLASHLIGHT"),
        "torch" to listOf("FLASHLIGHT"),
        "vibrate" to listOf("VIBRATE"),
        "dnd" to listOf("DND"),
        "brightness" to listOf("BRIGHTNESS"),
        "timeout" to listOf("SCREEN_TIMEOUT"),
        "settings" to listOf("OPEN_SETTINGS"),
        "file" to listOf("WRITE_FILE", "READ_FILE"),
        "notes" to listOf("WRITE_FILE", "READ_FILE"),
        "toast" to listOf("TOAST"),
        "http" to listOf("HTTP")
    )

    fun filter(
        profiles: List<AutomationProfile>,
        query: ProfileListQuery
    ): List<AutomationProfile> {
        if (query.isUnfiltered) return profiles

        val idFilter = query.id?.trim()?.takeIf { it.isNotEmpty() }
        val actionFilter = query.actionType?.trim()?.takeIf { it.isNotEmpty() }
        val triggerFilter = query.triggerType?.trim()?.takeIf { it.isNotEmpty() }
        val tokens = tokenize(query.q)

        val scored = ArrayList<Pair<AutomationProfile, Int>>(profiles.size)
        for (profile in profiles) {
            if (idFilter != null && !profile.id.equals(idFilter, ignoreCase = true)) continue
            if (triggerFilter != null && !profile.triggerType.equals(triggerFilter, ignoreCase = true)) continue
            if (query.enabled != null && profile.isEnabled != query.enabled) continue
            val types = actionTypes(profile)
            if (actionFilter != null && types.none { it.equals(actionFilter, ignoreCase = true) }) continue
            val score = if (tokens.isEmpty()) {
                1
            } else {
                scoreTokens(profile, types, tokens) ?: continue
            }
            scored.add(profile to score)
        }

        scored.sortWith(
            compareByDescending<Pair<AutomationProfile, Int>> { it.second }
                .thenByDescending { it.first.priority }
                .thenBy { it.first.id }
        )

        val defaultLimit = if (tokens.isNotEmpty()) DEFAULT_FILTER_LIMIT else scored.size
        val limit = (query.limit ?: defaultLimit).coerceIn(1, MAX_LIMIT)
        return scored.take(limit).map { it.first }
    }

    internal fun tokenize(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotEmpty() }
    }

    internal fun actionTypes(profile: AutomationProfile): List<String> {
        val raw = profile.actionsJson.trim()
        if (raw.isEmpty()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val type = arr.optJSONObject(i)?.optString("type")?.trim().orEmpty()
                    if (type.isNotEmpty()) add(type)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun scoreTokens(
        profile: AutomationProfile,
        types: List<String>,
        tokens: List<String>
    ): Int? {
        var total = 0
        for (token in tokens) {
            val part = tokenScore(profile, types, token)
            if (part <= 0) return null
            total += part
        }
        return total
    }

    private fun tokenScore(
        profile: AutomationProfile,
        types: List<String>,
        token: String
    ): Int {
        var score = 0
        val id = profile.id.lowercase()
        val name = profile.name.lowercase()
        val desc = profile.description.lowercase()
        val trigger = profile.triggerType.lowercase()
        if (id == token || id == "cos-$token") score += 100
        if (id.contains(token)) score += 40
        if (name.contains(token)) score += 30
        if (desc.contains(token)) score += 15
        if (trigger == token || trigger.contains(token)) score += 25
        val typesLower = types.map { it.lowercase() }
        if (typesLower.any { it == token || it.contains(token) }) score += 50
        val aliases = ACTION_ALIASES[token].orEmpty()
        if (aliases.any { alias -> types.any { it.equals(alias, ignoreCase = true) } }) {
            score += 45
        }
        return score
    }
}
