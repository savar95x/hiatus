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
import android.text.TextUtils
import android.text.TextPaint
import android.os.Build
import kotlin.math.max
import kotlin.math.min

/** Colours of one widget: background, the big number, label + caption, the small icon. */
data class Palette(val bg: Int, val number: Int, val text: Int, val accent: Int)

object Renderer {

    // ------------------------------------------------------------------ colours

    fun systemDark(ctx: Context): Boolean =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /**
     * A Material You colour: the named Android 14+ role if the phone has it, else the matching tonal-palette
     * shade (Android 12+), else a neutral fallback.
     */
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

        val surface = c("surface_container", "neutral1", 900, 50, 0xFF1F1F23.toInt(), 0xFFF1EFF4.toInt())
        val onSurface = c("on_surface", "neutral1", 100, 900, 0xFFE4E2E6.toInt(), 0xFF1B1B1F.toInt())
        val onVariant = c("on_surface_variant", "neutral2", 200, 700, 0xFFC6C5D0.toInt(), 0xFF46464F.toInt())
        val primary = c("primary", "accent1", 200, 600, 0xFFB8C4FF.toInt(), 0xFF4A5BB0.toInt())

        fun container(role: String, fam: String): Palette {
            val bg = c("${role}_container", fam, 700, 100, 0xFF3A4370.toInt(), 0xFFDDE1FF.toInt())
            val on = c("on_${role}_container", fam, 100, 900, 0xFFDDE1FF.toInt(), 0xFF101A4A.toInt())
            return Palette(bg, on, withAlpha(on, 0.78f), on)
        }

