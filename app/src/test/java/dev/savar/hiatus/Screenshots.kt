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

    private fun fakeReport(sleepExcluded: Boolean = false): Report {
        val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 21); set(Calendar.MINUTE, 40) }
        val now = cal.timeInMillis
        val h = 3_600_000L
        val m = 60_000L
        fun day(offset: Int, share: Float): DayStats {
            val start = Usage.dayStart(now, offset)
            val end = if (offset == 0) now else Usage.dayStart(now, offset + 1)
            val counted = end - start
            return DayStats(
                start = start, end = end, countedMs = counted, awayMs = (counted * share).toLong(),
                breaks = listOf(Iv(start + 30 * m, start + 7 * h + 42 * m), Iv(end - 2 * h - 20 * m, end - 10 * m), Iv(start + 13 * h, start + 13 * h + 47 * m)),
                unlocks = (58 / share * 0.8f).toInt(), firstUnlock = start + 7 * h + 42 * m,
            )
        }
        val shares = listOf(0.74f, 0.81f, 0.69f, 0.84f, 0.77f, 0.72f, 0.79f)
        val days = (-6..0).map { day(it, shares[it + 6]) }
        return Report(now, Away(2 * h + 10 * m, now - 2 * h - 20 * m, now - 10 * m, false), days, sleepExcluded)
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
                val cfg = cfg0
                val wb = WidgetRenderer.render(ctx, cfg, (wDp * d).toInt(), (hDp * d).toInt(), d, report)
                c.drawBitmap(wb, x.toFloat(), y.toFloat(), null)
                x += colW[si] + gap
            }
            y += rowH[ri] + gap
        }
        save(bmp, name)
    }

    private val sizes = listOf(170 to 170, 340 to 170, 170 to 90, 340 to 340)

    private fun looks() = listOf(
        "Default (Surface, dark)" to WidgetConfig(),
        "Primary" to WidgetConfig(role = ColorRole.PRIMARY),
        "Black" to WidgetConfig(role = ColorRole.BLACK),
        "Light theme, centred, caps" to WidgetConfig(theme = ThemeMode.LIGHT, align = HAlign.CENTER, uppercaseLabel = true),
        "2 hr 13 min, no caption" to WidgetConfig(format = DurationFormat.LONG, showCaption = false),
    )

    @Test fun widgets() = sheet("widgets", looks(), sizes, dark = true)
    @Test fun widgets_wallpaper_light() = sheet("widgets_light", looks(), sizes, dark = false)

    // ------------------------------------------------------------------ screens

    /** [frozen]: keep the clock still (needed for screens with endless animations; clicks then need manual advancing). */
    private fun screen(
        name: String, dark: Boolean, black: Boolean = false, frozen: Boolean = true,
        after: () -> Unit = {}, content: @Composable () -> Unit,
    ) {
        ThemePrefs.load(ctx)
        ThemePrefs.setMode(ctx, if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
        ThemePrefs.setPureBlack(ctx, black)
        compose.mainClock.autoAdvance = !frozen
        compose.setContent { HiatusTheme { content() } }
        compose.mainClock.advanceTimeBy(1_500)
        Thread.sleep(400) // icons load on Dispatchers.IO
        compose.mainClock.advanceTimeBy(1_500)
        after()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_500)
        save(compose.onRoot().captureToImage().asAndroidBitmap(), name)
    }

    @Test fun analytics_light() { val r = fakeReport(); screen("analytics_light", false) { AnalyticsScreen(r, true, false, {}) } }
    @Test fun analytics_dark() { val r = fakeReport(); screen("analytics_dark", true) { AnalyticsScreen(r, true, false, {}) } }
    @Test fun analytics_black() { val r = fakeReport(true); screen("analytics_black", true, black = true) { AnalyticsScreen(r, true, false, {}) } }

    @Config(qualifiers = "w411dp-h2000dp-xxhdpi")
    @Test fun analytics_full() { val r = fakeReport(); screen("analytics_full", true) { AnalyticsScreen(r, true, false, {}) } }

    @Test fun analytics_noaccess() = screen("analytics_noaccess", false) { AnalyticsScreen(null, false, false, {}) }

    @Config(qualifiers = "w411dp-h1400dp-xxhdpi")
    @Test fun editor_dark() { val r = fakeReport(); screen("editor_dark", true, frozen = false) { WidgetEditor(-1, r) } }

    @Config(qualifiers = "w411dp-h1400dp-xxhdpi")
    @Test fun editor_text() {
        val r = fakeReport()
        screen("editor_text", false, frozen = false, after = { compose.onNodeWithText("Text").performClick() }) { WidgetEditor(-1, r) }
    }

    @Test fun editor_new() { val r = fakeReport(); screen("editor_new", true, frozen = false) { WidgetEditor(7, r, onDone = {}) } }

    @Config(qualifiers = "w411dp-h1600dp-xxhdpi")
    @Test fun settings_dark() = screen("settings_dark", true, frozen = false) { AppSettingsScreen(true, {}) }
}
