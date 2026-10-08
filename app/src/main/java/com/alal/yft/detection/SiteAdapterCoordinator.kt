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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext

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
    ) : SiteAdapterOutcome {
        /** Whether asking again can change the answer, so the browser offers Try again. */
        val canRetry: Boolean
            get() = reason !in FINAL_REASONS

        /**
         * Whether the site's own player playing the video is what the lookup waits for, so the
         * browser retries once by itself after the page's player fetched media.
         */
        val retriesAfterPlayback: Boolean
            get() = reason == SiteExtractionFailure.BOT_CHECK

        private companion object {
            val FINAL_REASONS = setOf(
                SiteExtractionFailure.UNSUPPORTED_URL,
                SiteExtractionFailure.ADAPTER_DISABLED,
                SiteExtractionFailure.DRM_PROTECTED,
                SiteExtractionFailure.GEO_RESTRICTED,
            )
        }
    }
}

/**
 * Runs at most one site adapter for a page and keeps the generic detector as the fallback.
 *
 * This layer orchestrates only. It never parses site markup, so a site change stays contained in
 * its adapter. Adapter candidates are re-anchored to the URL the browser is actually showing,
 * because the page store groups candidates by the live page address while an adapter reports the
 * canonical one. A merged quality this phone cannot write or play, such as AV1 before Android
 * 14, is left out here so no sheet offers it (P4).
 *
 * P17: answers are shared through [SiteLookupCache]: a video found minutes ago is not asked for
 * again, and a second lookup of a video while the first runs waits for it. A lookup with the
 * user's session may take the public answer of one without; never the other way round.
 */
