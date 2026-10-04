package com.alal.yft.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.alal.yft.R
import com.alal.yft.core.model.settings.SiteBrand
import com.alal.yft.ui.theme.YftColors

/**
 * Logos for known sites in "Your sites", drawn from the Simple Icons shapes (CC0-1.0, see
 * `docs/THIRD_PARTY_NOTICES.md`). They are bundled; YFT never fetches a site's icon. Site names
 * and logos belong to their owners; YFT is not affiliated with them.
 */
@DrawableRes
internal fun SiteBrand.logoRes(): Int = when (this) {
    SiteBrand.YOUTUBE -> R.drawable.ic_site_youtube
    SiteBrand.FACEBOOK -> R.drawable.ic_site_facebook
    SiteBrand.TIKTOK -> R.drawable.ic_site_tiktok
    SiteBrand.INSTAGRAM -> R.drawable.ic_site_instagram
    SiteBrand.X -> R.drawable.ic_site_x
}

/** The brand colour from the Simple Icons data. */
internal fun SiteBrand.brandColor(): Color = when (this) {
    SiteBrand.YOUTUBE -> Color(0xFFFF0000)
    SiteBrand.FACEBOOK -> Color(0xFF0866FF)
    SiteBrand.TIKTOK -> Color(0xFF000000)
    SiteBrand.INSTAGRAM -> Color(0xFFFF0069)
    SiteBrand.X -> Color(0xFF000000)
}

/**
 * The logo's colour on the site circle. Day uses the brand colour. Night keeps the logo visible
 * on the dark circle: black marks use the Night text colour and coloured ones are lightened.
 */
internal fun siteLogoTint(brand: SiteBrand, colors: YftColors): Color {
    val color = brand.brandColor()
    if (!colors.isDark) return color
    return if (color.luminance() < DARK_MARK_LUMINANCE) {
        colors.textPrimary
    } else {
        lerp(color, Color.White, NIGHT_LIGHTEN)
    }
}

private const val DARK_MARK_LUMINANCE = 0.05f
private const val NIGHT_LIGHTEN = 0.35f
