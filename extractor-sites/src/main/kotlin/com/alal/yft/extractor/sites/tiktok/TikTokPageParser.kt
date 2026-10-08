package com.alal.yft.extractor.sites.tiktok

import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonReadResult
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asBooleanOrNull
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.api.json.path
import java.net.URI
import java.util.Locale

/** The video codec of one TikTok file, as its data names it. */
internal enum class TikTokCodec(val mark: String?, val codecTag: String?, val order: Int) {
    H264(null, "avc1", 0),
    UNKNOWN(null, null, 1),
    H265("H.265", "hvc1", 2),
}

/** Where a quality's addresses came from in the post's data. */
internal enum class TikTokQualitySource { QUALITY_LIST, PLAY_ADDRESS, DOWNLOAD_ADDRESS }

/**
 * One quality TikTok listed for a post, with every https address it gave for that file in the
 * page's order. [heightLabel] is the standard height its label shows (1080, 720, 540); [width]
 * and [height] are the file's own size when the data states it.
 */
internal data class TikTokQuality(
    val heightLabel: Int?,
    val codec: TikTokCodec,
    val addresses: List<String>,
    val bitrateBitsPerSecond: Long?,
    val sizeBytes: Long?,
    val width: Int?,
    val height: Int?,
    val source: TikTokQualitySource,
) {
    /** "1080p H.265", "720p" or "With TikTok watermark"; never an address. */
    val label: String
        get() = if (source == TikTokQualitySource.DOWNLOAD_ADDRESS) {
            WATERMARK_LABEL
        } else {
            listOfNotNull(heightLabel?.let { "${it}p" } ?: "Video", codec.mark).joinToString(" ")
        }

    /** The label a title carries: none for an address whose height and codec are unknown. */
    val titleLabel: String?
        get() = label.takeUnless {
            heightLabel == null && codec.mark == null &&
                source != TikTokQualitySource.DOWNLOAD_ADDRESS
        }

    companion object {
        const val WATERMARK_LABEL = "With TikTok watermark"
    }
}

/** Non-sensitive description of a TikTok post plus its qualities. */
internal data class TikTokPost(
    val videoId: String,
    val authorHandle: String?,
    val title: String?,
    val thumbnailUrl: String?,
    val durationMillis: Long?,
    /** Watermark-free qualities: highest first, H.264 before H.265, then higher bitrate. */
    val qualities: List<TikTokQuality>,
    /** The download address, which TikTok stamps with its watermark; used only as a last resort. */
    val watermarked: TikTokQuality?,
    val hasPlayAddress: Boolean,
)

/** What kind of page a TikTok answer was, for honest reasons and Details. */
internal enum class TikTokPageKind(val label: String) {
    VIDEO("video page"),
    HOME("home page"),
    CHALLENGE("challenge page"),
    LOGIN("login page"),
    OTHER("other page"),
}

/** How one page was read. Only fixed words, data keys and positions; never an address. */
internal data class TikTokPageNotes(
    val kind: TikTokPageKind,
    /** e.g. `universal webapp.reflow.video.detail`, `SIGI_STATE ItemModule` or `none`. */
    val dataKey: String = "none",
    /** `read`, `read after cleanup`, `error MalformedJson at char 1234`, or null (no data). */
    val json: String? = null,
    /** `matches`, `other id` or `missing`. */
    val postId: String = "missing",
)

internal sealed interface TikTokParseResult {
    val notes: TikTokPageNotes

    data class Success(
        val post: TikTokPost,
        override val notes: TikTokPageNotes,
    ) : TikTokParseResult

    data class Failure(
        val reason: SiteExtractionFailure,
        override val notes: TikTokPageNotes,
        /** The adapter's own text for the user, when the reason's generic text would mislead. */
        val message: String? = null,
        /**
         * True when another page answer cannot change the outcome (TikTok's own status for the
         * post, DRM, a photo post or a link that lands on the home page).
         */
        val final: Boolean = false,
    ) : TikTokParseResult
}

