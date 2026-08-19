# UI/UX — production surfaces

Status: Target IA for Phases 3–5. **Today’s UI is a lab shell**; §2 documents it so implementers do not confuse it with the product.

Runtime stays behind `AutomationCommandFacade`. This document does not add a second execute path.

Design tokens: [`DESIGN_SYSTEM.md`](DESIGN_SYSTEM.md). Brand: [`BRAND.md`](BRAND.md). Sequencing: [`ROADMAP.md`](ROADMAP.md).

The product is **Android-native situational control**, not a chat wrapper.

## 1. Jobs to be done

| Actor | Job |
| --- | --- |
| Operator (human, on the phone) | See the situation, arm/disarm profiles, inspect a run, grant a capability, pair a remote, recover `INDETERMINATE`. |
| CoS (on-device brain) | Act without opening the UI. The UI **attributes** those acts. |
| Paired remote | Not a first-class in-app actor. The UI shows their fires and lets the operator revoke them. |

If a screen does not serve one of those jobs, it belongs behind Diagnostics.

## 2. What ships today (2.1.0)

Single activity, no Navigation graph, tab `Int` 0–4 (`AutoTaskMainScreen.kt`).

| Tab | Composable | What it does | Gaps |
| --- | --- | --- | --- |
| POLICIES | `PoliciesTab` | List `AutomationProfile`, toggle, FIRE ALL, NEW POLICY, JSON sheet | No run linkage, no search (`ProfileSearch` unused), 32 dp buttons |
| CATALOGUE | `CatalogueTab` | Schema triggers/actions, prefill new policy | Lab reference, not a destination |
| LOGS | `LogsTab` | `ExecutionLog` cards, CLEAR ALL | Not the durable run timeline; no `effectId`, no steps |
| EVENTS | `EventsTab` | Fire sample payloads / all 44 trigger types | Simulator, not “what just happened” |
| STATUS | `StatusTab` | Ktor bind, port, LAN flag, REST tester | No pairing UI, no brain panel, no watch facts |

Also present, not a “screen”:

- `MainActivity` + `enableEdgeToEdge()` + `MyApplicationTheme` (light-only)
- Add/edit policy dialogs + JSON dialog inside the same file
- `CapabilityOnboarding` — **no UI**
- WhatsApp QR: `WhatsAppBridgeActivity` (exported=false)
- FGS notifications (engine + brain) with stock icons
- **No** onboarding, pairing view, run timeline, schedule view, settings, widgets, splash, Glance

Treat the five tabs as **Diagnostics-grade** after Phase 4. The REST tester must survive behind a flag; it is how we still talk to 8788 without curl.

## 3. Information architecture (target)

```text
(splash — SplashScreen API)
    │
    ├─ first-run? → Onboarding
    └─ else → Root (single activity, Navigation-Compose)

Root
  Home            /situation
  Profiles        /profiles
    Profile       /profiles/{id}
    Editor        /profiles/{id}/edit          (optional; JSON editor ok v1)
  Runs            /runs
    Run           /runs/{runId}
  More
    Schedule      /schedule
    Trust         /trust                       pairing + credentials
    Diagnostics   /diagnostics                 old Status + watch + brain
    Settings      /settings
```

Bottom destinations: **Home · Profiles · Runs · More**. Catalogue and Events simulator move under Diagnostics. Logs are superseded by Runs (keep `execution_logs` as a Diagnostics dump).

Deep links (Phase 5 widgets / notifications). **This table is SoT.** [`BRAND.md`](BRAND.md) and the master design cite it. Plural resources match Navigation-Compose routes.

| URI | Destination |
| --- | --- |
| `autotask://situation` | Home |
| `autotask://profiles/{id}` | Profile detail |
| `autotask://runs/{runId}` | Run detail |
| `autotask://trust` | Pairing / trust |
| `autotask://diagnostics` | Diagnostics |

Do **not** use singular `/profile/` or `/run/`. Titles use **Adjutant** (`brand_name`). Scheme stays **`autotask://`** (decided). Do not switch to `adjutant://`.

## 4. Operator vs CoS surfaces

The same screens serve both. Attribution is the difference.

| Surface | Operator | When CoS acted |
| --- | --- | --- |
| Home | “You armed quiet-night” | Situation line: “CoS sent SMS to … · 2m ago” + `PrincipalChip(CoS)` |
| Run timeline | Local MANUAL fires | Rows tagged `INTERNAL_BRAIN`; tap-through to the same `Run` screen |
| Run detail | Retry / cancel | Same controls (privileged path). Copy explains CoS is not approval-gated. A paired-remote run that hit `APPROVAL_REQUIRED` shows the missing action types. |
| Notifications | Operator-initiated NOTIFICATION actions | Same channel; title prefix optional “CoS ·” |
| Widgets | Last run you fired | Last run **anyone** fired, with principal |
| Pairing | Human-only | Hidden from CoS; brain uses `cos-` on loopback |

