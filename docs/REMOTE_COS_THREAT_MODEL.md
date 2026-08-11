# Remote CoS — Threat Model & Revocation Checklist

> Kaneo #24 / GH #24. Parent: #2. Companion docs: `MCP_TOOL_SCHEMA.md` (#22),
> `REMOTE_COS_DESIGN.md` (#2).
> Status: **security design**. No remote listener ships in this PR.

Today AutoTask binds `127.0.0.1:8788` only (`KtorLoopbackServer`, host hard-coded to
`127.0.0.1`). Callers are already inside the device's trust boundary, so the API is
unauthenticated. Opting into remote access **deletes that assumption**: the transport becomes
hostile, the caller becomes unauthenticated-by-default, and every endpoint becomes
internet-reachable. This document enumerates what must be true before that switch may exist.

**The device is not a server.** It is a phone with SMS, telephony, camera, and location
permissions already granted. A compromise here is not data loss — it is billable actions taken
in the user's name from the user's number.

## Assets

| Asset | Why an attacker wants it |
| --- | --- |
| Device action surface | Send SMS, place calls, toggle DND, torch, camera — cost and physical impact. |
| Stored profiles | Persistence. A profile is deferred code that survives the session. |
| Execution logs | Message bodies, recipients, URLs, timing — a behavioural record of the user. |
| Capability document | Reconnaissance: exactly which permissions are granted and ready. |
| API key | The whole surface, until revoked. |
| Kill switch state | Disabling it re-enables everything else. |

## Adversaries

| # | Adversary | Position |
| --- | --- | --- |
| A1 | Network attacker | On-path between agent and device (public Wi-Fi, hostile LAN, malicious relay). |
| A2 | Internet scanner | Finds the port; no credential. Untargeted, constant. |
| A3 | Malicious/compromised agent | Holds a valid key; over-reaches its mandate. |
| A4 | Prompt-injected honest agent | Valid key, hostile *instructions* from attacker-controlled content the agent read. |
| A5 | Relay operator | Runs the rendezvous service if a relay is used. |
| A6 | Local malicious app | Same device, reaching loopback. Pre-existing; noted for scope. |
| A7 | Thief with the unlocked device | Physical access to the key material. |

**A4 is the defining threat.** The agent is authenticated, in-scope, and behaving exactly as
instructed — by an attacker. No amount of authentication addresses it. Only *bounded scope*,
*default-off execution*, and a *human-armed kill switch* do. This is why #22 caps the tool
surface and why remote-authored profiles land disabled.

---

## T1 — Transport security (TLS)

**Threat (A1):** plaintext HTTP exposes the bearer token on first use and permits response
tampering (e.g. forging `capabilities` to make the agent believe `SEND_SMS` is ready).

Requirements:

- **R1.1** Remote mode MUST NOT accept plaintext HTTP on a non-loopback interface. Loopback
  may remain plaintext.
- **R1.2** TLS 1.2 minimum; TLS 1.3 preferred. No downgrade path.
- **R1.3** Self-signed certificates are acceptable only with **pinning** — the pairing payload
  carries the certificate fingerprint and the client pins it. An unpinned self-signed
  certificate provides no protection against A1 and MUST NOT be presented as secure.
- **R1.4** No mixed mode: if remote is enabled, the remote listener is TLS-only. It must be
  impossible to reach the remote listener over cleartext by omitting a parameter.
- **R1.5** Keys never travel in URLs (query strings land in logs and history) — `Authorization`
  header only.

## T2 — Origin & interface binding

**Threat (A2):** binding `0.0.0.0` publishes the API to the LAN and, behind a router with
UPnP or a carrier without NAT, to the internet.

Requirements:

- **R2.1** Bind address is explicit and defaults to `127.0.0.1`. Remote mode is a deliberate
  change, never a side effect of another setting.
- **R2.2** The remote listener SHOULD use a **separate port** from loopback `8788`, so
  loopback and remote can be reasoned about and firewalled independently.
- **R2.3** Enabling remote MUST NOT alter the loopback listener's behaviour. The two are
  separable; disabling remote leaves loopback exactly as it was.
- **R2.4** `Host`/`Origin` headers MUST be validated against an expected value where present;
  reject mismatches to blunt DNS-rebinding.
- **R2.5** No CORS. Browsers are not a supported client class; do not emit
  `Access-Control-Allow-Origin`.
- **R2.6** The UI MUST show, persistently and unmissably, that the device is remotely
  reachable — a foreground-service notification while remote mode is on.

## T3 — Replay & request freshness

**Threat (A1/A3):** a captured `events_fire` is replayed N times. Each replay is individually
"valid".

Requirements:

- **R3.1** Mutating requests MUST carry a nonce and a timestamp, covered by the signature.
- **R3.2** Timestamp skew window ≤ 300s; outside → reject.
- **R3.3** Nonces cached for at least the skew window; a repeat → reject. Bounded cache with
  eviction (the cache itself must not become a memory-exhaustion vector).
- **R3.4** Non-idempotent tools (`events_fire`, `profiles_delete`, `logs_clear`) MUST be
  replay-protected. Read tools MAY be exempt.
- **R3.5** Reject and audit — never silently dedupe, or the client cannot distinguish a replay
  attack from its own retry.

## T4 — Request limits & resource exhaustion

**Threat (A2/A3):** oversized bodies, deep JSON, or floods on a battery-powered device. DoS
here is also *battery drain* and *thermal impact* — a phone-specific cost.

Requirements:

- **R4.1** Max request body: **256 KiB**. Exceeding → `413`, connection closed.
- **R4.2** Max JSON nesting depth 32; reject deeper (parser-bomb defence).
- **R4.3** `actionsJson` ≤ 32 entries; profile ids ≤ 128 chars; `logs_recent.limit` ≤ 500
  (matching `McpToolCatalog` constants).
- **R4.4** Read timeout ≤ 30s; bounded concurrent connections; refuse beyond the cap.
- **R4.5** Unauthenticated requests rejected **before** body parsing — never allocate for an
  unauthenticated caller.
- **R4.6** Bounded total profile count and total stored-profile bytes, so `profiles_upsert`
  cannot fill storage.

## T5 — Scope enforcement

**Threat (A3/A4):** a key minted for read-only telemetry is used to author a `SEND_SMS`
profile and fire it.

Requirements:

- **R5.1** Scopes are `read`, `write`, `execute` per #22, bound to the key at issuance.
- **R5.2** `execute` is **never** implied by `write`. Default for remote keys: `read` only.
- **R5.3** Scope checked before dispatch and before body parsing where possible; failure →
  `403` + audit.
- **R5.4** Scopes MUST NOT be escalatable through the API. There is no `execution_policy_set`
  tool and no endpoint that widens a key's scope. Widening is a local, human, in-app action.
- **R5.5** Remote-authored profiles are persisted `isEnabled=false` regardless of the requested
  value. Arming is local and human. This is the single most important control against A4.
- **R5.6** Even with `execute`, high-risk actions remain subject to
  `ExecutionPolicy.isHighRiskAllowed()`.

## T6 — Relay vs. direct exposure

Two ways to be reachable; both are worse than loopback.

**Direct (device listens on a routable address):**

| | |
| --- | --- |
| Pro | No third party. Fewest moving parts. Traffic never leaves the user's control. |
| Con | Requires port-forward/NAT traversal — user-hostile and error-prone. Device IP is public and scannable (A2). Mobile IPs churn. Certificate management on a rotating address is painful. The phone absorbs every unauthenticated connection attempt: battery and thermal cost. |

**Relay (device dials out to a rendezvous; agent connects to the relay):**

| | |
| --- | --- |
| Pro | No inbound port, no NAT traversal, works on cellular. Relay absorbs scanning traffic (mitigates A2 and the battery cost of R4.5). Stable, certificate-friendly address. |
| Con | Introduces **A5**. The relay sees traffic shape, timing, and volume. Terminating TLS at the relay makes it a full MITM. It is a censorship/availability chokepoint and a single high-value target for compromise. |

Requirements:

- **R6.1** If a relay is used it MUST be a **blind forwarder**: end-to-end encryption between
  agent and device, with the relay unable to read or alter payloads. A TLS-terminating relay is
  equivalent to handing it the device.
- **R6.2** Relay identity pinned; a substituted relay must not be transparently trusted.
- **R6.3** Relay outage MUST fail **closed** (no access), never fall back to direct exposure.
- **R6.4** Whichever mode is chosen, the user must be told in plain language who can reach the
  device and who can observe the connection.
- **R6.5** **Recommendation:** neither by default. Prefer a user-established private network
  (VPN/WireGuard/Tailscale) with AutoTask bound to that interface — this gets the relay's NAT
  benefit without introducing A5, and keeps AutoTask out of the business of running
  infrastructure.

## T7 — API-key lifecycle, rotation & revocation

**Threat (A3/A7):** a leaked key stays valid forever and its use is indistinguishable from
legitimate traffic.

Requirements:

- **R7.1** Keys are per-device and per-client. One key per agent — never a shared key, or
  revocation becomes all-or-nothing and the audit log cannot attribute actions.
- **R7.2** ≥ 256 bits of CSPRNG entropy. Never derived from device identifiers (IMEI, ANDROID_ID,
  serial) — those are guessable, enumerable, and not secret.
- **R7.3** Shown **once** at creation. Only a verifier (HMAC/hash) is stored; the plaintext key
  is never persisted or logged.
- **R7.4** Every key carries: key id, label, scopes, `createdAt`, `expiresAt`, `lastUsedAt`,
  `revoked`.
- **R7.5** Default expiry ≤ 90 days. Expiry is enforced at validation, not merely displayed.
- **R7.6** **Revocation is immediate and local** — effective on the next request, requiring no
  network round-trip and no relay cooperation.
- **R7.7** Revoked key ids are retained (never reused) so historical audit entries stay
  attributable.
- **R7.8** Rotation overlap: issue new → migrate → revoke old. Rotation must never require a
  window in which the API is unauthenticated.
- **R7.9** Constant-time comparison for key verification (timing oracles).
- **R7.10** Key material at rest in Android Keystore / EncryptedSharedPreferences.
  *(The #23 scaffold uses an in-memory HMAC secret and is explicitly marked TODO for this.)*
- **R7.11** Disabling remote mode MUST invalidate active sessions immediately, not merely stop
  accepting new connections.

### Revocation checklist

Operational runbook for a suspected key compromise:

- [ ] **Kill switch first.** Toggle `ExecutionPolicy.agentWritesEnabled = false` — stops damage
      in one action without diagnosing which key leaked.
- [ ] Disable remote mode entirely if compromise is unconfirmed but suspected.
- [ ] Identify the key id from the audit log (`lastUsedAt`, source address, tool mix).
- [ ] Revoke that key id; confirm the next request from it returns `401`.
- [ ] Verify active sessions/connections using that key were terminated, not just future ones.
- [ ] Audit every action taken by that key id since issuance.
- [ ] **Review stored profiles for persistence** — the attacker's goal is a profile that
      survives revocation. Diff against known-good; check for `isEnabled=true` transitions.
- [ ] Check for anti-forensic gaps: was `logs_clear` called? Does the append-only remote audit
      log show a gap the local log does not?
- [ ] Verify the kill switch itself was not toggled via the API (it must not be reachable).
- [ ] Issue a replacement key with the narrowest sufficient scope.
- [ ] Re-enable remote mode only after profiles are verified clean.
- [ ] Record the incident, including which control failed.

## T8 — Rate limiting

**Threat (A2/A3):** credential brute force; action flooding; battery drain.

Requirements:

- **R8.1** Per-key **and** per-source-address limits (a stolen key from many addresses; many
  key guesses from one address).
- **R8.2** Unauthenticated requests limited most aggressively — cheapest to reject, most likely
  hostile.
- **R8.3** Mutating tools limited far more tightly than reads. Suggested starting point: 60
  reads/min, 10 writes/min, **2 `events_fire`/min**.
- **R8.4** Exponential backoff on repeated auth failure; temporary source blocks.
- **R8.5** Sustained auth failure MUST raise a **user-visible** alert — brute force against a
  personal device is never routine.
- **R8.6** `429` with `Retry-After`; limiter state bounded so it cannot itself be exhausted.
- **R8.7** Limits enforced before engine work, so a flood cannot cost battery.

## T9 — Audit logging with client identity

**Threat (A3/A4):** actions cannot be attributed after the fact; existing `ExecutionLog` records
*what the engine did*, not *who asked*.

Requirements:

- **R9.1** Every remote request logged with: timestamp, key id, key label, source address,
  tool name, target id, scope decision, kill-switch state at decision time, outcome, duration.
- **R9.2** **Never log the key itself** — key id only. Redact secrets from bodies.
- **R9.3** Audit entries link to the resulting `ExecutionLog` rows, so a device action traces
  back to the requesting identity.
- **R9.4** **The remote audit log is append-only and NOT clearable through the API.**
  `logs_clear` MUST NOT reach it (see #22, T-anti-forensic). Without this, A3's first move is to
  erase the record.
- **R9.5** Denials are logged as loudly as successes — denials are the attack signal.
- **R9.6** Bounded retention with rotation; user-inspectable in-app, not only over the API.
- **R9.7** Log the *decision inputs* (scopes held, policy flags), so a later reviewer can tell
  whether a control worked or was simply never consulted.

## T10 — Remote-control kill switch (**mandatory**)

**Threat (A3/A4/A7):** the user sees something wrong and has no single, fast, reliable stop.

This is the last-resort control and the one that must never fail.

Requirements:

- **R10.1** A **user-visible, one-tap** remote-control kill switch on the main screen —
  not buried in settings, not a hidden flag.
- **R10.2** It ties to `ExecutionPolicy.agentWritesEnabled`. When false, **all** mutating tools
  (`profiles_upsert`, `profiles_patch`, `profiles_delete`, `logs_clear`, live `events_fire`) are
  refused with `403`, regardless of key validity or scope. **A valid, in-scope key does not
  override the kill switch.**
- **R10.3** It MUST NOT be settable through the API. There is no tool that reaches it — the
  restrained party cannot lift its own restraint (see #22 non-goals).
- **R10.4** Effective immediately on in-flight and subsequent requests; no restart, no network.
- **R10.5** **Fails closed**: on any ambiguity — unreadable state, corrupt preferences, storage
  error — behave as if writes are disabled.
- **R10.6** State survives restart, reboot, and app update. An attacker must not be able to
  clear it by forcing a crash.
- **R10.7** Reachable with no network connectivity (it is local state, not a server call).
- **R10.8** Current state always visible, including in the foreground-service notification, so
  the user never has to guess whether the switch is on.
- **R10.9** Toggling is audit-logged with the actor (local user).
- **R10.10** Separately, disabling **remote mode** stops the listener and drops connections —
  a bigger hammer than the write kill switch, and also one tap.

---

## Requirements traceability

| Area | Requirements | Enforced by |
| --- | --- | --- |
| TLS | R1.1–R1.5 | Remote listener config (#23, future) |
| Origin/binding | R2.1–R2.6 | `RemoteAccessConfig` (#23) |
| Replay | R3.1–R3.5 | Remote auth filter (future) |
| Request limits | R4.1–R4.6 | Adapter + `McpToolCatalog` constants (#22) |
| Scope | R5.1–R5.6 | `McpScope` / `ApiKeyManager` (#22/#23) |
| Relay | R6.1–R6.5 | Deployment choice (#2) |
| Key lifecycle | R7.1–R7.11 | `ApiKeyManager` (#23) |
| Rate limits | R8.1–R8.7 | Remote listener (future) |
| Audit | R9.1–R9.7 | Remote audit sink (future) |
| Kill switch | R10.1–R10.10 | `ExecutionPolicy` (#19/#21) + UI |

## Status of controls in this PR

Design only. The #23 scaffold implements **R7.1, R7.2, R7.3, R7.4, R7.6, R7.7, R7.9** in
`ApiKeyManager` and **R2.1/R2.3** in `RemoteAccessConfig` (default OFF, separable from
loopback). Everything else is unimplemented and blocks any live remote listener.

**No remote listener may ship until every requirement above is either implemented or
explicitly waived in writing by the repository owner.**
