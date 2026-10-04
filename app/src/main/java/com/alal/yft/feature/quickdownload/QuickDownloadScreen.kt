package com.alal.yft.feature.quickdownload

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alal.yft.feature.preview.MeteredDownloadDialog
import com.alal.yft.feature.preview.PreviewDownloadStatus
import com.alal.yft.ui.components.YftMediaKind
import com.alal.yft.ui.components.YftPrimaryButton
import com.alal.yft.ui.components.YftRadioMark
import com.alal.yft.ui.components.YftTextButton
import com.alal.yft.ui.components.YftThumbnail
import com.alal.yft.ui.components.YftTonalButton
import com.alal.yft.ui.components.rememberNotificationPermissionRequest
import com.alal.yft.ui.format.YftFormat
import com.alal.yft.ui.theme.YftIcons
import com.alal.yft.ui.theme.YftShapes
import com.alal.yft.ui.theme.YftTheme

@Composable
fun QuickDownloadRoute(
    onNavigateBack: () -> Unit,
    onOpenDownloads: () -> Unit,
    onMoreFormats: (MoreFormatsTarget) -> Unit,
    viewModel: QuickDownloadViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val requestNotificationPermission = rememberNotificationPermissionRequest()
    QuickDownloadScreen(
        state = state,
        onSelect = viewModel::select,
        onDownload = {
            requestNotificationPermission()
            viewModel.download()
        },
        onConfirmMetered = viewModel::confirmMeteredDownload,
        onDismissMetered = viewModel::dismissMeteredDownload,
        onMoreFormats = { onMoreFormats(viewModel.moreFormats()) },
        onOpenDownloads = onOpenDownloads,
        onClose = onNavigateBack,
    )
}

/**
 * "Video you copied": the sheet Home opens when a pasted link found one video. A placeholder
 * thumbnail (YFT never fetches remote images), the title and length, Music (M4A · Fast) and
 * up to two Video rows (Fast ≤ 480p, High ≤ 720p) with their real labels and sizes, More
 * formats and one Download button.
 */
@Composable
fun QuickDownloadScreen(
    state: QuickDownloadUiState,
    onSelect: (String) -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
    onConfirmMetered: () -> Unit = {},
    onDismissMetered: () -> Unit = {},
    onMoreFormats: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    if (state.downloadStatus == PreviewDownloadStatus.ConfirmMetered) {
        MeteredDownloadDialog(onConfirm = onConfirmMetered, onDismiss = onDismissMetered)
    }
    val density = LocalDensity.current
    val reservedTop = WindowInsets.statusBars.getTop(density) +
        with(density) { SHEET_TOP_GAP.roundToPx() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val maxHeight = if (constraints.hasBoundedHeight) {
                    (constraints.maxHeight - reservedTop).coerceAtLeast(constraints.minHeight)
                } else {
                    constraints.maxHeight
                }
                val placeable = measurable.measure(constraints.copy(maxHeight = maxHeight))
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
            .testTag("quick-sheet"),
    ) {
        Text(
            text = SHEET_TITLE,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        val choices = state.choices
        if (choices == null) {
            Text(
                text = "The copied link no longer has media here. Paste it on Home again.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .testTag("quick-empty"),
                color = YftTheme.colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            YftTonalButton(
                text = "Close",
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .testTag("navigate-back"),
            )
            return@Column
        }
        Header(choices)
        Column(modifier = Modifier.selectableGroup()) {
            val music = listOfNotNull(choices.music, choices.mp3)
            if (music.isNotEmpty()) {
                SectionLabel("Music")
                music.forEach { row ->
                    QuickRowItem(
                        row = row,
                        selected = row.id == state.selectedId,
                        enabled = state.canChooseRow,
                        onClick = { onSelect(row.id) },
                    )
                }
            }
            if (choices.video.isNotEmpty()) {
                SectionLabel("Video")
                choices.video.forEach { row ->
                    QuickRowItem(
                        row = row,
                        selected = row.id == state.selectedId,
                        enabled = state.canChooseRow,
                        onClick = { onSelect(row.id) },
                    )
                }
            }
        }
        YftTextButton(
            text = "More formats",
            onClick = onMoreFormats,
            modifier = Modifier
                .padding(top = 4.dp)
                .testTag("quick-more-formats"),
        )
        DownloadAction(
            state = state,
            onDownload = onDownload,
            onOpenDownloads = onOpenDownloads,
        )
    }
}

@Composable
private fun Header(choices: QuickChoices) {
    val colors = YftTheme.colors
    val audioOnly = choices.video.isEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .testTag("quick-header"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YftThumbnail(
            image = null,
            kind = if (audioOnly) YftMediaKind.Audio else YftMediaKind.Video,
            modifier = Modifier.size(THUMBNAIL),
            shape = YftShapes.thumbnailSmall,
            iconSize = 28.dp,
            glyph = if (audioOnly) YftIcons.Waveform else null,
            muted = audioOnly,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(
                text = choices.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            choices.durationMillis?.let { length ->
                Text(
                    text = YftFormat.duration(length),
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .testTag("quick-length"),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .padding(top = 12.dp, bottom = 4.dp)
            .semantics { heading() },
        color = YftTheme.colors.textSecondary,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
private fun QuickRowItem(
    row: QuickRow,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = YftTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(YftShapes.thumbnailSmall)
            .background(if (selected) colors.accentSoft else Color.Transparent)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .testTag("quick-row-${row.id}")
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YftRadioMark(selected = selected)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(text = row.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = row.detail,
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ColumnScope.DownloadAction(
    state: QuickDownloadUiState,
    onDownload: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val colors = YftTheme.colors
    val status = state.downloadStatus
    YftPrimaryButton(
        text = if (status == PreviewDownloadStatus.Enqueuing) {
            "Queueing download…"
        } else {
            "Download"
        },
        onClick = onDownload,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag("quick-download"),
        icon = YftIcons.Download,
        enabled = state.canDownload,
        large = true,
    )
    val message = when (status) {
        PreviewDownloadStatus.Idle,
        PreviewDownloadStatus.Enqueuing,
        PreviewDownloadStatus.ConfirmMetered,
        -> null

        is PreviewDownloadStatus.Queued -> if (status.waitingForUnmetered) {
            "Queued ${status.fileName}. It starts when Wi-Fi is available."
        } else {
            "Queued ${status.fileName}. Track progress on the Downloads screen."
        }

        is PreviewDownloadStatus.Rejected -> status.message
    }
    message?.let { text ->
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .testTag("quick-download-status"),
            color = if (status is PreviewDownloadStatus.Rejected) {
                colors.coralText
            } else {
                colors.textPrimary
            },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
    if (status is PreviewDownloadStatus.Queued) {
        YftTonalButton(
            text = "View downloads",
            onClick = onOpenDownloads,
            modifier = Modifier
                .padding(top = 8.dp)
                .align(Alignment.CenterHorizontally)
                .testTag("quick-open-downloads"),
            icon = YftIcons.Download,
        )
    }
}

private val QuickDownloadUiState.canChooseRow: Boolean
    get() = downloadStatus != PreviewDownloadStatus.Enqueuing &&
        downloadStatus != PreviewDownloadStatus.ConfirmMetered

internal const val SHEET_TITLE = "Video you copied"
private val SHEET_TOP_GAP = 48.dp
private val THUMBNAIL = 64.dp
