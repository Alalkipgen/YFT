package com.alal.yft.extractor.master

import com.alal.yft.core.model.logging.DiagnosticTextSanitizer
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.generic.normalizer.CandidateNormalizer
import com.alal.yft.extractor.master.contract.SiteContracts
import com.alal.yft.extractor.master.layers.LayerStack
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.lookup.RouteBLookup
import com.alal.yft.extractor.master.modules.MasterSiteModule
import com.alal.yft.extractor.master.modules.youtube.YouTubeUrls
import com.alal.yft.extractor.master.policy.SnapshotBudget
import com.alal.yft.extractor.master.policy.TerminalRules
import com.alal.yft.extractor.master.social.SocialStage
import com.alal.yft.extractor.master.toolkit.UrlPolicy
import com.alal.yft.extractor.master.verify.CandidateGate
import com.alal.yft.extractor.master.verify.FocusSelection
import com.alal.yft.extractor.master.verify.ProbeSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

/** Optional host policy; null preserves the original engine path exactly. */
typealias MasterCaptureSelection = suspend (
    MasterRequest,
    PageSnapshot,
    List<MediaCandidate>,
    suspend (MediaCandidate) -> ValidationResult,
) -> MasterResult?

/**
 * Isolated, opt-in second choice. No registry membership and no recursion into site adapters.
 *
 * Orchestration only: [TerminalRules] decide whether to run, [LayerStack] reads evidence,
 * [CandidateGate]/[FocusSelection] decide what belongs to the focused video and
 * [ProbeSession] checks it. A successful primary result must never call this engine.
 * Network/rate-limit failures do not launch another lookup. Browser capture is requested at
 * most once per invocation. R6: a page a [MasterSiteModule] claims is answered by that module
 * alone, and only when no site adapter looked the video up first (P12). R8: with [contracts],
 * the identified video's own site endpoint is asked once first (L2 contract); its answer is read
 * like any snapshot and shares the probe budget. It is never asked after a login, bot-check or
 * player-script failure (that answer needs the user's own playback). Phase 1.4 item 7 (YouTube
 * routing B) R3: with [MasterPolicy.youtubeRouteB] the YouTube module also answers a page main
 * failed with a recoverable reason, inside the lookup's record ([routeB]); off, P12 exactly.
 */
