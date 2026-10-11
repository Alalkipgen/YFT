package com.alal.yft.extractor.master.solver

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
 * Phase 1.1 S5: YouTube's n/sig values from YouTube's own current player, computed by Master's
 * own solver core (`yft-own-solver/own.solver.core.js`) instead of main's bundled ejs solver.
 *
 * The player is fetched exactly as main's `YouTubePlayerScriptRunner` fetches it: the address
 * the page named (the phone build), then the desktop build of the same version, with the page as
 * Referer, and only from YouTube's own player path. The core runs inside [engine] (no network,
 * no cookies). Its prepared program is kept in memory per player ID, so later videos on the same
 * player skip the fetch and the parse.
 *
 * Nothing is guessed: the core's SelfCheck refuses a kind it cannot verify, and every value it
 * does not return drops its stream in the YouTube reader. A failed run is
 * [PlayerScriptResult.Failed] with [SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED]; a failed
 * player download keeps its own reason, as in main's runner.
 */
class OwnPlayerScriptRunner(
    private val http: ExtractorHttpClient,
    private val engine: OwnSolverEngine,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val maxPlayerBytes: Long = DEFAULT_MAX_PLAYER_BYTES,
    private val maxCachedPlayers: Int = DEFAULT_MAX_CACHED_PLAYERS,
) : PlayerScriptRunner {
    init {
        require(timeoutMillis > 0)
        require(maxCachedPlayers > 0)
    }

    private val lock = Mutex()

    /** Guarded by [lock]: player ID to prepared program, least recently used first. */
    private val prepared = LinkedHashMap<String, String>(4, 0.75f, true)

    /**
     * Failure classes of the last run ("sig:disagree", "engine", ...), never a value; for the
     * canary and diagnostics.
     */
    @Volatile
    var lastFailures: List<String> = emptyList()
        private set

    override val isAvailable: Boolean
        get() = engine.isAvailable

    override suspend fun resolve(request: PlayerScriptRequest): PlayerScriptResult {
        val playerId = playerIdOf(request.playerScriptUrl)
            ?: return PlayerScriptResult.Failed(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED)
        if (!engine.isAvailable) return PlayerScriptResult.Unavailable

        val batch = OwnSolverProtocol.Batch(
            rateInputs = request.inputsOf(PlayerScriptChallengeKind.RATE_PARAM),
            signatureInputs = request.inputsOf(PlayerScriptChallengeKind.SIGNATURE),
        )
        val outcome = lock.withLock { solve(playerId, request, batch) }
        lastFailures = outcome.failures
        val solved = when (outcome) {
            is Outcome.Solved -> outcome.values
            is Outcome.Fetch -> return PlayerScriptResult.Failed(outcome.reason)
            is Outcome.Refused, is Outcome.Broken ->
                return PlayerScriptResult.Failed(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED)
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
        batch: OwnSolverProtocol.Batch,
    ): Outcome {
        prepared[playerId]?.let { program ->
            val reused = run(OwnSolverProtocol.Player.Prepared(program), batch, false)
            // SelfCheck's verdict on a kind does not change with a fresh parse; a program that
            // no longer runs at all is rebuilt once from the original player.
            if (reused !is Outcome.Broken) return reused
            prepared.remove(playerId)
        }

        val source = when (val fetched = fetchPlayer(playerId, request)) {
            is Fetched.Source -> fetched.text
            is Fetched.Failed -> return Outcome.Fetch(fetched.reason)
        }
        val outcome = run(OwnSolverProtocol.Player.Raw(source), batch, keepPrepared = true)
        outcome.program?.let { remember(playerId, it) }
        return outcome
    }

    private fun remember(playerId: String, program: String) {
        prepared[playerId] = program
        while (prepared.size > maxCachedPlayers) {
            prepared.remove(prepared.keys.first())
        }
    }

    private suspend fun run(
        player: OwnSolverProtocol.Player,
        batch: OwnSolverProtocol.Batch,
        keepPrepared: Boolean,
    ): Outcome {
        val input = OwnSolverProtocol.encode(player, batch, keepPrepared)
        val output = withTimeoutOrNull(timeoutMillis) { engine.run(input) }
            ?: return Outcome.Broken(listOf("engine"))
        return when (val decoded = OwnSolverProtocol.decode(output, batch)) {
            null -> Outcome.Broken(listOf("reply"))
            is OwnSolverProtocol.Decoded.Failed -> Outcome.Broken(listOf("error ${decoded.reason}"))
            is OwnSolverProtocol.Decoded.Solved -> when {
                decoded.failedWhole -> Outcome.Broken(decoded.failures)
                decoded.isEmpty -> Outcome.Refused(decoded.failures, decoded.preparedProgram)
                else -> Outcome.Solved(decoded)
            }
        }
    }

    /**
     * The player the page named, then the desktop build of the same version, exactly as main's
     * runner fetches it; every build of one version computes the same values.
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

    private sealed interface Outcome {
        val failures: List<String>
        val program: String? get() = null

        class Solved(val values: OwnSolverProtocol.Decoded.Solved) : Outcome {
            override val failures: List<String> get() = values.failures
            override val program: String? get() = values.preparedProgram
        }

        /** The core ran and SelfCheck refused every kind asked. */
        class Refused(
            override val failures: List<String>,
            override val program: String?,
        ) : Outcome

        /** The engine, the worker or the core as a whole failed. */
        class Broken(override val failures: List<String>) : Outcome

        class Fetch(val reason: SiteExtractionFailure) : Outcome {
            override val failures: List<String> get() = listOf("fetch ${reason.name}")
        }
    }

    private sealed interface Fetched {
        class Source(val text: String) : Fetched

        class Failed(val reason: SiteExtractionFailure) : Fetched
    }

    companion object {
        /** A slow phone takes several seconds for a first pass; this bounds a stuck engine. */
        const val DEFAULT_TIMEOUT_MILLIS: Long = 45_000

        /** Current players are 1.5 to 3 MB; the cap leaves room for growth (as main). */
        const val DEFAULT_MAX_PLAYER_BYTES: Long = 8L * 1024 * 1024

        /** Prepared programs kept in memory; users rarely meet more than one player version. */
        const val DEFAULT_MAX_CACHED_PLAYERS: Int = 2

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
