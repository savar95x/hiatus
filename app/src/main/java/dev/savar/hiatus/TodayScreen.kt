package dev.savar.hiatus

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.lerp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** "2h 13m" with the unit letters at a smaller size, like Digital Wellbeing. */
internal fun richDuration(text: String, unitScale: Float = 0.55f): AnnotatedString = buildAnnotatedString {
    for (ch in text) {
        if (ch.isDigit() || ch == ':' || ch == '.') append(ch)
        else withStyle(SpanStyle(fontSize = unitScale.em)) { append(ch) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TodayScreen(
    report: Report?,
    granted: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scheme = MaterialTheme.colorScheme
    val black = isAppDark() && ThemePrefs.pureBlack
    var sel by rememberSaveable { mutableIntStateOf(-1) } // -1 = today
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = { Text("Hiatus") },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, contentDescription = "Settings") }
                },
                scrollBehavior = scroll,
                colors = if (black) TopAppBarDefaults.topAppBarColors(scrolledContainerColor = scheme.surface)
                else TopAppBarDefaults.topAppBarColors(),
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                !granted -> PermissionCard()
                report == null -> LoadingIndicator(Modifier.align(Alignment.Center))
                else -> {
                    val idx = if (sel in report.days.indices) sel else report.days.lastIndex
                    val day = report.days[idx]
                    val isToday = idx == report.days.lastIndex
                    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            HeroCard(report)
                            WeekCard(report.days, idx) { sel = it }
                            DonutCard(day, isToday)
                            StatsGroup(day, isToday)
                            HourlyCard(day, if (isToday) report.now else null)
                            AppsGroup(day)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PermissionCard() {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(120.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(scheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.HourglassBottom, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(48.dp)) }
        Spacer(Modifier.height(28.dp))
        Text("See your time off the phone", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Hiatus reads unlocks and app usage on this phone. Nothing leaves it.",
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = { openUsageAccess(ctx) }, modifier = Modifier.height(56.dp)) {
            Text("Allow usage access", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Find Hiatus in the list and switch it on",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
    }
}

internal fun openUsageAccess(ctx: Context) {
    val direct = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:" + ctx.packageName))
    val ok = runCatching { ctx.startActivity(direct) }.isSuccess
    if (!ok) runCatching { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroCard(r: Report) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val a = r.away
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    Surface(color = scheme.primaryContainer, shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (a?.ongoing == true) "Away now" else "Last away",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                AnimatedContent(Fmt.dur(a?.ms ?: 0), label = "away") { v ->
                    Text(
                        richDuration(v),
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Medium,
                        color = scheme.onPrimaryContainer,
                        maxLines = 1,
                    )
                }
                Text(
                    when {
                        a == null -> "No break yet today"
                        a.ongoing -> "Since " + Fmt.time(ctx, a.start)
                        else -> Fmt.time(ctx, a.start) + " – " + Fmt.time(ctx, a.end)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.fillMaxSize().rotate(angle).clip(MaterialShapes.SoftBurst.toShape()).background(scheme.primary),
                )
                Icon(Icons.Rounded.SelfImprovement, null, tint = scheme.onPrimary, modifier = Modifier.size(36.dp))
            }
        }
    }
}

private fun dayName(d: DayStats, today: Boolean): String {
    if (today) return "Today"
    if (d.start == Usage.dayStart(System.currentTimeMillis(), -1)) return "Yesterday"
    return SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(Date(d.start))
}

@Composable
private fun WeekCard(days: List<DayStats>, sel: Int, onSelect: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val max = days.maxOf { it.screenTimeMs }.coerceAtLeast(3_600_000L)
    val tracked = days.filter { it.screenTimeMs > 0 }
    val avg = if (tracked.isEmpty()) 0L else tracked.sumOf { it.screenTimeMs } / tracked.size
    val dayFmt = remember { SimpleDateFormat("EEE", Locale.getDefault()) }
    Surface(color = scheme.surfaceContainer, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("This week", style = MaterialTheme.typography.titleMedium)
                    Text("Daily average " + Fmt.dur(avg), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
                AnimatedContent(Fmt.dur(days[sel].screenTimeMs), label = "selTotal") { v ->
                    Text(richDuration(v), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium, color = scheme.primary)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().height(148.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                days.forEachIndexed { i, d ->
                    val frac by animateFloatAsState((d.screenTimeMs.toFloat() / max).coerceIn(0.06f, 1f), label = "bar")
                    val selected = i == sel
                    val color by animateColorAsState(if (selected) scheme.primary else scheme.secondaryContainer, label = "barColor")
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp))
                            .clickable {
                                if (!selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(i)
                            }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            Box(Modifier.fillMaxWidth(0.58f).fillMaxHeight(frac).clip(RoundedCornerShape(50)).background(color))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            dayFmt.format(Date(d.start)),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

/** App share of the day's screen time, as a donut (Digital Wellbeing's dashboard). */
@Composable
private fun DonutCard(day: DayStats, isToday: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val colors = listOf(scheme.primary, scheme.tertiary, scheme.secondary, scheme.primary.copy(alpha = 0.55f), scheme.tertiary.copy(alpha = 0.55f))
    val other = scheme.surfaceVariant
    val top = day.apps.take(5)
    val total = day.screenTimeMs.coerceAtLeast(1)
    val rest = (day.screenTimeMs - top.sumOf { it.ms }).coerceAtLeast(0)
    val slices = top.mapIndexed { i, a -> a.ms.toFloat() / total to colors[i] } +
        (if (rest > 0) listOf(rest.toFloat() / total to other) else emptyList())
    val grow = remember(day.start) { Animatable(0f) }
    LaunchedEffect(day.start) { grow.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    Surface(color = scheme.surfaceContainer, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(dayName(day, isToday), style = MaterialTheme.typography.titleMedium)
            Text("Screen time", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(212.dp)) {
                    val stroke = 30.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    if (slices.isEmpty()) {
                        drawArc(other, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    } else {
                        val gap = if (slices.size > 1) 2.5f else 0f
                        var start = -90f
                        for ((frac, color) in slices) {
                            val sweep = 360f * frac * grow.value
                            if (sweep - gap > 0.3f) {
                                drawArc(color, start + gap / 2, sweep - gap, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                            }
                            start += sweep
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        richDuration(Fmt.dur(day.screenTimeMs)),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text("${day.unlocks} unlocks", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            }
            if (top.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                ChipFlow {
                    top.forEachIndexed { i, a ->
                        val label = remember(a.pkg) { Usage.label(ctx, a.pkg) }
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(scheme.surfaceContainerHigh).padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(colors[i]))
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatRow(icon: ImageVector, title: String, value: String, supporting: String? = null) {
    val scheme = MaterialTheme.colorScheme
    ListItem(
        leadingContent = { RowIcon(icon) },
        headlineContent = { Text(title) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = { Text(value, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatsGroup(d: DayStats, isToday: Boolean) {
    Group("Details") {
        item { StatRow(Icons.Rounded.SelfImprovement, if (isToday) "Away so far" else "Away", Fmt.dur(d.awayMs)) }
        item { StatRow(Icons.Rounded.Spa, "Longest break", Fmt.dur(d.longestAwayMs)) }
        item { StatRow(Icons.Rounded.LockOpen, "Unlocks", d.unlocks.toString()) }
        item { StatRow(Icons.Rounded.Visibility, "Screen wakes", d.pickups.toString()) }
        item { StatRow(Icons.Rounded.Timelapse, "Per session", Fmt.dur(d.avgSessionMs), "${d.sessions} sessions") }
    }
}

@Composable
private fun HourlyCard(d: DayStats, now: Long?) {
    val scheme = MaterialTheme.colorScheme
    val nowHour = now?.let { Calendar.getInstance().apply { timeInMillis = it }.get(Calendar.HOUR_OF_DAY) }
    val peak = d.hourly.indices.maxByOrNull { d.hourly[it] }?.takeIf { d.hourly[it] > 0 }
    Surface(color = scheme.surfaceContainer, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("By hour", style = MaterialTheme.typography.titleMedium)
            Text(
                peak?.let { "Busiest " + hourLabel(it) + " – " + hourLabel((it + 1) % 24) } ?: "No screen time",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            val accent = scheme.primary
            val used = scheme.primary.copy(alpha = 0.55f)
            val track = scheme.secondaryContainer
            val future = scheme.secondaryContainer.copy(alpha = 0.45f)
            Canvas(Modifier.fillMaxWidth().height(112.dp)) {
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
                    drawRoundRect(color, Offset(h * (bw + gap), size.height - bh), Size(bw, bh), CornerRadius(bw / 2, bw / 2))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf("12 AM", "6 AM", "12 PM", "6 PM").forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
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
private fun AppsGroup(day: DayStats) {
    val top = day.apps.take(10)
    if (top.isEmpty()) return
    val max = top.first().ms.coerceAtLeast(1)
    Group("Top apps") {
        top.forEach { a -> item { AppRow(a, a.ms.toFloat() / max) } }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppRow(a: AppUsage, frac: Float) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val icon by produceState<ImageBitmap?>(null, a.pkg) {
        value = withContext(Dispatchers.IO) {
            runCatching { ctx.packageManager.getApplicationIcon(a.pkg).toBitmap(128, 128).asImageBitmap() }.getOrNull()
        }
    }
    val label = remember(a.pkg) { Usage.label(ctx, a.pkg) }
    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            icon?.let { Image(it, null, Modifier.size(40.dp)) }
                ?: Box(Modifier.size(40.dp).clip(CircleShape).background(scheme.secondaryContainer))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(Fmt.dur(a.ms), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            val w by animateFloatAsState(frac.coerceIn(0.02f, 1f), tween(700, easing = FastOutSlowInEasing), label = "appBar")
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(scheme.surfaceContainerHighest)) {
                Box(Modifier.fillMaxWidth(w).fillMaxHeight().clip(RoundedCornerShape(50)).background(scheme.primary))
            }
        }
    }
}
