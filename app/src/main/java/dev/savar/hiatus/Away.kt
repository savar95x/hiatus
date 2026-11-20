package dev.savar.hiatus

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

/** Half-open time span [start, end) in epoch millis. */
data class Span(val start: Long, val end: Long) {
    val ms: Long get() = end - start
    fun clip(a: Long, b: Long): Span? = Span(max(start, a), min(end, b)).takeIf { it.end > it.start }
}

private fun AppSpan.clip(a: Long, b: Long): Long = (min(end, b) - max(start, a)).coerceAtLeast(0)

/**
 * When the phone was in use (unlocked) between [from] and [now]. Away = everything else.
 * Glancing at the lock screen doesn't count as use: only unlocking does.
 */
class Timeline(
    val from: Long,
    val now: Long,
    val use: List<Span>,       // sorted, non-overlapping
    val unlocks: LongArray,    // sorted
    val inUseNow: Boolean,
    val apps: List<AppSpan> = emptyList(),          // an app in the foreground (launcher left out)
    val firstApps: List<Pair<Long, String>> = emptyList(), // (unlock time, first app opened after it)
)

/** [pkg] in the foreground for [start]..[end]. */
class AppSpan(val pkg: String, val start: Long, val end: Long)

/** One app over a day: time in the foreground, times opened, times it was the first app after an unlock. */
class AppStat(val pkg: String, val ms: Long, val opens: Int, val firsts: Int)

/** One day, measured over "counted" time: the day so far, minus sleep when sleep is left out. */
class Day(
    val start: Long,           // midnight
    val end: Long,             // next midnight
    val until: Long,           // min(end, now): where the data stops
    val counted: List<Span>,   // the windows that count
    val sleep: List<Span>,     // left out (sleep), within the day
    val use: List<Span>,       // phone in use, within the day
    val breaks: List<Span>,    // away stretches at least the shortest break long, within counted time
    val unlocks: LongArray,    // unlocks within counted time
    val ongoing: Boolean,      // the last break is still going (today, phone locked right now)
    val hasData: Boolean,      // false for future days and days with no record at all
    val apps: List<AppStat> = emptyList(), // most used first; only for days Android still has events for
) {
    /** When the day's count started: midnight, or waking up when sleep is left out (a late night before it aside). */
    val countFrom: Long? get() = sleep.firstOrNull { it.start < start + 12 * HOUR }?.end?.takeIf { it < until }
        ?: counted.firstOrNull()?.start

    val countedMs: Long = counted.sumOf { it.ms }
    val awayMs: Long = breaks.sumOf { it.ms }
    /** Share of counted time spent away, 0..1. */
    val share: Float = if (countedMs <= 0) 0f else (awayMs.toFloat() / countedMs).coerceIn(0f, 1f)
    val percent: Int get() = (share * 100).toInt()
    val longest: Span? = breaks.maxByOrNull { it.ms }
    val averageMs: Long = if (breaks.isEmpty()) 0 else awayMs / breaks.size
    val lastBreak: Span? = breaks.lastOrNull()
    val firstUnlock: Long? = unlocks.firstOrNull()
    val lastUnlock: Long? = unlocks.lastOrNull()
    /** Average time between unlocks over counted time. */
    val unlockEveryMs: Long = if (unlocks.isEmpty()) 0 else countedMs / unlocks.size
    val isFuture: Boolean get() = until <= start

    /** Unlocks per hour of the day (24 buckets). */
    fun unlocksByHour(): IntArray {
        val out = IntArray(24)
        val c = Calendar.getInstance()
        for (t in unlocks) { c.timeInMillis = t; out[c.get(Calendar.HOUR_OF_DAY)]++ }
        return out
    }

    /** Away time per bucket: under 15 min, 15 min–1 h, 1–3 h, over 3 h. (count, ms) */
    fun breakBuckets(): List<Pair<Int, Long>> {
        val limits = longArrayOf(15 * MIN, HOUR, 3 * HOUR, Long.MAX_VALUE)
        val count = IntArray(4)
        val ms = LongArray(4)
        for (b in breaks) {
            val i = limits.indexOfFirst { b.ms < it }
            count[i]++; ms[i] += b.ms
        }
        return (0 until 4).map { count[it] to ms[it] }
    }

    companion object {
        const val MIN = 60_000L
        const val HOUR = 3_600_000L
    }
}

