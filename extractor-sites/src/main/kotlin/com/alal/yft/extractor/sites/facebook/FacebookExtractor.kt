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
 * A reel is first asked as Safari without the user's session (P15): when that public page is the
 * requested video with AVC video and an AAC track, it is the whole lookup, one page request
 * instead of two (P23). Anything else reads the page with the user's session. Share, short and
 * post links skip that step: Facebook redirects them only for the desktop identity. So do
 * /watch/ links: Safari gets those without the video (sandbox live check, 2026-10-06). The
 * tracks of every page read for the video are merged, and the AVC ladder is asked once on the
 * video's own page when the pages lack the AVC sizes they show ([avcLadder]).
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
            is ExtractorHttpResult.Failure -> return publicFiles(
                request,
                public,
                publicDetails + "page GET failed (${result.reason})$PUBLIC_KEPT",
            ) ?: SiteExtractionResult.Failure(
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
            val wall = "page: login/checkpoint wall"
            return publicFiles(request, public, pageDetails + "$wall$PUBLIC_KEPT")
                ?: SiteExtractionResult.Failure(
                    SiteExtractionFailure.LOGIN_REQUIRED,
                    details = pageDetails + wall,
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
            // A refusal of DRM is final, whatever the public page showed.
            val kept = if (parsed.reason == SiteExtractionFailure.DRM_PROTECTED) {
                null
            } else {
                val gap = "page: ${parsed.reason.name}$PUBLIC_KEPT"
                publicFiles(request, public, pageDetails + parsed.details + gap)
            }
            return kept ?: SiteExtractionResult.Failure(
                failureFor(parsed.reason, resolvedIdentity),
                details = pageDetails + parsed.details,
            )
        }
        parsed as FacebookParseResult.Success
        val post = parsed.post
        val read = mutableListOf("page: ${contents(post)}")
        val publicPost = public?.post?.takeIf { it.videoId != null && it.videoId == post.videoId }
        val known = FacebookDashOffers.merged(post.dashTracks, publicPost?.dashTracks.orEmpty())
        val fromPublic = known.filterNot(post.dashTracks::contains)
        if (fromPublic.isNotEmpty()) {
            read += "public page added: ${FacebookDashOffers.summary(fromPublic)}"
        }
        val ladder = avcLadder(post, known, response.finalUrl, public)
        val tracks = FacebookDashOffers.merged(known, ladder.tracks)
        return offers(
            request,
            post.copy(dashTracks = tracks),
            pageUrl(resolvedIdentity, post),
            pageDetails + parsed.details + read + ladder.detail,
        )
    }

    /**
     * The page asked as Safari without the user's session (P15), or null when the link is not
     * a reel. Its [PublicPage.result] is set only when the page is the requested video with AVC
     * video and an AAC track (P23), or when the line failed: asking again would wait on the same
     * line. Any other answer leaves the lookup to the page with the user's session; a page with
     * only HD/SD files is kept in [PublicPage.post] for the merge and in case that page fails.
     */
    private suspend fun publicPage(request: SiteExtractionRequest): PublicPage? {
        val identity = request.identity
        // Share, short and post links redirect to their video only for the desktop identity:
        // Safari got a small page without the redirect (sandbox live check, 2026-10-05), and a
        // /watch/ page without the video (2026-10-05 and 2026-10-06).
        if (!FacebookUrls.isReel(identity)) return null
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
            FacebookDashOffers.hasAvcWithSound(post.dashTracks) -> null
            post.dashTracks.isEmpty() -> contents(post)
            post.dashTracks.none(FacebookDashOffers::isAvc) -> "${contents(post)}, no AVC video"
            else -> "${contents(post)}, no audio track"
        }
        if (gap != null || post == null) {
            return PublicPage(url, listOf(read, "public page: $gap$SESSION_NEXT"), post)
        }
        val details = listOf(read) + (parsed as FacebookParseResult.Success).details +
            "public page: the video, without the session: ${contents(post)}"
        val result = offers(request, post, pageUrl(identity, post), details)
        if (result is SiteExtractionResult.Success) return PublicPage(url, details, post, result)
        return PublicPage(url, listOf(read, "public page: no download left$SESSION_NEXT"), post)
    }

    /**
     * The public page's own files when the session page could not be read: the video was public
     * there, so its HD/SD files still download (P23). Null without such a page.
     */
    private fun publicFiles(
        request: SiteExtractionRequest,
        public: PublicPage?,
        details: List<String>,
    ): SiteExtractionResult? {
        val post = public?.post ?: return null
        return offers(request, post, pageUrl(request.identity, post), details)
            .takeIf { it is SiteExtractionResult.Success }
    }

    /** What a page held, for the details: `AVC 360/720 + audio`, `HD/SD files only`. */
    private fun contents(post: FacebookPost): String {
        val files = post.renditions.count { it.delivery == FacebookDelivery.PROGRESSIVE }
        return when {
            post.dashTracks.isNotEmpty() -> FacebookDashOffers.summary(post.dashTracks) +
                if (files > 0) ", $files whole files" else ""
            files > 0 -> "HD/SD files only"
            else -> "a DASH address only"
        }
    }

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

    /** The candidates of [post], whose tracks are those of every page read for the video. */
    private fun offers(
        request: SiteExtractionRequest,
        post: FacebookPost,
        pageUrl: String,
        details: List<String>,
    ): SiteExtractionResult {
        val tracks = post.dashTracks
        val sound = FacebookDashOffers.audio(tracks)
        val progressive = post.renditions.mapNotNull { rendition ->
            val expiresAtEpochMs = FacebookUrls.mediaExpiryEpochMs(rendition.url)
            // Handing an already-expired link to the download engine would only fail later.
            if (expiresAtEpochMs != null && expiresAtEpochMs <= request.nowEpochMs) {
                return@mapNotNull null
            }
            val whole = rendition.delivery == FacebookDelivery.PROGRESSIVE
            val encoding = if (whole) FacebookDashOffers.encodingOf(rendition.url, tracks) else null
            // A file states its picture only with its sound's codec (P23): a stated size alone
            // would stop the resolver reading the file's own header, which finds its sound (P3).
            val codecs = if (encoding != null && sound != null) {
                listOf(encoding.codec, sound.codec)
            } else {
                emptyList()
            }
            val described = encoding?.takeIf { codecs.isNotEmpty() }
            MediaCandidate(
                pageUrl = pageUrl,
                mediaUrl = rendition.url,
                sources = setOf(CandidateSource.MANIFEST),
                kind = if (whole) MediaKind.DIRECT else MediaKind.DASH,
                mimeType = if (whole) MP4_MIME_TYPE else DASH_MIME_TYPE,
                title = displayTitle(post, fileLabel(rendition, encoding)),
                thumbnailUrl = post.thumbnailUrl,
                durationMillis = post.durationMillis,
                requestContext = mediaContext(pageUrl, request.requestContext),
                confidence = CandidateConfidence.HIGH,
                expiresAtEpochMs = expiresAtEpochMs,
                drmHint = false,
                observedAtEpochMs = request.nowEpochMs,
                codecs = codecs,
                width = described?.width,
                height = described?.height,
                framesPerSecond = described?.framesPerSecond,
                // The address's own bitrate matched the file; never the manifest's peak value.
                bitrateBitsPerSecond = rendition.url.takeIf { whole }
                    ?.let(FacebookUrls::statedBitrate),
            )
        }
        val candidates = progressive + trackCandidates(post, tracks, pageUrl, request)

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
                details = details,
            )
        }
        return SiteExtractionResult.Success(
            candidates,
            details + "parsed ${candidates.size} renditions",
        )
    }

    /** `720p · HD` for a file made from a manifest track, else the page's own label. */
    private fun fileLabel(rendition: FacebookRendition, encoding: FacebookDashTrack?): String? {
        val label = rendition.label
        if (encoding == null || label != null && NAMED_HEIGHT.containsMatchIn(label)) return label
        val name = FacebookDashOffers.qualityName(encoding)
        return rendition.quality?.let { "$name · $it" } ?: name
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
        // The audio track's bandwidth matched its file, like the bitrate its address states.
        val audioBitrate = FacebookUrls.statedBitrate(audio.url) ?: audio.bandwidthBitsPerSecond
        val companion = CompanionAudio(
            mediaUrl = audio.url,
            mimeType = audio.mimeType,
            codecs = listOf(audio.codec),
            requestContext = context,
            // The resolver adds this estimate to the video file's own length into one size.
            contentLengthBytes = estimatedBytes(audioBitrate, post.durationMillis),
            bitrateBitsPerSecond = audioBitrate,
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
                    // The address's average bitrate, never the manifest's peak (P23): the sheet
                    // estimates a size from it until the CDN states the file's length.
                    bitrateBitsPerSecond = FacebookUrls.statedBitrate(video.url),
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
            bitrateBitsPerSecond = audioBitrate,
        )
        return merged + sound
    }

    private class AvcLadder(val tracks: List<FacebookDashTrack>, val detail: String)

    /**
     * Reads the AVC ladder from the page Facebook serves Safari ([FacebookPageIdentity
     * .AVC_LADDER_USER_AGENT]) when the pages read so far lack the AVC sizes they show
     * ([FacebookDashOffers.needsAvcLadder]). It is asked once, without the session, on the
     * video's own page: the page the session request ended on (a share, short or post link
     * after its redirect, a /watch/ link at `/{page}/videos/{id}/`), else the permalink the page
     * states. A public video gets its AVC tracks and AAC sound; a video only the signed-in user
     * may see keeps the first page's tracks, and the cookie never travels with a second
     * identity. Only tracks of the same video ID are taken, and the public page already asked
     * that way (P15) is not asked again.
     */
    private suspend fun avcLadder(
        post: FacebookPost,
        known: List<FacebookDashTrack>,
        finalUrl: String,
        public: PublicPage?,
    ): AvcLadder {
        // A file shows its picture through its manifest track, else an HD file counts as 720
        // lines, as the download sheet ranks it.
        val files = post.renditions.filter { it.delivery == FacebookDelivery.PROGRESSIVE }
            .mapNotNull { file ->
                FacebookDashOffers.encodingOf(file.url, known)?.let(FacebookDashOffers::shortSide)
                    ?: NAMED_FILE_SIDES[file.quality]
            }
        if (!FacebookDashOffers.needsAvcLadder(known, files)) {
            val best = FacebookDashOffers.bestAvc(known)
            return AvcLadder(emptyList(), "ladder: not needed ($best)")
        }
        val videoId = post.videoId?.takeIf(FacebookUrls::isNumericId)
            ?: return AvcLadder(emptyList(), "ladder: skipped without a video ID")
        val url = FacebookUrls.videoPage(videoId, listOf(finalUrl, post.permalinkUrl))
            ?: return AvcLadder(emptyList(), "ladder: skipped, no reel or video page to ask")
        if (public != null && public.url == url) {
            return AvcLadder(emptyList(), "ladder: the public page, already read")
        }
        val response = when (
            val result = http.get(
                url = url,
                headers = publicPageHeaders(),
                maxBodyBytes = maxPageBytes,
            )
        ) {
            is ExtractorHttpResult.Failure ->
                return AvcLadder(emptyList(), "ladder GET failed (${result.reason})")

            is ExtractorHttpResult.Success -> result
        }
        if (FacebookUrls.isAccessWall(response.finalUrl)) {
            return AvcLadder(emptyList(), "ladder: login/checkpoint wall")
        }
        val read = "ladder GET ${response.statusCode} (${response.body.length} characters)"
        val parsed = FacebookPageParser.parse(response.body, videoId)
        val tracks = (parsed as? FacebookParseResult.Success)?.post
            ?.takeIf { it.videoId == videoId }
            ?.dashTracks.orEmpty()
        val added = FacebookDashOffers.merged(known, tracks).filterNot(known::contains)
        val answer = when {
            parsed is FacebookParseResult.Failure -> parsed.reason.name
            tracks.isEmpty() -> "no tracks of this video"
            added.isEmpty() -> "nothing new (${FacebookDashOffers.summary(tracks)})"
            else -> "added ${FacebookDashOffers.summary(added)}"
        }
        return AvcLadder(tracks, "$read: $answer")
    }

    private fun isExpired(url: String, nowEpochMs: Long): Boolean =
        FacebookUrls.mediaExpiryEpochMs(url)?.let { it <= nowEpochMs } == true

    /**
     * An audio bitrate over the duration; null when either is unknown. Used for the audio track
     * only: Facebook's audio bandwidth matched the real file in the live check, while a video
     * track's bandwidth can be a peak value.
     */
    private fun estimatedBytes(bitsPerSecond: Long?, durationMillis: Long?): Long? {
        val millis = durationMillis?.takeIf { it > 0 } ?: return null
        val bits = bitsPerSecond ?: return null
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
        private const val PUBLIC_KEPT = ", so the public page's files are kept"
        private val NAMED_HEIGHT = Regex("""^\d+p\b""")
        private val NAMED_FILE_SIDES = mapOf("HD" to 720, "Full HD" to 1_080, "4K" to 2_160)

        /** A public page that failed this way ends the lookup: the line itself failed. */
        private val LINE_FAILURES = setOf(
            SiteExtractionFailure.NETWORK,
            SiteExtractionFailure.RATE_LIMITED,
        )
    }
}
