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

    private fun alpha(c: Int, a: Float) =
        Color.argb((Color.alpha(c) * a).roundToInt().coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))

    fun palette(ctx: Context, cfg: WidgetConfig): Palette {
        val dark = isDark(ctx, cfg)
        val wallpaper = cfg.useWallpaperColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val p = if (!wallpaper) {
            val (bg, num, txt) = if (cfg.useWallpaperColors) {
                // Wallpaper colours asked for, but the phone is older than Android 12.
                if (dark) Triple(0xFF1B1D22.toInt(), 0xFFB8C4FF.toInt(), 0xFFE4E2E6.toInt())
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
                cfg.role == ColorRole.SURFACE && dark -> Palette(
                    s("neutral1", 900), s("accent1", 200), s("neutral1", 100), s("neutral2", 200), s("accent2", 700), s("neutral1", 800),
                )
                cfg.role == ColorRole.SURFACE -> Palette(
                    s("neutral1", 50), s("accent1", 600), s("neutral1", 900), s("neutral2", 700), s("accent2", 100), s("neutral2", 100),
                )
                dark -> Palette(s(acc, 800), s(acc, 100), s(acc, 50), s(acc, 200), s(acc, 600), s(acc, 700))
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

    /** Draws [text] fitted inside [box] (as big as possible, × sizePercent). Returns the digit height used. */
    private fun bigNumber(
        c: Canvas, text: String, box: RectF, cfg: WidgetConfig, f: Fonts, color: Int,
        center: Boolean, maxSize: Float = Float.MAX_VALUE, end: Boolean = false,
    ): Float {
        if (box.width() <= 0 || box.height() <= 0) return 0f
        val r = rich(text, cfg)
        val cap = capRatio(f.number)
        val byH = box.height() / cap
        val byW = box.width() / max(1f, richWidth(r, 1f, f.number))
        val size = min(min(byH, byW), maxSize) * (cfg.sizePercent / 100f).coerceIn(0.4f, 1.3f)
        val fitted = min(size, byW) // never overflow sideways
        val w = richWidth(r, fitted, f.number)
        val x = when {
            end -> box.right - w
            center -> box.centerX() - w / 2
            else -> box.left
        }
        val baseline = box.centerY() + fitted * cap / 2
        drawRich(c, r, x, baseline, fitted, color, f.number)
        return fitted * cap
    }

    private fun line(c: Canvas, s: String, box: RectF, baseline: Float, p: TextPaint, center: Boolean) {
        val t = TextUtils.ellipsize(s, p, box.width(), TextUtils.TruncateAt.END).toString()
        val x = if (center) box.centerX() - p.measureText(t) / 2 else box.left
        c.drawText(t, x, baseline, p)
    }

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

        val pad = (if (min(wDp, hDp) < 130) 14f else 18f) * d
        val area = RectF(pad, pad, w - pad, h - pad)
        val center = cfg.align == HAlign.CENTER

        if (report == null) {
            hero(ctx, c, area, d, cfg, p, f, "—", cfg.label(), "Tap to allow usage access", center)
            return bmp
        }

        val value = Metrics.value(cfg.metric, report, cfg.format)
        val caption = Metrics.caption(ctx, cfg.metric, report, cfg.format)

        when (cfg.style) {
            WidgetStyle.HERO -> hero(ctx, c, area, d, cfg, p, f, value, cfg.label(), caption, center)
            WidgetStyle.RING -> ring(ctx, c, area, d, cfg, p, f, report, value, caption, center)
            WidgetStyle.SHAPE -> shape(c, area, d, cfg, p, f, value, center)
            WidgetStyle.TILES -> tiles(c, area, d, cfg, p, f, report, cfg.stats)
            WidgetStyle.CHART -> chart(c, area, d, cfg, p, f, report)
            WidgetStyle.APPS -> apps(ctx, c, area, d, cfg, p, f, report, header = hDp > 120)
            WidgetStyle.DASHBOARD -> dashboard(ctx, c, area, d, cfg, p, f, report, value, caption, wDp, hDp, center)
        }
        return bmp
    }

    // ---------------------------------------------------------------- pieces

    /** Label, big number, caption. Drops the caption, then the label, as space runs out. */
    private fun hero(
        ctx: Context, c: Canvas, r: RectF, d: Float, cfg: WidgetConfig, p: Palette, f: Fonts,
        value: String, label: String, caption: String, center: Boolean,
    ) {
        val small = (r.height() * 0.12f).coerceIn(10f * d, 15f * d)
        val lp = paint(small, p.sub, f.label).apply { if (cfg.uppercaseLabel) letterSpacing = 0.08f }
        val cp = paint(small * 0.95f, p.sub, f.body)
        val showLabel = cfg.showLabel && r.height() > 56 * d
        val showCaption = cfg.showCaption && r.height() > 88 * d
        var top = r.top
        var bottom = r.bottom
        if (showLabel) {
            line(c, label, r, r.top + small, lp, center)
            top += small * 1.7f
        }
        if (showCaption) {
            line(c, caption, r, r.bottom - small * 0.15f, cp, center)
            bottom -= small * 1.6f
        }
        bigNumber(c, value, RectF(r.left, top, r.right, bottom), cfg, f, p.number, center)
    }

    private fun ring(
        ctx: Context, c: Canvas, r: RectF, d: Float, cfg: WidgetConfig, p: Palette, f: Fonts,
        report: Report, value: String, caption: String, center: Boolean,
    ) {
        val goal = cfg.goalMinutes.coerceAtLeast(1) * 60_000f
        val frac = (report.today.screenTimeMs / goal).coerceIn(0f, 1f)
        val wide = r.width() > r.height() * 1.5f
        val diam = if (wide) r.height() else min(r.width(), r.height())
        val box = if (wide) RectF(r.left, r.top, r.left + diam, r.bottom)
        else RectF(r.centerX() - diam / 2, r.centerY() - diam / 2, r.centerX() + diam / 2, r.centerY() + diam / 2)
        val stroke = diam * 0.085f
        drawRing(c, box, stroke, frac, p.number, p.track, cfg.wavy)

        val inner = diam - stroke * 2
        if (wide) {
            // Ring holds screen time vs the goal; the chosen metric sits beside it.
            val tb = RectF(box.left + stroke * 1.9f, box.top, box.right - stroke * 1.9f, box.bottom)
            val pct = (report.today.screenTimeMs * 100 / goal).roundToInt()
            val sp = paint(inner * 0.13f, p.sub, f.label)
            bigNumber(c, "$pct%", RectF(tb.left, tb.centerY() - inner * 0.2f, tb.right, tb.centerY() + inner * 0.08f), cfg, f, p.number, true)
            line(c, "of " + Fmt.dur(goal.toLong(), cfg.format), tb, tb.centerY() + inner * 0.27f, sp, true)
            val gap = 16 * d
            hero(ctx, c, RectF(box.right + gap, r.top, r.right, r.bottom), d, cfg, p, f, value, cfg.label(), caption, center)
        } else {
            val tb = RectF(box.centerX() - inner * 0.36f, box.top, box.centerX() + inner * 0.36f, box.bottom)
            val ls = inner * 0.11f
            val lp = paint(ls, p.sub, f.label)
            val numTop = box.centerY() - inner * 0.2f
            val numBottom = box.centerY() + inner * 0.12f
            bigNumber(c, value, RectF(tb.left, numTop, tb.right, numBottom), cfg, f, p.number, true)
            if (cfg.showLabel) line(c, cfg.label(), tb, numTop - ls * 0.6f, lp, true)
            if (cfg.showCaption) line(c, "of " + Fmt.dur(goal.toLong(), cfg.format), tb, numBottom + ls * 1.5f, paint(ls * 0.95f, p.sub, f.body), true)
        }
    }

    /** Material 3 circular progress: track with gaps, indicator flat or wavy (Expressive). */
    private fun drawRing(c: Canvas, box: RectF, stroke: Float, frac: Float, active: Int, track: Int, wavy: Boolean) {
        val rad = box.width() / 2 - stroke
        val cx = box.centerX()
        val cy = box.centerY()
        val pp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND
        }
        val oval = RectF(cx - rad, cy - rad, cx + rad, cy + rad)
        val gapDeg = Math.toDegrees((stroke * 1.5 / rad)).toFloat()
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
            c.drawArc(oval, -90f, max(sweep, 0.5f), false, pp)
            return
        }
        val amp = stroke * 0.32f
        val waves = (2 * PI * rad / (stroke * 3.2f)).roundToInt().coerceAtLeast(6)
        val path = Path()
        val steps = max(8, (sweep * 2).toInt())
        pp.strokeWidth = stroke * 0.8f
        for (i in 0..steps) {
            val a = Math.toRadians((-90.0 + sweep * i / steps))
            val t = (a + PI / 2)
            val rr = rad + amp * sin(t * waves).toFloat()
            val x = cx + rr * cos(a).toFloat()
            val y = cy + rr * sin(a).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        c.drawPath(path, pp)
    }

    private fun shape(c: Canvas, r: RectF, d: Float, cfg: WidgetConfig, p: Palette, f: Fonts, value: String, center: Boolean) {
        val side = min(r.width(), r.height())
        val left = if (center || r.width() - side < 8 * d) r.centerX() - side / 2 else r.left
        val box = RectF(left, r.centerY() - side / 2, left + side, r.centerY() + side / 2)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.track }
        c.drawPath(shapePath(cfg.shape, box), fill)
        val inner = RectF(box.left + side * 0.2f, box.top + side * 0.2f, box.right - side * 0.2f, box.bottom - side * 0.2f)
        val ls = side * 0.075f
        val lp = paint(ls, p.text, f.label).apply { if (cfg.uppercaseLabel) letterSpacing = 0.08f }
        val numBox = RectF(inner.left, inner.centerY() - side * 0.16f, inner.right, inner.centerY() + side * 0.14f)
        bigNumber(c, value, numBox, cfg, f, p.number, true)
        if (cfg.showLabel) line(c, cfg.label(), inner, numBox.top - ls * 0.5f, lp, true)
    }

    private fun tiles(c: Canvas, r: RectF, d: Float, cfg: WidgetConfig, p: Palette, f: Fonts, report: Report, stats: List<Metric>) {
        if (stats.isEmpty()) return
        val n = stats.size
        val gap = 8 * d
        // Pick the grid whose cells are closest to a 1.6:1 card, penalising empty cells.
        var best = 1 to n
        var bestScore = Float.NEGATIVE_INFINITY
        for (cols in 1..n) {
            val rows = ceil(n / cols.toFloat()).toInt()
            val cw = (r.width() - (cols - 1) * gap) / cols
            val ch = (r.height() - (rows - 1) * gap) / rows
            if (cw <= 0 || ch <= 0) continue
            val score = -abs(ln((cw / ch) / 1.6f)) - (cols * rows - n) * 0.35f
            if (score > bestScore) { bestScore = score; best = cols to rows }
        }
        val (cols, rows) = best
        val cw = (r.width() - (cols - 1) * gap) / cols
        val ch = (r.height() - (rows - 1) * gap) / rows
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.tile }
        stats.forEachIndexed { i, m ->
            val col = i % cols
            val row = i / cols
            val cell = RectF(r.left + col * (cw + gap), r.top + row * (ch + gap), r.left + col * (cw + gap) + cw, r.top + row * (ch + gap) + ch)
            val rad = min(18 * d, min(cw, ch) * 0.3f)
            c.drawRoundRect(cell, rad, rad, tp)
            val inPad = min(12 * d, min(cw, ch) * 0.16f)
            val inner = RectF(cell.left + inPad, cell.top + inPad, cell.right - inPad, cell.bottom - inPad)
            val ls = (ch * 0.16f).coerceIn(8 * d, 12 * d)
            val withLabel = ch > 46 * d
            if (withLabel) line(c, m.label, inner, inner.top + ls, paint(ls, p.sub, f.label), false)
            val numTop = if (withLabel) inner.top + ls * 1.6f else inner.top
            bigNumber(
                c, Metrics.value(m, report, cfg.format), RectF(inner.left, numTop, inner.right, inner.bottom),
                cfg, f, p.number, false, maxSize = 30 * d,
            )
        }
    }

    private fun chart(c: Canvas, r: RectF, d: Float, cfg: WidgetConfig, p: Palette, f: Fonts, report: Report) {
        val day = report.today
        val headH = (r.height() * 0.26f).coerceAtMost(40 * d)
        val ls = (headH * 0.36f).coerceIn(10 * d, 14 * d)
        if (cfg.showLabel) {
            val lbl = cfg.customLabel.ifBlank { "Screen time" }.let { if (cfg.uppercaseLabel) it.uppercase() else it }
            line(c, lbl, RectF(r.left, r.top, r.centerX(), r.bottom), r.top + ls, paint(ls, p.sub, f.label), false)
        }
        val total = Fmt.dur(day.screenTimeMs, cfg.format)
        val tw = r.width() * 0.5f
        bigNumber(c, total, RectF(r.right - tw, r.top, r.right, r.top + headH), cfg, f, p.number, false, maxSize = 34 * d, end = true)
        val axis = cfg.showCaption && r.height() > 110 * d
        val axisH = if (axis) 16 * d else 0f
        val top = r.top + headH + 10 * d
        val bottom = r.bottom - axisH
        bars(c, RectF(r.left, top, r.right, bottom), p, day, report.now)
        if (axis) {
            val ap = paint(10.5f * d, p.sub, f.body)
            val unit = r.width() / (24 + 23 * 0.45f)
            listOf(0 to "12 AM", 6 to "6 AM", 12 to "12 PM", 18 to "6 PM").forEach { (hr, s) ->
                c.drawText(s, r.left + hr * unit * 1.45f, r.bottom - 2 * d, ap)
            }
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
                frac > 0f -> alpha(p.number, 0.6f)
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
            val lbl = cfg.customLabel.ifBlank { "Top apps" }.let { if (cfg.uppercaseLabel) it.uppercase() else it }
            if (cfg.showLabel) line(c, lbl, r, r.top + ls, paint(ls, p.sub, f.label), false)
            val total = Fmt.dur(report.today.screenTimeMs, cfg.format)
            val tp = paint(ls, p.number, f.label)
            c.drawText(total, r.right - tp.measureText(total), r.top + ls, tp)
            top += ls * 2f
        }
        if (list.isEmpty()) {
            line(c, "No app use yet", r, top + 14 * d, paint(13 * d, p.sub, f.body), false)
            return
        }
        val avail = r.bottom - top
        val rowH0 = 44 * d
        val rows = min(min(maxRows, list.size), max(1, (avail / rowH0).toInt()))
        val rowH = avail / rows
        val maxMs = list.first().ms.coerceAtLeast(1)
        val pm = ctx.packageManager
        val ip = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until rows) {
            val a = list[i]
            val y = top + i * rowH
            val icon = min(rowH * 0.7f, 34 * d)
            val iy = y + (rowH - icon) / 2
            runCatching {
                val dr = pm.getApplicationIcon(a.pkg)
                dr.setBounds(r.left.toInt(), iy.toInt(), (r.left + icon).toInt(), (iy + icon).toInt())
                dr.draw(c)
            }
            val tx = r.left + icon + 12 * d
            val ts = min(14 * d, rowH * 0.32f)
            val np = paint(ts, p.text, f.label)
            val vp = paint(ts, p.sub, f.body)
            val v = Fmt.dur(a.ms, cfg.format)
            val vw = vp.measureText(v)
            val base = y + rowH / 2 - ts * 0.15f
            line(c, Usage.label(ctx, a.pkg), RectF(tx, y, r.right - vw - 10 * d, y + rowH), base, np, false)
            c.drawText(v, r.right - vw, base, vp)
            val barH = max(3 * d, ts * 0.28f)
            val by = base + ts * 0.55f
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
                val heroH = r.height() * 0.36f
                val top = RectF(r.left, r.top, r.right, r.top + heroH)
                if (wDp >= 300 && side.isNotEmpty()) {
                    val split = top.left + top.width() * 0.52f
                    hero(ctx, c, RectF(top.left, top.top, split - gap / 2, top.bottom), d, cfg, p, f, value, cfg.label(), caption, center)
                    tiles(c, RectF(split + gap / 2, top.top, top.right, top.bottom), d, cfg, p, f, report, side.take(2))
                } else {
                    hero(ctx, c, top, d, cfg, p, f, value, cfg.label(), caption, center)
                }
                val chartTop = top.bottom + gap
                val chartH = (r.bottom - chartTop) * 0.4f
                bars(c, RectF(r.left, chartTop, r.right, chartTop + chartH), p, report.today, report.now)
                apps(ctx, c, RectF(r.left, chartTop + chartH + gap, r.right, r.bottom), d, cfg, p, f, report, header = false, maxRows = 3)
            }
            wDp >= 250 && hDp >= 100 && side.isNotEmpty() -> {
                val split = r.left + r.width() * 0.54f
                hero(ctx, c, RectF(r.left, r.top, split - gap / 2, r.bottom), d, cfg, p, f, value, cfg.label(), caption, center)
                val right = RectF(split + gap / 2, r.top, r.right, r.bottom)
                // One column of stat tiles.
                val n = side.size
                val th = (right.height() - (n - 1) * 6 * d) / n
                side.forEachIndexed { i, m ->
                    val y = right.top + i * (th + 6 * d)
                    tiles(c, RectF(right.left, y, right.right, y + th), d, cfg, p, f, report, listOf(m))
                }
            }
            else -> hero(ctx, c, r, d, cfg, p, f, value, cfg.label(), caption, center)
        }
    }

    fun description(cfg: WidgetConfig, report: Report?): String {
        if (report == null) return "Hiatus. Tap to allow usage access."
        return cfg.label() + " " + Metrics.value(cfg.metric, report, cfg.format) + ". Screen time today " +
            Fmt.dur(report.today.screenTimeMs) + ", " + report.today.unlocks + " unlocks."
    }
}
