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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Which text a font setting is for. */
private enum class Part(val title: String) { NUMBER("Number"), LABEL("Label"), CAPTION("Below the number") }

/** Sub-pages of the Widget tab. [depth] decides which way the page slides. */
private enum class Page(val title: String, val depth: Int, val part: Part? = null) {
    TEXT("Text", 1), FONTS("Fonts", 1), LOOK("Look", 1), TAP("On tap", 1),
    NUMBER_TYPEFACE("Number typeface", 2, Part.NUMBER),
    LABEL_TYPEFACE("Label typeface", 2, Part.LABEL),
    CAPTION_TYPEFACE("Caption typeface", 2, Part.CAPTION),
}

private fun Style.font(p: Part) = when (p) { Part.NUMBER -> numberFont; Part.LABEL -> labelFont; Part.CAPTION -> captionFont }
private fun Style.withFont(p: Part, f: FontSpec) = when (p) {
    Part.NUMBER -> copy(numberFont = f); Part.LABEL -> copy(labelFont = f); Part.CAPTION -> copy(captionFont = f)
}

/**
 * The Widget tab, laid out like Minutia: a preview and a short list of rows showing their current values;
 * each row opens its own page (shared-axis slide, like Aria and Minutia), with the preview pinned on top.
 * Every change is saved and pushed to the home screen.
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
    var page by rememberSaveable { mutableStateOf<Page?>(null) }
    val back: () -> Unit = { page = if ((page?.depth ?: 0) == 2) Page.FONTS else null }
    BackHandler(page != null) { back() }
    var menu by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var appPicker by remember { mutableStateOf(false) }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            // Material shared axis X: deeper pages come in from the right, going back from the left.
            val dir = if ((targetState?.depth ?: 0) > (initialState?.depth ?: 0)) 1 else -1
            (slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { w -> dir * w / 6 } + fadeIn(tween(220, delayMillis = 60)))
                .togetherWith(slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { w -> -dir * w / 6 } + fadeOut(tween(120)))
        },
        label = "page",
    ) { shown ->
        val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        Scaffold(
            modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                LargeTopAppBar(
                    title = { Text(shown?.title ?: "Widget") },
                    navigationIcon = { if (shown != null) IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                    actions = {
                        if (shown == null) Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Reset widget") }, onClick = { menu = false; confirmReset = true })
                            }
                        }
                    },
                    scrollBehavior = scroll,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
        ) { pad ->
            val part = shown?.part
            if (part != null) {
                Box(Modifier.padding(pad)) {
                    TypefacePage(style.font(part), loader) { f -> set(style.withFont(part, f)) }
                }
                return@Scaffold
            }
            Column(Modifier.fillMaxSize().padding(pad)) {
                // Sub-pages keep the preview in view; the main page lets it scroll away.
                if (shown != null) {
                    Box(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                        Preview(style, size, previewDay, rules, fontTick, 168.dp)
                    }
                }
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    when (shown) {
                        null -> MainList(style, previewDay, rules, fontTick, size) { page = it }
                        Page.TEXT -> TextPage(style, set)
                        Page.FONTS -> FontsPage(style, set, loader) { page = it }
                        Page.LOOK -> LookPage(style, set)
                        Page.TAP -> TapPage(style, set) { appPicker = true }
                        else -> {}
                    }
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
private fun MainList(style: Style, day: Day, rules: Rules, fontTick: Int, size: Pair<Int, Int>, open: (Page) -> Unit) {
    val ctx = LocalContext.current
    Preview(style, size, day, rules, fontTick, 220.dp)
    Group {
        item { NavRow("Text", textSummary(style), Icons.Rounded.TextFields, onClick = { open(Page.TEXT) }) }
        item { NavRow("Fonts", fontsSummary(style), Icons.Rounded.FontDownload, onClick = { open(Page.FONTS) }) }
        item { NavRow("Look", lookSummary(style), Icons.Rounded.Palette, onClick = { open(Page.LOOK) }) }
        item {
            NavRow("On tap", if (style.tap == Tap.APP && style.tapPackage.isNotBlank()) "Open " + appLabel(ctx, style.tapPackage) else style.tap.title,
                Icons.Rounded.TouchApp, onClick = { open(Page.TAP) })
        }
    }
    val mgr = remember { AppWidgetManager.getInstance(ctx) }
    if (Widgets.ids(ctx).isEmpty() && mgr.isRequestPinAppWidgetSupported) {
        Group {
            item {
                ListItem(
                    headlineContent = { Text("Not on your home screen yet") },
                    supportingContent = { Text("Add it, then come back to style it") },
                    trailingContent = {
                        FilledTonalButton(onClick = { runCatching { mgr.requestPinAppWidget(Widgets.component(ctx), null, null) } }) { Text("Add") }
                    },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                )
            }
        }
    }
}

private fun textSummary(s: Style) = listOfNotNull(
    if (s.showLabel) s.label.ifBlank { null } else null,
    s.format.title,
    when (s.caption) { Caption.NONE -> null; Caption.WINDOW -> "Time span"; else -> s.caption.title },
).joinToString(" · ")

private fun fontsSummary(s: Style): String {
    val names = listOf(s.numberFont, s.labelFont, s.captionFont).map { it.title }.distinct()
    return if (names.size == 1) names[0] else names[0] + " · " + names.drop(1).joinToString(" · ")
}

private fun lookSummary(s: Style) = listOfNotNull(
    when (s.theme) { ThemeMode.AUTO -> "Auto"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" },
    s.background.title,
    if (s.clock) "Clock" else null,
).joinToString(" · ")

@Composable
private fun TextPage(style: Style, set: (Style) -> Unit) {
    Group {
        item { SwitchRow("Label", style.showLabel, icon = Icons.Rounded.Title) { set(style.copy(showLabel = it)) } }
        if (style.showLabel) item { TextRow("Label text", style.label) { set(style.copy(label = it)) } }
    }
    Group {
        item { ChoiceRow("Number", NumberFormat.entries, style.format, { it.title }, icon = Icons.Rounded.Timer) { set(style.copy(format = it)) } }
        item { ChoiceRow("Below the number", Caption.entries, style.caption, { it.title }, icon = Icons.Rounded.Subtitles) { set(style.copy(caption = it)) } }
    }
}

@Composable
private fun FontsPage(style: Style, set: (Style) -> Unit, loader: FontLoader, open: (Page) -> Unit) {
    loader.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 8.dp)) }
    FontGroup(Part.NUMBER, style, set, loader, Page.NUMBER_TYPEFACE, open, sizeRange = 40..100, sizeText = { "$it%" })
    if (style.showLabel) FontGroup(Part.LABEL, style, set, loader, Page.LABEL_TYPEFACE, open, sizeRange = 70..150, sizeText = { "$it%" })
    if (style.caption != Caption.NONE) FontGroup(Part.CAPTION, style, set, loader, Page.CAPTION_TYPEFACE, open, sizeRange = 70..150, sizeText = { "$it%" })
}

/** One text's font: typeface (its own page), weight and size. Weight applies on release (a Google font may need it downloaded). */
@Composable
private fun FontGroup(
    part: Part, style: Style, set: (Style) -> Unit, loader: FontLoader, typefacePage: Page, open: (Page) -> Unit,
    sizeRange: IntRange, sizeText: (Int) -> String,
) {
    val spec = style.font(part)
    val sizeValue = when (part) { Part.NUMBER -> style.numberScale; Part.LABEL -> style.labelScale; Part.CAPTION -> style.captionScale }
    var weight by remember(spec.weight) { mutableFloatStateOf(spec.weight.toFloat()) }
    Group(part.title) {
        item {
            NavRow("Typeface", if (loader.busy != null && spec.source == FontSource.GOOGLE) "Downloading ${loader.busy}…" else spec.title,
                Icons.Rounded.FontDownload, onClick = { open(typefacePage) })
        }
        item {
            Pad {
                if (spec.source != FontSource.FILE) {
                    SliderLabel(Icons.Rounded.LineWeight, "Weight", weightName(weight.roundToInt()))
                    Slider(
                        value = weight,
                        onValueChange = { weight = it },
                        onValueChangeFinished = { loader.use(spec.copy(weight = (weight / 100).roundToInt() * 100)) { f -> set(style.withFont(part, f)) } },
                        valueRange = 100f..900f,
                        steps = 7,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                SliderLabel(Icons.Rounded.FormatSize, "Size", sizeText(sizeValue))
                Slider(
                    value = sizeValue.toFloat(),
                    onValueChange = { v ->
                        val n = ((v / 5).roundToInt() * 5).coerceIn(sizeRange.first, sizeRange.last)
                        set(when (part) {
                            Part.NUMBER -> style.copy(numberScale = n)
                            Part.LABEL -> style.copy(labelScale = n)
                            Part.CAPTION -> style.copy(captionScale = n)
                        })
                    },
                    valueRange = sizeRange.first.toFloat()..sizeRange.last.toFloat(),
                )
            }
        }
    }
}

@Composable
private fun SliderLabel(icon: ImageVector, title: String, value: String) {
    IconTitle(icon, title) { Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun LookPage(style: Style, set: (Style) -> Unit) {
    Group {
        item {
            Pad {
                IconTitle(Icons.Rounded.DarkMode, "Theme")
                Spacer(Modifier.height(10.dp))
                Segmented(ThemeMode.entries, style.theme, {
                    when (it) { ThemeMode.AUTO -> "Auto"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" }
                }) { set(style.copy(theme = it)) }
            }
        }
        item {
            Pad {
                IconTitle(Icons.Rounded.Wallpaper, "Background")
                Spacer(Modifier.height(10.dp))
                Segmented(Background.entries, style.background, { it.title }) { set(style.copy(background = it)) }
            }
        }
        item { SwitchRow("Clock", style.clock, summary = "A thin clock in the corner, just for looks", icon = Icons.Rounded.Schedule) { set(style.copy(clock = it)) } }
    }
}

@Composable
private fun TapPage(style: Style, set: (Style) -> Unit, pickApp: () -> Unit) {
    val ctx = LocalContext.current
    Group {
        item { ChoiceRow("When you tap the widget", Tap.entries, style.tap, { it.title }, icon = Icons.Rounded.TouchApp) { set(style.copy(tap = it)); if (it == Tap.APP && style.tapPackage.isBlank()) pickApp() } }
        if (style.tap == Tap.APP) item {
            NavRow("App", if (style.tapPackage.isBlank()) "Choose" else appLabel(ctx, style.tapPackage), Icons.Rounded.Apps, onClick = pickApp)
        }
    }
}

internal fun weightName(w: Int) = when {
    w <= 150 -> "Thin"; w <= 250 -> "Extra light"; w <= 350 -> "Light"; w <= 450 -> "Regular"; w <= 550 -> "Medium"
    w <= 650 -> "Semibold"; w <= 750 -> "Bold"; w <= 850 -> "Extra bold"; else -> "Black"
}

/** The widget at its real proportions (the placed widget's size, else 2 × 2) on a wallpaper-like backdrop. */
@Composable
private fun Preview(style: Style, size: Pair<Int, Int>, day: Day, rules: Rules, fontTick: Int, height: Dp) {
    val ctx = LocalContext.current
    val density = LocalDensity.current.density
    val dark = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val (w, h) = size
    val bmp = remember(style, size, day, rules, dark, fontTick) {
        Renderer.render(ctx, style, (w * density).toInt(), (h * density).toInt(), density, day, rules, dark).asImageBitmap()
    }
    Box(
        Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(32.dp))
            .background(Brush.linearGradient(listOf(scheme.tertiaryContainer, scheme.primaryContainer, scheme.secondaryContainer))),
        contentAlignment = Alignment.Center,
    ) {
        Image(bmp, contentDescription = "Widget preview", contentScale = ContentScale.Fit,
            modifier = Modifier.padding(18.dp).sizeIn(maxWidth = w.dp, maxHeight = h.dp).fillMaxSize())
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

private val CATEGORIES = listOf("Sans" to "Sans Serif", "Serif" to "Serif", "Display" to "Display", "Mono" to "Monospace", "Handwriting" to "Handwriting")

/**
 * Every typeface: search and style filters pinned at the top, then the list, each name in its own face.
 * System default and your own file come first, then the suggested Google fonts, then all of them.
 * Tapping one applies it straight away (Google fonts download first); a tick marks the current one.
 */
@Composable
private fun TypefacePage(spec: FontSpec, loader: FontLoader, onSpec: (FontSpec) -> Unit) {
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

    run {
        Column(Modifier.fillMaxSize()) {
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
                            spec.source == FontSource.FILE, false) { pick.launch(arrayOf("*/*")) }
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