There is **no chat transcript home**. If we later show CoS rationale, it is a card on the run (“CRM: VIP, open deal”) sourced from watch/RPC — not a message list.

## 5. Navigation and chrome

- `BrandTheme` dark-first ([`DESIGN_SYSTEM.md`](DESIGN_SYSTEM.md)).
- Top: `SituationHeader` on **Home only**. Other screens: small top app bar (mark + title + overflow). No situation chip in every top bar.
- Bottom `NavigationBar` 4 items, 48 dp min, **12 sp** labels (design-system `label` token is 12 / 16 / Medium — not 9 sp, not 11 sp).
- `WindowInsets.safeDrawing` on every scaffold. Cutout-safe. No fake island.
- Back: Navigation-Compose pop. Hardware back from root: system default (home), do not kill the FGS.

## 6. Screens

Each screen lists purpose, primary data, states, and facade calls.

### 6.1 Onboarding / permissions — `/onboarding`

**Purpose.** First-run and “repair a missing capability.” Consumes `CapabilityOnboarding` (`requiredCapabilitiesFor`, `repairActionFor`).

**Flow**

1. Mark + `brand_role` (“Chief of Staff”) + one sentence: the phone watches and acts; you stay in control of grants.
2. Runtime permissions in one grouped list: SMS, Phone, Notifications, Location (optional), Contacts (optional). Request via `ActivityResult` contracts.
3. Special access as separate cards (cannot be granted in-app): exact alarm, DND, notification listener, accessibility, battery exemption, write settings. Each card: why + “Open settings” (`repairActionFor`).
4. Optional: start `BrainService` + explain Turso is already baked if `.env` was set at build.
5. Done → `/situation`.

**States**

| State | UI |
| --- | --- |
| Empty (first launch) | Full pager |
| Partial grants | Continue enabled; warn which profiles will `SKIPPED` |
| Permission denied forever | Card: open App Info |
| Return from Settings | Re-read `CapabilityProvider.getCapabilitiesJson` |

**Not** a 12-step product tour. Catalogue is not onboarding.

### 6.2 Home / situation — `/situation`

**Purpose.** Answer: what is the phone doing, what will it do next, what just happened.

**Layout**

1. `SituationHeader`
   - Brain: running / halted / off (`BrainService.statusJson`)
   - Watch: bound or “8787 failed” (Headroom hazard)
   - Next schedule (`listSchedules` min `nextFireAt`)
2. **Now** card: in-flight runs (`QUEUED` / `RUNNING` / `WAITING`). Duration WAIT shows `wakeAt` (“continues automatically”). `kind=sms_sent` shows radio countdown (“fails in Ns”).
3. **Last 5 terminal** runs — `status in {SUCCESS, PARTIAL, FAILED, CANCELLED, SKIPPED, INDETERMINATE}`. Do **not** call `listRuns(limit=5)` unfiltered (in-flight rows steal slots). Prefer `GET /v1/runs?status=…&limit=5` (PR 4.0) or fetch N≫5 and filter. `PrincipalChip` from persisted `principalKind` / `principalId` (Room v7). Infer-from-source is rejected.
4. **Pinned profiles** (v1: first 3 enabled `cos-*` from seeder; later: operator pin). Arm switch + run.
5. Permission repair strip if any **enabled** profile is capability-blocked.

**States**

| State | UI |
| --- | --- |
| Empty (no profiles) | EmptyState → Profiles |
| Empty (no runs) | “No runs yet. Arm a profile or wait for the CoS.” |
| Brain halted | ErrorBanner + “Open Diagnostics” |
| Watch bind fail | ErrorBanner: “Port 8787 in use (Headroom?). Watch is dark.” |
| All profiles disarmed | Quiet home; CTA “Arm a profile” |

**Facade:** `listRuns`, `listSchedules`, `listProfiles`, `setProfileEnabled`, `requestRun` (MANUAL). Status extras from existing `/v1/status` or engine snapshots — do not add a second store.

### 6.3 Profiles — `/profiles`

Successor to Policies.

- Search field wired to `ProfileSearch` / `ProfileListQuery` (same rules as `GET /v1/profiles?q=`).
- Filters: enabled, trigger type.
- `ProfileCard`: name, id (mono), trigger, lastTriggeredAt, arm switch, overflow (edit / json / delete).
- FAB: new profile (schema-backed editor or JSON; JSON is acceptable v1 if Catalogue lives in Diagnostics).

