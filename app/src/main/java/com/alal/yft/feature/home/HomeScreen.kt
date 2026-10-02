package com.alal.yft.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alal.yft.ui.components.YftTopBar
import com.alal.yft.ui.navigation.YftDestination

/**
 * Start screen: a link field with Paste, plus every app section.
 *
 * The clipboard is read only inside the Paste button's click handler, never on composition or
 * focus, so copied text stays private until the user asks for it.
 */
@Composable
fun HomeScreen(
    onOpenDestination: (YftDestination) -> Unit,
    onOpenLink: (String) -> Unit = { onOpenDestination(YftDestination.BROWSER) },
) {
    var link by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val submit = {
        val trimmed = link.trim()
        if (trimmed.isNotEmpty()) onOpenLink(trimmed)
    }

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
                            modifier = Modifier.semantics { heading() },
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = "Paste a page or media link, or browse to a page. YFT finds " +
                                "direct, HLS and DASH media you can preview and download.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = link,
                            onValueChange = { link = it.take(HomeLinks.MAX_LENGTH) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home-link"),
                            label = { Text(text = "Page or media link") },
                            placeholder = { Text(text = "https://example.com/video") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go,
                            ),
                            keyboardActions = KeyboardActions(onGo = { submit() }),
                            trailingIcon = {
                                if (link.isNotEmpty()) {
                                    IconButton(
                                        onClick = { link = "" },
                                        modifier = Modifier.testTag("home-link-clear"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Clear,
                                            contentDescription = "Clear link",
                                        )
                                    }
                                }
                            },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    HomeLinks.fromClipboard(clipboard.getText()?.text)
                                        ?.let { link = it }
                                },
                                modifier = Modifier.testTag("home-paste"),
                            ) {
                                Text(text = "Paste")
                            }
                            Button(
                                onClick = submit,
                                enabled = link.isNotBlank(),
                                modifier = Modifier.testTag("home-open-link"),
                            ) {
                                Text(text = "Open link")
                            }
                        }
                        TextButton(
                            onClick = { onOpenDestination(YftDestination.BROWSER) },
                            modifier = Modifier.testTag("home-open-browser"),
                        ) {
                            Text(text = "Open browser without a link")
                        }
                    }
                }
            }

            item {
                Text(
                    text = "App sections",
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .semantics { heading() },
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
