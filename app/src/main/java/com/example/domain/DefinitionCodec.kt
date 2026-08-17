package com.example.domain

import com.example.data.AutomationProfile
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses compatibility JSON and Room rows into typed [AutomationDefinition]
 * values and serializes them back. Validation is [DefinitionValidator]'s job.
 */
object DefinitionCodec {
    fun fromJson(input: JSONObject, existingRevision: Long = 0L): AutomationDefinition {
        val trigger = parseTrigger(input)
        val conditions = parseConditions(input)
        val steps = parseSteps(input)
        val executionPolicy = parseExecutionPolicy(input)
        val schemaVersion = if (input.has("schemaVersion")) {
            input.optInt("schemaVersion", AutomationSchema.CURRENT_VERSION)
        } else {
            AutomationSchema.CURRENT_VERSION
        }
        val enabled = when {
            input.has("enabled") -> input.optBoolean("enabled")
            input.has("isEnabled") -> input.optBoolean("isEnabled")
            else -> false
        }
        val providedRisk = parseRiskPolicy(input)
        val riskPolicy = providedRisk ?: AutomationSchema.deriveRiskPolicy(steps)
        return AutomationDefinition(
            id = input.optString("id", "").trim(),
            revision = if (input.has("revision")) input.optLong("revision") else existingRevision,
            schemaVersion = schemaVersion,
            name = input.optString("name", "").trim(),
            description = input.optString("description", ""),
            enabled = enabled,
            trigger = trigger,
            conditions = conditions,
            steps = steps,
            executionPolicy = executionPolicy,
            riskPolicy = riskPolicy
        )
    }

    fun fromProfile(profile: AutomationProfile): AutomationDefinition {
        return fromJson(profileToCompatJson(profile), existingRevision = profile.revision)
    }

    fun profileToCompatJson(profile: AutomationProfile): JSONObject = JSONObject()
        .put("id", profile.id)
        .put("name", profile.name)
        .put("description", profile.description)
        .put("isEnabled", profile.isEnabled)
        .put("enabled", profile.isEnabled)
        .put("triggerType", profile.triggerType)
        .put("triggerConfigJson", profile.triggerConfigJson)
        .put("conditionsJson", profile.conditionsJson)
        .put("actionsJson", profile.actionsJson)
        .put("cooldownMs", profile.cooldownMs)
        .put("priority", profile.priority)
        .put("schemaVersion", profile.schemaVersion)
        .put("revision", profile.revision)
        .put("createdAt", profile.createdAt)
        .put("updatedAt", profile.updatedAt)
        .put("lastTriggeredAt", profile.lastTriggeredAt)

    fun mergePatch(existing: AutomationProfile, patch: JSONObject): JSONObject {
        val merged = profileToCompatJson(existing)
        patch.keys().forEach { key ->
            merged.put(key, patch.opt(key))
        }
        return merged
    }

    fun toCanonicalJson(definition: AutomationDefinition): JSONObject {
        val conditions = JSONArray()
        definition.conditions.forEach { condition ->
            conditions.put(
                JSONObject()
                    .put("type", condition.type)
                    .put("value", condition.value.toJson())
            )
        }
        return JSONObject()
            .put("id", definition.id)
            .put("revision", definition.revision)
            .put("schemaVersion", definition.schemaVersion)
            .put("name", definition.name)
            .put("description", definition.description)
            .put("enabled", definition.enabled)
            .put(
                "trigger",
                JSONObject()
                    .put("type", definition.trigger.type)
                    .put("config", definition.trigger.config.toJsonObject())
            )
            .put("conditions", conditions)
            .put("steps", stepsToArray(definition.steps))
            .put(
                "executionPolicy",
                JSONObject()
                    .put("cooldownMs", definition.executionPolicy.cooldownMs)
                    .put("priority", definition.executionPolicy.priority)
            )
            .put(
                "riskPolicy",
                JSONObject()
                    .put("maxRisk", definition.riskPolicy.maxRisk)
                    .put("requireConfirmation", definition.riskPolicy.requireConfirmation)
            )
    }

    fun conditionsToObject(conditions: List<ConditionSpec>): JsonValue.ObjectValue {
        val fields = linkedMapOf<String, JsonValue>()
        conditions.forEach { fields[it.type] = it.value }
        return JsonValue.ObjectValue(fields)
    }

    fun stepsToArray(steps: List<ActionStep>): JSONArray {
        val array = JSONArray()
        steps.forEach { step ->
            array.put(
                JSONObject()
                    .put("type", step.type)
                    .put("params", step.params.toJsonObject())
            )
        }
        return array
    }

    private fun parseTrigger(input: JSONObject): TriggerSpec {
        val triggerObj = input.optJSONObject("trigger")
        if (triggerObj != null) {
            val type = triggerObj.optString("type", "").ifBlank {
                input.optString("triggerType", "")
            }.uppercase()
            val config = when (val raw = triggerObj.opt("config")) {
                is JSONObject -> JsonValue.fromObject(raw)
                is String -> JsonValue.parseObject(raw)
                else -> readConfigObject(input, "triggerConfig", "triggerConfigJson")
            }
            return TriggerSpec(type, config)
        }
        val type = input.optString("triggerType", "").uppercase()
        val config = readConfigObject(input, "triggerConfig", "triggerConfigJson")
        return TriggerSpec(type, config)
    }

