package com.alal.yft.feature.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alal.yft.BuildConfig
import com.alal.yft.ui.components.YftTopBar

@Composable
fun AboutScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            YftTopBar(
                title = "About",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "Video Downloader", style = MaterialTheme.typography.headlineSmall)
            Text(text = "Version ${BuildConfig.VERSION_NAME}")
            Text(text = "Built for authorized, non-DRM media. It does not bypass DRM, payment protection or private access controls.")
            Text(text = "Sensitive cookies, tokens and signed URLs must never be written to logs.")
        }
    }
}
