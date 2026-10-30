package com.alal.yft.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.navArgument
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.feature.about.AboutRoute
import com.alal.yft.feature.about.LicensesScreen
import com.alal.yft.feature.browser.BrowserRoute
import com.alal.yft.feature.home.HOME_DOWNLOAD_LINK_KEY
import com.alal.yft.feature.detectedmedia.DetectedMediaRoute
import com.alal.yft.feature.downloads.DownloadsRoute
import com.alal.yft.feature.home.HomeRoute
import com.alal.yft.feature.library.LibraryRoute
import com.alal.yft.feature.library.PlayerRoute
import com.alal.yft.feature.preview.PreviewRoute
import com.alal.yft.feature.quickdownload.QuickDownloadRoute
import com.alal.yft.feature.settings.SettingsRoute
import com.alal.yft.ui.components.YftModalSheet
import com.alal.yft.update.UpdatesRoute
import com.alal.yft.update.UpdatesViewModel

@Composable
fun YftNavHost(
    navController: NavHostController,
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    homeContent: @Composable (
        onOpenBrowser: (link: String?) -> Unit,
        onOpenDetectedMedia: () -> Unit,
        onOpenLibrary: () -> Unit,
        onOpenQuickDownload: () -> Unit,
        onOpenSearch: () -> Unit,
        onOpenUpdates: () -> Unit,
    ) -> Unit = { onBrowser, onDetected, onLibrary, onQuick, onSearch, onUpdates ->
        HomeRoute(
            onOpenBrowser = onBrowser,
            onOpenDetectedMedia = onDetected,
            onOpenLibrary = onLibrary,
            onOpenQuickDownload = onQuick,
            onOpenSearch = onSearch,
            onOpenUpdates = onUpdates,
            updatesViewModel = hiltViewModel<UpdatesViewModel>(),
        )
    },
    browserContent: @Composable (
        onNavigateBack: () -> Unit,
        initialLink: String?,
        onGoHome: () -> Unit,
        searchMode: Boolean,
        onDownloadLink: (String) -> Unit,
        onOpenQuickDownload: () -> Unit,
    ) -> Unit = { onNavigateBack, link, onGoHome, search, onDownloadLink, onQuick ->
        BrowserRoute(
            onNavigateBack = onNavigateBack,
            initialLink = link,
            onGoHome = onGoHome,
            searchMode = search,
            onDownloadLink = onDownloadLink,
            onOpenQuickDownload = onQuick,
        )
    },
    detectedMediaContent: @Composable (
        onNavigateBack: () -> Unit,
        onOpenQuickDownload: () -> Unit,
        onOpenBrowser: () -> Unit,
    ) -> Unit = { onNavigateBack, onOpenQuickDownload, onOpenBrowser ->
        DetectedMediaRoute(
            onNavigateBack = onNavigateBack,
            onOpenQuickDownload = onOpenQuickDownload,
            onOpenBrowser = onOpenBrowser,
        )
    },
    previewContent: @Composable (
        onNavigateBack: () -> Unit,
        onOpenDownloads: () -> Unit,
    ) -> Unit = { onNavigateBack, onOpenDownloads ->
        PreviewRoute(onNavigateBack = onNavigateBack, onOpenDownloads = onOpenDownloads)
    },
    quickDownloadContent: @Composable (
        onNavigateBack: () -> Unit,
        onOpenDownloads: () -> Unit,
        onOpenDetails: () -> Unit,
    ) -> Unit = { onNavigateBack, onOpenDownloads, onOpenDetails ->
        QuickDownloadRoute(
            onNavigateBack = onNavigateBack,
            onOpenDownloads = onOpenDownloads,
            onOpenDetails = onOpenDetails,
        )
    },
    downloadsContent: @Composable (
        onOpenSettings: () -> Unit,
        onOpenPlayer: () -> Unit,
    ) -> Unit = { onOpenSettings, onOpenPlayer ->
        DownloadsRoute(onOpenSettings = onOpenSettings, onOpenPlayer = onOpenPlayer)
    },
    libraryContent: @Composable (onOpenPlayer: () -> Unit) -> Unit = { onOpenPlayer ->
        LibraryRoute(onOpenPlayer = onOpenPlayer)
    },
    playerContent: @Composable (onClose: () -> Unit) -> Unit = { onClose ->
        PlayerRoute(onClose = onClose)
    },
    settingsContent: @Composable (
        onOpenAbout: () -> Unit,
        onOpenLicenses: () -> Unit,
        onOpenUpdates: () -> Unit,
    ) -> Unit = { onOpenAbout, onOpenLicenses, onOpenUpdates ->
        SettingsRoute(
            themeMode = themeMode,
            onThemeModeChanged = onThemeModeChanged,
            onOpenAbout = onOpenAbout,
            onOpenLicenses = onOpenLicenses,
            onOpenUpdates = onOpenUpdates,
        )
    },
    updatesContent: @Composable (onNavigateBack: () -> Unit) -> Unit = { onNavigateBack ->
        UpdatesRoute(onNavigateBack = onNavigateBack)
    },
) {
    val navigateBack = { navController.navigateUp(); Unit }
    val openPlayer = {
        navController.navigate(YftDestination.PLAYER.route) { launchSingleTop = true }
    }
    val openAbout = {
        navController.navigate(YftDestination.ABOUT.route) { launchSingleTop = true }
    }
    val openLicenses = {
        navController.navigate(YftDestination.LICENSES.route) { launchSingleTop = true }
    }
    val openUpdates = {
        navController.navigate(YftDestination.UPDATES.route) { launchSingleTop = true }
    }
    val openQuickDownload = {
        navController.navigate(YftDestination.QUICK_DOWNLOAD.route) { launchSingleTop = true }
    }

    NavHost(
        navController = navController,
        startDestination = YftDestination.HOME.route,
        modifier = modifier,
    ) {
        composable(YftDestination.HOME.route) {
            homeContent(
                { link ->
                    navController.navigate(
                        if (link == null) YftDestination.BROWSER.route else browserRouteFor(link),
                    )
                },
                { navController.navigate(YftDestination.DETECTED_MEDIA.route) },
                { navController.navigateToTab(YftDestination.LIBRARY) },
                openQuickDownload,
                { navController.navigate(BROWSER_SEARCH_ROUTE) },
                openUpdates,
            )
        }
        composable(
            route = BROWSER_ROUTE_PATTERN,
            arguments = listOf(
                navArgument(BROWSER_LINK_ARGUMENT) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(BROWSER_SEARCH_ARGUMENT) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { entry ->
            browserContent(
                navigateBack,
                entry.arguments?.getString(BROWSER_LINK_ARGUMENT),
                navController::goHome,
                entry.arguments?.getBoolean(BROWSER_SEARCH_ARGUMENT) == true,
                navController::lookUpOnHome,
                openQuickDownload,
            )
        }
        composable(YftDestination.DETECTED_MEDIA.route) {
            detectedMediaContent(
                navigateBack,
                openQuickDownload,
                { navController.navigate(YftDestination.BROWSER.route) },
            )
        }
        // "Download as" rises as a sheet over the page that opened it. The dialog destination
        // keeps that page composed underneath and owns the Preview back stack entry.
        dialog(
            route = YftDestination.PREVIEW.route,
            dialogProperties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            YftModalSheet(onDismissRequest = navigateBack) { hide ->
                previewContent(
                    { hide(navigateBack) },
                    {
                        hide {
                            navController.popBackStack()
                            navController.navigateToTab(YftDestination.DOWNLOADS)
                        }
                    },
                )
            }
        }
        // The download sheet rises over Home, the browser or the found list the same way; More
        // formats › Details swaps it for Download as.
        dialog(
            route = YftDestination.QUICK_DOWNLOAD.route,
            dialogProperties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            YftModalSheet(onDismissRequest = navigateBack) { hide ->
                quickDownloadContent(
                    { hide(navigateBack) },
                    {
                        hide {
                            navController.popBackStack()
                            navController.navigateToTab(YftDestination.DOWNLOADS)
                        }
                    },
                    {
                        hide {
                            navController.popBackStack()
                            navController.navigate(YftDestination.PREVIEW.route) {
                                launchSingleTop = true
                            }
                        }
                    },
                )
            }
        }
        composable(YftDestination.DOWNLOADS.route) {
            downloadsContent({ navController.navigateToTab(YftDestination.SETTINGS) }, openPlayer)
        }
        composable(YftDestination.LIBRARY.route) {
            libraryContent(openPlayer)
        }
        composable(YftDestination.PLAYER.route) {
            playerContent(navigateBack)
        }
        composable(YftDestination.SETTINGS.route) {
            settingsContent(openAbout, openLicenses, openUpdates)
        }
        composable(YftDestination.ABOUT.route) {
            AboutRoute(onNavigateBack = navigateBack, onOpenLicenses = openLicenses)
        }
        composable(YftDestination.LICENSES.route) {
            LicensesScreen(onNavigateBack = navigateBack)
        }
        composable(YftDestination.UPDATES.route) {
            updatesContent(navigateBack)
        }
    }
}

/** Optional link handed from Home to the browser; plain "browser" still opens it empty. */
internal const val BROWSER_LINK_ARGUMENT = "link"
internal const val BROWSER_SEARCH_ARGUMENT = "search"
internal val BROWSER_ROUTE_PATTERN =
    "${YftDestination.BROWSER.route}?$BROWSER_LINK_ARGUMENT={$BROWSER_LINK_ARGUMENT}" +
        "&$BROWSER_SEARCH_ARGUMENT={$BROWSER_SEARCH_ARGUMENT}"

/** "Search to download" from Home: the start page with the address field focused. */
internal val BROWSER_SEARCH_ROUTE = "${YftDestination.BROWSER.route}?$BROWSER_SEARCH_ARGUMENT=true"

internal fun browserRouteFor(link: String): String =
    "${YftDestination.BROWSER.route}?$BROWSER_LINK_ARGUMENT=${Uri.encode(link)}"

/** Back to Home: the screens over it close, or Home's tab comes back. */
internal fun NavHostController.goHome() {
    if (!popBackStack(YftDestination.HOME.route, inclusive = false)) {
        navigateToTab(YftDestination.HOME)
    }
}

/**
 * Home looks [link] up as a pasted link (its download sheet opens): the browser's Download of
 * the link you copied and, v1.0.2 (item 4), a link another app shared. Home, the start
 * destination, is always in the back stack; its entry takes the link, then Home comes to the
 * front ([goHome]).
 */
internal fun NavHostController.lookUpOnHome(link: String) {
    runCatching { getBackStackEntry(YftDestination.HOME.route) }
        .getOrNull()
        ?.savedStateHandle
        ?.set(HOME_DOWNLOAD_LINK_KEY, link)
    goHome()
}

/**
 * Tabs keep one copy each: switching saves the tab being left and restores the one chosen, and
 * going back from any tab returns to Home before leaving the app.
 */
internal fun NavHostController.navigateToTab(destination: YftDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
