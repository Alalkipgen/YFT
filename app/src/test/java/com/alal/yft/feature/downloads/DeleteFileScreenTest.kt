package com.alal.yft.feature.downloads

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** P42: "Delete file" on finished rows only, and the question before it deletes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
class DeleteFileScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aFinishedRowsMenuHasDeleteFileBesideRemoveFromList() {
        val asked = mutableListOf<String>()
        setScreen(
            rows = listOf(
                row("c", DownloadTaskStatus.COMPLETED),
                row("p", DownloadTaskStatus.PAUSED),
                row("f", DownloadTaskStatus.FAILED),
            ),
            actions = DeleteFileActions(onDeleteFile = { asked += it }, {}, {}),
        )

        composeRule.onNodeWithTag("download-c").performClick()
        composeRule.onNodeWithTag("download-menu-delete-c").assertIsDisplayed()
        composeRule.onNodeWithTag("download-menu-delete-file-c")
            .assertIsDisplayed()
            .performClick()

        assertEquals(listOf("c"), asked)
        for (id in listOf("p", "f")) {
            composeRule.onNodeWithTag("download-$id").performClick()
            composeRule.onAllNodesWithTag("download-menu-delete-file-$id").assertCountEquals(0)
            composeRule.onNodeWithTag("download-$id").performClick()
        }
    }

    @Test
    fun theQuestionNamesTheFileAndDeleteOrCancelAnswersIt() {
        var confirmed = 0
        var cancelled = 0
        setScreen(
            rows = listOf(row("c", DownloadTaskStatus.COMPLETED)),
            question = DeleteFileQuestion("c", "Movie.mp4"),
            actions = DeleteFileActions(
                {},
                onConfirm = { confirmed++ },
                onCancel = { cancelled++ },
            ),
        )

        composeRule.onNodeWithTag("download-delete-file-dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Delete this file?").assertIsDisplayed()
        composeRule.onNodeWithText(
            "\u201cMovie.mp4\u201d will be removed from Download/YFT and from this list. " +
                "This can't be undone.",
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("download-delete-file-confirm").assertTextEquals("Delete")
        composeRule.onNodeWithTag("download-delete-file-confirm").performClick()
        composeRule.onNodeWithTag("download-delete-file-cancel").performClick()

        assertEquals(1, confirmed)
        assertEquals(1, cancelled)
    }

    private fun setScreen(
        rows: List<DownloadRowUiState>,
        question: DeleteFileQuestion? = null,
        actions: DeleteFileActions,
    ) {
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(
                    LocalMediaDetailsSource provides MediaDetailsSource.None,
                    LocalDownloadThumbnails provides DownloadThumbnails.None,
                ) {
                    DownloadsScreen(
                        uiState = DownloadsUiState(rows = rows),
                        onAction = { _, _ -> },
                        onPauseAll = {},
                        todayStartEpochMs = 0,
                        deleteFileQuestion = question,
                        deleteFileActions = actions,
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
