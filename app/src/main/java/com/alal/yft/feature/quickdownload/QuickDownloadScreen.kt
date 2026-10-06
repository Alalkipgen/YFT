package com.alal.yft.feature.quickdownload

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
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
import com.alal.yft.core.model.settings.QualityPreference
import com.alal.yft.feature.preview.MeteredDownloadDialog
import com.alal.yft.feature.preview.PreviewDownloadStatus
import com.alal.yft.thumbnail.rememberRemoteThumbnail
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
    onOpenOtherVideos: () -> Unit = onNavigateBack,
    viewModel: QuickDownloadViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val requestNotificationPermission = rememberNotificationPermissionRequest()
    QuickDownloadScreen(
        state = state,
        onSelect = viewModel::select,
        onPickEarly = viewModel::pickEarly,
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
            // P12: the browser's found list opens under this sheet, which closes. P24: over
            // Home the found list opens instead ([onOpenOtherVideos]).
            if (viewModel.openOtherVideos()) onOpenOtherVideos()
        },
    )
}

/**
 * The download sheet (P3, P3-FIX): every way to download a video opens it — Home's View, Preview
 * in the found lists and the browser's Download button. The video's 16:9 picture (P19: loaded
 * over HTTPS without cookies; its placeholder until then), the title, site and length; exactly
 * two sections, **Audio** (M4A, MP3
 * at 128 kbps in the short view) and **Video** (the preferred quality and the next lower one).
 * More formats expands the same sheet to every row, without changing the selection; only the
 * rows scroll. Details and Download with the size stay pinned below them. P16: the sheet opens at
 * once; until the lookup and the qualities answer it shows what is known, placeholder rows and
 * "Getting qualities…".
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
    onPickEarly: (OptionSection) -> Unit = {},
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

            state.loading -> Waiting(
                state = state,
                onDownload = onDownload,
                onPickEarly = onPickEarly,
            )
            else -> Failure(
                message = state.failure,
                onRetry = onRetry.takeIf { state.canRetry },
                details = state.failureDetails,
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
        val picture = rememberRemoteThumbnail(header.thumbnailUrl)
        YftThumbnail(
            image = picture,
            kind = if (header.audioOnly) YftMediaKind.Audio else YftMediaKind.Video,
            modifier = Modifier
                .width(THUMBNAIL_WIDTH)
                .aspectRatio(THUMBNAIL_RATIO)
                .testTag("quick-thumbnail"),
            shape = YftShapes.thumbnailSmall,
            iconSize = 28.dp,
            glyph = if (header.audioOnly) YftIcons.Waveform else null,
            muted = header.audioOnly && picture == null,
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

/**
 * P16: the sheet opens before the qualities are known. "Getting qualities…", then two Audio and
 * two Video placeholder rows where the real rows will appear, and Download. P18: the first row
 * of each section is a choice — "M4A", or the Default quality ("720p") — and Download queues it
 * ("Starts when ready…") until the rows arrive.
 */
@Composable
private fun ColumnScope.Waiting(
    state: QuickDownloadUiState,
    onDownload: () -> Unit,
    onPickEarly: (OptionSection) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp)
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
            text = WAITING_MESSAGE,
            modifier = Modifier.padding(start = 12.dp),
            color = YftTheme.colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Column(
        modifier = Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .testTag("quick-waiting-rows"),
    ) {
        listOf(
            Triple("Audio", "quick-placeholder-audio", OptionSection.AUDIO),
            Triple("Video", "quick-placeholder-video", OptionSection.VIDEO),
        ).forEach { (label, tag, section) ->
            SectionLabel(label, Modifier.testTag(tag))
            repeat(PLACEHOLDER_ROWS) { index ->
                PlaceholderRow(
                    modifier = Modifier.testTag("quick-placeholder-row"),
                    choice = if (index == 0) {
                        EarlyChoice(
                            title = earlyTitle(section, state.defaultQuality),
                            selected = state.earlySection == section,
                            enabled = state.canChooseRow && !state.startsWhenReady,
                            onClick = { onPickEarly(section) },
                            tag = "quick-early-${section.name.lowercase()}",
                        )
                    } else {
                        null
                    },
                )
            }
        }
    }
    DownloadAction(state = state, onDownload = onDownload, onOpenDownloads = {})
}

