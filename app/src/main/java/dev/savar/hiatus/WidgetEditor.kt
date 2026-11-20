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

private enum class EditorPage(val title: String, val parent: EditorPage?) {
    CUSTOMISE("Customise", null),
    TAP("On tap", null),
    STYLE("Style", CUSTOMISE),
    CONTENT("Content", CUSTOMISE),
    FONT("Font", CUSTOMISE),
    COLOURS("Colours", CUSTOMISE),
    LAYOUT("Layout", CUSTOMISE),
}

private fun depth(p: EditorPage?): Int = if (p == null) 0 else 1 + depth(p.parent)

/** A preset counts as chosen while the look still matches it (font and tap action are the user's own). */
private fun matches(cfg: WidgetConfig, preset: Preset): Boolean =
    cfg.copy(font = preset.config.font, fontForLabels = preset.config.fontForLabels, tapAction = preset.config.tapAction, tapPackage = preset.config.tapPackage) == preset.config

private fun applyPreset(cfg: WidgetConfig, preset: Preset): WidgetConfig =
    preset.config.copy(font = cfg.font, fontForLabels = cfg.fontForLabels, tapAction = cfg.tapAction, tapPackage = cfg.tapPackage)

/** Plausible numbers so previews look right before usage access is granted. */
internal fun sampleReport(ctx: Context): Report {
    val now = System.currentTimeMillis()
    val start = Usage.dayStart(now)
    val h = 3_600_000L
    val m = 60_000L
    val pattern = longArrayOf(0, 0, 0, 0, 0, 0, 0, 8, 22, 14, 9, 18, 31, 26, 12, 10, 15, 21, 28, 35, 40, 33, 19, 6)
    val hourNow = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val hourly = LongArray(24) { if (it <= hourNow) pattern[it] * m else 0 }
    @Suppress("DEPRECATION")
    val pkgs = runCatching {
        ctx.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName }.filter { it != ctx.packageName }.distinct().take(6)
    }.getOrDefault(emptyList())
    val apps = pkgs.mapIndexed { i, p -> AppUsage(p, (95 - i * 15) * m) }
    val day = DayStats(
        start = start, end = now, screenTimeMs = 3 * h + 41 * m, inUseMs = 3 * h + 52 * m, awayMs = 9 * h + 20 * m,
        longestAwayMs = 7 * h + 12 * m, sessions = 46, unlocks = 58, pickups = 74, hourly = hourly, apps = apps,
    )
    return Report(now, Away(2 * h + 13 * m, now - 2 * h - 23 * m, now - 10 * m, false), listOf(day))
}

