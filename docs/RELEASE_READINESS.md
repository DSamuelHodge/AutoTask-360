# Release Readiness

> Kaneo #1 (parent) · GH #11
> Unified production-readiness source for AutoTask360.
> Consolidates the CI gate (#25), smoke plan (#26), and distribution checklist (#27).
> Manifest-specific gates live in `RELEASE_HARDENING.md`; this doc points to it.

## Scope

| Task | Deliverable | File |
|------|-------------|------|
| #25 CI gate | Test/lint/build pipeline + release APK artifact | `.github/workflows/ci.yml` |
| #26 Smoke plan | Emulator + physical-device verification matrix | `docs/SMOKE_TEST_PLAN.md` |
| #27 Distribution | Tier checklist + policy/manifest gates | `docs/DISTRIBUTION_CHECKLIST.md` |
| #1 Readiness (this) | Single source tying the above together | `docs/RELEASE_READINESS.md` |

## Verification source of truth

**CI (`.github/workflows/ci.yml`) is the source of truth for build/test verification.**
A distribution candidate is "built & verified" only when the CI workflow is green for
the target commit:

1. `./gradlew testDebugUnitTest` — unit tests (debug)
2. `./gradlew testReleaseUnitTest` — unit tests (release)
3. `./gradlew lintDebug` — Android lint
4. `./gradlew :app:assembleRelease` — release APK assembled & uploaded as artifact

The `emulator-smoke` job is present but disabled (`if: false`) for speed; it maps to
`docs/SMOKE_TEST_PLAN.md` automated checks and can be enabled for full smoke coverage.

## Release gate sequence

```
code pushed ─▶ CI green (tests+lint+assemble) ─▶ tier classification
   (RELEASE_READINESS)      (ci.yml)              (DISTRIBUTION_CHECKLIST)
                                              │
                                              ▼
                                   manifest gates G1–G5
                              (RELEASE_HARDENING.md)
                                              │
                                              ▼
                              external distribution allowed
```

## Tier → gate matrix

| Tier | CI green | Manifest gates (G1–G5) | Data-safety decl | Store review |
|------|----------|------------------------|------------------|-------------|
| Internal APK pilot | Required | Not required | No | No |
| Hardened beta APK | Required | Required | Required | No |
| Store-style | Required | Required | Required | Required |

## Open items tracked outside this PR

- `isMinifyEnabled` is `false` (app/build.gradle.kts:45) — must be `true` before
  store-style distribution (gate G2).
- `AutoTaskContentProvider` is `exported="true"` (AndroidManifest.xml:105) — restrict
  per gate G3.
- `android:allowBackup="true"` (AndroidManifest.xml:50) — confirm backup rules (G4).
- Sibling docs `RELEASE_HARDENING.md` and `DND_SMOKE.md` (if present) own the detailed
  manifest inspection and Do-Not-Disturb smoke specifics; this PR does not duplicate them.

## Acceptance

- [ ] PR builds green on CI
- [ ] Release APK artifact produced
- [ ] Smoke plan reviewed by QA
- [ ] Distribution tier chosen and checklist completed
- [ ] Manifest gates G1–G5 signed off by release manager
