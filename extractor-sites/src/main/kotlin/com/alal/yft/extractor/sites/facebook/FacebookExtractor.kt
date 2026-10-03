package com.alal.yft.extractor.sites.facebook

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageNavigationHeaders
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.SitePageIdentity

/**
 * Facebook adapter for public and already-authorized non-DRM videos and reels.
 *
 * The adapter only reads the page Facebook already serves to the user's browser and replays the
 * user's own session context to facebook.com. It performs no signing, no DRM handling and no
 * login, paywall or access-control bypass: a video the user cannot open in the browser fails with
 * a structured reason instead of being worked around.
 */
class FacebookExtractor(
    private val http: ExtractorHttpClient,
    private val maxPageBytes: Long = DEFAULT_MAX_PAGE_BYTES,
) : SiteExtractor {
    override val id: String = FacebookUrls.SITE_ID

    override val displayName: String = "Facebook"

    override fun identify(pageUrl: String): SitePageIdentity? = FacebookUrls.identify(pageUrl)

    override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult {
        require(request.identity.siteId == id) { "This adapter only handles Facebook identities" }

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

        val pageDetails = listOf(
            "page GET ${response.statusCode} (${response.body.length} characters)",
        )

        // Facebook answers a gated page with a redirect to its login or checkpoint flow.
        if (FacebookUrls.isAccessWall(response.finalUrl)) {
            return SiteExtractionResult.Failure(
                SiteExtractionFailure.LOGIN_REQUIRED,
                details = pageDetails + "page: login/checkpoint wall",
            )
        }

        // A short or share link resolves to the real video, so the identity is only known now.
        val resolvedIdentity = resolvedIdentity(request.identity, response.finalUrl)
        val parsed = FacebookPageParser.parse(
            html = response.body,
            expectedVideoId = resolvedIdentity.contentId
                .takeUnless { resolvedIdentity.requiresCanonicalResolution },
        )
        if (parsed is FacebookParseResult.Failure) {
            return SiteExtractionResult.Failure(
                parsed.reason, details = pageDetails + parsed.details,
            )
        }
        parsed as FacebookParseResult.Success
        val post = parsed.post
        val details = pageDetails + parsed.details

        val pageUrl = pageUrl(resolvedIdentity, post)
        val candidates = post.renditions.mapNotNull { rendition ->
            val expiresAtEpochMs = FacebookUrls.mediaExpiryEpochMs(rendition.url)
            // Handing an already-expired link to the download engine would only fail later.
            if (expiresAtEpochMs != null && expiresAtEpochMs <= request.nowEpochMs) {
                return@mapNotNull null
            }
            MediaCandidate(
                pageUrl = pageUrl,
                mediaUrl = rendition.url,
                sources = setOf(CandidateSource.MANIFEST),
                kind = when (rendition.delivery) {
                    FacebookDelivery.PROGRESSIVE -> MediaKind.DIRECT
                    FacebookDelivery.DASH_MANIFEST -> MediaKind.DASH
                },
                mimeType = when (rendition.delivery) {
                    FacebookDelivery.PROGRESSIVE -> MP4_MIME_TYPE
                    FacebookDelivery.DASH_MANIFEST -> DASH_MIME_TYPE
                },
                title = displayTitle(post, rendition.label),
                thumbnailUrl = post.thumbnailUrl,
                durationMillis = post.durationMillis,
                requestContext = mediaContext(pageUrl, request.requestContext),
                confidence = CandidateConfidence.HIGH,
                expiresAtEpochMs = expiresAtEpochMs,
                drmHint = false,
                observedAtEpochMs = request.nowEpochMs,
            )
        }

        // The parser only returns a post with renditions, so an empty list means every link the
        // page exposed had already expired and the page has to be reloaded.
        if (candidates.isEmpty()) {
            return SiteExtractionResult.Failure(
                SiteExtractionFailure.EXPIRED_LINK, details = details,
            )
        }
        return SiteExtractionResult.Success(
            candidates, details + "parsed ${candidates.size} renditions",
        )
    }

    /**
     * Rebuilds the identity from the final URL after redirects.
     *
     * Short and share links only become addressable here, and a redirect that leaves Facebook
     * keeps the original identity rather than being followed into another site's content.
     */
    private fun resolvedIdentity(
        identity: SitePageIdentity,
        finalUrl: String,
    ): SitePageIdentity {
        if (!identity.requiresCanonicalResolution) return identity
        return FacebookUrls.identify(finalUrl)?.takeUnless { it.requiresCanonicalResolution }
            ?: identity
    }

    /** Prefers the ID the page itself reported, keeping the surface the request resolved to. */
    private fun pageUrl(identity: SitePageIdentity, post: FacebookPost): String {
        val videoId = post.videoId?.takeIf(FacebookUrls::isNumericId)
            ?: return identity.canonicalPageUrl
        return FacebookUrls.canonicalUrl(videoId, FacebookUrls.kindOf(identity.canonicalPageUrl))
    }

    /** Title stays metadata-only: the caption, the owner name, then the quality label. */
    private fun displayTitle(post: FacebookPost, label: String?): String? {
        val base = post.title?.trim()?.takeIf(String::isNotEmpty)
            ?: post.ownerName?.trim()?.takeIf(String::isNotEmpty)?.let { "$it on Facebook" }
            ?: return label
        return if (label.isNullOrBlank()) base else "$base — $label"
    }

    private fun pageHeaders(context: BrowserRequestContext): Map<String, String> =
        PageNavigationHeaders.withDefaults(
            buildMap {
                context.userAgent?.takeIf(String::isNotBlank)?.let { put("User-Agent", it) }
                context.cookie?.takeIf(String::isNotBlank)?.let { put("Cookie", it) }
                put("Accept-Language", "en-US,en;q=0.9")
                put("Referer", "https://www.facebook.com/")
            },
        )

    /**
     * Media requests keep the user agent and cookie but are re-anchored to the canonical page.
     *
     * The downstream resolver strips these again for cross-origin hops, so a CDN on another origin
     * never receives the session cookie.
     */
    private fun mediaContext(
        pageUrl: String,
        context: BrowserRequestContext,
    ): BrowserRequestContext = BrowserRequestContext(
        pageUrl = pageUrl,
        userAgent = context.userAgent,
        cookie = context.cookie,
    )

    companion object {
        const val DEFAULT_MAX_PAGE_BYTES: Long = 6L * 1024 * 1024
        private const val MP4_MIME_TYPE = "video/mp4"
        private const val DASH_MIME_TYPE = "application/dash+xml"
    }
}
