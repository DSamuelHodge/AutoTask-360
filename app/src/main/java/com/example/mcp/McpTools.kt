package com.example.mcp

import org.json.JSONObject

/**
 * MCP tool registry for the CoS brain (stateless MCP, 2026-07-28).
 *
 * Each tool is a thin, stateless wrapper over an `aware.*` / `crm.*` RPC. The
 * protocol is stateless: no sessions, no initialize handshake — every request
 * self-describes via `_meta` + mirrored HTTP headers, and every tool call
 * carries everything it needs. State (if ever needed) is an explicit handle a
 * tool returns and the client passes back — never implicit server state.
 */
object McpTools {

    /** name → Tool definition. Deterministic order for client caching. */
    val tools: List<Tool> = listOf(
        Tool(
            name = "autotask.schema",
            title = "AutoTask Schema",
            description = "Return the AutoTask 2.1 trigger/action schema, REST endpoint catalog, " +
                "and MCP tool names. Call this before creating or patching profiles.",
            params = schema()
        ),
        Tool(
            name = "autotask.capabilities",
            title = "AutoTask Capabilities",
            description = "Return live device/app capability state, including runtime permissions " +
                "and policy gates that determine whether actions can execute.",
            params = schema()
        ),
        Tool(
            name = "autotask.profiles.list",
            title = "List Automation Profiles",
            description = "Resolve saved profiles. Always pass q (or actionType) — do not list " +
                "the full catalog. Matches id, name, description, trigger, and action types " +
                "(sms → SEND_SMS). Same filter as GET /v1/profiles?q=.",
            params = schema(
                "q" to string("Free-text intent or keyword, e.g. sms, morning brief, flashlight"),
                "search" to string("Alias of q"),
                "id" to string("Exact profile id"),
                "actionType" to string("Exact action type, e.g. SEND_SMS"),
                "triggerType" to string("Exact trigger type, e.g. MANUAL"),
                "enabled" to bool("If set, only enabled or only disabled profiles"),
                "limit" to number("Max matches; default 20 when q is set, max 100"),
            )
        ),
        Tool(
            name = "autotask.profiles.get",
            title = "Get Automation Profile",
            description = "Fetch a single automation profile by id.",
            params = schema(
                "id" to string("Automation profile id", true),
            )
        ),
        Tool(
            name = "autotask.profiles.validate",
            title = "Validate Automation Profile",
            description = "Validate an automation definition without persisting or executing it. " +
                "Accepts the same fields as upsert, including the canonical trigger/steps shape.",
            params = schema(
                "id" to string("Stable automation id"),
                "name" to string("Human-readable automation name"),
                "description" to string("Short explanation of what this automation does"),
                "isEnabled" to bool("Whether the automation should run when its trigger fires"),
                "enabled" to bool("Alias of isEnabled"),
                "triggerType" to string("Trigger type from autotask.schema"),
                "trigger" to obj("Canonical trigger object {type, config}"),
                "triggerConfig" to obj("Trigger filter/config object"),
                "triggerConfigJson" to string("Trigger filter/config as a JSON string"),
                "conditions" to obj("Runtime condition gates object or array"),
                "conditionsJson" to string("Runtime condition gates as a JSON string"),
                "actions" to arr("Ordered action array, each item shaped as {type, params}"),
                "actionsJson" to string("Ordered action array as a JSON string"),
                "steps" to arr("Canonical action array, each item shaped as {type, params}"),
                "cooldownMs" to number("Minimum milliseconds between non-manual executions"),
                "priority" to number("Higher priority profiles run first for the same trigger"),
                "executionPolicy" to obj("Canonical execution policy {cooldownMs, priority}"),
                "schemaVersion" to number("Definition schema version; defaults to the current version"),
            )
        ),
        Tool(
            name = "autotask.profiles.upsert",
            title = "Create or Replace Automation Profile",
            description = "Create or replace a persistent multi-step automation profile. Pass " +
                "`actions` as an ordered array of {type, params}; triggerConfig and conditions " +
                "may be JSON objects.",
            params = schema(
                "id" to string("Stable automation id, e.g. 'cos-morning-briefing'", true),
                "name" to string("Human-readable automation name", true),
                "description" to string("Short explanation of what this automation does"),
                "isEnabled" to bool("Whether the automation should run when its trigger fires"),
                "triggerType" to string("Trigger type from autotask.schema, e.g. MANUAL, TIME, SMS, SCHEDULE", true),
                "triggerConfig" to obj("Trigger filter/config object"),
                "triggerConfigJson" to string("Trigger filter/config as a JSON string"),
                "conditions" to obj("Runtime condition gates object"),
                "conditionsJson" to string("Runtime condition gates as a JSON string"),
                "actions" to arr("Ordered action array, each item shaped as {type, params}"),
                "actionsJson" to string("Ordered action array as a JSON string"),
                "cooldownMs" to number("Minimum milliseconds between non-manual executions"),
                "priority" to number("Higher priority profiles run first for the same trigger"),
            )
        ),
        Tool(
            name = "autotask.profiles.patch",
            title = "Patch Automation Profile",
            description = "Patch selected fields on an existing automation profile without replacing " +
                "the whole object. Supports the same mutable fields as upsert.",
            params = schema(
                "id" to string("Automation profile id", true),
                "name" to string("Human-readable automation name"),
                "description" to string("Short explanation of what this automation does"),
                "isEnabled" to bool("Whether the automation should run when its trigger fires"),
                "triggerType" to string("Trigger type from autotask.schema"),
                "triggerConfig" to obj("Trigger filter/config object"),
                "triggerConfigJson" to string("Trigger filter/config as a JSON string"),
                "conditions" to obj("Runtime condition gates object"),
                "conditionsJson" to string("Runtime condition gates as a JSON string"),
                "actions" to arr("Ordered action array, each item shaped as {type, params}"),
                "actionsJson" to string("Ordered action array as a JSON string"),
                "cooldownMs" to number("Minimum milliseconds between non-manual executions"),
                "priority" to number("Higher priority profiles run first for the same trigger"),
            )
        ),
        Tool(
            name = "autotask.profiles.delete",
            title = "Delete Automation Profile",
            description = "Delete an automation profile by id.",
            params = schema(
                "id" to string("Automation profile id", true),
            )
        ),
        Tool(
            name = "autotask.events.fire",
            title = "Fire Automation Event",
            description = "Fire or dry-run an automation event. Use dryRun=true to validate which " +
                "profiles would run before executing actions. For direct execution, use " +
                "triggerType=MANUAL with profileId. Returns runIds for durable observation.",
            params = schema(
                "triggerType" to string("Trigger type to fire; defaults to MANUAL"),
                "profileId" to string("Optional target profile id for MANUAL events"),
                "dryRun" to bool("When true, report planned profiles without executing actions"),
                "payload" to obj("Event payload available to matchers and template variables"),
                "eventId" to string("Optional stable event id for replay protection"),
                "dedupeKey" to string("Optional dedupe key; duplicate keys reuse the first event"),
                "correlationId" to string("Optional correlation id linking related events"),
                "idempotencyKey" to string("Optional idempotency key; duplicate keys reuse the first event"),
                "source" to string("Event source, e.g. api, sms, notification"),
            )
        ),
        Tool(
            name = "autotask.runs.request",
            title = "Request Automation Run",
            description = "Enqueue a durable automation run and return runId. Supports eventId, " +
                "dedupeKey, and idempotencyKey. Long WAIT steps persist a continuation instead of blocking.",
            params = schema(
                "triggerType" to string("Trigger type to fire; defaults to MANUAL"),
                "profileId" to string("Optional target profile id for MANUAL events"),
                "payload" to obj("Event payload available to matchers and template variables"),
                "eventId" to string("Optional stable event id for replay protection"),
                "dedupeKey" to string("Optional dedupe key"),
                "correlationId" to string("Optional correlation id"),
                "idempotencyKey" to string("Optional idempotency key"),
            )
        ),
        Tool(
            name = "autotask.runs.get",
            title = "Get Automation Run",
            description = "Fetch a durable run and its step checkpoints by runId.",
            params = schema(
                "runId" to string("Run id", true),
            )
        ),
        Tool(
            name = "autotask.runs.list",
            title = "List Automation Runs",
            description = "List recent durable automation runs, optionally filtered by profileId.",
            params = schema(
                "limit" to number("Maximum number of runs to return; defaults to 50"),
                "profileId" to string("Optional profile id filter"),
            )
        ),
        Tool(
            name = "autotask.runs.cancel",
            title = "Cancel Automation Run",
            description = "Cancel a queued, running, or waiting run. Terminal runs are unchanged.",
            params = schema(
                "runId" to string("Run id", true),
            )
        ),
        Tool(
            name = "autotask.runs.retry",
            title = "Retry Automation Run",
            description = "Retry a terminal run from the first step. Bounded by maxAttempts.",
            params = schema(
                "runId" to string("Run id", true),
            )
        ),
        Tool(
            name = "autotask.runs.resume",
            title = "Resume Automation Run",
            description = "Resume an interrupted or waiting run from its last checkpoint.",
            params = schema(
                "runId" to string("Run id", true),
            )
        ),
        Tool(
            name = "autotask.schedules.list",
            title = "List Schedules",
            description = "List persisted schedule registrations and next-fire times for TIME, " +
                "SCHEDULE, and SUNRISE_SUNSET automations.",
            params = schema()
        ),
        Tool(
            name = "autotask.schedules.get",
            title = "Get Schedule",
            description = "Fetch the persisted next-fire registration for a profile id.",
            params = schema(
                "profileId" to string("Automation profile id", true),
            )
        ),
        Tool(
            name = "autotask.schedules.reconcile",
            title = "Reconcile Schedules",
            description = "Recalculate and re-register next-fire times after boot, timezone, " +
                "profile, or missed-delivery recovery. reason=timezone skips catch-up.",
            params = schema(
                "reason" to string("Reconciliation reason, e.g. boot, timezone, time_changed, update, manual"),
            )
        ),
        Tool(
            name = "autotask.logs.list",
            title = "List Automation Logs",
            description = "List recent automation execution logs.",
            params = schema(
                "limit" to number("Maximum number of logs to return; defaults to 100"),
            )
        ),
        Tool(
            name = "aware.sms",
            title = "SMS Triage",
            description = "Triage an inbound SMS: resolve the sender in the CRM, log the " +
                "interaction, auto-capture any mentioned contacts, and surface an informed " +
                "notification with context (VIP status, open deals).",
            params = schema(
                "owner" to string("CRM owner id (e.g. 'derrick')", true),
                "sender" to string("Sender phone number", true),
                "smsBody" to string("The SMS text", true),
            )
        ),
        Tool(
            name = "aware.whatsapp",
            title = "WhatsApp Triage",
            description = "Triage an inbound WhatsApp message: resolve the sender by name in " +
                "the CRM, log the interaction, and surface an informed notification with context.",
            params = schema(
                "owner" to string("CRM owner id (e.g. 'derrick')", true),
                "sender" to string("Sender display name", true),
                "text" to string("The message text", true),
            )
        ),
        Tool(
            name = "aware.whatsapp.send",
            title = "Send WhatsApp",
            description = "Send a WhatsApp message via the on-device WebView bridge. Resolves " +
                "the recipient by name or phone in the CRM. Returns immediately (async dispatch); " +
                "delivery is confirmed by the bridge's send result / informed notification.",
            params = schema(
                "owner" to string("CRM owner id (e.g. 'derrick')", true),
                "recipient" to string("Recipient name or full international number (+1...)"),
                "text" to string("Message body", true),
            )
        ),
        Tool(
            name = "aware.call",
            title = "Incoming Call Context",
            description = "Resolve an incoming call's number in the CRM and surface an informed " +
                "notification with contact context.",
            params = schema(
                "owner" to string("CRM owner id (e.g. 'derrick')", true),
                "number" to string("Caller phone number", true),
            )
        ),
        Tool(
            name = "aware.capture",
            title = "Capture Lead",
            description = "Capture a new contact/lead into the CRM (optionally at a company, " +
                "with phone/email and a starter deal amount).",
            params = schema(
                "owner" to string("CRM owner id (e.g. 'derrick')", true),
                "first_name" to string("Contact first name", true),
                "last_name" to string("Contact last name", true),
                "company" to string("Company name (created if missing)"),
                "phone" to string("Phone number"),
                "email" to string("Email address"),
                "amount" to number("Starter deal amount (creates a deal if set)"),
            )
        ),
        Tool(
            name = "aware.meeting",
            title = "Meeting Briefing",
            description = "Produce a briefing for an upcoming meeting: attendees, open deals, " +
                "and relevant contact context.",
            params = schema(
                "owner" to string("CRM owner id (e.g. 'derrick')", true),
            )
        ),
        Tool(
            name = "aware.briefing",
            title = "Daily Briefing",
            description = "Produce a daily briefing of calendar + CRM state.",
            params = schema(
                "owner" to string("CRM owner id (e.g. 'derrick')", true),
            )
        ),
        Tool(
            name = "aware.deals",
            title = "Open Deals",
            description = "List open deals in the CRM.",
            params = schema(
                "owner" to string("CRM owner id (e.g. 'derrick')", true),
            )
        ),
        Tool(
            name = "aware.travel",
            title = "Travel Time",
            description = "Compute drive time/distance to a destination from current GPS and " +
                "surface it (optionally opening navigation).",
            params = schema(
                "owner" to string("CRM owner id (e.g. 'derrick')", true),
                "destination" to string("Destination address", true),
                "openMaps" to bool("Open Google Maps navigation with the route"),
            )
        ),
        Tool(
            name = "aware.sync_contacts",
            title = "Sync Contacts",
            description = "Sync the device address book into the CRM (idempotent; creates new " +
                "contacts, skips existing).",
            params = schema(
                "owner" to string("CRM owner id (e.g. 'derrick')", true),
            )
        ),
        Tool(
            name = "aware.sms.send",
            title = "Send SMS",
            description = "Send an outbound SMS via the native SEND_SMS action. Resolves the " +
                "recipient by name or phone in the CRM. Returns immediately (async dispatch).",
            params = schema(
                "owner" to string("CRM owner id (e.g. 'derrick')", true),
                "recipient" to string("Recipient name or full international number (+1...)"),
                "text" to string("SMS body", true),
            )
        ),
        Tool(
            name = "aware.open",
            title = "Open in Browser",
            description = "Open a URL in the phone's default web browser.",
            params = schema(
                "owner" to string("CRM owner id (e.g. 'derrick')", true),
                "url" to string("Web address to open (http/https)", true),
            )
        ),
        Tool(
            name = "aware.search",
            title = "Web Search",
            description = "Search the web (DuckDuckGo) and return the top result titles + URLs.",
            params = schema(
                "owner" to string("CRM owner id (e.g. 'derrick')", true),
                "query" to string("Search query", true),
            )
        ),
        Tool(
            name = "aware.email",
            title = "Compose Email",
            description = "Draft an email in the phone's Gmail app via a mailto: link (no OAuth; " +
                "compose only).",
            params = schema(
                "owner" to string("CRM owner id (e.g. 'derrick')", true),
                "to" to string("Recipient email address", true),
                "subject" to string("Email subject"),
                "body" to string("Email body"),
            )
        ),
        Tool(
            name = "crm.list_contacts",
            title = "List Contacts",
            description = "List all CRM contacts.",
            params = schema(
                "owner" to string("CRM owner id (e.g. 'derrick')", true),
            )
        ),
        Tool(
            name = "crm.interactions_for_contact",
            title = "Contact Interactions",
            description = "List logged interactions (calls, SMS, WhatsApp, meetings, notes) for " +
                "a contact.",
            params = schema(
                "owner" to string("CRM owner id (e.g. 'derrick')", true),
                "contact_id" to string("Contact UUID", true),
            )
        ),
    )

