# File structure

## AutoTask-360 (Android app, Kotlin)

### `app/src/main/java/com/example/`

| Path | Responsibility |
|---|---|
| `MainActivity.kt` | Launcher activity. |
| `accessibility/CoSAccessibilityService.kt` | The eyes/hands layer: screen-tree dump (`/v1/screen`), gestures (`tap`/`type`), global actions (`back`/`home`/`recents`/`notifications`/`quick_settings`). User-granted; config in `res/xml/accessibility_service_config.xml`. |
| `data/AutomationProfile.kt` | Room entity: id, name, triggerType, triggerConfigJson, conditionsJson, actionsJson, cooldownMs, priority, timestamps. |
| `data/AutomationProfileDao.kt` | Room DAO for profiles. |
| `data/ExecutionLog.kt` | Room entity: profile execution results. |
| `data/ExecutionLogDao.kt` | Room DAO for logs. |
| `data/AutoTaskDatabase.kt` | Room database (merged into `cos.db` via `SingleFileOpenHelper`). |
| `data/AutoTaskRepository.kt` | Profile/log CRUD + seed logic. |
| `data/PolicySeeder.kt` | Base automation profiles (battery, wifi, bluetooth, time, sms, call, boot…). |
| `data/SingleFileOpenHelper.kt` | Custom `SupportSQLiteOpenHelper` so Room opens the brain's absolute `cos.db` path. |
| `engine/ActionExecutor.kt` | Runs each profile action (`NOTIFICATION`, `SEND_SMS`, `CALL`, `OPEN_URL`, `SEND_INTENT`, `HTTP`, …). |
| `engine/AutoTaskEngine.kt` | Engine singleton: event dispatch, matcher, executor, location warm-up. |
| `engine/AutomationEvent.kt` | Event type + payload. |
| `engine/CapabilityPolicy.kt` | Guard between inbound commands and privileged actions (SKIP with reason). |
| `engine/CapabilityProvider.kt` | Capability registry: permission summary, special access, actions, triggers, provisioning hints. |
| `engine/Matcher.kt` | Profile trigger/condition/cooldown evaluation. |
| `engine/SchemaProvider.kt` | JSON schema of triggers + actions (exposed at `/v1/schema`). |
| `mcp/McpHandler.kt` | Stateless MCP handler (protocol 2026-07-28): header validation, tools/list, tools/call. |
| `mcp/McpTools.kt` | MCP tool registry (16 tools). |
| `onboarding/CapabilityOnboarding.kt` | Capability grant flow UI logic. |
| `ota/OtaInstallReceiver.kt` | Receives PackageInstaller result; launches confirm dialog on `STATUS_PENDING_USER_ACTION`. |
| `ota/OtaUpdater.kt` | Manifest fetch, version compare, APK download, SHA-256 + signing-cert verify, install. |
| `provider/AutoTaskContentProvider.kt` | Content provider (external trigger surface). |
| `server/KtorLoopbackServer.kt` | All HTTP routes on `127.0.0.1:8788` (see [http-api.md](http-api.md)). |
| `server/KtorServerConfig.kt` | Server config. |
| `server/EventRequestParser.kt` | `/v1/events` request parsing. |
| `service/AutoTaskNotificationListener.kt` | `NotificationListenerService`: notification → `NOTIFICATION` event. |
| `service/AutoTaskService.kt` | Foreground engine service (starts Ktor, listeners, engine). |
| `service/BootReceiver.kt` | `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` → start services + dispatch BOOT event. |
| `service/SystemEventReceivers.kt` | SMS, battery, wifi, bluetooth, headset, call, light, power-save receivers. |
| `service/TimeTriggerWorker.kt` | WorkManager time-based trigger. |
| `ui/AutoTaskMainScreen.kt`, `ui/AutoTaskViewModel.kt`, `ui/theme/*` | Compose UI. |
| `wa/BrainClient.kt` | LocalSocket HTTP client to the brain socket. |
| `wa/BrainService.kt` | Spawns + supervises `libcosd.so` (healthcheck, backoff, kill-switch, PID guard). |
| `wa/HealthMonitor.kt` | Passive special-access grant drift detection + repair notifications. |
| `wa/WhatsAppBridgeManager.kt` | WebView + injected JS bridge to web.whatsapp.com (auth, probe, send). |
| `wa/WhatsAppBridgeService.kt` | Foreground service hosting the bridge. |
| `wa/WhatsAppBridgeActivity.kt` | Pairing activity (WebView). |

### `app/src/main/res/`

- `xml/accessibility_service_config.xml` — accessibility service capabilities.
- `xml/file_paths.xml` — FileProvider cache path for OTA installs.
- `values/strings.xml` — app name + accessibility description.

### Other app files

- `app/src/main/jniLibs/arm64-v8a/libcosd.so` — the cross-compiled Rust brain.
- `app/build.gradle.kts` — AGP config, signing, `useLegacyPackaging=true`, deps.
- `app/src/main/AndroidManifest.xml` — 43 permissions, 15 uses-feature, services/receivers/providers.

## agent-cal-crm (Rust daemon)

### `src/`

| Path | Responsibility |
|---|---|
| `lib.rs` | Library root (calendar + CRM API). |
| `types.rs` | Core types (Calendar, Slot, Booking, Deal, Contact…). |
| `error.rs` | `AgentError` enum. |
| `rpc.rs` | `dispatch()` — `crm.*` + `cal.*` method routing for the server. |
| `agent_api.rs` | High-level agent API facade. |
| `availability.rs` | Slot generation from availability windows. |
| `scheduler.rs` | Time-based scheduling helpers. |
| `seed.rs` | Seed data (default contacts/deals). |
| `crm/` | `mod.rs`, `agent.rs` (AgentCrm facade), `store.rs` (Store trait), `libsql.rs` (libSQL store), `types.rs` (CRM types). |
| `store/` | `mod.rs`, `libsql_store.rs`, `memory.rs`, `null.rs` — storage backends. |
| `bin/cos.rs` | Binary: `cos serve` / `cos seed`, CLI args, `aware.*` dispatch, token auth, UNIX socket / TCP serve. |
| `bin/aware.rs` | Situational-awareness handlers: `aware.sms`, `aware.whatsapp`, `aware.whatsapp.send`, `aware.call`, `aware.capture`, `aware.sync_contacts`, `sync.logseq`, `aware.travel`, `aware.open`, `aware.search`, `aware.email`, `aware.meeting`, `aware.briefing`, `aware.deals`. Engine interaction helpers (`autotask_post`, `proxy_http`, `http_get`, `notify`). |

### Other

- `Cargo.toml`, `Cargo.lock` — Rust workspace deps.
- `termux/` — legacy Termux integration (no longer used in the AutoTask architecture).
- `tests/`, `examples/` — test + example crates.
- `DEPLOY.md`, `README.md` — deployment + overview.
