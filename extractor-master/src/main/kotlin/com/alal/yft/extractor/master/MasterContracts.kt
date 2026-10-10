package com.alal.yft.extractor.master

import com.alal.yft.core.model.logging.DiagnosticTextSanitizer
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionResult

/**
 * Input to a second-choice engine, not another registry adapter.
 *
 * [generation] identifies one tab navigation, including SPA navigation. The browser host must
 * increment it and discard old observations when the focused page changes.
 * [expectedContentId] is supplied by the caller when known; this engine never invents an ID.
 */
data class MasterRequest(
    val pageUrl: String,
    val generation: Long,
    val nowEpochMs: Long,
    val primaryFailure: SiteExtractionFailure,
    val expectedContentId: String? = null,
    val requestContext: BrowserRequestContext = BrowserRequestContext(pageUrl, null, null),
    val snapshot: PageSnapshot? = null,
) {
    init {
        require(generation >= 0)
        require(nowEpochMs >= 0)
        require(expectedContentId == null || expectedContentId.isNotBlank())
    }

    override fun toString(): String =
        "MasterRequest(generation=$generation, primaryFailure=$primaryFailure, " +
            "expectedContentIdPresent=${expectedContentId != null}, " +
            "snapshotPresent=${snapshot != null})"
}

/**
 * An in-memory view of one page. Bodies, addresses and credentials must never be persisted.
 *
 * A browser producer must obtain each [CapturedRequest.context] for that exact request origin,
 * rather than copying the page's cookies to every CDN. This is a trusted host boundary, not
 * something that a page-provided JSON document is allowed to populate.
 */
data class PageSnapshot(
    val pageUrl: String,
    val generation: Long,
    val html: String? = null,
    val apiResponses: List<String> = emptyList(),
    val requests: List<CapturedRequest> = emptyList(),
    val playingMediaUrl: String? = null,
    val authorizedPlayback: Boolean = false,
    val accessFailure: SiteExtractionFailure? = null,
) {
    override fun toString(): String =
        "PageSnapshot(generation=$generation, htmlPresent=${html != null}, " +
            "responseCount=${apiResponses.size}, requestCount=${requests.size}, " +
            "authorizedPlayback=$authorizedPlayback, accessFailure=$accessFailure)"
}

data class CapturedRequest(
    val url: String,
    val method: String = "GET",
    val mimeType: String? = null,
    val context: BrowserRequestContext = BrowserRequestContext(null, null, null),
    val observedAtEpochMs: Long = 0,
    val pageRole: PageMediaRole? = null,
    val contentId: String? = null,
    /** R7: the page's player appended this address's bytes to the focused video's MediaSource. */
    val fedPlayer: Boolean = false,
    /** R7: picture size of the init segment the player appended (MSE), when it was read. */
    val width: Int? = null,
    val height: Int? = null,
) {
    init {
        require(width == null || width in 1..16_384)
        require(height == null || height in 1..16_384)
    }

    override fun toString(): String =
        "CapturedRequest(method=$method, pageRole=$pageRole, " +
            "contentIdPresent=${contentId != null}, fedPlayer=$fedPlayer)"
}

data class MasterPolicy(
    /** Opt-in even in this isolated module; there is deliberately no app/DI wiring. */
    val enabled: Boolean = false,
    val maxCandidates: Int = 16,
    val maxSnapshotChars: Int = 2 * 1024 * 1024,
    val maxObservations: Int = 200,
    val timeoutMillis: Long = 20_000,
) {
    init {
        require(maxCandidates in 1..50)
        require(maxSnapshotChars > 0)
        require(maxObservations in 1..1_000)
        require(timeoutMillis > 0)
    }
}

enum class MasterStage {
    PAGE_DATA,
    PLAYBACK_CAPTURE,

    /** R6: a Master site module's own answer; its rows are the module's, not probed again. */
    SITE_MODULE,
}

sealed interface CaptureResult {
    data class Available(val snapshot: PageSnapshot) : CaptureResult
    data object Unavailable : CaptureResult
}

/** The host may collect/play once. The engine never calls the failed site adapter again. */
fun interface PlaybackCaptureProvider {
    suspend fun capture(request: MasterRequest): CaptureResult
}

object NoPlaybackCaptureProvider : PlaybackCaptureProvider {
    override suspend fun capture(request: MasterRequest): CaptureResult = CaptureResult.Unavailable
}

sealed interface ValidationResult {
    data class Valid(val candidate: MediaCandidate) : ValidationResult {
        override fun toString(): String = "Valid(kind=${candidate.kind}, address=[omitted])"
    }
    data class Rejected(val reason: SiteExtractionFailure) : ValidationResult
}

fun interface MasterMediaValidator {
    suspend fun validate(candidate: MediaCandidate, nowEpochMs: Long): ValidationResult
}

sealed interface MasterResult {
    /** Probe-validated candidates, not a promise that a final download/export will succeed. */
    data class Success(
        val result: SiteExtractionResult.Success,
        val stage: MasterStage,
    ) : MasterResult {
        override fun toString(): String =
            "Success(candidateCount=${result.candidates.size}, stage=$stage, " +
                "details=${DiagnosticTextSanitizer.details(result.details)})"
    }

    data class Failure(
        val reason: SiteExtractionFailure,
        val details: List<String>,
    ) : MasterResult {
        override fun toString(): String =
            "Failure(reason=$reason, details=${DiagnosticTextSanitizer.details(details)})"
    }

    data class NeedsPlayback(val details: List<String>) : MasterResult {
        override fun toString(): String =
            "NeedsPlayback(details=${DiagnosticTextSanitizer.details(details)})"
    }

    data class NeedsSelection(val details: List<String>) : MasterResult {
        override fun toString(): String =
            "NeedsSelection(details=${DiagnosticTextSanitizer.details(details)})"
    }

    data class Skipped(val reason: SiteExtractionFailure) : MasterResult
}