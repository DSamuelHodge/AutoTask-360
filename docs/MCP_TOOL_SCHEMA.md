# AutoTask MCP Tool Schema (bounded surface)

> Kaneo #22 / GH #22. Parent: #2 (opt-in remote CoS endpoint).
> Status: **design + Kotlin sketch**. No MCP server ships in this PR.

This document defines the **complete and closed** set of MCP tools AutoTask exposes to an
agent. It is a *bounded* surface: an MCP adapter may expose these eleven tools and nothing
else. Every tool maps onto an endpoint the loopback server (`KtorLoopbackServer`, default
`127.0.0.1:8788`) already implements, so the adapter is a thin translation layer and never a
second implementation of engine behaviour.

## Design rules

1. **Closed set.** Eleven tools, enumerated below. An adapter that exposes a tool not in this
   list is non-conforming.
2. **NO generic shell / run-anything tool.** There is deliberately no `exec`, `shell`, `run`,
   `eval`, `adb`, `intent_send`, or `http_request` tool. The agent may only manipulate
   *declarative automation profiles*; it may never ask the device to run arbitrary code. Any
   future proposal to add one must be treated as a redesign of the trust model, not a feature.
3. **Stateless.** No sessions, cursors, or server-side agent state. Each call is independently
   authorized and independently auditable. Re-authorization happens per call.
4. **Bounded blast radius via declarative indirection.** Side effects only occur when the
   *engine* executes a profile's actions. The agent writes profiles and fires synthetic
   events; it never invokes an action executor directly.
5. **Risk is inherited, not declared by the caller.** A tool's risk class is the maximum risk
   of what it can cause. `profiles_upsert` is `secure_settings_mutation` because a profile can
   contain a `DND` action — even though the write itself is just a DB row.
6. **Mechanical mapping.** Every tool names the exact HTTP method + path it proxies.

## Risk classes

