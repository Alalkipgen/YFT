package com.alal.yft.feature.browser

import com.alal.yft.core.browser.detection.RequestObservation
import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.feature.detectedmedia.DetectedMediaStore
import com.alal.yft.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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

/**
 * P39 (R25): when the TikTok adapter cannot read a page whose player fetched TikTok files, the
 * sheet offers the file the player plays instead of a failure. Fails on the P38 browser, which
 * showed the failure.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BrowserPlayerFileFallbackTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun aVideoPageTikTokCannotReadOffersThePlayersFileInsteadOfAFailure() = runTest {
        val store = DetectedMediaStore()
        val viewModel = BrowserViewModel(OkHttpClient(), adapters(TikTokShapedExtractor()), store)

        viewModel.onPageStarted(VIDEO_PAGE)
        viewModel.onRequest(playerRequest(PLAYER_FILE, VIDEO_PAGE))
        viewModel.onPageFinished(VIDEO_PAGE, "Sunrise | TikTok")
        runCurrent()

        assertNull("no failure in the sheet", store.lookup.value?.failure)
        assertEquals(PlayerFileFallback.NOTICE, viewModel.uiState.value.siteNotice)
        assertTrue(viewModel.openPageVideo())
        val row = store.selection.value?.candidates.orEmpty().single()
        assertEquals(PLAYER_FILE, row.mediaUrl)
        assertEquals(PageMediaRole.MAIN, row.pageRole)
        assertEquals("tiktok:$VIDEO_ID", row.videoId)
        assertEquals(VIDEO_PAGE, row.pageUrl)
        assertEquals("tt_chain_token=fixture", row.requestContext.cookie)
        assertEquals("https://www.tiktok.com/", row.requestContext.observedHeaders["Referer"])
    }

    @Test
    fun aFeedVideoTikTokCannotReadOffersTheOnScreenPlayersFile() = runTest {
        val store = DetectedMediaStore()
        val viewModel = BrowserViewModel(OkHttpClient(), adapters(TikTokShapedExtractor()), store)
        viewModel.onPageStarted(FEED_PAGE)
        viewModel.onPageFinished(FEED_PAGE, "For You | TikTok")
        runCurrent()
        // The on-screen video's file, then the next video's file the player loads ahead.
        viewModel.onRequest(playerRequest(PLAYER_FILE, FEED_PAGE))
        viewModel.onRequest(playerRequest(NEXT_FILE, FEED_PAGE))
        runCurrent()

        assertNotNull(viewModel.focusedVideoScript())
        viewModel.onFocusedVideoResult(answer(VIDEO_PAGE, src = PLAYER_FILE))
        runCurrent()

        assertNull("no failure in the sheet", store.lookup.value?.failure)
        assertEquals(
            listOf(PLAYER_FILE),
            store.selection.value?.candidates?.map { it.mediaUrl },
        )
        assertEquals(PlayerFileFallback.NOTICE, viewModel.uiState.value.siteNotice)
    }

    @Test
    fun withoutAPlayerFileOrForAProtectedVideoTheFailureStaysWithItsDetails() = runTest {
        val store = DetectedMediaStore()
        val viewModel = BrowserViewModel(OkHttpClient(), adapters(TikTokShapedExtractor()), store)
        viewModel.onPageStarted(VIDEO_PAGE)
        viewModel.onPageFinished(VIDEO_PAGE, "Sunrise | TikTok")
        runCurrent()

        val failed = store.lookup.value
        assertEquals(TikTokShapedExtractor.MESSAGE, failed?.failure)
        val details = failed?.details.orEmpty()
        assertTrue(details.toString(), details.containsAll(TikTokShapedExtractor.DETAILS))

        val protectedStore = DetectedMediaStore()
        val protectedPage = BrowserViewModel(
            OkHttpClient(),
            adapters(TikTokShapedExtractor(SiteExtractionFailure.DRM_PROTECTED)),
            protectedStore,
        )
        protectedPage.onPageStarted(VIDEO_PAGE)
        protectedPage.onRequest(playerRequest(PLAYER_FILE, VIDEO_PAGE))
        protectedPage.onPageFinished(VIDEO_PAGE, "Sunrise | TikTok")
        runCurrent()
        assertNotNull(protectedStore.lookup.value?.failure)
    }

    private fun adapters(extractor: SiteExtractor) =
        SiteAdapterCoordinator(SiteExtractorRegistry(listOf(extractor)))

    private fun playerRequest(url: String, page: String) = RequestObservation(
        pageUrl = page,
        requestUrl = url,
        method = "GET",
        headers = mapOf("Referer" to "https://www.tiktok.com/", "Range" to "bytes=0-"),
        userAgent = "Mozilla/5.0 (Linux; Android 15; wv) Chrome/137.0.0.0 Mobile Safari/537.36",
        cookie = "tt_chain_token=fixture",
        observedAtEpochMs = 1_000,
    )

    private fun answer(url: String, src: String?): String = JSONObject.quote(
        JSONObject().put("url", url).put("source", "playing").put("src", src).toString(),
    )

    /** Fails like TikTok's changed page; TikTok's player files are its own. */
    private class TikTokShapedExtractor(
        private val reason: SiteExtractionFailure = SiteExtractionFailure.RESPONSE_CHANGED,
    ) : SiteExtractor {
        override val id: String = "tiktok"
        override val displayName: String = "TikTok"

        override fun identify(pageUrl: String): SitePageIdentity? {
            val id = VIDEO.find(pageUrl)?.groupValues?.get(1) ?: return null
            return SitePageIdentity("tiktok", id, "https://www.tiktok.com/@fixture_user/video/$id")
        }

        override fun isPlayerMediaRequest(requestUrl: String): Boolean =
            requestUrl.contains("/video/tos/")

        override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult =
            SiteExtractionResult.Failure(reason, details = DETAILS, message = MESSAGE)

        companion object {
            const val MESSAGE = "TikTok's page could not be read. Tap Details to see why."
            val DETAILS = listOf("page: phone · HTTP 200 · 150 KB · landed on: video page")
            private val VIDEO = Regex("tiktok\\.com/@[\\w.-]*/video/(\\d{15,22})")
        }
    }

    private companion object {
        const val VIDEO_ID = "7311234567890123456"
        const val VIDEO_PAGE = "https://www.tiktok.com/@fixture_user/video/$VIDEO_ID"
        const val FEED_PAGE = "https://www.tiktok.com/foryou"
        const val PLAYER_FILE =
            "https://v16-webapp-prime.us.tiktok.com/video/tos/useast5/a/?mime_type=video_mp4"
        const val NEXT_FILE =
            "https://v16-webapp-prime.us.tiktok.com/video/tos/useast5/b/?mime_type=video_mp4"
    }
}
