package dev.savar.hiatus

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import kotlin.math.sqrt

/**
 * The one Hiatus widget. Away time only changes while the phone is locked, so the widget redraws when you come
 * back (unlock), at midnight, when a setting changes, and on a lazy half-hourly alarm as a safety net. All alarms
 * are non-waking: they never wake the phone, they just run the next time it's awake anyway.
 */
class HiatusWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH, ACTION_TICK,
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED -> {
                val pr = goAsync()
                Widgets.refresh(context) { pr.finish() }
                Alarms.schedule(context)
            }
            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pr = goAsync()
        Widgets.refresh(context, ids) { pr.finish() }
        Alarms.schedule(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        val pr = goAsync()
        Widgets.refresh(context, intArrayOf(id), fresh = false) { pr.finish() }
    }

    override fun onEnabled(context: Context) = Alarms.schedule(context)
    override fun onDisabled(context: Context) = Alarms.cancel(context)

    companion object {
        const val ACTION_REFRESH = "dev.savar.hiatus.REFRESH"
        const val ACTION_TICK = "dev.savar.hiatus.TICK"
    }
}

class HiatusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        ScreenWatch.register(this)
        Alarms.schedule(this)
    }
}

/**
 * While the process is alive: redraw just after an unlock, and when the screen goes off arm a one-shot non-waking
 * alarm, which Android delivers the moment the phone next wakes (so the widget is fresh even if this process
 * was frozen or killed in between).
 */
object ScreenWatch {
    private var registered = false
    private var lastNight = -1
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (Widgets.ids(ctx).isEmpty()) return
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> Alarms.armWake(ctx)
                Intent.ACTION_CONFIGURATION_CHANGED -> {
                    val night = ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    if (night != lastNight) { lastNight = night; later(ctx, 300) }
                }
                else -> later(ctx, 400) // USER_PRESENT
            }
        }
    }

    private fun later(ctx: Context, ms: Long) {
        pending?.let { handler.removeCallbacks(it) }
        val r = Runnable { pending = null; Widgets.refresh(ctx) }
        pending = r
        handler.postDelayed(r, ms)
    }

    fun register(ctx: Context) {
        if (registered) return
        registered = true
        lastNight = ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
        }
        runCatching {
            ContextCompat.registerReceiver(ctx.applicationContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }.onFailure { registered = false }
    }
}

object Alarms {
    private fun pi(ctx: Context, code: Int, action: String) = PendingIntent.getBroadcast(
        ctx, code, Intent(ctx, HiatusWidget::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        if (Widgets.ids(ctx).isEmpty()) { cancel(ctx); return }
        am.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_HALF_HOUR,
            AlarmManager.INTERVAL_HALF_HOUR,
            pi(ctx, 0, HiatusWidget.ACTION_TICK),
        )
        // Just past midnight, so a new day starts at 0.
        am.set(AlarmManager.RTC, Away.dayStart(System.currentTimeMillis(), 1) + 5_000, pi(ctx, 1, HiatusWidget.ACTION_REFRESH))
    }

    /** Due a minute after the screen goes off; delivered when the phone next wakes. */
    fun armWake(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 60_000, pi(ctx, 2, HiatusWidget.ACTION_REFRESH))
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        listOf(0 to HiatusWidget.ACTION_TICK, 1 to HiatusWidget.ACTION_REFRESH, 2 to HiatusWidget.ACTION_REFRESH)
            .forEach { (code, action) -> am.cancel(pi(ctx, code, action)) }
    }
}

object Widgets {
    private val exec = Executors.newSingleThreadExecutor()

    data class Size(val w: Int, val h: Int, val d: Float, val wDp: Int, val hDp: Int)

    fun component(ctx: Context) = ComponentName(ctx, HiatusWidget::class.java)

    fun ids(ctx: Context): IntArray =
        runCatching { AppWidgetManager.getInstance(ctx).getAppWidgetIds(component(ctx)) }.getOrNull() ?: IntArray(0)

    /** A widget's size on the home screen, in px; capped to stay well inside the RemoteViews bitmap budget. */
    fun size(ctx: Context, id: Int): Size {
        val portrait = ctx.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        var wDp = 0
        var hDp = 0
        if (id >= 0) {
            val o = AppWidgetManager.getInstance(ctx).getAppWidgetOptions(id)
            wDp = o.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            hDp = o.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        }
        if (wDp <= 0 || hDp <= 0) { wDp = 170; hDp = 170 }
        val density = ctx.resources.displayMetrics.density
        var w = wDp * density
        var h = hDp * density
        var k = 1f
        val maxPx = 1_000_000f
        if (w * h > maxPx) { k = sqrt(maxPx / (w * h)); w *= k; h *= k }
        return Size(w.toInt(), h.toInt(), density * k, wDp, hDp)
    }

