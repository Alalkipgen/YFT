package com.alal.yft.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic YFT colors that Material 3 has no role for. Read them with `YftTheme.colors`.
 *
 * Mint ([accent]) is a fill color: on light surfaces it never carries text, and Ink sits on it.
 * Coral ([coral]) also carries Ink. Text-on-background pairs meet WCAG AA (4.5:1), which
 * `YftThemeContrastTest` enforces for both themes.
 */
@Immutable
data class YftColors(
    val isDark: Boolean,
    val background: Color,
    val card: Color,
    val border: Color,
    /** Tonal chips that sit on a [card]. */
    val chip: Color,
    /** Filter chips and other controls that sit on the [background]. */
    val chipOnBackground: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val onAccent: Color,
    /** Tinted rows such as the selected quality, and the light navigation indicator. */
    val accentSoft: Color,
    val navIndicator: Color,
    val onNavIndicator: Color,
    /** Links, text buttons and leading row icons. */
    val link: Color,
    val coral: Color,
    val onCoral: Color,
    /** Error text; Coral itself is too light for text on light surfaces. */
    val coralText: Color,
    val coralSoft: Color,
    val waiting: Color,
    val onWaiting: Color,
    val success: Color,
    /** Strong 1.5dp outline of the idle Promptbox field. */
    val fieldOutline: Color,
    val scrim: Color,
    val onScrim: Color,
    val audioTileStart: Color,
    val audioTileEnd: Color,
    val videoTileStart: Color,
    val videoTileEnd: Color,
)

internal val YftLightPalette = YftColors(
    isDark = false,
    background = YftPalette.Surface,
    card = YftPalette.White,
    border = YftPalette.Border,
    chip = YftPalette.Surface,
    chipOnBackground = YftPalette.Border,
    textPrimary = YftPalette.Ink,
    textSecondary = YftPalette.Slate,
    accent = YftPalette.MintTeal,
    onAccent = YftPalette.Ink,
    accentSoft = YftPalette.MintSoft,
    navIndicator = YftPalette.MintSoft,
    onNavIndicator = YftPalette.Ink,
    link = YftPalette.DeepTeal,
    coral = YftPalette.Coral,
    onCoral = YftPalette.Ink,
    coralText = YftPalette.CoralDeep,
    coralSoft = YftPalette.CoralSoft,
    waiting = YftPalette.Slate,
    onWaiting = YftPalette.White,
    success = YftPalette.SuccessLight,
    fieldOutline = YftPalette.Ink,
    scrim = Color(0x99000000),
    onScrim = YftPalette.White,
    audioTileStart = YftPalette.MintTeal,
    audioTileEnd = YftPalette.DeepTeal,
    videoTileStart = Color(0xFF2E5357),
    videoTileEnd = YftPalette.Ink,
)

internal val YftDarkPalette = YftColors(
    isDark = true,
    background = YftPalette.Night,
    card = YftPalette.NightCard,
    border = YftPalette.NightBorder,
    chip = YftPalette.NightChip,
    chipOnBackground = YftPalette.NightChip,
    textPrimary = YftPalette.NightText,
    textSecondary = YftPalette.NightTextSecondary,
    accent = YftPalette.MintTeal,
    onAccent = YftPalette.Ink,
    accentSoft = YftPalette.NightMintSoft,
    navIndicator = YftPalette.MintTeal,
    onNavIndicator = YftPalette.Ink,
    link = YftPalette.MintTeal,
    coral = YftPalette.Coral,
    onCoral = YftPalette.Ink,
    coralText = YftPalette.Coral,
    coralSoft = YftPalette.NightCoralSoft,
    waiting = YftPalette.NightWaiting,
    onWaiting = YftPalette.NightText,
    success = YftPalette.NightSuccess,
    fieldOutline = YftPalette.MintTeal.copy(alpha = 0.6f),
    scrim = Color(0x99000000),
    onScrim = YftPalette.White,
    audioTileStart = Color(0xFF1FA88F),
    audioTileEnd = Color(0xFF0B4F48),
    videoTileStart = Color(0xFF24403F),
    videoTileEnd = Color(0xFF0E1A1C),
)

