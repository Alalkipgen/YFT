package com.alal.yft.feature.quickdownload

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.alal.yft.ui.components.YftMetaChip
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
    onOpenDetails: () -> Unit,
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
        onOpenDetails = { if (viewModel.openDetails()) onOpenDetails() },
        onRetry = viewModel::retry,
        onOpenDownloads = onOpenDownloads,
        onClose = onNavigateBack,
        onOpenOtherVideos = {
            // P12: the browser's found list opens under this sheet, which closes.
            if (viewModel.openOtherVideos()) onNavigateBack()
        },
    )
}

/**
 * The download sheet (P3, P3-FIX): every way to download a video opens it — Home's View, Preview
 * in the found lists and the browser's Download button. A placeholder thumbnail (YFT never
 * fetches remote images), the title, site and length; exactly two sections, **Audio** (M4A, MP3
 * at 128 kbps in the short view) and **Video** (the preferred quality and the next lower one).
 * More formats expands the same sheet to every row, without changing the selection; only the
 * rows scroll. Details and Download with the size stay pinned below them.
 */
@Composable
fun QuickDownloadScreen(
    state: QuickDownloadUiState,
    onSelect: (String) -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
    onConfirmMetered: () -> Unit = {},
    onDismissMetered: () -> Unit = {},
    onOpenDetails: () -> Unit = {},
    onRetry: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onClose: () -> Unit = {},
    onOpenOtherVideos: () -> Unit = {},
) {
    if (state.downloadStatus == PreviewDownloadStatus.ConfirmMetered) {
        MeteredDownloadDialog(onConfirm = onConfirmMetered, onDismiss = onDismissMetered)
    }
    var expanded by rememberSaveable(state.header?.title, state.header?.source) {
        mutableStateOf(false)
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
        val header = state.header
        if (header == null) {
            Text(
                text = "This video is no longer here. Open the page or paste the link again.",
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
        Header(header)
        if (state.otherVideos > 0) {
            YftTextButton(
                text = "Other videos on this page (${state.otherVideos})",
                onClick = onOpenOtherVideos,
                modifier = Modifier.testTag("quick-other-videos"),
            )
        }
        val choices = state.choices
        when {
            choices != null -> {
                val compact = QuickDownloadChoices.compact(choices, state.defaultQuality)
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .testTag("quick-rows"),
                ) {
                    Sections(
                        choices = if (expanded) choices else compact,
                        state = state,
                        onSelect = onSelect,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val hidden = choices.options.size - compact.options.size
                    if (hidden > 0) {
                        YftTextButton(
                            text = if (expanded) "Fewer formats" else "More formats · $hidden",
                            onClick = { expanded = !expanded },
                            modifier = Modifier.testTag(
                                if (expanded) "quick-fewer-formats" else "quick-more-formats",
                            ),
                        )
                    }
                    YftTextButton(
                        text = "Details",
                        onClick = onOpenDetails,
                        modifier = Modifier.testTag("quick-details"),
                    )
                }
                DownloadAction(
                    state = state,
                    onDownload = onDownload,
                    onOpenDownloads = onOpenDownloads,
                )
            }

            state.loading -> Loading(findingVideo = state.findingVideo)
            else -> Failure(
                message = state.failure,
                onRetry = onRetry.takeIf { state.canRetry },
            )
        }
    }
}

@Composable
private fun Header(header: SheetHeader) {
    val colors = YftTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .testTag("quick-header"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YftThumbnail(
            image = null,
            kind = if (header.audioOnly) YftMediaKind.Audio else YftMediaKind.Video,
            modifier = Modifier.size(THUMBNAIL),
            shape = YftShapes.thumbnailSmall,
            iconSize = 28.dp,
            glyph = if (header.audioOnly) YftIcons.Waveform else null,
            muted = header.audioOnly,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(
                text = header.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(modifier = Modifier.padding(top = 4.dp)) {
                header.source?.let { site ->
                    Text(
                        text = if (header.durationMillis != null) "$site · " else site,
                        modifier = Modifier.testTag("quick-source"),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                }
                header.durationMillis?.let { length ->
                    Text(
                        text = YftFormat.duration(length),
                        modifier = Modifier.testTag("quick-length"),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Audio, then Video: each row one format with its real quality and size (P3-FIX). */
@Composable
private fun Sections(
    choices: QuickChoices,
    state: QuickDownloadUiState,
    onSelect: (String) -> Unit,
) {
    val selected = state.selectedOption?.id
    Column(modifier = Modifier.selectableGroup()) {
        listOf(
            Triple("Audio", "quick-section-audio", choices.audio),
            Triple("Video", "quick-section-video", choices.video),
        ).forEach { (label, tag, options) ->
            if (options.isEmpty()) return@forEach
            SectionLabel(label, Modifier.testTag(tag))
            options.forEach { option ->
                FormatRow(
                    title = option.title,
                    detail = option.detail,
                    chips = option.chips,
                    size = option.size ?: SIZE_UNKNOWN,
                    selected = option.id == selected,
                    enabled = state.canChooseRow,
                    onClick = { onSelect(option.id) },
                    modifier = Modifier.testTag("quick-option-${option.id}"),
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .padding(top = 12.dp, bottom = 4.dp)
            .semantics { heading() },
        color = YftTheme.colors.textSecondary,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormatRow(
    title: String,
    detail: String,
    chips: List<String>,
    size: String?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
            .then(modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YftRadioMark(selected = selected)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (detail.isNotEmpty()) {
                Text(
                    text = detail,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (chips.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    chips.forEach { chip -> YftMetaChip(text = chip) }
                }
            }
        }
        size?.let { text ->
            Text(
                text = text,
                modifier = Modifier.padding(start = 8.dp),
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Loading(findingVideo: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .testTag("quick-loading"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            color = YftTheme.colors.accent,
            strokeWidth = 3.dp,
        )
        Text(
            text = if (findingVideo) "Looking up this video…" else "Reading qualities and sizes…",
            modifier = Modifier.padding(start = 12.dp),
            color = YftTheme.colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ColumnScope.Failure(message: String?, onRetry: (() -> Unit)?) {
    Text(
        text = message ?: "No format of this video could be read.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .testTag("quick-error"),
        color = YftTheme.colors.coralText,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
    // P12: a protected video has no Try again; asking again would not change it.
    onRetry ?: return
    YftTonalButton(
        text = "Try again",
        onClick = onRetry,
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .testTag("quick-retry"),
        icon = YftIcons.Refresh,
    )
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
            downloadLabel(state.selectedOption)
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

/** "Download · 25 MB"; just "Download" while the size is unknown. */
internal fun downloadLabel(option: SheetOption?): String =
    option?.size?.let { "Download · $it" } ?: "Download"

private val QuickDownloadUiState.canChooseRow: Boolean
    get() = downloadStatus != PreviewDownloadStatus.Enqueuing &&
        downloadStatus != PreviewDownloadStatus.ConfirmMetered

internal const val SHEET_TITLE = "Download"
private const val SIZE_UNKNOWN = "Size unknown"
private val SHEET_TOP_GAP = 48.dp
private val THUMBNAIL = 64.dp
