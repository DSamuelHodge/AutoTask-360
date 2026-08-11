# Release Signing

How signed release builds of AutoTask-360 are produced. The goal: **no secrets
in the repo, CI-injected signing, reproducible local fallback.**

> Companion docs: `RELEASE_HARDENING.md`, `RELEASE_READINESS.md`,
> `DISTRIBUTION_CHECKLIST.md`. This file covers only the signing/keystore slice.

## 1. How signing is wired

`app/build.gradle.kts` defines a `release` signing config that reads from
environment / project properties — never hardcoded:

| Property / Env var | Purpose |
| --- | --- |
| `KEYSTORE_PATH` | Absolute path to the upload keystore (`.jks`). |
| `STORE_PASSWORD` | Keystore password. |
| `KEY_ALIAS` | Key alias (default `upload`). |
| `KEY_PASSWORD` | Key password. |

Resolution order: `System.getenv(...)` → Gradle `findProperty(...)` → local
fallback (`${rootDir}/my-upload-key.jks`, alias `upload`).

`assembleRelease` (a default Android Gradle Plugin task) consumes this config
via `buildTypes.release.signingConfig`.

## 2. CI secret setup

In CI (GitHub Actions / your runner):

1. Store the upload keystore as an **encrypted secret** (not a raw file in repo).
2. Export the four values above as masked environment variables before the build
   step.
3. Run: `./gradlew assembleRelease`.

Never:
- Commit the `.jks` file (it is covered by `.gitignore` — verify).
- `echo`/log `STORE_PASSWORD` / `KEY_PASSWORD` / `KEY_ALIAS`.
- Print the keystore bytes in any CI step.

## 3. Local fallback (no CI)

Requirements:
- **JDK 17** (toolchain compatible with AGP).
- **Android SDK** with `compileSdk` / build-tools matching `app/build.gradle.kts`.
- A locally generated upload keystore:

  ```bash
  keytool -genkeypair -v -keystore my-upload-key.jks \
    -keyalg RSA -keysize 2048 -validity 10000 -alias upload
  export KEYSTORE_PATH="$PWD/my-upload-key.jks"
  export STORE_PASSWORD=...
  export KEY_PASSWORD=...
  export KEY_ALIAS=upload
  ./gradlew assembleRelease
  ```

`my-upload-key.jks` lives at the repo root and must stay git-ignored.

## 4. Version ownership

- `versionCode` must increase monotonically for every Play/store release.
- `versionName` is human-facing; bump alongside `versionCode`.
- Both live in `app/build.gradle.kts` `defaultConfig`; the release process owns
  them. Do not auto-decrement.

## 5. Rotation / compromise

- If the upload key is compromised, use Play App Signing key upgrade or your
  store's key-reset flow.
- Rotate the CI secret and purge any logged copies.
- Bump `versionCode`.
