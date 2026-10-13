package com.alal.yft.extractor.master.solver

import com.alal.yft.extractor.api.PlayerScriptChallengeKind
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.get
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Phase 1.2 N7 (owner's option b): one ladder step on one player build for the phone canary, the
 * debug build's owner tool "YFT solver canary" only. Nothing on a user's path makes one.
 *
 * [run] builds the step's job exactly as [OwnPlayerScriptRunner] builds it with ownSolverNext when
 * the lookup has its full time: worker P from the player text (keeping the prepared program) or
 * from that kept program, worker B, page P or page B, each with its step's cap as the deadline;
 * the worker steps ask for the tripwire and carry the locator parts kept for that step. It sends
 * the job to the engine, times it and decodes the reply. The app's breaker ([breaker], the object
 * the app's runner uses) is around every job exactly as in the runner: a step it has off does not
 * run ([BREAKER_OFF]); the marker is written before the job and cleared after it; a job timeout
 * or a gone renderer turns the step off until the process restarts; a step whose signatures V
 * failed answers no signature.
 *
 * The time ([Summary.totalMillis], the N7 "hit": from sending the job to its reply, the engine's
 * cold start included; [Summary.readyMillis]: until the page took the job) starts after the marker
 * is written and stops before it is cleared. A run that did not end with a reply, or that ran out
 * of time ([Summary.budget]), is [Run.stopped]: the canary goes no further.
 *
 * Locator parts are kept per player build and worker step ([workerP], [workerB]: P's and B's part
 * of a kind, and the builds of one player version, never replace each other) and learned with
 * [learn] only for kinds the caller found right (the canary compares with main's ejs). Prepared
 * programs stay in memory, at most [MAX_PREPARED] builds. Inputs and values stay in [Run.rate] and
 * [Run.signature]; summaries and every toString hold kinds, counts, failure classes and times.
 */
class OwnSolverProbe(
    private val breaker: OwnSolverBreaker,
    private val workerP: OwnSolverLocators = OwnSolverLocators(),
    private val workerB: OwnSolverLocators = OwnSolverLocators(),
    private val readyTimeoutMillis: Long = OwnPlayerScriptRunner.DEFAULT_READY_TIMEOUT_MILLIS,
    private val jobTimeoutMillis: Long = OwnPlayerScriptRunner.DEFAULT_TIMEOUT_MILLIS,
    private val workerCapMillis: Long = OwnPlayerScriptRunner.DEFAULT_WORKER_CAP_MILLIS,
    private val pageCapMillis: Long = OwnPlayerScriptRunner.DEFAULT_PAGE_CAP_MILLIS,
    private val clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
) {
    init {
        require(readyTimeoutMillis > 0 && jobTimeoutMillis > 0)
        // As the runner: the core's deadline ends before the job timeout.
        require(workerCapMillis in 1 until jobTimeoutMillis && pageCapMillis in 1 until jobTimeoutMillis)
    }

    /** One step's run as the report shows it: kinds, counts, failure classes and times only. */
    data class Summary(
        val step: OwnSolverBreaker.Step,
        /** [REPLY], or why there was none ([BREAKER_OFF], [READY_TIMEOUT], [JOB_TIMEOUT], ...). */
        val outcome: String,
        val fromPrepared: Boolean = false,
        val strategyB: Boolean = false,
        /** The job asked for parts (the worker steps). */
        val located: Boolean = false,
        /** The kinds whose kept part the job carried. */
        val partsSent: Set<PlayerScriptChallengeKind> = emptySet(),
        /** The kinds a sent part answered (the core's `locator_hit`). */
        val locatorHits: Set<PlayerScriptChallengeKind> = emptySet(),
        /** The kinds the core gave a part for (kinds it answered cleanly). */
        val partsGiven: Set<PlayerScriptChallengeKind> = emptySet(),
        /** The kinds answered for every input; a step whose signatures V failed answers none. */
        val answered: Set<PlayerScriptChallengeKind> = emptySet(),
        val initErrors: Int? = null,
        val failures: List<String> = emptyList(),
        val budget: Boolean = false,
        val signaturesOff: Boolean = false,
        val readyMillis: Long? = null,
        val totalMillis: Long? = null,
    ) {
        /** No reply, or the core ran out of time: the canary stops here. */
        val stopped: Boolean get() = outcome != REPLY || budget
    }

    /** One run: its [summary] and the values it gave by input (session values, never logged). */
    class Run internal constructor(
        val summary: Summary,
        val rate: Map<String, String>,
        val signature: Map<String, String>,
        internal val key: String,
        /** The player text's hash the step ran from; null when the job never went out. */
        internal val scriptHash: String?,
        internal val asked: Set<OwnSolverLadderRule.Kind>,
        /** The reply's parts (the core's locator). */
        internal val given: OwnSolverLocator.Parts?,
    ) {
        val stopped: Boolean get() = summary.stopped

        override fun toString(): String =
            "Run(summary=$summary, rate=${rate.size}, signature=${signature.size})"
    }

    private class Kept(val program: String, val scriptHash: String)

    /** Guarded by itself: player build to prepared program, least recently used first. */
    private val prepared = LinkedHashMap<String, Kept>(4, 0.75f, true)

    /** Whether a worker P run from [build]'s text left a prepared program for [run] to reuse. */
    fun hasPrepared(playerId: String, build: String): Boolean =
        synchronized(prepared) { prepared.containsKey(keyOf(playerId, build)) }

    /**
     * Runs [step] on [build] (`phone`, `main`, ...) of player [playerId], whose text is [source],
     * for the inputs given. [fromPrepared] (worker P only) runs the program a worker P run from the
     * text kept ([NO_PREPARED] when there is none).
     */
    suspend fun run(
        engine: OwnSolverEngine,
        step: OwnSolverBreaker.Step,
        playerId: String,
        build: String,
        source: String,
        rateInputs: List<String>,
        signatureInputs: List<String>,
        fromPrepared: Boolean = false,
    ): Run {
        require(PLAYER_ID.matches(playerId)) { "A player ID" }
        require(BUILD.matches(build)) { "A build name: a few small letters" }
        require(!fromPrepared || step == OwnSolverBreaker.Step.WORKER_P) { "Only worker P runs a kept program" }
        val batch = OwnSolverProtocol.Batch(rateInputs.distinct(), signatureInputs.distinct())
        val key = keyOf(playerId, build)
        val asked = buildSet {
            if (batch.rateInputs.isNotEmpty()) add(OwnSolverLadderRule.Kind.RATE)
            if (batch.signatureInputs.isNotEmpty()) add(OwnSolverLadderRule.Kind.SIGNATURE)
        }
        val strategyB = step == OwnSolverBreaker.Step.WORKER_B || step == OwnSolverBreaker.Step.PAGE_B
        fun notRun(outcome: String) = Run(
            summary = Summary(step, outcome, fromPrepared = fromPrepared, strategyB = strategyB),
            rate = emptyMap(),
            signature = emptyMap(),
            key = key,
            scriptHash = null,
            asked = asked,
            given = null,
        )
        if (!breaker.allows(playerId, step)) return notRun(BREAKER_OFF)
        val kept: Kept? = if (fromPrepared) {
            synchronized(prepared) { prepared[key] } ?: return notRun(NO_PREPARED)
        } else {
            null
        }
        val scriptHash = kept?.scriptHash ?: OwnSolverLocator.sha256Hex(source)
        val worker = step == OwnSolverBreaker.Step.WORKER_P || step == OwnSolverBreaker.Step.WORKER_B
        val parts = if (worker) partsFor(step, key, scriptHash, batch) else null
        val options = when (step) {
            OwnSolverBreaker.Step.WORKER_P ->
                OwnSolverProtocol.Options(tripwire = true, deadlineMillis = workerCapMillis, locator = parts)
            OwnSolverBreaker.Step.WORKER_B -> OwnSolverProtocol.Options(
                strategyB = true,
                hint = false,
                tripwire = true,
                deadlineMillis = workerCapMillis,
                locator = parts,
            )
            OwnSolverBreaker.Step.PAGE_P -> OwnSolverProtocol.Options(deadlineMillis = pageCapMillis)
            OwnSolverBreaker.Step.PAGE_B ->
                OwnSolverProtocol.Options(strategyB = true, hint = false, deadlineMillis = pageCapMillis)
        }
        val input = if (kept != null) {
            OwnSolverProtocol.encode(OwnSolverProtocol.Player.Prepared(kept.program), batch, false, options)
        } else {
            val keepPrepared = step == OwnSolverBreaker.Step.WORKER_P
            OwnSolverProtocol.encode(OwnSolverProtocol.Player.Raw(source), batch, keepPrepared, options)
        }

        breaker.begin(playerId, step)
        val timed = try {
            runEngine(engine, input)
        } finally {
            // As the runner: cleared whatever happened; only a process death leaves the marker.
            breaker.end(playerId, step, OwnSolverBreaker.End.DONE)
        }
        if (timed.end != OwnSolverBreaker.End.DONE) breaker.end(playerId, step, timed.end)

        val decoded = timed.text?.let { OwnSolverProtocol.decode(it, batch) }
        val solved = decoded as? OwnSolverProtocol.Decoded.Solved
        if (step == OwnSolverBreaker.Step.WORKER_P) {
            synchronized(prepared) {
                if (kept != null) {
                    // As the runner: a kept program that broke goes; one that ran out of time stays.
                    if (solved == null || (solved.failedWhole && !solved.budget)) prepared.remove(key)
                } else {
                    solved?.preparedProgram?.let { remember(key, Kept(it, scriptHash)) }
                }
            }
        }
        val signaturesOff = !breaker.signaturesAllowed(playerId, step)
        val summary = summaryOf(
            step = step,
            outcome = outcomeOf(timed.text, timed.label, decoded),
            fromPrepared = kept != null,
            strategyB = strategyB,
            sent = parts,
            decoded = decoded,
            batch = batch,
            signaturesOff = signaturesOff,
            readyMillis = timed.readyMillis,
            totalMillis = timed.totalMillis,
        )
        return Run(
            summary = summary,
            rate = solved?.rate.orEmpty(),
            signature = if (signaturesOff) emptyMap() else solved?.signature.orEmpty(),
            key = key,
            scriptHash = scriptHash,
            asked = asked,
            given = solved?.locator,
        )
    }

    /**
     * Keeps [run]'s parts for the kinds in [right] (found right by the caller) as the runner keeps
     * the chosen step's: a worker run with no init errors whose core gave a part of its strategy.
     * Any other kept part of that build and step for an asked kind goes (it missed, it was wrong, or
     * it is no longer given). A run whose job never went out changes nothing.
     */
    fun learn(run: Run, right: Set<PlayerScriptChallengeKind>) {
        val step = run.summary.step
        if (step != OwnSolverBreaker.Step.WORKER_P && step != OwnSolverBreaker.Step.WORKER_B) return
        val hash = run.scriptHash ?: return
        val store = storeOf(step)
        val strategyB = step == OwnSolverBreaker.Step.WORKER_B
        for (kind in run.asked) {
            val fresh = run.given?.of(kind)
                ?.takeIf { run.summary.initErrors == 0 && publicKind(kind) in right && it.isB == strategyB }
                ?.let { OwnSolverLocators.Located(step, it) }
            val old = store.peek(run.key)?.of(kind)
            if (old != null && old != fresh) store.drop(run.key, kind)
            if (fresh != null) store.put(run.key, hash, kind, fresh)
        }
    }

    private fun remember(key: String, kept: Kept) {
        prepared[key] = kept
        while (prepared.size > MAX_PREPARED) prepared.remove(prepared.keys.first())
    }

    private fun storeOf(step: OwnSolverBreaker.Step): OwnSolverLocators =
        if (step == OwnSolverBreaker.Step.WORKER_B) workerB else workerP

    /** As the runner's: the parts kept for [step] on this text; empty (locate only) when none. */
    private fun partsFor(
        step: OwnSolverBreaker.Step,
        key: String,
        scriptHash: String,
        batch: OwnSolverProtocol.Batch,
    ): OwnSolverLocator.Parts {
        val entry = storeOf(step).get(key, scriptHash) ?: return OwnSolverLocator.Parts()
        fun part(kind: OwnSolverLadderRule.Kind, inputs: List<String>) =
            entry.of(kind)?.takeIf { it.step == step && inputs.isNotEmpty() }?.part
        return OwnSolverLocator.Parts(
            rate = part(OwnSolverLadderRule.Kind.RATE, batch.rateInputs),
            signature = part(OwnSolverLadderRule.Kind.SIGNATURE, batch.signatureInputs),
        )
    }

    private class Timed(
        val label: String,
        val end: OwnSolverBreaker.End,
        val text: String?,
        val readyMillis: Long?,
        val totalMillis: Long?,
    )

    private class Box(val value: String?)

    /** The runner's ready and job timeouts, timed on [clock] from the job's start. */
    private suspend fun runEngine(target: OwnSolverEngine, input: String): Timed = coroutineScope {
        val sentAt = clock()
        if (!target.reportsReady) {
            val box = withTimeoutOrNull(readyTimeoutMillis + jobTimeoutMillis) { Box(target.run(input)) }
            val total = clock() - sentAt
            val text = box?.value
            return@coroutineScope when {
                box == null -> Timed(JOB_TIMEOUT, OwnSolverBreaker.End.TIMEOUT, null, null, total)
                text == null -> Timed(ENGINE, OwnSolverBreaker.End.DONE, null, null, total)
                else -> Timed(REPLY, OwnSolverBreaker.End.DONE, text, null, total)
            }
        }
        val readyAt = AtomicLong(NOT_YET)
        val started = CompletableDeferred<Unit>()
        val gone = AtomicBoolean(false)
        val events = object : OwnSolverEngine.Events {
            override fun started() {
                readyAt.compareAndSet(NOT_YET, clock())
                started.complete(Unit)
            }

            override fun gone() {
                gone.set(true)
            }
        }
        val run = async { target.run(input, events) }
        val ready = withTimeoutOrNull(readyTimeoutMillis) {
            select<Unit> {
                started.onAwait { }
                run.onAwait { }
            }
        }
        if (ready == null) {
            run.cancel()
            return@coroutineScope Timed(READY_TIMEOUT, OwnSolverBreaker.End.DONE, null, null, null)
        }
        val box = withTimeoutOrNull(jobTimeoutMillis) { Box(run.await()) }
        val total = clock() - sentAt
        val readyMillis = readyAt.get().takeIf { it != NOT_YET }?.let { it - sentAt }
        val text = box?.value
        when {
            box == null -> {
                run.cancel()
                Timed(JOB_TIMEOUT, OwnSolverBreaker.End.TIMEOUT, null, readyMillis, total)
            }
            gone.get() -> Timed(GONE, OwnSolverBreaker.End.GONE, null, readyMillis, total)
            text == null -> Timed(ENGINE, OwnSolverBreaker.End.DONE, null, readyMillis, total)
            else -> Timed(REPLY, OwnSolverBreaker.End.DONE, text, readyMillis, total)
        }
    }

    companion object {
        /** The engine replied (whatever the core answered). */
        const val REPLY: String = "reply"

        /** The engine gave no reply. */
        const val ENGINE: String = "engine"
        const val READY_TIMEOUT: String = "ready timeout"
        const val JOB_TIMEOUT: String = "job timeout"
        const val GONE: String = "gone"
        const val REPLY_UNREADABLE: String = "reply unreadable"
        const val BREAKER_OFF: String = "breaker off"
        const val NO_PREPARED: String = "no prepared program"

        /** Prepared programs kept in memory (the phone and the main build). */
        const val MAX_PREPARED: Int = 2

        private const val NANOS_PER_MILLI = 1_000_000L
        private const val NOT_YET = Long.MIN_VALUE

        /** The locator file's player key is `[A-Za-z0-9_-]{4,32}`. */
        private const val MAX_KEY_CHARS = 32
        private const val MAX_TAIL_NODES = 2_000

        /** Where a job's fields after the player text start; never inside an escaped string. */
        private const val BATCH_FIELD = ",\"n\":["
        private const val PREPARED_PREFIX = "{\"type\":\"prepared\""
        private val PLAYER_ID = Regex("[A-Za-z0-9_-]{4,32}")
        private val BUILD = Regex("[a-z]{1,8}")

        /**
         * One job as an engine got it ([input], from [OwnPlayerScriptRunner] or [run]) and its
         * [reply], as a [Summary] without times: [Summary.outcome] is [REPLY], [ENGINE] (no reply),
         * [REPLY_UNREADABLE] or the worker's error class. The canary's view of the app runner's
         * jobs in its timed lookup. The step comes from the job's fields (the tripwire marks a
         * worker job, strategy B a B job). Null when [input] is not a solver job.
         */
        fun describe(input: String, reply: String?): Summary? {
            val at = input.indexOf(BATCH_FIELD)
            if (at < 0) return null
            val tail = BoundedJsonParser.parse("{" + input.substring(at + 1), maxNodes = MAX_TAIL_NODES)
                as? JsonValue.Object ?: return null
            val rateInputs = textsOf(tail["n"]) ?: return null
            val signatureInputs = textsOf(tail["sig"]) ?: return null
            if (rateInputs.isEmpty() && signatureInputs.isEmpty()) return null
            val strategyB = (tail["strategy"] as? JsonValue.Text)?.value == "B"
            val tripwire = (tail["tripwire"] as? JsonValue.Bool)?.value == true
            val step = when {
                tripwire && strategyB -> OwnSolverBreaker.Step.WORKER_B
                tripwire -> OwnSolverBreaker.Step.WORKER_P
                strategyB -> OwnSolverBreaker.Step.PAGE_B
                else -> OwnSolverBreaker.Step.PAGE_P
            }
            val locator = tail.entries["locator"]
            val sent = when {
                locator == null -> null
                locator is JsonValue.Object && locator.entries.isEmpty() -> OwnSolverLocator.Parts()
                else -> OwnSolverLocator.decode(locator) ?: OwnSolverLocator.Parts()
            }
            val batch = OwnSolverProtocol.Batch(rateInputs.distinct(), signatureInputs.distinct())
            val decoded = reply?.let { OwnSolverProtocol.decode(it, batch) }
            return summaryOf(
                step = step,
                outcome = outcomeOf(reply, ENGINE, decoded),
                fromPrepared = input.startsWith(PREPARED_PREFIX),
                strategyB = strategyB,
                sent = sent,
                decoded = decoded,
                batch = batch,
                signaturesOff = false,
                readyMillis = null,
                totalMillis = null,
            )
        }

        /** The store key of a player build; within the locator file's player key form. */
        internal fun keyOf(playerId: String, build: String): String {
            val suffix = "-$build"
            return playerId.take(MAX_KEY_CHARS - suffix.length) + suffix
        }

        private fun outcomeOf(text: String?, label: String, decoded: OwnSolverProtocol.Decoded?): String = when {
            text == null -> label
            decoded == null -> REPLY_UNREADABLE
            decoded is OwnSolverProtocol.Decoded.Failed -> "error ${decoded.reason}"
            else -> REPLY
        }

        private fun summaryOf(
            step: OwnSolverBreaker.Step,
            outcome: String,
            fromPrepared: Boolean,
            strategyB: Boolean,
            sent: OwnSolverLocator.Parts?,
            decoded: OwnSolverProtocol.Decoded?,
            batch: OwnSolverProtocol.Batch,
            signaturesOff: Boolean,
            readyMillis: Long?,
            totalMillis: Long?,
        ): Summary {
            val solved = decoded as? OwnSolverProtocol.Decoded.Solved
            val answered = decoded?.let { OwnSolverLadderRule.step(it, batch).answered }.orEmpty()
                .let { if (signaturesOff) it - OwnSolverLadderRule.Kind.SIGNATURE else it }
            return Summary(
                step = step,
                outcome = outcome,
                fromPrepared = fromPrepared,
                strategyB = strategyB,
                located = sent != null,
                partsSent = kindsOf(sent),
                locatorHits = solved?.locatorHits.orEmpty().mapTo(LinkedHashSet()) { publicKind(it) },
                partsGiven = kindsOf(solved?.locator),
                answered = answered.mapTo(LinkedHashSet()) { publicKind(it) },
                initErrors = solved?.initErrors,
                failures = when (decoded) {
                    is OwnSolverProtocol.Decoded.Solved -> decoded.failures
                    is OwnSolverProtocol.Decoded.Failed -> listOf(decoded.reason)
                    null -> emptyList()
                },
                budget = solved?.budget == true,
                signaturesOff = signaturesOff,
                readyMillis = readyMillis,
                totalMillis = totalMillis,
            )
        }

        private fun kindsOf(parts: OwnSolverLocator.Parts?): Set<PlayerScriptChallengeKind> = buildSet {
            if (parts?.rate != null) add(PlayerScriptChallengeKind.RATE_PARAM)
            if (parts?.signature != null) add(PlayerScriptChallengeKind.SIGNATURE)
        }

        private fun publicKind(kind: OwnSolverLadderRule.Kind): PlayerScriptChallengeKind = when (kind) {
            OwnSolverLadderRule.Kind.RATE -> PlayerScriptChallengeKind.RATE_PARAM
            OwnSolverLadderRule.Kind.SIGNATURE -> PlayerScriptChallengeKind.SIGNATURE
        }

        private fun textsOf(node: JsonValue?): List<String>? =
            (node as? JsonValue.Array)?.items?.map { (it as? JsonValue.Text)?.value ?: return null }
    }
}