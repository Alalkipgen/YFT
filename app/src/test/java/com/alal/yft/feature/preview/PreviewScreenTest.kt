package com.alal.yft.feature.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaSizeAccuracy
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.VariantResolutionFailure
import com.alal.yft.core.model.media.VariantSupport
import com.alal.yft.ui.theme.YftTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
class PreviewScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readyStateShowsQualityRowsDetailsOnRequestAndSwitchesToSeparateAudio() {
        val video = variant(
            id = "video-720",
            trackType = MediaTrackType.VIDEO,
            label = "720p stream",
            width = 1280,
            height = 720,
            fps = 29.97,
            bitrate = 800_000,
            size = 1_000_000,
            sizeAccuracy = MediaSizeAccuracy.ESTIMATED,
        )
        val audio = variant(
            id = "audio-en",
            trackType = MediaTrackType.AUDIO,
            label = "English",
            bitrate = 128_000,
            size = null,
            sizeAccuracy = null,
            language = "en",
        )
        var state by mutableStateOf(
            PreviewUiState.Ready(
                asset = asset(listOf(video, audio)),
                selectedTab = PreviewTab.VIDEO,
                selectedVariantId = video.id,
            ),
        )
        setScreen(
            uiState = { state },
            onTabSelected = { tab ->
                val selected = state.asset.variants.first { it.belongsTo(tab) && it.isPreviewable }
                state = state.copy(selectedTab = tab, selectedVariantId = selected.id)
            },
        )

        composeRule.onNodeWithTag("preview-player").assertIsDisplayed()
        composeRule.onNodeWithText("Download as").assertIsDisplayed()
        composeRule.onNodeWithText("Fixture asset").assertIsDisplayed()
        composeRule.onNodeWithText("page.example.test · Video").assertIsDisplayed()
        composeRule.onNodeWithText("720p · HD").assertIsDisplayed()
        composeRule.onNodeWithText("~977 KB").assertIsDisplayed()
        composeRule.onNodeWithText("Download · ~977 KB").assertIsDisplayed()
        composeRule.onNodeWithText("Saves to Download/YFT").assertIsDisplayed()
        composeRule.onAllNodesWithTag("preview-details").assertCountEquals(0)

        composeRule.onNodeWithContentDescription("Show details").performClick()

        composeRule.onNodeWithTag("preview-details").assertIsDisplayed()
        composeRule.onNodeWithText("Resolution: 1280 × 720", substring = true)
            .fetchSemanticsNode()
        composeRule.onNodeWithText("FPS: 29.97", substring = true).fetchSemanticsNode()
        composeRule.onNodeWithText("Estimated size: ~977 KB", substring = true)
            .fetchSemanticsNode()

        composeRule.onNodeWithTag("preview-tab-audio").performClick()

        composeRule.onNodeWithText("128 kbps · English").assertIsDisplayed()
        composeRule.onNodeWithText("page.example.test · Audio").assertIsDisplayed()
        composeRule.onNodeWithText("Language: en", substring = true).fetchSemanticsNode()
        composeRule.onNodeWithText("Size: Unknown", substring = true).fetchSemanticsNode()
        composeRule.onNodeWithTag("preview-download").assertIsEnabled()

