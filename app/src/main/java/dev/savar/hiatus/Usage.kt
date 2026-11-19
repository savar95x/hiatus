package dev.savar.hiatus

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/** Half-open time interval [s, e) in epoch millis. */
data class Iv(val s: Long, val e: Long)

/** One stretch away from the phone. [ongoing] = the phone hasn't been unlocked since [start]. */
data class Away(val ms: Long, val start: Long, val end: Long, val ongoing: Boolean)

data class AppUsage(val pkg: String, val ms: Long)

data class DayStats(
    val start: Long,
    val end: Long,          // min(day end, now)
    val screenTimeMs: Long, // apps in the foreground (launcher excluded), like Digital Wellbeing
    val inUseMs: Long,      // unlocked until the screen went off
    val awayMs: Long,
    val longestAwayMs: Long,
    val sessions: Int,
    val unlocks: Int,
    val pickups: Int,       // screen turned on (incl. glances without unlocking)
    val hourly: LongArray,  // screen time per hour of day
    val apps: List<AppUsage>,
) {
    val avgSessionMs: Long get() = if (sessions == 0) 0 else inUseMs / sessions
}

data class Report(val now: Long, val away: Away?, val days: List<DayStats>) {
    val today: DayStats get() = days.last()
}

/**
 * Everything we know between [from] and [now], built from one pass over the system's usage events.
 * "Using the phone" = unlocked and screen on. Devices without a lock screen never report unlocks; for those
 * the screen-on intervals are used instead.
 */
