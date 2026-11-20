package dev.savar.hiatus

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

/** Half-open time interval [s, e) in epoch millis. */
data class Iv(val s: Long, val e: Long) {
    val len: Long get() = e - s
}

/** One stretch away from the phone. [ongoing] = the phone hasn't been unlocked since [start]. */
data class Away(val ms: Long, val start: Long, val end: Long, val ongoing: Boolean)

/**
 * One day of time away from the phone. Everything covers "counted" time: the day so far, minus the sleep window
 * when the user chose to leave sleep out.
 */
data class DayStats(
    val start: Long,
    val end: Long,         // min(day end, now)
    val countedMs: Long,   // time looked at (elapsed, minus sleep if excluded)
    val awayMs: Long,
    val breaks: List<Iv>,  // away stretches at least the "shortest break" long
    val unlocks: Int,
    val firstUnlock: Long?,
) {
    /** Share of counted time spent away, 0–100. */
    val awayPct: Int get() = if (countedMs <= 0) 0 else ((awayMs * 100) / countedMs).toInt()
    val longestBreakMs: Long get() = breaks.maxOfOrNull { it.len } ?: 0
    val avgBreakMs: Long get() = if (breaks.isEmpty()) 0 else breaks.sumOf { it.len } / breaks.size
    /** Average time between unlocks over counted time. */
    val unlockEveryMs: Long get() = if (unlocks == 0) 0 else countedMs / unlocks
}

data class Report(val now: Long, val away: Away?, val days: List<DayStats>, val sleepExcluded: Boolean = false) {
    val today: DayStats get() = days.last()
}

/**
 * Unlocked stretches between [from] and [now] from one pass over the system's usage events.
 * "Using the phone" = unlocked and screen on. Devices without a lock screen never report unlocks; for those
 * the screen-on intervals are used instead.
 */
class Timeline(
    val from: Long,
    val now: Long,
    val sessions: List<Iv>,
    val inUseNow: Boolean,
    val unlocks: LongArray,
)

/** The user's sleep window, minutes after midnight. Wraps past midnight when [bed] > [wake]. */
data class Sleep(val bed: Int, val wake: Int)

object Usage {
    private const val HOUR = 3_600_000L

    fun hasAccess(ctx: Context): Boolean {
        val aom = ctx.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = aom.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            ctx.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    fun dayStart(t: Long, offsetDays: Int = 0): Long = Calendar.getInstance().run {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, offsetDays)
        timeInMillis
    }

    /** The last [days] days, oldest first, today last. */
    fun report(ctx: Context, days: Int = 7, now: Long = System.currentTimeMillis()): Report {
        val minBreak = AppPrefs.minBreakMinutes(ctx) * 60_000L
        val sleep = AppPrefs.sleep(ctx)
        val starts = (0..days).map { dayStart(now, it - days + 1) } // days + 1 boundaries
        val tl = timeline(ctx, starts.first() - 24 * HOUR, now)
        val stats = (0 until days).map { day(tl, starts[it], starts[it + 1], minBreak, sleep) }
        return Report(now, lastAway(tl, minBreak), stats, sleep != null)
    }

    /** Just today, for the widget. */
    fun today(ctx: Context, now: Long = System.currentTimeMillis()): Report = report(ctx, 1, now)

