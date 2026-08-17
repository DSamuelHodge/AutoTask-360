package com.example.engine

import com.example.BuildConfig
import com.example.domain.AutomationSchema
import com.example.mcp.McpTools
import org.json.JSONArray
import org.json.JSONObject

object SchemaProvider {

    fun getSchemaJson(): String {
        val root = JSONObject()

        root.put("service", "AutoTask Tool Server Engine")
        root.put("version", BuildConfig.VERSION_NAME)
        root.put("versionCode", BuildConfig.VERSION_CODE)
        root.put("schemaVersion", AutomationSchema.CURRENT_VERSION)
        root.put("architecture", "Command facade. Policy is data. Execution is deterministic.")
        root.put("capabilitiesEndpoint", "/v1/capabilities")
        root.put("mcpEndpoint", "/mcp")
        root.put("mcpProtocolVersion", "2026-07-28")
        val endpoints = JSONObject()
        fun add(name: String, method: String, path: String, scope: String, purpose: String, extra: JSONObject.() -> Unit = {}) {
            endpoints.put(
                name,
                JSONObject()
                    .put("method", method)
                    .put("path", path)
                    .put("scope", scope)
                    .put("purpose", purpose)
                    .also { extra(it) }
            )
        }
        add("status", "GET", "/v1/status", "READ", "Engine, bind mode, counts, product version, uptime")
        add("schema", "GET", "/v1/schema", "READ", "Triggers, actions, endpoints, and template variables")
        add("capabilities", "GET", "/v1/capabilities", "READ", "Live permissions, special access, and agent policy")
        add("profiles", "GET", "/v1/profiles", "READ", "List automation profiles")
        add("profile", "GET", "/v1/profiles/{id}", "READ", "Read one profile")
        add("profilesValidate", "POST", "/v1/profiles/validate", "PROFILE_WRITE", "Validate a definition without persisting")
        add("profilesUpsert", "POST", "/v1/profiles", "PROFILE_WRITE", "Create or replace a profile")
        add("profilesPatch", "PATCH", "/v1/profiles/{id}", "PROFILE_WRITE", "Patch selected profile fields")
        add("profilesDelete", "DELETE", "/v1/profiles/{id}", "PROFILE_WRITE", "Delete a profile")
        add("events", "POST", "/v1/events", "READ dryRun / EXECUTE otherwise", "Fire or dry-run an event; returns runIds") {
            put(
                "canonicalBody",
                JSONObject()
                    .put("triggerType", "MANUAL")
                    .put("profileId", "optional target profile ID; omit only for broadcast")
                    .put("dryRun", "Boolean; planned profiles only when true")
                    .put("payload", "JSONObject event payload")
                    .put("idempotencyKey", "optional")
            )
            put("aliasesAccepted", JSONArray(listOf("type", "trigger_type", "profile_id", "dry_run")))
        }
        add("runsRequest", "POST", "/v1/runs", "EXECUTE", "Enqueue a durable run")
        add("runs", "GET", "/v1/runs", "READ", "List durable runs")
        add("run", "GET", "/v1/runs/{id}", "READ", "Get run and step checkpoints")
        add("runCancel", "POST", "/v1/runs/{id}/cancel", "EXECUTE", "Cancel a queued, running, or waiting run")
        add("runRetry", "POST", "/v1/runs/{id}/retry", "EXECUTE", "Retry a terminal run")
        add("runResume", "POST", "/v1/runs/{id}/resume", "EXECUTE", "Resume an interrupted or waiting run")
        add("schedules", "GET", "/v1/schedules", "READ", "List next-fire registrations")
        add("schedule", "GET", "/v1/schedules/{id}", "READ", "Get one schedule by profile id")
        add("schedulesReconcile", "POST", "/v1/schedules/reconcile", "PROFILE_WRITE", "Recalculate and re-register next fires")
        add("logs", "GET", "/v1/logs", "READ", "Recent execution logs")
        add("logsClear", "DELETE", "/v1/logs", "PROFILE_WRITE", "Clear execution logs")
        add("pairingStart", "POST", "/v1/pairing/start", "LOOPBACK", "Issue a 6-digit pairing code")
        add("pairingComplete", "POST", "/v1/pairing/complete", "LOOPBACK", "Exchange code for an atc- token once")
        add("pairingCredentials", "GET", "/v1/pairing/credentials", "LOOPBACK", "List hashed paired credentials")
        add("pairingRevoke", "POST", "/v1/pairing/revoke", "LOOPBACK", "Revoke a paired credential")
        add("pairingLan", "POST", "/v1/pairing/lan", "LOOPBACK", "Enable LAN bind after a live credential exists")
        add("brainStatus", "GET", "/v1/brain/status", "READ", "Rust brain supervisor health")
        add("brain", "POST", "/v1/brain", "EXECUTE", "Proxy JSON-RPC to the internal brain socket")
        add("http", "POST", "/v1/http", "EXECUTE", "Outbound HTTP proxy; high risk")
        add("contacts", "POST", "/v1/contacts", "READ", "Device address book; sensitive")
        add("location", "POST", "/v1/location", "READ", "GPS fix; sensitive")
        add("screen", "GET", "/v1/screen", "UI_CONTROL", "Accessibility tree; sensitive")
        add("uiTap", "POST", "/v1/ui/tap", "UI_CONTROL", "Tap coordinates")
        add("uiType", "POST", "/v1/ui/type", "UI_CONTROL", "Type into the focused field")
        add("uiGlobal", "POST", "/v1/ui/global", "UI_CONTROL", "back, home, recents, notifications, quick_settings")
        add("otaStatus", "GET", "/v1/ota/status", "READ", "Installed version and update readiness")
        add("otaConfig", "POST", "/v1/ota/config", "OTA", "Set update manifest URL")
        add("otaCheck", "POST", "/v1/ota/check", "OTA", "Compare remote manifest")
        add("otaInstall", "POST", "/v1/ota/install", "OTA", "Download, verify, and request install")
        add("waStatus", "GET", "/v1/wa/status", "READ", "WhatsApp bridge state")
        add("waDebug", "POST", "/v1/wa/debug", "EXECUTE", "Probe the WhatsApp WebView")
        add("waSend", "POST", "/v1/wa/send", "EXECUTE", "Send WhatsApp; high risk")
        add("mcp", "POST", "/mcp", "READ then per-tool", "Stateless MCP tools/list and tools/call")
        root.put("endpoints", endpoints)
        root.put(
            "mcpTools",
            JSONArray(McpTools.tools.filter { it.name.startsWith("autotask.") }.map { it.name })
        )

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
