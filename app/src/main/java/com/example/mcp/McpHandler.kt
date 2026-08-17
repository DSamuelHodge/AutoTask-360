package com.example.mcp

import android.content.Context
import com.example.application.AutomationCommandFacade
import com.example.application.ProfileNotFoundException
import com.example.security.AccessDeniedException
import com.example.security.AccessPrincipal
import com.example.security.ApprovalRequiredException
import com.example.security.CommandContext
import com.example.security.ExternalAccess
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stateless MCP handler for the CoS brain (protocol 2026-07-28).
 *
 * Implements the stateless Streamable HTTP server side: a single POST endpoint
 * that accepts one JSON-RPC request per HTTP request, validates the mirrored
 * headers against the body (`MCP-Protocol-Version`, `Mcp-Method`, `Mcp-Name`),
 * and answers with a single JSON object. There is NO session, NO `initialize`
 * handshake, NO `Mcp-Session-Id`: every request carries its own protocol
 * version + capabilities in `_meta`, and the server stores no cross-request
 * state. Tool calls that need state return an explicit handle (not implemented
 * yet — our aware.* RPCs are naturally stateless).
 *
 * Supported methods: `tools/list`, `tools/call`. Capabilities: `tools` only.
 */
object McpHandler {

    const val PROTOCOL_VERSION = "2026-07-28"
    private const val MAX_BODY_BYTES = 256 * 1024

    /**
     * Handle one POST body + the required headers. Returns a JSON response
     * object to send back, or an HTTP status + JSON-RPC error.
     */
    suspend fun handle(
        context: Context,
        headers: Map<String, String>,
        body: String,
        principal: AccessPrincipal = AccessPrincipal.LOCAL
    ): McpResult {
        // ── Body / size sanity ────────────────────────────────────────────
        if (body.toByteArray().size > MAX_BODY_BYTES) {
            return McpResult.jsonRpc(400, error(-32600, "Request body too large", null))
        }
        val req = try {
            JSONObject(body)
        } catch (e: Exception) {
            return McpResult.jsonRpc(400, error(-32700, "Parse error: ${e.message}", null))
        }
        val method = req.optString("method", "")
        val id = if (req.has("id")) req.opt("id") else null
        val params = req.optJSONObject("params") ?: JSONObject()

        // ── Header ↔ body validation (stateless per-request) ─────────────
        val protoHeader = headers["MCP-Protocol-Version"] ?: headers["mcp-protocol-version"]
        val methodHeader = headers["Mcp-Method"] ?: headers["mcp-method"]
        val nameHeader = headers["Mcp-Name"] ?: headers["mcp-name"]

        if (protoHeader == null || protoHeader != PROTOCOL_VERSION) {
            val supported = JSONArray().put(PROTOCOL_VERSION)
            return McpResult.jsonRpc(
                400, error(-32022, "Unsupported protocol version", JSONObject().put("supported", supported))
            )
        }
        if (methodHeader == null || methodHeader != method) {
            return McpResult.jsonRpc(400, error(-32020, "Header mismatch: Mcp-Method", null))
        }
        // tools/call must mirror params.name in Mcp-Name.
        if (method == "tools/call") {
            val name = params.optString("name", "")
            if (nameHeader == null || nameHeader != name) {
                return McpResult.jsonRpc(400, error(-32020, "Header mismatch: Mcp-Name", null))
            }
        }

        // ── Per-request _meta (required fields) ──────────────────────────
        val meta = params.optJSONObject("_meta") ?: JSONObject()
        val bodyVersion = meta.optString("io.modelcontextprotocol/protocolVersion", "")
        if (bodyVersion.isNotEmpty() && bodyVersion != protoHeader) {
            return McpResult.jsonRpc(400, error(-32020, "Header/body protocol version mismatch", null))
        }
        if (!meta.has("io.modelcontextprotocol/clientCapabilities")) {
            return McpResult.jsonRpc(
                400, error(-32602, "Missing required _meta field: io.modelcontextprotocol/clientCapabilities", null)
            )
        }

        // ── Dispatch ─────────────────────────────────────────────────────
        return when (method) {
            "tools/list" -> handleToolsList(id)
            "tools/call" -> handleToolsCall(context, id, params, principal)
            else -> McpResult.jsonRpc(404, error(-32601, "Method not found: $method", null))
        }
    }

    private fun handleToolsList(id: Any?): McpResult {
        val tools = JSONArray()
        for (t in McpTools.tools) {
            val o = JSONObject()
                .put("name", t.name)
                .put("title", t.title)
                .put("description", t.description)
                .put("inputSchema", t.params)
            tools.put(o)
        }
        val result = JSONObject()
            .put("resultType", "complete")
            .put("tools", tools)
        return McpResult.jsonRpc(200, ok(id, result))
    }

