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
                // Dark: a quarter of the way from the container tone to black, still tinted by the wallpaper.
                val bg = if (dark) mix(container, Color.BLACK, 0.25f) else container
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

    /** The number as (text, isUnit) pieces split over two lines ("19h" / "35m"), or null if it can't split. */
    private fun twoLines(p: List<Pair<String, Boolean>>): Pair<List<Pair<String, Boolean>>, List<Pair<String, Boolean>>>? {
        val i = p.indexOfFirst { it.second }
        if (i < 0 || i == p.lastIndex) return null
        val a = p.subList(0, i + 1).map { it.first.trim() to it.second }
        val b = p.subList(i + 1, p.size).filter { it.first.isNotBlank() }.mapIndexed { j, x -> (if (j == 0) x.first.trimStart() else x.first) to x.second }
        return if (b.isEmpty()) null else a to b
    }

    /**
     * Draws the widget at [w]×[h] px ([d] px per dp). [day] null = usage access not granted yet.
     * The layout follows the size, so every size looks deliberate:
     * - 1 × 1: just the number, centred, on two lines when that makes it bigger.
     * - wide and short (4 × 1): the number on the left, label and caption stacked on the right.
     * - everything else: label on top (with the clock, if on), number and caption at the bottom; tall widgets
     *   split the number over two lines; big ones (4 × 2 and up) add a hairline strip of the day.
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
        val wd = w / d
        val hd = h / d
        val minSide = min(w, h).toFloat()
        val k = (minSide / (150f * d)).coerceIn(0.85f, 1.2f)
        val pad = min(16f * d, minSide * 0.14f)
        val left = pad; val top = pad; val right = w - pad; val bottom = h - pad
        val innerW = right - left; val innerH = bottom - top

        val numFace = Fonts.typeface(ctx, s.numberFont)
        val numP = paint(numFace, 100f, pal.number, shadow).apply { letterSpacing = -0.02f }
        val unitP = paint(numFace, 50f, pal.number, shadow)
        val labelP = paint(Fonts.typeface(ctx, s.labelFont), 13.5f * d * k * s.labelScale / 100f, pal.text, shadow)
        val capP = paint(Fonts.typeface(ctx, s.captionFont), 12.5f * d * k * s.captionScale / 100f, pal.text, shadow)
        val pieces = if (day == null) listOf("–" to false) else Fmt.parts(day.awayMs, s.format)
        val split = twoLines(pieces)

        fun width(ps: List<Pair<String, Boolean>>, size: Float): Float {
            numP.textSize = size; unitP.textSize = size * 0.5f
            return ps.sumOf { (t, u) -> (if (u) unitP else numP).measureText(t).toDouble() }.toFloat()
        }
        val digitH = Rect().also { numP.textSize = 100f; numP.getTextBounds("0", 0, 1, it) }.height().coerceAtLeast(1) / 100f
        val lineGap = 0.2f // between the two number lines, in digit heights

        /** Biggest number size for a box, one line or two. */
        fun fit(boxW: Float, boxH: Float, two: Boolean): Float {
            if (boxW <= 0 || boxH <= 0) return 0f
            return if (!two || split == null) min(boxW / max(1f, width(pieces, 100f)) * 100f, boxH / digitH)
            else min(boxW / max(1f, max(width(split.first, 100f), width(split.second, 100f))) * 100f, boxH / (digitH * (2 + lineGap)))
        }
        fun drawPieces(ps: List<Pair<String, Boolean>>, x0: Float, baseline: Float, size: Float) {
            numP.textSize = size; unitP.textSize = size * 0.5f
            var x = x0
            for ((t, u) in ps) { val p = if (u) unitP else numP; c.drawText(t, x, baseline, p); x += p.measureText(t) }
        }
        /** Draws the number with its top-left at (x, y); returns its height. */
        fun drawNumber(x: Float, y: Float, size: Float, two: Boolean, alignRight: Boolean = false, boxW: Float = 0f, centre: Boolean = false): Float {
            val lines = if (two && split != null) listOf(split.first, split.second) else listOf(pieces)
            val dh = digitH * size
            lines.forEachIndexed { i, ps ->
                val lw = width(ps, size)
                val lx = when { centre -> x + (boxW - lw) / 2f; alignRight -> x + boxW - lw; else -> x }
                drawPieces(ps, lx, y + dh + i * dh * (1 + lineGap), size)
            }
            return dh * lines.size + dh * lineGap * (lines.size - 1)
        }
        fun lineH(p: Paint) = p.fontMetrics.let { it.descent - it.ascent }
        /** Shrinks [p] (to 75% at most) until [t] fits [maxW], then ellipsizes what's left over. */
        fun fitText(t: String, p: TextPaint, maxW: Float): String {
            val full = p.measureText(t)
            if (full > maxW) p.textSize *= max(0.75f, maxW / full)
            return TextUtils.ellipsize(t, p, maxW, TextUtils.TruncateAt.END).toString()
        }

        val label = if (s.showLabel && s.label.isNotBlank()) s.label else null
        val caption = captionText(ctx, s, day, rules)

        when {
            // 1 × 1: only the number.
            wd < 120 && hd < 120 -> {
                val one = fit(innerW, innerH, false)
                val two = fit(innerW, innerH, true)
                val useTwo = two > one * 1.15f
                val size = max(one, two) * s.numberScale.coerceIn(40, 100) / 100f
                val nh = (if (useTwo) digitH * (2 + lineGap) else digitH) * size
                drawNumber(left, top + (innerH - nh) / 2f, size, useTwo, boxW = innerW, centre = true)
            }
            // Wide and short: number left, label and caption stacked on the right.
            hd < 120 && wd >= 250 -> {
                val sideW = innerW * 0.42f
                val lt = label?.let { fitText(it, labelP, sideW) }
                val ct = caption?.let { fitText(it, capP, sideW) }
                val size = fit(innerW - sideW - 12f * d, innerH, false) * s.numberScale.coerceIn(40, 100) / 100f
                val nh = digitH * size
                drawNumber(left, top + (innerH - nh) / 2f, size, false)
                val blockH = (if (lt != null) lineH(labelP) else 0f) + (if (ct != null) lineH(capP) + (if (lt != null) 2f * d else 0f) else 0f)
                var y = top + (innerH - blockH) / 2f
                lt?.let { c.drawText(it, right - labelP.measureText(it), y - labelP.fontMetrics.ascent, labelP); y += lineH(labelP) + 2f * d }
                ct?.let { c.drawText(it, right - capP.measureText(it), y - capP.fontMetrics.ascent, capP) }
            }
            else -> {
                val big = wd >= 250 && hd >= 150
                val tall = hd > wd * 1.25f
                val iconSize = (labelP.textSize * 1.25f).coerceIn(12f * d, 22f * d)
                val gapL = labelP.textSize * 0.5f
                val gapC = capP.textSize * 0.4f
                val stripH = if (big && day != null && day.hasData) 3f * d else 0f
                val stripGap = if (stripH > 0) 12f * d else 0f
                var lt = label
                var ct = caption
                val topRow = { if (lt != null) lineH(labelP) + gapL else if (s.clock) iconSize + gapL else 0f }
                val bottomRows = { (if (ct != null) lineH(capP) + gapC else 0f) + stripH + stripGap }
                var two = false
                fun best(): Float {
                    val boxH = innerH - topRow() - bottomRows()
                    val one = fit(innerW, boxH, false)
                    val t = if (tall) fit(innerW, boxH, true) else 0f
                    two = t > one * 1.15f
                    return max(one, t)
                }
                var size = best()
                // Short widgets (2 × 1): room for the number first; drop the caption, then the label.
                if (ct != null && size < labelP.textSize * 2.1f) { ct = null; size = best() }
                if (lt != null && size < labelP.textSize * 1.7f) { lt = null; size = best() }
                size *= s.numberScale.coerceIn(40, 100) / 100f

                // Top line: label, and the clock at the far end.
                lt?.let {
                    val t = fitText(it, labelP, innerW - if (s.clock) iconSize + 8f * d else 0f)
                    c.drawText(t, left, top - labelP.fontMetrics.ascent, labelP)
                }
                if (s.clock) {
                    val cy = if (lt != null) top + lineH(labelP) / 2f else top + iconSize / 2f
                    drawClock(c, right - iconSize / 2f, cy, iconSize, pal.text, d)
                }
                // Bottom: number, caption, strip.
                var y = bottom
                if (stripH > 0 && day != null) {
                    drawStrip(c, day, left, y - stripH, innerW, stripH, pal)
                    y -= stripH + stripGap
                }
                ct?.let {
                    val t = fitText(it, capP, innerW)
                    y -= lineH(capP)
                    c.drawText(t, left, y - capP.fontMetrics.ascent, capP)
                    y -= gapC
                }
                val nh = (if (two) digitH * (2 + lineGap) else digitH) * size
                drawNumber(left, y - nh, size, two)
            }
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

    /** A thin line clock, hands at ten past ten. Faint: it's decoration, not information. */
    private fun drawClock(c: Canvas, cx: Float, cy: Float, size: Float, color: Int, d: Float) {
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = withAlpha(color, 0.6f)
            style = Paint.Style.STROKE
            strokeWidth = max(1f * d, size * 0.07f)
            strokeCap = Paint.Cap.ROUND
        }
        val r = size / 2f - stroke.strokeWidth / 2f
        c.drawCircle(cx, cy, r, stroke)
        fun hand(deg: Double, len: Float) {
            val a = Math.toRadians(deg - 90)
            c.drawLine(cx, cy, cx + len * cos(a).toFloat(), cy + len * sin(a).toFloat(), stroke)
        }
        hand(305.0, r * 0.45f) // hour hand, just past 10
        hand(60.0, r * 0.68f)  // minute hand at 10 past
    }

    /** The day in one hairline: away in the number's colour, the rest faint. */
    private fun drawStrip(c: Canvas, day: Day, x: Float, y: Float, w: Float, h: Float, pal: Palette) {
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(pal.text, 0.18f) }
        val on = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(pal.number, 0.85f) }
        val rr = h / 2f
        c.drawRoundRect(RectF(x, y, x + w, y + h), rr, rr, track)
        val span = (day.end - day.start).toFloat()
        c.save()
        c.clipRect(x, y, x + w * ((day.until - day.start) / span), y + h)
        for (b in day.breaks) {
            val a = x + w * ((b.start - day.start) / span)
            val e = x + w * ((b.end - day.start) / span)
            c.drawRoundRect(RectF(a, y, max(e, a + h), y + h), rr, rr, on)
        }
        c.restore()
    }
}
