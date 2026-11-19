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
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Typography
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Checkbox
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.lerp
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog

// Settings building blocks shared with Minutia (savar95x/minutia), so both apps look and behave alike.

internal val SWATCHES = listOf(
    0xFFFFFFFF, 0xFFFFF4E0, 0xFFFFB59E, 0xFFFF7A59, 0xFFFF8FAB, 0xFFC9B6FF, 0xFF9AD0FF,
    0xFF9EF0C8, 0xFFD4F57A, 0xFFFFD95A, 0xFFC7CBD3, 0xFF5A6070, 0xFF1B1D22, 0xFF000000,
).map { it.toInt() }

internal fun hex(c: Int) = String.format("#%06X", 0xFFFFFF and c)

internal fun parseHex(s: String): Int? {
    val t = s.trim().removePrefix("#")
    if (t.length != 6 && t.length != 8) return null
    return try {
        android.graphics.Color.parseColor("#$t")
    } catch (e: Exception) {
        null
    }
}


/** Collects the rows of a [Group]; rows can be added conditionally. */
internal class GroupScope {
    val items = mutableListOf<@Composable () -> Unit>()
    fun item(content: @Composable () -> Unit) { items += content }
}

/**
 * Material 3 Expressive list group (Android 16 Settings style): each row is its own tonal container,
 * 2dp apart, with large corners on the outside of the group and small corners between rows.
 */
@Composable
internal fun Group(title: String? = null, build: GroupScope.() -> Unit) {
    val items = GroupScope().apply(build).items
    if (items.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )
        }
        Column(
            Modifier.fillMaxWidth().animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items.forEachIndexed { i, content ->
                val big = 24.dp
                val small = 6.dp
                val shape = RoundedCornerShape(
                    topStart = if (i == 0) big else small,
                    topEnd = if (i == 0) big else small,
                    bottomStart = if (i == items.lastIndex) big else small,
                    bottomEnd = if (i == items.lastIndex) big else small,
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = shape,
                    modifier = Modifier.fillMaxWidth(),
                ) { content() }
            }
        }
    }
}

/** Padding for free-form content (sliders, pickers) inside a group row. */
@Composable
internal fun Pad(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), content = content)
}

/** Leading icon in a tonal circle, as in Android 16 Settings. */
@Composable
internal fun RowIcon(
    icon: ImageVector,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    tint: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(container),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp)) }
}

/** Home list row: icon, title, current value, chevron. Opens a sub-page. */
@Composable
internal fun NavRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { RowIcon(icon) },
        headlineContent = { Text(title) },
        supportingContent = { Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}


@Composable
internal fun SwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    summary: String? = null,
    onChange: (Boolean) -> Unit,
) {
    val content = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = content)
            if (summary != null) {
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null, // the whole row toggles
            enabled = enabled,
            thumbContent = if (checked) {
                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
            } else {
                null
            },
        )
    }
}

@Composable
internal fun LabeledSlider(
    label: String,
    value: Int,
    range: IntRange,
    steps: Int,
    valueText: String,
    onChange: (Int) -> Unit,
    onDone: () -> Unit,
) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.toFloat().coerceIn(range.first.toFloat(), range.last.toFloat()),
            onValueChange = { onChange(it.roundToInt()) },
            onValueChangeFinished = onDone,
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = steps,
        )
    }
}

@Composable
internal fun ColorPicker(label: String, color: Int, onPick: (Int) -> Unit) {
    var text by remember(color) { mutableStateOf(hex(color)) }
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    parseHex(it)?.let { c -> onPick(c or 0xFF000000.toInt()) }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(120.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SWATCHES) { s ->
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(s))
                        .border(
                            if ((s or 0xFF000000.toInt()) == (color or 0xFF000000.toInt())) 3.dp else 1.dp,
                            if ((s or 0xFF000000.toInt()) == (color or 0xFF000000.toInt())) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape,
                        )
                        .clickable { onPick(s) },
                )
            }
        }
    }
}

