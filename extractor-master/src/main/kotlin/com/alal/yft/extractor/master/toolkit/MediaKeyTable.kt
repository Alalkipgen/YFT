/*
 * Provenance (Master toolkit T4, copied as data, not moved; main 34a41890):
 *   extractor-sites/.../facebook/FacebookPageParser.kt  MEDIA_KEYS, LEGACY_FIELDS
 *   extractor-sites/.../instagram/InstagramMedia.kt     video_versions, video_url, isPost
 *   extractor-sites/.../tiktok/TikTokPageParser.kt      playAddr, downloadAddr, bitrateInfo
 *   extractor-sites/.../x/XExtractor.kt                 video_info.variants
 *   extractor-sites/.../vimeo/VimeoConfigParser.kt      files.progressive/hls/dash
 * Spike recipes (PayloadRecipes) stay the source for L4; this table feeds L3.
 */
package com.alal.yft.extractor.master.toolkit

import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.master.recipes.NodeRule
import com.alal.yft.extractor.master.recipes.PayloadRecipes

/**
 * T4: the media-key table as data, versioned. L3 ([com.alal.yft.extractor.master.layers.ShapeLayer])
 * uses the known keys so it always finds what L4 finds; the hint words let it keep finding
 * media after a site renames a key. Changing a list here is a behavior change: bump [VERSION].
 */
internal object MediaKeyTable {
    const val VERSION = 1

    /** Known single-address keys and the MIME type L4 gives them (null: read from a sibling). */
    val ADDRESS_KEYS: Map<String, String?> = buildMap {
        PayloadRecipes.NODE_RULES.forEach { rule ->
            when (rule) {
                is NodeRule.Address -> put(rule.field, rule.mime)
                is NodeRule.AddressOrList -> put(rule.field, rule.mime)
                else -> Unit
            }
        }
        // main's keys that L4 does not list (yet).
        put("hd_src", "video/mp4")
        put("sd_src", "video/mp4")
        put("hd_src_no_ratelimit", "video/mp4")
        put("sd_src_no_ratelimit", "video/mp4")
        put("progressive_url", "video/mp4")
        put("playUrl", "video/mp4")
        put("video_dash_manifest", null)
    }

    /** Keys holding a list of address-bearing items (`node[key][i].url`) and the item MIME type. */
    val VERSION_LIST_KEYS: Map<String, String?> = buildMap {
        PayloadRecipes.NODE_RULES.forEach { rule ->
            if (rule is NodeRule.Versions) put(rule.path.last(), rule.mime)
        }
        put("progressive", "video/mp4") // Vimeo files.progressive
    }

    /** `parent.listKey` lists of plain addresses (TikTok `PlayAddr.UrlList`). */
    val ADDRESS_LIST_PARENTS: Map<String, String> = buildMap {
        PayloadRecipes.NODE_RULES.forEach { rule ->
            when (rule) {
                is NodeRule.AddressOrList -> put(rule.field.lowercase(), rule.mime)
                is NodeRule.VersionLists -> put(rule.listPath.first().lowercase(), rule.mime)
                else -> Unit
            }
        }
    }
    val ADDRESS_LIST_KEYS: Set<String> = PayloadRecipes.NODE_RULES.flatMap { rule ->
        when (rule) {
            is NodeRule.AddressOrList -> listOf(rule.list)
            is NodeRule.VersionLists -> listOf(rule.listPath.last())
            else -> emptyList()
        }
    }.toSet()

    /** Content ID fields, as L4 reads them (after `videoDetails.videoId`). */
    val ID_FIELDS: List<String> = PayloadRecipes.CONTENT_ID_FIELDS

    /** L4's rule: a plain `id` names media when one of these is present. */
    val MEDIA_ID_FIELDS: Set<String> = PayloadRecipes.MEDIA_ID_FIELDS + "video"

    /** L3 also takes a plain `id` from an entity that holds media directly. */
    val ENTITY_TYPENAME = Regex("""(?i)video|media|clip|reel|post|tweet""")
    val ENTITY_FIELDS = setOf(
        "title", "caption", "desc", "description", "owner", "author", "user", "name",
        "permalink_url", "url_title",
    )

    val MIME_FIELDS = listOf(
        "mimeType", "mime_type", "content_type", "contentType", "encodingFormat", "mime",
    )

