package com.alal.yft.core.model.settings

/** The web search for words typed in the in-app browser (Google first). */
enum class SearchEngine(
    /** The engine's name as Settings and the browser's start page show it. */
    val displayName: String,
) {
    GOOGLE(displayName = "Google"),
    DUCKDUCKGO(displayName = "DuckDuckGo"),
    BING(displayName = "Bing"),
}

/**
 * Settings › Browser. Fields are only ever added, each with its default, so a store an
 * older build wrote reads the defaults for what it does not have yet.
 */
data class BrowserPreferences(
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    /** Pages the browser opens are kept in its History. */
    val saveHistory: Boolean = true,
    /**
     * New windows and redirects to other sites that the page started without
     * the user's tap, and YFT's list of pop-up ad networks, are kept from replacing the page.
     */
    val blockPopups: Boolean = true,
)
