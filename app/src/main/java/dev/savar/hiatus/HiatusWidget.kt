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
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import kotlin.math.sqrt

class HiatusWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED -> {
                val pr = goAsync()
                Widgets.refresh(context) { pr.finish() }
                Refresher.schedule(context)
            }
            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pr = goAsync()
        Widgets.refresh(context, ids) { pr.finish() }
        Refresher.schedule(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        val pr = goAsync()
        Widgets.refresh(context, intArrayOf(id), fresh = false) { pr.finish() }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { ConfigStore.delete(context, it) }
    }

    override fun onEnabled(context: Context) = Refresher.schedule(context)
    override fun onDisabled(context: Context) = Refresher.cancel(context)

    companion object {
        const val ACTION_REFRESH = "dev.savar.hiatus.REFRESH"
    }
}

/**
 * While the process is alive, redraw the moment the screen turns on or the phone is unlocked, so the widget
 * shows the stretch you just came back from. The 15-minute alarm keeps things roughly fresh otherwise (and,
 * being non-wakeup, also fires right as the phone wakes after a long sleep).
 */
class HiatusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ScreenListener.register(this)
        Refresher.schedule(this)
    }
}

object ScreenListener {
    private var registered = false
    private var lastNight = -1

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (Widgets.ids(ctx).isEmpty()) return
            if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED) {
                // Only dark/light switches change how the widget looks.
                val night = ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                if (night == lastNight) return
                lastNight = night
            }
            // Screen-on and unlock arrive a second or so apart: one redraw, after the unlock settles.
            pending?.let { handler.removeCallbacks(it) }
            val r = Runnable { pending = null; Widgets.refresh(ctx) }
            pending = r
            handler.postDelayed(r, if (intent.action == Intent.ACTION_SCREEN_ON) 1_500L else 300L)
        }
    }
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pending: Runnable? = null

    fun register(ctx: Context) {
        if (registered) return
        registered = true
        lastNight = ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
        }
        runCatching {
            ContextCompat.registerReceiver(ctx.applicationContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }.onFailure { registered = false }
    }
}

object Refresher {
    private fun pending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 0,
        Intent(ctx, HiatusWidget::class.java).setAction(HiatusWidget.ACTION_REFRESH),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun schedule(ctx: Context) {
        if (Widgets.ids(ctx).isEmpty()) { cancel(ctx); return }
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
            AlarmManager.INTERVAL_FIFTEEN_MINUTES,
            pending(ctx),
        )
    }

    fun cancel(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java)?.cancel(pending(ctx))
    }
}

object Widgets {
    private val exec = Executors.newSingleThreadExecutor()

    data class SizePx(val w: Int, val h: Int, val dp: Float, val wDp: Int, val hDp: Int)

    fun component(ctx: Context) = ComponentName(ctx, HiatusWidget::class.java)

    fun ids(ctx: Context): IntArray =
        AppWidgetManager.getInstance(ctx).getAppWidgetIds(component(ctx)) ?: IntArray(0)

    /** A widget's size on the home screen; for [id] < 0 (no widget yet) the usual 2×2. */
    fun size(ctx: Context, id: Int): SizePx {
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
        val maxPx = 1_200_000f // stay well inside the RemoteViews bitmap budget
        if (w * h > maxPx) { k = sqrt(maxPx / (w * h)); w *= k; h *= k }
        return SizePx(w.toInt(), h.toInt(), density * k, wDp, hDp)
    }

    // Requests are coalesced: while one redraw is queued, further requests just add their widget ids to it.
    private val lock = Any()
    private val pendingIds = LinkedHashSet<Int>()
    private var pendingAll = false
    private var pendingFresh = false
    private var scheduled = false
    private val callbacks = ArrayList<() -> Unit>()

    // Last computed stats. Settings changes reuse them so the redraw is instant: reading 48 hours of usage events
    // takes a second or two, long enough for Android to freeze the app if you go home right after an edit.
    @Volatile private var cached: Report? = null
    @Volatile private var cachedAt = 0L
    private const val CACHE_MS = 90_000L

    /** Share stats the app already computed (any number of days; widgets use today). */
    fun remember(report: Report) {
        cached = report
        cachedAt = SystemClock.elapsedRealtime()
    }

    /**
     * Redraw [ids] (all widgets if null) off the main thread. [fresh] recomputes usage (screen-on, unlock, timer);
     * otherwise recent stats are reused (settings edits).
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
        val all: Boolean
        val idsNow: List<Int>
        val fresh: Boolean
        val cbs: List<() -> Unit>
        synchronized(lock) {
            all = pendingAll; idsNow = pendingIds.toList(); fresh = pendingFresh; cbs = callbacks.toList()
            pendingAll = false; pendingIds.clear(); pendingFresh = false; callbacks.clear(); scheduled = false
        }
        try {
            val targets = if (all) ids(app).toList() else idsNow
            if (targets.isEmpty()) return
            val report = if (!Usage.hasAccess(app)) null else {
                val c = cached
                if (!fresh && c != null && SystemClock.elapsedRealtime() - cachedAt < CACHE_MS) c
                else runCatching { Usage.today(app) }.getOrNull()?.also { remember(it) }
            }
            val m = AppWidgetManager.getInstance(app)
            targets.forEach { runCatching { draw(app, m, it, report) } }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cbs.forEach { runCatching { it() } }
        }
    }

    private fun draw(ctx: Context, m: AppWidgetManager, id: Int, report: Report?) {
        val cfg = ConfigStore.load(ctx, id)
        val sz = size(ctx, id)
        val bmp = WidgetRenderer.render(ctx, cfg, sz.w, sz.h, sz.dp, report)
        val rv = RemoteViews(ctx.packageName, R.layout.widget_root)
        rv.setImageViewBitmap(R.id.img, bmp)
        rv.setContentDescription(R.id.img, WidgetRenderer.description(report))
        tapIntent(ctx, cfg, id, report == null)?.let { rv.setOnClickPendingIntent(android.R.id.background, it) }
        m.updateAppWidget(id, rv)
    }

    private fun tapIntent(ctx: Context, cfg: WidgetConfig, id: Int, noAccess: Boolean): PendingIntent? {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(
            ctx, id * 4 + 1,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            flags,
        )
        if (noAccess) return open
        return when (cfg.tapAction) {
            TapAction.HIATUS -> open
            TapAction.APP -> ctx.packageManager.getLaunchIntentForPackage(cfg.tapPackage)?.let {
                PendingIntent.getActivity(ctx, id * 4 + 2, it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags)
            } ?: open
            TapAction.REFRESH -> PendingIntent.getBroadcast(
                ctx, id * 4 + 3,
                Intent(ctx, HiatusWidget::class.java).setAction(HiatusWidget.ACTION_REFRESH),
                flags,
            )
        }
    }
}
