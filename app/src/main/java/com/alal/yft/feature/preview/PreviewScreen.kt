package com.alal.yft.feature.preview

import androidx.compose.runtime.Composable
import com.alal.yft.ui.components.PhasePlaceholderScreen
import com.alal.yft.ui.navigation.YftDestination

@Composable
fun PreviewScreen(onNavigateBack: () -> Unit) {
    PhasePlaceholderScreen(
        title = YftDestination.PREVIEW.title,
        summary = YftDestination.PREVIEW.summary,
        phaseNote = "Variant resolution and Media3 preview UI are Phase 3 work.",
        onNavigateBack = onNavigateBack,
    )
}
