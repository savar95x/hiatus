@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package dev.savar.hiatus

import android.appwidget.AppWidgetManager
import android.graphics.Typeface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private class PreviewSize(val label: String, val w: Int, val h: Int)

/**
 * The widget's look. A pinned live preview on top (drawn by the same code as the widget), every option below.
 * Changes are saved and pushed to the home screen as you make them.
 */
@Composable
fun WidgetEditor(day: Day?, rules: Rules) {
    val ctx = LocalContext.current
    var style by remember { mutableStateOf(Style.load(ctx)) }
    var fontTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(style) {
        Style.save(ctx, style)
        delay(120)
        Widgets.refresh(ctx, fresh = false)
    }
    val set: (Style) -> Unit = { style = it }

    // Preview sizes: the placed widget's own size first, if there is one.
    val sizes = remember {
        val placed = Widgets.ids(ctx).firstOrNull()?.let { Widgets.size(ctx, it) }
        listOfNotNull(
            placed?.let { PreviewSize("Yours", it.wDp, it.hDp) },
            PreviewSize("1 × 1", 76, 80), PreviewSize("2 × 1", 168, 80),
            PreviewSize("2 × 2", 168, 168), PreviewSize("4 × 2", 344, 168),
        )
    }
    var sizeIdx by remember { mutableIntStateOf(if (sizes.first().label == "Yours") 0 else 3) }
    val previewDay = day ?: remember(rules) { Sample.today(rules, System.currentTimeMillis()) }

    var fontSheet by remember { mutableStateOf<Boolean?>(null) } // true = number font, false = text font
    var appPicker by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Preview(style, sizes[sizeIdx], previewDay, rules, fontTick)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Segmented(sizes.indices.toList(), sizeIdx, { sizes[it].label }) { sizeIdx = it }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Group("Text") {
                item { SwitchRow("Label", style.showLabel) { set(style.copy(showLabel = it)) } }
                if (style.showLabel) item { TextRow("Label text", style.label) { set(style.copy(label = it)) } }
                item { ChoiceRow("Below the number", Caption.entries, style.caption, { it.title }) { set(style.copy(caption = it)) } }
                item { ChoiceRow("Number", NumberFormat.entries, style.format, { it.title }) { set(style.copy(format = it)) } }
            }

            Group("Font") {
                item { NavRow("Number", style.numberFont.title + " · " + weightName(style.numberFont.weight), Icons.Rounded.FontDownload) { fontSheet = true } }
                item { NavRow("Label and caption", style.textFont.title + " · " + weightName(style.textFont.weight), Icons.Rounded.TextFields) { fontSheet = false } }
            }

            Group("Look") {
                item {
                    Pad {
                        Text("Theme", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(10.dp))
                        Segmented(ThemeMode.entries, style.theme, { themeName(it) }) { set(style.copy(theme = it)) }
                        Spacer(Modifier.height(18.dp))
                        Text("Background", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(10.dp))
                        Segmented(Background.entries, style.background, { it.title }) { set(style.copy(background = it)) }
                        Spacer(Modifier.height(18.dp))
                        Text("Alignment", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(10.dp))
                        Segmented(HAlign.entries, style.hAlign, { it.title }) { set(style.copy(hAlign = it)) }
                    }
                }
                item { SliderRow("Number size", style.numberScale, 40..100, "${style.numberScale}%", step = 5) { set(style.copy(numberScale = it)) } }
                item { SwitchRow("Corner shape", style.shape, summary = "A faint shape off the top corner, just for looks") { set(style.copy(shape = it)) } }
            }

            Group("On tap") {
                item { ChoiceRow(null, Tap.entries, style.tap, { it.title }) { set(style.copy(tap = it)); if (it == Tap.APP && style.tapPackage.isBlank()) appPicker = true } }
                if (style.tap == Tap.APP) item {
                    NavRow("App", if (style.tapPackage.isBlank()) "Choose" else appLabel(ctx, style.tapPackage), Icons.Rounded.TouchApp) { appPicker = true }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                val mgr = remember { AppWidgetManager.getInstance(ctx) }
                if (Widgets.ids(ctx).isEmpty() && mgr.isRequestPinAppWidgetSupported) {
                    Button(onClick = { runCatching { mgr.requestPinAppWidget(Widgets.component(ctx), null, null) } }) {
                        Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Add to home screen")
                    }
                }
                OutlinedButton(onClick = { confirmReset = true }) {
                    Icon(Icons.Rounded.RestartAlt, null); Spacer(Modifier.width(8.dp)); Text("Reset")
                }
            }
        }
    }

    fontSheet?.let { number ->
        FontSheet(
            title = if (number) "Number font" else "Label and caption font",
            spec = if (number) style.numberFont else style.textFont,
            onApply = { f -> set(if (number) style.copy(numberFont = f) else style.copy(textFont = f)); fontTick++ },
            onDismiss = { fontSheet = null },
        )
    }
    if (appPicker) {
        AppPickerDialog(onDismiss = { appPicker = false }) { pkg -> set(style.copy(tap = Tap.APP, tapPackage = pkg)); appPicker = false }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset the widget?") },
            text = { Text("Its look goes back to the default, fonts included.") },
            confirmButton = { TextButton(onClick = { set(Style()); fontTick++; confirmReset = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

private fun themeName(t: ThemeMode) = when (t) { ThemeMode.AUTO -> "Auto"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" }

internal fun weightName(w: Int) = when {
    w <= 150 -> "Thin"; w <= 250 -> "Extra light"; w <= 350 -> "Light"; w <= 450 -> "Regular"; w <= 550 -> "Medium"
    w <= 650 -> "Semibold"; w <= 750 -> "Bold"; w <= 850 -> "Extra bold"; else -> "Black"
}

/** The widget at its real size, on a wallpaper-like backdrop. */
@Composable
private fun Preview(style: Style, size: PreviewSize, day: Day, rules: Rules, fontTick: Int) {
    val ctx = LocalContext.current
    val density = LocalDensity.current.density
    val dark = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val bmp = remember(style, size, day, rules, dark, fontTick) {
        Renderer.render(ctx, style, (size.w * density).toInt(), (size.h * density).toInt(), density, day, rules, dark).asImageBitmap()
    }
    Box(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp).padding(top = 12.dp)
            .height(236.dp).clip(RoundedCornerShape(32.dp))
            .background(Brush.linearGradient(listOf(scheme.tertiaryContainer, scheme.primaryContainer, scheme.secondaryContainer))),
        contentAlignment = Alignment.Center,
    ) {
        Image(bmp, contentDescription = "Widget preview", modifier = Modifier.size(size.w.dp, size.h.dp))
    }
}

@Composable
private fun TextRow(title: String, value: String, onChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    Pad {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(40); onChange(text) },
            label = { Text(title) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ------------------------------------------------------------------ fonts

private val WEIGHTS = listOf(300, 400, 500, 600, 700, 900)

/** System / Google Fonts / your own file, and the weight. Google fonts download when picked (a few hundred KB). */
@Composable
private fun FontSheet(title: String, spec: FontSpec, onApply: (FontSpec) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(spec)
    var tab by remember { mutableStateOf(spec.source) }
    var query by remember { mutableStateOf(if (spec.source == FontSource.GOOGLE) spec.family else "") }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val catalogue by produceState(Fonts.cachedFamilies(ctx)) { Fonts.loadCatalogue(ctx); value = Fonts.cachedFamilies(ctx) }

    fun google(name: String, weight: Int = current.weight) {
        if (name.isBlank() || busy != null) return
        busy = name; error = null
        scope.launch {
            Fonts.downloadGoogleFont(ctx, name, weight)
                .onSuccess { fam -> Fonts.addRecent(ctx, fam); onApply(FontSpec(FontSource.GOOGLE, fam, weight)); query = fam }
                .onFailure { error = it.message ?: "Couldn't download" }
            busy = null
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            Fonts.importLocalFont(ctx, uri)
                .onSuccess { onApply(FontSpec(FontSource.FILE, it, current.weight)); error = null }
                .onFailure { error = it.message }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(0.9f).padding(horizontal = 20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(14.dp))
            Segmented(FontSource.entries, tab, { when (it) { FontSource.SYSTEM -> "System"; FontSource.GOOGLE -> "Google"; FontSource.FILE -> "File" } }) {
                tab = it; error = null
                if (it == FontSource.SYSTEM && current.source != FontSource.SYSTEM) onApply(FontSpec(FontSource.SYSTEM, "sans-serif", current.weight))
            }
            Spacer(Modifier.height(14.dp))
            if (tab != FontSource.FILE) {
                Text("Weight", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        WEIGHTS.forEach { w ->
                            FilterChip(
                                selected = current.weight == w,
                                onClick = {
                                    if (current.source == FontSource.GOOGLE && !Fonts.isGoogleDownloaded(ctx, current.family, w)) google(current.family, w)
                                    else onApply(current.copy(weight = w))
                                },
                                label = { Text(weightName(w)) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(8.dp)) }
            when (tab) {
                FontSource.SYSTEM -> LazyColumn {
                    items(Fonts.system) { (fam, label) ->
                        FontRow(label, Typeface.create(fam, Typeface.NORMAL), spec.source == FontSource.SYSTEM && spec.family == fam, false) {
                            onApply(FontSpec(FontSource.SYSTEM, fam, current.weight))
                        }
                    }
                }
                FontSource.GOOGLE -> {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        placeholder = { Text(if (catalogue.isEmpty()) "Search Google Fonts" else "Search ${catalogue.size} fonts") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { google(query) }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    val q = query.trim().lowercase().replace(" ", "")
                    val showing = remember(q, catalogue) {
                        if (q.isEmpty() || (spec.source == FontSource.GOOGLE && q == spec.family.lowercase().replace(" ", ""))) {
                            (Fonts.recentFonts(ctx) + Fonts.googleSuggestions).distinct()
                        } else if (catalogue.isEmpty()) listOf(query.trim())
                        else catalogue.map { it.name }.filter { it.lowercase().replace(" ", "").contains(q) }.take(80)
                    }
                    val listState = rememberLazyListState()
                    LazyColumn(state = listState) {
                        items(showing, key = { it }) { fam ->
                            val tf by produceState<Typeface?>(null, fam) { value = Fonts.previewTypeface(ctx, fam) }
                            FontRow(fam, tf, spec.source == FontSource.GOOGLE && spec.family.equals(fam, true), busy == fam) { google(fam) }
                        }
                    }
                }
                FontSource.FILE -> Column {
                    Text("A .ttf or .otf font file from your phone.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { pick.launch(arrayOf("*/*")) }) {
                        Text(if (spec.source == FontSource.FILE) "Choose another file" else "Choose a file")
                    }
                }
            }
        }
    }
}

@Composable
private fun FontRow(name: String, tf: Typeface?, selected: Boolean, loading: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val family = remember(tf) { tf?.let { FontFamily(it) } }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(16.dp))
            .background(if (selected) scheme.secondaryContainer else scheme.surfaceContainerLow)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.titleMedium, fontFamily = family, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        when {
            loading -> LoadingIndicator(Modifier.size(28.dp))
            selected -> Icon(Icons.Rounded.Check, "Selected", tint = scheme.primary)
            else -> Text(Fonts.PREVIEW_TEXT, style = MaterialTheme.typography.headlineSmall, fontFamily = family,
                color = if (family == null) scheme.onSurfaceVariant else scheme.onSurface)
        }
    }
    Spacer(Modifier.height(4.dp))
}
