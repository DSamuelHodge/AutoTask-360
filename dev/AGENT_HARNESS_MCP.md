# Agent Harness MCP Connection

This guide connects an agentic harness running on a Mac host to AutoTask 2.1.1
(`versionName` 2.1.1, `versionCode` 9) running inside the Android app.

This is a **local development** setup. The Ktor server binds loopback by
default. Use `adb forward` (below). LAN bind requires an explicit paired
`atc-` credential and is documented in `docs/LAN_SECURITY.md`. Do not expose
port `8788` to the internet.

## Connection topology

The Android Ktor server binds `127.0.0.1` unless LAN mode is enabled after
pairing. Loopback MCP accepts the internal brain token; LAN requests must use
a paired client token and never the brain token:

```text
Agent harness on Mac
  http://192.168.40.88:8788/mcp       or       http://127.0.0.1:8788/mcp
              |                                      |
              | LAN + bearer token                    | adb forward
              v                                      v
Android app 192.168.40.88:8788/mcp       Android app 127.0.0.1:8788/mcp
              |
              | UNIX socket + bearer token
              v
Rust CoS brain daemon
```

Use the phone's current LAN IP for direct access. The example phone address is
`192.168.40.88`; it may change when the phone reconnects to Wi-Fi.

Use `adb forward`, not `adb reverse`:

```bash
adb -s <device-serial> forward tcp:8788 tcp:8788
```

`adb reverse` is the opposite direction. It attempts to bind a host port on
the phone and conflicts with the Ktor server already listening on phone port
`8788`.

## Prerequisites

- Android device with the Sapphire-Blu APK installed.
- USB ADB or Android Wireless Debugging connected.
- `adb` available on the host.
- The app package is `com.aistudio.autotask.svcqx`.
- The host has the agent harness configuration file.

Find the device serial:

```bash
adb devices
```

If more than one device is listed, always use `-s`:

```bash
SERIAL="1010018024018888"
adb -s "$SERIAL" devices
```

## Start the Android services

```bash
SERIAL="<device-serial>"

adb -s "$SERIAL" forward tcp:8788 tcp:8788

adb -s "$SERIAL" shell am start-foreground-service \
  -n com.aistudio.autotask.svcqx/com.example.service.AutoTaskService \
  -a com.example.autotask.action.START

adb -s "$SERIAL" shell am start-foreground-service \
  -n com.aistudio.autotask.svcqx/com.example.wa.BrainService \
  -a com.example.autotask.action.BRAIN_START
```

Confirm the forward:

```bash
adb -s "$SERIAL" forward --list
```

## Token ownership

`COS_MCP_TOKEN` is issued by Sapphire-Blu, not by Codex, OpenCode, Pass, or
the LLM provider.

`BrainService.getToken()` generates a `cos-<uuid>` token the first time it is
needed and stores it in the app's private `brain_config` preferences. The same
token authenticates the Android MCP endpoint and the app-to-brain UNIX socket.

Pass should store a copy of this token; it does not generate or rotate it.

### Debug-build token retrieval

For a debuggable APK, retrieve the preference file with:

```bash
adb -s "$SERIAL" shell run-as com.aistudio.autotask.svcqx \
  cat shared_prefs/brain_config.xml
```

Read the value of `brain_token`. Never commit the value, paste it into an
issue, or put it in this repository.

Store it in Pass, using an entry containing only the token:

```bash
pass insert autotask/sapphire-blu/brain-token
```

The release product needs a secure provisioning, rotation, and revocation
flow. `run-as` is a debug-development technique only.

## Load the token into the harness

For a terminal-launched harness:

```bash
export COS_MCP_TOKEN="$(pass show autotask/sapphire-blu/brain-token)"
```

The environment variable is inherited by processes started from that shell.
The agent receives MCP tools, not the token value itself.

For a GUI harness launched by macOS `launchd`:

```bash
launchctl setenv COS_MCP_TOKEN \
  "$(pass show autotask/sapphire-blu/brain-token)"
```