**States:** empty, no search hits, save `InvalidAutomationException` inline.

**Facade:** `listProfiles`, `setProfileEnabled`, `upsertProfile` / `patchProfile`, `deleteProfile`, `validateAutomation`, `requestRun`.

### 6.4 Profile detail — `/profiles/{id}`

- Definition summary (trigger, steps, cooldown, priority, `riskPolicy`).
- Next fire if TIME/SCHEDULE/SUNRISE_SUNSET.
- Last 10 runs for this id.
- Capabilities required vs granted (`CapabilityOnboarding.requiredCapabilitiesFor`).
- Arm, Run now, Edit, Delete.

**CoS note:** if `riskPolicy.requireConfirmation` is set, show “operator opt-in hook — not enforced on CoS in 2.1.0” so we do not lie.

### 6.5 Run timeline — `/runs`

**This is the product’s history**, not `LogsTab`.

- Reverse chrono `listRuns`.
- Filters: status (incl. `INDETERMINATE`), profile, **`principalKind`** (Room v7, PR 4.0 — required before this screen ships).
- `RunRow` → detail.

**States:** empty, filter empty, recover-incomplete banner if `coordinator` still has `RUNNING` after process death (startup already recovers; banner if any remain `INDETERMINATE`).

### 6.6 Run detail — `/runs/{runId}`

The ledger, visible.

- Header: profile, status, duration, `runId`, `correlationId`, `retryOfRunId`.
- `StepRow` list: index, type, status, attempt, `effectId`, resume class (`safe` / `dedupe` / `fail-closed` from `StepResumePolicy`).
- Duration `WAIT` (`continuationJson.kind` absent or `wait`): `wakeAt` + **“will continue automatically.”**
- SMS park (`kind=sms_sent`): **“Waiting for radio — fails in Ns”** (countdown from `deadlineAt`). Not “will continue.” Timeout → `FAILED` `sms_radio_timeout`; do not imply success.
- `INDETERMINATE`: warning copy from [`BRAND.md`](BRAND.md) §11. Actions: Inspect (deep links: SMS app / call log) · Retry (new run, new `effectId`) · Dismiss.
- Terminal: Retry / (no cancel). In-flight: Cancel / Resume.

**Facade:** `getRun`, `cancelRun`, `retryRun`, `resumeRun`.

Do **not** show raw SMS body. `Redaction` applies. Detail string from the step is already what REST returns.

### 6.7 Schedule — `/schedule`

- Rows from `listSchedules`: profile, trigger, `nextFireAt`, `lastFiredAt`, `missedCount`, status/error.
- Reconcile button → `reconcileSchedules("manual")`.
- Tap → profile detail.

**States:** none registered; `status=error` (cron/timezone) with the `error` string; exact-alarm not granted → repair card.

### 6.8 Pairing / trust — `/trust`

Today pairing is **HTTP-only** (`POST /v1/pairing/start` …). The operator should not need curl.

**Layout**

1. LAN toggle (`POST /v1/pairing/lan`) — disabled until ≥1 active credential. Copy from [`../LAN_SECURITY.md`](../LAN_SECURITY.md).
2. Active credentials: name, scopes, `approvedActions`, last used, Revoke.
3. **Add device:** Start → show 6-digit `PairingCode` + 5-minute TTL. Completing still happens from the remote (`complete` with code). Phone is the display, not the typer.
4. Explain `cos-` vs `atc-`. Never show the raw `cos-` token. `atc-` raw token is shown **once** only if we ever complete pairing on-device (we should not).

**States:** no credentials; LAN-on; challenge expired; revoke confirm.

**Security:** this screen is `LOCAL_DEVICE` only. No export.

### 6.9 Diagnostics — `/diagnostics`

Absorbs Status + Catalogue + Events simulator.

Sections:

- Runtime: version `2.1.0` / `versionCode` 8, uptime, FGS
- Command bind: host, port 8788, LAN, last error
- Watch: 8787 running, buffered facts, recent `WatchFact` list
- Brain: `statusJson`, sock vs 8790, Turso configured (boolean only), last log lines **redacted**
- REST tester (existing OkHttp helper) behind “Advanced”
- Catalogue (schema) behind “Schema”
- Event simulator behind “Fire test event”
- `execution_logs` dump + clear
- ContentProvider URI
- Headroom note if watch failed to bind

This is the screen engineers keep. It is not Home.

### 6.10 Settings — `/settings`

