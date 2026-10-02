package com.alal.yft.detection.script

/**
 * The only addresses the solver page may load, all served by the app itself.
 *
 * The page lives on Android's reserved asset host, which never resolves to a real server, and
 * every address outside this table is refused, so neither the solver nor the site's player
 * functions can reach the network.
 */
internal object SolverPageRoutes {
    const val HOST: String = "appassets.androidplatform.net"
    const val PAGE_URL: String = "https://$HOST/yft-solver/solver.html"
    const val ASSET_DIRECTORY: String = "youtube-solver"

    private const val PREFIX = "/yft-solver/"
    private const val INPUT_NAME = "solver-input.json"

    /**
     * Scripts load only from the page's own origin; `unsafe-eval` exists for the solver, which
     * evaluates the functions it extracts from the player. Workers may start only from blobs.
     */
    const val CONTENT_SECURITY_POLICY: String =
        "default-src 'none'; script-src 'self' 'unsafe-eval' blob:; worker-src blob:; " +
            "child-src blob:; connect-src 'self'"

    sealed interface Route {
        /** A bundled file from [ASSET_DIRECTORY]. */
        data class Asset(val name: String, val mimeType: String) : Route

        /** The job document for this run, served from memory. */
        data object Input : Route

        data object Refused : Route
    }

    private val ASSETS = mapOf(
        "solver.html" to "text/html",
        "solver-page.js" to "text/javascript",
        "solver-worker.js" to "text/javascript",
        "yt.solver.lib.min.js" to "text/javascript",
        "yt.solver.core.min.js" to "text/javascript",
    )

    fun route(scheme: String?, host: String?, path: String?): Route {
        if (scheme != "https" || host != HOST || path == null || !path.startsWith(PREFIX)) {
            return Route.Refused
        }
        val name = path.removePrefix(PREFIX)
        if (name == INPUT_NAME) return Route.Input
        val mimeType = ASSETS[name] ?: return Route.Refused
        return Route.Asset(name, mimeType)
    }
}