        composeRule.onNodeWithContentDescription("Hide details").performClick()
        composeRule.onAllNodesWithTag("preview-details").assertCountEquals(0)
    }

    @Test
    fun unknownMetadataStaysExplicitlyUnknown() {
        val unknown = variant(
            id = "unknown",
            trackType = MediaTrackType.AUDIO_VIDEO,
            label = null,
            width = null,
            height = null,
            fps = null,
            bitrate = null,
            size = null,
            sizeAccuracy = null,
        ).copy(codecs = emptyList(), container = null, mimeType = null)
        setScreen(
            uiState = {
                PreviewUiState.Ready(
                    asset = asset(listOf(unknown)),
                    selectedTab = PreviewTab.VIDEO,
                    selectedVariantId = unknown.id,
                )
            },
        )

        composeRule.onNodeWithText("Quality unknown").assertIsDisplayed()
        composeRule.onNodeWithTag("preview-download").assertIsDisplayed()
        composeRule.onNodeWithText("Download").assertIsDisplayed()
        composeRule.onNodeWithTag("preview-details-toggle").performClick()

        listOf(
            "Resolution: Unknown",
            "FPS: Unknown",
        ).forEach { expected ->
            composeRule.onAllNodesWithText(expected, substring = true).assertCountEquals(1)
        }
        listOf(
            "Codec: Unknown",
            "Bitrate: Unknown",
            "Size: Unknown",
        ).forEach { expected ->
            assertTrue(unknown.metadataLabel().contains(expected))
        }
    }

    @Test
    fun qualitiesRunHighestFirstWithSizesAndUnsupportedOnesCannotBeChosen() {
        val p360 = variant("p360", MediaTrackType.AUDIO_VIDEO, "360p", height = 360)
            .copy(sizeBytes = 31L * MIB, sizeAccuracy = MediaSizeAccuracy.EXACT)
        val p1080 = variant("p1080", MediaTrackType.AUDIO_VIDEO, "1080p", height = 1_080)
            .copy(sizeBytes = 186L * MIB, sizeAccuracy = MediaSizeAccuracy.EXACT)
        val p720 = variant("p720", MediaTrackType.AUDIO_VIDEO, "720p", height = 720)
            .copy(sizeBytes = 96L * MIB, sizeAccuracy = MediaSizeAccuracy.ESTIMATED)
        val p480 = variant("p480", MediaTrackType.AUDIO_VIDEO, "480p", height = 480)
            .copy(support = VariantSupport.UNSUPPORTED_CODEC)
        val chosen = mutableListOf<String>()
        setScreen(
            uiState = {
                PreviewUiState.Ready(
                    asset = asset(listOf(p360, p1080, p720, p480)),
                    selectedTab = PreviewTab.VIDEO,
                    selectedVariantId = p720.id,
                )
            },
            onVariantSelected = { chosen += it },
        )

        val tops = listOf("p1080", "p720", "p480", "p360").map { id ->
            composeRule.onNodeWithTag("preview-variant-$id").fetchSemanticsNode().boundsInRoot.top
        }
        assertEquals(tops.sorted(), tops)
        composeRule.onNodeWithText("1080p · Full HD").assertIsDisplayed()
        composeRule.onNodeWithText("186 MB").assertIsDisplayed()
        composeRule.onNodeWithText("~96 MB").assertIsDisplayed()
        composeRule.onNodeWithText("31 MB").assertIsDisplayed()
        composeRule.onNodeWithText("360p · Data saver").assertIsDisplayed()
        composeRule.onNodeWithText("Download · ~96 MB").assertIsDisplayed()
        composeRule.onNodeWithTag("preview-variant-p720").assertIsSelected()
        composeRule.onNodeWithText("Unsupported codec").assertIsDisplayed()
        composeRule.onNodeWithTag("preview-variant-p480").assertIsNotEnabled().performClick()

        composeRule.onNodeWithTag("preview-variant-p1080").performClick()

        assertEquals(listOf("p1080"), chosen)
    }

    @Test
    fun wifiOnlyRowShowsTheSettingAndSaveLocationFollowsStorage() {
        val video = variant("video", MediaTrackType.AUDIO_VIDEO, "720p", height = 720)
        val changes = mutableListOf<Boolean>()
        setScreen(
            uiState = {
                PreviewUiState.Ready(
                    asset = asset(listOf(video)),
                    selectedTab = PreviewTab.VIDEO,
                    selectedVariantId = video.id,
                )
            },
            options = PreviewDownloadOptions(wifiOnly = true, savesToSharedDownloads = false),
            onWifiOnlyChange = { changes += it },
        )

        composeRule.onNodeWithTag("preview-wifi-only").assertIsOn().performClick()

        assertEquals(listOf(false), changes)
        composeRule.onNodeWithText("Saves to app storage").assertIsDisplayed()
    }

    @Test
    fun audioTabIsOffWithoutAudioAndAQueuedDownloadLinksToDownloads() {
        val video = variant("video", MediaTrackType.AUDIO_VIDEO, "720p", height = 720)
        var openedDownloads = 0
        setScreen(
            uiState = {
                PreviewUiState.Ready(
                    asset = asset(listOf(video)),
                    selectedTab = PreviewTab.VIDEO,
                    selectedVariantId = video.id,
                    downloadStatus = PreviewDownloadStatus.Queued("Fixture asset.mp4"),
                )
            },
            onOpenDownloads = { openedDownloads += 1 },
        )

        composeRule.onNodeWithTag("preview-tab-audio").assertIsNotEnabled()
        composeRule.onNodeWithTag("preview-tab-video").assertIsSelected()
        composeRule.onNodeWithText("page.example.test · Video + audio").assertIsDisplayed()
        composeRule.onAllNodesWithTag("navigate-back").assertCountEquals(0)
        composeRule.onNodeWithTag("preview-open-downloads").performScrollTo().performClick()

        assertEquals(1, openedDownloads)
    }

    @Test
    fun downloadActionReportsQueuedAndRejectedStatesHonestly() {
        val video = variant(
            id = "video-720",
            trackType = MediaTrackType.VIDEO,
            label = "720p stream",
            width = 1280,
            height = 720,
            fps = 30.0,
            bitrate = 800_000,
            size = 1_000_000,
            sizeAccuracy = MediaSizeAccuracy.EXACT,
        )
        var state by mutableStateOf(
            PreviewUiState.Ready(
                asset = asset(listOf(video)),
                selectedTab = PreviewTab.VIDEO,
                selectedVariantId = video.id,
            ),
        )
        var downloads = 0
        setScreen(
            uiState = { state },
            onDownload = {
                downloads += 1
                state = state.copy(downloadStatus = PreviewDownloadStatus.Enqueuing)
            },
        )

        composeRule.onAllNodesWithTag("preview-download-status").assertCountEquals(0)
        composeRule.onAllNodesWithTag("preview-open-downloads").assertCountEquals(0)
        composeRule.onNodeWithTag("preview-download").assertIsEnabled().performClick()

        assertEquals(1, downloads)
        composeRule.onNodeWithTag("preview-download").assertIsNotEnabled()
        composeRule.onNodeWithText("Queueing download", substring = true).assertIsDisplayed()

        state = state.copy(
            downloadStatus = PreviewDownloadStatus.Queued("Fixture asset 720p stream.mp4"),
        )

        composeRule.onNodeWithTag("preview-download-status").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Fixture asset 720p stream.mp4", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("preview-open-downloads").performScrollTo().assertIsDisplayed()

        state = state.copy(
            downloadStatus = PreviewDownloadStatus.Rejected("Download storage is unavailable."),
        )

        composeRule.onNodeWithText("Download storage is unavailable.").performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("preview-download").assertIsEnabled()
        composeRule.onAllNodesWithTag("preview-open-downloads").assertCountEquals(0)
    }

    @Test
    fun mobileDataPromptConfirmsOrDismissesAndWifiOnlyWaitIsExplained() {
        val video = variant(
            id = "video-720",
            trackType = MediaTrackType.VIDEO,
            label = "720p stream",
            height = 720,
        )
        var state by mutableStateOf(
            PreviewUiState.Ready(
                asset = asset(listOf(video)),
                selectedTab = PreviewTab.VIDEO,
                selectedVariantId = video.id,
                downloadStatus = PreviewDownloadStatus.ConfirmMetered,
            ),
        )
        var confirmed = 0
        var dismissed = 0
        setScreen(
            uiState = { state },
            onConfirmMetered = {
                confirmed += 1
                state = state.copy(downloadStatus = PreviewDownloadStatus.Enqueuing)
            },
            onDismissMetered = {
                dismissed += 1
                state = state.copy(downloadStatus = PreviewDownloadStatus.Idle)
            },
        )

        composeRule.onNodeWithText("Download on mobile data?").assertIsDisplayed()
        composeRule.onNodeWithTag("metered-dismiss").performClick()

        assertEquals(1, dismissed)
        assertEquals(0, confirmed)
        composeRule.onAllNodesWithTag("metered-confirm").assertCountEquals(0)
        composeRule.onNodeWithTag("preview-download").assertIsEnabled()

        state = state.copy(downloadStatus = PreviewDownloadStatus.ConfirmMetered)
        composeRule.onNodeWithTag("metered-confirm").performClick()

        assertEquals(1, confirmed)
        composeRule.onAllNodesWithTag("metered-confirm").assertCountEquals(0)
        composeRule.onNodeWithTag("preview-download").assertIsNotEnabled()

        state = state.copy(
            downloadStatus = PreviewDownloadStatus.Queued(
                fileName = "Fixture asset.mp4",
                waitingForUnmetered = true,
            ),
        )
        composeRule.onNodeWithText("starts when Wi-Fi is available", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun drmErrorIsClearAndNotRetryableAndCloseLeavesTheSheet() {
        val message = "DRM-protected media cannot be previewed."
        var closed = 0
        setScreen(
            uiState = {
                PreviewUiState.Error(
                    reason = VariantResolutionFailure.DRM_PROTECTED,
                    message = message,
                    retryable = false,
                )
            },
            onNavigateBack = { closed += 1 },
        )

        composeRule.onNodeWithTag("preview-error").assertIsDisplayed()
        composeRule.onNodeWithText("Preview unavailable").assertIsDisplayed()
        composeRule.onNodeWithText(message).assertIsDisplayed()
        composeRule.onAllNodesWithTag("preview-retry").assertCountEquals(0)
        composeRule.onNodeWithTag("navigate-back").performClick()

        assertEquals(1, closed)
    }

    @Test
    fun loadingThenRetryableErrorOffersTryAgain() {
        var state: PreviewUiState by mutableStateOf(PreviewUiState.Loading)
        var retries = 0
        setScreen(uiState = { state }, onRetry = { retries += 1 })

        composeRule.onNodeWithTag("preview-loading").assertIsDisplayed()
        composeRule.onNodeWithText("Checking qualities…").assertIsDisplayed()

        state = PreviewUiState.Error(
            reason = VariantResolutionFailure.NETWORK,
            message = "The page could not be reached.",
            retryable = true,
        )
        composeRule.onNodeWithTag("preview-retry").performClick()
        composeRule.onNodeWithText("Close").assertIsDisplayed()

        assertEquals(1, retries)
    }

    @Test
    fun playerControlsPlayPauseShowTimeAndSeek() {
        var playing by mutableStateOf(false)
        var position by mutableStateOf(0L)
        var toggles = 0
        val seeks = mutableListOf<Float>()
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                PreviewPlayerControls(
                    playing = playing,
                    positionMs = position,
                    durationMs = 252_000,
                    onPlayPause = { toggles += 1 },
                    onSeek = { seeks += it },
                    modifier = Modifier.size(width = 176.dp, height = 99.dp),
                )
            }
        }

        composeRule.onNodeWithText("4:12").assertIsDisplayed()
        composeRule.onAllNodesWithTag("preview-seek").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Play preview").performClick()
        assertEquals(1, toggles)

        playing = true
        position = 12_000
        composeRule.onNodeWithText("0:12 / 4:12").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Pause preview").assertIsDisplayed()
        composeRule.onNodeWithTag("preview-seek").performSemanticsAction(
            SemanticsActions.SetProgress,
        ) { setProgress -> setProgress(0.5f) }

        assertEquals(listOf(0.5f), seeks)
    }

    private fun setScreen(
        uiState: () -> PreviewUiState,
        onNavigateBack: () -> Unit = {},
        onRetry: () -> Unit = {},
        onTabSelected: (PreviewTab) -> Unit = {},
        onVariantSelected: (String) -> Unit = {},
        onDownload: () -> Unit = {},
        onConfirmMetered: () -> Unit = {},
        onDismissMetered: () -> Unit = {},
        options: PreviewDownloadOptions = PreviewDownloadOptions(),
        onWifiOnlyChange: (Boolean) -> Unit = {},
        onOpenDownloads: () -> Unit = {},
    ) {
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                PreviewScreen(
                    uiState = uiState(),
                    onNavigateBack = onNavigateBack,
                    onRetry = onRetry,
                    onTabSelected = onTabSelected,
                    onVariantSelected = onVariantSelected,
                    playerSurface = { _, modifier ->
                        Box(modifier = modifier.testTag("fixture-player"))
                    },
                    onDownload = onDownload,
                    onConfirmMetered = onConfirmMetered,
                    onDismissMetered = onDismissMetered,
                    options = options,
                    onWifiOnlyChange = onWifiOnlyChange,
                    onOpenDownloads = onOpenDownloads,
                )
            }
        }
    }

    private fun asset(variants: List<MediaVariant>) = MediaAsset(
        sourcePageUrl = "https://page.example.test/watch",
        title = "Fixture asset",
        thumbnailUrl = null,
        durationMillis = 10_000,
        variants = variants,
        resolvedAtEpochMs = 1,
    )

    private fun variant(
        id: String,
        trackType: MediaTrackType,
        label: String?,
        width: Int? = null,
        height: Int? = null,
        fps: Double? = null,
        bitrate: Long? = null,
        size: Long? = null,
        sizeAccuracy: MediaSizeAccuracy? = null,
        language: String? = null,
    ) = MediaVariant(
        id = id,
        playbackUrl = "https://media.example.test/$id.m3u8",
        kind = MediaKind.HLS,
        trackType = trackType,
        requestContext = BrowserRequestContext(
            pageUrl = "https://page.example.test/watch",
            userAgent = "fixture-agent",
            cookie = null,
        ),
        label = label,
        container = "HLS",
        codecs = if (trackType == MediaTrackType.AUDIO) {
            listOf("mp4a.40.2")
        } else {
            listOf("avc1.4d401f")
        },
        width = width,
        height = height,
        framesPerSecond = fps,
        bitrateBitsPerSecond = bitrate,
        durationMillis = 10_000,
        sizeBytes = size,
        sizeAccuracy = sizeAccuracy,
        language = language,
    )

    private companion object {
        const val MIB = 1_024L * 1_024
    }
}
