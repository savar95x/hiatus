package dev.savar.hiatus

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { HiatusTheme { HiatusScreen() } }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HiatusTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme: ColorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive(), content = content)
}

@Composable
private fun HiatusScreen() {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(Usage.hasAccess(ctx)) }
    var report by remember { mutableStateOf<Report?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load(pulled: Boolean) {
        scope.launch {
            if (pulled) refreshing = true
            report = withContext(Dispatchers.Default) { runCatching { Usage.report(ctx) }.getOrNull() }
            refreshing = false
            Widgets.refresh(ctx)
        }
    }

    LifecycleResumeEffect(Unit) {
        granted = Usage.hasAccess(ctx)
        if (granted) load(false) else Widgets.refresh(ctx)
        onPauseOrDispose { }
    }

    AnimatedContent(targetState = granted, label = "access") { ok ->
        if (ok) Dashboard(report, refreshing) { load(true) } else PermissionScreen()
    }
}

// ---------------------------------------------------------------------------------------------
// Permission

@Composable
private fun PermissionScreen() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surface, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            DotText("AWAY", cs.primary, cs.onSurface, Modifier.fillMaxWidth().height(72.dp))
            Spacer(Modifier.height(32.dp))
            Text("Time off your phone", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Needs Usage access to read unlocks and app usage. Nothing leaves your phone.",
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = { openUsageAccess(ctx) }, modifier = Modifier.height(56.dp)) {
                Text("Allow usage access", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private fun openUsageAccess(ctx: Context) {
    val direct = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:" + ctx.packageName))
    val ok = runCatching { ctx.startActivity(direct) }.isSuccess
    if (!ok) runCatching { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
}

// ---------------------------------------------------------------------------------------------
// Dashboard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Dashboard(r: Report?, refreshing: Boolean, onRefresh: () -> Unit) {
    val ctx = LocalContext.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var sel by rememberSaveable { mutableIntStateOf(-1) } // -1 = today
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Hiatus") },
                actions = {
                    IconButton(onClick = { pinWidget(ctx) }) { Icon(Icons.Rounded.Widgets, "Add widget") }
                },
                scrollBehavior = scroll,
            )
        },
    ) { pad ->
        if (r == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@Scaffold
        }
        val idx = if (sel in r.days.indices) sel else r.days.lastIndex
        val day = r.days[idx]
        val isToday = idx == r.days.lastIndex
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.padding(pad)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { HeroCard(r) }
                item { WeekCard(r.days, idx) { sel = it } }
                item { StatsGrid(day, isToday) }
                item { HourlyCard(day, if (isToday) r.now else null) }
                if (day.apps.isNotEmpty()) {
                    item {
                        Text(
                            "Top apps",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
                        )
                    }
                    val top = day.apps.take(10)
                    val max = top.first().ms.coerceAtLeast(1)
                    itemsIndexed(top, key = { _, a -> a.pkg }) { i, a ->
                        AppRow(a, a.ms.toFloat() / max, first = i == 0, last = i == top.lastIndex)
                    }
                }
            }
        }
    }
}

private fun pinWidget(ctx: Context) {
    val m = AppWidgetManager.getInstance(ctx)
    if (m.isRequestPinAppWidgetSupported) m.requestPinAppWidget(Widgets.component(ctx), null, null)
    else Toast.makeText(ctx, "Add it from your home screen's widget list", Toast.LENGTH_LONG).show()
}

@Composable
private fun DotText(text: String, lit: Color, unit: Color, modifier: Modifier, wrap: Boolean = false) {
    Canvas(modifier) {
        val f = DotMatrix.fit(text, size.width, size.height, wrap)
        drawIntoCanvas {
            DotMatrix.draw(it.nativeCanvas, f, 0f, (size.height - f.height) / 2, lit.toArgb(), unit.toArgb(), unit.copy(alpha = 0.08f).toArgb())
        }
    }
}

@Composable
private fun HeroCard(r: Report) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val a = r.away
    Surface(color = cs.primaryContainer, shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Text(
                if (a?.ongoing == true) "AWAY NOW" else "LAST AWAY",
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.6.sp),
                color = cs.onPrimaryContainer.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(16.dp))
            DotText(a?.let { Fmt.dur(it.ms) } ?: "0m", cs.primary, cs.onPrimaryContainer, Modifier.fillMaxWidth().height(64.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                when {
                    a == null -> "No break of a minute or more yet"
                    a.ongoing -> "Since " + Fmt.time(ctx, a.start)
                    else -> Fmt.time(ctx, a.start) + " – " + Fmt.time(ctx, a.end)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onPrimaryContainer,
            )
            Text(
                "Away today " + Fmt.dur(r.today.awayMs) + "  ·  longest " + Fmt.dur(r.today.longestAwayMs),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onPrimaryContainer.copy(alpha = 0.75f),
            )
        }
    }
}

private fun dayName(d: DayStats, today: Boolean): String {
    if (today) return "Today"
    val yesterday = Usage.dayStart(System.currentTimeMillis(), -1)
    if (d.start == yesterday) return "Yesterday"
    return SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(d.start))
}

