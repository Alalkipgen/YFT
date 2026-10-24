/*
 * Provenance (social plan task 15, F2; copied, not moved; main 34a41890):
 *   extractor-sites/.../instagram/InstagramMedia.kt      InstagramPageDocuments.posts, documents,
 *                                                        mentions; InstagramMedia.find, isPost,
 *                                                        codeOf, postOf, itemOf, isHttps
 *   extractor-sites/.../instagram/InstagramExtractor.kt  ask (a page's post: the first with a
 *                                                        file, else the first)
 * Adapted: counts and marks only (each item's files counted, never kept; no title, owner or
 * caption read); BalancedJson's stringAt and objectAt (Master's copies of main's own).
 * Drift check: scripts/master-toolkit-drift.py (toolkit-provenance.tsv).
 */
package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asBooleanOrNull
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.api.json.path
import com.alal.yft.extractor.master.toolkit.BalancedJson

/**
 * Social plan task 15 (F2, the phone session of 2026-10-23: slot 5's real embed page, about 227k
 * characters, was "video without files" in main's Details but `SHAPE_CHANGED (no video read)` in
 * I2's): main's own rule for the post in an Instagram page, copied so I2 reads a page as main
 * does (main unchanged, S1) — the page's JSON documents that name the post's code or a video (its
 * `application/json` scripts, the embed page's `contextJSON` texts and `__additionalDataLoaded`
 * objects, at most 40), in each the post by its code (a node with a media key and a name key),
 * else the only post naming no code; of the posts, the first with a file, else the first. Each
 * item's video marks (a file, a manifest, `is_video`, `media_type` 2) and files (HTTPS
 * `video_versions` and `video_url`) are main's. Counts and marks only: no link, ID or word of the
 * page leaves it. Reads only; no request, no log.
 */
internal object InstagramPageRule {
    /** One item of main's post: a video by main's marks, its files' count, whether it has a manifest. */
    data class Item(val video: Boolean, val files: Int, val manifest: Boolean) {
        /** Main's `isPlayable`: a video with a file or a manifest. */
        val playable: Boolean get() = video && (files > 0 || manifest)
    }

    /** Main's post: its [node] and its [items] in order (a carousel's, else the node's own). */
    class Post(val node: JsonValue.Object, val items: List<Item>) {
        /** Main's `hasVideo`. */
        val video: Boolean get() = items.any(Item::video)

        /** Main's `hasFiles`. */
        val playable: Boolean get() = items.any(Item::playable)

        /** The post's files, every item's. */
        val files: Int get() = items.sumOf(Item::files)
    }

    /** A page by main's rule: its JSON [documents]' count and main's [post] in them (null: none). */
    class Page(val documents: Int, val post: Post?)

    /** [html], the page of the post [code], by main's rule ([InstagramPageDocuments] and `ask`). */
    fun page(html: String, code: String): Page {
        val documents = documents(html, code)
        val posts = documents.mapNotNull { document -> BoundedJsonParser.parse(document)?.let { find(it, code) } }
        return Page(documents.size, posts.firstOrNull(Post::playable) ?: posts.firstOrNull())
    }

    /** [node], a JSON answer's post, read by main's rule (its items, marks and files). */
    fun post(node: JsonValue.Object): Post = postOf(node)

    /** Main's `InstagramMedia.find`: the post with [code] in [root], else the only post it holds. */
    private fun find(root: JsonValue?, code: String): Post? {
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

    private fun postOf(node: JsonValue.Object): Post {
        val children = node["carousel_media"].asArrayOrEmpty.ifEmpty {
            node.path("edge_sidecar_to_children", "edges").asArrayOrEmpty.mapNotNull { it["node"] }
        }
        return Post(node, children.ifEmpty { listOf(node) }.take(MAX_ITEMS).map(::itemOf))
    }

    private fun itemOf(node: JsonValue): Item {
        val versions = node["video_versions"].asArrayOrEmpty.mapNotNull { version ->
            version["url"].asStringOrNull?.takeIf(::isHttps)
        }
        val graphFile = node["video_url"].asStringOrNull?.takeIf(::isHttps)
        val files = (versions + listOfNotNull(graphFile)).distinct().size
        val manifest = node["video_dash_manifest"].asStringOrNull
            ?: node.path("dash_info", "video_dash_manifest").asStringOrNull
        val video = files > 0 || manifest != null ||
            node["is_video"].asBooleanOrNull == true || node["media_type"].asLongOrNull == VIDEO_TYPE
        return Item(video = video, files = files, manifest = manifest != null)
    }

    private fun isHttps(url: String): Boolean = url.startsWith("https://")

    /** Main's `InstagramPageDocuments.documents`: the page's JSON documents naming the post or a video. */
    private fun documents(html: String, code: String): List<String> = buildList {
        JSON_SCRIPT.findAll(html).forEach { match ->
            val body = match.groupValues[1]
            if (size < MAX_DOCUMENTS && mentions(body, code)) add(body)
        }
        var at = html.indexOf(CONTEXT_KEY)
        while (at >= 0 && size < MAX_DOCUMENTS) {
            BalancedJson.stringAt(html, at + CONTEXT_KEY.length - 1)
                ?.takeIf { mentions(it, code) }
                ?.let(::add)
            at = html.indexOf(CONTEXT_KEY, at + CONTEXT_KEY.length)
        }
        var data = html.indexOf(ADDITIONAL_DATA)
        while (data >= 0 && size < MAX_DOCUMENTS) {
            val open = html.indexOf('{', data)
            BalancedJson.objectAt(html, open)?.takeIf { mentions(it, code) }?.let(::add)
            data = html.indexOf(ADDITIONAL_DATA, data + ADDITIONAL_DATA.length)
        }
    }

    private fun mentions(text: String, code: String): Boolean =
        code in text || "video_versions" in text || "video_url" in text

    private val JSON_SCRIPT = Regex(
        """<script\b[^>]*\btype="application/json"[^>]*>(.*?)</script>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )
    private const val CONTEXT_KEY = "\"contextJSON\":\""
    private const val ADDITIONAL_DATA = "__additionalDataLoaded("
    private const val MAX_DOCUMENTS = 40
    private const val MAX_NODES_WALKED = 400_000
    private const val MAX_DEPTH = 48
    private const val MAX_ITEMS = 20
    private const val VIDEO_TYPE = 2L
}
