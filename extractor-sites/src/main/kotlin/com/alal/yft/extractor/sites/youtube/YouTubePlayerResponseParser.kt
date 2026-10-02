package com.alal.yft.extractor.sites.youtube

import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asBooleanOrNull
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.api.json.path

/** Whether a stream URL is usable as read, or still needs the site's own player script. */
internal enum class YouTubeProtection {
    NONE,
    PLAYER_SCRIPT,
}

internal data class YouTubeStream(
    val itag: Int,
    val url: String?,
    /** Raw protected descriptor, kept opaque; only a player-script host interprets it. */
    val protectedDescriptor: String?,
    val protection: YouTubeProtection,
    val mimeType: String?,
    val codecs: String?,
    val qualityLabel: String?,
    val bitrate: Long?,
    val width: Int?,
    val height: Int?,
    val contentLengthBytes: Long?,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
) {
    /** Protected descriptors carry session-derived values, so they never print verbatim. */
    override fun toString(): String =
        "YouTubeStream(itag=$itag, protection=$protection, qualityLabel=$qualityLabel, " +
            "hasVideo=$hasVideo, hasAudio=$hasAudio)"
}

internal data class YouTubeVideo(
    val videoId: String?,
    val title: String?,
    val author: String?,
    val durationMillis: Long?,
    val thumbnailUrl: String?,
    /** Progressive streams already carry both tracks and need no muxing. */
    val progressive: List<YouTubeStream>,
    /** Adaptive streams carry one track each and need pairing before they are usable. */
    val adaptive: List<YouTubeStream>,
    val expiresAtEpochMs: Long?,
)

/** Signals the watch page exposes about its own player, read without executing any script. */
internal data class YouTubePageSignals(
    val playerResponseJson: String?,
    val playerScriptUrl: String?,
    val apiKey: String?,
    val clientVersion: String?,
    val visitorData: String?,
) {
    /** Visitor data identifies the session, so only its presence is printable. */
    override fun toString(): String =
        "YouTubePageSignals(playerResponse=${playerResponseJson != null}, " +
            "playerScriptUrl=$playerScriptUrl, apiKey=${apiKey != null}, " +
            "clientVersion=$clientVersion, visitorData=${visitorData != null})"
}

internal sealed interface YouTubeParseResult {
    data class Success(val video: YouTubeVideo) : YouTubeParseResult

    data class Failure(val reason: SiteExtractionFailure) : YouTubeParseResult
}

/**
 * Reads YouTube's player response.
 *
 * The same document shape is served inline in the watch page and returned by YouTube's own
 * player endpoint, so one parser covers both. It performs no script execution, no signature
 * computation and no DRM handling; a protected stream is reported as protected.
 */
internal object YouTubePlayerResponseParser {
    private const val PLAYER_RESPONSE_MARKER = "ytInitialPlayerResponse"
    private const val MAX_JSON_NODES = 400_000

    private val JS_URL_PATTERN = Regex("\"jsUrl\"\\s*:\\s*\"([^\"]{1,400})\"")
    private val API_KEY_PATTERN = Regex("\"INNERTUBE_API_KEY\"\\s*:\\s*\"([A-Za-z0-9_-]{10,80})\"")
    private val CLIENT_VERSION_PATTERN =
        Regex("\"INNERTUBE_CLIENT_VERSION\"\\s*:\\s*\"([0-9A-Za-z.\\-]{3,40})\"")
    private val VISITOR_DATA_PATTERN =
        Regex("\"visitorData\"\\s*:\\s*\"([A-Za-z0-9_\\-%=+/]{5,400})\"")

    fun pageSignals(html: String): YouTubePageSignals = YouTubePageSignals(
        playerResponseJson = slicePlayerResponse(html),
        playerScriptUrl = YouTubeUrls.playerScriptUrl(JS_URL_PATTERN.find(html)?.groupValues?.get(1)),
        apiKey = API_KEY_PATTERN.find(html)?.groupValues?.get(1),
        clientVersion = CLIENT_VERSION_PATTERN.find(html)?.groupValues?.get(1),
        visitorData = VISITOR_DATA_PATTERN.find(html)?.groupValues?.get(1),
    )

