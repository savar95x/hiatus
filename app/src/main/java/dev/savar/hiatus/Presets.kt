package dev.savar.hiatus

/** A curated starting look. Picking one replaces the widget's look; Customise tweaks from there. */
data class Preset(val name: String, val config: WidgetConfig)

object Presets {
    val all: List<Preset> = listOf(
        Preset("Away", WidgetConfig(style = WidgetStyle.HERO, metric = Metric.LAST_AWAY, role = ColorRole.PRIMARY)),
        Preset("Goal ring", WidgetConfig(style = WidgetStyle.RING, metric = Metric.SCREEN_TIME, role = ColorRole.SURFACE)),
        Preset("Cookie", WidgetConfig(style = WidgetStyle.SHAPE, metric = Metric.LAST_AWAY, role = ColorRole.TERTIARY, background = false)),
        Preset(
            "Glance",
            WidgetConfig(
                style = WidgetStyle.TILES, role = ColorRole.SURFACE,
                stats = listOf(Metric.SCREEN_TIME, Metric.LAST_AWAY, Metric.UNLOCKS, Metric.PER_SESSION),
            ),
        ),
        Preset("Day", WidgetConfig(style = WidgetStyle.CHART, role = ColorRole.SECONDARY)),
        Preset("Top apps", WidgetConfig(style = WidgetStyle.APPS, role = ColorRole.SURFACE)),
        Preset("Overview", WidgetConfig(style = WidgetStyle.DASHBOARD, role = ColorRole.PRIMARY)),
        Preset("Midnight", WidgetConfig(style = WidgetStyle.HERO, metric = Metric.SCREEN_TIME, role = ColorRole.BLACK)),
    )
}
