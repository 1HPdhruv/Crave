package com.srmfood.gag.core.ui.theme

import androidx.compose.ui.graphics.Color

// ─── CRAVE ORBIT Design System ─────────────────────────────────────────────────
// Visual direction: ivory/charcoal neutral surfaces · lime/chartreuse accent · editorial typography

// ─── Crave website palette (paper · ink · orange · chartreuse · lavender) ───────
val CravePaper            = Color(0xFFF5F1EA)
val CravePaperDeep        = Color(0xFFE9E1D5)
val CraveInk              = Color(0xFF1C1B1A)
val CraveMuted            = Color(0xFF77716B)
val CraveLine             = Color(0xFFD8D0C6)
val CraveOrange           = Color(0xFFFF5C35)
val CraveOrangeDeep       = Color(0xFFD94120)
val CraveChartreuse       = Color(0xFFD8FF44)
val CraveLavender         = Color(0xFFC5B5FF)
val CraveAqua             = Color(0xFF8FE8DD)
val CraveSun              = Color(0xFFFFD44F)
val CraveWhite            = Color(0xFFFFFDF9)
val CraveRedSoft          = Color(0xFFFFE3E0)

// ─── Primary accent (kept under the legacy Orbit* names so existing screens re-theme) ──
val OrbitLime             = CraveOrange          // Primary accent — CTAs, active states
val OrbitLimeDark         = CraveOrangeDeep      // Accent for text on light surfaces
val OrbitLimeContainer    = Color(0xFFFFE0D6)    // Accent container on dark
val OrbitOnLime           = CraveInk             // Text on accent buttons

// ─── Dark theme surfaces (warm ink) ───────────────────────────────
val OrbitDarkBackground   = Color(0xFF141312)
val OrbitDarkSurface      = Color(0xFF1E1C1A)
val OrbitDarkSurfaceVar   = Color(0xFF2A2724)
val OrbitDarkSurfaceHigh  = Color(0xFF353230)
val OrbitDarkOutline      = Color(0xFF4A4540)
val OrbitDarkOutlineVar   = Color(0xFF33302C)

val OrbitDarkOnBg         = CravePaper
val OrbitDarkOnSurface    = Color(0xFFEFE9E0)
val OrbitDarkOnSurfaceVar = Color(0xFFA39C93)
val OrbitDarkOnSurfaceDim = Color(0xFF6B655E)

// ─── Light theme surfaces (paper) ─────────────────────────────────
val OrbitLightBackground  = CravePaper
val OrbitLightSurface     = CraveWhite
val OrbitLightSurfaceVar  = CravePaperDeep
val OrbitLightSurfaceHigh = Color(0xFFDDD4C6)
val OrbitLightOutline     = CraveLine
val OrbitLightOutlineVar  = Color(0xFFE6DFD4)

val OrbitLightOnBg        = CraveInk
val OrbitLightOnSurface   = CraveInk
val OrbitLightOnSurfaceVar= CraveMuted
val OrbitLightOnSurfaceDim= Color(0xFFA69E95)

// ─── Semantic Colors ──────────────────────────────────────────────
val OrbitSuccess          = Color(0xFF4E8A1E)    // Olive green — readable on paper
val OrbitSuccessContainer = Color(0xFFE8F7B8)
val OrbitWarning          = Color(0xFFD98A00)
val OrbitError            = Color(0xFFC92A2A)
val OrbitErrorContainer   = CraveRedSoft
val OrbitBlue             = Color(0xFF6C5CE7)    // Lavender-deep info

// ─── Semantic Dietary ─────────────────────────────────────────────
val VegGreen              = Color(0xFF3F9B2B)
val NonVegRed             = Color(0xFFC92A2A)

// ─── Rating ───────────────────────────────────────────────────────
val GagStarYellow         = Color(0xFFF5A400)

// ─── Order/Slot Status ────────────────────────────────────────────
val StatusCreated         = Color(0xFF8A8580)
val StatusPlaced          = OrbitBlue
val StatusAccepted        = Color(0xFF8E7BE8)
val StatusPreparing       = Color(0xFFE09A00)
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
val GagPinkContainerLight = CraveChartreuse
val GagOnPinkContainer    = OrbitOnLime
val GagOnPinkContainerLight = CraveInk

val GagSecondaryDark      = Color(0xFF383838)
val GagSecondaryLight     = Color(0xFF6B6762)
val GagSecondaryContainerDark  = OrbitDarkSurfaceVar
val GagSecondaryContainerLight = OrbitLightSurfaceVar

val GagAccent             = OrbitWarning
val GagAccentContainer    = Color(0xFFFFE9C2)
val GagOnAccentContainer  = CraveInk

val GagBlue               = OrbitBlue
val GagBlueContainer      = Color(0xFFDCD4FF)
val GagOnBlueContainer    = CraveInk

val GagYellow             = Color(0xFFFBBF24)
val GagYellowContainer    = Color(0xFFFFF1BF)
val GagOnYellowContainer  = CraveInk
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
