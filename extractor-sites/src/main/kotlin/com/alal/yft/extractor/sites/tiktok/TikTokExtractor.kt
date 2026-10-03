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
        // Only TikTok's own cookies may follow the media; another site's cookie never does.
        val pageCookies = response.cookies.filter { TikTokUrls.isTikTokDomain(it.domain) }
        val candidates = post.renditions.map { rendition ->
            MediaCandidate(
                pageUrl = pageUrl,
                mediaUrl = rendition.url,
                sources = setOf(CandidateSource.MANIFEST),
                kind = MediaKind.DIRECT,
                mimeType = MP4_MIME_TYPE,
                title = displayTitle(post, rendition.label),
                thumbnailUrl = post.thumbnailUrl,
                durationMillis = post.durationMillis,
                contentLengthBytes = rendition.sizeBytes,
                requestContext = mediaContext(
                    pageUrl, request.requestContext, rendition.url, pageCookies,
                ),
                confidence = CandidateConfidence.HIGH,
                drmHint = false,
                observedAtEpochMs = request.nowEpochMs,
            )
        }
        return SiteExtractionResult.Success(candidates)
    }

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
     * Media requests keep the user agent and are re-anchored to the canonical page.
     *
     * The browser path keeps the WebView's cookie. A Home lookup has none, and TikTok's media
     * host refuses a request without the cookies the page just set, so the media request gets
     * the page's TikTok cookies that a browser would send to that media address. The resolver
     * and the downloader send them to the media URL's own origin only and drop them on any
     * cross-origin redirect, so a CDN on another origin never receives them.
     */
    private fun mediaContext(
        pageUrl: String,
        context: BrowserRequestContext,
        mediaUrl: String,
        pageCookies: List<ResponseCookie>,
    ): BrowserRequestContext = BrowserRequestContext(
        pageUrl = pageUrl,
        userAgent = context.userAgent,
        cookie = context.cookie?.takeIf(String::isNotBlank)
            ?: pageCookies.filter { it.matches(mediaUrl) }
                .joinToString("; ") { it.pair }
                .takeIf(String::isNotEmpty),
    )

    companion object {
        const val DEFAULT_MAX_PAGE_BYTES: Long = 3L * 1024 * 1024
        private const val MP4_MIME_TYPE = "video/mp4"
    }
}
