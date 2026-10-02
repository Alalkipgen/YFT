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

internal data class YouTubeStream(
    val itag: Int,
    /** The address exactly as YouTube sent it, or null when only a protected descriptor exists. */
    val url: String?,
    /** Protected descriptor, kept opaque until the extractor splits it. */
    val protectedDescriptor: String?,
    val mimeType: String?,
    val codecs: List<String>,
    val qualityLabel: String?,
    val bitrate: Long?,
    val width: Int?,
    val height: Int?,
    val contentLengthBytes: Long?,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    /** Whether this is the original audio track; null when the response does not say. */
    val isDefaultAudio: Boolean?,
    /** Dynamic-range-compressed audio that YouTube offers next to the original mix. */
    val isDrc: Boolean,
) {
    val isProtected: Boolean
        get() = url == null

    /** Stream addresses and descriptors carry session-bound values, so they never print. */
    override fun toString(): String =
        "YouTubeStream(itag=$itag, protected=$isProtected, mimeType=$mimeType, " +
            "qualityLabel=$qualityLabel, hasVideo=$hasVideo, hasAudio=$hasAudio)"
}

internal data class YouTubeVideo(
    val videoId: String?,
    val title: String?,
    val author: String?,
    val durationMillis: Long?,
    val thumbnailUrl: String?,
    /** Progressive streams already carry both tracks and need no merge step. */
    val progressive: List<YouTubeStream>,
    /** Adaptive streams carry one track each. */
    val adaptive: List<YouTubeStream>,
    /** Expiry YouTube states for the whole response, independent of any one address. */
    val expiresAtEpochMs: Long?,
)

/** What the watch page states about its own player, read without executing any script. */
internal data class YouTubePageSignals(
    val playerResponseJson: String?,
    val playerId: String?,
    val apiKey: String?,
    val clientName: String?,
    val clientVersion: String?,
    val visitorData: String?,
    val signatureTimestamp: Int?,
) {
    /** Visitor data identifies the session, so only its presence is printable. */
    override fun toString(): String =
        "YouTubePageSignals(playerResponse=${playerResponseJson != null}, playerId=$playerId, " +
            "apiKey=${apiKey != null}, clientName=$clientName, clientVersion=$clientVersion, " +
            "visitorData=${visitorData != null}, signatureTimestamp=$signatureTimestamp)"
}

internal sealed interface YouTubeParseResult {
    data class Success(val video: YouTubeVideo) : YouTubeParseResult

    /**
     * A response that produced no streams.
     *
     * [definite] is true when the verdict describes the video itself, such as a private,
     * removed, region-locked, age-gated or DRM-protected video, rather than this one request.
     */
    data class Failure(
        val reason: SiteExtractionFailure,
        val definite: Boolean,
    ) : YouTubeParseResult
}

/**
 * Reads YouTube's player response.
 *
 * The same document shape is served inline in the watch page and returned by YouTube's own
 * player endpoint, so one parser covers both. It performs no script execution, no signature
 * computation and no DRM handling; a protected stream is reported as protected.
 */
internal object YouTubePlayerResponseParser {
    private const val MAX_JSON_NODES = 400_000
    private const val MAX_REASON_DEPTH = 8
    private const val MAX_REASON_PARTS = 32

    private val PLAYER_RESPONSE_ASSIGNMENT =
        Regex("ytInitialPlayerResponse[\"']?]?\\s*=\\s*\\{")
    private val JS_URL = Regex("\"(?:jsUrl|PLAYER_JS_URL)\"\\s*:\\s*\"([^\"]{1,400})\"")
    private val API_KEY = Regex("\"INNERTUBE_API_KEY\"\\s*:\\s*\"([A-Za-z0-9_-]{10,80})\"")
    private val CLIENT_NAME = Regex("\"INNERTUBE_CLIENT_NAME\"\\s*:\\s*\"([A-Z_]{2,40})\"")
    private val CLIENT_VERSION =
        Regex("\"INNERTUBE_CLIENT_VERSION\"\\s*:\\s*\"([0-9.]{3,40})\"")
    private val VISITOR_DATA =
        Regex("\"(?:VISITOR_DATA|visitorData)\"\\s*:\\s*\"([A-Za-z0-9_\\-%=+/]{5,2000})\"")
    private val SIGNATURE_TIMESTAMP =
        Regex("\"(?:STS|signatureTimestamp)\"\\s*:\\s*(\\d{4,7})\\b")

