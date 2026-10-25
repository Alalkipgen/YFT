package com.alal.yft.extractor.master.modules.youtube

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.api.json.path
import com.alal.yft.extractor.master.CaptureResult
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.MasterStage
import com.alal.yft.extractor.master.PlaybackCaptureProvider
import com.alal.yft.extractor.master.RecordingValidator
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.master.modules.youtube.testing.Fixtures
import com.alal.yft.extractor.sites.youtube.YouTubeExtractor as MainYouTubeExtractor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R6 passthrough parity: main's YouTube extractor (no script runner, no token provider, no
 * cookie: what Master runs with) and Master's module give the same rows, order, labels, sizes
 * and details on main's fixtures, asking the same requests. Cipher and SABR give 0 rows.
 */
class MasterYouTubeParityTest {
    @Test
    fun `Master's module matches main on every YouTube fixture scenario`() = runTest {
        var successes = 0
        for ((name, scenario) in scenarios()) {
            val mainHttp = scenario()
            val masterHttp = scenario()
            val main = MainYouTubeExtractor(mainHttp).extract(request(cookie = null))
            val master = MasterYouTubeModule(masterHttp).extract(request(cookie = COOKIE))

            assertEquals("$name: same requests", mainHttp.requestedUrls, masterHttp.requestedUrls)
            assertEquals("$name: same posts", mainHttp.postedBodies, masterHttp.postedBodies)
            assertEquals("$name: same headers", mainHttp.postedHeaders, masterHttp.postedHeaders)
            assertFalse(masterHttp.postedHeaders.any { it.keys.any(::isCookie) })
            assertFalse(masterHttp.requestedHeaders.any { it.keys.any(::isCookie) })
            when (main) {
                is SiteExtractionResult.Success -> {
                    successes++
                    master as SiteExtractionResult.Success
                    assertEquals("$name: rows", main.candidates.map(::tagged), master.candidates)
                    assertEquals("$name: details", main.details, master.details)
                }
                is SiteExtractionResult.Failure -> assertEquals("$name: failure", main, master)
            }
        }
        assertTrue("parity must cover real rows, saw $successes", successes >= 4)
    }

    @Test
    fun `the complete visionOS answer keeps main's labels and sizes`() = runTest {
        val result = MasterYouTubeModule(scenarios().getValue("visionOS complete")())
            .extract(request()) as SiteExtractionResult.Success

        assertEquals(
            listOf(
                "Fixture video title — 1080p",
                "Fixture video title — 720p",
                "Fixture video title — Audio 131 kbps",
            ),
            result.candidates.map(MediaCandidate::title),
        )
        assertEquals(
            listOf(84_361_446L, 29_905_327L, 3_449_447L),
            result.candidates.map(MediaCandidate::contentLengthBytes),
        )
        assertTrue(MasterYouTubeClients.CANARY_DETAIL in result.details)
    }

    @Test
    fun `cipher and SABR fixtures give no row`() = runTest {
        for (name in listOf("cipher", "cipher ladder", "SABR only")) {
            val result = MasterYouTubeModule(scenarios().getValue(name)()).extract(request())
            assertTrue("$name gave $result", result is SiteExtractionResult.Failure)
        }
    }

    @Test
    fun `a bot check is terminal through the engine, with no capture and no probe`() = runTest {
        val capture = CountingCapture()
        val validator = RecordingValidator()
        val engine = MasterFallbackEngine(
            validator, capture, MasterPolicy(enabled = true),
            modules = listOf(MasterYouTubeModule(scenarios().getValue("bot check")())),
        )

        val result = engine.extract(
            MasterRequest(CANONICAL, 1, NOW, SiteExtractionFailure.UNSUPPORTED_URL),
        )

        assertEquals(SiteExtractionFailure.BOT_CHECK, (result as MasterResult.Failure).reason)
        assertEquals(0, capture.calls)
        assertTrue(validator.seen.isEmpty())
    }

    @Test
    fun `the engine answers a YouTube page through the module only`() = runTest {
        val capture = CountingCapture()
        val engine = MasterFallbackEngine(
            RecordingValidator(), capture, MasterPolicy(enabled = true),
            modules = listOf(MasterYouTubeModule(scenarios().getValue("visionOS complete")())),
        )

        val result = engine.extract(
            MasterRequest(SHARED_LINK, 1, NOW, SiteExtractionFailure.UNSUPPORTED_URL),
        ) as MasterResult.Success
        val after = engine.extract(
            MasterRequest(SHARED_LINK, 1, NOW, SiteExtractionFailure.NO_MEDIA_FOUND),
        )

        assertEquals(MasterStage.SITE_MODULE, result.stage)
        assertEquals(3, result.result.candidates.size)
        assertTrue(result.result.candidates.all { it.videoId == "youtube:$VIDEO_ID" })
        assertEquals(MasterResult.Skipped(SiteExtractionFailure.NO_MEDIA_FOUND), after)
        assertEquals(0, capture.calls)
    }

    @Test
    fun `the client table is versioned and asks visionOS first`() {
        assertEquals("VISIONOS", MasterYouTubeClients.ORDER.first().clientName)
        assertTrue(MasterYouTubeClients.ORDER.none { it.usesPlayerScript || it.usesPoToken })
        assertEquals(
            "table ${MasterYouTubeClients.TABLE_VERSION} (${MasterYouTubeClients.SOURCE})",
            MasterYouTubeClients.describe().first(),
        )
    }

    private fun tagged(row: MediaCandidate) = row.copy(videoId = "youtube:$VIDEO_ID")

