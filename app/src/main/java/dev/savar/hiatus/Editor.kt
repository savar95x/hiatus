@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package dev.savar.hiatus

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class Page { MAIN, NUMBER_FONT, TEXT_FONT, NUMBER_TYPEFACE, TEXT_TYPEFACE }

/**
 * The widget's look: a preview (drawn by the same code as the widget, at the placed widget's size) and the
 * options. Fonts open their own page, as in Aria. Every change is saved and pushed to the home screen.
 */
@Composable
fun WidgetEditor(day: Day?, rules: Rules) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var style by remember { mutableStateOf(Style.load(ctx)) }
    var fontTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(style) {
        Style.save(ctx, style)
        delay(120)
        Widgets.refresh(ctx, fresh = false)
    }
    val set: (Style) -> Unit = { style = it }
    val size = remember {
        Widgets.ids(ctx).firstOrNull()?.let { Widgets.size(ctx, it) }?.let { it.wDp to it.hDp } ?: (168 to 168)
    }
    val previewDay = day ?: remember(rules) { Sample.today(rules, System.currentTimeMillis()) }
    val loader = remember { FontLoader(ctx, scope) { fontTick++ } }
    var page by rememberSaveable { mutableStateOf(Page.MAIN) }
    BackHandler(page != Page.MAIN) {
        page = when (page) {
            Page.NUMBER_TYPEFACE -> Page.NUMBER_FONT
            Page.TEXT_TYPEFACE -> Page.TEXT_FONT
            else -> Page.MAIN
        }
    }
    val preview: @Composable () -> Unit = { Preview(style, size, previewDay, rules, fontTick) }

    AnimatedContent(page, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "page") { p ->
        when (p) {
            Page.MAIN -> EditorMain(style, set, preview, onFont = { page = if (it) Page.NUMBER_FONT else Page.TEXT_FONT })
            Page.NUMBER_FONT, Page.TEXT_FONT -> {
                val number = p == Page.NUMBER_FONT
                FontPage(
                    title = if (number) "Number font" else "Label and caption font",
                    spec = if (number) style.numberFont else style.textFont,
                    loader = loader,
                    preview = preview,
                    onSpec = { f -> set(if (number) style.copy(numberFont = f) else style.copy(textFont = f)) },
                    onTypeface = { page = if (number) Page.NUMBER_TYPEFACE else Page.TEXT_TYPEFACE },
                    onBack = { page = Page.MAIN },
                )
            }
            Page.NUMBER_TYPEFACE, Page.TEXT_TYPEFACE -> {
                val number = p == Page.NUMBER_TYPEFACE
                TypefacePage(
                    spec = if (number) style.numberFont else style.textFont,
                    loader = loader,
                    onSpec = { f -> set(if (number) style.copy(numberFont = f) else style.copy(textFont = f)) },
                    onBack = { page = if (number) Page.NUMBER_FONT else Page.TEXT_FONT },
                )
            }
        }
    }
}

