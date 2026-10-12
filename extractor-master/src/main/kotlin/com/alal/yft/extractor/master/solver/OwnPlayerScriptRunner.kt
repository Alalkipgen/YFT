package com.alal.yft.extractor.master.solver

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.PlayerScriptChallengeKind
import com.alal.yft.extractor.api.PlayerScriptRequest
import com.alal.yft.extractor.api.PlayerScriptResult
import com.alal.yft.extractor.api.PlayerScriptRunner
import com.alal.yft.extractor.api.SiteExtractionFailure
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
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
 *
 * Phase 1.2 N5b: with [next] (ownSolverNext on) the ladder runs instead, see [Next]; without it
 * this runner is exactly Phase 1.1.
 */
class OwnPlayerScriptRunner(
    private val http: ExtractorHttpClient,
    private val engine: OwnSolverEngine,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val maxPlayerBytes: Long = DEFAULT_MAX_PLAYER_BYTES,
    private val maxCachedPlayers: Int = DEFAULT_MAX_CACHED_PLAYERS,
    private val next: Next? = null,
) : PlayerScriptRunner, SignatureVerdicts {
    /**
     * Phase 1.2 N5b, ownSolverNext on: the ladder instead of Phase 1.1's single worker P run.
     * Worker P (prepared program kept as before), then worker B for a kind P did not give, then
     * the page ([page], strategy E: page P, then page B for a kind page P did not answer cleanly)
     * only when a worker step needs it, all decided by [OwnSolverLadderRule]; worker jobs ask for
     * the tripwire. A signature comes with a [PendingCheck] (the reader runs V before using it)
     * unless V already passed for that player version and step. "Ready" (the page took the job)
     * and the job itself time out apart; [breaker] trips on job timeouts, gone renderers, process
     * deaths during a job and V = fail. B and the page run from the player text (fetched when
     * needed, not kept).
     */
    class Next(
        val page: OwnSolverEngine? = null,
        val breaker: OwnSolverBreaker = OwnSolverBreaker(),
        val readyTimeoutMillis: Long = DEFAULT_READY_TIMEOUT_MILLIS,
        val jobTimeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    ) {
        init {
            require(readyTimeoutMillis > 0 && jobTimeoutMillis > 0)
        }
    }

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
        next?.let { return resolveNext(playerId, request, batch, it) }
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

    // ---------------------------------------------------------------------------------------------
    // Phase 1.2 N5b: the ladder (ownSolverNext only).

    /** Guarded by itself: the V each answer's signatures still need, by request (read once). */
    private val pendingChecks = WeakHashMap<PlayerScriptRequest, PendingCheck>()

    /** Player version and step pairs whose signatures passed V, for the process. */
    private val passed = ConcurrentHashMap.newKeySet<String>()

    override fun pendingCheck(request: PlayerScriptRequest): PendingCheck? =
        synchronized(pendingChecks) { pendingChecks.remove(request) }

    override fun record(check: PendingCheck, verdict: SignatureVerdicts.Verdict) {
        when (verdict) {
            SignatureVerdicts.Verdict.PASS -> {
                if (passed.size >= MAX_PASSED) passed.clear()
                passed += check.cacheKey
            }
            SignatureVerdicts.Verdict.FAIL -> next?.breaker?.signatureFailed(check.playerId, check.step)
            SignatureVerdicts.Verdict.UNKNOWN -> Unit
        }
    }

    private suspend fun resolveNext(
        playerId: String,
        request: PlayerScriptRequest,
        batch: OwnSolverProtocol.Batch,
        next: Next,
    ): PlayerScriptResult {
        val ladder = lock.withLock { ladder(Ladder(playerId, request, batch), next) }
        lastFailures = ladder.failures.take(MAX_LADDER_FAILURES)
        val resolved = request.challenges.mapNotNull { challenge ->
            val values = when (challenge.kind) {
                PlayerScriptChallengeKind.RATE_PARAM -> ladder.rate
                PlayerScriptChallengeKind.SIGNATURE -> ladder.signature
            }
            values[challenge.input]?.let { challenge.key to it }
        }.toMap()
        if (resolved.isEmpty()) {
            return PlayerScriptResult.Failed(ladder.fetchFailure ?: SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED)
        }
        val step = ladder.signatureStep
        val pending = if (step != null && ladder.signature.isNotEmpty() &&
            OwnSolverBreaker.key(playerId, step) !in passed
        ) {
            PendingCheck(playerId, step)
        } else {
            null
        }
        synchronized(pendingChecks) {
            if (pending == null) pendingChecks.remove(request) else pendingChecks[request] = pending
        }
        return PlayerScriptResult.Success(resolved)
    }

    /** One ladder run's state: the player text once fetched, each step's reply and rule step. */
    private class Ladder(
        val playerId: String,
        val request: PlayerScriptRequest,
        val batch: OwnSolverProtocol.Batch,
    ) {
        var source: String? = null
        var fetchFailure: SiteExtractionFailure? = null
        val failures = mutableListOf<String>()
        val replies = HashMap<OwnSolverBreaker.Step, OwnSolverProtocol.Decoded.Solved>()
        val steps = HashMap<OwnSolverBreaker.Step, OwnSolverLadderRule.Step>()
    }

    private class LadderResult(
        val rate: Map<String, String>,
        val signature: Map<String, String>,
        val signatureStep: OwnSolverBreaker.Step?,
        val failures: List<String>,
        val fetchFailure: SiteExtractionFailure?,
    )

    private suspend fun ladder(state: Ladder, next: Next): LadderResult {
        val kinds = buildList {
            if (state.batch.rateInputs.isNotEmpty()) add(OwnSolverLadderRule.Kind.RATE)
            if (state.batch.signatureInputs.isNotEmpty()) add(OwnSolverLadderRule.Kind.SIGNATURE)
        }
        // Positions matter to the rule (P first, B second): a step that did not run answers nothing.
        val workers = mutableListOf(workerP(state, next) ?: NOT_RUN)
        fun open() = kinds.filter { kind ->
            OwnSolverLadderRule.candidate(kind, workers, null) == OwnSolverLadderRule.Source.NONE
        }
        if (open().isNotEmpty()) {
            val options = OwnSolverProtocol.Options(strategyB = true, hint = false, tripwire = true)
            workers += fromText(state, next, OwnSolverBreaker.Step.WORKER_B, engine, options) ?: NOT_RUN
        }
        val left = open()
        val page = next.page
        if (left.isNotEmpty() && workers.any(OwnSolverLadderRule::needsPage) && page != null && page.isAvailable) {
            val pageP = fromText(state, next, OwnSolverBreaker.Step.PAGE_P, page, OwnSolverProtocol.Options())
            if (left.any { kind -> !(pageP != null && pageP.initErrors == 0 && kind in pageP.answered) }) {
                val options = OwnSolverProtocol.Options(strategyB = true, hint = false)
                fromText(state, next, OwnSolverBreaker.Step.PAGE_B, page, options)
            }
        }
        var rate = emptyMap<String, String>()
        var signature = emptyMap<String, String>()
        var signatureStep: OwnSolverBreaker.Step? = null
        for (kind in kinds) {
            // The page step for this kind: page P when it answered it cleanly, else page B.
            var pageStep: OwnSolverBreaker.Step? = null
            for (name in PAGE_STEPS) {
                val step = state.steps[name] ?: continue
                pageStep = name
                if (step.initErrors == 0 && kind in step.answered) break
            }
            val from = when (OwnSolverLadderRule.candidate(kind, workers, pageStep?.let(state.steps::get))) {
                OwnSolverLadderRule.Source.P -> OwnSolverBreaker.Step.WORKER_P
                OwnSolverLadderRule.Source.B -> OwnSolverBreaker.Step.WORKER_B
                OwnSolverLadderRule.Source.E -> pageStep
                OwnSolverLadderRule.Source.NONE -> null
            }
            val reply = from?.let(state.replies::get)
            if (from == null || reply == null) {
                state.failures += "${kindName(kind)}:ladder"
                continue
            }
            when (kind) {
                OwnSolverLadderRule.Kind.RATE -> rate = reply.rate
                OwnSolverLadderRule.Kind.SIGNATURE -> {
                    signature = reply.signature
                    signatureStep = from
                }
            }
        }
        return LadderResult(rate, signature, signatureStep, state.failures.toList(), state.fetchFailure)
    }

    /** Worker P: the kept prepared program first (rebuilt once from the player if it broke). */
    private suspend fun workerP(state: Ladder, next: Next): OwnSolverLadderRule.Step? {
        val name = OwnSolverBreaker.Step.WORKER_P
        val options = OwnSolverProtocol.Options(tripwire = true)
        prepared[state.playerId]?.let { program ->
            if (!next.breaker.allows(state.playerId, name)) return null
            val player = OwnSolverProtocol.Player.Prepared(program)
            val input = OwnSolverProtocol.encode(player, state.batch, false, options)
            val decoded = job(state, next, name, engine, input)
            if (decoded is OwnSolverProtocol.Decoded.Solved && !decoded.failedWhole) {
                return record(state, next, name, decoded)
            }
            prepared.remove(state.playerId)
        }
        if (!next.breaker.allows(state.playerId, name)) return null
        val source = source(state) ?: return null
        val input = OwnSolverProtocol.encode(OwnSolverProtocol.Player.Raw(source), state.batch, true, options)
        val decoded = job(state, next, name, engine, input) ?: return null
        (decoded as? OwnSolverProtocol.Decoded.Solved)?.preparedProgram?.let { remember(state.playerId, it) }
        return record(state, next, name, decoded)
    }

    /** Worker B or a page step: from the player text, nothing kept. */
    private suspend fun fromText(
        state: Ladder,
        next: Next,
        name: OwnSolverBreaker.Step,
        target: OwnSolverEngine,
        options: OwnSolverProtocol.Options,
    ): OwnSolverLadderRule.Step? {
        if (!next.breaker.allows(state.playerId, name)) return null
        val source = source(state) ?: return null
        val input = OwnSolverProtocol.encode(OwnSolverProtocol.Player.Raw(source), state.batch, false, options)
        val decoded = job(state, next, name, target, input) ?: return null
        return record(state, next, name, decoded)
    }

    private suspend fun source(state: Ladder): String? {
        state.source?.let { return it }
        if (state.fetchFailure != null) return null
        return when (val fetched = fetchPlayer(state.playerId, state.request)) {
            is Fetched.Source -> fetched.text.also { state.source = it }
            is Fetched.Failed -> {
                state.fetchFailure = fetched.reason
                state.failures += "fetch ${fetched.reason.name}"
                null
            }
        }
    }

    /** One job with the breaker's marker around it; null when no reply came. */
    private suspend fun job(
        state: Ladder,
        next: Next,
        name: OwnSolverBreaker.Step,
        target: OwnSolverEngine,
        input: String,
    ): OwnSolverProtocol.Decoded? {
        next.breaker.begin(state.playerId, name)
        val outcome = try {
            runEngine(target, input, next)
        } finally {
            // Cleared whatever happened (a cancelled lookup too); only a process death leaves it.
            next.breaker.end(state.playerId, name, OwnSolverBreaker.End.DONE)
        }
        if (outcome.end != OwnSolverBreaker.End.DONE) next.breaker.end(state.playerId, name, outcome.end)
        val text = (outcome as? EngineOutcome.Reply)?.text
        if (text == null) {
            state.failures += "${stepName(name)} ${outcome.label}"
            return null
        }
        return OwnSolverProtocol.decode(text, state.batch).also { decoded ->
            if (decoded == null) state.failures += "${stepName(name)} reply"
        }
    }

    /** A reply as the rule's step; a step whose signatures failed V answers no signature. */
    private fun record(
        state: Ladder,
        next: Next,
        name: OwnSolverBreaker.Step,
        decoded: OwnSolverProtocol.Decoded,
    ): OwnSolverLadderRule.Step {
        if (decoded is OwnSolverProtocol.Decoded.Solved) {
            state.replies[name] = decoded
            decoded.failures.forEach { state.failures += "${stepName(name)} $it" }
        } else if (decoded is OwnSolverProtocol.Decoded.Failed) {
            state.failures += "${stepName(name)} error ${decoded.reason}"
        }
        val step = OwnSolverLadderRule.step(decoded, state.batch).let { step ->
            if (next.breaker.signaturesAllowed(state.playerId, name)) {
                step
            } else {
                step.copy(answered = step.answered - OwnSolverLadderRule.Kind.SIGNATURE)
            }
        }
        state.steps[name] = step
        return step
    }

    private sealed interface EngineOutcome {
        val label: String
        val end: OwnSolverBreaker.End get() = OwnSolverBreaker.End.DONE

        class Reply(val text: String) : EngineOutcome {
            override val label: String get() = "reply"
        }

        object NoReply : EngineOutcome {
            override val label: String get() = "engine"
        }

        object NotReady : EngineOutcome {
            override val label: String get() = "ready timeout"
        }

        object JobTimeout : EngineOutcome {
            override val label: String get() = "job timeout"
            override val end: OwnSolverBreaker.End get() = OwnSolverBreaker.End.TIMEOUT
        }

        object Gone : EngineOutcome {
            override val label: String get() = "gone"
            override val end: OwnSolverBreaker.End get() = OwnSolverBreaker.End.GONE
        }
    }

    private class Box(val value: String?)

    /**
     * Ready and job time out apart: a page that never takes the job is only a ready timeout (no
     * strike); a job that does not finish is a job timeout. An engine that cannot tell has one
     * timeout for both, counted as a job timeout.
     */
    private suspend fun runEngine(target: OwnSolverEngine, input: String, next: Next): EngineOutcome = coroutineScope {
        if (!target.reportsReady) {
            val box = withTimeoutOrNull(next.readyTimeoutMillis + next.jobTimeoutMillis) { Box(target.run(input)) }
            val text = box?.value
            return@coroutineScope when {
                box == null -> EngineOutcome.JobTimeout
                text == null -> EngineOutcome.NoReply
                else -> EngineOutcome.Reply(text)
            }
        }
        val started = CompletableDeferred<Unit>()
        val gone = AtomicBoolean(false)
        val events = object : OwnSolverEngine.Events {
            override fun started() {
                started.complete(Unit)
            }

            override fun gone() {
                gone.set(true)
            }
        }
        val run = async { target.run(input, events) }
        val ready = withTimeoutOrNull(next.readyTimeoutMillis) {
            select<Unit> {
                started.onAwait { }
                run.onAwait { }
            }
        }
        if (ready == null) {
            run.cancel()
            return@coroutineScope EngineOutcome.NotReady
        }
        val box = withTimeoutOrNull(next.jobTimeoutMillis) { Box(run.await()) }
        val text = box?.value
        when {
            box == null -> {
                run.cancel()
                EngineOutcome.JobTimeout
            }
            gone.get() -> EngineOutcome.Gone
            text == null -> EngineOutcome.NoReply
            else -> EngineOutcome.Reply(text)
        }
    }

    private fun kindName(kind: OwnSolverLadderRule.Kind): String = when (kind) {
        OwnSolverLadderRule.Kind.RATE -> "n"
        OwnSolverLadderRule.Kind.SIGNATURE -> "sig"
    }

    private fun stepName(step: OwnSolverBreaker.Step): String = when (step) {
        OwnSolverBreaker.Step.WORKER_P -> "P"
        OwnSolverBreaker.Step.WORKER_B -> "B"
        OwnSolverBreaker.Step.PAGE_P -> "pP"
        OwnSolverBreaker.Step.PAGE_B -> "pB"
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

        /** N5b: a fresh WebView loading the app's own page and scripts; no strike when it is slow. */
        const val DEFAULT_READY_TIMEOUT_MILLIS: Long = 20_000

        private const val MAX_PASSED = 64
        private const val MAX_LADDER_FAILURES = 16
        private val NOT_RUN = OwnSolverLadderRule.Step(emptySet(), null, listOf("not run"))
        private val PAGE_STEPS = listOf(OwnSolverBreaker.Step.PAGE_P, OwnSolverBreaker.Step.PAGE_B)

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
