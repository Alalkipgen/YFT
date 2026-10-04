package com.alal.yft.feature.quickdownload

import com.alal.yft.core.data.preferences.DownloadPreferencesRepository
import com.alal.yft.core.media.resolver.VariantResolver
import com.alal.yft.core.media.session.PreviewSelectionStore
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaSizeAccuracy
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.Mp3Conversion
import com.alal.yft.core.model.media.VariantResolutionFailure
import com.alal.yft.core.model.media.VariantResolutionResult
import com.alal.yft.core.model.settings.DownloadPreferences
import com.alal.yft.core.model.settings.QualityPreference
import com.alal.yft.download.EnqueueResult
import com.alal.yft.download.PreviewDownloadStarter
import com.alal.yft.download.policy.NetworkSnapshot
import com.alal.yft.download.policy.NetworkStatusSource
import com.alal.yft.feature.detectedmedia.DetectedMediaStore
import com.alal.yft.feature.preview.PreviewDownloadStatus
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.MIB
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.video
import com.alal.yft.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class QuickDownloadViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val store = DetectedMediaStore()
    private val selection = PreviewSelectionStore()
    private val resolver = FakeResolver()
    private val starter = FakeStarter()

    @Test
    fun theDefaultQualityPreselectsARowWithoutARequestForStatedFiles() = runTest {
        select(QuickDownloadFixtures.youtube())

        val state = viewModel().uiState.value
        assertEquals("high", state.selectedId)
        assertEquals("Ocean waves", state.header?.title)
        assertEquals("youtube.com", state.header?.source)
        assertFalse(state.loading)
        // Every file stated its type, size and height: nothing was requested to show them.
        assertTrue(resolver.requested.isEmpty())
        assertEquals(
            "fast",
            viewModel(DownloadPreferences(defaultQuality = QualityPreference.UP_TO_480P))
                .uiState.value.selectedId,
        )
    }

    @Test
    fun aMergedYoutubeRowIsQueuedWithItsAudioCompanionAndNamedAfterItsQuality() = runTest {
        select(QuickDownloadFixtures.youtube())
        val viewModel = viewModel()

        viewModel.download()
        advanceUntilIdle()

        val resolved = resolver.requested.single()
        assertNotNull(resolved.audioCompanion)
        val queued = starter.variants.single()
        assertNotNull(queued.audioCompanion)
        assertEquals("720p", queued.label)
        assertEquals(720, queued.height)
        assertEquals("Ocean waves", starter.assets.single().title)
        assertEquals(
            PreviewDownloadStatus.Queued("Ocean waves.mp4"),
            viewModel.uiState.value.downloadStatus,
        )
    }

    @Test
    fun theMp3RowQueuesTheM4aConvertedToMp3() = runTest {
        select(QuickDownloadFixtures.youtube())
        val viewModel = viewModel()

        viewModel.select("mp3")
        viewModel.download()
        advanceUntilIdle()

        assertEquals(
            "https://media.example.test/audio-128.m4a",
            resolver.requested.single().mediaUrl,
        )
        val queued = starter.variants.single()
        assertEquals(Mp3Conversion(192, "direct-0"), queued.mp3)
        assertEquals("audio/mpeg", queued.mimeType)
        assertEquals("https://media.example.test/audio-128.m4a", queued.playbackUrl)
    }

    @Test
    fun musicOfAnMp4OnlyVideoQueuesTheVideosSoundKeptAsM4a() = runTest {
        val file = video(360, 11 * MIB, videoId = "facebook:1")
        select(listOf(file))
        val viewModel = viewModel()

        viewModel.select("music")
        viewModel.download()
        advanceUntilIdle()

        val queued = starter.variants.single()
        assertTrue(queued.audioFromVideo)
        assertEquals(MediaTrackType.AUDIO, queued.trackType)
        assertEquals("audio/mp4", queued.mimeType)
        assertEquals(file.mediaUrl, queued.playbackUrl)
        assertNull(queued.mp3)
    }

    @Test
    fun aVideoWithoutAHeightIsLookedUpBeforeItIsShown() = runTest {
        val hd = video(null, 25 * MIB, label = "HD", videoId = "facebook:1", index = 1)
        resolver.heights[hd.mediaUrl] = 720
        select(listOf(hd))

        val state = viewModel().uiState.value

        assertEquals(listOf(hd), resolver.requested)
        val high = state.choices!!.video.single()
        assertEquals(QuickRowKind.HIGH, high.kind)
        assertEquals("720p · 25 MB", high.detail)
        assertEquals("high", state.selectedId)
    }

    @Test
    fun aFailedLookupSaysWhyAndTryAgainReadsTheFormatsAgain() = runTest {
        val hd = video(null, 25 * MIB, label = "HD", videoId = "facebook:1", index = 1)
        resolver.failure = VariantResolutionFailure.NETWORK
        select(listOf(hd))
        val viewModel = viewModel()

        assertNull(viewModel.uiState.value.choices)
        assertEquals(
            "The media could not be reached. Check the connection and try again.",
            viewModel.uiState.value.failure,
        )
        assertEquals("Ocean waves", viewModel.uiState.value.header?.title)

        resolver.failure = null
        viewModel.retry()

        assertNotNull(viewModel.uiState.value.choices)
        assertNull(viewModel.uiState.value.failure)
    }

    @Test
    fun moreFormatsOpensInsideTheSheetAndAnyFormatCanBeChosen() = runTest {
        select(QuickDownloadFixtures.youtube())
        val viewModel = viewModel()
        val choices = viewModel.uiState.value.choices!!
        val tallest = choices.more.first()

        viewModel.toggleMoreFormats()
        viewModel.select(QuickChoices.moreId(tallest))

        assertTrue(viewModel.uiState.value.moreFormatsExpanded)
        assertSame(tallest, viewModel.uiState.value.selectedOption)
        assertEquals("1080p · Full HD", tallest.title)
        viewModel.select("more:missing")
        assertSame(tallest, viewModel.uiState.value.selectedOption)
        viewModel.toggleMoreFormats()
        assertFalse(viewModel.uiState.value.moreFormatsExpanded)
    }

    @Test
    fun detailsHandsTheChosenFormatsFileToDownloadAs() = runTest {
        val candidates = QuickDownloadFixtures.youtube()
        select(candidates)
        val viewModel = viewModel()

        viewModel.select("fast")

        assertTrue(viewModel.openDetails())
        assertSame(candidates[2], selection.selection.value)
    }

    @Test
    fun mobileDataAsksFirstAndWifiOnlyWaits() = runTest {
        select(QuickDownloadFixtures.youtube())
        val asking = viewModel(DownloadPreferences(confirmOnMeteredNetwork = true), MOBILE)

        asking.select("music")
        asking.download()
        advanceUntilIdle()
        assertEquals(PreviewDownloadStatus.ConfirmMetered, asking.uiState.value.downloadStatus)
        assertTrue(starter.variants.isEmpty())
        asking.select("fast")
        assertEquals("music", asking.uiState.value.selectedId)

        asking.confirmMeteredDownload()
        advanceUntilIdle()
        assertEquals(
            "https://media.example.test/audio-128.m4a",
            resolver.requested.single().mediaUrl,
        )

        val waiting = viewModel(
            DownloadPreferences(confirmOnMeteredNetwork = false, unmeteredOnly = true),
            MOBILE,
        )
        waiting.download()
        advanceUntilIdle()
        assertEquals(
            PreviewDownloadStatus.Queued("Ocean waves.mp4", waitingForUnmetered = true),
            waiting.uiState.value.downloadStatus,
        )
    }

    @Test
    fun aResolverFailureOnDownloadIsShownAndNothingIsQueued() = runTest {
        select(QuickDownloadFixtures.youtube())
        resolver.failure = VariantResolutionFailure.EXPIRED_URL
        val viewModel = viewModel()

        viewModel.download()
        advanceUntilIdle()

        assertEquals(
            PreviewDownloadStatus.Rejected("This link has expired. Open the page again."),
            viewModel.uiState.value.downloadStatus,
        )
        assertTrue(starter.variants.isEmpty())
    }

    @Test
    fun theChosenVideoWinsOverThePageAndSeveralUnchosenVideosShowNothing() = runTest {
        val one = video(720, 42 * MIB, videoId = "youtube:one", title = "One", index = 1)
        val two = video(720, 40 * MIB, videoId = "youtube:two", title = "Two", index = 2)
        store.publish(QuickDownloadFixtures.PAGE, "Page", listOf(one, two))

        val nothing = viewModel()
        assertNull(nothing.uiState.value.header)
        nothing.download()
        assertEquals(PreviewDownloadStatus.Idle, nothing.uiState.value.downloadStatus)

        store.select(MediaGroups.of(listOf(one, two)).last())
        assertEquals("Two", viewModel().uiState.value.header?.title)
    }

    private fun select(candidates: List<MediaCandidate>) {
        store.publish(QuickDownloadFixtures.PAGE, "Ocean waves", candidates)
        store.select(MediaGroups.of(candidates).single())
    }

    private fun viewModel(
        preferences: DownloadPreferences = DownloadPreferences(confirmOnMeteredNetwork = false),
        network: NetworkSnapshot = WIFI,
    ) = QuickDownloadViewModel(
        store = store,
        selectionStore = selection,
        resolver = resolver,
        downloadStarter = starter,
        downloadPreferences = object : DownloadPreferencesRepository {
            override val preferences: Flow<DownloadPreferences> = MutableStateFlow(preferences)
            override suspend fun update(
                transform: (DownloadPreferences) -> DownloadPreferences,
            ) = Unit
        },
        network = object : NetworkStatusSource {
            override val snapshot: StateFlow<NetworkSnapshot> = MutableStateFlow(network)
        },
    )

    /**
     * Resolves like the real resolver for a whole file: one variant keeping the companion and
     * the codecs, with the height its MP4 header would give ([heights]).
     */
    private class FakeResolver : VariantResolver {
        val requested = mutableListOf<MediaCandidate>()
        val heights = mutableMapOf<String, Int>()
        var failure: VariantResolutionFailure? = null

        override suspend fun resolve(candidate: MediaCandidate): VariantResolutionResult {
            requested += candidate
            failure?.let { return VariantResolutionResult.Failure(it) }
            val variant = MediaVariant(
                id = "direct-0",
                playbackUrl = candidate.mediaUrl,
                kind = MediaKind.DIRECT,
                trackType = if (candidate.mimeType?.startsWith("audio/") == true) {
                    MediaTrackType.AUDIO
                } else {
                    MediaTrackType.AUDIO_VIDEO
                },
                requestContext = BrowserRequestContext(candidate.pageUrl, null, null),
                mimeType = candidate.mimeType,
                codecs = candidate.codecs,
                height = heights[candidate.mediaUrl],
                sizeBytes = candidate.contentLengthBytes,
                sizeAccuracy = candidate.contentLengthBytes?.let {
                    MediaSizeAccuracy.EXACT
                },
                audioCompanion = candidate.audioCompanion,
            )
            return VariantResolutionResult.Success(
                MediaAsset(
                    sourcePageUrl = candidate.pageUrl,
                    title = candidate.title,
                    thumbnailUrl = null,
                    durationMillis = candidate.durationMillis,
                    variants = listOf(variant),
                    resolvedAtEpochMs = 1,
                ),
            )
        }
    }

    private class FakeStarter : PreviewDownloadStarter {
        val variants = mutableListOf<MediaVariant>()
        val assets = mutableListOf<MediaAsset>()

        override suspend fun enqueue(asset: MediaAsset, variant: MediaVariant): EnqueueResult {
            assets += asset
            variants += variant
            return EnqueueResult.Started("task-${variants.size}", "Ocean waves.mp4")
        }
    }

    private companion object {
        val WIFI = NetworkSnapshot(connected = true, validated = true, unmetered = true)
        val MOBILE = NetworkSnapshot(connected = true, validated = true, unmetered = false)
    }
}
