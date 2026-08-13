package com.example.engine

import android.content.Context
import org.json.JSONObject

/**
 * Capability policy — the guard between inbound commands and privileged
 * actions.
 *
 * Every action a profile (or an inbound /v1/events command) triggers is
 * checked here before it executes. This is the same trust posture Tasker
 * applies to untrusted third-party intents: *external input → local
 * privileged action* must be validated against named, per-capability
 * requirements. When a capability is missing the action is SKIPPED (not
 * failed silently, not force-run), and the caller sees exactly why.
 *
 * Checks reference the live capability state in [CapabilityProvider], so a
 * revoked grant immediately gates every action that needs it.
 */
object CapabilityPolicy {

    /**
     * Returns null if [type] may run under current capabilities, or a
     * human-readable reason to SKIP it otherwise. `params` allows the rare
     * value-dependent check (e.g. AUDIO ringerMode=silent).
     */
    fun require(context: Context, type: String, params: JSONObject): String? {
        val summary = CapabilityProvider.permissionSummary(context)
        return when (type) {
            "SEND_SMS" -> grant(summary["send_sms_granted"] == true, "SEND_SMS", "android.permission.SEND_SMS not granted")
            "CALL" -> grant(summary["call_phone_granted"] == true, "CALL", "android.permission.CALL_PHONE not granted")
            "DND" -> grant(summary["dnd_ready"] == true, "DND", "Do Not Disturb access not granted")
            "AUDIO" -> {
                // ringerMode=silent is the DND-gated case.
                if (params.optString("ringerMode").equals("silent", ignoreCase = true)) {
                    grant(summary["dnd_ready"] == true, "AUDIO:silent", "DND access required for ringerMode=silent")
                } else null
            }
            "BRIGHTNESS", "SCREEN_TIMEOUT", "ROTATION" ->
                grant(summary["device_settings_ready"] == true, type, "WRITE_SETTINGS (modify system settings) not granted")
            "FLASHLIGHT", "CAMERA" ->
                grant(summary["camera_granted"] == true, type, "android.permission.CAMERA not granted")
            "NOTIFICATION" ->
                grant(summary["post_notifications_granted"] == true, "NOTIFICATION", "android.permission.POST_NOTIFICATIONS not granted")
            "SEND_INTENT" -> {
                // UI-driving intents (whatsapp://, mailto:, geo:) may need the
                // accessibility "hands" for auto-confirmation only; the intent
                // itself runs with just a VIEW handler.
                null
            }
            "UI_DRIVE" ->
                grant(
                    com.example.accessibility.CoSAccessibilityService.isEnabled(context),
                    "UI_DRIVE",
                    "Accessibility (CoS Screen Access) not granted — required to drive other apps' UIs"
                )
            else -> null
        }
    }

    private fun grant(ok: Boolean, action: String, reason: String): String? =
        if (ok) null else "capability '$action' blocked: $reason"
}
