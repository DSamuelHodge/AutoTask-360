# Persistence — `autotask.db` vs `cos.db`

Status: descriptive of AutoTask360 2.1.0.

Runtime contract: [`../../spec.md`](../../spec.md) §9 and §16. Architecture: [`OVERVIEW.md`](OVERVIEW.md).

**Rule:** Room exclusively owns `autotask.db`. The Rust brain exclusively owns `cos.db`. They meet only over versioned RPC. They never share a SQLite file, a Room handle, or a WAL.

## 1. Why two files

| Concern | AutoTask (`autotask.db`) | CoS (`cos.db`) |
| --- | --- | --- |
| Job | Definitions, admission, execution, audit | Memory, relationships, calendar, CRM |
| Writer | One Android process via Room | One Rust child via libSQL |
| Crash model | Durable runs / steps / effect ledger | WAL + optional Turso replica |
| Clients | Facade, UI, REST, MCP | `aware.*` / `crm.*` / `cal.*` RPC |
| Cloud | None | Turso is SoT when `TURSO_URL` + `TURSO_TOKEN` are set |

Sharing a file would couple two page-cache owners, two migration systems (Room vs `CREATE TABLE IF NOT EXISTS` batches), and two crash domains. PR2 split the paths for that reason. `AutoTaskDatabasePathTest` asserts the files are different:

```text
Room  = context.getDatabasePath("autotask.db")
Rust  = File(context.getDir("brain", MODE_PRIVATE), "cos.db")
```

## 2. `autotask.db` — Room, currently version 6

**Owner:** `app/src/main/java/com/example/data/AutoTaskDatabase.kt`

```text
@Database(
  entities = [
    AutomationProfile, ExecutionLog, EventEnvelopeEntity,
    AutomationRunEntity, StepRunEntity, ScheduleRegistrationEntity,
    EffectRecordEntity
  ],
  version = 6,
  exportSchema = false
)
```

`DATABASE_NAME = "autotask.db"`. Path: Android’s default databases directory.

**Planned v7 (PR 4.0, before Home chips):** `automation_runs.principalKind` and `principalId` (`TEXT NOT NULL`, defaults `LOCAL_DEVICE` / `local-device` for existing rows). Written at admission from `CommandContext`. Not inferable from today’s columns. Same PR updates `spec.md` run snapshot JSON. Do not implement in this docs pass.

### 2.1 Tables

| Table | Entity | Role |
| --- | --- | --- |
| `automation_profiles` | `AutomationProfile` | Versioned definitions as JSON columns (`triggerConfigJson`, `conditionsJson`, `actionsJson`) plus `schemaVersion`, `revision`, cooldown, priority. Compile-on-write produces typed `AutomationDefinition` in memory. |
| `execution_logs` | `ExecutionLog` | Human/ops audit rows. Capped at 500 newest and 14-day age. |
| `event_envelopes` | `EventEnvelopeEntity` | Admitted events. Unique `eventId`, optional `dedupeKey` + `idempotencyKey`. |
| `automation_runs` | `AutomationRunEntity` | Durable run header: status, `currentStepIndex`, attempt, `wakeAt`, `retryOfRunId`. |
| `step_runs` | `StepRunEntity` | Per-step checkpoint. `effectId` added in v5. |
| `schedule_registrations` | `ScheduleRegistrationEntity` | Next-fire, delivery mode, timezone, missed count. |
| `effect_records` | `EffectRecordEntity` | **v6.** Write-ahead outcome ledger keyed by `effectId`. |

### 2.2 Migrations

| Migration | What landed |
| --- | --- |
| 1 → 2 | `schemaVersion`, `revision` on profiles |
| 2 → 3 | `runId` on logs; `event_envelopes`; `automation_runs`; `step_runs` |
| 3 → 4 | `schedule_registrations` |
| 4 → 5 | `step_runs.effectId` |
| 5 → 6 | `effect_records (effectId PK, type, status, detail, runId, stepIndex, completedAt)` |

`exportSchema = false` — there is no checked-in Room schema JSON. Release gate in `spec.md` still requires tested migrations from the previous released schema.

### 2.3 Effect ledger

`effectId` is generated when the coordinator admits a step as `RUNNING` (`RunCoordinator`). That is the **request** record.

On handler `OK`, if `StepResumePolicy.dedupesByEffectId(type)`, `ActionExecutor` writes `EffectRecord(status=OK)` via `RunStore.putEffect`. Resume of a dedupe-capable `RUNNING` step re-enters `execute` with the **same** `effectId`. If the ledger already has `OK`, the executor returns that detail and **does not** repeat the side effect. If the ledger has no `OK` (crash before commit), execute runs again (at-least-once).

Safe types (`LOG`, `WAIT`, `TOAST`) re-enter without ledger consult. Camera, toggles, UI drive stay fail-closed (`INDETERMINATE`).

`RetentionSweeper` deletes effect rows older than the same 14-day cutoff as terminal runs.

### 2.4 Legacy import

`LegacyAutoTaskMigration` opens `BrainService.dbPath(context)` **read-only**. If that file still has `automation_profiles` / `execution_logs` (pre-PR2 shared file), it copies those tables into Room once, then sets SharedPreferences marker `legacy_shared_db_v1_complete`. It never deletes or writes the Rust file.

