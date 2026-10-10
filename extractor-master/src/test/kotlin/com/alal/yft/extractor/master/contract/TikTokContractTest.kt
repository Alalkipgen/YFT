package com.alal.yft.extractor.master.contract

import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.MasterStage
import com.alal.yft.extractor.master.RecordingValidator
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.master.modules.youtube.testing.Fixtures
import com.alal.yft.extractor.sites.tiktok.TikTokExtractor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R8 TikTok: Master asks the embed player page (`embed/v2/{id}`) as the browser, without the
 * user's cookies, and reads it with the TikTok key table, which also reads main's page shapes.
 * Parity with main's adapter on main's fixtures; only DRM and a photo post stop, because an
 * anonymous embed's wording says nothing about what the signed-in user may watch.
 */
class TikTokContractTest {
    @Test
    fun `the embed page gives its file, asked as the browser without cookies`() = runTest {
        val identity = identify(PAGE)
        val http = answering(embedUrl(identity) to EMBED, contentType = "text/html")
        val validator = RecordingValidator()
        val capture = CountingCapture()

        val result = contractEngine(http, validator, capture)
            .extract(contractRequest(PAGE, identity, cookie = COOKIE))

        val row = (result as MasterResult.Success).result.candidates.single()
        assertEquals(MasterStage.CONTRACT, result.stage)
        assertEquals(0, capture.calls)
        assertEquals(EMBED_FILE, row.mediaUrl)
        assertEquals(KEY, row.videoId)
        assertEquals(PageMediaRole.MAIN, row.pageRole)
        assertEquals(720, row.width)
        assertEquals(1280, row.height)
        assertEquals(26_000L, row.durationMillis)
        assertEquals("Have you ever heard the sonification of space?", row.title)
        assertEquals("https://p19-common-sign.example-cdn.test/obj/cover-embed", row.thumbnailUrl)
        assertNull("files open without cookies", row.requestContext.cookie)
        assertEquals(listOf("https://www.tiktok.com/embed/v2/$VIDEO"), http.requestedUrls)
        val headers = http.requestedHeaders.single()
        assertEquals("the browser's own agent", AGENT, headers["User-Agent"])
        assertNull("the embed is asked without cookies", headers["Cookie"])
        assertEquals("https://www.tiktok.com/", headers["Referer"])
        assertFalse("the music is never a video row", validator.seen.any { "music" in it.mediaUrl })
    }

    @Test
    fun `main's page shapes give main's rows`() = runTest {
        mapOf(
            "tiktok/universal_video.html" to VIDEO,
            "tiktok/sigi_video.html" to "7311234567890123458",
            "tiktok/universal_reflow_video.html" to VIDEO,
            "tiktok/live_desktop_video_detail.html" to VIDEO,
        ).forEach { (fixture, id) ->
            val identity = identify("$BASE$id")
            val body = Fixtures.read(fixture)
            val main = TikTokExtractor(FakeExtractorHttpClient.serving(identity.canonicalPageUrl, body))
                .extract(mainRequest(identity, COOKIE))
            val validator = RecordingValidator()

            val result = contractEngine(answering(embedUrl(identity) to body, contentType = "text/html"), validator)
                .extract(contractRequest(identity.canonicalPageUrl, identity))

            val expected = (main as? SiteExtractionResult.Success)?.candidates
            assertTrue("$fixture: main $main", expected != null)
            val rows = assertCovers(fixture, expected!!, result, validator, "tiktok:$id")
            assertFalse("$fixture: watermarked file beside others", rows.any { "download" in it.mediaUrl })
        }

        // Main's quality fields: the best file first, with its bitrate, size and codec.
        val identity = identify(PAGE)
        val validator = RecordingValidator()
        contractEngine(
            answering(embedUrl(identity) to Fixtures.read("tiktok/universal_video.html"), contentType = "text/html"),
            validator,
        ).extract(contractRequest(PAGE, identity))
        val best = validator.seen.first()
        assertEquals("https://v16-webapp.example-cdn.test/video/play/fixture-1080.mp4?expire=4102444800", best.mediaUrl)
        assertEquals(2_496_000L, best.bitrateBitsPerSecond)
        assertEquals(5_308_416L, best.contentLengthBytes)
        assertEquals(listOf("avc1"), best.codecs)
        assertEquals("Sunrise over the harbour #fixture", best.title)
        assertEquals(17_000L, best.durationMillis)
        assertEquals("https://p16-sign.example-cdn.test/obj/cover-fixture", best.thumbnailUrl)
    }