@Composable
private fun WeekCard(days: List<DayStats>, sel: Int, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val max = days.maxOf { it.screenTimeMs }.coerceAtLeast(3_600_000L)
    val tracked = days.filter { it.screenTimeMs > 0 }
    val avg = if (tracked.isEmpty()) 0L else tracked.sumOf { it.screenTimeMs } / tracked.size
    val letter = SimpleDateFormat("EEEEE", Locale.getDefault())
    Surface(color = cs.surfaceContainer, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(dayName(days[sel], sel == days.lastIndex), style = MaterialTheme.typography.titleMedium)
                    Text("Avg " + Fmt.dur(avg) + " a day", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                Text(
                    Fmt.dur(days[sel].screenTimeMs),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.primary,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().height(136.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                days.forEachIndexed { i, d ->
                    val frac by animateFloatAsState((d.screenTimeMs.toFloat() / max).coerceIn(0.05f, 1f), label = "bar")
                    val selected = i == sel
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).clickable { onSelect(i) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            Box(
                                Modifier.fillMaxWidth(0.62f).fillMaxHeight(frac)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (selected) cs.primary else cs.secondaryContainer),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            letter.format(Date(d.start)),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) cs.primary else cs.onSurfaceVariant,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatsGrid(d: DayStats, today: Boolean) {
    val tiles = listOf(
        Tile(Icons.Rounded.Smartphone, Fmt.dur(d.screenTimeMs), "Screen time", MaterialShapes.Cookie9Sided),
        Tile(Icons.Rounded.SelfImprovement, Fmt.dur(d.awayMs), if (today) "Away so far" else "Hiatus", MaterialShapes.Clover4Leaf),
        Tile(Icons.Rounded.LockOpen, d.unlocks.toString(), "Unlocks", MaterialShapes.Pill),
        Tile(Icons.Rounded.TouchApp, d.pickups.toString(), "Screen wakes", MaterialShapes.SoftBurst),
        Tile(Icons.Rounded.Timelapse, Fmt.dur(d.avgSessionMs), "Per session", MaterialShapes.Sunny),
        Tile(Icons.Rounded.Spa, Fmt.dur(d.longestAwayMs), "Longest away", MaterialShapes.Flower),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { StatTile(it, Modifier.weight(1f)) }
            }
        }
    }
}

private class Tile(val icon: ImageVector, val value: String, val label: String, val shape: androidx.graphics.shapes.RoundedPolygon)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatTile(t: Tile, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surfaceContainerHigh, shape = RoundedCornerShape(24.dp), modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Box(
                Modifier.size(40.dp).clip(t.shape.toShape()).background(cs.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(t.icon, null, tint = cs.onSecondaryContainer, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(t.value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(t.label, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun HourlyCard(d: DayStats, now: Long?) {
    val cs = MaterialTheme.colorScheme
    val nowHour = now?.let { Calendar.getInstance().apply { timeInMillis = it }.get(Calendar.HOUR_OF_DAY) }
    val peak = d.hourly.indices.maxByOrNull { d.hourly[it] }?.takeIf { d.hourly[it] > 0 }
    Surface(color = cs.surfaceContainer, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("By hour", style = MaterialTheme.typography.titleMedium)
            Text(
                peak?.let { "Busiest " + hourLabel(it) + " – " + hourLabel((it + 1) % 24) } ?: "No screen time",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            val accent = cs.primary
            val used = cs.primary.copy(alpha = 0.5f)
            val track = cs.secondaryContainer
            val future = cs.secondaryContainer.copy(alpha = 0.45f)
            Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                val gapK = 0.45f
                val bw = size.width / (24 + 23 * gapK)
                val gap = bw * gapK
                for (h in 0 until 24) {
                    val frac = (d.hourly[h] / 3_600_000f).coerceIn(0f, 1f)
                    val bh = (size.height * frac).coerceAtLeast(bw)
                    val color = when {
                        nowHour != null && h == nowHour -> accent
                        nowHour != null && h > nowHour -> future
                        frac > 0f -> if (h == peak) accent else used
                        else -> track
                    }
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(h * (bw + gap), size.height - bh),
                        size = Size(bw, bh),
                        cornerRadius = CornerRadius(bw / 2, bw / 2),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf("12 AM", "6 AM", "12 PM", "6 PM").forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun hourLabel(h: Int): String = when {
    h == 0 -> "12 AM"
    h < 12 -> "$h AM"
    h == 12 -> "12 PM"
    else -> "${h - 12} PM"
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppRow(a: AppUsage, frac: Float, first: Boolean, last: Boolean) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val big = 24.dp
    val small = 6.dp
    val shape = RoundedCornerShape(
        topStart = if (first) big else small, topEnd = if (first) big else small,
        bottomStart = if (last) big else small, bottomEnd = if (last) big else small,
    )
    val icon by produceState<ImageBitmap?>(null, a.pkg) {
        value = withContext(Dispatchers.IO) {
            runCatching { ctx.packageManager.getApplicationIcon(a.pkg).toBitmap(128, 128).asImageBitmap() }.getOrNull()
        }
    }
    val label = remember(a.pkg) { Usage.label(ctx, a.pkg) }
    Surface(color = cs.surfaceContainer, shape = shape, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                icon?.let { Image(it, null, Modifier.size(40.dp)) }
                    ?: Box(Modifier.size(40.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(cs.secondaryContainer))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(Fmt.dur(a.ms), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                LinearWavyProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
