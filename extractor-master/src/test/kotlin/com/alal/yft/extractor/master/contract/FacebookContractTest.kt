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
import com.alal.yft.extractor.sites.facebook.FacebookExtractor
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R8 Facebook: Master asks the embedded-video player page (`plugins/video.php`) as desktop
 * Safari without the session and reads it with the Facebook key table, which also reads main's
 * page shapes. Parity with main's adapter on main's fixtures; only DRM stops, because an
 * anonymous embed's wording says nothing about what the signed-in user may watch.
 */
class FacebookContractTest {
    @Test
    fun `the embed page gives its files, asked as Safari without the session`() = runTest {
        val identity = identify(WATCH)
        val http = answering(embedUrl(identity) to EMBED, contentType = "text/html")
        val validator = RecordingValidator()
        val capture = CountingCapture()

        val result = contractEngine(http, validator, capture)
            .extract(contractRequest(WATCH, identity, cookie = COOKIE))

        val rows = (result as MasterResult.Success).result.candidates
        assertEquals(MasterStage.CONTRACT, result.stage)
        assertEquals(0, capture.calls)
        assertEquals(
            listOf(
                "https://video.example-cdn.test/v/embed-hd.mp4?oe=F2A52380&oh=fixture",
                "https://video.example-cdn.test/v/embed-sd.mp4?oe=F2A52380&oh=fixture",
            ),
            rows.map { it.mediaUrl },
        )
        rows.forEach { row ->
            assertEquals(KEY, row.videoId)
            assertEquals(PageMediaRole.MAIN, row.pageRole)
            assertNull(row.requestContext.cookie)
        }
        assertEquals(
            listOf("https://www.facebook.com/plugins/video.php?href=" + encode(identity.canonicalPageUrl)),
            http.requestedUrls,
        )
        val headers = http.requestedHeaders.single()
        assertTrue(headers["User-Agent"].orEmpty().contains("Version/17.5 Safari"))
        assertNull("the embed is asked without the session", headers["Cookie"])
        assertEquals("https://www.facebook.com/", headers["Referer"])
    }

    @Test
    fun `main's page shapes give main's rows, never the suggested video's`() = runTest {
        mapOf(
            "facebook/watch_progressive.html" to WATCH,
            "facebook/reel_legacy_fields.html" to "$REEL/7180001112223334",
            "facebook/reel_hd_sd_only.html" to "$REEL/7180001112223340",
            "facebook/reel_inline_dash.html" to "$REEL/7180001112223340",
            "facebook/public_reel_empty_licences.html" to "$REEL/1603698891196107",
        ).forEach { (fixture, page) ->
            val identity = identify(page)
            val body = Fixtures.read(fixture)
            val main = FacebookExtractor(FakeExtractorHttpClient.serving(identity.canonicalPageUrl, body))
                .extract(mainRequest(identity, COOKIE))
            val validator = RecordingValidator()

            val result = contractEngine(answering(embedUrl(identity) to body, contentType = "text/html"), validator)
                .extract(contractRequest(page, identity))

            val expected = (main as? SiteExtractionResult.Success)?.candidates
            assertTrue("$fixture: main $main", expected != null)
            val rows = assertCovers(fixture, expected!!, result, validator, "facebook:${identity.contentId}")
            assertFalse(fixture, rows.any { "suggested" in it.mediaUrl })
        }
    }

    @Test
    fun `protected media stop before any capture or media check`() = runTest {
        val drmEmbed = EMBED.replace("\"videoLicenseUriMap\":{}", "\"videoLicenseUriMap\":{\"widevine\":\"https://licence.example.test\"}")
        listOf(
            Fixtures.read("facebook/drm_video.html") to identify("${WATCH_BASE}2223334445556667"),
            drmEmbed to identify(WATCH),
        ).forEach { (body, identity) ->
            val validator = RecordingValidator()
            val capture = CountingCapture()

            val result = contractEngine(answering(embedUrl(identity) to body, contentType = "text/html"), validator, capture)
                .extract(contractRequest(identity.canonicalPageUrl, identity))

            assertEquals(SiteExtractionFailure.DRM_PROTECTED, (result as MasterResult.Failure).reason)
            assertEquals(0, capture.calls)
            assertTrue(validator.seen.isEmpty())
        }
    }