    // Requests are coalesced: while one redraw is queued, more requests just join it.
    private val lock = Any()
    private val pendingIds = LinkedHashSet<Int>()
    private var pendingAll = false
    private var pendingFresh = false
    private var scheduled = false
    private val callbacks = ArrayList<() -> Unit>()

    // Last computed day. Style edits reuse it so the redraw is instant (before OxygenOS freezes the app).
    @Volatile private var cached: Day? = null
    @Volatile private var cachedAt = 0L
    @Volatile private var cachedRules: Rules? = null

    /** The app shares today's numbers it already computed. */
    fun remember(today: Day, rules: Rules) {
        cached = today; cachedRules = rules; cachedAt = SystemClock.elapsedRealtime()
    }

    /**
     * Redraw [ids] (all if null) off the main thread. [fresh] = recompute away time; otherwise recent numbers
     * are reused (look changes).
     */
    fun refresh(ctx: Context, ids: IntArray? = null, fresh: Boolean = true, done: (() -> Unit)? = null) {
        val app = ctx.applicationContext
        synchronized(lock) {
            if (ids == null) pendingAll = true else ids.forEach { pendingIds.add(it) }
            pendingFresh = pendingFresh || fresh
            done?.let { callbacks.add(it) }
            if (scheduled) return
            scheduled = true
        }
        exec.execute { drain(app) }
    }

    private fun drain(app: Context) {
        val all: Boolean; val idsNow: List<Int>; val fresh: Boolean; val cbs: List<() -> Unit>
        synchronized(lock) {
            all = pendingAll; idsNow = pendingIds.toList(); fresh = pendingFresh; cbs = callbacks.toList()
            pendingAll = false; pendingIds.clear(); pendingFresh = false; callbacks.clear(); scheduled = false
        }
        try {
            val targets = if (all) ids(app).toList() else idsNow
            if (targets.isEmpty()) return
            Prefs.init(app)
            val rules = Prefs.rules
            val day = if (!Away.hasAccess(app)) null else {
                val c = cached
                if (!fresh && c != null && cachedRules == rules && SystemClock.elapsedRealtime() - cachedAt < 120_000 &&
                    Away.dayStart(System.currentTimeMillis()) == c.start) c
                else runCatching { Away.today(app, rules) }.getOrNull()?.also { remember(it, rules) }
            }
            val style = Style.load(app)
            val m = AppWidgetManager.getInstance(app)
            targets.forEach { id -> runCatching { draw(app, m, id, style, day, rules) } }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cbs.forEach { runCatching { it() } }
        }
    }

    private fun draw(ctx: Context, m: AppWidgetManager, id: Int, style: Style, day: Day?, rules: Rules) {
        val sz = size(ctx, id)
        val bmp = Renderer.render(ctx, style, sz.w, sz.h, sz.d, day, rules)
        // The root is @android:id/background, so the launcher clips and animates it like its other widgets.
        val rv = RemoteViews(ctx.packageName, R.layout.widget)
        rv.setImageViewBitmap(R.id.img, bmp)
        rv.setContentDescription(R.id.img, Renderer.description(ctx, day))
        rv.setOnClickPendingIntent(R.id.img, tap(ctx, style, id, day == null))
        m.updateAppWidget(id, rv)
    }

    private fun tap(ctx: Context, s: Style, id: Int, noAccess: Boolean): PendingIntent {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(
            ctx, id * 4 + 1,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            flags,
        )
        if (noAccess) return open
        return when (s.tap) {
            Tap.OPEN -> open
            Tap.APP -> ctx.packageManager.getLaunchIntentForPackage(s.tapPackage)?.let {
                PendingIntent.getActivity(ctx, id * 4 + 2, it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags)
            } ?: open
            Tap.REFRESH -> PendingIntent.getBroadcast(
                ctx, id * 4 + 3, Intent(ctx, HiatusWidget::class.java).setAction(HiatusWidget.ACTION_REFRESH), flags,
            )
        }
    }
}
