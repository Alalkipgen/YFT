package com.alal.yft.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.alal.yft.R

/**
 * Plus Jakarta Sans (SIL OFL 1.1), bundled as Latin subsets in `res/font`. Four static weights
 * keep the APK small; anything outside the subset falls back to the system font.
 */
val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = PlusJakartaSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
)

/**
 * The brief's four roles: Display 32 Bold (`headlineLarge`; screen titles use `headlineMedium`
 * 28 Bold, as measured in the images), Title 22 SemiBold (`titleLarge`, sections and sheets),
 * Body 16 Regular (`bodyLarge`) and Label 14 Medium (`labelLarge`). The remaining Material
 * roles are scaled from those.
 */
val YftTypography = Typography(
    displayLarge = style(48, 56, FontWeight.Bold, -0.02),
    displayMedium = style(40, 48, FontWeight.Bold, -0.02),
    displaySmall = style(36, 44, FontWeight.Bold, -0.02),
    headlineLarge = style(32, 40, FontWeight.Bold, -0.02),
    headlineMedium = style(28, 36, FontWeight.Bold, -0.01),
    headlineSmall = style(24, 32, FontWeight.SemiBold),
    titleLarge = style(22, 28, FontWeight.SemiBold),
    titleMedium = style(17, 24, FontWeight.SemiBold),
    titleSmall = style(15, 20, FontWeight.SemiBold),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 20, FontWeight.Normal),
    bodySmall = style(12, 16, FontWeight.Normal),
    labelLarge = style(14, 20, FontWeight.Medium),
    labelMedium = style(12, 16, FontWeight.Medium),
    labelSmall = style(11, 16, FontWeight.Medium, 0.01),
)
