package com.alal.yft.extractor.master.modules.youtube

import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.extractor.api.PlayerScriptChallengeKind
import com.alal.yft.extractor.api.PlayerScriptRequest
import com.alal.yft.extractor.api.PlayerScriptResult
import com.alal.yft.extractor.api.PlayerScriptRunner
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.master.solver.PendingCheck
import com.alal.yft.extractor.master.solver.SignatureVerdicts
import java.net.URI
import java.net.URLDecoder
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext

/**
 * Phase 1.2 N5b (ownSolverNext): V for the own solver's signatures, around the reader. The reader
 * is a whole-file copy of main's and stays main's text (R3 drift check), so V lives here.
 *
 * [runner] wraps the reader's runner. For an answer whose signatures still need V it notes, in
 * the lookup's coroutine context ([Lookup]), the runner's pending check and the signature values
 * (in memory only, never logged). An answer with no lookup to note it in loses its signatures.
 *
 * [verify], after the lookup: one stream URL that carries such a value is checked (StreamCheck:
 * one bounded read; the control is a stream URL of the same lookup without an own signature; only
 * on hosts this lookup returned; the download's User-Agent and Referer, no cookies). The lookup
 * runs only for the page the user opened, so it counts as user-started. Only PASS keeps the
 * streams that carry those values; the verdict goes back to the runner, which keeps a pass per
 * player version and step and turns a step's signatures off on FAIL.
 */
internal class SignatureGate(
    private val inner: PlayerScriptRunner,
    private val verdicts: SignatureVerdicts,
    private val streamCheck: StreamCheck,
) {
    /** One lookup's answers that still need V. */
    class Lookup : AbstractCoroutineContextElement(Lookup) {
        companion object Key : CoroutineContext.Key<Lookup>

        private val pending = mutableListOf<Pending>()

        fun add(item: Pending) = synchronized(pending) { pending += item }

        fun drain(): List<Pending> = synchronized(pending) { pending.toList().also { pending.clear() } }
    }

    class Pending(val check: PendingCheck, val signatures: Set<String>)

    val runner: PlayerScriptRunner = object : PlayerScriptRunner {
        override val isAvailable: Boolean get() = inner.isAvailable

        override suspend fun resolve(request: PlayerScriptRequest): PlayerScriptResult {
            val result = inner.resolve(request)
            val check = verdicts.pendingCheck(request) ?: return result
            if (result !is PlayerScriptResult.Success) return result
            val keys = request.challenges
                .filter { it.kind == PlayerScriptChallengeKind.SIGNATURE }
                .mapTo(HashSet()) { it.key }
            val signatures = result.resolved.filterKeys { it in keys }.values.toSet()
            if (signatures.isEmpty()) return result
            val lookup = currentCoroutineContext()[Lookup]
                ?: return PlayerScriptResult.Success(result.resolved - keys)
            lookup.add(Pending(check, signatures))
            return result
        }
    }

    suspend fun verify(result: SiteExtractionResult, lookup: Lookup): SiteExtractionResult {
        val pending = lookup.drain()
        if (pending.isEmpty() || result !is SiteExtractionResult.Success) return result
        var candidates = result.candidates
        val details = result.details.toMutableList()
        for (item in pending) {
            fun signed(candidate: MediaCandidate) = urls(candidate).any { carries(it, item.signatures) }
            val target = candidates.firstOrNull(::signed) ?: continue
            val urls = candidates.flatMap(::urls)
            val control = urls.firstOrNull { url -> pending.none { carries(url, it.signatures) } }
            val context = target.requestContext
            val checked = streamCheck.check(
                StreamCheck.Request(
                    target = urls(target).first { carries(it, item.signatures) },
                    control = control,
                    returnedHosts = urls.mapNotNullTo(HashSet()) { url -> runCatching { URI(url).host }.getOrNull() },
                    headers = buildMap {
                        context.userAgent?.let { put("User-Agent", it) }
                        context.pageUrl?.let { put("Referer", it) }
                    },
                    kinds = setOf(StreamCheck.Kind.SIG),
                    budgetKey = item.check.budgetKey,
                    cacheKey = item.check.cacheKey,
                    userStarted = true,
                ),
            )
            val verdict = when {
                checked.verdict == StreamCheck.Verdict.PASS -> SignatureVerdicts.Verdict.PASS
                checked.strategyFailed -> SignatureVerdicts.Verdict.FAIL
                else -> SignatureVerdicts.Verdict.UNKNOWN
            }
            verdicts.record(item.check, verdict)
            details += "live check ${verdict.name.lowercase()}"
            if (verdict != SignatureVerdicts.Verdict.PASS) candidates = candidates.filterNot(::signed)
        }
        if (candidates.isEmpty()) {
            return SiteExtractionResult.Failure(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED, details = details)
        }
        return result.copy(candidates = candidates, details = details)
    }

    private fun urls(candidate: MediaCandidate): List<String> =
        listOfNotNull(candidate.mediaUrl, candidate.audioCompanion?.mediaUrl)

    /** True when a query value of [url], decoded, is one of [values]. */
    private fun carries(url: String, values: Set<String>): Boolean =
        url.substringAfter('?', "").substringBefore('#').split('&').any { part ->
            val raw = part.substringAfter('=', "")
            raw.isNotEmpty() && runCatching { URLDecoder.decode(raw, Charsets.UTF_8.name()) }.getOrNull() in values
        }
}
