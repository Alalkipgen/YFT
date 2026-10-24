/*
 * Provenance (Master toolkit T8, copied, not moved; main 34a41890):
 *   extractor-sites/.../tiktok/TikTokExtractor.kt  check, probeAll, probe (R30 rounds),
 *                                                  MAX_FILE_CHECKS, MAX_PARALLEL_CHECKS
 * Adapted: generic over the quality value; the one-byte check is a parameter.
 */
package com.alal.yft.extractor.master.toolkit

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * T8: verified rows by rounds of one-byte checks. Round one checks the first address of every
 * quality; each next round checks the next address of the qualities still refused. At most
 * [maxChecks] checks per lookup (minus a [reserve] kept for a later file) and [maxParallel] at
 * a time. A quality is a row only when one of its addresses answered.
 */
internal class ProbeRounds(maxChecks: Int = MAX_CHECKS, private val maxParallel: Int = MAX_PARALLEL) {
    init {
        require(maxChecks >= 0)
        require(maxParallel >= 1)
    }

    var checksLeft: Int = maxChecks
        private set

    sealed interface Answer {
        data class Answered(val statusCode: Int, val totalBytes: Long?) : Answer
        data class Refused(val why: String) : Answer
        data object Unsupported : Answer
    }

    /** One quality: its label for details and its addresses in preference order. */
    data class Quality<T>(val value: T, val label: String, val addresses: List<String>)

    data class Outcome<T>(
        val quality: Quality<T>,
        val found: String?,
        val totalBytes: Long?,
        val trail: List<String>,
        val unsupported: Boolean,
    ) {
        override fun toString(): String =
            "Outcome(label=${quality.label}, found=${found != null}, trail=$trail)"
    }

    private class Check<T>(val quality: Quality<T>) {
        var next = 0
        var found: String? = null
        var totalBytes: Long? = null
        var unsupported = false
        val trail = mutableListOf<String>()
        val pending: Boolean
            get() = found == null && !unsupported && next < quality.addresses.size
    }

    suspend fun <T> run(
        qualities: List<Quality<T>>,
        reserve: Int = 0,
        probe: suspend (String) -> Answer,
    ): List<Outcome<T>> {
        val checks = qualities.map { Check(it) }
        while (true) {
            val room = checksLeft - reserve
            val round = checks.filter { it.pending }.take(room.coerceAtLeast(0))
            if (round.isEmpty()) break
            checksLeft -= round.size
            val permits = Semaphore(maxParallel)
            coroutineScope {
                round.map { check -> async { permits.withPermit { one(check, probe) } } }.awaitAll()
            }
        }
        return checks.map {
            Outcome(it.quality, it.found, it.totalBytes, it.trail.toList(), it.unsupported)
        }
    }

    private suspend fun <T> one(check: Check<T>, probe: suspend (String) -> Answer) {
        val address = check.quality.addresses[check.next]
        when (val answer = probe(address)) {
            is Answer.Answered -> {
                check.found = address
                check.totalBytes = answer.totalBytes
                check.trail += answer.statusCode.toString()
            }
            is Answer.Refused -> check.trail += answer.why
            Answer.Unsupported -> check.unsupported = true
        }
        if (!check.unsupported) check.next += 1
    }

    companion object {
        const val MAX_CHECKS = 8
        const val MAX_PARALLEL = 3
    }
}
