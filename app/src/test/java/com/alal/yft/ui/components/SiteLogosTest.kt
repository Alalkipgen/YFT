package com.alal.yft.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.alal.yft.core.model.settings.SiteBrand
import com.alal.yft.ui.theme.YftDarkPalette
import com.alal.yft.ui.theme.YftLightPalette
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteLogosTest {
    @Test
    fun everyBrandHasItsOwnLogo() {
        val logos = SiteBrand.entries.map { it.logoRes() }

        assertEquals(SiteBrand.entries.size, logos.toSet().size)
    }

    @Test
    fun logosKeepNonTextContrastOnTheSiteCircleInDayAndNight() {
        listOf("Day" to YftLightPalette, "Night" to YftDarkPalette).forEach { (theme, colors) ->
            SiteBrand.entries.forEach { brand ->
                val ratio = contrast(siteLogoTint(brand, colors), colors.accentSoft)
                assertTrue("$theme ${brand.slug} is ${"%.2f".format(ratio)}:1", ratio >= 3.0)
            }
        }
    }

    @Test
    fun dayUsesTheBrandColour() {
        SiteBrand.entries.forEach { brand ->
            assertEquals(brand.brandColor(), siteLogoTint(brand, YftLightPalette))
        }
    }

    private fun contrast(first: Color, second: Color): Double {
        val a = first.luminance().toDouble()
        val b = second.luminance().toDouble()
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }
}