@Composable
internal fun ChipFlow(content: @Composable () -> Unit) {
    // Chips keep their 32dp height instead of padding out to 48dp touch targets, so rows sit tight.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

internal class AppEntry(val label: String, val pkg: String, val icon: ImageBitmap)

internal fun appLabel(ctx: Context, pkg: String): String = try {
    val pm = ctx.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
} catch (e: Exception) {
    pkg
}

@Suppress("DEPRECATION")
internal fun loadApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    val px = (40 * ctx.resources.displayMetrics.density).toInt()
    return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .distinctBy { it.activityInfo.packageName }
        .map { ri ->
            AppEntry(
                ri.loadLabel(pm).toString(),
                ri.activityInfo.packageName,
                ri.loadIcon(pm).toBitmap(px, px).asImageBitmap(),
            )
        }
        .sortedBy { it.label.lowercase() }
}

@Composable
internal fun AppPickerDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    val apps by produceState<List<AppEntry>?>(null) {
        value = withContext(Dispatchers.IO) { loadApps(ctx) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Choose app") },
        text = {
            val list = apps
            if (list == null) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(Modifier.heightIn(max = 440.dp)) {
                    items(list) { a ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPick(a.pkg) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(a.icon, contentDescription = null, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.width(14.dp))
                            Text(a.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
    )
}


/**
 * Pick a font from System / Google Fonts / a file, plus its weight. Used for both the clock
 * and the date. [onPreview] updates the preview only (while dragging); [onApply] saves.
 */
@Composable
internal fun FontEditor(
    spec: FontSpec,
    resetKey: Any,
    catalogueVersion: Int,
    recentKey: Int,
    onPreview: (FontSpec) -> Unit,
    onApply: (FontSpec) -> Unit,
    onFontLoaded: () -> Unit,
    showWeight: Boolean = true,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val current by rememberUpdatedState(spec)
    val applyFont by rememberUpdatedState(onApply)
    val fontLoaded by rememberUpdatedState(onFontLoaded)

    var tab by remember(resetKey) { mutableStateOf(spec.source) }
    var query by remember(resetKey) { mutableStateOf(spec.googleFamily) }
    var status by remember(resetKey) { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var showBrowser by remember { mutableStateOf(false) }

    fun fetch(name: String, weight: Int = current.weight) {
        val typed = name.trim()
        if (typed.isEmpty() || downloading) return
        downloading = true
        status = null
        scope.launch {
            val r = FontManager.downloadGoogleFont(ctx, typed, weight)
            downloading = false
            r.onSuccess { family ->
                FontManager.addRecent(ctx, family)
                applyFont(current.copy(source = FontSource.GOOGLE, googleFamily = family, weight = weight))
                tab = FontSource.GOOGLE
                query = family
                fontLoaded()
                status = null
            }.onFailure {
                status = it.message ?: "Failed"
            }
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                FontManager.importLocalFont(ctx, uri)
                    .onSuccess {
                        applyFont(current.copy(source = FontSource.FILE, localFontFile = it))
                        tab = FontSource.FILE
                        fontLoaded()
                        status = null
                    }
                    .onFailure { status = it.message }
            }
        }
    }

    // Make sure a chosen Google font is on the phone (e.g. after reinstalling).
    LaunchedEffect(resetKey) {
        if (spec.source == FontSource.GOOGLE && spec.googleFamily.isNotBlank() &&
            !FontManager.isGoogleDownloaded(ctx, spec.googleFamily, spec.weight)
        ) fetch(spec.googleFamily)
    }

    Column {
        Segmented(FontSource.entries, tab, { it.label }) { choice ->
            tab = choice
            status = null
            when (choice) {
                FontSource.SYSTEM -> applyFont(current.copy(source = choice))
                FontSource.GOOGLE -> {
                    val fam = current.googleFamily
                    if (fam.isBlank()) {
                        // Nothing chosen yet: just show the search box.
                    } else if (FontManager.isGoogleDownloaded(ctx, fam, current.weight)) {
                        applyFont(current.copy(source = choice))
                    } else {
                        fetch(fam)
                    }
                }
                FontSource.FILE ->
                    if (current.localFontFile.isNotEmpty()) applyFont(current.copy(source = choice))
                    else pick.launch(arrayOf("*/*"))
            }
        }
        Spacer(Modifier.height(14.dp))

        AnimatedContent(
            targetState = tab,
            transitionSpec = { (fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120))) using SizeTransform(clip = false) },
            label = "fontTab",
        ) { shown ->
        when (shown) {
            FontSource.SYSTEM -> ChipFlow {
                FontManager.systemFamilies.forEach { (fam, label) ->
                    Chip(
                        selected = spec.source == FontSource.SYSTEM && spec.systemFamily == fam,
                        onClick = { applyFont(current.copy(source = FontSource.SYSTEM, systemFamily = fam)) },
                        label = { Text(label) },
                    )
                }
            }

            FontSource.GOOGLE -> Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text("Search Google Fonts") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (downloading) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { fetch(query) }, enabled = query.isNotBlank()) {
                                Icon(Icons.Default.Check, contentDescription = "Use this font")
                            }
                        }
                    },
                    supportingText = if (status != null) { { Text(status ?: "") } } else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { fetch(query) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                val matches = remember(query, catalogueVersion) { FontManager.suggest(ctx, query) }
                val typing = query.isNotBlank() && !query.trim().equals(spec.googleFamily, ignoreCase = true)
                // Fonts you've searched for come first (newest first, up to 50), then the curated
                // picks in their fixed order. Curated picks are never stored as recents, so never moved.
                val recents = remember(recentKey) {
                    FontManager.recentFonts(ctx)
                        .filterNot { r -> FontManager.googleSuggestions.any { it.equals(r, ignoreCase = true) } }
                }
                val everyday = remember(recents) { recents + FontManager.googleSuggestions }
                val chips = if (typing && matches.isNotEmpty()) matches else everyday
                Spacer(Modifier.height(8.dp))
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    // Fresh scroll position whenever the list switches between matches and popular picks.
                    val rowState = remember(chips) { LazyListState() }
                    LazyRow(state = rowState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(chips) { fam ->
                            Chip(
                                selected = spec.source == FontSource.GOOGLE && spec.googleFamily.equals(fam, ignoreCase = true),
                                onClick = { query = fam; fetch(fam) },
                                label = { Text(fam) },
                            )
                        }
                    }
                }
                TextButton(
                    onClick = { showBrowser = true },
                    contentPadding = PaddingValues(horizontal = 0.dp),
                ) { Text("Browse all fonts") }
                if (showBrowser) {
                    FontBrowserSheet(
                        selected = if (spec.source == FontSource.GOOGLE) spec.googleFamily else "",
                        onPick = { fam -> showBrowser = false; query = fam; fetch(fam) },
                        onDismiss = { showBrowser = false },
                    )
                }
            }

            FontSource.FILE -> Column {
                OutlinedButton(onClick = { pick.launch(arrayOf("*/*")) }) {
                    Text(if (spec.localFontFile.isEmpty()) "Choose .ttf / .otf" else "Change file")
                }
                status?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.error)
                }
            }
        }
        }

        Reveal(tab != FontSource.FILE && showWeight) {
            Spacer(Modifier.height(10.dp))
            LabeledSlider(
                label = "Weight",
                value = spec.weight,
                range = 100..900,
                steps = 7,
                valueText = "${spec.weight}",
                onChange = { onPreview(current.copy(weight = (it / 100) * 100)) },
                onDone = {
                    val f = current
                    if (f.source == FontSource.GOOGLE && !FontManager.isGoogleDownloaded(ctx, f.googleFamily, f.weight)) {
                        fetch(f.googleFamily, f.weight)
                    } else {
                        applyFont(f)
                    }
                },
            )
        }
    }
}

internal fun openUrl(ctx: Context, url: String) {
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Material 3 single-choice segmented buttons (selected segment gets the animated check + fill). */
@Composable
internal fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
            ) { Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** Settings that come and go expand + fade instead of popping in. */
@Composable
internal fun Reveal(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220, delayMillis = 40)) + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(tween(140)) + shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)),
    ) {
        Column { content() }
    }
}

/** Material filter chip: selected chips show a leading check. */
@Composable
internal fun Chip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
    )
}

/** The live widget preview on a wallpaper-like gradient; tap toggles a light/dark backdrop. */
