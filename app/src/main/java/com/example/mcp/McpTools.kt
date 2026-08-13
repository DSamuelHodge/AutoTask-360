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

    private fun schema(vararg props: Pair<String, JSONObject>): JSONObject {
        val o = JSONObject().put("type", "object")
        val properties = JSONObject()
        val required = org.json.JSONArray()
        for ((k, v) in props) {
            properties.put(k, v.remove("__required"))
            if (v.optBoolean("__required", false)) required.put(k)
        }
        if (required.length() > 0) o.put("required", required)
        o.put("properties", properties)
        return o
    }
}
