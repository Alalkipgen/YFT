package com.alal.yft.extractor.sites.facebook

import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asBooleanOrNull
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.api.json.path
import java.util.Locale

/** How a Facebook rendition is delivered, which decides how the resolver has to treat it. */
internal enum class FacebookDelivery {
    /** A complete progressive MP4 with audio and video already muxed. */
    PROGRESSIVE,

    /** A DASH manifest; the resolver reads it and pairs the separate tracks itself. */
    DASH_MANIFEST,
}

/** One playable rendition Facebook exposed for a post. */
internal data class FacebookRendition(
    val url: String,
    val label: String?,
    val delivery: FacebookDelivery,
)

/** Non-sensitive description of a Facebook video plus its renditions. */
internal data class FacebookPost(
    val videoId: String?,
    val ownerName: String?,
    val title: String?,
    val thumbnailUrl: String?,
    val durationMillis: Long?,
    val renditions: List<FacebookRendition>,
)

internal sealed interface FacebookParseResult {
    data class Success(val post: FacebookPost) : FacebookParseResult

    data class Failure(val reason: SiteExtractionFailure) : FacebookParseResult
}

/**
 * Reads the JSON Facebook embeds in its own page.
 *
 * Facebook moves the video node around between surfaces and releases, so the parser searches the
 * embedded `application/json` payloads for a node that actually carries delivery fields instead of
 * depending on one fixed path. Both the current `videoDeliveryResponseFragment` shape and the
 * older `playable_url` / `browser_native_*` fields are understood, because a single account can be
 * served either. When no such node exists the parser reports a structured failure rather than
 * scraping an arbitrary URL out of the HTML.
 */
internal object FacebookPageParser {
    private const val JSON_SCRIPT_TYPE = "application/json"
    private const val MAX_SCRIPTS = 80
    private const val MAX_SCRIPT_CHARS = 2_000_000
    private const val MAX_SEARCH_NODES = 300_000

    /** Delivery fields that mark a node as the video node rather than a related entity. */
    private val MEDIA_KEYS = listOf(
        "videoDeliveryResponseFragment",
        "videoDeliveryLegacyFields",
        "progressive_urls",
        "playable_url",
        "playable_url_quality_hd",
        "browser_native_hd_url",
        "browser_native_sd_url",
        "dash_manifest_url",
    )

    /** Legacy single-URL fields in descending quality order. */
    private val LEGACY_FIELDS = listOf(
        "playable_url_quality_hd" to "HD",
        "browser_native_hd_url" to "HD",
        "playable_url" to "SD",
        "browser_native_sd_url" to "SD",
    )

    /**
     * Page markers that describe an access outcome.
     *
     * They are only consulted once the page turned out to carry no playable media, so a page that
     * parsed normally can never be downgraded by a stray phrase. Region blocks are checked before
     * the generic unavailable copy, which the same page also shows.
     */
    private val ACCESS_MARKERS = listOf(
        "video_unavailable_geoblock" to SiteExtractionFailure.GEO_RESTRICTED,
        "isn't available in your location" to SiteExtractionFailure.GEO_RESTRICTED,
        "isn\u2019t available in your location" to SiteExtractionFailure.GEO_RESTRICTED,
        "not available in your country" to SiteExtractionFailure.GEO_RESTRICTED,
        "You must log in to continue" to SiteExtractionFailure.LOGIN_REQUIRED,
        "id=\"loginform\"" to SiteExtractionFailure.LOGIN_REQUIRED,
        "login_form_container" to SiteExtractionFailure.LOGIN_REQUIRED,
        "This content isn't available right now" to
            SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
        "This content isn\u2019t available right now" to
            SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
        "This video isn't available anymore" to SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
        "This video isn\u2019t available anymore" to
            SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
        "content_not_found" to SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
        "VideoNotFoundError" to SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
    )

    fun parse(html: String, expectedVideoId: String?): FacebookParseResult {
        val node = mediaNode(html, expectedVideoId)
            ?: return FacebookParseResult.Failure(
                accessFailure(html) ?: SiteExtractionFailure.RESPONSE_CHANGED,
            )
        if (isDrmProtected(node)) {
            return FacebookParseResult.Failure(SiteExtractionFailure.DRM_PROTECTED)
        }

        val renditions = renditions(node)
        if (renditions.isEmpty()) {
            return FacebookParseResult.Failure(
                accessFailure(html) ?: SiteExtractionFailure.NO_MEDIA_FOUND,
            )
        }
        return FacebookParseResult.Success(post(node, html, renditions))
    }

