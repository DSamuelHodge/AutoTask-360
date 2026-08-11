package com.example.onboarding

import android.Manifest
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

/**
 * Capability-scoped onboarding scaffold (Kaneo #8).
 *
 * Pure, hermetic mapping from a policy's required capabilities to the runtime
 * permissions / special-access settings they need. The source of truth for the
 * underlying permission strings and settings actions is
 * [com.example.engine.CapabilityProvider]; this module reuses the SAME constants
 * so onboarding and the capability report never drift.
 *
 * This is a scaffold only: it does not render UI or rebuild the launcher. It is
 * intended to be consumed by a future first-run onboarding screen.
 */

/**
 * A single permission/special-access requirement derived from a profile.
 *
 * @param capability the logical capability, e.g. "SMS", "DND", "LOCATION", "BATTERY".
 * @param kind whether this is a normal/runtime [Kind.RUNTIME_PERMISSION] or a
 *   guarded [Kind.SPECIAL_ACCESS] setting (requires a user trip to Settings).
 * @param androidPermission the Android permission string, or null for special access.
 * @param settingsAction the Settings action to launch for special access, or null.
 * @param notes human-readable explanation for the onboarding UI.
 */
data class CapabilityRequirement(
    val capability: String,
    val kind: Kind,
    val androidPermission: String?,
    val settingsAction: String?,
    val notes: String
) {
    enum class Kind { RUNTIME_PERMISSION, SPECIAL_ACCESS }
}

/**
 * Returns the list of capability requirements a profile needs, in onboarding order.
 * Triggers and actions are both inspected so the user is prompted for everything
 * the policy could touch.
 *
 * @param profile the automation profile whose [AutomationProfile.triggerType] and
 *   [AutomationProfile.actionsJson] define the capability surface.
 */
fun requiredCapabilitiesFor(profile: com.example.data.AutomationProfile): List<CapabilityRequirement> {
    val caps = LinkedHashSet<String>()
    caps += capabilityForTrigger(profile.triggerType)
    runCatching {
        val actions = JSONArray(profile.actionsJson)
        for (i in 0 until actions.length()) {
            val obj = actions.opt(i) as? JSONObject ?: continue
            caps += capabilityForAction(obj.optString("type", "").uppercase())
        }
    }
    // Stable, user-friendly ordering.
    val ordered = listOf("SMS", "DND", "LOCATION", "BATTERY", "NOTIFICATIONS", "PHONE", "CALENDAR", "CAMERA")
    val result = LinkedHashSet<CapabilityRequirement>()
    ordered.filter { it in caps }.forEach { c -> requirementFor(c)?.let { result += it } }
    caps.filter { it !in ordered }.forEach { c -> requirementFor(c)?.let { result += it } }
    return result.toList()
}

/**
 * Returns a Settings [android.content.Intent] action (or a documented pseudo-action)
 * the user can launch to repair a missing capability, reusing the exact actions
 * from [com.example.engine.CapabilityProvider.specialAccessJson] / provisioning
 * hints. For runtime permissions the caller should request the permission directly
 * instead of launching Settings, so this returns null for those.
 */
fun repairActionFor(capability: String): String? = when (capability) {
    // Special-access capabilities require a Settings trip (matches CapabilityProvider).
    "DND" -> Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
    "WRITE_SETTINGS" -> Settings.ACTION_MANAGE_WRITE_SETTINGS
    "USAGE_STATS" -> Settings.ACTION_USAGE_ACCESS_SETTINGS
    "MANAGE_EXTERNAL_STORAGE" -> Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
    "EXACT_ALARM" -> Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
    "NOTIFICATION_LISTENER" -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
    "DRAW_OVER_APPS" -> Settings.ACTION_MANAGE_OVERLAY_PERMISSION
    "BATTERY" -> android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
    // Runtime-permission capabilities: request the permission, do not launch Settings.
    "SMS", "LOCATION", "PHONE", "CALENDAR", "CAMERA", "NOTIFICATIONS" -> null
    else -> null
}

