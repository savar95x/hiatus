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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
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
 * Draws a whole widget into one bitmap, so every style can use any font and Material shape.
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
                    mix(s("neutral1", 900), Color.BLACK, 0.5f), s("accent1", 200), s("neutral1", 100), s("neutral2", 200),
                    s("accent2", 800), s("neutral1", 900),
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

    // ---------------------------------------------------------------- shapes

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    fun polygon(k: ShapeKind): RoundedPolygon = when (k) {
        ShapeKind.COOKIE_9 -> MaterialShapes.Cookie9Sided
        ShapeKind.COOKIE_4 -> MaterialShapes.Cookie4Sided
        ShapeKind.SUNNY -> MaterialShapes.Sunny
        ShapeKind.SOFT_BURST -> MaterialShapes.SoftBurst
        ShapeKind.FLOWER -> MaterialShapes.Flower
        ShapeKind.CLOVER_4 -> MaterialShapes.Clover4Leaf
        ShapeKind.CLOVER_8 -> MaterialShapes.Clover8Leaf
        ShapeKind.PUFFY -> MaterialShapes.Puffy
        ShapeKind.GEM -> MaterialShapes.Gem
        ShapeKind.PENTAGON -> MaterialShapes.Pentagon
        ShapeKind.CIRCLE -> MaterialShapes.Circle
        ShapeKind.SQUARE -> MaterialShapes.Square
    }

    private fun shapePath(k: ShapeKind, r: RectF): Path {
        val path = polygon(k).toPath(Path())
        val b = RectF()
        path.computeBounds(b, true)
        val m = Matrix()
        m.setRectToRect(b, r, Matrix.ScaleToFit.CENTER)
        path.transform(m)
        return path
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

    private fun labelText(cfg: WidgetConfig, fallback: String) =
        cfg.customLabel.ifBlank { fallback }.let { if (cfg.uppercaseLabel) it.uppercase() else it }

    // ---------------------------------------------------------------- entry

    /**
     * @param d px per dp (already scaled down if the bitmap was capped)
     * @param report null when usage access is missing
     */
    fun render(ctx: Context, cfg: WidgetConfig, w: Int, h: Int, d: Float, report: Report?): Bitmap {
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = palette(ctx, cfg)
        val f = fonts(ctx, cfg)
        val wDp = w / d
        val hDp = h / d

        if (Color.alpha(p.bg) > 0) {
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.bg }
            val rad = min(cfg.cornerRadius * d, min(w, h) / 2f)
            c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), rad, rad, bg)
        }

        if (cfg.style == WidgetStyle.AWAY && cfg.decoration && report != null) {
            // A soft Material shape tucked into the top-right corner, a tone off the background.
            val s = min(w, h) * 1.15f
            val box = RectF(w - s * 0.6f, -s * 0.4f, w + s * 0.4f, s * 0.6f)
            val path = shapePath(cfg.shape, box)
            path.transform(Matrix().apply { setRotate(18f, box.centerX(), box.centerY()) })
            val rad = min(cfg.cornerRadius * d, min(w, h) / 2f)
            c.save()
            c.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), rad, rad, Path.Direction.CW) })
            c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.tile })
            c.restore()
        }

        // Material widget padding: 16dp, a little less on the smallest sizes.
        val pad = (if (min(wDp, hDp) < 130) 14f else 16f) * d
        val area = RectF(pad, pad, w - pad, h - pad)
        val center = cfg.align == HAlign.CENTER

        if (report == null) {
            hero(c, area, d, cfg, p, f, "—", cfg.label(), "Tap to allow usage access", center)
            return bmp
        }

        val value = Metrics.value(cfg.metric, report, cfg.format)
        val caption = Metrics.caption(ctx, cfg.metric, report, cfg.format)

        when (cfg.style) {
            WidgetStyle.AWAY -> away(c, area, d, cfg, p, f, report, value, caption, center)
            WidgetStyle.HERO -> hero(c, area, d, cfg, p, f, value, cfg.label(), caption, center)
            WidgetStyle.RING -> ring(c, area, d, cfg, p, f, report, value, caption, center)
            WidgetStyle.SHAPE -> shape(c, area, d, cfg, p, f, report, value, caption)
            WidgetStyle.TILES -> tiles(c, area, d, cfg, p, f, report, cfg.stats)
            WidgetStyle.CHART -> chart(c, area, d, cfg, p, f, report)
            WidgetStyle.APPS -> apps(ctx, c, area, d, cfg, p, f, report, header = hDp > 120)
            WidgetStyle.DASHBOARD -> dashboard(ctx, c, area, d, cfg, p, f, report, value, caption, wDp, hDp, center)
        }
        return bmp
    }

    // ---------------------------------------------------------------- pieces

    /**
     * Label on top; number and caption anchored to the bottom (centred as a group when [center]).
     * Drops the caption, then the label, as space runs out.
     */
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

    /**
     * The signature look: label, the big number, and a day-long timeline underneath where away time is filled
     * and phone use shows as gaps. Caption (share of the day) and the longest break sit below.
     */
    private fun away(
        c: Canvas, r: RectF, d: Float, cfg: WidgetConfig, p: Palette, f: Fonts,
        report: Report, value: String, caption: String, center: Boolean,
    ) {
        val day = report.today
        val small = (min(r.width(), r.height()) * 0.1f).coerceIn(11f * d, 14f * d)
        val showLabel = cfg.showLabel && r.height() > 56 * d
        val showCaption = cfg.showCaption && r.height() > 100 * d
        val barH = (r.height() * 0.075f).coerceIn(6f * d, 12f * d)
        var bottom = r.bottom
        if (showCaption) {
            val base = r.bottom - small * 0.2f
            val cp = paint(small, p.sub, f.body)
            val wide = r.width() > 220 * d && cfg.metric != Metric.LONGEST_AWAY
            if (wide) {
                val right = "Longest " + Fmt.dur(day.longestAwayMs, cfg.format)
                line(c, right, r, base, cp, false, end = true)
                line(c, caption, RectF(r.left, 0f, r.right - cp.measureText(right) - 12 * d, 0f), base, cp, false)
            } else {
                line(c, caption, r, base, cp, center)
            }
            bottom -= small * 1.75f
        }
        val barTop = bottom - barH
        timeline(c, RectF(r.left, barTop, r.right, bottom), d, p, day)

        val top = r.top + if (showLabel) small * 1.9f else 0f
        val numBase = barTop - max(10f * d, small * 0.9f)
        val size = fitSize(value, r.width(), numBase - top, cfg, f, maxCap = min(r.width(), r.height()) * 0.38f)
        numberAt(c, value, r, numBase, size, cfg, f, p.number, center)
        if (showLabel) {
            // A small dot in the accent colour leads the label.
            val lp = paint(small, p.sub, f.label).apply { if (cfg.uppercaseLabel) letterSpacing = 0.08f }
            val label = cfg.label()
            val dot = small * 0.32f
            val lw = lp.measureText(label) + dot * 2 + small * 0.5f
            val x0 = if (center) r.centerX() - lw / 2 else r.left
            c.drawCircle(x0 + dot, r.top + small * 0.62f, dot, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.number })
            line(c, label, RectF(x0 + dot * 2 + small * 0.5f, 0f, r.right, 0f), r.top + small, lp, false)
        }
    }

    /** Midnight to midnight: away so far filled, phone use as gaps, the rest of the day faint. */
    private fun timeline(c: Canvas, b: RectF, d: Float, p: Palette, day: DayStats) {
        val start = day.start
        val span = (Usage.dayStart(start, 1) - start).toFloat().coerceAtLeast(1f)
        fun x(t: Long) = b.left + ((t - start) / span).coerceIn(0f, 1f) * b.width()
        val rad = b.height() / 2
        c.save()
        c.clipPath(Path().apply { addRoundRect(b, rad, rad, Path.Direction.CW) })
        val pt = Paint(Paint.ANTI_ALIAS_FLAG)
        pt.color = alpha(p.track, 0.5f)
        c.drawRect(b, pt)
        pt.color = p.number
        c.drawRect(b.left, b.top, x(day.end), b.bottom, pt)
        pt.color = p.track
        for (iv in day.use) {
            val x0 = x(iv.s)
            c.drawRect(x0, b.top, max(x(iv.e), x0 + 1.5f * d), b.bottom, pt)
        }
        c.restore()
    }

    /** Screen time against the daily goal. Wide widgets show the chosen stat beside the ring. */
    private fun ring(
        c: Canvas, r: RectF, d: Float, cfg: WidgetConfig, p: Palette, f: Fonts,
        report: Report, value: String, caption: String, center: Boolean,
    ) {
        val goalMs = cfg.goalMinutes.coerceAtLeast(1) * 60_000L
        val st = report.today.screenTimeMs
        val frac = (st / goalMs.toFloat()).coerceIn(0f, 1f)
        val wide = r.width() > r.height() * 1.45f
        val diam = if (wide) r.height() else min(r.width(), r.height())
        val box = if (wide) RectF(r.left, r.top, r.left + diam, r.bottom)
        else RectF(r.centerX() - diam / 2, r.centerY() - diam / 2, r.centerX() + diam / 2, r.centerY() + diam / 2)
        val stroke = (diam * 0.06f).coerceIn(5f * d, 14f * d)
        drawRing(c, box, stroke, frac, p.number, p.track, cfg.wavy)

        val inner = diam - stroke * 3.2f
        val tb = RectF(box.centerX() - inner * 0.4f, box.top, box.centerX() + inner * 0.4f, box.bottom)
        val ls = (inner * 0.11f).coerceIn(9f * d, 14f * d)
        val sp = paint(ls, p.sub, f.body)
        val goalText = "of " + Fmt.dur(goalMs, cfg.format)
        if (wide) {
            val pct = (st * 100 / goalMs).toInt()
            val capH = bigNumber(c, "$pct%", RectF(tb.left, box.centerY() - inner * 0.22f, tb.right, box.centerY() + inner * 0.06f), cfg, f, p.number, true)
            line(c, goalText, tb, box.centerY() + inner * 0.06f + ls * 1.6f, sp, true)
            if (capH <= 0) return
            hero(c, RectF(box.right + 18 * d, r.top, r.right, r.bottom), d, cfg, p, f, value, cfg.label(), caption, center)
        } else {
            val showLabel = cfg.showLabel && inner > 110 * d
            val showGoal = cfg.showCaption && inner > 70 * d
            val numH = inner * 0.26f
            val base = box.centerY() + numH / 2 - (if (showGoal) ls * 0.6f else 0f) + (if (showLabel) ls * 0.6f else 0f)
            val size = fitSize(Fmt.dur(st, cfg.format), tb.width(), numH, cfg, f)
            numberAt(c, Fmt.dur(st, cfg.format), tb, base, size, cfg, f, p.number, true)
            if (showLabel) line(c, labelText(cfg, "Screen time"), tb, base - numH - ls * 0.7f, paint(ls, p.sub, f.label), true)
            if (showGoal) line(c, goalText, tb, base + ls * 1.6f, sp, true)
        }
    }

    /** Material 3 circular progress: track with gaps, indicator flat or gently wavy (Expressive). */
    private fun drawRing(c: Canvas, box: RectF, stroke: Float, frac: Float, active: Int, track: Int, wavy: Boolean) {
        val rad = box.width() / 2 - stroke
        val cx = box.centerX()
        val cy = box.centerY()
        val pp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND
        }
        val oval = RectF(cx - rad, cy - rad, cx + rad, cy + rad)
        val gapDeg = Math.toDegrees((stroke * 1.4 / rad)).toFloat()
        val sweep = 360f * frac
        pp.color = track
        when {
            frac <= 0f -> c.drawOval(oval, pp)
            frac < 1f -> {
                val start = -90f + sweep + gapDeg
                val len = 360f - sweep - 2 * gapDeg
                if (len > 0) c.drawArc(oval, start, len, false, pp)
            }
        }
        if (frac <= 0f) return
        pp.color = active
        if (!wavy) {
            if (frac >= 1f) c.drawOval(oval, pp) else c.drawArc(oval, -90f, max(sweep, 0.5f), false, pp)
            return
        }
        val amp = stroke * 0.2f
        val waves = (2 * PI * rad / (stroke * 3f)).roundToInt().coerceAtLeast(10)
        val path = Path()
        val steps = max(16, (sweep * 3).toInt())
        for (i in 0..steps) {
            val a = Math.toRadians((-90.0 + sweep * i / steps))
            val rr = rad + amp * sin((a + PI / 2) * waves).toFloat()
            val x = cx + rr * cos(a).toFloat()
            val y = cy + rr * sin(a).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (frac >= 1f) path.close()
        c.drawPath(path, pp)
    }

    /** The number inside a Material shape. Wide widgets put the label and context beside it. */
    private fun shape(
        c: Canvas, r: RectF, d: Float, cfg: WidgetConfig, p: Palette, f: Fonts,
        report: Report, value: String, caption: String,
    ) {
        val wide = r.width() > r.height() * 1.45f
        val side = if (wide) r.height() else min(r.width(), r.height())
        val left = if (wide) r.left else r.centerX() - side / 2
        val box = RectF(left, r.centerY() - side / 2, left + side, r.centerY() + side / 2)
        c.drawPath(shapePath(cfg.shape, box), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.track })
        val inner = RectF(box.left + side * 0.22f, box.top, box.right - side * 0.22f, box.bottom)
        val showLabel = !wide && cfg.showLabel && side > 200 * d
        val ls = (side * 0.07f).coerceIn(10f * d, 15f * d)
        val numH = side * 0.2f
        val base = box.centerY() + numH / 2 + (if (showLabel) ls * 0.7f else 0f)
        val size = fitSize(value, inner.width(), numH, cfg, f)
        numberAt(c, value, inner, base, size, cfg, f, p.number, true)
        if (showLabel) line(c, cfg.label(), inner, base - numH - ls * 0.8f, paint(ls, p.text, f.label), true)
        if (!wide) return

        // Beside the shape: label, caption, and two quiet facts.
        val col = RectF(box.right + 18 * d, r.top, r.right, r.bottom)
        val t = (col.height() * 0.13f).coerceIn(12f * d, 16f * d)
        val s = t * 0.86f
        val rows = buildList {
            if (cfg.showLabel) add(Triple(cfg.label(), t, paint(t, p.text, f.label)))
            if (cfg.showCaption) add(Triple(caption, s, paint(s, p.sub, f.body)))
            if (cfg.metric != Metric.SCREEN_TIME) add(Triple(Fmt.dur(report.today.screenTimeMs, cfg.format) + " screen time", s, paint(s, p.sub, f.body)))
            if (cfg.metric != Metric.UNLOCKS) add(Triple("${report.today.unlocks} unlocks", s, paint(s, p.sub, f.body)))
        }
        val total = rows.sumOf { (it.second * 1.55f).toDouble() }.toFloat()
        var y = col.centerY() - total / 2
        for ((text, size2, pt) in rows) {
            y += size2 * 1.55f
            line(c, text, col, y - size2 * 0.4f, pt, false)
        }
    }

    /**
     * Stat tiles. Shows as many of [stats] as fit legibly. Short, wide tiles put the label beside the value.
     * [forceCols] pins the column count (dashboard side panel); [minH] is the smallest tile height allowed.
     */
    private fun tiles(
        c: Canvas, r: RectF, d: Float, cfg: WidgetConfig, p: Palette, f: Fonts, report: Report, stats: List<Metric>,
        forceCols: Int? = null, minH: Float = 58 * d,
    ) {
        if (stats.isEmpty()) return
        val gap = 8 * d
        val minW = 92 * d
        var cols = 1
        var rows = 1
        var n = stats.size
        search@ while (n >= 1) {
            var bestScore = Float.NEGATIVE_INFINITY
            var found = false
            for (cc in (forceCols?.let { listOf(it) } ?: (1..n).toList())) {
                val rr = ceil(n / cc.toFloat()).toInt()
                val cw = (r.width() - (cc - 1) * gap) / cc
                val ch = (r.height() - (rr - 1) * gap) / rr
                if (cw < minW || ch < minH) continue
                val score = -abs(ln((cw / ch) / 1.6f)) - (cc * rr - n) * 0.5f
                if (score > bestScore) { bestScore = score; cols = cc; rows = rr; found = true }
            }
            if (found) break@search
            n--
        }
        if (n < 1) { n = 1; cols = 1; rows = 1 }
        val cw = (r.width() - (cols - 1) * gap) / cols
        val ch = (r.height() - (rows - 1) * gap) / rows
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.tile }
        val rowTile = ch < 64 * d && cw > 140 * d
        val tlsBig = (ch * 0.14f).coerceIn(10.5f * d, 13f * d)
        val inPadBig = min(14 * d, min(cw, ch) * 0.16f)
        // Every tile's value at the same size: the largest that fits all of them.
        val shared = (0 until n).minOf { i ->
            val v = Metrics.value(stats[i], report, cfg.format)
            if (rowTile) fitSize(v, (cw - 28 * d) * 0.5f, ch * 0.42f, cfg, f)
            else fitSize(v, cw - 2 * inPadBig, ch - 2 * inPadBig - tlsBig * 1.7f, cfg, f, maxCap = ch * 0.34f)
        }
        for (i in 0 until n) {
            val m = stats[i]
            val col = i % cols
            val row = i / cols
            val x0 = r.left + col * (cw + gap)
            val y0 = r.top + row * (ch + gap)
            val cell = RectF(x0, y0, x0 + cw, y0 + ch)
            val rad = min(16 * d, min(cw, ch) * 0.32f)
            c.drawRoundRect(cell, rad, rad, tp)
            val v = Metrics.value(m, report, cfg.format)
            if (rowTile) {
                // Row tile: label left, value right.
                val inner = RectF(cell.left + 14 * d, cell.top, cell.right - 14 * d, cell.bottom)
                val ls = 12.5f * d
                val size = shared
                val vw = richWidth(rich(v, cfg), size, f.number)
                val lp = paint(ls, p.sub, f.label)
                val room = inner.width() - vw - 8 * d
                val lbl = if (lp.measureText(m.label) <= room) m.label else m.short
                line(c, lbl, RectF(inner.left, 0f, inner.left + room, 0f), cell.centerY() + ls * 0.36f, lp, false)
                numberAt(c, v, inner, cell.centerY() + size * capRatio(f.number) / 2, size, cfg, f, p.number, false, end = true)
            } else {
                val inner = RectF(cell.left + inPadBig, cell.top + inPadBig, cell.right - inPadBig, cell.bottom - inPadBig)
                val lp = paint(tlsBig, p.sub, f.label)
                val lbl = if (lp.measureText(m.label) <= inner.width()) m.label else m.short
                line(c, lbl, inner, inner.top + tlsBig, lp, false)
                numberAt(c, v, inner, inner.bottom, shared, cfg, f, p.number, false)
            }
        }
    }

    private fun chart(c: Canvas, r: RectF, d: Float, cfg: WidgetConfig, p: Palette, f: Fonts, report: Report) {
        val day = report.today
        val narrow = r.width() < 230 * d
        val ls = 13f * d
        val headH = (r.height() * 0.24f).coerceIn(22f * d, 36f * d)
        if (cfg.showLabel && !narrow) line(c, labelText(cfg, "Screen time"), RectF(r.left, 0f, r.centerX(), 0f), r.top + ls, paint(ls, p.sub, f.label), false)
        val total = Fmt.dur(day.screenTimeMs, cfg.format)
        val size = fitSize(total, if (narrow) r.width() else r.width() * 0.5f, headH, cfg, f)
        numberAt(c, total, r, r.top + size * capRatio(f.number), size, cfg, f, p.number, false, end = !narrow)
        val axis = cfg.showCaption && r.height() > 110 * d
        val axisH = if (axis) 16 * d else 0f
        val top = r.top + headH + 12 * d
        bars(c, RectF(r.left, top, r.right, r.bottom - axisH), p, day, report.now)
        if (axis) {
            val ap = paint(10.5f * d, p.sub, f.body)
            val unit = r.width() / (24 + 23 * 0.45f)
            val labels = if (narrow) listOf(0 to "12a", 6 to "6a", 12 to "12p", 18 to "6p")
            else listOf(0 to "12 AM", 6 to "6 AM", 12 to "12 PM", 18 to "6 PM")
            labels.forEach { (hr, s) -> c.drawText(s, r.left + hr * unit * 1.45f, r.bottom - 2 * d, ap) }
        }
    }

    private fun bars(c: Canvas, r: RectF, p: Palette, day: DayStats, now: Long) {
        if (r.height() <= 0) return
        val unit = r.width() / (24 + 23 * 0.45f)
        val gap = unit * 0.45f
        val nowHour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        val bp = Paint(Paint.ANTI_ALIAS_FLAG)
        for (hr in 0 until 24) {
            val x = r.left + hr * (unit + gap)
            val frac = (day.hourly[hr] / 3_600_000f).coerceIn(0f, 1f)
            val bh = (r.height() * frac).coerceAtLeast(unit)
            bp.color = when {
                hr == nowHour -> p.number
                hr > nowHour -> alpha(p.track, 0.5f)
                frac > 0f -> alpha(p.number, 0.55f)
                else -> p.track
            }
            c.drawRoundRect(RectF(x, r.bottom - bh, x + unit, r.bottom), unit / 2, unit / 2, bp)
        }
    }

    private fun apps(
        ctx: Context, c: Canvas, r: RectF, d: Float, cfg: WidgetConfig, p: Palette, f: Fonts,
        report: Report, header: Boolean, maxRows: Int = cfg.appsCount,
    ) {
        val list = report.today.apps
        var top = r.top
        if (header) {
            val ls = 13 * d
            if (cfg.showLabel) line(c, labelText(cfg, "Top apps"), RectF(r.left, 0f, r.centerX(), 0f), r.top + ls, paint(ls, p.sub, f.label), false)
            line(c, Fmt.dur(report.today.screenTimeMs, cfg.format), r, r.top + ls, paint(ls, p.number, f.label), false, end = true)
            top += ls * 2.1f
        }
        if (list.isEmpty()) {
            line(c, "No app use yet", r, top + 14 * d, paint(13 * d, p.sub, f.body), false)
            return
        }
        val narrow = r.width() < 170 * d
        val avail = r.bottom - top
        val rowH0 = if (narrow) 40 * d else 46 * d
        val rows = min(min(maxRows, list.size), max(1, (avail / rowH0).toInt()))
        val rowH = min(avail / rows, 64 * d)
        val maxMs = list.first().ms.coerceAtLeast(1)
        val pm = ctx.packageManager
        val ip = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until rows) {
            val a = list[i]
            val y = top + i * rowH
            val icon = min(rowH * 0.66f, 32 * d)
            val iy = y + (rowH - icon) / 2
            runCatching {
                val dr = pm.getApplicationIcon(a.pkg)
                dr.setBounds(r.left.toInt(), iy.toInt(), (r.left + icon).toInt(), (iy + icon).toInt())
                dr.draw(c)
            }
            val tx = r.left + icon + 12 * d
            val ts = min(13.5f * d, rowH * 0.3f)
            val np = paint(ts, p.text, f.label)
            val vp = paint(ts * 0.92f, p.sub, f.body)
            val v = Fmt.dur(a.ms, cfg.format)
            val name = Usage.label(ctx, a.pkg)
            if (narrow) {
                // Name over time; no bar.
                val mid = y + rowH / 2
                line(c, name, RectF(tx, 0f, r.right, 0f), mid - ts * 0.15f, np, false)
                line(c, v, RectF(tx, 0f, r.right, 0f), mid + ts * 1.05f, vp, false)
                continue
            }
            val vw = vp.measureText(v)
            val base = y + rowH / 2 - ts * 0.1f
            line(c, name, RectF(tx, 0f, r.right - vw - 10 * d, 0f), base, np, false)
            c.drawText(v, r.right - vw, base, vp)
            val barH = 4 * d
            val by = base + ts * 0.6f
            ip.color = p.track
            c.drawRoundRect(RectF(tx, by, r.right, by + barH), barH / 2, barH / 2, ip)
            ip.color = p.number
            val bw = max(barH, (r.right - tx) * a.ms / maxMs.toFloat())
            c.drawRoundRect(RectF(tx, by, tx + bw, by + barH), barH / 2, barH / 2, ip)
        }
    }

    private fun dashboard(
        ctx: Context, c: Canvas, r: RectF, d: Float, cfg: WidgetConfig, p: Palette, f: Fonts,
        report: Report, value: String, caption: String, wDp: Float, hDp: Float, center: Boolean,
    ) {
        val gap = 12 * d
        val side = cfg.stats.take(3)
        when {
            hDp >= 230 && wDp >= 230 -> {
                val heroH = r.height() * 0.34f
                val top = RectF(r.left, r.top, r.right, r.top + heroH)
                if (wDp >= 300 && side.isNotEmpty()) {
                    val split = top.left + top.width() * 0.5f
                    hero(c, RectF(top.left, top.top, split - gap / 2, top.bottom), d, cfg, p, f, value, cfg.label(), caption, center)
                    tiles(c, RectF(split + gap / 2, top.top, top.right, top.bottom), d, cfg, p, f, report, side.take(2), forceCols = 1, minH = 36 * d)
                } else {
                    hero(c, top, d, cfg, p, f, value, cfg.label(), caption, center)
                }
                val chartTop = top.bottom + gap * 1.5f
                val chartH = (r.bottom - chartTop) * 0.36f
                bars(c, RectF(r.left, chartTop, r.right, chartTop + chartH), p, report.today, report.now)
                apps(ctx, c, RectF(r.left, chartTop + chartH + gap, r.right, r.bottom), d, cfg, p, f, report, header = false, maxRows = 3)
            }
            wDp >= 250 && hDp >= 100 && side.isNotEmpty() -> {
                val split = r.left + r.width() * 0.5f
                hero(c, RectF(r.left, r.top, split - gap / 2, r.bottom), d, cfg, p, f, value, cfg.label(), caption, center)
                tiles(c, RectF(split + gap / 2, r.top, r.right, r.bottom), d, cfg, p, f, report, side, forceCols = 1, minH = 36 * d)
            }
            else -> hero(c, r, d, cfg, p, f, value, cfg.label(), caption, center)
        }
    }

    fun description(cfg: WidgetConfig, report: Report?): String {
        if (report == null) return "Hiatus. Tap to allow usage access."
        return cfg.label() + " " + Metrics.value(cfg.metric, report, cfg.format) + ". Screen time today " +
            Fmt.dur(report.today.screenTimeMs) + ", " + report.today.unlocks + " unlocks."
    }
}