    private fun isCookie(name: String) =
        name.equals("Cookie", ignoreCase = true) || name.equals("Authorization", ignoreCase = true)

    /** Each scenario builds a fresh fake, so both readers see identical answers. */
    private fun scenarios(): Map<String, () -> FakeExtractorHttpClient> = linkedMapOf(
        "visionOS complete" to {
            client(
                page = watchPage(ownStreams()),
                visionOs = fixture("player_visionos.json"),
                android = fixture("player_android.json"),
                embedded = fixture("player_ok.json"),
            )
        },
        "visionOS ladder" to {
            client(
                page = watchPage(ownStreams()),
                visionOs = fixture("player_embed_refused.json"),
                visionOsAgain = fixture("player_visionos_ladder.json"),
                android = fixture("player_android.json"),
            )
        },
        "embedded" to {
            client(
                page = watchPage(ownStreams()),
                embedded = fixture("player_ok.json"),
                android = fixture("player_android.json"),
            )
        },
        "4K" to { client(page = watchPage(null), embedded = fixture("player_4k.json")) },
        "4K AV1 only" to {
            client(page = watchPage(null), embedded = fixture("player_4k_av1_only.json"))
        },
        "Android only" to {
            client(page = watchPage(null), android = fixture("player_android.json"))
        },
        "cipher" to {
            client(
                page = watchPage(fixture("player_cipher.json")),
                embedded = fixture("player_embed_refused.json"),
            )
        },
        "cipher ladder" to {
            client(
                page = watchPage(fixture("player_cipher_ladder.json")),
                embedded = fixture("player_embed_refused.json"),
            )
        },
        "SABR only" to {
            client(
                page = watchPage(fixture("player_sabr_only.json")),
                embedded = fixture("player_embed_refused.json"),
            )
        },
        "bot check" to {
            client(
                page = watchPage(fixture("player_bot_check.json")),
                visionOs = fixture("player_bot_check.json"),
                embedded = fixture("player_bot_check.json"),
            )
        },
        "DRM" to {
            client(page = watchPage(fixture("player_drm.json")), visionOs = fixture("player_drm.json"))
        },
        "geo" to { client(page = watchPage(fixture("player_geo.json"))) },
        "private" to { client(page = watchPage(null), visionOs = fixture("player_private.json")) },
        "unavailable" to { client(page = watchPage(fixture("player_unavailable.json"))) },
        "live" to { client(page = watchPage(fixture("player_live.json"))) },
        "malformed" to { client(page = watchPage(fixture("player_malformed.json"))) },
        "age gate" to { client(page = watchPage(fixture("player_age_gate.json"))) },
        "no status" to { client(page = watchPage(null), own = fixture("player_no_status.json")) },
        "changed markup" to { client(page = fixture("changed_markup.html")) },
        "page unreachable" to { client(page = null) },
    )

    private fun client(
        page: String?,
        embedded: String? = null,
        own: String? = null,
        visionOs: String? = null,
        android: String? = null,
        visionOsAgain: String? = null,
    ): FakeExtractorHttpClient = FakeExtractorHttpClient(
        responses = page
            ?.let { mapOf(PAGE_FETCH to FakeExtractorHttpClient.html(it, MOBILE_PAGE)) }
            .orEmpty(),
        postResponder = { url, body ->
            val answer = when (clientOf(body)) {
                "WEB_EMBEDDED_PLAYER" -> embedded
                "VISIONOS" -> visionOsAgain?.takeIf { "\"visitorData\"" in body } ?: visionOs
                "ANDROID" -> android
                else -> own
            }
            answer?.let { FakeExtractorHttpClient.json(it, url) }
        },
    )

    private fun clientOf(body: String): String =
        BoundedJsonParser.parse(body, maxNodes = 1_000)
            .path("context", "client")["clientName"]?.asStringOrNull.orEmpty()

    private fun request(cookie: String? = COOKIE) = SiteExtractionRequest(
        identity = requireNotNull(YouTubeUrls.identify(SHARED_LINK)),
        requestContext = BrowserRequestContext(MOBILE_PAGE, USER_AGENT, cookie),
        nowEpochMs = NOW,
    )

    private fun watchPage(playerResponse: String?): String =
        fixture("watch_page.html").replace("__PLAYER_RESPONSE__", playerResponse.orEmpty())

    private fun ownStreams(): String = fixture("player_ok.json").replace(EMBED_HOST, PAGE_HOST)

    private fun fixture(name: String): String = Fixtures.read("youtube/$name")

    private class CountingCapture : PlaybackCaptureProvider {
        var calls = 0
        override suspend fun capture(request: MasterRequest): CaptureResult {
            calls++
            return CaptureResult.Unavailable
        }
    }

    private companion object {
        const val VIDEO_ID = "Yft0Fixture"
        const val NOW = 1_791_000_000_000L
        const val SHARED_LINK = "https://youtu.be/$VIDEO_ID?si=fixture"
        const val CANONICAL = "https://www.youtube.com/watch?v=$VIDEO_ID"
        const val PAGE_FETCH = "$CANONICAL&bpctr=9999999999&has_verified=1"
        const val MOBILE_PAGE = "https://m.youtube.com/watch?v=$VIDEO_ID"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Fixture) Mobile Safari/537.36"
        const val COOKIE = "PREF=fixture; SID=fixture-session"
        const val EMBED_HOST = "rr1---sn-fixture.googlevideo.example-cdn.test"
        const val PAGE_HOST = "rr2---sn-page.googlevideo.example-cdn.test"
    }
}