    @Test
    fun `the anonymous embed's wording never decides access, capture does`() = runTest {
        mapOf(
            "facebook/login_required.html" to VIDEO,
            "facebook/content_unavailable.html" to VIDEO,
            "facebook/geo_restricted.html" to "6667778889990001",
            "facebook/no_media.html" to "3334445556667778",
            "facebook/malformed_payload.html" to "8889990001112223",
            "facebook/insecure_renditions.html" to "4445556667778889",
        ).forEach { (fixture, id) ->
            val identity = identify("$WATCH_BASE$id")
            val validator = RecordingValidator()
            val capture = CountingCapture()

            val result = contractEngine(
                answering(embedUrl(identity) to Fixtures.read(fixture), contentType = "text/html"),
                validator, capture,
            ).extract(contractRequest(identity.canonicalPageUrl, identity))

            assertTrue("$fixture: $result", result is MasterResult.NeedsPlayback)
            assertEquals(fixture, 1, capture.calls)
            assertTrue(fixture, validator.seen.isEmpty())
        }
    }

    @Test
    fun `a renamed field is found by shape, another video's embed gives nothing`() = runTest {
        val identity = identify(WATCH)
        val renamed = EMBED.replace("\"hd_src\"", "\"hd_src_v2\"").replace("\"sd_src\"", "\"sd_src_v2\"")
        val validator = RecordingValidator()

        val found = contractEngine(answering(embedUrl(identity) to renamed, contentType = "text/html"), validator)
            .extract(contractRequest(WATCH, identity))

        val rows = (found as MasterResult.Success).result.candidates
        assertTrue(rows.any { "embed-hd.mp4" in it.mediaUrl })
        rows.forEach { assertEquals(KEY, it.videoId) }

        // Main's changed page: a renamed delivery key, found by shape under the video's own ID.
        val changed = identify("${WATCH_BASE}7778889990001112")
        val shaped = contractEngine(
            answering(embedUrl(changed) to Fixtures.read("facebook/changed_markup.html"), contentType = "text/html"),
        ).extract(contractRequest(changed.canonicalPageUrl, changed))
        assertEquals(
            listOf("https://video.example-cdn.test/v/unknown-shape.mp4"),
            (shaped as MasterResult.Success).result.candidates.map { it.mediaUrl },
        )

        val foreign = EMBED.replace(VIDEO, "9988776655443322")
        val other = RecordingValidator()
        val none = contractEngine(answering(embedUrl(identity) to foreign, contentType = "text/html"), other)
            .extract(contractRequest(WATCH, identity))
        assertTrue(none is MasterResult.NeedsPlayback)
        assertTrue(other.seen.isEmpty())
    }

    @Test
    fun `a share link is never asked about`() = runTest {
        val share = identify("https://www.facebook.com/share/v/1Q3kAyptrS/")
        val http = answering()

        contractEngine(http).extract(contractRequest(share.canonicalPageUrl, share))

        assertTrue(share.requiresCanonicalResolution)
        assertTrue(http.requestedUrls.isEmpty())
    }

    private fun identify(page: String): SitePageIdentity =
        checkNotNull(FacebookExtractor(answering()).identify(page)) { page }

    private companion object {
        const val VIDEO = "1234567890123456"
        const val WATCH_BASE = "https://www.facebook.com/watch/?v="
        const val WATCH = "$WATCH_BASE$VIDEO"
        const val REEL = "https://www.facebook.com/reel"
        const val KEY = "facebook:$VIDEO"
        const val COOKIE = "c_user=0; xs=fixture-cookie"

        fun encode(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

        fun embedUrl(identity: SitePageIdentity) =
            "https://www.facebook.com/plugins/video.php?href=" + encode(identity.canonicalPageUrl)

        /** The embed page's shape (live 2026-10-10), with fixture addresses. */
        val EMBED = """
            <!DOCTYPE html><html><head><title>Facebook</title></head><body>
            <script nonce="fixture">requireLazy(["TimeSliceImpl","ServerJS"],function(TimeSlice,ServerJS){var s=(new ServerJS());s.handle({"instances":[["__inst_fixture",["VideoConfig"],[{"ad_client_token":null,"video_id":"$VIDEO","videoData":[{"is_hls":false,"video_id":"$VIDEO","hd_src":"https:\/\/video.example-cdn.test\/v\/embed-hd.mp4?oe=F2A52380&oh=fixture","sd_src":"https:\/\/video.example-cdn.test\/v\/embed-sd.mp4?oe=F2A52380&oh=fixture","dash_manifest":null,"original_height":720,"original_width":1280,"videoLicenseUriMap":{},"graphApiVideoLicenseUri":null,"OzDrmHelper":null}]}],1]]});});</script>
            </body></html>
        """.trimIndent()
    }
}
