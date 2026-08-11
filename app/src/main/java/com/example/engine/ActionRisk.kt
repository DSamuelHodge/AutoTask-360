package com.example.engine

import org.json.JSONObject

/**
 * Local trust contract: action risk taxonomy (#20).
 *
 * Centralizes the risk classification that CapabilityProvider already hints at via its
 * per-action `risk` field and `agentPolicy.confirmationRequiredFor` list. Keep this the
 * single source of truth for "does action type X require confirmation / an allowlist".
 */
enum class RiskClass(val label: String) {
    OBSERVE_ONLY("observe_only"),
    LOCAL_UX("local_ux"),
    EXTERNAL_NETWORK("external_network"),
    MESSAGE_PHONE("message_phone"),
    SECURE_SETTINGS_MUTATION("secure_settings_mutation")
}

object ActionRisk {
    // Risk class per action `type`. Mirrors CapabilityProvider.actionCapabilitiesJson risk tiers.
    private val riskByType: Map<String, RiskClass> = mapOf(
        "NOTIFICATION" to RiskClass.OBSERVE_ONLY,
        "TOAST" to RiskClass.LOCAL_UX,
        "SPEAK" to RiskClass.LOCAL_UX,
        "VIBRATE" to RiskClass.LOCAL_UX,
        "FLASHLIGHT" to RiskClass.LOCAL_UX,
        "BRIGHTNESS" to RiskClass.SECURE_SETTINGS_MUTATION,
        "SCREEN_TIMEOUT" to RiskClass.SECURE_SETTINGS_MUTATION,
        "ROTATION" to RiskClass.SECURE_SETTINGS_MUTATION,
        "DND" to RiskClass.SECURE_SETTINGS_MUTATION,
        "AUDIO" to RiskClass.SECURE_SETTINGS_MUTATION,
        "HTTP" to RiskClass.EXTERNAL_NETWORK,
        "OPEN_URL" to RiskClass.EXTERNAL_NETWORK,
        "BROADCAST" to RiskClass.EXTERNAL_NETWORK,
        "SEND_SMS" to RiskClass.MESSAGE_PHONE,
        "CALL" to RiskClass.MESSAGE_PHONE,
        "CLIPBOARD" to RiskClass.LOCAL_UX,
        "WRITE_FILE" to RiskClass.LOCAL_UX,
        "READ_FILE" to RiskClass.OBSERVE_ONLY,
        "LAUNCH_APP" to RiskClass.LOCAL_UX,
        "OPEN_SETTINGS" to RiskClass.LOCAL_UX,
        "PROFILE" to RiskClass.LOCAL_UX,
        "WAIT" to RiskClass.OBSERVE_ONLY,
        "LOG" to RiskClass.OBSERVE_ONLY,
        "POWER_SAVE" to RiskClass.SECURE_SETTINGS_MUTATION,
        "WIFI_ACTION" to RiskClass.SECURE_SETTINGS_MUTATION,
        "BLUETOOTH_ACTION" to RiskClass.SECURE_SETTINGS_MUTATION,
        "AIRPLANE_MODE_ACTION" to RiskClass.SECURE_SETTINGS_MUTATION,
        "HOTSPOT" to RiskClass.SECURE_SETTINGS_MUTATION,
        "NFC_ACTION" to RiskClass.LOCAL_UX,
        "KILL_APP" to RiskClass.LOCAL_UX,
        "CAMERA" to RiskClass.MESSAGE_PHONE
    )

    // Derived from CapabilityProvider.agentPolicyJson confirmationRequiredFor:
    // ["SEND_SMS", "CALL", "DND", "AUDIO:silent"]. AUDIO requires confirmation only when
    // ringerMode=silent.
    private val confirmationRequiredBase: Set<String> = setOf("SEND_SMS", "CALL", "DND")

    fun riskFor(type: String): RiskClass =
        riskByType[type.uppercase()] ?: RiskClass.LOCAL_UX

    fun requiresConfirmation(type: String, params: Map<String, Any?> = emptyMap()): Boolean {
        val upper = type.uppercase()
        if (upper in confirmationRequiredBase) return true
        if (upper == "AUDIO") {
            val mode = (params["ringerMode"] ?: params["mode"])?.toString()?.lowercase()
            if (mode == "silent") return true
        }
        return false
    }

    fun knownTypes(): Set<String> = riskByType.keys

    fun isHighRisk(type: String, params: Map<String, Any?> = emptyMap()): Boolean =
        riskFor(type) in setOf(
            RiskClass.MESSAGE_PHONE,
            RiskClass.SECURE_SETTINGS_MUTATION,
            RiskClass.EXTERNAL_NETWORK
        ) || requiresConfirmation(type, params)
}

/** Convert a JSONObject of action params into a Map for risk/confirmation evaluation. */
fun JSONObject.toParamMap(): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    keys().forEach { map[it] = opt(it) }
    return map
}