    /**
     * Finds the video node, preferring the one whose ID matches the page being extracted.
     *
     * A Facebook page also embeds suggested and related videos, so an ID match is what keeps the
     * adapter from returning a neighbouring video's media.
     */
    private fun mediaNode(html: String, expectedVideoId: String?): JsonValue.Object? {
        var fallback: JsonValue.Object? = null
        scriptPayloads(html).forEach { payload ->
            val root = BoundedJsonParser.parse(payload) ?: return@forEach
            val (node, matchesExpectedId) = search(root, expectedVideoId) ?: return@forEach
            if (matchesExpectedId) return node
            if (fallback == null) fallback = node
        }
        return fallback
    }

    private fun search(
        root: JsonValue,
        expectedVideoId: String?,
    ): Pair<JsonValue.Object, Boolean>? {
        val pending = ArrayDeque<JsonValue>()
        pending += root
        var visited = 0
        var fallback: JsonValue.Object? = null

        while (pending.isNotEmpty()) {
            visited += 1
            if (visited > MAX_SEARCH_NODES) break
            when (val current = pending.removeFirst()) {
                is JsonValue.Object -> {
                    if (isMediaNode(current)) {
                        if (expectedVideoId != null && videoId(current) == expectedVideoId) {
                            return current to true
                        }
                        if (fallback == null) fallback = current
                    }
                    pending += current.entries.values
                }

                is JsonValue.Array -> pending += current.items
                else -> Unit
            }
        }
        return fallback?.let { it to false }
    }

    private fun isMediaNode(node: JsonValue.Object): Boolean = MEDIA_KEYS.any { key ->
        val value = node.entries[key]
        value != null && value != JsonValue.Null
    }

    private fun videoId(node: JsonValue.Object): String? =
        node["video_id"].asStringOrNull
            ?: node["videoId"].asStringOrNull
            ?: node["id"].asStringOrNull

    /**
     * Collects renditions in a stable order: explicit progressive qualities first, then the legacy
     * single-URL fields, then the DASH manifest. Only HTTPS URLs survive and duplicates collapse on
     * first occurrence, so the highest-quality entry keeps its label.
     */
    private fun renditions(node: JsonValue.Object): List<FacebookRendition> {
        val collected = LinkedHashMap<String, FacebookRendition>()

        progressiveEntries(node).forEach { entry ->
            val url = entry["progressive_url"].asStringOrNull?.httpsOrNull() ?: return@forEach
            collected.putIfAbsent(
                url,
                FacebookRendition(
                    url = url,
                    label = qualityLabel(entry.path("metadata", "quality").asStringOrNull),
                    delivery = FacebookDelivery.PROGRESSIVE,
                ),
            )
        }

        val legacy = node["videoDeliveryLegacyFields"]
        LEGACY_FIELDS.forEach { (key, label) ->
            val url = node[key].asStringOrNull?.httpsOrNull()
                ?: legacy[key].asStringOrNull?.httpsOrNull()
                ?: return@forEach
            collected.putIfAbsent(
                url,
                FacebookRendition(url, label, FacebookDelivery.PROGRESSIVE),
            )
        }

        dashManifestUrls(node).forEach { url ->
            collected.putIfAbsent(
                url,
                FacebookRendition(url, "Adaptive", FacebookDelivery.DASH_MANIFEST),
            )
        }
        return collected.values.toList()
    }

    private fun progressiveEntries(node: JsonValue.Object): List<JsonValue> {
        val result = node.path("videoDeliveryResponseFragment", "videoDeliveryResponseResult")
        return result["progressive_urls"].asArrayOrEmpty + node["progressive_urls"].asArrayOrEmpty
    }

    private fun dashManifestUrls(node: JsonValue.Object): List<String> {
        val legacy = node["videoDeliveryLegacyFields"]
        val result = node.path("videoDeliveryResponseFragment", "videoDeliveryResponseResult")
        val direct = listOfNotNull(
            node["dash_manifest_url"].asStringOrNull,
            legacy["dash_manifest_url"].asStringOrNull,
        )
        val listed = result["dash_manifest_urls"].asArrayOrEmpty.mapNotNull { entry ->
            entry["manifest_url"].asStringOrNull
        }
        return (direct + listed).mapNotNull { it.httpsOrNull() }.distinct()
    }

