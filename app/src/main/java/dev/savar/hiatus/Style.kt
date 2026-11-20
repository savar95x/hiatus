package dev.savar.hiatus

import android.content.Context
import org.json.JSONObject

enum class NumberFormat(val title: String) {
    HM("2h 45m"), WORDS("2 hr 45 min"), CLOCK("2:45"), DECIMAL("2.8h"), MINUTES("165m"), HOURS("2h"),
}

enum class Caption(val title: String) {
    SHARE("Share of day"), LONGEST("Longest break"), LAST("Last break"), BREAKS("Breaks"), CUSTOM("Custom"), NONE("None"),
}

/** Which colours the widget takes: the wallpaper's Material tones, your own, or none. */
enum class Background(val title: String) {
    SURFACE("Surface"), PRIMARY("Primary"), SECONDARY("Secondary"), TERTIARY("Tertiary"), CUSTOM("Custom"), NONE("None"),
}

enum class HAlign { START, CENTER, END }
enum class VAlign(val title: String) { SPREAD("Spread"), TOP("Top"), CENTER("Centre"), BOTTOM("Bottom") }
enum class IconKind(val title: String) { NONE("None"), MARK("Hiatus"), MOON("Moon"), PAUSE("Pause") }
enum class IconPlace(val title: String) { CORNER("Corner"), BEHIND("Behind") }
enum class Tap(val title: String) { OPEN("Open Hiatus"), APP("Open an app"), REFRESH("Refresh") }
enum class FontSource { SYSTEM, GOOGLE, FILE }

data class FontSpec(
    val source: FontSource = FontSource.SYSTEM,
    val family: String = "sans-serif",   // system family, Google family name, or stored file name
    val weight: Int = 400,
) {
    val title: String get() = when (source) {
        FontSource.SYSTEM -> Fonts.system.firstOrNull { it.first == family }?.second ?: family
        FontSource.GOOGLE -> family
        FontSource.FILE -> "Your font"
    }

    fun json(): JSONObject = JSONObject().put("s", source.name).put("f", family).put("w", weight)

    companion object {
        fun from(o: JSONObject?, def: FontSpec): FontSpec = if (o == null) def else FontSpec(
            FontSource.entries.firstOrNull { it.name == o.optString("s") } ?: def.source,
            o.optString("f", def.family),
            o.optInt("w", def.weight),
        )
    }
}

/** The widget's whole look. One style, shared by every Hiatus widget on the home screen. */
data class Style(
    val label: String = "Away today",
    val showLabel: Boolean = true,
    val caption: Caption = Caption.SHARE,
    val customCaption: String = "",
    val format: NumberFormat = NumberFormat.HM,
    val smallUnits: Boolean = true,
    val numberFont: FontSpec = FontSpec(weight = 400),
    val textFont: FontSpec = FontSpec(weight = 500),
    val numberScale: Int = 100,          // % of the size that fits
    val textScale: Int = 100,
    val theme: ThemeMode = ThemeMode.DARK,
    val background: Background = Background.SURFACE,
    val bgColor: Int = 0xFF1C1C1E.toInt(),
    val bgAlpha: Int = 100,              // %
    val numberColor: Int? = null,        // null = from the background's palette
    val textColor: Int? = null,
    val hAlign: HAlign = HAlign.START,
    val vAlign: VAlign = VAlign.SPREAD,
    val corners: Int = -1,               // dp; -1 = same as the phone's other widgets
    val padding: Int = 16,               // dp
    val icon: IconKind = IconKind.NONE,
    val iconPlace: IconPlace = IconPlace.CORNER,
    val iconAlpha: Int = 22,             // %
    val iconColor: Int? = null,          // null = the palette's accent
    val tap: Tap = Tap.OPEN,
    val tapPackage: String = "",
) {
    fun json(): String = JSONObject().apply {
        put("label", label); put("showLabel", showLabel)
        put("caption", caption.name); put("customCaption", customCaption)
        put("format", format.name); put("smallUnits", smallUnits)
        put("numberFont", numberFont.json()); put("textFont", textFont.json())
        put("numberScale", numberScale); put("textScale", textScale)
        put("theme", theme.name); put("background", background.name)
        put("bgColor", bgColor); put("bgAlpha", bgAlpha)
        numberColor?.let { put("numberColor", it) }
        textColor?.let { put("textColor", it) }
        put("hAlign", hAlign.name); put("vAlign", vAlign.name)
        put("corners", corners); put("padding", padding)
        put("icon", icon.name); put("iconPlace", iconPlace.name); put("iconAlpha", iconAlpha)
        iconColor?.let { put("iconColor", it) }
        put("tap", tap.name); put("tapPackage", tapPackage)
    }.toString()

    companion object {
        private inline fun <reified E : Enum<E>> JSONObject.enum(key: String, def: E): E =
            enumValues<E>().firstOrNull { it.name == optString(key) } ?: def

        private fun JSONObject.intOrNull(key: String): Int? = if (has(key)) optInt(key) else null

        fun parse(text: String?): Style {
            val d = Style()
            if (text.isNullOrBlank()) return d
            return runCatching {
                val o = JSONObject(text)
                Style(
                    label = o.optString("label", d.label),
                    showLabel = o.optBoolean("showLabel", d.showLabel),
                    caption = o.enum("caption", d.caption),
                    customCaption = o.optString("customCaption", d.customCaption),
                    format = o.enum("format", d.format),
                    smallUnits = o.optBoolean("smallUnits", d.smallUnits),
                    numberFont = FontSpec.from(o.optJSONObject("numberFont"), d.numberFont),
                    textFont = FontSpec.from(o.optJSONObject("textFont"), d.textFont),
                    numberScale = o.optInt("numberScale", d.numberScale),
                    textScale = o.optInt("textScale", d.textScale),
                    theme = o.enum("theme", d.theme),
                    background = o.enum("background", d.background),
                    bgColor = o.optInt("bgColor", d.bgColor),
                    bgAlpha = o.optInt("bgAlpha", d.bgAlpha),
                    numberColor = o.intOrNull("numberColor"),
                    textColor = o.intOrNull("textColor"),
                    hAlign = o.enum("hAlign", d.hAlign),
                    vAlign = o.enum("vAlign", d.vAlign),
                    corners = o.optInt("corners", d.corners),
                    padding = o.optInt("padding", d.padding),
                    icon = o.enum("icon", d.icon),
                    iconPlace = o.enum("iconPlace", d.iconPlace),
                    iconAlpha = o.optInt("iconAlpha", d.iconAlpha),
                    iconColor = o.intOrNull("iconColor"),
                    tap = o.enum("tap", d.tap),
                    tapPackage = o.optString("tapPackage", d.tapPackage),
                )
            }.getOrDefault(d)
        }

        // v2: the 0.7 rebuild. Older styles (key "w_*" in prefs "widget") are not carried over.
        private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences("style", Context.MODE_PRIVATE)
        @Volatile private var cache: Style? = null

        fun load(ctx: Context): Style = cache ?: parse(p(ctx).getString("v2", null)).also { cache = it }

        fun save(ctx: Context, s: Style) {
            cache = s
            p(ctx).edit().putString("v2", s.json()).apply()
        }
    }
}
