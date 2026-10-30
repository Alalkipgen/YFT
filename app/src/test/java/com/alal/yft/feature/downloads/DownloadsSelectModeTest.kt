package com.alal.yft.feature.downloads

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.download.DownloadPlanType
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.feature.library.LocalMediaDetailsSource
import com.alal.yft.feature.library.MediaDetailsSource
import com.alal.yft.thumbnail.DownloadThumbnails
import com.alal.yft.thumbnail.LocalDownloadThumbnails
import com.alal.yft.ui.theme.YftTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Item 5 (v1.0.2): a long press on a finished download starts select mode; the bar removes the
 * picked downloads from the list or deletes their files with one question. Before item 5 a
 * download could only be removed or deleted one at a time from its own menu.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
class DownloadsSelectModeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun aLongPressPicksAFinishedDownloadAndTapsPickMore() {
        setScreen()
        composeRule.onAllNodesWithTag("downloads-select-bar").assertCountEquals(0)

        composeRule.onNodeWithTag("download-a").performTouchInput { longClick() }

        composeRule.onNodeWithTag("downloads-select-bar").assertIsDisplayed()
        composeRule.onNodeWithTag("downloads-select-count").assertTextEquals("1 selected")
        composeRule.onNodeWithTag("download-a").assert(isSelected(true))
        // A tap picks in select mode; it opens no menu.
        composeRule.onNodeWithTag("download-b").performClick()
        composeRule.onNodeWithTag("downloads-select-count").assertTextEquals("2 selected")
        composeRule.onAllNodesWithTag("download-menu-delete-file-b").assertCountEquals(0)
        composeRule.onNodeWithTag("download-b").assert(isSelected(true))
        composeRule.onNodeWithTag("download-c").assert(isSelected(false))
        // Play gives way to the pick mark, so a tap on it only picks.
        composeRule.onAllNodesWithTag("download-open-a").assertCountEquals(0)
        composeRule.onNodeWithTag("download-pick-a", useUnmergedTree = true).assertIsDisplayed()
        // Another tap drops it.
        composeRule.onNodeWithTag("download-b").performClick()
        composeRule.onNodeWithTag("downloads-select-count").assertTextEquals("1 selected")
    }

    @Test
    fun aRunningDownloadIsNeitherPickedNorOpenedInSelectMode() {
        setScreen()

        composeRule.onNodeWithTag("download-a").performTouchInput { longClick() }
        composeRule.onNodeWithTag("download-r").performClick()
        composeRule.onNodeWithTag("download-r").performTouchInput { longClick() }

        composeRule.onNodeWithTag("downloads-select-count").assertTextEquals("1 selected")
        composeRule.onAllNodesWithTag("download-menu-pause-r").assertCountEquals(0)
        composeRule.onNodeWithTag("download-r")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))

        // Out of select mode, a long press on it starts none (it opens its menu, as a tap does).
        composeRule.onNodeWithTag("downloads-select-cancel").performClick()
        composeRule.onNodeWithTag("download-r").performTouchInput { longClick() }
        composeRule.onAllNodesWithTag("downloads-select-bar").assertCountEquals(0)
    }

    @Test
    fun selectAllPicksEveryFinishedDownloadAndCancelOrBackLeaves() {
        setScreen()

        composeRule.onNodeWithTag("download-a").performTouchInput { longClick() }
        composeRule.onNodeWithTag("downloads-select-all").assertIsEnabled().performClick()

        composeRule.onNodeWithTag("downloads-select-count").assertTextEquals("3 selected")
        composeRule.onNodeWithTag("downloads-select-all").assertIsNotEnabled()
        composeRule.onNodeWithTag("downloads-select-delete-file").assertTextEquals("Delete files")

        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onAllNodesWithTag("downloads-select-bar").assertCountEquals(0)

        composeRule.onNodeWithTag("download-c").performTouchInput { longClick() }
        composeRule.onNodeWithTag("downloads-select-cancel").performClick()
        composeRule.onAllNodesWithTag("downloads-select-bar").assertCountEquals(0)
        // Out of select mode a tap opens the menu again.
        composeRule.onNodeWithTag("download-a").performClick()
        composeRule.onNodeWithTag("download-menu-delete-file-a").assertIsDisplayed()
    }

    @Test
    fun removeFromListHandsOverThePickedDownloadsAndLeavesSelectMode() {
        val removed = mutableListOf<List<String>>()
        setScreen(
            selectionActions = DownloadSelectionActions(
                onRemoveFromList = { removed += it },
                onDeleteFiles = {},
            ),
        )

        composeRule.onNodeWithTag("download-c").performTouchInput { longClick() }
        composeRule.onNodeWithTag("download-a").performClick()
        composeRule.onNodeWithTag("downloads-select-remove")
            .assertTextEquals("Remove from list")
            .performClick()

        assertEquals(listOf(listOf("c", "a")), removed)
        composeRule.onAllNodesWithTag("downloads-select-bar").assertCountEquals(0)
    }

    @Test
    fun deleteFileAsksOnceForThePickedDownloadsAndDeleteLeavesSelectMode() {
        val question = mutableStateOf<DeleteFileQuestion?>(null)
        var confirmed = 0
        setScreen(
            question = { question.value },
            actions = DeleteFileActions(
                onDeleteFile = {},
                onConfirm = {
                    confirmed++
                    question.value = null
                },
                onCancel = { question.value = null },
            ),
            selectionActions = DownloadSelectionActions(
                onRemoveFromList = {},
                onDeleteFiles = { question.value = DeleteFileQuestion(it, "${it.first()}.mp4") },
            ),
        )

        composeRule.onNodeWithTag("download-a").performTouchInput { longClick() }
        composeRule.onNodeWithTag("downloads-select-delete-file")
            .assertTextEquals("Delete file")
        composeRule.onNodeWithTag("download-b").performClick()
        composeRule.onNodeWithTag("downloads-select-delete-file").performClick()

        composeRule.onNodeWithTag("download-delete-file-dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Delete 2 files?").assertIsDisplayed()
        composeRule.onNodeWithText(
            "These 2 files will be removed from Download/YFT and from this list. " +
                "This can't be undone.",
        ).assertIsDisplayed()
        // Cancel keeps what was picked.
        composeRule.onNodeWithTag("download-delete-file-cancel").performClick()
        composeRule.onNodeWithTag("downloads-select-count").assertTextEquals("2 selected")

        composeRule.onNodeWithTag("downloads-select-delete-file").performClick()
        composeRule.onNodeWithTag("download-delete-file-confirm").performClick()

        assertEquals(1, confirmed)
        composeRule.onAllNodesWithTag("downloads-select-bar").assertCountEquals(0)
    }

    @Test
    fun anotherFilterLeavesSelectMode() {
        setScreen()

        composeRule.onNodeWithTag("download-a").performTouchInput { longClick() }
        composeRule.onNodeWithTag("downloads-filter-done").performClick()

        composeRule.onAllNodesWithTag("downloads-select-bar").assertCountEquals(0)
        assertTrue(composeRule.onAllNodesWithTag("download-a").fetchSemanticsNodes().isNotEmpty())
    }

    private fun isSelected(selected: Boolean) =
        SemanticsMatcher.expectValue(SemanticsProperties.Selected, selected)

    private fun setScreen(
        question: () -> DeleteFileQuestion? = { null },
        actions: DeleteFileActions = DeleteFileActions.None,
        selectionActions: DownloadSelectionActions = DownloadSelectionActions.None,
    ) {
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(
                    LocalMediaDetailsSource provides MediaDetailsSource.None,
                    LocalDownloadThumbnails provides DownloadThumbnails.None,
                ) {
                    DownloadsScreen(
                        uiState = DownloadsUiState(
                            rows = listOf(
                                row("r", DownloadTaskStatus.RUNNING),
                                row("a", DownloadTaskStatus.COMPLETED),
                                row("b", DownloadTaskStatus.COMPLETED),
                                row("c", DownloadTaskStatus.COMPLETED),
                            ),
                        ),
                        onAction = { _, _ -> },
                        onPauseAll = {},
                        todayStartEpochMs = 0,
                        deleteFileQuestion = question(),
                        deleteFileActions = actions,
                        selectionActions = selectionActions,
                    )
                }
            }
        }
    }

    private fun row(id: String, status: DownloadTaskStatus) = DownloadRowUiState(
        id = id,
        displayName = "$id.mp4",
        status = status,
        planType = DownloadPlanType.DIRECT,
        destinationKind = DownloadDestinationKind.MEDIA_STORE,
        downloadedBytes = 100,
        totalBytes = 100,
        progressPercent = if (status == DownloadTaskStatus.COMPLETED) 100 else 50,
        requiresLinkRefresh = false,
        failureReason = null,
        updatedAtEpochMs = 1,
        bytesPerSecond = null,
    )
}
