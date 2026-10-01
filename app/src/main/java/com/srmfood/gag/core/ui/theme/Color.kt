package com.srmfood.gag.core.ui.theme

import androidx.compose.ui.graphics.Color

// ─── CRAVE ORBIT Design System ─────────────────────────────────────────────────
// Visual direction: ivory/charcoal neutral surfaces · lime/chartreuse accent · editorial typography

// ─── Orbit Accent: Lime / Chartreuse ──────────────────────────────
val OrbitLime             = Color(0xFFCDEA45)    // Primary lime accent — active states, CTAs
val OrbitLimeDark         = Color(0xFF9DB520)    // Darker lime for light mode primary
val OrbitLimeContainer    = Color(0xFF2A3300)    // Lime container on dark
val OrbitOnLime           = Color(0xFF1A1F00)    // Text on lime buttons

// ─── Orbit Dark Theme Surfaces ────────────────────────────────────
// Deep charcoal near-black base
val OrbitDarkBackground   = Color(0xFF0E0E0E)    // Near-black screen bg
val OrbitDarkSurface      = Color(0xFF181818)    // Cards, nav bars
val OrbitDarkSurfaceVar   = Color(0xFF242424)    // Input fields, chips
val OrbitDarkSurfaceHigh  = Color(0xFF2E2E2E)    // Elevated sheets
val OrbitDarkOutline      = Color(0xFF383838)    // Borders
val OrbitDarkOutlineVar   = Color(0xFF2A2A2A)    // Subtle dividers

// ─── Orbit Dark Text ──────────────────────────────────────────────
val OrbitDarkOnBg         = Color(0xFFF5F0E8)    // Warm white — primary text
val OrbitDarkOnSurface    = Color(0xFFEDE8E0)    // On cards
val OrbitDarkOnSurfaceVar = Color(0xFF8A8580)    // Secondary text
val OrbitDarkOnSurfaceDim = Color(0xFF555050)    // Disabled / dimmed

// ─── Orbit Light Theme Surfaces ───────────────────────────────────
// Warm ivory / off-white base
val OrbitLightBackground  = Color(0xFFF5F2EC)    // Warm ivory screen bg
val OrbitLightSurface     = Color(0xFFFFFFFB)    // Cards, nav bars
val OrbitLightSurfaceVar  = Color(0xFFEDEAE4)    // Input fields, chips
val OrbitLightSurfaceHigh = Color(0xFFE4E0D9)    // Elevated elements
val OrbitLightOutline     = Color(0xFFCECBC4)    // Borders
val OrbitLightOutlineVar  = Color(0xFFE0DDD6)    // Subtle dividers

// ─── Orbit Light Text ─────────────────────────────────────────────
val OrbitLightOnBg        = Color(0xFF111111)    // Near-black primary text
val OrbitLightOnSurface   = Color(0xFF1A1A1A)    // On cards
val OrbitLightOnSurfaceVar= Color(0xFF6B6762)    // Secondary text
val OrbitLightOnSurfaceDim= Color(0xFF9E9A94)    // Dimmed / disabled

// ─── Semantic Colors ──────────────────────────────────────────────
val OrbitSuccess          = Color(0xFF22C55E)    // Green — success/ready
val OrbitSuccessContainer = Color(0xFF0A3D22)
val OrbitWarning          = Color(0xFFFF8C00)    // Orange — limited/warning
val OrbitError            = Color(0xFFEF4444)    // Red — error
val OrbitErrorContainer   = Color(0xFF4D0000)
val OrbitBlue             = Color(0xFF60A5FA)    // Info blue

// ─── Semantic Dietary ─────────────────────────────────────────────
val VegGreen              = Color(0xFF22C55E)
val NonVegRed             = Color(0xFFEF4444)

// ─── Rating ───────────────────────────────────────────────────────
val GagStarYellow         = Color(0xFFFBBF24)    // Star rating amber

// ─── Order/Slot Status ────────────────────────────────────────────
val StatusCreated         = Color(0xFF8A8580)
val StatusPlaced          = OrbitBlue
val StatusAccepted        = Color(0xFFA78BFA)
val StatusPreparing       = Color(0xFFFBBF24)
val StatusReady           = OrbitSuccess
val StatusPickedUp        = OrbitSuccess
val StatusCancelled       = OrbitError
val StatusRejected        = OrbitError
val StatusExpired         = Color(0xFF8A8580)
val StatusRefunded        = OrbitBlue

val SlotAvailable         = OrbitSuccess
val SlotLimited           = OrbitWarning
val SlotFull              = OrbitError

// ─── Compatibility aliases — keep all existing references compiling ──
// These map old GagPink/GagOrange names to Orbit lime so screens
// using MaterialTheme.colorScheme tokens get the new look automatically.
val GagPink               = OrbitLime
val GagPinkLight          = OrbitLimeDark
val GagPinkContainer      = OrbitLimeContainer
val GagPinkContainerLight = Color(0xFFEEF7B0)
val GagOnPinkContainer    = OrbitOnLime
val GagOnPinkContainerLight = Color(0xFF1A1F00)

