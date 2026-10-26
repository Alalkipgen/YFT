package com.alal.yft.feature.browser

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.core.browser.detection.FocusedVideoProbe
import com.alal.yft.core.data.preferences.DownloadPreferencesRepository
import com.alal.yft.core.media.resolver.VariantResolver
import com.alal.yft.core.media.session.PreviewSelectionStore
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.VariantResolutionResult
import com.alal.yft.core.model.settings.DownloadPreferences
import com.alal.yft.detection.MergeSupport
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.detection.VideoPlaybackSupport
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
import com.alal.yft.feature.detectedmedia.DetectedMediaStore
import com.alal.yft.feature.quickdownload.QuickDownloadRoute
import com.alal.yft.feature.quickdownload.QuickDownloadViewModel
import com.alal.yft.testing.MainDispatcherRule
import com.alal.yft.ui.LocalQuietRelease
import com.alal.yft.ui.theme.YftTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * VK-Master wave 2 (the success sheet's Lookup details): after the backup finder's success the
 * browser's real ViewModel hands the lookup's own lines (`outcome.details`) with the video to the
 * real download sheet — on a site's page (Download after the lookup or while it runs) and for the
 * video on a feed's screen — which shows them behind "Lookup details" with Copy details; a quiet
 * build without Show details shows none, and main's own success has none. Made-up lines only.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
class LookupDetailsSheetTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun aPageTheBackupFoundOpensTheSheetWithItsLookupDetailsShownAndCopiedAsTheLines() {
        val store = DetectedMediaStore()
        val backup = FoundByBackup(listOf(row(SITE, "fixture:11111")))
        val browser = browser(PageExtractor(), store, backup)
        browser.onPageStarted(SITE)
        browser.onPageFinished(SITE, "Fixture")
        settle()
        assertEquals(1, backup.recovered)

        // Download after the lookup found it: the sheet's video comes with the lookup's lines.
        assertTrue(browser.openPageVideo())
        assertEquals(LINES, store.lookupDetailsOf(store.selection.value))
        val sheet = sheet(store)
        settle()
        assertNotNull(sheet.uiState.value.choices)
        assertEquals(LINES, sheet.uiState.value.lookupDetails)

        // The real sheet: Lookup details under the rows; Copy details copies the lines as shown.
        var quiet by mutableStateOf(false)
        composeRule.setContent {
            CompositionLocalProvider(LocalQuietRelease provides quiet) {
                YftTheme(themeMode = ThemeMode.LIGHT) {
                    QuickDownloadRoute(
                        onNavigateBack = {},
                        onOpenDownloads = {},
                        onOpenDetails = {},
                        viewModel = sheet,
                    )
                }
            }
        }
        composeRule.onNodeWithTag("quick-details").assertIsDisplayed()
        composeRule.onAllNodesWithTag("quick-lookup-detail-text").assertCountEquals(0)
        composeRule.onNodeWithTag("quick-lookup-details").performScrollTo().performClick()
        composeRule.onNodeWithTag("quick-lookup-detail-text")
            .assertTextEquals(LINES.joinToString("\n"))
        composeRule.onNodeWithTag("quick-lookup-copy").performScrollTo().performClick()
        assertEquals(LINES.joinToString("\n"), copied())

        // A quiet build without Show details: no Lookup details, the rows and Download as before.
        quiet = true
        composeRule.waitForIdle()
        for (tag in listOf(
            "quick-lookup-details", "quick-lookup-detail-text", "quick-lookup-copy",
        )) {
            composeRule.onAllNodesWithTag(tag).assertCountEquals(0)
        }
        composeRule.onNodeWithTag("quick-download").assertIsDisplayed()
        composeRule.onNodeWithTag("quick-details").assertIsDisplayed()
    }

    @Test
    fun aSheetThatWaitedOnThePagesLookupTakesTheLinesWithTheVideo() {
        val gate = CompletableDeferred<Unit>()
        val store = DetectedMediaStore()
        val browser = browser(
            PageExtractor(),
            store,
            FoundByBackup(listOf(row(SITE, "fixture:11111")), gate),
        )
        browser.onPageStarted(SITE)
        browser.onPageFinished(SITE, "Fixture")
        settle()

        // Download while the backup still looks: the sheet waits, with no lines yet.
        assertTrue(browser.openPageVideo())
        assertNull(store.selection.value)
        val sheet = sheet(store)
        settle()
        assertNull(sheet.uiState.value.choices)
        assertTrue(sheet.uiState.value.lookupDetails.isEmpty())

        gate.complete(Unit)
        settle()
        assertNotNull(sheet.uiState.value.choices)
        assertEquals(LINES, sheet.uiState.value.lookupDetails)
    }

    @Test
    fun theVideoOnAFeedsScreenTheBackupFoundGivesTheSheetItsLookupDetails() {
        val store = DetectedMediaStore()
        val backup = FoundByBackup(listOf(row(FOCUSED, "youtube:BBBBBBBBBB2")))
        val browser = browser(FeedExtractor(failing = true), store, backup)
        browser.onPageStarted(FEED)
        browser.onPageFinished(FEED, "YouTube")
        settle()

        // The feed's Download: the sheet opens on the video on screen and waits for its lookup.
        assertEquals(FocusedVideoProbe.script, browser.focusedVideoScript())
        browser.onFocusedVideoResult(answer(FOCUSED))
        val waiting = sheet(store)
        assertTrue(waiting.uiState.value.lookupDetails.isEmpty())
        settle()
        assertEquals(1, backup.recovered)
        assertNotNull(waiting.uiState.value.choices)
        assertEquals(LINES, waiting.uiState.value.lookupDetails)

        // The same video again: the found answer, its lines with it, and no new ask.
        assertEquals(FocusedVideoProbe.script, browser.focusedVideoScript())
        browser.onFocusedVideoResult(answer(FOCUSED))
        settle()
        assertEquals(1, backup.recovered)
        assertEquals(LINES, store.lookupDetailsOf(store.selection.value))
        val again = sheet(store)
        settle()
        assertNotNull(again.uiState.value.choices)
        assertEquals(LINES, again.uiState.value.lookupDetails)
    }

    @Test
    fun mainsOwnSuccessGivesNoLookupDetails() {
        // A page main found: the backup is not asked and the sheet has no lines.
        val pageStore = DetectedMediaStore()
        val pageBackup = FoundByBackup(listOf(row(SITE, "fixture:11111")))
        val page = browser(PageExtractor(found = true), pageStore, pageBackup)
        page.onPageStarted(SITE)
        page.onPageFinished(SITE, "Fixture")
        settle()
        assertTrue(page.openPageVideo())
        val pageSheet = sheet(pageStore)
        settle()
        assertEquals(0, pageBackup.recovered)
        assertNotNull(pageSheet.uiState.value.choices)
        assertTrue(pageSheet.uiState.value.lookupDetails.isEmpty())

        // The video on a feed's screen main found: none either.
        val feedStore = DetectedMediaStore()
        val feedBackup = FoundByBackup(listOf(row(FOCUSED, "youtube:BBBBBBBBBB2")))
        val feed = browser(FeedExtractor(failing = false), feedStore, feedBackup)
        feed.onPageStarted(FEED)
        feed.onPageFinished(FEED, "YouTube")
        settle()
        assertEquals(FocusedVideoProbe.script, feed.focusedVideoScript())
        feed.onFocusedVideoResult(answer(FOCUSED))
        val feedSheet = sheet(feedStore)
        settle()
        assertEquals(0, feedBackup.recovered)
        assertNotNull(feedSheet.uiState.value.choices)
        assertTrue(feedSheet.uiState.value.lookupDetails.isEmpty())

        // Another video than the one the lookup found never takes its lines.
        val store = DetectedMediaStore()
        val found = MediaGroups.pageVideos(listOf(row(SITE, "fixture:11111"))).single()
        store.select(found, lookupDetails = LINES)
        assertEquals(LINES, store.lookupDetailsOf(store.selection.value))
        val otherVideo = MediaGroups.pageVideos(listOf(row(SITE, "fixture:22222"))).single()
        assertTrue(store.lookupDetailsOf(otherVideo).isEmpty())
        store.select(otherVideo)
        assertTrue(store.lookupDetailsOf(store.selection.value).isEmpty())
        assertTrue(store.lookupDetailsOf(found).isEmpty())
    }

    private fun copied(): String = ApplicationProvider.getApplicationContext<Context>()
        .getSystemService(ClipboardManager::class.java)
        .primaryClip!!
        .getItemAt(0)
        .text
        .toString()

    private fun settle() {
        main.dispatcher.scheduler.advanceTimeBy(1_000)
        main.dispatcher.scheduler.runCurrent()
    }

    /** The focused-video script's answer as `WebView.evaluateJavascript` hands it back. */
    private fun answer(url: String): String =
        JSONObject.quote(JSONObject().put("url", url).put("source", "centre").toString())

    private fun browser(
        extractor: SiteExtractor,
        store: DetectedMediaStore,
        backup: BrowserMasterFallback,
    ) = BrowserViewModel(
        OkHttpClient(),
        SiteAdapterCoordinator(SiteExtractorRegistry(listOf(extractor)), MergeSupport { true }),
        store,
        masterFallback = backup,
    )

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
    )

    /** One whole MP4 of [page]'s video, named by the site's [videoId]. */
    private fun row(page: String, videoId: String) = MediaCandidate(
        pageUrl = page,
        mediaUrl = "https://cdn.fixture.test/${videoId.substringAfter(':')}.mp4",
        sources = setOf(CandidateSource.REQUEST),
        kind = MediaKind.DIRECT,
        mimeType = "video/mp4",
        title = "Fixture clip",
        durationMillis = 61_966,
        contentLengthBytes = 4_000_000L,
        confidence = CandidateConfidence.HIGH,
        width = 1280,
        height = 720,
        videoId = videoId,
    )

    /** The backup finder's success after main's failure: [rows] with its own [LINES]. */
    private class FoundByBackup(
        private val rows: List<MediaCandidate>,
        private val gate: CompletableDeferred<Unit>? = null,
    ) : BrowserMasterFallback {
        override val enabled = true
        var recovered = 0

        override suspend fun recover(
            lookupUrl: String,
            primary: SiteAdapterOutcome,
            nowEpochMs: Long,
            genericOnDemand: Boolean,
        ): SiteAdapterOutcome {
            recovered++
            gate?.await()
            return SiteAdapterOutcome.Detected("master", rows, details = LINES)
        }
    }

    /** A site's video page: main finds nothing, or (with [found]) the page's one video. */
    private inner class PageExtractor(private val found: Boolean = false) : SiteExtractor {
        override val id = "fixture"
        override val displayName = "Fixture"
        override fun identify(pageUrl: String): SitePageIdentity? =
            if (pageUrl == SITE) SitePageIdentity(id, "11111", pageUrl) else null

        override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult =
            if (found) {
                SiteExtractionResult.Success(listOf(row(SITE, "fixture:11111")))
            } else {
                SiteExtractionResult.Failure(SiteExtractionFailure.NO_MEDIA_FOUND)
            }
    }

    /** A YouTube-shaped adapter: watch pages only; main fails (with [failing]) or finds it. */
    private inner class FeedExtractor(private val failing: Boolean) : SiteExtractor {
        override val id = "youtube"
        override val displayName = "YouTube"
        override fun identify(pageUrl: String): SitePageIdentity? {
            val videoId = WATCH.matchEntire(pageUrl)?.groupValues?.get(1) ?: return null
            return SitePageIdentity(id, videoId, "https://www.youtube.com/watch?v=$videoId")
        }

        override fun isPlayerMediaRequest(requestUrl: String): Boolean = false

        override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult =
            if (failing) {
                SiteExtractionResult.Failure(SiteExtractionFailure.NO_MEDIA_FOUND)
            } else {
                SiteExtractionResult.Success(
                    listOf(row(request.identity.canonicalPageUrl, "youtube:BBBBBBBBBB2")),
                )
            }
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
        const val SITE = "https://video.fixture.test/watch/11111"
        const val FEED = "https://m.youtube.com/"
        const val FOCUSED = "https://www.youtube.com/watch?v=BBBBBBBBBB2"
        val WATCH = Regex("https://(?:www|m)\\.youtube\\.com/watch\\?v=([A-Za-z0-9_-]{11})(?:&.*)?")

        /** Made-up lines of the backup's success: a ladder line and a heal line, no value. */
        val LINES = listOf(
            "master: fixture ladder after NO_MEDIA_FOUND; main's answers re-read, no new ask",
            "master: fixture V2 heal client_id: healed from fixture-script " +
                "(the page and its common script: 2 asks, 1843200 bytes)",
        )
    }
}
