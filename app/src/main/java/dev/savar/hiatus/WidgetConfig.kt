package dev.savar.hiatus

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class WidgetStyle(val label: String) {
    HERO("Number"),
    RING("Ring"),
    SHAPE("Shape"),
    TILES("Tiles"),
    CHART("Chart"),
    APPS("Top apps"),
    DASHBOARD("Dashboard"),
}

/** Everything a widget can show as a number. */
/** [away]: the app's main subject; these are offered first, the rest under "More stats". */
enum class Metric(val label: String, val short: String, val away: Boolean = false) {
    LAST_AWAY("Last away", "Away", away = true),
    AWAY_TODAY("Away today", "Away", away = true),
    SCREEN_TIME("Screen time", "Screen"),
    UNLOCKS("Unlocks", "Unlocks"),
    PICKUPS("Screen wakes", "Wakes"),
    PER_SESSION("Per session", "Session"),
    LONGEST_AWAY("Longest away", "Longest", away = true),
    SESSIONS("Sessions", "Sessions"),
}

enum class FontSource(val label: String) {
    SYSTEM("System"),
    GOOGLE("Google"),
    FILE("File"),
}

/** One font choice: where it comes from plus the details for that source. */
data class FontSpec(
    val source: FontSource = FontSource.SYSTEM,
    val systemFamily: String = "sans-serif",
    val weight: Int = 500,
    val googleFamily: String = "",
    val localFontFile: String = "",
)

/** Which wallpaper (Material You) palette the widget's container takes. */
enum class ColorRole(val label: String) {
    SURFACE("Surface"),
    PRIMARY("Primary"),
    SECONDARY("Secondary"),
    TERTIARY("Tertiary"),
    BLACK("Black"),
}

enum class ThemeMode(val label: String) {
    SYSTEM("Auto"),
    LIGHT("Light"),
    DARK("Dark"),
}

enum class DurationFormat(val label: String) {
    UNITS("2h 13m"),
    LONG("2 hr 13 min"),
    COLON("2:13"),
    DECIMAL("2.2h"),
}

enum class HAlign(val label: String) {
    START("Left"),
    CENTER("Centre"),
}

enum class ShapeKind(val label: String) {
    COOKIE_9("Cookie"),
    COOKIE_4("Cookie 4"),
    SUNNY("Sunny"),
    SOFT_BURST("Burst"),
    FLOWER("Flower"),
    CLOVER_4("Clover"),
    CLOVER_8("Clover 8"),
    PUFFY("Puffy"),
    GEM("Gem"),
    PENTAGON("Pentagon"),
    CIRCLE("Circle"),
    SQUARE("Square"),
}

enum class TapAction(val label: String) {
    HIATUS("Hiatus"),
    APP("App…"),
    NONE("None"),
}

