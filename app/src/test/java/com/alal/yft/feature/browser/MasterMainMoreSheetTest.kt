package com.alal.yft.feature.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.alal.yft.core.data.preferences.DownloadPreferencesRepository
import com.alal.yft.core.media.resolver.VariantResolver
import com.alal.yft.core.media.session.PreviewSelectionStore
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroup
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.VariantResolutionResult
import com.alal.yft.core.model.settings.DownloadPreferences
import com.alal.yft.detection.MergeSupport
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.detection.VideoPlaybackSupport
import com.alal.yft.detection.master.AndroidBrowserMasterFallback
import com.alal.yft.detection.master.BrowserMasterFallback
import com.alal.yft.download.EnqueueResult
import com.alal.yft.download.PreviewDownloadStarter
import com.alal.yft.download.policy.NetworkSnapshot
import com.alal.yft.download.policy.NetworkStatusSource
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.present.MasterMainPresentation
import com.alal.yft.feature.detectedmedia.DetectedMediaStore
import com.alal.yft.feature.quickdownload.QuickDownloadRoute
import com.alal.yft.feature.quickdownload.QuickDownloadUiState
import com.alal.yft.feature.quickdownload.QuickDownloadViewModel
import com.alal.yft.testing.MainDispatcherRule
import com.alal.yft.ui.theme.YftTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * M2 after P45 (the tapped video only): the browser's real ViewModel takes Master's main/More
 * presentation through the existing hook, the real download sheet (QuickDownloadRoute) shows
 * only the main video with no "Other videos" row, and the remaining videos stay in the
 * browser's own found list. The presentation fixture has the module's shape (one group per
 * verified file, owned UI keys, no post ID); the selection policy itself is covered by the
 * module tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
class MasterMainMoreSheetTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun mainWithSevenMoreShowsOnlyTheMainInTheSheetAndTheRestInTheFoundList() {
        val videos = listOf(packed(0, MAIN)) + (1..7).map { packed(it, "Alternative $it") }
        val backup = CapturedMainMore(videos)
        val store = DetectedMediaStore()
        val browser = browser(FixtureExtractor(), store, backup)
        openSitePage(browser)
        assertEquals(1, backup.recovered)
        assertEquals(videos.first().mediaUrl, store.selection.value!!.candidates.single().mediaUrl)
        assertFalse(browser.uiState.value.sitePage)
        val listed = browser.uiState.value.candidates.map { it.mediaUrl }
        videos.forEach { assertTrue(it.mediaUrl in listed) }

        render(browser, sheet(store))
        composeRule.onNodeWithTag("quick-header").assertIsDisplayed()
        composeRule.onNodeWithText(MAIN).assertIsDisplayed()
        (1..7).forEach { composeRule.onAllNodesWithText("Alternative $it").assertCountEquals(0) }
        composeRule.onAllNodesWithTag("quick-other-videos").assertCountEquals(0)
        composeRule.onAllNodesWithText("Other videos on this page", substring = true)
            .assertCountEquals(0)

        // The sheet closes; the browser's found list (More) still lists the other seven.
        composeRule.runOnIdle { sheetOpen = false }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("quick-header").assertCountEquals(0)
        composeRule.onNodeWithTag("media-found-button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("found-list").assertIsDisplayed()
        (1..7).forEach { index ->
            val title = "Alternative $index"
            composeRule.onNodeWithTag("found-list").performScrollToNode(hasText(title))
            composeRule.onNodeWithText(title).assertIsDisplayed()
        }
    }

    @Test
    fun mainWithNoMoreShowsTheMainAndNoMoreButton() {
        val only = packed(0, MAIN)
        val backup = CapturedMainMore(listOf(only))
        val store = DetectedMediaStore()
        val browser = browser(FixtureExtractor(), store, backup)
        openSitePage(browser)
        assertEquals(only.mediaUrl, store.selection.value!!.candidates.single().mediaUrl)

        render(browser, sheet(store))
        composeRule.onNodeWithTag("quick-header").assertIsDisplayed()
        composeRule.onNodeWithText(MAIN).assertIsDisplayed()
        composeRule.onNodeWithTag("quick-download").assertIsDisplayed()
        composeRule.onAllNodesWithTag("quick-other-videos").assertCountEquals(0)
        composeRule.onAllNodesWithText("Other videos on this page", substring = true)
            .assertCountEquals(0)
    }

