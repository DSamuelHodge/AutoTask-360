# Opt-in Remote CoS Endpoint — Design

> Kaneo #2 (parent) / GH #10. Children: #22 (MCP tool schema), #24 (threat model),
> #23 (API-key alpha).
> Status: **design + scaffold**. **No remote listener ships in this PR.**

## Problem

`KtorLoopbackServer` binds `127.0.0.1:8788`. An agent must therefore run *on the device*, or a
human must bridge with `adb forward`. Users want an off-device agent (a laptop assistant, a
hosted CoS) to read status and author automations.

The naive fix — bind `0.0.0.0` — is unacceptable. It converts an unauthenticated API with SMS,
telephony, camera, and location permissions into an internet-reachable service. This document
describes the only shape of remote access we consider defensible.

## Principles

1. **Opt-in, default-off.** `RemoteAccessConfig.DEFAULT_ENABLED = false`. An install is never
   remotely reachable until the user deliberately makes it so.
2. **Separable from loopback.** Remote is a distinct listener on a distinct port. Enabling or
   disabling it does not perturb loopback behaviour.
3. **Bounded surface.** Remote callers reach the eleven tools of #22 and nothing else. No
   shell, no arbitrary HTTP, no intent dispatch.
4. **Scoped credentials.** Per-client keys carrying `read` / `write` / `execute`. `execute` is
   never implied by `write`. Default issuance is `read` only.
5. **Audit everything, attributably.** Every remote call is logged with the requesting key id,
   to an append-only sink the API cannot clear.
6. **Kill switch above all.** One local tap disables agent writes, overriding any valid key.
7. **Fail closed.** Ambiguity — unreadable state, expired key, unknown action type, missing
   policy — always resolves to refusal.

## Architecture

```
  ┌────────────────────┐        TLS + Bearer key        ┌───────────────────────────────┐
  │  Off-device agent  │ ─────────────────────────────► │  Remote listener  :8789       │
  │  (MCP client)      │                                │  (OPT-IN, DEFAULT OFF)        │
  └────────────────────┘                                │                               │
                                                        │  1. TLS terminate  (T1)       │
                                                        │  2. Rate limit     (T8)       │
                                                        │  3. ApiKeyManager  (#23, T7)  │
                                                        │  4. Scope check    (T5)       │
                                                        │  5. Replay/nonce   (T3)       │
                                                        │  6. Size limits    (T4)       │
                                                        │  7. ExecutionPolicy KILL (T10)│
                                                        │  8. Audit sink   (append-only)│
                                                        └──────────────┬────────────────┘
                                                                       │ in-process
                                                                       ▼
  ┌────────────────────┐                                ┌───────────────────────────────┐
  │  On-device agent   │ ─────────────────────────────► │  Loopback server  :8788       │
  │  / adb forward     │        no auth (sandbox)       │  (EXISTING, UNCHANGED)        │
  └────────────────────┘                                └──────────────┬────────────────┘
                                                                       ▼
                                                        ┌───────────────────────────────┐
                                                        │  AutoTaskEngine / Repository  │
                                                        │  ActionRisk · ExecutionPolicy │
                                                        └───────────────────────────────┘
```

The remote listener is a **gate, not a second API**. It authenticates, authorizes, and bounds;
then it delegates to exactly the handlers loopback already uses. There is no path by which a
remote caller reaches engine behaviour that a loopback caller cannot, and no duplicated
business logic to drift.

## How the three pieces fit

| Piece | Issue | Answers | Artifact |
| --- | --- | --- | --- |
| MCP tool schema | #22 | *What may be asked for?* | `docs/MCP_TOOL_SCHEMA.md`, `server/mcp/McpToolSchema.kt` |
| Threat model | #24 | *What must be true to allow it?* | `docs/REMOTE_COS_THREAT_MODEL.md` |
| API-key alpha | #23 | *Who is asking, and may they?* | `server/remote/ApiKeyManager.kt`, `RemoteAccessConfig.kt` |

The tool schema bounds the surface; the threat model states the preconditions; the key manager
supplies identity and scope. None is sufficient alone — a perfectly authenticated caller with an
unbounded tool surface is still a remote-code-execution hazard, which is why #22 exists.

## Request lifecycle

Ordered so the cheapest, most likely rejections happen first and an unauthenticated caller
never causes allocation or engine work (R4.5, R8.7):

1. **TLS handshake.** Plaintext refused on non-loopback (R1.1).
2. **Rate limit** by source and key (R8.1–R8.3).
3. **Authenticate.** `Authorization: Bearer atk_<id>.<secret>` → `ApiKeyManager.validate`.
   Invalid / revoked / expired → `401`, audited.
