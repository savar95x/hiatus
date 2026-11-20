package dev.savar.hiatus

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.TextPaint
import android.text.TextUtils
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Colours of one widget: background, the big number, label + caption, the corner shape. */
data class Palette(val bg: Int, val number: Int, val text: Int, val accent: Int)

object Renderer {

    // ------------------------------------------------------------------ colours

    fun systemDark(ctx: Context): Boolean =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /** A Material You colour: the named Android 14+ role, else the tonal-palette shade (12+), else a fallback. */
    private fun sys(ctx: Context, role: String, family: String, shade: Int, fallback: Int): Int {
        val r = ctx.resources
        if (Build.VERSION.SDK_INT >= 34) {
            val id = r.getIdentifier("system_$role", "color", "android")
            if (id != 0) return runCatching { ctx.getColor(id) }.getOrDefault(fallback)
        }
        if (Build.VERSION.SDK_INT >= 31) {
            val id = r.getIdentifier("system_${family}_$shade", "color", "android")
            if (id != 0) return runCatching { ctx.getColor(id) }.getOrDefault(fallback)
        }
        return fallback
    }

    fun palette(ctx: Context, s: Style, systemDark: Boolean = systemDark(ctx)): Palette {
        val dark = when (s.theme) { ThemeMode.AUTO -> systemDark; ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
        val m = if (dark) "dark" else "light"
        fun c(role: String, fam: String, dShade: Int, lShade: Int, dFb: Int, lFb: Int) =
            sys(ctx, "${role}_$m", fam, if (dark) dShade else lShade, if (dark) dFb else lFb)

        val onSurface = c("on_surface", "neutral1", 100, 900, 0xFFE4E2E6.toInt(), 0xFF1B1B1F.toInt())
        val onVariant = c("on_surface_variant", "neutral2", 200, 700, 0xFFC6C5D0.toInt(), 0xFF46464F.toInt())
        val primary = c("primary", "accent1", 200, 600, 0xFFB8C4FF.toInt(), 0xFF4A5BB0.toInt())
        return when (s.background) {
            Background.SURFACE -> {
                val container = c("surface_container", "neutral1", 900, 50, 0xFF1F1F23.toInt(), 0xFFF1EFF4.toInt())
                // Dark: about halfway from the container tone to black, still tinted by the wallpaper.
                val bg = if (dark) mix(container, Color.BLACK, 0.5f) else container
                Palette(bg, onSurface, onVariant, primary)
            }
            Background.TINTED -> {
                val bg = c("secondary_container", "accent2", 700, 100, 0xFF3A4370.toInt(), 0xFFDDE1FF.toInt())
                val on = c("on_secondary_container", "accent2", 100, 900, 0xFFDDE1FF.toInt(), 0xFF101A4A.toInt())
                Palette(if (dark) mix(bg, Color.BLACK, 0.25f) else bg, on, withAlpha(on, 0.75f), on)
            }
            Background.NONE -> {
                val fg = if (dark) Color.WHITE else 0xFF111114.toInt()
                Palette(Color.TRANSPARENT, fg, withAlpha(fg, 0.8f), fg)
            }
        }
    }

    private fun withAlpha(c: Int, a: Float): Int = (c and 0xFFFFFF) or ((Color.alpha(c) * a).toInt().coerceIn(0, 255) shl 24)

    private fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )

    /** The phone's own widget corner radius (Android 12+), which launchers like OxygenOS's also clip to. */
    fun cornerDp(ctx: Context): Float {
        val r = ctx.resources
        val id = r.getIdentifier("system_app_widget_background_radius", "dimen", "android")
        val px = if (id != 0) runCatching { r.getDimension(id) }.getOrDefault(0f) else 0f
        return if (px > 0) px / r.displayMetrics.density else 24f
    }

    // ------------------------------------------------------------------ text

    fun captionText(ctx: Context, s: Style, day: Day?, rules: Rules): String? = when {
        day == null -> "Tap to set up"
        else -> when (s.caption) {
            Caption.NONE -> null
            Caption.WINDOW -> Fmt.window(ctx, day)
            Caption.SHARE -> Fmt.shareText(day, rules)
            Caption.LONGEST -> day.longest?.let { "Longest " + Fmt.dur(it.ms) } ?: "No breaks yet"
        }
    }

    fun description(ctx: Context, day: Day?): String =
        if (day == null) "Hiatus: allow usage access" else "Away today " + Fmt.dur(day.awayMs) + ", " + Fmt.window(ctx, day)

    // ------------------------------------------------------------------ drawing

