package com.ashuchat.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalAppTheme = staticCompositionLocalOf { AppTheme.CLASSIC }

private val DarkColorScheme = darkColorScheme(
    primary = RosePrimary,
    secondary = RoseAccent,
    tertiary = VioletAccent,
    background = MidnightBg,
    surface = SurfaceElevated,
    onPrimary = TextPrimary,
    onSecondary = TextPrimary,
    onTertiary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

private val NeoColorScheme = lightColorScheme(
    primary = NeoBlack,
    onPrimary = Color.White,
    secondary = NeoBlack,
    onSecondary = Color.White,
    tertiary = NeoBlack,
    onTertiary = Color.White,
    background = NeoBackground,
    onBackground = NeoBlack,
    surface = Color.White,
    onSurface = NeoBlack,
    surfaceVariant = Color.White,
    onSurfaceVariant = NeoBlack,
    outline = NeoBlack
)

@Composable
fun AshuChatTheme(
    appTheme: AppTheme = AppTheme.CLASSIC,
    content: @Composable () -> Unit
) {
    val isPaper = appTheme == AppTheme.PAPER
    val colorScheme = if (isPaper) NeoColorScheme else DarkColorScheme
    val typography = if (isPaper) PaperTypography else ClassicTypography
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                if (isPaper) {
                    window.statusBarColor = NeoHeaderYellow.toArgb()
                    window.navigationBarColor = NeoBackground.toArgb()
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
                    WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
                } else {
                    window.statusBarColor = MidnightBg.toArgb()
                    window.navigationBarColor = MidnightBg.toArgb()
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                    WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
                }
            }
        }
    }

    CompositionLocalProvider(LocalAppTheme provides appTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
