@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.savar.hiatus

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

@Composable
fun SettingsScreen(granted: Boolean) {
    val ctx = LocalContext.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val rules = Prefs.rules
    var picking by remember { mutableStateOf<Boolean?>(null) } // true = bedtime, false = wake-up

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Group("Appearance") {
                item {
                    Pad {
                        Text("Theme", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(10.dp))
                        Segmented(ThemeMode.entries, Prefs.theme, {
                            when (it) { ThemeMode.AUTO -> "Auto"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" }
                        }) { Prefs.setTheme(ctx, it) }
                    }
                }
                item {
                    SwitchRow(
                        "Pure black", Prefs.pureBlack, summary = "Black background in dark theme, for OLED screens",
                        enabled = Prefs.theme != ThemeMode.LIGHT,
                    ) { Prefs.setPureBlack(ctx, it) }
                }
            }

            Group("Counting") {
                item {
                    ChoiceRow(
                        "Shortest break", BREAKS, rules.minBreakMs, { Fmt.minBreak(it) },
                        summary = "Shorter gaps between unlocks don't count as time away",
                    ) { Prefs.setRules(ctx, rules.copy(minBreakMs = it)) }
                }
                item {
                    Pad {
                        Text("Leave out sleep", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(10.dp))
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
                    item { NavRow("Stop counting at", Fmt.clock(ctx, rules.bed)) { picking = true } }
                    item { NavRow("Start again at", Fmt.clock(ctx, rules.wake)) { picking = false } }
                    item {
                        ChoiceRow(null, PRESETS, rules.bed to rules.wake, { (b, w) -> Fmt.clock(ctx, b) + " – " + Fmt.clock(ctx, w) }) { (b, w) ->
                            Prefs.setRules(ctx, rules.copy(bed = b, wake = w))
                        }
                    }
                }
            }

            Group("Permissions") {
                item {
                    NavRow("Usage access", if (granted) "Allowed" else "Needed to see when you unlock") {
                        openUsageAccess(ctx)
                    }
                }
                item {
                    NavRow("Battery", "Set Hiatus to Unrestricted if the widget is slow to update after you unlock") {
                        runCatching {
                            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)))
                        }
                    }
                }
            }

            Group("About") {
                item { UpdateSettingsRow() }
                item {
                    NavRow("Source and releases", "github.com/savar95x/hiatus") {
                        openUrl(ctx, "https://github.com/savar95x/hiatus/releases")
                    }
                }
            }
        }
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
