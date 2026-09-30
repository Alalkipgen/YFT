package com.alal.yft.feature.detectedmedia

import androidx.compose.runtime.Composable
import com.alal.yft.ui.components.PhasePlaceholderScreen
import com.alal.yft.ui.navigation.YftDestination

@Composable
fun DetectedMediaScreen(onNavigateBack: () -> Unit) {
    PhasePlaceholderScreen(
        title = YftDestination.DETECTED_MEDIA.title,
        summary = YftDestination.DETECTED_MEDIA.summary,
        phaseNote = "Candidate collection and normalization are Phase 2 work.",
        onNavigateBack = onNavigateBack,
    )
}