/**
 * Reads the JSON TikTok embeds in its own page.
 *
 * P39 (R27, R28): the data script is found by its `id` attribute, never by the first quoted
 * mention of its name; any `__DEFAULT_SCOPE__` key holding `itemInfo.itemStruct` is a post (the
 * desktop page uses `webapp.video-detail`, the phone page `webapp.reflow.video.detail`, and
 * TikTok renames keys); `SIGI_STATE` is still read; a post with another id is skipped for the
 * next shape. One lenient retry decodes HTML entities and cuts trailing text after the object.
 * [SiteExtractionFailure.RESPONSE_CHANGED] is left only for a data shape the parser does not
 * know; a home, login or challenge page gets its own honest reason.
 */
internal object TikTokPageParser {
    const val NOT_A_VIDEO_MESSAGE =
        "This TikTok link does not open a video. It may be removed or private — open it in " +
            "YFT's browser to check."

    private const val UNIVERSAL_SCRIPT_ID = "__UNIVERSAL_DATA_FOR_REHYDRATION__"
    private const val SIGI_SCRIPT_ID = "SIGI_STATE"
    private const val DEFAULT_SCOPE = "__DEFAULT_SCOPE__"
    private val PREFERRED_KEYS = listOf("webapp.video-detail", "webapp.reflow.video.detail")
    private val ID_ATTRIBUTE =
        Regex("""(?:^|\s)id\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+))""", RegexOption.IGNORE_CASE)
    private val CHALLENGE_MARKERS = listOf("captcha", "verify", "waf", "please wait", "challenge")
    private val HEIGHT = Regex("(\\d{3,4})")
    private val STANDARD_HEIGHTS = listOf(2160, 1440, 1080, 720, 540, 480, 360, 240, 144)

    /** TikTok status codes that describe an access outcome rather than a transport error. */
    private val STATUS_FAILURES = mapOf(
        10101L to SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
        10102L to SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
        10204L to SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
        10216L to SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
        10217L to SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
        10218L to SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
        10221L to SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
        10222L to SiteExtractionFailure.LOGIN_REQUIRED,
        10223L to SiteExtractionFailure.LOGIN_REQUIRED,
        10231L to SiteExtractionFailure.GEO_RESTRICTED,
    )

    private class Shape(
        val dataKey: String,
        val item: JsonValue?,
        val status: SiteExtractionFailure?,
    )

    private class Read(val value: JsonValue?, val note: String)

    fun parse(html: String, finalUrl: String, expectedVideoId: String?): TikTokParseResult {
        val urlKind = kindOf(finalUrl)
        val scripts = scripts(html)
        val shapes = mutableListOf<Shape>()
        val jsonNotes = mutableListOf<String>()

        scripts[UNIVERSAL_SCRIPT_ID]?.let { body ->
            val read = lenientRead(body)
            jsonNotes += read.note
            val scope = read.value[DEFAULT_SCOPE] as? JsonValue.Object
            scope?.entries?.entries
                ?.sortedBy { (key, _) -> PREFERRED_KEYS.indexOf(key).takeIf { it >= 0 } ?: 99 }
                ?.forEach { (key, detail) ->
                    val item = detail.path("itemInfo", "itemStruct")
                    val status = statusFailure(detail)
                    if (item != null || status != null && key in PREFERRED_KEYS) {
                        shapes += Shape("universal $key", item, status)
                    }
                }
        }
        scripts[SIGI_SCRIPT_ID]?.let { body ->
            val read = lenientRead(body)
            jsonNotes += read.note
            val module = read.value["ItemModule"] as? JsonValue.Object
            val entry = expectedVideoId?.let { module?.entries?.get(it) }
                ?: module?.entries?.values?.firstOrNull()
            if (entry != null) shapes += Shape("SIGI_STATE ItemModule", entry, null)
        }

        val json = jsonNotes.firstOrNull { it.startsWith("read") } ?: jsonNotes.firstOrNull()
        var statusFailure: SiteExtractionFailure? = null
        var sawOtherId = false
        for (shape in shapes) {
            if (shape.status != null) {
                statusFailure = statusFailure ?: shape.status
                continue
            }
            val id = shape.item["id"].asStringOrNull ?: continue
            if (expectedVideoId != null && id != expectedVideoId) {
                sawOtherId = true
                continue
            }
            val kind = if (urlKind == TikTokPageKind.OTHER) TikTokPageKind.VIDEO else urlKind
            return post(shape.item, id, TikTokPageNotes(kind, shape.dataKey, json, "matches"))
        }

        val notes = TikTokPageNotes(
            kind = urlKind,
            dataKey = shapes.firstOrNull()?.dataKey ?: "none",
            json = json,
            postId = if (sawOtherId) "other id" else "missing",
        )
        val failure = Outcome(notes)
        return when {
            statusFailure != null -> failure.final(statusFailure)
            urlKind == TikTokPageKind.HOME -> failure.final(
                SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
                NOT_A_VIDEO_MESSAGE,
            )

            urlKind == TikTokPageKind.LOGIN ->
                TikTokParseResult.Failure(SiteExtractionFailure.LOGIN_REQUIRED, notes)

            scripts.keys.none { it == UNIVERSAL_SCRIPT_ID || it == SIGI_SCRIPT_ID } &&
                looksLikeChallenge(html) -> TikTokParseResult.Failure(
                SiteExtractionFailure.BOT_CHECK,
                notes.copy(kind = TikTokPageKind.CHALLENGE),
            )

            json != null && !json.startsWith("read") ->
                TikTokParseResult.Failure(SiteExtractionFailure.RESPONSE_CHANGED, notes)

            expectedVideoId == null -> failure.final(
                SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
                NOT_A_VIDEO_MESSAGE,
            )

            else -> TikTokParseResult.Failure(SiteExtractionFailure.RESPONSE_CHANGED, notes)
        }
    }

