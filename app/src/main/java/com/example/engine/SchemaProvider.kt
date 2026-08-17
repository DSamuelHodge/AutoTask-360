package com.example.engine

import com.example.domain.AutomationSchema
import org.json.JSONArray
import org.json.JSONObject

object SchemaProvider {

    fun getSchemaJson(): String {
        val root = JSONObject()

        root.put("service", "AutoTask Tool Server Engine")
        root.put("version", "2.0.0")
        root.put("schemaVersion", AutomationSchema.CURRENT_VERSION)
        root.put("architecture", "Policy is Data. Execution is Deterministic.")
        root.put("capabilitiesEndpoint", "/v1/capabilities")
        val endpoints = JSONObject()
        val eventsEndpoint = JSONObject()
        eventsEndpoint.put("method", "POST")
        eventsEndpoint.put("path", "/v1/events")
        eventsEndpoint.put("canonicalBody", JSONObject(mapOf(
            "triggerType" to "MANUAL",
            "profileId" to "optional target profile ID; omit only for broadcast",
            "dryRun" to "Boolean; validates and reports planned profiles without executing actions",
            "payload" to "JSONObject event payload"
        )))
        eventsEndpoint.put("aliasesAccepted", JSONArray(listOf("type", "trigger_type", "profile_id", "dry_run")))
        endpoints.put("events", eventsEndpoint)
        root.put("endpoints", endpoints)

        val triggerTypes = JSONObject()
        AutomationSchema.triggers.forEach { (type, descriptor) ->
            val obj = JSONObject()
            obj.put("source", descriptor.source)
            obj.put("description", descriptor.description)
            obj.put("state", descriptor.state)
            val cfgObj = JSONObject()
            descriptor.config.forEach { param -> cfgObj.put(param.name, param.description) }
            obj.put("configKeys", cfgObj)
            obj.put("templateVars", JSONArray(descriptor.templateVars))
            triggerTypes.put(type, obj)
        }
        root.put("triggerTypes", triggerTypes)

        val actionTypes = JSONObject()
        AutomationSchema.actions.forEach { (type, descriptor) ->
            val obj = JSONObject()
            obj.put("description", descriptor.description)
            val pObj = JSONObject()
            descriptor.params.forEach { param -> pObj.put(param.name, param.description) }
            obj.put("params", pObj)
            if (descriptor.notes.isNotEmpty()) obj.put("notes", descriptor.notes)
            obj.put("requirements", JSONArray(descriptor.requirements))
            obj.put("risk", descriptor.risk)
            obj.put("autonomy", descriptor.autonomy)
            actionTypes.put(type, obj)
        }
        root.put("actionTypes", actionTypes)
        root.put("universalTemplateVariables", JSONArray(AutomationSchema.universalTemplateVariables))

        return root.toString(2)
    }
}
