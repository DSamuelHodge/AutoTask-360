# Capabilities & permissions

## Manifest surface

`app/src/main/AndroidManifest.xml` declares **43 permissions** and **15
`uses-feature`** entries (only `android.hardware.touchscreen` is `required`).

### Declared permissions (grouped)

| Group | Permissions |
|---|---|
| Core | INTERNET, ACCESS_NETWORK_STATE, FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE, RECEIVE_BOOT_COMPLETED, WAKE_LOCK |
| Scheduling | SCHEDULE_EXACT_ALARM, USE_EXACT_ALARM, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS |
| Notifications | POST_NOTIFICATIONS, ACCESS_NOTIFICATION_POLICY |
| Telephony | RECEIVE_SMS, READ_SMS, SEND_SMS, READ_PHONE_STATE, READ_PHONE_NUMBERS, CALL_PHONE, READ_CALL_LOG |
| People/calendar | READ_CONTACTS, READ_CALENDAR, WRITE_CALENDAR |
| Location/activity | ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, ACCESS_BACKGROUND_LOCATION, ACTIVITY_RECOGNITION |
| Connectivity | ACCESS_WIFI_STATE, CHANGE_WIFI_STATE, CHANGE_NETWORK_STATE, BLUETOOTH*, NFC |
| Media/storage | CAMERA, RECORD_AUDIO, MODIFY_AUDIO_SETTINGS, MANAGE_EXTERNAL_STORAGE, SYSTEM_ALERT_WINDOW |
| Special settings | WRITE_SETTINGS, PACKAGE_USAGE_STATS |
| OTA | REQUEST_INSTALL_PACKAGES |
| Other | VIBRATE, ACCESS_BACKGROUND_LOCATION, SYSTEM_ALERT_WINDOW… |

### Services (system-bound)

- `AutoTaskService` — foreground engine (specialUse).
- `AutoTaskNotificationListener` — `BIND_NOTIFICATION_LISTENER_SERVICE`.
- `CoSAccessibilityService` — `BIND_ACCESSIBILITY_SERVICE` (user-granted).
- `WhatsAppBridgeService`, `BrainService`, `HealthMonitor` — foreground.
- `BootReceiver`, `OtaInstallReceiver` — receivers.

## Special-access model (the capability registry)

`CapabilityProvider` reports, for each special access: `granted`,
`settingsAction` (a deep-link the user taps to grant), `grantMode`, `notes`.
Endpoints: `/v1/capabilities`. `HealthMonitor` posts a repair notification when
a grant drifts.

| Capability | Check | Grant deep-link |
|---|---|---|
| DND / notification policy | `NotificationManager.isNotificationPolicyAccessGranted` | `Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS` |
| Write settings | `Settings.System.canWrite` | `Settings.ACTION_MANAGE_WRITE_SETTINGS` |
| Usage stats | AppOps `GET_USAGE_STATS` | `Settings.ACTION_USAGE_ACCESS_SETTINGS` |
| Manage external storage | `Environment.isExternalStorageManager` | `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` |
| Exact alarm | `AlarmManager.canScheduleExactAlarms` | `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM` |
| Notification listener | `enabled_notification_listeners` secure setting | `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` |
| Accessibility | `enabled_accessibility_services` secure setting | `Settings.ACTION_ACCESSIBILITY_SETTINGS` |
| Draw over apps | `Settings.canDrawOverlays` | `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` |

> **Restricted-setting note (Android 13+):** apps installed via `adb`
> (unknown source) have sensitive toggles (accessibility, DND, overlay,
> notification listener) greyed out until the user allows "Restricted settings"
> for the app. This is the OS gate — not a device-admin restriction.

## Capability policy guard

`CapabilityPolicy.require(context, type, params)` runs at the top of
`ActionExecutor.runSingleAction`. Every action a profile/event triggers is
checked against live capability state; a missing capability returns
`SKIPPED` with the reason.

| Action | Required |
|---|---|
| `SEND_SMS` | `send_sms_granted` |
| `CALL` | `call_phone_granted` |
| `DND` | `dnd_ready` |
| `AUDIO` (ringerMode=silent) | `dnd_ready` |
| `BRIGHTNESS` / `SCREEN_TIMEOUT` / `ROTATION` | `device_settings_ready` (WRITE_SETTINGS) |
| `FLASHLIGHT` / `CAMERA` | `camera_granted` |
| `NOTIFICATION` | `post_notifications_granted` |
| `UI_DRIVE` | accessibility enabled |
| `SEND_INTENT` | none (resolve-and-actuate) |

## Engine actions (SchemaProvider)

`BRIGHTNESS` `SCREEN_TIMEOUT` `ROTATION` `POWER_SAVE` `WIFI_ACTION`
`BLUETOOTH_ACTION` `AIRPLANE_MODE_ACTION` `HOTSPOT` `NFC_ACTION` `NOTIFICATION`
`SPEAK` `TOAST` `VIBRATE` `SEND_SMS` `CALL` `OPEN_URL` `SEND_INTENT`
`LAUNCH_APP` `KILL_APP` `OPEN_SETTINGS` `FLASHLIGHT` `CLIPBOARD` `CAMERA`
`HTTP` `WRITE_FILE` `READ_FILE` `BROADCAST` `PROFILE` `WAIT` `LOG`

## Bridge profiles (API-created, not seeded)

These are recreated on a fresh DB via `POST /v1/profiles`:

| id | trigger | action |
|---|---|---|
| `cos-informed-notify` | `MANUAL` | `NOTIFICATION` (title=`{{sender}}`, text=`{{smsBody}}`, high) |
| `cos-aware-sms` | `SMS` | HTTP → `/v1/brain` `aware.sms` |
| `cos-aware-call` | `INCOMING_CALL` | HTTP → `/v1/brain` `aware.call` |
| `cos-sms-send` | `MANUAL` | `SEND_SMS` (`{{number}}`, `{{text}}`) |
| `cos-open-url` | `MANUAL` | `OPEN_URL` (`{{url}}`) |