    /** The kind of page [finalUrl] is, from its path alone. */
    fun kindOf(finalUrl: String): TikTokPageKind {
        val path = runCatching { URI(finalUrl).rawPath }.getOrNull().orEmpty()
            .lowercase(Locale.US)
            .trimEnd('/')
        return when {
            path.isEmpty() || path == "/foryou" || path.startsWith("/foryou/") ->
                TikTokPageKind.HOME

            path == "/login" || path.startsWith("/login/") -> TikTokPageKind.LOGIN
            TikTokUrls.identify(finalUrl)?.takeUnless { it.requiresCanonicalResolution } != null ->
                TikTokPageKind.VIDEO

            else -> TikTokPageKind.OTHER
        }
    }

    private class Outcome(val notes: TikTokPageNotes) {
        fun final(reason: SiteExtractionFailure, message: String? = null) =
            TikTokParseResult.Failure(reason, notes, message, final = true)
    }

    private fun post(item: JsonValue?, videoId: String, notes: TikTokPageNotes): TikTokParseResult {
        if (item["imagePost"] != null && item["imagePost"] !is JsonValue.Null) {
            return TikTokParseResult.Failure(
                SiteExtractionFailure.NO_MEDIA_FOUND,
                notes,
                final = true,
            )
        }
        if (item.path("video", "isDrm").asBooleanOrNull == true) {
            return TikTokParseResult.Failure(
                SiteExtractionFailure.DRM_PROTECTED,
                notes,
                final = true,
            )
        }
        val video = item["video"]
            ?: return TikTokParseResult.Failure(SiteExtractionFailure.RESPONSE_CHANGED, notes)
        val qualities = qualities(video)
        val watermarked = addressesOf(video["downloadAddr"]).takeIf { it.isNotEmpty() }?.let {
            TikTokQuality(
                heightLabel = video.heightLabel(),
                codec = codecOf(video["codecType"].asStringOrNull, null),
                addresses = it,
                bitrateBitsPerSecond = null,
                sizeBytes = null,
                width = video["width"].asPositiveInt,
                height = video["height"].asPositiveInt,
                source = TikTokQualitySource.DOWNLOAD_ADDRESS,
            )
        }
        if (qualities.isEmpty() && watermarked == null) {
            return TikTokParseResult.Failure(SiteExtractionFailure.NO_MEDIA_FOUND, notes)
        }
        return TikTokParseResult.Success(
            TikTokPost(
                videoId = videoId,
                authorHandle = item.path("author", "uniqueId").asStringOrNull
                    ?: item["author"].asStringOrNull,
                title = item["desc"].asStringOrNull,
                thumbnailUrl = listOf("cover", "originCover", "dynamicCover")
                    .firstNotNullOfOrNull { key -> video[key].asStringOrNull?.httpsOrNull() },
                durationMillis = video["duration"].asLongOrNull
                    ?.takeIf { it > 0 }
                    ?.let { seconds -> seconds * 1_000 },
                qualities = qualities,
                watermarked = watermarked,
                hasPlayAddress = addressesOf(video["playAddr"]).isNotEmpty(),
            ),
            notes,
        )
    }

