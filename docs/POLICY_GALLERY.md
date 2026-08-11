# Policy Gallery (Disabled-by-Default CoS Templates)

The Policy Gallery is a **separate, read-only catalog** of context-of-situations (CoS) policy
templates. It is intentionally parallel to the active policy system (`PolicySeeder` +
`automation_profiles` table) and never touches it.

## Model

`PolicyGalleryEntry` (`app/src/main/java/com/example/data/PolicyGallery.kt`):

| Field | Meaning |
|-------|---------|
| `id` | Stable gallery id, `gallery-*`. Distinct namespace from active profiles. |
| `name` | Human label. |
| `category` | Thesis category: `focus`, `safety`, `context`, `relay`. |
| `requiredCapabilities` | Capabilities (mapped to `CapabilityProvider` action risk levels) the entry needs. |
| `riskClass` | Reused risk labels: `low` / `medium` / `elevated` / `high`. |
| `cosHandoff` | How an agent/user would enable and what it does. |
| `description` | Plain-language summary. |
| `actionsJson` | Representative action array (placeholder fields marked honestly). |
| `triggerType` | Trigger family (e.g. `MEETING`, `TIME`, `LOCATION`). |
| `disabledByDefault` | **Always `true`.** No gallery entry executes until explicitly cloned/enabled. |

`PolicyGalleryStore` holds the catalog in memory and exposes:
- `getGallery()` — full catalog (surface point for API/provider, parent #4).
- `getEntry(id)`.
- `cloneEntry(entryId, existingIds)` — the **only** path to an active `AutomationProfile`.
  Returns `null` if the id already exists (never clobbers user/agent edits), and never mutates
  the source entry.

## The 8 Starter Entries (all `disabledByDefault = true`)

1. **Meeting Shield** (`gallery-meeting-shield`) — focus · `READ_CALENDAR`,`DND` · elevated
2. **Driving Context** (`gallery-driving-context`) — safety · `ACTIVITY_RECOGNITION`,`DND`,`NOTIFICATION` · medium
3. **Deep Work Fortress** (`gallery-deep-work-fortress`) — focus · `DND`,`BRIGHTNESS`,`SCHEDULE_EXACT_ALARM` · medium
4. **Relationship Context Card** (`gallery-relationship-context-card`) — context · `READ_CONTACTS`,`READ_CALENDAR` · low
5. **Morning Brief Relay** (`gallery-morning-brief-relay`) — relay · `READ_CALENDAR`,`READ_CONTACTS`,`SCHEDULE_EXACT_ALARM` · low
6. **Battery Judgment Mode** (`gallery-battery-judgment-mode`) — safety · `BATTERY` · low
7. **Errand Nudge** (`gallery-errand-nudge`) — context · `ACCESS_FINE_LOCATION`,`READ_CALENDAR` · low
8. **Ambient Briefing** (`gallery-ambient-briefing`) — relay · `NOTIFICATION`,`SCHEDULE_EXACT_ALARM` · low

## Disabled-by-Default Guarantee

- Gallery entries are **not** inserted as active profiles at seed time.
- `AutoTaskRepository.seedDefaultRecipesIfNeeded()` is unchanged and still only seeds `PolicySeeder` starters.
- An entry only becomes an active profile via explicit `cloneEntry` on user/agent request.
- `cloneEntry` refuses to overwrite an existing profile with the same id.
- The gallery source is immutable; cloning reads a copy.

## Tests

`app/src/test/java/com/example/PolicyGalleryTest.kt` verifies: catalog uniqueness, every entry
disabled, required capabilities & risk class present, clone-without-clobber, and no source mutation.
