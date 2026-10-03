package com.alal.yft.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Raw YFT brand tokens from the design brief (`docs/design/DESIGN-NOTES.md`). Screens should not
 * use these directly; they go through [YftColors] or the Material color scheme so light and dark
 * themes stay in step.
 */
object YftPalette {
    /** Primary fill: buttons, selected chips/tabs, switches and progress. Never text on light. */
    val MintTeal = Color(0xFF19C9A9)

    /** Main text, and text or icons placed on Mint or Coral. */
    val Ink = Color(0xFF0F1C1E)

    /** Icons, links and text buttons on light surfaces. */
    val DeepTeal = Color(0xFF007A6E)

    /** Secondary text on light surfaces. */
    val Slate = Color(0xFF5B6B6E)

    /** Soft page backgrounds and tonal chips. */
    val Surface = Color(0xFFF2F7F7)

    /** 1dp card borders and dividers; YFT uses borders instead of heavy shadows. */
    val Border = Color(0xFFE2EAEA)

    /** Count badges and the Failed status. Carries Ink text: white on Coral is only 2.7:1. */
    val Coral = Color(0xFFFF7452)

    /** Dark theme background. */
    val Night = Color(0xFF0B1416)

    val White = Color(0xFFFFFFFF)

    /** Coral darkened until text passes 4.5:1 on white and on [Surface]. */
    internal val CoralDeep = Color(0xFFB93A1A)
    internal val CoralSoft = Color(0xFFFFE7E0)
    internal val MintSoft = Color(0xFFD2F3ED)

    /** Between [Surface] and [Border]; Deep Teal and Slate text still pass AA on it. */
    internal val SurfaceMuted = Color(0xFFECF2F2)
    internal val OutlineLight = Color(0xFF7A8A8D)
    internal val SuccessLight = Color(0xFF178A55)

    internal val NightCard = Color(0xFF121E21)
    internal val NightRaised = Color(0xFF162427)
    internal val NightChip = Color(0xFF1C2A2D)
    internal val NightBorder = Color(0xFF22302F)
    internal val NightText = Color(0xFFE8F1F1)
    internal val NightTextSecondary = Color(0xFF9DB0B2)
    internal val NightOutline = Color(0xFF62777A)
    internal val NightMintSoft = Color(0xFF17403A)
    internal val NightCoralSoft = Color(0xFF4A1F15)
    internal val NightSuccess = Color(0xFF5BD69A)
}
