# DND / AUDIO On-Device Smoke (Android 13+)

Manual verification for Kaneo #11, #31, #32. The permission is already declared
in the manifest; the Android-13 failure is a runtime grant-path issue.

## Prereqs

1. Install release (or debug) build on a physical Android 13+ device.
2. Grant Notification Policy Access:
   - Settings → Notifications → Do Not Disturb access → enable **AutoTask**, OR
   - `adb shell cmd notification allow_dnd <pkg> 0` (pkg =
     `com.aistudio.autotask.svcqx`).

## Cases

1. **DND granted**: run a profile with `{"type":"DND","params":{"enabled":true}}`.
   Expect interruption filter changes and step status `OK`.
2. **DND denied**: revoke DND access, re-run. Expect step status `SKIPPED` with
   reason naming the Settings / `allow_dnd` fix (not `FAILED`).
3. **AUDIO silent granted/denied**: `{"type":"AUDIO","params":{"ringerMode":"silent"}}`
   — when access is missing expect `SKIPPED`; when granted expect `OK`.
4. **AUDIO normal**: `{"type":"AUDIO","params":{"ringerMode":"normal"}}` must
   succeed even without DND access.

## Regression test

`DndAudioRegressionTest` (Robolectric) asserts the denied path returns `SKIPPED`
(not `FAILED`) and that `AUDIO normal` does not require policy access, by
injecting `CapabilityProvider.notificationPolicyAccessOverride`.