    /**
     * Draws the widget at [w]×[h] px ([d] px per dp). [day] null = usage access not granted yet.
     * Label on top, number and caption at the bottom. What doesn't fit goes: first the caption, then the label.
     */
    fun render(
        ctx: Context, s: Style, w: Int, h: Int, d: Float, day: Day?, rules: Rules,
        systemDark: Boolean = systemDark(ctx),
    ): Bitmap {
        val bmp = Bitmap.createBitmap(max(1, w), max(1, h), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val pal = palette(ctx, s, systemDark)
        val r = cornerDp(ctx) * d
        val clip = Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, Path.Direction.CW) }
        c.save()
        c.clipPath(clip)
        if (Color.alpha(pal.bg) > 0) c.drawColor(pal.bg)
        val shadow = s.background == Background.NONE
        val minSide = min(w, h).toFloat()

        if (s.shape) drawShape(c, w.toFloat(), minSide, pal.accent)

        val k = (minSide / (150f * d)).coerceIn(0.8f, 1.25f)     // text grows a little with the widget
        val pad = min(16f * d, minSide * 0.13f)
        val numP = paint(Fonts.typeface(ctx, s.numberFont), 100f, pal.number, shadow).apply { letterSpacing = -0.02f }
        val unitP = paint(Fonts.typeface(ctx, s.numberFont), 50f, pal.number, shadow)
        val labelP = paint(Fonts.typeface(ctx, s.textFont), 13.5f * d * k, pal.text, shadow)
        val capP = paint(Fonts.typeface(ctx, s.textFont), 12.5f * d * k, pal.text, shadow)

        val pieces = if (day == null) listOf("–" to false) else Fmt.parts(day.awayMs, s.format)
        fun numWidth() = pieces.sumOf { (t, unit) -> (if (unit) unitP else numP).measureText(t).toDouble() }.toFloat()
        val w100 = max(1f, numWidth())
        val b = Rect(); numP.getTextBounds("0", 0, 1, b)
        val cap100 = max(1, b.height()).toFloat()                 // digit height at size 100

        val left = pad; val top = pad; val bottom = h - pad
        val innerW = w - 2 * pad; val innerH = bottom - top
        fun lineH(p: Paint) = p.fontMetrics.let { it.descent - it.ascent }

        var label = if (s.showLabel && s.label.isNotBlank()) s.label else null
        var caption = captionText(ctx, s, day, rules)
        val gapL = labelP.textSize * 0.5f
        val gapC = capP.textSize * 0.45f
        fun fit(): Float {
            var avail = innerH
            if (label != null) avail -= lineH(labelP) + gapL
            if (caption != null) avail -= lineH(capP) + gapC
            return max(0f, min(avail / cap100 * 100f, innerW / w100 * 100f))
        }
        var size = fit()
        // Small widgets: room for the number first. Drop the caption, then the label.
        if (caption != null && size < labelP.textSize * 2.1f) { caption = null; size = fit() }
        if (label != null && size < labelP.textSize * 1.7f) { label = null; size = fit() }
        size *= s.numberScale.coerceIn(40, 100) / 100f
        numP.textSize = size
        unitP.textSize = size * 0.5f
        val numW = numWidth()

        fun x(lineW: Float) = if (s.hAlign == HAlign.CENTER) left + (innerW - lineW) / 2f else left
        label?.let {
            val t = TextUtils.ellipsize(it, labelP, innerW, TextUtils.TruncateAt.END).toString()
            c.drawText(t, x(labelP.measureText(t)), top - labelP.fontMetrics.ascent, labelP)
        }
        val capH = if (caption != null) lineH(capP) + gapC else 0f
        val baseline = bottom - capH
        var nx = x(numW)
        for ((t, unit) in pieces) {
            val p = if (unit) unitP else numP
            c.drawText(t, nx, baseline, p)
            nx += p.measureText(t)
        }
        caption?.let {
            val t = TextUtils.ellipsize(it, capP, innerW, TextUtils.TruncateAt.END).toString()
            c.drawText(t, x(capP.measureText(t)), baseline + gapC - capP.fontMetrics.ascent, capP)
        }
        c.restore()
        return bmp
    }

    private fun paint(face: Typeface, size: Float, color: Int, shadow: Boolean) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face
        textSize = size
        this.color = color
        isSubpixelText = true
        if (shadow) setShadowLayer(size * 0.12f, 0f, size * 0.04f, 0x66000000)
    }

    /**
     * Purely decorative: a soft scalloped shape (like Material's "cookie") sitting off the top-right corner,
     * mostly outside the widget and very faint, with a small dot drifting below it.
     */
    private fun drawShape(c: Canvas, w: Float, minSide: Float, color: Int) {
        val radius = minSide * 0.42f
        val cx = w - radius * 0.28f
        val cy = radius * 0.22f
        val path = Path()
        val steps = 180
        for (i in 0..steps) {
            val a = i.toDouble() / steps * 2 * Math.PI
            val rr = radius * (1f + 0.07f * cos(9 * a).toFloat())
            val px = cx + rr * cos(a).toFloat()
            val py = cy + rr * sin(a).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = withAlpha(color, 0.10f) })
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = withAlpha(color, 0.24f) }
        c.drawCircle(cx - radius * 1.14f, cy + radius * 1.0f, max(2f, minSide * 0.022f), dot)
    }
}
