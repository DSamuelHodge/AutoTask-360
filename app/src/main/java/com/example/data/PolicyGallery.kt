package com.example.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * A gallery entry is a DISABLED-BY-DEFAULT policy template, distinct from an active
 * [AutomationProfile]. Gallery entries are never inserted as active profiles automatically;
 * they are only cloned into an [AutomationProfile] on explicit user/agent request via
 * [PolicyGalleryStore.cloneEntry].
 */
data class PolicyGalleryEntry(
    val id: String,
    val name: String,
    val category: String,
    val requiredCapabilities: List<String>,
    val riskClass: String,
    val cosHandoff: String,
    val description: String,
    val actionsJson: String,
    val triggerType: String,
    val disabledByDefault: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("category", category)
        put("requiredCapabilities", JSONArray(requiredCapabilities))
        put("riskClass", riskClass)
        put("cosHandoff", cosHandoff)
        put("description", description)
        put("actionsJson", actionsJson)
        put("triggerType", triggerType)
        put("disabledByDefault", disabledByDefault)
    }
}

/**
 * In-memory store of disabled-by-default CoS policy templates.
 *
 * This module is intentionally parallel to [PolicySeeder] and the active profile table.
 * Nothing here seeds the database or auto-enables anything. The only path from a gallery
 * entry to an active [AutomationProfile] is [cloneEntry], which requires an explicit call
 * and never overwrites an existing profile with the same id.
 */
object PolicyGalleryStore {

