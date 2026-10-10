package com.alal.yft.extractor.master

import com.alal.yft.core.model.logging.DiagnosticTextSanitizer
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.generic.normalizer.CandidateNormalizer
import com.alal.yft.extractor.master.layers.LayerStack
import com.alal.yft.extractor.master.policy.SnapshotBudget
import com.alal.yft.extractor.master.policy.TerminalRules
import com.alal.yft.extractor.master.toolkit.UrlPolicy
import com.alal.yft.extractor.master.verify.CandidateGate
import com.alal.yft.extractor.master.verify.FocusSelection
import com.alal.yft.extractor.master.verify.ProbeSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
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
 * most once per invocation.
 */
class MasterFallbackEngine(
    private val validator: MasterMediaValidator,
    private val capture: PlaybackCaptureProvider = NoPlaybackCaptureProvider,
    private val policy: MasterPolicy = MasterPolicy(),
    private val captureSelection: MasterCaptureSelection? = null,
) {
    private val layers = LayerStack.standard()
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
        return try {
            withTimeout(policy.timeoutMillis) { run(request) }
        } catch (timeout: TimeoutCancellationException) {
            // Only our own timeout becomes a result. An enclosing timeout remains cancellation.
            coroutineContext.ensureActive()
            failure(SiteExtractionFailure.NETWORK, listOf("master: bounded lookup timed out"))
        } catch (cancellation: CancellationException) {
            throw cancellation
        }
    }

    private suspend fun run(request: MasterRequest): MasterResult {
        val details = mutableListOf<String>()
        var ambiguous = false
        var lastFailure: SiteExtractionFailure? = null
        val probes = ProbeSession(validator, request, policy.maxCandidates)
        val stages = mutableListOf<Pair<MasterStage, PageSnapshot>>()
        request.snapshot?.let { stages += MasterStage.PAGE_DATA to it }

        for (index in 0..1) {
            val stage = if (index == 0) {
                stages.firstOrNull()
            } else {
                details += "master: one browser-assisted capture requested"
                when (val captured = capture.capture(request)) {
                    is CaptureResult.Available ->
                        MasterStage.PLAYBACK_CAPTURE to captured.snapshot
                    CaptureResult.Unavailable -> null
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
            val evidence = layers.collect(request, snapshot)
            details += evidence.details
            evidence.terminalFailure?.let { return failure(it, details) }
            val normalized = CandidateGate.admit(
                evidence.candidates,
                normalizer.normalize(request.pageUrl, evidence.candidates),
                request.nowEpochMs,
            )
            if (name == MasterStage.PLAYBACK_CAPTURE) {
                captureSelection?.invoke(request, snapshot, normalized, probes::probe)
                    ?.let { return it }
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
            else -> MasterResult.NeedsPlayback(
                safeDetails + "Open the page, play its video, and supply a fresh capture.",
            )
        }
    }

    private fun failure(reason: SiteExtractionFailure, details: List<String>) =
        MasterResult.Failure(reason, DiagnosticTextSanitizer.details(details))
}
