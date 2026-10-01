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
import com.alal.yft.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    private fun viewModel(
        resolver: VariantResolver,
        store: PreviewSelectionStore,
    ): PreviewViewModel {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return PreviewViewModel(
            resolver = resolver,
            selectionStore = store,
            mediaPlayerFactory = MediaPlayerFactory(context),
            previewSourceFactory = PreviewSourceFactory(OkHttpClient()),
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
    ) = MediaVariant(
        id = id,
        playbackUrl = "https://media.example.test/$id.m3u8",
        kind = MediaKind.HLS,
        trackType = trackType,
        requestContext = BrowserRequestContext(null, "fixture-agent", null),
    )

    private class FakeResolver(
        var result: VariantResolutionResult,
    ) : VariantResolver {
        var calls = 0

        override suspend fun resolve(candidate: MediaCandidate): VariantResolutionResult {
            calls += 1
            return result
        }
    }
}