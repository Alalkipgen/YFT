package com.alal.yft.extractor.master.modules.youtube

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorProbeResult
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Phase 1.2 N2: live check V. Asks YouTube whether one solved stream URL really opens, so an
 * answer from a new strategy is never trusted on its own (MASTER_KEY_PHASE1_2_PLAN.md §1 item 3
 * and §2 "Live check V in more detail").
 *
 * - Only for a lookup the user started; never on bot-check, login, age, private or DRM states.
 * - Only on a stream host YouTube returned in this lookup, through the download's own client and
 *   headers, minus cookies and credentials.
 * - One bounded read: the client's one-byte file check (`Range: bytes=0-0`), far under 64 KiB.
 * - At most [Policy.maxChecksPerKeyPerDay] checks per player version per UTC day. A check is one
 *   target request plus, only after a 403, one control request.
 * - A 403 is `fail` for the strategy only when the control (a direct URL of the same lookup)
 *   opens. When the control fails too the verdict is `unknown`. Without a control a 403 fails
 *   this lookup only and does not mark the strategy failed for the player version.
 * - Nothing is logged. Results carry verdict and status classes only, never a URL or a value.
 * - `unknown` is never `pass`.
 *
 * Not wired into the YouTube module yet: N5 calls it behind `ownSolverNext`, so Phase 1.1 is
 * unchanged.
 */
