package com.srmfood.gag.core.ui.theme

import androidx.compose.ui.graphics.Color

// CRAVE brand palette: restrained red, warm neutrals, and quiet semantic accents.
val GagPink = Color(0xFFE21B12)
val GagPinkContainer = Color(0xFFFFE4E1)
val GagOnPinkContainer = Color(0xFF5F0A06)

val GagBlue = Color(0xFF315A7D)
val GagBlueContainer = Color(0xFFDCEAF5)
val GagOnBlueContainer = Color(0xFF17344B)

val GagYellow = Color(0xFFD28A00)
val GagYellowContainer = Color(0xFFFFF1CC)
val GagOnYellowContainer = Color(0xFF4D3500)

val GagGreen = Color(0xFF168A45)
val GagGreenContainer = Color(0xFFDDF4E7)
val GagOrangeBrand = Color(0xFFB85C18)
val GagOrangeBrandContainer = Color(0xFFFFE7D2)

val GagDarkBackground = Color(0xFF191919)
val GagDarkSurface = Color(0xFF242424)
val GagDarkSurfaceVariant = Color(0xFF303030)
val GagDarkSurfaceHighest = Color(0xFF3A3A3A)
val GagDarkOutline = Color(0xFF555555)
val GagDarkOutlineVariant = Color(0xFF3A3A3A)

val GagLightBackground = Color(0xFFF5F5F5)
val GagLightSurface = Color(0xFFFFFFFF)
val GagLightSurfaceVariant = Color(0xFFF0F0F0)
val GagLightSurfaceHighest = Color(0xFFE7E7E7)
val GagLightOutline = Color(0xFFD2D2D2)
val GagLightOutlineVariant = Color(0xFFE5E5E5)

val GagDarkOnBackground = Color(0xFFF7F7F7)
val GagDarkOnSurface = Color(0xFFF3F3F3)
val GagDarkOnSurfaceVariant = Color(0xFFB7B7B7)
val GagDarkOnSurfaceDim = Color(0xFF888888)

val GagLightOnBackground = Color(0xFF191919)
val GagLightOnSurface = Color(0xFF222222)
val GagLightOnSurfaceVariant = Color(0xFF666666)
val GagLightOnSurfaceDim = Color(0xFF8A8A8A)

val GagSuccess = GagGreen
val GagSuccessContainer = GagGreenContainer
val GagError = Color(0xFFB3261E)
val GagErrorContainer = Color(0xFFFFDAD6)
val GagWarning = GagOrangeBrand
val GagWarningContainer = GagOrangeBrandContainer
val GagInfo = GagBlue

val StatusCreated = Color(0xFF8A8A8A)
val StatusPlaced = GagBlue
val StatusAccepted = Color(0xFF7257A8)
val StatusPreparing = GagYellow
val StatusReady = GagGreen
val StatusPickedUp = Color(0xFF666666)
val StatusCancelled = GagError
val StatusRejected = GagError
val StatusExpired = Color(0xFF666666)
val StatusRefunded = Color(0xFF2A8A9A)

val SlotAvailable = GagGreen
val SlotLimited = GagOrangeBrand
val SlotFull = GagError

val CategoryMeals = GagPink
val CategoryFastFood = GagYellow
val CategoryBeverages = GagBlue
val CategoryPizza = GagPink
val CategorySnacks = GagGreen
val CategoryChinese = Color(0xFF7257A8)
val CategoryDesserts = GagOrangeBrand

val VegGreen = GagGreen
val NonVegRed = GagError

val GradientStart = GagPink
val GradientEnd = Color(0xFFB3120A)
val GradientDarkStart = GagDarkBackground
val GradientDarkEnd = Color(0xFF2A0C0A)

val CardOverlayDark = Color(0xCC000000)
val CardOverlayLight = Color(0x1A000000)
val BottomNavBackground = GagDarkSurface
val BottomNavBorder = GagDarkOutlineVariant
val LightBottomNavBackground = GagLightSurface
val LightBottomNavBorder = GagLightOutlineVariant

// Compatibility aliases retained for existing screens and data-layer UI contracts.
val GagOrange = GagPink
val GagOrangeLight = Color(0xFFF05B52)
val GagOrangeDark = Color(0xFFB3120A)
val GagOrangeContainer = GagPinkContainer
val GagOnOrangeContainer = GagOnPinkContainer
val GagAmber = GagYellow
val GagAmberLight = Color(0xFFE2A72A)
val GagAmberDark = Color(0xFF996300)
val GagBackground = GagLightBackground
val GagSurface = GagLightSurface
val GagSurfaceVariant = GagLightSurfaceVariant
val GagSurfaceHighest = GagLightSurfaceHighest
val GagOutline = GagLightOutline
val GagOutlineVariant = GagLightOutlineVariant
val GagOnBackground = GagLightOnBackground
val GagOnSurface = GagLightOnSurface
val GagOnSurfaceVariant = GagLightOnSurfaceVariant
val GagOnSurfaceDim = GagLightOnSurfaceDim
