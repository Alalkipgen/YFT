/*
 * Provenance (social plan S0a, copied, not moved; main 34a41890; listed in toolkit-provenance.tsv):
 *   extractor-sites/.../facebook/FacebookPageParser.kt  MEDIA_KEYS, ACCESS_MARKERS (in their
 *                                        order: region, sign-in, then removed; any case)
 *   extractor-sites/.../facebook/FacebookUrls.kt  isAccessWall, WALL_PATHS
 *   extractor-sites/.../tiktok/TikTokPageParser.kt  kindOf (home and login paths),
 *                                        CHALLENGE_MARKERS (only without TikTok's data scripts)
 *   extractor-sites/.../tiktok/TikTokDownloadService.kt  lookUp (`code` 0 and its files)
 *   extractor-sites/.../instagram/InstagramUrls.kt  isLoginWall
 *   extractor-sites/.../x/XSyndication.kt  parse, TOMBSTONE (a tombstone or an empty answer is
 *                                        no post)
 * Reviewed (social plan 0.1, main's X card fix on this branch): main's parse now also reads the
 *   post's own card (`card.binding_values.unified_card`, JSON in a string) after its own media and
 *   before the quoted post, never the quoted post's card; tweetResult reads the same three as
 *   data. The table's row keeps its base hash (34a41890): the drift check against main reports
 *   it once this fix reaches main.
 */
package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonProblem
import com.alal.yft.extractor.api.json.JsonReadResult
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asBooleanOrNull
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.api.json.path
import com.alal.yft.extractor.master.lookup.AnswerMark
import com.alal.yft.extractor.master.lookup.AnswerMark.BOT_CHECK
import com.alal.yft.extractor.master.lookup.AnswerMark.DATA
import com.alal.yft.extractor.master.lookup.AnswerMark.LOGIN_PAGE
import com.alal.yft.extractor.master.lookup.AnswerMark.NOT_JSON
import com.alal.yft.extractor.master.lookup.AnswerMark.NO_DATA
import com.alal.yft.extractor.master.lookup.AnswerMark.NO_POST
import com.alal.yft.extractor.master.lookup.AskReading
import java.net.URI
import java.security.MessageDigest

/**
 * S0a: what a 2xx answer of each way held, as a mark ([AnswerMark]) and a shape fingerprint. A
 * reading is a quick look at the answer's text, not main's parse: "data" is media data of the kind
 * main reads (an HTTPS file address, an inline manifest), whatever main then does with it (a DRM
 * flag, an unreadable payload). A page's shape is `h` + 8 hex of the markers it holds (key names
 * of the site's data, never a value); a JSON answer's is `j` + 8 hex of its key paths (sorted keys
 * at each level, at most [MAX_KEY_DEPTH] keys deep, an array by its first item, at most
 * [MAX_PATHS] paths; a key that is a number or very long counts as `#`). The same structure with
 * other values or keys in another order keeps its shape; a renamed key changes it.
 */
internal object SiteReadings {
    const val MAX_KEY_DEPTH = 4
    const val MAX_PATHS = 400

    /** Longer JSON is not read (main's own clients cap a body well below this). */
    const val MAX_JSON_CHARS = 4_000_000

    fun facebookPage(result: ExtractorHttpResult.Success): AskReading? {
        val body = result.body
        val mark = when {
            facebookWall(result.finalUrl) -> LOGIN_PAGE
            FB_MEDIA.containsMatchIn(body) || INLINE_MANIFEST.containsMatchIn(body) -> DATA
            else -> FB_ACCESS.firstOrNull { (marker, _) -> body.contains(marker, ignoreCase = true) }?.second ?: NO_DATA
        }
        return AskReading(mark, pageShape(body, FB_MARKERS))
    }

    fun tiktokPage(result: ExtractorHttpResult.Success): AskReading? {
        val body = result.body
        val path = runCatching { URI(result.finalUrl.trim()).rawPath }.getOrNull().orEmpty().lowercase().trimEnd('/')
        val mark = when {
            path == "/login" || path.startsWith("/login/") -> LOGIN_PAGE
            path.isEmpty() || path == "/foryou" || path.startsWith("/foryou/") -> NO_POST
            TT_SCRIPTS.none(body::contains) && body.lowercase().let { text -> TT_CHALLENGE.any(text::contains) } ->
                BOT_CHECK
            TT_PHOTO.containsMatchIn(body) -> NO_DATA
            TT_MEDIA.containsMatchIn(body) && TT_ITEMS.any(body::contains) -> DATA
            TT_STATUS.containsMatchIn(body) -> NO_POST
            else -> NO_DATA
        }
        return AskReading(mark, pageShape(body, TT_MARKERS))
    }

    /** Master's embed player: its video's file, else no data (a bot check is main's page's word). */
    fun tiktokEmbed(result: ExtractorHttpResult.Success): AskReading? {
        val body = result.body
        val mark = if (TT_MEDIA.containsMatchIn(body) && TT_ITEMS.any(body::contains)) DATA else NO_DATA
        return AskReading(mark, pageShape(body, TT_MARKERS))
    }

