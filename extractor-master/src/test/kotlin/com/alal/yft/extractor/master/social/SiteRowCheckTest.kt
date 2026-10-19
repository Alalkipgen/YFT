package com.alal.yft.extractor.master.social

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.master.social.SiteRowCheck.Verdict.FAIL
import com.alal.yft.extractor.master.social.SiteRowCheck.Verdict.PASS
import com.alal.yft.extractor.master.social.SiteRowCheck.Verdict.UNKNOWN
import com.alal.yft.extractor.master.social.SiteWaysTest.Companion.BROWSER_AGENT
import com.alal.yft.extractor.master.social.SiteWaysTest.Companion.FB_REEL
import com.alal.yft.extractor.master.social.SiteWaysTest.Companion.TT_POST
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Social plan S0b: the one-row check (V, [SiteRowCheck]): one byte of one row with the download's
 * headers, only on the site's media hosts; HLS or another host is unknown, never a pass; a DASH
 * row checks its tallest video file; the best row is the tallest, then the highest bitrate.
 */
class SiteRowCheckTest {
    @Test
    fun `a whole file on the site's media host passes, asked once with the download's headers`() = runTest {
        val http = FakeExtractorHttpClient(probeResponder = { _, _ -> ExtractorProbeResult.Answered(206, 1_000L) })
        val row = row(X_FILE, height = 1280)

        val checked = SiteRowCheck(http).check(SiteWays.X, row, NOW_MS)

        assertEquals(SiteRowCheck.Checked(PASS, null, 206, requests = 1), checked)
        assertEquals(listOf(X_FILE), http.probedUrls)
        assertEquals(listOf(row.requestContext.replayHeaders()), http.probedHeaders)
        assertEquals(BROWSER_AGENT, http.probedHeaders.single()["User-Agent"])
        assertEquals(X_POST, http.probedHeaders.single()["Referer"])
        assertTrue("nothing but the file is asked", http.requestedUrls.isEmpty())
    }

    @Test
    fun `a refused file fails with its reason and status, and a 429 is a rate limit`() = runTest {
        val refused = FakeExtractorHttpClient(
            probeResponder = { _, _ -> ExtractorProbeResult.Refused(SiteExtractionFailure.HTTP_STATUS, 403) },
        )
        val limited = FakeExtractorHttpClient(
            probeResponder = { _, _ -> ExtractorProbeResult.Refused(SiteExtractionFailure.RATE_LIMITED, 429) },
        )

        assertEquals(
            SiteRowCheck.Checked(FAIL, "HTTP_STATUS", 403, requests = 1),
            SiteRowCheck(refused).check(SiteWays.X, row(X_FILE), NOW_MS),
        )
        assertEquals(
            SiteRowCheck.Checked(FAIL, "RATE_LIMITED", 429, requests = 1, rateLimited = true),
            SiteRowCheck(limited).check(SiteWays.X, row(X_FILE), NOW_MS),
        )
    }

    @Test
    fun `HLS, another host, a page address or plain HTTP are unknown, and nothing is asked`() = runTest {
        val http = FakeExtractorHttpClient(probeResponder = { _, _ -> ExtractorProbeResult.Answered(206) })
        val check = SiteRowCheck(http)

        assertEquals(
            SiteRowCheck.Checked(UNKNOWN, SiteRowCheck.HLS),
            check.check(SiteWays.X, row("https://video.twimg.com/ext_tw_video/1/pu/pl/fixture.m3u8", MediaKind.UNKNOWN), NOW_MS),
        )
        assertEquals(
            SiteRowCheck.Checked(UNKNOWN, SiteRowCheck.HLS),
            check.check(SiteWays.X, row(X_FILE, MediaKind.HLS), NOW_MS),
        )
        assertEquals(
            SiteRowCheck.Checked(UNKNOWN, SiteRowCheck.ANOTHER_HOST),
            check.check(SiteWays.TIKTOK, row("https://v16m.example-cdn.test/hd/fixture.mp4"), NOW_MS),
        )
        assertEquals(
            "TikTok's own page is not one of its video files",
            SiteRowCheck.Checked(UNKNOWN, SiteRowCheck.ANOTHER_HOST),
            check.check(SiteWays.TIKTOK, row(TT_POST), NOW_MS),
        )
        assertEquals(
            SiteRowCheck.Checked(UNKNOWN, SiteRowCheck.ANOTHER_HOST),
            check.check(SiteWays.X, row(X_FILE.replace("https://", "http://")), NOW_MS),
        )
        assertTrue(http.probedUrls.isEmpty())
        assertEquals(
            SiteRowCheck.Checked(PASS, null, 206, requests = 1),
            check.check(SiteWays.TIKTOK, row(TT_FILE), NOW_MS),
        )
    }

