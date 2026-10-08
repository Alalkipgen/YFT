package com.alal.yft.extractor.master

import com.alal.yft.core.model.logging.DiagnosticTextSanitizer
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.generic.normalizer.CandidateNormalizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.coroutineContext

/**
 * Isolated, opt-in second choice. No registry membership and no recursion into site adapters.
 *
 * A successful primary result must never call this engine. Network/rate-limit failures do not
 * launch another lookup. Browser capture is requested at most once per invocation.
 */
class MasterFallbackEngine(
    private val validator: MasterMediaValidator,
    private val capture: PlaybackCaptureProvider = NoPlaybackCaptureProvider,
    private val policy: MasterPolicy = MasterPolicy(),
) {
    private val reader = PayloadMediaReader()
    private val normalizer = CandidateNormalizer(
        CandidateNormalizer.Policy(maxCandidates = policy.maxCandidates, tinyDirectAssetBytes = 0),
    )

    suspend fun extract(request: MasterRequest): MasterResult {
        if (!policy.enabled) return MasterResult.Skipped(SiteExtractionFailure.ADAPTER_DISABLED)
        if (request.primaryFailure in NEVER_FALLBACK) {
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
        var probesRemaining = policy.maxCandidates
        val checked = mutableMapOf<Pair<String, BrowserRequestContext>, ValidationResult.Valid>()
        suspend fun probe(candidate: MediaCandidate): ValidationResult {
            val key = candidate.mediaUrl to candidate.requestContext
            checked[key]?.let { return it }
            if (probesRemaining == 0) {
                return ValidationResult.Rejected(SiteExtractionFailure.NO_MEDIA_FOUND)
            }
            probesRemaining -= 1
            val result = validator.validate(candidate, request.nowEpochMs)
            if (result is ValidationResult.Valid) {
                if (
                    !UrlPolicy.samePage(result.candidate.pageUrl, request.pageUrl) ||
                    !eligible(result.candidate, request.nowEpochMs)
                ) {
                    return ValidationResult.Rejected(SiteExtractionFailure.RESPONSE_CHANGED)
                }
                checked[key] = result
            }
            return result
        }
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
            if (oversized(snapshot)) {
                return failure(
                    SiteExtractionFailure.RESPONSE_TOO_LARGE,
                    details + "master: snapshot exceeds the read budget",
                )
            }
            if (
                request.primaryFailure in BROWSER_REQUIRED &&
                !snapshot.authorizedPlayback
            ) {
                details += "master: authorized browser playback is required"
                continue
            }
            val read = reader.read(request, snapshot)
            details += read.details
            read.terminalFailure?.let { return failure(it, details) }
            // Explicit preview evidence vetoes a weaker page/JSON claim for the same file.
            val previews = read.candidates.filter { it.pageRole == PageMediaRole.PREVIEW }
                .map { UrlPolicy.whole(it.mediaUrl) }.toSet()
            val normalized = normalizer.normalize(request.pageUrl, read.candidates)
                .filter {
                    eligible(it, request.nowEpochMs) && UrlPolicy.whole(it.mediaUrl) !in previews
                }
            val selected = select(normalized, snapshot)
            if (normalized.isNotEmpty() && selected.isEmpty()) {
                ambiguous = true
                details += "master: focused video cannot be established; no guess was made"
            }
            val valid = mutableListOf<MediaCandidate>()
            for (candidate in selected) {
                coroutineContext.ensureActive()
                if (probesRemaining == 0) {
                    details += "master: budget limits additional media checks"
                    break
                }
                when (val verdict = probe(candidate)) {
                    is ValidationResult.Valid -> {
                        var media = verdict.candidate
                        val companion = media.audioCompanion
                        if (companion != null) {
                            val audio = media.copy(
                                mediaUrl = companion.mediaUrl,
                                mimeType = companion.mimeType,
                                kind = MediaKind.DIRECT,
                                codecs = companion.codecs,
                                requestContext = companion.requestContext,
                                expiresAtEpochMs = companion.expiresAtEpochMs,
                                contentLengthBytes = companion.contentLengthBytes,
                                width = null, height = null, framesPerSecond = null,
                                audioCompanion = null,
                            )
                            val audioCheck = if (eligible(audio, request.nowEpochMs)) {
                                probe(audio)
                            } else {
                                ValidationResult.Rejected(SiteExtractionFailure.EXPIRED_LINK)
                            }
                            if (audioCheck is ValidationResult.Rejected) {
                                lastFailure = audioCheck.reason
                                details += "master: companion check ${audioCheck.reason.name}"
                                if (audioCheck.reason == SiteExtractionFailure.DRM_PROTECTED) {
                                    return failure(audioCheck.reason, details)
                                }
                                continue
                            }
                            val verified = (audioCheck as ValidationResult.Valid).candidate
                            media = media.copy(
                                audioCompanion = PayloadMediaReader.companion(verified),
                            )
                        }
                        valid += media
                    }
                    is ValidationResult.Rejected -> {
                        lastFailure = verdict.reason
                        details += "master: media check ${verdict.reason.name}"
                        if (verdict.reason == SiteExtractionFailure.DRM_PROTECTED) {
                            return failure(verdict.reason, details)
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
            if (probesRemaining == 0) {
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

    private fun oversized(snapshot: PageSnapshot): Boolean {
        val characters = snapshot.apiResponses.sumOf { it.length.toLong() } +
            (snapshot.html?.length ?: 0)
        return characters > policy.maxSnapshotChars ||
            snapshot.requests.size > policy.maxObservations ||
            snapshot.apiResponses.size > 16
    }

    private fun eligible(candidate: MediaCandidate, now: Long): Boolean =
        UrlPolicy.secure(candidate.mediaUrl) != null && candidate.drmHint != true &&
            candidate.pageRole != PageMediaRole.PREVIEW &&
            !UrlPolicy.looksLikeAd(candidate.mediaUrl) &&
            candidate.expiresAtEpochMs?.let { it > now } != false

    private fun select(
        candidates: List<MediaCandidate>,
        snapshot: PageSnapshot,
    ): List<MediaCandidate> {
        val focused = snapshot.playingMediaUrl?.let(UrlPolicy::secure)?.let(UrlPolicy::whole)
        val playing = candidates.filter { UrlPolicy.whole(it.mediaUrl) == focused }
        if (playing.isNotEmpty()) {
            val ids = playing.mapNotNull(MediaCandidate::videoId).toSet()
            val keys = playing.mapNotNull(MediaCandidate::pageVideoKey).toSet()
            return candidates.filter {
                it in playing || (it.videoId != null && it.videoId in ids) ||
                    (it.pageVideoKey != null && it.pageVideoKey in keys)
            }
        }
        val named = candidates.filter { it.pageRole == PageMediaRole.MAIN }
        if (named.isEmpty()) return emptyList()
        // Multiple anonymous page videos are not automatically all qualities of one video.
        val groups = named.groupBy {
            it.videoId ?: it.pageVideoKey ?: UrlPolicy.whole(it.mediaUrl)
        }
        return if (groups.size == 1) named else emptyList()
    }

    private fun failure(reason: SiteExtractionFailure, details: List<String>) =
        MasterResult.Failure(reason, DiagnosticTextSanitizer.details(details))

    private companion object {
        val NEVER_FALLBACK = setOf(
            SiteExtractionFailure.ADAPTER_DISABLED,
            SiteExtractionFailure.DRM_PROTECTED,
            SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
            SiteExtractionFailure.GEO_RESTRICTED,
            SiteExtractionFailure.NETWORK,
            SiteExtractionFailure.RATE_LIMITED,
        )
        val BROWSER_REQUIRED = setOf(
            SiteExtractionFailure.LOGIN_REQUIRED,
            SiteExtractionFailure.BOT_CHECK,
            SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED,
        )
    }
}