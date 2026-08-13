# Daemon RPC

The Rust daemon (`agent-cal-crm/src/bin/cos.rs`) serves JSON-RPC-style requests
over its UNIX socket (or debug TCP). `POST /v1/brain` and every MCP tool call
reach it. Request body: `{"method": "<name>", "params": {...}}`.

Response: `{"ok": true, "result": ...}` or `{"ok": false, "error": "..."}`.
Auth: `Authorization: Bearer <token>` (the socket enforces it; the daemon was
started with `--token`).

## `aware.*` (situational awareness) — `cos.rs` dispatch

Handlers in `src/bin/aware.rs`. They resolve against the CRM/calendar, build an
informed human-readable summary, and fire an informed notification via
`notify()` (POSTs a `MANUAL` event to the `cos-informed-notify` profile).

| Method | Summary |
|---|---|
| `aware.sms` | Triage inbound SMS. Resolves sender, logs interaction, auto-captures mentioned device contacts, notifies with context (VIP, open deals). |
| `aware.sms.send` | Outbound SMS via native `SEND_SMS` (`cos-sms-send` MANUAL event). Resolves recipient by name/phone, logs outbound interaction. |
| `aware.whatsapp` | Triage inbound WhatsApp message (by sender name). |
| `aware.whatsapp.send` | Send via WebView bridge (async dispatch to `/v1/wa/send`), logs outbound. |
| `aware.call` | Incoming-call context flash. |
| `aware.capture` | Create contact (optionally company + starter deal). |
| `aware.sync_contacts` | Idempotent device-address-book → CRM sync (reads `/v1/contacts`). |
| `aware.travel` | Drive time/distance; optional `origin_lat`/`origin_lon` override; geocode via Photon, routing via OSRM, all through engine `/v1/http`. |
| `aware.open` | Open URL in browser (`cos-open-url` MANUAL event). |
| `aware.search` | DuckDuckGo HTML search (browser User-Agent via `proxy_http_ua`); detects rate-limit anomalies. |
| `aware.email` | Gmail compose via `mailto:` (`cos-open-url` MANUAL event). |
| `aware.meeting` | Next booking + attendee CRM briefing. |
| `aware.briefing` | Daily calendar + CRM briefing. |
| `aware.deals` | Open deals with next action. |
| `sync.logseq` | Push CRM contacts to a Logseq page (async thread; create_page + insert_block). |

## `crm.*` — `src/rpc.rs`

| Method | Params | Returns |
|---|---|---|
| `crm.summary` | `owner` | CRM summary |
| `crm.resolve_by_phone` | `owner`, `number` | contact |
| `crm.resolve_by_email` | `owner`, `email` | contact |
| `crm.contact_context` | `owner`, `number` | contact + context |
| `crm.search` | `owner`, `query` | contacts |
| `crm.vector_search` | `owner`, `query` | vector search results |
| `crm.create_company` | `owner`, `name`, `description` | company |
| `crm.get_company` | `owner`, `company_id` | company |
| `crm.list_companies` | `owner` | companies |
| `crm.create_contact` | `owner`, `first_name`, `last_name` | contact |
| `crm.get_contact` | `owner`, `contact_id` | contact |
| `crm.update_contact` | `owner`, contact fields | contact |
| `crm.list_contacts` | `owner` | contacts |
| `crm.create_deal` | `owner`, `company_id`, `name`, `amount` | deal |
| `crm.get_deal` | `owner`, `deal_id` | deal |
| `crm.list_deals` | `owner` | deals |
| `crm.list_deals_for_company` | `owner`, `company_id` | deals |
| `crm.advance_deal` | `owner`, `deal_id`, `stage` | deal |
| `crm.log_interaction` | `owner`, `contact_id`, `kind`, `direction`, `summary` | interaction |
| `crm.interactions_for_contact` | `owner`, `contact_id` | interactions |
| `crm.attendee_for_contact` | `owner`, `contact_id` | attendee link |
| `crm.contact_for_booking` | `owner`, `booking_id` | contact |

## `cal.*` — `src/rpc.rs`

| Method | Params | Returns |
|---|---|---|
| `cal.create_calendar_simple` | `owner`, `name` | calendar |
| `cal.add_window` | `owner`, `day_of_week`, `start`, `end`, `label` | window |
| `cal.block` | `owner`, `start`, `end` | blocked slot |
| `cal.create_link` | `owner`, `title`, `duration_minutes`, `min_notice_hours`, `max_days_ahead` | booking link |
| `cal.get_slots` | `owner`, `link_id`, `from`, `to`, `limit` | slots |
| `cal.book` | `owner`, `link_id`, `slot`, `attendees`, `notes`, `metadata` | booking |
| `cal.cancel` | `owner`, `booking_id` | cancelled booking |
| `cal.get_booking` | `owner`, `booking_id` | booking |
| `cal.list_bookings` | `owner`, `status` | bookings |
| `cal.summary` | `owner` | calendar summary |
| `cal.upcoming` | `owner`, `limit` | upcoming bookings |

## Daemon CLI

```
cos <serve|seed> [--addr HOST:PORT | --sock PATH] [--db PATH] [--token TOKEN]
```

- `cos seed --db <path>` — idempotently create the DB + default contacts.
- `cos serve --db <path> --token <token> [--sock PATH | --addr HOST:PORT]` —
  serve RPCs. `--sock` = UNIX socket (production); `--addr` = loopback TCP
  (debug/adb). Token required unless the socket is app-private.
- Environment override for the engine URL: `AUTOTASK_URL` (default
  `http://127.0.0.1:8788`).

The app spawns it via `BrainService`:
`libcosd.so seed --db app_brain/cos.db`, then
`libcosd.so serve --db app_brain/cos.db --token <token> --sock app_brain/cosd.sock`.
