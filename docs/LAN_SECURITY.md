# External control and LAN security

AutoTask360 treats the HTTP/MCP surface as an external controller. On-device
UI, broadcasts, and the scheduler call `AutomationCommandFacade` directly and
do not use these tokens.

## Bind modes

| Mode | Bind address | When |
| --- | --- | --- |
| Default | `127.0.0.1` | Always, including release |
| LAN | `0.0.0.0` | Only after a paired credential exists and LAN mode is enabled |

Development from a Mac should use `adb forward tcp:8788 tcp:8788` against
loopback. That is the supported remote-development path.

## Credentials

Two credential families exist and are not interchangeable on the LAN:

- **Internal brain token** (`cos-…`) authenticates the UNIX-socket brain IPC
  and loopback MCP. It is rejected on non-loopback HTTP.
- **Paired client token** (`atc-…`) is issued once during pairing. Only the
  SHA-256 hash is stored. Scopes are `READ`, `PROFILE_WRITE`, `EXECUTE`,
  `UI_CONTROL`, and `OTA`.

## Pairing

1. From loopback, `POST /v1/pairing/start` returns a 6-digit code (5 minute TTL).
2. `POST /v1/pairing/complete` with `{code, name, scopes, approvedActions}`
   returns the raw token once.
3. `POST /v1/pairing/lan` with `{enabled:true}` is loopback-only and fails if
   no active credential exists.
4. Revoke with `POST /v1/pairing/revoke`.

## High-risk remote execution

Paired clients that execute `SEND_SMS`, `CALL`, `UI_DRIVE`, `HTTP`,
`WRITE_FILE`, `CAMERA`, or other `confirm_required` / elevated-risk actions
must include those action types in `approvedActions`. Missing approvals return
`403 APPROVAL_REQUIRED` and do not run.

On-device automations and the internal brain are not gated by this pairing
approval list.

## Request controls

- Maximum body size: 256 KiB
- Rate limit: 60 requests / minute / principal
- `Idempotency-Key` on POST/PATCH/DELETE replays the first response
- Browser `Origin` headers must be loopback, or LAN when LAN mode is on
- Audit events store principal id, path, and outcome. Tokens, SMS bodies, and
  screen dumps are redacted.

## Release posture

- Cleartext is denied except `localhost` / `127.0.0.1`
- Internal services, the ContentProvider, and the WhatsApp bridge are not
  exported. `MainActivity`, the notification listener, and `BootReceiver`
  remain exported because the platform requires it.