/** P18: a waiting section's first row, which an early Download takes. */
private class EarlyChoice(
    val title: String,
    val selected: Boolean,
    val enabled: Boolean,
    val onClick: () -> Unit,
    val tag: String,
)

/** P18: "M4A", or the Default quality: "720p", "Highest available", "Smallest file". */
internal fun earlyTitle(section: OptionSection, quality: QualityPreference): String =
    when {
        section == OptionSection.AUDIO -> "M4A"
        quality == QualityPreference.HIGHEST -> "Highest available"
        quality == QualityPreference.LOWEST -> "Smallest file"
        else -> "${quality.maxHeight}p"
    }

/**
 * A row's shape without its text: the radio mark, the quality, its detail and the size. P18: a
 * [choice] row names what it stands for and can be picked.
 */
@Composable
private fun PlaceholderRow(modifier: Modifier = Modifier, choice: EarlyChoice? = null) {
    val bar = YftTheme.colors.chip
    val selected = choice?.selected == true
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(YftShapes.thumbnailSmall)
            .background(if (selected) YftTheme.colors.accentSoft else Color.Transparent)
            .then(
                if (choice == null) {
                    Modifier
                } else {
                    Modifier.selectable(
                        selected = selected,
                        enabled = choice.enabled,
                        role = Role.RadioButton,
                        onClick = choice.onClick,
                    )
                },
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
            if (choice == null) {
                PlaceholderBar(color = bar, modifier = Modifier.fillMaxWidth(0.45f).height(14.dp))
            } else {
                Text(
                    text = choice.title,
                    modifier = Modifier.testTag(choice.tag),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            PlaceholderBar(
                color = bar,
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(0.3f).height(12.dp),
            )
        }
        PlaceholderBar(color = bar, modifier = Modifier.padding(start = 8.dp).size(48.dp, 12.dp))
    }
}

@Composable
private fun PlaceholderBar(color: Color, modifier: Modifier) {
    Box(modifier = modifier.clip(YftShapes.thumbnailSmall).background(color))
}

@Composable
private fun ColumnScope.Failure(
    message: String?,
    onRetry: (() -> Unit)?,
    details: List<String> = emptyList(),
) {
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
    FailureDetails(details)
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
        text = when {
            // P18: Download tapped before the qualities came.
            state.startsWhenReady -> STARTS_WHEN_READY
            status == PreviewDownloadStatus.Enqueuing -> "Queueing download…"
            else -> downloadLabel(state.selectedOption)
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
    // P18: which quality a Download tapped before the qualities took.
    state.startedNote?.takeIf { status is PreviewDownloadStatus.Queued }?.let { note ->
        Text(
            text = note,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .testTag("quick-download-note"),
            color = colors.textPrimary,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
        )
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
    if (status is PreviewDownloadStatus.Rejected) FailureDetails(state.downloadDetails)
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

/**
 * P24: "Details" under a failure: the step, the host and the status of the request that failed,
 * so a screenshot tells what went wrong. Hidden until asked for.
 */
@Composable
private fun ColumnScope.FailureDetails(lines: List<String>) {
    if (lines.isEmpty()) return
    var shown by rememberSaveable(lines) { mutableStateOf(false) }
    YftTextButton(
        text = if (shown) "Hide details" else "Details",
        onClick = { shown = !shown },
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .testTag("quick-error-details"),
    )
    if (shown) {
        Text(
            text = lines.joinToString("\n"),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .testTag("quick-error-detail-text"),
            color = YftTheme.colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
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

/** P16: what the waiting sheet says until the lookup and the qualities answer. */
internal const val WAITING_MESSAGE = "Getting qualities…"

/** P18: Download's label once it was tapped before the qualities came. */
internal const val STARTS_WHEN_READY = "Starts when ready…"
private const val PLACEHOLDER_ROWS = 2
private const val SIZE_UNKNOWN = "Size unknown"
private val SHEET_TOP_GAP = 48.dp
private val THUMBNAIL_WIDTH = 112.dp
private const val THUMBNAIL_RATIO = 16f / 9f
