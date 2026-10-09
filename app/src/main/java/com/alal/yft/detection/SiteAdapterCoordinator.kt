package com.alal.yft.detection

import com.alal.yft.core.model.logging.DiagnosticTextSanitizer
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.extractor.api.SiteAdapterSelection
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.api.SitePageData
import com.alal.yft.extractor.api.SitePageIdentity
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
 *
 * P40 (step 5): a lookup runs its steps in order and its Details list each step and its result.
 * In the browser ([inspect]'s tab given): the tab's own data for the post, the adapter's page
 * read with the tab's cookies, then [hiddenPages] only when the tab holds no data for the post
 * (it is read once more first: a loading page may have it by now). On Home: the page read,
 * then the hidden page. The hidden page follows only a failure of YFT's own request (a page it
 * could not read, an HTTP or network error, files that did not open), never the site's own
 * answer about the post or the user (private, sign-in, region, DRM, a check, a rate limit).
 */
class SiteAdapterCoordinator @Inject constructor(
    private val registry: SiteExtractorRegistry,
    private val mergeSupport: MergeSupport = DeviceMergeSupport(),
    private val lookups: SiteLookupCache = SiteLookupCache(),
    private val hiddenPages: HiddenPageReader = HiddenPageReader.None,
) {
    /**
     * [fresh] (Try again) always asks the site; it drops the video's remembered answer. [tab]
     * reads the user's tab's own data for the post (the browser's lookups).
     */
    suspend fun inspect(
        pageUrl: String,
        requestContext: BrowserRequestContext,
        nowEpochMs: Long,
        fresh: Boolean = false,
        tab: TabDataSource? = null,
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
                extract(matched, pageUrl, requestContext, nowEpochMs, key, tab)
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
        tab: TabDataSource?,
    ): SiteAdapterOutcome {
        val ordered = inOrder(matched, pageUrl, requestContext, nowEpochMs, tab)
        val result = ordered.result

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
                        addAll(ordered.steps)
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

    /** The final result of a lookup's steps and the Details of the steps before it. */
    private class Ordered(val result: SiteExtractionResult, val steps: List<String>)

    /** P40 step 5: tab data → the page read → the hidden page, as far as each is needed. */
    private suspend fun inOrder(
        matched: SiteAdapterSelection.Matched,
        pageUrl: String,
        requestContext: BrowserRequestContext,
        nowEpochMs: Long,
        tab: TabDataSource?,
    ): Ordered {
        val identity = matched.identity
        val postId = identity.contentId.takeUnless { identity.requiresCanonicalResolution }
        val steps = mutableListOf<String>()
        val tabRead = postId?.let { id -> tab?.let { readTab(it, identity.siteId, id) } }
        tabRead?.let { steps += it.details }
        val first = run(matched, identity, requestContext.with(tabRead), nowEpochMs, tabRead)
        val hiddenNext = first is SiteExtractionResult.Failure &&
            tabRead?.pageData == null &&
            first.reason in HIDDEN_PAGE_AFTER &&
            hiddenPages.handles(identity.siteId)
        if (!hiddenNext) return Ordered(first, steps)
        val pageRead = first as SiteExtractionResult.Failure
        steps += stepLine("page read", pageRead)
        steps += pageRead.details
        // A tab whose page was still loading may hold the post by now.
        val again = postId?.let { id -> tab?.let { readTab(it, identity.siteId, id) } }
        again?.let { steps += it.details }
        if (again?.pageData != null) {
            return Ordered(
                run(matched, identity, requestContext.with(again), nowEpochMs, again),
                steps,
            )
        }
        val link = if (identity.requiresCanonicalResolution) pageUrl else identity.canonicalPageUrl
        val hidden = try {
            hiddenPages.read(link, postId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            HiddenPageResult.NotFound(null, null, listOf("hidden page: " + errorLine(failure)))
        }
        steps += hidden.details
        val last = when (hidden) {
            is HiddenPageResult.Found -> fromHiddenPage(matched, hidden, postId, nowEpochMs, steps)
                ?: pageRead.copy(details = emptyList())

            // The page said why (a check, TikTok's status for the post); else the read's reason.
            is HiddenPageResult.NotFound -> hidden.reason?.let { reason ->
                SiteExtractionResult.Failure(reason, message = hidden.message)
            } ?: pageRead.copy(details = emptyList())

            HiddenPageResult.Off -> pageRead.copy(details = emptyList())
        }
        return Ordered(last, steps)
    }

    /** The rows from the hidden page's data, asked with its agent and cookies (never logged). */
    private suspend fun fromHiddenPage(
        matched: SiteAdapterSelection.Matched,
        hidden: HiddenPageResult.Found,
        postId: String?,
        nowEpochMs: Long,
        steps: MutableList<String>,
    ): SiteExtractionResult? {
        val landed = (registry.select(hidden.finalUrl) as? SiteAdapterSelection.Matched)
            ?.identity
            ?.takeIf { it.siteId == matched.identity.siteId && !it.requiresCanonicalResolution }
        val found = landed ?: matched.identity.takeIf { postId != null }
        if (found == null) {
            steps += "hidden page: landed on a page without the post's id"
            return null
        }
        val context = BrowserRequestContext(
            pageUrl = found.canonicalPageUrl,
            userAgent = hidden.userAgent,
            cookie = hidden.cookie,
        )
        val result = run(matched, found, context, nowEpochMs, hidden.data)
        if (result is SiteExtractionResult.Failure) steps += stepLine("hidden page's data", result)
        return result
    }

    /** One adapter run; a crash is a failure named by its class (P39, R25). */
    private suspend fun run(
        matched: SiteAdapterSelection.Matched,
        identity: SitePageIdentity,
        requestContext: BrowserRequestContext,
        nowEpochMs: Long,
        tab: TabData?,
    ): SiteExtractionResult = run(matched, identity, requestContext, nowEpochMs, tab?.pageData)

    private suspend fun run(
        matched: SiteAdapterSelection.Matched,
        identity: SitePageIdentity,
        requestContext: BrowserRequestContext,
        nowEpochMs: Long,
        pageData: SitePageData?,
    ): SiteExtractionResult {
        val extracted = try {
            matched.extractor.extract(
                SiteExtractionRequest(
                    identity = identity,
                    requestContext = requestContext,
                    nowEpochMs = nowEpochMs,
                    pageData = pageData,
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
        return playableOnThisPhone(extracted)
    }

    private suspend fun readTab(source: TabDataSource, siteId: String, postId: String): TabData? =
        try {
            source.read(siteId, postId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            TabData(null, listOf("tab data: " + errorLine(failure)))
        }

    /** The tab's cookies taken with its data, for the rows made from that data. */
    private fun BrowserRequestContext.with(tab: TabData?): BrowserRequestContext =
        tab?.takeIf { it.pageData != null }?.cookie?.let { copy(cookie = it) } ?: this

    private fun stepLine(step: String, failure: SiteExtractionResult.Failure): String =
        "$step: ${failure.reason}" + failure.httpStatusCode?.let { " · HTTP $it" }.orEmpty()

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

    /**
     * P39 (R25): whether the enabled adapter [adapterId] counts [requestUrl] as its player
     * fetching media, on any page of its site (a feed page has no video address of its own).
     */
    fun isPlayerMediaOf(adapterId: String, requestUrl: String): Boolean {
        val extractor = registry.enabled(adapterId) ?: return false
        return runCatching { extractor.isPlayerMediaRequest(requestUrl) }.getOrDefault(false)
    }

    /** The site of [pageUrl] when an enabled adapter handles it, such as "tiktok". */
    fun siteId(pageUrl: String): String? =
        (registry.select(pageUrl) as? SiteAdapterSelection.Matched)?.identity?.siteId

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

    private companion object {
        /**
         * P40: failures of YFT's own request, after which the hidden page may still read the
         * post. The site's own answers about the post or the user are never in this list.
         */
        val HIDDEN_PAGE_AFTER = setOf(
            SiteExtractionFailure.RESPONSE_CHANGED,
            SiteExtractionFailure.MALFORMED_RESPONSE,
            SiteExtractionFailure.RESPONSE_TOO_LARGE,
            SiteExtractionFailure.HTTP_STATUS,
            SiteExtractionFailure.NETWORK,
            SiteExtractionFailure.NO_MEDIA_FOUND,
            SiteExtractionFailure.EXPIRED_LINK,
        )
    }

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
