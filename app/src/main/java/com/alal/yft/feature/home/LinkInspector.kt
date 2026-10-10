package com.alal.yft.feature.home

import com.alal.yft.core.browser.detection.HeadlessPageFetcher
import com.alal.yft.core.browser.detection.HtmlMediaScanner
import com.alal.yft.core.browser.detection.MediaMetadataProbe
import com.alal.yft.core.browser.policy.BrowserAddressNormalizer
import com.alal.yft.core.browser.policy.BrowserAddressResult
import com.alal.yft.core.model.logging.DiagnosticTextSanitizer
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageVideoFacts
import com.alal.yft.detection.HeadlessIdentity
import com.alal.yft.detection.InstagramHomeSession
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.detection.tiktok.TikTokHomeSession
import com.alal.yft.detection.tiktok.TikTokPageSettings
import com.alal.yft.extractor.generic.classifier.MediaUrlClassifier
import com.alal.yft.ui.components.PromptboxStatus
import javax.inject.Inject
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient

/** What checking a link from Home found. */
sealed interface LinkInspection {
    data class Found(
        val pageUrl: String,
        val pageTitle: String?,
        val candidates: List<MediaCandidate>,
        /** P28: what the page states about its own video: its length, title and picture. */
        val facts: PageVideoFacts? = null,
    ) : LinkInspection

    /**
     * Nothing to download. [canOpenInBrowser] is false when the address itself is unusable, so
     * offering the browser would only show the same problem again.
     */
    data class NotFound(
        val message: String,
        val canOpenInBrowser: Boolean = true,
        val details: List<String> = emptyList(),
        /** P16: false when asking again cannot change the answer (a protected video). */
        val canRetry: Boolean = true,
    ) : LinkInspection
}

/**
 * P16: a link to one video of a site an adapter reads, known before any request. [key] is the
 * video's "site:contentId" and [pageUrl] the address the lookup asks for.
 */
data class SiteVideoLink(val key: String, val pageUrl: String) {
    override fun toString(): String = "SiteVideoLink(site=${key.substringBefore(':')})"
}

/** Looks for downloadable media behind a link without opening the browser. */
fun interface LinkInspector {
    suspend fun inspect(link: String): LinkInspection

    /** P17: Try again: like [inspect], but never answered from a remembered lookup. */
    suspend fun inspectAgain(link: String): LinkInspection = inspect(link)

    /**
     * P16: the video [link] names when a site adapter reads it, so Home opens its sheet before
     * [inspect] answers; null for every other link.
     */
    fun siteVideo(link: String): SiteVideoLink? = null
}

/**
 * Checks a link the way the design's Promptbox promises ("Looking for media…"), in order:
 * a direct media or manifest address, a site adapter for pages one supports, then the page's
 * own markup. Nothing here carries the user's browser session but a TikTok link's lookup,
 * which gets the browser's TikTok cookies when the owner allows it (P40, G3): the page fetch
 * has no cookies, so pages that need sign-in or build their player with scripts end in "No
 * downloadable media", where Home offers the full browser instead.
 */
