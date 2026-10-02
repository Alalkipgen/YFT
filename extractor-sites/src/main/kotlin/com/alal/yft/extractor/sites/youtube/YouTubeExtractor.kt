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
 * YouTube adapter for offline copies of videos the user can watch.
 *
 * It reads the watch page the user's browser receives, then asks YouTube's player endpoint the
 * way YouTube's own embedded player does. When the embedded player is refused, it falls back to
 * the response the watch page itself carries. The values YouTube's player computes for each
 * stream are computed by YouTube's own current player script through a [PlayerScriptRunner];
 * without one, such streams fail as [SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED].
 *
 * There is no DRM handling, no age or content-gate acknowledgement, no proof-of-origin token and
 * no device impersonation. The user's cookie is sent only to YouTube's own pages, never to the
 * embedded player request and never to the media servers. A video the user cannot play fails
 * with a structured reason.
 */
class YouTubeExtractor(
    private val http: ExtractorHttpClient,
    private val playerScripts: PlayerScriptRunner = NoPlayerScriptRunner,
    private val maxPageBytes: Long = DEFAULT_MAX_PAGE_BYTES,
    private val maxPlayerBytes: Long = DEFAULT_MAX_PLAYER_BYTES,
) : SiteExtractor {
    override val id: String = YouTubeUrls.SITE_ID

    override val displayName: String = "YouTube"

    override fun identify(pageUrl: String): SitePageIdentity? = YouTubeUrls.identify(pageUrl)

    override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult {
        require(request.identity.siteId == id) { "This adapter only handles YouTube identities" }
        val videoId = request.identity.contentId
        if (!VIDEO_ID.matches(videoId)) {
            return SiteExtractionResult.Failure(SiteExtractionFailure.UNSUPPORTED_URL)
        }
        val pageUrl = YouTubeUrls.canonicalUrl(videoId)

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
        val lookup = Lookup(videoId, pageUrl, signals, request)
        val inline = signals.playerResponseJson
            ?.let { YouTubePlayerResponseParser.parse(it, request.nowEpochMs) }

        // A verdict about the video itself, such as private, removed or age-gated, ends the
        // lookup: no other client is asked to unlock what the user's own session was refused.
        if (inline is YouTubeParseResult.Failure && inline.definite) {
            return SiteExtractionResult.Failure(inline.reason)
        }

        val verdicts = Verdicts()

        // The embedded player is asked first because its links need no proof-of-origin token.
        // Its refusals describe the embed rather than the video, so they are never final here.
        val embedded = askPlayer(YouTubeClientProfiles.embedded(signals), lookup)
        attempt(Tier.FALLBACK, embedded, lookup, verdicts)?.let { return it }

        val own = inline ?: askPlayer(YouTubeClientProfiles.page(signals), lookup)
        if (own is YouTubeParseResult.Failure && own.definite) {
            return SiteExtractionResult.Failure(own.reason)
        }
        attempt(Tier.PAGE, own, lookup, verdicts)?.let { return it }

        return SiteExtractionResult.Failure(verdicts.final())
    }

    /** Turns one player response into candidates, or records why it produced none. */
    private suspend fun attempt(
        tier: Tier,
        parsed: YouTubeParseResult,
        lookup: Lookup,
        verdicts: Verdicts,
    ): SiteExtractionResult.Success? {
        when (parsed) {
            is YouTubeParseResult.Failure -> verdicts.record(tier, parsed.reason)
            is YouTubeParseResult.Success -> when (val delivery = deliver(parsed.video, lookup)) {
                is Delivery.Ready -> return SiteExtractionResult.Success(delivery.candidates)
                is Delivery.Blocked -> verdicts.record(Tier.DELIVERY, delivery.reason)
            }
        }
        return null
    }

    private suspend fun askPlayer(
        client: YouTubeClientProfile,
        lookup: Lookup,
    ): YouTubeParseResult {
        val signals = lookup.signals
        val result = http.postJson(
            url = YouTubeUrls.innerTubeUrl(signals.apiKey),
            body = client.playerRequestBody(
                videoId = lookup.videoId,
                visitorData = signals.visitorData,
                signatureTimestamp = signals.signatureTimestamp,
            ),
            headers = playerHeaders(client, lookup),
            maxBodyBytes = maxPlayerBytes,
        )
        return when (result) {
            is ExtractorHttpResult.Failure -> YouTubeParseResult.Failure(
                reason = result.reason,
                definite = false,
            )

            is ExtractorHttpResult.Success ->
                YouTubePlayerResponseParser.parse(result.body, lookup.nowEpochMs)
        }
    }

    private suspend fun deliver(video: YouTubeVideo, lookup: Lookup): Delivery {
        val selected = select(video)
        if (selected.isEmpty()) return Delivery.Blocked(SiteExtractionFailure.NO_MEDIA_FOUND)

        val prepared = selected.mapNotNull(::prepare)
        if (prepared.isEmpty()) return Delivery.Blocked(SiteExtractionFailure.RESPONSE_CHANGED)

        // Expired links are dropped before any script runs, so a stale page costs no work.
        val fresh = prepared
            .map { pending -> pending to expiryOf(pending, video) }
            .filter { (_, expiry) -> expiry == null || expiry > lookup.nowEpochMs }
        if (fresh.isEmpty()) return Delivery.Blocked(SiteExtractionFailure.EXPIRED_LINK)

        val challenges = fresh.flatMap { (pending, _) -> pending.challenges() }
        val solved = if (challenges.isEmpty()) {
            emptyMap()
        } else {
            when (val outcome = solve(challenges, lookup)) {
                is Solved.Values -> outcome.values
                is Solved.Refused -> return Delivery.Blocked(outcome.reason)
            }
        }

        val candidates = fresh.mapNotNull { (pending, expiry) ->
            finalUrl(pending, solved)
                ?.let { url -> candidate(pending.stream, url, expiry, video, lookup) }
        }
        if (candidates.isEmpty()) {
            return Delivery.Blocked(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED)
        }
        return Delivery.Ready(candidates)
    }

    /**
     * Picks the streams a single download can play.
     *
     * Progressive MP4 streams already carry both tracks. One AAC audio stream is offered as an
     * audio-only download: the original mix when YouTube marks one, never the volume-compressed
     * variant. Video-only adaptive streams are not offered, because a download of one would be
     * silent.
     */
    private fun select(video: YouTubeVideo): List<YouTubeStream> {
        val progressive = video.progressive
            .filter { it.hasVideo && it.hasAudio && it.mimeType == VIDEO_MP4 }
            .distinctBy(YouTubeStream::itag)
            .sortedByDescending { it.height ?: 0 }
        val audio = video.adaptive
            .filter { stream ->
                !stream.hasVideo && stream.mimeType == AUDIO_MP4 && !stream.isDrc &&
                    stream.codecs.any { it.startsWith(AAC_CODEC_PREFIX) }
            }
            .filter { stream -> progressive.none { it.itag == stream.itag } }
            .maxWithOrNull(
                compareBy<YouTubeStream>({ it.isDefaultAudio != false }, { it.bitrate ?: 0L }),
            )
        return progressive + listOfNotNull(audio)
    }

    /**
     * Splits a stream into the address and the values YouTube's player computes for it.
     *
     * A protected descriptor is a form-encoded document holding the media address, the value
     * the player must transform and the parameter that carries the result. Only HTTPS media
     * addresses are accepted.
     */
    private fun prepare(stream: YouTubeStream): PendingStream? {
        val descriptor = stream.protectedDescriptor
        if (descriptor == null) {
            val url = stream.url ?: return null
            return PendingStream(
                stream = stream,
                baseUrl = url,
                signatureInput = null,
                signatureParam = DEFAULT_SIGNATURE_PARAM,
                rateInput = rateInput(url),
            )
        }
        val fields = formFields(descriptor)
        val url = fields["url"]?.takeIf { it.startsWith("https://") } ?: return null
        val signature = fields["s"]?.takeIf(String::isNotBlank) ?: return null
        val param = fields["sp"]?.takeIf(SIGNATURE_PARAM::matches) ?: DEFAULT_SIGNATURE_PARAM
        return PendingStream(
            stream = stream,
            baseUrl = url,
            signatureInput = signature,
            signatureParam = param,
            rateInput = rateInput(url),
        )
    }

    private fun rateInput(url: String): String? = YouTubeUrls.queryParamOf(url, RATE_PARAM)
        ?.let(::decode)
        ?.takeIf(String::isNotBlank)

    /** The earliest of the address's own expiry and the expiry YouTube stated for the response. */
    private fun expiryOf(pending: PendingStream, video: YouTubeVideo): Long? {
        val stated = YouTubeUrls.queryParamOf(pending.baseUrl, EXPIRE_PARAM)
            ?.takeIf { it.length <= MAX_EXPIRE_DIGITS && it.all(Char::isDigit) }
            ?.toLong()
            ?.times(MILLIS_PER_SECOND)
        return listOfNotNull(stated, video.expiresAtEpochMs).minOrNull()
    }

    private suspend fun solve(challenges: List<PlayerScriptChallenge>, lookup: Lookup): Solved {
        val playerId = lookup.signals.playerId
        if (!playerScripts.isAvailable || playerId == null) {
            return Solved.Refused(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED)
        }
        val result = playerScripts.resolve(
            PlayerScriptRequest(
                playerScriptUrl = YouTubeUrls.playerScriptUrl(playerId),
                pageUrl = lookup.pageUrl,
                challenges = challenges,
            ),
        )
        return when (result) {
            is PlayerScriptResult.Success -> Solved.Values(result.resolved)
            PlayerScriptResult.Unavailable ->
                Solved.Refused(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED)

            is PlayerScriptResult.Failed -> Solved.Refused(result.reason)
        }
    }

    /**
     * Applies the computed values, or returns null when one is missing or implausible.
     *
     * A stream whose values did not come back is dropped rather than shipped with an address
     * YouTube's own player would never request.
     */
    private fun finalUrl(pending: PendingStream, solved: Map<String, String>): String? {
        var url = pending.baseUrl
        pending.rateInput?.let { input ->
            val output = solved[pending.rateKey]
                ?.takeIf { isPlausibleRate(input, it) }
                ?: return null
            url = YouTubeUrls.replaceQueryParam(url, RATE_PARAM, output)
        }
        pending.signatureInput?.let {
            val output = solved[pending.signatureKey]
                ?.takeIf(::isPlausibleSignature)
                ?: return null
            url = YouTubeUrls.appendQueryParam(url, pending.signatureParam, output)
        }
        return url
    }

    /**
     * A transformed rate parameter differs from its input and stays in the URL-safe alphabet.
     *
     * YouTube's player falls back to an `enhanced_except_` marker when its own transform throws,
     * which is a failure, not a value.
     */
    private fun isPlausibleRate(input: String, output: String): Boolean =
        output != input && RATE_OUTPUT.matches(output) && !output.startsWith(FAILED_RATE_PREFIX)

    private fun isPlausibleSignature(output: String): Boolean =
        output.length in MIN_SIGNATURE_LENGTH..MAX_SIGNATURE_LENGTH &&
            output.none(Char::isWhitespace)

    private fun candidate(
        stream: YouTubeStream,
        mediaUrl: String,
        expiresAtEpochMs: Long?,
        video: YouTubeVideo,
        lookup: Lookup,
    ): MediaCandidate = MediaCandidate(
        pageUrl = lookup.pageUrl,
        mediaUrl = mediaUrl,
        sources = setOf(CandidateSource.MANIFEST),
        kind = MediaKind.DIRECT,
        mimeType = stream.mimeType ?: if (stream.hasVideo) VIDEO_MP4 else AUDIO_MP4,
        title = displayTitle(video, stream),
        thumbnailUrl = video.thumbnailUrl,
        durationMillis = video.durationMillis,
        contentLengthBytes = stream.contentLengthBytes,
        // YouTube's cookie belongs to YouTube's pages. The media servers are a different origin
        // and never receive it.
        requestContext = BrowserRequestContext(
            pageUrl = lookup.pageUrl,
            userAgent = lookup.request.requestContext.userAgent,
            cookie = null,
        ),
        confidence = CandidateConfidence.HIGH,
        expiresAtEpochMs = expiresAtEpochMs,
        drmHint = false,
        observedAtEpochMs = lookup.nowEpochMs,
    )

    /** Title stays metadata-only: the video title, then what this download contains. */
    private fun displayTitle(video: YouTubeVideo, stream: YouTubeStream): String {
        val base = video.title ?: video.author?.let { "$it on YouTube" } ?: "YouTube video"
        val label = if (stream.hasVideo) {
            stream.qualityLabel ?: stream.height?.let { "${it}p" }
        } else {
            stream.bitrate?.takeIf { it > 0 }
                ?.let { "Audio ${(it + HALF_KILOBIT) / BITS_PER_KILOBIT} kbps" }
                ?: "Audio"
        }
        return if (label == null) base else "$base — $label"
    }

    private fun pageHeaders(context: BrowserRequestContext): Map<String, String> = buildMap {
        context.userAgent?.takeIf(String::isNotBlank)?.let { put("User-Agent", it) }
        context.cookie?.takeIf(String::isNotBlank)?.let { put("Cookie", it) }
        put("Accept-Language", ACCEPT_LANGUAGE)
    }

    /**
     * Headers for the player request, matching what YouTube's own player sends.
     *
     * The embedded player is asked without the user's cookie, so a public embed request never
     * carries the account identity.
     */
    private fun playerHeaders(
        client: YouTubeClientProfile,
        lookup: Lookup,
    ): Map<String, String> = buildMap {
        val context = lookup.request.requestContext
        context.userAgent?.takeIf(String::isNotBlank)?.let { put("User-Agent", it) }
        if (client.replaysSession) {
            context.cookie?.takeIf(String::isNotBlank)?.let { put("Cookie", it) }
        }
        put("Accept", "application/json")
        put("Accept-Language", ACCEPT_LANGUAGE)
        put("Origin", YOUTUBE_ORIGIN)
        put(
            "Referer",
            if (client.thirdPartyEmbedUrl != null) {
                YouTubeUrls.embedUrl(lookup.videoId)
            } else {
                lookup.pageUrl
            },
        )
        put("X-YouTube-Client-Name", client.clientNameId.toString())
        put("X-YouTube-Client-Version", client.clientVersion)
        lookup.signals.visitorData?.takeIf(String::isNotBlank)
            ?.let { put("X-Goog-Visitor-Id", it) }
    }

    private fun formFields(descriptor: String): Map<String, String> = buildMap {
        descriptor.split('&').forEach { pair ->
            val separator = pair.indexOf('=')
            if (separator > 0) {
                val name = pair.substring(0, separator)
                val value = decode(pair.substring(separator + 1))
                if (value != null && name !in this) put(name, value)
            }
        }
    }

    private fun decode(value: String): String? =
        runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrNull()

    private class Lookup(
        val videoId: String,
        val pageUrl: String,
        val signals: YouTubePageSignals,
        val request: SiteExtractionRequest,
    ) {
        val nowEpochMs: Long
            get() = request.nowEpochMs
    }

    private class PendingStream(
        val stream: YouTubeStream,
        val baseUrl: String,
        val signatureInput: String?,
        val signatureParam: String,
        val rateInput: String?,
    ) {
        val signatureKey: String = "sig-${stream.itag}"
        val rateKey: String = "n-${stream.itag}"

        fun challenges(): List<PlayerScriptChallenge> = listOfNotNull(
            signatureInput?.let {
                PlayerScriptChallenge(signatureKey, PlayerScriptChallengeKind.SIGNATURE, it)
            },
            rateInput?.let {
                PlayerScriptChallenge(rateKey, PlayerScriptChallengeKind.RATE_PARAM, it)
            },
        )

        /** Addresses carry session-bound values, so they never print. */
        override fun toString(): String = "PendingStream(itag=${stream.itag})"
    }

    private sealed interface Delivery {
        class Ready(val candidates: List<MediaCandidate>) : Delivery

        class Blocked(val reason: SiteExtractionFailure) : Delivery
    }

    private sealed interface Solved {
        class Values(val values: Map<String, String>) : Solved

        class Refused(val reason: SiteExtractionFailure) : Solved
    }

    /** Where a failure came from, in increasing order of how much it says about the video. */
    private enum class Tier {
        /** The embedded player, which refuses videos that merely disallow embedding. */
        FALLBACK,

        /** The watch page's own client, which sees what the user's browser sees. */
        PAGE,

        /** A response with streams that still could not become a download. */
        DELIVERY,
    }

    /**
     * Keeps the most informative failure seen so far.
     *
     * A failure from a later, more authoritative source wins; within one source, a specific
     * reason wins over changed markup, so the user is told the real reason.
     */
    private class Verdicts {
        private var tier: Tier? = null
        private var reason: SiteExtractionFailure? = null

        fun record(from: Tier, failure: SiteExtractionFailure) {
            val currentTier = tier
            val currentReason = reason
            val wins = currentTier == null || currentReason == null || from > currentTier ||
                (from == currentTier && rank(failure) > rank(currentReason))
            if (wins) {
                tier = from
                reason = failure
            }
        }

        fun final(): SiteExtractionFailure = reason ?: SiteExtractionFailure.RESPONSE_CHANGED

        private fun rank(failure: SiteExtractionFailure): Int = when (failure) {
            SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED,
            SiteExtractionFailure.EXPIRED_LINK,
            SiteExtractionFailure.RATE_LIMITED,
            -> 3

            SiteExtractionFailure.LOGIN_REQUIRED,
            SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
            SiteExtractionFailure.GEO_RESTRICTED,
            SiteExtractionFailure.DRM_PROTECTED,
            -> 2

            SiteExtractionFailure.NO_MEDIA_FOUND,
            SiteExtractionFailure.NETWORK,
            SiteExtractionFailure.HTTP_STATUS,
            SiteExtractionFailure.RESPONSE_TOO_LARGE,
            -> 1

            else -> 0
        }
    }

    companion object {
        const val DEFAULT_MAX_PAGE_BYTES: Long = 4L * 1024 * 1024
        const val DEFAULT_MAX_PLAYER_BYTES: Long = 2L * 1024 * 1024

        private const val YOUTUBE_ORIGIN = "https://www.youtube.com"
        private const val ACCEPT_LANGUAGE = "en-US,en;q=0.9"
        private const val VIDEO_MP4 = "video/mp4"
        private const val AUDIO_MP4 = "audio/mp4"
        private const val AAC_CODEC_PREFIX = "mp4a."
        private const val RATE_PARAM = "n"
        private const val EXPIRE_PARAM = "expire"
        private const val DEFAULT_SIGNATURE_PARAM = "signature"
        private const val FAILED_RATE_PREFIX = "enhanced_except"
        private const val MIN_SIGNATURE_LENGTH = 8
        private const val MAX_SIGNATURE_LENGTH = 512
        private const val MAX_EXPIRE_DIGITS = 12
        private const val MILLIS_PER_SECOND = 1_000L
        private const val BITS_PER_KILOBIT = 1_000L
        private const val HALF_KILOBIT = 500L

        private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")
        private val SIGNATURE_PARAM = Regex("^[A-Za-z]{1,16}$")
        private val RATE_OUTPUT = Regex("^[A-Za-z0-9_-]{2,128}$")
    }
}
