@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.savar.hiatus

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Everything [HomeContent] needs: the week shown, which day is picked, how far back you can go. */
class HomeData(val days: List<Day>, val weeks: Int, val canGoBack: Boolean, val now: Long)

/** Home: loads the week (on resume, and when settings or the week change) and shows it. */
@Composable
fun HomeScreen(granted: Boolean) {
    val ctx = LocalContext.current
    var weeks by rememberSaveable { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose {} }
    val rules = Prefs.rules
    val data by produceState<HomeData?>(null, granted, weeks, rules, tick) {
        if (!granted) return@produceState
        value = withContext(Dispatchers.Default) {
            val now = System.currentTimeMillis()
            val days = runCatching { Away.week(ctx, rules, weeks, now) }.getOrDefault(emptyList())
            if (weeks == 0) days.lastOrNull { !it.isFuture }?.let { Widgets.remember(it, rules) }
            val earliest = History.earliest(ctx) ?: Away.dayStart(now, -9)
            HomeData(days, weeks, Away.weekStart(now, weeks) > earliest, now)
        }
    }
    if (!granted) AccessGate() else HomeContent(data, rules, onWeek = { weeks = it })
}

@Composable
fun HomeContent(data: HomeData?, rules: Rules, onWeek: (Int) -> Unit) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // Picked day: an index in the week, -1 = today / the week's last day.
    var picked by rememberSaveable(data?.weeks) { mutableIntStateOf(-1) }
    val days = data?.days.orEmpty()
    val last = days.indexOfLast { !it.isFuture }.coerceAtLeast(0)
    val idx = if (picked in days.indices && !days[picked].isFuture) picked else last
    val day = days.getOrNull(idx)
    val isToday = day != null && data != null && day.start == Away.dayStart(data.now)

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = { Text(if (day == null) "Hiatus" else dayTitle(day, data!!.now)) },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { pad ->
        if (data == null || day == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Hero(day, rules, isToday)
            if (day.hasData) DayStrip(day)
            WeekCard(data, idx, onPick = { picked = it }, onWeek = { onWeek(it); picked = -1 })
            if (day.hasData) {
                Breaks(day, rules, isToday)
                CheckIns(day)
            }
            Footnote(rules)
        }
    }
}

private fun dayTitle(d: Day, now: Long): String = when (d.start) {
    Away.dayStart(now) -> "Today"
    Away.dayStart(now, -1) -> "Yesterday"
    else -> Fmt.weekday(d.start, short = false) + ", " + Fmt.date(d.start)
}

// ------------------------------------------------------------------ hero

