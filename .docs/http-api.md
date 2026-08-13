# HTTP API

All endpoints are served by Ktor on `127.0.0.1:8788` (loopback only) inside
`KtorLoopbackServer.kt`. The phone is reachable from a host machine via
`adb reverse tcp:8788 tcp:8788`.

JSON request/response everywhere. Errors are `{error, code, message}`.

## Engine / automation

### `GET /v1/status`
Engine + permission readiness summary: profile/log counts, relay target,
notification-policy/write-settings/notification-listener grant states, `ready`
sub-object, version, uptime.

### `GET /v1/schema`
Full trigger + action JSON schema (`SchemaProvider`).

### `GET /v1/capabilities`
Capability registry: `permissionSummary`, `declaredPermissions`, `specialAccess`
(with `settingsAction` grant deep-links), `runtimePermissions`,
`triggerRequirements`, `actions` (risk/autonomy/ready), `provisioningHints`,
`agentPolicy`.

### `GET /v1/profiles`
List all automation profiles.

### `GET /v1/profiles/{id}`
Single profile, or `404`.

### `POST /v1/profiles`
Create/upsert a profile. Body: `id`, `name`, `triggerType`, optional
`description`, `isEnabled`, `triggerConfigJson`, `conditionsJson`,
`actionsJson`, `cooldownMs`, `priority`, `createdAt`, `updatedAt`. Returns
`201` + profile.

### `PATCH /v1/profiles/{id}`
Partial update of a profile. Returns `404` if missing.

### `DELETE /v1/profiles/{id}`
Delete a profile. Returns `{deletedProfileId}` or `404`.

### `POST /v1/events`
Fire an event. Body: `triggerType`, optional `dryRun`, `targetProfileId`,
`payload`. For `MANUAL` + `profileId` in payload, targets that profile
directly. Returns matched profile results/logs.

### `GET /v1/logs?limit=N`
Recent execution logs (default 100). Each: `id`, `profileId`, `profileName`,
`triggerType`, `status`, `skippedReason`, `actionsResultJson`, `durationMs`,
`timestamp`.

### `DELETE /v1/logs`
Clear execution logs.

## Brain bridge

### `GET /v1/brain/status`
Brain health: `brain_running`, `binary`, `sock`, `db`, `last_error`,
`supervisor` (running/halted/restart_count/backoff), `health` (monitor status).

### `POST /v1/brain`
Proxy an RPC to the brain over its UNIX socket. Body = JSON-RPC-style
`{method, params}` (e.g. `{"method":"aware.deals","params":{"owner":"derrick"}}`).
Response is the brain's JSON. This is how the `aware.*` bridge profiles call
the brain.

### `POST /v1/http`
Outbound HTTP proxy for the brain (it has no TLS/network stack). Body:
`{url, method, data, headers}`. The engine's OkHttp performs the request and
returns the raw response body with its status code. Used for search, geocode,
routing, Logseq.

## Device data

### `POST /v1/contacts`
Device address book: `{ok, count, contacts:[{name, number}]}` via
`ContactsContract`. Requires `READ_CONTACTS`.

### `POST /v1/location`
Last known GPS fix: `{ok, latitude, longitude, accuracy, time, provider}`.
Requests a single update (4s timeout) with last-known fallback. Requires
`ACCESS_FINE_LOCATION`.

## Accessibility (eyes / hands)

Requires the CoS Screen Access accessibility service to be enabled.

### `GET /v1/screen`
What's on screen: `{enabled, bound, last_event_pkg, last_event_text, screen:
{ok, package, className, text:[{text, desc, id, class, clickable, bounds,
depth}]}}`. Walks the active window's accessibility tree.

### `POST /v1/ui/tap`
Synthesize a tap. Body: `{x, y}`.

### `POST /v1/ui/type`
Set text into the focused editable field. Body: `{text}`.

### `POST /v1/ui/global`
Global action. Body: `{action}` ∈ `back` | `home` | `recents` |
`notifications` | `quick_settings`.

## OTA self-update

### `POST /v1/ota/config`
Set persisted update URL. Body: `{updateUrl}`.

### `GET /v1/ota/status`
`{version_code, version_name, update_url, can_request_install}`.

### `POST /v1/ota/check`
Fetch the version manifest and compare. Body optional `{updateUrl}`. Returns
current/latest version, `apk_url`, `sha256`, `available`.

### `POST /v1/ota/install`
Download, verify (SHA-256 + signing cert match vs installed app), and launch
the system install confirmation (`ACTION_INSTALL_PACKAGE` + FileProvider).
Returns `{ok, installing, version_code, version_name, apk_path, cert_verified}`
or an error.

## WhatsApp bridge

### `GET /v1/wa/status`
Bridge state: `bridge_running`, `paired`, `last_error`, `last_send_result`,
`last_debug`.

### `POST /v1/wa/debug`
Run the DOM probe on the WebView.

### `POST /v1/wa/send`
Send via the WebView bridge. Body: `{phone, text}`. Returns
`{status, paired, message}`.

## MCP

### `POST /mcp`
Stateless MCP endpoint (protocol 2026-07-28). Requires
`Authorization: Bearer <brain token>` + Origin allow-list. See [mcp.md](mcp.md).
