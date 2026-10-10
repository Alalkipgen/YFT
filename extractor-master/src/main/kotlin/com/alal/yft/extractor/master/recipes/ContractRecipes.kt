/*
 * Provenance (Master R8, copied, not moved; main 34a41890):
 *   extractor-sites/.../vimeo/VimeoConfigParser.kt  ACCESS_MARKERS (access phrases, read only
 *                                                   when the answer has no files), renditions
 *                                                   (progressive by height, then HLS/DASH with
 *                                                   the default CDN first), isDrmProtected
 *   extractor-sites/.../vimeo/VimeoExtractor.kt     configHeaders (anchored to the clip page)
 *   extractor-sites/.../x/XSyndication.kt           url, videoOf (MP4 files by bitrate, the
 *                                                   playlist only without them), isMediaUrl,
 *                                                   SIZE_IN_PATH, parse (the quoted post's
 *                                                   videos only when the post has none)
 *   extractor-sites/.../x/XExtractor.kt             answerHeaders, DEFAULT_MAX_ANSWER_BYTES,
 *                                                   displayTitle (first line, no trailing link)
 *   extractor-sites/.../x/XUrls.kt                  VIDEO_SUFFIX (`/video/N` names the item)
 * Adapted: the same endpoints, headers and keys as data that ContractLayer interprets.
 */
package com.alal.yft.extractor.master.recipes

import com.alal.yft.extractor.api.SiteExtractionFailure

/**
 * R8: one entry of a contract key table: where one site's own answer lists files. Data only;
 * [com.alal.yft.extractor.master.layers.ContractLayer] reads it. A renamed key is a one-line edit.
 */
internal data class ContractMedia(
    /** From the video node (or item); `*` visits every array item or object value. */
    val path: List<String>,
    /** Address fields of an object entry, first present wins; a text entry is its address. */
    val url: List<String> = listOf("url"),
    val mime: String? = null,
    val mimeField: String? = null,
    /** Only entries whose MIME type is this one (X lists MP4 files and a playlist together). */
    val only: String? = null,
    /** At a `*` over object values, the entry the parent's field names comes first. */
    val preferredBy: String? = null,
    /** One row for the entry list: the first usable address (one manifest per delivery). */
    val first: Boolean = false,
    /** Read only when the earlier entries found no file (X: the playlist backs up the MP4s). */
    val onlyIfNone: Boolean = false,
    val order: ContractOrder = ContractOrder.LISTED,
    /** The value is an inline DASH document (whole-file tracks), not an address. */
    val inlineDash: Boolean = false,
)

internal enum class ContractOrder { LISTED, HEIGHT_DESC, BITRATE_DESC }

/**
 * R8: one site's public player endpoint for one identified video (L2 contract), as its own
 * player or embed widget asks it. Endpoint placeholders: `{id}` content ID, `{hashQuery}` an
 * unlisted Vimeo hash as `?h=`, `{token}` X's widget token, `{href}` the encoded page address.
 */
internal data class ContractRecipe(
    val site: String,
    val endpoint: String,
    /** The answer must come from these hosts, also after redirects. */
    val answerHosts: Set<String>,
    /** Fixed request headers; `{page}` is the identified video's canonical page. */
    val headers: Map<String, String> = emptyMap(),
    /** A fixed agent (an agent rule main keeps); null sends the browser's own. */
    val agent: String? = null,
    /** The user's own cookie is replayed only inside this domain, as main does. */
    val cookieDomain: String? = null,
    val maxBytes: Long = 1L * 1024 * 1024,
    /** The answer is a page: its data scripts and the objects after [callMarkers] are read. */
    val html: Boolean = false,
    val callMarkers: List<String> = emptyList(),
    /** Where a JSON answer names its video; another value is another video's answer. */
    val idPaths: List<List<String>> = emptyList(),
    /** Pages: the video node is the object whose field among these is the content ID. */
    val nodeIdFields: List<String> = emptyList(),
    /** A list of the post's media; the page's number ([itemNumber]) or the first with files. */
    val items: List<String>? = null,
    val itemNumber: Regex? = null,
    val media: List<ContractMedia>,
    /** Files only from these hosts (a changed answer cannot point a download elsewhere). */
    val mediaHosts: Set<String>? = null,
    /** Picture size written in the address (`/vid/720x1280/`), width then height. */
    val sizeInUrl: Regex? = null,
    /** An object with `width`/`height` for files that state none, from the video node. */
    val sizePath: List<String>? = null,
    /** A present, non-null value means protected media: Master stops (DRM_PROTECTED). */
    val drmPaths: List<List<String>> = emptyList(),
    /** Checked only when no file was found, so a stray phrase never downgrades an answer. */
    val accessMarkers: List<Pair<String, SiteExtractionFailure>> = emptyList(),
    /** Epoch seconds the answer's files stop working. */
    val expiryPath: List<String>? = null,
    val titlePath: List<String>? = null,
    /** The title is a post's text: its first line without a trailing link, 80 characters. */
    val postText: Boolean = false,
    /** From the chosen item when the recipe has [items], else from the video node. */
    val durationPath: List<String>? = null,
    val durationInMillis: Boolean = false,
    /** The poster picture (HTTPS only), from the chosen item, else from the video node. */
    val thumbnailPath: List<String>? = null,
    /** Another post the answer embeds; its files count only when the post itself has none. */
    val fallbackPath: List<String>? = null,
)