Fully quit and reopen the harness after setting the variable. Existing
processes do not receive changes to their environment.

Check or remove the user-session value:

```bash
launchctl getenv COS_MCP_TOKEN
launchctl unsetenv COS_MCP_TOKEN
```

The token is held in the environment of the launched process. Prefer a
short-lived launcher that reads from Pass rather than keeping the token in a
persistent user-session environment when practical.

## Codex configuration

In `/Users/<user>/.codex/config.toml`:

```toml
[mcp_servers.cos]
url = "http://127.0.0.1:8788/mcp"
bearer_token_env_var = "COS_MCP_TOKEN"
```

The Codex MCP client reads `COS_MCP_TOKEN` and sends:

```text
Authorization: Bearer <token>
```

### Codex stdio compatibility bridge

The Android server currently implements `POST /mcp` only. It does not
implement an SSE/streaming `GET /mcp`; `GET /mcp` correctly returns `405
Method Not Allowed`. Some Codex HTTP transport checks probe `GET /mcp`, which
produces a `cos SSE error: Non-200 status code (405)` even though POST-based
MCP calls are healthy.

For Codex, use a local stdio bridge that translates Codex stdio MCP messages
into authenticated HTTP POST requests:

```toml
[mcp_servers.cos]
command = "/Users/<user>/.codex/bin/cos-mcp-stdio"
```

The bridge should:

- Read the token from Pass, for example
  `pass show autotask/sapphire-blu/brain-token`.
- Send MCP requests to `http://127.0.0.1:8788/mcp`.
- Keep protocol responses on stdout only.
- Send diagnostics to stderr, never stdout.
- Never print the bearer token.

The host-specific implementation used during development is:

```text
/Users/<user>/.codex/bin/cos-mcp-stdio
/Users/<user>/.codex/bin/cos-mcp-stdio.js
```

This bridge is a Codex host integration, not an Android app component, and
should not be committed with credentials. It was verified to expose the CoS
tools over stdio.

## AutoTask automation MCP tools

The same `/mcp` endpoint also exposes the AutoTask360 automation control plane
so an agent can create and test persistent multi-step device workflows without
hand-writing REST calls:

- `autotask.schema`
- `autotask.capabilities`
- `autotask.profiles.list`
- `autotask.profiles.get`
- `autotask.profiles.upsert`
- `autotask.profiles.patch`
- `autotask.profiles.delete`
- `autotask.events.fire`
- `autotask.logs.list`

Resolve a saved profile with `autotask.profiles.list` and `q` (same as
`GET /v1/profiles?q=`). Do not list the unfiltered catalog. Use
`autotask.schema` only when constructing a **new** profile. Fire a known
MANUAL profile with `autotask.events.fire` / `POST /v1/events` (`profileId`,
no `dryRun`). Dry-run is for new definitions, not for `cos-sms-send`.

After changing global MCP configuration, fully quit and reopen Codex or start
a new task. An already-running chat does not hot-load newly configured MCP
tools.

## OpenCode configuration

In `~/.config/opencode/opencode.jsonc`:

```jsonc
{
  "$schema": "https://opencode.ai/config.json",
  "mcp": {
    "cos": {
      "type": "remote",
      "url": "http://127.0.0.1:8788/mcp",
      "headers": {
        "Authorization": "Bearer {env:COS_MCP_TOKEN}"
      },
      "enabled": true
    }
  }
}
```

Restart OpenCode after changing its configuration.

## MCP protocol requirements

The endpoint is stateless and supports `tools/list` and `tools/call`. Each
request must include:

- `Authorization: Bearer <COS_MCP_TOKEN>`
- `MCP-Protocol-Version: 2026-07-28`
- `Mcp-Method` matching the JSON-RPC method
- `Mcp-Name` matching `params.name` for `tools/call`
- `params._meta.io.modelcontextprotocol/clientCapabilities`

The server accepts these development origins when an Origin header is sent:

- `http://127.0.0.1:8788`
- `http://localhost:8788`

## REST API endpoints

The same Ktor server exposes the REST control surface at:

