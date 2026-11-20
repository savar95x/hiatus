package dev.savar.hiatus

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Calendar

/**
 * Not real tests: renders the widget and the screens to PNG (build/shots) so changes can be reviewed in CI
 * without a phone. Run by .github/workflows/screenshots.yml; fetch with scripts/shots.sh. Data is [Sample].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class Screenshots {
    @get:Rule val compose = createComposeRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val out = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }

    /** 9:40 PM today: a full-looking day. */
    private val now: Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 21); set(Calendar.MINUTE, 40); set(Calendar.SECOND, 0)
    }.timeInMillis

    private fun save(bmp: Bitmap, name: String) {
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // ------------------------------------------------------------------ widget

    /** Rows of looks × columns of sizes (dp), on a wallpaper-like gradient. */
    private fun sheet(name: String, rows: List<Pair<String, Style>>, dark: Boolean, rules: Rules = Rules()) {
        val sizes = listOf(76 to 80, 168 to 80, 344 to 80, 168 to 168, 344 to 168, 168 to 260)
        val day = Sample.today(rules, now)
        val d = 2.625f
        val gap = (18 * d).toInt()
        val labelH = (20 * d).toInt()
        val w = sizes.sumOf { (it.first * d).toInt() } + gap * (sizes.size + 1)
        val rowH = (260 * d).toInt()
        val h = (rowH + labelH + gap) * rows.size + gap
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = if (dark) LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xFF22324A.toInt(), 0xFF3B2A40.toInt(), Shader.TileMode.CLAMP)
            else LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xFFE9DFCF.toInt(), 0xFFC9DAEA.toInt(), Shader.TileMode.CLAMP)
        })
        val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12 * d; color = if (dark) Color.WHITE else Color.BLACK }
        var y = gap
        for ((label, style) in rows) {
            c.drawText(label, gap.toFloat(), y + labelH * 0.7f, lp)
            y += labelH
            var x = gap
            for ((wDp, hDp) in sizes) {
                c.drawBitmap(Renderer.render(ctx, style, (wDp * d).toInt(), (hDp * d).toInt(), d, day, rules, systemDark = dark), x.toFloat(), y.toFloat(), null)
                x += (wDp * d).toInt() + gap
            }
            y += rowH + gap
        }
        save(bmp, name)
    }

    private fun looks() = listOf(
        "Default" to Style(),
        "Clock" to Style(clock = true),
        "Nothing below the number" to Style(caption = Caption.NONE),
        "Tinted, share of day, clock" to Style(background = Background.TINTED, caption = Caption.SHARE, clock = true),
        "Light, 2 hr 45 min, longest break" to Style(theme = ThemeMode.LIGHT, format = NumberFormat.WORDS, caption = Caption.LONGEST),
        "2.8h, no label, 80%" to Style(format = NumberFormat.DECIMAL, showLabel = false, numberScale = 80),
        "Clear, clock" to Style(background = Background.NONE, clock = true),
    )

    @Test fun widget_dark() = sheet("widget_dark", looks(), dark = true)
    @Test fun widget_light() = sheet("widget_light", looks(), dark = false)
    @Test fun widget_sleep() = sheet("widget_sleep", listOf("Sleep auto" to Style()), dark = true, rules = Rules(sleep = SleepMode.AUTO))

    // ------------------------------------------------------------------ screens

    private fun screen(name: String, dark: Boolean, black: Boolean = false, after: () -> Unit = {}, content: @Composable () -> Unit) {
        Prefs.init(ctx)
        Prefs.setTheme(ctx, if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
        Prefs.setPureBlack(ctx, black)
        compose.mainClock.autoAdvance = false
        compose.setContent { HiatusTheme { content() } }
        repeat(4) { compose.mainClock.advanceTimeBy(800); Thread.sleep(150) }
        after()
        repeat(4) { compose.mainClock.advanceTimeBy(800); Thread.sleep(150) }
        save(compose.onRoot().captureToImage().asAndroidBitmap(), name)
    }

    private fun home(rules: Rules = Rules()) = HomeData(Sample.week(rules, now), 0, true, now)

    @Test fun home_dark() { val r = Rules(); val d = home(r); screen("home_dark", true) { HomeContent(d, r) {} } }
    @Test fun home_light() { val r = Rules(); val d = home(r); screen("home_light", false) { HomeContent(d, r) {} } }

    @Config(qualifiers = "w411dp-h3000dp-xxhdpi")
    @Test fun home_full_black() {
        val r = Rules(sleep = SleepMode.AUTO); val d = home(r)
        screen("home_full_black", true, black = true) { HomeContent(d, r) {} }
    }

    @Config(qualifiers = "w411dp-h3000dp-xxhdpi")
    @Test fun home_full_light_fixed_sleep() {
        val r = Rules(sleep = SleepMode.FIXED, bed = 60, wake = 8 * 60, minBreakMs = 5 * 60_000L); val d = home(r)
        screen("home_full_light", false) { HomeContent(d, r) {} }
    }

    @Test fun update_card() {
        val r = Rules(); val d = home(r)
        Updates.showForPreview(Updates.State.Available(Release("0.9.0",
            "- Update Hiatus from inside the app.\n- Checks GitHub at most once an hour.\n- Downloads once, resumes if cut off.",
            "", 2_900_000L)))
        screen("update_card", true) { HomeContent(d, r) {} }
        Updates.showForPreview(Updates.State.Idle)
    }

    @Test fun access() = screen("access", false) { AccessGate() }

    @Config(qualifiers = "w411dp-h2600dp-xxhdpi")
    @Test fun editor() { val r = Rules(); val day = Sample.today(r, now); screen("editor", true) { WidgetEditor(day, r) } }

    @Config(qualifiers = "w411dp-h2200dp-xxhdpi")
    @Test fun editor_fonts() {
        val r = Rules(); val day = Sample.today(r, now)
        screen("editor_fonts", true, after = { compose.onNodeWithText("Fonts").performClick() }) { WidgetEditor(day, r) }
    }

    @Config(qualifiers = "w411dp-h1800dp-xxhdpi")
    @Test fun editor_text() {
        val r = Rules(); val day = Sample.today(r, now)
        screen("editor_text", false, after = { compose.onNodeWithText("Text").performClick() }) { WidgetEditor(day, r) }
    }

    @Config(qualifiers = "w411dp-h1800dp-xxhdpi")
    @Test fun settings() = screen("settings", true) { SettingsScreen(true) }

    @Config(qualifiers = "w411dp-h914dp-xxhdpi")
    @Test fun app_nav() = screen("app", true) { App(Tab.SETTINGS) {} }
}
