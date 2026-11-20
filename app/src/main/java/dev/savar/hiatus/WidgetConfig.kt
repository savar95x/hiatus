package dev.savar.hiatus

import android.content.Context
import org.json.JSONObject

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

/** Which wallpaper (Material You) palette the widget takes. */
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

enum class TapAction(val label: String) {
    HIATUS("Hiatus"),
    APP("App…"),
    REFRESH("Refresh"),
}

/** The one Hiatus widget: time away today. Everything here is about how it looks. */
data class WidgetConfig(
    val showLabel: Boolean = true,
    val customLabel: String = "",
    val uppercaseLabel: Boolean = false,
    val showCaption: Boolean = true,
    val format: DurationFormat = DurationFormat.UNITS,
    val smallUnits: Boolean = true,
    val font: FontSpec = FontSpec(weight = 500),
    val fontForLabels: Boolean = false,
    val useWallpaperColors: Boolean = true,
    val role: ColorRole = ColorRole.SURFACE,
    val theme: ThemeMode = ThemeMode.DARK,
    val bgColor: Int = 0xFF1B1B1F.toInt(),
    val numberColor: Int = 0xFFB8C4FF.toInt(),
    val textColor: Int = 0xFFE4E2E6.toInt(),
    val background: Boolean = true,
    val bgOpacity: Int = 100,
    val systemCorners: Boolean = true, // same roundness as the phone's other widgets
    val cornerRadius: Int = 24,
    val sizePercent: Int = 100,
    val align: HAlign = HAlign.START,
    val tapAction: TapAction = TapAction.HIATUS,
    val tapPackage: String = "",
) {
    fun label(): String = customLabel.ifBlank { "Away today" }.let { if (uppercaseLabel) it.uppercase() else it }

    fun toJson(): String = JSONObject().apply {
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
        put("systemCorners", systemCorners)
        put("cornerRadius", cornerRadius)
        put("sizePercent", sizePercent)
        put("align", align.name)
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
                WidgetConfig(
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
                    systemCorners = o.optBoolean("systemCorners", d.systemCorners),
                    cornerRadius = o.optInt("cornerRadius", d.cornerRadius),
                    sizePercent = o.optInt("sizePercent", d.sizePercent),
                    align = enumOr(o.optString("align"), d.align),
                    tapAction = enumOr(o.optString("tapAction"), d.tapAction),
                    tapPackage = o.optString("tapPackage", d.tapPackage),
                )
            } catch (e: Exception) {
                d
            }
        }
    }
}

/**
 * Per-widget settings. New widgets start from whatever was edited last. The prefs file is new as of 0.6.0
 * ("widget"): the old one held looks that no longer exist, so every widget starts fresh on the new default.
 */
object ConfigStore {
    private const val PREFS = "widget"
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

/** App-wide tracking settings (prefs `ui`). */
object AppPrefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE)

    /** Shortest gap that counts as a break, in minutes. */
    fun minBreakMinutes(ctx: Context): Int = p(ctx).getInt("min_break", 1)
    fun setMinBreakMinutes(ctx: Context, m: Int) = p(ctx).edit().putInt("min_break", m).apply()

    fun excludeSleep(ctx: Context): Boolean = p(ctx).getBoolean("exclude_sleep", false)
    fun setExcludeSleep(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("exclude_sleep", on).apply()

    /** Bedtime and wake-up, minutes after midnight. */
    fun bedtime(ctx: Context): Int = p(ctx).getInt("bedtime", 23 * 60)
    fun wakeTime(ctx: Context): Int = p(ctx).getInt("wake", 7 * 60)
    fun setBedtime(ctx: Context, m: Int) = p(ctx).edit().putInt("bedtime", m).apply()
    fun setWakeTime(ctx: Context, m: Int) = p(ctx).edit().putInt("wake", m).apply()

    /** The sleep window to leave out, or null when sleep counts as away. */
    fun sleep(ctx: Context): Sleep? = if (excludeSleep(ctx)) Sleep(bedtime(ctx), wakeTime(ctx)) else null
}
