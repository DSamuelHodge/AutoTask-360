# Design system — CoS-on-phone (Android)

Status: Draft for Phase 3 implementation. Tokens must land before Phase 4 screens.

This is an **Android-native** system. Dynamic Island is iOS. We specify the actual platform surfaces: SplashScreen API, adaptive icons, WindowInsets / display cutouts, App Widgets / Glance, Ongoing Notifications, and — where the OS provides them — Android 16 Live Updates / status chips.

Brand intent: [`BRAND.md`](BRAND.md) — face name **Adjutant**, accent **amber-on-ink**. Screens: [`UI_UX.md`](UI_UX.md).

## 1. Principles

1. **Dark-first.** The 2.1.0 UI is a light lab (`HighDensityBackground #FDFBFF`, white cards, white `NavigationBar`). The product lives on a phone that is always on; dark ink is the default. Light is a supported inversion, not the identity.
2. **Ledger, not chat.** Density is high but not cramped. Numbers (run ids, clocks, ports) are tabular. Status is color + word, not color alone.
3. **Cutout-honest.** Content never sits under the camera hole or gesture bar. We do not draw a pill in the notch to mimic iPhone.
4. **One execute path.** Every destructive control in the UI calls `AutomationCommandFacade`. The system does not invent a second confirmation chrome that bypasses `HighRiskPolicy`.
5. **Name-agnostic chrome.** The mark and tokens do not embed the letters “AutoTask.” Wordmark is a string.

## 2. Today vs target

| Token | Today (`ui/theme/Color.kt`) | Target |
| --- | --- | --- |
| Primary | `#005FB0` HighDensity blue | `Brand.accent` — **amber-on-ink** (decided 2026-08-18). Steel-cyan rejected. Do not keep Material Studio blue. |
| Background | `#FDFBFF` | `Brand.ink` ≈ `#0B0E12` |
| Surface | `#F3F4F9` / white cards | `Brand.inkElevated` ≈ `#141922` |
| Success | `#34C759` (iOS green) | `Status.ok` |
| Error | `#FF3B30` (iOS red) | `Status.failed` |
| Type | `FontFamily.Default` 9–11 sp labels | See §4 |
| Theme | `MyApplicationTheme` ignores `darkTheme` | Dark scheme default; light scheme complete |
| Splash | none | SplashScreen API |
| Widgets | none | Glance |
| Live status | generic FGS text | Situation ongoing + optional Live Update |

`colors.xml` still has unused purple/teal Studio leftovers. Delete in the token PR.

## 3. Color tokens

Implement as Compose `Color` + `MaterialTheme` scheme, and as XML colors for widgets / splash / notifications.

### 3.1 Core

| Token | Dark | Light | Use |
| --- | --- | --- | --- |
| `ink` | `#0B0E12` | `#F4F1EA` | App/splash/widget background |
| `inkElevated` | `#141922` | `#FFFFFF` | Cards, bars |
| `inkHighest` | `#1C2330` | `#ECE7DC` | Sheets, menus |
| `line` | `#2A3344` | `#D7D0C3` | 1 dp hairlines |
| `accent` | `#C4A46A` | `#8A6A2F` | Primary actions, selected nav |
| `accentMuted` | `#C4A46A` @ 16% | `#8A6A2F` @ 12% | Selected containers |
| `onInk` | `#E8E4DA` | `#161410` | Primary text |
| `onInkMuted` | `#9AA3B2` | `#5C564A` | Secondary text |
| `onAccent` | `#161410` | `#F4F1EA` | Text on accent buttons |

**Accent is amber-on-ink** (decided). Steel-cyan is rejected. Do not swap the scale.

### 3.2 Status (map 1:1 to runtime)

| Token | Color (dark) | Runtime |
| --- | --- | --- |
| `statusQueued` | `#6B7380` | `QUEUED` |
| `statusRunning` | `accent` | `RUNNING` |
| `statusWaiting` | `#6EA8D6` | `WAITING` |
| `statusOk` | `#3DDC97` | `SUCCESS`, `OK`, `SKIPPED` (skipped uses muted) |
| `statusPartial` | `#E0B44F` | `PARTIAL` |
| `statusFailed` | `#E85D4C` | `FAILED` |
| `statusCancelled` | `#6B7380` | `CANCELLED` |
| `statusIndeterminate` | `#C77DFF` | `INDETERMINATE` — distinct, never green |

Do not use status color as the only indicator. Always pair with the status word (TalkBack + deuteranopia).

### 3.3 Principal chips

