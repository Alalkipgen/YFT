package com.alal.yft.detection.script

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.PlayerScriptChallenge
import com.alal.yft.extractor.api.PlayerScriptChallengeKind
import com.alal.yft.extractor.api.PlayerScriptRequest
import com.alal.yft.extractor.api.PlayerScriptResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubePlayerScriptRunnerTest {
    @Test
    fun `values come back keyed by challenge, with repeated inputs solved once`() = runTest {
        val http = FakeHttp(mapOf(PHONE_PLAYER to PLAYER_SOURCE))
        val engine = FakeEngine()
        val runner = YouTubePlayerScriptRunner(http, engine)

        val result = runner.resolve(
            request(
                rate("n-18", "rateIn"),
                rate("n-140", "rateIn"),
                signature("sig-18", "sigIn"),
            ),
        )

        assertEquals(
            PlayerScriptResult.Success(
                mapOf("n-18" to "N:rateIn", "n-140" to "N:rateIn", "sig-18" to "S:sigIn"),
            ),
            result,
        )
        assertEquals(listOf(PHONE_PLAYER), http.requestedUrls)
        assertEquals(mapOf("Referer" to PAGE), http.requestedHeaders.single())
        val job = engine.jobs.single()
        assertEquals("player", job["type"].asStringOrNull)
        assertEquals(PLAYER_SOURCE, (job["player"] as JsonValue.Text).value)
        assertEquals(
            listOf(listOf("rateIn"), listOf("sigIn")),
            job["requests"].asArrayOrEmpty.map { request ->
                request["challenges"].asArrayOrEmpty.map { it.asStringOrNull }
            },
        )
    }

    @Test
    fun `a later video on the same player reuses the reduced copy`() = runTest {
        val http = FakeHttp(mapOf(PHONE_PLAYER to PLAYER_SOURCE))
        val engine = FakeEngine()
        val runner = YouTubePlayerScriptRunner(http, engine)

        runner.resolve(request(rate("n-18", "first")))
        val second = runner.resolve(request(rate("n-18", "second")))

        assertEquals(PlayerScriptResult.Success(mapOf("n-18" to "N:second")), second)
        assertEquals(1, http.requestedUrls.size)
        assertEquals(
            listOf("player", "preprocessed"),
            engine.jobs.map { it["type"].asStringOrNull },
        )
        assertEquals(
            "reduced:$PLAYER_SOURCE",
            engine.jobs[1]["preprocessed_player"].asStringOrNull,
        )
    }

    @Test
    fun `a reduced copy that stops working is rebuilt from the player`() = runTest {
        val http = FakeHttp(mapOf(PHONE_PLAYER to PLAYER_SOURCE))
        val engine = FakeEngine(failPreprocessed = true)
        val runner = YouTubePlayerScriptRunner(http, engine)

        runner.resolve(request(rate("n-18", "first")))
        val second = runner.resolve(request(rate("n-18", "second")))

        assertEquals(PlayerScriptResult.Success(mapOf("n-18" to "N:second")), second)
        assertEquals(2, http.requestedUrls.size)
        assertEquals(
            listOf("player", "preprocessed", "player"),
            engine.jobs.map { it["type"].asStringOrNull },
        )
    }

    @Test
    fun `the desktop build is fetched when the phone build is missing`() = runTest {
        val http = FakeHttp(mapOf(MAIN_PLAYER to PLAYER_SOURCE))
        val runner = YouTubePlayerScriptRunner(http, FakeEngine())

        val result = runner.resolve(request(rate("n-18", "rateIn")))

        assertEquals(PlayerScriptResult.Success(mapOf("n-18" to "N:rateIn")), result)
        assertEquals(listOf(PHONE_PLAYER, MAIN_PLAYER), http.requestedUrls)
    }

    @Test
    fun `a player that cannot be fetched reports the transport failure`() = runTest {
        val http = FakeHttp(emptyMap(), failure = SiteExtractionFailure.NETWORK)
        val engine = FakeEngine()
        val runner = YouTubePlayerScriptRunner(http, engine)

        val result = runner.resolve(request(rate("n-18", "rateIn")))

        assertEquals(PlayerScriptResult.Failed(SiteExtractionFailure.NETWORK), result)
        assertTrue(engine.jobs.isEmpty())
    }

    @Test
    fun `only YouTube's own player path is ever fetched`() = runTest {
        val http = FakeHttp(mapOf(PHONE_PLAYER to PLAYER_SOURCE))
        val runner = YouTubePlayerScriptRunner(http, FakeEngine())

        listOf(
            "https://evil.example.test/s/player/f1x7ure0/base.js",
            "https://www.youtube.com/s/player/f1x7ure0/../../evil/base.js",
            "https://www.youtube.com/s/player/f1x7ure0/base.css",
            "https://www.youtube.com/watch?v=Yft0Fixture",
        ).forEach { address ->
            val result = runner.resolve(
                PlayerScriptRequest(address, PAGE, listOf(rate("n-18", "rateIn"))),
            )
            assertEquals(
                address,
                PlayerScriptResult.Failed(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED),
                result,
            )
        }
        assertTrue(http.requestedUrls.isEmpty())
    }

    @Test
    fun `an engine that cannot run, fails or stalls reports the player script requirement`() =
        runTest {
            val unavailable = YouTubePlayerScriptRunner(
                FakeHttp(mapOf(PHONE_PLAYER to PLAYER_SOURCE)),
                FakeEngine(isAvailable = false),
            )
            assertEquals(
                PlayerScriptResult.Unavailable,
                unavailable.resolve(request(rate("n-18", "rateIn"))),
            )

            listOf(
                FakeEngine(reply = { null }),
                FakeEngine(reply = { "{\"type\":\"error\",\"error\":\"parse\"}" }),
                FakeEngine(reply = { "not json" }),
                FakeEngine(reply = { awaitCancellation() }),
                FakeEngine(reply = {
                    "{\"type\":\"result\",\"responses\":[{\"type\":\"result\",\"data\":{}}]}"
                }),
            ).forEach { engine ->
                val runner = YouTubePlayerScriptRunner(
                    FakeHttp(mapOf(PHONE_PLAYER to PLAYER_SOURCE)),
                    engine,
                    timeoutMillis = 1_000,
                )
                assertEquals(
                    PlayerScriptResult.Failed(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED),
                    runner.resolve(request(rate("n-18", "rateIn"))),
                )
            }
        }

    private fun request(vararg challenges: PlayerScriptChallenge) =
        PlayerScriptRequest(PHONE_PLAYER, PAGE, challenges.toList())

    private fun rate(key: String, input: String) =
        PlayerScriptChallenge(key, PlayerScriptChallengeKind.RATE_PARAM, input)

    private fun signature(key: String, input: String) =
        PlayerScriptChallenge(key, PlayerScriptChallengeKind.SIGNATURE, input)

    /** Answers like the real solver: `N:`/`S:` plus the input, and a reduced player on request. */
    private class FakeEngine(
        override val isAvailable: Boolean = true,
        private val failPreprocessed: Boolean = false,
        private val reply: (suspend (JsonValue?) -> String?)? = null,
    ) : SolverEngine {
        val jobs = mutableListOf<JsonValue?>()

        override suspend fun run(input: String): String? {
            val job = BoundedJsonParser.parse(input)
            jobs += job
            reply?.let { return it(job) }
            val type = job["type"].asStringOrNull
            if (type == "preprocessed" && failPreprocessed) {
                return "{\"type\":\"error\",\"error\":\"stale\"}"
            }
            val responses = job["requests"].asArrayOrEmpty.joinToString(",") { request ->
                val prefix = if (request["type"].asStringOrNull == "n") "N:" else "S:"
                val data = request["challenges"].asArrayOrEmpty.joinToString(",") { challenge ->
                    val value = challenge.asStringOrNull
                    "\"$value\":\"$prefix$value\""
                }
                "{\"type\":\"result\",\"data\":{$data}}"
            }
            val reduced = if (type == "player") {
                ",\"preprocessed_player\":\"reduced:${job["player"].asStringOrNull}\""
            } else {
                ""
            }
            return "{\"type\":\"result\",\"responses\":[$responses]$reduced}"
        }
    }

    private class FakeHttp(
        private val bodies: Map<String, String>,
        private val failure: SiteExtractionFailure = SiteExtractionFailure.HTTP_STATUS,
    ) : ExtractorHttpClient {
        val requestedUrls = mutableListOf<String>()
        val requestedHeaders = mutableListOf<Map<String, String>>()

        override suspend fun get(
            url: String,
            headers: Map<String, String>,
            maxBodyBytes: Long,
        ): ExtractorHttpResult {
            requestedUrls += url
            requestedHeaders += headers
            return bodies[url]?.let { ExtractorHttpResult.Success(200, it, url) }
                ?: ExtractorHttpResult.Failure(failure)
        }

        override suspend fun postJson(
            url: String,
            body: String,
            headers: Map<String, String>,
            maxBodyBytes: Long,
        ): ExtractorHttpResult = error("The runner never posts")
    }

    private companion object {
        const val PAGE = "https://www.youtube.com/watch?v=Yft0Fixture"
        const val PHONE_PLAYER =
            "https://www.youtube.com/s/player/f1x7ure0/player-plasma-ias-phone-en_US.vflset/base.js"
        const val MAIN_PLAYER =
            "https://www.youtube.com/s/player/f1x7ure0/player_ias.vflset/en_US/base.js"
        const val PLAYER_SOURCE = "var _yt_player={};(function(g){})(_yt_player);"
    }
}
