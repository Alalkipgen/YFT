package com.alal.yft.ui.navigation

enum class YftDestination(
    val route: String,
    val title: String,
    val summary: String,
) {
    HOME("home", "Home", "Start with a link or choose a workspace."),
    BROWSER("browser", "Browser", "Browse securely and review media found on the current page."),
    DETECTED_MEDIA(
        "detected-media",
        "Detected Media",
        "Review media found on the page the browser showed last.",
    ),
    PREVIEW("preview", "Preview", "Inspect real media variants before downloading."),
    DOWNLOADS("downloads", "Downloads", "Track active and queued transfers."),
    LIBRARY("library", "Library", "Play, open, share or delete downloaded media."),
    SETTINGS("settings", "Settings", "Download preferences, privacy controls and appearance."),
    ABOUT("about", "About", "Product scope, privacy, licenses and version information."),
    ;

    companion object {
        val homeActions: List<YftDestination> = entries.filterNot { it == HOME }
    }
}
