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

// ─── CRAVE ORBIT Dark Color Scheme ────────────────────────────────
// Primary: #CDEA45 (lime) on near-black background #0E0E0E
private val OrbitDarkColorScheme = darkColorScheme(
    primary             = OrbitLime,              // #CDEA45 lime accent
    onPrimary           = OrbitOnLime,            // #1A1F00 near-black on lime
    primaryContainer    = OrbitLimeContainer,     // #2A3300
    onPrimaryContainer  = OrbitLime,
    secondary           = Color(0xFF9E9A94),      // Muted warm gray
    onSecondary         = OrbitDarkBackground,
    secondaryContainer  = OrbitDarkSurfaceVar,
    onSecondaryContainer= OrbitDarkOnSurface,
    tertiary            = OrbitWarning,           // #FF8C00 orange — limited/warning use
    onTertiary          = Color(0xFF1A0A00),
    tertiaryContainer   = Color(0xFF3A2000),
    onTertiaryContainer = Color(0xFFFFDBC8),
    // Surfaces — tonal charcoal steps
    background          = OrbitDarkBackground,    // #0E0E0E
    onBackground        = OrbitDarkOnBg,          // #F5F0E8
    surface             = OrbitDarkSurface,       // #181818
    onSurface           = OrbitDarkOnSurface,     // #EDE8E0
    surfaceVariant      = OrbitDarkSurfaceVar,    // #242424
    onSurfaceVariant    = OrbitDarkOnSurfaceVar,  // #8A8580
    outline             = OrbitDarkOutline,       // #383838
    outlineVariant      = OrbitDarkOutlineVar,    // #2A2A2A
    // Error
    error               = OrbitError,
    onError             = Color(0xFFFFFFF0),
    errorContainer      = OrbitErrorContainer,
    onErrorContainer    = Color(0xFFFFB3B3),
    // Scrim / inverse
    scrim               = Color(0x99000000),
    inverseSurface      = OrbitDarkOnBg,
    inverseOnSurface    = OrbitDarkBackground,
    inversePrimary      = OrbitLimeDark
)

// ─── CRAVE ORBIT Light Color Scheme ───────────────────────────────
// Primary: #9DB520 (dark lime) on warm ivory background #F5F2EC
private val OrbitLightColorScheme = lightColorScheme(
    primary             = OrbitLimeDark,          // #9DB520 darker lime for light bg
    onPrimary           = Color(0xFFFFFFFF),
    primaryContainer    = GagPinkContainerLight,  // #EEF7B0
    onPrimaryContainer  = OrbitOnLime,
    secondary           = Color(0xFF6B6762),
    onSecondary         = Color(0xFFFFFFFF),
    secondaryContainer  = OrbitLightSurfaceVar,
    onSecondaryContainer= OrbitLightOnSurface,
    tertiary            = OrbitWarning,
    onTertiary          = Color(0xFFFFFFFF),
    tertiaryContainer   = Color(0xFFFFDBC8),
    onTertiaryContainer = Color(0xFF3A1200),
    // Surfaces — warm ivory steps
    background          = OrbitLightBackground,   // #F5F2EC
    onBackground        = OrbitLightOnBg,         // #111111
    surface             = OrbitLightSurface,      // #FFFFFB
    onSurface           = OrbitLightOnSurface,    // #1A1A1A
    surfaceVariant      = OrbitLightSurfaceVar,   // #EDEAD4
    onSurfaceVariant    = OrbitLightOnSurfaceVar, // #6B6762
    outline             = OrbitLightOutline,      // #CECBC4
    outlineVariant      = OrbitLightOutlineVar,   // #E0DDD6
    // Error
    error               = OrbitError,
    onError             = Color(0xFFFFFFFF),
    errorContainer      = Color(0xFFFFDAD6),
    onErrorContainer    = Color(0xFF410002),
    // Scrim / inverse
    scrim               = Color(0x66000000),
    inverseSurface      = OrbitLightOnBg,
    inverseOnSurface    = OrbitLightBackground,
    inversePrimary      = OrbitLime
)

@Composable
fun GagTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) OrbitDarkColorScheme else OrbitLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Edge-to-edge: transparent bars, let Compose handle insets
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = if (darkTheme) {
                OrbitDarkSurface.toArgb()
            } else {
                OrbitLightSurface.toArgb()
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