    /**
     * Every quality in `bitrateInfo` with all of its https addresses, then the play address.
     * The same height, codec and exact size is one file listed twice, so it is kept once with
     * both address lists; a play address of a height and codec already listed is that quality's
     * last address rather than a second row.
     */
    private fun qualities(video: JsonValue): List<TikTokQuality> {
        val merged = mutableListOf<TikTokQuality>()
        video["bitrateInfo"].asArrayOrEmpty.forEach { entry ->
            val play = entry["PlayAddr"]
            val addresses = addressesOf(play)
            if (addresses.isEmpty()) return@forEach
            val width = play["Width"].asPositiveInt
            val height = play["Height"].asPositiveInt
            val quality = TikTokQuality(
                heightLabel = entry["GearName"].asStringOrNull?.heightNumber()
                    ?: minSide(width, height),
                codec = codecOf(entry["CodecType"].asStringOrNull, play["UrlKey"].asStringOrNull),
                addresses = addresses,
                bitrateBitsPerSecond = entry["Bitrate"].asLongOrNull?.takeIf { it > 0 },
                sizeBytes = (play["DataSize"].asLongOrNull ?: entry["DataSize"].asLongOrNull)
                    ?.takeIf { it > 0 },
                width = width,
                height = height,
                source = TikTokQualitySource.QUALITY_LIST,
            )
            // One row per height and codec: the same file listed twice merges its addresses,
            // and of two files TikTok encoded at one height the higher bitrate is kept.
            val same = merged.indexOfFirst {
                it.heightLabel == quality.heightLabel && it.codec == quality.codec
            }
            when {
                same < 0 -> merged += quality
                quality.sizeBytes != null && merged[same].sizeBytes == quality.sizeBytes ->
                    merged[same] = merged[same].withAddresses(addresses)
                (quality.bitrateBitsPerSecond ?: 0L) > (merged[same].bitrateBitsPerSecond ?: 0L) ->
                    merged[same] = quality
            }
        }

        val known = merged.flatMap(TikTokQuality::addresses).toSet()
        val playAddresses = addressesOf(video["playAddr"]).filterNot(known::contains)
        if (playAddresses.isNotEmpty()) {
            val heightLabel = video.heightLabel()
            val codec = codecOf(video["codecType"].asStringOrNull, null)
            val same = merged.indexOfFirst {
                it.heightLabel == heightLabel && (codec == TikTokCodec.UNKNOWN || it.codec == codec)
            }
            if (same >= 0) {
                merged[same] = merged[same].withAddresses(playAddresses)
            } else {
                merged += TikTokQuality(
                    heightLabel = heightLabel,
                    codec = codec,
                    addresses = playAddresses,
                    bitrateBitsPerSecond = video["bitrate"].asLongOrNull?.takeIf { it > 0 },
                    sizeBytes = null,
                    width = video["width"].asPositiveInt,
                    height = video["height"].asPositiveInt,
                    source = TikTokQualitySource.PLAY_ADDRESS,
                )
            }
        }
        return merged.sortedWith(
            compareByDescending<TikTokQuality> { it.heightLabel ?: 0 }
                .thenBy { it.codec.order }
                .thenByDescending { it.bitrateBitsPerSecond ?: 0L },
        )
    }

    private fun TikTokQuality.withAddresses(more: List<String>): TikTokQuality =
        copy(addresses = (addresses + more).distinct())

    /** The play address's height: its stated definition or ratio, else the file's short side. */
    private fun JsonValue.heightLabel(): Int? =
        (this["definition"].asStringOrNull ?: this["ratio"].asStringOrNull)?.heightNumber()
            ?: minSide(this["width"].asPositiveInt, this["height"].asPositiveInt)

    /** The standard height nearest the file's short side: 576 × 1024 is TikTok's "540p". */
    private fun minSide(width: Int?, height: Int?): Int? {
        if (width == null || height == null) return null
        val side = minOf(width, height)
        return STANDARD_HEIGHTS.minByOrNull { kotlin.math.abs(it - side) }
    }

