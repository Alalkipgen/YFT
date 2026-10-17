package com.alal.yft.master

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alal.yft.detection.OkHttpExtractorClient
import com.alal.yft.detection.script.WebViewSolverEngine
import com.alal.yft.detection.script.YouTubePlayerScriptRunner
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.PlayerScriptChallenge
import com.alal.yft.extractor.api.PlayerScriptChallengeKind
import com.alal.yft.extractor.api.PlayerScriptRequest
import com.alal.yft.extractor.api.PlayerScriptResult
import com.alal.yft.extractor.api.PlayerScriptRunner
import com.alal.yft.extractor.master.android.solver.WebViewOwnSolverEngine
import com.alal.yft.extractor.master.solver.OwnPlayerScriptRunner
import java.io.File
import java.util.Random
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 1.1 S6 phone canary (MASTER_KEY_PHASE1_1_PLAN.md §3 S6): today's YouTube player through
 * main's ejs runner and Master's own runner, both in their real WebView engines on this phone,
 * with the same YouTube-shaped inputs; pass/fail per kind (n, sig) plus timings.
 *
 * Opt-in only (`-e yft.ownSolverCanary 1`), never in default CI; `scripts/own-solver-canary.sh`
 * runs it on a phone or emulator. `-e yft.ownSolverCanary.player <ID>` checks a given player. The
 * report (app files `own-solver/own-solver-canary-*.json`) holds the player ID, verdicts, counts,
 * timings and the own runner's failure classes only: never an input, an output or an address.
 */
@RunWith(AndroidJUnit4::class)
class OwnSolverCanaryTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val args get() = InstrumentationRegistry.getArguments()

    @Test
    fun ownSolverEqualsEjsOnTodaysPlayer() = runBlocking {
        assumeTrue("Opt-in only: -e yft.ownSolverCanary 1", args.getString("yft.ownSolverCanary") == "1")
        val context = instrumentation.targetContext
        val http = OkHttpExtractorClient(
            client = OkHttpClient(),
            policy = OkHttpExtractorClient.Policy(callTimeoutSeconds = 60),
        )
        val player = args.getString("yft.ownSolverCanary.player")?.takeIf(PLAYER_ID::matches)
            ?: todaysPlayer(http)
        val request = PlayerScriptRequest(
            playerScriptUrl = "https://www.youtube.com/s/player/$player/$PHONE_VARIANT",
            pageUrl = PAGE,
            challenges = challenges(player),
        )

        val ejs = YouTubePlayerScriptRunner(http, WebViewSolverEngine(context))
        val own = OwnPlayerScriptRunner(http, WebViewOwnSolverEngine(context))
        val (ejsResult, ejsMs) = timed(ejs, request)
        val (ownResult, ownFirstMs) = timed(own, request)
        val firstFailures = own.lastFailures
        val (ownCached, ownCachedMs) = timed(own, request)

        val counts = PlayerScriptChallengeKind.entries.associateWith { kind ->
            val keys = request.challenges.filter { it.kind == kind }.map { it.key }
            Count(
                total = keys.size,
                ejs = keys.count { ejsResult.valueOf(it) != null },
                agree = keys.count { key ->
                    ejsResult.valueOf(key) != null &&
                        ownResult.valueOf(key) == ejsResult.valueOf(key) &&
                        ownCached.valueOf(key) == ejsResult.valueOf(key)
                },
            )
        }
        val verdicts = counts.mapValues { (_, count) ->
            when {
                count.ejs < count.total -> "oracle"
                count.agree == count.total -> "pass"
                else -> "fail"
            }
        }
        val report = buildString {
            append("{\"canary\":\"own-solver-phone\",\"player\":\"").append(player).append('"')
            append(",\"ms\":{\"ejs\":").append(ejsMs)
            append(",\"ownFirst\":").append(ownFirstMs)
            append(",\"ownCached\":").append(ownCachedMs).append('}')
            counts.forEach { (kind, count) ->
                append(",\"").append(kind.label).append("\":{\"total\":").append(count.total)
                append(",\"ejs\":").append(count.ejs).append(",\"agree\":").append(count.agree)
                append(",\"verdict\":\"").append(verdicts.getValue(kind)).append("\"}")
            }
            append(",\"ownFailures\":[")
            append(
                firstFailures.joinToString(",") { failure ->
                    "\"" + failure.filter { it.isLetterOrDigit() || it in " :-_." } + "\""
                },
            )
            append("]}")
        }
        val folder = File(checkNotNull(context.getExternalFilesDir(null)), "own-solver")
        folder.mkdirs()
        File(folder, "own-solver-canary-${System.currentTimeMillis()}.json").writeText(report)
        Log.i(TAG, report)

        assertEquals(report, mapOf(PlayerScriptChallengeKind.RATE_PARAM to "pass", PlayerScriptChallengeKind.SIGNATURE to "pass"), verdicts)
    }

    private suspend fun todaysPlayer(http: OkHttpExtractorClient): String {
        val page = http.get("https://www.youtube.com/iframe_api") as ExtractorHttpResult.Success
        return checkNotNull(EMBED_PLAYER.find(page.body)?.groupValues?.get(1)) {
            "could not read today's player from the embed API"
        }
    }

    private suspend fun timed(runner: PlayerScriptRunner, request: PlayerScriptRequest): Pair<PlayerScriptResult, Long> {
        val started = SystemClock.elapsedRealtime()
        val result = runner.resolve(request)
        return result to SystemClock.elapsedRealtime() - started
    }

    private fun PlayerScriptResult.valueOf(key: String): String? =
        (this as? PlayerScriptResult.Success)?.resolved?.get(key)

    private class Count(val total: Int, val ejs: Int, val agree: Int)

    private val PlayerScriptChallengeKind.label: String
        get() = if (this == PlayerScriptChallengeKind.RATE_PARAM) "n" else "sig"

    private companion object {
        const val TAG = "YftOwnSolver"
        const val PHONE_VARIANT = "player-plasma-ias-phone-en_US.vflset/base.js"
        const val PAGE = "https://www.youtube.com/watch?v=jNQXAC9IVRw"
        const val N_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        const val SIG_CHARS = "$N_CHARS="
        val PLAYER_ID = Regex("[A-Za-z0-9_-]{4,32}")
        val EMBED_PLAYER = Regex("\\\\/s\\\\/player\\\\/([A-Za-z0-9_-]{4,32})\\\\/")

        /** YouTube-shaped inputs, the same for every run on one player: 3 n and 3 sig. */
        fun challenges(player: String): List<PlayerScriptChallenge> {
            val random = Random(player.hashCode().toLong())
            fun pick(chars: String, length: Int) =
                String(CharArray(length) { chars[random.nextInt(chars.length)] })
            val rates = listOf(16, 18, 19).mapIndexed { index, length ->
                PlayerScriptChallenge("n-$index", PlayerScriptChallengeKind.RATE_PARAM, pick(N_CHARS, length))
            }
            val signatures = listOf(104, 108, 107).mapIndexed { index, length ->
                PlayerScriptChallenge("sig-$index", PlayerScriptChallengeKind.SIGNATURE, pick(SIG_CHARS, length))
            }
            return rates + signatures
        }
    }
}
