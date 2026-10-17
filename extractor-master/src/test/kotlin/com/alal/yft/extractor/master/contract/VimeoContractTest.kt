package com.alal.yft.extractor.master.contract

import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.master.CapturedRequest
import com.alal.yft.extractor.master.CaptureResult
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.MasterStage
import com.alal.yft.extractor.master.PageSnapshot
import com.alal.yft.extractor.master.RecordingValidator
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.master.modules.youtube.testing.Fixtures
import com.alal.yft.extractor.sites.vimeo.VimeoExtractor
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R8 Vimeo: Master asks the player configuration itself (one request) and reads it with the
 * Vimeo key table. Parity with main's adapter on main's fixtures; access verdicts stop with 0
 * capture and 0 media checks.
 */
class VimeoContractTest {
    private val identity = checkNotNull(VimeoExtractor(answering()).identify(PAGE))

    @Test
    fun `the player configuration gives main's rows, probed and keyed to the app's video`() =
        runTest {
            val main = VimeoExtractor(mainAnswers("vimeo/player_config.json"))
                .extract(mainRequest(identity)) as SiteExtractionResult.Success
            val http = answering(CONFIG to Fixtures.read("vimeo/player_config.json"))
            val validator = RecordingValidator()
            val capture = CountingCapture()

            val result = contractEngine(http, validator, capture)
                .extract(contractRequest(PAGE, identity))

            val rows = assertCovers("player_config", main.candidates, result, validator, KEY)
            assertEquals(MasterStage.CONTRACT, (result as MasterResult.Success).stage)
            assertEquals(0, capture.calls)
            assertEquals(listOf(CONFIG), http.requestedUrls)
            assertEquals(listOf(1080, 720, 540), rows.filter { it.kind == MediaKind.DIRECT }.map { it.height })
            assertEquals(65_400L, rows.first().durationMillis)
            assertTrue(rows.any { it.kind == MediaKind.HLS && "akfire" in it.mediaUrl })
            assertFalse(rows.any { "skyfire" in it.mediaUrl })
        }

    @Test
    fun `access verdicts match main and stop before any capture or media check`() = runTest {
        mapOf(
            "vimeo/config_drm.json" to SiteExtractionFailure.DRM_PROTECTED,
            "vimeo/config_private.json" to SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
            "vimeo/config_password.json" to SiteExtractionFailure.LOGIN_REQUIRED,
            "vimeo/config_geo.json" to SiteExtractionFailure.GEO_RESTRICTED,
        ).forEach { (fixture, reason) ->
            val main = VimeoExtractor(mainAnswers(fixture)).extract(mainRequest(identity))
            val validator = RecordingValidator()
            val capture = CountingCapture()

            val result = contractEngine(answering(CONFIG to Fixtures.read(fixture)), validator, capture)
                .extract(contractRequest(PAGE, identity))

            assertEquals(fixture, reason, (main as SiteExtractionResult.Failure).reason)
            assertEquals(fixture, reason, (result as MasterResult.Failure).reason)
            assertEquals(fixture, 0, capture.calls)
            assertTrue(fixture, validator.seen.isEmpty())
        }
    }

    @Test
    fun `an answer without usable files leaves the page to capture`() = runTest {
        listOf(
            "vimeo/config_no_files.json", "vimeo/config_insecure.json",
            "vimeo/config_malformed.json", "vimeo/config_expired.json",
        ).forEach { fixture ->
            val main = VimeoExtractor(mainAnswers(fixture)).extract(mainRequest(identity))
            val validator = RecordingValidator()
            val capture = CountingCapture()

            val result = contractEngine(answering(CONFIG to Fixtures.read(fixture)), validator, capture)
                .extract(contractRequest(PAGE, identity))

            assertTrue(fixture, main is SiteExtractionResult.Failure)
            assertTrue("$fixture: $result", result is MasterResult.NeedsPlayback)
            assertEquals(fixture, 1, capture.calls)
            assertTrue(fixture, validator.seen.isEmpty())
        }
    }

    @Test
    fun `an unlisted clip keeps its hash and the request is anchored to the clip page`() =
        runTest {
            val unlisted = checkNotNull(VimeoExtractor(answering()).identify("$PAGE/abcdef1234"))
            val http = answering(
                "$CONFIG?h=abcdef1234" to Fixtures.read("vimeo/player_config.json"),
            )

            val result = contractEngine(http).extract(
                contractRequest("$PAGE/abcdef1234", unlisted, cookie = "vimeo=session"),
            )

            assertTrue(result is MasterResult.Success)
            assertEquals(listOf("$CONFIG?h=abcdef1234"), http.requestedUrls)
            val headers = http.requestedHeaders.single()
            assertEquals("https://vimeo.com/123456789/abcdef1234", headers["Referer"])
            assertEquals(AGENT, headers["User-Agent"])
            assertEquals("vimeo=session", headers["Cookie"])
        }