    val byName: Map<String, Tool> = tools.associateBy { it.name }

    data class Tool(
        val name: String,
        val title: String,
        val description: String,
        val params: JSONObject,
    )

    private fun string(desc: String, required: Boolean = false): JSONObject {
        val o = JSONObject().put("type", "string")
        if (desc.isNotBlank()) o.put("description", desc)
        if (required) o.put("__required", true)
        return o
    }

    private fun number(desc: String): JSONObject =
        JSONObject().put("type", "number").put("description", desc)

    private fun bool(desc: String): JSONObject =
        JSONObject().put("type", "boolean").put("description", desc)

    private fun obj(desc: String): JSONObject =
        JSONObject().put("type", "object").put("description", desc)

    private fun arr(desc: String): JSONObject =
        JSONObject().put("type", "array").put("description", desc)

    private fun schema(vararg props: Pair<String, JSONObject>): JSONObject {
        val o = JSONObject().put("type", "object")
        val properties = JSONObject()
        val required = org.json.JSONArray()
        for ((k, v) in props) {
            val isRequired = v.optBoolean("__required", false)
            v.remove("__required")
            properties.put(k, v)
            if (isRequired) required.put(k)
        }
        if (required.length() > 0) o.put("required", required)
        o.put("properties", properties)
        return o
    }
}
