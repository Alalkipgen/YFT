package com.alal.yft.extractor.master.android.solver

/**
 * Phase 1.2 N4 (strategy E): the only addresses the own solver's page environment may load, all
 * served by the app, and the role (main frame or sub frame) each may be loaded in.
 *
 * Same model as [OwnSolverPageRoutes] (the worker's table, unchanged), with its own table: the
 * host page `page-env.html` (main frame only) and the sandboxed frame `page-frame.html` (sub frame
 * only) live on Android's reserved asset host, which never resolves to a real server, and every
 * address outside this table is refused, so neither the core nor the player's code can reach the
 * network. The worker's files and main's ejs files are not served here.
 */
internal object OwnSolverPageEnvRoutes {
    const val HOST: String = OwnSolverPageRoutes.HOST
    const val ORIGIN: String = "https://appassets.androidplatform.net"
    const val PAGE_URL: String = "$ORIGIN/yft-own-solver/page-env.html"
    const val FRAME_URL: String = "$ORIGIN/yft-own-solver/page-frame.html"
    const val ASSET_DIRECTORY: String = OwnSolverPageRoutes.ASSET_DIRECTORY
    const val INPUT_NAME: String = "page-env-input.json"

    /**
     * The reply object's name (androidx.webkit `addWebMessageListener`). WebView puts it only into
     * frames whose origin matches [ALLOWED_ORIGIN_RULES]: the host page, never the sandboxed
     * frame (its origin is opaque).
     */
    const val BRIDGE_NAME: String = "yftPageSolver"
    val ALLOWED_ORIGIN_RULES: Set<String> = setOf(ORIGIN)

    private const val PREFIX = "/yft-own-solver/"

    /** The host page runs only the app's script; it may load the app's files and frame its page. */
    const val HOST_CSP: String =
        "default-src 'none'; script-src 'self'; connect-src 'self'; frame-src 'self'; " +
            "base-uri 'none'; form-action 'none'"

    /**
     * The sandboxed frame: one script by address, `unsafe-eval` for the core (it runs the player's
     * top level as a function), no connection of any kind, no worker, frame, image or style.
     */
    const val FRAME_CSP: String =
        "default-src 'none'; script-src https://appassets.androidplatform.net/yft-own-solver/page-frame.js " +
            "'unsafe-eval'; worker-src 'none'; connect-src 'none'; webrtc 'block'; base-uri 'none'; " +
            "form-action 'none'"

    /** The frame's header adds what a meta tag cannot carry: its own sandbox and its framer. */
    const val FRAME_CSP_HEADER: String =
        "$FRAME_CSP; sandbox allow-scripts; frame-ancestors $ORIGIN"

    /** Scripts and the job are never documents; if one is opened as a page, nothing runs. */
    const val FILE_CSP: String = "default-src 'none'; sandbox"

    sealed interface Route {
        /** A bundled file from [ASSET_DIRECTORY] with the policy its response carries. */
        data class Asset(val name: String, val mimeType: String, val policy: String) : Route

        /** The job document for this run, served from memory. */
        data object Input : Route

        data object Refused : Route
    }

    private class Entry(val mimeType: String, val policy: String, val mainFrame: Boolean)

    private val ENTRIES: Map<String, Entry> = mapOf(
        "page-env.html" to Entry("text/html", HOST_CSP, mainFrame = true),
        "page-frame.html" to Entry("text/html", FRAME_CSP_HEADER, mainFrame = false),
        "page-env.js" to Entry("text/javascript", FILE_CSP, mainFrame = false),
        "page-frame.js" to Entry("text/javascript", FILE_CSP, mainFrame = false),
        "meriyah.umd.min.js" to Entry("text/javascript", FILE_CSP, mainFrame = false),
        "own.solver.core.js" to Entry("text/javascript", FILE_CSP, mainFrame = false),
    )

    /** Every file this table serves. */
    val ASSETS: Map<String, String> = ENTRIES.mapValues { it.value.mimeType }

    /**
     * [isForMainFrame] is WebView's `WebResourceRequest.isForMainFrame`: only the host page may be
     * a main frame; nothing else may.
     */
    fun route(scheme: String?, host: String?, path: String?, isForMainFrame: Boolean): Route {
        if (scheme != "https" || host != HOST || path == null || !path.startsWith(PREFIX)) {
            return Route.Refused
        }
        val name = path.removePrefix(PREFIX)
        if (name == INPUT_NAME) return if (isForMainFrame) Route.Refused else Route.Input
        val entry = ENTRIES[name] ?: return Route.Refused
        if (entry.mainFrame != isForMainFrame) return Route.Refused
        return Route.Asset(name, entry.mimeType, entry.policy)
    }

    /**
     * A navigation WebView asks about (`shouldOverrideUrlLoading`): only the sandboxed frame's own
     * page may load in a sub frame; every other navigation is stopped.
     */
    fun allowsNavigation(url: String?, isForMainFrame: Boolean): Boolean =
        !isForMainFrame && url == FRAME_URL
}