class MasterFallbackEngine(
    private val validator: MasterMediaValidator,
    private val capture: PlaybackCaptureProvider = NoPlaybackCaptureProvider,
    private val policy: MasterPolicy = MasterPolicy(),
    private val captureSelection: MasterCaptureSelection? = null,
    private val modules: List<MasterSiteModule> = emptyList(),
    private val contracts: SiteContracts? = null,
    /**
     * Social plan S1a (3): a social site's ladder ([SocialStage], Facebook's: `FacebookLadder`);
     * for a lookup it claims it answers the first stage in place of R8's ask. Null (every build
     * but the social-ladder one): the engine exactly as before.
     */
    private val social: SocialStage? = null,
) {
    private val layers = contracts?.let { LayerStack.standard(it::recipeOf) } ?: LayerStack.standard()
    private val normalizer = CandidateNormalizer(
        CandidateNormalizer.Policy(maxCandidates = policy.maxCandidates, tinyDirectAssetBytes = 0),
    )

    suspend fun extract(request: MasterRequest): MasterResult {
        if (!policy.enabled) return MasterResult.Skipped(SiteExtractionFailure.ADAPTER_DISABLED)
        if (TerminalRules.blocksFallback(request.primaryFailure, request.pageUrl)) {
            return MasterResult.Skipped(request.primaryFailure)
        }
        if (UrlPolicy.secure(request.pageUrl) == null) {
            return MasterResult.Failure(SiteExtractionFailure.UNSUPPORTED_URL, emptyList())
        }
        val claim = modules.firstNotNullOfOrNull { module ->
            module.identify(request.pageUrl)?.let { module to it }
        }
        // Item 7 R3: route B's run for a claimed page main failed recoverably, else null.
        val route = claim?.let { routeB(it.first, request) }
        // A claimed page never reaches layers or capture. The module asks only when no site
        // adapter answered for this page: after the adapter's lookup, its answer stands (route B
        // aside: then the module answers within the same lookup, main's answers replayed).
        if (claim != null && request.primaryFailure != SiteExtractionFailure.UNSUPPORTED_URL &&
            route == null
        ) {
            return MasterResult.Skipped(request.primaryFailure)
        }
        return try {
            withTimeout(policy.timeoutMillis) {
                if (claim == null) run(request) else answer(claim.first, claim.second, request, route)
            }
        } catch (timeout: TimeoutCancellationException) {
            // Only our own timeout becomes a result. An enclosing timeout remains cancellation.
            coroutineContext.ensureActive()
            failure(
                SiteExtractionFailure.NETWORK,
                routed(route, request) + "master: bounded lookup timed out",
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        }
    }

    private suspend fun run(request: MasterRequest): MasterResult {
        val details = mutableListOf<String>()
        var ambiguous = false
        var lastFailure: SiteExtractionFailure? = null
        val probes = ProbeSession(validator, request, policy.maxCandidates)
        // S1a (3): a lookup a social site's ladder claims (the social-ladder build only, else false).
        val claimed = social != null && social.claims(request)
        // Task 15 (F4): the ladder's last word (its stage's [SocialStage.Staged.last]): no stage after it.
        var last = false

        for (index in 0..2) {
            if (last) break
            // S1a (3): a social rung's snapshot is read for the video it asked about.
            var read = request
            val stage = when (index) {
                0 -> if (claimed) {
                    checkNotNull(social).stage(request, policy, details)?.let { staged ->
                        read = staged.request
                        last = staged.last
                        staged.stage to staged.snapshot
                    }
                } else {
                    contract(request, details)
                }
                1 -> request.snapshot?.let { MasterStage.PAGE_DATA to it }
                else -> {
                    details += "master: one browser-assisted capture requested"
                    when (val captured = capture.capture(request)) {
                        is CaptureResult.Available ->
                            MasterStage.PLAYBACK_CAPTURE to captured.snapshot
                        CaptureResult.Unavailable -> null
                    }
                }
            } ?: continue
            val (name, snapshot) = stage
            if (
                snapshot.generation != request.generation ||
                !UrlPolicy.samePage(request.pageUrl, snapshot.pageUrl)
            ) {
                return failure(
                    SiteExtractionFailure.RESPONSE_CHANGED,
                    details + "master: stale or unrelated page snapshot rejected",
                )
            }
            snapshot.accessFailure?.let {
                return failure(it, details + "master: page reports ${it.name}")
            }
            if (SnapshotBudget.oversized(snapshot, policy)) {
                return failure(
                    SiteExtractionFailure.RESPONSE_TOO_LARGE,
                    details + "master: snapshot exceeds the read budget",
                )
            }
            if (
                TerminalRules.needsAuthorizedPlayback(request.primaryFailure) &&
                !snapshot.authorizedPlayback
            ) {
                details += "master: authorized browser playback is required"
                continue
            }
            val evidence = layers.collect(read, snapshot)
            details += evidence.details
            evidence.terminalFailure?.let { return failure(it, details) }
            val normalized = CandidateGate.admit(
                evidence.candidates,
                normalizer.normalize(request.pageUrl, evidence.candidates),
                request.nowEpochMs,
            )
            if (name == MasterStage.PLAYBACK_CAPTURE) {
                // Social plan task 12 (observation b): a claimed lookup's capture answer keeps the
                // lines before it (the ladder's, the capture's request and evidence) ahead of its
                // own, as every other answer here does. Every other lookup: as before.
                captureSelection?.invoke(request, snapshot, normalized, probes::probe)
                    ?.let { return if (claimed) it.after(details) else it }
            }
            val selected = FocusSelection.select(normalized, snapshot)
            if (normalized.isNotEmpty() && selected.isEmpty()) {
                ambiguous = true
                details += "master: focused video cannot be established; no guess was made"
            }
            val valid = mutableListOf<MediaCandidate>()
            for (candidate in selected) {
                coroutineContext.ensureActive()
                if (probes.exhausted) {
                    details += "master: budget limits additional media checks"
                    break
                }
                when (val checked = probes.check(candidate)) {
                    is ProbeSession.Checked.Accepted -> valid += checked.media
                    is ProbeSession.Checked.Refused -> {
                        lastFailure = checked.reason
                        details += checked.detail
                        if (checked.reason == SiteExtractionFailure.DRM_PROTECTED) {
                            return failure(checked.reason, details)
                        }
                    }
                }
            }
            if (valid.isNotEmpty()) {
                details += "master: ${valid.size} checked candidates via ${name.name}"
                return MasterResult.Success(
                    SiteExtractionResult.Success(valid, DiagnosticTextSanitizer.details(details)),
                    name,
                )
            }
            if (probes.exhausted) {
                return failure(
                    lastFailure ?: SiteExtractionFailure.NO_MEDIA_FOUND,
                    details + "master: shared media-check budget exhausted",
                )
            }
        }
        val safeDetails = DiagnosticTextSanitizer.details(details)
        return when {
            lastFailure != null -> failure(checkNotNull(lastFailure), details)
            ambiguous -> MasterResult.NeedsSelection(safeDetails)
            // F4: the ladder's last word asked for no capture, so its lines end the answer.
            last -> MasterResult.NeedsPlayback(safeDetails)
            else -> MasterResult.NeedsPlayback(
                safeDetails + "Open the page, play its video, and supply a fresh capture.",
            )
        }
    }

    /**
     * Social plan task 12 (observation b): this capture answer with the lookup's earlier [lines]
     * first, its own last (kept whole within [DiagnosticTextSanitizer.MAX_STEPS]); a skipped
     * answer as is.
     */
    private fun MasterResult.after(lines: List<String>): MasterResult {
        fun kept(own: List<String>): List<String> {
            val last = DiagnosticTextSanitizer.details(own)
            val first = DiagnosticTextSanitizer.details(lines)
            return first.take((DiagnosticTextSanitizer.MAX_STEPS - last.size).coerceAtLeast(0)) + last
        }
        return when (this) {
            is MasterResult.Success -> copy(result = result.copy(details = kept(result.details)))
            is MasterResult.Failure -> copy(details = kept(details))
            is MasterResult.NeedsPlayback -> copy(details = kept(details))
            is MasterResult.NeedsSelection -> copy(details = kept(details))
            is MasterResult.Skipped -> this
        }
    }

    /**
     * R8: one bounded ask of the identified video's own endpoint, within its own time share so
     * capture keeps time. Unanswered (no recipe, an HTTP failure, a slow endpoint) moves on.
     */
    private suspend fun contract(
        request: MasterRequest,
        details: MutableList<String>,
    ): Pair<MasterStage, PageSnapshot>? {
        val contracts = contracts ?: return null
        val identity = request.identity ?: return null
        if (!contracts.reads(identity) ||
            TerminalRules.needsAuthorizedPlayback(request.primaryFailure) ||
            request.expectedContentId?.let { it != identity.contentId } == true
        ) {
            return null
        }
        val asked = withTimeoutOrNull(minOf(policy.contractTimeoutMillis, policy.timeoutMillis)) {
            contracts.ask(request)
        }
        return when (asked) {
            null -> {
                details += "master: ${identity.siteId} contract: no answer in time"
                null
            }
            is SiteContracts.Asked.Unanswered -> {
                asked.detail?.let(details::add)
                null
            }
            is SiteContracts.Asked.Answer -> {
                details += asked.detail
                MasterStage.CONTRACT to asked.snapshot
            }
        }
    }

    /**
     * Phase 1.4 item 7 (YouTube routing B) R3: the route-B run for a page [module] claims, or null
     * (P12: skipped as before). Only with [MasterPolicy.youtubeRouteB], for the YouTube module,
     * after main failed with a recoverable reason ([TerminalRules.ROUTE_B_RECOVERABLE]; final
     * reasons were skipped above), in a lookup whose record ([LookupRecord]) holds main's own
     * requests: one lookup = main + Master, so a lookup without them (no record, one that joined
     * another caller's lookup or a cached answer) stays skipped.
     */
    private suspend fun routeB(module: MasterSiteModule, request: MasterRequest): RouteBLookup? {
        if (!policy.youtubeRouteB || module.siteId != YouTubeUrls.SITE_ID) return null
        if (!TerminalRules.routeBRecoverable(request.primaryFailure)) return null
        val record = coroutineContext[LookupRecord]?.takeIf { it.mainAsks > 0 } ?: return null
        return RouteBLookup(record)
    }

    /** Item 7 R3: route B's Details line first (`master: route B after <reason>; replayed N; new M`). */
    private fun routed(route: RouteBLookup?, request: MasterRequest): List<String> =
        route?.let { listOf("master: route B after ${request.primaryFailure.name}; ${it.summary()}") }.orEmpty()

    private suspend fun answer(
        module: MasterSiteModule,
        identity: SitePageIdentity,
        request: MasterRequest,
        route: RouteBLookup? = null,
    ): MasterResult {
        if (request.expectedContentId != null && request.expectedContentId != identity.contentId) {
            return failure(
                SiteExtractionFailure.RESPONSE_CHANGED,
                routed(route, request) + "master: the page shows a different video than requested",
            )
        }
        val label = "master: ${module.siteId} module"
        val asked = SiteExtractionRequest(identity, request.requestContext, request.nowEpochMs)
        // Item 7 R3: the module's requests run inside route B's rules (replay, refusals, counts).
        val result = if (route == null) module.extract(asked) else withContext(route) { module.extract(asked) }
        return when (result) {
            is SiteExtractionResult.Success -> MasterResult.Success(
                SiteExtractionResult.Success(
                    result.candidates,
                    DiagnosticTextSanitizer.details(
                        routed(route, request) + result.details + "$label: ${result.candidates.size} rows",
                    ),
                ),
                MasterStage.SITE_MODULE,
            )
            is SiteExtractionResult.Failure -> failure(
                result.reason,
                routed(route, request) + result.details + "$label: ${result.reason.name}",
            )
        }
    }

    private fun failure(reason: SiteExtractionFailure, details: List<String>) =
        MasterResult.Failure(reason, DiagnosticTextSanitizer.details(details))
}
