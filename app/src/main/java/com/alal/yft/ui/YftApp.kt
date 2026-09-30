package com.alal.yft.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.alal.yft.ui.navigation.YftNavHost
import com.alal.yft.ui.theme.YftTheme

@Composable
fun YftApp(viewModel: AppViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    YftTheme(themeMode = uiState.themeMode) {
        YftNavHost(
            navController = rememberNavController(),
            themeMode = uiState.themeMode,
            onThemeModeChanged = viewModel::setThemeMode,
        )
    }
}
