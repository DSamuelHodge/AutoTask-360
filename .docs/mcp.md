# MCP Server

Stateless MCP over HTTP at `POST /mcp` (protocol **2026-07-28**), implemented in
`mcp/McpHandler.kt` + `mcp/McpTools.kt`.

## Protocol shape

- One JSON-RPC request per HTTP request; **no sessions**, no `initialize`
  handshake, no `Mcp-Session-Id`.
- Every request is self-describing:
  - **Headers**: `MCP-Protocol-Version`, `Mcp-Method`, `Mcp-Name` (mirrored).
  - **Body `params._meta`**: `io.modelcontextprotocol/protocolVersion` and
    `io.modelcontextprotocol/clientCapabilities`.
- Server stores no cross-request state. Tool calls are stateless; anything
  needing state would return an explicit handle.

## Validation

| Check | Error |
|---|---|
| `MCP-Protocol-Version` header ≠ `2026-07-28` | `-32022 Unsupported protocol version` (with `supported`) |
| `Mcp-Method` header ≠ body `method` | `-32020 Header mismatch: Mcp-Method` |
| `tools/call` `Mcp-Name` ≠ `params.name` | `-32020 Header mismatch: Mcp-Name` |
| `_meta.protocolVersion` ≠ header | `-32020 Header/body protocol version mismatch` |
| Missing `_meta.clientCapabilities` | `-32602 Missing required _meta field` |
| Body > 256 KiB | `-32600 Request body too large` |
| Malformed JSON | `-32700 Parse error` |
| Unknown method | `-32601 Method not found` |
| Unknown tool | `-32602 Unknown tool` |

Responses use `resultType: "complete"`, and tool-call failures use
`isError: true` with a text `content` block. Tool results carry
`structuredContent` = the brain's result.

## Tools (16)

Each tool forwards to the brain via the UNIX socket (`BrainClient.call`) with
`owner` defaulted to `derrick` if omitted.

| Tool | Params | What it does |
|---|---|---|
| `aware.sms` | `owner`, `sender`, `smsBody` | Triage inbound SMS: resolve sender, log interaction, auto-capture mentioned contacts, informed notification. |
| `aware.sms.send` | `owner`, `recipient`, `text` | Outbound SMS via native `SEND_SMS` action (resolves recipient by name/phone). |
| `aware.whatsapp` | `owner`, `sender`, `text` | Triage inbound WhatsApp: resolve + log + informed notification. |
| `aware.whatsapp.send` | `owner`, `recipient`, `text` | Send WhatsApp via the WebView bridge (async dispatch). |
| `aware.call` | `owner`, `number` | Incoming-call context flash (resolve caller + role). |
| `aware.capture` | `owner`, `first_name`, `last_name`, `company`, `phone`, `email`, `amount` | Capture a lead/contact (optionally company + starter deal). |
| `aware.meeting` | `owner` | Meeting-prep briefing: next booking + attendee CRM context. |
| `aware.briefing` | `owner` | Daily briefing (calendar + CRM state). |
| `aware.deals` | `owner` | List open deals with next action. |
| `aware.travel` | `owner`, `destination`, `openMaps`, `origin_lat`, `origin_lon` | Drive time/distance (Photon geocode + OSRM), optional maps intent. |
| `aware.sync_contacts` | `owner` | Sync device address book into CRM (idempotent). |
| `aware.open` | `owner`, `url` | Open a URL in the browser (`OPEN_URL`). |
| `aware.search` | `owner`, `query` | DuckDuckGo HTML search → top titles + URLs (rate-limit aware). |
| `aware.email` | `owner`, `to`, `subject`, `body` | Compose Gmail via `mailto:` link (no OAuth). |
| `crm.list_contacts` | `owner` | List CRM contacts. |
| `crm.interactions_for_contact` | `owner`, `contact_id` | Logged interactions for a contact. |

## Client wiring

The phone's MCP server is reached over `adb reverse tcp:8788 tcp:8788`. In
`~/.config/opencode/opencode.jsonc` the `cos` entry points at
`http://127.0.0.1:8788/mcp` with `Authorization: Bearer <token>` (the same
token the brain socket uses, generated once and persisted in the app prefs).
