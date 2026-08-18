---
name: intent
description: Resolve a natural-language intent to an AutoTask profile id and params in one step. Use when finding which profile sends SMS, opens a URL, or handles a device action.
---

# Intent → profile

AutoTask now filters on the server. Prefer that over any local catalog.

```bash
curl -sS 'http://127.0.0.1:8788/v1/profiles?q=sms'
```

Returns a **ranked subset**, not all 56. Pick the most specific `cos-*` id.

| Intent | Profile | Payload |
| --- | --- | --- |
| Send SMS / text | `cos-sms-send` | `{number, text}` |
| Open URL / Maps | `cos-open-url` / `cos-deep-link` | `{url}` |
| Notify + speak + SMS | `cos-informed-notify` | `{sender, smsBody}` |
| Clipboard | `cos-clipboard-manager` / `cos-get-clipboard` | `{text}` |
| Launch app | `cos-quick-launch-app` | `{packageName}` |
| Flight prep | `cos-travel-flight` | — |

Then hand the `id` + payload to the `autotask` skill and **fire**. Do not dry-run.
