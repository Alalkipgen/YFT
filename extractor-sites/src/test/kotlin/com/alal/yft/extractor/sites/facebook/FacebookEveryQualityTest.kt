package com.alal.yft.extractor.sites.facebook

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.sites.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.sites.testing.Fixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P23: every quality of a Facebook video. A page with HD/SD files only, or with AVC video only
 * below the sizes it shows, asks the video's own page as Safari for the AVC ladder once; the
 * tracks of both pages merge without repeats, and the HD file states the picture and sound of
 * the manifest track it was made from.
 */
class FacebookEveryQualityTest {
    @Test
    fun `a share link to a page with HD and SD only gets the reel's AVC sizes and audio`() =
        runTest {
            val http = pages(
                session = "facebook/reel_hd_sd_only.html",
                sessionEndsOn = "$REEL_URL/",
                ladderUrl = REEL_URL,
            )

            val result = FacebookExtractor(http).extract(request(share()))
                as SiteExtractionResult.Success

            assertEquals(listOf(SHARE_URL, REEL_URL), http.requestedUrls)
            assertEquals(COOKIE, http.requestedHeaders[0]["Cookie"])
            assertNull(http.requestedHeaders[1]["Cookie"])
            assertEquals(
                FacebookPageIdentity.AVC_LADDER_USER_AGENT,
                http.requestedHeaders[1]["User-Agent"],
            )
            assertEquals(
                listOf("720p · HD", "SD", "720p", "360p", "Audio"),
                labels(result),
            )
            assertTrue(result.candidates.all { it.pageUrl == REEL_URL })
            // Media requests keep the browser's own identity and session.
            assertTrue(result.candidates.all { it.requestContext.cookie == COOKIE })
            assertTrue(
                result.details.toString(),
                result.details.containsAll(
                    listOf(
                        "page: HD/SD files only",
                        "ladder GET ${result.ladderSize()}: added AVC 360/720 + audio",
                        "parsed 5 renditions",
                    ),
                ),
            )
        }

    @Test
    fun `the HD file states the picture and sound of the track it was made from`() = runTest {
        val http = pages(
            session = "facebook/reel_hd_sd_only.html",
            sessionEndsOn = "$REEL_URL/",
            ladderUrl = REEL_URL,
        )

        val result = FacebookExtractor(http).extract(request(share()))
            as SiteExtractionResult.Success

        val (hd, sd, merged720) = result.candidates
        assertEquals("$CDN/fixture-avc-720.mp4", hd.mediaUrl.substringBefore('?'))
        assertEquals(1108, hd.width)
        assertEquals(720, hd.height)
        assertEquals(listOf("avc1.64001f", "mp4a.40.5"), hd.codecs)
        assertNull(hd.audioCompanion)
        // The address's own bitrate (video plus sound), never a manifest peak.
        assertEquals(373_059L, hd.bitrateBitsPerSecond)
        assertEquals(31_500L, hd.durationMillis)
        // No track has the SD file's path: the resolver reads its header (P3).
        assertNull(sd.height)
        assertTrue(sd.codecs.isEmpty())
        assertEquals(134_126L, sd.bitrateBitsPerSecond)
        // The merged 720p row: the average its address states, no size of its own.
        assertEquals(listOf("avc1.64001f"), merged720.codecs)
        assertEquals(312_410L, merged720.bitrateBitsPerSecond)
        assertNull(merged720.contentLengthBytes)
        assertEquals(57_372L, merged720.audioCompanion?.bitrateBitsPerSecond)
    }

    @Test
    fun `AVC at 360p below AV1 sizes asks the ladder, which adds AVC 720p once`() = runTest {
        val http = pages(
            session = "facebook/reel_avc360_av1.html",
            sessionEndsOn = "$REEL_URL/",
            ladderUrl = REEL_URL,
        )

        val result = FacebookExtractor(http).extract(request(share()))
            as SiteExtractionResult.Success

        assertEquals(listOf(SHARE_URL, REEL_URL), http.requestedUrls)
        assertEquals(
            listOf("720p · HD", "SD", "1080p", "720p", "360p", "Audio"),
            labels(result),
        )
        val merged = result.candidates.filter { it.audioCompanion != null }
        assertEquals(
            listOf(listOf("av01.0.08M.08"), listOf("avc1.64001f"), listOf("avc1.4d001e")),
            merged.map(MediaCandidate::codecs),
        )
        // The session page's own 360p track and sound stay; the ladder's repeats are dropped.
        assertTrue(merged.last().mediaUrl.endsWith("oh=fixture-session"))
        assertTrue(merged.all { it.audioCompanion!!.mediaUrl.endsWith("oh=fixture-session") })
        assertTrue(
            result.details.toString(),
            result.details.contains("ladder GET ${result.ladderSize()}: added AVC 720"),
        )
    }

