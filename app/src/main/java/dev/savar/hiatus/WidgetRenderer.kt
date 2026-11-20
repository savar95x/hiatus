package dev.savar.hiatus

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.TextPaint
import android.text.TextUtils
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Draws the Hiatus widget (time away today) into one bitmap, so it can use any font.
 * Colours come from the wallpaper's Material You tonal palettes (or the widget's custom colours).
 */
object WidgetRenderer {

    class Palette(
        val bg: Int,     // container
        val number: Int, // the big number, progress, active bars
        val text: Int,   // names, values in lists
        val sub: Int,    // labels, captions
        val track: Int,  // empty progress, idle bars, shape fill
        val tile: Int,   // nested tiles
    )

    // ---------------------------------------------------------------- colours

    private val toneIds = HashMap<String, Int>()

    /** android.R.color.system_<pal>_<shade>, e.g. ("accent1", 700). API 31+. */
    private fun sys(ctx: Context, pal: String, shade: Int): Int {
        val name = "system_${pal}_$shade"
        val id = toneIds.getOrPut(name) { ctx.resources.getIdentifier(name, "color", "android") }
        return if (id != 0) ctx.getColor(id) else Color.MAGENTA
    }

    fun isDark(ctx: Context, cfg: WidgetConfig): Boolean = when (cfg.theme) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    private fun mix(a: Int, b: Int, t: Float) = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).roundToInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).roundToInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).roundToInt(),
    )

    private fun alpha(c: Int, a: Float) =
        Color.argb((Color.alpha(c) * a).roundToInt().coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))

    fun palette(ctx: Context, cfg: WidgetConfig): Palette {
        val dark = isDark(ctx, cfg)
        val wallpaper = cfg.useWallpaperColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val p = if (!wallpaper) {
            val (bg, num, txt) = if (cfg.useWallpaperColors) {
                // Wallpaper colours asked for, but the phone is older than Android 12.
                if (dark) Triple(0xFF0E0F13.toInt(), 0xFFB8C4FF.toInt(), 0xFFE4E2E6.toInt())
                else Triple(0xFFF3F3FA.toInt(), 0xFF4A5C92.toInt(), 0xFF1B1B1F.toInt())
            } else Triple(cfg.bgColor, cfg.numberColor, cfg.textColor)
            Palette(bg, num, txt, alpha(txt, 0.72f), alpha(num, 0.28f), alpha(txt, 0.09f))
        } else {
            fun s(pal: String, shade: Int) = sys(ctx, pal, shade)
            val acc = when (cfg.role) {
                ColorRole.SECONDARY -> "accent2"
                ColorRole.TERTIARY -> "accent3"
                else -> "accent1"
            }
            when {
                cfg.role == ColorRole.BLACK -> Palette(
                    Color.BLACK, s("accent1", 200), s("neutral1", 50), s("neutral2", 300), s("accent2", 800), s("neutral1", 900),
                )
                // Dark widgets sit near black, like OxygenOS's own dark UI.
                cfg.role == ColorRole.SURFACE && dark -> Palette(
                    s("neutral1", 900), s("accent1", 200), s("neutral1", 100), s("neutral2", 200), s("accent2", 800), s("neutral1", 800),
                )
                cfg.role == ColorRole.SURFACE -> Palette(
                    s("neutral1", 50), s("accent1", 600), s("neutral1", 900), s("neutral2", 700), s("accent2", 100), s("neutral2", 100),
                )
                dark -> Palette(
                    mix(s(acc, 900), Color.BLACK, 0.25f), s(acc, 100), s(acc, 50), s(acc, 200), s(acc, 700), s(acc, 800),
                )
                else -> Palette(s(acc, 100), s(acc, 800), s(acc, 900), s(acc, 700), s(acc, 200), s(acc, 50))
            }
        }
        val bg = if (cfg.background) alpha(p.bg, cfg.bgOpacity / 100f) else Color.TRANSPARENT
        return Palette(bg, p.number, p.text, p.sub, p.track, p.tile)
    }

    // ---------------------------------------------------------------- text

    private class Fonts(val number: Typeface, val label: Typeface, val body: Typeface)

    private fun fonts(ctx: Context, cfg: WidgetConfig): Fonts {
        val number = FontManager.typeface(ctx, cfg.font)
        val sans500 = Typeface.create(Typeface.DEFAULT, 500, false)
        val label = if (cfg.fontForLabels) FontManager.typeface(ctx, cfg.font.copy(weight = 500)) else sans500
        val body = if (cfg.fontForLabels) FontManager.typeface(ctx, cfg.font.copy(weight = 400)) else Typeface.DEFAULT
        return Fonts(number, label, body)
    }

    private fun paint(size: Float, color: Int, tf: Typeface) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color; typeface = tf
    }

    /** Digit height as a fraction of text size, per typeface. */
    private val capCache = HashMap<Typeface, Float>()
    private fun capRatio(tf: Typeface): Float = capCache.getOrPut(tf) {
        val p = paint(100f, 0, tf)
        val b = Rect()
        p.getTextBounds("0123456789", 0, 10, b)
        (b.height() / 100f).takeIf { it > 0.3f } ?: 0.72f
    }

    /** A number like "2h 13m" split into digit runs and (smaller) unit runs. */
    private class Rich(val runs: List<Pair<String, Boolean>>, val unitScale: Float)

    private fun rich(text: String, cfg: WidgetConfig): Rich {
        val runs = ArrayList<Pair<String, Boolean>>()
        val sb = StringBuilder()
        var unit = false
        for (ch in text) {
            val u = !(ch.isDigit() || ch == ':' || ch == '.' || ch == ',')
            if (sb.isNotEmpty() && u != unit) { runs.add(sb.toString() to unit); sb.clear() }
            unit = u
            sb.append(ch)
        }
        if (sb.isNotEmpty()) runs.add(sb.toString() to unit)
        val scale = if (!cfg.smallUnits) 1f else if (cfg.format == DurationFormat.LONG) 0.42f else 0.55f
        return Rich(runs, scale)
    }

    private fun richWidth(r: Rich, size: Float, tf: Typeface): Float {
        val p = paint(size, 0, tf)
        var w = 0f
        for ((s, u) in r.runs) {
            p.textSize = if (u) size * r.unitScale else size
            w += p.measureText(s)
        }
        return w
    }

    private fun drawRich(c: Canvas, r: Rich, x: Float, baseline: Float, size: Float, color: Int, tf: Typeface) {
        val p = paint(size, color, tf)
        var cx = x
        for ((s, u) in r.runs) {
            p.textSize = if (u) size * r.unitScale else size
            c.drawText(s, cx, baseline, p)
            cx += p.measureText(s)
        }
    }

    /** Text size at which [text] fills w×h (digit height), capped by [maxCap] (digit height) and × sizePercent. */
    private fun fitSize(text: String, w: Float, h: Float, cfg: WidgetConfig, f: Fonts, maxCap: Float = Float.MAX_VALUE): Float {
        if (w <= 0 || h <= 0) return 0f
        val r = rich(text, cfg)
        val cap = capRatio(f.number)
        val byW = w / max(1f, richWidth(r, 1f, f.number))
        val size = min(min(h, maxCap) / cap, byW) * (cfg.sizePercent / 100f).coerceIn(0.4f, 1.3f)
        return min(size, byW) // never overflow sideways
    }

    /** Draws a fitted number with its baseline at [baseline]; x from [box] and alignment. */
    private fun numberAt(
        c: Canvas, text: String, box: RectF, baseline: Float, size: Float, cfg: WidgetConfig, f: Fonts, color: Int,
        center: Boolean, end: Boolean = false,
    ) {
        val r = rich(text, cfg)
        val w = richWidth(r, size, f.number)
        val x = when {
            end -> box.right - w
            center -> box.centerX() - w / 2
            else -> box.left
        }
        drawRich(c, r, x, baseline, size, color, f.number)
    }

    /** Draws [text] as big as fits inside [box], vertically centred. Returns the digit height used. */
    private fun bigNumber(
        c: Canvas, text: String, box: RectF, cfg: WidgetConfig, f: Fonts, color: Int,
        center: Boolean, maxCap: Float = Float.MAX_VALUE, end: Boolean = false,
    ): Float {
        val size = fitSize(text, box.width(), box.height(), cfg, f, maxCap)
        if (size <= 0) return 0f
        val capH = size * capRatio(f.number)
        numberAt(c, text, box, box.centerY() + capH / 2, size, cfg, f, color, center, end)
        return capH
    }

    private fun line(c: Canvas, s: String, box: RectF, baseline: Float, p: TextPaint, center: Boolean, end: Boolean = false) {
        val t = TextUtils.ellipsize(s, p, box.width(), TextUtils.TruncateAt.END).toString()
        val x = when {
            end -> box.right - p.measureText(t)
            center -> box.centerX() - p.measureText(t) / 2
            else -> box.left
        }
        c.drawText(t, x, baseline, p)
    }

    // ---------------------------------------------------------------- entry

    /** Corner radius in dp: the phone's own widget radius (Android 12+) unless set by hand. */
    fun cornerDp(ctx: Context, cfg: WidgetConfig): Float {
        if (!cfg.systemCorners) return cfg.cornerRadius.toFloat()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                return ctx.resources.getDimension(android.R.dimen.system_app_widget_background_radius) /
                    ctx.resources.displayMetrics.density
            }
        }
        return 24f
    }

    /**
     * @param d px per dp (already scaled down if the bitmap was capped)
     * @param report null when usage access is missing
     */
    fun render(ctx: Context, cfg: WidgetConfig, w: Int, h: Int, d: Float, report: Report?): Bitmap {
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = palette(ctx, cfg)
        val f = fonts(ctx, cfg)
        if (Color.alpha(p.bg) > 0) {
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.bg }
            val rad = min(cornerDp(ctx, cfg) * d, min(w, h) / 2f)
            c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), rad, rad, bg)
        }
        // Material widget padding: 16dp, a little less on the smallest sizes.
        val pad = (if (min(w / d, h / d) < 130) 14f else 16f) * d
        val area = RectF(pad, pad, w - pad, h - pad)
        val center = cfg.align == HAlign.CENTER
        if (report == null) {
            hero(c, area, d, cfg, p, f, "—", cfg.label(), "Tap to allow usage access", center)
        } else {
            hero(c, area, d, cfg, p, f, Fmt.dur(report.today.awayMs, cfg.format), cfg.label(), Fmt.share(report), center)
        }
        return bmp
    }

    // ---------------------------------------------------------------- layout

    private fun hero(
        c: Canvas, r: RectF, d: Float, cfg: WidgetConfig, p: Palette, f: Fonts,
        value: String, label: String, caption: String, center: Boolean,
    ) {
        val small = (min(r.width(), r.height()) * 0.1f).coerceIn(11f * d, 14f * d)
        val lp = paint(small, p.sub, f.label).apply { if (cfg.uppercaseLabel) letterSpacing = 0.08f }
        val cp = paint(small, p.sub, f.body)
        val showLabel = cfg.showLabel && r.height() > 60 * d
        val showCaption = cfg.showCaption && r.height() > 92 * d
        val labelBlock = if (showLabel) small * 1.9f else 0f
        val captionBlock = if (showCaption) small * 1.75f else 0f
        val numAvail = r.height() - labelBlock - captionBlock
        val size = fitSize(value, r.width(), numAvail, cfg, f, maxCap = min(r.width(), r.height()) * 0.36f)
        val capH = size * capRatio(f.number)
        if (center) {
            val group = labelBlock + capH + captionBlock
            val top = r.top + (r.height() - group) / 2
            if (showLabel) line(c, label, r, top + small, lp, true)
            val base = top + labelBlock + capH
            numberAt(c, value, r, base, size, cfg, f, p.number, true)
            if (showCaption) line(c, caption, r, base + small * 1.75f, cp, true)
        } else {
            if (showLabel) line(c, label, r, r.top + small, lp, false)
            val capBase = r.bottom - small * 0.2f
            val numBase = if (showCaption) capBase - small * 1.55f else r.bottom
            numberAt(c, value, r, numBase, size, cfg, f, p.number, false)
            if (showCaption) line(c, caption, r, capBase, cp, false)
        }
    }

    fun description(report: Report?): String =
        if (report == null) "Hiatus. Tap to allow usage access."
        else "Away today " + Fmt.dur(report.today.awayMs) + ", " + Fmt.share(report) + "."
}
