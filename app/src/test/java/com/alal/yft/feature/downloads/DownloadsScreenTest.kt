package com.alal.yft.feature.downloads

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.download.DownloadPlanType
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.ui.theme.YftTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyQueueExplainsHowToStartADownload() {
        setScreen(DownloadsUiState.Empty)

        composeRule.onNodeWithTag("downloads-empty").assertIsDisplayed()
        composeRule.onNodeWithText("No downloads yet").assertIsDisplayed()
    }

    @Test
    fun runningTaskShowsDeterminateProgressAndForwardsPause() {
        val actions = mutableListOf<Pair<DownloadAction, String>>()
        setScreen(
            DownloadsUiState(
                listOf(
                    row(
                        id = "a",
                        status = DownloadTaskStatus.RUNNING,
                        downloaded = 1_572_864,
                        total = 3_145_728,
                        progressPercent = 50,
                    ),
                ),
            ),
            onAction = { action, id -> actions += action to id },
        )

        composeRule.onNodeWithTag("download-a").assertIsDisplayed()
        composeRule.onNodeWithTag("download-progress-a").assertIsDisplayed()
        composeRule.onNodeWithText("1.5 MB of 3.0 MB · 50%").assertIsDisplayed()
        composeRule.onNodeWithTag("downloads-summary").assertIsDisplayed()
        composeRule.onNodeWithTag("downloads-pause-all").assertIsEnabled()

        composeRule.onNodeWithTag("download-action-pause-a").performClick()

        assertEquals(listOf(DownloadAction.PAUSE to "a"), actions)
    }

    @Test
    fun unknownRemoteSizeStaysHonestAndUsesIndeterminateProgress() {
        setScreen(
            DownloadsUiState(
                listOf(
                    row(
                        id = "stream",
                        status = DownloadTaskStatus.RUNNING,
                        downloaded = 2_048,
                        total = null,
                        progressPercent = 0,
                        planType = DownloadPlanType.HLS,
                        destinationKind = DownloadDestinationKind.MEDIA_STORE,
                    ),
                ),
            ),
        )

        composeRule.onNodeWithTag("download-progress-indeterminate-stream").assertIsDisplayed()
        composeRule.onNodeWithText("2.0 KB downloaded · total size unknown").assertIsDisplayed()
        composeRule.onNodeWithText("Downloading · HLS stream · Device media").assertIsDisplayed()
    }

    @Test
    fun expiredLinkTaskExplainsRefreshAndOnlyOffersRemoval() {
        val actions = mutableListOf<Pair<DownloadAction, String>>()
        setScreen(
            DownloadsUiState(
                listOf(
                    row(
                        id = "old",
                        status = DownloadTaskStatus.NEEDS_REFRESH,
                        downloaded = 1_024,
                        total = 4_096,
                        progressPercent = 25,
                        requiresLinkRefresh = true,
                        failureReason = DownloadFailureReason.EXPIRED_URL,
                    ),
                ),
            ),
            onAction = { action, id -> actions += action to id },
        )

        composeRule.onNodeWithTag("download-refresh-note-old").assertIsDisplayed()
        composeRule.onNodeWithTag("downloads-pause-all").assertIsNotEnabled()

        composeRule.onNodeWithTag("download-action-delete-old").performClick()

        assertEquals(listOf(DownloadAction.DELETE to "old"), actions)
    }

    @Test
    fun pauseAllForwardsTheBulkControl() {
        var pauseAllCount = 0
        setScreen(
            DownloadsUiState(
                listOf(
                    row(
                        id = "a",
                        status = DownloadTaskStatus.QUEUED,
                        downloaded = 0,
                        total = 1_024,
                        progressPercent = 0,
                    ),
                ),
            ),
            onPauseAll = { pauseAllCount++ },
        )

        composeRule.onNodeWithTag("downloads-pause-all").performClick()

        assertEquals(1, pauseAllCount)
    }

    private fun setScreen(
        uiState: DownloadsUiState,
        onAction: (DownloadAction, String) -> Unit = { _, _ -> },
        onPauseAll: () -> Unit = {},
    ) {
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                DownloadsScreen(
                    uiState = uiState,
                    onNavigateBack = {},
                    onAction = onAction,
                    onPauseAll = onPauseAll,
                )
            }
        }
    }

    private fun row(
        id: String,
        status: DownloadTaskStatus,
        downloaded: Long,
        total: Long?,
        progressPercent: Int,
        planType: DownloadPlanType = DownloadPlanType.DIRECT,
        destinationKind: DownloadDestinationKind = DownloadDestinationKind.APP_PRIVATE,
        requiresLinkRefresh: Boolean = false,
        failureReason: DownloadFailureReason? = null,
    ): DownloadRowUiState = DownloadRowUiState(
        id = id,
        displayName = "$id.mp4",
        status = status,
        planType = planType,
        destinationKind = destinationKind,
        downloadedBytes = downloaded,
        totalBytes = total,
        progressPercent = progressPercent,
        requiresLinkRefresh = requiresLinkRefresh,
        failureReason = failureReason,
    )
}
