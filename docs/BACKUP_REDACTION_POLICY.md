# Backup, Redaction & Token Policy

Scope of this document: how AutoTask-360 handles Android backups, sensitive
profile data, endpoint token exposure, and token lifecycle. This is **policy only
for the production build** — no app behavior changes are implied beyond the
single manifest flag documented below.

> Related sibling docs (do not duplicate): `RELEASE_HARDENING.md`,
> `RELEASE_READINESS.md`, `DISTRIBUTION_CHECKLIST.md`. This doc focuses on the
> backup/redaction/token slice; cross-reference those for the broader release
> posture.

## 1. Production backup default (recommendation + change)

**Finding:** the app manifest previously declared
`android:allowBackup="true"` with `fullBackupContent` and `dataExtractionRules`
pointing at `app/src/main/res/xml/backup_rules.xml` and
`app/src/main/res/xml/data_extraction_rules.xml`.

**Decision:** flip to `android:allowBackup="false"` on the `<application>` element.

Why:
- AutoTask stores automation profiles that can contain **sensitive fields**
  (see §2). Auto backup / cloud backup / device-to-device transfer would move
  that data off the device in a form the app does not control.
- `allowBackup=false` is the production-safe default and the lowest-risk way to
  guarantee no profile data, logs, or tokens leave the device via the OS backup
  path.
- `backup_rules.xml` / `data_extraction_rules.xml` are left in place (harmless
  when `allowBackup=false`) so a future encrypted/limited backup can be opted
  back in deliberately, with exclusions, after a redaction pass.

If a future build re-enables backups, it MUST first implement §2 redaction and
use `<exclude>` rules for `sharedpref`/DB entries holding secrets or PII.

## 2. Profile fields that are secrets / sensitive

`AutomationProfile` (`app/src/main/java/com/example/data/AutomationProfile.kt`)
and its `triggerConfigJson` / `conditionsJson` / `actionsJson` may carry:

- **Webhook URLs** — can embed tokens / query secrets.
- **Custom HTTP headers** — may include `Authorization` / API keys.
- **SMS message templates** — personal content, recipient phone numbers.
- **Phone numbers** — PII.
- **Notification/calendar content** — PII in trigger configs.

**Policy:**
- These fields MUST NOT be stored in plaintext in any OS backup.
- They MUST NOT be written to logcat, crash reports, or the loopback
  `/v1/logs` endpoint in recoverable form.
- Any export flow (e.g. debug share) must be encrypted and user-initiated.

## 3. Endpoint token exposure (status / log endpoints)

The loopback tool server (`app/src/main/java/com/example/server/KtorLoopbackServer.kt`)
and the capability surface (`app/src/main/java/com/example/engine/CapabilityProvider.kt`)
expose read endpoints. Policy:

- `/v1/status`, `/v1/schema`, `/v1/capabilities`, `/v1/profiles`, `/v1/logs`
  are **safe-read** endpoints (see `CapabilityProvider.agentPolicyJson()`).
- These endpoints MUST NOT return authentication tokens, signing keys, or
  secret profile fields.
- Token verification (if any) is performed server-side; tokens are never echoed
  back in responses or logs.
- Enforcement of "no token in logs" is the responsibility of both
  `KtorLoopbackServer` response serialization and the log writer in
  `ActionExecutor`/`ExecutionLog`.

## 4. Token lifecycle / invalidation

- Tokens (App Check debug tokens, webhook secrets, remote-COS credentials if
  added) are provided via CI secrets or the local `.env` (see
  `RELEASE_SIGNING.md` and `.env.example`); they are never committed.
- On token compromise: rotate the secret at the source, purge it from any export,
  and bump `versionCode` on the next release.
- Debug-only tokens (e.g. `FIREBASE_APP_CHECK_DEBUG_TOKEN`) are explicitly
  ignored by the Secrets Gradle plugin (`app/build.gradle.kts`) and must never
  ship in a production build.

## 5. Verification

- `grep -n "allowBackup" app/src/main/AndroidManifest.xml` → expect `false`.
- Manual: `adb shell bu help` / attempt a backup; confirm profile DB is not
  included when `allowBackup=false`.
- Review `CapabilityProvider.getCapabilitiesJson` output to confirm no token
  fields are serialized.
