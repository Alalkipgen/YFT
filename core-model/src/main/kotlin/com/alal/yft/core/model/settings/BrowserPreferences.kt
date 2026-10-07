package com.alal.yft.core.model.settings

/** The web search for words typed in the in-app browser (P30, decision F2: Google first). */
enum class SearchEngine(
    /** The engine's name as Settings and the browser's start page show it. */
    val displayName: String,
) {
    GOOGLE(displayName = "Google"),
    DUCKDUCKGO(displayName = "DuckDuckGo"),
    BING(displayName = "Bing"),
}

/**
 * Settings › Browser (P30). Fields are only ever added, each with its default, so a store an
 * older build wrote reads the defaults for what it does not have yet.
 */
data class BrowserPreferences(
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    /** P31 (decision F3): pages the browser opens are kept in its History. */
    val saveHistory: Boolean = true,
)
