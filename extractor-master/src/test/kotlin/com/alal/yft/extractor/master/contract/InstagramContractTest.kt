package com.alal.yft.extractor.master.contract

import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.MasterStage
import com.alal.yft.extractor.master.RecordingValidator
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.master.modules.youtube.testing.Fixtures
import com.alal.yft.extractor.sites.instagram.InstagramExtractor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R8 Instagram: Master asks the public embed page (`/p/{code}/embed/captioned/`) as the browser,
 * without the user's cookies, and reads its `contextJSON` post with the Instagram key table,
 * which also reads main's other answers (app API, GraphQL, the signed-in page). Parity with
 * main's adapter on main's fixtures; a photo post stops, while a missing, walled or fileless
 * post goes to the user's own playback, because an anonymous embed decides no access.
 */
class InstagramContractTest {
    @Test
    fun `the embed page gives its file, asked as the browser without cookies`() = runTest {
        val identity = identify(REEL)
        val http = answering(embedUrl(identity) to fixture("embed_captioned.html"), contentType = "text/html")
        val validator = RecordingValidator()
        val capture = CountingCapture()

        val result = contractEngine(http, validator, capture)
            .extract(contractRequest(REEL, identity, cookie = SESSION))

        val row = (result as MasterResult.Success).result.candidates.single()
        assertEquals(MasterStage.CONTRACT, result.stage)
        assertEquals(0, capture.calls)
        assertTrue(row.mediaUrl, "embed_prog640.mp4" in row.mediaUrl)
        assertEquals(KEY, row.videoId)
        assertEquals(PageMediaRole.MAIN, row.pageRole)
        assertEquals(640, row.width)
        assertEquals(1136, row.height)
        assertEquals(15_200L, row.durationMillis)
        assertEquals("Embed caption", row.title)
        assertEquals("https://instagram.fxyz1-1.fna.fbcdn.net/v/e.jpg", row.thumbnailUrl)
        assertNotNull("the file's own expiry", row.expiresAtEpochMs)
        assertNull("files open without cookies", row.requestContext.cookie)
        assertEquals(listOf("https://www.instagram.com/p/$CODE/embed/captioned/"), http.requestedUrls)
        val headers = http.requestedHeaders.single()
        assertEquals("the browser's own agent", AGENT, headers["User-Agent"])
        assertNull("the embed is asked without cookies", headers["Cookie"])
        assertEquals("https://www.instagram.com/", headers["Referer"])
        assertEquals(listOf(row.mediaUrl), validator.seen.map { it.mediaUrl })
    }

    @Test
    fun `main's answers give main's rows, never another post's`() = runTest {
        listOf(
            Triple("embed_captioned.html", REEL, null),
            Triple("page_signed_in.html", REEL, SESSION),
            Triple("graphql_reel.json", REEL, null),
            Triple("api_info_reel.json", REEL, SESSION),
            Triple("graphql_carousel.json", "$SIDECAR?img_index=3", null),
            Triple("graphql_carousel.json", "$SIDECAR?img_index=1", null),
        ).forEach { (name, page, cookie) ->
            val identity = identify(page)
            val body = fixture(name)
            val main = InstagramExtractor(mainClient(name, body)).extract(mainRequest(identity, cookie))
            val expected = (main as? SiteExtractionResult.Success)?.candidates
            assertTrue("$name: main $main", expected != null)
            val validator = RecordingValidator()

            val result = contractEngine(answering(embedUrl(identity) to asPage(body), contentType = "text/html"), validator)
                .extract(contractRequest(identity.canonicalPageUrl, identity))

            val rows = assertCovers("$name $page", expected!!, result, validator, "instagram:${identity.contentId}")
            assertFalse("$name: another post's file", rows.any { "other.mp4" in it.mediaUrl })
            assertEquals("$name: main's title", expected.first().title, rows.first().title)
        }
    }

    @Test
    fun `a carousel link picks its item, else the first video, as main does`() = runTest {
        val body = asPage(fixture("graphql_carousel.json"))
        mapOf(
            "?img_index=3" to Triple("side3.mp4", 4_000L, "c3.jpg"),
            "?img_index=1" to Triple("side2.mp4", 7_500L, "c2.jpg"),
            "" to Triple("side2.mp4", 7_500L, "c2.jpg"),
        ).forEach { (query, expected) ->
            val identity = identify("$SIDECAR$query")

            val result = contractEngine(answering(embedUrl(identity) to body, contentType = "text/html"))
                .extract(contractRequest(identity.canonicalPageUrl, identity))

            val row = (result as MasterResult.Success).result.candidates.single()
            assertTrue(query, expected.first in row.mediaUrl)
            assertEquals(query, expected.second, row.durationMillis)
            assertTrue(query, row.thumbnailUrl.orEmpty().endsWith(expected.third))
            assertEquals(query, "Fixture Creator on Instagram", row.title)
            assertEquals(query, "instagram:C9fixtSIDE1", row.videoId)
        }
    }