```text
http://<phone-ip>:8788/v1
```

Important authentication boundary (2.0):

- `POST /mcp` always requires a bearer token.
- Loopback `/v1/*` (including `adb forward`) is local-trust in debug builds.
- Loopback MCP accepts the internal brain token (`cos-…`).
- LAN is off until pairing. LAN requests must use a paired `atc-…` token.
  The brain token is rejected on LAN.

```text
Authorization: Bearer <paired-atc-token>
```

Confirm the phone is on 2.1.1 with `GET /v1/status` → `version` is `2.1.1`.

All request and response bodies are JSON unless noted otherwise. The normal
error shape is `{error, code, message}`.

### Safe read and discovery endpoints

These are the preferred first calls for an agent. They are read-only, but may
still return sensitive device state:

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/v1/status` | Engine, Ktor, permission-readiness, profile/log counts, version, and uptime. |
| `GET` | `/v1/schema` | Trigger/action schema, template variables, risk, and autonomy metadata. |
| `GET` | `/v1/capabilities` | Runtime permissions, special access, repair actions, readiness, and agent policy. |
| `GET` | `/v1/profiles` | List profiles. Pass `q` (or `search`), `id`, `actionType`, `triggerType`, `enabled`, `limit`. Unfiltered GET returns every profile — CoS must not do that. |
| `GET` | `/v1/profiles/{id}` | Read one automation profile. |
| `GET` | `/v1/logs?limit=N` | Read recent execution logs; default limit is 100. |
| `GET` | `/v1/runs?limit=N` | List durable run records and statuses. |
| `GET` | `/v1/runs/{id}` | Read one run and its step checkpoints. |
| `GET` | `/v1/schedules` | List next-fire registrations for TIME/SCHEDULE/SUNRISE_SUNSET. |
| `GET` | `/v1/schedules/{id}` | Read one schedule by profile id. |
| `GET` | `/v1/brain/status` | Rust brain process, socket, database, supervisor, and health state. |
| `GET` | `/v1/ota/status` | Installed version, update URL, and install capability. |
| `GET` | `/v1/wa/status` | WhatsApp bridge running, pairing, error, and send state. |
| `GET` | `/v1/screen` | Current accessibility tree and screen metadata; requires accessibility enabled and is highly sensitive. |

Typical discovery sequence:

```bash
BASE_URL="http://<phone-ip>:8788"
AUTH=(-H "Authorization: Bearer $COS_MCP_TOKEN")
curl -sS "${AUTH[@]}" "$BASE_URL/v1/status"
curl -sS "${AUTH[@]}" "$BASE_URL/v1/profiles?q=sms"
curl -sS "${AUTH[@]}" "$BASE_URL/v1/profiles/cos-sms-send"
# schema + capabilities only when creating or patching a definition
curl -sS "${AUTH[@]}" "$BASE_URL/v1/logs?limit=20"
```

Interpret readiness rather than assuming it. For example, the server and
engine can be healthy while `notification_listener_enabled` or `dnd_ready`
is false. Calendar runtime permissions can be granted while a CoS briefing
still fails because of owner, calendar selection, or brain-side data mapping.

### Automation and profile endpoints

| Method | Endpoint | Purpose and safety |
|---|---|---|
| `POST` | `/v1/profiles/validate` | Validate a definition without persisting. |
| `POST` | `/v1/profiles` | Create or upsert a profile. Validate against `/v1/schema` first. |
| `PATCH` | `/v1/profiles/{id}` | Partially update a profile. |
| `DELETE` | `/v1/profiles/{id}` | Delete a profile; destructive. |
| `POST` | `/v1/events` | Fire an event. Supports `dryRun`, `profileId`, and `payload`; may execute real actions. |
| `POST` | `/v1/runs` | Enqueue a durable run; returns `runId`. |
| `POST` | `/v1/runs/{id}/cancel` | Cancel a queued, running, or waiting run. |
| `POST` | `/v1/runs/{id}/retry` | Retry a terminal run. |
| `POST` | `/v1/runs/{id}/resume` | Resume an interrupted or waiting run. |
| `POST` | `/v1/schedules/reconcile` | Recalculate next-fire times. |
| `DELETE` | `/v1/logs` | Clear execution logs; destructive and irreversible. |

`POST /v1/events` body:

```json
{
  "triggerType": "MANUAL",
  "dryRun": true,
  "targetProfileId": "optional-profile-id",
  "payload": {}
}
```

Agents must use `dryRun: true` before any event that could send SMS, place a
call, change device settings, drive the UI, send WhatsApp, or open an external
URL. Use `targetProfileId` when targeting one manual profile. Never assume a
manual event is harmless.

### Brain and network bridge endpoints

| Method | Endpoint | Purpose and safety |
|---|---|---|
| `POST` | `/v1/brain` | Forward a JSON-RPC-style request to the Rust brain. May trigger CRM, calendar, messaging, browser, or device actions. |
| `POST` | `/v1/http` | Engine-owned outbound HTTP proxy for search, geocoding, routing, and integrations. Treat URLs, headers, and data as sensitive/high risk. |

`POST /v1/brain` example:

```json
{
  "method": "aware.deals",
  "params": {"owner": "derrick"}
}
```

The available brain methods are documented in `.docs/daemon-rpc.md`. MCP tool
calls are normally preferred because they expose the bounded tool registry;
use `/v1/brain` only when the harness explicitly needs the lower-level RPC
surface.

### Device data endpoints

| Method | Endpoint | Purpose and safety |
|---|---|---|
| `POST` | `/v1/contacts` | Read the device address book; requires `READ_CONTACTS`. Highly sensitive. |
| `POST` | `/v1/location` | Request/read a GPS fix; requires location access. Highly sensitive. |

These endpoints use `POST` even when the operation is read-only.

### Accessibility endpoints

These require the CoS Screen Access accessibility service:

| Method | Endpoint | Purpose and safety |
|---|---|---|
| `GET` | `/v1/screen` | Read the active accessibility tree. |
| `POST` | `/v1/ui/tap` | Tap screen coordinates; side effect. Body: `{x, y}`. |
| `POST` | `/v1/ui/type` | Type into the focused field; side effect. Body: `{text}`. |
| `POST` | `/v1/ui/global` | Execute `back`, `home`, `recents`, `notifications`, or `quick_settings`; side effect. |

Never use UI-driving endpoints for login, payment, deletion, messaging, or
other high-impact actions without explicit user confirmation.

### Pairing endpoints (loopback only)

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/v1/pairing/start` | Issue a 6-digit code. |
| `POST` | `/v1/pairing/complete` | Exchange the code for an `atc-` token (shown once). |
| `GET` | `/v1/pairing/credentials` | List hashed credentials. |
| `POST` | `/v1/pairing/revoke` | Revoke a credential. |
| `POST` | `/v1/pairing/lan` | `{enabled:true}` after a live credential exists. |