    /** The download service: `code` 0 with a file; another code is no post (a limit: no data). */
    fun downloadService(result: ExtractorHttpResult.Success): AskReading? = json(result) { root ->
        val code = root["code"].asLongOrNull
        val data = root["data"]
        when {
            code == 0L && SERVICE_FILES.any { !data[it].asStringOrNull.isNullOrBlank() } -> DATA
            code == 0L -> NO_DATA
            root["msg"].asStringOrNull?.contains("limit", ignoreCase = true) == true -> NO_DATA
            else -> NO_POST
        }
    }

    /** The app API ([appApi]) or GraphQL: the post (its items, its shortcode media) and its video. */
    fun instagramJson(result: ExtractorHttpResult.Success, appApi: Boolean): AskReading? {
        if (instagramWall(result.finalUrl)) return AskReading(LOGIN_PAGE)
        return json(result) { root ->
            val post = if (appApi) {
                root["items"].asArrayOrEmpty.isNotEmpty()
            } else {
                IG_MEDIA_KEYS.any { root.path("data", it) is JsonValue.Object }
            }
            when {
                root["require_login"].asBooleanOrNull == true || root["message"].asStringOrNull == "login_required" ->
                    LOGIN_PAGE
                !post -> NO_POST
                IG_VIDEO.containsMatchIn(result.body) -> DATA
                else -> NO_DATA
            }
        }
    }

    /** The post's page or the embed page. */
    fun instagramPage(result: ExtractorHttpResult.Success): AskReading? {
        val body = result.body
        val mark = when {
            instagramWall(result.finalUrl) -> LOGIN_PAGE
            IG_VIDEO.containsMatchIn(body) -> DATA
            IG_GONE.any(body::contains) -> NO_POST
            else -> NO_DATA
        }
        return AskReading(mark, pageShape(body, IG_MARKERS))
    }

    /**
     * X's widget: a tombstone or an empty answer is no post; a video or GIF with files is data: in
     * the post's own media, its own card's media (0.1: `unified_card`, JSON in a string) or the
     * quoted post's media, the three places main reads (as main, not the quoted post's card).
     */
    fun tweetResult(result: ExtractorHttpResult.Success): AskReading? = json(result) { root ->
        when {
            root !is JsonValue.Object -> NO_DATA
            root.entries.isEmpty() || root["__typename"].asStringOrNull == TOMBSTONE || root["tombstone"] != null ->
                NO_POST
            hasVideo(root["mediaDetails"]) || hasVideo(cardMedia(root)) ||
                hasVideo(root.path("quoted_tweet", "mediaDetails")) -> DATA
            else -> NO_DATA
        }
    }

    /** `h` + 8 hex of the [markers] [body] holds. */
    fun pageShape(body: String, markers: List<String>): String =
        "h" + hex8(markers.filter(body::contains).joinToString("\n"))

    /** `j` + 8 hex of [root]'s key paths. */
    fun jsonShape(root: JsonValue): String {
        val paths = ArrayList<String>()
        fun walk(value: JsonValue, prefix: String, depth: Int) {
            when (value) {
                is JsonValue.Object -> for ((key, child) in value.entries.entries.sortedBy { it.key }) {
                    if (paths.size >= MAX_PATHS) return
                    val name = key.takeUnless { it.length > MAX_KEY_CHARS || NUMBER_KEY.matches(it) } ?: "#"
                    val path = if (prefix.isEmpty()) name else "$prefix.$name"
                    paths += path
                    if (depth < MAX_KEY_DEPTH) walk(child, path, depth + 1)
                }
                is JsonValue.Array -> value.items.firstOrNull()?.let { walk(it, "$prefix[]", depth) }
                else -> Unit
            }
        }
        walk(root, "", 1)
        return "j" + hex8(paths.sorted().joinToString("\n"))
    }

    /** A JSON answer read by [mark]; not JSON is [NOT_JSON]; too deep or too big: not read. */
    private inline fun json(result: ExtractorHttpResult.Success, mark: (JsonValue) -> AnswerMark): AskReading? {
        if (result.body.length > MAX_JSON_CHARS) return null
        return when (val read = BoundedJsonParser.read(result.body.trim())) {
            is JsonReadResult.Read -> AskReading(mark(read.value), jsonShape(read.value))
            is JsonReadResult.Failed -> when (read.problem) {
                JsonProblem.MALFORMED, JsonProblem.TRAILING_TEXT -> AskReading(NOT_JSON)
                JsonProblem.TOO_DEEP, JsonProblem.TOO_MANY_NODES -> null
            }
        }
    }

    private fun hasVideo(items: JsonValue?): Boolean = items.asArrayOrEmpty.any { item ->
        item["type"].asStringOrNull in X_VIDEO_TYPES && item.path("video_info", "variants").asArrayOrEmpty.isNotEmpty()
    }

