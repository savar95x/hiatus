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
        val apps = ArrayList<AppSpan>()
        val firsts = ArrayList<Pair<Long, String>>()
        val pool = listOf(
            "com.instagram.android", "com.instagram.android", "com.instagram.android", "com.whatsapp", "com.whatsapp",
            "com.whatsapp", "com.google.android.youtube", "com.google.android.youtube", "com.spotify.music",
            "com.twitter.android", "com.android.chrome", "com.google.android.gm",
        )
        fun session(rnd: Random, a: Long, b: Long) {
            val p1 = pool[rnd.nextInt(pool.size)]
            firsts += a to p1
            val mid = if (b - a > 4 * MIN && rnd.nextBoolean()) a + (b - a) / 2 else b
            apps += AppSpan(p1, a + 2_000, mid)
            if (mid < b) apps += AppSpan(pool[rnd.nextInt(pool.size)], mid, b)
        }
        for (back in days downTo 0) {
            val d = Away.dayStart(now, -back)
            val rnd = Random(d / 86_400_000L)
            var t = d + (6 * 60 + 50 + rnd.nextInt(80)) * MIN       // wake 6:50–8:10
            val sleepAt = d + (22 * 60 + 40 + rnd.nextInt(100)) * MIN // sleep 22:40–00:20
            // A late-night check just after midnight, some nights.
            if (rnd.nextInt(3) == 0) {
                val a = d + (10 + rnd.nextInt(30)) * MIN
                val e = a + (3 + rnd.nextInt(9)) * MIN
                use += Span(a, e); unlocks += a; session(rnd, a, e)
            }
            while (t < sleepAt && t < now) {
                val len = (1 + rnd.nextInt(if (rnd.nextInt(5) == 0) 35 else 9)) * MIN
                val end = minOf(t + len, now)
                use += Span(t, end); unlocks += t; session(rnd, t, end)
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
        return Timeline(Away.dayStart(now, -days), now, Away.merge(use), unlocks.sorted().toLongArray(), inUse,
            apps.sortedBy { it.start }, firsts.sortedBy { it.first })
    }

    fun week(rules: Rules, now: Long, weeks: Int = 0): List<Day> {
        val tl = timeline(now)
        val ws = Away.weekStart(now, weeks)
        return (0 until 7).map { Away.day(tl, Away.dayStart(ws, it), rules, now) }
    }

    fun today(rules: Rules, now: Long): Day = Away.day(timeline(now, 2), Away.dayStart(now), rules, now)
}