| Principal | Chip |
| --- | --- |
| You (`LOCAL_DEVICE`) | `onInk` on `inkHighest` |
| CoS (`INTERNAL_BRAIN`) | `accent` outline |
| Paired (`PAIRED_CLIENT`) | `statusWaiting` outline |
| Denied / anonymous | `statusFailed` outline |

### 3.4 Contrast

- Body text on `ink`: ≥ 4.5:1.
- `onInkMuted` on `ink`: verify ≥ 4.5:1; if not, darken muted.
- Accent buttons: `onAccent` on `accent` ≥ 4.5:1. The amber-on-ink pair is tuned for that.
- Status dots are **never** the only cue.

## 4. Type

`Type.kt` today only overrides `bodyLarge`. Replace with a full scale.

| Role | Size / line / weight | Notes |
| --- | --- | --- |
| `display` | 32 / 36 / Medium | Home situation line |
| `title` | 20 / 24 / Medium | Screen titles |
| `titleSmall` | 16 / 20 / Medium | Cards |
| `body` | 14 / 20 / Regular | Default |
| `bodySmall` | 12 / 16 / Regular | Secondary |
| `label` | 12 / 16 / Medium | Nav, chips — **not** 9 sp. Matches `UI_UX.md` 12 sp nav. |
| `mono` | 12 / 16 / Regular | run ids, ports, JSON, `effectId` |

- UI face: platform `FontFamily.SansSerif` unless Phase 3 adds one licensed grotesque. Do not add a playful display face.
- `mono` uses `FontFamily.Monospace` with `fontFeatureSettings = "tnum"` where supported.
- Minimum body 14 sp. The current 9–11 sp lab labels fail accessibility.

## 5. Elevation, shape, space

| Token | Value |
| --- | --- |
| Radius card | 12 dp |
| Radius chip / button | 8 dp |
| Radius sheet | 20 dp top |
| Hairline | 1 dp `line` |
| Elevation | Prefer hairline + surface step over shadows. Max 2 dp ambient if needed. |
| Space scale | 4 / 8 / 12 / 16 / 24 / 32 / 48 |
| Screen gutter | 16 dp + cutout insets |
| Min touch | **48 × 48 dp** (today several 30–34 dp buttons fail this) |

## 6. Motion

| Token | Duration | Easing | Use |
| --- | --- | --- | --- |
| `motion.fast` | 90 ms | emphasized accelerate | Press, toggle |
| `motion.base` | 180 ms | standard | Tab / sheet |
| `motion.slow` | 280 ms | emphasized decelerate | Situation change |
| `motion.none` | 0 | — | `INDETERMINATE` appear (no cute bounce) |

Reduce motion: honor `AccessibilityManager` / Compose `LocalAccessibilityManager`. Hard-cut instead of animate.

Do not use hero shared-element from splash into a fake island.

## 7. Components

Implement in `com.example.ui.components` (new, still inside `app`).

| Component | Behavior |
| --- | --- |
| `BrandMark` | Vector, 16–32 dp |
| `SituationHeader` | Next action, brain state, last run |
| `StatusBadge` | Status token + word |
| `PrincipalChip` | You / CoS / paired name |
| `RunRow` | Profile, trigger, status, relative time → timeline |
| `StepRow` | Index, type, status, `effectId` (mono, copy), resume class |
| `ProfileCard` | Name, trigger, armed switch (48 dp), last fire |
| `PermissionRepairCard` | Capability + why + deep link (`CapabilityOnboarding.repairActionFor`) |
| `EmptyState` | Mark, one sentence, one CTA |
| `ErrorBanner` | `statusFailed`, retry if safe |
| `PairingCode` | 6-digit tabular, 5:00 TTL countdown |
| `DangerButton` | High-risk fire; still goes through facade (CoS path does not add a second prompt) |
| `DiagnosticsKv` | Mono key/value (ports, versions) |
| `JsonViewer` | Read-only, redacted |

Material3 is the substrate (`material3` already on the BOM). Do not add a second component library.

## 8. Edge-to-edge, cutouts, punch-hole

`MainActivity` already calls `enableEdgeToEdge()`. The lab UI only applies `navigationBarsPadding()` on the nav bar.

**Rules**

- Scaffold padding = `WindowInsets.safeDrawing` (status + cutout + IME + nav).
- Background (`ink`) draws edge-to-edge; **text and controls** stay inside safe drawing.
- Camera punch-holes (G63-class) are **display cutouts**. Use `WindowInsets.displayCutout`. Do not place the situation chip in the cutout rectangle.
- Split / fold: not a launch target. Single-pane phone is the product. If `LocalConfiguration.screenWidthDp >= 600`, allow a list/detail for Runs, not a tablet redesign.
- Gesture nav: bottom bar clears `navigationBars`.
- `fitsSystemWindows` XML is not used; this is Compose.

