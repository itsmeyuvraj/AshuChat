package com.ashuchat.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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
    content: @Composable () -> Unit
) {
    val colorScheme = NeoColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = NeoHeaderYellow.toArgb()
                window.navigationBarColor = NeoBackground.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
