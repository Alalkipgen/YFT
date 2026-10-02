package com.alal.yft.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.alal.yft.feature.downloads.formatBytes
import com.alal.yft.ui.components.YftTopBar
import com.alal.yft.ui.navigation.YftDestination
import java.text.DateFormat
import java.util.Date

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LibraryRoute(
    onNavigateBack: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Files can change while YFT is in the background, so the list is re-read on every resume.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    LibraryScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onRefresh = viewModel::refresh,
        onPlay = viewModel::play,
        onStopPlayback = viewModel::stopPlayback,
        onOpen = { item ->
            if (!LibraryIntents.start(context, LibraryIntents.view(item))) {
                viewModel.showMessage("No app on this device can open ${item.displayName}.")
            }
        },
        onShare = { item ->
            if (!LibraryIntents.start(context, LibraryIntents.share(item))) {
                viewModel.showMessage("${item.displayName} could not be shared.")
            }
        },
        onRequestDelete = viewModel::requestDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onDismissDelete = viewModel::dismissDelete,
        playerSurface = { item, modifier ->
            LibraryPlayerSurface(item = item, modifier = modifier, viewModel = viewModel)
        },
    )
}

@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit = {},
    onPlay: (LibraryItem) -> Unit = {},
    onStopPlayback: () -> Unit = {},
    onOpen: (LibraryItem) -> Unit = {},
    onShare: (LibraryItem) -> Unit = {},
    onRequestDelete: (LibraryItem) -> Unit = {},
    onConfirmDelete: () -> Unit = {},
    onDismissDelete: () -> Unit = {},
    playerSurface: @Composable (LibraryItem, Modifier) -> Unit = { _, _ -> },
) {
    val ready = uiState as? LibraryUiState.Ready
    ready?.pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text(text = "Delete this file?") },
            text = {
                Text(
                    text = "${item.displayName} will be removed from ${item.location.label}. " +
                        "This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirmDelete,
                    modifier = Modifier.testTag("library-delete-confirm"),
                ) { Text(text = "Delete") }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissDelete,
                    modifier = Modifier.testTag("library-delete-dismiss"),
                ) { Text(text = "Cancel") }
            },
            modifier = Modifier.testTag("library-delete-dialog"),
        )
    }
    Scaffold(
        topBar = {
            YftTopBar(
                title = YftDestination.LIBRARY.title,
                canNavigateBack = true,
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(
                        onClick = onRefresh,
                        enabled = uiState !is LibraryUiState.Loading,
                        modifier = Modifier.testTag("library-refresh"),
                    ) {
                        Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when (uiState) {
            LibraryUiState.Loading -> CenteredStatus(
                modifier = contentModifier.testTag("library-loading"),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.semantics { contentDescription = "Loading the library" },
                )
            }

            is LibraryUiState.Error -> CenteredStatus(
                modifier = contentModifier.testTag("library-error"),
            ) {
                Text(
                    text = uiState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(
                    onClick = onRefresh,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .testTag("library-retry"),
                ) { Text(text = "Try again") }
            }

            is LibraryUiState.Ready -> ReadyLibrary(
                state = uiState,
                modifier = contentModifier,
                onPlay = onPlay,
                onStopPlayback = onStopPlayback,
                onOpen = onOpen,
                onShare = onShare,
                onRequestDelete = onRequestDelete,
                playerSurface = playerSurface,
            )
        }
    }
}

@Composable
private fun ReadyLibrary(
    state: LibraryUiState.Ready,
    modifier: Modifier,
    onPlay: (LibraryItem) -> Unit,
    onStopPlayback: () -> Unit,
    onOpen: (LibraryItem) -> Unit,
    onShare: (LibraryItem) -> Unit,
    onRequestDelete: (LibraryItem) -> Unit,
    playerSurface: @Composable (LibraryItem, Modifier) -> Unit,
) {
    if (state.items.isEmpty() && state.message == null) {
        CenteredStatus(modifier = modifier.testTag("library-empty")) {
            Text(
                text = "No finished downloads yet",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "Completed downloads saved to Download/YFT or to app storage appear here.",
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier.testTag("library-list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.message?.let { message ->
            item(key = "message") {
                Text(
                    text = message,
                    modifier = Modifier.testTag("library-message"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        state.playing?.let { item ->
            item(key = "player") {
                PlayerCard(item = item, onClose = onStopPlayback, playerSurface = playerSurface)
            }
        }
        if (state.items.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = "No finished downloads yet",
                    modifier = Modifier.testTag("library-empty"),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        items(items = state.items, key = LibraryItem::id) { item ->
            LibraryItemCard(
                item = item,
                onPlay = onPlay,
                onOpen = onOpen,
                onShare = onShare,
                onRequestDelete = onRequestDelete,
            )
        }
    }
}

@Composable
private fun PlayerCard(
    item: LibraryItem,
    onClose: () -> Unit,
    playerSurface: @Composable (LibraryItem, Modifier) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().testTag("library-player")) {
        val surfaceModifier = if (item.isAudio) {
            Modifier
                .fillMaxWidth()
                .height(96.dp)
        } else {
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        }
        playerSurface(item, surfaceModifier)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.displayName,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
            )
            TextButton(
                onClick = onClose,
                modifier = Modifier.testTag("library-player-close"),
            ) { Text(text = "Close player") }
        }
    }
}

@Composable
private fun LibraryItemCard(
    item: LibraryItem,
    onPlay: (LibraryItem) -> Unit,
    onOpen: (LibraryItem) -> Unit,
    onShare: (LibraryItem) -> Unit,
    onRequestDelete: (LibraryItem) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().testTag("library-item-${item.id}")) {
        Column(modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 4.dp)) {
            Text(
                text = item.displayName,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = item.metadataLabel(),
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (item.isPlayable) {
                    TextButton(
                        onClick = { onPlay(item) },
                        modifier = Modifier.testTag("library-play-${item.id}"),
                    ) { Text(text = "Play") }
                }
                TextButton(
                    onClick = { onOpen(item) },
                    modifier = Modifier.testTag("library-open-${item.id}"),
                ) { Text(text = "Open") }
                TextButton(
                    onClick = { onShare(item) },
                    modifier = Modifier.testTag("library-share-${item.id}"),
                ) { Text(text = "Share") }
                TextButton(
                    onClick = { onRequestDelete(item) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.testTag("library-delete-${item.id}"),
                ) { Text(text = "Delete") }
            }
        }
    }
}

@Composable
private fun CenteredStatus(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        content()
    }
}

internal fun LibraryItem.metadataLabel(): String = buildList {
    add(
        when {
            isAudio -> "Audio"
            mimeType?.startsWith("video/") == true -> "Video"
            else -> "File"
        },
    )
    sizeBytes?.let { add(formatBytes(it)) }
    add(location.label)
    modifiedAtEpochMs?.let { add(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))) }
}.joinToString(separator = " · ")

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun LibraryPlayerSurface(
    item: LibraryItem,
    modifier: Modifier,
    viewModel: LibraryViewModel,
) {
    val player = remember(item.id) { viewModel.createPlayer() }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                viewModel.onPlaybackError(item)
            }
        }
        player.addListener(listener)
        player.setMediaItem(MediaItem.fromUri(item.uri))
        player.prepare()
        player.playWhenReady = true
        onDispose {
            player.removeListener(listener)
            player.release()
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
