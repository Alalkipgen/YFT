package com.alal.yft.feature.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.download.DownloadPlanType
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.download.policy.TransferNetworkState
import com.alal.yft.ui.components.YftTopBar
import com.alal.yft.ui.navigation.YftDestination
import java.util.Locale

@Composable
fun DownloadsRoute(
    onNavigateBack: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DownloadsScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onAction = viewModel::onAction,
        onPauseAll = viewModel::pauseAll,
    )
}

@Composable
fun DownloadsScreen(
    uiState: DownloadsUiState,
    onNavigateBack: () -> Unit,
    onAction: (DownloadAction, String) -> Unit,
    onPauseAll: () -> Unit,
) {
    Scaffold(
        topBar = {
            YftTopBar(
                title = YftDestination.DOWNLOADS.title,
                canNavigateBack = true,
                onNavigateBack = onNavigateBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            NetworkBanner(network = uiState.network)
            if (uiState.isEmpty) {
                EmptyQueue()
                return@Column
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${uiState.occupyingCount} active of ${uiState.rows.size}",
                    modifier = Modifier.testTag("downloads-summary"),
                    style = MaterialTheme.typography.labelLarge,
                )
                TextButton(
                    onClick = onPauseAll,
                    enabled = uiState.canPauseAll,
                    modifier = Modifier.testTag("downloads-pause-all"),
                ) {
                    Text(text = "Pause all")
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("downloads-list"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = uiState.rows, key = { it.id }) { row ->
                    DownloadRow(row = row, onAction = onAction)
                }
            }
        }
    }
}

/** Explains why queued work is not moving; nothing is shown while transfers may run. */
@Composable
private fun NetworkBanner(network: TransferNetworkState) {
    val (title, body) = when (network) {
        TransferNetworkState.ALLOWED -> return
        TransferNetworkState.WAITING_FOR_UNMETERED ->
            "Waiting for Wi-Fi" to "Downloads start on Wi-Fi or another unmetered network. " +
                "Turn off \"Download over Wi-Fi only\" in Settings to use mobile data."

        TransferNetworkState.OFFLINE ->
            "No connection" to "Downloads continue automatically when the device is back online."
    }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag("downloads-network-banner")
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EmptyQueue() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("downloads-empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "No downloads yet", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Open a page in the browser, preview the media and start a download.",
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DownloadRow(
    row: DownloadRowUiState,
    onAction: (DownloadAction, String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().testTag("download-${row.id}")) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = row.displayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = statusLabel(row),
                modifier = Modifier
                    .padding(top = 2.dp)
                    .testTag("download-status-${row.id}"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            val fraction = row.progressFraction
            if (fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("download-progress-${row.id}"),
                )
            } else if (row.isOccupyingQueue) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("download-progress-indeterminate-${row.id}"),
                )
            }

            Text(
                text = sizeLabel(row),
                modifier = Modifier
                    .padding(top = 6.dp)
                    .testTag("download-size-${row.id}"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            if (row.requiresLinkRefresh) {
                Text(
                    text = "Media link expired. Re-open the page in the browser to refresh it.",
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .testTag("download-refresh-note-${row.id}"),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                DownloadAction.entries
                    .filter { it in row.availableActions }
                    .forEach { action ->
                        TextButton(
                            onClick = { onAction(action, row.id) },
                            modifier = Modifier.testTag(actionTag(action, row.id)),
                        ) {
                            Text(text = actionLabel(action))
                        }
                    }
            }
        }
    }
}

internal fun actionTag(action: DownloadAction, id: String): String =
    "download-action-${action.name.lowercase(Locale.US)}-$id"

internal fun actionLabel(action: DownloadAction): String = when (action) {
    DownloadAction.PAUSE -> "Pause"
    DownloadAction.RESUME -> "Resume"
    DownloadAction.RETRY -> "Retry"
    DownloadAction.CANCEL -> "Cancel"
    DownloadAction.DELETE -> "Remove"
}

internal fun statusLabel(row: DownloadRowUiState): String {
    val status = when (row.status) {
        DownloadTaskStatus.QUEUED -> "Queued"
        DownloadTaskStatus.PROBING -> "Checking source"
        DownloadTaskStatus.RUNNING -> "Downloading"
        DownloadTaskStatus.PAUSING -> "Pausing"
        DownloadTaskStatus.PAUSED -> "Paused"
        DownloadTaskStatus.WAITING_FOR_NETWORK -> "Waiting for network"
        DownloadTaskStatus.NEEDS_REFRESH -> "Needs a refreshed link"
        DownloadTaskStatus.VERIFYING -> "Verifying"
        DownloadTaskStatus.COMPLETED -> "Completed"
        DownloadTaskStatus.FAILED -> "Failed"
        DownloadTaskStatus.CANCELLED -> "Cancelled"
    }
    val details = listOfNotNull(
        planLabel(row.planType),
        destinationLabel(row.destinationKind),
        row.failureReason?.let(::failureLabel),
    )
    return "$status · ${details.joinToString(" · ")}"
}

internal fun planLabel(planType: DownloadPlanType): String = when (planType) {
    DownloadPlanType.DIRECT -> "Direct file"
    DownloadPlanType.HLS -> "HLS stream"
    DownloadPlanType.DASH -> "DASH stream"
    DownloadPlanType.AUDIO_VIDEO_MUX -> "Audio + video"
}

internal fun destinationLabel(kind: DownloadDestinationKind): String = when (kind) {
    DownloadDestinationKind.APP_PRIVATE -> "App storage"
    DownloadDestinationKind.MEDIA_STORE -> "Device media"
    DownloadDestinationKind.SAF_DOCUMENT -> "Chosen folder"
}

internal fun failureLabel(reason: DownloadFailureReason): String = when (reason) {
    DownloadFailureReason.INVALID_URL -> "invalid link"
    DownloadFailureReason.EXPIRED_URL -> "link expired"
    DownloadFailureReason.DRM_PROTECTED -> "protected content"
    DownloadFailureReason.UNSUPPORTED_SOURCE -> "unsupported source"
    DownloadFailureReason.INCOMPATIBLE_TRACKS -> "incompatible tracks"
    DownloadFailureReason.UNSAFE_REDIRECT -> "unsafe redirect"
    DownloadFailureReason.TOO_MANY_REDIRECTS -> "too many redirects"
    DownloadFailureReason.AUTHENTICATION_REQUIRED -> "sign-in required"
    DownloadFailureReason.ACCESS_DENIED -> "access denied"
    DownloadFailureReason.NOT_FOUND -> "not found"
    DownloadFailureReason.GONE -> "no longer available"
    DownloadFailureReason.RANGE_NOT_SATISFIABLE -> "resume not supported"
    DownloadFailureReason.SERVER_ERROR -> "server error"
    DownloadFailureReason.HTTP_STATUS -> "unexpected HTTP status"
    DownloadFailureReason.MALFORMED_RESPONSE -> "malformed response"
    DownloadFailureReason.NETWORK -> "network error"
    DownloadFailureReason.STORAGE_UNAVAILABLE -> "storage unavailable"
    DownloadFailureReason.INSUFFICIENT_STORAGE -> "not enough storage"
    DownloadFailureReason.INTEGRITY_MISMATCH -> "integrity mismatch"
}

internal fun sizeLabel(row: DownloadRowUiState): String {
    val downloaded = formatBytes(row.downloadedBytes)
    val total = row.totalBytes
    return if (total == null) {
        "$downloaded downloaded · total size unknown"
    } else {
        "$downloaded of ${formatBytes(total)} · ${row.progressPercent}%"
    }
}

internal fun formatBytes(bytes: Long): String {
    if (bytes < UNIT) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / UNIT
    var index = 0
    while (value >= UNIT && index < units.lastIndex) {
        value /= UNIT
        index++
    }
    return String.format(Locale.US, "%.1f %s", value, units[index])
}

private const val UNIT = 1024
