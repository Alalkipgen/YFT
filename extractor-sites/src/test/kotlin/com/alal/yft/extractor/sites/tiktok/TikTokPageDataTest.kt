package com.alal.yft.extractor.sites.tiktok

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SitePageData
import com.alal.yft.extractor.api.SitePageDataSource
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.sites.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.sites.testing.Fixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P40 step 1: the post's data a TikTok page already holds ([SiteExtractionRequest.pageData]).
 * The item fixtures are what `core-browser/src/main/assets/tiktok/page-data.js` returned on the
 * instrumented fixture page (`app/src/androidTest/assets/tiktok/video.html`) in Chromium.
 */
class TikTokPageDataTest {
    @Test
    fun `the tab's data for the post gives its rows without asking for the page`() = runTest {
        val http = FakeExtractorHttpClient(probeResponder = { url, _ -> fileAnswer(url) })
        val request = request(identity(POST_ID), data(scriptItem(), SitePageDataSource.TAB_SCRIPT))

        val result = TikTokExtractor(http).extract(request) as SiteExtractionResult.Success

        // The old adapter asked for the page first; now the page's own data is enough.
        assertEquals(emptyList<String>(), http.requestedUrls)
        assertEquals(
            listOf("fixture-1080.mp4", "fixture-720.mp4", "fixture-540.mp4"),
            result.candidates.map { it.mediaUrl.substringAfterLast('/').substringBefore('?') },
        )
        val first = result.candidates.first()
        assertEquals("fixture-tab-agent", first.requestContext.userAgent)
        assertEquals(TAB_COOKIE, first.requestContext.cookie)
        assertEquals("https://www.tiktok.com/@fixture_user/video/$POST_ID", first.pageUrl)
        // Each file was checked with the tab's cookies and TikTok's Referer.
        assertEquals(3, http.probedUrls.size)
        assertTrue(http.probedHeaders.all { it["Cookie"] == TAB_COOKIE })
        assertTrue(http.probedHeaders.all { it["Referer"] == "https://www.tiktok.com/" })
        assertTrue(result.details.contains("data: tab · page script · JSON: read"))
        assertTrue(result.details.contains("post id: matches"))
        assertTrue(result.details.contains("answer: tab · 3 working qualities"))
    }

    @Test
    fun `a feed answer's item for the post gives its rows too`() = runTest {
        val http = FakeExtractorHttpClient(probeResponder = { url, _ -> fileAnswer(url) })
        val request = request(
            identity(FEED_POST_ID, author = "feed_user"),
            data(
                Fixtures.read("tiktok/page_data_api_item.json"),
                SitePageDataSource.TAB_API_ANSWER,
            ),
        )

        val result = TikTokExtractor(http).extract(request) as SiteExtractionResult.Success

        assertEquals(emptyList<String>(), http.requestedUrls)
        // Its two listed qualities and its play address, a 540p file of its own.
        assertEquals(listOf(1920, 1280, 1024), result.candidates.map(MediaCandidate::height))
        assertTrue(result.details.contains("data: tab · API answer · JSON: read"))
    }

    @Test
    fun `the script's item gives the same rows as reading the page it came from`() = runTest {
        val identity = identity(POST_ID)
        val page = FakeExtractorHttpClient.serving(
            url = identity.canonicalPageUrl,
            body = Fixtures.read("tiktok/universal_video.html"),
        )
        val fromPage = TikTokExtractor(page).extract(request(identity, pageData = null))
            as SiteExtractionResult.Success
        val tab = FakeExtractorHttpClient()
        val fromData = TikTokExtractor(tab).extract(
            request(identity, data(scriptItem(), SitePageDataSource.TAB_SCRIPT)),
        ) as SiteExtractionResult.Success

        assertEquals(listOf(identity.canonicalPageUrl), page.requestedUrls)
        assertEquals(emptyList<String>(), tab.requestedUrls)
        assertEquals(fromPage.candidates, fromData.candidates)
    }

    @Test
    fun `another post's data is not used and the page is read`() = runTest {
        val identity = identity(POST_ID)
        val http = FakeExtractorHttpClient.serving(
            url = identity.canonicalPageUrl,
            body = Fixtures.read("tiktok/universal_video.html"),
        )
        val feedItem = Fixtures.read("tiktok/page_data_api_item.json")

        val result = TikTokExtractor(http).extract(
            request(identity, data(feedItem, SitePageDataSource.TAB_API_ANSWER)),
        ) as SiteExtractionResult.Success

        assertEquals(listOf(identity.canonicalPageUrl), http.requestedUrls)
        assertEquals(3, result.candidates.size)
        assertTrue(result.details.contains("post id: other id"))
        assertTrue(result.details.contains("data: not used · page read next"))
    }

    @Test
    fun `data whose files do not open falls back to the page read`() = runTest {
        val identity = identity(POST_ID)
        val page = Fixtures.read("tiktok/universal_video.html")
        val http = FakeExtractorHttpClient(
            responses = mapOf(
                identity.canonicalPageUrl to FakeExtractorHttpClient.html(
                    page,
                    identity.canonicalPageUrl,
                ),
            ),
            probeResponder = { url, _ -> fileAnswer(url) },
        )
        val stale = scriptItem().replace("/video/play/fixture-", "/video/play/stale-")
            .replace("/video/download/fixture-", "/video/download/stale-")

        val result = TikTokExtractor(http).extract(
            request(identity, data(stale, SitePageDataSource.TAB_SCRIPT)),
        ) as SiteExtractionResult.Success

        assertEquals(listOf(identity.canonicalPageUrl), http.requestedUrls)
        assertTrue(result.candidates.all { "/fixture-" in it.mediaUrl })
        assertTrue(result.details.contains("data: no file opened · page read next"))
        assertTrue(result.details.contains("answer: phone · 3 working qualities"))
    }

