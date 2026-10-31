package com.alal.yft.design

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextLayoutResult
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.core.model.settings.HomeSite
import com.alal.yft.core.model.settings.HomeSites
import com.alal.yft.feature.downloads.SelectionBar
import com.alal.yft.feature.home.HomeScreen
import com.alal.yft.feature.home.HomeUiState
import com.alal.yft.feature.quickdownload.QuickDownloadScreen
import com.alal.yft.feature.quickdownload.QuickDownloadUiState
import com.alal.yft.feature.quickdownload.SheetHeader
import com.alal.yft.ui.navigation.YftBottomBar
import com.alal.yft.ui.navigation.YftDestination
import java.io.File
import kotlin.math.roundToInt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v1.0.2 fix batch after #149 (the owner's phone: Xiaomi HyperOS, large text): the texts that
 * were cut at 130 % and 150 % text fit there now, on a 360 dp wide screen, one test per fix
 * (N1 Home's site names, N3 Downloads' select-mode buttons, N4 the bottom bar's tab names, N7
 * the sheet's length, …). A text fits when its layout neither overflows nor ends in an
 * ellipsis. With `YFT_RENDER_DIR` set, each case is also saved as `fit<percent>-<name>.png`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xhdpi")
class LargeFontFitTest(private val fontScale: Float) {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `N1 Home's site names fit their tiles`() {
        val sites = HomeSites.DEFAULTS + listOf(
            HomeSite("Wikimedia", "https://commons.wikimedia.org"),
            HomeSite("Archive", "https://archive.org"),
        )
        composeRule.setContent {
            DesignStage(themeMode = ThemeMode.LIGHT, fontScale = fontScale) {
                HomeScreen(
                    state = HomeUiState(sites = sites),
                    onAction = {},
                    onOpenBrowser = {},
                    onOpenDetectedMedia = {},
                    onOpenLibrary = {},
                )
            }
        }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("home-site-add"))
        render("n1-home-sites")

        (sites.map(HomeSite::name) + "Add").forEach { name ->
            composeRule.onNodeWithText(name, useUnmergedTree = true).assertFits(name)
        }
    }

    @Test
    fun `N3 Downloads' select-mode buttons are shown whole`() {
        composeRule.setContent {
            DesignStage(themeMode = ThemeMode.LIGHT, fontScale = fontScale) {
                SelectionBar(
                    count = 2,
                    allPicked = false,
                    onCancel = {},
                    onSelectAll = {},
                    onRemoveFromList = {},
                    onDeleteFiles = {},
                )
            }
        }
        render("n3-downloads-select-bar")

        listOf("2 selected", "Select all", "Remove from list", "Delete files").forEach { label ->
            composeRule.onNodeWithText(label, useUnmergedTree = true).assertFits(label)
        }
    }

    @Test
    fun `N4 the bottom bar's tab names are shown whole`() {
        composeRule.setContent {
            DesignStage(themeMode = ThemeMode.LIGHT, fontScale = fontScale) {
                YftBottomBar(
                    selected = YftDestination.DOWNLOADS,
                    activeDownloads = 2,
                    onSelect = {},
                )
            }
        }
        render("n4-bottom-bar")

        YftDestination.topLevel.map(YftDestination::title).forEach { name ->
            composeRule.onNodeWithText(name, useUnmergedTree = true).assertFits(name)
        }
    }

    @Test
    fun `N7 the sheet's length is shown whole beside a long site name`() {
        composeRule.setContent {
            DesignStage(themeMode = ThemeMode.LIGHT, fontScale = fontScale) {
                QuickDownloadScreen(
                    state = QuickDownloadUiState(
                        header = SheetHeader(
                            title = "Обзор матча",
                            source = "vksport.m.vkvideo.ru",
                            durationMillis = 845_000,
                            audioOnly = false,
                        ),
                        loading = true,
                    ),
                    onSelect = {},
                    onDownload = {},
                )
            }
        }
        render("n7-sheet-length")

        composeRule.onNodeWithText("14:05", useUnmergedTree = true).assertFits("14:05")
    }

    /**
     * The text is shown whole: no line ends in an ellipsis, no line is left out, and a one-line
     * text's own width fits its room (a centred text's layout is as wide as its room, so its
     * overflow flags say nothing).
     */
    private fun SemanticsNodeInteraction.assertFits(what: String) {
        val layout = textLayout()
        val where = "$what at ${fontScale}x"
        assertTrue(
            "$where ends in an ellipsis",
            (0 until layout.lineCount).none { layout.isLineEllipsized(it) },
        )
        assertFalse("$where leaves lines out", layout.multiParagraph.didExceedMaxLines)
        if (layout.layoutInput.maxLines == 1) {
            val needed = layout.multiParagraph.intrinsics.maxIntrinsicWidth
            val room = layout.layoutInput.constraints.maxWidth
            assertTrue("$where needs $needed px of $room", needed <= room + HALF_PIXEL)
        }
    }

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val node = fetchSemanticsNode()
        requireNotNull(node.config[SemanticsActions.GetTextLayoutResult].action)(results)
        return results.single()
    }

    private fun render(name: String) {
        val directory = System.getenv("YFT_RENDER_DIR")?.let(::File) ?: return
        val percent = (fontScale * PERCENT).roundToInt()
        composeRule.saveWindow(File(directory, "fit$percent-$name.png"))
    }

    companion object {
        private const val PERCENT = 100
        private const val HALF_PIXEL = 0.5f

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "text at {0}x")
        fun scales(): List<Array<Any>> = listOf(1.3f, 1.5f).map { arrayOf<Any>(it) }
    }
}