    /** Turns Facebook quality tokens such as `FULL_HD` into a short, honest label. */
    private fun qualityLabel(quality: String?): String? {
        val token = quality?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return when (token.uppercase(Locale.US)) {
            "SD" -> "SD"
            "HD" -> "HD"
            "FULL_HD" -> "Full HD"
            "UHD", "4K" -> "4K"
            else -> token
        }
    }

    private fun isDrmProtected(node: JsonValue.Object): Boolean =
        node["is_drm_protected"].asBooleanOrNull == true ||
            node.path("videoDeliveryLegacyFields", "is_drm_protected").asBooleanOrNull == true ||
            (node["drm_info"].asStringOrNull != null)

    private fun post(
        node: JsonValue.Object,
        html: String,
        renditions: List<FacebookRendition>,
    ): FacebookPost = FacebookPost(
        videoId = videoId(node),
        ownerName = node.path("owner", "name").asStringOrNull,
        title = node.path("title", "text").asStringOrNull
            ?: node["title"].asStringOrNull
            ?: node.path("savable_description", "text").asStringOrNull
            ?: node.path("message", "text").asStringOrNull
            ?: metaContent(html, "og:title"),
        thumbnailUrl = listOf(
            node.path("preferred_thumbnail", "image", "uri").asStringOrNull,
            node.path("thumbnailImage", "uri").asStringOrNull,
            node.path("image", "uri").asStringOrNull,
            metaContent(html, "og:image"),
        ).firstNotNullOfOrNull { it?.httpsOrNull() },
        durationMillis = node["playable_duration_in_ms"].asLongOrNull?.takeIf { it > 0 }
            ?: node["length_in_second"].asLongOrNull?.takeIf { it > 0 }?.let { it * 1_000 }
            ?: node["playable_duration"].asLongOrNull?.takeIf { it > 0 }?.let { it * 1_000 },
        renditions = renditions,
    )

    private fun accessFailure(html: String): SiteExtractionFailure? =
        ACCESS_MARKERS.firstOrNull { (marker, _) -> html.contains(marker, ignoreCase = true) }
            ?.second

    /**
     * Yields the body of every `<script type="application/json">` block.
     *
     * Facebook splits its page state across many payloads, so the scan is bounded in both the
     * number and the size of the blocks it will read.
     */
    private fun scriptPayloads(html: String): Sequence<String> = sequence {
        var cursor = 0
        var emitted = 0
        while (emitted < MAX_SCRIPTS) {
            val tagStart = html.indexOf("<script", cursor, ignoreCase = true)
                .takeIf { it >= 0 } ?: return@sequence
            val tagEnd = html.indexOf('>', tagStart).takeIf { it >= 0 } ?: return@sequence
            val bodyEnd = html.indexOf("</script", tagEnd, ignoreCase = true)
                .takeIf { it >= 0 } ?: return@sequence
            cursor = bodyEnd + 1

            if (!html.substring(tagStart, tagEnd).contains(JSON_SCRIPT_TYPE, ignoreCase = true)) {
                continue
            }
            val body = html.substring(tagEnd + 1, bodyEnd).trim()
            if (body.isEmpty() || body.length > MAX_SCRIPT_CHARS) continue
            emitted += 1
            yield(body)
        }
    }

    /** Reads a single `<meta property="...">` value, used only as a metadata fallback. */
    private fun metaContent(html: String, property: String): String? {
        val tag = Regex(
            "<meta[^>]*?(?:property|name)=[\"']${Regex.escape(property)}[\"'][^>]*?>",
            RegexOption.IGNORE_CASE,
        ).find(html)?.value ?: return null
        val content = Regex("content=[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
            .find(tag)?.groupValues?.get(1) ?: return null
        return content.decodeHtmlEntities().trim().takeIf(String::isNotEmpty)
    }

    private fun String.decodeHtmlEntities(): String = this
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")

    private fun String.httpsOrNull(): String? =
        takeIf { it.startsWith("https://", ignoreCase = true) }
}