    private val TEXT_KEYS = setOf("reason", "subreason", "messages", "simpleText", "text")
    private val AGE_GATE = Regex("\\bage\\b", RegexOption.IGNORE_CASE)
    private val REGION_LOCK = Regex("in your (country|region)", RegexOption.IGNORE_CASE)
    private val EMBED_REFUSAL = Regex("other websites|embedd", RegexOption.IGNORE_CASE)

    fun pageSignals(html: String): YouTubePageSignals = YouTubePageSignals(
        playerResponseJson = slicePlayerResponse(html),
        playerId = YouTubeUrls.playerId(JS_URL.find(html)?.groupValues?.get(1)),
        apiKey = API_KEY.find(html)?.groupValues?.get(1),
        clientName = CLIENT_NAME.find(html)?.groupValues?.get(1),
        clientVersion = CLIENT_VERSION.find(html)?.groupValues?.get(1),
        visitorData = VISITOR_DATA.find(html)?.groupValues?.get(1),
        signatureTimestamp = SIGNATURE_TIMESTAMP.find(html)?.groupValues?.get(1)?.toIntOrNull(),
    )

    /**
     * Slices the inline player response out of the page.
     *
     * Pages declare the variable as `null` before assigning the real object, so only an
     * assignment of an object literal is accepted. The object is then located by brace balance
     * rather than by a terminator guess, and string contents are skipped so a brace inside a
     * title cannot end the slice early.
     */
    fun slicePlayerResponse(html: String): String? =
        PLAYER_RESPONSE_ASSIGNMENT.findAll(html).firstNotNullOfOrNull { match ->
            balancedObject(html, match.range.last)
        }