    @Test
    fun `the hidden page's data is the last word when its files do not open`() = runTest {
        val http = FakeExtractorHttpClient(probeResponder = { _, _ -> refused() })

        val result = TikTokExtractor(http).extract(
            request(identity(POST_ID), data(scriptItem(), SitePageDataSource.HIDDEN_PAGE)),
        ) as SiteExtractionResult.Failure

        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, result.reason)
        assertEquals(TikTokExtractor.FILES_REFUSED_MESSAGE, result.message)
        assertEquals(emptyList<String>(), http.requestedUrls)
        assertTrue(result.details.contains("data: hidden page · JSON: read"))
        assertTrue(result.details.contains("data: no file opened"))
    }

    @Test
    fun `rows from the hidden page carry its agent and cookies`() = runTest {
        val http = FakeExtractorHttpClient(probeResponder = { url, _ -> fileAnswer(url) })
        val hidden = SiteExtractionRequest(
            identity = identity(POST_ID),
            requestContext = BrowserRequestContext(
                pageUrl = identity(POST_ID).canonicalPageUrl,
                userAgent = "fixture-desktop-agent",
                cookie = "tt_chain_token=hidden-fixture",
            ),
            nowEpochMs = NOW,
            pageData = data(scriptItem(), SitePageDataSource.HIDDEN_PAGE),
        )

        val result = TikTokExtractor(http).extract(hidden) as SiteExtractionResult.Success

        assertEquals(emptyList<String>(), http.requestedUrls)
        assertTrue(result.candidates.all { it.requestContext.userAgent == "fixture-desktop-agent" })
        assertTrue(
            result.candidates.all { it.requestContext.cookie == "tt_chain_token=hidden-fixture" },
        )
        assertTrue(result.details.contains("answer: hidden page · 3 working qualities"))
    }

    @Test
    fun `a protected post's data ends the lookup without the page`() = runTest {
        val http = FakeExtractorHttpClient()
        val drm = scriptItem().replace("\"video\":{", "\"video\":{\"isDrm\":true,")

        val result = TikTokExtractor(http).extract(
            request(identity(POST_ID), data(drm, SitePageDataSource.TAB_SCRIPT)),
        ) as SiteExtractionResult.Failure

        assertEquals(SiteExtractionFailure.DRM_PROTECTED, result.reason)
        assertEquals(emptyList<String>(), http.requestedUrls)
    }

    @Test
    fun `a short link's lookup reads the page, its post id is not known yet`() = runTest {
        val short = SitePageIdentity(
            siteId = "tiktok",
            contentId = "ZMfixture",
            canonicalPageUrl = "https://vt.tiktok.com/ZMfixture/",
            requiresCanonicalResolution = true,
        )
        val http = FakeExtractorHttpClient()

        val result = TikTokExtractor(http).extract(
            request(short, data(scriptItem(), SitePageDataSource.TAB_SCRIPT)),
        )

        assertTrue(result is SiteExtractionResult.Failure)
        assertEquals(listOf(short.canonicalPageUrl), http.requestedUrls.distinct())
        assertTrue(
            (result as SiteExtractionResult.Failure).details
                .contains("data: tab · page script · not used (no post id in the link)"),
        )
    }

    @Test
    fun `data that is not JSON is not used and the page is read`() = runTest {
        val identity = identity(POST_ID)
        val http = FakeExtractorHttpClient.serving(
            url = identity.canonicalPageUrl,
            body = Fixtures.read("tiktok/universal_video.html"),
        )

        val result = TikTokExtractor(http).extract(
            request(identity, data("{\"id\":", SitePageDataSource.TAB_SCRIPT)),
        ) as SiteExtractionResult.Success

        assertEquals(listOf(identity.canonicalPageUrl), http.requestedUrls)
        assertEquals(3, result.candidates.size)
        assertTrue(result.details.any { it.startsWith("data: tab · page script · JSON: error") })
    }

    private fun scriptItem(): String = Fixtures.read("tiktok/page_data_script_item.json")

    private fun data(json: String, source: SitePageDataSource) = SitePageData(json.trim(), source)

    private fun fileAnswer(url: String): ExtractorProbeResult =
        if ("/stale-" in url) refused() else ExtractorProbeResult.Answered(206, 1_000_000L)

    private fun refused() =
        ExtractorProbeResult.Refused(SiteExtractionFailure.LOGIN_REQUIRED, 403)

    private fun identity(videoId: String, author: String = "fixture_user") = SitePageIdentity(
        siteId = "tiktok",
        contentId = videoId,
        canonicalPageUrl = "https://www.tiktok.com/@$author/video/$videoId",
    )

    private fun request(identity: SitePageIdentity, pageData: SitePageData?) =
        SiteExtractionRequest(
            identity = identity,
            requestContext = BrowserRequestContext(
                pageUrl = identity.canonicalPageUrl,
                userAgent = "fixture-tab-agent",
                cookie = TAB_COOKIE,
            ),
            nowEpochMs = NOW,
            pageData = pageData,
        )

    private companion object {
        const val POST_ID = "7311234567890123456"
        const val FEED_POST_ID = "7311234567890123457"
        const val TAB_COOKIE = "tt_chain_token=tab-fixture; ttwid=wid-fixture"
        const val NOW = 1_700_000_000_000L
    }
}