private fun fontName(cfg: WidgetConfig): String {
    val f = cfg.font
    return when (f.source) {
        FontSource.SYSTEM -> (FontManager.systemFamilies.firstOrNull { it.first == f.systemFamily }?.second ?: "System") + " · ${f.weight}"
        FontSource.GOOGLE -> f.googleFamily.ifBlank { "Google Fonts" }
        FontSource.FILE -> "Font file"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WidgetEditor(launchId: Int, report: Report?, onDone: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val black = isAppDark() && ThemePrefs.pureBlack

    var widgetIds by remember { mutableStateOf(Widgets.ids(ctx).toList()) }
    var selectedId by remember { mutableIntStateOf(if (launchId >= 0) launchId else widgetIds.firstOrNull() ?: -1) }
    LifecycleResumeEffect(Unit) {
        widgetIds = Widgets.ids(ctx).toList()
        if (selectedId !in widgetIds && selectedId != launchId) selectedId = widgetIds.firstOrNull() ?: -1
        onPauseOrDispose { }
    }

    var cfg by remember(selectedId) { mutableStateOf(ConfigStore.load(ctx, selectedId)) }
    var fontVersion by remember { mutableIntStateOf(0) }
    var resetCount by remember { mutableIntStateOf(0) }
    var catalogueVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        FontManager.loadCatalogue(ctx)
        catalogueVersion++
    }
    val sample = remember { sampleReport(ctx) }
    val data = report ?: sample

    fun commit() {
        ConfigStore.save(ctx, selectedId, cfg)
        if (selectedId >= 0) Widgets.refresh(ctx, intArrayOf(selectedId))
    }
    fun setLocal(c: WidgetConfig) { cfg = c }
    fun update(c: WidgetConfig) { cfg = c; commit() }

    var page by rememberSaveable { mutableStateOf<EditorPage?>(null) }
    BackHandler(enabled = page != null) { page = page?.parent }
    var showAppPicker by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val pinSupported = remember { AppWidgetManager.getInstance(ctx).isRequestPinAppWidgetSupported }
    fun pin() = AppWidgetManager.getInstance(ctx).requestPinAppWidget(Widgets.component(ctx), null, null)

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            // Material shared-axis X: forward slides in from the right, back from the left.
            val dir = if (depth(targetState) > depth(initialState)) 1 else -1
            (slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { w -> dir * w / 6 } + fadeIn(tween(220, delayMillis = 60)))
                .togetherWith(slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { w -> -dir * w / 6 } + fadeOut(tween(120)))
        },
        label = "editorPage",
    ) { shown ->
        val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        Scaffold(
            modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                LargeTopAppBar(
                    title = { Text(shown?.title ?: if (onDone != null) "New widget" else "Widgets") },
                    navigationIcon = {
                        if (shown != null) IconButton(onClick = { page = shown.parent }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (onDone != null) Button(onClick = onDone, modifier = Modifier.padding(end = 12.dp)) { Text("Done") }
                    },
                    scrollBehavior = scroll,
                    // The preview stays pinned, so nothing scrolls under the bar.
                    colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = scheme.surface),
                )
            },
        ) { inner ->
            Column(Modifier.fillMaxSize().padding(inner).imePadding()) {
                PreviewCard(
                    cfg, selectedId, data, fontVersion,
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                    maxHeight = if (shown == null) 240.dp else 190.dp,
                )
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    when (shown) {
                        null -> {
                            if (onDone == null && widgetIds.isNotEmpty() && (widgetIds.size > 1 || pinSupported)) {
                                ChipFlow {
                                    widgetIds.forEachIndexed { i, id ->
                                        Chip(selected = id == selectedId, onClick = { selectedId = id }, label = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Widget ${i + 1}")
                                                Spacer(Modifier.width(6.dp))
                                                val s = remember(id) { Widgets.size(ctx, id) }
                                                Text("${s.wDp}×${s.hDp}", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                                            }
                                        })
                                    }
                                    if (pinSupported) AssistChip(
                                        onClick = { pin() },
                                        label = { Text("Add") },
                                        leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(AssistChipDefaults.IconSize)) },
                                    )
                                }
                            }
                            if (onDone == null && widgetIds.isEmpty()) Group {
                                item {
                                    ListItem(
                                        leadingContent = { RowIcon(Icons.Rounded.Widgets) },
                                        headlineContent = { Text("No widget yet") },
                                        supportingContent = { Text(if (pinSupported) "Pick a look, then add it" else "Add one from your home screen's widget list") },
                                        trailingContent = if (pinSupported) { { FilledTonalButton(onClick = { pin() }) { Text("Add") } } } else null,
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    )
                                }
                            }
                            SectionTitle("Looks")
                            Presets.all.chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    pair.forEach { pr ->
                                        val sel = matches(cfg, pr)
                                        LookCard(pr.name, applyPreset(cfg, pr), data, sel, fontVersion, Modifier.weight(1f)) {
                                            if (!sel) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            update(applyPreset(cfg, pr))
                                        }
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                            Group {
                                item {
                                    val custom = Presets.all.none { matches(cfg, it) }
                                    NavRow(
                                        Icons.Rounded.Tune, "Customise",
                                        (if (custom) "Custom · " else "") + cfg.style.label + " · " + fontName(cfg).substringBefore(" ·"),
                                    ) { page = EditorPage.CUSTOMISE }
                                }
                                item {
                                    val v = when (cfg.tapAction) {
                                        TapAction.APP -> if (cfg.tapPackage.isEmpty()) "App" else remember(cfg.tapPackage) { appLabel(ctx, cfg.tapPackage) }
                                        TapAction.NONE -> "Nothing"
                                        TapAction.REFRESH -> "Refresh"
                                        TapAction.HIATUS -> "Open Hiatus"
                                    }
                                    NavRow(Icons.Rounded.TouchApp, "On tap", v) { page = EditorPage.TAP }
                                }
                            }
                        }

                        EditorPage.CUSTOMISE -> {
                            Group {
                                item { NavRow(Icons.Rounded.Dashboard, "Style", cfg.style.label) { page = EditorPage.STYLE } }
                                item {
                                    val v = when (cfg.style) {
                                        WidgetStyle.TILES -> cfg.stats.joinToString(", ") { it.label }
                                        WidgetStyle.CHART -> "Screen time by hour"
                                        WidgetStyle.APPS -> "${cfg.appsCount} apps"
                                        WidgetStyle.RING -> "Goal " + Fmt.dur(cfg.goalMinutes * 60_000L)
                                        else -> cfg.metric.label
                                    }
                                    NavRow(Icons.Rounded.Insights, "Content", v) { page = EditorPage.CONTENT }
                                }
                                item { NavRow(Icons.Rounded.TextFields, "Font", fontName(cfg)) { page = EditorPage.FONT } }
                                item {
                                    val v = if (cfg.useWallpaperColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                        "Wallpaper · ${cfg.role.label}" else "Custom"
                                    NavRow(Icons.Rounded.Palette, "Colours", v) { page = EditorPage.COLOURS }
                                }
                                item { NavRow(Icons.Rounded.AspectRatio, "Layout", "${cfg.sizePercent}% · ${cfg.align.label}") { page = EditorPage.LAYOUT } }
                            }
                            Group {
                                item {
                                    ListItem(
                                        modifier = Modifier.clickable { confirmReset = true },
                                        leadingContent = { RowIcon(Icons.Rounded.RestartAlt) },
                                        headlineContent = { Text("Reset to default") },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    )
                                }
                            }
                        }

                        EditorPage.STYLE -> {
                            WidgetStyle.entries.chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    pair.forEach { st ->
                                        LookCard(st.label, cfg.copy(style = st), data, st == cfg.style, fontVersion, Modifier.weight(1f)) {
                                            if (st != cfg.style) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            update(cfg.copy(style = st))
                                        }
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }

                        EditorPage.CONTENT -> {
                            val usesMetric = cfg.style !in setOf(WidgetStyle.TILES, WidgetStyle.CHART, WidgetStyle.APPS)
                            if (usesMetric) Group("Number") {
                                item {
                                    Pad {
                                        ChipFlow {
                                            Metric.entries.forEach { m ->
                                                Chip(selected = cfg.metric == m, onClick = { update(cfg.copy(metric = m)) }, label = { Text(m.label) })
                                            }
                                        }
                                    }
                                }
                            }
                            if (cfg.style == WidgetStyle.TILES || cfg.style == WidgetStyle.DASHBOARD) Group("Tiles") {
                                item {
                                    Pad {
                                        Text(
                                            if (cfg.style == WidgetStyle.DASHBOARD) "Shown beside the number when there's room" else "In the order you pick them",
                                            style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        ChipFlow {
                                            Metric.entries.forEach { m ->
                                                val i = cfg.stats.indexOf(m)
                                                Chip(
                                                    selected = i >= 0,
                                                    onClick = {
                                                        val s = if (i >= 0) cfg.stats - m else cfg.stats + m
                                                        if (s.isNotEmpty()) update(cfg.copy(stats = s))
                                                    },
                                                    label = { Text(if (i >= 0) "${i + 1}  ${m.label}" else m.label) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Group("Text") {
                                item {
                                    SwitchRow("Label", cfg.showLabel, summary = cfg.label()) { update(cfg.copy(showLabel = it)) }
                                }
                                if (cfg.showLabel) item {
                                    Pad {
                                        OutlinedTextField(
                                            value = cfg.customLabel,
                                            onValueChange = { update(cfg.copy(customLabel = it.take(24))) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(16.dp),
                                            label = { Text("Custom label") },
                                            placeholder = { Text(cfg.metric.label) },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                                if (cfg.showLabel) item {
                                    SwitchRow("All caps", cfg.uppercaseLabel) { update(cfg.copy(uppercaseLabel = it)) }
                                }
                                item {
                                    val ex = remember(cfg.metric, cfg.format, data) { Metrics.caption(ctx, cfg.metric, data, cfg.format) }
                                    SwitchRow("Caption", cfg.showCaption, summary = ex) { update(cfg.copy(showCaption = it)) }
                                }
                            }
                            Group("Durations") {
                                item {
                                    Pad {
                                        ChipFlow {
                                            DurationFormat.entries.forEach { f ->
                                                Chip(selected = cfg.format == f, onClick = { update(cfg.copy(format = f)) }, label = { Text(f.label) })
                                            }
                                        }
                                    }
                                }
                                item {
                                    SwitchRow("Small units", cfg.smallUnits, summary = "Shrinks the h and m") { update(cfg.copy(smallUnits = it)) }
                                }
                            }
                            when (cfg.style) {
                                WidgetStyle.RING -> Group("Ring") {
                                    item {
                                        Pad {
                                            LabeledSlider(
                                                "Daily screen time goal", cfg.goalMinutes, 30..600, 37, Fmt.dur(cfg.goalMinutes * 60_000L),
                                                onChange = { setLocal(cfg.copy(goalMinutes = (it / 15) * 15)) }, onDone = { commit() },
                                            )
                                        }
                                    }
                                    item { SwitchRow("Wavy", cfg.wavy) { update(cfg.copy(wavy = it)) } }
                                }
                                WidgetStyle.SHAPE -> Group("Shape") {
                                    item { Pad { ShapePicker(cfg.shape) { update(cfg.copy(shape = it)) } } }
                                }
                                WidgetStyle.APPS -> Group("Apps") {
                                    item {
                                        Pad {
                                            LabeledSlider(
                                                "Apps shown", cfg.appsCount, 1..8, 6, "${cfg.appsCount}",
                                                onChange = { setLocal(cfg.copy(appsCount = it)) }, onDone = { commit() },
                                            )
                                        }
                                    }
                                }
                                else -> {}
                            }
                        }

                        EditorPage.FONT -> {
                            Group("Numbers") {
                                item {
                                    Pad {
                                        FontEditor(
                                            spec = cfg.font,
                                            resetKey = Triple(selectedId, resetCount, "number"),
                                            catalogueVersion = catalogueVersion,
                                            recentKey = fontVersion,
                                            onPreview = { setLocal(cfg.copy(font = it)) },
                                            onApply = { update(cfg.copy(font = it)) },
                                            onFontLoaded = { fontVersion++ },
                                        )
                                    }
                                }
                            }
                            Group {
                                item {
                                    SwitchRow("Use for labels too", cfg.fontForLabels, summary = "Otherwise labels use the system font") {
                                        update(cfg.copy(fontForLabels = it))
                                    }
                                }
                            }
                        }

                        EditorPage.COLOURS -> {
                            val canWall = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                            Group {
                                item {
                                    SwitchRow(
                                        "Wallpaper colours", cfg.useWallpaperColors && canWall, enabled = canWall,
                                        icon = Icons.Rounded.Wallpaper,
                                        summary = if (canWall) "Material You, follows your wallpaper" else "Needs Android 12",
                                    ) { update(cfg.copy(useWallpaperColors = it)) }
                                }
                            }
                            if (cfg.useWallpaperColors && canWall) {
                                Group("Palette") { item { Pad { RolePicker(cfg) { update(cfg.copy(role = it)) } } } }
                                Group("Theme") {
                                    item { Pad { Segmented(ThemeMode.entries, cfg.theme, { it.label }) { update(cfg.copy(theme = it)) } } }
                                }
                            } else {
                                Group("Colours") {
                                    item { Pad { ColorPicker("Background", cfg.bgColor) { update(cfg.copy(bgColor = it)) } } }
                                    item { Pad { ColorPicker("Number", cfg.numberColor) { update(cfg.copy(numberColor = it)) } } }
                                    item { Pad { ColorPicker("Text", cfg.textColor) { update(cfg.copy(textColor = it)) } } }
                                }
                            }
                            Group("Background") {
                                item { SwitchRow("Background", cfg.background) { update(cfg.copy(background = it)) } }
                                if (cfg.background) item {
                                    Pad {
                                        LabeledSlider(
                                            "Opacity", cfg.bgOpacity, 0..100, 19, "${cfg.bgOpacity}%",
                                            onChange = { setLocal(cfg.copy(bgOpacity = it)) }, onDone = { commit() },
                                        )
                                    }
                                }
                                if (cfg.background) item {
                                    Pad {
                                        LabeledSlider(
                                            "Corners", cfg.cornerRadius, 0..48, 11, "${cfg.cornerRadius} dp",
                                            onChange = { setLocal(cfg.copy(cornerRadius = it)) }, onDone = { commit() },
                                        )
                                    }
                                }
                            }
                        }

                        EditorPage.LAYOUT -> {
                            Group {
                                item {
                                    Pad {
                                        LabeledSlider(
                                            "Number size", cfg.sizePercent, 50..130, 15, "${cfg.sizePercent}%",
                                            onChange = { setLocal(cfg.copy(sizePercent = it)) }, onDone = { commit() },
                                        )
                                    }
                                }
                                item {
                                    Pad {
                                        Text("Alignment", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 10.dp))
                                        Segmented(HAlign.entries, cfg.align, { it.label }) { update(cfg.copy(align = it)) }
                                    }
                                }
                            }
                        }

                        EditorPage.TAP -> {
                            Group {
                                item {
                                    Pad {
                                        Segmented(TapAction.entries, cfg.tapAction, { it.label }) {
                                            if (it == TapAction.APP) showAppPicker = true else update(cfg.copy(tapAction = it))
                                        }
                                    }
                                }
                                if (cfg.tapAction == TapAction.APP && cfg.tapPackage.isNotEmpty()) item {
                                    ListItem(
                                        headlineContent = { Text(remember(cfg.tapPackage) { appLabel(ctx, cfg.tapPackage) }) },
                                        supportingContent = { Text("Opens when you tap the widget") },
                                        trailingContent = { TextButton(onClick = { showAppPicker = true }) { Text("Change") } },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            onDismiss = { showAppPicker = false },
            onPick = { update(cfg.copy(tapAction = TapAction.APP, tapPackage = it)); showAppPicker = false },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset widget?") },
            text = { Text("Back to the default look.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    update(WidgetConfig())
                    resetCount++
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

/** The live widget on a wallpaper-like gradient; tap to flip between a dark and a light backdrop. */
@Composable
private fun PreviewCard(cfg: WidgetConfig, id: Int, data: Report, fontVersion: Int, modifier: Modifier, maxHeight: Dp) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var light by remember { mutableStateOf(false) }
    val g1 by animateColorAsState(if (light) Color(0xFFE9E4DA) else scheme.primaryContainer, tween(450), label = "g1")
    val g2 by animateColorAsState(if (light) Color(0xFFC9D6E3) else Color(0xFF1A1C22), tween(450), label = "g2")
    val g3 by animateColorAsState(if (light) Color(0xFFF3E1D3) else scheme.tertiaryContainer, tween(450), label = "g3")
    val size = remember(id, cfg.style) { Widgets.size(ctx, id, cfg.style) }
    val bmp = remember(cfg, size, data, fontVersion) {
        WidgetRenderer.render(ctx, cfg, size.w, size.h, size.dp, data).asImageBitmap()
    }
    val aspect = size.w.toFloat() / size.h.coerceAtLeast(1)
    BoxWithConstraints(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.linearGradient(listOf(g1, g2, g3)))
            .clickable { light = !light }
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        val h = minOf(maxHeight - 32.dp, maxWidth / aspect)
        Image(bmp, "Preview", Modifier.height(h).aspectRatio(aspect), contentScale = ContentScale.Fit)
        Icon(
            if (light) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
            contentDescription = if (light) "Light backdrop" else "Dark backdrop",
            tint = if (light) Color(0x99000000) else Color(0xB3FFFFFF),
            modifier = Modifier.align(Alignment.BottomEnd).clip(CircleShape)
                .background(if (light) Color(0x1A000000) else Color(0x26FFFFFF)).padding(6.dp).size(16.dp),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

/** A look (preset or style): live thumbnail at its usual size, name below. */
@Composable
private fun LookCard(
    name: String, look: WidgetConfig, data: Report, selected: Boolean, fontVersion: Int,
    modifier: Modifier, onClick: () -> Unit,
) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val thumb = remember(look, data, fontVersion) {
        val s = Widgets.size(ctx, -1, look.style)
        WidgetRenderer.render(ctx, look, s.w, s.h, s.dp, data).asImageBitmap()
    }
    val outline by animateColorAsState(if (selected) scheme.primary else Color.Transparent, tween(250), label = "cardOutline")
    val corner by animateDpAsState(if (selected) 32.dp else 20.dp, spring(stiffness = Spring.StiffnessMediumLow), label = "cardCorner")
    Column(
        modifier.clip(RoundedCornerShape(corner))
            .background(scheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().height(116.dp).clip(RoundedCornerShape(corner - 8.dp))
                .background(Brush.linearGradient(listOf(scheme.primaryContainer, Color(0xFF1A1C22))))
                .border(BorderStroke(3.dp, outline), RoundedCornerShape(corner - 8.dp)),
        ) {
            Image(thumb, name, Modifier.fillMaxSize().padding(12.dp), contentScale = ContentScale.Fit)
        }
        Row(Modifier.padding(top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(selected) {
                Icon(Icons.Rounded.Check, null, tint = scheme.primary, modifier = Modifier.padding(end = 4.dp).size(18.dp))
            }
            Text(
                name, style = MaterialTheme.typography.labelLarge,
                color = if (selected) scheme.primary else scheme.onSurfaceVariant, maxLines = 1,
            )
        }
    }
}

/** Material shapes to put the number in. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShapePicker(selected: ShapeKind, onPick: (ShapeKind) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    ChipFlow {
        ShapeKind.entries.forEach { k ->
            val sel = k == selected
            val bg by animateColorAsState(if (sel) scheme.secondaryContainer else Color.Transparent, label = "shapeBg")
            Box(
                Modifier.size(60.dp).clip(RoundedCornerShape(18.dp)).background(bg).clickable { onPick(k) }.padding(10.dp),
            ) {
                val shape = WidgetRenderer.polygon(k).toShape()
                Box(Modifier.fillMaxSize().clip(shape).background(if (sel) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.35f)))
            }
        }
    }
}

/** Wallpaper palettes as two-tone swatches: container colour with the number colour inside. */
@Composable
private fun RolePicker(cfg: WidgetConfig, onPick: (ColorRole) -> Unit) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        ColorRole.entries.forEach { role ->
            val p = remember(role, cfg.theme) {
                WidgetRenderer.palette(ctx, cfg.copy(role = role, background = true, bgOpacity = 100))
            }
            val sel = cfg.role == role
            Column(
                Modifier.clip(RoundedCornerShape(16.dp)).clickable { onPick(role) }.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(Color(p.bg))
                        .border(if (sel) 3.dp else 1.dp, if (sel) scheme.primary else scheme.outlineVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(20.dp).clip(CircleShape).background(Color(p.number))) }
                Spacer(Modifier.height(6.dp))
                Text(
                    role.label, style = MaterialTheme.typography.labelMedium,
                    color = if (sel) scheme.primary else scheme.onSurfaceVariant,
                )
            }
        }
    }
}
