package com.alal.yft.extractor.master.solver

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.PlayerScriptChallenge
import com.alal.yft.extractor.api.PlayerScriptChallengeKind
import com.alal.yft.extractor.api.PlayerScriptRequest
import com.alal.yft.extractor.api.PlayerScriptResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnPlayerScriptRunnerTest {
    @Test
    fun `the phone player is fetched like main's runner and every challenge is answered`() =
        runTest {
            val http = players()
            val engine = ScriptedOwnEngine()

            val result = OwnPlayerScriptRunner(http, engine).resolve(request())

            assertEquals(
                PlayerScriptResult.Success(
                    mapOf(
                        "sig-18" to ScriptedOwnEngine.signature(SIG),
                        "n-18" to ScriptedOwnEngine.rate(RATE),
                        "n-140" to ScriptedOwnEngine.rate(RATE),
                    ),
                ),
                result,
            )
            assertEquals(listOf(PHONE_PLAYER), http.requestedUrls)
            assertEquals(mapOf("Referer" to PAGE), http.requestedHeaders.single())
            assertEquals(OwnPlayerScriptRunner.DEFAULT_MAX_PLAYER_BYTES, http.requestedBodyLimits.single())
            val job = engine.jobs.single()
            assertEquals("player", job.type)
            assertTrue(job.keepPrepared)
            assertEquals(PLAYER_TEXT, job.player)
            // Inputs go once per kind, however many streams share them.
            assertEquals(listOf(RATE), job.n)
            assertEquals(listOf(SIG), job.sig)
        }

    @Test
    fun `a cached program serves the same player without a fetch or a parse`() = runTest {
        val http = players()
        val engine = ScriptedOwnEngine()
        val runner = OwnPlayerScriptRunner(http, engine)

        runner.resolve(request())
        val second = runner.resolve(request())

        assertTrue(second is PlayerScriptResult.Success)
        assertEquals(1, http.requestedUrls.size)
        val reused = engine.jobs[1]
        assertEquals("prepared", reused.type)
        assertEquals(ScriptedOwnEngine.programOf(PLAYER_TEXT), reused.program)
        assertEquals(null, reused.player)
    }

    @Test
    fun `the cache keeps the most recent players only`() = runTest {
        val http = FakeExtractorHttpClient(
            getResponder = { url, _ -> FakeExtractorHttpClient.json("player of $url", url) },
        )
        val engine = ScriptedOwnEngine()
        val runner = OwnPlayerScriptRunner(http, engine, maxCachedPlayers = 2)

        listOf("aaaa1111", "bbbb2222", "cccc3333", "cccc3333", "aaaa1111").forEach {
            runner.resolve(request(phonePlayer(it)))
        }

        assertEquals(
            listOf("player", "player", "player", "prepared", "player"),
            engine.jobs.map { it.type },
        )
        assertEquals(4, http.requestedUrls.size)
    }

    @Test
    fun `a cached program that no longer runs is rebuilt once from the player`() = runTest {
        val http = players()
        var brokenPrepared = false
        val engine = ScriptedOwnEngine { job ->
            if (job.type == "prepared" && brokenPrepared) {
                ScriptedOwnEngine.result(failed = "program:TypeError")
            } else {
                ScriptedOwnEngine.answerAll(job)
            }
        }
        val runner = OwnPlayerScriptRunner(http, engine)
        runner.resolve(request())
        brokenPrepared = true

        val result = runner.resolve(request())

        assertTrue(result is PlayerScriptResult.Success)
        assertEquals(listOf("player", "prepared", "player"), engine.jobs.map { it.type })
        assertEquals(2, http.requestedUrls.size)
    }

    @Test
    fun `SelfCheck refusing one kind keeps the other and names the refusal`() = runTest {
        val engine = ScriptedOwnEngine { job ->
            ScriptedOwnEngine.result(
                n = job.n.associateWith(ScriptedOwnEngine::rate),
                failed = "sig:disagree",
                prepared = ScriptedOwnEngine.preparedFor(job),
            )
        }
        val runner = OwnPlayerScriptRunner(players(), engine)

        val result = runner.resolve(request())

        // The signature stream is then dropped by the YouTube reader; nothing is guessed.
        assertEquals(
            PlayerScriptResult.Success(
                mapOf(
                    "n-18" to ScriptedOwnEngine.rate(RATE),
                    "n-140" to ScriptedOwnEngine.rate(RATE),
                ),
            ),
            result,
        )
        assertEquals(listOf("sig:disagree"), runner.lastFailures)
    }

    @Test
    fun `a refused run is final for the cached program and is not rebuilt`() = runTest {
        val http = players()
        val engine = ScriptedOwnEngine { job ->
            ScriptedOwnEngine.result(
                failed = "n:shape,sig:encoding",
                prepared = ScriptedOwnEngine.preparedFor(job),
            )
        }
        val runner = OwnPlayerScriptRunner(http, engine)

        assertEquals(REQUIRED, runner.resolve(request()))
        assertEquals(REQUIRED, runner.resolve(request()))

        assertEquals(listOf("player", "prepared"), engine.jobs.map { it.type })
        assertEquals(1, http.requestedUrls.size)
        assertEquals(listOf("n:shape", "sig:encoding"), runner.lastFailures)
    }

    @Test
    fun `every solver failure maps to the player script requirement`() = runTest {
        val replies = mapOf<String, suspend (ScriptedOwnEngine.Job) -> String?>(
            "engine" to { null },
            "error SyntaxError" to { ScriptedOwnEngine.error("SyntaxError") },
            "reply" to { "<html>" },
            "no candidates" to { ScriptedOwnEngine.result(failed = "no candidates") },
        )
        replies.forEach { (failure, reply) ->
            val http = players()
            val runner = OwnPlayerScriptRunner(http, ScriptedOwnEngine(reply = reply))

            assertEquals(failure, REQUIRED, runner.resolve(request()))
            assertEquals(failure, listOf(failure), runner.lastFailures)
            // A broken run never leaves a program behind: the next lookup starts from the player.
            runner.resolve(request())
            assertEquals(failure, 2, http.requestedUrls.size)
        }
    }

    @Test
    fun `answers to nothing that was asked are a failure, not an empty success`() = runTest {
        val engine = ScriptedOwnEngine {
            ScriptedOwnEngine.result(n = mapOf("other" to "value"))
        }

        assertEquals(REQUIRED, OwnPlayerScriptRunner(players(), engine).resolve(request()))
    }

    @Test
    fun `a stuck engine is timed out`() = runTest {
        val engine = ScriptedOwnEngine { awaitCancellation() }
        val runner = OwnPlayerScriptRunner(players(), engine, timeoutMillis = 1_000)

        assertEquals(REQUIRED, runner.resolve(request()))
        assertEquals(listOf("engine"), runner.lastFailures)
    }

    @Test
    fun `the desktop build is fetched when the phone build fails, and a failed fetch keeps its reason`() =
        runTest {
            val fallback = FakeExtractorHttpClient(
                responses = mapOf(MAIN_PLAYER to FakeExtractorHttpClient.json(PLAYER_TEXT, MAIN_PLAYER)),
                fallback = ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK),
            )
            val engine = ScriptedOwnEngine()
            assertTrue(OwnPlayerScriptRunner(fallback, engine).resolve(request()) is PlayerScriptResult.Success)
            assertEquals(listOf(PHONE_PLAYER, MAIN_PLAYER), fallback.requestedUrls)

            val offline = FakeExtractorHttpClient(
                fallback = ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK),
            )
            val silent = ScriptedOwnEngine()
            assertEquals(
                PlayerScriptResult.Failed(SiteExtractionFailure.NETWORK),
                OwnPlayerScriptRunner(offline, silent).resolve(request()),
            )
            assertTrue(silent.jobs.isEmpty())
        }

    @Test
    fun `only YouTube's own player path is ever fetched`() = runTest {
        val http = players()
        val engine = ScriptedOwnEngine()
        val runner = OwnPlayerScriptRunner(http, engine)
        listOf(
            "https://www.youtube.com.example/s/player/f1x7ure0/base.js",
            "https://www.youtube.com/s/player/f1x7ure0/../evil.js",
            "https://cdn.example/s/player/f1x7ure0/base.js",
        ).forEach { address ->
            assertEquals(address, REQUIRED, runner.resolve(request(address)))
        }
        assertTrue(http.requestedUrls.isEmpty())
        assertTrue(engine.jobs.isEmpty())
    }

    @Test
    fun `no engine on the device means unavailable, without a fetch`() = runTest {
        val http = players()
        val runner = OwnPlayerScriptRunner(http, ScriptedOwnEngine(isAvailable = false))

        assertFalse(runner.isAvailable)
        assertEquals(PlayerScriptResult.Unavailable, runner.resolve(request()))
        assertTrue(http.requestedUrls.isEmpty())
    }

    private fun players() = FakeExtractorHttpClient(
        responses = mapOf(PHONE_PLAYER to FakeExtractorHttpClient.json(PLAYER_TEXT, PHONE_PLAYER)),
    )

    private fun request(playerUrl: String = PHONE_PLAYER) = PlayerScriptRequest(
        playerScriptUrl = playerUrl,
        pageUrl = PAGE,
        challenges = listOf(
            PlayerScriptChallenge("sig-18", PlayerScriptChallengeKind.SIGNATURE, SIG),
            PlayerScriptChallenge("n-18", PlayerScriptChallengeKind.RATE_PARAM, RATE),
            PlayerScriptChallenge("n-140", PlayerScriptChallengeKind.RATE_PARAM, RATE),
        ),
    )

    private companion object {
        const val PAGE = "https://www.youtube.com/watch?v=Yft0Fixture"
        const val PHONE_PLAYER =
            "https://www.youtube.com/s/player/f1x7ure0/player-plasma-ias-phone-en_US.vflset/base.js"
        const val MAIN_PLAYER =
            "https://www.youtube.com/s/player/f1x7ure0/player_ias.vflset/en_US/base.js"
        const val PLAYER_TEXT = "var _yt_player = {}; (function (g) { /* fixture */ })(_yt_player);"
        const val SIG = "AOq0QJ8wRAIgFixtureVideoSignature0123456789abcdefgh=="
        const val RATE = "Fx7nInput0"
        val REQUIRED = PlayerScriptResult.Failed(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED)

        fun phonePlayer(id: String) =
            "https://www.youtube.com/s/player/$id/player-plasma-ias-phone-en_US.vflset/base.js"
    }
}
