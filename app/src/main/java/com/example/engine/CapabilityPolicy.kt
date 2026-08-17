package com.example.engine

import android.content.Context
import com.example.engine.actions.ActionRegistry
import com.example.engine.actions.capabilityBlocked
import org.json.JSONObject

/**
 * Capability policy — the guard between inbound commands and privileged
 * actions.
 *
 * Requirements live on the registered [com.example.engine.actions.ActionHandler]
 * for the action type. Missing capability → SKIPPED with an explicit reason.
 */
object CapabilityPolicy {
    fun require(
        context: Context,
        type: String,
        params: JSONObject,
        registry: ActionRegistry = ActionRegistry.standard()
    ): String? {
        registry.handler(type)?.let { return it.capabilityDenial(context, params) }
        return when (type) {
            "UI_DRIVE" -> {
                val granted = com.example.accessibility.CoSAccessibilityService.isEnabled(context)
                if (granted) null else capabilityBlocked(
                    "UI_DRIVE",
                    "Accessibility (CoS Screen Access) not granted — required to drive other apps' UIs"
                )
            }
            else -> null
        }
    }
}
