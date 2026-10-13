package com.alal.yft.extractor.master.solver

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.PlayerScriptChallengeKind
import com.alal.yft.extractor.api.PlayerScriptRequest
import com.alal.yft.extractor.api.PlayerScriptResult
import com.alal.yft.extractor.api.PlayerScriptRunner
import com.alal.yft.extractor.api.SiteExtractionFailure
import java.util.WeakHashMap
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
 * this runner is Phase 1.1. N5b A.8: Phase 1.1's signatures wait for V as the ladder's do (see
 * [phase11Checked]); its values and its runs are unchanged.
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
     *
     * A.2 caps: each job gets a deadline, min(its step's cap, the time left in the lookup):
     * [workerCapMillis] for worker P and B, [pageCapMillis] for page P and B, [lookupCapMillis]
     * for the whole lookup (from the ladder's start, the player download included; [clock] in
     * milliseconds). The core stops at it and gives `budget` for what it could not check. A step
     * that ended `budget` with its full cap is skipped for that player version until the process
     * restarts; with a shorter deadline (the lookup had less time left) only on the second such
     * run in a row. No breaker for either. When the lookup has no time left, later steps do not
     * run (not recorded, no breaker), and the ready wait never outlasts the lookup. A core that
     * ignores its deadline still ends in the job timeout, which trips the breaker as before.
     *
     * A.4 locator cache ([locators], see [OwnSolverLocators]): every worker job asks for the core's
     * parts and carries the parts cached for its step and this player text (by SHA-256; the page
     * steps never locate). A kind's part is kept only from the step the ladder chose for it, a
     * worker step without init errors whose core gave one (a kind answered cleanly); a signature's
     * part only once V passed for that answer. A part that came back without a part, or that the
     * ladder did not take, is dropped; V = fail drops that step's signature part.
     *
     * A.5 V ledger ([ledger], see [OwnSolverVLedger]): the passes, keyed by player version, step,
     * player text hash and the hash of the locator part the answer used (`-` for none), so a hit
     * on a part needs its own V pass. Memory unless the app gives both stores a file.
     */
    class Next(
        val page: OwnSolverEngine? = null,
        val breaker: OwnSolverBreaker = OwnSolverBreaker(),
        val readyTimeoutMillis: Long = DEFAULT_READY_TIMEOUT_MILLIS,
        val jobTimeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
        val workerCapMillis: Long = DEFAULT_WORKER_CAP_MILLIS,
        val pageCapMillis: Long = DEFAULT_PAGE_CAP_MILLIS,
        val lookupCapMillis: Long = DEFAULT_LOOKUP_CAP_MILLIS,
        val clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
        val locators: OwnSolverLocators = OwnSolverLocators(),
        val ledger: OwnSolverVLedger = OwnSolverVLedger(),
    ) {
        init {
            require(readyTimeoutMillis > 0 && jobTimeoutMillis > 0 && lookupCapMillis > 0)
            // The core's deadline ends before the job timeout, with time left for the reply.
            require(workerCapMillis in 1 until jobTimeoutMillis && pageCapMillis in 1 until jobTimeoutMillis)
        }

        internal fun capOf(step: OwnSolverBreaker.Step): Long = when (step) {
            OwnSolverBreaker.Step.WORKER_P, OwnSolverBreaker.Step.WORKER_B -> workerCapMillis
            OwnSolverBreaker.Step.PAGE_P, OwnSolverBreaker.Step.PAGE_B -> pageCapMillis
        }
    }

    init {
        require(timeoutMillis > 0)
        require(maxCachedPlayers > 0)
    }

    private val lock = Mutex()

    /**
     * A prepared program and, with ownSolverNext (A.4), the SHA-256 of the player text it came
     * from (Phase 1.1 computes none).
     */
    private class Kept(val program: String, val scriptHash: String?)

    /** Guarded by [lock]: player ID to prepared program, least recently used first. */
    private val prepared = LinkedHashMap<String, Kept>(4, 0.75f, true)

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
        return phase11Checked(playerId, request, resolved)
    }

    /** N5b A.8: Phase 1.1's V passes, by [PendingCheck.cacheKey]; memory only (for the process). */
    private val phase11Passes = OwnSolverVLedger()

    /**
     * N5b A.8, guarded by itself: player versions whose Phase 1.1 signatures V proved wrong (FAIL,
     * with a control), for the process; at most [MAX_PHASE11_OFF], the oldest goes first.
     */
    private val phase11Off = LinkedHashSet<String>()

    /**
     * N5b A.8: Phase 1.1's signatures wait for V as the ladder's do. An answer with signatures
     * gets a [PendingCheck] for worker P with no hashes (one V pass per player version and
     * process, kept in [phase11Passes]) unless that pass is there. A player version in
     * [phase11Off] gives no signatures; an answer left with no values fails as before.
     */
    private fun phase11Checked(
        playerId: String,
        request: PlayerScriptRequest,
        resolved: Map<String, String>,
    ): PlayerScriptResult {
        val signatureKeys = request.challenges
            .filter { it.kind == PlayerScriptChallengeKind.SIGNATURE }
            .mapTo(HashSet()) { it.key }
        val off = synchronized(phase11Off) { playerId in phase11Off }
        val kept = if (off) resolved - signatureKeys else resolved
        if (kept.isEmpty()) return PlayerScriptResult.Failed(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED)
        val check = PendingCheck(playerId, OwnSolverBreaker.Step.WORKER_P)
            .takeIf { kept.keys.any(signatureKeys::contains) && !phase11Passes.passed(it.cacheKey) }
        synchronized(pendingChecks) {
            if (check == null) pendingChecks.remove(request) else pendingChecks[request] = check
        }
        return PlayerScriptResult.Success(kept)
    }

    private suspend fun solve(
        playerId: String,
        request: PlayerScriptRequest,
        batch: OwnSolverProtocol.Batch,
    ): Outcome {
        prepared[playerId]?.let { kept ->
            val reused = run(OwnSolverProtocol.Player.Prepared(kept.program), batch, false)
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
        outcome.program?.let { remember(playerId, Kept(it, null)) }
        return outcome
    }

    private fun remember(playerId: String, kept: Kept) {
        prepared[playerId] = kept
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

    /**
     * Guarded by itself: the V each answer's signatures still need, by request (read once); the
     * ladder's and (A.8) Phase 1.1's.
     */
    private val pendingChecks = WeakHashMap<PlayerScriptRequest, PendingCheck>()

    /**
     * N5b A.4, guarded by itself: a signature's part waiting for its answer's V (written on PASS),
     * by check; at most [MAX_WAITING], the oldest goes first.
     */
    private val waiting = LinkedHashMap<PendingCheck, Learned>()

    /** A part to keep for [kind] of [playerId], learned from player text [scriptHash]. */
    private class Learned(
        val playerId: String,
        val scriptHash: String,
        val kind: OwnSolverLadderRule.Kind,
        val located: OwnSolverLocators.Located,
    )

    /**
     * N5b A.2, guarded by [lock]: the budget memory, per player version and step (keys as the
     * breaker's, never an input or a value), for the process: the step's last run ended `budget`.
     * At most [MAX_BUDGETS] keys; the oldest goes first.
     */
    private val budgets = LinkedHashMap<String, Budget>()

    /**
     * [off]: the step is skipped (a full cap ran out, or a short deadline twice in a row); else
     * one short deadline ran out. [last] is that run as the rule sees it (kinds, counts and
     * classes only): a skipped step counts as it, without answers, so the ladder still sends the
     * kinds to the page when that run needed the page.
     */
    private class Budget(val off: Boolean, val last: OwnSolverLadderRule.Step)

    override fun pendingCheck(request: PlayerScriptRequest): PendingCheck? =
        synchronized(pendingChecks) { pendingChecks.remove(request) }

    override fun record(check: PendingCheck, verdict: SignatureVerdicts.Verdict) {
        val next = next ?: return recordPhase11(check, verdict)
        val learned = synchronized(waiting) { waiting.remove(check) }
        when (verdict) {
            SignatureVerdicts.Verdict.PASS -> {
                next.ledger.pass(check.cacheKey)
                learned?.let { next.locators.put(it.playerId, it.scriptHash, it.kind, it.located) }
            }
            SignatureVerdicts.Verdict.FAIL -> {
                next.breaker.signatureFailed(check.playerId, check.step)
                next.locators.drop(check.playerId, OwnSolverLadderRule.Kind.SIGNATURE, check.step)
            }
            SignatureVerdicts.Verdict.UNKNOWN -> Unit
        }
    }

    /** N5b A.8, Phase 1.1: PASS is kept for the process; FAIL turns the player version's signatures off. */
    private fun recordPhase11(check: PendingCheck, verdict: SignatureVerdicts.Verdict) {
        when (verdict) {
            SignatureVerdicts.Verdict.PASS -> phase11Passes.pass(check.cacheKey)
            SignatureVerdicts.Verdict.FAIL -> synchronized(phase11Off) {
                phase11Off.remove(check.playerId)
                phase11Off += check.playerId
                while (phase11Off.size > MAX_PHASE11_OFF) phase11Off.remove(phase11Off.first())
            }
            SignatureVerdicts.Verdict.UNKNOWN -> Unit
        }
    }

    private suspend fun resolveNext(
        playerId: String,
        request: PlayerScriptRequest,
        batch: OwnSolverProtocol.Batch,
        next: Next,
    ): PlayerScriptResult {
        val ladder = lock.withLock { ladder(Ladder(playerId, request, batch, next.clock()), next) }
        lastFailures = ladder.failures.take(MAX_LADDER_FAILURES)
        val step = ladder.signatureStep
        val check = if (step != null && ladder.signature.isNotEmpty()) checkOf(ladder.state, step) else null
        val pending = check?.takeUnless { next.ledger.passed(it.cacheKey) }
        learn(ladder, next, pending)
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
        /** N5b A.2: when the lookup started, on [Next.clock]. */
        val startedAt: Long,
    ) {
        var source: String? = null

        /** N5b A.4: the SHA-256 of [source], once fetched. */
        var textHash: String? = null
        var fetchFailure: SiteExtractionFailure? = null
        val failures = mutableListOf<String>()
        val replies = HashMap<OwnSolverBreaker.Step, OwnSolverProtocol.Decoded.Solved>()
        val steps = HashMap<OwnSolverBreaker.Step, OwnSolverLadderRule.Step>()

        /** N5b A.4: the player text hash each step ran from (P on the kept program: the program's). */
        val hashes = HashMap<OwnSolverBreaker.Step, String>()

        /** N5b A.4: the parts each worker step was sent. */
        val sent = HashMap<OwnSolverBreaker.Step, OwnSolverLocator.Parts>()
    }

    private class LadderResult(
        val state: Ladder,
        val rate: Map<String, String>,
        val signature: Map<String, String>,
        val rateStep: OwnSolverBreaker.Step?,
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
        var rateStep: OwnSolverBreaker.Step? = null
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
                OwnSolverLadderRule.Kind.RATE -> {
                    rate = reply.rate
                    rateStep = from
                }
                OwnSolverLadderRule.Kind.SIGNATURE -> {
                    signature = reply.signature
                    signatureStep = from
                }
            }
        }
        return LadderResult(state, rate, signature, rateStep, signatureStep, state.failures.toList(), state.fetchFailure)
    }

    /**
     * N5b A.5: the V that [step]'s signatures need: the player text it ran from and the part its
     * answer used (a hit on a part that step was sent; `-` for none).
     */
    private fun checkOf(state: Ladder, step: OwnSolverBreaker.Step): PendingCheck {
        val sent = state.sent[step]?.signature
        val hit = state.replies[step]?.locatorHits?.contains(OwnSolverLadderRule.Kind.SIGNATURE) == true
        return PendingCheck(
            playerId = state.playerId,
            step = step,
            scriptHash = state.hashes[step].orEmpty(),
            // A hit names the part sent (a reply cannot bring a part of its own into the key).
            locatorHash = if (sent != null && hit) OwnSolverLocator.hashOf(sent) else PendingCheck.NO_LOCATOR,
        )
    }

    /**
     * N5b A.4: the cache after a lookup, per asked kind. The part kept is the chosen step's own: a
     * worker step whose run had no init errors and whose core gave a part of its strategy for that
     * kind (the core gives one only for a kind it answered cleanly). Any other part of the entry
     * goes (it missed, it failed, or the ladder took the kind elsewhere). A signature's part is
     * kept once V passed: now when no V is [pending], else on its PASS ([record]).
     */
    private fun learn(ladder: LadderResult, next: Next, pending: PendingCheck?) {
        val state = ladder.state
        val chosen = mapOf(
            OwnSolverLadderRule.Kind.RATE to ladder.rateStep,
            OwnSolverLadderRule.Kind.SIGNATURE to ladder.signatureStep,
        )
        for ((kind, step) in chosen) {
            val asked = when (kind) {
                OwnSolverLadderRule.Kind.RATE -> state.batch.rateInputs.isNotEmpty()
                OwnSolverLadderRule.Kind.SIGNATURE -> state.batch.signatureInputs.isNotEmpty()
            }
            if (!asked) continue
            val hash = step?.let(state.hashes::get)
            val fresh = if (step != null && hash != null) learnedPart(state, step, kind) else null
            val old = next.locators.peek(state.playerId)?.of(kind)
            if (old != null && old != fresh) next.locators.drop(state.playerId, kind)
            if (fresh == null || hash == null) continue
            if (kind == OwnSolverLadderRule.Kind.SIGNATURE && pending != null) {
                synchronized(waiting) {
                    waiting.remove(pending)
                    waiting[pending] = Learned(state.playerId, hash, kind, fresh)
                    while (waiting.size > MAX_WAITING) waiting.remove(waiting.keys.first())
                }
            } else {
                next.locators.put(state.playerId, hash, kind, fresh)
            }
        }
    }

    /** [step]'s part for [kind]: a worker step, no init errors, a part of its strategy. */
    private fun learnedPart(
        state: Ladder,
        step: OwnSolverBreaker.Step,
        kind: OwnSolverLadderRule.Kind,
    ): OwnSolverLocators.Located? {
        if (step != OwnSolverBreaker.Step.WORKER_P && step != OwnSolverBreaker.Step.WORKER_B) return null
        val reply = state.replies[step] ?: return null
        if (reply.initErrors != 0) return null
        val part = reply.locator?.of(kind) ?: return null
        if (part.isB != (step == OwnSolverBreaker.Step.WORKER_B)) return null
        return OwnSolverLocators.Located(step, part)
    }

    /**
     * N5b A.4: the parts cached for worker step [name] on player text [scriptHash]; empty (locate
     * only) when there are none.
     */
    private fun partsFor(state: Ladder, next: Next, name: OwnSolverBreaker.Step, scriptHash: String?): OwnSolverLocator.Parts {
        val entry = scriptHash?.let { next.locators.get(state.playerId, it) } ?: return OwnSolverLocator.Parts()
        fun part(kind: OwnSolverLadderRule.Kind, inputs: List<String>) =
            entry.of(kind)?.takeIf { it.step == name && inputs.isNotEmpty() }?.part
        return OwnSolverLocator.Parts(
            rate = part(OwnSolverLadderRule.Kind.RATE, state.batch.rateInputs),
            signature = part(OwnSolverLadderRule.Kind.SIGNATURE, state.batch.signatureInputs),
        )
    }

    /**
     * Worker P: the kept prepared program first (rebuilt once from the player if it broke; a run
     * that ran out of time did not break it).
     */
    private suspend fun workerP(state: Ladder, next: Next): OwnSolverLadderRule.Step? {
        val name = OwnSolverBreaker.Step.WORKER_P
        if (!next.breaker.allows(state.playerId, name)) return null
        skipped(state, name)?.let { return it }
        prepared[state.playerId]?.let { kept ->
            val deadline = deadline(state, next, name) ?: return null
            val parts = partsFor(state, next, name, kept.scriptHash)
            val options = OwnSolverProtocol.Options(tripwire = true, deadlineMillis = deadline, locator = parts)
            val player = OwnSolverProtocol.Player.Prepared(kept.program)
            val input = OwnSolverProtocol.encode(player, state.batch, false, options)
            val decoded = job(state, next, name, engine, input, deadline)
            if (decoded is OwnSolverProtocol.Decoded.Solved && (!decoded.failedWhole || decoded.budget)) {
                return record(state, next, name, decoded, kept.scriptHash, parts)
            }
            prepared.remove(state.playerId)
        }
        if (!next.breaker.allows(state.playerId, name)) return null
        val source = source(state) ?: return null
        val deadline = deadline(state, next, name) ?: return null
        val parts = partsFor(state, next, name, state.textHash)
        val options = OwnSolverProtocol.Options(tripwire = true, deadlineMillis = deadline, locator = parts)
        val input = OwnSolverProtocol.encode(OwnSolverProtocol.Player.Raw(source), state.batch, true, options)
        val decoded = job(state, next, name, engine, input, deadline) ?: return null
        (decoded as? OwnSolverProtocol.Decoded.Solved)?.preparedProgram?.let {
            remember(state.playerId, Kept(it, state.textHash))
        }
        return record(state, next, name, decoded, state.textHash, parts)
    }

    /** Worker B or a page step: from the player text, nothing kept; the page never locates. */
    private suspend fun fromText(
        state: Ladder,
        next: Next,
        name: OwnSolverBreaker.Step,
        target: OwnSolverEngine,
        options: OwnSolverProtocol.Options,
    ): OwnSolverLadderRule.Step? {
        if (!next.breaker.allows(state.playerId, name)) return null
        skipped(state, name)?.let { return it }
        val source = source(state) ?: return null
        val deadline = deadline(state, next, name) ?: return null
        val parts = if (name == OwnSolverBreaker.Step.WORKER_B) partsFor(state, next, name, state.textHash) else null
        val timed = options.copy(deadlineMillis = deadline, locator = parts)
        val input = OwnSolverProtocol.encode(OwnSolverProtocol.Player.Raw(source), state.batch, false, timed)
        val decoded = job(state, next, name, target, input, deadline) ?: return null
        return record(state, next, name, decoded, state.textHash, parts)
    }

    /**
     * N5b A.2: a step off for budget does not run; the ladder takes its last run instead, without
     * answers. Null: the step runs.
     */
    private fun skipped(state: Ladder, name: OwnSolverBreaker.Step): OwnSolverLadderRule.Step? {
        val budget = budgets[OwnSolverBreaker.key(state.playerId, name)]
        if (budget == null || !budget.off) return null
        state.failures += "${stepName(name)} budget off"
        return budget.last.copy(answered = emptySet())
    }

    /** N5b A.2: the time left in the lookup, in milliseconds (0 or less: none). */
    private fun left(state: Ladder, next: Next): Long = next.lookupCapMillis - (next.clock() - state.startedAt)

    /**
     * N5b A.2: [name]'s deadline, min(its cap, the time left in the lookup); null when the lookup
     * has no time left: the step does not run, nothing is recorded, no breaker.
     */
    private fun deadline(state: Ladder, next: Next, name: OwnSolverBreaker.Step): Long? {
        val left = left(state, next)
        if (left <= 0) {
            state.failures += "${stepName(name)} lookup cap"
            return null
        }
        return minOf(next.capOf(name), left)
    }

    /**
     * N5b A.2: the budget memory after a run of [name] with [deadline]: [decoded] null (no reply)
     * or not `budget` starts the count again; `budget` with the full cap turns the step off at
     * once, with a shorter deadline the second time in a row. A job that never started (ready
     * timeout) is not a run and does not come here.
     */
    private fun noteBudget(
        state: Ladder,
        next: Next,
        name: OwnSolverBreaker.Step,
        deadline: Long,
        decoded: OwnSolverProtocol.Decoded?,
    ) {
        val key = OwnSolverBreaker.key(state.playerId, name)
        val before = budgets.remove(key)
        if (decoded !is OwnSolverProtocol.Decoded.Solved || !decoded.budget) return
        val off = deadline >= next.capOf(name) || before != null
        budgets[key] = Budget(off, OwnSolverLadderRule.step(decoded, state.batch))
        while (budgets.size > MAX_BUDGETS) budgets.remove(budgets.keys.first())
    }

    private suspend fun source(state: Ladder): String? {
        state.source?.let { return it }
        if (state.fetchFailure != null) return null
        return when (val fetched = fetchPlayer(state.playerId, state.request)) {
            is Fetched.Source -> fetched.text.also {
                state.source = it
                state.textHash = OwnSolverLocator.sha256Hex(it)
            }
            is Fetched.Failed -> {
                state.fetchFailure = fetched.reason
                state.failures += "fetch ${fetched.reason.name}"
                null
            }
        }
    }

    /**
     * One job with the breaker's marker around it; null when no reply came. The ready wait is
     * bounded by the time left in the lookup; the job's own timeout is not (a core that ignores
     * its [deadline] ends in the job timeout and the breaker).
     */
    private suspend fun job(
        state: Ladder,
        next: Next,
        name: OwnSolverBreaker.Step,
        target: OwnSolverEngine,
        input: String,
        deadline: Long,
    ): OwnSolverProtocol.Decoded? {
        val readyMillis = left(state, next).coerceIn(1, next.readyTimeoutMillis)
        next.breaker.begin(state.playerId, name)
        val outcome = try {
            runEngine(target, input, next, readyMillis)
        } finally {
            // Cleared whatever happened (a cancelled lookup too); only a process death leaves it.
            next.breaker.end(state.playerId, name, OwnSolverBreaker.End.DONE)
        }
        if (outcome.end != OwnSolverBreaker.End.DONE) next.breaker.end(state.playerId, name, outcome.end)
        val text = (outcome as? EngineOutcome.Reply)?.text
        if (text == null) {
            state.failures += "${stepName(name)} ${outcome.label}"
            if (outcome !is EngineOutcome.NotReady) noteBudget(state, next, name, deadline, null)
            return null
        }
        return OwnSolverProtocol.decode(text, state.batch).also { decoded ->
            if (decoded == null) state.failures += "${stepName(name)} reply"
            noteBudget(state, next, name, deadline, decoded)
        }
    }

    /**
     * A reply as the rule's step; a step whose signatures failed V answers no signature. A.4: the
     * player text hash the step ran from and the parts it was sent.
     */
    private fun record(
        state: Ladder,
        next: Next,
        name: OwnSolverBreaker.Step,
        decoded: OwnSolverProtocol.Decoded,
        scriptHash: String?,
        parts: OwnSolverLocator.Parts?,
    ): OwnSolverLadderRule.Step {
        scriptHash?.let { state.hashes[name] = it } ?: state.hashes.remove(name)
        parts?.let { state.sent[name] = it } ?: state.sent.remove(name)
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
    private suspend fun runEngine(
        target: OwnSolverEngine,
        input: String,
        next: Next,
        readyMillis: Long,
    ): EngineOutcome = coroutineScope {
        if (!target.reportsReady) {
            val box = withTimeoutOrNull(readyMillis + next.jobTimeoutMillis) { Box(target.run(input)) }
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
        val ready = withTimeoutOrNull(readyMillis) {
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

        /**
         * N5b A.2 caps (owner, 2026-10-13, from A.1's numbers with a phone margin of x4): a
         * worker step's deadline is the job timeout less 5 s for the reply (the core checks it
         * every 4096 steps); P's init (20 s) fits with 20 s left for its search.
         */
        const val DEFAULT_WORKER_CAP_MILLIS: Long = 40_000

        /**
         * A page step's deadline, tighter: it runs only for the kinds the workers left, on the
         * app's shared renderer (provisional; N7 re-tunes it with the phone's numbers).
         */
        const val DEFAULT_PAGE_CAP_MILLIS: Long = 35_000

        /** One lookup: P's init budget (20 s) + a worker cap, so B keeps its full cap after P. */
        const val DEFAULT_LOOKUP_CAP_MILLIS: Long = 60_000

        private const val NANOS_PER_MILLI = 1_000_000L

        private const val MAX_WAITING = 16
        private const val MAX_PHASE11_OFF = 32
        private const val MAX_BUDGETS = 64
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
