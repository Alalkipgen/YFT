package com.alal.yft.extractor.sites.youtube

import com.alal.yft.core.model.logging.DiagnosticTextSanitizer
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.CompanionAudio
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageNavigationHeaders
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.NoPlayerScriptRunner
import com.alal.yft.extractor.api.NoPoTokenProvider
import com.alal.yft.extractor.api.PlayerScriptChallenge
import com.alal.yft.extractor.api.PlayerScriptChallengeKind
import com.alal.yft.extractor.api.PlayerScriptRequest
import com.alal.yft.extractor.api.PlayerScriptResult
import com.alal.yft.extractor.api.PlayerScriptRunner
import com.alal.yft.extractor.api.PoTokenProvider
import com.alal.yft.extractor.api.PoTokenRequest
import com.alal.yft.extractor.api.PoTokenResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.SitePageIdentity
import java.net.URLDecoder

/**
 * YouTube adapter for offline copies of videos the user can watch.
 *
 * It reads the watch page the user's browser receives, then asks YouTube's player endpoint as a
 * chain of clients, the order the owner chose in ADR-006 (D2 = A + B + C):
 *
 * 1. YouTube's own visionOS and Android apps (option B), whose streams carry direct addresses;
 * 2. YouTube's embedded player, for videos their owners allow to be embedded;
 * 3. the client the watch page itself runs, with the user's own session and, on its media
 *    requests, the proof-of-origin token YouTube's web player would mint (option A);
 * 4. YouTube's mobile site, when the page was the desktop site, which often streams only
 *    through YouTube's SABR protocol.
 *
 * Downloads found along the way are combined, and the chain stops once it has a video with
 * sound and an audio track. Higher qualities, which YouTube serves only as separate video and
 * audio files, are offered as 480p, 720p and 1080p AVC rows that carry their AAC audio track
 * and are merged into one MP4 on the phone (T17). When a lookup meets YouTube's bot check, the
 * user plays the video in YFT's browser and tries again there, so the lookup carries the
 * browser's own session (option C). The values YouTube's player computes for a stream are
 * computed by YouTube's own
 * current player script through a [PlayerScriptRunner]; without one, such streams fail as
 * [SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED].
 *
 * There is no DRM handling and no age-check bypass: a verdict about the video from the page's
 * own client, such as private or age-restricted, ends the lookup before any other client is
 * asked, and an age check from any other client leaves the answer to the user's own session.
 * The user's cookie goes only to YouTube's own pages and to the clients that act for the page,
 * never to a device or embedded client and never to the media servers.
 *
 * Every result carries lookup details: the watch page fetch and, for each client asked, its
 * name, YouTube's verdict and reason, how many formats had addresses and whether YouTube offered
 * only its SABR protocol. They hold no address, cookie, token or visitor data.
 */