class SiteAdapterCoordinator @Inject constructor(
    private val registry: SiteExtractorRegistry,
    private val mergeSupport: MergeSupport = DeviceMergeSupport(),
    private val lookups: SiteLookupCache = SiteLookupCache(),
) {
    /** [fresh] (Try again) always asks the site; it drops the video's remembered answer. */
    suspend fun inspect(
        pageUrl: String,
        requestContext: BrowserRequestContext,
        nowEpochMs: Long,
        fresh: Boolean = false,
    ): SiteAdapterOutcome {
        val selection = registry.select(pageUrl)
        val matched = when (selection) {
            SiteAdapterSelection.None -> return SiteAdapterOutcome.NotHandled
            is SiteAdapterSelection.Disabled -> return SiteAdapterOutcome.NotHandled
            is SiteAdapterSelection.Matched -> selection
        }
        val key = SiteLookupKey(
            siteId = matched.identity.siteId,
            contentId = matched.identity.contentId,
            session = !requestContext.cookie.isNullOrBlank(),
        )
        if (fresh) {
            lookups.forget(key)
        } else {
            remembered(key, nowEpochMs)?.let { return it.anchoredTo(pageUrl) }
        }
        // The shared lookup runs on the caller's dispatcher; the last caller to stop stops it.
        val context = currentCoroutineContext().minusKey(Job)
        val lookup = lookups.join(key) {
            CoroutineScope(context + SupervisorJob()).async {
                extract(matched, pageUrl, requestContext, nowEpochMs, key)
            }
        }
        return try {
            when (val outcome = lookup.work.await()) {
                is SiteAdapterOutcome.Detected -> outcome.anchoredTo(pageUrl)
                else -> outcome
            }
        } finally {
            lookups.leave(key, lookup)
        }
    }

    private fun remembered(key: SiteLookupKey, nowEpochMs: Long): SiteAdapterOutcome.Detected? =
        lookups.get(key, nowEpochMs)
            ?: key.takeIf { it.session }?.let { lookups.get(it.copy(session = false), nowEpochMs) }

    private fun SiteAdapterOutcome.Detected.anchoredTo(pageUrl: String) =
        copy(candidates = candidates.map { it.anchoredTo(pageUrl) })

    private suspend fun extract(
        matched: SiteAdapterSelection.Matched,
        pageUrl: String,
        requestContext: BrowserRequestContext,
        nowEpochMs: Long,
        key: SiteLookupKey,
    ): SiteAdapterOutcome {
        val extracted = try {
            matched.extractor.extract(
                SiteExtractionRequest(
                    identity = matched.identity,
                    requestContext = requestContext,
                    nowEpochMs = nowEpochMs,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            // An adapter crash must not take the page down; generic detection still runs.
            // P39 (R25): it is named by its class, never reported as a changed page format.
            SiteExtractionResult.Failure(
                reason = SiteExtractionFailure.MALFORMED_RESPONSE,
                details = listOf(errorLine(failure)),
            )
        }
        val result = playableOnThisPhone(extracted)

        return when (result) {
            is SiteExtractionResult.Success -> SiteAdapterOutcome.Detected(
                adapterId = matched.extractor.id,
                // One extraction is one video: its qualities share one download sheet (P3).
                candidates = result.candidates.map { candidate ->
                    candidate.anchoredTo(pageUrl).let { anchored ->
                        if (anchored.videoId != null) {
                            anchored
                        } else {
                            anchored.copy(
                                videoId = "${matched.extractor.id}:${matched.identity.contentId}",
                            )
                        }
                    }
                },
            )

            is SiteExtractionResult.Failure -> SiteAdapterOutcome.Failed(
                adapterId = matched.extractor.id,
                reason = result.reason,
                message = result.message
                    ?: messageFor(matched.extractor.displayName, result.reason),
                allowsGenericFallback = result.allowsGenericFallback,
                details = DiagnosticTextSanitizer.details(
                    buildList {
                        add("adapter ${matched.extractor.id}: ${result.reason}")
                        result.httpStatusCode?.let { add("adapter HTTP $it") }
                        addAll(result.details)
                    },
                ),
            )
        }.also { outcome ->
            when {
                outcome is SiteAdapterOutcome.Detected && outcome.candidates.isNotEmpty() ->
                    lookups.put(key, outcome, nowEpochMs)
                // A video that cannot be read now has no answer to keep.
                else -> lookups.forget(key)
            }
        }
    }

    /** Drops merges this phone cannot make; nothing left is a video without a usable quality. */
    private fun playableOnThisPhone(result: SiteExtractionResult): SiteExtractionResult {
        if (result !is SiteExtractionResult.Success) return result
        val kept = result.candidates.filter(mergeSupport::canMerge)
        val dropped = result.candidates.size - kept.size
        if (dropped == 0) return result
        val note = "merges this phone cannot make: $dropped"
        return if (kept.isEmpty()) {
            SiteExtractionResult.Failure(
                SiteExtractionFailure.NO_MEDIA_FOUND,
                details = result.details + note,
            )
        } else {
            SiteExtractionResult.Success(kept, result.details + note)
        }
    }

    /**
     * Whether [requestUrl], seen on [pageUrl], is the matched site's own player fetching media.
     *
     * Only the adapter knows its site's media servers, so the browser asks rather than parsing
     * addresses itself. Disabled and unmatched pages never count.
     */
    fun isPlayerMediaRequest(pageUrl: String, requestUrl: String): Boolean =
        when (val selection = registry.select(pageUrl)) {
            is SiteAdapterSelection.Matched ->
                runCatching { selection.extractor.isPlayerMediaRequest(requestUrl) }
                    .getOrDefault(false)

            SiteAdapterSelection.None,
            is SiteAdapterSelection.Disabled,
            -> false
        }

    /** Whether an enabled site adapter handles [pageUrl], so a lookup can find its video. */
    fun handles(pageUrl: String): Boolean =
        registry.select(pageUrl) is SiteAdapterSelection.Matched

    /**
     * The video [pageUrl] shows as "site:contentId", the same for every address of it, or null
     * when no enabled adapter handles the page (P12: one lookup per video).
     */
    fun videoKey(pageUrl: String): String? =
        (registry.select(pageUrl) as? SiteAdapterSelection.Matched)?.identity?.let { identity ->
            "${identity.siteId}:${identity.contentId}"
        }

    /**
     * Whether two addresses show the same post of the same site, for example when the site adds
     * a tracking or start-time parameter to the address after the video opened.
     */
    fun sameContent(firstUrl: String, secondUrl: String): Boolean {
        val first = registry.select(firstUrl) as? SiteAdapterSelection.Matched ?: return false
        val second = registry.select(secondUrl) as? SiteAdapterSelection.Matched ?: return false
        return first.identity.siteId == second.identity.siteId &&
            first.identity.contentId == second.identity.contentId
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

        // The site's own browser player can usually answer a bot check, so the message sends
        // the user there instead of suggesting a sign-in that would not help. The browser also
        // tries again by itself once the video plays.
        SiteExtractionFailure.BOT_CHECK ->
            "$site wants to check that this is not a bot. Open the video in YFT's browser, " +
                "let it play for a moment, then tap Try again."

        SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE ->
            "This $site post is private or no longer available."

        SiteExtractionFailure.GEO_RESTRICTED ->
            "$site does not allow this post in your region."

        SiteExtractionFailure.DRM_PROTECTED ->
            "This $site media is protected and cannot be downloaded."

        SiteExtractionFailure.EXPIRED_LINK ->
            "This $site media link expired. Reload the page and try again."

        SiteExtractionFailure.RESPONSE_CHANGED ->
            "$site's page could not be read. Tap Details to see why, or Try again."

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
            "Couldn't reach $site."
    }
}
