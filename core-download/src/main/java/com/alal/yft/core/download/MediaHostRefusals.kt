package com.alal.yft.core.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * F8 (D7 run 12): one whole file's 403 answers on YouTube's media hosts. A fresh link may answer
 * its first bytes and refuse its other ranges for a while (yt-dlp waits for the video's ads
 * before it downloads, its `available_at`: "not waiting is likely to throw a 403"), and the
 * download asks four ranges of the file at once where yt-dlp asks one.
 *
 * Once the file has a refusal ([isRefusal]), its ranges are asked one at a time, each in its
 * [turn]. While refusals come in a row, a turn waits [firstWaitMillis] after the last one, twice
 * as long after each refusal of a range asked alone, at most [maxWaitMillis]; any other answer
 * to a range asked after the row began ends the row. A refused range is asked again
 * ([mayAskAgain]) while its next turn starts within [limitMillis] of the row's first refusal,
 * at most [MAX_REFUSALS] times for the file. [describe] is the Detail of a file refused for good.
 * It keeps counts and times only, never an address. The download's own times
 * ([DashTransferEngine.Policy], F8 trim, owner 2026-10-19): a refused range is asked again once,
 * 3 s after the refusal; refused again, the file fails (D7 runs 13 and 13b: 45 s of asking again
 * never got an answer).
 */
internal class MediaHostRefusals(
    private val limitMillis: Long,
    private val firstWaitMillis: Long,
    private val maxWaitMillis: Long,
    private val elapsedMillis: () -> Long,
    val isRefusal: (Throwable) -> Boolean,
) {
    init {
        require(limitMillis > 0)
        require(firstWaitMillis > 0)
        require(maxWaitMillis >= firstWaitMillis)
    }

    private val gate = Mutex()
    private val lock = Any()
    private var inFlight = 0
    private var refused = false
    private var refusals = 0
    private var firstRefusedAt = 0L
    private var firstOffset = 0L
    private var firstAtOnce = 0
    private var lastRefusedAt = 0L
    private var rowStartedAt: Long? = null
    private var aloneInRow = 0

    /**
     * Asks [ask] for the range at [offset]: at once until the file has a refusal, then alone,
     * after the row's wait.
     */
    suspend fun <T> turn(offset: Long, ask: suspend () -> T): T {
        if (!synchronized(lock) { refused }) return counted(offset, alone = false, ask)
        return gate.withLock {
            val wait = synchronized(lock) {
                rowStartedAt?.let { lastRefusedAt + waitMillis() - elapsedMillis() } ?: 0L
            }
            if (wait > 0) delay(wait)
            counted(offset, alone = true, ask)
        }
    }

    /** After a refusal: true when the range is to be asked again in its next [turn]. */
    fun mayAskAgain(): Boolean = synchronized(lock) {
        if (refusals >= MAX_REFUSALS) return false
        val row = rowStartedAt ?: return true
        lastRefusedAt + waitMillis() - row <= limitMillis
    }

    /**
     * "403×9 at 1 MiB, 4 at once, 41 s": the file's refusals, where its first refused range
     * starts, how many of its ranges were being asked then (that one too), and the time from
     * its first refusal to its last.
     */
    fun describe(): String = synchronized(lock) {
        val seconds = (lastRefusedAt - firstRefusedAt).coerceAtLeast(0) / MILLIS_PER_SECOND
        "403×$refusals at ${firstOffset / BYTES_PER_MIB} MiB, $firstAtOnce at once, $seconds s"
    }

    private suspend fun <T> counted(offset: Long, alone: Boolean, ask: suspend () -> T): T {
        val askedAt = synchronized(lock) {
            inFlight += 1
            elapsedMillis()
        }
        try {
            val answer = ask()
            synchronized(lock) { answered(askedAt) }
            return answer
        } catch (error: Throwable) {
            if (error !is CancellationException) {
                synchronized(lock) {
                    if (isRefusal(error)) noteRefusal(offset, alone) else answered(askedAt)
                }
            }
            throw error
        } finally {
            synchronized(lock) { inFlight -= 1 }
        }
    }

    private fun noteRefusal(offset: Long, alone: Boolean) {
        val now = elapsedMillis()
        if (refusals == 0) {
            firstRefusedAt = now
            firstOffset = offset
            firstAtOnce = inFlight
        }
        refusals += 1
        refused = true
        if (rowStartedAt == null) {
            rowStartedAt = now
            aloneInRow = 0
        } else if (alone) {
            aloneInRow += 1
        }
        lastRefusedAt = now
    }

    /** An answer that is not a refusal, to a range asked after the row began, ends the row. */
    private fun answered(askedAt: Long) {
        val row = rowStartedAt ?: return
        if (askedAt > row) {
            rowStartedAt = null
            aloneInRow = 0
        }
    }

    private fun waitMillis(): Long {
        val doubled = firstWaitMillis shl aloneInRow.coerceAtMost(MAX_DOUBLINGS)
        return minOf(doubled, maxWaitMillis)
    }

    companion object {
        /** Refusals of one file after which it fails whatever the time ([mayAskAgain]). */
        const val MAX_REFUSALS = 64
        private const val MAX_DOUBLINGS = 16
        private const val MILLIS_PER_SECOND = 1_000L
        private const val BYTES_PER_MIB = 1_024L * 1_024
    }
}