Reuses the taxonomy from `com.example.engine.RiskClass` (#20) verbatim — do not invent new
names here:

| Class | Meaning |
| --- | --- |
| `observe_only` | Reads state. No device mutation. |
| `local_ux` | Local, visible, trivially reversible (toast, vibrate, flashlight). |
| `external_network` | Leaves the device (HTTP, open URL, broadcast). |
| `message_phone` | Contacts third parties or costs money (SMS, call, camera). |
| `secure_settings_mutation` | Changes protected system settings (DND, brightness, rotation, audio). |

Tool-level risk is the **ceiling** of what the tool can trigger, per rule 5.

## Scopes

Scopes are coarse and additive. A credential carries a set; a tool requires exactly one.

| Scope | Grants |
| --- | --- |
| `read` | `status`, `capabilities`, `schema`, `profiles_list`, `profiles_get`, `logs_recent` |
| `write` | `profiles_upsert`, `profiles_patch`, `profiles_delete`, `logs_clear` |
| `execute` | `events_fire` with `dryRun=false` |

`execute` is **not** implied by `write`. A credential may hold `read`+`write` and still be
unable to cause any device side effect — it can author profiles but not fire them. This is the
recommended default for remote agents.

## Auth model (remote mode only)

Loopback callers on `127.0.0.1` are already inside the app sandbox's trust boundary and are
unauthenticated today. **These auth notes apply only when the opt-in remote listener of #23 is
enabled.** See `REMOTE_COS_DESIGN.md` and `REMOTE_COS_THREAT_MODEL.md`.

- Every remote call carries `Authorization: Bearer <apiKey>` (see `ApiKeyManager`).
- The key's scope set is checked *before* dispatch; scope failure returns `403` and is audited.
- Writes additionally require `ExecutionPolicy.isAgentWriteAllowed()`; high-risk execution
  additionally requires `ExecutionPolicy.isHighRiskAllowed()`. The kill switch beats the key:
  a valid, in-scope key is still refused when the user has disabled agent writes.
- Every call is audit-logged with the key id (never the key itself), tool name, and outcome.

---

## Tools

### 1. `status`

Engine liveness and readiness snapshot.

- **Maps to:** `GET /v1/status`
- **Scope:** `read` · **Risk:** `observe_only`
- **Input:** `{}` (no properties, `additionalProperties: false`)
- **Output:**

```json
{
  "engine_running": 1,
  "profile_count": 12,
  "log_count": 340,
  "ktor_server_running": true,
  "ready": { "api": true, "permissions": false, "dnd": false,
             "device_settings": true, "notification_listener": false },
  "uptime_ms": 918273,
  "version": "1.0.0"
}
```

- **Auth notes:** Safe first call for any agent. Reveals permission posture but no user
  content. In remote mode the response SHOULD omit `relay_target` and `provider_uri`, which
  describe internal attack surface and are of no use to a remote caller.

### 2. `capabilities`

What this device can actually do, including per-action risk and readiness.

- **Maps to:** `GET /v1/capabilities`
- **Scope:** `read` · **Risk:** `observe_only`
- **Input:** `{}`
- **Output:** the `CapabilityProvider` document — `permissionSummary`, `declaredPermissions`,
  `specialAccess`, `runtimePermissions`, `triggerRequirements`, `actions` (each with `risk`,
  `autonomy`, `ready`, `requirements`, `notes`), `provisioningHints`, `agentPolicy`.
- **Auth notes:** The agent MUST consult `actions[type].ready` before authoring a profile that
  uses that action; authoring against an unready capability produces a profile that silently
  skips at runtime. `provisioningHints` contains ADB command templates — a remote adapter
  SHOULD strip that block, as it is operator-facing and useless to a remote agent.

### 3. `profiles_list`

Enumerate all automation profiles.

- **Maps to:** `GET /v1/profiles`
- **Scope:** `read` · **Risk:** `observe_only`
- **Input:** `{}`
- **Output:** array of profile objects (see `profiles_get`).
- **Auth notes:** Profile bodies may embed user content (SMS text, phone numbers, URLs). Treat
  the response as sensitive; it is a data-egress path in remote mode.

### 4. `profiles_get`

Fetch one profile by id.

- **Maps to:** `GET /v1/profiles/{id}`
- **Scope:** `read` · **Risk:** `observe_only`
- **Input:**

```json
{ "type": "object", "additionalProperties": false,
  "required": ["id"],
  "properties": { "id": { "type": "string", "minLength": 1, "maxLength": 128 } } }
```

- **Output:**

```json
{ "id": "night-mode", "name": "Night mode", "description": "",
  "isEnabled": true, "triggerType": "TIME",
  "triggerConfigJson": {}, "conditionsJson": {}, "actionsJson": [],
  "cooldownMs": 0, "priority": 0,
  "createdAt": 0, "updatedAt": 0, "lastTriggeredAt": 0 }
```

- **Errors:** `404` when absent.
- **Auth notes:** `id` is caller-supplied and used as a DB key — the adapter MUST reject ids
  containing path separators or exceeding `maxLength` before proxying.

### 5. `profiles_upsert`

Create or replace a profile. **Validated.**

- **Maps to:** `POST /v1/profiles`
- **Scope:** `write` · **Risk:** `secure_settings_mutation` (ceiling — a profile may contain any action)
- **Input:**

```json
{ "type": "object", "additionalProperties": false,
  "required": ["id", "name", "triggerType"],
  "properties": {
    "id":          { "type": "string", "minLength": 1, "maxLength": 128 },
    "name":        { "type": "string", "minLength": 1, "maxLength": 256 },
    "description": { "type": "string", "maxLength": 1024 },
    "isEnabled":   { "type": "boolean", "default": false },
    "triggerType": { "type": "string", "enum": ["MANUAL", "TIME", "SCHEDULE", "WIFI",
                                                "BLUETOOTH", "NOTIFICATION", "SMS",
                                                "CALL", "LOCATION", "POWER", "..."] },
    "triggerConfigJson": { "type": "object" },
    "conditionsJson":    { "type": "object" },
    "actionsJson":       { "type": "array", "maxItems": 32,
                           "items": { "type": "object", "required": ["type"] } },
    "cooldownMs": { "type": "integer", "minimum": 0 },
    "priority":   { "type": "integer" }
  } }
```

- **Validation performed server-side (already in `KtorLoopbackServer`):** `id`, `name`,
  `triggerType` non-blank; `triggerType` upper-cased; unparseable bodies → `400`.
- **Additional validation REQUIRED of an MCP adapter:** reject unknown `triggerType`; reject
  action `type` values outside `ActionRisk.knownTypes()`; enforce `maxItems`; enforce a body
  size cap (see threat model). Fail closed on anything unrecognised.
- **Output:** `{ "status": "OK", "message": "...", "profile": { ... } }`, HTTP `201`.
- **Auth notes:** This is the primary privilege-escalation path — a profile is *stored,
  deferred code*. Writing `isEnabled: true` with a `TIME` trigger and a `SEND_SMS` action
  creates a recurring side effect that outlives the agent session. Therefore:
  `ExecutionPolicy.isAgentWriteAllowed()` MUST gate this tool, and profiles authored by a
  remote agent SHOULD be persisted with `isEnabled=false` regardless of the requested value,
  requiring a local human toggle to arm them. Provenance is recorded per #21.

### 6. `profiles_patch`

Partially update an existing profile.

- **Maps to:** `PATCH /v1/profiles/{id}`
- **Scope:** `write` · **Risk:** `secure_settings_mutation` (ceiling)
- **Input:** `{ "id": "<string>", "patch": { <any subset of the upsert properties> } }`
- **Output:** `{ "status": "OK", "message": "Profile patched", "profile": { ... } }`
- **Errors:** `404` when absent; `400` on invalid patch.
- **Auth notes:** Same escalation concern as `profiles_upsert`, plus a subtler one: patching
  `isEnabled: true` **arms an existing profile the agent did not author and may not have
  read**. An adapter SHOULD treat an `isEnabled` transition `false → true` as requiring the
  same confirmation as a high-risk action, and MUST audit-log the before/after value.

### 7. `profiles_delete`

Delete a profile by id.

- **Maps to:** `DELETE /v1/profiles/{id}`
- **Scope:** `write` · **Risk:** `local_ux` (destructive to config, not to the device)
- **Input:** `{ "id": "<string>" }`
- **Output:** `{ "status": "OK", "deletedProfileId": "<id>" }`
- **Errors:** `404` when absent.
- **Auth notes:** Deletion is availability-affecting and irreversible (no soft delete today).
  A remote caller silently deleting safety-relevant automations is a real denial-of-function
  attack; audit-log the full profile body before deletion.

### 8. `events_fire`

Fire a **synthetic** event. `MANUAL` / test use only.

- **Maps to:** `POST /v1/events`
- **Scope:** `execute` when `dryRun=false`; `read` when `dryRun=true` · **Risk:** `secure_settings_mutation` (ceiling)
- **Input:**

```json
{ "type": "object", "additionalProperties": false,
  "properties": {
    "triggerType":     { "type": "string", "default": "MANUAL" },
    "targetProfileId": { "type": ["string", "null"], "maxLength": 128 },
    "dryRun":          { "type": "boolean", "default": true },
    "payload":         { "type": "object" }
  } }
```

- **Output (dryRun):** `{ "status":"OK", "dryRun":true, "profilesMatched":N,
  "logsGenerated":0, "plannedProfiles":[...] }` — the plan, with nothing executed.
- **Output (live):** `{ "status":"OK", "dryRun":false, "logsGenerated":N,
  "results":[{ "id","profileId","profileName","status","skippedReason","durationMs" }] }`
- **Auth notes:** The **only** tool that causes immediate device side effects, and the one to
  scrutinise. Constraints:
  - **Synthetic only.** The agent may inject `MANUAL` (and explicitly test-designated) trigger
    types. It MUST NOT be able to forge a `SMS`/`CALL`/`NOTIFICATION` event to impersonate a
    real-world signal and thereby launder a trusted trigger. An adapter SHOULD restrict
    `triggerType` to `MANUAL` unless the operator has opted into synthetic trigger injection.
  - **`dryRun` defaults to `true`** at the MCP layer (the HTTP endpoint defaults to `false`;
    the adapter tightens this). An agent must explicitly opt into a live fire.
  - Live fire requires `execute` scope **and** `ExecutionPolicy.executionEnabled` **and**, for
    profiles containing high-risk actions, `ExecutionPolicy.isHighRiskAllowed()`.

### 9. `logs_recent`

Recent execution logs.

- **Maps to:** `GET /v1/logs?limit=N`
- **Scope:** `read` · **Risk:** `observe_only`
- **Input:** `{ "limit": { "type": "integer", "minimum": 1, "maximum": 500, "default": 100 } }`
- **Output:** array of `{ id, profileId, profileName, triggerType, status, skippedReason,
  actionsResultJson, durationMs, timestamp }`.
- **Auth notes:** Logs contain action *results*, which may include message bodies, URLs, and
  recipient numbers. This is the highest-density data-egress path in the read scope. The
  adapter SHOULD cap `limit` and MAY redact `actionsResultJson` for remote callers.

### 10. `logs_clear`

Delete all execution logs.

- **Maps to:** `DELETE /v1/logs`
- **Scope:** `write` · **Risk:** `local_ux`
- **Input:** `{}`
- **Output:** `{ "status": "OK", "message": "Execution logs cleared" }`
- **Auth notes:** **Anti-forensic.** An attacker's natural last step is to erase evidence.
  The remote audit log (#24) MUST be a separate, append-only sink that `logs_clear` cannot
  reach, and the clear itself MUST be recorded there. Consider denying this tool to remote
  credentials entirely.

### 11. `schema`

Machine-readable description of triggers, conditions, and actions.

- **Maps to:** `GET /v1/schema`
- **Scope:** `read` · **Risk:** `observe_only`
- **Input:** `{}`
- **Output:** the `SchemaProvider` document.
- **Auth notes:** Static, non-sensitive. Together with `capabilities`, this is what an agent
  should read before authoring profiles.

---

## Summary table

| Tool | Method + path | Scope | Risk class |
| --- | --- | --- | --- |
| `status` | `GET /v1/status` | `read` | `observe_only` |
| `capabilities` | `GET /v1/capabilities` | `read` | `observe_only` |
| `schema` | `GET /v1/schema` | `read` | `observe_only` |
| `profiles_list` | `GET /v1/profiles` | `read` | `observe_only` |
| `profiles_get` | `GET /v1/profiles/{id}` | `read` | `observe_only` |
| `logs_recent` | `GET /v1/logs?limit=N` | `read` | `observe_only` |
| `profiles_upsert` | `POST /v1/profiles` | `write` | `secure_settings_mutation` |
| `profiles_patch` | `PATCH /v1/profiles/{id}` | `write` | `secure_settings_mutation` |
| `profiles_delete` | `DELETE /v1/profiles/{id}` | `write` | `local_ux` |
| `logs_clear` | `DELETE /v1/logs` | `write` | `local_ux` |
| `events_fire` | `POST /v1/events` | `execute` (live) / `read` (dryRun) | `secure_settings_mutation` |

## Non-goals — tools deliberately absent

| Rejected tool | Why |
| --- | --- |
| `shell` / `exec` / `run` | Unbounded. Defeats the entire capability model. Never add. |
| `http_request` | Would turn the device into an SSRF proxy onto the user's LAN. The `HTTP` *action* stays reachable only through a stored, user-armable profile. |
| `intent_send` | Arbitrary intent dispatch is equivalent to arbitrary app control. |
| `permissions_grant` | Permission grants are a user/device-owner decision, never an agent one. |
| `settings_write` | Direct settings writes bypass profile provenance and the risk taxonomy. |
| `contacts_read` / `sms_read` | Bulk PII reads with no automation purpose. |
| `execution_policy_set` | The kill switch must never be disableable by the party it restrains. |

## Kotlin sketch

`app/src/main/java/com/example/server/mcp/McpToolSchema.kt` encodes this table as data so the
doc and any future adapter cannot drift. It is inert — pure declarations, no server, no
transport, no wiring into `KtorLoopbackServer`.
