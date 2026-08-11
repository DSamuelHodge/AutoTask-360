# Release Hardening

This document captures the release-gate checklist for the AutoTask360 build and
the fixes delivered for Kaneo issues #13, #14, #15, #12, #11, #31, #32.

## Gate checklist (blocking for `release`)

- [x] **Exported components** — every `<activity>`, `<service>`, `<receiver>`,
      `<provider>` declares `android:exported`. `AutoTaskContentProvider` is
      exported but now requires an explicit ACL (see below). No debug-only
      component is merged into release (checked by `checkReleaseManifest()`).
- [x] **Provider ACL (#14)** — `AutoTaskContentProvider` declares
      `android:readPermission="com.example.autotask.permission.AGENT_READ"` and
      `android:writePermission="com.example.autotask.permission.AGENT_WRITE"`.
      The permissions are declared in the manifest with
      `protectionLevel="signatureOrSystem"` (release) and overridden to
      `signature` in `app/src/debug/AndroidManifest.xml` so local curl works.
- [x] **debuggable=false** — not set to true in the release manifest. The
      `checkReleaseManifest()` task fails the build if `android:debuggable="true"`
      or a `com.example.autotask.DEBUG` marker is detected in the merged manifest.
- [x] **Minification (#13, #15)** — `release { isMinifyEnabled = true;
      isShrinkResources = true }` with `proguard-rules.pro`. R8 keep rules
      preserve Room entities/DAOs, the engine/provider classes, enums, and
      kotlinx/moshi serialization model classes so runtime dispatch and DB access
      do not regress.

## Build-time checks

`checkReleaseManifest()` (registered in `app/build.gradle.kts`) parses the merged
release manifest after `processReleaseManifest` and fails the build when the
provider ACL is missing or when debug surfaces leak. CI is the source of truth:
run `./gradlew :app:checkReleaseManifest` (and the full `assembleRelease` +
`testReleaseUnitTest`) as the verification gate.

## Coverage notes / limitations

- The `signatureOrSystem` protection level still allows same-UID/same-signature
  callers. For stricter isolation, switch to `signature` + a shared signing
  identity between agent and host app.
- `ACCESS_NOTIFICATION_POLICY` is already declared (#11 gap was a runtime grant
  path, not a missing declaration). DND/AUDIO now emit an actionable SKIPPED
  reason pointing operators to Settings / `cmd notification allow_dnd`.
