# Changelog

## [0.11.0] - 2026-11-20
- The widget is called Hiatus in the widget picker.
- Every widget size has its own layout: 1 × 1 shows just the number (on two lines when that's bigger); 4 × 1
  puts the number on the left with the label and caption on the right; tall widgets stack the number; 4 × 2 and
  bigger add a hairline strip of your day under the caption.
- The corner decoration is now a thin, faint clock.
- "Nothing below the number" is back to the number sitting at the bottom.
- The time span reads "From 7:12 AM to 4:25 PM", with shorter wordings when the widget is narrow.
- New number format: 2.8h.
- Widget tab rebuilt like Minutia: preview, then Text, Fonts, Look and On tap, each opening its own page with
  the preview pinned on top and a sideways slide between pages. Reset is in the ⋮ menu.
- Fonts page: the number, the label and the line below the number each get their own typeface, weight and
  size. Alignment is gone.
- Home is calmer: green means away, everything else is neutral, and each app gets its own colour from its icon.
  Icons removed from tiles and settings rows.

## [0.10.0] - 2026-11-20
- Widget background is lighter again: between 0.7's surface and 0.8's near-black.
- The corner shape is much smaller and fainter, tucked into the corner.
- With nothing below the number, the number now sits centred in the space under the label instead of hugging
  the bottom.
- Widget tab: titled "Widget", and the preview scrolls with the page instead of staying pinned. The size
  buttons are gone; the preview uses your widget's real size (2 × 2 until one is placed).
- Fonts work like Aria's: a font page with a live preview, the typeface, and weight on a slider; the typeface
  list has search and style filters pinned on top, with System default, From a file (opens the file picker
  straight away), Suggested (Google Sans, Google Sans Flex, Fragment Mono, Space Grotesk, Work Sans, Inter and a
  few more; no Roboto) and then every Google Font, each shown in its own face.

## [0.9.0] - 2026-11-20
- Updates from inside the app: when a newer release is on GitHub, Home shows what's new with an Update button,
  and Settings → About has Check for updates. Android asks once to let Hiatus install its updates; after the
  first one, later updates install without a prompt on Android 12+.
- No wasted downloads: GitHub is checked at most once an hour (an unchanged answer costs nothing), each update
  is downloaded once, resumes if the connection drops, and is deleted after it's installed. On Wi-Fi it's
  fetched ahead, so Install is instant.

## [0.8.0] - 2026-11-20
- Widget: darker default background (still tinted by your wallpaper). Under the number it now shows the time
  span counted, like "7:12 AM – 4:25 PM" (from waking up when sleep is left out); share of day is still an option.
- Widget: the corner icon is now an abstract shape, a soft scalloped form sitting off the top-right corner, faint
  and purely decorative.
- Widget editor trimmed to what matters: label, what's under the number, number format, fonts (system, Google
  or a file), theme, background (surface, tinted, clear), alignment, number size, corner shape, tap action.
  Your fonts, label and tap action carry over.
- Home has colours with meaning: away is green, phone in use is coral, sleep is indigo, across the hero, the
  day strip, the week, breaks and check-ins.
- New Apps section: most used apps (time and opens), and "Pulls you back", the apps you open first after
  unlocking.
- The font picker's tabs read System, Google and File.

## [0.7.0] - 2026-11-20
- Rebuilt from scratch: new code throughout, same app (installs over 0.6.0). Widget looks start fresh on the new
  default; re-place the widget if it looks stuck.
- New icon: a ring with a gap and a coral dot, in the same colours and line weight as Aria and Minutia.
- Home: away today with a wavy share-of-day bar, the day as a strip (away, in use, sleep), the week's bars with
  arrows back to earlier weeks (finished days are now kept on the phone, so history outlasts Android's week of
  events), breaks (longest, average, count, last, and how the away time splits by break length) and check-ins
  (unlocks, how often, first and last unlock, unlocks by hour).
- Away now only counts breaks at least the shortest break long, so the breaks add up to the total.
- Leave out sleep: Off, Auto (each night's longest break is taken as sleep, no setup) or Set hours (with presets
  like 3 – 8 AM).
