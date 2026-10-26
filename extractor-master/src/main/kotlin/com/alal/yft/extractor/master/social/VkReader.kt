/*
 * Provenance (VK-Master plan §4 V1, Appendix B step 3; copied, not moved; main 50fb8d0f: "# pending"
 * rows in toolkit-provenance.tsv's header, as VkClient's):
 *   extractor-sites/.../vk/VkEmbedParser.kt  CACHE_KEY, MAX_CACHES, MAX_SCAN_DEPTH, MP4, HLS, DASH,
 *                                         MP4_KEY, HLS_KEYS, DASH_KEYS, MANIFEST_ORDER,
 *                                         SHAPE_PROGRESSIVE_KEY, LIVE_STATES, NOT_MEDIA_KEYS,
 *                                         parseApi, prefetchCaches, closingBracket, itemOf,
 *                                         byTable, tableShape, shapeOf, byShape, namedVideo, meta,
 *                                         ordered, rank, largestImage, REPLACEMENT_CHARACTER,
 *                                         MAX_DURATION_SECONDS (the embed's player data and
 *                                         video.get's item: VK's key table, then the shape scan;
 *                                         the item's title, length and picture)
 * Adapted: one host rule for both answers and both reads (VkClient.mediaFile: VK's video servers,
 * never okcdn.ru's image host); the shape scan also takes a file whose address names its type
 * (.mp4, .m3u8, .mpd) under a key of another shape, and leaves poster-like keys out too; a live
 * broadcast keeps no file; what was found is counted for the judge (VkReading) and written as the
 * one video's answer that VK's recipe reads.
 */
package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.master.ContractAnswer
import java.net.URI
import java.util.Locale

/**
 * VK-Master plan §4 (V1, V2): Master's own read of VK's answers for the link's video — main's embed
 * page (`video_ext.php`: its `apiPrefetchCache`) and `video.get`'s answer (main's, or V2's own) —
 * bound to the video by its `owner_id` and `id` ([VkClient.Link]). VK's key table first (the item's
 * `files`: `mp4_{height}`, the HLS and DASH keys), the shape scan second (only when the table finds
 * nothing: files under rendition-like keys, or whose address names its type, inside an object
 * naming the video). Another video's data, the trailer, the poster and every picture, first
 * frames, timeline sprites, ads, subtitles and stats pixels are never media; only VK's video
 * servers count ([VkClient.mediaFile]). Reads only: no request, no log.
 *
 * What it found goes to the judge ([Read.reading]) and, with files, to VK's recipe as one video's
 * answer ([Read.answer]): the video's ID (`{owner}_{id}`), title, length, picture, the files'
 * earliest expiry and the files in VK's order (MP4 from the tallest down, then HLS, then DASH).
 */
internal object VkReader {
    const val MP4 = "video/mp4"
    const val HLS = "application/x-mpegURL"
    const val DASH = "application/dash+xml"

    /** One file of the video: VK's [key] for it, its address, its MIME [type] and [height]. */
    class File(val key: String, val url: String, val type: String, val height: Int?) {
        override fun toString(): String = "File($key)"
    }

    /**
     * What one answer held for the link's video: its [files] (none for a live broadcast), its
     * [title], [durationSeconds] and [thumbnail], the judge's [reading] and [byShape] (the files
     * came from the shape scan, not VK's key table).
     */
    class Read(
        val files: List<File> = emptyList(),
        val title: String? = null,
        val durationSeconds: Long? = null,
        val thumbnail: String? = null,
        val reading: VkReading = VkReading(),
        val byShape: Boolean = false,
    ) {
        /** The item's line: counts and kinds only, never an address, an ID or the title. */
        fun line(): String {
            val counts = listOf(MP4 to "MP4 files", HLS to "HLS", DASH to "DASH")
                .joinToString(", ") { (type, name) -> "$name ${files.count { it.type == type }}" }
            val refused = reading.refused.takeIf { it > 0 }?.let { "; $it on unknown hosts left out" }.orEmpty()
            return "$counts (${if (byShape) "the shape scan" else "VK's key table"})$refused; " +
                "title ${if (title != null) "known" else "unknown"}; " +
                "length ${if (durationSeconds != null) "known" else "unknown"}"
        }

        /**
         * The video's answer for VK's recipe, read from [finalUrl] (the answer's own address):
         * `{"id","title","duration","image","expires","files":[{"key","url","type","height"}]}`;
         * null without files.
         */
        fun answer(link: VkClient.Link, finalUrl: String): ContractAnswer? {
            if (files.isEmpty()) return null
            val body = buildString {
                append("{\"id\":")
                quote(link.contentId)
                title?.let {
                    append(",\"title\":")
                    quote(it)
                }
                durationSeconds?.let { append(",\"duration\":").append(it) }
                thumbnail?.let {
                    append(",\"image\":")
                    quote(it)
                }
                files.mapNotNull { VkClient.expiresAtOf(it.url) }.minOrNull()
                    ?.let { append(",\"expires\":").append(it / 1_000) }
                append(",\"files\":[")
                files.forEachIndexed { index, file ->
                    if (index > 0) append(',')
                    append("{\"key\":")
                    quote(file.key)
                    append(",\"url\":")
                    quote(file.url)
                    append(",\"type\":")
                    quote(file.type)
                    file.height?.let { append(",\"height\":").append(it) }
                    append('}')
                }
                append("]}")
            }
            return ContractAnswer(VkClient.SITE_ID, body, finalUrl)
        }

        override fun toString(): String = "Read(files=${files.size}, byShape=$byShape, $reading)"
    }

