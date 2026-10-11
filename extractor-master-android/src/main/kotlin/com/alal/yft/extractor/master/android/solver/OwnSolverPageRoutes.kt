package com.alal.yft.extractor.master.android.solver

/**
 * Phase 1.1 S5: the only addresses the own solver's page may load, all served by the app.
 *
 * Same model as main's `SolverPageRoutes` (copied, not shared; main's file is unchanged): the
 * page lives on Android's reserved asset host, which never resolves to a real server, and every
 * address outside this table is refused, so neither the core nor the player's code can reach the
 * network. The own solver has its own path and asset folder, so it never loads main's ejs files.
 */
internal object OwnSolverPageRoutes {
    const val HOST: String = "appassets.androidplatform.net"
    const val PAGE_URL: String = "https://$HOST/yft-own-solver/own-solver.html"
    const val ASSET_DIRECTORY: String = "yft-own-solver"

    private const val PREFIX = "/yft-own-solver/"
    const val INPUT_NAME: String = "own-solver-input.json"

    /**
     * Scripts load only from the page's own origin; `unsafe-eval` exists for the core, which runs
     * the player's top level as a function. Workers may start only from blobs.
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

    val ASSETS: Map<String, String> = mapOf(
        "own-solver.html" to "text/html",
        "own-solver-page.js" to "text/javascript",
        "own-solver-worker.js" to "text/javascript",
        "meriyah.umd.min.js" to "text/javascript",
        "own.solver.core.js" to "text/javascript",
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
