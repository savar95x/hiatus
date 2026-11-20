package dev.savar.hiatus

/** A curated starting look. Picking one replaces the widget's look; Customise tweaks from there. */
data class Preset(val name: String, val config: WidgetConfig)

object Presets {
    /** Away time is what Hiatus is for: these lead. */
    val away: List<Preset> = listOf(
        Preset("Away", WidgetConfig(style = WidgetStyle.AWAY, metric = Metric.AWAY_TODAY, role = ColorRole.PRIMARY)),
        Preset("Midnight", WidgetConfig(style = WidgetStyle.AWAY, metric = Metric.AWAY_TODAY, role = ColorRole.BLACK, shape = ShapeKind.SOFT_BURST)),
        Preset("Cookie", WidgetConfig(style = WidgetStyle.SHAPE, metric = Metric.AWAY_TODAY, role = ColorRole.TERTIARY, background = false)),
        Preset(
            "Away + day",
            WidgetConfig(
                style = WidgetStyle.DASHBOARD, metric = Metric.AWAY_TODAY, role = ColorRole.SURFACE,
                stats = listOf(Metric.LONGEST_AWAY, Metric.LAST_AWAY, Metric.UNLOCKS),
            ),
        ),
    )

    /** Last break and screen-time looks, one tap further. */
    val more: List<Preset> = listOf(
        Preset("Last away", WidgetConfig(style = WidgetStyle.HERO, metric = Metric.LAST_AWAY, role = ColorRole.PRIMARY)),
        Preset("Goal ring", WidgetConfig(style = WidgetStyle.RING, metric = Metric.SCREEN_TIME, role = ColorRole.SURFACE)),
        Preset(
            "Glance",
            WidgetConfig(
                style = WidgetStyle.TILES, role = ColorRole.SURFACE,
                stats = listOf(Metric.SCREEN_TIME, Metric.UNLOCKS, Metric.PER_SESSION, Metric.PICKUPS),
            ),
        ),
        Preset("Day", WidgetConfig(style = WidgetStyle.CHART, role = ColorRole.SECONDARY)),
        Preset("Top apps", WidgetConfig(style = WidgetStyle.APPS, role = ColorRole.SURFACE)),
    )

    val all: List<Preset> = away + more
}
