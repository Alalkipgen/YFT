package com.alal.yft.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.alal.yft.ui.navigation.YftDestination

/**
 * Opens the Updates screen by itself, once per found version (§4.5): at once when Home is in
 * front, else at the next app open — never over another screen or a sheet, never as a dialog.
 * Back is "not now": the screen does not open again for that version. Collected only while the
 * app is in front.
 */
@Composable
fun UpdateAutoOpen(
    navController: NavHostController,
    viewModel: UpdatesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val pending = state.autoOpen
    LaunchedEffect(pending?.version?.name, route) {
        val update = pending ?: return@LaunchedEffect
        when (route) {
            // The graph is not set yet.
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
}
