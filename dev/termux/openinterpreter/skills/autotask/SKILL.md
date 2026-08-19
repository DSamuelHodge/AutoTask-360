---
name: autotask
description: Control AutoTask 2.1.1 on this phone via loopback. Use for automations, profiles, runs, schedules, /autotask.
---

# AutoTask 2.1.1

You are on the phone. Talk to AutoTask at `http://127.0.0.1:8788`. Do not use adb. Do not invent types. Do not use Termux SMS/telephony APIs when an AutoTask profile exists.

Judge SMS from the run step, not HTTP 200. `OK` / `SMS sent to` means the modem acked. `sms_radio_timeout` / `sms_send_failed` means it did not send — do not claim success. Termux `termux-sms-send` is a fallback only after AutoTask reports those failures.

## First

```bash
curl -sS http://127.0.0.1:8788/v1/status
```

Stop if `version` is not `2.1.1`.

## Resolve, then fire

Never dump `GET /v1/profiles`. Never pull schema or dry-run a saved profile.
Resolve with `q` (default limit 20). Do not pipe schema, capabilities, runs,
or `GET /v1/watch` through `hrlite json`. Those are catalogs / actuation
records / mixed snapshots, not spam. `hrlite` is only the `watch.sh` SSE
seatbelt (`watch-line`).

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

```bash
curl -sS http://127.0.0.1:8788/v1/runs/<runId>
```

## New definitions only

Schema, capabilities, validate, and `dryRun=true` are for **creating** a profile, not for `cos-*` you already resolved. Fetch them whole. Do not crush them.

```bash
curl -sS http://127.0.0.1:8788/v1/schema
curl -sS http://127.0.0.1:8788/v1/capabilities
```

Use only `delivery-ready` types. If a capability is missing, tell the user.

## Watch

Watch stays on 8787 via `~/autotask/watch.sh` (tmux session `at-watch`). Do not block this chat on `curl -N`. `hrlite` is a seatbelt on that pipe only: it collapses consecutive same-type events (BATTERY vs CALL are different) and keeps every `kind=run`. It is not catalog compression and it does not bind 8787.

```bash
tail -n 40 ~/autotask/watch.log
```

Facts: `event` / `event.deduped` / `run` (including `INDETERMINATE`). Act on 8788.

## Confirm first

SMS, call, WhatsApp send, UI drive, HTTP, file write, camera, OTA, contacts, screen.

## MCP (optional)

`POST /mcp` needs `Authorization: Bearer <cos-token>` and protocol `2026-07-28`. Prefer `/v1` from Termux. `autotask.profiles.list` takes the same `q`.
