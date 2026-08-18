---
name: autotask
description: Control AutoTask 2.0 on this phone via loopback. Use for automations, profiles, runs, schedules, /autotask.
---

# AutoTask 2.0

You are on the phone. Talk to AutoTask at `http://127.0.0.1:8788`. Do not use adb. Do not invent types.

## First

```bash
curl -sS http://127.0.0.1:8788/v1/status
```

Stop if `version` is not `2.0`.

Then:

```bash
curl -sS http://127.0.0.1:8788/v1/schema
curl -sS http://127.0.0.1:8788/v1/capabilities
```

Use only `delivery-ready` types. If a capability is missing, tell the user.

## Do work

```text
POST /v1/profiles/validate
POST /v1/events          {"dryRun":true,"triggerType":"MANUAL","profileId":"..."}
POST /v1/profiles        # after user ok
POST /v1/runs            # returns runId
GET  /v1/runs/{runId}
```

One profile: `triggerType=MANUAL` + `profileId`. Judge success from the run, not HTTP 200.

Also: `GET /v1/schedules`, `POST /v1/runs/{id}/cancel|retry|resume`.

Watch (loopback 8787, not 8788):

```bash
curl -sS http://127.0.0.1:8787/v1/watch
curl -N http://127.0.0.1:8787/v1/watch/stream
```

Facts are `event` / `event.deduped` / `run` (including `INDETERMINATE`). Command stays on 8788.

## Confirm first

SMS, call, WhatsApp send, UI drive, HTTP, file write, camera, OTA, contacts, screen.

## MCP (optional)

`POST /mcp` needs `Authorization: Bearer <cos-token>` and protocol `2026-07-28`. Same commands as `autotask.*` tools. Prefer `/v1` from Termux.
