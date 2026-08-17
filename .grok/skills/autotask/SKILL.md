---
name: autotask
description: >
  Connect to and control AutoTask 360 2.0 on a USB or LAN phone: adb forward,
  MCP/REST, schema, capabilities, profiles, durable runs, and schedules.
  Use when the user says AutoTask, AutoTask360, Sapphire-Blu, phone automation,
  "connect to the phone", /autotask, or asks to run, schedule, or inspect
  automations on the device.
---

# AutoTask 2.0

AutoTask is an on-device Android execution runtime. You are a client. You do
not call Android APIs, Room, or the Rust brain directly.

Confirm product version **2.0** (`versionCode` 7) before doing work.

## Connect

Preferred path is USB + `adb forward` (loopback). Do not bind or expose LAN
unless the user asked for it.

```bash
adb devices -l
adb -s <serial> forward tcp:8788 tcp:8788
curl -sS http://127.0.0.1:8788/v1/status
```

`GET /v1/status` must report `"version": "2.0"`. If it does not, stop and say
the phone is not running AutoTask 2.0.

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

## Discover, then act

Every session starts with live discovery. Do not invent trigger or action types.

1. `GET /v1/status` or assume version from it
2. `GET /v1/schema` or MCP `autotask.schema` — triggers, actions, REST catalog, MCP names
3. `GET /v1/capabilities` or MCP `autotask.capabilities` — grants and `agentPolicy`

Use only types whose schema `state` is `delivery-ready`. If a capability is
missing, tell the user what to grant. Do not retry a blocked high-risk action.

REST and MCP are the same command boundary. Prefer MCP `autotask.*` when the
harness is on MCP; use `/v1` for curl or when MCP is not wired.

## Control loop

```text
schema + capabilities
  → autotask.profiles.validate     POST /v1/profiles/validate
  → autotask.events.fire dryRun    POST /v1/events  {"dryRun":true}
  → ask the user if risk is high
  → autotask.profiles.upsert       POST /v1/profiles
  → autotask.runs.request          POST /v1/runs
  → autotask.runs.get              GET  /v1/runs/{runId}
```

- Target one profile: `triggerType=MANUAL` and `profileId`.
- Observe with `runId`. Do not assume success from HTTP 200 alone.
- Cancel / retry / resume via `autotask.runs.*`.
- TIME / SCHEDULE / SUNRISE_SUNSET next-fire: `autotask.schedules.*`.
- Pairing and LAN toggle are loopback-only (`/v1/pairing/*`).

## High-risk

Treat as confirmation-required: SMS, calls, WhatsApp send, UI drive, HTTP,
file write, camera, OTA, DND/silent, contacts, screen dump.

Paired LAN clients need those types in `approvedActions` or the server returns
`403 APPROVAL_REQUIRED` and does not execute.

On-device automations the user already saved may still run. You still dry-run
before creating or firing new ones.

## Do not

- Use the brain token on LAN
- Call `/v1/brain` when an `autotask.*` or `aware.*` tool exists
- Expose port 8788 to the internet
- Print bearer tokens, SMS bodies, contacts, or screen dumps
