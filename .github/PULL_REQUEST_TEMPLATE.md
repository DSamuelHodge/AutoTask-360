# Pull Request

## Outcome

<!-- One sentence: what ships when this is merged. -->

## Context

<!-- Why this change is needed; link issues (e.g. closes #9). -->

## Scope

<!-- Files/components touched. -->

## Out-of-scope

<!-- Explicitly what is NOT included. -->

## Acceptance criteria

- [ ] 
- [ ]

## Verification plan

<!-- Exact steps. CI is the source of truth for build/test. -->
- `./gradlew testDebugUnitTest`
- `./gradlew assembleRelease` (requires signing secrets — see docs/RELEASE_SIGNING.md)

## Risk level

<!-- low | medium | high -->

## Safety / privacy / permission notes

<!-- REQUIRED. Address each that applies:
- Android permissions (runtime vs special)
- Exported components and their protection
- IPC surface (ContentProvider, bound services, loopback server) and auth
- Network exposure (endpoints, local-only vs remote, token/auth)
- Backup behavior (allowBackup, data in backups)
- Tokens / secrets (storage, never logged, invalidation)
- Sensitive data (logs, webhook URLs, headers, SMS templates, phone numbers)
-->

## Reviewer checklist

- [ ] No hardcoded secrets; CI secrets used instead
- [ ] Permissions minimized to what the feature needs
- [ ] Exported components are protected
- [ ] Status/log endpoints do not expose tokens
- [ ] Backup policy consistent (see docs/BACKUP_REDACTION_POLICY.md)
- [ ] Docs updated / cross-referenced where relevant
