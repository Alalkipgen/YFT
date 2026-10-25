package com.alal.yft.extractor.sites.vk

import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import java.util.Locale

/** One real rendition of the requested video (VK_PLAN §4.2, the owner's answer 4). */
internal data class VkRendition(
    val key: String,
    val url: String,
    val kind: MediaKind,
    val mimeType: String,
    val height: Int?,
    val expiresAtEpochMs: Long?,
)

/** What the embed says about the requested video; a null title means "use the fallback". */
internal data class VkVideo(
    val title: String?,
    val thumbnailUrl: String?,
    val durationMillis: Long?,
    val renditions: List<VkRendition>,
    val isLive: Boolean,
    val foundBy: String,
    val refusedAddresses: Int,
)

internal sealed interface VkEmbed {
    /** VK's data for the requested video (its renditions may be empty). */
    data class Found(val video: VkVideo) : VkEmbed

    /** `<div id="video_ext_msg">Video not found.</div>`: deleted, private and sign-in alike. */
    data object NotFound : VkEmbed

    /**
     * VK5: `video_ext_msg` with VK's adult-content text or `embedErrorCallback(9)` — "may contain
     * adult content … can't be viewed on external sites". VK's own site plays these signed out;
     * only this answer opens path D.
     */
    data object Adult : VkEmbed

    /** VK's SPA shell (`spa_root`, `isRedirectChallenge`) without the video: not an answer. */
    data object Shell : VkEmbed

    /** Neither data for the video nor a known VK text: the markup changed. */
    data class Changed(val why: String) : VkEmbed
}

/** Path D's answer from api.vkvideo.ru's `video.get` (VK5). */
internal sealed interface VkApiAnswer {
    /** The item for the requested video (its renditions may be empty). */
    data class Found(val video: VkVideo) : VkApiAnswer

    /** The item says VK does not play it (`restriction.can_play` 0: deleted, blocked, gated). */
    data object Restricted : VkApiAnswer

    /** VK's API error (`error_code`: 5 the token, 14 a captcha, …). */
    data class Error(val code: Long?) : VkApiAnswer

    /** Not JSON, or no item for the video. */
    data class Changed(val why: String) : VkApiAnswer
}

/**
 * Which addresses count as media: the embed's (v1: `*.okcdn.ru` in the table, `vkvd{N}.okcdn.ru`
 * in the shape scan) or path D's (VK5: the same, and `vk{N}-{N}.vkuser.net`).
 */
internal enum class VkMediaHosts(
    val table: (String) -> Boolean,
    val shape: (String) -> Boolean,
) {
    EMBED(VkUrls::isMediaFileUrl, VkUrls::isPlayerMediaUrl),
    API(VkUrls::isApiMediaFileUrl, VkUrls::isApiPlayerMediaUrl),
}

/**
 * Reads `video_ext.php`'s `window.cur = Object.assign(…, {"apiPrefetchCache": [ … ]})`.
 *
 * Key table first (the item of `response.items` whose `owner_id` and `id` are the link's, and its
 * `files`), the shape scan second (only when the table finds nothing): objects naming the
 * requested video that hold `vkvd{N}.okcdn.ru` addresses under rendition-like keys. Another
 * video's data, previews (`trailer`), images, first frames, timeline sprites, ads, subtitles and
 * stats pixels are never media.
 */
internal object VkEmbedParser {
    private const val CACHE_KEY = "\"apiPrefetchCache\""
    private const val MAX_CACHES = 3
    private const val MAX_SCAN_DEPTH = 48
    private const val NOT_FOUND_MARKER = "id=\"video_ext_msg\""
    private val SHELL_MARKERS = listOf("spa_root", "isRedirectChallenge")

    /** VK5: the embed's error callback for adult content (VK's "Video not found." calls 7). */
    private val ADULT_CALLBACK = Regex("embedErrorCallback\\s*(?:\\?\\.)?\\s*\\(\\s*9\\s*\\)")

    /** VK5: VK's adult-content text inside `video_ext_msg`, in English and Russian. */
    private val ADULT_WORDS = listOf("adult content", "для взрослых")
    private const val MAX_MESSAGE_CHARS = 2_000
    private val SPACES = Regex("\\s+")

    private const val MP4 = "video/mp4"
    private const val HLS = "application/x-mpegURL"
    private const val DASH = "application/dash+xml"