    @Test
    fun `an expired row fails unasked, and a client that cannot check files leaves it unknown`() = runTest {
        val unsupported = FakeExtractorHttpClient()

        assertEquals(
            SiteRowCheck.Checked(FAIL, SiteRowCheck.EXPIRED),
            SiteRowCheck(unsupported).check(SiteWays.X, row(X_FILE).copy(expiresAtEpochMs = NOW_MS), NOW_MS),
        )
        assertEquals(
            SiteRowCheck.Checked(UNKNOWN, SiteRowCheck.NO_FILE_CHECK),
            SiteRowCheck(unsupported).check(SiteWays.X, row(X_FILE).copy(expiresAtEpochMs = NOW_MS + 1), NOW_MS),
        )
    }

    @Test
    fun `a DASH row reads its manifest on the media host and checks its tallest video file`() = runTest {
        val http = FakeExtractorHttpClient(
            getResponder = { url, _ -> FakeExtractorHttpClient.html(MANIFEST, url).takeIf { url == FB_MANIFEST } },
            probeResponder = { _, _ -> ExtractorProbeResult.Answered(206, 9_000L) },
        )
        val row = row(FB_MANIFEST, MediaKind.DASH, page = FB_REEL).copy(mimeType = "application/dash+xml")

        val checked = SiteRowCheck(http).check(SiteWays.FACEBOOK, row, NOW_MS)

        assertEquals(SiteRowCheck.Checked(PASS, null, 206, requests = 2), checked)
        assertEquals(listOf(FB_MANIFEST), http.requestedUrls)
        assertEquals(listOf(SiteRowCheck.MAX_MANIFEST_BYTES), http.requestedBodyLimits)
        assertEquals("the relative 720 file, resolved against the manifest", listOf(FB_720), http.probedUrls)
        val headers = row.requestContext.replayHeaders()
        assertEquals(listOf(headers), http.requestedHeaders)
        assertEquals(listOf(headers), http.probedHeaders)
    }