    /**
     * Slices the inline player response out of the page.
     *
     * The assignment is followed by minified JavaScript, so the object is located by brace
     * balance rather than by a terminator guess, and string contents are skipped so a brace
     * inside a title cannot end the slice early.
     */
    fun slicePlayerResponse(html: String): String? {
        val marker = html.indexOf(PLAYER_RESPONSE_MARKER)
        if (marker < 0) return null
        val start = html.indexOf('{', marker)
        if (start < 0) return null
        var depth = 0
        var index = start
        var inString = false
        var escaped = false
        while (index < html.length) {
            val character = html[index]
            when {
                escaped -> escaped = false
                inString && character == '\\' -> escaped = true
                character == '"' -> inString = !inString
                inString -> Unit
                character == '{' -> depth++
                character == '}' -> {
                    depth--
                    if (depth == 0) return html.substring(start, index + 1)
                }
            }
            index++
        }
        return null
    }

    fun parse(json: String, nowEpochMs: Long): YouTubeParseResult {
        val root = BoundedJsonParser.parse(json, maxNodes = MAX_JSON_NODES)
            ?: return YouTubeParseResult.Failure(SiteExtractionFailure.MALFORMED_RESPONSE)

        val status = root.path("playabilityStatus", "status").asStringOrNull
        val reasonText = buildString {
            append(root.path("playabilityStatus", "reason").asStringOrNull.orEmpty())
            append(' ')
            append(
                root.path("playabilityStatus", "errorScreen").toString()
                    .takeIf { status != null } ?: "",
            )
        }
        playabilityFailure(status, reasonText)?.let { return YouTubeParseResult.Failure(it) }

        val streamingData = root["streamingData"]
        if (streamingData == null) {
            // A status of OK with no streaming data means YouTube accepted the request but
            // withheld delivery, which is what its bot checks do on datacenter networks.
            return YouTubeParseResult.Failure(SiteExtractionFailure.LOGIN_REQUIRED)
        }
        if (isDrmProtected(root, streamingData)) {
            return YouTubeParseResult.Failure(SiteExtractionFailure.DRM_PROTECTED)
        }
        // A running live stream has no finite file to write, so it is reported as having no
        // downloadable media rather than being queued and never finishing.
        if (root.path("videoDetails", "isLiveContent").asBooleanOrNull == true &&
            root.path("videoDetails", "isLive").asBooleanOrNull == true
        ) {
            return YouTubeParseResult.Failure(SiteExtractionFailure.NO_MEDIA_FOUND)
        }

        val progressive = streamingData["formats"].asArrayOrEmpty.mapNotNull(::readStream)
        val adaptive = streamingData["adaptiveFormats"].asArrayOrEmpty.mapNotNull(::readStream)
        if (progressive.isEmpty() && adaptive.isEmpty()) {
            return YouTubeParseResult.Failure(SiteExtractionFailure.RESPONSE_CHANGED)
        }

        return YouTubeParseResult.Success(
            YouTubeVideo(
                videoId = root.path("videoDetails", "videoId").asStringOrNull,
                title = root.path("videoDetails", "title").asStringOrNull?.trim()
                    ?.takeIf(String::isNotEmpty),
                author = root.path("videoDetails", "author").asStringOrNull?.trim()
                    ?.takeIf(String::isNotEmpty),
                durationMillis = root.path("videoDetails", "lengthSeconds").asStringOrNull
                    ?.toLongOrNull()?.takeIf { it > 0 }?.times(1_000),
                thumbnailUrl = largestThumbnail(root),
                progressive = progressive,
                adaptive = adaptive,
                expiresAtEpochMs = expiry(streamingData, progressive + adaptive, nowEpochMs),
            ),
        )
    }

    /**
     * Maps YouTube's own playability verdict onto the closed failure set.
     *
     * Age and content gates are reported as unavailable instead of being acknowledged, so the
     * adapter never tells YouTube that a check it did not satisfy is satisfied.
     */
    private fun playabilityFailure(status: String?, reason: String): SiteExtractionFailure? =
        when (status) {
            null -> SiteExtractionFailure.RESPONSE_CHANGED
            "OK" -> null
            "LOGIN_REQUIRED" -> if (reason.contains("private", ignoreCase = true)) {
                SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE
            } else {
                SiteExtractionFailure.LOGIN_REQUIRED
            }

            "AGE_VERIFICATION_REQUIRED", "CONTENT_CHECK_REQUIRED" ->
                SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE

            "LIVE_STREAM_OFFLINE" -> SiteExtractionFailure.NO_MEDIA_FOUND
            "UNPLAYABLE", "ERROR" -> when {
                reason.contains("not available in your country", ignoreCase = true) ||
                    reason.contains("uploader has not made", ignoreCase = true) ->
                    SiteExtractionFailure.GEO_RESTRICTED

                else -> SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE
            }

            else -> SiteExtractionFailure.RESPONSE_CHANGED
        }