    private val MP4_KEY = Regex("^mp4_([0-9]{2,4})$")
    private val HLS_KEYS = listOf("hls", "hls_fmp4", "hls_ondemand")

    /** MP4-DASH before WebM-DASH. */
    private val DASH_KEYS = listOf("dash_sep", "dash_ondemand", "dash_webm", "dash_webm_av1")
    private val MANIFEST_ORDER = HLS_KEYS + DASH_KEYS

    /** The shape scan's progressive keys: `mp4_720`, `url720`, `cache720`. */
    private val SHAPE_PROGRESSIVE_KEY = Regex("^(?:mp4|url|cache)_?([0-9]{3,4})$")

    /** VK's documented states of a broadcast that is not over (a finished one is `postlive`). */
    private val LIVE_STATES = setOf("started", "waiting", "upcoming")

    private val NOT_MEDIA_KEYS = setOf(
        "trailer",
        "image",
        "first_frame",
        "timeline_thumbs",
        "ads",
        "subtitles",
        "stats_pixels",
    )

    fun parse(html: String, ownerId: Long, videoId: Long): VkEmbed {
        val caches = prefetchCaches(html)
        val item = caches.firstNotNullOfOrNull { itemOf(it, ownerId, videoId) }
        val video = if (item != null) {
            byTable(item, VkMediaHosts.EMBED)
                ?: byShape(item, ownerId, videoId, item, VkMediaHosts.EMBED)
                ?: meta(item, emptyList(), "table")
        } else {
            caches.firstNotNullOfOrNull { byShape(it, ownerId, videoId, null, VkMediaHosts.EMBED) }
        }
        if (video != null) return VkEmbed.Found(video)
        return when {
            isAdult(html) -> VkEmbed.Adult
            NOT_FOUND_MARKER in html -> VkEmbed.NotFound
            SHELL_MARKERS.any { it in html } -> VkEmbed.Shell
            caches.isEmpty() -> VkEmbed.Changed("no player data")
            else -> VkEmbed.Changed("no item for the video")
        }
    }

    /**
     * Path D (VK5): api.vkvideo.ru's `video.get` answer — VK's error, or the `response.items[]`
     * entry naming the link's video (the identity check), read by the embed's key table and shape
     * scan with path D's hosts. An item VK says it does not play is [VkApiAnswer.Restricted].
     */
    fun parseApi(json: String, ownerId: Long, videoId: Long): VkApiAnswer {
        val root = BoundedJsonParser.parse(json) as? JsonValue.Object
            ?: return VkApiAnswer.Changed("not JSON")
        (root["error"] as? JsonValue.Object)?.let { error ->
            return VkApiAnswer.Error(error["error_code"].asLongOrNull)
        }
        val item = itemOf(JsonValue.Array(listOf(root)), ownerId, videoId)
            ?: return VkApiAnswer.Changed("no item for the video")
        if (item["restriction"]["can_play"].asLongOrNull == 0L) return VkApiAnswer.Restricted
        val video = byTable(item, VkMediaHosts.API)
            ?: byShape(item, ownerId, videoId, item, VkMediaHosts.API)
            ?: meta(item, emptyList(), "table")
        return VkApiAnswer.Found(video)
    }

    /**
     * VK5: the embed's `video_ext_msg` holds VK's adult-content text, or the page calls
     * `embedErrorCallback(9)` beside it. Free words elsewhere in the page never count.
     */
    private fun isAdult(html: String): Boolean {
        val message = messageOf(html) ?: return false
        return ADULT_CALLBACK.containsMatchIn(html) || ADULT_WORDS.any { it in message }
    }

    /** The text of `<div id="video_ext_msg">`, entities decoded, spaces folded, lower case. */
    private fun messageOf(html: String): String? {
        val at = html.indexOf(NOT_FOUND_MARKER).takeIf { it >= 0 } ?: return null
        val open = html.indexOf('>', at).takeIf { it >= 0 } ?: return null
        val close = html.indexOf("</div>", open).takeIf { it >= 0 } ?: return null
        if (close - open > MAX_MESSAGE_CHARS) return null
        return decodeEntities(html.substring(open + 1, close))
            .replace('\u00A0', ' ')
            .replace(SPACES, " ")
            .trim()
            .lowercase(Locale.ROOT)
    }

