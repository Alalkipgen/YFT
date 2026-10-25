package com.alal.yft.update

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.ui.components.YftPrimaryButton
import com.alal.yft.ui.theme.YftIcons
import com.alal.yft.ui.theme.YftTheme
import com.alal.yft.update.Updates.START
import com.alal.yft.update.Updates.found
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The owner's large text size (U3): **Download APK to update** wraps to two lines instead of being
 * cut ("Download APK to upd…"), and a primary button elsewhere keeps its one line. Native graphics,
 * so the text is measured as on a phone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xhdpi")
class UpdatesLargeTextTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theDownloadLabelWrapsToTwoLinesInsteadOfBeingCut() {
        composeRule.setContent {
            LargeText {
                UpdatesScreen(
                    state = UpdateState(
                        mode = UpdateMode.RELEASE,
                        installedName = "1.0.0",
                        autoCheck = true,
                        found = found("v1.0.1"),
                    ),
                    onNavigateBack = {},
                    onCheckNow = {},
                    onAutoCheckChanged = {},
                    times = UpdateTimes(
                        now = START,
                        is24Hour = true,
                        zone = TimeZone.getTimeZone("UTC"),
                    ),
                    onOpenInstallSettings = {},
                    onOpenLink = { true },
                )
            }
        }

        val label = layoutOf(LABEL)
        assertEquals(2, label.lineCount)
        assertFalse(label.isLineEllipsized(1))
    }

    @Test
    fun anotherPrimaryButtonKeepsItsOneLine() {
        composeRule.setContent {
            LargeText {
                YftPrimaryButton(
                    text = LABEL,
                    onClick = {},
                    modifier = Modifier.width(SCREEN_BUTTON_WIDTH),
                    icon = YftIcons.Download,
                    large = true,
                )
            }
        }

        val label = layoutOf(LABEL)
        assertEquals(1, label.lineCount)
        assertTrue(label.isLineEllipsized(0))
    }

    private fun layoutOf(text: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    @Composable
    private fun LargeText(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        YftTheme(themeMode = ThemeMode.LIGHT) {
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, FONT_SCALE),
                content = content,
            )
        }
    }

    private companion object {
        const val LABEL = "Download APK to update"

        /** Android's largest text size. */
        const val FONT_SCALE = 2f

        /** The button's width on a 360dp phone: the screen's and the card's padding taken off. */
        val SCREEN_BUTTON_WIDTH = 296.dp
    }
}
