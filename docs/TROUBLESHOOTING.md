# AutoTask 2.0 troubleshooting

Short recovery notes for the on-device runtime. The CoS talks to AutoTask through `/v1` or MCP. It does not open `autotask.db`.

## Permissions

`GET /v1/capabilities` is the source of truth. A missing grant shows up as a `capability 'TYPE' blocked: …` step, not as a silent skip.

| Symptom | Check |
| --- | --- |
| SMS / call will not send | `send_sms_granted` / `call_phone_granted` |
| Schedule never fires at the exact minute | `exact_alarm_granted` |
| DND / silent fails | `dnd_ready` and notification-policy access |
| Notification actions do nothing | `post_notifications_granted` |
| UI drive / screen dump fails | Accessibility enabled |

Do not retry a blocked high-risk action from a paired remote. Fix the grant or the stored `approvedActions`.

## Scheduler

Every enabled `TIME` / `SCHEDULE` / `SUNRISE_SUNSET` profile must have a row from `GET /v1/schedules`.

- After reboot or timezone change the runtime reconciles on `AutoTaskRuntime.start()`. If a next-fire is missing, `POST /v1/schedules/reconcile` (or MCP `autotask.schedules.reconcile`).
- Exact times use `AlarmManager`. Flexible work uses `WorkManager`. A profile that claims cron but has `status=error` on the schedule row is not registered.
- Missed delivery: the manager catches up within 15 minutes (or one interval). It does not fire a backlog of every skipped occurrence.

## Run recovery

`GET /v1/runs/{runId}` is the source of truth. HTTP 200 is not success.

| Status | Meaning | What to do |
| --- | --- | --- |
| `QUEUED` / `RUNNING` / `WAITING` | Still in flight | Wait, or `resume` after a process restart |
| `SUCCESS` / `PARTIAL` / `FAILED` / `SKIPPED` / `CANCELLED` | Terminal | `retry` starts a **new** run from step 0 |
| `INDETERMINATE` | Crash after a non-idempotent step was admitted (`effectId` written) and before `OK` | Do **not** assume the side effect happened or did not. Inspect the device (sent SMS, call log). `retry` is an explicit new send |

Safe to re-enter after a crash: `LOG`, `WAIT`, `TOAST`. Everything else, including `SEND_SMS`, stays `INDETERMINATE`.

Startup order: `AutoTaskApplication` → `AutoTaskRuntime.start()` → recover incomplete runs → reconcile schedules → prune history older than 14 days. Incomplete runs are never pruned.

## Server

Loopback `127.0.0.1:8788` after `adb forward`. `GET /v1/status` must report `"version": "2.0"` and `ktor_server_running: true`. LAN needs pairing; the `cos-` brain token is rejected on LAN.