    /** Sibling keys that show an object describes a media file. */
    val SHAPE_FIELDS = setOf(
        "width", "height", "bitrate", "bandwidth", "duration", "codec", "codecs", "codectype",
        "format", "quality", "definition", "gearname", "qualitytype", "datasize", "fps",
        "contentlength", "content_length", "size", "resolution", "rendition",
    )

    /** A key path with one of these may hold an address even without a file extension. */
    val MEDIA_HINTS = listOf(
        "play", "download", "video", "stream", "src", "file", "media", "hls", "dash",
        "manifest", "progressive", "playback", "mp4", "rendition", "variant",
    )
    /** Stricter hints: what a player fetches, not what a page links (`share_url`, `videoUrl`). */
    val PLAYER_HINTS = listOf(
        "play", "download", "stream", "playback", "mp4", "src", "hls", "dash", "manifest",
    )
    val IMAGE_HINTS = listOf(
        "cover", "thumb", "image", "poster", "avatar", "photo", "img", "pic", "display",
        "icon", "logo", "sprite", "storyboard", "banner", "still",
    )
    val PREVIEW_HINTS = listOf("preview", "teaser", "trailer", "animated", "hover", "loop_")
    val AD_HINTS = Regex("""(?i)(?:^|_|\b)(?:ads?|advert|vast|preroll|pre_roll|sponsor)(?:_|$|\b)""")

    /** Subtrees L3 never reads: YouTube streams belong to the YouTube module only (R2/R6). */
    val SKIP_SUBTREES = setOf("streamingData", "adPlacements", "playerAds", "adSlots")

    val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "avif", "heic", "heif", "bmp", "svg", "ico",
    )

    /**
     * A DRM statement on an object: L4's boolean flags; Facebook's `drm_info` only with a licence
     * (main's FacebookPageParser.drmAssessment: an empty `video_license_uri_map` is a public
     * reel); a non-empty DRM object such as Vimeo's `files.drm` (main's VimeoConfigParser); a
     * licence URL. Never bypassed: L3 stops.
     */
    val DRM_FLAG_FIELDS: List<String> = PayloadRecipes.DRM_FLAGS
    val DRM_INFO_FIELDS = setOf("drm_info", "drmInfo")
    private val DRM_OBJECT_FIELDS = setOf("drm", "widevine", "fairplay", "playready", "keySystems")
    private val LICENCE_FIELDS = setOf(
        "license_url", "licenseUrl", "licenseServerUrl", "license_server_url",
        "graph_api_video_license_uri",
    )

    fun drmStatement(node: JsonValue.Object): Boolean {
        val entries = node.entries
        if (DRM_FLAG_FIELDS.any { (entries[it] as? JsonValue.Bool)?.value == true }) return true
        if (DRM_INFO_FIELDS.any { licensed(entries[it]) }) return true
        if ((entries["drm"] as? JsonValue.Bool)?.value == true) return true
        if ((entries["drmFamilies"] as? JsonValue.Array)?.items?.isNotEmpty() == true) return true
        if (DRM_OBJECT_FIELDS.any { (entries[it] as? JsonValue.Object)?.entries?.isNotEmpty() == true }) {
            return true
        }
        return LICENCE_FIELDS.any { (entries[it] as? JsonValue.Text)?.value?.isNotBlank() == true }
    }

    private fun licensed(value: JsonValue?): Boolean {
        val info = when (value) {
            is JsonValue.Object -> value
            is JsonValue.Text -> value.value.takeIf { it.length <= 64 * 1024 }
                ?.let { BoundedJsonParser.parse(it, maxDepth = 16, maxNodes = 1_000) } as? JsonValue.Object
            else -> null
        } ?: return false
        val licences = when (val map = info.entries["video_license_uri_map"]) {
            is JsonValue.Object -> map.entries.isNotEmpty()
            is JsonValue.Array -> map.items.isNotEmpty()
            is JsonValue.Text -> map.value.isNotBlank()
            else -> false
        }
        val graph = info.entries["graph_api_video_license_uri"]
        return licences || (graph != null && graph != JsonValue.Null)
    }

    /** Config-level expiry in epoch seconds (Vimeo `request.expires`). */
    val EXPIRY_FIELDS = listOf("expires", "expires_at", "expiry")

    fun hinted(key: String?, words: List<String>): Boolean {
        val lower = key?.lowercase() ?: return false
        return words.any(lower::contains)
    }
}