    @Test
    fun flagOffKeepsTheLegacySheetExactly() {
        val sites = coordinator(FixtureExtractor(named = true))
        val off = AndroidBrowserMasterFallback.create(false, OkHttpClient(), sites)
        assertSame(BrowserMasterFallback.None, off)
        assertNull(off.mainAndMore(listOf(packed(0, MAIN))))

        // Legacy reference: the ViewModel's own pre-Master default, no fallback argument.
        val legacyStore = DetectedMediaStore()
        val legacy = BrowserViewModel(
            OkHttpClient(), coordinator(FixtureExtractor(named = true)), legacyStore,
        )
        val offStore = DetectedMediaStore()
        val flagOff = BrowserViewModel(OkHttpClient(), sites, offStore, masterFallback = off)
        openSitePage(legacy)
        openSitePage(flagOff)
        assertEquals(legacyStore.selection.value, offStore.selection.value)
        assertEquals(NAMED_FIRST, offStore.selection.value!!.title)
        assertEquals(legacy.uiState.value.candidates, flagOff.uiState.value.candidates)
        assertEquals(legacy.uiState.value.sitePage, flagOff.uiState.value.sitePage)

        // A failing adapter is not recovered by capture: the same lookup failure as before.
        val failedLegacyStore = DetectedMediaStore()
        val failedOffStore = DetectedMediaStore()
        openSitePage(
            BrowserViewModel(OkHttpClient(), coordinator(FixtureExtractor()), failedLegacyStore),
        )
        openSitePage(BrowserViewModel(
            OkHttpClient(), coordinator(FixtureExtractor()), failedOffStore,
            masterFallback = AndroidBrowserMasterFallback.create(false, OkHttpClient(), sites),
        ))
        assertNull(failedOffStore.selection.value)
        assertEquals(failedLegacyStore.lookup.value, failedOffStore.lookup.value)

        val legacySheet = sheet(legacyStore)
        val offSheet = sheet(offStore)
        assertEquals(
            legacySheet.uiState.value.withoutClock(),
            offSheet.uiState.value.withoutClock(),
        )
        render(flagOff, offSheet)
        composeRule.onNodeWithTag("quick-header").assertIsDisplayed()
        composeRule.onNodeWithText(NAMED_FIRST).assertIsDisplayed()
        composeRule.onNodeWithTag("quick-download").assertIsDisplayed()
        composeRule.onAllNodesWithTag("quick-other-videos").assertCountEquals(0)
    }

    private fun openSitePage(vm: BrowserViewModel) {
        vm.onPageStarted(SITE)
        vm.onPageFinished(SITE, "Fixture")
        settle()
        vm.openPageVideo()
        settle()
    }

    private var sheetOpen by mutableStateOf(true)

