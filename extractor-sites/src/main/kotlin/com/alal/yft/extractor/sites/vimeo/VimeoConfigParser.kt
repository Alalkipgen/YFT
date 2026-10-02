package com.alal.yft.extractor.sites.vimeo

import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asDoubleOrNull
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.api.json.path
import java.util.Locale

/** How a Vimeo rendition is delivered, which decides how the resolver has to treat it. */
internal enum class VimeoDelivery {
    /** A complete progressive MP4 with audio and video already muxed. */
    PROGRESSIVE,

    /** An HLS master playlist; the resolver reads its real variants. */
    HLS,

    /** A DASH manifest; the resolver reads it and pairs the separate tracks itself. */
    DASH,
}

/** One playable rendition the player configuration exposed. */
internal data class VimeoRendition(
    val url: String,
    val label: String?,
    val delivery: VimeoDelivery,
    val mimeType: String?,
    val sizeBytes: Long?,
)

/** Non-sensitive description of a Vimeo clip plus its renditions. */
internal data class VimeoClip(
    val videoId: String?,
    val title: String?,
    val ownerName: String?,
    val thumbnailUrl: String?,
    val durationMillis: Long?,
    val expiresAtEpochMs: Long?,
    val renditions: List<VimeoRendition>,
)

internal sealed interface VimeoParseResult {
    data class Success(val clip: VimeoClip) : VimeoParseResult

    data class Failure(val reason: SiteExtractionFailure) : VimeoParseResult
}

/** What a clip page gave the adapter to work with. */
internal sealed interface VimeoPageOutcome {
    /** The page already embeds the whole player configuration. */
    data class InlineConfig(val json: String) : VimeoPageOutcome

    /** The page only names the configuration address, which the adapter fetches next. */
    data class ConfigAddress(val url: String) : VimeoPageOutcome

    data class Failure(val reason: SiteExtractionFailure) : VimeoPageOutcome
}

/**
 * Reads Vimeo's player configuration.
 *
 * Vimeo keeps the playable files in one JSON document that the clip page either embeds directly
 * or names through a `config_url` on its own player host. Both paths are supported, and anything
 * else is reported as a structured failure rather than guessed from the surrounding markup.
 */
internal object VimeoConfigParser {
    private const val MAX_CONFIG_CHARS = 2_000_000

    /** Markers that introduce the embedded configuration object on a clip or player page. */
    private val CONFIG_MARKERS = listOf(
        "window.playerConfig",
        "var config",
        "window.vimeo.clip_page_config",
    )

    /**
     * Page markers that describe an access outcome.
     *
     * They are consulted only when no configuration was found, so a page that parsed normally can
     * never be downgraded by a stray phrase.
     */
    private val ACCESS_MARKERS = listOf(
        "not available in your country" to SiteExtractionFailure.GEO_RESTRICTED,
        "isn't available in your country" to SiteExtractionFailure.GEO_RESTRICTED,
        "isn\u2019t available in your country" to SiteExtractionFailure.GEO_RESTRICTED,
        "geoblocked" to SiteExtractionFailure.GEO_RESTRICTED,
        "password to watch" to SiteExtractionFailure.LOGIN_REQUIRED,
        "enter the password" to SiteExtractionFailure.LOGIN_REQUIRED,
        "\"password\":true" to SiteExtractionFailure.LOGIN_REQUIRED,
        "private video" to SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
        "has been deleted" to SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
        "page not found" to SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
        "not have permission" to SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
    )

    fun parsePage(html: String): VimeoPageOutcome {
        CONFIG_MARKERS.forEach { marker ->
            val json = objectAfter(html, marker) ?: return@forEach
            val root = BoundedJsonParser.parse(json) ?: return@forEach
            if (root.path("request", "files") != null) return VimeoPageOutcome.InlineConfig(json)
            configAddress(root)?.let { return VimeoPageOutcome.ConfigAddress(it) }
        }
        return VimeoPageOutcome.Failure(
            accessFailure(html) ?: SiteExtractionFailure.RESPONSE_CHANGED,
        )
    }

