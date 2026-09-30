package com.alal.yft.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.feature.about.AboutScreen
import com.alal.yft.feature.browser.BrowserScreen
import com.alal.yft.feature.detectedmedia.DetectedMediaScreen
import com.alal.yft.feature.downloads.DownloadsScreen
import com.alal.yft.feature.home.HomeScreen
import com.alal.yft.feature.library.LibraryScreen
import com.alal.yft.feature.preview.PreviewScreen
import com.alal.yft.feature.settings.SettingsScreen

@Composable
fun YftNavHost(
    navController: NavHostController,
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
) {
    val navigateBack = { navController.navigateUp(); Unit }

    NavHost(
        navController = navController,
        startDestination = YftDestination.HOME.route,
    ) {
        composable(YftDestination.HOME.route) {
            HomeScreen(onOpenDestination = { navController.navigate(it.route) })
        }
        composable(YftDestination.BROWSER.route) {
            BrowserScreen(onNavigateBack = navigateBack)
        }
        composable(YftDestination.DETECTED_MEDIA.route) {
            DetectedMediaScreen(onNavigateBack = navigateBack)
        }
        composable(YftDestination.PREVIEW.route) {
            PreviewScreen(onNavigateBack = navigateBack)
        }
        composable(YftDestination.DOWNLOADS.route) {
            DownloadsScreen(onNavigateBack = navigateBack)
        }
        composable(YftDestination.LIBRARY.route) {
            LibraryScreen(onNavigateBack = navigateBack)
        }
        composable(YftDestination.SETTINGS.route) {
            SettingsScreen(
                themeMode = themeMode,
                onThemeModeChanged = onThemeModeChanged,
                onNavigateBack = navigateBack,
            )
        }
        composable(YftDestination.ABOUT.route) {
            AboutScreen(onNavigateBack = navigateBack)
        }
    }
}
