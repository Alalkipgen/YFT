package com.alal.yft.extractor.sites.tiktok

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.sites.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.sites.testing.Fixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P39 regression proof. These tests use only the adapter API that P38 already had (one
 * constructor argument, no file checks), so they also run against the P38 adapter, where each
 * one fails: TikTok's live page shapes (2026-10-09) that P38 read as a changed page.
 */
class TikTokRegressionTest {
    @Test
    fun `a script that names the data script earlier does not hide the data`() = runTest {
        val identity = identity(POST_ID)
        val page = liveDesktop().replaceFirst(
            "<head>",
            "<head><script id=\"preload-order\">[\"__UNIVERSAL_DATA_FOR_REHYDRATION__\"]</script>",
        )
        val http = FakeExtractorHttpClient.serving(identity.canonicalPageUrl, page)

        val result = TikTokExtractor(http).extract(request(identity))

        assertTrue(result.toString(), result is SiteExtractionResult.Success)
        assertEquals(3, (result as SiteExtractionResult.Success).candidates.size)
    }

    @Test
    fun `text after the data object inside its script is cut, not read as a changed page`() =
        runTest {
            val identity = identity(POST_ID)
            val page = liveDesktop()
            val dataScript = page.indexOf("id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\"")
            val end = page.indexOf("</script>", dataScript)
            val http = FakeExtractorHttpClient.serving(
                url = identity.canonicalPageUrl,
                body = page.substring(0, end) + ";" + page.substring(end),
            )

            val result = TikTokExtractor(http).extract(request(identity))

            assertTrue(result.toString(), result is SiteExtractionResult.Success)
        }

    @Test
    fun `a phone page without the post is followed by the desktop page`() = runTest {
        val identity = identity(POST_ID)
        val phone = Fixtures.read("tiktok/malformed_payload.html")
        val http = FakeExtractorHttpClient(
            getResponder = { url, headers ->
                when {
                    url != identity.canonicalPageUrl -> null
                    headers["User-Agent"].orEmpty().contains("Windows NT") ->
                        FakeExtractorHttpClient.html(liveDesktop(), url)

                    else -> FakeExtractorHttpClient.html(phone, url)
                }
            },
        )

        val result = TikTokExtractor(http).extract(request(identity))

        assertEquals(2, http.requestedUrls.size)
        assertTrue(result.toString(), result is SiteExtractionResult.Success)
    }

    @Test
    fun `a short link that lands on the home page is not reported as a changed page`() =
        runTest {
            val identity = requireNotNull(TikTokUrls.identify("https://vt.tiktok.com/ZSfixture1/"))
            val http = FakeExtractorHttpClient.serving(
                url = identity.canonicalPageUrl,
                body = Fixtures.read("tiktok/live_home_page.html"),
                finalUrl = "https://www.tiktok.com/",
            )

            val failure = TikTokExtractor(http).extract(request(identity))
                as SiteExtractionResult.Failure

            assertEquals(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, failure.reason)
            assertFalse(failure.allowsGenericFallback)
        }

    @Test
    fun `an unexpected error becomes a failure that names its class`() = runTest {
        val identity = identity(POST_ID)
        val http = FakeExtractorHttpClient(getResponder = { _, _ -> error("fixture failure") })

        val result = runCatching { TikTokExtractor(http).extract(request(identity)) }

        val failure = result.getOrNull() as? SiteExtractionResult.Failure
        assertTrue(result.toString(), failure != null)
        assertEquals(SiteExtractionFailure.MALFORMED_RESPONSE, failure?.reason)
        assertTrue(
            failure?.details.toString(),
            failure?.details.orEmpty().any { it.contains("IllegalStateException") },
        )
    }

    private fun liveDesktop(): String = Fixtures.read("tiktok/live_desktop_video_detail.html")

    private fun identity(videoId: String) = SitePageIdentity(
        siteId = "tiktok",
        contentId = videoId,
        canonicalPageUrl = "https://www.tiktok.com/@fixture_user/video/$videoId",
    )

    private fun request(identity: SitePageIdentity) = SiteExtractionRequest(
        identity = identity,
        requestContext = BrowserRequestContext(
            pageUrl = identity.canonicalPageUrl,
            userAgent = "fixture-agent",
            cookie = null,
        ),
        nowEpochMs = 1_700_000_000_000,
    )

    private companion object {
        const val POST_ID = "7311234567890123456"
    }
}
