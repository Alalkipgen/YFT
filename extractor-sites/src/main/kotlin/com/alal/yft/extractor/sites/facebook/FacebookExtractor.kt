package com.alal.yft.extractor.sites.facebook

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.CompanionAudio
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
                failureFor(parsed.reason, resolvedIdentity),
                details = pageDetails + parsed.details,
            )
        }
        parsed as FacebookParseResult.Success
        val post = parsed.post
        val details = pageDetails + parsed.details

        val pageUrl = pageUrl(resolvedIdentity, post)
        val avc = avcLadder(pageUrl, post)
        val tracks = (post.dashTracks + avc.tracks).distinctBy(FacebookDashTrack::url)
        val progressive = post.renditions.mapNotNull { rendition ->
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
        val candidates = progressive + trackCandidates(post, tracks, pageUrl, request)
        val trackDetails = listOfNotNull(
            "manifest: ${post.dashTracks.size} whole-file tracks".takeIf {
                post.dashTracks.isNotEmpty()
            },
            avc.detail,
        )

        // The parser only returns a post with media: with nothing left, either every link the
        // page exposed had already expired and the page has to be reloaded, or no track was one
        // the phone can save.
        if (candidates.isEmpty()) {
            val expired = (post.renditions.map(FacebookRendition::url) + tracks.map { it.url })
                .any { isExpired(it, request.nowEpochMs) }
            return SiteExtractionResult.Failure(
                if (expired) {
                    SiteExtractionFailure.EXPIRED_LINK
                } else {
                    SiteExtractionFailure.NO_MEDIA_FOUND
                },
                details = details + trackDetails,
            )
        }
        return SiteExtractionResult.Success(
            candidates,
            details + trackDetails + "parsed ${candidates.size} renditions",
        )
    }

    /**
     * The manifest's tracks as download rows (P4): every mergeable picture size as one MP4 with
     * the best AAC track as its sound, like YouTube's merged rows, and that AAC track alone for
     * Audio (M4A, MP3). A merged size is estimated from both bitrates over the duration; the
     * audio file's exact size comes from its own lookup.
     */
    private fun trackCandidates(
        post: FacebookPost,
        tracks: List<FacebookDashTrack>,
        pageUrl: String,
        request: SiteExtractionRequest,
    ): List<MediaCandidate> {
        val now = request.nowEpochMs
        val audio = FacebookDashOffers.audio(tracks)
            ?.takeUnless { isExpired(it.url, now) } ?: return emptyList()
        val context = mediaContext(pageUrl, request.requestContext)
        val companion = CompanionAudio(
            mediaUrl = audio.url,
            mimeType = audio.mimeType,
            codecs = listOf(audio.codec),
            requestContext = context,
            bitrateBitsPerSecond = audio.bandwidthBitsPerSecond,
            expiresAtEpochMs = FacebookUrls.mediaExpiryEpochMs(audio.url),
        )
        val merged = FacebookDashOffers.videos(tracks)
            .filterNot { isExpired(it.url, now) }
            .map { video ->
                MediaCandidate(
                    pageUrl = pageUrl,
                    mediaUrl = video.url,
                    sources = setOf(CandidateSource.MANIFEST),
                    kind = MediaKind.DIRECT,
                    mimeType = video.mimeType,
                    title = displayTitle(post, FacebookDashOffers.qualityName(video)),
                    thumbnailUrl = post.thumbnailUrl,
                    durationMillis = post.durationMillis,
                    contentLengthBytes = estimatedBytes(video, audio, post.durationMillis),
                    requestContext = context,
                    confidence = CandidateConfidence.HIGH,
                    expiresAtEpochMs = listOfNotNull(
                        FacebookUrls.mediaExpiryEpochMs(video.url),
                        companion.expiresAtEpochMs,
                    ).minOrNull(),
                    drmHint = false,
                    observedAtEpochMs = now,
                    codecs = listOf(video.codec),
                    audioCompanion = companion,
                    width = video.width,
                    height = video.height,
                    framesPerSecond = video.framesPerSecond,
                    bitrateBitsPerSecond = video.bandwidthBitsPerSecond,
                )
            }
        val sound = MediaCandidate(
            pageUrl = pageUrl,
            mediaUrl = audio.url,
            sources = setOf(CandidateSource.MANIFEST),
            kind = MediaKind.DIRECT,
            mimeType = audio.mimeType,
            title = displayTitle(post, AUDIO_LABEL),
            thumbnailUrl = post.thumbnailUrl,
            durationMillis = post.durationMillis,
            requestContext = context,
            confidence = CandidateConfidence.HIGH,
            expiresAtEpochMs = companion.expiresAtEpochMs,
            drmHint = false,
            observedAtEpochMs = now,
            codecs = listOf(audio.codec),
            bitrateBitsPerSecond = audio.bandwidthBitsPerSecond,
        )
        return merged + sound
    }

    private class AvcLadder(val tracks: List<FacebookDashTrack>, val detail: String?)

    /**
     * Reads the AVC ladder from the page Facebook serves Safari when the first page listed AV1 or
     * VP9 video only ([FacebookPageIdentity.AVC_LADDER_USER_AGENT]). The request carries no
     * session: a public video gets its AVC tracks, a video only the signed-in user may see keeps
     * the first page's tracks, and the cookie never travels with a second identity. Only tracks
     * of the same video ID are taken.
     */
    private suspend fun avcLadder(pageUrl: String, post: FacebookPost): AvcLadder {
        if (!FacebookDashOffers.lacksAvcVideo(post.dashTracks)) return AvcLadder(emptyList(), null)
        val videoId = post.videoId?.takeIf(FacebookUrls::isNumericId)
            ?: return AvcLadder(emptyList(), "AVC page: skipped without a video ID")
        val response = when (
            val result = http.get(
                url = pageUrl,
                headers = PageNavigationHeaders.withDefaults(
                    mapOf(
                        "User-Agent" to FacebookPageIdentity.AVC_LADDER_USER_AGENT,
                        "Accept-Language" to "en-US,en;q=0.9",
                        "Referer" to "https://www.facebook.com/",
                    ),
                ),
                maxBodyBytes = maxPageBytes,
            )
        ) {
            is ExtractorHttpResult.Failure ->
                return AvcLadder(emptyList(), "AVC page: ${result.reason}")

            is ExtractorHttpResult.Success -> result
        }
        if (FacebookUrls.isAccessWall(response.finalUrl)) {
            return AvcLadder(emptyList(), "AVC page: login/checkpoint wall")
        }
        val parsed = FacebookPageParser.parse(response.body, videoId)
        val tracks = (parsed as? FacebookParseResult.Success)?.post
            ?.takeIf { it.videoId == videoId }
            ?.dashTracks.orEmpty()
            .filter(FacebookDashOffers::isAvc)
        return AvcLadder(tracks, "AVC page GET ${response.statusCode}: ${tracks.size} AVC tracks")
    }

    private fun isExpired(url: String, nowEpochMs: Long): Boolean =
        FacebookUrls.mediaExpiryEpochMs(url)?.let { it <= nowEpochMs } == true

    /** Both tracks' stated bitrates over the duration; null when one of them is unknown. */
    private fun estimatedBytes(
        video: FacebookDashTrack,
        audio: FacebookDashTrack,
        durationMillis: Long?,
    ): Long? {
        val millis = durationMillis?.takeIf { it > 0 } ?: return null
        val videoBits = video.bandwidthBitsPerSecond ?: return null
        val audioBits = audio.bandwidthBitsPerSecond ?: return null
        return ((videoBits + audioBits) * millis / BITS_PER_BYTE_MILLIS).takeIf { it > 0 }
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

    /**
     * A post whose page did not lead to a video holds no video: that is not a changed page
     * format. A post with a video redirects to the video's own page and resolves above.
     */
    private fun failureFor(
        reason: SiteExtractionFailure,
        identity: SitePageIdentity,
    ): SiteExtractionFailure =
        if (reason == SiteExtractionFailure.RESPONSE_CHANGED && FacebookUrls.isPost(identity)) {
            SiteExtractionFailure.NO_MEDIA_FOUND
        } else {
            reason
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
                FacebookPageIdentity.forPageRequest(context.userAgent)
                    ?.takeIf(String::isNotBlank)
                    ?.let { put("User-Agent", it) }
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
        private const val AUDIO_LABEL = "Audio"
        private const val BITS_PER_BYTE_MILLIS = 8_000L
    }
}