/**
 * Light scheme for stock Material components. `primary` is Deep Teal (not Mint) so any stock
 * text button, focused label or radio stays readable; YFT components paint Mint explicitly.
 */
internal val YftLightColors: ColorScheme = lightColorScheme(
    primary = YftPalette.DeepTeal,
    onPrimary = YftPalette.White,
    primaryContainer = YftPalette.MintTeal,
    onPrimaryContainer = YftPalette.Ink,
    secondary = YftPalette.Slate,
    onSecondary = YftPalette.White,
    secondaryContainer = YftPalette.MintSoft,
    onSecondaryContainer = YftPalette.Ink,
    tertiary = YftPalette.Coral,
    onTertiary = YftPalette.Ink,
    tertiaryContainer = YftPalette.CoralSoft,
    onTertiaryContainer = Color(0xFF5C1A0B),
    error = YftPalette.CoralDeep,
    onError = YftPalette.White,
    errorContainer = YftPalette.CoralSoft,
    onErrorContainer = Color(0xFF5C1A0B),
    background = YftPalette.Surface,
    onBackground = YftPalette.Ink,
    surface = YftPalette.Surface,
    onSurface = YftPalette.Ink,
    surfaceVariant = YftPalette.SurfaceMuted,
    onSurfaceVariant = YftPalette.Slate,
    surfaceTint = Color.Transparent,
    inverseSurface = YftPalette.Ink,
    inverseOnSurface = YftPalette.Surface,
    inversePrimary = YftPalette.MintTeal,
    outline = YftPalette.OutlineLight,
    outlineVariant = YftPalette.Border,
    scrim = Color(0xFF000000),
    surfaceBright = YftPalette.White,
    surfaceDim = YftPalette.SurfaceMuted,
    surfaceContainerLowest = YftPalette.White,
    surfaceContainerLow = YftPalette.White,
    surfaceContainer = YftPalette.White,
    surfaceContainerHigh = YftPalette.White,
    surfaceContainerHighest = YftPalette.SurfaceMuted,
)

internal val YftDarkColors: ColorScheme = darkColorScheme(
    primary = YftPalette.MintTeal,
    onPrimary = YftPalette.Ink,
    primaryContainer = YftPalette.MintTeal,
    onPrimaryContainer = YftPalette.Ink,
    secondary = YftPalette.NightTextSecondary,
    onSecondary = YftPalette.Night,
    secondaryContainer = YftPalette.NightMintSoft,
    onSecondaryContainer = YftPalette.NightText,
    tertiary = YftPalette.Coral,
    onTertiary = YftPalette.Ink,
    tertiaryContainer = YftPalette.NightCoralSoft,
    onTertiaryContainer = Color(0xFFFFDBD0),
    error = YftPalette.Coral,
    onError = YftPalette.Ink,
    errorContainer = YftPalette.NightCoralSoft,
    onErrorContainer = Color(0xFFFFDBD0),
    background = YftPalette.Night,
    onBackground = YftPalette.NightText,
    surface = YftPalette.Night,
    onSurface = YftPalette.NightText,
    surfaceVariant = YftPalette.NightChip,
    onSurfaceVariant = YftPalette.NightTextSecondary,
    surfaceTint = Color.Transparent,
    inverseSurface = YftPalette.NightText,
    inverseOnSurface = YftPalette.Ink,
    inversePrimary = YftPalette.DeepTeal,
    outline = YftPalette.NightOutline,
    outlineVariant = YftPalette.NightBorder,
    scrim = Color(0xFF000000),
    surfaceBright = YftPalette.NightBorder,
    surfaceDim = YftPalette.Night,
    surfaceContainerLowest = Color(0xFF070E10),
    surfaceContainerLow = YftPalette.NightCard,
    surfaceContainer = YftPalette.NightCard,
    surfaceContainerHigh = YftPalette.NightRaised,
    surfaceContainerHighest = YftPalette.NightChip,
)

internal val LocalYftColors = staticCompositionLocalOf { YftLightPalette }