- Appearance: dark / light / system (default dark)
- Start engine / start brain toggles (call existing service actions)
- Turso: configured yes/no; no token display; “re-seed from BuildConfig” is a debug-only action
- Retention copy (14 days / 500 logs) — read-only unless we add a settings write later
- About: `brand_name_long`, version, `applicationId`, link to `spec.md` meaning (“runtime 2.1.0”)
- Open source / licenses placeholder
- Export nothing by default (`allowBackup=false`)

### 6.11 WhatsApp bridge (existing)

Keep `WhatsAppBridgeActivity` as a **task** launched from Diagnostics or a profile that needs it. Do not put QR pairing on Home.

## 7. Global states

### 7.1 Empty

One sentence + one CTA. Examples in §6. Use `EmptyState` component. Never a blank `LazyColumn`.

### 7.2 Error

| Class | Treatment |
| --- | --- |
| Validation | Inline on the field / editor (`InvalidAutomationException.message`) |
| Capability | `PermissionRepairCard`, not a toast |
| Approval required (paired) | Banner listing missing `approvedActions` |
| Transport (tester) | Show status + body in Diagnostics only |
| Brain halted | Home + Diagnostics banners |
| Watch bind | Home banner |

Toasts are for `TOAST` actions the runtime fires, not for app errors.

### 7.3 Permission denied

Always: what failed, why the profile needs it, deep link. Never silently hide the profile.

### 7.4 Loading

First frame after splash may have empty lists (flows start empty). Use a 1-shot skeleton on Home only; do not spinner-block the facade.

### 7.5 Offline / airplane

Runtime is on-device. Airplane is a **trigger**, not an app-dead state. Turso sync may fail — show on Diagnostics, not as a full-screen error.

## 8. Widgets (Phase 5)

At least three. Glance. Tokens from the design system. Deep links in §3.

### 8.1 Next action / live situation — `situation`

- 2×2 and 4×2.
- Line 1: next `nextFireAt` or in-flight WAIT.
- Line 2: brain + watch glyph.
- Tap → `/situation`.

Empty: “Nothing scheduled.” Permission: “Exact alarm off” → Settings.

### 8.2 Last run — `last_run`

- Newest **terminal** run (same filter as Home).
- Profile name, `StatusBadge`, relative time, principal from persisted `principalKind` (PR 4.0).
- Tap → `autotask://runs/{runId}` (UI_UX §3).
- `INDETERMINATE` uses the purple token, never green.

### 8.3 Quick-arm — `quick_arm`

- 2×2: one pinned profile, big switch (48 dp).
- Calls `setProfileEnabled`. Haptic on toggle.
- If capability missing, widget shows “Repair” and opens onboarding repair.

**No** widget that sends SMS directly. Run-now from a widget is allowed only for profiles whose steps are all non-high-risk, or it opens the profile detail. Open question: whether widget run-now is ever allowed for `SEND_SMS`. Default: **no**.

## 9. Live status (Phase 5)

Documented in [`DESIGN_SYSTEM.md`](DESIGN_SYSTEM.md) §9.

Home situation model is the single source. Notification / Live Update / widgets **read the same snapshot** (in-process, fed by `WatchBus` + schedule store). Do not let the widget query Room with a different definition of “next.”

Cutout: chrome is inset; the live surface is the notification / chip, **not** a Compose pill drawn into the punch-hole.

## 10. Motion and empty/error choreography

- Arm switch: `motion.fast`.
- New run appearing on Home: `motion.base` fade+slide 8 dp.
- `INDETERMINATE`: no celebration animation.
- Permission repair: no shake. Static card.
- Splash → Home: system SplashScreen exit; first frame already inset.

## 11. Implementation plan (UI PRs)

See master PR plan. Inside the app:

1. Tokens + theme + splash (Phase 3) — lab tabs still visible, recolored. `runtimeReady` splash 400–1200 ms.
2. **Room v7 principal columns (4.0)** then Navigation-Compose + `FeatureFlags` (4.1).
3. Home + Runs (4.2/4.3) — **gated on 4.0**. Last-5 = terminal only.
4. Profiles + Onboarding + Trust (4.4–4.6). Onboarding depends on 4.1 only.
5. Schedule + Settings + move lab into Diagnostics (4.7). Flag default on.
6. Widgets + situation ongoing (Phase 5). Deep links as §3.

Do not rewrite `AutoTaskMainScreen.kt` in one PR. Extract tabs as Diagnostics children first.

## 12. Explicit non-goals

- Chat-first home.
- Driving other apps from the operator UI (that is accessibility + facade `UI_DRIVE`, Diagnostics).
- iOS Dynamic Island clone.
- Mac CoS window.
- A second JSON schema in the UI that the compiler does not understand.