enum class SleepMode { OFF, AUTO, FIXED }

/** What "counted" means: the shortest break, and how sleep is left out. Minutes after midnight for bed/wake. */
data class Rules(
    val minBreakMs: Long = 60_000L,
    val sleep: SleepMode = SleepMode.OFF,
    val bed: Int = 23 * 60,
    val wake: Int = 7 * 60,
)

object Away {
    private const val HOUR = Day.HOUR
    private const val DAY = 24 * HOUR

    fun hasAccess(ctx: Context): Boolean {
        val aom = ctx.getSystemService(AppOpsManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        val mode = if (android.os.Build.VERSION.SDK_INT >= 29) {
            aom.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            aom.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            ctx.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else mode == AppOpsManager.MODE_ALLOWED
    }

    fun dayStart(t: Long, plusDays: Int = 0): Long = Calendar.getInstance().run {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, plusDays)
        timeInMillis
    }

    /** First day of the week containing [t] (locale's first weekday), shifted by [weeks]. */
    fun weekStart(t: Long, weeks: Int = 0): Long = Calendar.getInstance().run {
        timeInMillis = dayStart(t)
        val back = (get(Calendar.DAY_OF_WEEK) - firstDayOfWeek + 7) % 7
        add(Calendar.DAY_OF_YEAR, -back + weeks * 7)
        timeInMillis
    }

    private fun at(dayStart: Long, minutes: Int): Long = Calendar.getInstance().run {
        timeInMillis = dayStart
        add(Calendar.MINUTE, minutes)
        timeInMillis
    }

    // ------------------------------------------------------------------ reading the system's events

    /**
     * One pass over the system's usage events. In use = from an unlock (keyguard hidden) until the lock screen
     * comes back or the screen turns off. Phones without a lock screen never report unlocks; for those the
     * screen-on time is used instead.
     */
    fun live(ctx: Context, from: Long, now: Long): Timeline {
        val usm = ctx.getSystemService(UsageStatsManager::class.java)
            ?: return Timeline(from, now, emptyList(), LongArray(0), false)
        val events = usm.queryEvents(from, now)
        val ev = UsageEvents.Event()
        var screenOn = -1L
        var unlocked = -1L
        var sawUnlock = false
        val screen = ArrayList<Span>()
        val use = ArrayList<Span>()
        val unlocks = ArrayList<Long>()
        val ignore = launchers(ctx)
        val apps = ArrayList<AppSpan>()
        val firstApps = ArrayList<Pair<Long, String>>()
        var fg: String? = null
        var fgStart = -1L
        var awaitingFirst = -1L // unlock time still waiting for its first app
        fun closeApp(t: Long) {
            val p = fg ?: return
            if (t > fgStart) apps.add(AppSpan(p, fgStart, t))
            fg = null
        }
        fun lock(t: Long) {
            if (unlocked >= 0 && t > unlocked) use.add(Span(unlocked, t))
            unlocked = -1
            closeApp(t)
            awaitingFirst = -1
        }
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            val t = ev.timeStamp
            when (ev.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> if (screenOn < 0) screenOn = t
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.DEVICE_SHUTDOWN -> {
                    if (screenOn >= 0 && t > screenOn) screen.add(Span(screenOn, t))
                    screenOn = -1
                    lock(t)
                }
                UsageEvents.Event.KEYGUARD_HIDDEN -> {
                    sawUnlock = true
                    if (unlocked < 0) { unlocked = t; unlocks.add(t); awaitingFirst = t }
                }
                UsageEvents.Event.KEYGUARD_SHOWN -> lock(t)
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    val p = ev.packageName ?: continue
                    if (p == fg) continue
                    closeApp(t)
                    if (p in ignore) continue
                    // Moving between screens of one app: keep it one stretch.
                    val last = apps.lastOrNull()
                    if (last != null && last.pkg == p && t - last.end < 2_000) {
                        apps.removeAt(apps.lastIndex); fgStart = last.start
                    } else fgStart = t
                    fg = p
                    if (awaitingFirst >= 0) { firstApps.add(awaitingFirst to p); awaitingFirst = -1 }
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> if (ev.packageName == fg) closeApp(t)
            }
        }
        val inUse = if (sawUnlock) unlocked >= 0 else screenOn >= 0
        lock(now)
        if (screenOn >= 0) screen.add(Span(screenOn, now))
        return Timeline(from, now, merge(if (sawUnlock) use else screen), unlocks.toLongArray(), inUse, apps, firstApps)
    }

