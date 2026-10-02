package com.alal.yft.extractor.sites.youtube

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.NoPlayerScriptRunner
import com.alal.yft.extractor.api.PlayerScriptChallenge
import com.alal.yft.extractor.api.PlayerScriptChallengeKind
import com.alal.yft.extractor.api.PlayerScriptRequest
import com.alal.yft.extractor.api.PlayerScriptResult
import com.alal.yft.extractor.api.PlayerScriptRunner
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.SitePageIdentity
import java.net.URLDecoder

/**
 * YouTube adapter for videos the user can already play in their own browser.
 *
 * It reads the watch page the browser receives and, when that page withholds delivery details,
 * asks YouTube's own player endpoint with one of the client profiles in
 * [YouTubeClientProfiles]. It executes no script itself: a stream YouTube protects with a
 * player-computed value is handed to a [PlayerScriptRunner] when the build has one, and
 * otherwise reported as [SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED].
 *
 * There is no DRM handling, no age or content-gate acknowledgement and no device impersonation.
 * A video the user cannot play fails with a structured reason.
 */
class YouTubeExtractor(
    private val http: ExtractorHttpClient,
    private val playerScripts: PlayerScriptRunner = NoPlayerScriptRunner,
    private val clients: List<YouTubeClientProfile> = YouTubeClientProfiles.DEFAULT_ORDER,
    private val maxPageBytes: Long = DEFAULT_MAX_PAGE_BYTES,
    private val maxPlayerBytes: Long = DEFAULT_MAX_PLAYER_BYTES,
) : SiteExtractor {
    override val id: String = YouTubeUrls.SITE_ID

    override val displayName: String = "YouTube"

    override fun identify(pageUrl: String): SitePageIdentity? = YouTubeUrls.identify(pageUrl)

    override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult {
        require(request.identity.siteId == id) { "This adapter only handles YouTube identities" }
        val pageUrl = request.identity.canonicalPageUrl

        val page = when (
            val result = http.get(
                url = pageUrl,
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

        val signals = YouTubePlayerResponseParser.pageSignals(page.body)
        var firstFailure: SiteExtractionFailure? = null

        // The inline response is tried first because it costs no extra request and reflects the
        // user's own session exactly as the browser saw it.
        signals.playerResponseJson?.let { json ->
            when (val parsed = YouTubePlayerResponseParser.parse(json, request.nowEpochMs)) {
                is YouTubeParseResult.Success -> finish(parsed.video, signals, request)
                    ?.let { return it }

                is YouTubeParseResult.Failure -> firstFailure = keep(firstFailure, parsed.reason)
            }
        }

        for (client in clients) {
            val response = requestPlayer(client, signals, request) ?: continue
            when (val parsed = YouTubePlayerResponseParser.parse(response, request.nowEpochMs)) {
                is YouTubeParseResult.Success -> finish(parsed.video, signals, request)
                    ?.let { return it }

                is YouTubeParseResult.Failure -> firstFailure = keep(firstFailure, parsed.reason)
            }
        }

        return SiteExtractionResult.Failure(
            firstFailure ?: SiteExtractionFailure.RESPONSE_CHANGED,
        )
    }

    /**
     * Keeps the most informative failure seen so far.
     *
     * A definite verdict such as a private video must survive a later client that merely reports
     * changed markup, so the user is told the real reason.
     */
    private fun keep(
        current: SiteExtractionFailure?,
        candidate: SiteExtractionFailure,
    ): SiteExtractionFailure {
        if (current == null) return candidate
        return if (rank(candidate) > rank(current)) candidate else current
    }

    private fun rank(reason: SiteExtractionFailure): Int = when (reason) {
        SiteExtractionFailure.DRM_PROTECTED,
        SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
        SiteExtractionFailure.GEO_RESTRICTED,
        -> 4

        SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED -> 3
        SiteExtractionFailure.LOGIN_REQUIRED, SiteExtractionFailure.NO_MEDIA_FOUND -> 2
        SiteExtractionFailure.RESPONSE_CHANGED, SiteExtractionFailure.MALFORMED_RESPONSE -> 0
        else -> 1
    }

    private suspend fun requestPlayer(
        client: YouTubeClientProfile,
        signals: YouTubePageSignals,
        request: SiteExtractionRequest,
    ): String? {
        val body = client.playerRequestBody(
            videoId = request.identity.contentId,
            clientVersion = signals.clientVersion,
            visitorData = signals.visitorData,
        )
        val result = http.postJson(
            url = YouTubeUrls.innerTubeUrl(signals.apiKey),
            body = body,
            headers = playerHeaders(client, signals, request.requestContext),
            maxBodyBytes = maxPlayerBytes,
        )
        return (result as? ExtractorHttpResult.Success)?.body
    }

    /**
     * Turns a parsed response into candidates, or null when this response cannot produce any.
     *
     * Returning null lets the caller try the next client instead of reporting a failure that a
     * different client may not have.
     */
    private suspend fun finish(
        video: YouTubeVideo,
        signals: YouTubePageSignals,
        request: SiteExtractionRequest,
    ): SiteExtractionResult? {
        val expiresAtEpochMs = video.expiresAtEpochMs
        if (expiresAtEpochMs != null && expiresAtEpochMs <= request.nowEpochMs) {
            return SiteExtractionResult.Failure(SiteExtractionFailure.EXPIRED_LINK)
        }

        // Only progressive streams are offered today: they already carry both tracks, so they
        // need no merge step. Adaptive-only responses are reported honestly instead of being
        // offered as a silent video-only download.
        val plain = video.progressive.filter { it.protection == YouTubeProtection.NONE }
        val usable = plain.ifEmpty {
            resolveProtected(video.progressive, signals, request) ?: return null
        }
        if (usable.isEmpty()) return null

        val candidates = usable
            .sortedByDescending { it.height ?: 0 }
            .map { stream -> candidate(stream, video, expiresAtEpochMs, request) }
        return SiteExtractionResult.Success(candidates)
    }

    /**
     * Asks the player-script host to compute the values YouTube's player would compute.
     *
     * Returns null when the host cannot help, so the caller can try another client first; the
     * final verdict is reported by [extract] as [SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED].
     */
    private suspend fun resolveProtected(
        streams: List<YouTubeStream>,
        signals: YouTubePageSignals,
        request: SiteExtractionRequest,
    ): List<YouTubeStream>? {
        val protected = streams.filter { it.protectedDescriptor != null }
        if (protected.isEmpty()) return null
        val scriptUrl = signals.playerScriptUrl
        if (!playerScripts.isAvailable || scriptUrl == null) return null

        val descriptors = protected.mapNotNull { stream ->
            val parsed = parseDescriptor(stream.protectedDescriptor!!) ?: return@mapNotNull null
            stream to parsed
        }
        if (descriptors.isEmpty()) return null

        val challenges = descriptors.map { (stream, descriptor) ->
            PlayerScriptChallenge(
                key = "sig-${stream.itag}",
                kind = PlayerScriptChallengeKind.SIGNATURE,
                input = descriptor.signatureInput,
            )
        }
        val result = playerScripts.resolve(
            PlayerScriptRequest(
                playerScriptUrl = scriptUrl,
                pageUrl = request.identity.canonicalPageUrl,
                challenges = challenges,
            ),
        )
        val resolved = (result as? PlayerScriptResult.Success)?.resolved ?: return null

        return descriptors.mapNotNull { (stream, descriptor) ->
            val signature = resolved["sig-${stream.itag}"]?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val separator = if (descriptor.mediaUrl.contains('?')) '&' else '?'
            stream.copy(
                url = "${descriptor.mediaUrl}$separator${descriptor.signatureParam}=$signature",
                protection = YouTubeProtection.NONE,
            )
        }.takeIf { it.isNotEmpty() }
    }

    private data class Descriptor(
        val mediaUrl: String,
        val signatureInput: String,
        val signatureParam: String,
    )

    /**
     * Splits YouTube's protected descriptor into its parts.
     *
     * The descriptor is a form-encoded document holding the media URL, the value the player must
     * transform and the parameter name to put the result in. Only HTTPS media URLs are accepted.
     */
    private fun parseDescriptor(descriptor: String): Descriptor? {
        val fields = descriptor.split('&').mapNotNull { pair ->
            val separator = pair.indexOf('=')
            if (separator <= 0) {
                null
            } else {
                pair.substring(0, separator) to decode(pair.substring(separator + 1))
            }
        }.toMap()
        val mediaUrl = fields["url"]?.takeIf { it.startsWith("https://") } ?: return null
        val signatureInput = fields["s"]?.takeIf { it.isNotBlank() } ?: return null
        val signatureParam = fields["sp"]?.takeIf { it.all(Char::isLetterOrDigit) } ?: "signature"
        return Descriptor(mediaUrl, signatureInput, signatureParam)
    }

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrElse { value }

    private fun candidate(
        stream: YouTubeStream,
        video: YouTubeVideo,
        expiresAtEpochMs: Long?,
        request: SiteExtractionRequest,
    ): MediaCandidate {
        val pageUrl = request.identity.canonicalPageUrl
        return MediaCandidate(
            pageUrl = pageUrl,
            mediaUrl = requireNotNull(stream.url),
            sources = setOf(CandidateSource.MANIFEST),
            kind = MediaKind.DIRECT,
            mimeType = stream.mimeType ?: "video/mp4",
            title = displayTitle(video, stream),
            thumbnailUrl = video.thumbnailUrl,
            durationMillis = video.durationMillis,
            contentLengthBytes = stream.contentLengthBytes,
            requestContext = BrowserRequestContext(
                pageUrl = pageUrl,
                userAgent = request.requestContext.userAgent,
                cookie = request.requestContext.cookie,
            ),
            confidence = CandidateConfidence.HIGH,
            expiresAtEpochMs = expiresAtEpochMs,
            drmHint = false,
            observedAtEpochMs = request.nowEpochMs,
        )
    }

    /** Title stays metadata-only: the video title, then the quality label YouTube stated. */
    private fun displayTitle(video: YouTubeVideo, stream: YouTubeStream): String? {
        val base = video.title ?: video.author?.let { "$it on YouTube" } ?: return stream.qualityLabel
        val label = stream.qualityLabel ?: return base
        return "$base — $label"
    }

    private fun pageHeaders(context: BrowserRequestContext): Map<String, String> = buildMap {
        context.userAgent?.takeIf(String::isNotBlank)?.let { put("User-Agent", it) }
        context.cookie?.takeIf(String::isNotBlank)?.let { put("Cookie", it) }
        put("Accept-Language", "en-US,en;q=0.9")
    }

    /**
     * Headers for the player request.
     *
     * A profile that does not replay the session is asked without the user's cookie, so a public
     * embed request never carries the account identity.
     */
    private fun playerHeaders(
        client: YouTubeClientProfile,
        signals: YouTubePageSignals,
        context: BrowserRequestContext,
    ): Map<String, String> = buildMap {
        context.userAgent?.takeIf(String::isNotBlank)?.let { put("User-Agent", it) }
        if (client.replaysSession) {
            context.cookie?.takeIf(String::isNotBlank)?.let { put("Cookie", it) }
        }
        put("Content-Type", "application/json")
        put("Accept", "application/json")
        put("Accept-Language", "en-US,en;q=0.9")
        put("Referer", client.referer)
        put("Origin", "https://www.youtube.com")
        put("X-YouTube-Client-Name", client.clientName)
        signals.clientVersion?.takeIf { it.isNotBlank() }
            ?.let { put("X-YouTube-Client-Version", it) }
        signals.visitorData?.takeIf { it.isNotBlank() }?.let { put("X-Goog-Visitor-Id", it) }
    }

    companion object {
        const val DEFAULT_MAX_PAGE_BYTES: Long = 4L * 1024 * 1024
        const val DEFAULT_MAX_PLAYER_BYTES: Long = 2L * 1024 * 1024
    }
}