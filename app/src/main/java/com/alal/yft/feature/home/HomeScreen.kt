package com.alal.yft.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.alal.yft.ui.components.YftTopBar
import com.alal.yft.ui.navigation.YftDestination

@Composable
fun HomeScreen(onOpenDestination: (YftDestination) -> Unit) {
    Scaffold(
        topBar = {
            YftTopBar(
                title = "Video Downloader",
                canNavigateBack = false,
                onNavigateBack = {},
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("home-list")
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Download authorized, non-DRM media",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = "Paste-link and media detection workflows are added in later phases. The production navigation foundation is ready now.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { onOpenDestination(YftDestination.BROWSER) }) {
                            Text(text = "Open browser")
                        }
                    }
                }
            }

            item {
                Text(
                    text = "App sections",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            items(
                items = YftDestination.homeActions,
                key = YftDestination::route,
            ) { destination ->
                OutlinedButton(
                    onClick = { onOpenDestination(destination) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("destination-${destination.route}"),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(text = destination.title)
                        Text(
                            text = destination.summary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
