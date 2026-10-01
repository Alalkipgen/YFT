package com.alal.yft.feature.detectedmedia

import androidx.compose.runtime.Composable
import com.alal.yft.ui.components.PhasePlaceholderScreen
import com.alal.yft.ui.navigation.YftDestination

@Composable
fun DetectedMediaScreen(onNavigateBack: () -> Unit) {
    PhasePlaceholderScreen(
        title = YftDestination.DETECTED_MEDIA.title,
        summary = YftDestination.DETECTED_MEDIA.summary,
        phaseNote = "Current-page candidates are available from the browser button. A cross-session catalog is not part of Phase 2.",
        onNavigateBack = onNavigateBack,
    )
}