internal class StreamCheck(
    private val http: ExtractorHttpClient,
    private val policy: Policy = Policy(),
    private val ledger: Ledger = MemoryLedger(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    enum class Verdict { PASS, FAIL, UNKNOWN }

    /** What a URL's solved values are: a signature, a rate value (`n`), or both. */
    enum class Kind { SIG, N }

    /** The lookup's state as the reader classified it; every state but [OK] stays terminal. */
    enum class LookupState { OK, BOT_CHECK, LOGIN, AGE, PRIVATE, DRM }

    /**
     * How a 2xx speaks for `n`. Set by the owner's phone measurement (N2, plan §2):
     * - [PENDING]: not measured yet; a 2xx proves the signature only, a refusal still fails.
     * - [JUDGES_N]: a wrong `n` was refused, so V judges `n` like the signature.
     * - [SIG_ONLY]: a wrong `n` was served (T4), so V never judges `n`.
     */
    enum class NRule { PENDING, JUDGES_N, SIG_ONLY }

    enum class StatusClass { OK_2XX, FORBIDDEN_403, OTHER_4XX, SERVER_5XX, OTHER_STATUS, NETWORK, UNSUPPORTED }

    enum class Reason {
        CHECKED,
        CACHED,
        NOT_USER_STARTED,
        TERMINAL_STATE,
        NOTHING_TO_JUDGE,
        HOST_NOT_RETURNED,
        BUDGET_SPENT,
        NO_CONTROL,
        CONTROL_FAILED,
        OTHER_STATUS,
        NETWORK,
        UNSUPPORTED,
    }

    data class Policy(
        val maxChecksPerKeyPerDay: Int = MAX_CHECKS_PER_DAY,
        val timeoutMillis: Long = ExtractorHttpClient.DEFAULT_PROBE_TIMEOUT_MILLIS,
        val nRule: NRule = NRule.PENDING,
        val cacheMillis: Long = DAY_MILLIS,
    ) {
        init {
            require(maxChecksPerKeyPerDay in 1..MAX_CHECKS_PER_DAY)
            require(timeoutMillis in 1..MAX_TIMEOUT_MILLIS)
            require(cacheMillis in 0..DAY_MILLIS)
        }
    }

    /**
     * One check. [target] is a solved stream URL; [control] a direct URL of the same lookup
     * (no `n`, no signature), when there is one. [returnedHosts] are the stream hosts YouTube
     * returned in this lookup. [budgetKey] is the player version; [cacheKey] adds the strategy
     * and its answer, so a verdict is reused only for the same solved function.
     */
    class Request(
        val target: String,
        val control: String?,
        val returnedHosts: Set<String>,
        val headers: Map<String, String>,
        val kinds: Set<Kind>,
        val budgetKey: String,
        val cacheKey: String,
        val userStarted: Boolean,
        val state: LookupState = LookupState.OK,
    ) {
        /** Never shows an address, a header or a key. */
        override fun toString(): String =
            "StreamCheck.Request(kinds=$kinds, control=${control != null}, " +
                "userStarted=$userStarted, state=$state)"
    }

    data class Result(
        val verdict: Verdict,
        val reason: Reason,
        /** True only when a control proved that the refusal belongs to the solved values. */
        val strategyFailed: Boolean,
        val requests: Int,
        val cached: Boolean,
        val target: StatusClass?,
        val control: StatusClass?,
        val kinds: Set<Kind>,
        val nRule: NRule,
    ) {
        /** The verdict for one kind of value; `n` follows [nRule]. */
        fun verdictFor(kind: Kind): Verdict = when {
            kind !in kinds -> Verdict.UNKNOWN
            kind == Kind.SIG -> verdict
            else -> when (nRule) {
                NRule.JUDGES_N -> verdict
                NRule.SIG_ONLY -> Verdict.UNKNOWN
                NRule.PENDING -> if (verdict == Verdict.PASS) Verdict.UNKNOWN else verdict
            }
        }

        /** Whether this check marks [kind]'s strategy failed for the player version. */
        fun marksFailed(kind: Kind): Boolean = strategyFailed && verdictFor(kind) == Verdict.FAIL
    }

    /** Spent checks and remembered verdicts. N5 adds a small file behind the same interface. */
    interface Ledger {
        /** Takes one check from [key]'s budget for [day]; false when the budget is spent. */
        fun take(key: String, day: Long, max: Int): Boolean

        fun recall(key: String): Remembered?

        fun remember(key: String, entry: Remembered)
    }

    data class Remembered(val result: Result, val atMillis: Long)

    /** In memory and bounded: the oldest keys go first. */
    class MemoryLedger(private val maxKeys: Int = 64) : Ledger {
        private val spent = LinkedHashMap<String, Pair<Long, Int>>()
        private val verdicts = LinkedHashMap<String, Remembered>()

        init {
            require(maxKeys > 0)
        }

        @Synchronized
        override fun take(key: String, day: Long, max: Int): Boolean {
            val used = spent[key]?.takeIf { it.first == day }?.second ?: 0
            if (used >= max) return false
            spent.remove(key)
            spent[key] = day to used + 1
            trim(spent)
            return true
        }

        @Synchronized
        override fun recall(key: String): Remembered? = verdicts[key]

        @Synchronized
        override fun remember(key: String, entry: Remembered) {
            verdicts.remove(key)
            verdicts[key] = entry
            trim(verdicts)
        }

        private fun trim(map: LinkedHashMap<String, *>) {
            val keys = map.keys.iterator()
            while (map.size > maxKeys && keys.hasNext()) {
                keys.next()
                keys.remove()
            }
        }
    }

    suspend fun check(request: Request): Result {
        val kinds = request.kinds.toSet()
        fun unknown(reason: Reason, requests: Int = 0, target: StatusClass? = null, control: StatusClass? = null) =
            Result(Verdict.UNKNOWN, reason, false, requests, false, target, control, kinds, policy.nRule)

        if (!request.userStarted) return unknown(Reason.NOT_USER_STARTED)
        if (request.state != LookupState.OK) return unknown(Reason.TERMINAL_STATE)
        if (kinds.isEmpty()) return unknown(Reason.NOTHING_TO_JUDGE)
        val hosts = request.returnedHosts.mapTo(HashSet()) { it.lowercase() }
        if (!allowed(request.target, hosts)) return unknown(Reason.HOST_NOT_RETURNED)
        val control = request.control?.takeIf { allowed(it, hosts) }

        val now = clock()
        val memoKey = request.cacheKey + "|" + kinds.sorted().joinToString(",")
        ledger.recall(memoKey)
            ?.takeIf { now - it.atMillis in 0 until policy.cacheMillis }
            ?.let { return it.result.copy(requests = 0, cached = true, reason = Reason.CACHED, nRule = policy.nRule) }

        val day = Math.floorDiv(now, DAY_MILLIS)
        if (!ledger.take(request.budgetKey, day, policy.maxChecksPerKeyPerDay)) {
            return unknown(Reason.BUDGET_SPENT)
        }

        val headers = request.headers.filterKeys { it.trim().lowercase() !in DROPPED_HEADERS }
        val target = ask(request.target, headers)
        val result = when (target) {
            StatusClass.OK_2XX ->
                Result(Verdict.PASS, Reason.CHECKED, false, 1, false, target, null, kinds, policy.nRule)

            StatusClass.FORBIDDEN_403 -> if (control == null) {
                Result(Verdict.FAIL, Reason.NO_CONTROL, false, 1, false, target, null, kinds, policy.nRule)
            } else {
                val answer = ask(control, headers)
                if (answer == StatusClass.OK_2XX) {
                    Result(Verdict.FAIL, Reason.CHECKED, true, 2, false, target, answer, kinds, policy.nRule)
                } else {
                    unknown(Reason.CONTROL_FAILED, 2, target, answer)
                }
            }

            StatusClass.NETWORK -> unknown(Reason.NETWORK, 1, target)
            StatusClass.UNSUPPORTED -> unknown(Reason.UNSUPPORTED, 0, target)
            else -> unknown(Reason.OTHER_STATUS, 1, target)
        }
        // Only proven verdicts are reused; a lone 403 or an unknown may be checked again.
        if (result.verdict == Verdict.PASS || result.strategyFailed) {
            ledger.remember(memoKey, Remembered(result, now))
        }
        return result
    }

    private suspend fun ask(url: String, headers: Map<String, String>): StatusClass {
        val answer = try {
            withTimeoutOrNull(policy.timeoutMillis + GRACE_MILLIS) {
                http.probe(url, headers, policy.timeoutMillis)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            null
        }
        return when (answer) {
            null -> StatusClass.NETWORK
            is ExtractorProbeResult.Answered -> StatusClass.OK_2XX
            ExtractorProbeResult.Unsupported -> StatusClass.UNSUPPORTED
            is ExtractorProbeResult.Refused -> when (answer.statusCode) {
                null -> StatusClass.NETWORK
                403 -> StatusClass.FORBIDDEN_403
                in 400..499 -> StatusClass.OTHER_4XX
                in 500..599 -> StatusClass.SERVER_5XX
                else -> StatusClass.OTHER_STATUS
            }
        }
    }

    private fun allowed(url: String, hosts: Set<String>): Boolean {
        if (!YouTubeUrls.isMediaServerRequest(url)) return false
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return false
        return host in hosts
    }

    companion object {
        const val MAX_CHECKS_PER_DAY = 4
        const val MAX_TIMEOUT_MILLIS = 10_000L
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
        private const val GRACE_MILLIS = 1_000L

        /** Cookies and credentials never go with a check; the probe sets its own range. */
        private val DROPPED_HEADERS = setOf(
            "cookie",
            "cookie2",
            "authorization",
            "proxy-authorization",
            "x-goog-authuser",
            "range",
        )
    }
}
