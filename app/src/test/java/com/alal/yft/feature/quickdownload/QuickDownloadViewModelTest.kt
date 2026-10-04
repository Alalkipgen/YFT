package com.alal.yft.feature.quickdownload

import com.alal.yft.core.data.preferences.DownloadPreferencesRepository
import com.alal.yft.core.media.resolver.VariantResolver
import com.alal.yft.core.media.session.PreviewSelectionStore
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
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
import com.alal.yft.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    fun theDefaultQualityPreselectsARow() = runTest {
        publish(QuickDownloadFixtures.youtube())

        assertEquals("high", viewModel().uiState.value.selectedId)
        assertEquals(
            "fast",
            viewModel(DownloadPreferences(defaultQuality = QualityPreference.UP_TO_480P))
                .uiState.value.selectedId,
        )
    }

    @Test
    fun aMergedYoutubeRowIsQueuedWithItsAudioCompanion() = runTest {
        publish(QuickDownloadFixtures.youtube())
        val viewModel = viewModel()

        viewModel.download()
        advanceUntilIdle()

        val resolved = resolver.requested.single()
        assertNotNull(resolved.audioCompanion)
        assertEquals("Ocean waves — 720p", resolved.title)
        assertNotNull(starter.variants.single().audioCompanion)
        assertEquals(
            PreviewDownloadStatus.Queued("Ocean waves.mp4"),
            viewModel.uiState.value.downloadStatus,
        )
    }

    @Test
    fun theMp3RowQueuesTheM4aConvertedToMp3() = runTest {
        publish(QuickDownloadFixtures.youtube())
        val viewModel = viewModel()

        viewModel.select("mp3")
        viewModel.download()
        advanceUntilIdle()

        assertEquals("Ocean waves — Audio 128 kbps", resolver.requested.single().title)
        val queued = starter.variants.single()
        assertEquals(Mp3Conversion(192, "direct-0"), queued.mp3)
        assertEquals("audio/mpeg", queued.mimeType)
        assertEquals("https://media.example.test/audio-128.m4a", queued.playbackUrl)
    }

    @Test
    fun mobileDataAsksFirstAndWifiOnlyWaits() = runTest {
        publish(QuickDownloadFixtures.youtube())
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
        assertEquals("Ocean waves — Audio 128 kbps", resolver.requested.single().title)

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
    fun aResolverFailureIsShownAndNothingIsQueued() = runTest {
        publish(QuickDownloadFixtures.youtube())
        resolver.failure = VariantResolutionFailure.EXPIRED_URL
        val viewModel = viewModel()

        viewModel.download()
        advanceUntilIdle()

        assertEquals(
            PreviewDownloadStatus.Rejected("This link has expired. Paste it on Home again."),
            viewModel.uiState.value.downloadStatus,
        )
        assertTrue(starter.variants.isEmpty())
    }

    @Test
    fun moreFormatsOpensTheListOrTheSingleFilesDownloadAs() = runTest {
        publish(QuickDownloadFixtures.youtube())
        assertEquals(MoreFormatsTarget.FOUND_LIST, viewModel().moreFormats())
        assertNull(selection.selection.value)

        val single = QuickDownloadFixtures.video("720p")
        publish(listOf(single))
        assertEquals(MoreFormatsTarget.DOWNLOAD_AS, viewModel().moreFormats())
        assertSame(single, selection.selection.value)
    }

    @Test
    fun aStoreWithoutOneVideoShowsNoRows() = runTest {
        val viewModel = viewModel()

        assertNull(viewModel.uiState.value.choices)
        viewModel.download()
        assertEquals(PreviewDownloadStatus.Idle, viewModel.uiState.value.downloadStatus)
    }

    private fun publish(candidates: List<MediaCandidate>) {
        store.publish(QuickDownloadFixtures.PAGE, "Ocean waves", candidates)
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

    /** Resolves like the real resolver for a whole file: one variant keeping the companion. */
    private class FakeResolver : VariantResolver {
        val requested = mutableListOf<MediaCandidate>()
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

        override suspend fun enqueue(asset: MediaAsset, variant: MediaVariant): EnqueueResult {
            variants += variant
            return EnqueueResult.Started("task-${variants.size}", "Ocean waves.mp4")
        }
    }

    private companion object {
        val WIFI = NetworkSnapshot(connected = true, validated = true, unmetered = true)
        val MOBILE = NetworkSnapshot(connected = true, validated = true, unmetered = false)
    }
}