class YouTubeExtractor(
    private val http: ExtractorHttpClient,
    private val playerScripts: PlayerScriptRunner = NoPlayerScriptRunner,
    private val poTokens: PoTokenProvider = NoPoTokenProvider,
    private val maxPageBytes: Long = DEFAULT_MAX_PAGE_BYTES,
    private val maxPlayerBytes: Long = DEFAULT_MAX_PLAYER_BYTES,
) : SiteExtractor {
    override val id: String = YouTubeUrls.SITE_ID

    override val displayName: String = "YouTube"

    override fun identify(pageUrl: String): SitePageIdentity? = YouTubeUrls.identify(pageUrl)

    override fun isPlayerMediaRequest(requestUrl: String): Boolean =
        YouTubeUrls.isMediaServerRequest(requestUrl)

    override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult {
        require(request.identity.siteId == id) { "This adapter only handles YouTube identities" }
        val videoId = request.identity.contentId
        if (!VIDEO_ID.matches(videoId)) {
            return SiteExtractionResult.Failure(SiteExtractionFailure.UNSUPPORTED_URL)
        }
        val pageUrl = YouTubeUrls.canonicalUrl(videoId)

        val page = when (
            val result = http.get(
                url = YouTubeUrls.watchPageFetchUrl(videoId),
                headers = pageHeaders(request.requestContext),
                maxBodyBytes = maxPageBytes,
            )
        ) {
            is ExtractorHttpResult.Failure -> return SiteExtractionResult.Failure(
                reason = result.reason,
                httpStatusCode = result.statusCode,
                details = listOf("watch page GET failed (${result.reason})"),
            )

            is ExtractorHttpResult.Success -> result
        }

        val signals = YouTubePlayerResponseParser.pageSignals(page.body)
        val lookup = Lookup(videoId, pageUrl, signals, request)
        lookup.details += "watch page GET ${page.statusCode} (${page.body.length} characters)"
        val pageClient = YouTubeClientProfiles.page(signals)
        val inlineLabel = "client ${pageClient.clientName} (watch page response)"
        val inline = signals.playerResponseJson?.let { json ->
            val response = YouTubePlayerResponseParser.inspect(json, request.nowEpochMs)
            lookup.details += response.summary.lines(inlineLabel)
            response.result
        }
        if (inline == null) lookup.details += "watch page: no inline player response"

        // The page's own client sees what the user's browser sees, so without an inline answer
        // it is asked first. Its verdict about the video itself, such as private, removed or
        // age-restricted, ends the lookup: no other client is asked to unlock what the user's
        // own session was refused.
        val own = inline ?: askPlayer(pageClient, lookup)
        if (own is YouTubeParseResult.Failure && own.definite) {
            return lookup.failure(own.reason)
        }
        val ownLabel = if (inline != null) inlineLabel else pageClient.label

        if (askFallbacks(signals, lookup)) return lookup.success()

        collect(Tier.PAGE, pageClient, own, ownLabel, lookup)
        if (lookup.offers.hasVideo) return lookup.success()

        if (pageClient.clientName != MOBILE_CLIENT_NAME) {
            val mobile = YouTubeClientProfiles.mobileWeb()
            collect(Tier.PAGE, mobile, askPlayer(mobile, lookup), mobile.label, lookup)
        }
        return if (lookup.offers.isEmpty) {
            lookup.failure(lookup.verdicts.final())
        } else {
            lookup.success()
        }
    }

    /**
     * Asks the device clients, then the embedded player, and returns whether that was enough.
     *
     * Device clients answer with direct addresses that need neither the player script nor a
     * token; they are asked until the lookup has a video with sound and an audio track. The
     * embedded player ends this part once there is any video. What these clients refuse
     * describes the client, so it is never final. An age check is the exception: only the
     * user's own session may answer it, so no further client is asked to get around it and
     * what earlier clients offered is dropped.
     */
    private suspend fun askFallbacks(signals: YouTubePageSignals, lookup: Lookup): Boolean {
        val clients = YouTubeClientProfiles.DEVICE_CLIENTS +
            YouTubeClientProfiles.embedded(signals)
        for (client in clients) {
            val parsed = askPlayer(client, lookup)
            collect(Tier.FALLBACK, client, parsed, client.label, lookup)
            if (parsed is YouTubeParseResult.Failure && parsed.ageCheck) {
                lookup.offers.clear()
                lookup.details += "${client.label}: age check, so only the user's session is asked"
                return false
            }
            val enough = if (client.device != null) {
                lookup.offers.isComplete
            } else {
                lookup.offers.hasVideo
            }
            if (enough) return true
        }
        return false
    }

    /** Adds one client's downloads to the lookup, or records why it produced none. */
    private suspend fun collect(
        tier: Tier,
        client: YouTubeClientProfile,
        parsed: YouTubeParseResult,
        label: String,
        lookup: Lookup,
    ) {
        if (parsed is YouTubeParseResult.Failure) {
            lookup.verdicts.record(tier, parsed.reason)
            return
        }
        val video = (parsed as YouTubeParseResult.Success).video
        when (val delivery = deliver(video, client, lookup)) {
            is Delivery.Ready -> {
                val added = lookup.offers.add(delivery.offers)
                val noun = if (added == 1) "download" else "downloads"
                lookup.details += "$label: $added $noun offered"
            }

            // Streams that existed but could not become downloads say the most about this
            // video; an answer without a usable stream describes only this client.
            is Delivery.Blocked -> {
                lookup.details += "$label: no download (${delivery.reason})"
                lookup.verdicts.record(
                    if (delivery.streamsFound) Tier.DELIVERY else tier,
                    delivery.reason,
                )
            }
        }
    }

    /** Asks one client and records what it answered, never the answer itself. */
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
                poToken = if (client.usesPoToken) poToken(lookup, TokenUse.PLAYER) else null,
            ),
            headers = playerHeaders(client, lookup),
            maxBodyBytes = maxPlayerBytes,
        )
        return when (result) {
            is ExtractorHttpResult.Failure -> {
                val status = result.statusCode?.let { " (HTTP $it)" }.orEmpty()
                lookup.details += "${client.label}: ${result.reason}$status"
                YouTubeParseResult.Failure(reason = result.reason, definite = false)
            }

            is ExtractorHttpResult.Success -> {
                val response = YouTubePlayerResponseParser.inspect(result.body, lookup.nowEpochMs)
                lookup.details += response.summary.lines(client.label)
                response.result
            }
        }
    }

    private suspend fun deliver(
        video: YouTubeVideo,
        client: YouTubeClientProfile,
        lookup: Lookup,
    ): Delivery {
        val selected = select(video)
        if (selected.isEmpty()) {
            return Delivery.Blocked(SiteExtractionFailure.NO_MEDIA_FOUND, streamsFound = false)
        }

        val prepared = selected.mapNotNull { stream -> prepare(stream, client) }
        if (prepared.isEmpty()) {
            return Delivery.Blocked(SiteExtractionFailure.RESPONSE_CHANGED, streamsFound = false)
        }

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

        val addresses = fresh.mapNotNull { (pending, expiry) ->
            finalUrl(pending, solved)?.let { url -> Triple(pending, url, expiry) }
        }
        if (addresses.isEmpty()) {
            return Delivery.Blocked(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED)
        }
        // The token is minted only once there is something to attach it to.
        val token = if (client.usesPoToken) poToken(lookup, TokenUse.MEDIA) else null
        val tracks = addresses.map { (pending, url, expiry) ->
            val mediaUrl = token?.let { YouTubeUrls.appendQueryParam(url, POT_PARAM, it) } ?: url
            Triple(pending.stream, mediaUrl, expiry)
        }
        // Video-only streams are downloaded with this answer's audio track and merged on the
        // phone; without that track they would be silent, so they are dropped.
        val companion = tracks.firstOrNull { (stream, _, _) -> !stream.hasVideo }
            ?.let { (stream, url, expiry) -> companionAudio(stream, url, expiry, lookup) }
        val offers = tracks.mapNotNull { (stream, url, expiry) ->
            if (stream.hasVideo && !stream.hasAudio) {
                companion?.let { audio ->
                    Offer(stream, candidate(stream, url, expiry, video, lookup, audio))
                }
            } else {
                Offer(stream, candidate(stream, url, expiry, video, lookup))
            }
        }
        return Delivery.Ready(offers)
    }

    /**
     * The proof-of-origin token for one use, minted at most once per binding and lookup.
     *
     * The player request's token is bound to the video. Media requests follow YouTube's own
     * rule, read from the page: bound to the video when the page says so, otherwise to the
     * signed-in account's data-sync identifier, or to the visitor data when signed out.
     * Without a token the lookup continues, because YouTube still accepts some media requests
     * without one; the details say why there was none, never the token.
     */
    private suspend fun poToken(lookup: Lookup, use: TokenUse): String? {
        val signals = lookup.signals
        val (binding, label) = when {
            use == TokenUse.PLAYER || signals.contentBoundPoToken -> lookup.videoId to "video"
            signals.loggedIn == true -> signals.dataSyncId to "account"
            else -> signals.visitorData to "visitor"
        }
        if (binding.isNullOrBlank()) {
            lookup.details += "proof of origin ($label): nothing to bind to"
            return null
        }
        lookup.mintedTokens[binding]?.let { return it.token }
        val playerId = signals.playerId
        val token = when {
            !poTokens.isAvailable ->
                null.also { lookup.details += "proof of origin ($label): no host" }
            playerId == null -> null.also {
                lookup.details += "proof of origin ($label): no player version"
            }

            else -> when (
                val result = poTokens.mint(
                    PoTokenRequest(
                        contentBinding = binding,
                        playerScriptUrl = YouTubeUrls.playerScriptUrl(playerId),
                        pageUrl = lookup.pageUrl,
                    ),
                )
            ) {
                is PoTokenResult.Minted -> result.token.takeIf(POT_VALUE::matches).also { valid ->
                    lookup.details += if (valid != null) {
                        "proof of origin ($label): minted"
                    } else {
                        "proof of origin ($label): unusable"
                    }
                }

                PoTokenResult.Unavailable ->
                    null.also { lookup.details += "proof of origin ($label): unavailable" }

                is PoTokenResult.Failed -> null.also {
                    lookup.details += "proof of origin ($label): failed (${result.reason})"
                }
            }
        }
        lookup.mintedTokens[binding] = MintedToken(token)
        return token
    }

    /**
     * Picks the streams downloads are made of.
     *
     * Progressive MP4 streams already carry both tracks. One AAC audio stream is offered as an
     * audio-only download: the original mix when YouTube marks one, never the volume-compressed
     * variant. The same audio track is merged on the phone with the best video-only AVC stream
     * of each of [MERGED_QUALITIES] that no progressive stream already offers. VP9 and AV1 are
     * left out, because the phone's merge writes AVC and AAC only.
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
            ?: return progressive
        val offeredQualities = progressive.mapNotNull { it.quality() }.toSet()
        val merged = video.adaptive
            .filter { stream ->
                stream.hasVideo && !stream.hasAudio && stream.mimeType == VIDEO_MP4 &&
                    stream.codecs.isNotEmpty() &&
                    stream.codecs.all { it.startsWith(AVC_CODEC_PREFIX) }
            }
            .groupBy { it.quality() }
            .filterKeys { it in MERGED_QUALITIES && it !in offeredQualities }
            .values
            .mapNotNull { streams -> streams.maxByOrNull { it.bitrate ?: 0L } }
            .sortedByDescending { it.quality() }
        return progressive + merged + audio
    }

    /**
     * Splits a stream into the address and the values YouTube's player computes for it.
     *
     * A protected descriptor is a form-encoded document holding the media address, the value
     * the player must transform and the parameter that carries the result. Only HTTPS media
     * addresses are accepted. A client that runs no player script, such as a device client,
     * uses its addresses as YouTube sent them.
     */
    private fun prepare(stream: YouTubeStream, client: YouTubeClientProfile): PendingStream? {
        val descriptor = stream.protectedDescriptor
        if (descriptor == null) {
            val url = stream.url ?: return null
            return PendingStream(
                stream = stream,
                baseUrl = url,
                signatureInput = null,
                signatureParam = DEFAULT_SIGNATURE_PARAM,
                rateInput = if (client.usesPlayerScript) rateInput(url) else null,
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
        audioCompanion: CompanionAudio? = null,
    ): MediaCandidate = MediaCandidate(
        pageUrl = lookup.pageUrl,
        mediaUrl = mediaUrl,
        sources = setOf(CandidateSource.MANIFEST),
        kind = MediaKind.DIRECT,
        mimeType = stream.mimeType ?: if (stream.hasVideo) VIDEO_MP4 else AUDIO_MP4,
        title = displayTitle(video, stream),
        thumbnailUrl = video.thumbnailUrl,
        durationMillis = video.durationMillis,
        contentLengthBytes = if (audioCompanion == null) {
            stream.contentLengthBytes
        } else {
            stream.contentLengthBytes?.let { videoBytes ->
                audioCompanion.contentLengthBytes?.plus(videoBytes)
            }
        },
        requestContext = mediaContext(lookup),
        confidence = CandidateConfidence.HIGH,
        expiresAtEpochMs = listOfNotNull(expiresAtEpochMs, audioCompanion?.expiresAtEpochMs)
            .minOrNull(),
        drmHint = false,
        observedAtEpochMs = lookup.nowEpochMs,
        codecs = stream.codecs,
        audioCompanion = audioCompanion,
        // The picture YouTube states, so the download sheet never has to guess it (P3).
        width = stream.width?.takeIf { stream.hasVideo && it > 0 },
        height = stream.height?.takeIf { stream.hasVideo && it > 0 },
        framesPerSecond = stream.fps?.takeIf { stream.hasVideo }?.toDouble(),
        bitrateBitsPerSecond = stream.bitrate?.takeIf { it > 0 },
    )

    private fun companionAudio(
        stream: YouTubeStream,
        mediaUrl: String,
        expiresAtEpochMs: Long?,
        lookup: Lookup,
    ): CompanionAudio = CompanionAudio(
        mediaUrl = mediaUrl,
        mimeType = stream.mimeType ?: AUDIO_MP4,
        codecs = stream.codecs,
        requestContext = mediaContext(lookup),
        contentLengthBytes = stream.contentLengthBytes?.takeIf { it > 0 },
        bitrateBitsPerSecond = stream.bitrate?.takeIf { it > 0 },
        expiresAtEpochMs = expiresAtEpochMs,
    )

    /**
     * What the media servers receive. YouTube's cookie belongs to YouTube's pages; the media
     * servers are a different origin and never receive it.
     */
    private fun mediaContext(lookup: Lookup): BrowserRequestContext = BrowserRequestContext(
        pageUrl = lookup.pageUrl,
        userAgent = lookup.request.requestContext.userAgent,
        cookie = null,
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

    private fun pageHeaders(context: BrowserRequestContext): Map<String, String> =
        PageNavigationHeaders.withDefaults(
            buildMap {
                context.userAgent?.takeIf(String::isNotBlank)?.let { put("User-Agent", it) }
                context.cookie?.takeIf(String::isNotBlank)?.let { put("Cookie", it) }
                put("Accept-Language", ACCEPT_LANGUAGE)
            },
        )

    /**
     * Headers for the player request, matching what that client's own player sends.
     *
     * Device clients send their app's user agent and no referer. Only the clients that act for
     * the page carry the user's cookie, with the authorization YouTube's web player adds for a
     * signed-in session, so a device or embedded request never carries the account identity.
     */
    private fun playerHeaders(
        client: YouTubeClientProfile,
        lookup: Lookup,
    ): Map<String, String> = buildMap {
        val context = lookup.request.requestContext
        (client.userAgent ?: context.userAgent?.takeIf(String::isNotBlank))
            ?.let { put("User-Agent", it) }
        if (client.replaysSession) {
            context.cookie?.takeIf(String::isNotBlank)?.let { cookie ->
                put("Cookie", cookie)
                putAll(
                    YouTubeSessionAuth.headers(
                        cookie = cookie,
                        signals = lookup.signals,
                        nowEpochSeconds = lookup.nowEpochMs / MILLIS_PER_SECOND,
                    ),
                )
            }
        }
        put("Accept", "application/json")
        put("Accept-Language", ACCEPT_LANGUAGE)
        put("Origin", YOUTUBE_ORIGIN)
        if (client.device == null) {
            put(
                "Referer",
                if (client.thirdPartyEmbedUrl != null) {
                    YouTubeUrls.embedUrl(lookup.videoId)
                } else {
                    lookup.pageUrl
                },
            )
        }
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

    private val YouTubeClientProfile.label: String
        get() = "client $clientName"

    private class Lookup(
        val videoId: String,
        val pageUrl: String,
        val signals: YouTubePageSignals,
        val request: SiteExtractionRequest,
    ) {
        /** Steps in the order they happened; sanitized before they leave the adapter. */
        val details = mutableListOf<String>()

        val offers = Offers()

        val verdicts = Verdicts()

        /** Tokens by binding, kept once asked for, so one lookup mints each at most once. */
        val mintedTokens = mutableMapOf<String, MintedToken>()

        val nowEpochMs: Long
            get() = request.nowEpochMs

        fun safeDetails(): List<String> = DiagnosticTextSanitizer.details(details)

        fun success(): SiteExtractionResult.Success =
            SiteExtractionResult.Success(offers.candidates(), safeDetails())

        fun failure(reason: SiteExtractionFailure): SiteExtractionResult.Failure =
            SiteExtractionResult.Failure(reason, details = safeDetails())
    }

    /** A token, or the fact that none could be had, for the rest of one lookup. */
    private class MintedToken(val token: String?) {
        override fun toString(): String = "MintedToken(present=${token != null})"
    }

    /** What a token is attached to: the player request, or the media addresses. */
    private enum class TokenUse { PLAYER, MEDIA }

    /** One download and the stream it came from. */
    private class Offer(val stream: YouTubeStream, val candidate: MediaCandidate)

    /**
     * Downloads combined across clients.
     *
     * The first client to offer a progressive stream of a given format and the first to offer
     * an audio track win, so the order of the chain decides which address is shipped. A merged
     * row is kept only for a quality no other row offers: a progressive stream needs no merge
     * on the phone, so it wins over a merged row of the same quality from any client.
     */
    private class Offers {
        private val videos = LinkedHashMap<Int, Offer>()
        private var audio: Offer? = null

        /**
         * Whether some client offered a progressive video, which already has sound. Merged rows
         * do not count, so they never change which clients are asked.
         */
        val hasVideo: Boolean
            get() = videos.values.any { it.stream.hasAudio }

        /**
         * A video with sound and an audio track. The answer that brought the audio track also
         * brought the merged rows paired with it, so asking further clients adds nothing.
         */
        val isComplete: Boolean
            get() = hasVideo && audio != null

        val isEmpty: Boolean
            get() = videos.isEmpty() && audio == null

        /** Forgets everything offered so far. */
        fun clear() {
            videos.clear()
            audio = null
        }

        /** Adds what is new and returns how many downloads that was. */
        fun add(offers: List<Offer>): Int = offers.count { offer ->
            val stream = offer.stream
            when {
                stream.hasVideo && stream.hasAudio ->
                    videos.putIfAbsent(stream.itag, offer) == null

                stream.hasVideo -> {
                    val quality = stream.quality()
                    videos.values.none { it.stream.quality() == quality } &&
                        videos.putIfAbsent(stream.itag, offer) == null
                }

                audio == null -> {
                    audio = offer
                    true
                }

                else -> false
            }
        }

        fun candidates(): List<MediaCandidate> {
            val progressive = videos.values
                .filter { it.stream.hasAudio }
                .mapNotNull { it.stream.quality() }
                .toSet()
            return videos.values
                .filter { it.stream.hasAudio || it.stream.quality() !in progressive }
                .sortedWith(
                    compareByDescending<Offer> { it.stream.quality() ?: 0 }
                        .thenByDescending { it.stream.height ?: 0 },
                )
                .map(Offer::candidate) + listOfNotNull(audio?.candidate)
        }
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
        class Ready(val offers: List<Offer>) : Delivery

        /** [streamsFound] is false when the answer held no stream a download could use. */
        class Blocked(
            val reason: SiteExtractionFailure,
            val streamsFound: Boolean = true,
        ) : Delivery
    }

    private sealed interface Solved {
        class Values(val values: Map<String, String>) : Solved

        class Refused(val reason: SiteExtractionFailure) : Solved
    }

    /** Where a failure came from, in increasing order of how much it says about the video. */
    private enum class Tier {
        /** Device clients and the embedded player, whose refusals describe themselves. */
        FALLBACK,

        /** The clients that act for the page, which see what the user's browser sees. */
        PAGE,

        /** A response with streams that still could not become a download. */
        DELIVERY,
    }

    /**
     * Keeps the most informative failure seen so far.
     *
     * A failure from a later, more authoritative source wins; within one source, a specific
     * reason wins over changed markup, so the user is told the real reason. A bot check counts
     * as the page's own verdict wherever it came from, because playing the video in YFT's
     * browser is what the user can do about it.
     */
    private class Verdicts {
        private var tier: Tier? = null
        private var reason: SiteExtractionFailure? = null

        fun record(source: Tier, failure: SiteExtractionFailure) {
            val from = if (failure == SiteExtractionFailure.BOT_CHECK) {
                maxOf(source, Tier.PAGE)
            } else {
                source
            }
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
            -> 4

            SiteExtractionFailure.BOT_CHECK -> 3

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
        private const val MOBILE_CLIENT_NAME = "MWEB"
        private const val VIDEO_MP4 = "video/mp4"
        private const val AUDIO_MP4 = "audio/mp4"
        private const val AAC_CODEC_PREFIX = "mp4a."
        private const val AVC_CODEC_PREFIX = "avc1"

        /** Merged video-and-audio rows, by the quality YouTube names them (T17). */
        private val MERGED_QUALITIES = setOf(480, 720, 1080)
        private val QUALITY_LABEL = Regex("^(\\d{3,4})p")
        private const val RATE_PARAM = "n"
        private const val EXPIRE_PARAM = "expire"
        private const val POT_PARAM = "pot"
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

        /** A minted token is URL-safe base64; anything else is not attached to an address. */
        private val POT_VALUE = Regex("^[A-Za-z0-9_-]{16,4096}={0,2}$")

        /** The quality YouTube names a stream by: the number in its label, else its short side. */
        private fun YouTubeStream.quality(): Int? =
            qualityLabel?.let(QUALITY_LABEL::find)?.groupValues?.get(1)?.toIntOrNull()
                ?: listOfNotNull(width, height).minOrNull()
    }
}