    /** Every `"apiPrefetchCache": [ … ]` array in the page that parses as JSON (at most 3). */
    private fun prefetchCaches(html: String): List<JsonValue> {
        val caches = mutableListOf<JsonValue>()
        var from = 0
        while (caches.size < MAX_CACHES) {
            val at = html.indexOf(CACHE_KEY, from).takeIf { it >= 0 } ?: break
            from = at + CACHE_KEY.length
            var cursor = from
            while (cursor < html.length && html[cursor].isWhitespace()) cursor++
            if (cursor >= html.length || html[cursor] != ':') continue
            cursor++
            while (cursor < html.length && html[cursor].isWhitespace()) cursor++
            if (cursor >= html.length || html[cursor] != '[') continue
            val end = closingBracket(html, cursor) ?: continue
            BoundedJsonParser.parse(html.substring(cursor, end + 1))?.let(caches::add)
            from = end + 1
        }
        return caches
    }

    /** The index of the bracket closing the one at [start], skipping JSON strings. */
    private fun closingBracket(text: String, start: Int): Int? {
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val character = text[index]
            when {
                inString && escaped -> escaped = false
                inString && character == '\\' -> escaped = true
                inString && character == '"' -> inString = false
                inString -> Unit
                character == '"' -> inString = true
                character == '[' || character == '{' -> depth++
                character == ']' || character == '}' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
        return null
    }

    /** The entry `response.items[]` naming the requested video, by its own `owner_id` and `id`. */
    private fun itemOf(cache: JsonValue, ownerId: Long, videoId: Long): JsonValue.Object? =
        cache.asArrayOrEmpty.asSequence()
            .flatMap { entry -> entry["response"]["items"].asArrayOrEmpty.asSequence() }
            .filterIsInstance<JsonValue.Object>()
            .firstOrNull { namedVideo(it) == ownerId to videoId }

    private fun byTable(item: JsonValue.Object, hosts: VkMediaHosts): VkVideo? {
        val files = item["files"] as? JsonValue.Object ?: return null
        val renditions = mutableListOf<VkRendition>()
        var refused = 0
        files.entries.forEach { (key, value) ->
            val url = value.asStringOrNull ?: return@forEach
            val shape = tableShape(key) ?: return@forEach
            if (!hosts.table(url)) {
                refused++
                return@forEach
            }
            renditions += rendition(key, url, shape)
        }
        // No usable key at all: a live broadcast (`*live*` keys only), or nothing for the table.
        val liveKeysOnly = renditions.isEmpty() && refused == 0 &&
            files.entries.keys.any { "live" in it }
        if (renditions.isEmpty() && refused == 0 && !liveKeysOnly) return null
        return meta(item, renditions, "table", refused, liveKeysOnly)
    }

    private data class Shape(val kind: MediaKind, val mimeType: String, val height: Int?)

    private fun tableShape(key: String): Shape? {
        MP4_KEY.matchEntire(key)?.let { match ->
            return match.groupValues[1].toIntOrNull()?.takeIf { it > 0 }
                ?.let { Shape(MediaKind.DIRECT, MP4, it) }
        }
        return when (key) {
            in HLS_KEYS -> Shape(MediaKind.HLS, HLS, null)
            in DASH_KEYS -> Shape(MediaKind.DASH, DASH, null)
            else -> null
        }
    }

    private fun shapeOf(key: String): Shape? {
        SHAPE_PROGRESSIVE_KEY.matchEntire(key)?.let { match ->
            return match.groupValues[1].toIntOrNull()?.takeIf { it > 0 }
                ?.let { Shape(MediaKind.DIRECT, MP4, it) }
        }
        return when {
            "live" in key -> null
            key.startsWith("hls") -> Shape(MediaKind.HLS, HLS, null)
            key.startsWith("dash") && key != "dash_uni" -> Shape(MediaKind.DASH, DASH, null)
            else -> null
        }
    }

    private fun rendition(key: String, url: String, shape: Shape) = VkRendition(
        key = key,
        url = url,
        kind = shape.kind,
        mimeType = shape.mimeType,
        height = shape.height,
        expiresAtEpochMs = VkUrls.expiresAtOf(url),
    )

    /**
     * The shape scan over [root]: renditions found inside an object naming the requested video;
     * an object naming another video is skipped whole, as are the not-media keys.
     */
    private fun byShape(
        root: JsonValue,
        ownerId: Long,
        videoId: Long,
        knownItem: JsonValue.Object?,
        hosts: VkMediaHosts,
    ): VkVideo? {
        val found = LinkedHashMap<String, VkRendition>()
        var owner: JsonValue.Object? = knownItem

        fun walk(value: JsonValue, depth: Int, inside: Boolean) {
            if (depth > MAX_SCAN_DEPTH) return
            when (value) {
                is JsonValue.Object -> {
                    val named = namedVideo(value)
                    if (named != null && named != ownerId to videoId) return
                    if (named != null && owner == null) owner = value
                    val within = inside || named != null
                    value.entries.forEach { (key, child) ->
                        if (key in NOT_MEDIA_KEYS) return@forEach
                        val url = (child as? JsonValue.Text)?.value
                        if (within && url != null && hosts.shape(url)) {
                            shapeOf(key)?.let { shape ->
                                found.getOrPut(url) { rendition(key, url, shape) }
                            }
                        } else {
                            walk(child, depth + 1, within)
                        }
                    }
                }

                is JsonValue.Array -> value.items.forEach { walk(it, depth + 1, inside) }
                else -> Unit
            }
        }

        walk(root, depth = 1, inside = knownItem != null)
        if (found.isEmpty()) return null
        return meta(owner, found.values.toList(), "shape scan")
    }

    /** The video an object names by its own `owner_id` and `id` (or `oid` and `vid`). */
    private fun namedVideo(value: JsonValue.Object): Pair<Long, Long>? {
        val owner = value["owner_id"].asLongOrNull ?: value["oid"].asLongOrNull
        val id = (if (value["owner_id"] != null) value["id"] else value["vid"]).asLongOrNull
        return if (owner != null && id != null) owner to id else null
    }

    private fun meta(
        item: JsonValue.Object?,
        renditions: List<VkRendition>,
        foundBy: String,
        refused: Int = 0,
        liveKeysOnly: Boolean = false,
    ): VkVideo {
        val liveState = item?.get("live_status").asStringOrNull
        return VkVideo(
            title = item?.get("title").asStringOrNull?.let(::decodeEntities)?.trim()
                ?.takeIf { it.isNotEmpty() && REPLACEMENT_CHARACTER !in it },
            thumbnailUrl = item?.let(::largestImage),
            durationMillis = item?.get("duration").asLongOrNull
                ?.takeIf { it in 1..MAX_DURATION_SECONDS }
                ?.times(1_000),
            renditions = ordered(renditions),
            isLive = liveState in LIVE_STATES || liveKeysOnly,
            foundBy = foundBy,
            refusedAddresses = refused,
        )
    }

    /** MP4 from the largest height down, then HLS, then DASH, each in VK's key order. */
    private fun ordered(renditions: List<VkRendition>): List<VkRendition> =
        renditions.sortedWith(
            compareBy<VkRendition> { it.kind.ordinal }
                .thenByDescending { it.height ?: 0 }
                .thenBy { rank(it.key) },
        )

    private fun rank(key: String): Int =
        MANIFEST_ORDER.indexOf(key).takeIf { it >= 0 } ?: Int.MAX_VALUE

    /** The largest `image[]` entry (width × height) with an HTTPS address. */
    private fun largestImage(item: JsonValue.Object): String? =
        item["image"].asArrayOrEmpty
            .mapNotNull { image ->
                val url = image["url"].asStringOrNull?.takeIf { it.startsWith("https://") }
                val width = image["width"].asLongOrNull ?: 0L
                val height = image["height"].asLongOrNull ?: 0L
                url?.let { it to width * height }
            }
            .maxByOrNull { it.second }
            ?.first

    /** HTML's basic entities (`&amp;`, `&quot;`, `&#39;`, `&#x27;`, …) in one pass. */
    fun decodeEntities(text: String): String {
        if ('&' !in text) return text
        return ENTITY.replace(text) { match ->
            val name = match.groupValues[1]
            when {
                name.startsWith("#x") || name.startsWith("#X") ->
                    name.substring(2).toIntOrNull(16)?.let(::codePoint)
                name.startsWith("#") -> name.substring(1).toIntOrNull()?.let(::codePoint)
                else -> NAMED_ENTITIES[name]
            } ?: match.value
        }
    }

    private fun codePoint(value: Int): String? =
        if (value in 1..0x10FFFF && value !in 0xD800..0xDFFF) {
            String(Character.toChars(value))
        } else {
            null
        }

    private val ENTITY = Regex("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z]{2,6});")
    private val NAMED_ENTITIES = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " ",
    )
    private const val REPLACEMENT_CHARACTER = '\uFFFD'
    private const val MAX_DURATION_SECONDS = 7L * 24 * 3600
}
