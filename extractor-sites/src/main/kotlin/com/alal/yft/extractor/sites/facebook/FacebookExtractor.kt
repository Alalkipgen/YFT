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
 *
 * A reel or video link is first asked as Safari without the user's session (P15): when that
 * public page is the requested video with AVC tracks or a whole file, it is the whole lookup,
 * one page request instead of two. Anything else reads the page with the user's session as
 * before. Share, short and post links skip that step: Facebook redirects them only for the
 * desktop Chrome identity.
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

        val public = publicPage(request)
        public?.result?.let { return it }
        val publicDetails = public?.details.orEmpty()

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
                details = publicDetails,
            )

            is ExtractorHttpResult.Success -> result
        }

        val pageDetails = publicDetails + listOf(
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
        val pageUrl = pageUrl(resolvedIdentity, post)
        val avc = avcLadder(pageUrl, post, public)
        return offers(request, post, pageUrl, pageDetails + parsed.details, avc)
    }

    /**
     * The page asked as Safari without the user's session (P15), or null when the link is not
     * asked that way. Its [PublicPage.result] is set only when the page is the requested video
     * with AVC tracks or a whole file, or when the line failed: asking again would wait on the
     * same line. Any other answer leaves the lookup to the page with the user's session.
     */
    private suspend fun publicPage(request: SiteExtractionRequest): PublicPage? {
        val identity = request.identity
        // Share, short and post links redirect to their video only for desktop Chrome: Safari
        // got a small page without the redirect (sandbox live check, 2026-10-05).
        if (identity.requiresCanonicalResolution) return null
        val url = identity.canonicalPageUrl
        val response = when (
            val result = http.get(url, publicPageHeaders(), maxPageBytes)
        ) {
            is ExtractorHttpResult.Failure -> {
                val detail = "public page GET failed (${result.reason})"
                val final = SiteExtractionResult.Failure(
                    reason = result.reason,
                    httpStatusCode = result.statusCode,
                    details = listOf(detail),
                ).takeIf { result.reason in LINE_FAILURES }
                return PublicPage(url, listOf(detail + SESSION_NEXT), result = final)
            }

            is ExtractorHttpResult.Success -> result
        }
        val read = "public page GET ${response.statusCode} (${response.body.length} characters)"
        if (FacebookUrls.isAccessWall(response.finalUrl)) {
            return PublicPage(url, listOf(read, "public page: login/checkpoint wall$SESSION_NEXT"))
        }
        val parsed = FacebookPageParser.parse(response.body, identity.contentId)
        val post = (parsed as? FacebookParseResult.Success)?.post
            ?.takeIf { it.videoId == identity.contentId }
        val gap = when {
            parsed is FacebookParseResult.Failure -> parsed.reason.name
            post == null -> "another video"
            !hasSavableFile(post) -> "no AVC track or whole file"
            else -> null
        }
        if (gap != null || post == null) {
            return PublicPage(url, listOf(read, "public page: $gap$SESSION_NEXT"), post)
        }
        val details = listOf(read) + (parsed as FacebookParseResult.Success).details +
            "public page: the video, without the session"
        val result = offers(request, post, pageUrl(identity, post), details, AvcLadder.NONE)
        if (result is SiteExtractionResult.Success) return PublicPage(url, details, post, result)
        return PublicPage(url, listOf(read, "public page: no download left$SESSION_NEXT"), post)
    }

    /** An AVC track every phone merges, or a whole file with its sound. */
    private fun hasSavableFile(post: FacebookPost): Boolean =
        post.dashTracks.any(FacebookDashOffers::isAvc) ||
            post.renditions.any { it.delivery == FacebookDelivery.PROGRESSIVE }

    /**
     * What the public page answered: [post] is the requested video when the page had it, kept so
     * the AVC ladder never asks the same page twice; [result] ends the lookup when set.
     */
    private class PublicPage(
        val url: String,
        val details: List<String>,
        val post: FacebookPost? = null,
        val result: SiteExtractionResult? = null,
    )

    /** The candidates of one page's [post], with the AVC ladder's tracks when it was asked. */
    private fun offers(
        request: SiteExtractionRequest,
        post: FacebookPost,
        pageUrl: String,
        details: List<String>,
        avc: AvcLadder,
    ): SiteExtractionResult {
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
     * Audio (M4A, MP3). A merged row states no size: the resolver reads the video file's length
     * from the CDN and adds the sound's estimate (audio bitrate over the duration), because an
     * AVC track's bandwidth is a peak value. The Audio row's exact size comes from its own lookup.
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
            // The audio track's stated bitrate matches its file; the resolver adds it to the
            // video file's own length into one estimated size.
            contentLengthBytes = estimatedBytes(audio, post.durationMillis),
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
                    // Unknown on purpose: an AVC track's bandwidth is a peak (about 3.5 times the
                    // real file), so the resolver asks the CDN for the video file's length.
                    contentLengthBytes = null,
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

    private class AvcLadder(val tracks: List<FacebookDashTrack>, val detail: String?) {
        companion object {
            val NONE = AvcLadder(emptyList(), null)
        }
    }

    /**
     * Reads the AVC ladder from the page Facebook serves Safari when the first page listed AV1 or
     * VP9 video only ([FacebookPageIdentity.AVC_LADDER_USER_AGENT]). The request carries no
     * session: a public video gets its AVC tracks, a video only the signed-in user may see keeps
     * the first page's tracks, and the cookie never travels with a second identity. Only tracks
     * of the same video ID are taken. The public page already asked that way (P15) is not asked
     * again: its own AVC tracks are used.
     */
    private suspend fun avcLadder(
        pageUrl: String,
        post: FacebookPost,
        public: PublicPage?,
    ): AvcLadder {
        if (!FacebookDashOffers.lacksAvcVideo(post.dashTracks)) return AvcLadder.NONE
        val videoId = post.videoId?.takeIf(FacebookUrls::isNumericId)
            ?: return AvcLadder(emptyList(), "AVC page: skipped without a video ID")
        if (public != null && public.url == pageUrl) {
            val tracks = public.post?.takeIf { it.videoId == videoId }?.dashTracks.orEmpty()
                .filter(FacebookDashOffers::isAvc)
            return AvcLadder(tracks, "AVC page: the public page's ${tracks.size} AVC tracks")
        }
        val response = when (
            val result = http.get(
                url = pageUrl,
                headers = publicPageHeaders(),
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

    /**
     * A track's stated bitrate over the duration; null when either is unknown. Used for the audio
     * track only: Facebook's audio bandwidth matched the real file in the live check, while a
     * video track's bandwidth can be a peak value.
     */
    private fun estimatedBytes(track: FacebookDashTrack, durationMillis: Long?): Long? {
        val millis = durationMillis?.takeIf { it > 0 } ?: return null
        val bits = track.bandwidthBitsPerSecond ?: return null
        return (bits * millis / BITS_PER_BYTE_MILLIS).takeIf { it > 0 }
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

    /** Desktop Safari without any session: the public page (P15) and the AVC ladder (P4). */
    private fun publicPageHeaders(): Map<String, String> = PageNavigationHeaders.withDefaults(
        mapOf(
            "User-Agent" to FacebookPageIdentity.AVC_LADDER_USER_AGENT,
            "Accept-Language" to "en-US,en;q=0.9",
            "Referer" to "https://www.facebook.com/",
        ),
    )

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
        private const val SESSION_NEXT = ", so the session page is read"

        /** A public page that failed this way ends the lookup: the line itself failed. */
        private val LINE_FAILURES = setOf(
            SiteExtractionFailure.NETWORK,
            SiteExtractionFailure.RATE_LIMITED,
        )
    }
}
