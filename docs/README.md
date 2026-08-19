# AutoTask360 documentation

`spec.md` at the repo root remains the **active runtime specification** (Status: Active — AutoTask360 2.1.0). The **user-facing product name is Adjutant**; the spec title can stay AutoTask360 until a later runtime PR. These docs cite `spec.md`. They do not fork run/step semantics, effect-id rules, resume policy, or the command contract.

`spec.md` §1 already says the Rust brain subscribes to watch 8787. That is the **contract**. **2.1.0 ships the socket; the Rust subscriber lands in 2.2** ([`architecture/OVERVIEW.md`](architecture/OVERVIEW.md) §6). When a later implementation PR touches `spec.md`, add that clause there. Do not edit `spec.md` in this docs-only pass.

This folder now holds two layers:

1. **Architecture and product** — how the 2.1.0 system is built, and the production-ready plan to ship CoS-on-phone.
2. **Operations** — signing, LAN, backup, and field troubleshooting. Those files predate this set; they are unchanged except for being indexed here.

The approved production design (Key Decisions + PR Plan) is [`DESIGN.md`](DESIGN.md). Architecture and product pages below are the implementer-facing split of that packet.

## Runtime contract (do not fork)

| Document | Role |
| --- | --- |
| [`../spec.md`](../spec.md) | Command boundary, durable-run semantics, persistence split, security posture, PR 1–7 status. **Authoritative.** |

## Architecture (today)

| Document | Contents |
| --- | --- |
| [`architecture/OVERVIEW.md`](architecture/OVERVIEW.md) | 2.1.0 system architecture: processes, ports, trust principals, persistence split, repo topology. |
| [`architecture/CODEBASE.md`](architecture/CODEBASE.md) | Package map, key types, and how a command travels REST/MCP/UI → Facade → store/queue/coordinator/handlers. |
| [`architecture/DATA.md`](architecture/DATA.md) | `autotask.db` (Room v6) vs `cos.db` (libSQL replica). What lives where. Why they never share a file. |
| [`architecture/REPOS.md`](architecture/REPOS.md) | AutoTask-360 (`origin` only) vs decommissioned Sapphire-Blu vs Cal-CRM (`libcosd.so`). |

## Product (target)

| Document | Contents |
| --- | --- |
| [`product/ROADMAP.md`](product/ROADMAP.md) | Phases 0–6 from 2.1.0 to a shippable CoS-on-phone product, with exit criteria. |
| [`product/UI_UX.md`](product/UI_UX.md) | Information architecture, every production view, operator vs CoS surfaces, empty/error/permission states. |
| [`product/DESIGN_SYSTEM.md`](product/DESIGN_SYSTEM.md) | Tokens, components, motion, accessibility, cutouts, and Android live-status surfaces. |
| [`product/BRAND.md`](product/BRAND.md) | **Adjutant** face name, icon, splash, chrome. Package remains AutoTask360 / `com.aistudio.autotask.svcqx`. |

## Operations (existing; unchanged)

These pre-existed this documentation freeze. Do not treat them as architecture specs.

| Document | Contents |
| --- | --- |
| [`BACKUP_REDACTION_POLICY.md`](BACKUP_REDACTION_POLICY.md) | `allowBackup=false`, sensitive profile fields, token lifecycle. |
| [`LAN_SECURITY.md`](LAN_SECURITY.md) | Loopback vs LAN bind, `cos-` vs `atc-` credentials, pairing, high-risk remote approvals. |
| [`RELEASE_SIGNING.md`](RELEASE_SIGNING.md) | CI-injected keystore, `versionCode` 8 / `versionName` 2.1.0 ownership. |
| [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md) | Permissions, scheduler, run recovery, 8787/8788 bind failures. |

Related harness notes (not in this folder): [`../dev/AGENT_HARNESS_MCP.md`](../dev/AGENT_HARNESS_MCP.md), [`../dev/termux/openinterpreter/skills/autotask/SKILL.md`](../dev/termux/openinterpreter/skills/autotask/SKILL.md), [`.grok/skills/autotask/SKILL.md`](../.grok/skills/autotask/SKILL.md).

## How to read this set

1. Start at [`../spec.md`](../spec.md) for runtime rules.
2. Read [`architecture/OVERVIEW.md`](architecture/OVERVIEW.md) for the phone as it ships today.
3. Use [`architecture/CODEBASE.md`](architecture/CODEBASE.md) when changing Kotlin.
4. Use [`architecture/DATA.md`](architecture/DATA.md) before touching Room or the Rust store.
5. Use [`product/ROADMAP.md`](product/ROADMAP.md) for sequencing. UI/UX is first-class, not leftover.
6. Face name is **Adjutant** ([`product/BRAND.md`](product/BRAND.md)). Runtime spec title stays AutoTask360 2.1.0 until a later `spec.md` PR. v1 is **sideload only**.

## Known doc drift

[`TROUBLESHOOTING.md`](TROUBLESHOOTING.md) still says a crashed `SEND_SMS` stays `INDETERMINATE`. That was true in 2.0. In 2.1.0 `SEND_SMS` is dedupe-capable (`StepResumePolicy.dedupeCapableTypes`); the executor consults `effect_records` and skips a second send when that `effectId` already committed `OK`. The runtime contract is [`../spec.md`](../spec.md) §6.3.
