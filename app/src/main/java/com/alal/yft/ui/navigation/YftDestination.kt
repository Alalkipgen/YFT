package com.alal.yft.ui.navigation

/**
 * Every screen in the app. The four [isTopLevel] destinations are the bottom-bar tabs; the rest
 * open full screen on top of a tab and hide the bar until the user goes back.
 */
enum class YftDestination(
    val route: String,
    val title: String,
    val summary: String,
    val isTopLevel: Boolean = false,
) {
    HOME("home", "Home", "Start with a link or choose a workspace.", isTopLevel = true),
    BROWSER("browser", "Browser", "Browse securely and review media found on the current page."),
    DETECTED_MEDIA(
        "detected-media",
        "Detected Media",
        "Review media found on the page the browser showed last.",
    ),
    PREVIEW("preview", "Preview", "Inspect real media variants before downloading."),
    DOWNLOADS(
        "downloads",
        "Downloads",
        "Track active and queued transfers.",
        isTopLevel = true,
    ),
    LIBRARY(
        "library",
        "Library",
        "Play, open, share or delete downloaded media.",
        isTopLevel = true,
    ),
    SETTINGS(
        "settings",
        "Settings",
        "Download preferences, privacy controls and appearance.",
        isTopLevel = true,
    ),
    ABOUT("about", "About", "Product scope, privacy, licenses and version information."),
    ;

    companion object {
        /** Bottom-bar order: Home, Downloads, Library, Settings. */
        val topLevel: List<YftDestination> = entries.filter { it.isTopLevel }

        /** Full-screen destinations Home can still open directly. */
        val homeActions: List<YftDestination> = entries.filterNot { it.isTopLevel }

        /** The tab a route belongs to, or null for full-screen routes. */
        fun topLevelFor(route: String?): YftDestination? =
            topLevel.firstOrNull { it.route == route }
    }
}
