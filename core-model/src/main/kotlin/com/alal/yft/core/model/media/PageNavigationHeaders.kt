package com.alal.yft.core.model.media

/** Defaults for top-level HTML navigation only; never apply these to JSON/API requests. */
object PageNavigationHeaders {
    const val ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
    const val ACCEPT_LANGUAGE = "en-US,en;q=0.9"
    const val FETCH_MODE = "navigate"

    val DEFAULTS: Map<String, String> = mapOf(
        "Accept" to ACCEPT,
        "Accept-Language" to ACCEPT_LANGUAGE,
        "Sec-Fetch-Mode" to FETCH_MODE,
    )

    /** Retains explicit values and casing; no identity or session header is ever injected. */
    fun withDefaults(headers: Map<String, String>): Map<String, String> = buildMap {
        putAll(headers)
        DEFAULTS.forEach { (name, value) ->
            if (keys.none { it.equals(name, ignoreCase = true) }) put(name, value)
        }
    }
}
