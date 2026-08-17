package com.example.domain

object DefinitionValidator {
    fun validate(definition: AutomationDefinition) {
        val errors = mutableListOf<ValidationError>()
        if (definition.id.isBlank()) {
            errors += ValidationError("id", "is required")
        }
        if (definition.name.isBlank()) {
            errors += ValidationError("name", "is required")
        }
        if (definition.schemaVersion < 1) {
            errors += ValidationError("schemaVersion", "must be >= 1")
        }
        if (definition.schemaVersion > AutomationSchema.CURRENT_VERSION) {
            errors += ValidationError(
                "schemaVersion",
                "unsupported schema version ${definition.schemaVersion}; current is ${AutomationSchema.CURRENT_VERSION}"
            )
        }
        if (definition.revision < 0L) {
            errors += ValidationError("revision", "must be >= 0")
        }
        if (definition.executionPolicy.cooldownMs < 0L) {
            errors += ValidationError("executionPolicy.cooldownMs", "must be >= 0")
        }

        validateTrigger(definition.trigger, errors)
        definition.conditions.forEachIndexed { index, condition ->
            validateCondition(condition, "conditions[$index]", errors)
        }
        definition.steps.forEachIndexed { index, step ->
            validateStep(step, "steps[$index]", errors)
        }
        validateRiskPolicy(definition.riskPolicy, errors)

        if (errors.isNotEmpty()) {
            throw InvalidAutomationException(errors)
        }
    }

    private fun validateTrigger(trigger: TriggerSpec, errors: MutableList<ValidationError>) {
        if (trigger.type.isBlank()) {
            errors += ValidationError("trigger.type", "is required")
            return
        }
        val descriptor = AutomationSchema.trigger(trigger.type)
        if (descriptor == null) {
            errors += ValidationError("trigger.type", "unknown trigger type '${trigger.type}'")
            return
        }
        validateObject(trigger.config, descriptor.configByName, "trigger.config", errors)
        validateScheduleTrigger(trigger, errors)
    }

    private fun validateScheduleTrigger(trigger: TriggerSpec, errors: MutableList<ValidationError>) {
        val config = trigger.config
        config.fields["timezone"]?.asTextOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { zone ->
            try {
                java.time.ZoneId.of(zone)
            } catch (_: Exception) {
                errors += ValidationError("trigger.config.timezone", "unknown timezone '$zone'")
            }
        }
        when (trigger.type.uppercase()) {
            "TIME" -> {
                if (config.fields["hour"]?.asIntOrNull() == null) {
                    errors += ValidationError("trigger.config.hour", "is required")
                }
                if (config.fields["minute"]?.asIntOrNull() == null) {
                    errors += ValidationError("trigger.config.minute", "is required")
                }
            }
            "SCHEDULE" -> {
                val cron = config.fields["cronExpression"]?.asTextOrNull()?.trim().orEmpty()
                val interval = config.fields["intervalMs"]?.asLongOrNull()
                if (cron.isEmpty() && (interval == null || interval <= 0L)) {
                    errors += ValidationError(
                        "trigger.config",
                        "SCHEDULE requires cronExpression or intervalMs"
                    )
                }
                if (cron.isNotEmpty()) {
                    try {
                        com.example.engine.CronParser.parse(cron)
                    } catch (e: Exception) {
                        errors += ValidationError(
                            "trigger.config.cronExpression",
                            e.message ?: "invalid cron expression"
                        )
                    }
                }
                if (interval != null && interval < com.example.engine.NextFireCalculator.MIN_INTERVAL_MS) {
                    errors += ValidationError(
                        "trigger.config.intervalMs",
                        "must be >= ${com.example.engine.NextFireCalculator.MIN_INTERVAL_MS}"
                    )
                }
            }
            "SUNRISE_SUNSET" -> {
                if (config.fields["event"]?.asTextOrNull().isNullOrBlank()) {
                    errors += ValidationError("trigger.config.event", "is required")
                }
            }
        }
    }

    private fun validateCondition(
        condition: ConditionSpec,
        path: String,
        errors: MutableList<ValidationError>
    ) {
        if (condition.type.isBlank()) {
            errors += ValidationError("$path.type", "is required")
            return
        }
        val spec = AutomationSchema.conditionByName[condition.type]
        if (spec == null) {
            errors += ValidationError(path, "unknown condition '${condition.type}'")
            return
        }
        validateParam(condition.value, spec, "$path.value", errors)
    }

