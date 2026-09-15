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
private val GagDarkColorScheme = darkColorScheme(
    primary             = GagPink,
    onPrimary           = GagDarkOnBackground,
    primaryContainer    = Color(0xFF3D0A08),
    onPrimaryContainer  = GagPinkContainer,
    secondary           = GagInfo,
    onSecondary         = GagDarkOnBackground,
    secondaryContainer  = Color(0xFF1A2E5E),
    onSecondaryContainer = GagInfo,
    tertiary            = GagWarning,
    onTertiary          = GagDarkOnBackground,
    // Surfaces — deliberate tonal separation without shadows
    background          = GagDarkBackground,      // #0F0F0F
    onBackground        = GagDarkOnBackground,    // #FFFFFF
    surface             = GagDarkSurface,         // #171717
    onSurface           = GagDarkOnSurface,       // #FFFFFF
    surfaceVariant      = GagDarkSurfaceVariant,  // #202020
    onSurfaceVariant    = GagDarkOnSurfaceVariant,// #B8B8B8
    outline             = GagDarkOutline,          // #303030
    outlineVariant      = GagDarkOutlineVariant,    // #272727
    // Error
    error               = GagError,
    onError             = GagDarkOnBackground,
    errorContainer      = GagErrorContainer,
    onErrorContainer    = GagError
)

// ─── CRAVE Light Color Scheme ────────────────────────────────────
private val GagLightColorScheme = lightColorScheme(
    primary             = GagPink,
    onPrimary           = GagLightBackground,
    primaryContainer    = GagPinkContainer,
    onPrimaryContainer  = GagOnPinkContainer,
    secondary           = GagInfo,
    onSecondary         = GagLightBackground,
    secondaryContainer  = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF1E3A8A),
    tertiary            = GagWarning,
    onTertiary          = GagLightBackground,
    // Surfaces — clean white with subtle gray steps
    background          = GagLightBackground,         // #FFFFFF
    onBackground        = GagLightOnBackground,       // #191919
    surface             = GagLightSurface,            // #FFFFFF
    onSurface           = GagLightOnSurface,          // #191919
    surfaceVariant      = GagLightSurfaceVariant,     // #F5F5F5
    onSurfaceVariant    = GagLightOnSurfaceVariant,   // #666666
    outline             = GagLightOutline,             // #E5E5E5
    outlineVariant      = GagLightOutlineVariant,       // #F0F0F0
    // Error
    error               = GagError,
    onError             = GagLightBackground,
    errorContainer      = GagErrorContainer,
    onErrorContainer    = GagError
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
