package com.example.server.mcp

import com.example.engine.RiskClass

/**
 * Bounded MCP tool surface for AutoTask (#22).
 *
 * Declarative sketch only: this file describes the tool surface as data so that
 * `docs/MCP_TOOL_SCHEMA.md` and any future adapter cannot drift apart. It deliberately
 * contains no transport, no server, and no wiring into [com.example.server.KtorLoopbackServer].
 *
 * Two invariants this file exists to make mechanically checkable:
 *  1. The tool set is CLOSED — see [McpToolCatalog.tools]. There is no generic shell/exec tool
 *     and none may be added; see the non-goals table in the doc.
 *  2. Every tool maps onto an endpoint the loopback server already implements, so an adapter
 *     over 127.0.0.1:8788 is a translation layer rather than a second implementation.
 *
 * TODO(#2): a real adapter would bind these to an MCP transport and enforce
 *  [McpTool.scope] plus ExecutionPolicy gating before proxying to the loopback server.
 */

/** Coarse, additive capability scopes. A credential carries a set; a tool requires one. */
enum class McpScope(val label: String) {
    /** Read-only observation. Safe default for any agent. */
    READ("read"),

    /** Mutates stored automation profiles or clears logs. Requires the agent-write kill switch. */
    WRITE("write"),

    /**
     * Causes immediate device side effects (live [McpToolCatalog.EVENTS_FIRE]).
     * Intentionally NOT implied by [WRITE]: an agent may author profiles yet be unable to fire them.
     */
    EXECUTE("execute")
}

/** HTTP verb of the loopback endpoint a tool proxies. */
enum class HttpVerb { GET, POST, PATCH, DELETE }

/**
 * One MCP tool.
 *
 * @param name Stable tool identifier exposed to the agent.
 * @param description One-line human/agent-facing summary.
 * @param verb HTTP verb of the backing loopback endpoint.
 * @param path Backing loopback path, `{id}` denoting a path parameter.
 * @param scope Scope a credential must hold to invoke this tool.
 * @param risk CEILING of what invoking this tool can cause — not the cost of the call itself.
 *   `profiles_upsert` is [RiskClass.SECURE_SETTINGS_MUTATION] because a stored profile may
 *   contain a DND action, even though the write is only a database row.
 * @param inputFields Accepted input fields; empty means the tool takes `{}`.
 * @param authNotes Constraints an adapter MUST honour beyond scope checking.
 */
data class McpTool(
    val name: String,
    val description: String,
    val verb: HttpVerb,
    val path: String,
    val scope: McpScope,
    val risk: RiskClass,
    val inputFields: List<McpField> = emptyList(),
    val authNotes: String
)

/** A single input field constraint. Bounds are enforced by the adapter before proxying. */
data class McpField(
    val name: String,
    val type: String,
    val required: Boolean = false,
    val maxLength: Int? = null,
    val minimum: Int? = null,
    val maximum: Int? = null,
    val notes: String = ""
)

object McpToolCatalog {

    const val STATUS = "status"
    const val CAPABILITIES = "capabilities"
    const val SCHEMA = "schema"
    const val PROFILES_LIST = "profiles_list"
    const val PROFILES_GET = "profiles_get"
    const val PROFILES_UPSERT = "profiles_upsert"
    const val PROFILES_PATCH = "profiles_patch"
    const val PROFILES_DELETE = "profiles_delete"
    const val EVENTS_FIRE = "events_fire"
    const val LOGS_RECENT = "logs_recent"
    const val LOGS_CLEAR = "logs_clear"

    /** Max actions accepted in a single authored profile. */
    const val MAX_ACTIONS_PER_PROFILE = 32

    /** Max profile id / target id length accepted from a caller. */
    const val MAX_ID_LENGTH = 128

    /** Hard cap on `logs_recent` page size regardless of what the caller asks for. */
    const val MAX_LOG_LIMIT = 500

    /**
     * Trigger types an agent may inject via [EVENTS_FIRE].
     *
     * Restricted to MANUAL so an agent cannot forge an `SMS`/`CALL`/`NOTIFICATION` event to
     * impersonate a real-world signal and launder a trusted trigger.
     */
    val INJECTABLE_TRIGGER_TYPES: Set<String> = setOf("MANUAL")