    private val entries: List<PolicyGalleryEntry> = listOf(
        PolicyGalleryEntry(
            id = "gallery-meeting-shield",
            name = "Meeting Shield",
            category = "focus",
            requiredCapabilities = listOf("READ_CALENDAR", "DND"),
            riskClass = "elevated",
            cosHandoff = "Silences interruptions and announces the active meeting on user/agent request.",
            description = "Detects calendar meetings and engages DND + silent ringer for their duration.",
            triggerType = "MEETING",
            actionsJson = """[
                {"type":"DND","params":{"enabled":true,"policy":"priority"}},
                {"type":"AUDIO","params":{"ringerMode":"silent"}},
                {"type":"NOTIFICATION","params":{"title":"Meeting Shield","text":"Meeting started: {{title}}","priority":"low"}}
            ]""".trimIndent()
        ),
        PolicyGalleryEntry(
            id = "gallery-driving-context",
            name = "Driving Context",
            category = "safety",
            requiredCapabilities = listOf("ACTIVITY_RECOGNITION", "DND", "NOTIFICATION"),
            riskClass = "medium",
            cosHandoff = "Prompts hands-free mode and suppresses non-urgent notifications while driving.",
            description = "Recognizes driving activity and switches the device into a hands-free, low-distraction state.",
            triggerType = "ACTIVITY_RECOGNITION",
            actionsJson = """[
                {"type":"DND","params":{"enabled":true,"policy":"alarms"}},
                {"type":"NOTIFICATION","params":{"title":"Driving Mode","text":"Hands-free mode active.","priority":"low"}}
            ]""".trimIndent()
        ),
        PolicyGalleryEntry(
            id = "gallery-deep-work-fortress",
            name = "Deep Work Fortress",
            category = "focus",
            requiredCapabilities = listOf("DND", "BRIGHTNESS", "SCHEDULE_EXACT_ALARM"),
            riskClass = "medium",
            cosHandoff = "Locks in a sustained focus block with DND and dimmed screen on schedule.",
            description = "Engages a timed deep-work block: DND, silent ringer, and reduced brightness on a schedule.",
            triggerType = "TIME",
            actionsJson = """[
                {"type":"DND","params":{"enabled":true,"policy":"priority"}},
                {"type":"AUDIO","params":{"ringerMode":"silent"}},
                {"type":"BRIGHTNESS","params":{"level":25,"auto":false}}
            ]""".trimIndent()
        ),
        PolicyGalleryEntry(
            id = "gallery-relationship-context-card",
            name = "Relationship Context Card",
            category = "context",
            requiredCapabilities = listOf("READ_CONTACTS", "READ_CALENDAR"),
            riskClass = "low",
            cosHandoff = "Surfaces a lightweight context card for the active contact on call/meeting start.",
            description = "Builds a read-only context card (recent events, notes) when a known contact interacts.",
            triggerType = "INCOMING_CALL",
            actionsJson = """[
                {"type":"NOTIFICATION","params":{"title":"Context Card","text":"In touch with {{contactName}}: {{lastEvent}}","priority":"normal"}}
            ]""".trimIndent()
        ),
        PolicyGalleryEntry(
            id = "gallery-morning-brief-relay",
            name = "Morning Brief Relay",
            category = "relay",
            requiredCapabilities = listOf("READ_CALENDAR", "READ_CONTACTS", "SCHEDULE_EXACT_ALARM"),
            riskClass = "low",
            cosHandoff = "Relays a morning summary (agenda, weather placeholder) at a scheduled time.",
            description = "Assembles a morning brief from calendar and contacts and posts it at a scheduled time.",
            triggerType = "TIME",
            actionsJson = """[
                {"type":"NOTIFICATION","params":{"title":"Morning Brief","text":"Today: {{agendaSummary}}","priority":"normal"}},
                {"type":"SPEAK","params":{"text":"Good morning. You have {{eventCount}} events today."}}
            ]""".trimIndent()
        ),
        PolicyGalleryEntry(
            id = "gallery-battery-judgment-mode",
            name = "Battery Judgment Mode",
            category = "safety",
            requiredCapabilities = listOf("BATTERY"),
            riskClass = "low",
            cosHandoff = "Adds judgment-only advisory (no auto changes) when battery is critically low.",
            description = "Posts a low-battery advisory and suggests power-saving actions without forced changes.",
            triggerType = "BATTERY",
            actionsJson = """[
                {"type":"NOTIFICATION","params":{"title":"Battery Advisory","text":"Battery at {{levelPercent}}%. Consider power saver.","priority":"normal"}}
            ]""".trimIndent()
        ),
        PolicyGalleryEntry(
            id = "gallery-errand-nudge",
            name = "Errand Nudge",
            category = "context",
            requiredCapabilities = listOf("ACCESS_FINE_LOCATION", "READ_CALENDAR"),
            riskClass = "low",
            cosHandoff = "Nudges relevant errands when near a saved location (placeholder geofence).",
            description = "Matches calendar errands to location proximity and posts a contextual nudge.",
            triggerType = "LOCATION",
            actionsJson = """[
                {"type":"NOTIFICATION","params":{"title":"Errand Nudge","text":"Near {{place}}: {{errand}}","priority":"normal"}}
            ]""".trimIndent()
        ),
        PolicyGalleryEntry(
            id = "gallery-ambient-briefing",
            name = "Ambient Briefing",
            category = "relay",
            requiredCapabilities = listOf("NOTIFICATION", "SCHEDULE_EXACT_ALARM"),
            riskClass = "low",
            cosHandoff = "Periodic ambient briefing on a relaxed schedule (placeholder content source).",
            description = "Posts a quiet ambient briefing at a relaxed cadence without intrusive interruptions.",
            triggerType = "TIME",
            actionsJson = """[
                {"type":"NOTIFICATION","params":{"title":"Ambient Brief","text":"{{ambientSummary}}","priority":"low"}}
            ]""".trimIndent()
        )
    )

    fun getGallery(): List<PolicyGalleryEntry> = entries.toList()

    fun getEntry(id: String): PolicyGalleryEntry? = entries.firstOrNull { it.id == id }

    /**
     * Clones a gallery entry into an enabled [AutomationProfile. Never overwrites an existing
     * profile with the same id — returns null so callers can surface "already exists" without
     * clobbering user/agent edits. The gallery source entry is never mutated.
     */
    fun cloneEntry(entryId: String, existingIds: Set<String>): AutomationProfile? {
        val entry = getEntry(entryId) ?: return null
        if (entry.id in existingIds) return null
        val now = System.currentTimeMillis()
        return AutomationProfile(
            id = entry.id,
            name = entry.name,
            description = entry.description,
            isEnabled = false,
            triggerType = entry.triggerType,
            triggerConfigJson = "{}",
            conditionsJson = "{}",
            actionsJson = entry.actionsJson,
            cooldownMs = 60000L,
            priority = 5,
            createdAt = now,
            updatedAt = now
        )
    }
}