    fun parseConfig(json: String): VimeoParseResult {
        val root = BoundedJsonParser.parse(json)
            ?: return VimeoParseResult.Failure(SiteExtractionFailure.MALFORMED_RESPONSE)

        val files = root.path("request", "files")
        if (files == null) {
            // Vimeo answers a gated clip with a message instead of files.
            return VimeoParseResult.Failure(
                accessFailure(json) ?: SiteExtractionFailure.RESPONSE_CHANGED,
            )
        }
        if (isDrmProtected(root, files)) {
            return VimeoParseResult.Failure(SiteExtractionFailure.DRM_PROTECTED)
        }

        val renditions = renditions(files)
        if (renditions.isEmpty()) {
            return VimeoParseResult.Failure(
                accessFailure(json) ?: SiteExtractionFailure.NO_MEDIA_FOUND,
            )
        }
        return VimeoParseResult.Success(clip(root, renditions))
    }

    /**
     * Collects renditions in a stable order: progressive files from the highest resolution down,
     * then the adaptive manifests. Only HTTPS URLs survive and duplicates collapse on first
     * occurrence.
     */
    private fun renditions(files: JsonValue?): List<VimeoRendition> {
        val collected = LinkedHashMap<String, VimeoRendition>()

        files["progressive"].asArrayOrEmpty
            .mapNotNull { entry ->
                val url = entry["url"].asStringOrNull?.httpsOrNull() ?: return@mapNotNull null
                val height = entry["height"].asLongOrNull
                height to VimeoRendition(
                    url = url,
                    label = entry["quality"].asStringOrNull?.readableQuality()
                        ?: height?.let { "${it}p" },
                    delivery = VimeoDelivery.PROGRESSIVE,
                    mimeType = entry["mime"].asStringOrNull ?: MP4_MIME_TYPE,
                    sizeBytes = entry["size"].asLongOrNull?.takeIf { it > 0 },
                )
            }
            .sortedByDescending { (height, _) -> height ?: 0 }
            .forEach { (_, rendition) -> collected.putIfAbsent(rendition.url, rendition) }

        manifestUrl(files["hls"])?.let { url ->
            collected.putIfAbsent(
                url,
                VimeoRendition(url, "Adaptive HLS", VimeoDelivery.HLS, HLS_MIME_TYPE, null),
            )
        }
        manifestUrl(files["dash"])?.let { url ->
            collected.putIfAbsent(
                url,
                VimeoRendition(url, "Adaptive DASH", VimeoDelivery.DASH, DASH_MIME_TYPE, null),
            )
        }
        return collected.values.toList()
    }

    /** Prefers the CDN the configuration itself marks as default before any other entry. */
    private fun manifestUrl(adaptive: JsonValue?): String? {
        val cdns = adaptive["cdns"] as? JsonValue.Object ?: return null
        val preferred = adaptive["default_cdn"].asStringOrNull?.let { cdns.entries[it] }
        val candidates = listOfNotNull(preferred) + cdns.entries.values
        return candidates.firstNotNullOfOrNull { cdn ->
            cdn["url"].asStringOrNull?.httpsOrNull() ?: cdn["avc_url"].asStringOrNull?.httpsOrNull()
        }
    }

    private fun isDrmProtected(root: JsonValue?, files: JsonValue?): Boolean =
        (files["drm"] != null && files["drm"] !is JsonValue.Null) ||
            root.path("video", "drm") != null && root.path("video", "drm") !is JsonValue.Null

