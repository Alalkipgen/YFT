package com.alal.yft.extractor.master.contract

import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.MasterStage
import com.alal.yft.extractor.master.RecordingValidator
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.master.modules.youtube.testing.Fixtures
import com.alal.yft.extractor.sites.x.XExtractor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R8 X: Master asks X's embed-widget answer itself (the widget's token, no X cookie) and reads
 * it with the X key table. Parity with main's adapter on main's fixtures; posts the answer does
 * not show leave the page to the user's own playback.
 */
class XContractTest {
    @Test
    fun `the embed answer gives main's rows, probed and keyed to the app's video`() = runTest {
        val page = "https://twitter.com/fixture_user/status/$VIDEO?s=20"
        val identity = identify(page)
        val mainHttp = answering(answerUrl(VIDEO) to Fixtures.read("x/tweet_video.json"))
        val main = XExtractor(mainHttp).extract(mainRequest(identity, COOKIE))
            as SiteExtractionResult.Success
        val http = answering(answerUrl(VIDEO) to Fixtures.read("x/tweet_video.json"))
        val validator = RecordingValidator()
        val capture = CountingCapture()

        val result = contractEngine(http, validator, capture)
            .extract(contractRequest(page, identity, cookie = COOKIE))

        val rows = assertCovers("tweet_video", main.candidates, result, validator, "x:$VIDEO")
        assertEquals(MasterStage.CONTRACT, (result as MasterResult.Success).stage)
        assertEquals(0, capture.calls)
        assertEquals(mainHttp.requestedUrls, http.requestedUrls)
        val headers = http.requestedHeaders.single()
        assertNull("X's cookie never goes to the embed host", headers["Cookie"])
        assertEquals(AGENT, headers["User-Agent"])
        assertEquals("https://platform.twitter.com", headers["Origin"])
        assertEquals(listOf(1280, 852, 568), rows.map { it.height })
        assertEquals(listOf(720, 480, 320), rows.map { it.width })
        rows.forEach { row ->
            assertEquals(MediaKind.DIRECT, row.kind)
            assertNull(row.requestContext.cookie)
            assertEquals("Morning run by the river, fixture clip", row.title)
            assertEquals(31_533L, row.durationMillis)
            assertTrue(row.thumbnailUrl.orEmpty().startsWith("https://pbs.twimg.com/"))
        }
        assertFalse("the playlist backs up MP4 files only", rows.any { it.kind == MediaKind.HLS })
    }

    @Test
    fun `a video link picks that video of the post, as main does`() = runTest {
        listOf(
            "https://x.com/fixture_user/status/$MIXED/video/3" to listOf(720),
            "https://x.com/fixture_user/status/$MIXED" to listOf(1080, 270),
        ).forEach { (page, heights) ->
            val identity = identify(page)
            val main = XExtractor(answering(answerUrl(MIXED) to Fixtures.read("x/tweet_mixed.json")))
                .extract(mainRequest(identity)) as SiteExtractionResult.Success
            val validator = RecordingValidator()

            val result = contractEngine(
                answering(answerUrl(MIXED) to Fixtures.read("x/tweet_mixed.json")), validator,
            ).extract(contractRequest(page, identity))

            val rows = assertCovers(page, main.candidates, result, validator, "x:$MIXED")
            assertEquals(page, heights, rows.map { it.height })
            assertFalse(page, rows.any { "evil.example.test" in it.mediaUrl })
        }
    }

    @Test
    fun `quoted, GIF and playlist-only posts match main`() = runTest {
        mapOf(
            QUOTED to "x/tweet_quoted.json",
            GIF to "x/tweet_gif.json",
            HLS_ONLY to "x/tweet_hls_only.json",
        ).forEach { (id, fixture) ->
            // A `/video/2` link names the post's own media, never the quoted post's.
            val page = "https://x.com/someone/status/$id/video/2"
            val identity = identify(page)
            val main = XExtractor(answering(answerUrl(id) to Fixtures.read(fixture)))
                .extract(mainRequest(identity)) as SiteExtractionResult.Success
            val validator = RecordingValidator()

            val result = contractEngine(answering(answerUrl(id) to Fixtures.read(fixture)), validator)
                .extract(contractRequest(page, identity))

            val rows = assertCovers(fixture, main.candidates, result, validator, "x:$id")
            assertEquals(fixture, main.candidates.map { it.kind }, rows.map { it.kind })
            assertEquals(fixture, main.candidates.map { it.durationMillis }, rows.map { it.durationMillis })
        }
    }