    @Test
    fun `the session cookie never leaves Vimeo's own domain`() = runTest {
        val http = answering(CONFIG to Fixtures.read("vimeo/player_config.json"))

        contractEngine(http).extract(
            contractRequest("https://embed.example.test/post", identity, cookie = "other=1"),
        )

        assertFalse(http.requestedHeaders.single().containsKey("Cookie"))
    }

    @Test
    fun `an answer for another video is not this video's`() = runTest {
        val foreign = Fixtures.read("vimeo/player_config.json").replace("123456789", "987654321")
        val validator = RecordingValidator()
        val capture = CountingCapture()

        val result = contractEngine(answering(CONFIG to foreign), validator, capture)
            .extract(contractRequest(PAGE, identity))

        assertTrue(result is MasterResult.NeedsPlayback)
        assertEquals(1, capture.calls)
        assertTrue(validator.seen.isEmpty())
    }

    @Test
    fun `a renamed key still finds the files by shape`() = runTest {
        val renamed = Fixtures.read("vimeo/player_config.json")
            .replace("\"progressive\"", "\"progressive_files\"")
            .replace("\"hls\"", "\"hls_v2\"").replace("\"dash\"", "\"dash_v2\"")
        val validator = RecordingValidator()

        val result = contractEngine(answering(CONFIG to renamed), validator)
            .extract(contractRequest(PAGE, identity))

        val rows = (result as MasterResult.Success).result.candidates
        assertTrue(rows.mapNotNull { it.height }.containsAll(listOf(1080, 720, 540)))
        rows.forEach { assertEquals(KEY, it.videoId) }
    }

    @Test
    fun `login, bot-check and player-script failures never ask the endpoint`() = runTest {
        listOf(
            SiteExtractionFailure.LOGIN_REQUIRED, SiteExtractionFailure.BOT_CHECK,
            SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED,
        ).forEach { failure ->
            val http = answering(CONFIG to Fixtures.read("vimeo/player_config.json"))

            contractEngine(http).extract(contractRequest(PAGE, identity, failure))

            assertTrue(failure.name, http.requestedUrls.isEmpty())
        }
    }

    @Test
    fun `without contracts or an identity the engine asks nothing`() = runTest {
        val http = answering(CONFIG to Fixtures.read("vimeo/player_config.json"))
        val capture = CountingCapture()

        val plain = MasterFallbackEngine(RecordingValidator(), capture, MasterPolicy(enabled = true))
            .extract(contractRequest(PAGE, identity))
        val anonymous = contractEngine(http).extract(contractRequest(PAGE, identity).copy(identity = null))

        assertTrue(plain is MasterResult.NeedsPlayback)
        assertTrue(anonymous is MasterResult.NeedsPlayback)
        assertTrue(http.requestedUrls.isEmpty())
    }

    @Test
    fun `a slow endpoint leaves its time to capture`() = runTest {
        val slow = object : ExtractorHttpClient {
            override suspend fun get(
                url: String,
                headers: Map<String, String>,
                maxBodyBytes: Long,
            ): ExtractorHttpResult {
                delay(60_000)
                return ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)
            }

            override suspend fun postJson(
                url: String,
                body: String,
                headers: Map<String, String>,
                maxBodyBytes: Long,
            ) = ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)
        }
        val captured = PageSnapshot(
            PAGE, 1, requests = listOf(CapturedRequest("https://cdn.vimeo.test/a.mp4", mimeType = "video/mp4")),
            playingMediaUrl = "https://cdn.vimeo.test/a.mp4", authorizedPlayback = true,
        )
        val capture = CountingCapture(CaptureResult.Available(captured))
        val engine = MasterFallbackEngine(
            RecordingValidator(), capture, MasterPolicy(enabled = true), contracts = SiteContracts(slow),
        )

        val result = engine.extract(contractRequest(PAGE, identity))

        assertEquals(MasterStage.PLAYBACK_CAPTURE, (result as MasterResult.Success).stage)
        assertEquals(1, capture.calls)
        // R8 videoKey: capture rows on a Vimeo page carry the app's key.
        assertEquals(KEY, result.result.candidates.single().videoId)
    }

    private fun mainAnswers(config: String): FakeExtractorHttpClient = answering(
        PAGE to Fixtures.read("vimeo/clip_page.html"),
        MAIN_CONFIG to Fixtures.read(config),
    )

    private companion object {
        const val PAGE = "https://vimeo.com/123456789"
        const val KEY = "vimeo:123456789"
        const val CONFIG = "https://player.vimeo.com/video/123456789/config"
        const val MAIN_CONFIG =
            "https://player.vimeo.com/video/123456789/config?h=abcdef1234&s=fixture-signature"
    }
}