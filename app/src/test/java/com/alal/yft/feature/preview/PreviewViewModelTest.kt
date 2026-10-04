package com.alal.yft.feature.preview

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.core.media.player.MediaPlayerFactory
import com.alal.yft.core.media.player.PreviewSourceFactory
import com.alal.yft.core.media.resolver.VariantResolver
import com.alal.yft.core.media.session.PreviewSelectionStore
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.VariantResolutionFailure
import com.alal.yft.core.model.media.VariantResolutionResult
import com.alal.yft.download.EnqueueResult
import com.alal.yft.download.PreviewDownloadStarter
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.testing.MainDispatcherRule
import com.alal.yft.core.data.preferences.DownloadPreferencesRepository
import com.alal.yft.core.model.settings.DownloadPreferences
import com.alal.yft.core.model.settings.QualityPreference
import com.alal.yft.download.policy.NetworkSnapshot
import com.alal.yft.download.policy.NetworkStatusSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PreviewViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun resolvesSelectionAndSwitchesBetweenVideoAndAudioTracks() = runTest {
        val store = PreviewSelectionStore().apply { select(candidate()) }
        val video = variant("video", MediaTrackType.VIDEO)
        val audio = variant("audio", MediaTrackType.AUDIO)
        val resolver = FakeResolver(
            VariantResolutionResult.Success(asset(listOf(video, audio))),
        )
        val viewModel = viewModel(resolver, store)

        runCurrent()

        val ready = viewModel.uiState.value as PreviewUiState.Ready
        assertEquals(PreviewTab.VIDEO, ready.selectedTab)
        assertEquals(video.id, ready.selectedVariantId)

        viewModel.selectTab(PreviewTab.AUDIO)

        val audioReady = viewModel.uiState.value as PreviewUiState.Ready
        assertEquals(PreviewTab.AUDIO, audioReady.selectedTab)
        assertEquals(audio.id, audioReady.selectedVariantId)
    }

    @Test
    fun anM4aFileIsAlsoOfferedAsMp3ButTheOriginalStaysTheDefault() = runTest {
        val store = PreviewSelectionStore().apply { select(candidate()) }
        val m4a = variant("m4a", MediaTrackType.AUDIO).copy(
            playbackUrl = "https://media.example.test/audio.m4a",
            kind = MediaKind.DIRECT,
            mimeType = "audio/mp4",
            codecs = listOf("mp4a.40.2"),
            bitrateBitsPerSecond = 128_000,
        )
        val starter = FakeDownloadStarter()
        val resolver = FakeResolver(VariantResolutionResult.Success(asset(listOf(m4a))))
        val viewModel = viewModel(resolver, store, starter)

        runCurrent()

        val ready = viewModel.uiState.value as PreviewUiState.Ready
        assertEquals(
            // P3: 320 kbps joins 192 and 128.
            listOf("m4a", "m4a-mp3-320", "m4a-mp3-192", "m4a-mp3-128"),
            ready.asset.variants.map(MediaVariant::id),
        )
        assertEquals(PreviewTab.AUDIO, ready.selectedTab)
        assertEquals("m4a", ready.selectedVariantId)

        viewModel.selectVariant("m4a-mp3-128")
        viewModel.download()
        runCurrent()

        assertEquals(listOf("m4a-mp3-128"), starter.requested)
    }

    @Test
    fun networkFailureIsRetryableAndRetryUsesCurrentInMemorySelection() = runTest {
        val store = PreviewSelectionStore().apply { select(candidate()) }
        val resolver = FakeResolver(
            VariantResolutionResult.Failure(VariantResolutionFailure.NETWORK),
        )
        val viewModel = viewModel(resolver, store)
        runCurrent()

        val error = viewModel.uiState.value as PreviewUiState.Error
        assertTrue(error.retryable)
        assertTrue(error.message.contains("could not be reached"))

        resolver.result = VariantResolutionResult.Success(asset(listOf(variant())))
        viewModel.retry()
        runCurrent()

        assertTrue(viewModel.uiState.value is PreviewUiState.Ready)
        assertEquals(2, resolver.calls)
    }

    @Test
    fun drmFailureIsExplicitAndNotRetryable() = runTest {
        val store = PreviewSelectionStore().apply { select(candidate()) }
        val viewModel = viewModel(
            FakeResolver(
                VariantResolutionResult.Failure(
                    VariantResolutionFailure.DRM_PROTECTED,
                ),
            ),
            store,
        )

        runCurrent()

        val error = viewModel.uiState.value as PreviewUiState.Error
        assertFalse(error.retryable)
        assertEquals("DRM-protected media cannot be previewed.", error.message)
    }

    @Test
    fun downloadQueuesSelectedVariantAndIgnoresRepeatTapsWhileInFlight() = runTest {
        val store = PreviewSelectionStore().apply { select(candidate()) }
        val video = variant("video", MediaTrackType.VIDEO)
        val starter = FakeDownloadStarter(
            result = EnqueueResult.Started("task-9", "Fixture.mp4"),
        )
        val viewModel = viewModel(
            FakeResolver(VariantResolutionResult.Success(asset(listOf(video)))),
            store,
            starter,
        )
        runCurrent()

        viewModel.download()

        val enqueuing = viewModel.uiState.value as PreviewUiState.Ready
        assertEquals(PreviewDownloadStatus.Enqueuing, enqueuing.downloadStatus)
        assertFalse(enqueuing.canDownload)

        viewModel.download()
        runCurrent()

        assertEquals(1, starter.calls)
        assertEquals(listOf("video"), starter.requested)
        val queued = viewModel.uiState.value as PreviewUiState.Ready
        assertEquals(PreviewDownloadStatus.Queued("Fixture.mp4"), queued.downloadStatus)
        assertTrue(queued.canDownload)
    }

    @Test
    fun rejectedDownloadKeepsReasonAndSelectingAnotherVariantClearsIt() = runTest {
        val store = PreviewSelectionStore().apply { select(candidate()) }
        val video = variant("video", MediaTrackType.VIDEO)
        val other = variant("video-2", MediaTrackType.VIDEO)
        val starter = FakeDownloadStarter(
            result = EnqueueResult.Rejected(
                DownloadFailureReason.EXPIRED_URL,
                "This media link already expired. Reload the page and try again.",
            ),
        )
        val viewModel = viewModel(
            FakeResolver(VariantResolutionResult.Success(asset(listOf(video, other)))),
            store,
            starter,
        )
        runCurrent()

        viewModel.download()
        runCurrent()

        val rejected = viewModel.uiState.value as PreviewUiState.Ready
        assertEquals(
            PreviewDownloadStatus.Rejected(
                "This media link already expired. Reload the page and try again.",
            ),
            rejected.downloadStatus,
        )

        viewModel.selectVariant("video-2")

        val cleared = viewModel.uiState.value as PreviewUiState.Ready
        assertEquals(PreviewDownloadStatus.Idle, cleared.downloadStatus)
    }

    @Test
    fun unexpectedEnqueueFailureIsReportedWithoutCrashing() = runTest {
        val store = PreviewSelectionStore().apply { select(candidate()) }
        val video = variant("video", MediaTrackType.VIDEO)
        val starter = FakeDownloadStarter(failure = IllegalStateException("boom"))
        val viewModel = viewModel(
            FakeResolver(VariantResolutionResult.Success(asset(listOf(video)))),
            store,
            starter,
        )
        runCurrent()

        viewModel.download()
        runCurrent()

        val ready = viewModel.uiState.value as PreviewUiState.Ready
        assertEquals(
            PreviewDownloadStatus.Rejected("The download could not be queued. Try again."),
            ready.downloadStatus,
        )
    }

    @Test
    fun defaultQualityPreselectsTheTallestVariantWithinTheCeiling() = runTest {
        val store = PreviewSelectionStore().apply { select(candidate()) }
        val variants = listOf(
            variant("video-1080", height = 1_080),
            variant("video-480", height = 480),
            variant("video-720", height = 720),
        )
        val viewModel = viewModel(
            FakeResolver(VariantResolutionResult.Success(asset(variants))),
            store,
            prefs = DownloadPreferences(defaultQuality = QualityPreference.UP_TO_720P),
        )

        runCurrent()

        val ready = viewModel.uiState.value as PreviewUiState.Ready
        assertEquals(PreviewTab.VIDEO, ready.selectedTab)
        assertEquals("video-720", ready.selectedVariantId)
    }

    @Test
    fun mobileDataAsksFirstAndConfirmingQueuesTheDownload() = runTest {
        val store = PreviewSelectionStore().apply { select(candidate()) }
        val starter = FakeDownloadStarter()
        val viewModel = viewModel(
            FakeResolver(VariantResolutionResult.Success(asset(listOf(variant())))),
            store,
            starter,
            prefs = DownloadPreferences(confirmOnMeteredNetwork = true),
            networkSnapshot = MOBILE_DATA,
        )
        runCurrent()

        viewModel.confirmMeteredDownload()
        runCurrent()
        assertEquals(0, starter.calls)

        viewModel.download()
        runCurrent()

        val asking = viewModel.uiState.value as PreviewUiState.Ready
        assertEquals(PreviewDownloadStatus.ConfirmMetered, asking.downloadStatus)
        assertFalse(asking.canDownload)
        viewModel.download()
        runCurrent()
        assertEquals(0, starter.calls)

        viewModel.confirmMeteredDownload()
        runCurrent()

        assertEquals(1, starter.calls)
        val queued = viewModel.uiState.value as PreviewUiState.Ready
        assertEquals(
            PreviewDownloadStatus.Queued("Fixture.mp4", waitingForUnmetered = false),
            queued.downloadStatus,
        )
    }

    @Test
    fun dismissingTheMobileDataPromptReturnsToIdleWithoutQueueing() = runTest {
        val store = PreviewSelectionStore().apply { select(candidate()) }
        val starter = FakeDownloadStarter()
        val viewModel = viewModel(
            FakeResolver(VariantResolutionResult.Success(asset(listOf(variant())))),
            store,
            starter,
            prefs = DownloadPreferences(confirmOnMeteredNetwork = true),
            networkSnapshot = MOBILE_DATA,
        )
        runCurrent()

        viewModel.download()
        runCurrent()
        viewModel.dismissMeteredDownload()
        runCurrent()

        val idle = viewModel.uiState.value as PreviewUiState.Ready
        assertEquals(PreviewDownloadStatus.Idle, idle.downloadStatus)
        assertTrue(idle.canDownload)
        assertEquals(0, starter.calls)
    }

    @Test
    fun wifiOnlyQueuesWithoutAskingAndReportsTheWaitForWifi() = runTest {
        val store = PreviewSelectionStore().apply { select(candidate()) }
        val starter = FakeDownloadStarter()
        val viewModel = viewModel(
            FakeResolver(VariantResolutionResult.Success(asset(listOf(variant())))),
            store,
            starter,
            prefs = DownloadPreferences(unmeteredOnly = true, confirmOnMeteredNetwork = true),
            networkSnapshot = MOBILE_DATA,
        )
        runCurrent()

        viewModel.download()
        runCurrent()

        assertEquals(1, starter.calls)
        val queued = viewModel.uiState.value as PreviewUiState.Ready
        assertEquals(
            PreviewDownloadStatus.Queued("Fixture.mp4", waitingForUnmetered = true),
            queued.downloadStatus,
        )
    }

    @Test
    fun downloadOptionsFollowTheWifiOnlySettingAndTheSheetSwitchWritesIt() = runTest {
        val store = PreviewSelectionStore().apply { select(candidate()) }
        val preferences = FakePreferences(DownloadPreferences(unmeteredOnly = false))
        val viewModel = viewModel(
            FakeResolver(VariantResolutionResult.Success(asset(listOf(variant())))),
            store,
            preferences = preferences,
        )
        backgroundScope.launch { viewModel.downloadOptions.collect {} }
        runCurrent()

        // SDK 28 has no shared Download/YFT folder for YFT, so files stay in app storage.
        assertEquals(
            PreviewDownloadOptions(wifiOnly = false, savesToSharedDownloads = false),
            viewModel.downloadOptions.value,
        )

        viewModel.setWifiOnly(true)
        runCurrent()

        assertTrue(preferences.state.value.unmeteredOnly)
        assertTrue(viewModel.downloadOptions.value.wifiOnly)

        viewModel.setWifiOnly(false)
        runCurrent()

        assertFalse(preferences.state.value.unmeteredOnly)
        assertFalse(viewModel.downloadOptions.value.wifiOnly)
        assertEquals(2, preferences.updates)
    }

    private fun viewModel(
        resolver: VariantResolver,
        store: PreviewSelectionStore,
        starter: PreviewDownloadStarter = FakeDownloadStarter(),
        prefs: DownloadPreferences = DownloadPreferences(confirmOnMeteredNetwork = false),
        networkSnapshot: NetworkSnapshot = WIFI,
        preferences: DownloadPreferencesRepository = FakePreferences(prefs),
    ): PreviewViewModel {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return PreviewViewModel(
            resolver = resolver,
            selectionStore = store,
            mediaPlayerFactory = MediaPlayerFactory(context),
            previewSourceFactory = PreviewSourceFactory(OkHttpClient()),
            downloadStarter = starter,
            downloadPreferences = preferences,
            network = object : NetworkStatusSource {
                override val snapshot: StateFlow<NetworkSnapshot> =
                    MutableStateFlow(networkSnapshot)
            },
        )
    }

    private fun candidate() = MediaCandidate(
        pageUrl = "https://page.example.test/watch",
        mediaUrl = "https://media.example.test/master.m3u8",
        sources = setOf(CandidateSource.REQUEST),
        kind = MediaKind.HLS,
        requestContext = BrowserRequestContext(
            pageUrl = "https://page.example.test/watch",
            userAgent = "fixture-agent",
            cookie = "session=fixture",
        ),
        confidence = CandidateConfidence.HIGH,
        observedAtEpochMs = 1,
    )

    private fun asset(variants: List<MediaVariant>) = MediaAsset(
        sourcePageUrl = "https://page.example.test/watch",
        title = "Fixture",
        thumbnailUrl = null,
        durationMillis = null,
        variants = variants,
        resolvedAtEpochMs = 1,
    )

    private fun variant(
        id: String = "video",
        trackType: MediaTrackType = MediaTrackType.VIDEO,
        height: Int? = null,
    ) = MediaVariant(
        id = id,
        playbackUrl = "https://media.example.test/$id.m3u8",
        kind = MediaKind.HLS,
        trackType = trackType,
        requestContext = BrowserRequestContext(null, "fixture-agent", null),
        height = height,
    )

    private class FakePreferences(initial: DownloadPreferences) : DownloadPreferencesRepository {
        val state = MutableStateFlow(initial)
        var updates = 0

        override val preferences: Flow<DownloadPreferences> = state

        override suspend fun update(transform: (DownloadPreferences) -> DownloadPreferences) {
            updates += 1
            state.value = transform(state.value)
        }
    }

    private class FakeDownloadStarter(
        var result: EnqueueResult = EnqueueResult.Started("task-1", "Fixture.mp4"),
        var failure: Throwable? = null,
    ) : PreviewDownloadStarter {
        var calls = 0
        val requested = mutableListOf<String>()

        override suspend fun enqueue(
            asset: MediaAsset,
            variant: MediaVariant,
        ): EnqueueResult {
            calls += 1
            requested += variant.id
            failure?.let { throw it }
            return result
        }
    }

    private class FakeResolver(
        var result: VariantResolutionResult,
    ) : VariantResolver {
        var calls = 0

        override suspend fun resolve(candidate: MediaCandidate): VariantResolutionResult {
            calls += 1
            return result
        }
    }

    private companion object {
        val WIFI = NetworkSnapshot(connected = true, validated = true, unmetered = true)
        val MOBILE_DATA = NetworkSnapshot(connected = true, validated = true, unmetered = false)
    }
}
