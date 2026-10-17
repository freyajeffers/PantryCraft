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

private const val LIGHT_PRIMARY = 0xFF415F91
private const val LIGHT_PRIMARY_CONTAINER = 0xFFD6E3FF
private const val LIGHT_ON_PRIMARY_CONTAINER = 0xFF284777
private const val LIGHT_SECONDARY = 0xFF565F71
private const val LIGHT_SECONDARY_CONTAINER = 0xFFDAE2F9
private const val LIGHT_ON_SECONDARY_CONTAINER = 0xFF3E4759
private const val LIGHT_BACKGROUND = 0xFFFFFBFF
private const val LIGHT_ON_BACKGROUND = 0xFF1A1B20
private const val LIGHT_SURFACE_VARIANT = 0xFFE1E2EC
private const val LIGHT_ON_SURFACE_VARIANT = 0xFF44474F
private const val LIGHT_OUTLINE = 0xFF74777F
private const val LIGHT_OUTLINE_VARIANT = 0xFFC4C6D0
private const val LIGHT_ERROR = 0xFFBA1A1A
private const val LIGHT_ERROR_CONTAINER = 0xFFFFDAD6
private const val LIGHT_ON_ERROR_CONTAINER = 0xFF410002
private const val DARK_PRIMARY = 0xFFB8C7FF
private const val DARK_ON_PRIMARY = 0xFF172B60
private const val DARK_PRIMARY_CONTAINER = 0xFF263F78
private const val DARK_ON_PRIMARY_CONTAINER = 0xFFDCE2FF
private const val DARK_SECONDARY = 0xFFC3C6D4
private const val DARK_ON_SECONDARY = 0xFF2D3039
private const val DARK_SECONDARY_CONTAINER = 0xFF444750
private const val DARK_ON_SECONDARY_CONTAINER = 0xFFDFE2F0
private const val DARK_ON_BACKGROUND = 0xFFE6E1E5
private const val DARK_SURFACE_VARIANT = 0xFF1A1A1A
private const val DARK_ON_SURFACE_VARIANT = 0xFFC7C5CA
private const val DARK_SURFACE_CONTAINER = 0xFF101010
private const val DARK_SURFACE_CONTAINER_LOW = 0xFF080808
private const val DARK_SURFACE_CONTAINER_HIGH = 0xFF181818
private const val DARK_SURFACE_CONTAINER_HIGHEST = 0xFF202020
private const val DARK_OUTLINE = 0xFF918F94
private const val DARK_OUTLINE_VARIANT = 0xFF46464A
private const val DARK_ERROR = 0xFFFFB4AB
private const val DARK_ON_ERROR = 0xFF690005
private const val DARK_ERROR_CONTAINER = 0xFF93000A

private val PantryCraftLightColorScheme =
    lightColorScheme(
        primary = Color(LIGHT_PRIMARY),
        onPrimary = Color.White,
        primaryContainer = Color(LIGHT_PRIMARY_CONTAINER),
        onPrimaryContainer = Color(LIGHT_ON_PRIMARY_CONTAINER),
        secondary = Color(LIGHT_SECONDARY),
        onSecondary = Color.White,
        secondaryContainer = Color(LIGHT_SECONDARY_CONTAINER),
        onSecondaryContainer = Color(LIGHT_ON_SECONDARY_CONTAINER),
        background = Color(LIGHT_BACKGROUND),
        onBackground = Color(LIGHT_ON_BACKGROUND),
        surface = Color(LIGHT_BACKGROUND),
        onSurface = Color(LIGHT_ON_BACKGROUND),
        surfaceVariant = Color(LIGHT_SURFACE_VARIANT),
        onSurfaceVariant = Color(LIGHT_ON_SURFACE_VARIANT),
        outline = Color(LIGHT_OUTLINE),
        outlineVariant = Color(LIGHT_OUTLINE_VARIANT),
        error = Color(LIGHT_ERROR),
        onError = Color.White,
        errorContainer = Color(LIGHT_ERROR_CONTAINER),
        onErrorContainer = Color(LIGHT_ON_ERROR_CONTAINER),
    )

private val OledDarkColorScheme =
    darkColorScheme(
        primary = Color(DARK_PRIMARY),
        onPrimary = Color(DARK_ON_PRIMARY),
        primaryContainer = Color(DARK_PRIMARY_CONTAINER),
        onPrimaryContainer = Color(DARK_ON_PRIMARY_CONTAINER),
        secondary = Color(DARK_SECONDARY),
        onSecondary = Color(DARK_ON_SECONDARY),
        secondaryContainer = Color(DARK_SECONDARY_CONTAINER),
        onSecondaryContainer = Color(DARK_ON_SECONDARY_CONTAINER),
        background = Color.Black,
        onBackground = Color(DARK_ON_BACKGROUND),
        surface = Color.Black,
        onSurface = Color(DARK_ON_BACKGROUND),
        surfaceVariant = Color(DARK_SURFACE_VARIANT),
        onSurfaceVariant = Color(DARK_ON_SURFACE_VARIANT),
        surfaceContainer = Color(DARK_SURFACE_CONTAINER),
        surfaceContainerLow = Color(DARK_SURFACE_CONTAINER_LOW),
        surfaceContainerHigh = Color(DARK_SURFACE_CONTAINER_HIGH),
        surfaceContainerHighest = Color(DARK_SURFACE_CONTAINER_HIGHEST),
        outline = Color(DARK_OUTLINE),
        outlineVariant = Color(DARK_OUTLINE_VARIANT),
        error = Color(DARK_ERROR),
        onError = Color(DARK_ON_ERROR),
        errorContainer = Color(DARK_ERROR_CONTAINER),
        onErrorContainer = Color(LIGHT_ERROR_CONTAINER),
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
        content = content,
    )
}
