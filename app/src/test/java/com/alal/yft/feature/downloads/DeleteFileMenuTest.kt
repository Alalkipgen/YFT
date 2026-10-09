package com.alal.yft.feature.downloads

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P42: a finished download's menu offers "Delete file" beside "Remove from list"; unfinished
 * rows keep their menus. Before P42 the menu had only "Remove from list", which kept the file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
class DeleteFileMenuTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun onlyAFinishedRowsMenuOffersDeleteFileBesideRemoveFromList() {
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(
                    LocalMediaDetailsSource provides MediaDetailsSource.None,
                    LocalDownloadThumbnails provides DownloadThumbnails.None,
                ) {
                    DownloadsScreen(
                        uiState = DownloadsUiState(
                            rows = listOf(
                                row("c", DownloadTaskStatus.COMPLETED),
                                row("p", DownloadTaskStatus.PAUSED),
                                row("f", DownloadTaskStatus.FAILED),
                            ),
                        ),
                        onAction = { _, _ -> },
                        onPauseAll = {},
                        todayStartEpochMs = 0,
                    )
                }
            }
        }

        composeRule.onNodeWithTag("download-c").performClick()
        composeRule.onNodeWithTag("download-menu-delete-c").assertIsDisplayed()
        composeRule.onNodeWithTag("download-menu-delete-file-c")
            .assertIsDisplayed()
            .assertTextEquals("Delete file")
        composeRule.onNodeWithTag("download-c").performClick()
        for (id in listOf("p", "f")) {
            composeRule.onNodeWithTag("download-$id").performClick()
            composeRule.onAllNodesWithTag("download-menu-delete-file-$id").assertCountEquals(0)
            composeRule.onNodeWithTag("download-$id").performClick()
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
