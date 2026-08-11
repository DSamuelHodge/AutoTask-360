# Emulator / Device Smoke Test Plan

> Kaneo #26 · GH #26
> Companion to `.github/workflows/ci.yml` (the CI runners) and `docs/DISTRIBUTION_CHECKLIST.md`.
> The CI `emulator-smoke` job can execute the **Automated emulator** checks below.

This plan verifies runtime behavior of the AutoTask engine on a clean device. Each
section lists an **Automated emulator** check (suitable for `connectedAndroidTest`
under the emulator runner) and a **Physical / manual** check that requires a real
device, human interaction, or carrier/SMS conditions CI cannot reproduce.

---

## 1. Engine start

- **Automated emulator:** App launches `MainActivity`; `AutoTaskService` (special-use
  foreground service) starts and posts a persistent foreground notification within 5s.
- **Physical / manual:** Verify the foreground notification survives Doze and that
  battery-optimization exemption (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) is offered.

## 2. Boot restore

- **Automated emulator:** After simulating `BOOT_COMPLETED` (and `MY_PACKAGE_REPLACED`),
  `BootReceiver` restarts `AutoTaskService` and re-schedules pending policies.
- **Physical / manual:** Reboot a real device and confirm persisted policies re-arm and
  the engine is running on next unlock.

## 3. Alarm scheduling

- **Automated emulator:** Grant `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`; confirm an
  exact alarm is scheduled and fires the intended action at the target time.
- **Physical / manual:** Confirm behavior when exact-alarm permission is revoked at
  runtime (graceful fallback to inexact alarms).

## 4. SMS-disabled behavior

- **Automated emulator:** With `RECEIVE_SMS` / `READ_SMS` / `SEND_SMS` revoked, the engine
  must not crash and must surface a degraded-mode state (SMS triggers disabled).
- **Physical / manual:** Send a real SMS to the device and confirm trigger handling only
  when permission granted.

## 5. Notification-listener-disabled behavior

- **Automated emulator:** With `AutoTaskNotificationListener` not granted the
  `BIND_NOTIFICATION_LISTENER_SERVICE` role, the engine must operate with notification
  triggers disabled and show a repair prompt.
- **Physical / manual:** Toggle the listener off/on in system settings and confirm
  live re-binding and resume of notification triggers.

## 6. Bridge auth

- **Automated emulator:** The on-device Ktor tool-server bridge rejects unauthenticated
  requests and accepts a valid token (covered by `KtorServerConfigTest`).
- **Physical / manual:** Validate token rotation and revocation on a paired host.

## 7. Permission repair paths

- **Automated emulator:** From a degraded state, invoking the in-app repair flow re-requests
  the missing special permissions and returns the engine to full mode.
- **Physical / manual:** Walk the full repair flow including `WRITE_SETTINGS`,
  `PACKAGE_USAGE_STATS`, `MANAGE_EXTERNAL_STORAGE`, `SYSTEM_ALERT_WINDOW`, and
  notification-listener re-grant.

---

## Notes

- Automated checks should live under `app/src/androidTest` and be invoked by the CI
  `emulator-smoke` job (`connectedDebugAndroidTest`).
- Emulator runner is gated `if: false` in CI by default for speed; flip it on for full
  smoke. Physical/manual checks are tracked in release sign-off, not CI.
