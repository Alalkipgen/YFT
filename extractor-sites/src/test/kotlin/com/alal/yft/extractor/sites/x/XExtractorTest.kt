package com.alal.yft.extractor.sites.x

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.sites.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.sites.testing.Fixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XExtractorTest {
    private val agent = "Mozilla/5.0 (Linux; Android 14) Fixture"

    private fun request(url: String) = SiteExtractionRequest(
        identity = requireNotNull(XUrls.identify(url)),
        requestContext = BrowserRequestContext(url, agent, cookie = "auth_token=secret"),
        nowEpochMs = 1_000L,
    )

    private fun client(id: String, fixture: String) = FakeExtractorHttpClient(
        mapOf(
            XSyndication.url(id) to
                FakeExtractorHttpClient.json(Fixtures.read(fixture), XSyndication.url(id)),
        ),
    )

    @Test
    fun `every MP4 file of the post's video is a row with its sound and size`() = runTest {
        val http = client("1785999999999999999", "x/tweet_video.json")

        val result = XExtractor(http).extract(
            request("https://twitter.com/fixture_user/status/1785999999999999999?s=20"),
        ) as SiteExtractionResult.Success

        assertEquals(listOf(XSyndication.url("1785999999999999999")), http.requestedUrls)
        val headers = http.requestedHeaders.single()
        assertEquals(agent, headers["User-Agent"])
        assertNull("X's cookie never goes to the embed host", headers["Cookie"])
        assertEquals(listOf(1280, 852, 568), result.candidates.map { it.height })
        result.candidates.forEach { candidate ->
            assertEquals(MediaKind.DIRECT, candidate.kind)
            assertEquals("video/mp4", candidate.mimeType)
            assertNull(candidate.audioCompanion)
            assertNull(candidate.requestContext.cookie)
            assertEquals(agent, candidate.requestContext.userAgent)
            assertEquals("Morning run by the river, fixture clip", candidate.title)
            assertEquals(31_533L, candidate.durationMillis)
            assertEquals(
                "https://x.com/fixture_user/status/1785999999999999999",
                candidate.pageUrl,
            )
        }
        assertEquals(
            listOf(
                "embed answer GET 200 (${Fixtures.read("x/tweet_video.json").length} characters)",
                "answer: video 1, 1 in the post · 3 MP4 files (1280/852/568)",
            ),
            result.details,
        )
    }

    @Test
    fun `a video link picks that video of the post`() = runTest {
        val http = client("1786000000000000001", "x/tweet_mixed.json")
        val extractor = XExtractor(http)

        val third = extractor.extract(
            request("https://x.com/fixture_user/status/1786000000000000001/video/3"),
        ) as SiteExtractionResult.Success
        val first = extractor.extract(
            request("https://x.com/fixture_user/status/1786000000000000001"),
        ) as SiteExtractionResult.Success

        assertEquals(listOf(720), third.candidates.map { it.height })
        assertEquals("answer: video 3, 2 in the post · 1 MP4 files (720)", third.details.last())
        assertEquals(listOf(1080, 270), first.candidates.map { it.height })
    }

    @Test
    fun `a GIF states no picture, so its header shows it has no sound`() = runTest {
        val result = XExtractor(client("1786000000000000010", "x/tweet_gif.json")).extract(
            request("https://x.com/gif_maker/status/1786000000000000010"),
        ) as SiteExtractionResult.Success

        val gif = result.candidates.single()
        assertNull(gif.height)
        assertNull(gif.bitrateBitsPerSecond)
        assertEquals("Gif Maker on X", gif.title)
    }

    @Test
    fun `a quoted post's video is used when the post has none`() = runTest {
        val result = XExtractor(client("1786000000000000020", "x/tweet_quoted.json")).extract(
            request("https://x.com/quoter/status/1786000000000000020"),
        ) as SiteExtractionResult.Success

        assertEquals(listOf(360), result.candidates.map { it.height })
        assertTrue(result.details.last().startsWith("answer: the quoted post's video"))
    }

    @Test
    fun `a post with only a playlist offers the playlist`() = runTest {
        val result = XExtractor(client("1786000000000000040", "x/tweet_hls_only.json")).extract(
            request("https://x.com/broadcaster/status/1786000000000000040"),
        ) as SiteExtractionResult.Success

        assertEquals(MediaKind.HLS, result.candidates.single().kind)
    }

    @Test
    fun `hidden posts and photo posts keep the generic detector`() = runTest {
        val hidden = XExtractor(client("1786000000000000050", "x/tweet_tombstone.json")).extract(
            request("https://x.com/someone/status/1786000000000000050"),
        ) as SiteExtractionResult.Failure
        val photos = XExtractor(client("1786000000000000030", "x/tweet_photos.json")).extract(
            request("https://x.com/photographer/status/1786000000000000030"),
        ) as SiteExtractionResult.Failure
        val missing = XExtractor(FakeExtractorHttpClient()).extract(
            request("https://x.com/someone/status/1786000000000000060"),
        ) as SiteExtractionResult.Failure

        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, hidden.reason)
        assertEquals(XExtractor.HIDDEN_MESSAGE, hidden.message)
        assertEquals(XExtractor.PHOTOS_MESSAGE, photos.message)
        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, missing.reason)
        assertEquals(404, missing.httpStatusCode)
        listOf(hidden, photos, missing).forEach { assertTrue(it.allowsGenericFallback) }
    }

    @Test
    fun `a changed answer and a network failure are reported as such`() = runTest {
        val id = "1786000000000000070"
        val changed = XExtractor(
            FakeExtractorHttpClient(
                mapOf(
                    XSyndication.url(id) to
                        FakeExtractorHttpClient.json("[1,2]", XSyndication.url(id)),
                ),
            ),
        ).extract(request("https://x.com/someone/status/$id")) as SiteExtractionResult.Failure
        val offline = XExtractor(
            FakeExtractorHttpClient(
                fallback = ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK),
            ),
        ).extract(request("https://x.com/someone/status/$id")) as SiteExtractionResult.Failure

        assertEquals(SiteExtractionFailure.RESPONSE_CHANGED, changed.reason)
        assertTrue(changed.allowsGenericFallback)
        assertEquals(SiteExtractionFailure.NETWORK, offline.reason)
        assertFalse(offline.details.isEmpty())
    }
}
