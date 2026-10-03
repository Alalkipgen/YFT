package com.alal.yft.detection

import com.alal.yft.core.model.logging.DiagnosticTextSanitizer
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.extractor.api.SiteAdapterSelection
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractorRegistry
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/** What the browser layer should do after consulting the site adapters for a page. */
sealed interface SiteAdapterOutcome {
    /** No adapter handles this page, so generic detection is the whole story. */
    data object NotHandled : SiteAdapterOutcome

    data class Detected(
        val adapterId: String,
        val candidates: List<MediaCandidate>,
    ) : SiteAdapterOutcome

    data class Failed(
        val adapterId: String,
        val reason: SiteExtractionFailure,
        val message: String,
        val allowsGenericFallback: Boolean,
        val details: List<String> = emptyList(),
    ) : SiteAdapterOutcome
}

/**
 * Runs at most one site adapter for a page and keeps the generic detector as the fallback.
 *
 * This layer orchestrates only. It never parses site markup, so a site change stays contained in
 * its adapter. Adapter candidates are re-anchored to the URL the browser is actually showing,
 * because the page store groups candidates by the live page address while an adapter reports the
 * canonical one.
 */
class SiteAdapterCoordinator @Inject constructor(
    private val registry: SiteExtractorRegistry,
) {
    suspend fun inspect(
        pageUrl: String,
        requestContext: BrowserRequestContext,
        nowEpochMs: Long,
    ): SiteAdapterOutcome {
        val selection = registry.select(pageUrl)
        val matched = when (selection) {
            SiteAdapterSelection.None -> return SiteAdapterOutcome.NotHandled
            is SiteAdapterSelection.Disabled -> return SiteAdapterOutcome.NotHandled
            is SiteAdapterSelection.Matched -> selection
        }

        val result = try {
            matched.extractor.extract(
                SiteExtractionRequest(
                    identity = matched.identity,
                    requestContext = requestContext,
                    nowEpochMs = nowEpochMs,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // An adapter crash must not take the page down; generic detection still runs.
            SiteExtractionResult.Failure(SiteExtractionFailure.RESPONSE_CHANGED)
        }

        return when (result) {
            is SiteExtractionResult.Success -> SiteAdapterOutcome.Detected(
                adapterId = matched.extractor.id,
                candidates = result.candidates.map { it.anchoredTo(pageUrl) },
            )

            is SiteExtractionResult.Failure -> SiteAdapterOutcome.Failed(
                adapterId = matched.extractor.id,
                reason = result.reason,
                message = messageFor(matched.extractor.displayName, result.reason),
                allowsGenericFallback = result.allowsGenericFallback,
                details = DiagnosticTextSanitizer.details(
                    buildList {
                        add("adapter ${matched.extractor.id}: ${result.reason}")
                        result.httpStatusCode?.let { add("adapter HTTP $it") }
                        addAll(result.details)
                    },
                ),
            )
        }
    }

    /**
     * Re-anchors a candidate to the live page address.
     *
     * Only the grouping key changes. The request context keeps the canonical page the adapter
     * chose, so the downstream origin policy still decides what may be replayed.
     */
    private fun MediaCandidate.anchoredTo(pageUrl: String): MediaCandidate =
        if (this.pageUrl == pageUrl) this else copy(pageUrl = pageUrl)

    private fun messageFor(site: String, reason: SiteExtractionFailure): String = when (reason) {
        SiteExtractionFailure.UNSUPPORTED_URL ->
            "$site cannot handle this address."

        SiteExtractionFailure.ADAPTER_DISABLED ->
            "$site support is currently turned off."

        SiteExtractionFailure.NO_MEDIA_FOUND ->
            "This $site post has no downloadable video."

        SiteExtractionFailure.LOGIN_REQUIRED ->
            "Sign in to $site on this page first, then try again."

        SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE ->
            "This $site post is private or no longer available."

        SiteExtractionFailure.GEO_RESTRICTED ->
            "$site does not allow this post in your region."

        SiteExtractionFailure.DRM_PROTECTED ->
            "This $site media is protected and cannot be downloaded."

        SiteExtractionFailure.EXPIRED_LINK ->
            "This $site media link expired. Reload the page and try again."

        SiteExtractionFailure.RESPONSE_CHANGED ->
            "$site changed its page format. Falling back to generic detection."

        SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED ->
            "$site protects this video's links with its player script, and YFT could not run " +
                "it. Try again, or update YFT if this keeps happening."

        SiteExtractionFailure.RATE_LIMITED ->
            "$site is rate limiting this device. Wait a moment and try again."

        SiteExtractionFailure.HTTP_STATUS ->
            "$site returned an unexpected response."

        SiteExtractionFailure.MALFORMED_RESPONSE ->
            "The $site response could not be read."

        SiteExtractionFailure.RESPONSE_TOO_LARGE ->
            "The $site page was too large to inspect safely."

        SiteExtractionFailure.NETWORK ->
            "$site could not be reached. Check the connection and try again."
    }
}
