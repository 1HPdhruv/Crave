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
    primary = GagPink,
    onPrimary = GagDarkOnBackground,
    primaryContainer = GagPinkContainer,
    onPrimaryContainer = GagOnPinkContainer,
    secondary = GagBlue,
    onSecondary = GagDarkOnBackground,
    secondaryContainer = GagBlueContainer,
    onSecondaryContainer = GagOnBlueContainer,
    tertiary = GagYellow,
    onTertiary = GagDarkOnBackground,
    background = GagDarkBackground,
    onBackground = GagDarkOnBackground,
    surface = GagDarkSurface,
    onSurface = GagDarkOnSurface,
    surfaceVariant = GagDarkSurfaceVariant,
    onSurfaceVariant = GagDarkOnSurfaceVariant,
    outline = GagDarkOutline,
    outlineVariant = GagDarkOutlineVariant,
    error = GagError,
    onError = GagDarkOnBackground,
    errorContainer = GagErrorContainer,
    onErrorContainer = GagError
)

private val GagLightColorScheme = lightColorScheme(
    primary = GagPink,
    onPrimary = GagLightSurface,
    primaryContainer = GagPinkContainer,
    onPrimaryContainer = GagOnPinkContainer,
    secondary = GagBlue,
    onSecondary = GagLightSurface,
    secondaryContainer = GagBlueContainer,
    onSecondaryContainer = GagOnBlueContainer,
    tertiary = GagYellow,
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
