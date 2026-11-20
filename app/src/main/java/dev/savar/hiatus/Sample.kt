package dev.savar.hiatus

import java.util.Random

/**
 * A believable week of phone use, run through the real [Away] maths: for widget previews before usage access
 * is granted, and for the screenshot tests.
 */
object Sample {
    private const val MIN = 60_000L

    fun timeline(now: Long, days: Int = 14): Timeline {
        val use = ArrayList<Span>()
        val unlocks = ArrayList<Long>()
        for (back in days downTo 0) {
            val d = Away.dayStart(now, -back)
            val rnd = Random(d / 86_400_000L)
            var t = d + (6 * 60 + 50 + rnd.nextInt(80)) * MIN       // wake 6:50–8:10
            val sleepAt = d + (22 * 60 + 40 + rnd.nextInt(100)) * MIN // sleep 22:40–00:20
            // A late-night check just after midnight, some nights.
            if (rnd.nextInt(3) == 0) {
                val a = d + (10 + rnd.nextInt(30)) * MIN
                use += Span(a, a + (3 + rnd.nextInt(9)) * MIN); unlocks += a
            }
            while (t < sleepAt && t < now) {
                val len = (1 + rnd.nextInt(if (rnd.nextInt(5) == 0) 35 else 9)) * MIN
                val end = minOf(t + len, now)
                use += Span(t, end); unlocks += t
                if (end >= now) break
                val gap = when (rnd.nextInt(10)) {
                    in 0..4 -> 2 + rnd.nextInt(12)
                    in 5..7 -> 15 + rnd.nextInt(40)
                    else -> 60 + rnd.nextInt(130)
                } * MIN
                t = end + gap
            }
        }
        val inUse = use.lastOrNull()?.end == now
        return Timeline(Away.dayStart(now, -days), now, Away.merge(use), unlocks.sorted().toLongArray(), inUse)
    }

    fun week(rules: Rules, now: Long, weeks: Int = 0): List<Day> {
        val tl = timeline(now)
        val ws = Away.weekStart(now, weeks)
        return (0 until 7).map { Away.day(tl, Away.dayStart(ws, it), rules, now) }
    }

    fun today(rules: Rules, now: Long): Day = Away.day(timeline(now, 2), Away.dayStart(now), rules, now)
}
