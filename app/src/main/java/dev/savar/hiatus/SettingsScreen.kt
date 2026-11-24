@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.savar.hiatus

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.saveable.rememberSaveable
import dev.savar.kit.AppFont
import dev.savar.kit.FontPageContent
import dev.savar.kit.FontRow
import dev.savar.kit.FontSample
import dev.savar.kit.FontSettings
import dev.savar.kit.PureBlackRow
import dev.savar.kit.ThemeRow
import dev.savar.kit.TypefaceContent
import dev.savar.kit.UpdateRow
import dev.savar.kit.kitTopBarColors

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val BREAKS = listOf(30_000L, 60_000L, 2 * 60_000L, 5 * 60_000L, 10 * 60_000L, 15 * 60_000L, 30 * 60_000L)

/** Sleep presets: (bed, wake) in minutes after midnight. */
private val PRESETS = listOf(23 * 60 to 7 * 60, 0 to 8 * 60, 60 to 9 * 60, 3 * 60 to 8 * 60)

/** Sub-pages of Settings: the app font, and its typeface list. */
private enum class SettingsPage(val title: String, val depth: Int) { FONT("Font", 1), TYPEFACE("Typeface", 2) }

/** Same sample lines in the font preview as the rest of Hiatus. */
private val FONT_SAMPLE = FontSample("Away 4h 12m", "8 breaks · longest 1h 05m", "Today")

@Composable
fun SettingsScreen(granted: Boolean) {
    val ctx = LocalContext.current
    val rules = Prefs.rules
    var picking by remember { mutableStateOf<Boolean?>(null) } // true = bedtime, false = wake-up
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    val back: () -> Unit = { page = if (page == SettingsPage.TYPEFACE) SettingsPage.FONT else null }
    BackHandler(page != null) { back() }
    var menu by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            // Material shared axis X, like the Widget tab: deeper pages come in from the right.
            val dir = if ((targetState?.depth ?: 0) > (initialState?.depth ?: 0)) 1 else -1
            (slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { w -> dir * w / 6 } + fadeIn(tween(220, delayMillis = 60)))
                .togetherWith(slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { w -> -dir * w / 6 } + fadeOut(tween(120)))
        },
        label = "settings",
    ) { shown ->
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = { Text(shown?.title ?: "Settings") },
                navigationIcon = { if (shown != null) IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    if (shown == SettingsPage.FONT) Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Reset to defaults") }, onClick = { menu = false; confirmReset = true })
                        }
                    }
                },
                scrollBehavior = scroll,
                colors = kitTopBarColors(isPureBlack()),
            )
        },
    ) { pad ->
        when (shown) {
            SettingsPage.FONT -> {
                FontPageContent(AppFont.settings, FONT_SAMPLE, onTypeface = { page = SettingsPage.TYPEFACE },
                    onChange = { AppFont.set(ctx, it) }, modifier = Modifier.padding(pad))
                return@Scaffold
            }
            SettingsPage.TYPEFACE -> {
                TypefaceContent(AppFont.settings.font, onPick = { AppFont.set(ctx, AppFont.settings.copy(font = it)) },
                    modifier = Modifier.padding(pad))
                return@Scaffold
            }
            null -> {}
        }
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Group("Appearance") {
                item {
                    ThemeRow(Prefs.theme.ordinal) { Prefs.setTheme(ctx, ThemeMode.entries[it]) }
                }
                item {
                    PureBlackRow(Prefs.pureBlack, enabled = Prefs.theme != ThemeMode.LIGHT) { Prefs.setPureBlack(ctx, it) }
                }
                item { FontRow(AppFont.settings) { page = SettingsPage.FONT } }
            }

            Group("Counting") {
                item {
                    ChoiceRow(
                        "Shortest break", BREAKS, rules.minBreakMs, { Fmt.minBreak(it) },
                        summary = "Shorter gaps between unlocks don't count as time away", icon = Icons.Rounded.Timer,
                    ) { Prefs.setRules(ctx, rules.copy(minBreakMs = it)) }
                }
                item {
                    Pad {
                        IconTitle(Icons.Rounded.Bedtime, "Leave out sleep")
                        Spacer(Modifier.height(12.dp))
                        Segmented(SleepMode.entries, rules.sleep, {
                            when (it) { SleepMode.OFF -> "Off"; SleepMode.AUTO -> "Auto"; SleepMode.FIXED -> "Set hours" }
                        }) { Prefs.setRules(ctx, rules.copy(sleep = it)) }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            when (rules.sleep) {
                                SleepMode.OFF -> "Your whole day counts, sleep included."
                                SleepMode.AUTO -> "Each night's longest break (3 hours or more, reaching into midnight to 8 AM) " +
                                    "is taken as sleep and left out. Nothing to set up, and it follows late nights."
                                SleepMode.FIXED -> "Time between these hours isn't counted, whatever you do."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (rules.sleep == SleepMode.FIXED) {
                    item { NavRow("Stop counting at", Fmt.clock(ctx, rules.bed), Icons.Rounded.NightsStay) { picking = true } }
                    item { NavRow("Start again at", Fmt.clock(ctx, rules.wake), Icons.Rounded.WbSunny) { picking = false } }
                    item {
                        ChoiceRow(null, PRESETS, rules.bed to rules.wake, { (b, w) -> Fmt.clock(ctx, b) + " – " + Fmt.clock(ctx, w) }) { (b, w) ->
                            Prefs.setRules(ctx, rules.copy(bed = b, wake = w))
                        }
                    }
                }
            }

            Group("Permissions") {
                item {
                    NavRow("Usage access", if (granted) "Allowed" else "Needed to see when you unlock", Icons.Rounded.QueryStats) {
                        openUsageAccess(ctx)
                    }
                }
                item {
                    NavRow("Battery", "Set Hiatus to Unrestricted if the widget is slow to update after you unlock", Icons.Rounded.BatteryFull) {
                        runCatching {
                            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)))
                        }
                    }
                }
            }

            Group("About") {
                item { UpdateRow() }
                item {
                    NavRow("Source code", "github.com/savar95x/hiatus", Icons.Rounded.Code) {
                        openUrl(ctx, "https://github.com/savar95x/hiatus")
                    }
                }
            }
        }
    }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset font settings?") },
            text = { Text("Typeface, size, weight and letter spacing go back to their defaults.") },
            confirmButton = { TextButton(onClick = { AppFont.set(ctx, FontSettings()); confirmReset = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }

    picking?.let { bed ->
        TimeDialog(
            title = if (bed) "Stop counting at" else "Start again at",
            minutes = if (bed) rules.bed else rules.wake,
            onDismiss = { picking = null },
        ) { m ->
            Prefs.setRules(ctx, if (bed) rules.copy(bed = m) else rules.copy(wake = m))
            picking = null
        }
    }
}

@Composable
private fun TimeDialog(title: String, minutes: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val ctx = LocalContext.current
    val state = rememberTimePickerState(minutes / 60, minutes % 60, android.text.format.DateFormat.is24HourFormat(ctx))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