    @Test
    fun `photo posts stop before any capture or media check, as main says`() = runTest {
        val carousel = fixture("graphql_carousel.json")
            .replace("\"is_video\":true", "\"is_video\":false")
            .replace(Regex("\"video_url\":\"[^\"]+\","), "")
        val api = """{"items":[{"pk":"1","code":"C9fixtPHOTO","media_type":1,"image_versions2":{"candidates":[{"width":1080,"height":1080,"url":"https://instagram.fxyz1-1.fna.fbcdn.net/v/p.jpg"}]},"user":{"username":"fixture.creator"}}],"status":"ok"}"""
        val embed = fixture("embed_captioned.html")
            .replace("GraphVideo", "GraphImage").replace("\\\"is_video\\\":true", "\\\"is_video\\\":false")
            .replace(Regex("""\\"video_url\\":\\"[^\\]+\\","""), "")
        listOf(
            Triple("graphql_photo.json", fixture("graphql_photo.json"), PHOTO),
            Triple("graphql_carousel.json", carousel, SIDECAR),
            Triple("api_info_reel.json", api, PHOTO),
            Triple("embed_captioned.html", embed, REEL),
        ).forEach { (kind, body, page) ->
            val identity = identify(page)
            val main = InstagramExtractor(mainClient(kind, body)).extract(mainRequest(identity, SESSION))
            assertEquals("$kind: main", SiteExtractionFailure.NO_MEDIA_FOUND, (main as SiteExtractionResult.Failure).reason)
            val validator = RecordingValidator()
            val capture = CountingCapture()

            val result = contractEngine(answering(embedUrl(identity) to asPage(body), contentType = "text/html"), validator, capture)
                .extract(contractRequest(identity.canonicalPageUrl, identity))

            assertEquals(kind, SiteExtractionFailure.NO_MEDIA_FOUND, (result as MasterResult.Failure).reason)
            assertEquals(kind, 0, capture.calls)
            assertTrue(kind, validator.seen.isEmpty())
        }
    }

    @Test
    fun `a missing, walled or fileless post goes to the user's own playback`() = runTest {
        val identity = identify(REEL)
        val embed = embedUrl(identity)
        val fileless = """{"data":{"xdt_shortcode_media":{"__typename":"XDTGraphVideo","shortcode":"$CODE","is_video":true,"display_url":"https://instagram.fxyz1-1.fna.fbcdn.net/v/d.jpg","owner":{"username":"fixture.creator"}}},"status":"ok"}"""
        val login = "https://www.instagram.com/accounts/login/?next=%2Fp%2F$CODE%2Fembed%2Fcaptioned%2F"
        listOf(
            answering(embed to fixture("embed_no_video.html"), contentType = "text/html"),
            answering(embed to asPage(fixture("graphql_login.json")), contentType = "text/html"),
            answering(embed to asPage(fixture("graphql_null.json")), contentType = "text/html"),
            answering(embed to asPage(fileless), contentType = "text/html"),
            // A login page is no answer, whatever it holds.
            FakeExtractorHttpClient(
                mapOf(embed to ExtractorHttpResult.Success(200, fixture("embed_captioned.html"), login, "text/html")),
            ),
            answering(embed to fixture("embed_captioned.html").replace(CODE, "C9fixtOTHER"), contentType = "text/html"),
        ).forEachIndexed { index, http ->
            val validator = RecordingValidator()
            val capture = CountingCapture()

            val result = contractEngine(http, validator, capture).extract(contractRequest(REEL, identity))

            assertTrue("case $index: $result", result is MasterResult.NeedsPlayback)
            assertEquals("case $index", 1, capture.calls)
            assertTrue("case $index", validator.seen.isEmpty())
        }
    }

    @Test
    fun `a renamed field is found by shape`() = runTest {
        val identity = identify(REEL)
        val renamed = fixture("embed_captioned.html").replace("\\\"video_url\\\"", "\\\"video_src\\\"")

        val found = contractEngine(answering(embedUrl(identity) to renamed, contentType = "text/html"))
            .extract(contractRequest(REEL, identity))

        val rows = (found as MasterResult.Success).result.candidates
        assertTrue("$rows", rows.single().mediaUrl.contains("embed_prog640.mp4"))
        assertEquals(KEY, rows.single().videoId)
    }

    private fun identify(page: String): SitePageIdentity =
        checkNotNull(InstagramExtractor(answering()).identify(page)) { page }

    private companion object {
        const val CODE = "C9fixtREEL1"
        const val REEL = "https://www.instagram.com/reel/$CODE/"
        const val SIDECAR = "https://www.instagram.com/p/C9fixtSIDE1/"
        const val PHOTO = "https://www.instagram.com/p/C9fixtPHOTO/"
        const val KEY = "instagram:$CODE"
        const val SESSION = "csrftoken=csrfFixture; sessionid=555%3Asecret; ds_user_id=555"

        fun fixture(name: String) = Fixtures.read("instagram/$name")

        fun embedUrl(identity: SitePageIdentity) =
            "https://www.instagram.com/p/${identity.contentId}/embed/captioned/"

        /** A JSON answer as a page carries it: a JSON script, its `</` escaped. */
        fun asPage(body: String) = if (!body.trimStart().startsWith("{")) body else
            "<!DOCTYPE html><html><head><script type=\"application/json\">" +
                body.replace("</", "<\\/") + "</script></head><body></body></html>"

        /** Main asks its sources in turn; only the one the fixture is answers. */
        fun mainClient(name: String, body: String) = FakeExtractorHttpClient(
            getResponder = { url, _ ->
                val answers = when (name.substringBefore('_')) {
                    "api" -> "/api/v1/media/" in url
                    "graphql" -> "/graphql/query/" in url
                    "embed" -> url.endsWith("/embed/captioned/")
                    else -> listOf("/api/", "/graphql/", "/embed/").none { it in url }
                }
                when {
                    !answers -> null
                    body.trimStart().startsWith("{") -> FakeExtractorHttpClient.json(body, url)
                    else -> FakeExtractorHttpClient.html(body, url)
                }
            },
        )
    }
}