### 2.5 What AutoTask must not store

- CRM contacts, deals, calendar bookings, embeddings
- Turso credentials (those live in `TursoConfig` EncryptedSharedPreferences)
- Brain auth token (`brain_config` prefs, `cos-…`)
- Raw screen buffers, full SMS bodies in routine logs (`Redaction`)

## 3. `cos.db` — libSQL embedded replica

**Owner:** Rust crate `agentcal` (`/Users/hodgeluke/Desktop/Projects/agent-cal-crm`), binary `cos` (`src/bin/cos.rs`), shipped as `app/src/main/jniLibs/arm64-v8a/libcosd.so`.

**Open path:** `BrainService.dbPath()` → `files/brain/cos.db` (app-private dir `brain`, not the Room databases folder).

**Open logic:** `LibSqlStore::open_with_builder` (`src/store/libsql_store.rs`):

- If `TURSO_URL` **and** `TURSO_TOKEN` are set → `libsql::Builder::new_remote_replica(path, https_url, token)`. `libsql://` is rewritten to `https://`.
- Else → `Builder::new_local(path)`.

When Turso is configured, `BrainService.spawnBrain` **deletes** local `cos.db` (+ wal/shm/info) before spawn so a stale plain file cannot be adopted as a replica, and **skips** `cos seed`. Cloud is the source of truth. `cos serve` then syncs every 30s on a background Tokio runtime (`cos.rs`).

When Turso is **not** configured, spawn runs `cos seed --db <path>` first (idempotent starter data). **Owner id is `COS_OWNER`** (KD-17). Do not default to `"derrick"`. Empty `COS_OWNER` is fail-visible.

### 3.1 Schema (Rust-owned)

Applied as a `CREATE TABLE IF NOT EXISTS` batch in `libsql_store.rs`. Not Room. Not migrated by `AutoTaskDatabase`.

Includes:

- Calendar: `calendars`, `availability_windows`, `blocked_periods`, `booking_links`, `bookings`, `attendees`
- CRM: `companies`, `contacts` (with `F32_BLOB(64)` embedding), `deals`, `interactions`, FTS5 `crm_fts`
- CoS additive model: `principals`, `entities`, `entity_channels`, `entity_aliases`, plus further CoS tables in the same batch

Today the seed still inserts `principals` `derrick` (owner) and `agent` in `libsql_store.rs`. **PR 2.0** must read **`COS_OWNER`** for the owner principal and for `aware.*` / MCP examples. Hardcoded `"derrick"` must not ship in 2.2.

### 3.2 TLS

Android has no rustls-native CA store. `BrainService.extractCaBundle` copies `res/raw/cacert.pem` to `files/brain/cacert.pem` and sets `SSL_CERT_FILE` in the child environment. Without this, Turso sync fails certificate verification.

### 3.3 Credentials

`TursoConfig` (`app/src/main/java/com/example/wa/TursoConfig.kt`):

- Secrets Gradle plugin reads git-ignored `.env` → `BuildConfig.TURSO_URL` / `BuildConfig.TURSO_TOKEN`
- First `getUrl` / `getToken` seeds EncryptedSharedPreferences (Keystore AES-GCM)
- Runtime overrides possible; never log the token

`isConfigured` is true only when both URL and token are non-empty.

## 4. Crossing the boundary

Allowed (explicit RPC only):

| Path | Direction | Payload |
| --- | --- | --- |
| `POST /v1/brain` | Engine → brain sock | `{method, params}` — `aware.*`, `crm.*`, `cal.*` |
| MCP `aware.*` / `crm.*` | Client → engine → brain | Same RPC, via `McpHandler` |
| `POST /v1/http` | Brain → engine → internet | Outbound HTTPS proxy (brain does not dial TLS itself for those calls) |
| `POST /v1/contacts` | Engine reads device address book | Opt-in sync into CRM |
| `POST /v1/location` | Engine → brain / travel | Last-known lat/lng when travel asks |
| Watch 8787 | Engine → subscribers | Event + terminal-run facts. Brain **should** subscribe (Phase 2); it does not today. |

Forbidden:

- Room file, Room handle, or unbounded table dump to Rust
- Run/step rows inside `cos.db`
- Credentials / tokens in either DB
- Full message bodies or screen buffers in routine logs

## 5. What must not happen

1. **Do not** point Room at `BrainService.dbPath`.
2. **Do not** open `autotask.db` from Rust (no libSQL, no JNI, no `sqlite3` CLI “for debug”).
3. **Do not** attach both files in one SQLite session.
4. **Do not** copy run/step rows into Turso “for convenience.”
5. **Do not** treat the phone replica as SoT when `TURSO_*` is set — `BrainService` already deletes the local file on each spawn in that mode.

## 6. Backup

`android:allowBackup="false"` ([`../BACKUP_REDACTION_POLICY.md`](../BACKUP_REDACTION_POLICY.md)). Profile JSON can contain webhook secrets, SMS templates, and phone numbers. If backup is ever re-enabled, exclude both DB files, `turso_config` prefs, and `brain_config` prefs.
