@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package dev.savar.hiatus

import dev.savar.kit.KitGroup
import dev.savar.kit.KitGroupScope
import dev.savar.kit.KitIconTitle
import dev.savar.kit.KitSegmented
import dev.savar.kit.KitNavRow
import dev.savar.kit.KitPad
import dev.savar.kit.KitRowIcon
import dev.savar.kit.KitSwitchRow

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

internal typealias GroupScope = KitGroupScope

/** A titled group of rows: the shared kit's (each row its own tonal tile, 2dp apart, big corners outside). */
@Composable
internal fun Group(title: String? = null, modifier: Modifier = Modifier, build: GroupScope.() -> Unit) =
    KitGroup(title, modifier, build = build)

/** Free-form content inside a group row. */
@Composable
internal fun Pad(content: @Composable ColumnScope.() -> Unit) = KitPad(content)

/** A row title with its round icon, as in ListItem; [trailing] sits at the end (e.g. a slider's value). */
@Composable
internal fun IconTitle(icon: ImageVector?, title: String, trailing: (@Composable () -> Unit)? = null) =
    KitIconTitle(icon, title, trailing = trailing)

@Composable
internal fun RowIcon(icon: ImageVector, container: Color = MaterialTheme.colorScheme.secondaryContainer,
                     tint: Color = MaterialTheme.colorScheme.onSecondaryContainer) = KitRowIcon(icon, container, tint)

@Composable
internal fun NavRow(title: String, value: String?, icon: ImageVector? = null, onClick: () -> Unit) =
    KitNavRow(title, value, icon, onClick = onClick)

@Composable
internal fun SwitchRow(title: String, checked: Boolean, summary: String? = null, enabled: Boolean = true,
                       icon: ImageVector? = null, onChange: (Boolean) -> Unit) =
    KitSwitchRow(title, checked, summary, enabled, icon, onChange = onChange)

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
                           summary: String? = null, icon: ImageVector? = null, onSelect: (T) -> Unit) {
    Pad {
        if (title != null) IconTitle(icon, title)
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
                           onSelect: (T) -> Unit) = KitSegmented(options, selected, label, modifier, onSelect = onSelect)

@Composable
internal fun Reveal(visible: Boolean, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220, delayMillis = 40)) + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(tween(140)) + shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)),
    ) { Column(content = content) }
}

// ------------------------------------------------------------------ apps

internal class AppEntry(val label: String, val pkg: String, val icon: ImageBitmap)

internal fun appLabel(ctx: Context, pkg: String): String = runCatching {
    val pm = ctx.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
}.getOrDefault(Fmt.pkgName(pkg))

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