- Widget: resizes down to 1 × 1 (caption, then label make way for the number), optional faint icon (Hiatus mark,
  moon or pause; in the corner or behind), number formats including minutes and hours only, separate number and
  text fonts, vertical layout (spread, top, centre, bottom), number and text size, padding, corners.
- Widget refreshes when you unlock, at midnight and when you change something; no waking alarms.

## [0.6.0] - 2026-11-20
- Hiatus is now just about time away from your phone. Screen time, app usage, presets and the other widget
  styles are gone.
- Home is an analytics page: away today with share of your day, the week's away time (tap a day), breaks
  (longest, average, count, last) and check-ins (unlocks, how often, first unlock).
- Settings has its own tab. New: leave out sleep, so your bedtime to wake-up hours don't count as away.
- One widget: "Away today", the number, and share of your day. Default is the wallpaper's dark surface colour
  with the same corner roundness as your phone's other widgets. Text, font, colours, background, layout and tap
  action are all still customisable. Existing widgets start fresh on the new default.
- The app's theme is back to normal Material colours by default; pure black is an option in Settings.

## [0.5.0] - 2026-11-20
- New Away widget style, now the default: total time away today, a 24-pill timeline of the day (brighter where
  you were away), share of the day, longest break, and a soft Material shape in the corner (or none).
- Away today is the headline everywhere; "Last away" moved to More looks. The Today card shows the same timeline.
- Tap the widget to open Hiatus, open an app, or refresh it ("None" is gone).
- Pure black is always switchable in Settings, now reachable from the Widgets tab too.

## [0.4.0] - 2026-11-20
- Away time leads: the editor opens on four away looks (Away, Cookie, Midnight, Away + day); screen-time looks
  sit under "More looks", and other stats under "More stats".
- Today's top card also shows away time so far and the longest break.
- Darker dark mode, close to OxygenOS's own apps, for the app and the widgets. Pure black still goes to #000.
- Widgets update right after you change a setting, even if you leave the app straight away.
- Tapping the widget: Hiatus, an app, or nothing (Refresh removed; the widget keeps itself current).

## [0.3.0] - 2026-11-20
- Widgets: start from eight ready-made looks (Away, Goal ring, Cookie, Glance, Day, Top apps, Overview,
  Midnight). Every option is still there under Customise.
- Adding a widget from the home screen opens a focused "New widget" screen with Done.
- Widget layouts reworked: number and caption sit at the bottom, a finer Material wave on the ring, the shape
  style uses wide widgets properly, tiles share one number size and only show what fits, nothing truncates on
  small sizes, dashboard tiles show their labels.
- Today: day names that fit, the selected day's total in the week card, flat usage bars, cleaner stat rows.
- Smaller fixes: clearer permission screen, predictive back, consistent icons in Settings, "3h" instead of "3h 0m".
- Battery: the widget no longer wakes the phone every 30 minutes, and unlocking redraws it once instead of twice.

## [0.2.0] - 2026-11-20
- Redesigned in Material 3 Expressive; the dot-matrix look is gone.
- Seven widget styles: Number, Ring (screen time against a daily goal, flat or wavy), Shape (Material shapes),
  Tiles, Chart, Top apps and Dashboard. Each widget is set up on its own, with a live preview.
- Pick what the number shows (last away, away today, screen time, unlocks, screen wakes, per session, longest
  away, sessions), which tiles appear, labels, captions and duration format.
- Any font: system, Google Fonts or your own file, with weight.
- Wallpaper colours from five palettes (Surface, Primary, Secondary, Tertiary, Black) with a light/dark
  override, or your own colours; background opacity and corner radius; number size and alignment.
- Tap the widget to open Hiatus, another app, refresh it, or do nothing.
- App: new Today screen with a screen-time donut, week chart, stats and top apps; Widgets tab; Settings with
  theme, pure black (AMOLED) mode and the shortest break that counts as away.

## [0.1.0] - 2026-11-20
- First build: one resizable home-screen widget in the style of Nothing's Away Time. Small shows your last
  stretch off the phone in dot-matrix type; wider adds screen time, unlocks and time per session; taller adds
  hourly bars and your top three apps. Follows your wallpaper colours and dark mode.
- App: last away stretch, a 7-day screen-time chart (tap a day), six daily stats, hourly breakdown and top apps.
  Material 3 Expressive.
