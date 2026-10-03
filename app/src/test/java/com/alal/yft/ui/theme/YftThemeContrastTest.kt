package com.alal.yft.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YftThemeContrastTest {
    private val themes = listOf(
        Triple("light", YftLightColors, YftLightPalette),
        Triple("dark", YftDarkColors, YftDarkPalette),
    )

    @Test
    fun everyMaterialTextPairMeetsWcagAaInBothThemes() {
        themes.forEach { (name, scheme, _) ->
            materialTextPairs(scheme).forEach { (label, pair) ->
                assertRatio("$name $label", pair, TEXT_MINIMUM)
            }
            assertRatio("$name outline", scheme.outline to scheme.surface, NON_TEXT_MINIMUM)
        }
    }

    @Test
    fun everyYftSemanticPairMeetsItsMinimumInBothThemes() {
        themes.forEach { (name, _, colors) ->
            yftTextPairs(colors).forEach { (label, pair) ->
                assertRatio("$name $label", pair, TEXT_MINIMUM)
            }
            yftNonTextPairs(colors).forEach { (label, pair) ->
                assertRatio("$name $label", pair, NON_TEXT_MINIMUM)
            }
        }
    }

    @Test
    fun brandTokensMatchTheDesignBrief() {
        assertEquals(Color(0xFF19C9A9), YftPalette.MintTeal)
        assertEquals(Color(0xFF0F1C1E), YftPalette.Ink)
        assertEquals(Color(0xFF007A6E), YftPalette.DeepTeal)
        assertEquals(Color(0xFF5B6B6E), YftPalette.Slate)
        assertEquals(Color(0xFFF2F7F7), YftPalette.Surface)
        assertEquals(Color(0xFFE2EAEA), YftPalette.Border)
        assertEquals(Color(0xFFFF7452), YftPalette.Coral)
        assertEquals(Color(0xFF0B1416), YftPalette.Night)

        // Mint fills carry Ink in both themes, Coral carries Ink, and dark mode sits on Night.
        themes.forEach { (name, _, colors) ->
            assertEquals(name, YftPalette.MintTeal, colors.accent)
            assertEquals(name, YftPalette.Ink, colors.onAccent)
            assertEquals(name, YftPalette.Coral, colors.coral)
            assertEquals(name, YftPalette.Ink, colors.onCoral)
        }
        assertEquals(YftPalette.Night, YftDarkColors.background)
        assertEquals(YftPalette.Surface, YftLightColors.background)
        assertEquals(YftPalette.DeepTeal, YftLightPalette.link)
    }

    private fun materialTextPairs(scheme: ColorScheme): List<Pair<String, Pair<Color, Color>>> {
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

    private fun yftTextPairs(colors: YftColors): List<Pair<String, Pair<Color, Color>>> {
        val grounds = listOf(
            "background" to colors.background,
            "card" to colors.card,
            "chip" to colors.chip,
        )
        val text = grounds.flatMap { (label, ground) ->
            listOf(
                "textPrimary on $label" to (colors.textPrimary to ground),
                "textSecondary on $label" to (colors.textSecondary to ground),
                "link on $label" to (colors.link to ground),
                "coralText on $label" to (colors.coralText to ground),
            )
        }
        val scrimOverLight = colors.scrim.compositeOver(Color.White)
        return text + listOf(
            "textPrimary on chipOnBackground" to (colors.textPrimary to colors.chipOnBackground),
            "textSecondary on chipOnBackground" to
                (colors.textSecondary to colors.chipOnBackground),
            "textPrimary on accentSoft" to (colors.textPrimary to colors.accentSoft),
            "onAccent on accent" to (colors.onAccent to colors.accent),
            "onNavIndicator on navIndicator" to (colors.onNavIndicator to colors.navIndicator),
            "onCoral on coral" to (colors.onCoral to colors.coral),
            "coralText on coralSoft" to (colors.coralText to colors.coralSoft),
            "onWaiting on waiting" to (colors.onWaiting to colors.waiting),
            "onScrim on scrim over white" to (colors.onScrim to scrimOverLight),
        )
    }

    private fun yftNonTextPairs(colors: YftColors): List<Pair<String, Pair<Color, Color>>> = listOf(
        "success on card" to (colors.success to colors.card),
        "fieldOutline on card" to (colors.fieldOutline.compositeOver(colors.card) to colors.card),
        "link icon on card" to (colors.link to colors.card),
    )

    private fun assertRatio(label: String, pair: Pair<Color, Color>, minimum: Double) {
        val ratio = contrast(pair.first, pair.second)
        assertTrue("$label is ${"%.2f".format(ratio)}:1", ratio >= minimum)
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
