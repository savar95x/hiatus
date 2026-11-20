@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.savar.hiatus

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect

/** Versions the user put off with "Later" this session. */
private val laterFor = mutableStateListOf<String>()

/** Top of Home when there's a newer release: what's new, then download → install, all in place. */
@Composable
fun UpdateCard() {
    val s = Updates.state
    val release = when (s) {
        is Updates.State.Available -> s.release
        is Updates.State.Downloading -> s.release
        is Updates.State.Ready -> s.release
        is Updates.State.Installing -> s.release
        is Updates.State.Failed -> s.release
        else -> null
    } ?: return
    if (release.version in laterFor && s !is Updates.State.Downloading && s !is Updates.State.Installing) return
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.secondaryContainer, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SystemUpdate, null, tint = scheme.onSecondaryContainer)
                Spacer(Modifier.width(12.dp))
                Text("Hiatus ${release.version} is out", style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSecondaryContainer, modifier = Modifier.weight(1f))
            }
            UpdateBody(release, s, showNotes = true, onLater = { laterFor += release.version })
        }
    }
}

/** Settings → About: the version, and the update state or a "Check for updates" button. */
@Composable
fun UpdateSettingsRow() {
    val ctx = LocalContext.current
    val s = Updates.state
    Pad {
        Text("Hiatus " + Updates.installed(ctx), style = MaterialTheme.typography.bodyLarge)
        val release = when (s) {
            is Updates.State.Available -> s.release
            is Updates.State.Downloading -> s.release
            is Updates.State.Ready -> s.release
            is Updates.State.Installing -> s.release
            is Updates.State.Failed -> s.release
            else -> null
        }
        if (release != null) {
            Text("Version ${release.version} is available", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            UpdateBody(release, s, showNotes = false, onLater = null)
        } else {
            Text(
                when (s) {
                    Updates.State.Checking -> "Checking…"
                    Updates.State.UpToDate -> "You have the latest version"
                    is Updates.State.Failed -> s.message
                    else -> "Updates come from GitHub Releases"
                },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = { Updates.check(ctx, force = true) }, enabled = s != Updates.State.Checking) {
                Text("Check for updates")
            }
        }
    }
}

@Composable
private fun UpdateBody(release: Release, s: Updates.State, showNotes: Boolean, onLater: (() -> Unit)?) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    // "Install unknown apps" can change while we're away in Settings: re-read it on resume.
    var allowed by remember { mutableStateOf(Updates.canInstall(ctx)) }
    LifecycleResumeEffect(Unit) { allowed = Updates.canInstall(ctx); onPauseOrDispose { } }
    var expanded by remember { mutableStateOf(false) }

    if (showNotes && release.notes.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        Text(
            release.notes.lines().filter { it.isNotBlank() }.joinToString("\n") { it.trim().replaceFirst(Regex("^[-*] "), "• ") },
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSecondaryContainer.copy(alpha = 0.85f),
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
            Text(if (expanded) "Less" else "What's new")
        }
    } else Spacer(Modifier.height(12.dp))

    when (s) {
        is Updates.State.Downloading -> {
            LinearWavyProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(
                "Downloading " + (s.progress * 100).toInt() + "% of " + "%.1f".format(release.apkSize / 1_000_000f) + " MB",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        is Updates.State.Installing -> Row(verticalAlignment = Alignment.CenterVertically) {
            LoadingIndicator(Modifier.size(32.dp)); Spacer(Modifier.width(10.dp))
            Text("Installing… Hiatus restarts when it's done", style = MaterialTheme.typography.bodyMedium)
        }
        else -> {
            if (s is Updates.State.Failed) {
                Text(s.message, style = MaterialTheme.typography.bodyMedium, color = scheme.error)
                Spacer(Modifier.height(8.dp))
            }
            val ready = s is Updates.State.Ready
            if (ready && !allowed) {
                Text("Android asks once whether Hiatus may install its updates.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    when {
                        s is Updates.State.Available -> Updates.download(ctx, release)
                        s is Updates.State.Failed -> Updates.retry(ctx)
                        ready && !allowed -> Updates.allowInstalls(ctx)
                        ready -> Updates.install(ctx)
                    }
                }) {
                    Text(when {
                        s is Updates.State.Failed -> "Try again"
                        ready && !allowed -> "Allow"
                        ready -> "Install"
                        else -> "Update (" + "%.1f".format(release.apkSize / 1_000_000f) + " MB)"
                    })
                }
                if (onLater != null) TextButton(onClick = onLater) { Text("Later") }
            }
        }
    }
}