    /** Main's embed page [html] (B, B again, B2) for [link]'s video. */
    fun embed(html: String, link: VkClient.Link): Read {
        val caches = prefetchCaches(html)
        val item = caches.firstNotNullOfOrNull { itemOf(it, link) }
        if (item != null) return byTable(item) ?: byShape(item, link, item) ?: meta(item, emptyList())
        caches.firstNotNullOfOrNull { byShape(it, link, null) }?.let { return it }
        return Read(reading = VkReading(otherVideo = caches.any { othersOnly(it, link) }))
    }

    /**
     * `video.get`'s answer [json] for [link]'s video: VK's error (the judge reads its code), or the
     * `response.items[]` entry naming the video (the identity check), read as the embed's. An item
     * VK does not play (`restriction.can_play` 0) holds no file: restricted, deleted with VK's icon.
     */
    fun api(json: String, link: VkClient.Link): Read {
        val root = BoundedJsonParser.parse(json.trim()) as? JsonValue.Object ?: return Read()
        if (root["error"] is JsonValue.Object) return Read()
        val answer = JsonValue.Array(listOf(root))
        val item = itemOf(answer, link) ?: return Read(reading = VkReading(otherVideo = othersOnly(answer, link)))
        val restriction = item["restriction"]
        if (restriction["can_play"].asLongOrNull == 0L) {
            return Read(
                reading = VkReading(
                    item = true,
                    restricted = true,
                    deleted = restriction["icon_name"].asStringOrNull == VkAnswerJudge.DELETED_ICON,
                ),
            )
        }
        return byTable(item) ?: byShape(item, link, item) ?: meta(item, emptyList())
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

    /** The entries of `response.items[]` in [cache] (a prefetch cache, or `[answer]`). */
    private fun itemsOf(cache: JsonValue): Sequence<JsonValue.Object> =
        cache.asArrayOrEmpty.asSequence()
            .flatMap { entry -> entry["response"]["items"].asArrayOrEmpty.asSequence() }
            .filterIsInstance<JsonValue.Object>()

    /** The entry naming [link]'s video by its own `owner_id` and `id`. */
    private fun itemOf(cache: JsonValue, link: VkClient.Link): JsonValue.Object? =
        itemsOf(cache).firstOrNull { namedVideo(it) == link.ownerId to link.videoId }

    /** Whether [cache]'s entries name videos, none of them [link]'s. */
    private fun othersOnly(cache: JsonValue, link: VkClient.Link): Boolean {
        val named = itemsOf(cache).mapNotNull(::namedVideo).toList()
        return named.isNotEmpty() && named.none { it == link.ownerId to link.videoId }
    }

    /** VK's key table: the item's `files` under VK's keys, VK's video servers only. */
    private fun byTable(item: JsonValue.Object): Read? {
        val files = item["files"] as? JsonValue.Object ?: return null
        val found = LinkedHashMap<String, File>()
        var refused = 0
        files.entries.forEach { (key, value) ->
            val url = value.asStringOrNull?.trim() ?: return@forEach
            val shape = tableShape(key) ?: return@forEach
            if (!VkClient.mediaFile(url)) {
                refused++
                return@forEach
            }
            found.getOrPut(url) { File(key, url, shape.type, shape.height) }
        }
        // No usable key at all: a live broadcast (`*live*` keys only), or nothing for the table.
        val liveKeysOnly = found.isEmpty() && refused == 0 && files.entries.keys.any { "live" in it }
        if (found.isEmpty() && refused == 0 && !liveKeysOnly) return null
        return meta(item, found.values.toList(), refused = refused, liveKeysOnly = liveKeysOnly)
    }

    private class Shape(val type: String, val height: Int?)

    private fun tableShape(key: String): Shape? {
        MP4_KEY.matchEntire(key)?.let { match ->
            return match.groupValues[1].toIntOrNull()?.takeIf { it > 0 }?.let { Shape(MP4, it) }
        }
        return when (key) {
            in HLS_KEYS -> Shape(HLS, null)
            in DASH_KEYS -> Shape(DASH, null)
            else -> null
        }
    }

    private fun shapeOf(key: String): Shape? {
        SHAPE_PROGRESSIVE_KEY.matchEntire(key)?.let { match ->
            return match.groupValues[1].toIntOrNull()?.takeIf { it > 0 }?.let { Shape(MP4, it) }
        }
        return when {
            "live" in key -> null
            key.startsWith("hls") -> Shape(HLS, null)
            key.startsWith("dash") && key != "dash_uni" -> Shape(DASH, null)
            else -> null
        }
    }

    /** The value's shape: a file whose address names its type, under a key of another shape. */
    private fun typedByAddress(key: String, url: String): Shape? {
        if ("live" in key.lowercase(Locale.ROOT)) return null
        val path = runCatching { URI(url).rawPath }.getOrNull()?.lowercase(Locale.ROOT) ?: return null
        return when {
            path.endsWith(".mp4") -> Shape(MP4, null)
            path.endsWith(".m3u8") -> Shape(HLS, null)
            path.endsWith(".mpd") -> Shape(DASH, null)
            else -> null
        }
    }

    /**
     * The shape scan over [root]: files found inside an object naming [link]'s video; an object
     * naming another video is skipped whole, as are the not-media and poster-like keys.
     */
    private fun byShape(root: JsonValue, link: VkClient.Link, knownItem: JsonValue.Object?): Read? {
        val found = LinkedHashMap<String, File>()
        var owner: JsonValue.Object? = knownItem

        fun walk(value: JsonValue, depth: Int, inside: Boolean) {
            if (depth > MAX_SCAN_DEPTH) return
            when (value) {
                is JsonValue.Object -> {
                    val named = namedVideo(value)
                    if (named != null && named != link.ownerId to link.videoId) return
                    if (named != null && owner == null) owner = value
                    val within = inside || named != null
                    value.entries.forEach { (key, child) ->
                        if (key in NOT_MEDIA_KEYS || key in POSTER_KEYS) return@forEach
                        val url = (child as? JsonValue.Text)?.value?.trim()
                        if (within && url != null && VkClient.mediaFile(url)) {
                            (shapeOf(key) ?: typedByAddress(key, url))?.let { shape ->
                                found.getOrPut(url) { File(key, url, shape.type, shape.height) }
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
        return meta(owner, found.values.toList(), byShape = true)
    }

    /** The video an object names by its own `owner_id` and `id` (or `oid` and `vid`). */
    private fun namedVideo(value: JsonValue.Object): Pair<Long, Long>? {
        val owner = value["owner_id"].asLongOrNull ?: value["oid"].asLongOrNull
        val id = (if (value["owner_id"] != null) value["id"] else value["vid"]).asLongOrNull
        return if (owner != null && id != null) owner to id else null
    }

    private fun meta(
        item: JsonValue.Object?,
        files: List<File>,
        refused: Int = 0,
        liveKeysOnly: Boolean = false,
        byShape: Boolean = false,
    ): Read {
        val live = item?.get("live_status").asStringOrNull in LIVE_STATES || liveKeysOnly
        val kept = if (live) emptyList() else ordered(files)
        return Read(
            files = kept,
            title = item?.get("title").asStringOrNull?.let(VkAnswerJudge::decodeEntities)?.trim()
                ?.takeIf { it.isNotEmpty() && REPLACEMENT_CHARACTER !in it },
            durationSeconds = item?.get("duration").asLongOrNull?.takeIf { it in 1..MAX_DURATION_SECONDS },
            thumbnail = item?.let(::largestImage),
            reading = VkReading(
                rows = kept.size,
                byShape = byShape,
                refused = refused,
                item = item != null,
                live = live,
            ),
            byShape = byShape,
        )
    }

    /** MP4 from the largest height down, then HLS, then DASH, each in VK's key order. */
    private fun ordered(files: List<File>): List<File> =
        files.sortedWith(
            compareBy<File> { TYPE_ORDER.indexOf(it.type) }
                .thenByDescending { it.height ?: 0 }
                .thenBy { rank(it.key) },
        )

    private fun rank(key: String): Int = MANIFEST_ORDER.indexOf(key).takeIf { it >= 0 } ?: Int.MAX_VALUE

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

    private fun StringBuilder.quote(text: String) {
        append('"')
        for (char in text) {
            when {
                char == '"' -> append("\\\"")
                char == '\\' -> append("\\\\")
                char < ' ' -> append("\\u").append(String.format(Locale.US, "%04x", char.code))
                else -> append(char)
            }
        }
        append('"')
    }

    private const val CACHE_KEY = "\"apiPrefetchCache\""
    private const val MAX_CACHES = 3
    private const val MAX_SCAN_DEPTH = 48
    private val MP4_KEY = Regex("^mp4_([0-9]{2,4})$")
    private val HLS_KEYS = listOf("hls", "hls_fmp4", "hls_ondemand")

    /** MP4-DASH before WebM-DASH. */
    private val DASH_KEYS = listOf("dash_sep", "dash_ondemand", "dash_webm", "dash_webm_av1")
    private val MANIFEST_ORDER = HLS_KEYS + DASH_KEYS
    private val TYPE_ORDER = listOf(MP4, HLS, DASH)

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

    /** Master's own: the poster, previews and pictures under other names are never media either. */
    private val POSTER_KEYS = setOf(
        "poster",
        "preview",
        "previews",
        "thumb",
        "thumbs",
        "thumbnail",
        "animation",
        "photo",
        "photos",
    )
    private const val REPLACEMENT_CHARACTER = '\uFFFD'
    private const val MAX_DURATION_SECONDS = 7L * 24 * 3600
}