    @Test
    fun `posts the answer does not show leave the page to the user's playback`() = runTest {
        listOf(
            "x/tweet_photos.json" to PHOTOS,
            "x/tweet_tombstone.json" to VIDEO,
        ).forEach { (fixture, id) ->
            val page = "https://x.com/someone/status/$id"
            val identity = identify(page)
            val main = XExtractor(answering(answerUrl(id) to Fixtures.read(fixture)))
                .extract(mainRequest(identity))
            val validator = RecordingValidator()
            val capture = CountingCapture()

            val result = contractEngine(answering(answerUrl(id) to Fixtures.read(fixture)), validator, capture)
                .extract(contractRequest(page, identity, SiteExtractionFailure.NO_MEDIA_FOUND))

            assertEquals(
                fixture, SiteExtractionFailure.NO_MEDIA_FOUND,
                (main as SiteExtractionResult.Failure).reason,
            )
            assertTrue("$fixture: $result", result is MasterResult.NeedsPlayback)
            assertEquals(fixture, 1, capture.calls)
            assertTrue(fixture, validator.seen.isEmpty())
        }
        val missing = FakeExtractorHttpClient(
            responses = mapOf(answerUrl(VIDEO) to ExtractorHttpResult.Failure(SiteExtractionFailure.NO_MEDIA_FOUND, 404)),
        )
        val capture = CountingCapture()
        val result = contractEngine(missing, capture = capture)
            .extract(contractRequest("https://x.com/a/status/$VIDEO", identify("https://x.com/a/status/$VIDEO")))
        assertTrue(result is MasterResult.NeedsPlayback)
        assertEquals(1, capture.calls)
    }

    @Test
    fun `an answer for another post is not this post's`() = runTest {
        val identity = identify("https://x.com/fixture_user/status/$OTHER")
        val validator = RecordingValidator()

        val result = contractEngine(
            answering(answerUrl(OTHER) to Fixtures.read("x/tweet_video.json")), validator,
        ).extract(contractRequest("https://x.com/fixture_user/status/$OTHER", identity))

        assertTrue(result is MasterResult.NeedsPlayback)
        assertTrue(validator.seen.isEmpty())
    }

    @Test
    fun `the token is the embed widget's own`() {
        mapOf(
            "1785999999999999999" to "4buvua9lpjr",
            "1683920951807971329" to "42y6zv7ufp",
            "1460323737035677698" to "3jfqq1vhqna",
            "20" to "6dq1a2xwd93",
        ).forEach { (id, token) -> assertEquals(id, token, WidgetToken.token(id)) }
        mapOf(
            0.5 to "0.i",
            1e21 to "5v1j4f4ds7c000",
            0.1 to "0.3lllllllllm",
            35.99999999999999 to "z.zzzzzzzzz",
            -255.5 to "-73.i",
        ).forEach { (value, text) -> assertEquals(text, WidgetToken.radixString(value, 36)) }
    }

    private fun identify(page: String) = checkNotNull(XExtractor(answering()).identify(page))

    private companion object {
        const val VIDEO = "1785999999999999999"
        const val MIXED = "1786000000000000001"
        const val GIF = "1786000000000000010"
        const val QUOTED = "1786000000000000020"
        const val PHOTOS = "1786000000000000030"
        const val HLS_ONLY = "1786000000000000040"
        const val OTHER = "1786000000000000099"
        const val COOKIE = "auth_token=secret"

        fun answerUrl(id: String) =
            "https://cdn.syndication.twimg.com/tweet-result?id=$id&token=${WidgetToken.token(id)}&lang=en"
    }
}
