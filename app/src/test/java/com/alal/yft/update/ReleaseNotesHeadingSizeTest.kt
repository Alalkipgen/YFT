package com.alal.yft.update

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.ui.theme.YftTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v1.0.2 fix R1: at the owner's large text size a `###` heading was drawn as large as bold body
 * text (63 vs 62 px on the phone). Android 14 and newer scale large text less than small text, so
 * a heading only 1 sp up (titleSmall, 15 sp, before R1) all but lost its lead. The same word as
 * `#`, `##`, `###` and bold body text, measured with native graphics at each text size: each
 * heading is wider than the next level, and `###` at least 5% wider than the bold body text.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h872dp-xxhdpi")
class ReleaseNotesHeadingSizeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(fontScale = 1.0f)
    fun `at the default text size each heading stands out`() = assertHeadingsStandOut(1.0f)

    @Test
    @Config(fontScale = 1.3f)
    fun `at 130 percent text size each heading stands out`() = assertHeadingsStandOut(1.3f)

    @Test
    @Config(fontScale = 1.5f)
    fun `at 150 percent text size each heading stands out`() = assertHeadingsStandOut(1.5f)

    @Test
    @Config(fontScale = 2.0f)
    fun `at 200 percent text size each heading stands out`() = assertHeadingsStandOut(2.0f)

    private fun assertHeadingsStandOut(fontScale: Float) {
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) { ReleaseNotesView(NOTES) }
        }
        val layouts = List(4) { index -> layoutOf(index) }
        val density = layouts.first().layoutInput.density
        assertEquals(fontScale, density.fontScale, 0.001f)
        if (fontScale > 1.03f) {
            // As on Android 14 and newer: large text grows less than small text.
            val grown = with(density) { 24.sp.toDp() / 14.sp.toDp() }
            assertTrue("24 sp is $grown times 14 sp", grown < 24f / 14f)
        }
        val (h1, h2, h3, body) = layouts.map { it.getLineRight(0) - it.getLineLeft(0) }
        assertTrue("# $h1 px is wider than ## $h2 px", h1 > h2)
        assertTrue("## $h2 px is wider than ### $h3 px", h2 > h3)
        assertTrue("### $h3 px is 5% wider than bold body text $body px", h3 >= body * MIN_LEAD)
    }

    /** The [index]th "Notes": `#`, `##`, `###` and the bold body text, top to bottom. */
    private fun layoutOf(index: Int): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onAllNodesWithText(WORD, useUnmergedTree = true)[index]
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    private companion object {
        const val WORD = "Notes"
        const val NOTES = "# $WORD\n\n## $WORD\n\n### $WORD\n\n**$WORD**"
        const val MIN_LEAD = 1.05f
    }
}