@Composable
private fun EditorMain(style: Style, set: (Style) -> Unit, preview: @Composable () -> Unit, onFont: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var appPicker by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { LargeTopAppBar(title = { Text("Widget") }, scrollBehavior = scroll, colors = barColors()) },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            preview()

            Group("Text") {
                item { SwitchRow("Label", style.showLabel) { set(style.copy(showLabel = it)) } }
                if (style.showLabel) item { TextRow("Label text", style.label) { set(style.copy(label = it)) } }
                item { ChoiceRow("Below the number", Caption.entries, style.caption, { it.title }) { set(style.copy(caption = it)) } }
                item { ChoiceRow("Number", NumberFormat.entries, style.format, { it.title }) { set(style.copy(format = it)) } }
            }

            Group("Font") {
                item { NavRow("Number", fontSummary(style.numberFont), Icons.Rounded.FontDownload) { onFont(true) } }
                item { NavRow("Label and caption", fontSummary(style.textFont), Icons.Rounded.TextFields) { onFont(false) } }
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
                item { SwitchRow("Corner shape", style.shape, summary = "A small faint shape in the corner, just for looks") { set(style.copy(shape = it)) } }
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
    if (appPicker) {
        AppPickerDialog(onDismiss = { appPicker = false }) { pkg -> set(style.copy(tap = Tap.APP, tapPackage = pkg)); appPicker = false }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset the widget?") },
            text = { Text("Its look goes back to the default, fonts included.") },
            confirmButton = { TextButton(onClick = { set(Style()); confirmReset = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun barColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.surface,
    scrolledContainerColor = MaterialTheme.colorScheme.surface,
)

private fun themeName(t: ThemeMode) = when (t) { ThemeMode.AUTO -> "Auto"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" }

private fun fontSummary(f: FontSpec) = if (f.source == FontSource.FILE) f.title else f.title + " · " + weightName(f.weight)

internal fun weightName(w: Int) = when {
    w <= 150 -> "Thin"; w <= 250 -> "Extra light"; w <= 350 -> "Light"; w <= 450 -> "Regular"; w <= 550 -> "Medium"
    w <= 650 -> "Semibold"; w <= 750 -> "Bold"; w <= 850 -> "Extra bold"; else -> "Black"
}

/** The widget at its real size (the placed widget's, else 2 × 2) on a wallpaper-like backdrop. */
@Composable
private fun Preview(style: Style, size: Pair<Int, Int>, day: Day, rules: Rules, fontTick: Int) {
    val ctx = LocalContext.current
    val density = LocalDensity.current.density
    val dark = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val (w, h) = size
    val bmp = remember(style, size, day, rules, dark, fontTick) {
        Renderer.render(ctx, style, (w * density).toInt(), (h * density).toInt(), density, day, rules, dark).asImageBitmap()
    }
    Box(
        Modifier.fillMaxWidth().heightIn(min = 200.dp).clip(RoundedCornerShape(32.dp))
            .background(Brush.linearGradient(listOf(scheme.tertiaryContainer, scheme.primaryContainer, scheme.secondaryContainer)))
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(bmp, contentDescription = "Widget preview", modifier = Modifier.size(w.dp, h.dp))
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

/** Downloads Google fonts (one at a time) and imports files; tells the preview to redraw when one lands. */
private class FontLoader(val ctx: Context, val scope: CoroutineScope, val onLoaded: () -> Unit) {
    var busy by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)

    /** Makes sure [f] is on the phone (downloading a Google font at its weight if needed), then applies it. */
    fun use(f: FontSpec, apply: (FontSpec) -> Unit) {
        if (f.source != FontSource.GOOGLE || Fonts.isGoogleDownloaded(ctx, f.family, f.weight)) {
            apply(f); onLoaded(); return
        }
        if (busy != null) return
        busy = f.family; error = null
        scope.launch {
            Fonts.downloadGoogleFont(ctx, f.family, f.weight)
                .onSuccess { fam -> Fonts.addRecent(ctx, fam); apply(f.copy(family = fam)); onLoaded() }
                .onFailure { error = it.message ?: "Couldn't download ${f.family}" }
            busy = null
        }
    }

    fun import(uri: Uri, weight: Int, apply: (FontSpec) -> Unit) {
        scope.launch {
            Fonts.importLocalFont(ctx, uri)
                .onSuccess { apply(FontSpec(FontSource.FILE, it, weight)); onLoaded(); error = null }
                .onFailure { error = it.message }
        }
    }
}

/** One font setting, laid out like Aria's: preview, the typeface (its own page), and weight on a slider. */
@Composable
private fun FontPage(
    title: String, spec: FontSpec, loader: FontLoader, preview: @Composable () -> Unit,
    onSpec: (FontSpec) -> Unit, onTypeface: () -> Unit, onBack: () -> Unit,
) {
    var weight by remember(spec.weight) { mutableFloatStateOf(spec.weight.toFloat()) }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = barColors(),
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            preview()
            Group("Typeface") {
                item {
                    NavRow(
                        spec.title,
                        when {
                            loader.busy != null -> "Downloading ${loader.busy}…"
                            spec.source == FontSource.FILE -> "From a file · tap to change"
                            else -> "System, any Google Font, or a file"
                        },
                        Icons.Rounded.FontDownload,
                        onClick = onTypeface,
                    )
                }
            }
            loader.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 8.dp)) }
            if (spec.source != FontSource.FILE) {
                Group("Style") {
                    item {
                        Pad {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Weight", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Text(weightName(weight.roundToInt()), style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = weight,
                                onValueChange = { weight = it },
                                // Applied on release: a Google font may need that weight downloaded first.
                                onValueChangeFinished = { loader.use(spec.copy(weight = (weight / 100).roundToInt() * 100), onSpec) },
                                valueRange = 100f..900f,
                                steps = 7,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val CATEGORIES = listOf("Sans" to "Sans Serif", "Serif" to "Serif", "Display" to "Display", "Mono" to "Monospace", "Handwriting" to "Handwriting")

/**
 * Every typeface: search and style filters pinned at the top, then the list, each name in its own face.
 * System default and your own file come first, then the suggested Google fonts, then all of them.
 * Tapping one applies it straight away (Google fonts download first); a tick marks the current one.
 */
@Composable
private fun TypefacePage(spec: FontSpec, loader: FontLoader, onSpec: (FontSpec) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val catalogue by produceState(Fonts.cachedFamilies(ctx)) { Fonts.loadCatalogue(ctx); value = Fonts.cachedFamilies(ctx) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val q = query.trim().lowercase().replace(" ", "")
    val shown = remember(catalogue, q, category) {
        catalogue.filter { (category == null || it.category == category) && (q.isEmpty() || it.name.lowercase().replace(" ", "").contains(q)) }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) loader.import(uri, spec.weight, onSpec)
    }
    val browsing = q.isEmpty() && category == null
    val suggested = remember { (Fonts.googleSuggestions + Fonts.recentFonts(ctx)).distinctBy { it.lowercase() } }
    fun google(name: String) = loader.use(FontSpec(FontSource.GOOGLE, name, spec.weight), onSpec)
    fun isCurrent(name: String) = spec.source == FontSource.GOOGLE && spec.family.equals(name, ignoreCase = true)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Typeface") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = barColors(),
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search all Google Fonts") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") } },
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All") }) }
                items(CATEGORIES) { (label, value) ->
                    FilterChip(selected = category == value, onClick = { category = if (category == value) null else value }, label = { Text(label) })
                }
            }
            loader.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            }
            LazyColumn(Modifier.weight(1f), state = rememberLazyListState(), contentPadding = PaddingValues(bottom = 16.dp)) {
                if (browsing) {
                    item(key = "system") {
                        FontRow("System default", Typeface.create("sans-serif", Typeface.NORMAL), "Your phone's font",
                            spec.source == FontSource.SYSTEM, false) { loader.use(FontSpec(FontSource.SYSTEM, "sans-serif", spec.weight), onSpec) }
                    }
                    item(key = "file") {
                        FontRow("From a file", null, if (spec.source == FontSource.FILE) "Your font · pick another" else ".ttf or .otf on your phone",
                            spec.source == FontSource.FILE, false, icon = Icons.Rounded.FolderOpen) { pick.launch(arrayOf("*/*")) }
                    }
                    item(key = "h-suggested") { Header("Suggested") }
                    items(suggested, key = { "s-$it" }) { name ->
                        GoogleRow(name, "", isCurrent(name), loader.busy == name) { google(name) }
                    }
                    item(key = "h-all") { Header("All Google Fonts") }
                }
                when {
                    catalogue.isEmpty() -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                    }
                    shown.isEmpty() -> item(key = "none") {
                        Column(Modifier.padding(24.dp)) {
                            Text("No Google Font matches “${query.trim()}”.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { google(query.trim()) }, contentPadding = PaddingValues(0.dp)) { Text("Try downloading it anyway") }
                        }
                    }
                    else -> items(shown, key = { "g-" + it.name }) { f ->
                        GoogleRow(f.name, categoryLabel(f.category), isCurrent(f.name), loader.busy == f.name) { google(f.name) }
                    }
                }
            }
        }
    }
}

private fun categoryLabel(c: String) = CATEGORIES.firstOrNull { it.second == c }?.first ?: c

@Composable
private fun Header(t: String) {
    Text(t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 6.dp))
}

/** A Google font row: name and sample in the font itself (a few-KB subset, fetched as the row appears). */
@Composable
private fun GoogleRow(name: String, caption: String, selected: Boolean, loading: Boolean, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val tf by produceState<Typeface?>(null, name) { value = Fonts.previewTypeface(ctx, name) }
    FontRow(name, tf, caption, selected, loading, onClick = onClick)
}

@Composable
private fun FontRow(
    name: String, tf: Typeface?, caption: String, selected: Boolean, loading: Boolean,
    icon: ImageVector? = null, onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val family = remember(tf) { tf?.let { FontFamily(it) } }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = scheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, fontFamily = family, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (caption.isNotEmpty()) Text(caption, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        when {
            loading -> LoadingIndicator(Modifier.size(28.dp))
            selected -> Icon(Icons.Rounded.Check, "Selected", Modifier.size(22.dp), tint = scheme.primary)
            icon == null -> Text(Fonts.PREVIEW_TEXT, style = MaterialTheme.typography.titleLarge, fontFamily = family,
                color = if (family == null) scheme.outline else scheme.onSurfaceVariant)
        }
    }
}