@Composable
private fun Hero(day: Day, rules: Rules, isToday: Boolean) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val share by animateFloatAsState(day.share, tween(900, easing = FastOutSlowInEasing), label = "share")
    Surface(color = scheme.primaryContainer, shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth()) {
        Box {
            // A soft Material shape in the corner, the expressive touch.
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 36.dp, y = (-34).dp).size(150.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(scheme.onPrimaryContainer.copy(alpha = 0.06f)),
            )
            Column(Modifier.padding(24.dp)) {
                Text(
                    if (isToday) "Away today" else "Away",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                if (!day.hasData) {
                    Spacer(Modifier.height(8.dp))
                    Text("No record for this day", style = MaterialTheme.typography.headlineSmall, color = scheme.onPrimaryContainer)
                    Text(
                        "The phone was off, or Hiatus couldn't see it yet.",
                        style = MaterialTheme.typography.bodyMedium, color = scheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                    return@Column
                }
                AnimatedContent(day.awayMs / 60_000, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "away") { m ->
                    Text(
                        durText(m * 60_000),
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Medium,
                        color = scheme.onPrimaryContainer,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(16.dp))
                LinearWavyProgressIndicator(
                    progress = { share },
                    modifier = Modifier.fillMaxWidth(),
                    color = scheme.primary,
                    trackColor = scheme.onPrimaryContainer.copy(alpha = 0.14f),
                )
                Spacer(Modifier.height(14.dp))
                Text(Fmt.shareText(day, rules), style = MaterialTheme.typography.titleMedium, color = scheme.onPrimaryContainer)
                val counted = day.counted
                val sub = when {
                    rules.sleep != SleepMode.OFF && counted.isNotEmpty() && day.sleep.isNotEmpty() ->
                        "Counting " + Fmt.dur(day.countedMs) + " awake, sleep left out"
                    isToday -> "Since midnight, " + Fmt.dur(day.countedMs) + " so far"
                    else -> null
                }
                if (sub != null) {
                    Text(sub, style = MaterialTheme.typography.bodyMedium, color = scheme.onPrimaryContainer.copy(alpha = 0.75f))
                }
            }
        }
    }
}

// ------------------------------------------------------------------ the day, hour by hour

/** The day as one strip: away in the accent colour, phone in use as gaps, sleep (left out) hatched. */
@Composable
private fun DayStrip(day: Day) {
    val scheme = MaterialTheme.colorScheme
    InfoCard("Through the day") {
        val span = (day.end - day.start).toFloat()
        val away = scheme.primary
        val track = scheme.surfaceContainerHighest
        val sleepC = scheme.tertiary.copy(alpha = 0.45f)
        val future = scheme.surfaceContainerHigh
        Canvas(Modifier.fillMaxWidth().height(44.dp)) {
            val r = CornerRadius(14.dp.toPx())
            val clip = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, r)) }
            fun x(t: Long) = ((t - day.start) / span).coerceIn(0f, 1f) * size.width
            clipPath(clip) {
                drawRect(future)
                drawRect(track, size = Size(x(day.until), size.height))
                day.sleep.forEach { s ->
                    drawRect(sleepC, topLeft = Offset(x(s.start), 0f), size = Size(x(s.end) - x(s.start), size.height))
                    // hatching
                    val step = 7.dp.toPx()
                    var hx = x(s.start) - size.height
                    while (hx < x(s.end)) {
                        drawLine(scheme.tertiary.copy(alpha = 0.35f), Offset(hx, size.height), Offset(hx + size.height, 0f), 1.5.dp.toPx())
                        hx += step
                    }
                }
                day.breaks.forEach { b ->
                    val w = (x(b.end) - x(b.start)).coerceAtLeast(1.5f)
                    drawRect(away, topLeft = Offset(x(b.start), 0f), size = Size(w, size.height))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val ctx = LocalContext.current
            listOf(0, 6, 12, 18, 24).forEach { h ->
                Text(
                    if (h == 24) "" else Fmt.clock(ctx, h * 60).replace(":00", ""),
                    style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend(scheme.primary, "Away")
            Legend(scheme.surfaceContainerHighest, "Phone in use")
            if (day.sleep.isNotEmpty()) Legend(scheme.tertiary.copy(alpha = 0.6f), "Sleep")
        }
    }
}

@Composable
private fun Legend(c: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ------------------------------------------------------------------ week

@Composable
private fun WeekCard(data: HomeData, sel: Int, onPick: (Int) -> Unit, onWeek: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val days = data.days
    val known = days.filter { it.hasData }
    val avg = if (known.isEmpty()) 0L else known.sumOf { it.awayMs } / known.size
    val max = days.maxOfOrNull { it.awayMs }?.coerceAtLeast(3_600_000L) ?: 3_600_000L
    val title = when (data.weeks) {
        0 -> "This week"
        -1 -> "Last week"
        else -> Fmt.date(days.first().start) + " – " + Fmt.date(days.last().start)
    }
    Surface(color = scheme.surfaceContainer, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(top = 6.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (known.isEmpty()) "No record" else "Average " + Fmt.dur(avg) + " a day",
                        style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                    )
                }
                FilledTonalIconButton(onClick = { onWeek(data.weeks - 1) }, enabled = data.canGoBack) {
                    Icon(Icons.Rounded.ChevronLeft, "Previous week")
                }
                FilledTonalIconButton(onClick = { onWeek(data.weeks + 1) }, enabled = data.weeks < 0) {
                    Icon(Icons.Rounded.ChevronRight, "Next week")
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().padding(end = 12.dp).height(164.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                days.forEachIndexed { i, d ->
                    val selected = i == sel
                    val frac by animateFloatAsState(
                        if (!d.hasData) 0.04f else (d.awayMs.toFloat() / max).coerceIn(0.05f, 1f),
                        tween(700, easing = FastOutSlowInEasing), label = "bar",
                    )
                    val color by animateColorAsState(
                        when {
                            !d.hasData -> scheme.surfaceContainerHighest
                            selected -> scheme.primary
                            else -> scheme.secondaryContainer
                        }, label = "barColor",
                    )
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp))
                            .clickable(enabled = !d.isFuture) {
                                if (!selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onPick(i)
                            }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            if (d.hasData) Fmt.dur(d.awayMs).substringBefore(' ') else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            Box(Modifier.fillMaxWidth(0.62f).fillMaxHeight(frac).clip(RoundedCornerShape(50)).background(color))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            Fmt.weekday(d.start).take(3),
                            style = MaterialTheme.typography.labelLarge,
                            color = when {
                                selected -> scheme.primary
                                d.isFuture -> scheme.onSurfaceVariant.copy(alpha = 0.5f)
                                else -> scheme.onSurfaceVariant
                            },
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ breaks & check-ins

@Composable
private fun Breaks(day: Day, rules: Rules, isToday: Boolean) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    Section("Breaks") {
    TileGrid(
        listOf(
            Tile(Icons.Rounded.Spa, "Longest", day.longest?.let { durText(it.ms) } ?: AnnotatedString("–"),
                day.longest?.let { Fmt.span(ctx, it, day.ongoing && it == day.lastBreak) }, MaterialShapes.Cookie9Sided),
            Tile(Icons.Rounded.Timelapse, "Average", if (day.breaks.isEmpty()) AnnotatedString("–") else durText(day.averageMs),
                null, MaterialShapes.Sunny),
            Tile(Icons.Rounded.SelfImprovement, "Breaks", AnnotatedString(day.breaks.size.toString()),
                "${Fmt.minBreak(rules.minBreakMs)} or longer", MaterialShapes.Clover4Leaf),
            Tile(Icons.Rounded.History, if (day.ongoing) "Now" else "Last break",
                day.lastBreak?.let { durText(it.ms) } ?: AnnotatedString("–"),
                day.lastBreak?.let { Fmt.span(ctx, it, day.ongoing) }, MaterialShapes.Pill),
        ),
    )
    }
    // How the away time splits by break length.
    val buckets = day.breakBuckets()
    val total = buckets.sumOf { it.second }.coerceAtLeast(1)
    val names = listOf("Under 15m", "15m – 1h", "1 – 3h", "3h +")
    val colors = listOf(scheme.secondaryContainer, scheme.secondary.copy(alpha = 0.6f), scheme.primary.copy(alpha = 0.75f), scheme.primary)
    InfoCard("Where the time went") {
        Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(50)), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            buckets.forEachIndexed { i, (_, ms) ->
                if (ms > 0) Box(Modifier.weight(ms.toFloat() / total).fillMaxHeight().background(colors[i]))
            }
            if (day.awayMs == 0L) Box(Modifier.fillMaxSize().background(scheme.surfaceContainerHighest))
        }
        Spacer(Modifier.height(14.dp))
        buckets.forEachIndexed { i, (n, ms) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(colors[i]))
                Spacer(Modifier.width(10.dp))
                Text(names[i], style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    if (n == 0) "–" else "$n × · " + Fmt.dur(ms),
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CheckIns(day: Day) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    Section("Check-ins") {
    TileGrid(
        listOf(
            Tile(Icons.Rounded.LockOpen, "Unlocks", AnnotatedString(day.unlocks.size.toString()), null, MaterialShapes.Cookie4Sided),
            Tile(Icons.Rounded.Schedule, "One every", if (day.unlocks.isEmpty()) AnnotatedString("–") else durText(day.unlockEveryMs),
                "on average", MaterialShapes.SoftBurst),
            Tile(Icons.Rounded.WbSunny, "First unlock", AnnotatedString(day.firstUnlock?.let { Fmt.time(ctx, it) } ?: "–"),
                null, MaterialShapes.Sunny),
            Tile(Icons.Rounded.Bedtime, "Last unlock", AnnotatedString(day.lastUnlock?.let { Fmt.time(ctx, it) } ?: "–"),
                null, MaterialShapes.Cookie9Sided),
        ),
    )
    }
    val hours = day.unlocksByHour()
    val peak = hours.maxOrNull()?.coerceAtLeast(1) ?: 1
    InfoCard("Unlocks by hour") {
        Row(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
            hours.forEach { n ->
                Box(
                    Modifier.weight(1f).fillMaxHeight(if (n == 0) 0.04f else 0.12f + 0.88f * n / peak)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (n == peak && n > 0) scheme.primary else scheme.secondaryContainer),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0, 6, 12, 18, 24).forEach { h ->
                Text(if (h == 24) "" else Fmt.clock(ctx, h * 60).replace(":00", ""),
                    style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
        val busiest = hours.indices.maxByOrNull { hours[it] }
        if (busiest != null && hours[busiest] > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Most unlocks around " + Fmt.clock(ctx, busiest * 60) + " (${hours[busiest]})",
                style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Footnote(rules: Rules) {
    val ctx = LocalContext.current
    val sleep = when (rules.sleep) {
        SleepMode.OFF -> ""
        SleepMode.AUTO -> " Your night's sleep is left out."
        SleepMode.FIXED -> " " + Fmt.clock(ctx, rules.bed) + " – " + Fmt.clock(ctx, rules.wake) + " is left out."
    }
    Text(
        "Away is time with the phone locked, in breaks of ${Fmt.minBreak(rules.minBreakMs)} or more." + sleep +
            " Glancing at the lock screen doesn't count as use.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

// ------------------------------------------------------------------ building blocks

@Composable
private fun Section(t: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp))
        content()
    }
}

@Composable
internal fun InfoCard(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(14.dp))
            }
            content()
        }
    }
}

private class Tile(
    val icon: ImageVector, val title: String, val value: AnnotatedString, val sub: String?,
    val shape: androidx.graphics.shapes.RoundedPolygon,
)

@Composable
private fun TileGrid(tiles: List<Tile>) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        tiles.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                pair.forEachIndexed { col, t ->
                    val big = 28.dp; val small = 8.dp
                    val shape = RoundedCornerShape(
                        topStart = if (row == 0 && col == 0) big else small,
                        topEnd = if (row == 0 && col == 1) big else small,
                        bottomStart = if (row == 1 && col == 0) big else small,
                        bottomEnd = if (row == 1 && col == 1) big else small,
                    )
                    Surface(color = scheme.surfaceContainer, shape = shape, modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Column(Modifier.padding(18.dp)) {
                            Box(
                                Modifier.size(40.dp).clip(t.shape.toShape()).background(scheme.secondaryContainer),
                                contentAlignment = Alignment.Center,
                            ) { Icon(t.icon, null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(20.dp)) }
                            Spacer(Modifier.height(14.dp))
                            Text(t.title, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                            Text(t.value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                            if (t.sub != null) {
                                Text(t.sub, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ no access yet

@Composable
fun AccessGate() {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(128.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(scheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.SelfImprovement, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(56.dp)) }
        Spacer(Modifier.height(32.dp))
        Text("How long have you been away?", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            "Hiatus counts the time your phone stays locked. It needs usage access to see when you unlock. " +
                "Nothing leaves your phone.",
            style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = { openUsageAccess(ctx) }, modifier = Modifier.height(56.dp)) {
            Text("Allow usage access", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(12.dp))
        Text("Find Hiatus in the list and turn it on", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
    }
}
