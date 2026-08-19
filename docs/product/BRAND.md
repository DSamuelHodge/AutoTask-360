# Brand — Adjutant

Status: **Decided 2026-08-18.** Face name is **Adjutant**. AutoTask360 is the historical / runtime / package name. `applicationId` stays `com.aistudio.autotask.svcqx`. Scheme stays `autotask://`.

Related: [`DESIGN_SYSTEM.md`](DESIGN_SYSTEM.md), [`UI_UX.md`](UI_UX.md), [`ROADMAP.md`](ROADMAP.md). Runtime contract title remains AutoTask360 2.1.0 in [`../../spec.md`](../../spec.md) until a later spec PR.

## 1. What is printed today (2.1.0 lab)

| Surface | Today | Target (Phase 3) |
| --- | --- | --- |
| Play / install id | `com.aistudio.autotask.svcqx` | **unchanged** |
| Launcher label | `AutoTask` | **Adjutant** (`brand_name` / `app_name`) |
| AI Studio envelope | `"name": "AutoTask"` | Adjutant when that file is next touched |
| Spec | AutoTask360 2.1.0 | Keep title until a runtime PR |
| FGS notification | “AutoTask Engine Running” | Adjutant situation copy |
| Deep links | (none) | `autotask://…` (scheme **kept**) |

Sapphire-Blu is **not** a brand. It is a historical git remote to decommission ([`../architecture/REPOS.md`](../architecture/REPOS.md)).

## 2. Naming — decided

| Name | Stance |
| --- | --- |
| **Adjutant** | **Chosen** face name. Staff officer who executes. |
| AutoTask360 / AutoTask | Historical / runtime / `applicationId`. Not the launcher wordmark. |
| Castellan | **Rejected** |
| Sequence | **Rejected** |
| Antikythera | **Rejected** |

```xml
<string name="brand_name">Adjutant</string>
<string name="brand_name_long">Adjutant</string>
<string name="brand_role">Chief of Staff</string>
<string name="app_name">Adjutant</string>
```

Do not change `applicationId` or Kotlin `com.example` packages in brand PRs.

## 3. Brand idea

**A staff officer with a ledger, not a chatbot with plugins.**

- Situational, anticipatory, quiet.
- The human sees *what is happening* and *what just ran*, not a prompt box.
- Trust is visible (who acted: you, the brain, a paired remote).
- Motion is short and mechanical (checkpoint, not bounce).

Anti-ideas: neon “AI” gradients, chat-bubble home, Tasker toggle grids, Dynamic Island clones, Antikythera gears.

## 4. Mark

Today: slate + cyan stack + indigo lightning (lab). Target (Phase 3): a letter-free **seal** — closed ring interrupted by one cut. Dedicated monochrome path. `ic_stat_brand` 24 dp.

Background uses `Brand.ink`. Accent is **amber-on-ink** ([`DESIGN_SYSTEM.md`](DESIGN_SYSTEM.md) — decided).

## 5. Wordmark

**Adjutant** in title case. No “360” in the icon or wordmark. In-app chrome: mark + `brand_name`. Splash may be mark-only.

## 6. Splash (Android)

Android 12+ SplashScreen API. Background `Brand.ink`. Timed **400–1200 ms**. `runtimeReady` after `engine.start()`, **not** `isStarted()`. See design-system / master § splash.

## 7. Chrome

Home: `SituationHeader`. Other screens: mark + **Adjutant** + title. Cutout-safe insets. No fake island.

## 8. Notifications

Channel user-visible names use **Adjutant**. Four FGS types stay (8788/8789/8791/8792); situation replaces engine heartbeat **copy** only.

## 9. Widgets / live status

Deep links SoT: [`UI_UX.md`](UI_UX.md) §3 — `autotask://situation`, `autotask://profiles/{id}`, `autotask://runs/{runId}`. Scheme does **not** become `adjutant://`.

## 10. Copy

Precise, operator-facing. Runtime words stay (`WAITING`, `INDETERMINATE`). Established names: Adjutant (face), AutoTask360 (runtime spec), CoS, Facade, effect ledger, Turso, libSQL.

## 11. Phase 3 will not

- Change `applicationId`.
- Rewrite `namespace com.example`.
- Delete the Sapphire-Blu GitHub repo (operator action; see REPOS).
- Ship a Play listing (v1 is sideload-only).