internal object ContractRecipes {
    private const val ACCEPT_JSON = "application/json, text/plain;q=0.9"
    private const val LANGUAGE = "en-US,en;q=0.9"

    /** Vimeo's player configuration on its own player host; the unlisted hash is kept. */
    val VIMEO = ContractRecipe(
        site = "vimeo",
        endpoint = "https://player.vimeo.com/video/{id}/config{hashQuery}",
        answerHosts = setOf("player.vimeo.com"),
        headers = mapOf("Accept" to ACCEPT_JSON, "Accept-Language" to LANGUAGE, "Referer" to "{page}"),
        cookieDomain = "vimeo.com",
        idPaths = listOf(listOf("video", "id")),
        media = listOf(
            ContractMedia(
                listOf("request", "files", "progressive", "*"),
                mime = "video/mp4", mimeField = "mime", order = ContractOrder.HEIGHT_DESC,
            ),
            ContractMedia(
                listOf("request", "files", "hls", "cdns", "*"), url = listOf("url", "avc_url"),
                mime = "application/x-mpegURL", preferredBy = "default_cdn", first = true,
            ),
            ContractMedia(
                listOf("request", "files", "dash", "cdns", "*"), url = listOf("url", "avc_url"),
                mime = "application/dash+xml", preferredBy = "default_cdn", first = true,
            ),
        ),
        drmPaths = listOf(listOf("request", "files", "drm"), listOf("video", "drm")),
        accessMarkers = listOf(
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
        ),
        expiryPath = listOf("request", "expires"),
        titlePath = listOf("video", "title"),
        durationPath = listOf("video", "duration"),
    )

    /**
     * X's embed-widget answer (`tweet-result`, the widget's own `{token}`): no X cookie, its host
     * is not x.com. MP4 files (each with its sound) by bitrate; the playlist only without them.
     */
    val X = ContractRecipe(
        site = "x",
        endpoint = "https://cdn.syndication.twimg.com/tweet-result?id={id}&token={token}&lang=en",
        answerHosts = setOf("cdn.syndication.twimg.com"),
        headers = mapOf(
            "Accept" to ACCEPT_JSON,
            "Accept-Language" to LANGUAGE,
            "Origin" to "https://platform.twitter.com",
            "Referer" to "https://platform.twitter.com/",
        ),
        maxBytes = 2L * 1024 * 1024,
        idPaths = listOf(listOf("id_str")),
        items = listOf("mediaDetails"),
        itemNumber = Regex("/video/([1-4])$"),
        media = listOf(
            ContractMedia(
                listOf("video_info", "variants", "*"), mimeField = "content_type",
                only = "video/mp4", order = ContractOrder.BITRATE_DESC,
            ),
            ContractMedia(
                listOf("video_info", "variants", "*"), mimeField = "content_type",
                only = "application/x-mpegURL", first = true, onlyIfNone = true,
            ),
        ),
        mediaHosts = setOf("video.twimg.com"),
        sizeInUrl = Regex("/vid/(?:[a-z0-9]+/)?([0-9]{2,5})x([0-9]{2,5})/"),
        titlePath = listOf("text"),
        postText = true,
        durationPath = listOf("video_info", "duration_millis"),
        durationInMillis = true,
        thumbnailPath = listOf("media_url_https"),
        fallbackPath = listOf("quoted_tweet"),
    )

    val ALL: List<ContractRecipe> = listOf(VIMEO, X)

    fun of(site: String): ContractRecipe? = ALL.firstOrNull { it.site == site }
}