    private fun isDrmProtected(root: JsonValue?, streamingData: JsonValue?): Boolean {
        if (streamingData["drmParams"].asStringOrNull != null) return true
        if (root.path("playbackTracking", "drmSessionId").asStringOrNull != null) return true
        val formats = streamingData["formats"].asArrayOrEmpty +
            streamingData["adaptiveFormats"].asArrayOrEmpty
        return formats.any { format ->
            format["drmFamilies"].asArrayOrEmpty.isNotEmpty() ||
                format["drmTrackType"].asStringOrNull != null
        }
    }

    private fun readStream(format: JsonValue?): YouTubeStream? {
        val itag = format["itag"].asLongOrNull?.toInt() ?: return null
        val plainUrl = format["url"].asStringOrNull?.takeIf { it.startsWith("https://") }
        val descriptor = format["signatureCipher"].asStringOrNull
            ?: format["cipher"].asStringOrNull
        if (plainUrl == null && descriptor == null) return null

        val mimeType = format["mimeType"].asStringOrNull
        val codecs = mimeType?.substringAfter("codecs=\"", "")?.substringBefore('"')
            ?.takeIf(String::isNotEmpty)
        val bareMime = mimeType?.substringBefore(';')?.trim()?.takeIf(String::isNotEmpty)
        val hasVideo = format["height"].asLongOrNull != null ||
            format["qualityLabel"].asStringOrNull != null
        val hasAudio = format["audioQuality"].asStringOrNull != null ||
            format["audioSampleRate"].asStringOrNull != null ||
            format["audioChannels"].asLongOrNull != null

        return YouTubeStream(
            itag = itag,
            url = plainUrl,
            protectedDescriptor = descriptor,
            protection = if (plainUrl != null) {
                YouTubeProtection.NONE
            } else {
                YouTubeProtection.PLAYER_SCRIPT
            },
            mimeType = bareMime,
            codecs = codecs,
            qualityLabel = format["qualityLabel"].asStringOrNull
                ?: format["audioQuality"].asStringOrNull,
            bitrate = format["bitrate"].asLongOrNull,
            width = format["width"].asLongOrNull?.toInt(),
            height = format["height"].asLongOrNull?.toInt(),
            contentLengthBytes = format["contentLength"].asStringOrNull?.toLongOrNull()
                ?: format["contentLength"].asLongOrNull,
            hasVideo = hasVideo,
            hasAudio = hasAudio,
        )
    }

    private fun largestThumbnail(root: JsonValue?): String? = root
        .path("videoDetails", "thumbnail", "thumbnails")
        .asArrayOrEmpty
        .mapNotNull { entry ->
            val url = entry["url"].asStringOrNull?.takeIf { it.startsWith("https://") }
            val width = entry["width"].asLongOrNull ?: 0
            if (url == null) null else width to url
        }
        .maxByOrNull { it.first }
        ?.second

    /**
     * Reads the shortest expiry the response states.
     *
     * YouTube stamps every stream URL with an `expire` second, so a queued download that would
     * start after that point is rejected up front instead of failing mid-transfer.
     */
    private fun expiry(
        streamingData: JsonValue?,
        streams: List<YouTubeStream>,
        nowEpochMs: Long,
    ): Long? {
        val fromUrls = streams.mapNotNull { stream ->
            val query = stream.url?.substringAfter('?', "") ?: return@mapNotNull null
            YouTubeUrls.queryParam(query, "expire")?.toLongOrNull()?.times(1_000)
        }.minOrNull()
        val relative = streamingData["expiresInSeconds"].asStringOrNull?.toLongOrNull()
            ?: streamingData["expiresInSeconds"].asLongOrNull
        val fromRelative = relative?.takeIf { it > 0 }?.let { nowEpochMs + it * 1_000 }
        return listOfNotNull(fromUrls, fromRelative).minOrNull()
    }
}