@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.savar.hiatus

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class Tab(val title: String, val icon: ImageVector) {
    HOME("Away", Icons.Rounded.SelfImprovement),
    WIDGET("Widget", Icons.Rounded.Widgets),
    SETTINGS("Settings", Icons.Rounded.Settings),
}

/** The one activity; also the widget's settings screen (long-press the widget, then its settings). */
class MainActivity : ComponentActivity() {
    private var tab by mutableStateOf(Tab.HOME)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        handle(intent)
        setContent { HiatusTheme { App(tab) { tab = it } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == AppWidgetManager.ACTION_APPWIDGET_CONFIGURE) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            tab = Tab.WIDGET
        }
    }
}

@Composable
fun App(tab: Tab, onTab: (Tab) -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(Away.hasAccess(ctx)) }
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        val now = Away.hasAccess(ctx)
        if (now != granted) Widgets.refresh(ctx) // the widget was showing "Tap to set up"
        granted = now
        tick++
        Updates.check(ctx) // at most once an hour; a newer release shows on Home
        onPauseOrDispose { }
    }
    val rules = Prefs.rules
    val today by produceState<Day?>(null, granted, rules, tick) {
        value = if (!granted) null else withContext(Dispatchers.Default) { runCatching { Away.today(ctx, rules) }.getOrNull() }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { onTab(t) },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.title) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
            AnimatedContent(tab, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "tab") { t ->
                when (t) {
                    Tab.HOME -> HomeScreen(granted)
                    Tab.WIDGET -> WidgetEditor(today, rules)
                    Tab.SETTINGS -> SettingsScreen(granted)
                }
            }
        }
    }
}
