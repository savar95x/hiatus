package dev.savar.hiatus

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode { AUTO, LIGHT, DARK }

/**
 * App settings (prefs `settings`). Observable, so the UI and theme follow changes at once. Load with [init]
 * before reading; the widget path calls it too (cheap after the first time).
 */
object Prefs {
    var rules by mutableStateOf(Rules())
        private set
    var theme by mutableStateOf(ThemeMode.AUTO)
        private set
    var pureBlack by mutableStateOf(false)
        private set

    @Volatile private var loaded = false
    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun init(ctx: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val p = p(ctx)
            rules = Rules(
                minBreakMs = p.getLong("min_break_ms", 60_000L),
                sleep = SleepMode.entries.firstOrNull { it.name == p.getString("sleep", null) } ?: SleepMode.OFF,
                bed = p.getInt("bed", 23 * 60),
                wake = p.getInt("wake", 7 * 60),
            )
            theme = ThemeMode.entries.firstOrNull { it.name == p.getString("theme", null) } ?: ThemeMode.AUTO
            pureBlack = p.getBoolean("pure_black", false)
            loaded = true
        }
    }

    fun setRules(ctx: Context, r: Rules) {
        if (r == rules) return
        rules = r
        p(ctx).edit()
            .putLong("min_break_ms", r.minBreakMs)
            .putString("sleep", r.sleep.name)
            .putInt("bed", r.bed)
            .putInt("wake", r.wake)
            .apply()
        Widgets.refresh(ctx) // the numbers change
    }

    fun setTheme(ctx: Context, m: ThemeMode) {
        theme = m
        p(ctx).edit().putString("theme", m.name).apply()
    }

    fun setPureBlack(ctx: Context, on: Boolean) {
        pureBlack = on
        p(ctx).edit().putBoolean("pure_black", on).apply()
    }
}
