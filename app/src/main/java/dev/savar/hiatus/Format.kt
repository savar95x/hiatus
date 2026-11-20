package dev.savar.hiatus

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Fmt {
    /** "2h 45m", "45m", "0m". */
    fun dur(ms: Long): String {
        val m = ms / 60_000
        val h = m / 60
        return when {
            h > 0 && m % 60 == 0L -> "${h}h"
            h > 0 -> "${h}h ${m % 60}m"
            else -> "${m}m"
        }
    }

    /** The widget's number as (text, isUnit) pieces, so units can be drawn smaller. */
    fun parts(ms: Long, f: NumberFormat): List<Pair<String, Boolean>> {
        val m = ms / 60_000
        val h = m / 60
        val r = m % 60
        return when (f) {
            NumberFormat.HM -> when {
                h == 0L -> listOf("$m" to false, "m" to true)
                r == 0L -> listOf("$h" to false, "h" to true)
                else -> listOf("$h" to false, "h" to true, " " to false, "$r" to false, "m" to true)
            }
            NumberFormat.WORDS -> when {
                h == 0L -> listOf("$m" to false, " min" to true)
                r == 0L -> listOf("$h" to false, " hr" to true)
                else -> listOf("$h" to false, " hr " to true, "$r" to false, " min" to true)
            }
            NumberFormat.CLOCK -> listOf("%d:%02d".format(h, r) to false)
            NumberFormat.DECIMAL -> if (h == 0L) listOf("$m" to false, "m" to true)
                else listOf("%.1f".format(m / 60f) to false, "h" to true)
            NumberFormat.MINUTES -> listOf("$m" to false, "m" to true)
            NumberFormat.HOURS -> if (h == 0L) listOf("$m" to false, "m" to true) else listOf("$h" to false, "h" to true)
        }
    }

    fun time(ctx: Context, t: Long): String = android.text.format.DateFormat.getTimeFormat(ctx).format(Date(t))

    fun span(ctx: Context, s: Span, ongoing: Boolean = false): String =
        time(ctx, s.start) + " – " + if (ongoing) "now" else time(ctx, s.end)

    /** Minutes after midnight as a clock time. */
    fun clock(ctx: Context, minutes: Int): String {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutes / 60); set(Calendar.MINUTE, minutes % 60)
        }
        return time(ctx, c.timeInMillis)
    }

    fun weekday(t: Long, short: Boolean = true): String =
        SimpleDateFormat(if (short) "EEE" else "EEEE", Locale.getDefault()).format(Date(t))

    fun date(t: Long): String = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(t))

    fun shareText(day: Day, rules: Rules): String =
        "${day.percent}% of your " + if (rules.sleep != SleepMode.OFF) "waking day" else "day"

    fun minBreak(ms: Long): String = if (ms < 60_000) "${ms / 1000} s" else "${ms / 60_000} min"
}
