# Hiatus: handoff for AI agents

Android "time away from your phone" widget (idea from Nothing's "Away Time", look is pure Material 3) + an away-time analytics app.
**Scope is deliberately tiny** (owner, 0.6.0): only away time. No screen time, app usage, presets or other widget styles; don't add them back. Kotlin, package `dev.savar.hiatus`,
minSdk 29 · targetSdk 36 · compileSdk 37. Owner: Savar Verma (GitHub `savar95x`), daily driver **OnePlus 13s,
OxygenOS 16**. Sibling projects with the same conventions: `savar95x/minutia`, `savar95x/aria-player`.

## Golden rules
1. **Never change the signing key (`app/release.jks`) or `applicationId`.** Installed copies couldn't update.
2. **Every shipped change = version bump (`version.properties`) + `CHANGELOG.md` entry in the same push.**
   CI publishes `Hiatus X.Y.Z` with `Hiatus-X.Y.Z.apk` when the tag doesn't exist yet; otherwise it only build-checks.
3. **Commit as** `Savar Verma <102670483+savar95x@users.noreply.github.com>`.
4. **Material 3 (Expressive), minimal copy.** Stock components; small focused changes.
5. **Don't claim it works on a phone.** You can't run it. Ask for screenshots.

## Working loop (no Android SDK locally)
GitHub Actions is the compiler: push, `scripts/ci-wait.sh`, `scripts/fetch-apk.sh`,
`python3 scripts/inspect-apk.py out/Hiatus-X.Y.Z.apk` (pip install androguard).
Toolchain copied from Minutia: AGP 9.5.1 with built-in Kotlin (no `kotlin.android` plugin), Compose compiler
plugin 2.4.21, Compose BOM 2026.09.00, but **material3 pinned to 1.5.0-alpha27** (like Aria): the BOM's
material3 keeps the Expressive APIs internal. In that version `RoundedPolygon.toShape()` is `@Composable`.
The owner asked for it to look native to Android, not like a Nothing widget, and to be as customisable as Minutia.

## Seeing your work (screenshots)
`app/src/test/.../Screenshots.kt` renders every widget style × size × light/dark, all presets, and the main screens to
PNG with Robolectric native graphics. Workflow `screenshots.yml` runs it on every **non-main** branch push (Java 21)
and force-pushes the PNGs to the orphan branch `shots`; `scripts/shots.sh` waits and extracts them to `out/shots/`.
Work on a branch, look at the shots, then merge to main to release. Fake apps/usage come from `fakeReport()`.

## Architecture (`app/src/main/java/dev/savar/hiatus`)
| File | What it does |
|---|---|
| `Usage.kt` | One pass over `UsageStatsManager.queryEvents` → `Timeline` (unlocked sessions, unlocks). `day()` → `DayStats` over "counted" time (the day so far minus the sleep window if excluded): away, share, breaks (gaps ≥ shortest break), unlocks, first unlock. `lastAway()`. `Fmt` (durations, share text, clock). |
| `WidgetConfig.kt` | The one widget's look as JSON in prefs `widget` (`w_<id>`, new widgets inherit `last`). Defaults: Surface palette, dark, system corners. `AppPrefs`: shortest break, exclude sleep, bedtime/wake. |
| `WidgetRenderer.kt` | Draws the widget into a bitmap: label, big number (smaller units), "x% of your day". Palettes from `android.R.color.system_*` per `ColorRole` or custom colours; `cornerDp` = `system_app_widget_background_radius` unless set by hand. |
| `HiatusWidget.kt` | Provider (`goAsync` + single-thread executor), `Widgets.refresh/size` (coalesced; `fresh = false` reuses stats for 90 s so edits redraw before OxygenOS freezes the app; the app also redraws on ON_STOP), tap intents (Hiatus / app / refresh), `Refresher` (inexact 15-min alarm), `HiatusApp` + `ScreenListener`. |
| `MainActivity.kt` | Activity (also the widget configure activity, titled "Hiatus") and tabs: Hiatus (analytics) / Widget / Settings. |
| `AnalyticsScreen.kt` | Home: hero (away + share), week bars (tap a day), Breaks, Check-ins. |
| `WidgetEditor.kt` | Pinned live preview, then Text / Font / Colours / Layout / On tap, Reset. `sampleReport()` feeds previews before usage access. |
| `AppSettings.kt` | Theme, pure black (`pure_black_v2`, off by default), sleep window, shortest break, access, about. |
| `Theme.kt` | `HiatusTheme` (`MaterialExpressiveTheme`, dynamic colour, `ThemePrefs`, `pureBlack()`). Normal Material colours by default. |
| `Components.kt`, `FontManager.kt`, `FontBrowser.kt` | Copied from Minutia: grouped rows, switches, sliders, colour picker, app picker, font editor, Google Fonts. Keep in step with Minutia when either changes. |