    /** The closed tool set. Adding a generic shell/exec tool here is a trust-model redesign. */
    val tools: List<McpTool> = listOf(
        McpTool(
            name = STATUS,
            description = "Engine liveness and readiness snapshot.",
            verb = HttpVerb.GET,
            path = "/v1/status",
            scope = McpScope.READ,
            risk = RiskClass.OBSERVE_ONLY,
            authNotes = "Safe first call. In remote mode omit relay_target and provider_uri, " +
                "which describe internal attack surface."
        ),
        McpTool(
            name = CAPABILITIES,
            description = "Per-action readiness, risk, and permission posture for this device.",
            verb = HttpVerb.GET,
            path = "/v1/capabilities",
            scope = McpScope.READ,
            risk = RiskClass.OBSERVE_ONLY,
            authNotes = "Agent MUST check actions[type].ready before authoring a profile. " +
                "Remote adapters should strip provisioningHints (operator-facing ADB templates)."
        ),
        McpTool(
            name = SCHEMA,
            description = "Machine-readable trigger, condition, and action schema.",
            verb = HttpVerb.GET,
            path = "/v1/schema",
            scope = McpScope.READ,
            risk = RiskClass.OBSERVE_ONLY,
            authNotes = "Static and non-sensitive."
        ),
        McpTool(
            name = PROFILES_LIST,
            description = "Enumerate all automation profiles.",
            verb = HttpVerb.GET,
            path = "/v1/profiles",
            scope = McpScope.READ,
            risk = RiskClass.OBSERVE_ONLY,
            authNotes = "Profile bodies may embed user content (SMS text, numbers, URLs); " +
                "a data-egress path in remote mode."
        ),
        McpTool(
            name = PROFILES_GET,
            description = "Fetch a single automation profile by id.",
            verb = HttpVerb.GET,
            path = "/v1/profiles/{id}",
            scope = McpScope.READ,
            risk = RiskClass.OBSERVE_ONLY,
            inputFields = listOf(
                McpField("id", "string", required = true, maxLength = MAX_ID_LENGTH)
            ),
            authNotes = "Reject ids containing path separators or exceeding maxLength before proxying."
        ),
        McpTool(
            name = LOGS_RECENT,
            description = "Recent execution logs, newest first.",
            verb = HttpVerb.GET,
            path = "/v1/logs",
            scope = McpScope.READ,
            risk = RiskClass.OBSERVE_ONLY,
            inputFields = listOf(
                McpField("limit", "integer", minimum = 1, maximum = MAX_LOG_LIMIT)
            ),
            authNotes = "Highest-density egress path in the read scope: results may contain " +
                "message bodies and recipients. Cap limit; consider redacting actionsResultJson."
        ),
        McpTool(
            name = PROFILES_UPSERT,
            description = "Create or replace an automation profile (validated).",
            verb = HttpVerb.POST,
            path = "/v1/profiles",
            scope = McpScope.WRITE,
            risk = RiskClass.SECURE_SETTINGS_MUTATION,
            inputFields = listOf(
                McpField("id", "string", required = true, maxLength = MAX_ID_LENGTH),
                McpField("name", "string", required = true, maxLength = 256),
                McpField("description", "string", maxLength = 1024),
                McpField("isEnabled", "boolean", notes = "Forced false for remote callers."),
                McpField("triggerType", "string", required = true),
                McpField("triggerConfigJson", "object"),
                McpField("conditionsJson", "object"),
                McpField(
                    "actionsJson", "array",
                    maximum = MAX_ACTIONS_PER_PROFILE,
                    notes = "Each action type must be in ActionRisk.knownTypes()."
                ),
                McpField("cooldownMs", "integer", minimum = 0),
                McpField("priority", "integer")
            ),
            authNotes = "Primary escalation path: a profile is stored, deferred code. Gate on " +
                "ExecutionPolicy.isAgentWriteAllowed(); persist remote-authored profiles with " +
                "isEnabled=false so a human must arm them."
        ),
        McpTool(
            name = PROFILES_PATCH,
            description = "Partially update an existing automation profile.",
            verb = HttpVerb.PATCH,
            path = "/v1/profiles/{id}",
            scope = McpScope.WRITE,
            risk = RiskClass.SECURE_SETTINGS_MUTATION,
            inputFields = listOf(
                McpField("id", "string", required = true, maxLength = MAX_ID_LENGTH),
                McpField("patch", "object", required = true)
            ),
            authNotes = "An isEnabled false->true transition arms a profile the agent may not " +
                "have authored; treat as high-risk and audit the before/after value."
        ),
        McpTool(
            name = PROFILES_DELETE,
            description = "Delete an automation profile by id.",
            verb = HttpVerb.DELETE,
            path = "/v1/profiles/{id}",
            scope = McpScope.WRITE,
            risk = RiskClass.LOCAL_UX,
            inputFields = listOf(
                McpField("id", "string", required = true, maxLength = MAX_ID_LENGTH)
            ),
            authNotes = "Irreversible (no soft delete). Deleting safety-relevant automations is a " +
                "denial-of-function attack; audit the full body before deletion."
        ),
        McpTool(
            name = LOGS_CLEAR,
            description = "Delete all execution logs.",
            verb = HttpVerb.DELETE,
            path = "/v1/logs",
            scope = McpScope.WRITE,
            risk = RiskClass.LOCAL_UX,
            authNotes = "Anti-forensic. The remote audit sink must be append-only and " +
                "unreachable from this tool; consider denying it to remote credentials entirely."
        ),
        McpTool(
            name = EVENTS_FIRE,
            description = "Fire a synthetic MANUAL event; dry-run by default.",
            verb = HttpVerb.POST,
            path = "/v1/events",
            scope = McpScope.EXECUTE,
            risk = RiskClass.SECURE_SETTINGS_MUTATION,
            inputFields = listOf(
                McpField("triggerType", "string", notes = "Restricted to INJECTABLE_TRIGGER_TYPES."),
                McpField("targetProfileId", "string", maxLength = MAX_ID_LENGTH),
                McpField("dryRun", "boolean", notes = "Defaults to TRUE at the MCP layer."),
                McpField("payload", "object")
            ),
            authNotes = "Only tool with immediate side effects. Live fire needs EXECUTE scope, " +
                "ExecutionPolicy.executionEnabled, and isHighRiskAllowed() for high-risk actions."
        )
    )

    /** Tool names that never mutate anything. */
    val readOnlyToolNames: Set<String> =
        tools.filter { it.scope == McpScope.READ }.map { it.name }.toSet()

    fun byName(name: String): McpTool? = tools.firstOrNull { it.name == name }

    /**
     * Scope check. Note [McpScope.EXECUTE] is not implied by [McpScope.WRITE]; a credential
     * must hold each scope explicitly.
     */
    fun isAllowed(toolName: String, grantedScopes: Set<McpScope>): Boolean {
        val tool = byName(toolName) ?: return false
        return tool.scope in grantedScopes
    }

    /**
     * `events_fire` in dry-run mode plans without executing, so it only needs READ.
     * A live fire always needs EXECUTE.
     */
    fun requiredScopeForEventsFire(dryRun: Boolean): McpScope =
        if (dryRun) McpScope.READ else McpScope.EXECUTE
}
