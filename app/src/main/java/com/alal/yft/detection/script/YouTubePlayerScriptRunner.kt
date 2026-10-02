package com.alal.yft.detection.script

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.PlayerScriptChallengeKind
import com.alal.yft.extractor.api.PlayerScriptRequest
import com.alal.yft.extractor.api.PlayerScriptResult
import com.alal.yft.extractor.api.PlayerScriptRunner
import com.alal.yft.extractor.api.SiteExtractionFailure
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Computes YouTube's per-stream values with YouTube's own current player script.
 *
 * The player is fetched from YouTube exactly as the user's browser fetches it, and the bundled
 * yt-dlp ejs solver extracts and runs the player's own functions inside [engine], which has no
 * network and no cookies. The solver's reduced copy of the last player is kept in memory, so
 * later videos on the same player version skip the fetch and the slow first pass.
 */
class YouTubePlayerScriptRunner(
    private val http: ExtractorHttpClient,
    private val engine: SolverEngine,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val maxPlayerBytes: Long = DEFAULT_MAX_PLAYER_BYTES,
) : PlayerScriptRunner {
    private val lock = Mutex()

    /** Guarded by [lock]. One entry: users rarely meet two player versions in one session. */
    private var cached: CachedPlayer? = null

    override val isAvailable: Boolean
        get() = engine.isAvailable

    override suspend fun resolve(request: PlayerScriptRequest): PlayerScriptResult {
        val playerId = playerIdOf(request.playerScriptUrl)
            ?: return PlayerScriptResult.Failed(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED)
        if (!engine.isAvailable) return PlayerScriptResult.Unavailable

        val batch = EjsSolverProtocol.Batch(
            rateInputs = request.inputsOf(PlayerScriptChallengeKind.RATE_PARAM),
            signatureInputs = request.inputsOf(PlayerScriptChallengeKind.SIGNATURE),
        )
        val solved = when (val outcome = lock.withLock { solve(playerId, request, batch) }) {
            is Outcome.Solved -> outcome.values
            is Outcome.Failed -> return PlayerScriptResult.Failed(outcome.reason)
        }

        val resolved = request.challenges.mapNotNull { challenge ->
            val values = when (challenge.kind) {
                PlayerScriptChallengeKind.RATE_PARAM -> solved.rate
                PlayerScriptChallengeKind.SIGNATURE -> solved.signature
            }
            values[challenge.input]?.let { challenge.key to it }
        }.toMap()
        if (resolved.isEmpty()) {
            return PlayerScriptResult.Failed(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED)
        }
        return PlayerScriptResult.Success(resolved)
    }

    private suspend fun solve(
        playerId: String,
        request: PlayerScriptRequest,
        batch: EjsSolverProtocol.Batch,
    ): Outcome {
        cached?.takeIf { it.playerId == playerId }?.let { entry ->
            val reused = run(EjsSolverProtocol.Player.Preprocessed(entry.source), batch, false)
            if (reused is Outcome.Solved) return reused
            // A reduced copy that no longer runs is rebuilt once from the original player.
            cached = null
        }

        val source = when (val fetched = fetchPlayer(playerId, request)) {
            is Fetched.Source -> fetched.text
            is Fetched.Failed -> return Outcome.Failed(fetched.reason)
        }
        val outcome = run(EjsSolverProtocol.Player.Raw(source), batch, keepPreprocessed = true)
        if (outcome is Outcome.Solved) {
            outcome.values.preprocessedPlayer?.let { cached = CachedPlayer(playerId, it) }
        }
        return outcome
    }

    private suspend fun run(
        player: EjsSolverProtocol.Player,
        batch: EjsSolverProtocol.Batch,
        keepPreprocessed: Boolean,
    ): Outcome {
        val input = EjsSolverProtocol.encode(player, batch, keepPreprocessed)
        val output = withTimeoutOrNull(timeoutMillis) { engine.run(input) }
            ?: return Outcome.Failed(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED)
        return when (val decoded = EjsSolverProtocol.decode(output, batch)) {
            is EjsSolverProtocol.Decoded.Solved -> Outcome.Solved(decoded)
            EjsSolverProtocol.Decoded.Failed, null ->
                Outcome.Failed(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED)
        }
    }

    /**
     * Fetches the player the page named, then the desktop build of the same version.
     *
     * Every build of one player version computes identical values, so the fallback changes only
     * the download size.
     */
    private suspend fun fetchPlayer(playerId: String, request: PlayerScriptRequest): Fetched {
        val addresses = listOf(request.playerScriptUrl, mainPlayerUrl(playerId)).distinct()
        var failure = SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED
        for (address in addresses) {
            when (
                val result = http.get(
                    url = address,
                    headers = mapOf("Referer" to request.pageUrl),
                    maxBodyBytes = maxPlayerBytes,
                )
            ) {
                is ExtractorHttpResult.Success -> if (result.body.isNotBlank()) {
                    return Fetched.Source(result.body)
                }

                is ExtractorHttpResult.Failure -> failure = result.reason
            }
        }
        return Fetched.Failed(failure)
    }

    private fun PlayerScriptRequest.inputsOf(kind: PlayerScriptChallengeKind): List<String> =
        challenges.filter { it.kind == kind }.map { it.input }.distinct()

    private class CachedPlayer(val playerId: String, val source: String)

    private sealed interface Outcome {
        class Solved(val values: EjsSolverProtocol.Decoded.Solved) : Outcome

        class Failed(val reason: SiteExtractionFailure) : Outcome
    }

    private sealed interface Fetched {
        class Source(val text: String) : Fetched

        class Failed(val reason: SiteExtractionFailure) : Fetched
    }

    companion object {
        /** A slow phone takes a few seconds for a first pass; this bounds a stuck engine. */
        const val DEFAULT_TIMEOUT_MILLIS: Long = 45_000

        /** Current players are 1.5 to 3 MB; the cap leaves room for growth. */
        const val DEFAULT_MAX_PLAYER_BYTES: Long = 8L * 1024 * 1024

        private const val PLAYER_ORIGIN = "https://www.youtube.com/s/player/"
        private const val MAIN_VARIANT = "player_ias.vflset/en_US/base.js"

        /** Only YouTube's own player path is accepted, so no other script is ever evaluated. */
        private val PLAYER_URL = Regex(
            "^https://www\\.youtube\\.com/s/player/([A-Za-z0-9_-]{4,32})/" +
                "([A-Za-z0-9_-]+(?:\\.vflset)?/)*base\\.js$",
        )

        internal fun playerIdOf(url: String): String? =
            PLAYER_URL.matchEntire(url)?.groupValues?.get(1)

        internal fun mainPlayerUrl(playerId: String): String =
            "$PLAYER_ORIGIN$playerId/$MAIN_VARIANT"
    }
}
