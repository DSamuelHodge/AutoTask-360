package com.example.security

import com.example.data.AutomationProfile
import com.example.domain.ActionStep
import com.example.domain.AutomationSchema
import com.example.domain.DefinitionCompiler
import com.example.domain.InvalidAutomationException

object HighRiskPolicy {
    const val RISK_THRESHOLD = 2

    val alwaysConfirmTypes: Set<String> = setOf(
        "SEND_SMS", "CALL", "UI_DRIVE", "HTTP", "WRITE_FILE", "CAMERA",
        "BRAIN_RPC", "SCREEN_DUMP"
    )

    fun requiredApprovals(steps: List<ActionStep>): List<String> {
        return steps.map { it.type.uppercase() }
            .filter { type -> requiresApproval(type) }
            .distinct()
    }

    fun requiredApprovals(profile: AutomationProfile): List<String> {
        return try {
            requiredApprovals(DefinitionCompiler.getOrCompile(profile).definition.steps)
        } catch (_: InvalidAutomationException) {
            emptyList()
        }
    }

    fun requiresApproval(type: String): Boolean {
        val normalized = type.uppercase()
        if (normalized in alwaysConfirmTypes) return true
        val descriptor = AutomationSchema.action(normalized) ?: return false
        if (descriptor.autonomy == "confirm_required") return true
        val rank = AutomationSchema.riskRank[descriptor.risk] ?: 0
        return rank >= RISK_THRESHOLD
    }

    fun missingApprovals(needed: Collection<String>, approved: Set<String>): List<String> {
        val allowed = approved.map { it.uppercase() }.toSet()
        return needed.map { it.uppercase() }.filter { it !in allowed }.distinct()
    }
}
