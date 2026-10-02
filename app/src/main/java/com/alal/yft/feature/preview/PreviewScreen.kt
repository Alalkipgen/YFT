package com.alal.yft.feature.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.alal.yft.core.model.media.MediaSizeAccuracy
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.ui.components.YftTopBar
import java.util.Locale

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PreviewRoute(
    onNavigateBack: () -> Unit,
    viewModel: PreviewViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PreviewScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onRetry = viewModel::retry,
        onTabSelected = viewModel::selectTab,
        onVariantSelected = viewModel::selectVariant,
        onDownload = viewModel::download,
        playerSurface = { variant, modifier ->
            PreviewPlayerSurface(
                variant = variant,
                modifier = modifier,
                viewModel = viewModel,
            )
        },
    )
}

@Composable
fun PreviewScreen(
    uiState: PreviewUiState,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onTabSelected: (PreviewTab) -> Unit,
    onVariantSelected: (String) -> Unit,
    playerSurface: @Composable (MediaVariant, Modifier) -> Unit,
    onDownload: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            YftTopBar(
                title = "Preview",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack,
            )
        },
    ) { contentPadding ->
        when (uiState) {
            PreviewUiState.Empty -> StatusPanel(
                title = "No media selected",
                message = "Open Browser and choose Preview on a detected media candidate.",
                modifier = Modifier.padding(contentPadding),
            )
            PreviewUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .testTag("preview-loading"),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            is PreviewUiState.Error -> ErrorPanel(
                state = uiState,
                onRetry = onRetry,
                modifier = Modifier.padding(contentPadding),
            )
            is PreviewUiState.Ready -> ReadyPreview(
                state = uiState,
                onTabSelected = onTabSelected,
                onVariantSelected = onVariantSelected,
                onDownload = onDownload,
                playerSurface = playerSurface,
                modifier = Modifier.padding(contentPadding),
            )
        }
    }
}

@Composable
private fun ReadyPreview(
    state: PreviewUiState.Ready,
    onTabSelected: (PreviewTab) -> Unit,
    onVariantSelected: (String) -> Unit,
    onDownload: () -> Unit,
    playerSurface: @Composable (MediaVariant, Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = state.asset.title?.trim()?.take(120)?.takeIf(String::isNotEmpty)
                ?: "Detected media",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleLarge,
        )
        playerSurface(
            state.selectedVariant,
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
                .testTag("preview-player"),
        )
        state.playbackError?.let { message ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("preview-playback-error"),
            ) {
                Text(message, modifier = Modifier.padding(12.dp))
            }
        }
        TabRow(
            selectedTabIndex = state.selectedTab.ordinal,
            modifier = Modifier.fillMaxWidth(),
        ) {
            PreviewTab.entries.forEach { tab ->
                Tab(
                    selected = state.selectedTab == tab,
                    onClick = { onTabSelected(tab) },
                    enabled = state.hasPreviewableVariant(tab),
                    modifier = Modifier.testTag("preview-tab-${tab.name.lowercase()}"),
                    text = {
                        Text(
                            when (tab) {
                                PreviewTab.VIDEO -> "Video"
                                PreviewTab.AUDIO -> "Audio"
                            },
                        )
                    },
                )
            }
        }
        DownloadAction(
            state = state,
            onDownload = onDownload,
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("preview-variants"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = state.visibleVariants,
                key = MediaVariant::id,
            ) { variant ->
                VariantCard(
                    variant = variant,
                    selected = variant.id == state.selectedVariantId,
                    onClick = { onVariantSelected(variant.id) },
                )
            }
        }
    }
}

@Composable
private fun DownloadAction(
    state: PreviewUiState.Ready,
    onDownload: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onDownload,
            enabled = state.canDownload,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("preview-download"),
        ) {
            Text(
                when (state.downloadStatus) {
                    PreviewDownloadStatus.Enqueuing -> "Queueing download…"
                    else -> "Download ${state.selectedVariant.displayTitle()}"
                },
            )
        }
        val status = state.downloadStatus
        val statusMessage = when (status) {
            PreviewDownloadStatus.Idle, PreviewDownloadStatus.Enqueuing -> null
            is PreviewDownloadStatus.Queued ->
                "Queued ${status.fileName}. Track progress on the Downloads screen."
            is PreviewDownloadStatus.Rejected -> status.message
        }
        statusMessage?.let { message ->
            Text(
                text = message,
                color = if (status is PreviewDownloadStatus.Rejected) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("preview-download-status"),
            )
        }
    }
}

