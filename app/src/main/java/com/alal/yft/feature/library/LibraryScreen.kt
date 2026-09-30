package com.alal.yft.feature.library

import androidx.compose.runtime.Composable
import com.alal.yft.ui.components.PhasePlaceholderScreen
import com.alal.yft.ui.navigation.YftDestination

@Composable
fun LibraryScreen(onNavigateBack: () -> Unit) {
    PhasePlaceholderScreen(
        title = YftDestination.LIBRARY.title,
        summary = YftDestination.LIBRARY.summary,
        phaseNote = "MediaStore and Storage Access Framework export arrive with download support.",
        onNavigateBack = onNavigateBack,
    )
}