class Timeline(
    val from: Long,
    val now: Long,
    val sessions: List<Iv>,
    val inUseNow: Boolean,
    val wakes: LongArray,
    val unlocks: LongArray,
    val apps: Map<String, List<Iv>>,
    val all: List<Iv>,
)

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

    /** Full report: the last [days] days, oldest first, today last. */
    fun report(ctx: Context, days: Int = 7, now: Long = System.currentTimeMillis()): Report {
        val starts = (0..days).map { dayStart(now, it - days + 1) } // days + 1 boundaries
        val tl = timeline(ctx, starts.first() - 24 * HOUR, now)
        val stats = (0 until days).map { day(tl, starts[it], starts[it + 1]) }
        return Report(now, lastAway(tl, AppPrefs.minBreakMinutes(ctx) * 60_000L), stats)
    }

    /** Just today, for the widget. */
    fun today(ctx: Context, now: Long = System.currentTimeMillis()): Report = report(ctx, 1, now)

    private fun homePackages(ctx: Context): Set<String> = runCatching {
        @Suppress("DEPRECATION")
        ctx.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }.toSet()
    }.getOrDefault(emptySet()) + setOf("com.android.systemui")

    fun timeline(ctx: Context, from: Long, now: Long): Timeline {
        val usm = ctx.getSystemService(UsageStatsManager::class.java)
        val excluded = homePackages(ctx)
        val events = usm.queryEvents(from, now)
        val ev = UsageEvents.Event()

        var screenOn = false
        var screenStart = -1L
        var sessStart = -1L
        var sawUnlock = false
        val screenIvs = ArrayList<Iv>()
        val sessIvs = ArrayList<Iv>()
        val wakes = ArrayList<Long>()
        val unlocks = ArrayList<Long>()
        val open = HashMap<String, Pair<String, Long>>() // "pkg/class" -> (pkg, resumedAt)
        val raw = HashMap<String, MutableList<Iv>>()

        fun addApp(pkg: String, s: Long, e: Long) {
            if (e > s && pkg !in excluded) raw.getOrPut(pkg) { ArrayList() }.add(Iv(s, e))
        }
        fun closeApps(t: Long) {
            for ((pkg, s) in open.values) addApp(pkg, s, t)
            open.clear()
        }
        fun endSession(t: Long) {
            if (sessStart >= 0) sessIvs.add(Iv(sessStart, t))
            sessStart = -1
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            val t = ev.timeStamp
            when (ev.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    val key = ev.packageName + "/" + ev.className
                    if (key !in open) open[key] = ev.packageName to t
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    open.remove(ev.packageName + "/" + ev.className)?.let { addApp(it.first, it.second, t) }
                }
                UsageEvents.Event.SCREEN_INTERACTIVE -> if (!screenOn) {
                    screenOn = true; screenStart = t; wakes.add(t)
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.DEVICE_SHUTDOWN -> {
                    if (screenOn) screenIvs.add(Iv(screenStart, t))
                    screenOn = false
                    endSession(t)
                    closeApps(t)
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
        closeApps(now)

        val apps = raw.mapValues { merge(it.value) }
        return Timeline(
            from = from,
            now = now,
            sessions = if (sawUnlock) sessIvs else screenIvs,
            inUseNow = inUse,
            wakes = wakes.toLongArray(),
            unlocks = unlocks.toLongArray(),
            apps = apps,
            all = merge(apps.values.flatten()),
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

    fun day(tl: Timeline, a: Long, b: Long): DayStats {
        val end = min(b, tl.now)
        if (end <= a) return DayStats(a, a, 0, 0, 0, 0, 0, 0, 0, LongArray(24), emptyList())

        val inUse = clip(tl.sessions, a, end)
        var longest = 0L
        var prev = a
        var count = 0
        for (iv in tl.sessions) {
            if (iv.e <= a) continue
            if (iv.s >= end) break
            count++
            longest = max(longest, max(iv.s, a) - prev)
            prev = max(prev, min(iv.e, end))
        }
        longest = max(longest, end - prev)

        val hourly = LongArray(24)
        val cal = Calendar.getInstance().apply { timeInMillis = a }
        for (h in 0 until 24) {
            val hs = cal.timeInMillis
            cal.add(Calendar.HOUR_OF_DAY, 1)
            val he = min(cal.timeInMillis, end)
            if (hs >= end) break
            hourly[h] = clip(tl.all, hs, he)
        }

        val apps = tl.apps.mapNotNull { (pkg, ivs) ->
            val ms = clip(ivs, a, end)
            if (ms >= 1_000) AppUsage(pkg, ms) else null
        }.sortedByDescending { it.ms }

        return DayStats(
            start = a,
            end = end,
            screenTimeMs = clip(tl.all, a, end),
            inUseMs = inUse,
            awayMs = (end - a) - inUse,
            longestAwayMs = longest,
            sessions = count,
            unlocks = tl.unlocks.count { it in a until end },
            pickups = tl.wakes.count { it in a until end },
            hourly = hourly,
            apps = apps,
        )
    }

    private fun merge(ivs: List<Iv>): List<Iv> {
        if (ivs.isEmpty()) return ivs
        val sorted = ivs.sortedBy { it.s }
        val out = ArrayList<Iv>()
        var cur = sorted[0]
        for (i in 1 until sorted.size) {
            val n = sorted[i]
            cur = if (n.s <= cur.e) Iv(cur.s, max(cur.e, n.e)) else { out.add(cur); n }
        }
        out.add(cur)
        return out
    }

    private fun clip(ivs: List<Iv>, a: Long, b: Long): Long {
        var sum = 0L
        for (iv in ivs) {
            val d = min(iv.e, b) - max(iv.s, a)
            if (d > 0) sum += d
        }
        return sum
    }

    // ---- App names, cached for the process ----

    private val labels = ConcurrentHashMap<String, String>()

    fun label(ctx: Context, pkg: String): String = labels.getOrPut(pkg) {
        runCatching {
            val pm = ctx.packageManager
            pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
        }.getOrDefault(pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() })
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
                h > 0 -> "${h}h ${m % 60}m"
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
}

/** A metric as text: the number, and a short line of context under it. */
object Metrics {
    fun isDuration(m: Metric) = m !in setOf(Metric.UNLOCKS, Metric.PICKUPS, Metric.SESSIONS)

    fun value(m: Metric, r: Report, f: DurationFormat): String {
        val t = r.today
        return when (m) {
            Metric.LAST_AWAY -> Fmt.dur(r.away?.ms ?: 0, f)
            Metric.AWAY_TODAY -> Fmt.dur(t.awayMs, f)
            Metric.SCREEN_TIME -> Fmt.dur(t.screenTimeMs, f)
            Metric.UNLOCKS -> t.unlocks.toString()
            Metric.PICKUPS -> t.pickups.toString()
            Metric.PER_SESSION -> Fmt.dur(t.avgSessionMs, f)
            Metric.LONGEST_AWAY -> Fmt.dur(t.longestAwayMs, f)
            Metric.SESSIONS -> t.sessions.toString()
        }
    }

    fun caption(ctx: Context, m: Metric, r: Report, f: DurationFormat): String {
        val t = r.today
        return when (m) {
            Metric.LAST_AWAY -> {
                val a = r.away
                when {
                    a == null -> "No break yet"
                    a.ongoing -> "since " + Fmt.time(ctx, a.start)
                    else -> Fmt.time(ctx, a.start) + " – " + Fmt.time(ctx, a.end)
                }
            }
            Metric.AWAY_TODAY -> "longest " + Fmt.dur(t.longestAwayMs, f)
            Metric.SCREEN_TIME -> "${t.unlocks} unlocks"
            Metric.UNLOCKS -> if (t.unlocks == 0) "today" else "every " + Fmt.dur((t.end - t.start) / t.unlocks, f)
            Metric.PICKUPS -> "${t.unlocks} unlocked"
            Metric.PER_SESSION -> "${t.sessions} sessions"
            Metric.LONGEST_AWAY -> "today"
            Metric.SESSIONS -> Fmt.dur(t.avgSessionMs, f) + " each"
        }
    }
}