@Composable
private fun VariantCard(
    variant: MediaVariant,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("preview-variant-${variant.id}"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = variant.displayTitle(),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (selected) "Selected" else variant.trackLabel(),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(
                text = variant.metadataLabel(),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!variant.isPreviewable) {
                Text(
                    text = "Unsupported codec",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedButton(
                onClick = onClick,
                enabled = variant.isPreviewable && !selected,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (selected) "Selected" else "Select variant")
            }
        }
    }
}

@Composable
private fun StatusPanel(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("preview-empty"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ErrorPanel(
    state: PreviewUiState.Error,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("preview-error"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Preview unavailable", style = MaterialTheme.typography.headlineSmall)
            Text(state.message, style = MaterialTheme.typography.bodyMedium)
            if (state.retryable) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.testTag("preview-retry"),
                ) {
                    Text("Try again")
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun PreviewPlayerSurface(
    variant: MediaVariant,
    modifier: Modifier,
    viewModel: PreviewViewModel,
) {
    val player = remember(variant.id) { viewModel.createPlayer() }
    DisposableEffect(player, variant.id) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                viewModel.onPlaybackError(variant.id)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player, variant.id) {
        runCatching {
            player.setMediaSource(viewModel.createMediaSource())
            player.playWhenReady = false
            player.prepare()
        }.onFailure {
            viewModel.onPlaybackError(variant.id)
        }
    }
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = true
                this.player = player
            }
        },
        update = { view -> view.player = player },
        onRelease = { view -> view.player = null },
        modifier = modifier,
    )
}

private fun MediaVariant.displayTitle(): String = when {
    trackType == MediaTrackType.AUDIO ->
        label?.take(80) ?: language?.let { "$it audio" } ?: "Audio track"
    height != null -> "${height}p"
    !label.isNullOrBlank() -> label.orEmpty().take(80)
    else -> "Quality unknown"
}

private fun MediaVariant.trackLabel(): String = when (trackType) {
    MediaTrackType.AUDIO_VIDEO -> "Video + audio"
    MediaTrackType.VIDEO -> "Video"
    MediaTrackType.AUDIO -> "Audio"
}

private fun MediaVariant.resolutionLabel(): String =
    if (width != null && height != null) "$width × $height" else "Unknown"

internal fun MediaVariant.metadataLabel(): String = buildList {
    add("Resolution: ${resolutionLabel()}")
    add("FPS: ${framesPerSecond?.formatDecimal() ?: "Unknown"}")
    add("Codec: ${codecs.takeIf(List<String>::isNotEmpty)?.joinToString() ?: "Unknown"}")
    add("Bitrate: ${bitrateBitsPerSecond?.formatBitrate() ?: "Unknown"}")
    add("Duration: ${durationMillis?.formatDuration() ?: "Unknown"}")
    add(sizeLabel())
    add("Format: ${container ?: mimeType ?: "Unknown"}")
    language?.let { add("Language: $it") }
}.joinToString("\n")

private fun MediaVariant.sizeLabel(): String = when (sizeAccuracy) {
    MediaSizeAccuracy.EXACT -> "Size: ${sizeBytes?.formatBytes() ?: "Unknown"}"
    MediaSizeAccuracy.ESTIMATED ->
        "Estimated size: ~${sizeBytes?.formatBytes() ?: "Unknown"}"
    null -> "Size: Unknown"
}

private fun Long.formatBytes(): String {
    if (this < 1_024) return "$this B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = toDouble()
    var unit = -1
    while (value >= 1_024 && unit < units.lastIndex) {
        value /= 1_024
        unit += 1
    }
    return String.format(Locale.US, "%.1f %s", value, units[unit])
}

private fun Long.formatBitrate(): String = when {
    this >= 1_000_000 -> String.format(Locale.US, "%.2f Mbps", this / 1_000_000.0)
    else -> String.format(Locale.US, "%.0f kbps", this / 1_000.0)
}

private fun Double.formatDecimal(): String =
    String.format(Locale.US, if (this % 1.0 == 0.0) "%.0f" else "%.2f", this)

private fun Long.formatDuration(): String {
    val totalSeconds = this / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
