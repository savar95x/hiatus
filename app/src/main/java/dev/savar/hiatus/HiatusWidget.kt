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
        Widgets.refresh(context, intArrayOf(id)) { pr.finish() }
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
            Widgets.refresh(ctx)
        }
    }

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

    fun component(ctx: Context) = ComponentName(ctx, HiatusWidget::class.java)

    fun ids(ctx: Context): IntArray =
        AppWidgetManager.getInstance(ctx).getAppWidgetIds(component(ctx)) ?: IntArray(0)

    /** Recompute today's stats once and redraw [ids] (all widgets if null) off the main thread. */
    fun refresh(ctx: Context, ids: IntArray? = null, done: (() -> Unit)? = null) {
        val app = ctx.applicationContext
        exec.execute {
            try {
                val targets = ids ?: ids(app)
                if (targets.isNotEmpty()) {
                    val report = if (Usage.hasAccess(app)) runCatching { Usage.today(app) }.getOrNull() else null
                    val m = AppWidgetManager.getInstance(app)
                    targets.forEach { draw(app, m, it, report) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                done?.invoke()
            }
        }
    }

    private fun draw(ctx: Context, m: AppWidgetManager, id: Int, report: Report?) {
        val o = m.getAppWidgetOptions(id)
        val portrait = ctx.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        var wDp = o.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        var hDp = o.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        if (wDp <= 0) wDp = 160
        if (hDp <= 0) hDp = 160
        val density = ctx.resources.displayMetrics.density
        var w = wDp * density
        var h = hDp * density
        var k = 1f
        val maxPx = 1_200_000f // stay well inside the RemoteViews bitmap budget
        if (w * h > maxPx) { k = sqrt(maxPx / (w * h)); w *= k; h *= k }

        val bmp = WidgetRenderer.render(ctx, w.toInt(), h.toInt(), density * k, wDp, hDp, report)
        val rv = RemoteViews(ctx.packageName, R.layout.widget_root)
        rv.setImageViewBitmap(R.id.img, bmp)
        rv.setContentDescription(R.id.img, WidgetRenderer.description(ctx, report))
        rv.setOnClickPendingIntent(
            android.R.id.background,
            PendingIntent.getActivity(
                ctx, 1,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        m.updateAppWidget(id, rv)
    }
}
