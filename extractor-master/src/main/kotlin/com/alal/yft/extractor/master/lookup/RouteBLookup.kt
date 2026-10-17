package com.alal.yft.extractor.master.lookup

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.modules.youtube.ladder.LookupMemo
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStart
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Phase 1.4 item 7 (YouTube routing B, `MASTER_KEY_PHASE1_4_ROUTING_PLAN.md` §3 R2): one route-B
 * run of Master's YouTube module, over the lookup's [record] (main's asks, R1). It is put in the
 * coroutine context of Master's run only after main failed with a recoverable reason (the engine,
 * route B R3); without it Master's route-B wrappers ([RouteBHttpClient], [RouteBMediaStartReader],
 * [RouteBPlayerScripts]) only pass through.
 *
 * One lookup = main + Master (social plan decision 8). Each request of Master's module, in order:
 *  1. carrying the user's session or credentials ([LookupAsk.authorized]): refused, never sent;
 *  2. identical to one whose unauthenticated answer main got in this lookup: answered from the
 *     record, once ([replayed]);
 *  3. after a 429 (or a rate limit) in this lookup, main's or Master's: refused, nothing new goes
 *     out (a `RATE_LIMITED` failure, no status);
 *  4. to an endpoint (method, address, body; not a media host) already asked in this lookup —
 *     main's answer was authorized, too large to keep or already replayed, or Master asked it —
 *     refused: what was asked is not asked again;
 *  5. otherwise new ([newRequests]): sent and listed in the record as Master's (never kept). Only
 *     the rungs main lacks ask what main did not (A's version, the O rungs' requests in a form
 *     main did not send, V, H, L's reads, Z's file checks), each inside its own guardrail-7 bound.
 * File checks and media reads (never replayed, as in the ladder's memo) follow 1, 3 and 5. A
 * refusal reads as a request that got no answer (`NETWORK`, no status); it is never
 * `UNSUPPORTED_URL`. Counts only: nothing is logged; [toString] and [summary] show numbers.
 */
class RouteBLookup(val record: LookupRecord) : AbstractCoroutineContextElement(RouteBLookup) {
    private val replayedCount = AtomicInteger()
    private val newCount = AtomicInteger()
    private val refusedCount = AtomicInteger()

    /** Endpoints Master sent in this run, answered or still in flight. */
    private val sent = HashSet<String>()

    /** Master's requests answered from main's record. */
    val replayed: Int get() = replayedCount.get()

    /** Master's requests that went out. */
    val newRequests: Int get() = newCount.get()

    /** Master's requests (and player-script runs) refused by rules 1, 3 and 4. */
    val refused: Int get() = refusedCount.get()

    /** For the result's details (R3): `replayed N; new M` and, when any, `; refused K`. */
    fun summary(): String = "replayed $replayed; new $newRequests" +
        (refused.takeIf { it > 0 }?.let { "; refused $it" } ?: "")

    override fun toString(): String = "RouteBLookup(replayed=$replayed, new=$newRequests, refused=$refused)"

    /** A GET or POST of Master's module ([ask]), [send] when it may go out. */
    internal suspend fun exchange(
        ask: LookupAsk,
        maxBodyBytes: Long,
        send: suspend () -> ExtractorHttpResult,
    ): ExtractorHttpResult {
        if (ask.authorized) return refuse(NOT_SENT)
        record.replay(ask, maxBodyBytes)?.let { replayedCount.incrementAndGet(); return it }
        if (record.rateLimited) return refuse(RATE_LIMITED)
        if (LookupMemo.memoable(ask.url) && !claim(ask)) return refuse(NOT_SENT)
        newCount.incrementAndGet()
        val started = record.now()
        var result: ExtractorHttpResult? = null
        try {
            return send().also { result = it }
        } finally {
            result?.let { record.record(ask, it, started, LookupRecord.Asker.MASTER) }
                ?: record.unanswered(ask, started, LookupRecord.Asker.MASTER)
        }
    }

    /** A file check of Master's module ([ask]): never replayed. */
    internal suspend fun check(ask: LookupAsk, send: suspend () -> ExtractorProbeResult): ExtractorProbeResult {
        if (ask.authorized) return refusedCheck(SiteExtractionFailure.NETWORK)
        if (record.rateLimited) return refusedCheck(SiteExtractionFailure.RATE_LIMITED)
        newCount.incrementAndGet()
        val started = record.now()
        var result: ExtractorProbeResult? = null
        try {
            return send().also { result = it }
        } finally {
            result?.let { record.record(ask, it, started, LookupRecord.Asker.MASTER) }
                ?: record.unanswered(ask, started, LookupRecord.Asker.MASTER)
        }
    }

    /** A media file's first bytes for the ladder ([ask]): never replayed; a 429 stops what follows. */
    internal suspend fun read(ask: LookupAsk, send: suspend () -> MediaStart): MediaStart {
        if (ask.authorized || record.rateLimited) {
            refusedCount.incrementAndGet()
            return MediaStart.Failed(null)
        }
        newCount.incrementAndGet()
        val started = record.now()
        var result: MediaStart? = null
        try {
            return send().also { result = it }
        } finally {
            result?.let { record.record(ask, probeOf(it), started, LookupRecord.Asker.MASTER) }
                ?: record.unanswered(ask, started, LookupRecord.Asker.MASTER)
        }
    }

    /** Whether the player script may run: not after a 429 in this lookup (its script fetch would be new). */
    internal fun scriptsAllowed(): Boolean {
        if (!record.rateLimited) return true
        refusedCount.incrementAndGet()
        return false
    }

    /** Rule 4: [ask]'s endpoint was not asked in this lookup, and now it is Master's. */
    private fun claim(ask: LookupAsk): Boolean = synchronized(sent) {
        !record.asked(ask) && sent.add(ask.endpoint)
    }

    private fun refuse(failure: ExtractorHttpResult.Failure): ExtractorHttpResult {
        refusedCount.incrementAndGet()
        return failure
    }

    private fun refusedCheck(reason: SiteExtractionFailure): ExtractorProbeResult {
        refusedCount.incrementAndGet()
        return ExtractorProbeResult.Refused(reason)
    }

    companion object Key : CoroutineContext.Key<RouteBLookup> {
        private const val HTTP_TOO_MANY = 429
        private val NOT_SENT = ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)
        private val RATE_LIMITED = ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED)

        /** What a media read means to the record: an answer, a 429 (rate limited) or no answer. */
        private fun probeOf(start: MediaStart): ExtractorProbeResult = when (start) {
            is MediaStart.Read -> if (start.statusCode in 200..299) {
                ExtractorProbeResult.Answered(start.statusCode)
            } else {
                ExtractorProbeResult.Refused(SiteExtractionFailure.HTTP_STATUS, start.statusCode.takeIf { it in 100..599 })
            }
            is MediaStart.Failed -> when (val status = start.statusCode?.takeIf { it in 100..599 }) {
                HTTP_TOO_MANY -> ExtractorProbeResult.Refused(SiteExtractionFailure.RATE_LIMITED, status)
                null -> ExtractorProbeResult.Refused(SiteExtractionFailure.NETWORK)
                else -> ExtractorProbeResult.Refused(SiteExtractionFailure.HTTP_STATUS, status)
            }
        }
    }
}
