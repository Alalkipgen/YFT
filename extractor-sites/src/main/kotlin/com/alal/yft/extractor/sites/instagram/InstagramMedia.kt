package com.alal.yft.extractor.sites.instagram

import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asBooleanOrNull
import com.alal.yft.extractor.api.json.asDoubleOrNull
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.api.json.path
import kotlin.math.roundToLong

/** One whole MP4 file Instagram lists for a video (`video_versions`, `video_url`): with sound. */
internal data class InstagramFile(val url: String, val width: Int?, val height: Int?)

/** One video or photo of a post; a carousel has several. */
internal data class InstagramItem(
    val isVideo: Boolean,
    val files: List<InstagramFile>,
    val dashManifest: String?,
    val durationMillis: Long?,
    val thumbnailUrl: String?,
) {
    /** A video with a file or a manifest; a signed-out answer can name a video without them. */
    val isPlayable: Boolean get() = isVideo && (files.isNotEmpty() || dashManifest != null)
}

/** A post as Instagram's answers describe it, its items in order. */
internal data class InstagramPost(
    val code: String?,
    val userName: String?,
    val fullName: String?,
    val caption: String?,
    val items: List<InstagramItem>,
) {
    val hasVideo: Boolean get() = items.any(InstagramItem::isVideo)

    val hasFiles: Boolean get() = items.any(InstagramItem::isPlayable)
}

/**
 * P49: reads a post from any of Instagram's answers, in either of its two shapes: the app API's
 * item (`video_versions`, `video_dash_manifest`, `carousel_media`, `user`, `caption`), which the
 * signed-in web page and `/api/v1/media/{id}/info/` give, and the GraphQL `shortcode_media`
 * (`video_url`, `dash_info`, `edge_sidecar_to_children`, `owner`), which the GraphQL query and
 * the embed page give. A post is found by its shortcode; an answer naming no code may hold one
 * post only.
 */
internal object InstagramMedia {
    private const val MAX_NODES_WALKED = 400_000
    private const val MAX_DEPTH = 48
    private const val MAX_ITEMS = 20
    private const val MILLIS = 1_000.0

    /** The post with [code] in [root], else the only post it holds, else null. */
    fun find(root: JsonValue?, code: String): InstagramPost? {
        val found = mutableListOf<JsonValue.Object>()
        var walked = 0
        fun walk(node: JsonValue?, depth: Int) {
            if (node == null || depth > MAX_DEPTH || walked++ > MAX_NODES_WALKED) return
            when (node) {
                is JsonValue.Object -> {
                    if (isPost(node)) {
                        found += node
                        if (codeOf(node) == code) return
                    }
                    node.entries.values.forEach { walk(it, depth + 1) }
                }

                is JsonValue.Array -> node.items.forEach { walk(it, depth + 1) }
                else -> Unit
            }
        }
        walk(root, 0)
        val post = found.firstOrNull { codeOf(it) == code }
            ?: found.singleOrNull()?.takeIf { codeOf(it) == null }
            ?: return null
        return postOf(post)
    }

    /** [text] parsed, then [find]; null when it is not JSON. */
    fun findIn(text: String, code: String): InstagramPost? =
        BoundedJsonParser.parse(text)?.let { find(it, code) }

    private fun isPost(node: JsonValue.Object): Boolean {
        val keys = node.entries.keys
        val media = "video_versions" in keys || "video_url" in keys ||
            "carousel_media" in keys || "edge_sidecar_to_children" in keys ||
            "image_versions2" in keys || "display_url" in keys
        val named = "code" in keys || "shortcode" in keys || "pk" in keys || "id" in keys
        return media && named
    }

    private fun codeOf(node: JsonValue): String? =
        node["code"].asStringOrNull ?: node["shortcode"].asStringOrNull

    private fun postOf(node: JsonValue): InstagramPost {
        val children = node["carousel_media"].asArrayOrEmpty.ifEmpty {
            node.path("edge_sidecar_to_children", "edges").asArrayOrEmpty.mapNotNull { it["node"] }
        }
        val items = children.ifEmpty { listOf(node) }.take(MAX_ITEMS).map(::itemOf)
        val owner = node["user"] ?: node["owner"]
        return InstagramPost(
            code = codeOf(node),
            userName = owner["username"].asStringOrNull,
            fullName = owner["full_name"].asStringOrNull,
            caption = node.path("caption", "text").asStringOrNull
                ?: node.path("edge_media_to_caption", "edges").asArrayOrEmpty.firstOrNull()
                    .path("node", "text").asStringOrNull,
            items = items,
        )
    }

