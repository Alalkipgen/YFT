package com.alal.yft.extractor.sites.tiktok

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageNavigationHeaders
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ResponseCookie
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.SitePageIdentity

/**
 * TikTok adapter for public, non-DRM posts.
 *
 * The adapter only reads the page TikTok already serves to the user's browser and replays the
 * user's own session context to tiktok.com. It performs no signing, no DRM handling and no
 * paywall or access-control bypass: a post the user cannot open in the browser fails with a
 * structured reason.
 */
class TikTokExtractor(
    private val http: ExtractorHttpClient,
    private val maxPageBytes: Long = DEFAULT_MAX_PAGE_BYTES,
    /**
     * P36 (G6 `TIKTOK_QUALITIES=DESKTOP`): the user agent of TikTok's desktop page. A page read
     * with the phone's user agent lists no qualities, so the adapter asks the same page once
     * more with this one; null (`PAGE`) keeps the phone page's single address.
     */
    private val desktopUserAgent: String? = null,
) : SiteExtractor {
    override val id: String = TikTokUrls.SITE_ID

    override val displayName: String = "TikTok"

    override fun identify(pageUrl: String): SitePageIdentity? = TikTokUrls.identify(pageUrl)

    override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult {
        require(request.identity.siteId == id) { "This adapter only handles TikTok identities" }

        val response = when (
            val result = http.get(
                url = request.identity.canonicalPageUrl,
                headers = pageHeaders(request.requestContext),
                maxBodyBytes = maxPageBytes,
            )
        ) {
            is ExtractorHttpResult.Failure -> return SiteExtractionResult.Failure(
                reason = result.reason,
                httpStatusCode = result.statusCode,
            )

            is ExtractorHttpResult.Success -> result
        }

        // A short link resolves to the real post, so the canonical identity is only known now.
        val resolvedIdentity = resolvedIdentity(request.identity, response.finalUrl)
        if (TikTokUrls.isPhotoPost(resolvedIdentity.canonicalPageUrl)) {
            return SiteExtractionResult.Failure(SiteExtractionFailure.NO_MEDIA_FOUND)
        }

        val post = when (
            val parsed = TikTokPageParser.parse(
                html = response.body,
                expectedVideoId = resolvedIdentity.contentId
                    .takeUnless { resolvedIdentity.requiresCanonicalResolution },
            )
        ) {
            is TikTokParseResult.Failure -> return SiteExtractionResult.Failure(parsed.reason)
            is TikTokParseResult.Success -> parsed.post
        }

        val pageUrl = TikTokUrls.canonicalUrl(post.authorHandle, post.videoId)
        val context = request.requestContext
        val phonePage = PageAnswer(post, response.cookies.tiktokOnly(), context.userAgent)
        val answer = phonePage.takeIf { post.hasQualityList }
            ?: desktopPage(pageUrl, context, post.videoId)
            ?: phonePage
        val candidates = answer.post.renditions.map { rendition ->
            MediaCandidate(
                pageUrl = pageUrl,
                mediaUrl = rendition.url,
                sources = setOf(CandidateSource.MANIFEST),
                kind = MediaKind.DIRECT,
                mimeType = MP4_MIME_TYPE,
                title = displayTitle(answer.post, rendition.label),
                thumbnailUrl = answer.post.thumbnailUrl,
                durationMillis = answer.post.durationMillis,
                contentLengthBytes = rendition.sizeBytes,
                requestContext = mediaContext(pageUrl, context, answer, rendition.url),
                confidence = CandidateConfidence.HIGH,
                drmHint = false,
                observedAtEpochMs = request.nowEpochMs,
            )
        }
        return SiteExtractionResult.Success(candidates)
    }

    /**
     * A page answer whose addresses the media requests use: the post, the TikTok cookies that
     * answer set and the user agent that asked for it.
     */
    private class PageAnswer(
        val post: TikTokPost,
        val cookies: List<ResponseCookie>,
        val userAgent: String?,
    )

    /**
     * P36 (R20): the phone page has no quality list, so the same page is asked once more with
     * the desktop user agent. Null when that is off, already the identity used, or the answer
     * fails or lists no qualities of this post: the phone page's address is then the quality.
     */
    private suspend fun desktopPage(
        pageUrl: String,
        context: BrowserRequestContext,
        videoId: String,
    ): PageAnswer? {
        val agent = desktopUserAgent?.takeIf(String::isNotBlank) ?: return null
        if (agent == context.userAgent) return null
        val result = http.get(
            url = pageUrl,
            headers = pageHeaders(context.copy(userAgent = agent)),
            maxBodyBytes = maxPageBytes,
        ) as? ExtractorHttpResult.Success ?: return null
        val post = (TikTokPageParser.parse(result.body, videoId) as? TikTokParseResult.Success)
            ?.post
            ?.takeIf { it.videoId == videoId && it.hasQualityList }
            ?: return null
        return PageAnswer(post, result.cookies.tiktokOnly(), agent)
    }

    /** Only TikTok's own cookies may follow the media; another site's cookie never does. */
    private fun List<ResponseCookie>.tiktokOnly(): List<ResponseCookie> =
        filter { TikTokUrls.isTikTokDomain(it.domain) }

    /**
     * Rebuilds the identity from the final URL after redirects.
     *
     * Short links only become addressable here, and a redirect that leaves TikTok is treated as a
     * changed response rather than being followed blindly into another site's content.
     */
    private fun resolvedIdentity(
        identity: SitePageIdentity,
        finalUrl: String,
    ): SitePageIdentity {
        if (!identity.requiresCanonicalResolution) return identity
        return TikTokUrls.identify(finalUrl)?.takeUnless { it.requiresCanonicalResolution }
            ?: identity
    }

    /** Title stays metadata-only: the caption, the author handle, then the quality label. */
    private fun displayTitle(post: TikTokPost, label: String?): String? {
        val base = post.title?.trim()?.takeIf(String::isNotEmpty)
            ?: post.authorHandle?.let { "TikTok @$it" }
            ?: return label
        return if (label.isNullOrBlank()) base else "$base — $label"
    }

    private fun pageHeaders(context: BrowserRequestContext): Map<String, String> =
        PageNavigationHeaders.withDefaults(
            buildMap {
                context.userAgent?.takeIf(String::isNotBlank)?.let { put("User-Agent", it) }
                context.cookie?.takeIf(String::isNotBlank)?.let { put("Cookie", it) }
                put("Accept-Language", "en-US,en;q=0.9")
                put("Referer", "https://www.tiktok.com/")
            },
        )

    /**
     * Media requests are re-anchored to the canonical page and use the user agent that read the
     * page whose addresses they use.
     *
     * TikTok's media host answers only with the cookies of the page answer that gave the address
     * (`tt_chain_token`; R20). P36: the browser path keeps the WebView's cookie header, but the
     * page answer's cookies a browser would send to that media address replace same-named ones
     * (a stale WebView copy loses) and are added when missing; a Home lookup has only them. The
     * resolver and the downloader send them to the media URL's own origin only and drop them on
     * any cross-origin redirect, so a CDN on another origin never receives them.
     */
    private fun mediaContext(
        pageUrl: String,
        context: BrowserRequestContext,
        answer: PageAnswer,
        mediaUrl: String,
    ): BrowserRequestContext = BrowserRequestContext(
        pageUrl = pageUrl,
        userAgent = answer.userAgent,
        cookie = mergedCookie(context.cookie, answer.cookies.filter { it.matches(mediaUrl) }),
    )

    companion object {
        const val DEFAULT_MAX_PAGE_BYTES: Long = 3L * 1024 * 1024
        private const val MP4_MIME_TYPE = "video/mp4"

        /**
         * The [header]'s cookies with the [answer]'s ones put in: same-named pairs are replaced
         * in place, new ones are added at the end. Null when nothing is left.
         */
        internal fun mergedCookie(header: String?, answer: List<ResponseCookie>): String? {
            val pairs = LinkedHashMap<String, String>()
            header.orEmpty().split(';').map(String::trim).filter(String::isNotEmpty)
                .forEach { pair -> pairs.putIfAbsent(pair.substringBefore('=').trim(), pair) }
            answer.forEach { cookie -> pairs[cookie.name] = cookie.pair }
            return pairs.values.joinToString("; ").takeIf(String::isNotEmpty)
        }
    }
}
