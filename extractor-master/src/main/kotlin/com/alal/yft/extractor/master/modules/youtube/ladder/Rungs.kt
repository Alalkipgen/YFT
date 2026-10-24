package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.master.modules.youtube.YouTubeStream
import com.alal.yft.extractor.master.modules.youtube.YouTubeUrls
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/*
 * Phase 1.3 rungs on visionOS itself (MASTER_KEY_PHASE1_3_PLAN.md §3, §4 D4 and D5a). This file
 * holds rung Z; A (versions) and S (player script) come with D5a.
 */

/**
 * Rung **Z** — sizes from the one-byte probe (guardrail 6, §4 D4; class `INCOMPLETE`, sizes):
 * the existing one-byte file check's total size (`ExtractorProbeResult.Answered.totalBytes`)
 * replaces a missing `contentLength`.
 *
 * - Only for the rows the caller is about to offer (the offer set); every other row stays as it
 *   is. A row that has a size keeps it and is never asked.
 * - At most [MAX_PROBES_PER_LOOKUP] probes per lookup ([Lookup], one per lookup, never shared),
 *   over all calls; the same address is asked once per lookup.
 * - Only YouTube media-server addresses (`YouTubeUrls.isMediaServerRequest`), which YouTube
 *   returned in this lookup's rows; a row without a direct address is not asked.
 * - The download's headers without cookies, credentials or a range (the probe sets its own).
 * - Z fills a size only; it never judges a row (V and the init check do) and never adds or drops
 *   one. A refusal, a timeout or an answer without a total leaves the size missing.
 * - Nothing is logged; a result carries rows (whose `toString` hides addresses), outcomes and
 *   counts only.
 */
internal class ZRung(
    private val http: ExtractorHttpClient,
    private val timeoutMillis: Long = ExtractorHttpClient.DEFAULT_PROBE_TIMEOUT_MILLIS,
) {
    init {
        require(timeoutMillis in 1..MAX_TIMEOUT_MILLIS)
    }

    enum class Outcome {
        /** The row had a size; not asked. */
        KNOWN,

        /** The probe's total became the row's size. */
        FILLED,

        /** No direct address (a protected descriptor); not asked. */
        NO_ADDRESS,

        /** Not a YouTube media-server address; not asked. */
        NOT_MEDIA_HOST,

        /** The lookup's probes are spent; not asked. */
        BUDGET_SPENT,

        /** The file answered without a total (or with 0). */
        NO_TOTAL,

        /** The file refused (an HTTP status). */
        REFUSED,

        /** The client cannot check files. */
        UNSUPPORTED,

        /** No answer: network error or timeout. */
        NETWORK,
    }

    data class Result(
        /** The rows in the caller's order, sizes filled where the probe gave one. */
        val rows: List<YouTubeStream>,
        /** One outcome per row, in the same order. */
        val outcomes: List<Outcome>,
        /** Probes this call made. */
        val probes: Int,
    ) {
        val filled: Int get() = outcomes.count { it == Outcome.FILLED }

        /** Rows still without a size. */
        val missing: Int get() = rows.count { !hasSize(it) }
    }

    /** A new lookup's Z with its own budget. */
    fun lookup(): Lookup = Lookup()

    inner class Lookup internal constructor() {
        private val mutex = Mutex()
        private var probes = 0
        private var unsupported = false

        /** This lookup's answers by address, in memory only (never logged). */
        private val answers = HashMap<String, Pair<Outcome, Long?>>()

        /** Probes this lookup made so far. */
        val probesUsed: Int get() = probes

        /** Fills the missing sizes of [rows] (the rows about to be offered) with [headers]. */
        suspend fun fill(rows: List<YouTubeStream>, headers: Map<String, String>): Result = mutex.withLock {
            val sent = headers.filterKeys { it.trim().lowercase() !in DROPPED_HEADERS }
            val before = probes
            val filled = ArrayList<YouTubeStream>(rows.size)
            val outcomes = ArrayList<Outcome>(rows.size)
            for (row in rows) {
                val (outcome, total) = answer(row, sent)
                outcomes += outcome
                filled += if (outcome == Outcome.FILLED && total != null) row.copy(contentLengthBytes = total) else row
            }
            Result(filled, outcomes, probes - before)
        }

        private suspend fun answer(row: YouTubeStream, headers: Map<String, String>): Pair<Outcome, Long?> {
            if (hasSize(row)) return Outcome.KNOWN to null
            val address = row.url ?: return Outcome.NO_ADDRESS to null
            if (!YouTubeUrls.isMediaServerRequest(address)) return Outcome.NOT_MEDIA_HOST to null
            answers[address]?.let { return it }
            if (unsupported) return Outcome.UNSUPPORTED to null
            if (probes >= MAX_PROBES_PER_LOOKUP) return Outcome.BUDGET_SPENT to null
            val answer = ask(address, headers)
            if (answer.first == Outcome.UNSUPPORTED) unsupported = true else probes++
            answers[address] = answer
            return answer
        }
    }

    private suspend fun ask(address: String, headers: Map<String, String>): Pair<Outcome, Long?> {
        val answer = try {
            withTimeoutOrNull(timeoutMillis + GRACE_MILLIS) { http.probe(address, headers, timeoutMillis) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            null
        }
        return when (answer) {
            null -> Outcome.NETWORK to null
            is ExtractorProbeResult.Answered -> answer.totalBytes?.takeIf { it > 0 }
                ?.let { Outcome.FILLED to it }
                ?: (Outcome.NO_TOTAL to null)
            is ExtractorProbeResult.Refused -> (if (answer.statusCode == null) Outcome.NETWORK else Outcome.REFUSED) to null
            ExtractorProbeResult.Unsupported -> Outcome.UNSUPPORTED to null
        }
    }

    companion object {
        /** Guardrail 7: at most 8 Z probes per lookup. */
        const val MAX_PROBES_PER_LOOKUP = 8
        const val MAX_TIMEOUT_MILLIS = 10_000L
        private const val GRACE_MILLIS = 1_000L

        /** Cookies and credentials never go with a probe; the probe sets its own range. */
        private val DROPPED_HEADERS = setOf(
            "cookie",
            "cookie2",
            "authorization",
            "proxy-authorization",
            "x-goog-authuser",
            "range",
        )

        private fun hasSize(row: YouTubeStream): Boolean = (row.contentLengthBytes ?: 0L) > 0L
    }
}