**“Dynamic Island / notch” mapping**

| iOS idea | Android equivalent we will ship |
| --- | --- |
| Dynamic Island live activity | Ongoing Notification + (if present) Android 16 Live Updates / status chip / now-bar |
| Notch-safe layout | `WindowInsets.displayCutout` + `safeDrawing` |
| Island morph from app | **Not implemented** |
| App icon in island | Notification small icon `ic_stat_brand` |

## 9. Live status surfaces (Android only)

### 9.1 Ongoing notification (all API 24+)

Required. This is the primary “live” surface.

- Channel: `situation_channel` (new) **or** reuse `autotask_service_channel` to **replace the engine heartbeat copy** (id 8788). Brain (8791), WhatsApp (8789), and HealthMonitor (8792) **remain separate FGS**. Do not promise one ongoing for the whole APK.
- Title: situation line (“Waiting 4m · quiet-night”)
- Text: last terminal run or “Brain halted”
- Actions: Open · Arm/Disarm pinned profile
- Style: `NotificationCompat.Builder` + `setOngoing(true)` + `setOnlyAlertOnce(true)`
- Updates from `WatchBus` (in-process). Do not poll Room from a second loop.

Pixel **Ongoing Activity** (if dependency is accepted later) can promote this notification on Pixel / Wear. Optional, not a gate.

### 9.2 Android 16 promoted-ongoing / Live Updates

**Conditional, not the ship surface.** Platform API 36 names (use these, not `FLAG_PROMOTED`):

- `Notification.FLAG_PROMOTED_ONGOING`
- `Notification.hasPromotableCharacteristics()`
- `NotificationManager.canPostPromotedNotifications()`
- permission `POST_PROMOTED_NOTIFICATIONS` (add to the Phase 5 checklist)
- `Notification.ProgressStyle` — aimed at **user-initiated** start-to-end journeys

A CoS “next fire in 4m / brain halted” payload may **fail** `hasPromotableCharacteristics` on G63/OEM. If so, 5.4 is a no-op. **Ongoing notification remains the ship surface** (KD-8). Do not block Phase 5 on promotion.

### 9.3 Widgets (Glance)

Add `androidx.glance:glance-appwidget` in the Phase 5 PR (not earlier). Three widgets — see [`UI_UX.md`](UI_UX.md) §8.

Widget theme uses the same tokens via Glance `ColorProvider`. No WebView widgets.

## 10. Iconography

- Prefer a small custom set for status (queued / running / waiting / ok / failed / indeterminate) so they match tokens.
- Material Icons Extended is already a dependency — acceptable for nav and repair.
- Notification / launcher / widget **mark** is the brand path, not a Material lightning bolt.
- No emoji in chrome.

## 11. Accessibility

| Rule | Bar |
| --- | --- |
| Touch targets | ≥ 48 dp |
| Contrast | AA for text and buttons |
| TalkBack | Every icon button has `contentDescription`; status badges announce word + profile; pairing code announced digit-by-digit |
| Focus order | Top bar → situation → list → nav |
| Text scale | Layouts tolerate 200% font scale (no 9 sp fixed labels) |
| Reduce motion | Honor system |
| Color | Status never color-only |
| Accessibility service | The *product’s* `CoSAccessibilityService` is a capability, not a UI requirement. Disclose it in onboarding. |

## 12. Splash and adaptive icon

See [`BRAND.md`](BRAND.md) §§4–6.

- Adaptive: background + foreground + **dedicated** monochrome.
- SplashScreen API; `core-splashscreen` dependency in Phase 3.
- No `windowBackground` bitmap splash on API 31+ (system already uses SplashScreen).

## 13. Implementation notes (for the token PR)

- Replace `HighDensity*` names with `Brand` / `Status` tokens. Keep a temporary typealias if needed to land the PR without rewriting every tab in the same diff — but Phase 4 must not still say `HighDensity`.
- `MyApplicationTheme` → `BrandTheme(dark: Boolean = true)`.
- `Theme.MyApplication` → `Theme.Brand` + `Theme.Brand.Splash`.
- Do not change `applicationId` or Kotlin packages.

## 14. Inventory to delete

- Unused `colors.xml` purple/teal
- 30 dp lab buttons
- White nav bar + `Color(0xFFFDFBFF)` hardcodes in `AutoTaskMainScreen`
- Stock FGS icons once `ic_stat_brand` exists
