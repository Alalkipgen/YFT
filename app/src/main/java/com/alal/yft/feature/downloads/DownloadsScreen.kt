package com.alal.yft.feature.downloads

import androidx.compose.runtime.Composable
import com.alal.yft.ui.components.PhasePlaceholderScreen
import com.alal.yft.ui.navigation.YftDestination

@Composable
fun DownloadsScreen(onNavigateBack: () -> Unit) {
    PhasePlaceholderScreen(
        title = YftDestination.DOWNLOADS.title,
        summary = YftDestination.DOWNLOADS.summary,
        phaseNote = "Transfer engines, foreground service and recovery are Phase 4 work.",
        onNavigateBack = onNavigateBack,
    )
}