data class WidgetConfig(
    val style: WidgetStyle = WidgetStyle.HERO,
    val metric: Metric = Metric.LAST_AWAY,
    val stats: List<Metric> = listOf(Metric.AWAY_TODAY, Metric.LONGEST_AWAY, Metric.UNLOCKS, Metric.SCREEN_TIME),
    val showLabel: Boolean = true,
    val customLabel: String = "",
    val uppercaseLabel: Boolean = false,
    val showCaption: Boolean = true,
    val format: DurationFormat = DurationFormat.UNITS,
    val smallUnits: Boolean = true,
    val font: FontSpec = FontSpec(weight = 500),
    val fontForLabels: Boolean = false,
    val useWallpaperColors: Boolean = true,
    val role: ColorRole = ColorRole.PRIMARY,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val bgColor: Int = 0xFF0E0F13.toInt(),
    val numberColor: Int = 0xFFB8C4FF.toInt(),
    val textColor: Int = 0xFFE4E2E6.toInt(),
    val background: Boolean = true,
    val bgOpacity: Int = 100,
    val cornerRadius: Int = 28,
    val sizePercent: Int = 100,
    val align: HAlign = HAlign.START,
    val goalMinutes: Int = 180,
    val wavy: Boolean = true,
    val shape: ShapeKind = ShapeKind.COOKIE_9,
    val appsCount: Int = 4,
    val tapAction: TapAction = TapAction.HIATUS,
    val tapPackage: String = "",
) {
    /** Label shown above the number. */
    fun label(): String = customLabel.ifBlank { metric.label }.let { if (uppercaseLabel) it.uppercase() else it }

    fun toJson(): String = JSONObject().apply {
        put("style", style.name)
        put("metric", metric.name)
        put("stats", JSONArray(stats.map { it.name }))
        put("showLabel", showLabel)
        put("customLabel", customLabel)
        put("uppercaseLabel", uppercaseLabel)
        put("showCaption", showCaption)
        put("format", format.name)
        put("smallUnits", smallUnits)
        put("fontSource", font.source.name)
        put("systemFamily", font.systemFamily)
        put("fontWeight", font.weight)
        put("googleFamily", font.googleFamily)
        put("localFontFile", font.localFontFile)
        put("fontForLabels", fontForLabels)
        put("useWallpaperColors", useWallpaperColors)
        put("role", role.name)
        put("theme", theme.name)
        put("bgColor", bgColor)
        put("numberColor", numberColor)
        put("textColor", textColor)
        put("background", background)
        put("bgOpacity", bgOpacity)
        put("cornerRadius", cornerRadius)
        put("sizePercent", sizePercent)
        put("align", align.name)
        put("goalMinutes", goalMinutes)
        put("wavy", wavy)
        put("shape", shape.name)
        put("appsCount", appsCount)
        put("tapAction", tapAction.name)
        put("tapPackage", tapPackage)
    }.toString()

    companion object {
        private inline fun <reified T : Enum<T>> enumOr(name: String?, def: T): T =
            enumValues<T>().firstOrNull { it.name == name } ?: def

        fun fromJson(s: String): WidgetConfig {
            val d = WidgetConfig()
            return try {
                val o = JSONObject(s)
                val stats = o.optJSONArray("stats")?.let { a ->
                    (0 until a.length()).mapNotNull { i -> Metric.entries.firstOrNull { it.name == a.optString(i) } }
                } ?: d.stats
                WidgetConfig(
                    style = enumOr(o.optString("style"), d.style),
                    metric = enumOr(o.optString("metric"), d.metric),
                    stats = stats,
                    showLabel = o.optBoolean("showLabel", d.showLabel),
                    customLabel = o.optString("customLabel", d.customLabel),
                    uppercaseLabel = o.optBoolean("uppercaseLabel", d.uppercaseLabel),
                    showCaption = o.optBoolean("showCaption", d.showCaption),
                    format = enumOr(o.optString("format"), d.format),
                    smallUnits = o.optBoolean("smallUnits", d.smallUnits),
                    font = FontSpec(
                        source = enumOr(o.optString("fontSource"), d.font.source),
                        systemFamily = o.optString("systemFamily", d.font.systemFamily),
                        weight = o.optInt("fontWeight", d.font.weight),
                        googleFamily = o.optString("googleFamily", d.font.googleFamily),
                        localFontFile = o.optString("localFontFile", d.font.localFontFile),
                    ),
                    fontForLabels = o.optBoolean("fontForLabels", d.fontForLabels),
                    useWallpaperColors = o.optBoolean("useWallpaperColors", d.useWallpaperColors),
                    role = enumOr(o.optString("role"), d.role),
                    theme = enumOr(o.optString("theme"), d.theme),
                    bgColor = o.optInt("bgColor", d.bgColor),
                    numberColor = o.optInt("numberColor", d.numberColor),
                    textColor = o.optInt("textColor", d.textColor),
                    background = o.optBoolean("background", d.background),
                    bgOpacity = o.optInt("bgOpacity", d.bgOpacity),
                    cornerRadius = o.optInt("cornerRadius", d.cornerRadius),
                    sizePercent = o.optInt("sizePercent", d.sizePercent),
                    align = enumOr(o.optString("align"), d.align),
                    goalMinutes = o.optInt("goalMinutes", d.goalMinutes),
                    wavy = o.optBoolean("wavy", d.wavy),
                    shape = enumOr(o.optString("shape"), d.shape),
                    appsCount = o.optInt("appsCount", d.appsCount),
                    tapAction = enumOr(o.optString("tapAction"), d.tapAction),
                    tapPackage = o.optString("tapPackage", d.tapPackage),
                )
            } catch (e: Exception) {
                d
            }
        }
    }
}

/** Per-widget settings. New widgets start from whatever was edited last. */
object ConfigStore {
    private const val PREFS = "widget_configs"
    private const val LAST = "last"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(ctx: Context, widgetId: Int): WidgetConfig {
        val p = prefs(ctx)
        val s = p.getString("w_$widgetId", null) ?: p.getString(LAST, null)
        return s?.let { WidgetConfig.fromJson(it) } ?: WidgetConfig()
    }

    fun save(ctx: Context, widgetId: Int, cfg: WidgetConfig) {
        val json = cfg.toJson()
        prefs(ctx).edit().apply {
            if (widgetId >= 0) putString("w_$widgetId", json)
            putString(LAST, json)
        }.apply()
    }

    fun delete(ctx: Context, widgetId: Int) {
        prefs(ctx).edit().remove("w_$widgetId").apply()
    }
}

/** App-wide settings (prefs `ui`). */
object AppPrefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE)

    /** Shortest break that counts as "away", in minutes. */
    fun minBreakMinutes(ctx: Context): Int = p(ctx).getInt("min_break", 1)
    fun setMinBreakMinutes(ctx: Context, m: Int) = p(ctx).edit().putInt("min_break", m).apply()
}
