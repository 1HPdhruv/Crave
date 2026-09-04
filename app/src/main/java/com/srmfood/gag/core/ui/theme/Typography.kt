package com.srmfood.gag.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.srmfood.gag.R

// Using system font as Google Fonts aren't bundled by default.
// To add Outfit/Inter, download the font files to res/font/ and update here.
val GagFontFamily = FontFamily.Default

val GagTypography = Typography(
    // Display
    displayLarge = TextStyle(
        fontFamily = GagFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
        color = GagOnBackground
    ),
    displayMedium = TextStyle(
        fontFamily = GagFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        color = GagOnBackground
    ),
    displaySmall = TextStyle(
        fontFamily = GagFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        color = GagOnBackground
    ),

    // Headline
    headlineLarge = TextStyle(
        fontFamily = GagFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        color = GagOnBackground
    ),
    headlineMedium = TextStyle(
        fontFamily = GagFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        color = GagOnBackground
    ),
    headlineSmall = TextStyle(
        fontFamily = GagFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        color = GagOnBackground
    ),

    // Title
    titleLarge = TextStyle(
        fontFamily = GagFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        color = GagOnBackground
    ),
    titleMedium = TextStyle(
        fontFamily = GagFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
        color = GagOnBackground
    ),
    titleSmall = TextStyle(
        fontFamily = GagFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        color = GagOnBackground
    ),

    // Body
    bodyLarge = TextStyle(
        fontFamily = GagFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
        color = GagOnSurface
    ),
    bodyMedium = TextStyle(
        fontFamily = GagFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
        color = GagOnSurface
    ),
    bodySmall = TextStyle(
        fontFamily = GagFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
        color = GagOnSurfaceVariant
    ),

    // Label
    labelLarge = TextStyle(
        fontFamily = GagFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        color = GagOnSurface
    ),
    labelMedium = TextStyle(
        fontFamily = GagFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        color = GagOnSurfaceVariant
    ),
    labelSmall = TextStyle(
        fontFamily = GagFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        color = GagOnSurfaceVariant
    )
)
