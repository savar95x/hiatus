package dev.savar.hiatus

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Pure black (AMOLED) take on a dark scheme: the page goes to #000, raised surfaces keep a hint of tone. */
internal fun ColorScheme.pureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = lerp(Color.Black, surfaceContainerLow, 0.5f),
    surfaceContainer = lerp(Color.Black, surfaceContainer, 0.6f),
    surfaceContainerHigh = lerp(Color.Black, surfaceContainerHigh, 0.7f),
    surfaceContainerHighest = lerp(Color.Black, surfaceContainerHighest, 0.78f),
    surfaceVariant = lerp(Color.Black, surfaceVariant, 0.75f),
)

@Composable
internal fun isAppDark(): Boolean = when (Prefs.theme) {
    ThemeMode.AUTO -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Material 3 Expressive with the wallpaper's colours (Material You); plain Material colours before Android 12. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HiatusTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    Prefs.init(ctx)
    val dark = isAppDark()
    val base = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    val scheme = if (dark && Prefs.pureBlack) base.pureBlack() else base
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        (ctx as? Activity)?.window?.let { w ->
            w.setBackgroundDrawable(ColorDrawable(scheme.background.toArgb()))
            WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive(), content = content)
}

/**
 * Fixed meaning colours for the analytics, the same in every wallpaper so a chart reads at a glance:
 * away is green, phone in use is coral (Hiatus's accent), sleep is indigo.
 */
class Sem(
    val away: Color, val awayContainer: Color, val onAwayContainer: Color,
    val use: Color, val useContainer: Color, val onUseContainer: Color,
    val sleep: Color,
)

private val DarkSem = Sem(
    away = Color(0xFF7DD8A6), awayContainer = Color(0xFF16382A), onAwayContainer = Color(0xFFBDF2D2),
    use = Color(0xFFF0A383), useContainer = Color(0xFF47281D), onUseContainer = Color(0xFFFFDBCD),
    sleep = Color(0xFFA8B2FF),
)
private val LightSem = Sem(
    away = Color(0xFF1F7A4D), awayContainer = Color(0xFFC6EFD5), onAwayContainer = Color(0xFF00391F),
    use = Color(0xFFB4532F), useContainer = Color(0xFFFFDBCD), onUseContainer = Color(0xFF3A0B00),
    sleep = Color(0xFF4B5BC6),
)

@Composable
internal fun sem(): Sem = if (isAppDark()) DarkSem else LightSem