    @Test
    fun `a post with only the watermarked file offers it with main's label`() = runTest {
        val identity = identify(PAGE)
        val page = Fixtures.read("tiktok/universal_reflow_video.html")
            .replace(Regex("\"playAddr\":\"[^\"]*\","), "")
        val main = TikTokExtractor(FakeExtractorHttpClient.serving(identity.canonicalPageUrl, page))
            .extract(mainRequest(identity, COOKIE)) as SiteExtractionResult.Success

        val result = contractEngine(answering(embedUrl(identity) to page, contentType = "text/html"))
            .extract(contractRequest(PAGE, identity))

        val row = (result as MasterResult.Success).result.candidates.single()
        assertEquals(main.candidates.single().mediaUrl, row.mediaUrl)
        assertEquals("Sunrise over the harbour #fixture \u2014 With TikTok watermark", row.title)
    }

    @Test
    fun `protected media and photo posts stop before any capture or media check`() = runTest {
        val drmEmbed = EMBED.replace("\"videoMeta\":", "\"isDrm\":true,\"videoMeta\":")
        val photoEmbed = EMBED.replace("\"urls\":[\"$EMBED_FILE\"]", "\"urls\":[]")
            .replace("\"imagePostInfo\":null", "\"imagePostInfo\":{\"images\":[{\"displayImage\":{}}]}")
        listOf(
            Triple(Fixtures.read("tiktok/drm_video.html"), "${BASE}7311234567890123459", SiteExtractionFailure.DRM_PROTECTED),
            Triple(drmEmbed, PAGE, SiteExtractionFailure.DRM_PROTECTED),
            Triple(Fixtures.read("tiktok/universal_photo.html"), "${BASE}7311234567890123457", SiteExtractionFailure.NO_MEDIA_FOUND),
            Triple(photoEmbed, "https://www.tiktok.com/@fixture_user/photo/$VIDEO", SiteExtractionFailure.NO_MEDIA_FOUND),
        ).forEach { (body, page, reason) ->
            val identity = identify(page)
            val validator = RecordingValidator()
            val capture = CountingCapture()

            val result = contractEngine(answering(embedUrl(identity) to body, contentType = "text/html"), validator, capture)
                .extract(contractRequest(identity.canonicalPageUrl, identity))

            assertEquals(page, reason, (result as MasterResult.Failure).reason)
            assertEquals(page, 0, capture.calls)
            assertTrue(page, validator.seen.isEmpty())
        }
    }

    @Test
    fun `the anonymous embed's wording never decides access, capture does`() = runTest {
        val missing = "<!DOCTYPE html><html><body><script id=\"__FRONTITY_CONNECT_STATE__\" " +
            "type=\"application/json\">{\"source\":{\"data\":{\"/embed/v2/$VIDEO\":{\"isReady\":true," +
            "\"errorCode\":10204,\"errorStatus\":400,\"isError\":true,\"pageName\":\"video_v2_error\"}}}}" +
            "</script></body></html>"
        listOf(
            Fixtures.read("tiktok/universal_private.html") to VIDEO,
            Fixtures.read("tiktok/universal_login_required.html") to VIDEO,
            Fixtures.read("tiktok/universal_geo_restricted.html") to VIDEO,
            Fixtures.read("tiktok/malformed_payload.html") to VIDEO,
            Fixtures.read("tiktok/insecure_renditions.html") to "7311234567890123460",
            Fixtures.read("tiktok/live_home_page.html") to VIDEO,
            missing to VIDEO,
        ).forEachIndexed { index, (body, id) ->
            val identity = identify("$BASE$id")
            val validator = RecordingValidator()
            val capture = CountingCapture()

            val result = contractEngine(answering(embedUrl(identity) to body, contentType = "text/html"), validator, capture)
                .extract(contractRequest(identity.canonicalPageUrl, identity))

            assertTrue("case $index: $result", result is MasterResult.NeedsPlayback)
            assertEquals("case $index", 1, capture.calls)
            assertTrue("case $index", validator.seen.isEmpty())
        }
    }

