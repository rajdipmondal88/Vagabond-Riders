package com.example.ui.theme

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

private val LightColorScheme = lightColorScheme(
    primary = GeoPrimary,
    onPrimary = GeoOnPrimary,
    primaryContainer = GeoPrimaryContainer,
    onPrimaryContainer = GeoOnPrimaryContainer,
    secondary = GeoSecondary,
    onSecondary = GeoOnSecondary,
    secondaryContainer = GeoSecondaryContainer,
    onSecondaryContainer = GeoOnSecondaryContainer,
    tertiary = GeoTertiary,
    onTertiary = GeoOnTertiary,
    tertiaryContainer = GeoTertiaryContainer,
    onTertiaryContainer = GeoOnTertiaryContainer,
    background = GeoBackground,
    onBackground = GeoOnBackground,
    surface = GeoSurface,
    onSurface = GeoOnSurface,
    surfaceVariant = GeoSurfaceVariant,
    onSurfaceVariant = GeoOnSurfaceVariant,
    outline = GeoOutline,
    outlineVariant = GeoOutlineVariant,
    error = GeoError,
    onError = GeoOnError,
    errorContainer = GeoErrorContainer,
    onErrorContainer = GeoOnErrorContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = GeoDarkPrimary,
    onPrimary = GeoDarkOnPrimary,
    primaryContainer = GeoDarkPrimaryContainer,
    onPrimaryContainer = GeoDarkOnPrimaryContainer,
    secondary = GeoSecondaryContainer,
    onSecondary = GeoOnSecondaryContainer,
    secondaryContainer = GeoSecondary,
    onSecondaryContainer = GeoOnSecondary,
    tertiary = GeoTertiaryContainer,
    onTertiary = GeoOnTertiaryContainer,
    tertiaryContainer = GeoTertiary,
    onTertiaryContainer = GeoOnTertiary,
    background = GeoDarkBackground,
    onBackground = GeoDarkOnBackground,
    surface = GeoDarkSurface,
    onSurface = GeoDarkOnSurface,
    surfaceVariant = GeoDarkSurfaceVariant,
    onSurfaceVariant = GeoOutlineVariant,
    outline = GeoOutline,
    outlineVariant = GeoDarkOutlineVariant,
    error = GeoError,
    onError = GeoOnError,
    errorContainer = GeoErrorContainer,
    onErrorContainer = GeoOnErrorContainer
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
