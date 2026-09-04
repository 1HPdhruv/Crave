package com.srmfood.gag.core.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val GagDarkColorScheme = darkColorScheme(
    primary = GagOrange,
    onPrimary = GagOnBackground,
    primaryContainer = GagOrangeContainer,
    onPrimaryContainer = GagOnOrangeContainer,
    secondary = GagAmber,
    onSecondary = GagOnBackground,
    secondaryContainer = GagWarningContainer,
    onSecondaryContainer = GagAmberLight,
    tertiary = GagInfo,
    onTertiary = GagOnBackground,
    background = GagBackground,
    onBackground = GagOnBackground,
    surface = GagSurface,
    onSurface = GagOnSurface,
    surfaceVariant = GagSurfaceVariant,
    onSurfaceVariant = GagOnSurfaceVariant,
    outline = GagOutline,
    outlineVariant = GagOutlineVariant,
    error = GagError,
    onError = GagOnBackground,
    errorContainer = GagErrorContainer,
    onErrorContainer = GagError
)

private val GagLightColorScheme = lightColorScheme(
    primary = GagOrange,
    onPrimary = GagLightSurface,
    primaryContainer = GagOrangeContainer,
    onPrimaryContainer = GagOnOrangeContainer,
    secondary = GagAmber,
    onSecondary = GagLightSurface,
    secondaryContainer = GagWarningContainer,
    onSecondaryContainer = GagAmberLight,
    tertiary = GagInfo,
    onTertiary = GagLightSurface,
    background = GagLightBackground,
    onBackground = GagLightOnBackground,
    surface = GagLightSurface,
    onSurface = GagLightOnSurface,
    surfaceVariant = GagLightSurfaceVariant,
    onSurfaceVariant = GagLightOnSurfaceVariant,
    outline = GagLightOutline,
    outlineVariant = GagLightOutlineVariant,
    error = GagError,
    onError = GagLightSurface,
    errorContainer = GagErrorContainer,
    onErrorContainer = GagError
)

@Composable
fun GagTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) GagDarkColorScheme else GagLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = if (darkTheme) BottomNavBackground.toArgb() else LightBottomNavBackground.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = GagTypography,
        shapes = GagShapes,
        content = content
    )
}
