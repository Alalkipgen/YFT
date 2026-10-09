package com.alal.yft.extractor.sites.tiktok

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.sites.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.sites.testing.Fixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P46 (owner's choice B): the public download service after TikTok's own pages. */
class TikTokDownloadServiceTest {
    @Test
    fun `a post both TikTok pages call private comes from the service without cookies`() =
        runTest {
            val http = client(serviceAnswer())

            val result = extractor(http).extract(request()) as SiteExtractionResult.Success

            assertEquals(listOf(HD, NORMAL), result.candidates.map { it.mediaUrl })
            assertEquals("Fixture caption — HD", result.candidates.first().title)
            assertEquals(5_000_000L, result.candidates.first().contentLengthBytes)
            assertEquals(12_000L, result.candidates.first().durationMillis)
            assertTrue(result.candidates.all { it.requestContext.cookie == null })
            // Only the post's address went to the service; no cookie, no TikTok Referer.
            val asked = http.requestedUrls.last()
            assertTrue(asked.startsWith(TikTokDownloadService.ENDPOINT + "?url="))
            assertTrue(asked.contains("7311234567890123456"))
            val serviceHeaders = http.requestedHeaders.last()
            assertNull(serviceHeaders["Cookie"])
            assertTrue(http.probedHeaders.all { it["Cookie"] == null && it["Referer"] == null })
            assertTrue(
                result.details.toString(),
                result.details.contains(
                    "download service: found · qualities: 2 · watermarked file: yes",
                ),
            )
            assertTrue(result.details.contains("answer: download service · 2 working qualities"))
        }

    @Test
    fun `the service's own paths become its https addresses`() = runTest {
        val http = client(serviceAnswer(hd = "/video/media/hdplay/7311234567890123456.mp4"))

        val result = extractor(http).extract(request()) as SiteExtractionResult.Success

        assertEquals(
            "https://www.tikwm.com/video/media/hdplay/7311234567890123456.mp4",
            result.candidates.first().mediaUrl,
        )
    }

    @Test
    fun `a service that finds nothing leaves TikTok's own answer with its Details`() = runTest {
        val http = client("{\"code\":-1,\"msg\":\"Url parsing is failed!\"}")

        val failure = extractor(http).extract(request()) as SiteExtractionResult.Failure

        assertEquals(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, failure.reason)
        assertTrue(failure.details.contains("download service: answer code -1"))
    }

    @Test
    fun `another post's answer is not used`() = runTest {
        val http = client(serviceAnswer().replace("7311234567890123456", "7311234567890000000"))

        val failure = extractor(http).extract(request()) as SiteExtractionResult.Failure

        assertTrue(failure.details.contains("download service: other post"))
    }

    @Test
    fun `the service is off unless the owner turned it on`() = runTest {
        val http = client(serviceAnswer())

        val failure = TikTokExtractor(http).extract(request()) as SiteExtractionResult.Failure

        assertEquals(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, failure.reason)
        assertFalse(http.requestedUrls.any { it.startsWith(TikTokDownloadService.SERVICE_ORIGIN) })
    }

    @Test
    fun `a photo post never goes to the service`() = runTest {
        val identity = identity("7311234567890123457")
        val http = FakeExtractorHttpClient(
            getResponder = { url, _ ->
                if (url == identity.canonicalPageUrl) {
                    FakeExtractorHttpClient.html(Fixtures.read("tiktok/universal_photo.html"), url)
                } else {
                    FakeExtractorHttpClient.html(serviceAnswer(), url)
                }
            },
        )

        val failure = extractor(http).extract(request(identity)) as SiteExtractionResult.Failure

        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, failure.reason)
        assertFalse(http.requestedUrls.any { it.startsWith(TikTokDownloadService.SERVICE_ORIGIN) })
    }

    private fun extractor(http: FakeExtractorHttpClient) =
        TikTokExtractor(http, askDownloadService = true)

    /** TikTok's pages say private; the service answers [service]; every file opens. */
    private fun client(service: String) = FakeExtractorHttpClient(
        getResponder = { url, _ ->
            when {
                url.startsWith(TikTokDownloadService.ENDPOINT) -> ExtractorHttpResult.Success(
                    statusCode = 200,
                    body = service,
                    finalUrl = url,
                    contentType = "application/json",
                )
                else -> FakeExtractorHttpClient.html(
                    Fixtures.read("tiktok/universal_private.html"),
                    url,
                )
            }
        },
        probeResponder = { _, _ -> ExtractorProbeResult.Answered(206, 5_000_000L) },
    )

    private fun serviceAnswer(hd: String = HD) =
        "{\"code\":0,\"msg\":\"success\",\"data\":{\"id\":\"7311234567890123456\"," +
            "\"title\":\"Fixture caption\",\"cover\":\"https://p16.example-cdn.test/c.jpg\"," +
            "\"duration\":12,\"play\":\"$NORMAL\",\"hdplay\":\"$hd\",\"wmplay\":\"$WATERMARKED\"," +
            "\"size\":2000000,\"hd_size\":5000000,\"author\":{\"unique_id\":\"fixture_user\"}}}"

    private fun identity(videoId: String = "7311234567890123456") = SitePageIdentity(
        siteId = "tiktok",
        contentId = videoId,
        canonicalPageUrl = "https://www.tiktok.com/@fixture_user/video/$videoId",
    )

    private fun request(identity: SitePageIdentity = identity()) = SiteExtractionRequest(
        identity = identity,
        requestContext = BrowserRequestContext(
            pageUrl = identity.canonicalPageUrl,
            userAgent = "fixture-agent",
            cookie = "sessionid=fixture-cookie",
        ),
        nowEpochMs = 1_700_000_000_000,
    )

    private companion object {
        const val HD = "https://v16m.example-cdn.test/hd/7311234567890123456.mp4"
        const val NORMAL = "https://v16m.example-cdn.test/sd/7311234567890123456.mp4"
        const val WATERMARKED = "https://v16m.example-cdn.test/wm/7311234567890123456.mp4"
    }
}
