# Hiatus: handoff for AI agents

Android home-screen screen-time widget (idea from Nothing's "Away Time", look is pure Material 3 Expressive) + Compose stats app. Kotlin, package `dev.savar.hiatus`,
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
| `Usage.kt` | One pass over `UsageStatsManager.queryEvents` → `Timeline` (in-use sessions, unlocks, screen wakes, per-app foreground intervals). `day()` clips it into `DayStats`; `lastAway()` = latest gap ≥ the "shortest break" setting. Session = KEYGUARD_HIDDEN → screen off / KEYGUARD_SHOWN (screen-on intervals if the device never reports unlocks). Screen time = union of app RESUMED→PAUSED/STOPPED, launcher + systemui excluded. `Fmt` (duration formats), `Metrics` (value + caption per `Metric`). |
| `WidgetConfig.kt` | Per-widget settings as JSON in prefs `widget_configs` (`w_<id>`, new widgets inherit `last`), enums (`WidgetStyle`, `Metric`, `ColorRole`, `ShapeKind`, …), `FontSpec`, `AppPrefs` (shortest break). New setting = field + toJson/fromJson + editor row. |
| `WidgetRenderer.kt` | Whole widget as one bitmap. Styles HERO / RING / SHAPE / TILES / CHART / APPS / DASHBOARD built from pieces (`hero`, `drawRing`, `tiles`, `bars`, `apps`). Palettes from `android.R.color.system_<accent/neutral>_<shade>` per `ColorRole`, or custom colours. Units drawn smaller (`rich`). |
| `Presets.kt` | The curated looks shown first in the editor. Applying one keeps the user's font and tap action. |
| `HiatusWidget.kt` | Provider (`goAsync` + single-thread executor), `Widgets.refresh/size`, tap intents, `Refresher` (inexact 15-min alarm), `HiatusApp` + `ScreenListener` (redraw on SCREEN_ON / USER_PRESENT / dark-mode change while the process lives). |
| `MainActivity.kt` | Activity (also the widget configure activity) and root: NavigationBar Today / Widgets, Settings page. |
| `TodayScreen.kt` | Dashboard: hero, week bars, per-app donut, stats group, hourly chart, top apps. |
| `WidgetEditor.kt` | Pinned live preview, widget chips, Looks (presets), then Customise → Style / Content / Font / Colours / Layout, and On tap. `onDone` = launched by the launcher's configure step (focused "New widget" screen). `sampleReport()` feeds previews before usage access. |
| `AppSettings.kt` | Theme, pure black, shortest break, usage access, battery, source link. |
| `Theme.kt` | `HiatusTheme` (`MaterialExpressiveTheme`, dynamic colour, `ThemePrefs`, `pureBlack()`). |
| `Components.kt`, `FontManager.kt`, `FontBrowser.kt` | Copied from Minutia: grouped rows, switches, sliders, colour picker, app picker, font editor, Google Fonts. Keep in step with Minutia when either changes. |