### OTA endpoints

| Method | Endpoint | Purpose and safety |
|---|---|---|
| `POST` | `/v1/ota/config` | Persist the update manifest URL. |
| `POST` | `/v1/ota/check` | Fetch and compare an update manifest. |
| `POST` | `/v1/ota/install` | Download, verify, and launch Android package installation; high risk and user-confirmed. |

The OTA flow verifies SHA-256 and signing certificate before requesting the
system install confirmation. Agents must not change the update URL or install
an update without explicit authorization.

### WhatsApp bridge endpoints

| Method | Endpoint | Purpose and safety |
|---|---|---|
| `GET` | `/v1/wa/status` | Read bridge state. |
| `POST` | `/v1/wa/debug` | Probe the WebView DOM. |
| `POST` | `/v1/wa/send` | Send a WhatsApp message; high risk and confirmation-required. Body: `{phone, text}`. |

## Manual authenticated smoke test

```bash
curl -sS -X POST http://127.0.0.1:8788/mcp \
  -H "Authorization: Bearer $COS_MCP_TOKEN" \
  -H "Origin: http://127.0.0.1:8788" \
  -H "MCP-Protocol-Version: 2026-07-28" \
  -H "Mcp-Method: tools/list" \
  -H "Content-Type: application/json" \
  --data '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/list",
    "params": {
      "_meta": {
        "io.modelcontextprotocol/protocolVersion": "2026-07-28",
        "io.modelcontextprotocol/clientCapabilities": {}
      }
    }
  }'
```

