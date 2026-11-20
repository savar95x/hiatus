@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package dev.savar.hiatus

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// ------------------------------------------------------------------ groups (Android 16 Settings style)

internal class GroupScope {
    val items = mutableListOf<@Composable () -> Unit>()
    fun item(content: @Composable () -> Unit) { items += content }
}

/** A titled group of rows: each row its own tonal container, 2dp apart, big corners outside, small inside. */
@Composable
internal fun Group(title: String? = null, modifier: Modifier = Modifier, build: GroupScope.() -> Unit) {
    val items = GroupScope().apply(build).items
    if (items.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
            )
        }
        Column(
            Modifier.fillMaxWidth().animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items.forEachIndexed { i, content ->
                val big = 24.dp
                val small = 6.dp
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(
                        topStart = if (i == 0) big else small, topEnd = if (i == 0) big else small,
                        bottomStart = if (i == items.lastIndex) big else small, bottomEnd = if (i == items.lastIndex) big else small,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) { content() }
            }
        }
    }
}

/** Free-form content inside a group row. */
@Composable
internal fun Pad(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), content = content)
}

@Composable
internal fun RowIcon(icon: ImageVector, container: Color = MaterialTheme.colorScheme.secondaryContainer,
                     tint: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
internal fun NavRow(title: String, value: String?, icon: ImageVector? = null, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = icon?.let { { RowIcon(it) } },
        headlineContent = { Text(title) },
        supportingContent = value?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
        trailingContent = {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
internal fun SwitchRow(title: String, checked: Boolean, summary: String? = null, enabled: Boolean = true,
                       icon: ImageVector? = null, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        leadingContent = icon?.let { { RowIcon(it) } },
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = {
            Switch(
                checked = checked, onCheckedChange = null, enabled = enabled,
                thumbContent = if (checked) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
                } else null,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** A slider with its title and current value. [onChange] fires while dragging. */
@Composable
internal fun SliderRow(title: String, value: Int, range: IntRange, valueText: String, step: Int = 1,
                       onChange: (Int) -> Unit) {
    Pad {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        val steps = ((range.last - range.first) / step - 1).coerceAtLeast(0)
        Slider(
            value = value.toFloat().coerceIn(range.first.toFloat(), range.last.toFloat()),
            onValueChange = { v ->
                val snapped = range.first + ((v - range.first) / step).roundToInt() * step
                if (snapped != value) onChange(snapped)
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = if (steps <= 40) steps else 0,
        )
    }
}

/** A titled row of single-choice chips that wrap. */
@Composable
internal fun <T> ChoiceRow(title: String?, options: List<T>, selected: T, label: (T) -> String,
                           summary: String? = null, onSelect: (T) -> Unit) {
    Pad {
        if (title != null) Text(title, style = MaterialTheme.typography.bodyLarge)
        if (summary != null) {
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (title != null || summary != null) Spacer(Modifier.height(10.dp))
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { o ->
                    val sel = o == selected
                    FilterChip(
                        selected = sel,
                        onClick = { onSelect(o) },
                        label = { Text(label(o)) },
                        leadingIcon = if (sel) {
                            { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                        } else null,
                    )
                }
            }
        }
    }
}

/** Material 3 connected segmented buttons for 2–4 short options. */
@Composable
internal fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, modifier: Modifier = Modifier,
                           onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { i, o ->
            SegmentedButton(
                selected = o == selected,
                onClick = { onSelect(o) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
            ) { Text(label(o), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
internal fun Reveal(visible: Boolean, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220, delayMillis = 40)) + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(tween(140)) + shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)),
    ) { Column(content = content) }
}

// ------------------------------------------------------------------ colours

internal val SWATCHES = listOf(
    0xFFFFFFFF, 0xFFEEE7DC, 0xFFE2957A, 0xFFFF8A65, 0xFFFFB4A9, 0xFFF48FB1, 0xFFCE93D8, 0xFFB39DDB,
    0xFF9FA8DA, 0xFF90CAF9, 0xFF80DEEA, 0xFF80CBC4, 0xFFA5D6A7, 0xFFDCE775, 0xFFFFE082, 0xFFBCAAA4,
    0xFF9E9E9E, 0xFF607D8B, 0xFF3A3A3C, 0xFF1C1C1E, 0xFF000000,
).map { it.toInt() }

internal fun hex(c: Int) = String.format("#%06X", 0xFFFFFF and c)

internal fun parseHex(s: String): Int? {
    val t = s.trim().removePrefix("#")
    if (t.length != 6) return null
    return t.toLongOrNull(16)?.let { (it or 0xFF000000L).toInt() }
}

/** Swatches plus a hex field. [color] null = "Auto" (from the palette), offered when [auto] is given. */
@Composable
internal fun ColorChooser(title: String, color: Int?, auto: Int? = null, onPick: (Int?) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var text by remember(color) { mutableStateOf(color?.let { hex(it) } ?: "") }
    Pad {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; parseHex(it)?.let(onPick) },
                singleLine = true,
                placeholder = { Text("Auto") },
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.width(124.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (auto != null) item {
                val sel = color == null
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Color(auto))
                        .border(if (sel) 3.dp else 1.dp, if (sel) scheme.primary else scheme.outlineVariant, CircleShape)
                        .clickable { onPick(null) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("A", style = MaterialTheme.typography.labelLarge,
                        color = if (Renderer.luminance(auto) > 0.4) Color.Black else Color.White)
                }
            }
            items(SWATCHES) { s ->
                val sel = color != null && (color or 0xFF000000.toInt()) == s
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Color(s))
                        .border(if (sel) 3.dp else 1.dp, if (sel) scheme.primary else scheme.outlineVariant, CircleShape)
                        .clickable { onPick(s) },
                )
            }
        }
    }
}

// ------------------------------------------------------------------ apps

internal class AppEntry(val label: String, val pkg: String, val icon: ImageBitmap)

internal fun appLabel(ctx: Context, pkg: String): String = runCatching {
    val pm = ctx.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
}.getOrDefault(pkg)

@Suppress("DEPRECATION")
internal fun loadApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    val px = (40 * ctx.resources.displayMetrics.density).toInt()
    return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .distinctBy { it.activityInfo.packageName }
        .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm).toBitmap(px, px).asImageBitmap()) }
        .sortedBy { it.label.lowercase() }
}

@Composable
internal fun AppPickerDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    val apps by produceState<List<AppEntry>?>(null) { value = withContext(Dispatchers.IO) { loadApps(ctx) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Open which app?") },
        text = {
            val list = apps
            if (list == null) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            } else {
                LazyColumn(Modifier.heightIn(max = 440.dp)) {
                    items(list, key = { it.pkg }) { a ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(a.pkg) }
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

// ------------------------------------------------------------------ misc

internal fun openUsageAccess(ctx: Context) {
    val direct = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:" + ctx.packageName))
    if (runCatching { ctx.startActivity(direct) }.isFailure) {
        runCatching { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
    }
}

internal fun openUrl(ctx: Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** "2h 45m" with the units smaller, as on the widget. */
internal fun durText(ms: Long): AnnotatedString = buildAnnotatedString {
    for ((t, unit) in Fmt.parts(ms, NumberFormat.HM)) {
        if (unit) withStyle(SpanStyle(fontSize = 0.55.em)) { append(t) } else append(t)
    }
}
