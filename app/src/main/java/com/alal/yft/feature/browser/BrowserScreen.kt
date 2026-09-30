package com.alal.yft.feature.browser

import androidx.compose.runtime.Composable
import com.alal.yft.ui.components.PhasePlaceholderScreen
import com.alal.yft.ui.navigation.YftDestination

@Composable
fun BrowserScreen(onNavigateBack: () -> Unit) {
    PhasePlaceholderScreen(
        title = YftDestination.BROWSER.title,
        summary = YftDestination.BROWSER.summary,
        phaseNote = "Secure WebView and generic media observation are Phase 2 work.",
        onNavigateBack = onNavigateBack,
    )
}
