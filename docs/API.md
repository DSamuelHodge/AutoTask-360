# AutoTask360 Local API (`/v1`)

The Tool Server exposes a Ktor loopback server on `127.0.0.1:8788`. All request
and response fields use **camelCase** (`triggerType`, `profileId`, `dryRun`).

## GET /v1/status

Returns engine health and a `ready` summary:

```json
{
  "engine_running": 1,
  "ready": {
    "api": true,
    "permissions": true,
    "dnd": true,
    "device_settings": true,
    "notification_listener": true
  }
}
```

## POST /v1/events

Fire a manual or test event.

### Single-profile routing (Kaneo #28 / #10)

When `triggerType=MANUAL` and `profileId` is provided, the event targets **only**
that profile and does **not** broadcast to all enabled MANUAL profiles. Omit
`profileId` to broadcast to all enabled profiles for the trigger type.

```json
{
  "triggerType": "MANUAL",
  "profileId": "cos-quiet-night"
}
```

### Dry-run (Kaneo #29 / #10)

Set `dryRun: true` to validate routing/matching and return the would-be step
results **without** executing any side effect (no CALL, SMS, DND, HTTP,
notification, or settings write).

```json
{
  "triggerType": "MANUAL",
  "profileId": "cos-quiet-night",
  "dryRun": true
}
```

Response includes `"dryRun": true` and the list of `plannedProfiles`.

### Snake-case aliases

For backward compatibility the server also accepts `trigger_type`, `profile_id`,
and `dry_run`; these are normalized to camelCase.

## POST /v1/profiles

Create or upsert a profile. Required: `id`, `name`, `triggerType`. Profiles
support action types including `NOTIFICATION`, `AUDIO`, `DND`, `BRIGHTNESS`,
`HTTP`, `SEND_SMS`, `CALL`, `TOAST`, `VIBRATE`, and more.
