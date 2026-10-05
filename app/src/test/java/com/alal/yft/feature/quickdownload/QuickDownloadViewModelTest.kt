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
import com.alal.yft.detection.VideoPlaybackSupport
import com.alal.yft.download.EnqueueResult
import com.alal.yft.download.PreviewDownloadStarter
import com.alal.yft.download.policy.NetworkSnapshot
import com.alal.yft.download.policy.NetworkStatusSource
import com.alal.yft.feature.detectedmedia.DetectedMediaStore
import com.alal.yft.feature.preview.PreviewDownloadStatus
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.MIB
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.video
import com.alal.yft.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
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
        // P9: 720p is the unset preference; an explicitly saved Highest still stays.
        assertEquals(QualityPreference.UP_TO_720P, state.defaultQuality)
        assertEquals("720p · HD", state.selectedOption?.title)
        assertEquals(
            "1080p · Full HD",
            viewModel(DownloadPreferences(defaultQuality = QualityPreference.HIGHEST))
                .uiState.value.selectedOption?.title,
        )
        assertEquals("Ocean waves", state.header?.title)
        assertEquals("youtube.com", state.header?.source)
        assertFalse(state.loading)
        // Every file stated its type, size and height: nothing was requested to show them.
        assertTrue(resolver.requested.isEmpty())
        assertEquals(
            "480p",
            viewModel(DownloadPreferences(defaultQuality = QualityPreference.UP_TO_480P))
                .uiState.value.selectedOption?.title,
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
    fun aFourKRowThePhoneMayNotPlayIsWarnedAboutAndStillDownloads() = runTest {
        // P6: the phone's decoder list decides; the file itself is fine, so it stays offered.
        val fourK = video(2160, 300 * MIB, merged = true).copy(
            mediaUrl = "https://media.example.test/video-2160.webm",
            mimeType = "video/webm",
            codecs = listOf("vp9"),
        )
        select(listOf(fourK) + QuickDownloadFixtures.youtube())
        val viewModel = viewModel(playback = { variant -> "vp9" !in variant.codecs })

        val rows = viewModel.uiState.value.choices!!.video
        assertEquals("2160p · 4K", rows.first().title)
        assertEquals(listOf(QuickDownloadChoices.MAY_NOT_PLAY), rows.first().chips)
        assertTrue(rows.drop(1).all { it.chips.isEmpty() })
        assertEquals("720p · HD", viewModel.uiState.value.selectedOption?.title)
        viewModel.select(rows.first().id)
        viewModel.download()
        advanceUntilIdle()
        assertEquals("2160p", starter.variants.single().label)
        assertEquals(listOf("vp9"), starter.variants.single().codecs)
    }

    @Test
    fun theMp3RowQueuesTheM4aConvertedToMp3() = runTest {
        select(QuickDownloadFixtures.youtube())
        val viewModel = viewModel()

        val mp3 = viewModel.uiState.value.choices!!.audio.single { it.title == "MP3 · 192 kbps" }
        viewModel.select(mp3.id)
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

        val m4a = viewModel.uiState.value.choices!!.audio.first()
        assertEquals("The video's own sound", m4a.detail)
        viewModel.select(m4a.id)
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
    fun aGenericVideoWithoutAHeightIsMeasuredBeforeFormatsAreShown() = runTest {
        val hd = video(null, 25 * MIB, label = null, videoId = null, index = 1)
        resolver.heights[hd.mediaUrl] = 720
        select(listOf(hd))

        val state = viewModel().uiState.value

        assertEquals(listOf(hd), resolver.requested)
        val hdRow = state.choices!!.video.single()
        assertEquals("720p · HD", hdRow.title)
        assertEquals("720p · MP4", hdRow.detail)
        assertEquals("25 MB", hdRow.size)
        assertEquals(hdRow.id, state.selectedId)
    }

    @Test
    fun aFailedLookupSaysWhyAndTryAgainReadsTheFormatsAgain() = runTest {
        val hd = video(null, 25 * MIB, label = null, videoId = null, index = 1)
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
    fun anyRowOfEitherSectionCanBeChosenAndAnUnknownIdIsIgnored() = runTest {
        select(QuickDownloadFixtures.youtube())
        val viewModel = viewModel()
        val choices = viewModel.uiState.value.choices!!
        val lowest = choices.video.last()
        val mp3 = choices.audio.last()

        viewModel.select(lowest.id)
        assertSame(lowest, viewModel.uiState.value.selectedOption)
        assertEquals("360p", lowest.title)
        viewModel.select("missing")
        assertSame(lowest, viewModel.uiState.value.selectedOption)
        viewModel.select(mp3.id)
        assertSame(mp3, viewModel.uiState.value.selectedOption)
        assertEquals("MP3 · 128 kbps", mp3.title)
    }

    @Test
    fun detailsHandsTheChosenFormatsFileToDownloadAs() = runTest {
        val candidates = QuickDownloadFixtures.youtube()
        select(candidates)
        val viewModel = viewModel()

        viewModel.select(viewModel.uiState.value.choices!!.video.single { it.title == "480p" }.id)

        assertTrue(viewModel.openDetails())
        assertSame(candidates[2], selection.selection.value)
    }

    @Test
    fun mobileDataAsksFirstAndWifiOnlyWaits() = runTest {
        select(QuickDownloadFixtures.youtube())
        val asking = viewModel(DownloadPreferences(confirmOnMeteredNetwork = true), MOBILE)

        val choices = asking.uiState.value.choices!!
        val m4a = choices.audio.first()
        asking.select(m4a.id)
        asking.download()
        advanceUntilIdle()
        assertEquals(PreviewDownloadStatus.ConfirmMetered, asking.uiState.value.downloadStatus)
        assertTrue(starter.variants.isEmpty())
        asking.select(choices.video.last().id)
        assertEquals(m4a.id, asking.uiState.value.selectedId)

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
            PreviewDownloadStatus.Rejected("This quality is not available now — choose another"),
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

    @Test
    fun everyStatedRowIsVisibleBeforeSizeChecksFinishAndOnlyTwoRunAtOnce() = runTest {
        val candidates = listOf(1080, 720, 480, 360).map {
            video(it, videoId = "facebook:fixture")
        }
        val gates = candidates.associate { it.mediaUrl to CompletableDeferred<Unit>() }
        var active = 0
        var maximum = 0
        resolver.beforeResolve = { candidate ->
            active++
            maximum = maxOf(maximum, active)
            try { gates.getValue(candidate.mediaUrl).await() } finally { active-- }
        }
        candidates.forEach { resolver.sizes[it.mediaUrl] = 5 * MIB }
        select(candidates)
        val viewModel = viewModel()
        val first = viewModel.uiState.value.choices!!
        assertEquals(listOf(1080, 720, 480, 360), first.video.map { it.rankHeight })
        assertEquals(2, resolver.requested.size)
        assertFalse(viewModel.uiState.value.loading)
        val selected = viewModel.uiState.value.selectedId
        gates.values.forEach { it.complete(Unit) }
        advanceUntilIdle()
        val after = viewModel.uiState.value.choices!!
        assertEquals(first.options.map { it.id }, after.options.map { it.id })
        assertEquals(first.options.map { it.title }, after.options.map { it.title })
        assertEquals(selected, viewModel.uiState.value.selectedId)
        assertTrue(after.video.all { it.size == "5 MB" })
        assertEquals(2, maximum)
    }

    @Test
    fun aFailedSizeCheckKeepsTheQualityAndADownloadOnADeadLinkQueuesNothing() = runTest {
        val candidate = video(720, videoId = "facebook:fixture")
        resolver.failure = VariantResolutionFailure.HTTP_STATUS
        resolver.failureStatus = 404
        select(listOf(candidate))
        val viewModel = viewModel()
        assertEquals("720p · HD", viewModel.uiState.value.choices!!.video.single().title)
        assertNull(viewModel.uiState.value.choices!!.video.single().size)
        assertTrue(viewModel.uiState.value.canDownload)
        viewModel.download()
        advanceUntilIdle()
        assertEquals(
            PreviewDownloadStatus.Rejected("This quality is not available now — choose another"),
            viewModel.uiState.value.downloadStatus,
        )
        assertTrue(starter.variants.isEmpty())
        // One background HEAD, one final check; a 404 is never retried.
        assertEquals(2, resolver.requested.size)
        assertEquals(1, viewModel.uiState.value.choices!!.video.size)
    }

    @Test
    fun aFinalTransientFailureRetriesButAHealthyRowIsNeverReplaced() = runTest {
        select(QuickDownloadFixtures.youtube())
        resolver.beforeResolve = {
            resolver.failure = if (resolver.requested.size == 1) VariantResolutionFailure.NETWORK
                else null
        }
        val viewModel = viewModel()
        val selected = viewModel.uiState.value.selectedId
        viewModel.download()
        advanceUntilIdle()
        assertEquals(2, resolver.requested.size)
        assertEquals(1, starter.variants.size)
        assertEquals(selected, viewModel.uiState.value.selectedId)
        assertEquals("720p", starter.variants.single().label)
    }

    private fun select(candidates: List<MediaCandidate>) {
        store.publish(QuickDownloadFixtures.PAGE, "Ocean waves", candidates)
        store.select(MediaGroups.of(candidates).single())
    }

    private fun viewModel(
        preferences: DownloadPreferences = DownloadPreferences(confirmOnMeteredNetwork = false),
        network: NetworkSnapshot = WIFI,
        playback: VideoPlaybackSupport = VideoPlaybackSupport.ANY,
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
        playback = playback,
    )

    /**
     * Resolves like the real resolver for a whole file: one variant keeping the companion and
     * the codecs, with the height its MP4 header would give ([heights]).
     */
    private class FakeResolver : VariantResolver {
        val requested = mutableListOf<MediaCandidate>()
        val heights = mutableMapOf<String, Int>()
        var failure: VariantResolutionFailure? = null
        var failureStatus: Int? = null
        val sizes = mutableMapOf<String, Long>()
        var beforeResolve: suspend (MediaCandidate) -> Unit = {}

        override suspend fun resolve(candidate: MediaCandidate): VariantResolutionResult {
            requested += candidate
            beforeResolve(candidate)
            failure?.let {
                return VariantResolutionResult.Failure(it, httpStatusCode = failureStatus)
            }
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
                sizeBytes = sizes[candidate.mediaUrl] ?: candidate.contentLengthBytes,
                sizeAccuracy = (sizes[candidate.mediaUrl] ?: candidate.contentLengthBytes)?.let {
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