    private fun parseConditions(input: JSONObject): List<ConditionSpec> {
        val rawConditions = input.opt("conditions")
        val asArray = coerceJsonArray(rawConditions)
        if (asArray != null) {
            val parsed = ArrayList<ConditionSpec>(asArray.length())
            for (i in 0 until asArray.length()) {
                val item = asArray.optJSONObject(i) ?: continue
                val type = item.optString("type", "").trim()
                val value = if (item.has("value")) JsonValue.from(item.opt("value")) else JsonValue.Null
                parsed.add(ConditionSpec(type, value))
            }
            return parsed
        }
        val obj = when {
            coerceJsonObject(rawConditions) != null -> coerceJsonObject(rawConditions)!!
            input.has("conditionsJson") -> parseJsonObjectField(input.opt("conditionsJson"), "{}")
            rawConditions != null && rawConditions != JSONObject.NULL ->
                throw InvalidAutomationException(
                    listOf(ValidationError("conditions", "must be a JSON object or array"))
                )
            else -> JSONObject()
        }
        return obj.keys().asSequence()
            .map { key -> ConditionSpec(key, JsonValue.from(obj.opt(key))) }
            .toList()
    }

    private fun parseSteps(input: JSONObject): List<ActionStep> {
        val rawSteps = when {
            input.has("steps") -> input.opt("steps")
            input.has("actions") -> input.opt("actions")
            input.has("actionsJson") -> input.opt("actionsJson")
            else -> null
        }
        val actionsArray = when {
            rawSteps == null || rawSteps == JSONObject.NULL -> JSONArray()
            coerceJsonArray(rawSteps) != null -> coerceJsonArray(rawSteps)!!
            else -> throw InvalidAutomationException(
                listOf(ValidationError("steps", "must be a JSON array"))
            )
        }
        val steps = ArrayList<ActionStep>(actionsArray.length())
        for (i in 0 until actionsArray.length()) {
            val item = actionsArray.optJSONObject(i)
                ?: throw InvalidAutomationException(
                    listOf(ValidationError("steps[$i]", "each step must be a JSON object"))
                )
            val type = item.optString("type", "").uppercase()
            val params = when (val raw = item.opt("params")) {
                null, JSONObject.NULL -> JsonValue.emptyObject
                is JSONObject -> JsonValue.fromObject(raw)
                is String -> JsonValue.parseObject(raw)
                else -> throw InvalidAutomationException(
                    listOf(ValidationError("steps[$i].params", "params must be a JSON object"))
                )
            }
            steps.add(ActionStep(type, params))
        }
        return steps
    }

    private fun parseExecutionPolicy(input: JSONObject): ExecutionPolicy {
        val policy = input.optJSONObject("executionPolicy")
        val cooldownMs = when {
            policy != null && policy.has("cooldownMs") -> policy.optLong("cooldownMs")
            input.has("cooldownMs") -> input.optLong("cooldownMs")
            else -> 0L
        }
        val priority = when {
            policy != null && policy.has("priority") -> policy.optInt("priority")
            input.has("priority") -> input.optInt("priority")
            else -> 0
        }
        return ExecutionPolicy(cooldownMs = cooldownMs, priority = priority)
    }

    private fun parseRiskPolicy(input: JSONObject): RiskPolicy? {
        val policy = input.optJSONObject("riskPolicy") ?: return null
        return RiskPolicy(
            maxRisk = policy.optString("maxRisk", "low").ifBlank { "low" },
            requireConfirmation = policy.optBoolean("requireConfirmation", false)
        )
    }

    private fun readConfigObject(input: JSONObject, objectKey: String, stringKey: String): JsonValue.ObjectValue {
        return when {
            input.optJSONObject(objectKey) != null -> JsonValue.fromObject(input.getJSONObject(objectKey))
            input.has(stringKey) -> parseObjectField(input.opt(stringKey))
            input.has(objectKey) -> parseObjectField(input.opt(objectKey))
            else -> JsonValue.emptyObject
        }
    }

    private fun parseObjectField(value: Any?): JsonValue.ObjectValue = when (value) {
        null, JSONObject.NULL -> JsonValue.emptyObject
        is JSONObject -> JsonValue.fromObject(value)
        is String -> JsonValue.parseObject(value)
        else -> throw InvalidAutomationException(
            listOf(ValidationError("trigger.config", "must be a JSON object"))
        )
    }

    private fun parseJsonObjectField(value: Any?, defaultJson: String): JSONObject = when (value) {
        null, JSONObject.NULL -> JSONObject(defaultJson)
        is JSONObject -> value
        is String -> if (value.isBlank()) JSONObject(defaultJson) else JSONObject(value)
        else -> throw InvalidAutomationException(
            listOf(ValidationError("conditions", "must be a JSON object or array"))
        )
    }

    private fun coerceJsonObject(value: Any?): JSONObject? = when (value) {
        is JSONObject -> value
        is String -> {
            val trimmed = value.trim()
            if (trimmed.isEmpty() || trimmed == "{}" || trimmed.startsWith("{")) {
                if (trimmed.isEmpty()) JSONObject() else JSONObject(trimmed)
            } else {
                null
            }
        }
        else -> null
    }

    private fun coerceJsonArray(value: Any?): JSONArray? = when (value) {
        is JSONArray -> value
        is String -> {
            val trimmed = value.trim()
            if (trimmed.startsWith("[")) JSONArray(trimmed) else null
        }
        else -> null
    }
}