    /** The media of a post's own unified card (a JSON text), as an array; none when unreadable. */
    private fun cardMedia(tweet: JsonValue): JsonValue? {
        val text = tweet.path("card", "binding_values", "unified_card", "string_value").asStringOrNull ?: return null
        val card = (BoundedJsonParser.read(text) as? JsonReadResult.Read)?.value ?: return null
        val media = card["media_entities"] as? JsonValue.Object ?: return null
        return JsonValue.Array(media.entries.values.toList())
    }

    private fun facebookWall(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        if (host !in FACEBOOK_HOSTS) return false
        val path = uri.path.orEmpty().lowercase()
        return FB_WALL_PATHS.any { path == it || path.startsWith("$it/") }
    }

    private fun instagramWall(url: String): Boolean {
        val path = runCatching { URI(url.trim()).path }.getOrNull()?.lowercase() ?: return false
        return path.startsWith("/accounts/login") || path.startsWith("/challenge")
    }

    private fun hex8(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .take(4)
        .joinToString("") { "%02x".format(it) }

    private const val MAX_KEY_CHARS = 64
    private val NUMBER_KEY = Regex("^[0-9]{5,}$")

    private val FB_WALL_PATHS = listOf("/login", "/login.php", "/checkpoint", "/recover", "/r.php")
    private val FB_MEDIA = Regex(
        """"(progressive_url|playable_url|playable_url_quality_hd|browser_native_hd_url|browser_native_sd_url|dash_manifest_url|hd_src|sd_src)"\s*:\s*"https:""",
    )
    private val INLINE_MANIFEST = Regex(""""(dash_manifest|dash_manifest_xml_string|manifest_xml)"\s*:\s*"(\\u003[cC]|<)""")
    private val FB_ACCESS = listOf(
        "video_unavailable_geoblock" to NO_POST,
        "isn't available in your location" to NO_POST,
        "isn\u2019t available in your location" to NO_POST,
        "not available in your country" to NO_POST,
        "You must log in to continue" to LOGIN_PAGE,
        "id=\"loginform\"" to LOGIN_PAGE,
        "login_form_container" to LOGIN_PAGE,
        "This content isn't available right now" to NO_POST,
        "This content isn\u2019t available right now" to NO_POST,
        "This video isn't available anymore" to NO_POST,
        "This video isn\u2019t available anymore" to NO_POST,
        "content_not_found" to NO_POST,
        "VideoNotFoundError" to NO_POST,
    )
    private val FB_MARKERS = listOf(
        "videoDeliveryResponseFragment", "videoDeliveryLegacyFields", "progressive_urls", "playable_url",
        "playable_url_quality_hd", "browser_native_hd_url", "browser_native_sd_url", "dash_manifest_url",
        "dash_manifest_xml_string", "all_video_dash_prefetch_representations", "hd_src", "sd_src", "__bbox",
        "RelayPrefetchedStreamCache", "ScheduledServerJS", "drm_info",
    ).map { "\"$it\"" }

    private val TT_SCRIPTS = listOf("__UNIVERSAL_DATA_FOR_REHYDRATION__", "SIGI_STATE")
    private val TT_CHALLENGE = listOf("captcha", "verify", "waf", "please wait", "challenge")
    private val TT_PHOTO = Regex(""""imagePost"\s*:\s*\{""")
    private val TT_MEDIA = Regex(""""(playAddr|downloadAddr)"\s*:\s*"https:|"(UrlList|urls)"\s*:\s*\[\s*"https:""")
    private val TT_ITEMS = listOf("\"itemStruct\"", "\"ItemModule\"", "\"videoData\"")
    private val TT_STATUS = Regex(""""statusCode(V2)?"\s*:\s*-?[1-9]""")
    private val TT_MARKERS = listOf(
        "__UNIVERSAL_DATA_FOR_REHYDRATION__", "SIGI_STATE", "itemStruct", "ItemModule", "bitrateInfo", "playAddr",
        "downloadAddr", "imagePost", "webapp.video-detail", "webapp.reflow.video.detail",
        "__FRONTITY_CONNECT_STATE__", "videoData",
    ).map { "\"$it\"" }
    private val SERVICE_FILES = listOf("play", "hdplay", "wmplay")

    private val IG_MEDIA_KEYS = listOf("xdt_shortcode_media", "shortcode_media")
    private val IG_VIDEO =
        Regex("""\\?"(video_url|video_versions|video_dash_manifest)\\?"\s*:\s*(\\?"(https|<|\\u003[cC])|\[\s*\{)""")
    private val IG_GONE = listOf(
        "EmbedIsBroken",
        "This post is unavailable",
        "Sorry, this page isn't available",
        "Sorry, this page isn\u2019t available",
    )
    private val IG_MARKERS = listOf(
        "video_versions", "video_url", "video_dash_manifest", "display_url", "xdt_shortcode_media", "shortcode_media",
        "contextJSON", "carousel_media", "edge_sidecar_to_children", "EmbedIsBroken", "require_login",
    )

    private const val TOMBSTONE = "TweetTombstone"
    private val X_VIDEO_TYPES = setOf("video", "animated_gif")
}