    fun timeline(ctx: Context, from: Long, now: Long): Timeline {
        val usm = ctx.getSystemService(UsageStatsManager::class.java)
        val events = usm.queryEvents(from, now)
        val ev = UsageEvents.Event()

        var screenOn = false
        var screenStart = -1L
        var sessStart = -1L
        var sawUnlock = false
        val screenIvs = ArrayList<Iv>()
        val sessIvs = ArrayList<Iv>()
        val unlocks = ArrayList<Long>()

        fun endSession(t: Long) {
            if (sessStart >= 0) sessIvs.add(Iv(sessStart, t))
            sessStart = -1
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            val t = ev.timeStamp
            when (ev.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> if (!screenOn) {
                    screenOn = true; screenStart = t
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.DEVICE_SHUTDOWN -> {
                    if (screenOn) screenIvs.add(Iv(screenStart, t))
                    screenOn = false
                    endSession(t)
                }
                UsageEvents.Event.KEYGUARD_HIDDEN -> {
                    sawUnlock = true
                    unlocks.add(t)
                    if (sessStart < 0) sessStart = t
                }
                UsageEvents.Event.KEYGUARD_SHOWN -> endSession(t)
            }
        }
        if (screenOn) screenIvs.add(Iv(screenStart, now))
        val inUse = if (sawUnlock) sessStart >= 0 else screenOn
        endSession(now)

        return Timeline(
            from = from,
            now = now,
            sessions = if (sawUnlock) sessIvs else screenIvs,
            inUseNow = inUse,
            unlocks = unlocks.toLongArray(),
        )
    }

    /** The latest stretch of at least [minAway] ms without using the phone. */
    fun lastAway(tl: Timeline, minAway: Long): Away? {
        val s = tl.sessions
        if (s.isEmpty()) return null
        if (!tl.inUseNow) {
            val since = s.last().e
            if (tl.now - since >= minAway) return Away(tl.now - since, since, tl.now, true)
        }
        for (i in s.indices.reversed()) {
            if (i == 0) break
            val gap = s[i].s - s[i - 1].e
            if (gap >= minAway) return Away(gap, s[i - 1].e, s[i].s, false)
        }
        return null
    }

    /** Sleep windows overlapping the day that starts at [a]. */
    private fun sleepIvs(a: Long, sleep: Sleep): List<Iv> {
        fun at(min: Int): Long = Calendar.getInstance().run {
            timeInMillis = a
            add(Calendar.MINUTE, min)
            timeInMillis
        }
        val dayEnd = dayStart(a, 1)
        return if (sleep.bed > sleep.wake) {
            listOf(Iv(a, at(sleep.wake)), Iv(at(sleep.bed), dayEnd)) // e.g. 23:00 → 07:00
        } else {
            listOf(Iv(at(sleep.bed), at(sleep.wake)))                 // e.g. 01:00 → 09:00
        }.filter { it.e > it.s }
    }

    /** [whole] minus [cut], as sorted, non-overlapping windows. */
    private fun subtract(whole: Iv, cut: List<Iv>): List<Iv> {
        var parts = listOf(whole)
        for (c in cut) {
            parts = parts.flatMap { p ->
                if (c.e <= p.s || c.s >= p.e) listOf(p)
                else listOfNotNull(
                    if (c.s > p.s) Iv(p.s, c.s) else null,
                    if (c.e < p.e) Iv(c.e, p.e) else null,
                )
            }
        }
        return parts.sortedBy { it.s }
    }

    fun day(tl: Timeline, a: Long, b: Long, minBreak: Long, sleep: Sleep?): DayStats {
        val end = min(b, tl.now)
        if (end <= a) return DayStats(a, a, 0, 0, emptyList(), 0, null)
        val windows = if (sleep == null) listOf(Iv(a, end)) else subtract(Iv(a, end), sleepIvs(a, sleep))

        var counted = 0L
        var inUse = 0L
        val breaks = ArrayList<Iv>()
        for (w in windows) {
            counted += w.len
            inUse += clip(tl.sessions, w.s, w.e)
            var prev = w.s
            for (iv in tl.sessions) {
                if (iv.e <= w.s) continue
                if (iv.s >= w.e) break
                val gapEnd = max(iv.s, w.s)
                if (gapEnd - prev >= minBreak) breaks.add(Iv(prev, gapEnd))
                prev = max(prev, min(iv.e, w.e))
            }
            if (w.e - prev >= minBreak) breaks.add(Iv(prev, w.e))
        }
        val dayUnlocks = tl.unlocks.filter { it in a until end }
        return DayStats(
            start = a,
            end = end,
            countedMs = counted,
            awayMs = (counted - inUse).coerceAtLeast(0),
            breaks = breaks,
            unlocks = dayUnlocks.size,
            firstUnlock = dayUnlocks.firstOrNull(),
        )
    }

    private fun clip(ivs: List<Iv>, a: Long, b: Long): Long {
        var sum = 0L
        for (iv in ivs) {
            val d = min(iv.e, b) - max(iv.s, a)
            if (d > 0) sum += d
        }
        return sum
    }
}

object Fmt {
    fun dur(ms: Long, f: DurationFormat = DurationFormat.UNITS): String {
        val m = ms / 60_000
        val h = m / 60
        val d = h / 24
        return when (f) {
            DurationFormat.UNITS -> when {
                d > 0 -> "${d}d ${h % 24}h"
                h > 0 -> if (m % 60 == 0L) "${h}h" else "${h}h ${m % 60}m"
                else -> "${m}m"
            }
            DurationFormat.LONG -> when {
                d > 0 -> "$d day ${h % 24} hr"
                h > 0 -> if (m % 60 == 0L) "$h hr" else "$h hr ${m % 60} min"
                else -> "$m min"
            }
            DurationFormat.COLON -> "%d:%02d".format(h, m % 60)
            DurationFormat.DECIMAL -> if (h == 0L) "${m}m" else "%.1fh".format(m / 60f)
        }
    }

    fun time(ctx: Context, t: Long): String = android.text.format.DateFormat.getTimeFormat(ctx).format(t)

    /** "81% of your day", or "of your waking day" when sleep is left out. */
    fun share(r: Report, day: DayStats = r.today): String =
        "${day.awayPct}% of your " + if (r.sleepExcluded) "waking day" else "day"

    /** Minutes after midnight as a clock time. */
    fun clock(ctx: Context, min: Int): String {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, min / 60); set(Calendar.MINUTE, min % 60)
        }
        return time(ctx, c.timeInMillis)
    }
}
