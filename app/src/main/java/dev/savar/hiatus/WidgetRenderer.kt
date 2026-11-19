package dev.savar.hiatus

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.TextPaint
import android.text.TextUtils
import java.util.Calendar
import kotlin.math.min

/**
 * Draws the whole widget into a bitmap. One widget, three layouts by size:
 * compact (away time only) → wide (+ stats) → tall (+ hourly bars and top apps).
 */
object WidgetRenderer {

    class Palette(val bg: Int, val fg: Int, val fg2: Int, val accent: Int, val track: Int)

    fun palette(ctx: Context): Palette {
        val dark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            fun c(id: Int) = ctx.getColor(id)
            return if (dark) Palette(
                bg = c(android.R.color.system_neutral1_900),
                fg = c(android.R.color.system_neutral1_50),
                fg2 = c(android.R.color.system_neutral2_200),
                accent = c(android.R.color.system_accent1_200),
                track = c(android.R.color.system_accent2_800),
            ) else Palette(
                bg = c(android.R.color.system_neutral1_50),
                fg = c(android.R.color.system_neutral1_900),
                fg2 = c(android.R.color.system_neutral2_700),
                accent = c(android.R.color.system_accent1_600),
                track = c(android.R.color.system_accent2_100),
            )
        }
        return if (dark) Palette(0xFF1B1B1F.toInt(), 0xFFE4E2E6.toInt(), 0xFFC6C5D0.toInt(), 0xFFB8C4FF.toInt(), 0xFF2F3550.toInt())
        else Palette(0xFFF3F3FA.toInt(), 0xFF1B1B1F.toInt(), 0xFF45464F.toInt(), 0xFF4A5C92.toInt(), 0xFFDCE1F9.toInt())
    }

    private fun alpha(c: Int, a: Float) = Color.argb((Color.alpha(c) * a).toInt(), Color.red(c), Color.green(c), Color.blue(c))

    private val medium: Typeface = Typeface.create(Typeface.DEFAULT, 500, false)
    private val bold: Typeface = Typeface.create(Typeface.DEFAULT, 600, false)

    /**
     * @param d px per dp (already scaled down if the bitmap was capped)
     * @param report null when usage access is missing
     */
    fun render(ctx: Context, w: Int, h: Int, d: Float, wDp: Int, hDp: Int, report: Report?): Bitmap {
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = palette(ctx)

        val radiusDp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                ctx.resources.getDimension(android.R.dimen.system_app_widget_background_radius) /
                    ctx.resources.displayMetrics.density
            }.getOrDefault(24f)
        } else 24f
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.bg }
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radiusDp * d, radiusDp * d, bgPaint)

        val pad = (if (min(wDp, hDp) < 120) 14f else 18f) * d
        val area = RectF(pad, pad, w - pad, h - pad)

        if (report == null) {
            hero(c, area, d, p, "--", "Tap to allow usage access", null, wrap = false)
            return bmp
        }

        val away = report.away
        val today = report.today
        val value = away?.let { Fmt.dur(it.ms) } ?: "0m"
        val caption = when {
            away == null -> "No break yet"
            away.ongoing -> "since " + Fmt.time(ctx, away.start)
            else -> Fmt.time(ctx, away.start) + " – " + Fmt.time(ctx, away.end)
        }
        val stats = listOf(
            Fmt.dur(today.screenTimeMs) to "Screen time",
            today.unlocks.toString() to "Unlocks",
            Fmt.dur(today.avgSessionMs) to "Per session",
        )

        val tall = hDp >= 230 && wDp >= 230
        val wide = wDp >= 250 && hDp >= 90

        when {
            tall -> {
                val gap = 14f * d
                val topH = area.height() * 0.42f
                val top = RectF(area.left, area.top, area.right, area.top + topH)
                if (wDp >= 300) {
                    val split = top.left + top.width() * 0.56f
                    hero(c, RectF(top.left, top.top, split - gap / 2, top.bottom), d, p, value, caption,
                        "Today " + Fmt.dur(today.awayMs), wrap = true)
                    statPanel(c, RectF(split + gap / 2, top.top, top.right, top.bottom), d, p, stats)
                } else {
                    hero(c, top, d, p, value, caption, null, wrap = true)
                }
                val rest = RectF(area.left, top.bottom + gap, area.right, area.bottom)
                val chartH = rest.height() * 0.5f
                hourly(c, RectF(rest.left, rest.top, rest.right, rest.top + chartH), d, p, today, report.now)
                topApps(ctx, c, RectF(rest.left, rest.top + chartH + gap, rest.right, rest.bottom), d, p, today)
            }
            wide -> {
                val gap = 14f * d
                val split = area.left + area.width() * 0.56f
                hero(c, RectF(area.left, area.top, split - gap / 2, area.bottom), d, p, value, caption, null, wrap = hDp >= 150)
                statPanel(c, RectF(split + gap / 2, area.top, area.right, area.bottom), d, p, stats)
            }
            else -> hero(c, area, d, p, value, caption, null, wrap = hDp > wDp * 0.6f)
        }
        return bmp
    }

    private fun text(size: Float, color: Int, tf: Typeface = medium) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color; typeface = tf
    }

    private fun drawFitted(c: Canvas, s: String, x: Float, baseline: Float, maxW: Float, tp: TextPaint) {
        val t = TextUtils.ellipsize(s, tp, maxW, TextUtils.TruncateAt.END).toString()
        c.drawText(t, x, baseline, tp)
    }

    /** "AWAY" label, the dot-matrix duration, and captions under it. */
    private fun hero(c: Canvas, r: RectF, d: Float, p: Palette, value: String, caption: String, caption2: String?, wrap: Boolean) {
        val small = min(12f * d, r.height() * 0.13f).coerceAtLeast(8f * d)
        val label = text(small, p.fg2).apply { letterSpacing = 0.14f }
        c.drawText("AWAY", r.left, r.top + small, label)

        val cap = text(small, p.fg2)
        val captions = listOfNotNull(caption2, caption)
        val capH = captions.size * small * 1.35f
        var cy = r.bottom - capH + small
        for (s in captions) {
            drawFitted(c, s, r.left, cy, r.width(), cap); cy += small * 1.35f
        }

        val dotsTop = r.top + small * 1.9f
        val dotsBottom = r.bottom - capH - small * 0.6f
        val f = DotMatrix.fit(value, r.width(), (dotsBottom - dotsTop).coerceAtLeast(1f), wrap, maxPitch = 9f * d)
        val y = dotsTop + ((dotsBottom - dotsTop) - f.height) / 2
        DotMatrix.draw(c, f, r.left, y, p.accent, p.fg, alpha(p.fg, 0.08f))
    }

    /** Value-over-label rows inside a tonal container. */
    private fun statPanel(c: Canvas, r: RectF, d: Float, p: Palette, rows: List<Pair<String, String>>) {
        val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(p.track, 0.7f) }
        c.drawRoundRect(r, 16f * d, 16f * d, box)
        val inset = 12f * d
        val inner = RectF(r.left + inset, r.top + inset * 0.6f, r.right - inset, r.bottom - inset * 0.6f)
        val rowH = inner.height() / rows.size
        val vSize = min(18f * d, rowH * 0.42f)
        val lSize = min(11f * d, rowH * 0.26f)
        val vp = text(vSize, p.fg, bold)
        val lp = text(lSize, p.fg2)
        rows.forEachIndexed { i, (v, l) ->
            val top = inner.top + i * rowH
            val block = vSize + lSize * 1.3f
            val y0 = top + (rowH - block) / 2
            drawFitted(c, v, inner.left, y0 + vSize * 0.9f, inner.width(), vp)
            drawFitted(c, l, inner.left, y0 + vSize + lSize * 1.15f, inner.width(), lp)
        }
    }

    /** 24 rounded bars of screen time per hour; the current hour in the accent colour. */
    private fun hourly(c: Canvas, r: RectF, d: Float, p: Palette, day: DayStats, now: Long) {
        val small = min(11f * d, r.height() * 0.14f)
        val head = text(small, p.fg2).apply { letterSpacing = 0.1f }
        c.drawText("SCREEN TIME", r.left, r.top + small, head)
        val tot = text(small, p.fg, bold)
        val total = Fmt.dur(day.screenTimeMs)
        c.drawText(total, r.right - tot.measureText(total), r.top + small, tot)

        val axisH = small * 1.5f
        val top = r.top + small * 1.8f
        val bottom = r.bottom - axisH
        val unit = r.width() / (24 + 23 * 0.45f)
        val barW = unit
        val gap = unit * 0.45f
        val nowHour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        val bar = Paint(Paint.ANTI_ALIAS_FLAG)
        val maxH = bottom - top
        for (hr in 0 until 24) {
            val x = r.left + hr * (barW + gap)
            val frac = (day.hourly[hr] / 3_600_000f).coerceIn(0f, 1f)
            val bh = (maxH * frac).coerceAtLeast(barW)
            bar.color = when {
                hr == nowHour -> p.accent
                hr > nowHour -> alpha(p.track, 0.5f)
                frac > 0f -> alpha(p.accent, 0.55f)
                else -> p.track
            }
            c.drawRoundRect(RectF(x, bottom - bh, x + barW, bottom), barW / 2, barW / 2, bar)
        }
        val ax = text(small * 0.9f, p.fg2)
        listOf(0 to "12a", 6 to "6a", 12 to "12p", 18 to "6p").forEach { (hr, s) ->
            c.drawText(s, r.left + hr * (barW + gap), r.bottom - small * 0.15f, ax)
        }
    }

    /** Up to three apps: icon, name, time. */
    private fun topApps(ctx: Context, c: Canvas, r: RectF, d: Float, p: Palette, day: DayStats) {
        val apps = day.apps.take(3)
        if (apps.isEmpty()) return
        val colW = r.width() / 3
        val icon = min(min(28f * d, r.height() * 0.42f), colW * 0.4f)
        val nameSize = min(11f * d, r.height() * 0.17f)
        val np = text(nameSize, p.fg)
        val tp = text(nameSize, p.fg2)
        val pm = ctx.packageManager
        apps.forEachIndexed { i, a ->
            val x = r.left + i * colW
            val y = r.top + (r.height() - icon - nameSize * 2.8f) / 2
            runCatching {
                val dr = pm.getApplicationIcon(a.pkg)
                dr.setBounds(x.toInt(), y.toInt(), (x + icon).toInt(), (y + icon).toInt())
                dr.draw(c)
            }
            val ty = y + icon + nameSize * 1.25f
            drawFitted(c, Usage.label(ctx, a.pkg), x, ty, colW - 8f * d, np)
            drawFitted(c, Fmt.dur(a.ms), x, ty + nameSize * 1.3f, colW - 8f * d, tp)
        }
    }

    fun description(ctx: Context, report: Report?): String {
        if (report == null) return "Hiatus. Tap to allow usage access."
        val a = report.away
        return "Away " + (a?.let { Fmt.dur(it.ms) } ?: "0m") + ". Screen time today " +
            Fmt.dur(report.today.screenTimeMs) + ", " + report.today.unlocks + " unlocks."
    }
}
