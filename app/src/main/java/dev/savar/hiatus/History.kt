package dev.savar.hiatus

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A small copy of each finished day's unlocks and in-use stretches, so past weeks still show once Android has
 * dropped the events (it keeps about a week). One text file per day; written once, a few KB at most.
 */
object History {
    private const val KEEP_DAYS = 120

    private fun dir(ctx: Context) = File(ctx.filesDir, "history").apply { mkdirs() }
    private fun name(d: Long) = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(d))
    private fun file(ctx: Context, d: Long) = File(dir(ctx), name(d))

    /** Saves every finished day [tl] fully covers, unless already saved. */
    fun save(ctx: Context, tl: Timeline) {
        runCatching {
            // Only recent days: further back, Android may already have dropped part of a day's events.
            var d = maxOf(Away.dayStart(tl.now, -6), if (Away.dayStart(tl.from) == tl.from) tl.from else Away.dayStart(tl.from, 1))
            while (true) {
                val next = Away.dayStart(d, 1)
                if (next > tl.now) break
                val f = file(ctx, d)
                if (!f.exists()) {
                    val unlocks = tl.unlocks.filter { it in d until next }
                    val use = tl.use.mapNotNull { it.clip(d, next) }
                    if (unlocks.isNotEmpty() || use.isNotEmpty()) {
                        val text = buildString {
                            append("v1\n")
                            unlocks.forEach { append("u ").append(it).append('\n') }
                            use.forEach { append("s ").append(it.start).append(' ').append(it.end).append('\n') }
                        }
                        val tmp = File(f.parentFile, f.name + ".tmp")
                        tmp.writeText(text)
                        tmp.renameTo(f)
                    }
                }
                d = next
            }
            prune(ctx, tl.now)
        }
    }

    fun load(ctx: Context, d: Long): Pair<LongArray, List<Span>>? = runCatching {
        val f = file(ctx, d)
        if (!f.exists()) return null
        val unlocks = ArrayList<Long>()
        val use = ArrayList<Span>()
        f.forEachLine { line ->
            val p = line.split(' ')
            when (p[0]) {
                "u" -> p.getOrNull(1)?.toLongOrNull()?.let { unlocks += it }
                "s" -> {
                    val a = p.getOrNull(1)?.toLongOrNull()
                    val b = p.getOrNull(2)?.toLongOrNull()
                    if (a != null && b != null && b > a) use += Span(a, b)
                }
            }
        }
        unlocks.toLongArray() to use
    }.getOrNull()

    /** The earliest day with any record (saved or live), for how far back the week view can go. */
    fun earliest(ctx: Context): Long? = dir(ctx).list()
        ?.filter { it.length == 8 && it.all(Char::isDigit) }
        ?.minOrNull()
        ?.let { runCatching { SimpleDateFormat("yyyyMMdd", Locale.US).parse(it)?.time }.getOrNull() }

    private var lastPrune = 0L
    private fun prune(ctx: Context, now: Long) {
        if (now - lastPrune < 24 * 3_600_000L) return
        lastPrune = now
        val cutoff = name(Away.dayStart(now, -KEEP_DAYS))
        dir(ctx).listFiles()?.forEach { if (it.name.length == 8 && it.name < cutoff) it.delete() }
    }
}