    private fun itemOf(node: JsonValue): InstagramItem {
        val versions = node["video_versions"].asArrayOrEmpty.mapNotNull { version ->
            val url = version["url"].asStringOrNull?.takeIf(::isHttps) ?: return@mapNotNull null
            InstagramFile(
                url = url,
                width = version["width"].asLongOrNull?.toInt()?.takeIf { it > 0 },
                height = version["height"].asLongOrNull?.toInt()?.takeIf { it > 0 },
            )
        }
        val graphFile = node["video_url"].asStringOrNull?.takeIf(::isHttps)?.let { url ->
            InstagramFile(
                url = url,
                width = node.path("dimensions", "width").asLongOrNull?.toInt(),
                height = node.path("dimensions", "height").asLongOrNull?.toInt(),
            )
        }
        val files = (versions + listOfNotNull(graphFile)).distinctBy(InstagramFile::url)
        val manifest = node["video_dash_manifest"].asStringOrNull
            ?: node.path("dash_info", "video_dash_manifest").asStringOrNull
        val isVideo = files.isNotEmpty() || manifest != null ||
            node["is_video"].asBooleanOrNull == true || node["media_type"].asLongOrNull == 2L
        return InstagramItem(
            isVideo = isVideo,
            files = files,
            dashManifest = manifest,
            durationMillis = node["video_duration"].asDoubleOrNull
                ?.takeIf { it > 0 }?.let { (it * MILLIS).roundToLong() },
            thumbnailUrl = node.path("image_versions2", "candidates").asArrayOrEmpty.firstOrNull()
                .get("url").asStringOrNull?.takeIf(::isHttps)
                ?: node["display_url"].asStringOrNull?.takeIf(::isHttps),
        )
    }

    private fun isHttps(url: String): Boolean = url.startsWith("https://")
}

/**
 * P49: the JSON documents an Instagram HTML page carries: its `application/json` scripts (the
 * signed-in page's data), the embed page's `contextJSON` text and `__additionalDataLoaded`
 * object. Only documents that name the post's code or a video are parsed.
 */
internal object InstagramPageDocuments {
    private val JSON_SCRIPT = Regex(
        """<script\b[^>]*\btype="application/json"[^>]*>(.*?)</script>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )
    private const val CONTEXT_KEY = "\"contextJSON\":\""
    private const val ADDITIONAL_DATA = "__additionalDataLoaded("
    private const val MAX_DOCUMENTS = 40
    private const val MAX_OBJECT_CHARS = 4 * 1024 * 1024

    fun posts(html: String, code: String): List<InstagramPost> = buildList {
        documents(html, code).forEach { document ->
            InstagramMedia.findIn(document, code)?.let(::add)
        }
    }

    private fun documents(html: String, code: String): List<String> = buildList {
        JSON_SCRIPT.findAll(html).forEach { match ->
            val body = match.groupValues[1]
            if (size < MAX_DOCUMENTS && mentions(body, code)) add(body)
        }
        var at = html.indexOf(CONTEXT_KEY)
        while (at >= 0 && size < MAX_DOCUMENTS) {
            stringAt(html, at + CONTEXT_KEY.length - 1)
                ?.takeIf { mentions(it, code) }
                ?.let(::add)
            at = html.indexOf(CONTEXT_KEY, at + CONTEXT_KEY.length)
        }
        var data = html.indexOf(ADDITIONAL_DATA)
        while (data >= 0 && size < MAX_DOCUMENTS) {
            val open = html.indexOf('{', data)
            objectAt(html, open)?.takeIf { mentions(it, code) }?.let(::add)
            data = html.indexOf(ADDITIONAL_DATA, data + ADDITIONAL_DATA.length)
        }
    }

    private fun mentions(text: String, code: String): Boolean =
        code in text || "video_versions" in text || "video_url" in text

    /** The JSON string literal starting at the quote at [start], decoded; null if broken. */
    private fun stringAt(text: String, start: Int): String? {
        if (start !in text.indices || text[start] != '"') return null
        var index = start + 1
        while (index < text.length && index - start < MAX_OBJECT_CHARS) {
            when (text[index]) {
                '\\' -> index += 2
                '"' -> return (
                    BoundedJsonParser.parse(text.substring(start, index + 1))
                        as? JsonValue.Text
                    )?.value

                else -> index++
            }
        }
        return null
    }

    /** The balanced JSON object starting at the brace at [start]; null if it does not close. */
    private fun objectAt(text: String, start: Int): String? {
        if (start < 0 || start >= text.length || text[start] != '{') return null
        var depth = 0
        var inString = false
        var index = start
        while (index < text.length && index - start < MAX_OBJECT_CHARS) {
            val character = text[index]
            if (inString) {
                when (character) {
                    '\\' -> index++
                    '"' -> inString = false
                }
            } else {
                when (character) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> if (--depth == 0) return text.substring(start, index + 1)
                }
            }
            index++
        }
        return null
    }
}