    @Test
    fun `a watch link is asked for the ladder on the video page it redirected to`() = runTest {
        val identity = requireNotNull(FacebookUrls.identify(WATCH_URL))
        val videoPage = "https://www.facebook.com/FixturePage/videos/$VIDEO_ID/"
        val http = FakeExtractorHttpClient(
            responses = mapOf(
                WATCH_URL to FakeExtractorHttpClient.html(
                    Fixtures.read("facebook/reel_inline_dash.html"),
                    videoPage,
                ),
            ),
            getResponder = { url, headers ->
                FakeExtractorHttpClient.html(Fixtures.read(SAFARI_PAGE), url)
                    .takeIf { url == videoPage && isSafari(headers) }
            },
        )

        val result = FacebookExtractor(http).extract(request(identity))
            as SiteExtractionResult.Success

        // A /watch/ link has no public page: Safari gets it without the video.
        assertEquals(listOf(WATCH_URL, videoPage), http.requestedUrls)
        assertEquals(
            listOf("HD", "SD", "1080p", "720p", "360p", "Audio"),
            labels(result),
        )
        // One audio track for both pages, though each page signed its address differently.
        val sounds = result.candidates.mapNotNull { it.audioCompanion?.mediaUrl }.distinct()
        assertEquals(listOf("$CDN/fixture-audio-heaac.mp4?oe=F2A52380&oh=fixture"), sounds)
        assertTrue(
            result.details.toString(),
            result.details.contains("ladder GET ${result.ladderSize()}: added AVC 360/720"),
        )
    }

    @Test
    fun `a link that ends on a watch page asks the permalink the page states`() = runTest {
        val http = pages(
            session = "facebook/reel_hd_sd_only.html",
            sessionEndsOn = WATCH_URL,
            ladderUrl = REEL_URL,
        )

        val result = FacebookExtractor(http).extract(request(share()))
            as SiteExtractionResult.Success

        assertEquals(listOf(SHARE_URL, REEL_URL), http.requestedUrls)
        assertEquals(
            listOf("720p · HD", "SD", "720p", "360p", "Audio"),
            labels(result),
        )
    }

    @Test
    fun `a watch page without a permalink is never asked as Safari`() = runTest {
        val http = pages(
            session = "facebook/reel_inline_dash.html",
            sessionEndsOn = WATCH_URL,
            ladderUrl = WATCH_URL,
        )

        val result = FacebookExtractor(http).extract(request(share()))
            as SiteExtractionResult.Success

        assertEquals(listOf(SHARE_URL), http.requestedUrls)
        assertTrue(result.details.contains("ladder: skipped, no reel or video page to ask"))
    }

    /** [session] for the user's own request, the Safari ladder page at [ladderUrl]. */
    private fun pages(session: String, sessionEndsOn: String, ladderUrl: String) =
        FakeExtractorHttpClient(
            responses = mapOf(
                SHARE_URL to FakeExtractorHttpClient.html(Fixtures.read(session), sessionEndsOn),
            ),
            getResponder = { url, headers ->
                FakeExtractorHttpClient.html(Fixtures.read(SAFARI_PAGE), url)
                    .takeIf { url == ladderUrl && isSafari(headers) }
            },
        )

    private fun SiteExtractionResult.Success.ladderSize(): String =
        "200 (${Fixtures.read(SAFARI_PAGE).length} characters)"

    private fun labels(result: SiteExtractionResult.Success): List<String> =
        result.candidates.map { it.title.orEmpty().substringAfterLast("— ") }

    private fun isSafari(headers: Map<String, String>): Boolean =
        headers["User-Agent"] == FacebookPageIdentity.AVC_LADDER_USER_AGENT &&
            headers.keys.none { it.equals("Cookie", ignoreCase = true) }

    private fun share(): SitePageIdentity = requireNotNull(FacebookUrls.identify("$SHARE_URL/"))

    private fun request(identity: SitePageIdentity) = SiteExtractionRequest(
        identity = identity,
        requestContext = BrowserRequestContext(identity.canonicalPageUrl, "fixture-agent", COOKIE),
        nowEpochMs = 1_700_000_000_000,
    )

    private companion object {
        const val VIDEO_ID = "7180001112223340"
        const val REEL_URL = "https://www.facebook.com/reel/$VIDEO_ID"
        const val WATCH_URL = "https://www.facebook.com/watch/?v=$VIDEO_ID"
        const val SHARE_URL = "https://www.facebook.com/share/r/aBc123dEf"
        const val SAFARI_PAGE = "facebook/reel_safari_ladder.html"
        const val CDN = "https://video.example-cdn.test/o1/v"
        const val COOKIE = "c_user=0; xs=fixture-cookie"
    }
}
