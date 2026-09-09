package com.srmfood.gag.core.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ─── CRAVE Dark Color Scheme ─────────────────────────────────────
// Deliberately designed: 0F0F0F → 171717 → 202020 → 242424 surface stack
private val CraveDarkColorScheme = darkColorScheme(
    primary             = CraveRedDark,
    onPrimary           = CraveDarkOnBackground,
    primaryContainer    = Color(0xFF3D0A08),
    onPrimaryContainer  = CraveRedLight,
    secondary           = CraveInfoDark,
    onSecondary         = CraveDarkOnBackground,
    secondaryContainer  = Color(0xFF1A2E5E),
    onSecondaryContainer = CraveInfoDark,
    tertiary            = CraveWarningDark,
    onTertiary          = CraveDarkOnBackground,
    // Surfaces — deliberate tonal separation without shadows
    background          = CraveDarkBackground,      // #0F0F0F
    onBackground        = CraveDarkOnBackground,    // #FFFFFF
    surface             = CraveDarkSurface,         // #171717
    onSurface           = CraveDarkOnSurface,       // #FFFFFF
    surfaceVariant      = CraveDarkSurfaceVariant,  // #202020
    onSurfaceVariant    = CraveDarkOnSurfaceVariant,// #B8B8B8
    outline             = CraveDarkBorder,          // #303030
    outlineVariant      = CraveDarkBorderSubtle,    // #272727
    // Error
    error               = CraveErrorDark,
    onError             = CraveDarkOnBackground,
    errorContainer      = CraveErrorContainerDark,
    onErrorContainer    = CraveErrorDark
)

// ─── CRAVE Light Color Scheme ────────────────────────────────────
private val CraveLightColorScheme = lightColorScheme(
    primary             = CraveRed,
    onPrimary           = CraveLightBackground,
    primaryContainer    = CraveRedContainer,
    onPrimaryContainer  = CraveOnRedContainer,
    secondary           = CraveInfo,
    onSecondary         = CraveLightBackground,
    secondaryContainer  = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF1E3A8A),
    tertiary            = CraveWarning,
    onTertiary          = CraveLightBackground,
    // Surfaces — clean white with subtle gray steps
    background          = CraveLightBackground,         // #FFFFFF
    onBackground        = CraveLightOnBackground,       // #191919
    surface             = CraveLightSurface,            // #FFFFFF
    onSurface           = CraveLightOnSurface,          // #191919
    surfaceVariant      = CraveLightSurfaceVariant,     // #F5F5F5
    onSurfaceVariant    = CraveLightOnSurfaceVariant,   // #666666
    outline             = CraveLightBorder,             // #E5E5E5
    outlineVariant      = CraveLightBorderSubtle,       // #F0F0F0
    // Error
    error               = CraveError,
    onError             = CraveLightBackground,
    errorContainer      = CraveErrorContainer,
    onErrorContainer    = CraveError
)

@Composable
fun GagTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) CraveDarkColorScheme else CraveLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Edge-to-edge: transparent bars, let Compose handle insets
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = if (darkTheme) {
                BottomNavBackground.toArgb()
            } else {
                LightBottomNavBackground.toArgb()
            }
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