    private fun balancedObject(text: String, start: Int): String? {
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val character = text[index]
            when {
                escaped -> escaped = false
                inString && character == '\\' -> escaped = true
                character == '"' -> inString = !inString
                inString -> Unit
                character == '{' -> depth++
                character == '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, index + 1)
                }
            }
        }
        return null
    }

    fun parse(json: String, nowEpochMs: Long): YouTubeParseResult {
        val root = BoundedJsonParser.parse(json, maxNodes = MAX_JSON_NODES)
            ?: return failure(SiteExtractionFailure.MALFORMED_RESPONSE, definite = false)
        playability(root["playabilityStatus"])?.let { return it }

        // An OK status without streaming data means YouTube accepted the request but withheld
        // delivery, which is what its bot check does to requests it does not trust.
        val streamingData = root["streamingData"]
            ?: return failure(SiteExtractionFailure.LOGIN_REQUIRED, definite = false)
        if (isDrmProtected(root, streamingData)) {
            return failure(SiteExtractionFailure.DRM_PROTECTED, definite = true)
        }
        // A running live stream has no finite file to write, so it is reported as having no
        // downloadable media rather than being queued and never finishing.
        if (root.path("videoDetails", "isLive").asBooleanOrNull == true) {
            return failure(SiteExtractionFailure.NO_MEDIA_FOUND, definite = true)
        }

        val progressive = streamingData["formats"].asArrayOrEmpty.mapNotNull(::readStream)
        val adaptive = streamingData["adaptiveFormats"].asArrayOrEmpty.mapNotNull(::readStream)
        if (progressive.isEmpty() && adaptive.isEmpty()) {
            // Without any address YouTube only streams through its own player protocol.
            val reason = if (streamingData["serverAbrStreamingUrl"].asStringOrNull != null) {
                SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED
            } else {
                SiteExtractionFailure.RESPONSE_CHANGED
            }
            return failure(reason, definite = false)
        }

        val details = root["videoDetails"]
        return YouTubeParseResult.Success(
            YouTubeVideo(
                videoId = details["videoId"].asStringOrNull,
                title = details["title"].asStringOrNull?.trim()?.takeIf(String::isNotEmpty),
                author = details["author"].asStringOrNull?.trim()?.takeIf(String::isNotEmpty),
                durationMillis = details["lengthSeconds"].asStringOrNull?.toLongOrNull()
                    ?.takeIf { it > 0 }?.times(1_000),
                thumbnailUrl = largestThumbnail(details),
                progressive = progressive,
                adaptive = adaptive,
                expiresAtEpochMs = streamingData["expiresInSeconds"].asStringOrNull
                    ?.toLongOrNull()
                    ?.takeIf { it > 0 }
                    ?.let { nowEpochMs + it * 1_000 },
            ),
        )
    }

    /**
     * Maps YouTube's own playability verdict onto the closed failure set.
     *
     * Age and content gates are reported, never acknowledged, so the adapter never tells YouTube
     * that a check it did not satisfy is satisfied. A bot check and an embed refusal describe
     * one request rather than the video, so they are not definite.
     */
    private fun playability(status: JsonValue?): YouTubeParseResult.Failure? {
        val reason = reasonText(status)
        return when (status["status"].asStringOrNull) {
            null -> failure(SiteExtractionFailure.RESPONSE_CHANGED, definite = false)
            "OK" -> null
            "LOGIN_REQUIRED" -> when {
                reason.contains("private", ignoreCase = true) ->
                    failure(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, definite = true)

                AGE_GATE.containsMatchIn(reason) ->
                    failure(SiteExtractionFailure.LOGIN_REQUIRED, definite = true)

                else -> failure(SiteExtractionFailure.LOGIN_REQUIRED, definite = false)
            }

            "AGE_VERIFICATION_REQUIRED", "AGE_CHECK_REQUIRED", "CONTENT_CHECK_REQUIRED" ->
                failure(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, definite = true)

            "LIVE_STREAM_OFFLINE" -> failure(SiteExtractionFailure.NO_MEDIA_FOUND, definite = true)
            "UNPLAYABLE", "ERROR" -> when {
                REGION_LOCK.containsMatchIn(reason) ->
                    failure(SiteExtractionFailure.GEO_RESTRICTED, definite = true)

                EMBED_REFUSAL.containsMatchIn(reason) ->
                    failure(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, definite = false)

                else -> failure(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, definite = true)
            }

            else -> failure(SiteExtractionFailure.RESPONSE_CHANGED, definite = false)
        }
    }

    /** Collects the human-readable reason strings YouTube attaches to a verdict. */
    private fun reasonText(status: JsonValue?): String {
        val parts = mutableListOf<String>()
        collectText(status, key = null, depth = 0, into = parts)
        return parts.joinToString(" ")
    }

    private fun collectText(
        value: JsonValue?,
        key: String?,
        depth: Int,
        into: MutableList<String>,
    ) {
        if (depth > MAX_REASON_DEPTH || into.size >= MAX_REASON_PARTS) return
        when (value) {
            is JsonValue.Text -> if (key in TEXT_KEYS) into += value.value
            is JsonValue.Array -> value.items.forEach { collectText(it, key, depth + 1, into) }
            is JsonValue.Object -> value.entries.forEach { (childKey, child) ->
                collectText(child, childKey, depth + 1, into)
            }

            else -> Unit
        }
    }

    private fun failure(reason: SiteExtractionFailure, definite: Boolean) =
        YouTubeParseResult.Failure(reason, definite)

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
        val protectedDescriptor = descriptor
            ?.takeIf(String::isNotBlank)
        if (plainUrl == null && protectedDescriptor == null) return null

        val rawMime = format["mimeType"].asStringOrNull
        val mimeType = rawMime?.substringBefore(';')?.trim()?.lowercase()
            ?.takeIf(String::isNotEmpty)
        val codecs = rawMime?.substringAfter("codecs=\"", "")?.substringBefore('"')
            ?.split(',')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()
        val hasVideo = mimeType?.startsWith("video/") == true
        val hasAudio = mimeType?.startsWith("audio/") == true ||
            format["audioQuality"].asStringOrNull != null ||
            format["audioSampleRate"].asStringOrNull != null

        return YouTubeStream(
            itag = itag,
            url = plainUrl,
            protectedDescriptor = if (plainUrl == null) protectedDescriptor else null,
            mimeType = mimeType,
            codecs = codecs,
            qualityLabel = format["qualityLabel"].asStringOrNull,
            bitrate = format["averageBitrate"].asLongOrNull ?: format["bitrate"].asLongOrNull,
            width = format["width"].asLongOrNull?.toInt(),
            height = format["height"].asLongOrNull?.toInt(),
            contentLengthBytes = format["contentLength"].asStringOrNull?.toLongOrNull()
                ?: format["contentLength"].asLongOrNull,
            hasVideo = hasVideo,
            hasAudio = hasAudio,
            isDefaultAudio = format.path("audioTrack", "audioIsDefault").asBooleanOrNull,
            isDrc = format["isDrc"].asBooleanOrNull == true,
        )
    }

    private fun largestThumbnail(details: JsonValue?): String? = details
        .path("thumbnail", "thumbnails")
        .asArrayOrEmpty
        .mapNotNull { entry ->
            val url = entry["url"].asStringOrNull?.takeIf { it.startsWith("https://") }
            val width = entry["width"].asLongOrNull ?: 0
            if (url == null) null else width to url
        }
        .maxByOrNull { it.first }
        ?.second
}