    /** Home screens and the system UI: not "apps you used". */
    private fun launchers(ctx: Context): Set<String> {
        val out = hashSetOf("android", "com.android.systemui", ctx.packageName)
        runCatching {
            @Suppress("DEPRECATION")
            ctx.packageManager.queryIntentActivities(
                android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME), 0,
            ).forEach { out += it.activityInfo.packageName }
        }
        return out
    }

    internal fun merge(spans: List<Span>): List<Span> {
        if (spans.isEmpty()) return spans
        val sorted = spans.sortedBy { it.start }
        val out = ArrayList<Span>(sorted.size)
        var cur = sorted[0]
        for (s in sorted.drop(1)) {
            cur = if (s.start <= cur.end) Span(cur.start, max(cur.end, s.end)) else { out.add(cur); s }
        }
        out.add(cur)
        return out
    }

    /**
     * Live events plus saved history (the system only keeps about a week of events). Days the live events know
     * about are saved; days they don't come from the saved copy.
     */
    fun timeline(ctx: Context, from: Long, now: Long): Timeline {
        val liveFrom = max(from, dayStart(now, -10))
        val live = if (liveFrom < now) live(ctx, liveFrom, now) else Timeline(from, now, emptyList(), LongArray(0), false)
        History.save(ctx, live)
        val use = ArrayList(live.use)
        val unlocks = live.unlocks.toMutableList()
        var d = dayStart(from)
        while (d < now) {
            val next = dayStart(d, 1)
            val liveHas = live.unlocks.any { it in d until next } || live.use.any { it.end > d && it.start < next }
            if (!liveHas) History.load(ctx, d)?.let { (u, s) -> unlocks += u.toList(); use += s }
            d = next
        }
        unlocks.sort()
        return Timeline(from, now, merge(use), unlocks.toLongArray(), live.inUseNow, live.apps, live.firstApps)
    }

    // ------------------------------------------------------------------ days

    /** Stretches without use between [a] and [b]. */
    private fun gaps(use: List<Span>, a: Long, b: Long): List<Span> {
        val out = ArrayList<Span>()
        var prev = a
        for (u in use) {
            if (u.end <= a) continue
            if (u.start >= b) break
            if (u.start > prev) out.add(Span(prev, u.start))
            prev = max(prev, u.end)
        }
        if (b > prev) out.add(Span(prev, b))
        return out
    }

    /** Sleep within the day starting at [d]. */
    private fun sleep(tl: Timeline, d: Long, rules: Rules): List<Span> {
        val end = dayStart(d, 1)
        return when (rules.sleep) {
            SleepMode.OFF -> emptyList()
            SleepMode.FIXED -> when {
                rules.bed == rules.wake -> emptyList()
                rules.bed > rules.wake -> listOf(Span(d, at(d, rules.wake)), Span(at(d, rules.bed), end))
                else -> listOf(Span(at(d, rules.bed), at(d, rules.wake)))
            }
            // The longest stretch of 3 h+ without use that reaches into midnight–8 AM is that night's sleep.
            // Two nights touch a day: the one ending this morning and the one starting tonight.
            SleepMode.AUTO -> {
                val first = tl.use.firstOrNull()?.start ?: return emptyList()
                val all = gaps(tl.use, first, tl.now)
                fun night(n: Long) = all.filter { it.ms >= 3 * HOUR && it.end > n && it.start < at(n, 8 * 60) }
                    .maxByOrNull { it.ms }
                listOfNotNull(night(d), night(end)).mapNotNull { it.clip(d, end) }.distinct()
            }
        }.mapNotNull { it.clip(d, end) }.sortedBy { it.start }
    }

    private fun subtract(whole: Span, cut: List<Span>): List<Span> {
        var parts = listOf(whole)
        for (c in cut) parts = parts.flatMap { p ->
            if (c.end <= p.start || c.start >= p.end) listOf(p)
            else listOfNotNull(Span(p.start, c.start).takeIf { it.ms > 0 }, Span(c.end, p.end).takeIf { it.ms > 0 })
        }
        return parts
    }

    fun day(tl: Timeline, d: Long, rules: Rules, now: Long = tl.now): Day {
        val end = dayStart(d, 1)
        val until = min(end, tl.now)
        if (until <= d) return Day(d, end, d, emptyList(), emptyList(), emptyList(), emptyList(), LongArray(0), false, false)
        val use = tl.use.mapNotNull { it.clip(d, until) }
        val dayUnlocks = tl.unlocks.filter { it in d until until }
        // A day with no record at all: the phone was off, or before the app could see anything.
        val known = tl.use.firstOrNull()?.start ?: Long.MAX_VALUE
        val hasData = use.isNotEmpty() || dayUnlocks.isNotEmpty() || (until == now && known < d)
        val sleep = sleep(tl, d, rules).mapNotNull { it.clip(d, until) }
        val counted = subtract(Span(d, until), sleep)
        val breaks = counted.flatMap { w -> gaps(use, w.start, w.end) }.filter { it.ms >= rules.minBreakMs }
        val unlocks = dayUnlocks.filter { t -> counted.any { t >= it.start && t < it.end } }.toLongArray()
        val ongoing = until == now && !tl.inUseNow && breaks.lastOrNull()?.end == until
        return Day(d, end, until, counted, sleep, use, if (hasData) breaks else emptyList(), unlocks, ongoing, hasData, apps(tl, d, until))
    }

    private fun apps(tl: Timeline, a: Long, b: Long): List<AppStat> {
        if (tl.apps.isEmpty()) return emptyList()
        val ms = HashMap<String, Long>()
        val opens = HashMap<String, Int>()
        for (s in tl.apps) {
            val c = s.clip(a, b)
            if (c <= 0) continue
            ms[s.pkg] = (ms[s.pkg] ?: 0L) + c
            if (s.start >= a) opens[s.pkg] = (opens[s.pkg] ?: 0) + 1
        }
        val firsts = HashMap<String, Int>()
        for ((t, p) in tl.firstApps) if (t in a until b) firsts[p] = (firsts[p] ?: 0) + 1
        return (ms.keys + firsts.keys).map { AppStat(it, ms[it] ?: 0L, opens[it] ?: 0, firsts[it] ?: 0) }
            .filter { it.ms >= 1_000 || it.firsts > 0 }
            .sortedByDescending { it.ms }
    }

    /** The 7 days of a week (offset 0 = this week), and today. */
    fun week(ctx: Context, rules: Rules, weeks: Int = 0, now: Long = System.currentTimeMillis()): List<Day> {
        val ws = weekStart(now, weeks)
        val days = (0 until 7).map { dayStart(ws, it) }
        val tl = timeline(ctx, dayStart(ws, -1), min(now, dayStart(ws, 7)))
        return days.map { day(tl, it, rules, now) }
    }

    /** Just today, for the widget: reads one day and a bit of events. */
    fun today(ctx: Context, rules: Rules, now: Long = System.currentTimeMillis()): Day {
        val d = dayStart(now)
        return day(timeline(ctx, dayStart(d, -1), now), d, rules)
    }
}
