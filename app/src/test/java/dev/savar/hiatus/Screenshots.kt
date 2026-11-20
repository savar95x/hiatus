package dev.savar.hiatus

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Calendar

/**
 * Not real tests: renders the widgets and main screens to PNG (build/shots) so they can be reviewed in CI
 * without a phone. Run by .github/workflows/screenshots.yml; fetch with scripts/shots.sh.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class Screenshots {
    @get:Rule val compose = createComposeRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val out = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }

    // ------------------------------------------------------------------ fake data

    private val fakeApps = listOf(
        Triple("com.fake.youtube", "YouTube", 0xFFE53935.toInt()),
        Triple("com.fake.instagram", "Instagram", 0xFFD81B60.toInt()),
        Triple("com.fake.chrome", "Chrome", 0xFF43A047.toInt()),
        Triple("com.fake.whatsapp", "WhatsApp", 0xFF00897B.toInt()),
        Triple("com.fake.spotify", "Spotify", 0xFF1DB954.toInt()),
        Triple("com.fake.maps", "Maps", 0xFF1E88E5.toInt()),
        Triple("com.fake.gmail", "Gmail", 0xFFF4511E.toInt()),
    )

    private fun installFakeApps() {
        val spm = shadowOf(ctx.packageManager)
        for ((pkg, label, color) in fakeApps) {
            val ai = ApplicationInfo().apply { packageName = pkg; nonLocalizedLabel = label; name = label }
            spm.installPackage(PackageInfo().apply { packageName = pkg; applicationInfo = ai })
            spm.setApplicationIcon(pkg, ShapeDrawable(OvalShape()).apply {
                paint.color = color; intrinsicWidth = 96; intrinsicHeight = 96
            })
        }
    }

    private fun fakeReport(): Report {
        installFakeApps()
        val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 21); set(Calendar.MINUTE, 40) }
        val now = cal.timeInMillis
        val h = 3_600_000L
        val m = 60_000L
        val pattern = longArrayOf(0, 0, 0, 0, 0, 0, 0, 8, 22, 14, 9, 18, 31, 26, 12, 10, 15, 21, 28, 35, 40, 33, 0, 0)
        val mins = longArrayOf(78, 52, 37, 24, 18, 12, 6)
        fun day(offset: Int, scale: Float): DayStats {
            val start = Usage.dayStart(now, offset)
            val end = if (offset == 0) now else Usage.dayStart(now, offset + 1)
            val apps = fakeApps.mapIndexed { i, (pkg) -> AppUsage(pkg, (mins[i] * scale * m).toLong()) }
            val st = apps.sumOf { it.ms }
            return DayStats(
                start = start, end = end, screenTimeMs = st, inUseMs = st + 9 * m, awayMs = (end - start) - st - 9 * m,
                longestAwayMs = 7 * h + 12 * m, sessions = (46 * scale).toInt(), unlocks = (58 * scale).toInt(),
                pickups = (74 * scale).toInt(), hourly = LongArray(24) { (pattern[it] * scale * m).toLong() }, apps = apps,
            )
        }
        val scales = listOf(1.2f, 0.8f, 1.5f, 0.6f, 1.1f, 1.35f, 1f)
        val days = (-6..0).map { day(it, scales[it + 6]) }
        return Report(now, Away(2 * h + 13 * m, now - 2 * h - 23 * m, now - 10 * m, false), days)
    }

    // ------------------------------------------------------------------ widgets

    private fun save(bmp: Bitmap, name: String) {
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** One contact sheet: rows of configs, columns of sizes, on a wallpaper-like gradient. */
    private fun sheet(name: String, rows: List<Pair<String, WidgetConfig>>, sizes: List<Pair<Int, Int>>, dark: Boolean) {
        val report = fakeReport()
        val d = 2f
        val gap = (20 * d).toInt()
        val labelH = (22 * d).toInt()
        val colW = sizes.map { (it.first * d).toInt() }
        val rowH = rows.map { sizes.maxOf { (it.second * d).toInt() } }
        val w = colW.sum() + gap * (sizes.size + 1)
        val h = rowH.sum() + (gap + labelH) * rows.size + gap
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint().apply {
            shader = if (dark) LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xFF1D2B3A.toInt(), 0xFF2B1F33.toInt(), Shader.TileMode.CLAMP)
            else LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xFFE8E0D2.toInt(), 0xFFCFDCE8.toInt(), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
        val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13 * d; color = if (dark) Color.WHITE else Color.BLACK }
        var y = gap
        rows.forEachIndexed { ri, (label, cfg0) ->
            c.drawText(label, gap.toFloat(), y + labelH * 0.7f, lp)
            y += labelH
            var x = gap
            sizes.forEachIndexed { si, (wDp, hDp) ->
                val cfg = cfg0.copy(theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
                val wb = WidgetRenderer.render(ctx, cfg, (wDp * d).toInt(), (hDp * d).toInt(), d, report)
                c.drawBitmap(wb, x.toFloat(), y.toFloat(), null)
                x += colW[si] + gap
            }
            y += rowH[ri] + gap
        }
        save(bmp, name)
    }

    private val sizes = listOf(170 to 170, 340 to 170, 340 to 340)

    private fun styleRows() = WidgetStyle.entries.map { it.label to WidgetConfig(style = it) }

    @Test fun widgets_light() = sheet("widgets_light", styleRows(), sizes, dark = false)
    @Test fun widgets_dark() = sheet("widgets_dark", styleRows(), sizes, dark = true)
    @Test fun presets_light() = sheet("presets_light", Presets.all.map { it.name to it.config }, sizes, dark = false)
    @Test fun presets_dark() = sheet("presets_dark", Presets.all.map { it.name to it.config }, sizes, dark = true)

    // ------------------------------------------------------------------ screens

    private fun screen(name: String, dark: Boolean, black: Boolean = false, after: () -> Unit = {}, content: @Composable () -> Unit) {
        ThemePrefs.load(ctx)
        ThemePrefs.setMode(ctx, if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
        ThemePrefs.setPureBlack(ctx, black)
        compose.mainClock.autoAdvance = false
        compose.setContent { HiatusTheme { content() } }
        compose.mainClock.advanceTimeBy(1_500)
        Thread.sleep(400) // icons load on Dispatchers.IO
        compose.mainClock.advanceTimeBy(1_500)
        after()
        compose.mainClock.advanceTimeBy(1_500)
        save(compose.onRoot().captureToImage().asAndroidBitmap(), name)
    }

    @Test fun today_light() { val r = fakeReport(); screen("today_light", false) { TodayScreen(r, true, false, {}, {}) } }
    @Test fun today_black() { val r = fakeReport(); screen("today_black", true, black = true) { TodayScreen(r, true, false, {}, {}) } }

    @Config(qualifiers = "w411dp-h2600dp-xxhdpi")
    @Test fun today_full() { val r = fakeReport(); screen("today_full", true) { TodayScreen(r, true, false, {}, {}) } }

    @Test fun today_noaccess() = screen("today_noaccess", false) { TodayScreen(null, false, false, {}, {}) }
    @Test fun today_loading() = screen("today_loading", true) { TodayScreen(null, true, false, {}, {}) }

    @Config(qualifiers = "w411dp-h1400dp-xxhdpi")
    @Test fun editor_light() { val r = fakeReport(); screen("editor_light", false) { WidgetEditor(-1, r) } }

    @Config(qualifiers = "w411dp-h1400dp-xxhdpi")
    @Test fun editor_dark() { val r = fakeReport(); screen("editor_dark", true) { WidgetEditor(-1, r) } }

    @Config(qualifiers = "w411dp-h1400dp-xxhdpi")
    @Test fun settings_dark() = screen("settings_dark", true) { AppSettingsScreen(true, {}, {}) }

    @Config(qualifiers = "w411dp-h1400dp-xxhdpi")
    @Test fun editor_customise() {
        val r = fakeReport()
        screen("editor_customise", true, after = { compose.onNodeWithText("Customise").performClick() }) { WidgetEditor(-1, r) }
    }

    @Config(qualifiers = "w411dp-h1400dp-xxhdpi")
    @Test fun editor_content() {
        val r = fakeReport()
        screen("editor_content", false, after = {
            compose.onNodeWithText("Customise").performClick()
            compose.mainClock.advanceTimeBy(1_000)
            compose.onAllNodesWithText("Content").onFirst().performClick()
        }) { WidgetEditor(-1, r) }
    }

    @Test fun editor_new() { val r = fakeReport(); screen("editor_new", true, black = true) { WidgetEditor(7, r, onDone = {}) } }
}