    @Test
    fun `a DASH manifest of pieces, a file on another host, or a refused manifest`() = runTest {
        val pieces = MANIFEST.replace(Regex("<SegmentBase[^>]*>(.*?)</SegmentBase>", RegexOption.DOT_MATCHES_ALL), "") 
            .replace("<BaseURL>", "<SegmentTemplate media=\"seg-\$Number\$.m4s\" startNumber=\"1\"/><BaseURL>")
        val elsewhere = MANIFEST.replace("v720.mp4", "https://cdn.example.test/v720.mp4")
        val row = row(FB_MANIFEST, MediaKind.DASH, page = FB_REEL)
        val probe: (String, Map<String, String>) -> ExtractorProbeResult = { _, _ -> ExtractorProbeResult.Answered(206) }

        fun serving(body: String) = FakeExtractorHttpClient(
            getResponder = { url, _ -> FakeExtractorHttpClient.html(body, url) },
            probeResponder = probe,
        )

        val noFile = serving(pieces)
        assertEquals(
            SiteRowCheck.Checked(UNKNOWN, SiteRowCheck.NO_FILE, 200, requests = 1),
            SiteRowCheck(noFile).check(SiteWays.FACEBOOK, row, NOW_MS),
        )
        val other = serving(elsewhere)
        assertEquals(
            SiteRowCheck.Checked(UNKNOWN, SiteRowCheck.ANOTHER_HOST, null, requests = 1),
            SiteRowCheck(other).check(SiteWays.FACEBOOK, row, NOW_MS),
        )
        assertTrue(noFile.probedUrls.isEmpty() && other.probedUrls.isEmpty())
        val missing = FakeExtractorHttpClient(probeResponder = probe)
        assertEquals(
            SiteRowCheck.Checked(FAIL, "HTTP_STATUS", 404, requests = 1),
            SiteRowCheck(missing).check(SiteWays.FACEBOOK, row, NOW_MS),
        )
        val limited = FakeExtractorHttpClient(
            fallback = ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429),
            probeResponder = probe,
        )
        val checked = SiteRowCheck(limited).check(SiteWays.FACEBOOK, row, NOW_MS)
        assertTrue(checked.rateLimited)
        assertEquals(FAIL, checked.verdict)
    }

    @Test
    fun `the best row is the tallest picture, then the highest bitrate, else the first`() {
        val sound = row("https://video.twimg.com/a.mp4").copy(mimeType = "audio/mp4", bitrateBitsPerSecond = 128_000)
        val low = row("https://video.twimg.com/360.mp4", height = 360).copy(bitrateBitsPerSecond = 1_000_000)
        val tall = row("https://video.twimg.com/720a.mp4", height = 720).copy(bitrateBitsPerSecond = 2_000_000)
        val tallRich = row("https://video.twimg.com/720b.mp4", height = 720).copy(bitrateBitsPerSecond = 3_000_000)
        val unsized = row("https://video.twimg.com/unsized.mp4")

        assertSame(tallRich, SiteRowCheck.best(listOf(sound, low, tall, unsized, tallRich)))
        assertSame(unsized, SiteRowCheck.best(listOf(sound, unsized)))
        val quiet = sound.copy(bitrateBitsPerSecond = 64_000)
        assertSame("sound-only rows when all are", sound, SiteRowCheck.best(listOf(quiet, sound)))
        val twin = tall.copy(mediaUrl = "https://video.twimg.com/720c.mp4")
        assertSame("a tie keeps the answer's order", tall, SiteRowCheck.best(listOf(tall, twin)))
        assertNull(SiteRowCheck.best(emptyList()))
        assertEquals(720, SiteRowCheck.tallest(listOf(sound, low, tallRich, unsized)))
        assertNull(SiteRowCheck.tallest(listOf(sound, unsized)))
    }

    @Test
    fun `the check's answer holds no address`() = runTest {
        val http = FakeExtractorHttpClient(probeResponder = { _, _ -> ExtractorProbeResult.Answered(206) })

        val text = SiteRowCheck(http).check(SiteWays.X, row(X_FILE), NOW_MS).toString()

        assertFalse(text, "twimg" in text || "http" in text)
    }

    private fun row(
        url: String,
        kind: MediaKind = MediaKind.DIRECT,
        height: Int? = null,
        page: String = X_POST,
    ) = MediaCandidate(
        pageUrl = page,
        mediaUrl = url,
        sources = setOf(CandidateSource.MANIFEST),
        kind = kind,
        mimeType = if (kind == MediaKind.DIRECT) "video/mp4" else null,
        requestContext = BrowserRequestContext(page, BROWSER_AGENT, null),
        height = height,
    )

    private companion object {
        const val NOW_MS = 1_791_000_000_000L
        const val X_POST = "https://x.com/fixture_user/status/1785999999999999999"
        const val X_FILE = "https://video.twimg.com/ext_tw_video/1785999990000000000/pu/vid/avc1/720x1280/a720.mp4?tag=12"
        const val TT_FILE = "https://v16-webapp.tiktok.com/video/tos/useast2a/fixture/?mime_type=video_mp4"
        const val FB_MANIFEST = "https://video.xx.fbcdn.net/v/t42/manifest.mpd?oh=fixture"
        const val FB_720 = "https://video.xx.fbcdn.net/v/t42/v720.mp4?_nc_cat=1&oh=fixture"

        /** A Facebook-style manifest: two whole video files (360 absolute, 720 relative) and a sound file. */
        val MANIFEST = """
            <?xml version="1.0" encoding="UTF-8"?>
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" mediaPresentationDuration="PT10S">
              <Period>
                <AdaptationSet mimeType="video/mp4" segmentAlignment="true">
                  <Representation id="v1" codecs="avc1.4d401e" bandwidth="400000" width="360" height="640">
                    <BaseURL>https://video.xx.fbcdn.net/v/t42/v360.mp4?_nc_cat=1&amp;oh=fixture</BaseURL>
                    <SegmentBase indexRange="800-900"><Initialization range="0-799"/></SegmentBase>
                  </Representation>
                  <Representation id="v2" codecs="avc1.64001f" bandwidth="1500000" width="720" height="1280">
                    <BaseURL>v720.mp4?_nc_cat=1&amp;oh=fixture</BaseURL>
                    <SegmentBase indexRange="800-900"><Initialization range="0-799"/></SegmentBase>
                  </Representation>
                </AdaptationSet>
                <AdaptationSet mimeType="audio/mp4">
                  <Representation id="a1" codecs="mp4a.40.2" bandwidth="64000">
                    <BaseURL>audio.mp4?oh=fixture</BaseURL>
                    <SegmentBase indexRange="800-900"><Initialization range="0-799"/></SegmentBase>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
    }
}
