package com.alal.yft.extractor.sites.youtube

import com.alal.yft.extractor.api.SitePageIdentity
import java.net.URI
import java.net.URLEncoder

/**
 * Pure URL handling for YouTube.
 *
 * Selection must stay offline, so only the single-video shapes YouTube itself links to are
 * accepted, and a video ID is never invented from a channel, playlist or search address.
 */
internal object YouTubeUrls {
    const val SITE_ID: String = "youtube"

    /**
     * The phone build of YouTube's player script.
     *
     * It is the smallest current build, and it is one of the variants the bundled solver's own
     * test vectors cover. Every build of one player version computes identical values.
     */
    const val PHONE_PLAYER_VARIANT: String = "player-plasma-ias-phone-en_US.vflset/base.js"

    /** The desktop build, used only when the phone build cannot be fetched. */
    const val MAIN_PLAYER_VARIANT: String = "player_ias.vflset/en_US/base.js"

    private const val ORIGIN = "https://www.youtube.com"
    private const val PLAYER_SCRIPT_PREFIX = "/s/player/"
    private const val MEDIA_HOST_SUFFIX = ".googlevideo.com"
    private const val MEDIA_PATH = "/videoplayback"

    /** YouTube video IDs have been 11 URL-safe base64 characters for the platform's lifetime. */
    private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")
    private val PLAYER_ID = Regex("^[A-Za-z0-9_-]{4,32}$")
    private val API_KEY = Regex("^[A-Za-z0-9_-]{10,80}$")

    private val WATCH_HOSTS = setOf(
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "music.youtube.com",
        "youtube-nocookie.com",
        "www.youtube-nocookie.com",
    )

    private val SHORT_HOSTS = setOf("youtu.be", "www.youtu.be")

    /** Single-video paths YouTube serves a player on. */
    private val VIDEO_PATH_PREFIXES = listOf("/shorts/", "/embed/", "/live/", "/v/")

    fun identify(pageUrl: String): SitePageIdentity? {
        val uri = runCatching { URI(pageUrl) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() != "https") return null
        if (uri.rawUserInfo != null) return null
        val host = uri.host?.lowercase() ?: return null
        val path = uri.path.orEmpty()

        val videoId = when {
            host in SHORT_HOSTS -> path.trimStart('/').takeWhile { it != '/' }
            host in WATCH_HOSTS && (path == "/watch" || path == "/watch/") ->
                queryParam(uri.rawQuery, "v")

            host in WATCH_HOSTS -> VIDEO_PATH_PREFIXES
                .firstOrNull { path.startsWith(it) }
                ?.let { prefix -> path.removePrefix(prefix).takeWhile { it != '/' } }

            else -> null
        }
        if (videoId == null || !VIDEO_ID.matches(videoId)) return null

        return SitePageIdentity(
            siteId = SITE_ID,
            contentId = videoId,
            canonicalPageUrl = canonicalUrl(videoId),
        )
    }

    /** Every recognized shape collapses onto the one address YouTube treats as canonical. */
    fun canonicalUrl(videoId: String): String = "$ORIGIN/watch?v=$videoId"

    /**
     * The address the watch page is read from.
     *
     * The two extra parameters are the ones YouTube's own content-warning page adds when a
     * viewer clicks through it, so a video behind such a warning still serves its player
     * (ADR-006). They do not lift an age check.
     */
    fun watchPageFetchUrl(videoId: String): String =
        "${canonicalUrl(videoId)}&bpctr=9999999999&has_verified=1"

    /** The address YouTube's own embedded player is served from. */
    fun embedUrl(videoId: String): String = "$ORIGIN/embed/$videoId"

    fun innerTubeUrl(apiKey: String?): String {
        val base = "$ORIGIN/youtubei/v1/player?prettyPrint=false"
        return if (apiKey != null && API_KEY.matches(apiKey)) "$base&key=$apiKey" else base
    }

    /**
     * Reads the player version from the script address a page names.
     *
     * The address comes out of page markup, so it is untrusted: only YouTube's own hosts and its
     * `/s/player/<version>/` path are accepted, which keeps a changed page from pointing script
     * evaluation at a third-party origin.
     */
    fun playerId(rawJsUrl: String?): String? {
        val value = rawJsUrl?.trim()?.replace("\\/", "/")?.takeIf { it.isNotEmpty() }
            ?: return null
        val absolute = when {
            value.startsWith(PLAYER_SCRIPT_PREFIX) -> ORIGIN + value
            value.startsWith("https://") -> value
            else -> return null
        }
        val uri = runCatching { URI(absolute).normalize() }.getOrNull() ?: return null
        if (uri.host?.lowercase() !in WATCH_HOSTS || uri.rawUserInfo != null) return null
        val path = uri.path.orEmpty()
        if (!path.startsWith(PLAYER_SCRIPT_PREFIX) || !path.endsWith(".js")) return null
        return path.removePrefix(PLAYER_SCRIPT_PREFIX).substringBefore('/')
            .takeIf(PLAYER_ID::matches)
    }

    /**
     * Whether [url] is a request to YouTube's media servers for a stream, as YouTube's own
     * player makes while a video plays. Only the address shape is checked.
     */
    fun isMediaServerRequest(url: String): Boolean {
        val address = runCatching { URI(url) }.getOrNull() ?: return false
        val host = address.host?.lowercase() ?: return false
        return address.scheme.equals("https", ignoreCase = true) &&
            address.userInfo == null &&
            host.endsWith(MEDIA_HOST_SUFFIX) &&
            address.rawPath == MEDIA_PATH
    }

    fun playerScriptUrl(playerId: String, variant: String = PHONE_PLAYER_VARIANT): String {
        require(PLAYER_ID.matches(playerId)) { "Not a YouTube player version" }
        return "$ORIGIN$PLAYER_SCRIPT_PREFIX$playerId/$variant"
    }

    fun queryParam(rawQuery: String?, name: String): String? = rawQuery
        ?.split('&')
        ?.firstNotNullOfOrNull { pair ->
            val separator = pair.indexOf('=')
            if (separator <= 0 || pair.substring(0, separator) != name) {
                null
            } else {
                pair.substring(separator + 1).takeIf { it.isNotEmpty() }
            }
        }

    fun queryParamOf(url: String, name: String): String? =
        queryParam(url.substringAfter('?', "").substringBefore('#'), name)

    /** Replaces every [name] parameter in [url]; other parameters keep their order and bytes. */
    fun replaceQueryParam(url: String, name: String, value: String): String {
        val separator = url.indexOf('?')
        if (separator < 0) return url
        val encoded = encode(value)
        val query = url.substring(separator + 1).split('&').joinToString("&") { pair ->
            if (pair.substringBefore('=') == name) "$name=$encoded" else pair
        }
        return url.substring(0, separator + 1) + query
    }

    fun appendQueryParam(url: String, name: String, value: String): String {
        val joiner = if (url.contains('?')) '&' else '?'
        return "$url$joiner$name=${encode(value)}"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}
