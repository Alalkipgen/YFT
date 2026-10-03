package com.alal.yft.ui

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.alal.yft.ui.navigation.YftBottomBar
import com.alal.yft.ui.navigation.YftDestination
import com.alal.yft.ui.navigation.YftNavHost
import com.alal.yft.ui.navigation.navigateToTab
import com.alal.yft.ui.theme.YftTheme
import com.alal.yft.ui.theme.isDarkTheme

@Composable
fun YftApp(viewModel: AppViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val darkTheme = isDarkTheme(uiState.themeMode)
    SystemBarsFollowTheme(darkTheme)

    YftTheme(themeMode = uiState.themeMode) {
        val navController = rememberNavController()
        YftAppShell(navController = navController, activeDownloads = uiState.activeDownloads) {
            YftNavHost(
                navController = navController,
                themeMode = uiState.themeMode,
                onThemeModeChanged = viewModel::setThemeMode,
                modifier = it,
            )
        }
    }
}

/**
 * The bottom bar shows on the four tabs only; Browser, Detected Media, Preview and About open
 * full screen. [content] gets the padding the bar takes so screens never draw under it.
 */
@Composable
internal fun YftAppShell(
    navController: NavHostController,
    activeDownloads: Int,
    content: @Composable (Modifier) -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    // Before the graph is attached there is no entry yet; the start destination is Home.
    val currentTab = backStackEntry?.destination?.route.let { route ->
        if (route == null) YftDestination.HOME else YftDestination.topLevelFor(route)
    }

    Scaffold(
        containerColor = YftTheme.colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (currentTab != null) {
                YftBottomBar(
                    selected = currentTab,
                    activeDownloads = activeDownloads,
                    onSelect = navController::navigateToTab,
                )
            }
        },
    ) { padding ->
        content(Modifier.padding(padding).consumeWindowInsets(padding))
    }
}

/**
 * Status and navigation bar icons follow the app's theme choice rather than the system's, so a
 * Dark choice on a light phone still gets light icons.
 */
@Composable
private fun SystemBarsFollowTheme(darkTheme: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    DisposableEffect(activity, darkTheme) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) {
                darkTheme
            },
            navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
        )
        onDispose {}
    }
}

/** The scrims androidx.activity uses by default for three-button navigation. */
private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
