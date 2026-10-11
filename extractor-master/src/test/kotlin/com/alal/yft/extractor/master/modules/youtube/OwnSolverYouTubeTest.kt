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
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.master.modules.youtube.testing.Fixtures
import com.alal.yft.extractor.master.solver.OwnPlayerScriptRunner
import com.alal.yft.extractor.master.solver.ScriptedOwnEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.1 S5: Master's YouTube reader with the own solver's runner. The engine is scripted;
 * the reader, the runner and the job/reply protocol are the real ones.
 */
class OwnSolverYouTubeTest {
    @Test
    fun `signature and rate values computed by the own solver sign the streams`() = runTest {
        val players = players()
        val engine = ScriptedOwnEngine()

        val result = module(fixture("player_cipher.json"), players, engine).extract(request())
            as SiteExtractionResult.Success

        assertEquals(
            listOf(
                "$MEDIA?expire=4102444800&ei=Rml4dHVyZQ&itag=18&source=youtube&mime=video%2Fmp4" +
                    "&n=$SOLVED_RATE" +
                    "&sig=S%3D%3Dhgfedcba9876543210erutangiSoediVerutxiFgIARw8JQ0qOA",
                "$MEDIA?expire=4102444800&itag=140&mime=audio%2Fmp4&n=$SOLVED_RATE" +
                    "&signature=S%3D%3Dhgfedcba9876543210erutangiSoiduAerutxiFgIARw8JQ0qOA",
            ),
            result.candidates.map(MediaCandidate::mediaUrl),
        )
        // The page's phone player, fetched once with the page as Referer, parsed once.
        assertEquals(listOf(PHONE_PLAYER), players.requestedUrls)
        assertEquals("https://www.youtube.com/watch?v=$VIDEO_ID", players.requestedHeaders.single()["Referer"])
        assertEquals(listOf("player"), engine.jobs.map { it.type })
        assertEquals(listOf(RATE_INPUT), engine.jobs.single().n)
    }

    @Test
    fun `streams whose value SelfCheck refused are dropped, never guessed`() = runTest {
        val refusesSignature = ScriptedOwnEngine { job ->
            ScriptedOwnEngine.result(
                n = job.n.associateWith(ScriptedOwnEngine::rate),
                failed = "sig:disagree",
            )
        }

        // Every cipher stream needs a signature: nothing is offered.
        val cipher = module(fixture("player_cipher.json"), players(), refusesSignature)
            .extract(request())
        assertEquals(
            SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED,
            (cipher as SiteExtractionResult.Failure).reason,
        )

        // Streams that need only n keep their verified value.
        val plain = module(fixture("player_ok.json"), players(), refusesSignature)
            .extract(request()) as SiteExtractionResult.Success
        assertTrue(plain.candidates.isNotEmpty())
        assertTrue(plain.candidates.all { it.mediaUrl.contains("&n=$SOLVED_RATE") })
    }

    @Test
    fun `a broken own solver offers no stream that needs it`() = runTest {
        listOf<String?>(null, ScriptedOwnEngine.error("SyntaxError")).forEach { reply ->
            val broken = ScriptedOwnEngine { reply }

            val result = module(fixture("player_ok.json"), players(), broken).extract(request())

            assertEquals(
                SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED,
                (result as SiteExtractionResult.Failure).reason,
            )
        }
    }

    private fun module(
        embedded: String,
        players: FakeExtractorHttpClient,
        engine: ScriptedOwnEngine,
    ) = MasterYouTubeModule(
        http = FakeExtractorHttpClient(
            responses = mapOf(PAGE_FETCH to FakeExtractorHttpClient.html(watchPage(BOT_CHECK), MOBILE_PAGE)),
            postResponder = { url, body ->
                if (clientOf(body) == "WEB_EMBEDDED_PLAYER") {
                    FakeExtractorHttpClient.json(embedded, url)
                } else {
                    null
                }
            },
        ),
        playerScripts = OwnPlayerScriptRunner(players, engine),
    )

    private fun players() = FakeExtractorHttpClient(
        responses = mapOf(PHONE_PLAYER to FakeExtractorHttpClient.json(PLAYER_TEXT, PHONE_PLAYER)),
    )

    private fun clientOf(body: String): String? = BoundedJsonParser.parse(body, maxNodes = 1_000)
        .path("context", "client")["clientName"].asStringOrNull

    private fun request() = SiteExtractionRequest(
        identity = requireNotNull(YouTubeUrls.identify("https://youtu.be/$VIDEO_ID?si=fixture")),
        requestContext = BrowserRequestContext(pageUrl = MOBILE_PAGE, userAgent = USER_AGENT, cookie = null),
        nowEpochMs = NOW,
    )

    private fun watchPage(playerResponse: String): String =
        fixture("watch_page.html").replace("__PLAYER_RESPONSE__", playerResponse)

    private fun fixture(name: String): String = Fixtures.read("youtube/$name")

    private companion object {
        const val VIDEO_ID = "Yft0Fixture"
        const val NOW = 1_791_000_000_000L
        const val PAGE_FETCH = "https://www.youtube.com/watch?v=$VIDEO_ID&bpctr=9999999999&has_verified=1"
        const val MOBILE_PAGE = "https://m.youtube.com/watch?v=$VIDEO_ID"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Fixture) Mobile Safari/537.36"
        const val PHONE_PLAYER =
            "https://www.youtube.com/s/player/f1x7ure0/player-plasma-ias-phone-en_US.vflset/base.js"
        const val PLAYER_TEXT = "var _yt_player = {}; (function (g) { /* fixture */ })(_yt_player);"
        const val MEDIA = "https://rr1---sn-fixture.googlevideo.example-cdn.test/videoplayback"
        const val RATE_INPUT = "Fx7nInput0"
        const val SOLVED_RATE = "N0tupnIn7xF"
        val BOT_CHECK: String = Fixtures.read("youtube/player_bot_check.json")
    }
}