    private fun validateStep(step: ActionStep, path: String, errors: MutableList<ValidationError>) {
        if (step.type.isBlank()) {
            errors += ValidationError("$path.type", "is required")
            return
        }
        val descriptor = AutomationSchema.action(step.type)
        if (descriptor == null) {
            errors += ValidationError("$path.type", "unknown action type '${step.type}'")
            return
        }
        validateObject(step.params, descriptor.paramsByName, "$path.params", errors)
    }

    private fun validateRiskPolicy(policy: RiskPolicy, errors: MutableList<ValidationError>) {
        if (policy.maxRisk !in AutomationSchema.riskRank) {
            errors += ValidationError("riskPolicy.maxRisk", "unknown risk '${policy.maxRisk}'")
        }
    }

    private fun validateObject(
        obj: JsonValue.ObjectValue,
        allowed: Map<String, AutomationSchema.ParamSpec>,
        path: String,
        errors: MutableList<ValidationError>
    ) {
        obj.fields.forEach { (key, value) ->
            val spec = allowed[key]
            if (spec == null) {
                errors += ValidationError(path, "unknown field '$key'")
            } else {
                validateParam(value, spec, "$path.$key", errors)
            }
        }
    }

    private fun validateParam(
        value: JsonValue,
        spec: AutomationSchema.ParamSpec,
        path: String,
        errors: MutableList<ValidationError>
    ) {
        if (value is JsonValue.Null) {
            errors += ValidationError(path, "${spec.name} cannot be null")
            return
        }
        when (spec.kind) {
            AutomationSchema.ParamKind.STRING -> {
                val text = value.asTextOrNull()
                if (text == null) {
                    errors += ValidationError(path, "must be a string")
                    return
                }
                validateAllowed(text, spec, path, errors)
            }
            AutomationSchema.ParamKind.BOOLEAN -> {
                if (value.asBooleanOrNull() == null) {
                    errors += ValidationError(path, "must be a boolean")
                }
            }
            AutomationSchema.ParamKind.INT -> {
                val number = value.asIntOrNull()
                if (number == null) {
                    errors += ValidationError(path, "must be an integer")
                    return
                }
                validateRange(number.toDouble(), spec, path, errors)
            }
            AutomationSchema.ParamKind.LONG -> {
                val number = value.asLongOrNull()
                if (number == null) {
                    errors += ValidationError(path, "must be an integer")
                    return
                }
                validateRange(number.toDouble(), spec, path, errors)
            }
            AutomationSchema.ParamKind.FLOAT -> {
                val number = value.asDoubleOrNull()
                if (number == null) {
                    errors += ValidationError(path, "must be a number")
                    return
                }
                validateRange(number, spec, path, errors)
            }
            AutomationSchema.ParamKind.STRING_ARRAY -> {
                val array = value as? JsonValue.ArrayValue
                if (array == null) {
                    errors += ValidationError(path, "must be an array of strings")
                    return
                }
                array.values.forEachIndexed { index, item ->
                    val text = item.asTextOrNull()
                    if (text == null) {
                        errors += ValidationError("$path[$index]", "must be a string")
                    } else {
                        validateAllowed(text, spec, "$path[$index]", errors)
                    }
                }
            }
            AutomationSchema.ParamKind.LONG_ARRAY -> {
                val array = value as? JsonValue.ArrayValue
                if (array == null) {
                    errors += ValidationError(path, "must be an array of integers")
                    return
                }
                array.values.forEachIndexed { index, item ->
                    if (item.asLongOrNull() == null) {
                        errors += ValidationError("$path[$index]", "must be an integer")
                    }
                }
            }
            AutomationSchema.ParamKind.OBJECT -> {
                if (value !is JsonValue.ObjectValue) {
                    errors += ValidationError(path, "must be a JSON object")
                }
            }
        }
    }

    private fun validateRange(
        value: Double,
        spec: AutomationSchema.ParamSpec,
        path: String,
        errors: MutableList<ValidationError>
    ) {
        val min = spec.min
        val max = spec.max
        if (min != null && value < min) {
            errors += ValidationError(path, "must be >= ${canonicalBound(min)}")
        }
        if (max != null && value > max) {
            errors += ValidationError(path, "must be <= ${canonicalBound(max)}")
        }
    }

    private fun validateAllowed(
        value: String,
        spec: AutomationSchema.ParamSpec,
        path: String,
        errors: MutableList<ValidationError>
    ) {
        val allowed = spec.allowed ?: return
        val matches = if (spec.allowedIgnoreCase) {
            allowed.any { it.equals(value, ignoreCase = true) }
        } else {
            value in allowed
        }
        if (!matches) {
            errors += ValidationError(path, "must be one of ${allowed.joinToString()}")
        }
    }

    private fun canonicalBound(value: Double): String {
        return if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    }
}