Success returns a JSON-RPC result containing the CoS tool registry. A `401`
means the HTTP bridge is reachable but the token is missing or incorrect.

## Harness handoff prompt

Give an agentic harness this prompt after the host tunnel and token are ready:

```text
Connect to AutoTask 2.1.0.

Transport: Streamable HTTP, stateless
URL: http://127.0.0.1:8788/mcp
Authentication: Authorization: Bearer $COS_MCP_TOKEN (loopback MCP / brain token)
First REST call: GET /v1/status and confirm version is 2.1.0
Then call autotask.schema and autotask.capabilities before any write.
Protocol version: 2026-07-28
Required request metadata:
  params._meta.io.modelcontextprotocol/protocolVersion = 2026-07-28
  params._meta.io.modelcontextprotocol/clientCapabilities = {}
Required mirrored headers:
  MCP-Protocol-Version = 2026-07-28
  Mcp-Method = the JSON-RPC method
  Mcp-Name = params.name for tools/call

First call tools/list. Do not call side-effectful tools until you have
described the intended action and received confirmation. Treat SMS, calls,
WhatsApp sends, UI driving, navigation, and settings changes as high risk.
If the server is unreachable, verify that adb forward tcp:8788 tcp:8788 is
active on the host and that AutoTaskService and BrainService are running on
the device. Never expose port 8788 directly to the network.
```

## Troubleshooting

### `adb: more than one device/emulator`

Use the device serial on every command:

```bash
adb -s <device-serial> forward tcp:8788 tcp:8788
```

### `401 Unauthorized`

The forward works. Reload the token from Pass and export it in the same
environment that launches the harness.

### Connection refused

Check the forward and services:

```bash
adb -s "$SERIAL" forward --list
adb -s "$SERIAL" shell dumpsys activity services \
  com.aistudio.autotask.svcqx | grep -E 'AutoTaskService|BrainService'
```

Then restart both services and recreate the forward if needed.

### `run-as: package not debuggable`

The installed APK is a release build. The debug token retrieval method is not
available. Use the product's future secure token-provisioning flow; do not
extract private release storage through workarounds.

### Wireless ADB

On Android 11+, enable **Developer options → Wireless debugging**, pair from
the host, then connect:

```bash
adb pair <phone-ip>:<pairing-port>
adb connect <phone-ip>:<adb-port>
adb devices
```

After the wireless ADB connection is active, `adb forward` works the same as
with USB. The phone and Mac need network reachability, but the MCP client
still uses `127.0.0.1:8788` on the Mac when using the forward.

If the phone advertises a wireless-debugging endpoint such as
`192.168.40.88:44037` but `adb connect` returns `Connection refused`, the
endpoint is stale or wireless debugging has been disabled/re-enabled. Open
**Developer options -> Wireless debugging**, pair again, and use the current
pairing and connection ports shown by Android.

## Direct LAN access

Direct access such as `http://192.168.40.88:8788/v1/status` requires LAN mode
after pairing. Non-loopback requests must include a paired client token:

```text
Authorization: Bearer <atc-token>
```

The `/mcp` route always requires the bearer token. The phone firewall, Wi-Fi
isolation, and changing DHCP address can still prevent LAN access.

There are three connectivity modes:

1. **ADB bridge:** use `adb forward` and connect to `127.0.0.1` on the host.
2. **LAN development mode:** connect to the phone IP with the bearer token.
3. **Production remote mode:** use a dedicated gateway with TLS, scoped keys,
   rotation/revocation, audit logs, rate limits, replay protection,
   read/write permissions, and a kill switch.

Never expose port `8788` directly to the internet.
