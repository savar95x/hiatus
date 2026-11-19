# Hiatus: handoff for AI agents

Android home-screen widget (Nothing "Away Time" style) + Compose stats app. Kotlin, package `dev.savar.hiatus`,
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
plugin 2.4.21, Compose BOM 2026.09.00.

## Architecture (`app/src/main/java/dev/savar/hiatus`)
| File | What it does |
|---|---|
| `Usage.kt` | One pass over `UsageStatsManager.queryEvents` → `Timeline` (in-use sessions, unlocks, screen wakes, per-app foreground intervals). `day()` clips it into `DayStats`; `lastAway()` = latest gap ≥ 1 min between sessions. Session = KEYGUARD_HIDDEN → screen off / KEYGUARD_SHOWN (screen-on intervals if the device never reports unlocks). Screen time = union of app RESUMED→PAUSED/STOPPED, launcher + systemui excluded. `Fmt` formats durations. |
| `DotMatrix.kt` | 5×7 dot font (digits, h/m/d, A/W/Y, -) drawn on an android `Canvas`; used by widget and app. `fit()` picks one or two lines. |
| `WidgetRenderer.kt` | Whole widget as one bitmap. Layouts: compact (hero) / wide ≥250dp (hero + stat panel) / tall ≥230dp high (+ hourly bars + top 3 apps). Material You colours from `android.R.color.system_*`. |
| `HiatusWidget.kt` | Provider (`goAsync` + single-thread executor), `Widgets.refresh`, `Refresher` (inexact 15-min alarm), `HiatusApp` + `ScreenListener` (redraw on SCREEN_ON / USER_PRESENT / dark-mode change while the process lives). |
| `MainActivity.kt` | Compose UI: `MaterialExpressiveTheme`, permission screen, dashboard (hero, week chart, stat tiles with `MaterialShapes`, hourly chart, top apps with wavy progress). |