    private fun clip(root: JsonValue?, renditions: List<VimeoRendition>): VimeoClip = VimeoClip(
        videoId = root.path("video", "id").asLongOrNull?.toString()
            ?: root.path("video", "id").asStringOrNull,
        title = root.path("video", "title").asStringOrNull,
        ownerName = root.path("video", "owner", "name").asStringOrNull,
        thumbnailUrl = thumbnailUrl(root.path("video", "thumbs")),
        durationMillis = root.path("video", "duration").asDoubleOrNull
            ?.takeIf { it > 0 }
            ?.let { seconds -> (seconds * 1_000).toLong() },
        expiresAtEpochMs = root.path("request", "expires").asLongOrNull
            ?.takeIf { it in PLAUSIBLE_EXPIRY_SECONDS }
            ?.let { it * 1_000 },
        renditions = renditions,
    )

    /** Takes the widest named thumbnail, falling back to the base image. */
    private fun thumbnailUrl(thumbs: JsonValue?): String? {
        val entries = (thumbs as? JsonValue.Object)?.entries ?: return null
        val widest = entries.entries
            .mapNotNull { (key, value) ->
                val width = key.toIntOrNull() ?: return@mapNotNull null
                width to value.asStringOrNull
            }
            .sortedByDescending { (width, _) -> width }
            .firstNotNullOfOrNull { (_, url) -> url?.httpsOrNull() }
        return widest ?: entries["base"].asStringOrNull?.httpsOrNull()
    }

    private fun accessFailure(text: String): SiteExtractionFailure? =
        ACCESS_MARKERS.firstOrNull { (marker, _) -> text.contains(marker, ignoreCase = true) }
            ?.second

    /** Finds the `config_url` the clip page names for its own player host. */
    private fun configAddress(root: JsonValue?): String? {
        val pending = ArrayDeque<JsonValue>()
        root?.let { pending += it }
        var visited = 0
        while (pending.isNotEmpty()) {
            visited += 1
            if (visited > MAX_SEARCH_NODES) return null
            when (val current = pending.removeFirst()) {
                is JsonValue.Object -> {
                    current.entries["config_url"].asStringOrNull
                        ?.takeIf(VimeoUrls::isConfigUrl)
                        ?.let { return it }
                    pending += current.entries.values
                }

                is JsonValue.Array -> pending += current.items
                else -> Unit
            }
        }
        return null
    }

    /**
     * Extracts the JSON object that follows [marker].
     *
     * The scan starts at the first `{` after the marker and tracks string state, so a brace inside
     * a caption cannot end the object early. Oversized objects are rejected outright.
     */
    private fun objectAfter(text: String, marker: String): String? {
        val markerIndex = text.indexOf(marker).takeIf { it >= 0 } ?: return null
        val start = text.indexOf('{', markerIndex).takeIf { it >= 0 } ?: return null

        var depth = 0
        var index = start
        var inString = false
        var escaped = false
        while (index < text.length) {
            if (index - start > MAX_CONFIG_CHARS) return null
            val character = text[index]
            when {
                escaped -> escaped = false
                character == '\\' && inString -> escaped = true
                character == '"' -> inString = !inString
                inString -> Unit
                character == '{' -> depth += 1
                character == '}' -> {
                    depth -= 1
                    if (depth == 0) return text.substring(start, index + 1)
                }
            }
            index += 1
        }
        return null
    }

    /** Normalizes quality tokens such as `1080p` or `hd` into a short, honest label. */
    private fun String.readableQuality(): String? {
        val token = trim().takeIf(String::isNotEmpty) ?: return null
        return when (val lowered = token.lowercase(Locale.US)) {
            "hd" -> "HD"
            "sd" -> "SD"
            "mobile" -> "Mobile"
            else -> lowered
        }
    }

    private fun String.httpsOrNull(): String? =
        takeIf { it.startsWith("https://", ignoreCase = true) }

    private const val MAX_SEARCH_NODES = 200_000
    private const val MP4_MIME_TYPE = "video/mp4"
    private const val HLS_MIME_TYPE = "application/x-mpegURL"
    private const val DASH_MIME_TYPE = "application/dash+xml"

    /** 2014-05 to 2100-01: wide enough for real links, narrow enough to reject noise. */
    private val PLAUSIBLE_EXPIRY_SECONDS = 1_400_000_000L..4_102_444_800L
}