    @Test
    fun `a renamed field is found by shape, another post's data gives nothing`() = runTest {
        val identity = identify(PAGE)
        val renamed = EMBED.replace("\"urls\"", "\"playUrls\"")

        val found = contractEngine(answering(embedUrl(identity) to renamed, contentType = "text/html"))
            .extract(contractRequest(PAGE, identity))

        val rows = (found as MasterResult.Success).result.candidates
        assertEquals(listOf(EMBED_FILE), rows.map { it.mediaUrl })
        rows.forEach { assertEquals(KEY, it.videoId) }

        listOf(
            EMBED.replace(VIDEO, "7399999999999999999"),
            Fixtures.read("tiktok/changed_markup.html"),
        ).forEach { body ->
            val validator = RecordingValidator()
            val none = contractEngine(answering(embedUrl(identity) to body, contentType = "text/html"), validator)
                .extract(contractRequest(PAGE, identity))
            assertTrue("$none", none is MasterResult.NeedsPlayback)
            assertTrue(validator.seen.isEmpty())
        }
    }

    @Test
    fun `a short link is never asked about`() = runTest {
        val short = identify("https://vm.tiktok.com/ZMabcdef1/")
        val http = answering()

        contractEngine(http).extract(contractRequest(short.canonicalPageUrl, short))

        assertTrue(short.requiresCanonicalResolution)
        assertTrue(http.requestedUrls.isEmpty())
    }

    private fun identify(page: String): SitePageIdentity =
        checkNotNull(TikTokExtractor(answering()).identify(page)) { page }

    private companion object {
        const val VIDEO = "7311234567890123456"
        const val BASE = "https://www.tiktok.com/@fixture_user/video/"
        const val PAGE = "$BASE$VIDEO"
        const val KEY = "tiktok:$VIDEO"
        const val COOKIE = "sessionid=fixture-cookie; tt_chain_token=fixture"
        const val EMBED_FILE =
            "https://v45.example-cdn.test/video/tos/useast5/embed.mp4?mime_type=video_mp4&expire=4102444800"

        fun embedUrl(identity: SitePageIdentity) = "https://www.tiktok.com/embed/v2/${identity.contentId}"

        /** The embed page's shape (live 2026-10-10), with fixture addresses. */
        val EMBED = """
            <!DOCTYPE html><html><head><title>TikTok</title></head><body>
            <script id="__FRONTITY_CONNECT_STATE__" type="application/json">{"source":{"data":{"/embed/v2/$VIDEO":{"isFetching":false,"isReady":true,"route":"/embed/v2/$VIDEO/","code":200,"isError":false,"pageName":"video_v2","videoData":{"itemInfos":{"id":"$VIDEO","text":"Have you ever heard the sonification of space?","covers":["https://p19-common-sign.example-cdn.test/obj/cover-embed"],"coversOrigin":["https://p16-common-sign.example-cdn.test/obj/origin-embed"],"video":{"urls":["$EMBED_FILE"],"videoMeta":{"width":720,"height":1280,"ratio":26,"duration":26}},"secret":false,"forFriend":false},"authorInfos":{"uniqueId":"fixture_user"},"musicInfos":{"musicId":"7000000000000000000","playUrl":["https://v16m.example-cdn.test/video/tos/useast5/music.mp4?expire=4102444800"]},"imagePostInfo":null}},"strategy":{"page_context":{"biz_name":"Embed"}}}}}</script>
            </body></html>
        """.trimIndent()
    }
}