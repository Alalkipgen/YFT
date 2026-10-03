package com.alal.yft.ui

import android.graphics.Color
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.alal.yft.BuildConfig
import com.alal.yft.feature.library.LibraryPlayback
import com.alal.yft.feature.library.LocalLibraryPlayback
import com.alal.yft.feature.library.LocalMediaDetailsSource
import com.alal.yft.feature.library.MediaDetailsSource
import com.alal.yft.feature.library.MiniPlayerHost
import com.alal.yft.feature.library.playbackFailedMessage
import com.alal.yft.ui.navigation.YftBottomBar
import com.alal.yft.ui.navigation.YftDestination
import com.alal.yft.ui.navigation.YftNavHost
import com.alal.yft.ui.navigation.navigateToTab
import com.alal.yft.ui.theme.YftTheme
import com.alal.yft.ui.theme.isDarkTheme

/**
 * The whole app. [playback] is the Library's player, whose mini player sits above the bottom bar
 * on every tab while audio plays; [mediaDetails] gives saved files their real thumbnails.
 */
@Composable
fun YftApp(
    viewModel: AppViewModel = hiltViewModel(),
    playback: LibraryPlayback? = null,
    mediaDetails: MediaDetailsSource = MediaDetailsSource.None,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val darkTheme = isDarkTheme(uiState.themeMode)
    SystemBarsFollowTheme(darkTheme)
    PauseWhenStopped(playback)
    ReportPlaybackFailures(playback)

    YftTheme(themeMode = uiState.themeMode) {
        CompositionLocalProvider(
            LocalMediaDetailsSource provides mediaDetails,
            LocalLibraryPlayback provides playback,
        ) {
            val navController = rememberNavController()
            YftAppShell(
                navController = navController,
                activeDownloads = uiState.activeDownloads,
                miniPlayer = { playback?.let { MiniPlayerHost(it) } },
            ) {
                YftNavHost(
                    navController = navController,
                    themeMode = uiState.themeMode,
                    onThemeModeChanged = viewModel::setThemeMode,
                    modifier = it,
                )
            }
        }
    }
}

/**
 * The bottom bar shows on the four tabs only; Browser, Detected Media, Preview, About and the
 * video player open full screen. [miniPlayer] sits right above the bar, so it shows on every tab
 * and nowhere else. [content] gets the padding both take so screens never draw under them.
 */
@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun YftAppShell(
    navController: NavHostController,
    activeDownloads: Int,
    miniPlayer: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    // Before the graph is attached there is no entry yet; the start destination is Home.
    val currentTab = backStackEntry?.destination?.route.let { route ->
        if (route == null) YftDestination.HOME else YftDestination.topLevelFor(route)
    }

    Scaffold(
        // Debug-only IDs let the CI emulator report actual accessibility-tree bounds.
        modifier = Modifier.semantics { testTagsAsResourceId = BuildConfig.DEBUG },
        containerColor = YftTheme.colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (currentTab != null) {
                Column {
                    miniPlayer()
                    YftBottomBar(
                        selected = currentTab,
                        activeDownloads = activeDownloads,
                        onSelect = navController::navigateToTab,
                    )
                }
            }
        },
    ) { padding ->
        content(Modifier.padding(padding).consumeWindowInsets(padding))
    }
}

/**
 * There is no background playback service, so Library playback pauses when the app leaves the
 * screen. Rotation recreates the activity without leaving, so it keeps playing.
 */
@Composable
private fun PauseWhenStopped(playback: LibraryPlayback?) {
    if (playback == null) return
    val activity = LocalActivity.current
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (activity?.isChangingConfigurations != true) playback.pause()
    }
}

/** Playback can fail on any tab (the mini player follows the user), so the app says so. */
@Composable
private fun ReportPlaybackFailures(playback: LibraryPlayback?) {
    if (playback == null) return
    val context = LocalContext.current
    LaunchedEffect(playback) {
        playback.failures.collect { item ->
            Toast.makeText(context, playbackFailedMessage(item), Toast.LENGTH_LONG).show()
        }
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
