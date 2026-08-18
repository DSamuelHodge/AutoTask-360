---
name: autotask
description: >
  Connect to and control AutoTask 360 2.1.0 on a USB or LAN phone: adb forward,
  MCP/REST, schema, capabilities, profiles, durable runs, and schedules.
  Use when the user says AutoTask, AutoTask360, Sapphire-Blu, phone automation,
  "connect to the phone", /autotask, or asks to run, schedule, or inspect
  automations on the device.
---

# AutoTask 2.1.0

AutoTask is an on-device Android execution runtime. You are a client. You do
not call Android APIs, Room, or the Rust brain directly.

Confirm product version **2.1.0** (`versionCode` 8) before doing work.

## Connect

Preferred path is USB + `adb forward` (loopback). Do not bind or expose LAN
unless the user asked for it.

```bash
adb devices -l
adb -s <serial> forward tcp:8788 tcp:8788
adb -s <serial> forward tcp:8787 tcp:8787
curl -sS http://127.0.0.1:8788/v1/status
curl -sS http://127.0.0.1:8787/v1/watch
```

`GET /v1/status` must report `"version": "2.1.0"`. If it does not, stop and say
the phone is not running AutoTask 2.1.0.

| Mode | URL | Auth |
| --- | --- | --- |
| Debug loopback / adb forward | `http://127.0.0.1:8788` | `/v1` is local-trust; `/mcp` needs `Authorization: Bearer <cos-…>` |
| LAN | `http://<phone-ip>:8788` | Paired `atc-…` token only. Brain `cos-…` is rejected. |

MCP (`POST /mcp`) always needs a bearer token plus:

- `MCP-Protocol-Version: 2026-07-28`
- `Mcp-Method` = JSON-RPC method
- `Mcp-Name` = `params.name` on `tools/call`
- `params._meta.io.modelcontextprotocol/protocolVersion`
- `params._meta.io.modelcontextprotocol/clientCapabilities`

If `Origin` is sent, it must be `http://127.0.0.1:8788` or `http://localhost:8788`
unless LAN mode is on.

Details: `dev/AGENT_HARNESS_MCP.md`, `docs/LAN_SECURITY.md`.

## Resolve, then act

Do **not** dump `GET /v1/profiles` or pull schema on every turn. Resolve first.

```bash
curl -sS 'http://127.0.0.1:8788/v1/profiles?q=sms'
curl -sS http://127.0.0.1:8788/v1/profiles/cos-sms-send
```

| Query | Meaning |
| --- | --- |
| `q` / `search` | Intent text. AND across tokens. Aliases: sms→SEND_SMS, url→OPEN_URL. Default limit 20. |
| `id` | Exact profile id |
| `actionType` | Exact action (`SEND_SMS`) |
| `triggerType` | Exact trigger (`MANUAL`) |
| `enabled` | `true` / `false` |
| `limit` | Cap (max 100) |

MCP: `autotask.profiles.list` with the same fields.

Use only types whose schema `state` is `delivery-ready`. Fetch
`GET /v1/schema` and `GET /v1/capabilities` **only** when creating or
patching a definition, or when a fire fails on a missing grant.

## Known profile

```text
GET /v1/profiles?q=…          # or GET /v1/profiles/{id}
  → confirm if SMS / call / HTTP / file / UI / camera / OTA
  → POST /v1/events  {"triggerType":"MANUAL","profileId":"…","payload":{…}}
  → GET  /v1/runs/{runId}
```

Do not dry-run a saved MANUAL profile. `dryRun` only lists `plannedProfiles`;
it does not rehearse handlers.

Judge success from the run, not HTTP 200. Cancel / retry / resume via
`autotask.runs.*`. TIME / SCHEDULE next-fire: `autotask.schedules.*`.

## New definition

```text
GET /v1/schema + GET /v1/capabilities
  → POST /v1/profiles/validate
  → POST /v1/events  {"dryRun":true}
  → POST /v1/profiles
  → POST /v1/events  {profileId}  or  POST /v1/runs
```

## High-risk

Confirm first: SMS, calls, WhatsApp send, UI drive, HTTP, file write, camera,
OTA, DND/silent, contacts, screen dump.

Paired LAN clients need those types in `approvedActions` or the server returns
`403 APPROVAL_REQUIRED` and does not execute.

## Do not

- `GET /v1/profiles` with no query (returns every profile)
- Schema + dry-run before firing a known `cos-*` profile
- Use the brain token on LAN
- Call `/v1/brain` when an `autotask.*` or `aware.*` tool exists
- Expose port 8788 to the internet
- Print bearer tokens, SMS bodies, contacts, or screen dumps