    private suspend fun handleToolsCall(
        context: Context,
        id: Any?,
        params: JSONObject,
        principal: AccessPrincipal
    ): McpResult {
        val name = params.optString("name", "")
        val tool = McpTools.byName[name]
            ?: return McpResult.jsonRpc(404, error(-32602, "Unknown tool: $name", null))

        val access = ExternalAccess.getInstance(context)
        val toolAuth = access.guard.authorizeMcp(principal, name, loopback = true)
        if (!toolAuth.allowed) {
            return McpResult.jsonRpc(
                toolAuth.status,
                error(-32000, toolAuth.message, JSONObject().put("code", toolAuth.code))
            )
        }

        val arguments = params.optJSONObject("arguments") ?: JSONObject()
        if (name.startsWith("autotask.")) {
            return try {
                McpResult.jsonRpc(200, ok(id, toolResult(handleAutoTaskTool(context, name, arguments, principal))))
            } catch (e: ApprovalRequiredException) {
                McpResult.jsonRpc(
                    403,
                    error(-32000, e.message ?: "approval required", JSONObject().put("code", "APPROVAL_REQUIRED").put("actions", JSONArray(e.actions)))
                )
            } catch (e: AccessDeniedException) {
                McpResult.jsonRpc(e.status, error(-32000, e.message ?: "denied", JSONObject().put("code", e.code)))
            } catch (e: ToolException) {
                McpResult.jsonRpc(200, ok(id, toolError(e.message ?: "Tool failed")))
            } catch (e: Exception) {
                McpResult.jsonRpc(200, ok(id, toolError(e.localizedMessage ?: "Tool failed")))
            }
        }

        // Inject the owner default so callers don't have to remember it.
        val rpcParams = JSONObject(arguments.toString())
        if (!rpcParams.has("owner")) rpcParams.put("owner", "derrick")

        // Forward to the brain over the UNIX socket (token-authed).
        val rpcBody = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", System.nanoTime())
            .put("method", name)
            .put("params", rpcParams)
            .toString()

        val response = try {
            com.example.wa.BrainClient.call(context, rpcBody)
        } catch (e: Exception) {
            // Tool execution error (actionable, not a protocol error).
            val result = JSONObject()
                .put("resultType", "complete")
                .put("isError", true)
                .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "Brain unreachable: ${e.message}")))
            return McpResult.jsonRpc(200, ok(id, result))
        }

        val brain = try {
            JSONObject(response)
        } catch (e: Exception) {
            val result = JSONObject()
                .put("resultType", "complete")
                .put("isError", true)
                .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "Malformed brain response: $response")))
            return McpResult.jsonRpc(200, ok(id, result))
        }

        val result = JSONObject()
            .put("resultType", "complete")
            .put("isError", !brain.optBoolean("ok", false))
            .put("content", JSONArray().put(
                JSONObject()
                    .put("type", "text")
                    .put("text", brain.opt("result")?.toString() ?: brain.opt("error")?.toString() ?: brain.toString())
            ))
            .put("structuredContent", brain.optJSONObject("result") ?: brain)
        return McpResult.jsonRpc(200, ok(id, result))
    }

    private suspend fun handleAutoTaskTool(
        context: Context,
        name: String,
        args: JSONObject,
        principal: AccessPrincipal
    ): JSONObject {
        val commands = AutomationCommandFacade.getInstance(context)
        val ctx = CommandContext(principal)
        return when (name) {
            "autotask.schema" -> JSONObject(commands.schemaJson())
            "autotask.capabilities" -> JSONObject(commands.capabilitiesJson())
            "autotask.profiles.list" -> {
                val arr = JSONArray()
                commands.listProfiles().forEach { arr.put(AutomationCommandFacade.profileToJson(it)) }
                JSONObject().put("profiles", arr).put("count", arr.length())
            }
            "autotask.profiles.get" -> {
                val id = args.optString("id", "").trim()
                if (id.isBlank()) throw ToolException("id is required")
                val profile = commands.getProfile(id) ?: throw ProfileNotFoundException(id)
                JSONObject().put("profile", AutomationCommandFacade.profileToJson(profile))
            }
            "autotask.profiles.validate" -> {
                val definition = commands.validateAutomation(args)
                JSONObject()
                    .put("status", "OK")
                    .put("valid", true)
                    .put("definition", AutomationCommandFacade.definitionToJson(definition))
            }
            "autotask.profiles.upsert" -> {
                val profile = commands.upsertProfile(args)
                JSONObject()
                    .put("status", "OK")
                    .put("message", "Profile upserted")
                    .put("profile", AutomationCommandFacade.profileToJson(profile))
            }
            "autotask.profiles.patch" -> {
                val id = args.optString("id", "").trim()
                if (id.isBlank()) throw ToolException("id is required")
                val updated = commands.patchProfile(id, args)
                JSONObject()
                    .put("status", "OK")
                    .put("message", "Profile patched")
                    .put("profile", AutomationCommandFacade.profileToJson(updated))
            }
            "autotask.profiles.delete" -> {
                val id = args.optString("id", "").trim()
                if (id.isBlank()) throw ToolException("id is required")
                if (!commands.deleteProfile(id)) throw ProfileNotFoundException(id)
                JSONObject().put("status", "OK").put("deletedProfileId", id)
            }
            "autotask.events.fire" -> {
                val dryRun = args.optBoolean("dryRun", false)
                if (!dryRun) {
                    val exec = ExternalAccess.getInstance(context).guard.authorize(
                        principal,
                        com.example.security.AccessOperation.EXECUTE_RUNS,
                        true
                    )
                    if (!exec.allowed) throw AccessDeniedException(exec.status, exec.code, exec.message)
                }
                AutomationCommandFacade.eventResultToJson(commands.fireEvent(args, ctx))
            }
            "autotask.runs.request" -> {
                AutomationCommandFacade.eventResultToJson(commands.requestRun(args, ctx))
            }
            "autotask.runs.get" -> {
                val runId = args.optString("runId", "").trim()
                if (runId.isBlank()) throw ToolException("runId is required")
                AutomationCommandFacade.runToJson(commands.getRun(runId))
            }
            "autotask.runs.list" -> {
                val limit = args.optInt("limit", 50).coerceIn(1, 500)
                val profileId = args.optString("profileId", "").trim().ifBlank { null }
                val arr = JSONArray()
                commands.listRuns(limit, profileId).forEach { arr.put(AutomationCommandFacade.runToJson(it)) }
                JSONObject().put("runs", arr).put("count", arr.length())
            }
            "autotask.runs.cancel" -> {
                val runId = args.optString("runId", "").trim()
                if (runId.isBlank()) throw ToolException("runId is required")
                AutomationCommandFacade.runToJson(commands.cancelRun(runId))
            }
            "autotask.runs.retry" -> {
                val runId = args.optString("runId", "").trim()
                if (runId.isBlank()) throw ToolException("runId is required")
                AutomationCommandFacade.runToJson(commands.retryRun(runId))
            }
            "autotask.runs.resume" -> {
                val runId = args.optString("runId", "").trim()
                if (runId.isBlank()) throw ToolException("runId is required")
                AutomationCommandFacade.runToJson(commands.resumeRun(runId))
            }
            "autotask.schedules.list" -> {
                val arr = JSONArray()
                commands.listSchedules().forEach { arr.put(AutomationCommandFacade.scheduleToJson(it)) }
                JSONObject().put("schedules", arr).put("count", arr.length())
            }
            "autotask.schedules.get" -> {
                val profileId = args.optString("profileId", args.optString("id", "")).trim()
                if (profileId.isBlank()) throw ToolException("profileId is required")
                AutomationCommandFacade.scheduleToJson(commands.getSchedule(profileId))
            }
            "autotask.schedules.reconcile" -> {
                val reason = args.optString("reason", "manual").ifBlank { "manual" }
                val arr = JSONArray()
                commands.reconcileSchedules(reason).forEach { arr.put(AutomationCommandFacade.scheduleToJson(it)) }
                JSONObject().put("status", "OK").put("reason", reason).put("schedules", arr).put("count", arr.length())
            }
            "autotask.logs.list" -> {
                val limit = args.optInt("limit", 100).coerceIn(1, 500)
                val arr = JSONArray()
                commands.listLogs(limit).forEach { arr.put(AutomationCommandFacade.logToJson(it)) }
                JSONObject().put("logs", arr).put("count", arr.length())
            }
            else -> throw ToolException("Unknown AutoTask tool: $name")
        }
    }

    private fun toolResult(payload: JSONObject): JSONObject =
        JSONObject()
            .put("resultType", "complete")
            .put("isError", false)
            .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", payload.toString())))
            .put("structuredContent", payload)

    private fun toolError(message: String): JSONObject =
        JSONObject()
            .put("resultType", "complete")
            .put("isError", true)
            .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", message)))

    private fun ok(id: Any?, result: JSONObject): JSONObject {
        val o = JSONObject().put("jsonrpc", "2.0")
        if (id != null) o.put("id", id)
        o.put("result", result)
        return o
    }

    private fun error(code: Int, message: String, data: JSONObject?): JSONObject {
        val o = JSONObject().put("jsonrpc", "2.0").put("error", JSONObject().put("code", code).put("message", message))
        if (data != null) o.getJSONObject("error").put("data", data)
        return o
    }

    /** Simple error object for Origin rejection (no id). */
    fun error(message: String): JSONObject =
        JSONObject().put("jsonrpc", "2.0").put("error", JSONObject().put("code", -32600).put("message", message))

    data class McpResult(val status: Int, val json: JSONObject) {
        companion object {
            fun jsonRpc(status: Int, json: JSONObject) = McpResult(status, json)
        }
    }

    private class ToolException(message: String) : Exception(message)
}
