package com.alal.yft.ui.navigation

/**
 * Every screen in the app. The four [isTopLevel] destinations are the bottom-bar tabs; the rest
 * open on top of a tab and hide the bar until the user goes back. Download as ([PREVIEW]) is a
 * sheet over the page that opened it rather than a screen of its own, and so is Video you copied
 * ([QUICK_DOWNLOAD]) over Home.
 */
enum class YftDestination(
    val route: String,
    val title: String,
    val summary: String,
    val isTopLevel: Boolean = false,
) {
    HOME("home", "Home", "Paste a link or open a site to find media.", isTopLevel = true),
    BROWSER("browser", "Browser", "Browse securely and review media found on the current page."),
    DETECTED_MEDIA(
        "detected-media",
        "Found on this page",
        "Review media found on the page the browser showed last.",
    ),
    PREVIEW("preview", "Download as", "Pick a quality, try it and download it."),
    QUICK_DOWNLOAD(
        "quick-download",
        "Video you copied",
        "Download the video you copied, or its music, in one tap.",
    ),
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
    ABOUT("about", "About", "Product scope, privacy promises and version information."),
    LICENSES(
        "licenses",
        "Licenses",
        "Open-source licenses for the code YFT bundles and uses.",
    ),
    PLAYER("player", "Player", "Watch a saved video full screen."),
    ;

    companion object {
        /** Bottom-bar order: Home, Downloads, Library, Settings. */
        val topLevel: List<YftDestination> = entries.filter { it.isTopLevel }

        /** Destinations that open over a tab and hide the bottom bar. */
        val fullScreen: List<YftDestination> = entries.filterNot { it.isTopLevel }

        /** The tab a route belongs to, or null for full-screen routes. */
        fun topLevelFor(route: String?): YftDestination? =
            topLevel.firstOrNull { it.route == route }
    }
}
