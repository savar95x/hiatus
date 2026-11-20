# Hiatus: handoff for AI agents

Android "time away from your phone" widget (idea from Nothing's "Away Time", look is pure Material 3) + an away-time analytics app.
**Scope is deliberately tiny** (owner, 0.6.0; rebuilt 0.7.0): away time first. The only extra (owner, 0.8.0) is
an Apps section on Home (most used, and "pulls you back" = first app after unlocking). No screen-time totals,
presets or other widget styles. The widget editor was deliberately trimmed in 0.8.0: keep it short, keep fonts. Kotlin, package `dev.savar.hiatus`,
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
`app/src/test/.../Screenshots.kt` renders the widget (looks × sizes 1×1…4×2, dark/light wallpaper) and every screen
to PNG with Robolectric native graphics, using `Sample` data (a synthetic week run through the real `Away` maths).
Workflow `screenshots.yml` runs it on every **non-main** branch push and force-pushes the PNGs to the orphan
branch `shots`; `scripts/shots.sh` waits and extracts them to `out/shots/`. Work on a branch, look, then merge to main.

## Architecture (`app/src/main/java/dev/savar/hiatus`), rebuilt from scratch in 0.7.0
| File | What it does |
|---|---|
| `Away.kt` | The maths. `live()` = one pass over usage events → `Timeline` (in-use spans from unlock to lock/screen-off; screen-on if the phone never reports unlocks; foreground app spans from ACTIVITY_RESUMED/PAUSED minus launchers; first app after each unlock). `timeline()` = live + `History`. `day()` → `Day` over counted time (day so far minus sleep): breaks ≥ shortest break, away = sum of breaks, share, unlocks. Sleep: `Rules` OFF / AUTO (longest 3 h+ gap reaching into 00–08) / FIXED. `week()`, `today()`. |
| `History.kt` | One small text file per finished day (unlocks + in-use spans, last 6 days only, kept 120 days), so past weeks outlive Android's ~week of events. |
| `Prefs.kt` | Observable app settings: `Rules`, theme, pure black. `setRules` refreshes widgets. |
| `Style.kt` | The widget's whole look (one `Style` for all instances), JSON in prefs `style`/`v2`: label, caption, format (incl. 2.8h), number/label/caption fonts + sizes, theme, background, `clock`, tap. No alignment (owner removed it). `parse` migrates older styles (`textFont` → label+caption). |
| `Render.kt` | `Renderer.render()` draws the widget bitmap; used by the widget **and** the editor preview. Palettes from Material You roles (`system_*` by name, tonal shades before Android 14); dark surface = container mixed 25% to black. Size classes (0.11): 1×1 = number only, centred, two lines if bigger; wide & short = number left, label/caption stacked right; else label top, number + caption bottom (number on two lines for tall/square when clearly bigger), plus a hairline day strip at ≥ 250×150 dp. Caption = "From 7:12 AM to 4:25 PM" with shorter fallbacks (`Fmt.windowShort`). Optional thin faint clock top-right. |
| `Theme.kt` `Sem` | Analytics colour: green = away (the only accent since 0.11; in-use/sleep are neutral). Apps get per-app colours from their icons (`AppIcons.color`). No icons in tiles/settings rows (owner). |
| `Widget.kt` | Provider, `Widgets.refresh` (coalesced, single thread, reuses today's numbers for 2 min on look edits), `ScreenWatch` (unlock → redraw; screen-off → arm a non-waking alarm delivered on next wake), `Alarms` (half-hourly inexact + midnight, all non-waking), tap intents. Root layout `widget` uses `@android:id/background` so the launcher rounds it like its own widgets;  |
| `Home.kt` | Home: hero, day strip, week (arrows through history), breaks, check-ins, apps (live days only; not in `History`), `AccessGate`. `HomeContent` is pure (screenshots). |
| `Editor.kt` | Widget tab (0.11), modelled on Minutia: main page = scrolling preview + rows (Text, Fonts, Look, On tap) with current values, ⋮ Reset; each opens a sub-page (pinned 168dp preview, shared-axis X slide, depth-based direction). Fonts page: Number / Label / Below the number groups, each typeface row + weight slider + size slider → `TypefacePage` (pinned search + category chips; System default, From a file = picker straight away, Suggested = `Fonts.googleSuggestions` (owner's picks, no Roboto), all Google Fonts). `FontLoader` downloads/imports. Saves and pushes each change. |
| `Updater.kt`, `UpdateUi.kt` | In-app updates (0.9.0): `Updates.check` on resume (hourly, ETag) against `releases/latest`; APK kept in `noBackupFilesDir/updates`, resumed with Range, prefetched on unmetered, verified (same package, newer) and deleted once installed; installed via `PackageInstaller` session (`USER_ACTION_NOT_REQUIRED` on 12+), `InstallReceiver` launches the confirm screen. `UpdateCard` on Home, `UpdateSettingsRow` in About. Relies on the release asset being the `.apk` CI publishes. |
| `SettingsScreen.kt`, `MainActivity.kt`, `Theme.kt`, `Ui.kt`, `Fonts.kt`, `Format.kt`, `Sample.kt` | Settings tab; activity + bottom nav (also the widget's configure screen); theme; shared rows/groups/pickers; Google Fonts download (TTF via plain user agents); formatting; sample data. |

Icon: `res/drawable/ic_launcher_*.xml`, a ring (r 18, stroke 8.2, gap top-right) with a coral dot in the gap;
colours shared with Aria and Minutia (#1C1C1E, #EEE7DC, #E2957A).