val GagSecondaryDark      = Color(0xFF383838)
val GagSecondaryLight     = Color(0xFF6B6762)
val GagSecondaryContainerDark  = OrbitDarkSurfaceVar
val GagSecondaryContainerLight = OrbitLightSurfaceVar

val GagAccent             = OrbitWarning
val GagAccentContainer    = Color(0xFF3A2000)
val GagOnAccentContainer  = Color(0xFFFFDBC8)

val GagBlue               = OrbitBlue
val GagBlueContainer      = Color(0xFF1B3A62)
val GagOnBlueContainer    = Color(0xFFD6E8FF)

val GagYellow             = Color(0xFFFBBF24)
val GagYellowContainer    = Color(0xFF4D3500)
val GagOnYellowContainer  = Color(0xFFFFDC73)
val GagGreen              = OrbitSuccess
val GagGreenContainer     = OrbitSuccessContainer
val GagOrangeBrand        = GagAccent
val GagOrangeBrandContainer = GagAccentContainer

// Dark surfaces
val GagDarkBackground     = OrbitDarkBackground
val GagDarkSurface        = OrbitDarkSurface
val GagDarkSurfaceVariant = OrbitDarkSurfaceVar
val GagDarkSurfaceHighest = OrbitDarkSurfaceHigh
val GagDarkOutline        = OrbitDarkOutline
val GagDarkOutlineVariant = OrbitDarkOutlineVar
val GagDarkOnBackground   = OrbitDarkOnBg
val GagDarkOnSurface      = OrbitDarkOnSurface
val GagDarkOnSurfaceVariant = OrbitDarkOnSurfaceVar
val GagDarkOnSurfaceDim   = OrbitDarkOnSurfaceDim

// Light surfaces
val GagLightBackground    = OrbitLightBackground
val GagLightSurface       = OrbitLightSurface
val GagLightSurfaceVariant= OrbitLightSurfaceVar
val GagLightSurfaceHighest= OrbitLightSurfaceHigh
val GagLightOutline       = OrbitLightOutline
val GagLightOutlineVariant= OrbitLightOutlineVar
val GagLightOnBackground  = OrbitLightOnBg
val GagLightOnSurface     = OrbitLightOnSurface
val GagLightOnSurfaceVariant = OrbitLightOnSurfaceVar
val GagLightOnSurfaceDim  = OrbitLightOnSurfaceDim

// Semantic
val GagSuccess            = OrbitSuccess
val GagSuccessContainer   = OrbitSuccessContainer
val GagError              = OrbitError
val GagErrorContainer     = OrbitErrorContainer
val GagErrorLight         = OrbitError
val GagErrorContainerLight= Color(0xFFFFDAD6)
val GagWarning            = OrbitWarning
val GagWarningContainer   = GagAccentContainer
val GagInfo               = OrbitBlue

// Gradients
val GradientStart         = OrbitLime
val GradientEnd           = OrbitLimeDark
val GradientDarkStart     = OrbitDarkBackground
val GradientDarkEnd       = Color(0xFF1A1A1A)

// Overlays / navigation
val CardOverlayDark       = Color(0xCC000000)
val CardOverlayLight      = Color(0x1A000000)
val BottomNavBackground   = OrbitDarkSurface
val BottomNavBorder       = OrbitDarkOutlineVar
val LightBottomNavBackground = OrbitLightSurface
val LightBottomNavBorder  = OrbitLightOutlineVar

// Orange aliases
val GagOrange             = OrbitLime
val GagOrangeLight        = Color(0xFFDEF06A)
val GagOrangeDark         = OrbitLimeDark
val GagOrangeContainer    = OrbitLimeContainer
val GagOnOrangeContainer  = OrbitOnLime
val GagAmber              = GagYellow
val GagAmberLight         = Color(0xFFE2A72A)
val GagAmberDark          = Color(0xFF996300)

// Surface aliases
val GagBackground         = OrbitLightBackground
val GagSurface            = OrbitLightSurface
val GagSurfaceVariant     = OrbitLightSurfaceVar
val GagSurfaceHighest     = OrbitLightSurfaceHigh
val GagOutline            = OrbitLightOutline
val GagOutlineVariant     = OrbitLightOutlineVar
val GagOnBackground       = OrbitLightOnBg
val GagOnSurface          = OrbitLightOnSurface
val GagOnSurfaceVariant   = OrbitLightOnSurfaceVar
val GagOnSurfaceDim       = OrbitLightOnSurfaceDim

// Category colors
val CategoryMeals         = OrbitLimeDark
val CategoryFastFood      = GagYellow
val CategoryBeverages     = OrbitBlue
val CategoryPizza         = OrbitLimeDark
val CategorySnacks        = OrbitSuccess
val CategoryChinese       = Color(0xFFA78BFA)
val CategoryDesserts      = OrbitWarning
