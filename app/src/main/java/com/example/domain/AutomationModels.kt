package com.example.domain

import com.example.data.AutomationProfile

data class AutomationDefinition(
    val id: String,
    val revision: Long,
    val schemaVersion: Int,
    val name: String,
    val description: String,
    val enabled: Boolean,
    val trigger: TriggerSpec,
    val conditions: List<ConditionSpec>,
    val steps: List<ActionStep>,
    val executionPolicy: ExecutionPolicy,
    val riskPolicy: RiskPolicy
)

data class TriggerSpec(
    val type: String,
    val config: JsonValue.ObjectValue = JsonValue.emptyObject
)

data class ConditionSpec(
    val type: String,
    val value: JsonValue
)

data class ActionStep(
    val type: String,
    val params: JsonValue.ObjectValue = JsonValue.emptyObject
)

data class ExecutionPolicy(
    val cooldownMs: Long = 0L,
    val priority: Int = 0
)

data class RiskPolicy(
    val maxRisk: String = "low",
    val requireConfirmation: Boolean = false
)

data class CompiledAutomation(
    val definition: AutomationDefinition
) {
    val id: String get() = definition.id
    val revision: Long get() = definition.revision
    val schemaVersion: Int get() = definition.schemaVersion

    fun toProfile(
        createdAt: Long,
        updatedAt: Long,
        lastTriggeredAt: Long = 0L
    ): AutomationProfile = AutomationProfile(
        id = definition.id,
        name = definition.name,
        description = definition.description,
        isEnabled = definition.enabled,
        triggerType = definition.trigger.type,
        triggerConfigJson = definition.trigger.config.toCompactString(),
        conditionsJson = DefinitionCodec.conditionsToObject(definition.conditions).toCompactString(),
        actionsJson = DefinitionCodec.stepsToArray(definition.steps).toString(),
        cooldownMs = definition.executionPolicy.cooldownMs,
        priority = definition.executionPolicy.priority,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastTriggeredAt = lastTriggeredAt,
        schemaVersion = definition.schemaVersion,
        revision = definition.revision
    )
}

data class ValidationError(
    val path: String,
    val message: String
) {
    override fun toString(): String = "$path: $message"
}

class InvalidAutomationException(
    val errors: List<ValidationError>
) : IllegalArgumentException(
    if (errors.size == 1) errors.first().toString()
    else errors.joinToString(prefix = "Invalid automation: ", separator = "; ")
)