4. **Resolve tool.** Unknown name → `404`. The set is closed (#22).
5. **Authorize scope.** Missing scope → `403`, audited (R5.3).
6. **Replay check** for mutating tools (R3.1–R3.4).
7. **Bound the request.** Body ≤ 256 KiB, depth ≤ 32, `actionsJson` ≤ 32 (R4.1–R4.3).
8. **Kill switch.** Mutating tool + `!ExecutionPolicy.isAgentWriteAllowed()` → `403`.
   **A valid, in-scope key does not override this** (R10.2).
9. **Validate payload.** Unknown trigger or action type → `400`, fail closed.
10. **Delegate** to the existing loopback handler.
11. **Post-process.** Force `isEnabled=false` on remote-authored profiles (R5.5); strip
    `provisioningHints` and internal addresses from responses.
12. **Audit** with key id, tool, target, decision inputs, outcome (R9.1–R9.7).

Steps 3, 5, 8 are independent gates. Defeating one does not defeat the others: a stolen key
still faces scope, and full scope still faces the kill switch.

## Kill switch

`ExecutionPolicy.agentWritesEnabled` (owned by #19/#21) is the master control.

- Exposed as a **one-tap, always-visible** control on the main screen (R10.1).
- When false, every mutating tool returns `403` regardless of key validity or scope.
- **Not reachable through the API** — no tool sets it (#22 non-goals). The restrained party
  cannot lift its own restraint (R10.3).
- Effective immediately, offline, and across restart/reboot/update (R10.4, R10.6, R10.7).
- Fails closed if its state cannot be read (R10.5).

Disabling **remote mode** is the larger hammer: it stops the listener and drops live
connections, while leaving loopback fully functional.

## Deployment options

Per threat model T6. **Recommendation: neither direct nor a bespoke relay.** Prefer a
user-established private overlay network (WireGuard / Tailscale) with the remote listener bound
to that interface. This obtains NAT traversal without introducing a relay operator (A5) or
publishing a scannable port (A2), and keeps this project out of running infrastructure.

If a relay is ever offered, it must be a blind forwarder with end-to-end encryption (R6.1); a
TLS-terminating relay is equivalent to handing it the device.

## What ships in this PR

**Docs (primary deliverable)**

- `docs/MCP_TOOL_SCHEMA.md` — eleven tools, scopes, risk classes, endpoint mapping, non-goals.
- `docs/REMOTE_COS_THREAT_MODEL.md` — T1–T10, ~60 numbered requirements, revocation checklist.
- `docs/REMOTE_COS_DESIGN.md` — this document.

**Code (minimal, inert scaffold)**

- `server/mcp/McpToolSchema.kt` — the tool table as data. No transport, no wiring.
- `server/remote/ApiKeyManager.kt` — pure-JDK HMAC key issue/validate/revoke. No Context, no I/O.
- `server/remote/RemoteAccessConfig.kt` — the opt-in flag, **default OFF**.
- `app/src/test/java/com/example/ApiKeyManagerTest.kt` — hermetic lifecycle tests.

**Deliberately absent:** any remote listener, any TLS setup, any binding to a non-loopback
interface, any change to `KtorLoopbackServer`. Nothing in this PR makes any device reachable.

## Implementation gates

No remote listener may merge until, at minimum:

- [ ] TLS with pinning (R1.1–R1.5)
- [ ] Keys persisted in Android Keystore / EncryptedSharedPreferences (R7.10)
- [ ] Replay protection (R3.1–R3.5)
- [ ] Rate limiting (R8.1–R8.7)
- [ ] Append-only audit sink, not clearable via the API (R9.4)
- [ ] Kill switch wired into the UI and enforced on every mutating path (R10.1–R10.10)
- [ ] Remote-authored profiles forced `isEnabled=false` (R5.5)
- [ ] Persistent user-visible indicator while remote is enabled (R2.6)
- [ ] Request-size and depth limits (R4.1–R4.6)
- [ ] Full threat-model review signed off by the repository owner

## Open questions

1. Should `logs_clear` be denied to remote credentials outright? (Leaning yes — it is purely
   anti-forensic from a remote caller's perspective.)
2. Should `execute` scope be issuable at all in v1, or should every remote-authored profile
   require local arming? (Leaning: no `execute` in v1.)
3. Is per-request user confirmation for high-risk actions feasible, or does the latency make it
   unusable in practice?
4. Should remote keys be bound to a device-attested client identity rather than a bearer secret?
