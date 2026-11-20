package dev.savar.hiatus

import android.content.Context
import org.json.JSONObject

enum class NumberFormat(val title: String) { HM("2h 45m"), WORDS("2 hr 45 min"), CLOCK("2:45") }

enum class Caption(val title: String) {
    WINDOW("Time span"), SHARE("Share of day"), LONGEST("Longest break"), NONE("None"),
}

/** Dark surface tinted by the wallpaper, a stronger wallpaper tone, or no background. */
enum class Background(val title: String) { SURFACE("Surface"), TINTED("Tinted"), NONE("Clear") }

enum class HAlign(val title: String) { START("Start"), CENTER("Centre") }
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

/** The widget's look. One style, shared by every Hiatus widget on the home screen. */
data class Style(
    val label: String = "Away today",
    val showLabel: Boolean = true,
    val caption: Caption = Caption.WINDOW,
    val format: NumberFormat = NumberFormat.HM,
    val numberFont: FontSpec = FontSpec(weight = 400),
    val textFont: FontSpec = FontSpec(weight = 500),
    val numberScale: Int = 100,          // % of the size that fits
    val theme: ThemeMode = ThemeMode.DARK,
    val background: Background = Background.SURFACE,
    val hAlign: HAlign = HAlign.START,
    val shape: Boolean = false,          // a faint abstract shape off the top-right corner
    val tap: Tap = Tap.OPEN,
    val tapPackage: String = "",
) {
    fun json(): String = JSONObject().apply {
        put("label", label); put("showLabel", showLabel); put("caption", caption.name); put("format", format.name)
        put("numberFont", numberFont.json()); put("textFont", textFont.json()); put("numberScale", numberScale)
        put("theme", theme.name); put("background", background.name); put("hAlign", hAlign.name)
        put("shape", shape); put("tap", tap.name); put("tapPackage", tapPackage)
    }.toString()

    companion object {
        private inline fun <reified E : Enum<E>> JSONObject.enum(key: String, def: E): E =
            enumValues<E>().firstOrNull { it.name == optString(key) } ?: def

        /** Reads this version's style, or keeps the fonts, label and tap action of a 0.7.0 one. */
        fun parse(text: String?): Style {
            val d = Style()
            if (text.isNullOrBlank()) return d
            return runCatching {
                val o = JSONObject(text)
                Style(
                    label = o.optString("label", d.label),
                    showLabel = o.optBoolean("showLabel", d.showLabel),
                    caption = o.enum("caption", d.caption).let { if (o.has("shape")) it else d.caption },
                    format = o.enum("format", d.format),
                    numberFont = FontSpec.from(o.optJSONObject("numberFont"), d.numberFont),
                    textFont = FontSpec.from(o.optJSONObject("textFont"), d.textFont),
                    numberScale = o.optInt("numberScale", d.numberScale).coerceIn(40, 100),
                    theme = o.enum("theme", d.theme),
                    background = o.enum("background", d.background),
                    hAlign = o.enum("hAlign", d.hAlign),
                    shape = o.optBoolean("shape", d.shape),
                    tap = o.enum("tap", d.tap),
                    tapPackage = o.optString("tapPackage", d.tapPackage),
                )
            }.getOrDefault(d)
        }

        private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences("style", Context.MODE_PRIVATE)
        @Volatile private var cache: Style? = null

        fun load(ctx: Context): Style = cache ?: parse(p(ctx).getString("v2", null)).also { cache = it }

        fun save(ctx: Context, s: Style) {
            cache = s
            p(ctx).edit().putString("v2", s.json()).apply()
        }
    }
}
