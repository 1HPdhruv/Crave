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

// ─── CRAVE / ORBIT Dark Color Scheme ──────────────────────────────
// Deep charcoal surfaces keep the citron accent legible without relying on neon glow.
private val GagDarkColorScheme = darkColorScheme(
    primary             = GagPink,
    onPrimary           = Color(0xFF182120),
    primaryContainer    = Color(0xFF2B351D),
    onPrimaryContainer  = Color(0xFFDDF29A),
    secondary           = GagInfo,
    onSecondary         = GagDarkOnBackground,
    secondaryContainer  = Color(0xFF183B35),
    onSecondaryContainer = Color(0xFFA5D8CE),
    tertiary            = GagWarning,
    onTertiary          = GagDarkOnBackground,
    // Surfaces — calm charcoal stack with visible but subtle component separation.
    background          = GagDarkBackground,
    onBackground        = GagDarkOnBackground,
    surface             = GagDarkSurface,
    onSurface           = GagDarkOnSurface,
    surfaceVariant      = GagDarkSurfaceVariant,
    onSurfaceVariant    = GagDarkOnSurfaceVariant,
    outline             = GagDarkOutline,
    outlineVariant      = GagDarkOutlineVariant,
    // Error
    error               = GagError,
    onError             = GagDarkOnBackground,
    errorContainer      = GagErrorContainer,
    onErrorContainer    = GagError
)

// ─── CRAVE / ORBIT Light Color Scheme ─────────────────────────────
private val GagLightColorScheme = lightColorScheme(
    primary             = Color(0xFF4B6426),
    onPrimary           = Color.White,
    primaryContainer    = GagPinkContainer,
    onPrimaryContainer  = GagOnPinkContainer,
    secondary           = GagInfo,
    onSecondary         = GagLightBackground,
    secondaryContainer  = Color(0xFFD6ECE6),
    onSecondaryContainer = Color(0xFF123E38),
    tertiary            = GagWarning,
    onTertiary          = GagLightBackground,
    // Surfaces — ivory instead of stark white, with warm quiet separators.
    background          = GagLightBackground,
    onBackground        = GagLightOnBackground,
    surface             = GagLightSurface,
    onSurface           = GagLightOnSurface,
    surfaceVariant      = GagLightSurfaceVariant,
    onSurfaceVariant    = GagLightOnSurfaceVariant,
    outline             = GagLightOutline,
    outlineVariant      = GagLightOutlineVariant,
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