private fun capabilityForTrigger(triggerType: String): String = when (triggerType.uppercase()) {
    "SMS" -> "SMS"
    "BATTERY" -> "BATTERY"
    "LOCATION" -> "LOCATION"
    "CALL" -> "PHONE"
    "NOTIFICATION" -> "NOTIFICATION_LISTENER"
    "CALENDAR", "CALENDAR_EVENT", "MEETING" -> "CALENDAR"
    "WIFI", "BLUETOOTH" -> "LOCATION"
    else -> ""
}.takeIf { it.isNotEmpty() } ?: ""

private fun capabilityForAction(actionType: String): String = when (actionType) {
    "SEND_SMS", "READ_SMS" -> "SMS"
    "DND", "AUDIO" -> "DND"
    "BRIGHTNESS", "SCREEN_TIMEOUT", "ROTATION" -> "WRITE_SETTINGS"
    "LOCATION", "GEOFENCE" -> "LOCATION"
    "BATTERY" -> "BATTERY"
    "CALL" -> "PHONE"
    "NOTIFICATION", "POST_NOTIFICATION" -> "NOTIFICATIONS"
    "CAMERA", "FLASHLIGHT" -> "CAMERA"
    "READ_CALENDAR", "WRITE_CALENDAR" -> "CALENDAR"
    "USAGE_STATS" -> "USAGE_STATS"
    "MANAGE_EXTERNAL_STORAGE" -> "MANAGE_EXTERNAL_STORAGE"
    "EXACT_ALARM" -> "EXACT_ALARM"
    "DRAW_OVER_APPS" -> "DRAW_OVER_APPS"
    else -> ""
}.takeIf { it.isNotEmpty() } ?: ""

private fun requirementFor(capability: String): CapabilityRequirement? = when (capability) {
    "SMS" -> CapabilityRequirement(
        capability, CapabilityRequirement.Kind.RUNTIME_PERMISSION,
        Manifest.permission.SEND_SMS, null,
        "Send/read SMS actions require SEND_SMS (and RECEIVE_SMS/READ_SMS) runtime permission."
    )
    "LOCATION" -> CapabilityRequirement(
        capability, CapabilityRequirement.Kind.RUNTIME_PERMISSION,
        Manifest.permission.ACCESS_FINE_LOCATION, null,
        "Location triggers/actions require ACCESS_FINE_LOCATION (and BACKGROUND_LOCATION for geofence)."
    )
    "PHONE" -> CapabilityRequirement(
        capability, CapabilityRequirement.Kind.RUNTIME_PERMISSION,
        Manifest.permission.CALL_PHONE, null,
        "Call actions require CALL_PHONE runtime permission."
    )
    "CALENDAR" -> CapabilityRequirement(
        capability, CapabilityRequirement.Kind.RUNTIME_PERMISSION,
        Manifest.permission.READ_CALENDAR, null,
        "Calendar triggers require READ_CALENDAR / WRITE_CALENDAR runtime permission."
    )
    "CAMERA" -> CapabilityRequirement(
        capability, CapabilityRequirement.Kind.RUNTIME_PERMISSION,
        Manifest.permission.CAMERA, null,
        "Camera/flashlight actions require CAMERA runtime permission."
    )
    "NOTIFICATIONS" -> CapabilityRequirement(
        capability, CapabilityRequirement.Kind.RUNTIME_PERMISSION,
        Manifest.permission.POST_NOTIFICATIONS, null,
        "Posting status notifications requires POST_NOTIFICATIONS (Android 13+)."
    )
    "DND" -> CapabilityRequirement(
        capability, CapabilityRequirement.Kind.SPECIAL_ACCESS,
        null, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS,
        "DND / silent-audio actions require Notification Policy Access (special access)."
    )
    "WRITE_SETTINGS" -> CapabilityRequirement(
        capability, CapabilityRequirement.Kind.SPECIAL_ACCESS,
        null, Settings.ACTION_MANAGE_WRITE_SETTINGS,
        "Brightness / screen-timeout / rotation require WRITE_SETTINGS (special access)."
    )
    "BATTERY" -> CapabilityRequirement(
        capability, CapabilityRequirement.Kind.SPECIAL_ACCESS,
        null, android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
        "Reliable background execution benefits from Ignore Battery Optimizations."
    )
    "NOTIFICATION_LISTENER" -> CapabilityRequirement(
        capability, CapabilityRequirement.Kind.SPECIAL_ACCESS,
        null, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
        "Notification triggers require the Notification Listener to be enabled."
    )
    else -> null
}
