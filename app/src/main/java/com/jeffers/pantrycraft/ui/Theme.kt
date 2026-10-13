package com.jeffers.pantrycraft.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val PantryCraftLightColorScheme = lightColorScheme(
    primary = Color(0xFF415F91),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF284777),
    secondary = Color(0xFF565F71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDAE2F9),
    onSecondaryContainer = Color(0xFF3E4759),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1A1B20),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1A1B20),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val OledDarkColorScheme = darkColorScheme(
    primary = Color(0xFFB8C7FF),
    onPrimary = Color(0xFF172B60),
    primaryContainer = Color(0xFF263F78),
    onPrimaryContainer = Color(0xFFDCE2FF),
    secondary = Color(0xFFC3C6D4),
    onSecondary = Color(0xFF2D3039),
    secondaryContainer = Color(0xFF444750),
    onSecondaryContainer = Color(0xFFDFE2F0),
    background = Color.Black,
    onBackground = Color(0xFFE6E1E5),
    surface = Color.Black,
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFC7C5CA),
    surfaceContainer = Color(0xFF101010),
    surfaceContainerLow = Color(0xFF080808),
    surfaceContainerHigh = Color(0xFF181818),
    surfaceContainerHighest = Color(0xFF202020),
    outline = Color(0xFF918F94),
    outlineVariant = Color(0xFF46464A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun PantryCraftTheme(content: @Composable () -> Unit) {
    val isDarkTheme = isSystemInDarkTheme()
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val barColor = if (isDarkTheme) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            window.statusBarColor = barColor
            window.navigationBarColor = barColor
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDarkTheme
                isAppearanceLightNavigationBars = !isDarkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = if (isDarkTheme) OledDarkColorScheme else PantryCraftLightColorScheme,
        content = content
    )
}