    private fun render(browser: BrowserViewModel, sheet: QuickDownloadViewModel) {
        sheetOpen = true
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                val state by browser.uiState.collectAsState()
                Box {
                    BrowserScreen(
                        uiState = state,
                        canGoBack = false,
                        canGoForward = false,
                        onAddressChanged = {},
                        onGo = {},
                        onBrowserBack = {},
                        onBrowserForward = {},
                        onReload = {},
                        onStop = {},
                        onDownloadGroup = {},
                        onNavigateBack = {},
                        browserSurface = { Box(modifier = it) },
                    )
                    if (sheetOpen) {
                        QuickDownloadRoute(
                            onNavigateBack = { sheetOpen = false },
                            onOpenDownloads = {},
                            onOpenDetails = {},
                            viewModel = sheet,
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** Every sheet field compared; only the asset's wall-clock resolve time is masked. */
    private fun QuickDownloadUiState.withoutClock(): String =
        toString().replace(Regex("resolvedAtEpochMs=\\d+"), "resolvedAtEpochMs=<clock>")

    private fun settle() {
        main.dispatcher.scheduler.advanceTimeBy(1_000)
        main.dispatcher.scheduler.runCurrent()
    }

    private fun coordinator(extractor: FixtureExtractor) =
        SiteAdapterCoordinator(SiteExtractorRegistry(listOf(extractor)), MergeSupport { true })

    private fun browser(
        extractor: FixtureExtractor,
        store: DetectedMediaStore,
        backup: BrowserMasterFallback,
    ) = BrowserViewModel(OkHttpClient(), coordinator(extractor), store, masterFallback = backup)

    private fun sheet(store: DetectedMediaStore): QuickDownloadViewModel = QuickDownloadViewModel(
        store = store,
        selectionStore = PreviewSelectionStore(),
        resolver = WholeFileResolver,
        downloadStarter = object : PreviewDownloadStarter {
            override suspend fun enqueue(asset: MediaAsset, variant: MediaVariant) =
                EnqueueResult.Started("task", "fixture.mp4")
        },
        downloadPreferences = object : DownloadPreferencesRepository {
            override val preferences: Flow<DownloadPreferences> =
                MutableStateFlow(DownloadPreferences(confirmOnMeteredNetwork = false))
            override suspend fun update(transform: (DownloadPreferences) -> DownloadPreferences) =
                Unit
        },
        network = object : NetworkStatusSource {
            override val snapshot: StateFlow<NetworkSnapshot> = MutableStateFlow(
                NetworkSnapshot(connected = true, validated = true, unmetered = true),
            )
        },
        playback = VideoPlaybackSupport.ANY,
    ).also { settle() }

    /** Master's packed UI copy: no post ID, one owned presentation key per verified file. */
    private fun packed(index: Int, title: String) = MediaCandidate(
        pageUrl = SITE,
        mediaUrl = "https://cdn.test/master-$index.mp4",
        sources = setOf(CandidateSource.REQUEST),
        kind = MediaKind.DIRECT,
        mimeType = "video/mp4",
        title = title,
        durationMillis = 61_966,
        contentLengthBytes = 4_000_000L + index,
        confidence = CandidateConfidence.HIGH,
        width = 1080,
        height = 1920,
        pageVideoKey = "master:ui:fixture$index",
    )

    /** Main/More exactly as the module's presentation: owned keys only, verified order. */
    private class CapturedMainMore(private val verified: List<MediaCandidate>) :
        BrowserMasterFallback {
        override val enabled = true
        var recovered = 0

        override suspend fun recover(
            lookupUrl: String,
            primary: SiteAdapterOutcome,
            nowEpochMs: Long,
            genericOnDemand: Boolean,
        ): SiteAdapterOutcome {
            recovered++
            return SiteAdapterOutcome.Detected("master", verified)
        }

        override fun mainAndMore(candidates: List<MediaCandidate>): MasterMainPresentation? {
            val keys = verified.map { it.pageVideoKey }
            val owned = candidates.filter { it.videoId == null && it.pageVideoKey in keys }
                .distinctBy { it.mediaUrl }
                .sortedBy { keys.indexOf(it.pageVideoKey) }
            if (owned.isEmpty()) return null
            val groups = owned.map { MediaGroup(it.pageVideoKey!!, it.title, listOf(it)) }
            return MasterMainPresentation(groups.first(), groups.drop(1))
        }
    }

    private class FixtureExtractor(private val named: Boolean = false) : SiteExtractor {
        override val id = "tiktok"
        override val displayName = "Fixture"
        override fun identify(pageUrl: String): SitePageIdentity? =
            if (pageUrl == SITE) SitePageIdentity(id, "11111", pageUrl) else null

        override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult =
            if (named) {
                SiteExtractionResult.Success(listOf(
                    named("11111", NAMED_FIRST, 0), named("22222", "Named second", 1),
                ))
            } else SiteExtractionResult.Failure(SiteExtractionFailure.NO_MEDIA_FOUND)

        private fun named(id: String, title: String, index: Int) = MediaCandidate(
            pageUrl = SITE,
            mediaUrl = "https://cdn.test/named-$index.mp4",
            sources = setOf(CandidateSource.REQUEST),
            kind = MediaKind.DIRECT,
            mimeType = "video/mp4",
            title = title,
            durationMillis = 30_000L + index * 1_000,
            contentLengthBytes = 2_000_000L,
            confidence = CandidateConfidence.HIGH,
            videoId = "tiktok:$id",
        )
    }

    /** A whole MP4 resolves to itself, as the real resolver does for a stated file. */
    private object WholeFileResolver : VariantResolver {
        override suspend fun resolve(candidate: MediaCandidate): VariantResolutionResult =
            VariantResolutionResult.Success(
                MediaAsset(
                    sourcePageUrl = candidate.pageUrl,
                    title = candidate.title,
                    thumbnailUrl = null,
                    durationMillis = candidate.durationMillis,
                    variants = listOf(
                        MediaVariant(
                            id = "direct-0",
                            playbackUrl = candidate.mediaUrl,
                            kind = MediaKind.DIRECT,
                            trackType = MediaTrackType.AUDIO_VIDEO,
                            requestContext = BrowserRequestContext(candidate.pageUrl, null, null),
                            mimeType = candidate.mimeType,
                            width = candidate.width,
                            height = candidate.height,
                            sizeBytes = candidate.contentLengthBytes,
                        ),
                    ),
                    resolvedAtEpochMs = 1,
                ),
            )
    }

    private companion object {
        const val SITE = "https://www.tiktok.com/@fixture/video/11111"
        const val MAIN = "Main clip"
        const val NAMED_FIRST = "Named first"
    }
}
