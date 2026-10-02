package com.alal.yft.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YftThemeContrastTest {
    @Test
    fun everyTextPairMeetsWcagAaInBothThemes() {
        listOf("light" to YftLightColors, "dark" to YftDarkColors).forEach { (name, scheme) ->
            textPairs(scheme).forEach { (label, pair) ->
                val ratio = contrast(pair.first, pair.second)
                assertTrue("$name $label is ${"%.2f".format(ratio)}:1", ratio >= TEXT_MINIMUM)
            }
            val outline = contrast(scheme.outline, scheme.surface)
            assertTrue("$name outline is ${"%.2f".format(outline)}:1", outline >= NON_TEXT_MINIMUM)
        }
    }

    @Test
    fun paletteIsYftsOwnRatherThanTheMaterialDefault() {
        assertNotEquals(Color(0xFF6750A4), YftLightColors.primary)
        assertNotEquals(Color(0xFFD0BCFF), YftDarkColors.primary)
        assertNotEquals(YftLightColors.surface, YftDarkColors.surface)
    }

    private fun textPairs(scheme: ColorScheme): List<Pair<String, Pair<Color, Color>>> {
        val surfaces = listOf(
            "surface" to scheme.surface,
            "surfaceDim" to scheme.surfaceDim,
            "surfaceBright" to scheme.surfaceBright,
            "surfaceContainerLowest" to scheme.surfaceContainerLowest,
            "surfaceContainerLow" to scheme.surfaceContainerLow,
            "surfaceContainer" to scheme.surfaceContainer,
            "surfaceContainerHigh" to scheme.surfaceContainerHigh,
            "surfaceContainerHighest" to scheme.surfaceContainerHighest,
            "surfaceVariant" to scheme.surfaceVariant,
        )
        val onSurfaces = surfaces.flatMap { (label, background) ->
            listOf(
                "onSurface on $label" to (scheme.onSurface to background),
                "onSurfaceVariant on $label" to (scheme.onSurfaceVariant to background),
                "primary on $label" to (scheme.primary to background),
                "error on $label" to (scheme.error to background),
            )
        }
        return onSurfaces + listOf(
            "onPrimary" to (scheme.onPrimary to scheme.primary),
            "onPrimaryContainer" to (scheme.onPrimaryContainer to scheme.primaryContainer),
            "onSecondary" to (scheme.onSecondary to scheme.secondary),
            "onSecondaryContainer" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
            "onTertiary" to (scheme.onTertiary to scheme.tertiary),
            "onTertiaryContainer" to (scheme.onTertiaryContainer to scheme.tertiaryContainer),
            "onError" to (scheme.onError to scheme.error),
            "onErrorContainer" to (scheme.onErrorContainer to scheme.errorContainer),
            "onBackground" to (scheme.onBackground to scheme.background),
            "inverseOnSurface" to (scheme.inverseOnSurface to scheme.inverseSurface),
            "inversePrimary" to (scheme.inversePrimary to scheme.inverseSurface),
        )
    }

    private fun contrast(first: Color, second: Color): Double {
        val a = relativeLuminance(first)
        val b = relativeLuminance(second)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    /** WCAG 2.x relative luminance of an opaque sRGB color. */
    private fun relativeLuminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }

    private companion object {
        const val TEXT_MINIMUM = 4.5
        const val NON_TEXT_MINIMUM = 3.0
    }
}
