package com.alal.yft.extractor.master.recipes

import com.alal.yft.extractor.api.SiteExtractionFailure

/**
 * One rule for one JSON object. Data only: [com.alal.yft.extractor.master.layers.RecipeLayer]
 * interprets it; nothing here fetches, signs or runs code. A renamed site key is a one-line
 * edit in [PayloadRecipes.NODE_RULES].
 */
internal sealed interface NodeRule {
    /** `node[field]` is one address; the MIME type is fixed or read from `node[mimeField]`. */
    data class Address(
        val field: String,
        val mime: String?,
        val mimeField: String? = null,
    ) : NodeRule

    /** `node[path...][i][url]`; per-item metadata is read from the item. */
    data class Versions(
        val path: List<String>,
        val url: String,
        val mime: String?,
        val mimeField: String? = null,
    ) : NodeRule

    /** `node[field]` is an address or an object holding `[list]` addresses. */
    data class AddressOrList(val field: String, val list: String, val mime: String) : NodeRule

    /** `node[array][i][listPath...]` lists addresses; metadata is read from the item. */
    data class VersionLists(
        val array: String,
        val listPath: List<String>,
        val mime: String,
    ) : NodeRule

    /** `node[field]` holds an inline DASH MPD document (whole-file tracks only). */
    data class InlineDash(val field: String) : NodeRule
}

/** Known delivered-page shapes. The order is also the order media is checked in. */
internal object PayloadRecipes {
    val NODE_RULES: List<NodeRule> = listOf(
        NodeRule.Address("browser_native_hd_url", "video/mp4"), // Facebook
        NodeRule.Address("browser_native_sd_url", "video/mp4"), // Facebook
        NodeRule.Address("playable_url", "video/mp4"), // Facebook
        NodeRule.Address("playable_url_quality_hd", "video/mp4"), // Facebook
        NodeRule.Address("video_url", "video/mp4"), // Instagram
        NodeRule.Address("contentUrl", null, mimeField = "encodingFormat"), // schema.org JSON-LD
        NodeRule.Address("dash_manifest_url", "application/dash+xml"), // Facebook
        NodeRule.Versions(listOf("video_versions"), "url", "video/mp4"), // Instagram
        NodeRule.AddressOrList("playAddr", "UrlList", "video/mp4"), // TikTok
        NodeRule.AddressOrList("downloadAddr", "UrlList", "video/mp4"), // TikTok
        NodeRule.VersionLists("bitrateInfo", listOf("PlayAddr", "UrlList"), "video/mp4"), // TikTok
        NodeRule.Versions(
            listOf("video_info", "variants"), "url", null, mimeField = "content_type",
        ), // X
        NodeRule.InlineDash("dash_manifest_xml_string"), // Facebook
        NodeRule.InlineDash("dash_manifest"), // Facebook
    )

    /** Content ID fields, in priority order, after YouTube's `videoDetails.videoId`. */
    val CONTENT_ID_FIELDS = listOf("shortcode", "code", "rest_id", "videoId", "video_id")

    /** A plain `id` only names media when one of these is present. */
    val MEDIA_ID_FIELDS = setOf(
        "browser_native_hd_url", "playable_url", "video_versions", "video_url",
        "dash_manifest_xml_string", "dash_manifest", "video_info",
    )

    const val PRIVATE_FLAG = "is_private"
    val DRM_FLAGS = listOf("isDrm", "is_drm_protected")

    val JSON_SCRIPT_TYPES = setOf("application/json", "application/ld+json")
    val JSON_SCRIPT_IDS = setOf("__UNIVERSAL_DATA_FOR_REHYDRATION__", "SIGI_STATE")

    /** Inline player JSON assigned in a script rather than delivered as a JSON script. */
    val ASSIGNED_PLAYER_JSON = Regex("""\bytInitialPlayerResponse\s*=\s*(?=\{)""")
}

/** TikTok's rehydration scope reports private and regional states as status codes. */
internal object TikTokStatusRecipe {
    const val SCOPE = "__DEFAULT_SCOPE__"
    val DETAIL_FIELDS = listOf("webapp.video-detail", "webapp.reflow.video.detail")
    val ID_PATH = arrayOf("itemInfo", "itemStruct", "id")
    val STATUS_FIELDS = listOf("statusCode", "statusCodeV2")

    /** Null for an unknown non-zero status: reading stops, but nothing is claimed. */
    fun failure(status: Long): SiteExtractionFailure? = when (status) {
        10101L, 10102L, 10204L, 10216L, 10217L, 10218L, 10221L ->
            SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE
        10231L -> SiteExtractionFailure.GEO_RESTRICTED
        else -> null
    }
}
