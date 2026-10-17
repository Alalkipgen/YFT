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
 *   extractor-sites/.../facebook/FacebookPageParser.kt  MEDIA_KEYS, LEGACY_FIELDS (best first),
 *                                                   progressiveEntries, inlineManifests,
 *                                                   dashManifestUrls (only without inline
 *                                                   tracks), drmAssessment, videoId, post
 *                                                   (title, thumbnail, duration fields)
 *   extractor-sites/.../facebook/FacebookPageIdentity.kt  AVC_LADDER_USER_AGENT (desktop Safari)
 *   extractor-sites/.../facebook/FacebookExtractor.kt  publicPageHeaders (no session)
 *   extractor-sites/.../tiktok/TikTokPageParser.kt  post (a photo post has no video, `isDrm`,
 *                                                   caption, cover fields, duration in seconds),
 *                                                   qualities (`bitrateInfo` fields, the play
 *                                                   address, the download address apart),
 *                                                   TikTokQuality.WATERMARK_LABEL (the download
 *                                                   address's row)
 *   extractor-sites/.../tiktok/TikTokExtractor.kt   DEFAULT_MAX_PAGE_BYTES, TIKTOK_REFERER,
 *                                                   pageHeaders (navigation headers, Referer;
 *                                                   Master sends no cookie)
 *   extractor-sites/.../instagram/InstagramMedia.kt  postOf (`carousel_media`, the sidecar's
 *                                                   children, else the post itself), itemOf
 *                                                   (`video_versions`, `video_url` with its
 *                                                   `dimensions`, both DASH manifests, duration
 *                                                   in seconds, thumbnail fields), caption fields
 *   extractor-sites/.../instagram/InstagramUrls.kt  embedUrl, IMAGE_INDEX (a carousel item),
 *                                                   isLoginWall (login and checkpoint paths)
 *   extractor-sites/.../instagram/InstagramExtractor.kt  DEFAULT_MAX_PAGE_BYTES, pageHeaders
 *                                                   (no cookie for the embed), displayTitle
 *                                                   (the caption, else the owner's name)
 * Adapted: the same endpoints, headers and keys as data that ContractLayer interprets.
 */
package com.alal.yft.extractor.master.recipes

import com.alal.yft.core.model.media.PageNavigationHeaders
import com.alal.yft.extractor.api.SiteExtractionFailure

/**
 * R8: one entry of a contract key table: where one site's own answer lists files. Data only;
 * [com.alal.yft.extractor.master.layers.ContractLayer] reads it. A renamed key is a one-line edit.
 */
internal data class ContractMedia(
    /** From the video node (or item); `*` visits every array item or object value. */
    val path: List<String>,
    /**
     * Address fields of an object entry (dotted paths), first present wins; a text entry is its
     * address; a list (`UrlList`) is one file's mirrors: its first usable address.
     */
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
    /** Read only when no inline DASH track was found (its files replace the manifest's address). */
    val unlessInline: Boolean = false,
    val order: ContractOrder = ContractOrder.LISTED,
    /** The value is an inline DASH document (whole-file tracks), not an address. */
    val inlineDash: Boolean = false,
    /** Where the entry states its size, bitrate, bytes and codec, if not under the usual names. */
    val fields: ContractFields = ContractFields(),
    /** From the video node: the object that states those fields for files that state none. */
    val metaPath: List<String>? = null,
    /** Shown after the title, so the row says what it is (main's "With TikTok watermark"). */
    val label: String? = null,
)

/**
 * R8: one entry's own field names (dotted paths, first present wins). An empty list reads
 * nothing (a watermarked file has no bitrate of its own).
 */
internal data class ContractFields(
    val width: List<String> = listOf("width"),
    val height: List<String> = listOf("height"),
    val bitrate: List<String> = listOf("bitrate"),
    val bytes: List<String> = listOf("contentLength"),
    /** Texts naming the codec (`h264`, `bytevc1`), read as main's TikTok `codecOf`. */
    val codec: List<String> = emptyList(),
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
    /** Pages: the video node is the object whose field (dotted path) is the content ID. */
    val nodeIdFields: List<String> = emptyList(),
    /** Pages: JSON carried as JSON text (`"contextJSON":"{…}"`) is read as a document too. */
    val embeddedJson: Boolean = false,
    /**
     * Where a post lists its media (`*` visits each item), the first non-empty list winning; the
     * page's number ([itemNumber]) or the first item with files. Without a list the node is the
     * post's only item.
     */
    val items: List<List<String>> = emptyList(),
    val itemNumber: Regex? = null,
    val media: List<ContractMedia>,
    /** Files only from these hosts (a changed answer cannot point a download elsewhere). */
    val mediaHosts: Set<String>? = null,
    /** Picture size written in the address (`/vid/720x1280/`), width then height. */
    val sizeInUrl: Regex? = null,
    /** A present, non-null value means protected media: Master stops (DRM_PROTECTED). */
    val drmPaths: List<List<String>> = emptyList(),
    /** A present, non-empty value means a post without a video (photos): NO_MEDIA_FOUND. */
    val noVideoPaths: List<List<String>> = emptyList(),
    /**
     * A field stating this value marks a photo (`is_video` false, `media_type` 1). When no file
     * was found and every item (else the node) is marked, the post has no video: NO_MEDIA_FOUND.
     */
    val photoMarkers: List<Pair<List<String>, String>> = emptyList(),
    /** Answers ending on these paths of the site (its login page) answer nothing. */
    val wallPaths: List<String> = emptyList(),
    /** Checked only when no file was found, so a stray phrase never downgrades an answer. */
    val accessMarkers: List<Pair<String, SiteExtractionFailure>> = emptyList(),
    /** Epoch seconds the answer's files stop working. */
    val expiryPath: List<String>? = null,
    /** The first text found wins, as for the duration and poster paths. */
    val titlePaths: List<List<String>> = emptyList(),
    /** The title is a post's text: its first line without a trailing link, 80 characters. */
    val postText: Boolean = false,
    /** Without a title: a name the answer states, written into its template (`{} on Instagram`). */
    val nameTitles: List<Pair<List<String>, String>> = emptyList(),
    /** From the chosen item when the recipe has [items], else from the video node. */
    val durationMillisPaths: List<List<String>> = emptyList(),
    val durationSecondsPaths: List<List<String>> = emptyList(),
    /** The poster picture (HTTPS only), from the chosen item, else from the video node. */
    val thumbnailPaths: List<List<String>> = emptyList(),
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
        titlePaths = listOf(listOf("video", "title")),
        durationSecondsPaths = listOf(listOf("video", "duration")),
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
        items = listOf(listOf("mediaDetails", "*")),
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
        titlePaths = listOf(listOf("text")),
        postText = true,
        durationMillisPaths = listOf(listOf("video_info", "duration_millis")),
        thumbnailPaths = listOf(listOf("media_url_https")),
        fallbackPath = listOf("quoted_tweet"),
    )

    private const val MP4 = "video/mp4"
    private const val DASH = "application/dash+xml"
    private val DELIVERY = listOf("videoDeliveryResponseFragment", "videoDeliveryResponseResult")
    private val LEGACY = listOf("videoDeliveryLegacyFields")

    /** Main's FacebookPageIdentity.AVC_LADDER_USER_AGENT: desktop Safari, the public page's. */
    private const val SAFARI =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/17.5 Safari/605.1.15"

    /** A field of the video node and the same field of its legacy delivery copy. */
    private fun both(field: String, media: (List<String>) -> ContractMedia) =
        listOf(media(listOf(field)), media(LEGACY + field))

    /**
     * Facebook's embedded-video player page for the identified video (`plugins/video.php`),
     * asked as desktop Safari **without** the user's session, as main asks its public page: one
     * page for every surface (watch, reel, page video; live check 2026-10-10). Its access
     * wording is an anonymous view, so it never decides access; only DRM stops. The key table
     * also reads main's page shapes (delivery fragment, legacy fields, inline DASH), so a
     * changed page and the embed share one reader.
     */
    val FACEBOOK = ContractRecipe(
        site = "facebook",
        endpoint = "https://www.facebook.com/plugins/video.php?href={href}",
        answerHosts = setOf("www.facebook.com"),
        headers = mapOf(
            "Accept" to PageNavigationHeaders.ACCEPT,
            "Accept-Language" to LANGUAGE,
            "Sec-Fetch-Mode" to PageNavigationHeaders.FETCH_MODE,
            "Referer" to "https://www.facebook.com/",
        ),
        agent = SAFARI,
        maxBytes = 6L * 1024 * 1024,
        html = true,
        callMarkers = listOf(".handle("),
        nodeIdFields = listOf("video_id", "videoId", "id"),
        media = buildList {
            val progressive = { path: List<String> ->
                ContractMedia(path + listOf("progressive_urls", "*"), url = listOf("progressive_url"), mime = MP4)
            }
            add(progressive(DELIVERY))
            add(progressive(emptyList()))
            listOf("playable_url_quality_hd", "browser_native_hd_url", "playable_url", "browser_native_sd_url")
                .forEach { field -> addAll(both(field) { ContractMedia(it, mime = MP4) }) }
            add(ContractMedia(listOf("hd_src"), mime = MP4))
            add(ContractMedia(listOf("sd_src"), mime = MP4))
            listOf("dash_manifest_xml_string", "dash_manifest")
                .forEach { field -> addAll(both(field) { ContractMedia(it, inlineDash = true) }) }
            add(ContractMedia(DELIVERY + "dash_manifest", inlineDash = true))
            add(ContractMedia(DELIVERY + listOf("dash_manifests", "*", "manifest_xml"), inlineDash = true))
            addAll(both("dash_manifest_url") { ContractMedia(it, mime = DASH, unlessInline = true) })
            add(
                ContractMedia(
                    DELIVERY + listOf("dash_manifest_urls", "*"), url = listOf("manifest_url"),
                    mime = DASH, unlessInline = true,
                ),
            )
        },
        drmPaths = listOf(
            listOf("is_drm_protected"), LEGACY + "is_drm_protected",
            listOf("drm_info", "video_license_uri_map"), listOf("drm_info", "graph_api_video_license_uri"),
            LEGACY + listOf("drm_info", "video_license_uri_map"),
            LEGACY + listOf("drm_info", "graph_api_video_license_uri"),
            listOf("videoLicenseUriMap"), listOf("graphApiVideoLicenseUri"),
        ),
        titlePaths = listOf(
            listOf("title", "text"), listOf("title"), listOf("savable_description", "text"),
            listOf("message", "text"),
        ),
        durationMillisPaths = listOf(listOf("playable_duration_in_ms")),
        durationSecondsPaths = listOf(listOf("length_in_second"), listOf("playable_duration")),
        thumbnailPaths = listOf(
            listOf("preferred_thumbnail", "image", "uri"), listOf("thumbnailImage", "uri"),
            listOf("image", "uri"),
        ),
    )

    private const val TIKTOK_REFERER = "https://www.tiktok.com/"

    /** Main's `qualities`: one `bitrateInfo` entry is one file, its mirrors in `PlayAddr.UrlList`. */
    private val TIKTOK_QUALITY = ContractFields(
        width = listOf("PlayAddr.Width"),
        height = listOf("PlayAddr.Height"),
        bitrate = listOf("Bitrate"),
        bytes = listOf("PlayAddr.DataSize", "DataSize"),
        codec = listOf("CodecType", "PlayAddr.UrlKey"),
    )

    /**
     * TikTok's embed player page (`embed/v2`) for the identified post, asked as the browser
     * (the tab's own agent, never YFT's) **without** the user's cookies; its files open without
     * any (live check 2026-10-10). Its node is `videoData` (the post's `itemInfos`, a photo
     * post's `imagePostInfo`). The key table also reads main's page shapes (`itemStruct`,
     * `bitrateInfo`, the play address; the watermarked download address only without them),
     * so a changed embed and main's page share one reader. Anonymous: only DRM and a photo post
     * stop; any other wording goes to the user's own playback.
     */
    val TIKTOK = ContractRecipe(
        site = "tiktok",
        endpoint = "https://www.tiktok.com/embed/v2/{id}",
        answerHosts = setOf("www.tiktok.com"),
        headers = mapOf(
            "Accept" to PageNavigationHeaders.ACCEPT,
            "Accept-Language" to LANGUAGE,
            "Sec-Fetch-Mode" to PageNavigationHeaders.FETCH_MODE,
            "Referer" to TIKTOK_REFERER,
        ),
        maxBytes = 3L * 1024 * 1024,
        html = true,
        nodeIdFields = listOf("itemInfos.id", "id"),
        media = listOf(
            ContractMedia(
                listOf("itemInfos", "video", "urls"), mime = MP4,
                metaPath = listOf("itemInfos", "video", "videoMeta"),
            ),
            ContractMedia(
                listOf("video", "bitrateInfo", "*"), url = listOf("PlayAddr.UrlList"), mime = MP4,
                fields = TIKTOK_QUALITY, order = ContractOrder.BITRATE_DESC,
            ),
            ContractMedia(
                listOf("video", "playAddr"), url = listOf("UrlList"), mime = MP4,
                fields = ContractFields(codec = listOf("codecType")), metaPath = listOf("video"),
            ),
            ContractMedia(
                listOf("video", "downloadAddr"), url = listOf("UrlList"), mime = MP4,
                fields = ContractFields(bitrate = emptyList(), codec = listOf("codecType")),
                metaPath = listOf("video"), onlyIfNone = true, label = "With TikTok watermark",
            ),
        ),
        drmPaths = listOf(listOf("itemInfos", "video", "isDrm"), listOf("video", "isDrm")),
        noVideoPaths = listOf(listOf("imagePostInfo"), listOf("imagePost")),
        titlePaths = listOf(listOf("itemInfos", "text"), listOf("desc")),
        durationSecondsPaths = listOf(
            listOf("itemInfos", "video", "videoMeta", "duration"), listOf("video", "duration"),
        ),
        thumbnailPaths = listOf(
            listOf("itemInfos", "covers", "0"), listOf("itemInfos", "coversOrigin", "0"),
            listOf("video", "cover"), listOf("video", "originCover"), listOf("video", "dynamicCover"),
        ),
    )

    /** Main's InstagramExtractor names its embed rows after the owner when there is no caption. */
    private fun owner(field: String, template: String) =
        listOf(listOf("user", field) to template, listOf("owner", field) to template)

    /**
     * Instagram's public embed page (`/p/{code}/embed/captioned/`), asked as the browser
     * **without** the user's cookies, as main's last answer: its `contextJSON` text holds the
     * post (`shortcode_media`: `video_url`, `dimensions`, a sidecar's children), and its files
     * open without any (live check 2026-10-10). The key table also reads main's other shapes
     * (the app API's `video_versions`, `carousel_media`, inline DASH), so a changed embed and
     * main's answers share one reader. Anonymous: a photo post stops; a login page or any other
     * wording goes to the user's own playback.
     */
    val INSTAGRAM = ContractRecipe(
        site = "instagram",
        endpoint = "https://www.instagram.com/p/{id}/embed/captioned/",
        answerHosts = setOf("www.instagram.com"),
        headers = mapOf(
            "Accept" to PageNavigationHeaders.ACCEPT,
            "Accept-Language" to LANGUAGE,
            "Sec-Fetch-Mode" to PageNavigationHeaders.FETCH_MODE,
            "Referer" to "https://www.instagram.com/",
        ),
        maxBytes = 6L * 1024 * 1024,
        html = true,
        nodeIdFields = listOf("shortcode", "code"),
        embeddedJson = true,
        items = listOf(
            listOf("carousel_media", "*"),
            listOf("edge_sidecar_to_children", "edges", "*", "node"),
        ),
        itemNumber = Regex("[?&]img_index=([0-9]{1,2})(?:&|$)"),
        media = listOf(
            ContractMedia(listOf("video_versions", "*"), mime = MP4),
            ContractMedia(listOf("video_url"), mime = MP4, metaPath = listOf("dimensions")),
            ContractMedia(listOf("video_dash_manifest"), inlineDash = true),
            ContractMedia(listOf("dash_info", "video_dash_manifest"), inlineDash = true),
        ),
        photoMarkers = listOf(listOf("is_video") to "false", listOf("media_type") to "1"),
        wallPaths = listOf("/accounts/login", "/challenge"),
        titlePaths = listOf(
            listOf("caption", "text"), listOf("edge_media_to_caption", "edges", "0", "node", "text"),
        ),
        postText = true,
        nameTitles = owner("full_name", "{} on Instagram") + owner("username", "@{} on Instagram"),
        durationSecondsPaths = listOf(listOf("video_duration")),
        thumbnailPaths = listOf(listOf("image_versions2", "candidates", "0", "url"), listOf("display_url")),
    )

    val ALL: List<ContractRecipe> = listOf(VIMEO, X, FACEBOOK, TIKTOK, INSTAGRAM)

    fun of(site: String): ContractRecipe? = ALL.firstOrNull { it.site == site }
}
