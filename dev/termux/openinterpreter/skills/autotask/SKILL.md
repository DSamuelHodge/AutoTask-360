---
name: autotask
description: Control AutoTask 2.1.0 on this phone via loopback. Use for automations, profiles, runs, schedules, /autotask.
---

# AutoTask 2.1.0

You are on the phone. Talk to AutoTask at `http://127.0.0.1:8788`. Do not use adb. Do not invent types. Do not use Termux SMS/telephony APIs when an AutoTask profile exists.

## First

```bash
curl -sS http://127.0.0.1:8788/v1/status
```

Stop if `version` is not `2.1.0`.

## Resolve, then fire

Never dump `GET /v1/profiles`. Never pull schema or dry-run a saved profile.

```bash
curl -sS 'http://127.0.0.1:8788/v1/profiles?q=sms'
curl -sS http://127.0.0.1:8788/v1/profiles/cos-sms-send
```

`q` matches id, name, description, trigger, and action types (`sms` → `SEND_SMS`). Also: `id`, `actionType`, `triggerType`, `enabled`, `limit` (default 20).

Then fire:

```bash
curl -sS -X POST http://127.0.0.1:8788/v1/events \
  -H 'Content-Type: application/json' \
  -d '{"triggerType":"MANUAL","profileId":"cos-sms-send","payload":{"number":"+1…","text":"…"}}'
```

One profile: `triggerType=MANUAL` + `profileId`. Judge success from `runId` / `GET /v1/runs/{id}`, not HTTP 200.

## New definitions only

Schema, capabilities, validate, and `dryRun=true` are for **creating** a profile, not for `cos-*` you already resolved.

```bash
curl -sS http://127.0.0.1:8788/v1/schema
curl -sS http://127.0.0.1:8788/v1/capabilities
```

Use only `delivery-ready` types. If a capability is missing, tell the user.

## Watch

Watch stays on 8787 via `~/autotask/watch.sh` (tmux session `at-watch`). Do not block this chat on `curl -N`.

```bash
tail -n 40 ~/autotask/watch.log
curl -sS http://127.0.0.1:8787/v1/watch
```

Facts: `event` / `event.deduped` / `run` (including `INDETERMINATE`). Act on 8788.

## Confirm first

SMS, call, WhatsApp send, UI drive, HTTP, file write, camera, OTA, contacts, screen.

## MCP (optional)

`POST /mcp` needs `Authorization: Bearer <cos-token>` and protocol `2026-07-28`. Prefer `/v1` from Termux. `autotask.profiles.list` takes the same `q`.
