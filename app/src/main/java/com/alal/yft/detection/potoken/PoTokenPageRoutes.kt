package com.alal.yft.detection.potoken

/**
 * The only addresses the proof-of-origin page may load, all served by the app itself.
 *
 * The page lives on Android's reserved asset host, which never resolves to a real server, and
 * every address outside this table is refused, so BotGuard's interpreter can reach neither the
 * network nor any cookie, the user's YouTube session included.
 */
internal object PoTokenPageRoutes {
    const val HOST: String = "appassets.androidplatform.net"
    const val PAGE_URL: String = "https://$HOST/yft-potoken/potoken.html"
    const val ASSET_DIRECTORY: String = "youtube-potoken"

    private const val PREFIX = "/yft-potoken/"
    private const val CHALLENGE_NAME = "challenge.json"

    /**
     * Scripts load only from the page's own origin; `unsafe-eval` exists for YouTube's
     * interpreter, which the page evaluates from the challenge it read from that origin.
     */
    const val CONTENT_SECURITY_POLICY: String =
        "default-src 'none'; script-src 'self' 'unsafe-eval'; connect-src 'self'"

    sealed interface Route {
        /** A bundled file from [ASSET_DIRECTORY]. */
        data class Asset(val name: String, val mimeType: String) : Route

        /** This session's challenge, served from memory. */
        data object Challenge : Route

        data object Refused : Route
    }

    private val ASSETS = mapOf(
        "potoken.html" to "text/html",
        "potoken-page.js" to "text/javascript",
    )

    fun route(scheme: String?, host: String?, path: String?): Route {
        if (scheme != "https" || host != HOST || path == null || !path.startsWith(PREFIX)) {
            return Route.Refused
        }
        val name = path.removePrefix(PREFIX)
        if (name == CHALLENGE_NAME) return Route.Challenge
        val mimeType = ASSETS[name] ?: return Route.Refused
        return Route.Asset(name, mimeType)
    }
}
