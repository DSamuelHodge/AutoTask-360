# Build & deploy

## Toolchain

- **Android NDK**: `/opt/homebrew/share/android-commandlinetools/ndk/27.2.12479018`
- **Rust target**: `aarch64-linux-android` (`rustup target add aarch64-linux-android`)
- **cargo-ndk**: `cargo install cargo-ndk` (v4.x)
- **Java**: OpenJDK 17 (`/opt/homebrew/opt/openjdk@17`)

## Cross-compile the daemon

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_NDK_HOME=/opt/homebrew/share/android-commandlinetools/ndk/27.2.12479018

cd agent-cal-crm
cargo build            # host build (tests, clippy)
cargo clippy --all-targets
cargo test
cargo ndk -t arm64-v8a build --release --bin cos

# bundle into the app
cp target/aarch64-linux-android/release/cos \
   AutoTask-360/app/src/main/jniLibs/arm64-v8a/libcosd.so
```

`libcosd.so` is a PIE executable shipped as a native lib; `BrainService`
spawns it via `ProcessBuilder` (never dlopen). `packaging.jniLibs.useLegacyPackaging
= true` in `app/build.gradle.kts` makes PackageManager extract it into the
OS-owned, app-non-writable `nativeLibraryDir` (W^X-safe on Android 10+).

## Build the APK

```bash
cd AutoTask-360
./gradlew :app:assembleDebug
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
```

## Start the stack on-device

```bash
S=<serial>
adb -s $S shell am start-foreground-service -n com.aistudio.autotask.svcqx/com.example.service.AutoTaskService -a com.example.autotask.action.START
adb -s $S shell am start-foreground-service -n com.aistudio.autotask.svcqx/com.example.wa.BrainService -a com.example.autotask.action.BRAIN_START
adb -s $S shell am start-foreground-service -n com.aistudio.autotask.svcqx/com.example.wa.WhatsAppBridgeService -a com.example.autotask.action.WA_START
adb -s $S reverse tcp:8788 tcp:8788   # reach the loopback server from the host
```

`BootReceiver` restarts everything on `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED`.

## Grants used on the dev loop

`adb install -r` resets runtime grants; re-apply after install:

```bash
P=com.aistudio.autotask.svcqx
for perm in android.permission.ACCESS_FINE_LOCATION android.permission.ACCESS_COARSE_LOCATION \
  android.permission.READ_CONTACTS android.permission.READ_CALENDAR android.permission.WRITE_CALENDAR \
  android.permission.CALL_PHONE android.permission.READ_CALL_LOG android.permission.SEND_SMS; do
  adb -s $S shell pm grant $P $perm
done
adb -s $S shell settings put secure enabled_notification_listeners "$P/com.example.service.AutoTaskNotificationListener"
adb -s $S shell appops set $P SYSTEM_ALERT_WINDOW allow
adb -s $S shell appops set $P REQUEST_INSTALL_PACKAGES allow
```

Accessibility (CoS Screen Access) must be user-enabled in Settings (it is a
"Restricted setting" gate for adb-installed apps on Android 13+).

## OTA self-update (app)

1. Host `update.json` + `app-debug.apk` over HTTP (e.g. `python3 -m http.server 8890`
   in the APK dir), `adb reverse tcp:8890 tcp:8890`.
2. `update.json`: `{"versionCode": N, "versionName": "x.y", "url": "app-debug.apk", "sha256": "<hex>"}`.
3. Set URL: `POST /v1/ota/config {"updateUrl":"http://127.0.0.1:8890/update.json"}`.
4. Check: `POST /v1/ota/check` → `available: true`.
5. Install: `POST /v1/ota/install` → download, SHA-256 verify, signing-cert
   match, system confirm dialog → app replaces itself → `MY_PACKAGE_REPLACED`
   restarts services.

## Daemon on host (Mac) for local testing

```bash
DB=$(mktemp -d)/t.db
./target/debug/cos seed --db "$DB"
./target/debug/cos serve --db "$DB" --addr 127.0.0.1:18991 &
curl -s -X POST http://127.0.0.1:18991/ -d '{"method":"aware.deals","params":{"owner":"derrick"}}'
```

Note: the host daemon's engine calls default to `AUTOTASK_URL` =
`http://127.0.0.1:8788` (the phone's engine via adb reverse).

## Known environmental caveats

- **DuckDuckGo HTML search** intermittently rate-limits (`anomaly`/`challenge`
  page); `aware.search` reports `rate_limited` rather than failing.
- **Live GPS** may return no fresh fix on a stationary phone (2-day-old cache);
  `aware.travel` accepts `origin_lat`/`origin_lon` override.
- **Logseq HTTP API** (Mac app on `127.0.0.1:12315`) can go stale after a few
  calls; the mirror works but the API needs an app restart when unresponsive.
- **WhatsApp WebView bridge** is flaky for outbound sends (WebView DOM timing);
  the native-app path (`whatsapp://` + accessibility) is the more reliable
  upgrade.
