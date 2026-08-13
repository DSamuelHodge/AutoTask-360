package com.example.mcp

import android.content.Context
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
    fun handle(context: Context, headers: Map<String, String>, body: String): McpResult {
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
            "tools/call" -> handleToolsCall(context, id, params)
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

    private fun handleToolsCall(context: Context, id: Any?, params: JSONObject): McpResult {
        val name = params.optString("name", "")
        val tool = McpTools.byName[name]
            ?: return McpResult.jsonRpc(404, error(-32602, "Unknown tool: $name", null))

        val arguments = params.optJSONObject("arguments") ?: JSONObject()
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
}
