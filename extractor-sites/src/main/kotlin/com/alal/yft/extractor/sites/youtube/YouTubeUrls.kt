package com.alal.yft.extractor.sites.youtube

import com.alal.yft.extractor.api.SitePageIdentity
import java.net.URI

/**
 * Pure URL matching for YouTube watch pages.
 *
 * Selection must stay offline, so only the shapes YouTube itself links to are accepted and a
 * video ID is never invented from a channel, playlist or search address.
 */
internal object YouTubeUrls {
    const val SITE_ID: String = "youtube"

    /** YouTube video IDs have been 11 URL-safe base64 characters for the platform's lifetime. */
    private val ID_PATTERN = Regex("^[A-Za-z0-9_-]{11}$")

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

    private const val INNERTUBE_HOST = "www.youtube.com"
    private const val INNERTUBE_PATH = "/youtubei/v1/player"
    private const val PLAYER_SCRIPT_PREFIX = "/s/player/"

    fun identify(pageUrl: String): SitePageIdentity? {
        val uri = runCatching { URI(pageUrl) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() != "https") return null
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
        if (videoId == null || !ID_PATTERN.matches(videoId)) return null

        return SitePageIdentity(
            siteId = SITE_ID,
            contentId = videoId,
            canonicalPageUrl = canonicalUrl(videoId),
        )
    }

    /** Every recognized shape collapses onto the one address YouTube treats as canonical. */
    fun canonicalUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"

    fun innerTubeUrl(apiKey: String?): String = buildString {
        append("https://")
        append(INNERTUBE_HOST)
        append(INNERTUBE_PATH)
        if (!apiKey.isNullOrBlank() && apiKey.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
            append("?key=")
            append(apiKey)
        }
    }

    /**
     * Accepts only YouTube's own player script address.
     *
     * The script address comes out of page markup, so it is treated as untrusted input: a changed
     * page must not be able to point script execution at a third-party origin.
     */
    fun playerScriptUrl(rawJsUrl: String?): String? {
        val value = rawJsUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val absolute = when {
            value.startsWith(PLAYER_SCRIPT_PREFIX) -> "https://$INNERTUBE_HOST$value"
            value.startsWith("https://") -> value
            else -> return null
        }
        val uri = runCatching { URI(absolute) }.getOrNull() ?: return null
        if (uri.host?.lowercase() !in WATCH_HOSTS) return null
        if (!uri.path.orEmpty().startsWith(PLAYER_SCRIPT_PREFIX)) return null
        if (!uri.path.orEmpty().endsWith(".js")) return null
        return absolute
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
}