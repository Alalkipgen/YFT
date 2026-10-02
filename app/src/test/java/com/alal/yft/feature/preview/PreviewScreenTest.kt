package com.alal.yft.feature.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaSizeAccuracy
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.VariantResolutionFailure
import com.alal.yft.ui.theme.YftTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PreviewScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readyStateShowsHonestMetadataAndSwitchesToSeparateAudio() {
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
        val mutableState = mutableStateOf(
            PreviewUiState.Ready(
                asset = asset(listOf(video, audio)),
                selectedTab = PreviewTab.VIDEO,
                selectedVariantId = video.id,
            ),
        )
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                PreviewScreen(
                    uiState = mutableState.value,
                    onNavigateBack = {},
                    onRetry = {},
                    onTabSelected = { tab ->
                        val selected = mutableState.value.asset.variants.first {
                            it.belongsTo(tab) && it.isPreviewable
                        }
                        mutableState.value = mutableState.value.copy(
                            selectedTab = tab,
                            selectedVariantId = selected.id,
                        )
                    },
                    onVariantSelected = {},
                    playerSurface = { _, modifier ->
                        Box(modifier = modifier.testTag("fixture-player"))
                    },
                )
            }
        }

        composeRule.onNodeWithTag("preview-player").assertIsDisplayed()
        composeRule.onNodeWithText("Resolution: 1280 × 720", substring = true)
            .fetchSemanticsNode()
        composeRule.onNodeWithText("FPS: 29.97", substring = true).fetchSemanticsNode()
        composeRule.onNodeWithText("Estimated size:", substring = true).fetchSemanticsNode()

        composeRule.onNodeWithTag("preview-tab-audio").performClick()

        composeRule.onNodeWithText("English").assertIsDisplayed()
        composeRule.onNodeWithText("Language: en", substring = true).fetchSemanticsNode()
        composeRule.onNodeWithText("Size: Unknown", substring = true).fetchSemanticsNode()
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

        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                PreviewScreen(
                    uiState = PreviewUiState.Ready(
                        asset = asset(listOf(unknown)),
                        selectedTab = PreviewTab.VIDEO,
                        selectedVariantId = unknown.id,
                    ),
                    onNavigateBack = {},
                    onRetry = {},
                    onTabSelected = {},
                    onVariantSelected = {},
                    playerSurface = { _, modifier -> Box(modifier) },
                )
            }
        }

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
        val mutableState = mutableStateOf(
            PreviewUiState.Ready(
                asset = asset(listOf(video)),
                selectedTab = PreviewTab.VIDEO,
                selectedVariantId = video.id,
            ),
        )
        var downloads = 0
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                PreviewScreen(
                    uiState = mutableState.value,
                    onNavigateBack = {},
                    onRetry = {},
                    onTabSelected = {},
                    onVariantSelected = {},
                    playerSurface = { _, modifier -> Box(modifier) },
                    onDownload = {
                        downloads += 1
                        mutableState.value = mutableState.value.copy(
                            downloadStatus = PreviewDownloadStatus.Enqueuing,
                        )
                    },
                )
            }
        }

        composeRule.onAllNodesWithTag("preview-download-status").assertCountEquals(0)
        composeRule.onNodeWithTag("preview-download").assertIsEnabled().performClick()

        assertTrue(downloads == 1)
        composeRule.onNodeWithTag("preview-download").assertIsNotEnabled()
        composeRule.onNodeWithText("Queueing download", substring = true).assertIsDisplayed()

        mutableState.value = mutableState.value.copy(
            downloadStatus = PreviewDownloadStatus.Queued("Fixture asset 720p stream.mp4"),
        )

        composeRule.onNodeWithTag("preview-download-status").assertIsDisplayed()
        composeRule.onNodeWithText("Fixture asset 720p stream.mp4", substring = true)
            .assertIsDisplayed()

        mutableState.value = mutableState.value.copy(
            downloadStatus = PreviewDownloadStatus.Rejected("Download storage is unavailable."),
        )

        composeRule.onNodeWithText("Download storage is unavailable.").assertIsDisplayed()
        composeRule.onNodeWithTag("preview-download").assertIsEnabled()
    }

    @Test
    fun drmErrorIsClearAndNotRetryable() {
        val message = "DRM-protected media cannot be previewed."
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                PreviewScreen(
                    uiState = PreviewUiState.Error(
                        reason = VariantResolutionFailure.DRM_PROTECTED,
                        message = message,
                        retryable = false,
                    ),
                    onNavigateBack = {},
                    onRetry = {},
                    onTabSelected = {},
                    onVariantSelected = {},
                    playerSurface = { _, modifier -> Box(modifier) },
                )
            }
        }

        composeRule.onNodeWithTag("preview-error").assertIsDisplayed()
        composeRule.onNodeWithText(message).assertIsDisplayed()
        composeRule.onAllNodesWithTag("preview-retry").assertCountEquals(0)
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
}