        val base = when (s.background) {
            Background.SURFACE -> Palette(surface, onSurface, onVariant, primary)
            Background.PRIMARY -> container("primary", "accent1")
            Background.SECONDARY -> container("secondary", "accent2")
            Background.TERTIARY -> container("tertiary", "accent3")
            Background.CUSTOM -> {
                val light = luminance(s.bgColor) > 0.5
                val fg = if (light) 0xFF17171A.toInt() else 0xFFF3F1EE.toInt()
                Palette(s.bgColor, fg, withAlpha(fg, 0.72f), fg)
            }
            Background.NONE -> {
                val fg = if (dark) Color.WHITE else 0xFF111114.toInt()
                Palette(Color.TRANSPARENT, fg, withAlpha(fg, 0.8f), fg)
            }
        }
        val bg = if (s.background == Background.NONE) Color.TRANSPARENT else withAlpha(base.bg, s.bgAlpha / 100f)
        return Palette(bg, s.numberColor ?: base.number, s.textColor ?: base.text, s.iconColor ?: base.accent)
    }

    private fun withAlpha(c: Int, a: Float): Int = (c and 0xFFFFFF) or ((Color.alpha(c) * a).toInt().coerceIn(0, 255) shl 24)

    fun luminance(c: Int): Double {
        fun ch(v: Int): Double { val x = v / 255.0; return if (x <= 0.03928) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch(Color.red(c)) + 0.7152 * ch(Color.green(c)) + 0.0722 * ch(Color.blue(c))
    }

    /** The phone's own widget corner radius (Android 12+), which launchers like OxygenOS's also clip to. */
    fun systemCornerDp(ctx: Context): Float {
        val r = ctx.resources
        val id = r.getIdentifier("system_app_widget_background_radius", "dimen", "android")
        val px = if (id != 0) runCatching { r.getDimension(id) }.getOrDefault(0f) else 0f
        return if (px > 0) px / r.displayMetrics.density else 24f
    }

    fun cornerDp(ctx: Context, s: Style): Float = if (s.corners < 0) systemCornerDp(ctx) else s.corners.toFloat()

    // ------------------------------------------------------------------ text

    fun captionText(s: Style, day: Day?, rules: Rules): String? = when {
        day == null -> "Tap to set up"
        else -> when (s.caption) {
            Caption.NONE -> null
            Caption.SHARE -> Fmt.shareText(day, rules)
            Caption.LONGEST -> day.longest?.let { "Longest ${Fmt.dur(it.ms)}" } ?: "No breaks yet"
            Caption.LAST -> day.lastBreak?.let { if (day.ongoing) "Away for ${Fmt.dur(it.ms)}" else "Last break ${Fmt.dur(it.ms)}" }
                ?: "No breaks yet"
            Caption.BREAKS -> when (val n = day.breaks.size) { 0 -> "No breaks yet"; 1 -> "1 break"; else -> "$n breaks" }
            Caption.CUSTOM -> s.customCaption.ifBlank { null }
        }
    }

    fun description(day: Day?, rules: Rules): String =
        if (day == null) "Hiatus: set up usage access" else "Away today ${Fmt.dur(day.awayMs)}, ${Fmt.shareText(day, rules)}"

    // ------------------------------------------------------------------ drawing

    /**
     * Draws the widget at [w]×[h] px ([d] px per dp). [day] null = usage access not granted yet.
     * Text that doesn't fit is dropped: first the caption, then the label, down to just the number at 1×1.
     */
    fun render(
        ctx: Context, s: Style, w: Int, h: Int, d: Float, day: Day?, rules: Rules,
        systemDark: Boolean = systemDark(ctx),
    ): Bitmap {
        val bmp = Bitmap.createBitmap(max(1, w), max(1, h), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val pal = palette(ctx, s, systemDark)
        val r = cornerDp(ctx, s) * d
        val shape = Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, Path.Direction.CW) }
        c.save()
        c.clipPath(shape)
        if (Color.alpha(pal.bg) > 0) c.drawColor(pal.bg)
        val shadow = s.background == Background.NONE

        val minSide = min(w, h).toFloat()
        val k = (minSide / (150f * d)).coerceIn(0.78f, 1.3f)          // text grows a little with the widget
        val pad = min(s.padding * d, minSide * 0.13f)

        // Behind-the-text icon first.
        if (s.icon != IconKind.NONE && s.iconPlace == IconPlace.BEHIND) {
            val size = 1.15f * h.coerceAtMost((w * 0.9f).toInt())
            drawIcon(c, s.icon, w - size * 0.42f, h - size * 0.4f, size, withAlpha(pal.accent, s.iconAlpha / 100f * 0.7f))
        }

        val numFace = Fonts.typeface(ctx, s.numberFont)
        val textFace = Fonts.typeface(ctx, s.textFont)
        val pieces = if (day == null) listOf("–" to false) else Fmt.parts(day.awayMs, s.format)
        val unitK = if (s.smallUnits) 0.5f else 1f

        val labelP = textPaint(textFace, 13.5f * d * k * s.textScale / 100f, pal.text, shadow)
        val capP = textPaint(textFace, 12.5f * d * k * s.textScale / 100f, pal.text, shadow)
        val numP = textPaint(numFace, 100f, pal.number, shadow).apply { letterSpacing = -0.02f }
        val unitP = textPaint(numFace, 100f * unitK, pal.number, shadow).apply { letterSpacing = -0.01f }

        // Number metrics at size 100: width, and digit height (bounds of "0") for a tight layout.
        fun numWidth(): Float = pieces.sumOf { (t, unit) -> (if (unit) unitP else numP).measureText(t).toDouble() }.toFloat()
        val w100 = max(1f, numWidth())
        val b = Rect(); numP.getTextBounds("0", 0, 1, b)
        val cap100 = max(1, b.height()).toFloat()

        val innerL = pad; val innerR = w - pad; val innerT = pad; val innerB = h - pad
        val innerW = innerR - innerL; val innerH = innerB - innerT

        val iconCorner = s.icon != IconKind.NONE && s.iconPlace == IconPlace.CORNER
        val iconSize = (labelP.textSize * 1.35f).coerceIn(13f * d, 30f * d)

        var label = if (s.showLabel && s.label.isNotBlank()) s.label else null
        var caption = captionText(s, day, rules)
        val gapLabel = labelP.textSize * 0.45f
        val gapCap = capP.textSize * 0.45f

        fun lineH(p: Paint) = p.fontMetrics.let { it.descent - it.ascent }
        fun fitted(): Float {
            var avail = innerH
            if (label != null) avail -= lineH(labelP) + gapLabel
            if (caption != null) avail -= lineH(capP) + gapCap
            val byH = avail / cap100 * 100f
            val byW = innerW / w100 * 100f
            return max(0f, min(byH, byW))
        }
        var fit = fitted()
        // Tiny widgets: make room for the number by dropping the caption, then the label.
        if (caption != null && fit < labelP.textSize * 2.1f) { caption = null; fit = fitted() }
        if (label != null && fit < labelP.textSize * 1.7f) { label = null; fit = fitted() }
        val size = fit * (s.numberScale.coerceIn(30, 100) / 100f)
        numP.textSize = size
        unitP.textSize = size * unitK
        val numW = numWidth()
        val numH = cap100 * size / 100f

        // Vertical layout.
        val labelH = if (label != null) lineH(labelP) else 0f
        val capH = if (caption != null) lineH(capP) else 0f
        val blockH = labelH + (if (label != null) gapLabel else 0f) + numH + (if (caption != null) gapCap + capH else 0f)
        val labelTop: Float
        val numTop: Float
        when (if (s.vAlign == VAlign.SPREAD && label == null) VAlign.BOTTOM else s.vAlign) {
            VAlign.SPREAD -> {
                labelTop = innerT
                numTop = innerB - (if (caption != null) capH + gapCap else 0f) - numH
            }
            VAlign.TOP -> { labelTop = innerT; numTop = innerT + labelH + (if (label != null) gapLabel else 0f) }
            VAlign.CENTER -> {
                labelTop = innerT + (innerH - blockH) / 2f
                numTop = labelTop + labelH + (if (label != null) gapLabel else 0f)
            }
            VAlign.BOTTOM -> {
                labelTop = innerB - blockH
                numTop = labelTop + labelH + (if (label != null) gapLabel else 0f)
            }
        }
        val capTop = numTop + numH + gapCap

        fun x(lineW: Float, reserveEnd: Float = 0f): Float = when (s.hAlign) {
            HAlign.START -> innerL
            HAlign.CENTER -> innerL + (innerW - lineW) / 2f
            HAlign.END -> innerR - lineW
        }.let { if (s.hAlign == HAlign.START) it else min(it, innerR - reserveEnd - lineW) }

        // Corner icon: top end (top start when the text is end-aligned), level with the label.
        var iconReserve = 0f
        if (iconCorner) {
            iconReserve = iconSize + 6f * d
            val cy = if (label != null) labelTop + labelH / 2f else innerT + iconSize / 2f
            val cx = if (s.hAlign == HAlign.END) innerL + iconSize / 2f else innerR - iconSize / 2f
            drawIcon(c, s.icon, cx, cy, iconSize, withAlpha(pal.accent, s.iconAlpha / 100f))
        }

        label?.let {
            val maxW = innerW - (if (iconCorner && label != null) iconReserve else 0f)
            val t = ellipsize(it, labelP, maxW)
            val tw = labelP.measureText(t)
            val lx = when (s.hAlign) {
                HAlign.START -> innerL
                HAlign.CENTER -> innerL + (innerW - tw) / 2f
                HAlign.END -> innerR - tw
            }
            c.drawText(t, lx, labelTop - labelP.fontMetrics.ascent, labelP)
        }

        var nx = x(numW)
        val baseline = numTop + numH
        for ((t, unit) in pieces) {
            val p = if (unit) unitP else numP
            c.drawText(t, nx, baseline, p)
            nx += p.measureText(t)
        }

        caption?.let {
            val t = ellipsize(it, capP, innerW)
            c.drawText(t, x(capP.measureText(t)), capTop - capP.fontMetrics.ascent, capP)
        }
        c.restore()
        return bmp
    }

    private fun textPaint(face: Typeface, size: Float, color: Int, shadow: Boolean) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face
        textSize = size
        this.color = color
        isSubpixelText = true
        if (shadow) setShadowLayer(size * 0.12f, 0f, size * 0.04f, 0x66000000)
    }

    private fun ellipsize(t: String, p: TextPaint, w: Float): String =
        TextUtils.ellipsize(t, p, max(0f, w), TextUtils.TruncateAt.END).toString()

    // ------------------------------------------------------------------ icons

    /** The small marks, drawn on a 24-unit grid centred at ([cx], [cy]) and [size] wide. */
    fun drawIcon(c: Canvas, kind: IconKind, cx: Float, cy: Float, size: Float, color: Int) {
        if (kind == IconKind.NONE) return
        val u = size / 24f
        c.save()
        c.translate(cx - 12f * u, cy - 12f * u)
        c.scale(u, u)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
        when (kind) {
            IconKind.MARK -> {
                // Hiatus's mark: a ring with a gap at the top right, and a dot sitting in the gap.
                val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color; style = Paint.Style.STROKE; strokeWidth = 4f; strokeCap = Paint.Cap.ROUND
                }
                c.drawArc(RectF(3.4f, 3.4f, 20.6f, 20.6f), 0f, 270f, false, stroke)
                val a = Math.toRadians(-45.0)
                c.drawCircle(12f + 8.6f * Math.cos(a).toFloat(), 12f + 8.6f * Math.sin(a).toFloat(), 2f, fill)
            }
            IconKind.MOON -> {
                val outer = Path().apply { addCircle(12f, 12f, 9f, Path.Direction.CW) }
                val bite = Path().apply { addCircle(16.5f, 8.5f, 7.2f, Path.Direction.CW) }
                outer.op(bite, Path.Op.DIFFERENCE)
                c.drawPath(outer, fill)
            }
            IconKind.PAUSE -> {
                c.drawRoundRect(RectF(6f, 4.5f, 10.4f, 19.5f), 2.2f, 2.2f, fill)
                c.drawRoundRect(RectF(13.6f, 4.5f, 18f, 19.5f), 2.2f, 2.2f, fill)
            }
            IconKind.NONE -> {}
        }
        c.restore()
    }
}
