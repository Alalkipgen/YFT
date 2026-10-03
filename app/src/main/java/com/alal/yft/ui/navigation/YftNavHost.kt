package com.alal.yft.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.feature.about.AboutScreen
import com.alal.yft.feature.browser.BrowserRoute
import com.alal.yft.feature.detectedmedia.DetectedMediaRoute
import com.alal.yft.feature.downloads.DownloadsRoute
import com.alal.yft.feature.home.HomeScreen
import com.alal.yft.feature.library.LibraryRoute
import com.alal.yft.feature.preview.PreviewRoute
import com.alal.yft.feature.settings.SettingsRoute

@Composable
fun YftNavHost(
    navController: NavHostController,
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    browserContent: @Composable (
        onNavigateBack: () -> Unit,
        onOpenPreview: () -> Unit,
        initialLink: String?,
    ) -> Unit = { onNavigateBack, onOpenPreview, initialLink ->
        BrowserRoute(
            onNavigateBack = onNavigateBack,
            onOpenPreview = onOpenPreview,
            initialLink = initialLink,
        )
    },
    detectedMediaContent: @Composable (
        onNavigateBack: () -> Unit,
        onOpenPreview: () -> Unit,
        onOpenBrowser: () -> Unit,
    ) -> Unit = { onNavigateBack, onOpenPreview, onOpenBrowser ->
        DetectedMediaRoute(
            onNavigateBack = onNavigateBack,
            onOpenPreview = onOpenPreview,
            onOpenBrowser = onOpenBrowser,
        )
    },
    previewContent: @Composable (onNavigateBack: () -> Unit) -> Unit = { onNavigateBack ->
        PreviewRoute(onNavigateBack = onNavigateBack)
    },
    downloadsContent: @Composable (onNavigateBack: () -> Unit) -> Unit = { onNavigateBack ->
        DownloadsRoute(onNavigateBack = onNavigateBack)
    },
    libraryContent: @Composable (onNavigateBack: () -> Unit) -> Unit = { onNavigateBack ->
        LibraryRoute(onNavigateBack = onNavigateBack)
    },
    settingsContent: @Composable (
        onNavigateBack: () -> Unit,
        onOpenAbout: () -> Unit,
    ) -> Unit = { onNavigateBack, onOpenAbout ->
        SettingsRoute(
            themeMode = themeMode,
            onThemeModeChanged = onThemeModeChanged,
            onNavigateBack = onNavigateBack,
            onOpenAbout = onOpenAbout,
        )
    },
) {
    val navigateBack = { navController.navigateUp(); Unit }

    NavHost(
        navController = navController,
        startDestination = YftDestination.HOME.route,
        modifier = modifier,
    ) {
        composable(YftDestination.HOME.route) {
            HomeScreen(
                onOpenDestination = navController::open,
                onOpenLink = { link -> navController.navigate(browserRouteFor(link)) },
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
            ),
        ) { entry ->
            browserContent(
                navigateBack,
                { navController.navigate(YftDestination.PREVIEW.route) },
                entry.arguments?.getString(BROWSER_LINK_ARGUMENT),
            )
        }
        composable(YftDestination.DETECTED_MEDIA.route) {
            detectedMediaContent(
                navigateBack,
                { navController.navigate(YftDestination.PREVIEW.route) },
                { navController.navigate(YftDestination.BROWSER.route) },
            )
        }
        composable(YftDestination.PREVIEW.route) {
            previewContent(navigateBack)
        }
        composable(YftDestination.DOWNLOADS.route) {
            downloadsContent(navigateBack)
        }
        composable(YftDestination.LIBRARY.route) {
            libraryContent(navigateBack)
        }
        composable(YftDestination.SETTINGS.route) {
            settingsContent(navigateBack) { navController.navigate(YftDestination.ABOUT.route) }
        }
        composable(YftDestination.ABOUT.route) {
            AboutScreen(onNavigateBack = navigateBack)
        }
    }
}

/** Optional link handed from Home to the browser; plain "browser" still opens it empty. */
internal const val BROWSER_LINK_ARGUMENT = "link"
internal val BROWSER_ROUTE_PATTERN =
    "${YftDestination.BROWSER.route}?$BROWSER_LINK_ARGUMENT={$BROWSER_LINK_ARGUMENT}"

internal fun browserRouteFor(link: String): String =
    "${YftDestination.BROWSER.route}?$BROWSER_LINK_ARGUMENT=${Uri.encode(link)}"

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

/** Opens a tab the way the bottom bar does, or pushes a full-screen destination. */
internal fun NavHostController.open(destination: YftDestination) {
    if (destination.isTopLevel) navigateToTab(destination) else navigate(destination.route)
}