class HeadlessLinkInspector internal constructor(
    private val fetchPage: suspend (url: String) -> HeadlessPageFetcher.Result,
    private val probeMedia: suspend (MediaCandidate) -> MediaMetadataProbe.Result,
    private val siteAdapters: SiteAdapterCoordinator,
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeoutMillis: Long = TIMEOUT_MILLIS,
    private val homeCookie: (url: String) -> String? = { null },
) : LinkInspector {
    @Inject
    constructor(
        client: OkHttpClient,
        siteAdapters: SiteAdapterCoordinator,
        tikTokSettings: TikTokPageSettings = TikTokPageSettings.OWNER,
    ) : this(
        fetchPage = HeadlessPageFetcher(
            client,
            userAgent = HeadlessIdentity.USER_AGENT,
            navigationHeaders = HeadlessIdentity.NAVIGATION_HEADERS,
        )::fetch,
        probeMedia = MediaMetadataProbe(client)::probe,
        siteAdapters = siteAdapters,
        // P49: an Instagram link carries the browser's Instagram sign-in, if there is one.
        homeCookie = { url ->
            TikTokHomeSession.cookieFor(url, tikTokSettings) ?: InstagramHomeSession.cookieFor(url)
        },
    )

    private val scanner = HtmlMediaScanner(maxCandidates = MAX_CANDIDATES)

    override fun siteVideo(link: String): SiteVideoLink? {
        val url = (BrowserAddressNormalizer.normalize(link) as? BrowserAddressResult.Valid)?.url
            ?.takeIf { it.startsWith("https://") }
            ?: return null
        // A direct media address is looked up as a file, not as a site's video page.
        if (MediaUrlClassifier.classify(url) != null) return null
        return siteAdapters.videoKey(url)?.let { key -> SiteVideoLink(key, url) }
    }

    override suspend fun inspect(link: String): LinkInspection = inspect(link, fresh = false)

    override suspend fun inspectAgain(link: String): LinkInspection = inspect(link, fresh = true)

    private suspend fun inspect(link: String, fresh: Boolean): LinkInspection {
        val url = when (val address = BrowserAddressNormalizer.normalize(link)) {
            is BrowserAddressResult.Valid -> address.url
            is BrowserAddressResult.Invalid ->
                return LinkInspection.NotFound(address.reason, canOpenInBrowser = false)
        }
        if (!url.startsWith("https://")) {
            return LinkInspection.NotFound("Enter a web address", canOpenInBrowser = false)
        }
        return withTimeoutOrNull(timeoutMillis) { inspectUrl(url, fresh) }
            ?: LinkInspection.NotFound(
                "The page took too long to answer. Try it in the browser.",
                details = listOf("lookup: timed out after $timeoutMillis ms"),
            )
    }

    private suspend fun inspectUrl(url: String, fresh: Boolean): LinkInspection {
        val now = clock()
        MediaUrlClassifier.classify(url)?.let { kind ->
            val candidate = directCandidate(url, kind, mimeType = null, length = null, now)
            val probed = when (val result = probeMedia(candidate)) {
                is MediaMetadataProbe.Result.Detected -> result.candidate
                is MediaMetadataProbe.Result.Failed,
                is MediaMetadataProbe.Result.NotMedia,
                -> candidate
            }
            return LinkInspection.Found(url, pageTitle = null, candidates = listOf(probed))
        }

        var adapterMessage: String? = null
        var adapterDetails: List<String> = emptyList()
        when (
            val outcome = siteAdapters.inspect(
                pageUrl = url,
                requestContext = BrowserRequestContext(
                    url,
                    HeadlessIdentity.USER_AGENT,
                    // P40 (G3): a TikTok link carries the browser's TikTok cookies, if allowed.
                    cookie = homeCookie(url),
                ),
                nowEpochMs = now,
                fresh = fresh,
            )
        ) {
            SiteAdapterOutcome.NotHandled -> Unit
            is SiteAdapterOutcome.Detected -> if (outcome.candidates.isNotEmpty()) {
                return LinkInspection.Found(
                    pageUrl = url,
                    pageTitle = outcome.candidates.firstNotNullOfOrNull { it.title },
                    candidates = outcome.candidates.take(MAX_CANDIDATES),
                )
            }
            is SiteAdapterOutcome.Failed -> {
                if (!outcome.allowsGenericFallback) {
                    return LinkInspection.NotFound(
                        outcome.message,
                        details = outcome.details,
                        canRetry = outcome.canRetry,
                    )
                }
                adapterMessage = outcome.message
                adapterDetails = outcome.details
            }
        }

        return when (val page = fetchPage(url)) {
            is HeadlessPageFetcher.Result.Media -> LinkInspection.Found(
                pageUrl = url,
                pageTitle = null,
                candidates = listOf(
                    directCandidate(
                        url = page.url,
                        kind = MediaUrlClassifier.classify(page.url, page.mimeType)
                            ?: MediaKind.UNKNOWN,
                        mimeType = page.mimeType,
                        length = page.contentLengthBytes,
                        now = now,
                    ),
                ),
            )

            is HeadlessPageFetcher.Result.Page -> {
                val scan = scanner.scan(page.html, page.url, now)
                if (scan.candidates.isEmpty()) {
                    LinkInspection.NotFound(
                        adapterMessage ?: PromptboxStatus.NO_MEDIA_MESSAGE,
                        details = DiagnosticTextSanitizer.details(
                            adapterDetails + listOf(
                                "page GET: HTML (${page.html.length} characters)",
                                "markup: no media",
                            ),
                        ),
                    )
                } else {
                    LinkInspection.Found(
                        pageUrl = page.url,
                        pageTitle = scan.title,
                        candidates = scan.candidates.map { candidate ->
                            candidate.copy(
                                title = candidate.title ?: scan.title,
                                requestContext = candidate.requestContext.copy(
                                    userAgent = HeadlessIdentity.USER_AGENT,
                                    cookie = null,
                                ),
                            )
                        },
                        facts = scan.facts.takeUnless { it.isEmpty },
                    )
                }
            }

            is HeadlessPageFetcher.Result.Failed ->
                LinkInspection.NotFound(
                    adapterMessage ?: page.reason.message(),
                    details = adapterDetails + "page GET failed: ${page.reason}",
                )
        }
    }

    private fun directCandidate(
        url: String,
        kind: MediaKind,
        mimeType: String?,
        length: Long?,
        now: Long,
    ) = MediaCandidate(
        pageUrl = url,
        mediaUrl = url,
        sources = buildSet {
            add(CandidateSource.PASTED_URL)
            if (kind == MediaKind.HLS || kind == MediaKind.DASH) add(CandidateSource.MANIFEST)
        },
        kind = kind,
        mimeType = mimeType,
        contentLengthBytes = length,
        requestContext = BrowserRequestContext(url, HeadlessIdentity.USER_AGENT, cookie = null),
        confidence = CandidateConfidence.MEDIUM,
        observedAtEpochMs = now,
    )

    private fun HeadlessPageFetcher.FailureReason.message(): String = when (this) {
        HeadlessPageFetcher.FailureReason.INVALID_URL -> "Enter a valid HTTPS address"
        HeadlessPageFetcher.FailureReason.NETWORK ->
            "The page could not be reached. Check the connection or try the browser."
        HeadlessPageFetcher.FailureReason.HTTP_STATUS ->
            "The site did not return the page. It may need the browser."
        HeadlessPageFetcher.FailureReason.INSECURE_REDIRECT ->
            "The page sends you to an insecure address."
        HeadlessPageFetcher.FailureReason.TOO_MANY_REDIRECTS ->
            "The page keeps redirecting. Try it in the browser."
        HeadlessPageFetcher.FailureReason.NOT_A_PAGE -> PromptboxStatus.NO_MEDIA_MESSAGE
    }

    internal companion object {
        const val TIMEOUT_MILLIS = 90_000L
        const val MAX_CANDIDATES = 50
    }
}
