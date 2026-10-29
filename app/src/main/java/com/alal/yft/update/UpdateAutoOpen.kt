package com.alal.yft.update

import android.content.ActivityNotFoundException
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.alal.yft.ui.navigation.YftDestination

/**
 * Opens the Updates screen by itself, once per found version (§4.5): at once when Home is in
 * front, else at the next app open — never over another screen or a sheet, never as a dialog.
 * Back is "not now": the screen does not open again for that version. It also starts Android's
 * install confirmation once the checks passed (§4.4 step 7). Both are collected only while the app
 * is in front, so nothing opens from the background: a confirmation that came then waits for it.
 */
@Composable
fun UpdateAutoOpen(
    navController: NavHostController,
    viewModel: UpdatesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val install by viewModel.install.collectAsStateWithLifecycle()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val pending = state.autoOpen
    LaunchedEffect(pending?.version?.name, route) {
        val update = pending ?: return@LaunchedEffect
        // The graph is not set yet.
        if (route == null) return@LaunchedEffect
        // Where the app is now, not where it was when this started: a tab asked for in the same
        // frame (a download notification's tap, ShowRequestedTab) may have left Home already.
        when (navController.currentDestination?.route) {
            null -> Unit
            YftDestination.HOME.route -> {
                viewModel.markOpened(update.version.name)
                navController.navigate(YftDestination.UPDATES.route) { launchSingleTop = true }
            }
            // Already on the screen: it showed the version.
            YftDestination.UPDATES.route -> viewModel.markOpened(update.version.name)
            else -> viewModel.deferAutoOpen()
        }
    }
    val context = LocalContext.current
    val confirmation = (install as? InstallState.Installing)?.confirm
    LaunchedEffect(confirmation) {
        val request = confirmation ?: return@LaunchedEffect
        viewModel.confirmStarted(request.sessionId)
        try {
            // Android's own screen: the user installs or cancels there.
            context.startActivity(request.intent)
        } catch (missing: ActivityNotFoundException) {
            viewModel.confirmFailed(request.sessionId)
        } catch (refused: SecurityException) {
            viewModel.confirmFailed(request.sessionId)
        }
    }
}
