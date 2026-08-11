# Distribution Checklist & Policy Gates

> Kaneo #27 · GH #27 · parent #1
> For production-readiness summary see `docs/RELEASE_READINESS.md`.
> For manifest-level gates referenced here see `RELEASE_HARDENING.md`.

This document classifies distribution tiers and the gates required before any APK
leaves internal hands. **CI is the source of truth for build/test verification**
(see `.github/workflows/ci.yml`); these gates are layered on top.

---

## Distribution tiers

| Tier | Audience | Signing | Hardening | Store listing |
|------|----------|---------|-----------|---------------|
| Internal APK pilot | Trusted dev/QA | Debug or self-signed | None required | No |
| Hardened beta APK | Limited external testers | Release-signed | Required (below) | No |
| Store-style distribution | Public | Release-signed + Play App Signing | Required + review | Yes |

---

## Classification checklist (complete before picking a tier)

### Internal APK pilot
- [ ] Built from `:app:assembleRelease` (or debug) via CI
- [ ] CI green: `testDebugUnitTest`, `testReleaseUnitTest`, `lintDebug`
- [ ] APK artifact uploaded and retrievable
- [ ] Recipients are trusted and acknowledge no data-safety guarantees

### Hardened beta APK (adds to pilot)
- [ ] All **Required manifest gates** below pass
- [ ] Release build has minification/obfuscation enabled (`isMinifyEnabled = true`)
- [ ] Debug-only components stripped (none exported in release)
- [ ] Provider ACL reviewed (see gate G3)
- [ ] Privacy / data-safety declaration drafted (see below)
- [ ] Special-permission rationale documented per permission
- [ ] Crash/telemetry opt-in disclosed

### Store-style distribution (adds to beta)
- [ ] Data Safety form completed (see below)
- [ ] Special permissions justified to policy reviewer (Google Play)
- [ ] `targetSdk` / `compileSdk` meet store floor at release time
- [ ] Store listing + screenshots + policy attestation

---

## Privacy / data-safety / special-permission declarations

Declare in the release record:

- **Data collected:** on-device only by default; note any upload path (Gemini API key
  usage, bridge traffic). List `MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API` from `metadata.json`.
- **Special permissions used (from `AndroidManifest.xml`):** `WRITE_SETTINGS`,
  `PACKAGE_USAGE_STATS`, `MANAGE_EXTERNAL_STORAGE`, `SYSTEM_ALERT_WINDOW`,
  `ACCESS_NOTIFICATION_POLICY`, `SCHEDULE_EXACT_ALARM`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`,
  SMS/Call/Calendar/Contacts/Location/Bluetooth/NFC/Camera/Mic groups.
- **Justification:** each special permission must map to a documented engine feature
  (e.g. `PACKAGE_USAGE_STATS` → capability provider; SMS group → SMS trigger policy).
- **Data Safety form fields:** data shared, data collected, encryption-in-transit,
  policy around deletion.

---

## Required manifest gates (release-manager sign-off) — `RELEASE_HARDENING.md`

These are enforced as policy gates before *any* external distribution:

- **G1 — Debug components stripped:** no `android:debuggable="true"`, no debug
  providers/activities exported in the release manifest.
- **G2 — Minification:** `isMinifyEnabled = true` for release (currently `false` in
  `app/build.gradle.kts:45` — must be flipped before store-style distribution).
- **G3 — Provider ACL:** `AutoTaskContentProvider` (`com.example.autotask.provider`)
  is currently `android:exported="true"` (`AndroidManifest.xml:105`); restrict to
  specific signature/permission or set `exported="false"` unless cross-app access is
  required.
- **G4 — Backup / data exposure:** `android:allowBackup="true"` (`AndroidManifest.xml:50`)
  with backup rules — confirm no secrets leak via `data_extraction_rules` /
  `backup_rules`.
- **G5 — Exported services:** `AutoTaskService` and `AutoTaskNotificationListener` are
  `exported="true"`; confirm intent-filters/permissions are intentional (listener is
  correctly permission-gated; service should be reviewed).

Release manager must confirm G1–G5 before promoting an APK beyond the internal pilot.
