package com.alal.yft.feature.downloads

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.download.DownloadPlanType
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.settings.DownloadLocation
import com.alal.yft.download.policy.TransferNetworkState
import com.alal.yft.feature.library.AppPrivateDownloadProvider
import com.alal.yft.feature.library.FixedMediaDetails
import com.alal.yft.feature.library.LocalMediaDetailsSource
import com.alal.yft.feature.library.MediaDetails
import com.alal.yft.feature.library.MediaDetailsSource
import com.alal.yft.ui.theme.YftTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
class DownloadsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyQueueExplainsHowToStartADownload() {
        setScreen(DownloadsUiState.Empty)

        composeRule.onNodeWithTag("downloads-empty").assertIsDisplayed()
        composeRule.onNodeWithText("No downloads yet").assertIsDisplayed()
        composeRule.onNodeWithTag("downloads-pause-all").assertIsNotEnabled()
        composeRule.onAllNodesWithTag("downloads-filter-all").assertCountEquals(0)
    }

    @Test
    fun runningTaskShowsAmountSpeedAndTimeLeftAndForwardsPause() {
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
                        bytesPerSecond = 524_288,
                    ),
                ),
            ),
            onAction = { action, id -> actions += action to id },
        )

        composeRule.onNodeWithTag("download-a").assertIsDisplayed()
        composeRule.onNodeWithText("a").assertIsDisplayed()
        composeRule.onNodeWithText("MP4").assertIsDisplayed()
        composeRule.onNodeWithText("50%").assertIsDisplayed()
        composeRule.onNodeWithText("1.5 of 3 MB · 512 KB/s · 3 s left").assertIsDisplayed()
        composeRule.onNodeWithTag("download-progress-a", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("downloads-pause-all").assertIsEnabled()

        composeRule.onNodeWithTag("download-action-pause-a").performClick()

        assertEquals(listOf(DownloadAction.PAUSE to "a"), actions)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun narrowCardDropsTheSpeedBeforeTheTimeLeft() {
        val running = row(
            id = "n",
            status = DownloadTaskStatus.RUNNING,
            downloaded = 48_234_496,
            total = 220_200_960,
            progressPercent = 22,
            bytesPerSecond = 1_887_436,
        )
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                Box(modifier = Modifier.width(300.dp)) {
                    DownloadsScreen(
                        uiState = DownloadsUiState(listOf(running)),
                        onAction = { _, _ -> },
                        onPauseAll = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("46 of 210 MB · 2 min left").assertIsDisplayed()
        composeRule.onAllNodesWithText("46 of 210 MB · 1.8 MB/s · 2 min left")
            .assertCountEquals(0)
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

        composeRule.onNodeWithTag("download-progress-indeterminate-stream", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("HLS · MP4").assertIsDisplayed()
        composeRule.onNodeWithText("Downloading · 2 KB").assertIsDisplayed()
        composeRule.onAllNodesWithTag("download-action-resume-stream").assertCountEquals(0)
    }

    @Test
    fun pausedTaskOffersResume() {
        val actions = mutableListOf<Pair<DownloadAction, String>>()
        setScreen(
            DownloadsUiState(
                listOf(
                    row(
                        id = "p",
                        status = DownloadTaskStatus.PAUSED,
                        downloaded = 63_963_136,
                        total = 100_663_296,
                        progressPercent = 63,
                    ),
                ),
            ),
            onAction = { action, id -> actions += action to id },
        )

        composeRule.onNodeWithText("61 of 96 MB · Paused").assertIsDisplayed()
        composeRule.onNodeWithTag("download-action-resume-p").performClick()

        assertEquals(listOf(DownloadAction.RESUME to "p"), actions)
    }

    @Test
    fun waitingTaskSaysWhatItWaitsForWithItsSize() {
        setScreen(
            DownloadsUiState(
                rows = listOf(
                    row(
                        id = "w",
                        status = DownloadTaskStatus.WAITING_FOR_NETWORK,
                        downloaded = 0,
                        total = 12_582_912,
                        progressPercent = 0,
                        name = "Forest Rain Sounds.m4a",
                    ),
                ),
                network = TransferNetworkState.WAITING_FOR_UNMETERED,
            ),
        )

        composeRule.onNodeWithText("Forest Rain Sounds").assertIsDisplayed()
        composeRule.onNodeWithText("M4A · 12 MB").assertIsDisplayed()
        composeRule.onNodeWithTag("download-status-w", useUnmergedTree = true)
            .assert(hasAnyChild(hasText("Waiting for Wi-Fi")))
    }

    @Test
    fun failedTaskShowsTheReasonAndRetries() {
        val actions = mutableListOf<Pair<DownloadAction, String>>()
        setScreen(
            DownloadsUiState(
                listOf(
                    row(
                        id = "f",
                        status = DownloadTaskStatus.FAILED,
                        downloaded = 1_024,
                        total = 4_096,
                        progressPercent = 25,
                        failureReason = DownloadFailureReason.EXPIRED_URL,
                    ),
                ),
            ),
            onAction = { action, id -> actions += action to id },
        )

        composeRule.onNodeWithText("Failed · Link expired").assertIsDisplayed()
        composeRule.onNodeWithTag("download-action-retry-f").performClick()

        assertEquals(listOf(DownloadAction.RETRY to "f"), actions)
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

        composeRule.onNodeWithText("Link expired").assertIsDisplayed()
        composeRule.onNodeWithTag("download-refresh-note-old", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("downloads-pause-all").assertIsNotEnabled()
        composeRule.onAllNodesWithTag("download-action-retry-old").assertCountEquals(0)

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

        composeRule.onNodeWithTag("download-status-a", useUnmergedTree = true)
            .assert(hasAnyChild(hasText("Queued")))
        composeRule.onNodeWithTag("downloads-pause-all").performClick()

        assertEquals(1, pauseAllCount)
    }

    @Test
    fun filterChipsCountTheirTasksAndNarrowTheList() {
        setScreen(
            DownloadsUiState(
                listOf(
                    row("run", DownloadTaskStatus.RUNNING, 10, 100, 10),
                    row("held", DownloadTaskStatus.PAUSED, 10, 100, 10),
                    row("next", DownloadTaskStatus.QUEUED, 0, 100, 0),
                    row("wifi", DownloadTaskStatus.WAITING_FOR_NETWORK, 0, 100, 0),
                    row(
                        id = "bad",
                        status = DownloadTaskStatus.FAILED,
                        downloaded = 0,
                        total = 100,
                        progressPercent = 0,
                        failureReason = DownloadFailureReason.NETWORK,
                    ),
                    row("done", DownloadTaskStatus.COMPLETED, 100, 100, 100),
                ),
            ),
        )

        composeRule.onNodeWithTag("downloads-filter-all").assertIsSelected()
        composeRule.onNodeWithTag("downloads-filter-active").assert(hasText("2"))
        composeRule.onNodeWithTag("downloads-filter-queued").assert(hasText("2"))
        composeRule.onNodeWithTag("downloads-filter-failed").assert(hasText("1"))

        composeRule.onNodeWithTag("downloads-filter-active").performClick()
        composeRule.onNodeWithTag("downloads-filter-active").assertIsSelected()
        composeRule.onNodeWithTag("download-run").assertIsDisplayed()
        composeRule.onNodeWithTag("download-held").assertIsDisplayed()
        composeRule.onAllNodesWithTag("download-next").assertCountEquals(0)
        composeRule.onAllNodesWithTag("download-done").assertCountEquals(0)

        composeRule.onNodeWithTag("downloads-filter-queued").performClick()
        composeRule.onNodeWithTag("download-next").assertIsDisplayed()
        composeRule.onNodeWithTag("download-wifi").assertIsDisplayed()
        composeRule.onAllNodesWithTag("download-run").assertCountEquals(0)

        composeRule.onNodeWithTag("downloads-filter-failed").performScrollTo().performClick()
        composeRule.onNodeWithTag("download-bad").assertIsDisplayed()
        composeRule.onAllNodesWithTag("download-next").assertCountEquals(0)

        composeRule.onNodeWithTag("downloads-filter-done").performScrollTo().performClick()
        composeRule.onNodeWithTag("download-done").assertIsDisplayed()
        composeRule.onAllNodesWithTag("download-bad").assertCountEquals(0)
    }

    @Test
    fun anEmptyFilterSaysSo() {
        setScreen(
            DownloadsUiState(listOf(row("done", DownloadTaskStatus.COMPLETED, 100, 100, 100))),
        )

        composeRule.onNodeWithTag("downloads-filter-active").performClick()

        composeRule.onNodeWithTag("downloads-filter-empty").assertIsDisplayed()
        composeRule.onNodeWithText("Nothing is downloading right now.").assertIsDisplayed()
    }

    @Test
    fun finishedDownloadsAreGroupedByDayAndPlay() {
        val played = mutableListOf<String>()
        setScreen(
            DownloadsUiState(
                listOf(
                    row(
                        id = "new",
                        status = DownloadTaskStatus.COMPLETED,
                        downloaded = 7_340_032,
                        total = 7_340_032,
                        progressPercent = 100,
                        name = "Ocean Waves.m4a",
                        updatedAt = 2_000,
                    ),
                    row(
                        id = "old",
                        status = DownloadTaskStatus.COMPLETED,
                        downloaded = 1_024,
                        total = 1_024,
                        progressPercent = 100,
                        updatedAt = 500,
                    ),
                ),
            ),
            onPlay = { played += it.id },
            todayStartEpochMs = 1_000,
        )

        composeRule.onNodeWithText("Completed today").assertIsDisplayed()
        composeRule.onNodeWithText("Earlier").assertIsDisplayed()
        composeRule.onNodeWithText("M4A · 7 MB").assertIsDisplayed()

        composeRule.onNodeWithTag("download-open-new")
            .assertContentDescriptionEquals("Play Ocean Waves")
            .performClick()

        assertEquals(listOf("new"), played)
    }

    @Test
    fun aFinishedVideoShowsThePictureSizeReadFromTheFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = AppPrivateDownloadProvider.uriFor(context, "Mountain Lake.mp4").toString()
        setScreen(
            DownloadsUiState(
                listOf(
                    row(
                        id = "lake",
                        status = DownloadTaskStatus.COMPLETED,
                        downloaded = 100_663_296,
                        total = 100_663_296,
                        progressPercent = 100,
                        name = "Mountain Lake.mp4",
                    ),
                ),
            ),
            mediaDetails = FixedMediaDetails(
                mapOf(uri to MediaDetails(durationMs = 252_000, width = 1280, height = 720)),
            ),
        )

        composeRule.onNodeWithTag("download-meta-lake", useUnmergedTree = true)
            .assertTextEquals("720p · 96 MB")
    }

    @Test
    fun tappingACardOpensItsMenuWithEveryControl() {
        val actions = mutableListOf<Pair<DownloadAction, String>>()
        setScreen(
            DownloadsUiState(
                listOf(row("a", DownloadTaskStatus.RUNNING, 10, 100, 10)),
            ),
            onAction = { action, id -> actions += action to id },
        )

        composeRule.onNodeWithTag("download-a").performClick()

        composeRule.onNodeWithTag("download-menu-pause-a").assertIsDisplayed()
        composeRule.onNodeWithTag("download-menu-cancel-a").performClick()

        assertEquals(listOf(DownloadAction.CANCEL to "a"), actions)
        composeRule.onAllNodesWithTag("download-menu-pause-a").assertCountEquals(0)
    }

    @Test
    fun finishedCardMenuPlaysOpensOrRemovesTheRecord() {
        val actions = mutableListOf<Pair<DownloadAction, String>>()
        val opened = mutableListOf<String>()
        setScreen(
            DownloadsUiState(listOf(row("done", DownloadTaskStatus.COMPLETED, 100, 100, 100))),
            onAction = { action, id -> actions += action to id },
            onOpen = { opened += "open:${it.id}" },
            onPlay = { opened += "play:${it.id}" },
        )

        composeRule.onNodeWithTag("download-done").performClick()
        composeRule.onNodeWithTag("download-menu-play-done").performClick()
        composeRule.onNodeWithTag("download-done").performClick()
        composeRule.onNodeWithText("Open with…").performClick()
        composeRule.onNodeWithTag("download-done").performClick()
        composeRule.onNodeWithText("Remove from list").performClick()

        assertEquals(listOf("play:done", "open:done"), opened)
        assertEquals(listOf(DownloadAction.DELETE to "done"), actions)
    }

    @Test
    fun cardsOfferTheirControlsToAccessibilityServices() {
        setScreen(
            DownloadsUiState(
                listOf(row("a", DownloadTaskStatus.RUNNING, 10, 100, 10)),
            ),
        )

        val labels = composeRule.onNodeWithTag("download-a").fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
            .map { it.label }

        assertEquals(listOf("Pause", "Cancel download"), labels)
    }

    @Test
    fun storagePillShowsWhereDownloadsGoAndOpensSettings() {
        var settingsOpened = 0
        setScreen(
            DownloadsUiState(
                rows = listOf(row("a", DownloadTaskStatus.RUNNING, 10, 100, 10)),
                storage = DownloadStorageSummary(
                    location = DownloadLocation.SHARED_DOWNLOADS,
                    freeBytes = 19_541_180_006,
                ),
            ),
            onOpenSettings = { settingsOpened++ },
        )

        composeRule.onNodeWithText("Download/YFT · 18 GB free").assertIsDisplayed()
        composeRule.onNodeWithTag("downloads-storage").performClick()

        assertEquals(1, settingsOpened)
    }

    @Test
    fun networkNoticeExplainsWhyWorkWaitsAndLeadsToSettings() {
        var settingsOpened = 0
        val waiting = row(
            id = "w",
            status = DownloadTaskStatus.WAITING_FOR_NETWORK,
            downloaded = 0,
            total = 1_024,
            progressPercent = 0,
        )
        var state by mutableStateOf(
            DownloadsUiState(
                rows = listOf(waiting),
                network = TransferNetworkState.WAITING_FOR_UNMETERED,
            ),
        )
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                DownloadsScreen(
                    uiState = state,
                    onAction = { _, _ -> },
                    onPauseAll = {},
                    onOpenSettings = { settingsOpened++ },
                )
            }
        }

        composeRule.onNodeWithTag("downloads-network-banner")
            .assertIsDisplayed()
            .assert(hasText("Wi-Fi only is on", substring = true))
            .performClick()
        assertEquals(1, settingsOpened)

        state = state.copy(network = TransferNetworkState.OFFLINE)
        composeRule.onNodeWithTag("downloads-network-banner")
            .assert(hasText("No connection", substring = true))

        state = state.copy(network = TransferNetworkState.ALLOWED)
        composeRule.onAllNodesWithTag("downloads-network-banner").assertCountEquals(0)

        state = DownloadsUiState(network = TransferNetworkState.WAITING_FOR_UNMETERED)
        composeRule.onNodeWithTag("downloads-empty").assertIsDisplayed()
        composeRule.onAllNodesWithTag("downloads-network-banner").assertCountEquals(0)
    }

    private fun setScreen(
        uiState: DownloadsUiState,
        onAction: (DownloadAction, String) -> Unit = { _, _ -> },
        onPauseAll: () -> Unit = {},
        onOpen: (DownloadRowUiState) -> Unit = {},
        onPlay: (DownloadRowUiState) -> Unit = {},
        onOpenSettings: () -> Unit = {},
        todayStartEpochMs: Long = 0,
        mediaDetails: MediaDetailsSource = MediaDetailsSource.None,
    ) {
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalMediaDetailsSource provides mediaDetails) {
                    DownloadsScreen(
                        uiState = uiState,
                        onAction = onAction,
                        onPauseAll = onPauseAll,
                        onOpen = onOpen,
                        onPlay = onPlay,
                        onOpenSettings = onOpenSettings,
                        todayStartEpochMs = todayStartEpochMs,
                    )
                }
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
        name: String = "$id.mp4",
        updatedAt: Long = 1,
        bytesPerSecond: Long? = null,
    ): DownloadRowUiState = DownloadRowUiState(
        id = id,
        displayName = name,
        status = status,
        planType = planType,
        destinationKind = destinationKind,
        downloadedBytes = downloaded,
        totalBytes = total,
        progressPercent = progressPercent,
        requiresLinkRefresh = requiresLinkRefresh,
        failureReason = failureReason,
        updatedAtEpochMs = updatedAt,
        bytesPerSecond = bytesPerSecond,
    )
}
