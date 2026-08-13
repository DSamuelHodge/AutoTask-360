# Architecture

## Process model

The system is two processes on the same phone, connected over a UNIX domain
socket with Bearer-token auth.

```
┌────────────────────────── AutoTask (Android app, Kotlin) ──────────────────────────┐
│                                                                                    │
│  AutoTaskService (foreground, specialUse)                                          │
│    ├─ KtorLoopbackServer  :127.0.0.1:8788   (the HTTP/mcp surface)                │
│    │    /v1/*  engine routes (profiles, events, logs, OTA, UI, contacts…)          │
│    │    /mcp   stateless MCP endpoint (2026-07-28)                                 │
│    ├─ AutoTaskEngine      automation: profiles → Matcher → ActionExecutor          │
│    ├─ SystemEventReceivers SMS / call / battery / wifi… → engine.processEvent      │
│    ├─ AutoTaskNotificationListener  notification trigger ingestion                 │
│    ├─ CoSAccessibilityService  eyes (screen dump) + hands (tap/type/global)        │
│    ├─ WhatsAppBridgeService  WebView bridge to web.whatsapp.com (auth + send)      │
│    └─ Room database (automation_profiles, execution_logs) in cos.db                │
│                                                                                    │
│  BrainService (foreground, specialUse)  ── supervises the daemon process           │
│    └─ spawns libcosd.so (Rust daemon, aarch64-linux-android)                       │
│         binds unix://app_brain/cosd.sock  (+ token auth)                           │
│         db = app_brain/cos.db  (libSQL — the CoS brain DB)                         │
└────────────────────────────────────────────────────────────────────────────────────┘
```

- The **engine** (AutoTaskService) and the **brain** (daemon process) share one
  on-disk SQLite file: `app_brain/cos.db`, opened by both Room (engine tables)
  and libSQL (brain tables). WAL mode lets both write concurrently.
- The engine reaches the brain over the UNIX socket via `BrainClient`
  (LocalSocket, HTTP/1.1, `Authorization: Bearer <token>`).
- The brain reaches the engine over loopback TCP `127.0.0.1:8788`
  (`autotask_post`). It has no network/TLS stack of its own; outbound HTTPS
  goes through the engine's `/v1/http` OkHttp proxy.

## IPC transport

| Leg | Transport | Auth | Direction |
|---|---|---|---|
| Engine → brain | UNIX socket `app_brain/cosd.sock` | `Bearer <token>` (persisted in prefs, generated once) | RPC calls (`/v1/brain` proxies, MCP tools) |
| Brain → engine | loopback TCP `127.0.0.1:8788` | none (loopback-only) | events, `/v1/http` proxy, `/v1/location`, `/v1/contacts` |
| External → engine | LAN `:8788` or `adb forward tcp:8788` (dev) | `/mcp` and non-loopback `/v1/*` require Bearer token | MCP / HTTP |

The brain socket is app-private (`srwx------`). TCP 8790 is closed in
production; `--addr` is only a debug override.

## Trust boundaries

1. **External input → local privileged action.** Commands that arrive over the
   tunnel/MCP are treated like untrusted intents. Every action a profile
   triggers passes `CapabilityPolicy.require(context, type, params)` and is
   `SKIPPED` with a reason if the required capability is missing.
2. **MCP endpoint** requires `Authorization: Bearer <brain token>` and an
   Origin allow-list (`http://127.0.0.1:8788` / `http://localhost:8788`).
3. **Special access is user-granted** (accessibility, DND, overlay, notification
   listener). The engine only reports grant state and posts repair
   notifications; it cannot grant itself anything.

## Storage

`app_brain/cos.db` (single file, WAL):

- **Room** (engine): `automation_profiles`, `execution_logs`, Room meta tables.
- **libSQL** (brain): `calendars`, `availability_windows`, `blocked_periods`,
  `booking_links`, `bookings`, `attendees`, `companies`, `contacts`, `deals`,
  `interactions`, plus FTS5/vector shadow tables.
- Opened by Room via `SingleFileOpenHelperFactory` (custom `SupportSQLiteOpenHelper`
  that targets the absolute path; Room alone would resolve under `databases/`).

## Key flow: inbound SMS → informed notification

1. `SystemEventReceivers` gets `SMS_RECEIVED_ACTION` → dispatches `SMS` event
   with `sender`, `smsBody`.
2. `cos-aware-sms` profile (HTTP action) POSTs to `/v1/brain` →
   `aware.sms`.
3. Brain resolves sender in CRM, logs interaction, auto-captures mentioned
   device contacts (via `/v1/contacts`), builds an informed summary.
4. Brain fires `notify()` → POSTs `MANUAL` event → `cos-informed-notify`
   profile → `NOTIFICATION` action.