    private fun codecOf(codecType: String?, urlKey: String?): TikTokCodec {
        val text = listOfNotNull(codecType, urlKey).joinToString(" ").lowercase(Locale.US)
        return when {
            listOf("265", "hevc", "bytevc1", "hvc1").any(text::contains) -> TikTokCodec.H265
            listOf("264", "avc").any(text::contains) -> TikTokCodec.H264
            else -> TikTokCodec.UNKNOWN
        }
    }

    /** An address list: a string, a list of strings, or an object with `UrlList`. */
    private fun addressesOf(value: JsonValue?): List<String> {
        val texts = when (value) {
            is JsonValue.Text -> listOf(value.value)
            is JsonValue.Array -> value.items.mapNotNull { it.asStringOrNull }
            is JsonValue.Object -> value["UrlList"].asArrayOrEmpty.mapNotNull { it.asStringOrNull }
            else -> emptyList()
        }
        return texts.mapNotNull { it.trim().httpsOrNull() }.distinct()
    }

    private val JsonValue?.asPositiveInt: Int?
        get() = asLongOrNull?.takeIf { it in 1..100_000 }?.toInt()

    private fun statusFailure(detail: JsonValue?): SiteExtractionFailure? {
        val status = detail["statusCode"].asLongOrNull
            ?: detail["statusCodeV2"].asLongOrNull
            ?: return null
        if (status == 0L) return null
        return STATUS_FAILURES[status] ?: SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE
    }

    /** A strict read first; then entities decoded and anything after the object cut once. */
    private fun lenientRead(body: String): Read {
        val strict = BoundedJsonParser.read(body.trim())
        if (strict is JsonReadResult.Read) return Read(strict.value, "read")
        balancedObject(decodeEntities(body))?.let { cleaned ->
            val second = BoundedJsonParser.read(cleaned)
            if (second is JsonReadResult.Read) return Read(second.value, "read after cleanup")
        }
        val failed = strict as JsonReadResult.Failed
        return Read(null, "error ${failed.problem.label} at char ${failed.position}")
    }

    private fun decodeEntities(text: String): String = text
        .replace("&quot;", "\"")
        .replace("&#34;", "\"")
        .replace("&#39;", "'")
        .replace("&#x27;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")

    /** The first `{…}` object with balanced braces outside strings; null if it never closes. */
    private fun balancedObject(text: String): String? {
        val start = text.indexOf('{').takeIf { it >= 0 } ?: return null
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
                character == '{' -> depth += 1
                character == '}' -> {
                    depth -= 1
                    if (depth == 0) return text.substring(start, index + 1)
                }
            }
        }
        return null
    }

    /**
     * Every `<script>` body keyed by its `id` attribute. Only the opening tag's own attributes
     * are read, so the name mentioned earlier in the page (a preload hint, another script's
     * text) can never be mistaken for the data script.
     */
    private fun scripts(html: String): Map<String, String> {
        val found = LinkedHashMap<String, String>()
        var from = 0
        while (true) {
            val tagStart = html.indexOf("<script", from, ignoreCase = true).takeIf { it >= 0 }
                ?: break
            val tagEnd = html.indexOf('>', tagStart).takeIf { it >= 0 } ?: break
            val bodyEnd = html.indexOf("</script", tagEnd, ignoreCase = true).takeIf { it >= 0 }
                ?: break
            val attributes = html.substring(tagStart + "<script".length, tagEnd)
            ID_ATTRIBUTE.find(attributes)?.let { match ->
                val id = match.groupValues.drop(1).firstOrNull(String::isNotEmpty)
                val body = html.substring(tagEnd + 1, bodyEnd).trim()
                if (id != null && body.isNotEmpty()) found.putIfAbsent(id, body)
            }
            from = bodyEnd + 1
        }
        return found
    }

    private fun looksLikeChallenge(html: String): Boolean {
        val lowered = html.lowercase(Locale.US)
        return CHALLENGE_MARKERS.any(lowered::contains)
    }

    private fun String.httpsOrNull(): String? =
        takeIf { it.startsWith("https://", ignoreCase = true) }

    /** The height number in a gear name (`normal_720_0`), definition or ratio (`720p`). */
    private fun String.heightNumber(): Int? =
        HEIGHT.find(this)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 144..4320 }
}
