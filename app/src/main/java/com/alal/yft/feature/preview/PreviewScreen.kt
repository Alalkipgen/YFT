package com.alal.yft.feature.preview

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaSizeAccuracy
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.ui.components.YftCard
import com.alal.yft.ui.components.YftDivider
import com.alal.yft.ui.components.YftIcon
import com.alal.yft.ui.components.YftIconButton
import com.alal.yft.ui.components.YftPrimaryButton
import com.alal.yft.ui.components.YftRadioMark
import com.alal.yft.ui.components.YftSegmentedControl
import com.alal.yft.ui.components.YftSwitch
import com.alal.yft.ui.components.YftTextButton
import com.alal.yft.ui.components.YftTonalButton
import com.alal.yft.ui.components.rememberNotificationPermissionRequest
import com.alal.yft.ui.format.YftFormat
import com.alal.yft.ui.format.YftQualityNames
import com.alal.yft.ui.theme.YftIcons
import com.alal.yft.ui.theme.YftShapes
import com.alal.yft.ui.theme.YftTheme
import java.net.URI
import java.util.Locale
import kotlin.math.roundToInt

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PreviewRoute(
    onNavigateBack: () -> Unit,
    onOpenDownloads: () -> Unit = {},
    viewModel: PreviewViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val options by viewModel.downloadOptions.collectAsStateWithLifecycle()
    val requestNotificationPermission = rememberNotificationPermissionRequest()
    PreviewScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onRetry = viewModel::retry,
        onTabSelected = viewModel::selectTab,
        onVariantSelected = viewModel::selectVariant,
        onDownload = {
            requestNotificationPermission()
            viewModel.download()
        },
        onConfirmMetered = viewModel::confirmMeteredDownload,
        onDismissMetered = viewModel::dismissMeteredDownload,
        options = options,
        onWifiOnlyChange = viewModel::setWifiOnly,
        onOpenDownloads = onOpenDownloads,
        playerSurface = { variant, modifier ->
            PreviewPlayerSurface(
                variant = variant,
                modifier = modifier,
                viewModel = viewModel,
            )
        },
    )
}

/**
 * "Download as" (design 03), the body of the sheet that rises over the page when Preview is
 * chosen: a small player where the design shows the thumbnail, the title and "site · Video +
 * audio", the Video / Audio switch, the quality list with sizes, Wi-Fi only, and the Download
 * button with its size and where the file goes. The info button beside "Quality" shows the chosen
 * variant's details, where unknown values stay "Unknown"; sizes YFT only estimated carry a "~".
 *
 * The content scrolls and never grows past the space under the status bar.
 */
@Composable
fun PreviewScreen(
    uiState: PreviewUiState,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onTabSelected: (PreviewTab) -> Unit,
    onVariantSelected: (String) -> Unit,
    playerSurface: @Composable (MediaVariant, Modifier) -> Unit,
    modifier: Modifier = Modifier,
    onDownload: () -> Unit = {},
    onConfirmMetered: () -> Unit = {},
    onDismissMetered: () -> Unit = {},
    options: PreviewDownloadOptions = PreviewDownloadOptions(),
    onWifiOnlyChange: (Boolean) -> Unit = {},
    onOpenDownloads: () -> Unit = {},
) {
    val ready = uiState as? PreviewUiState.Ready
    if (ready?.downloadStatus == PreviewDownloadStatus.ConfirmMetered) {
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
            .padding(bottom = 16.dp)
            .testTag("preview-sheet"),
    ) {
        SheetTitle()
        when (uiState) {
            PreviewUiState.Empty -> SheetMessage(
                icon = YftIcons.Movie,
                title = "No media selected",
                message = "Open the browser and choose Preview on a media item.",
                modifier = Modifier.testTag("preview-empty"),
            ) {
                CloseButton(onClose = onNavigateBack)
            }

            PreviewUiState.Loading -> SheetMessage(
                icon = null,
                title = "Checking qualities…",
                message = "YFT is reading which video and audio versions this media offers.",
                modifier = Modifier.testTag("preview-loading"),
            )

            is PreviewUiState.Error -> SheetMessage(
                icon = YftIcons.Warning,
                iconTint = YftTheme.colors.coralText,
                title = "Preview unavailable",
                message = uiState.message,
                modifier = Modifier.testTag("preview-error"),
            ) {
                if (uiState.retryable) {
                    YftPrimaryButton(
                        text = "Try again",
                        onClick = onRetry,
                        modifier = Modifier.testTag("preview-retry"),
                        icon = YftIcons.Refresh,
                    )
                    YftTextButton(
                        text = "Close",
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("navigate-back"),
                        color = YftTheme.colors.link,
                    )
                } else {
                    CloseButton(onClose = onNavigateBack)
                }
            }

            is PreviewUiState.Ready -> ReadyContent(
                state = uiState,
                options = options,
                onTabSelected = onTabSelected,
                onVariantSelected = onVariantSelected,
                onDownload = onDownload,
                onWifiOnlyChange = onWifiOnlyChange,
                onOpenDownloads = onOpenDownloads,
                playerSurface = playerSurface,
            )
        }
    }
}

/** The sheet's title. Swiping down, tapping the page above or Back closes it (no X, as drawn). */
@Composable
private fun SheetTitle() {
    Text(
        text = SHEET_TITLE,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
            .semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

@Composable
private fun CloseButton(onClose: () -> Unit) {
    YftTonalButton(
        text = "Close",
        onClick = onClose,
        modifier = Modifier.testTag("navigate-back"),
    )
}

@Composable
private fun ReadyContent(
    state: PreviewUiState.Ready,
    options: PreviewDownloadOptions,
    onTabSelected: (PreviewTab) -> Unit,
    onVariantSelected: (String) -> Unit,
    onDownload: () -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onOpenDownloads: () -> Unit,
    playerSurface: @Composable (MediaVariant, Modifier) -> Unit,
) {
    val colors = YftTheme.colors
    val variant = state.selectedVariant
    var showDetails by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(PLAYER_WIDTH)
                .aspectRatio(16f / 9f)
                .clip(YftShapes.thumbnail)
                .background(Color.Black),
        ) {
            playerSurface(variant, Modifier.fillMaxSize().testTag("preview-player"))
        }
        Text(
            text = state.asset.displayTitle(),
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = sourceLine(state.asset, variant),
            modifier = Modifier.padding(top = 2.dp),
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        state.playbackError?.let { message -> PlaybackErrorBanner(message) }
        YftSegmentedControl(
            options = PreviewTab.entries,
            selected = state.selectedTab,
            onSelect = onTabSelected,
            label = PreviewTab::label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            containerColor = colors.card,
            testTag = { tab -> "preview-tab-${tab.name.lowercase(Locale.US)}" },
            isEnabled = state::hasPreviewableVariant,
        )
        QualityHeader(
            showDetails = showDetails,
            onToggleDetails = { showDetails = !showDetails },
        )
        VariantList(
            variants = state.visibleVariants,
            selectedId = state.selectedVariantId,
            onSelect = onVariantSelected,
        )
        if (showDetails) DetailsCard(variant = variant)
        YftDivider(modifier = Modifier.padding(vertical = 6.dp))
        WifiOnlyRow(checked = options.wifiOnly, onCheckedChange = onWifiOnlyChange)
        DownloadAction(
            state = state,
            options = options,
            onDownload = onDownload,
            onOpenDownloads = onOpenDownloads,
        )
    }
}

/** "Quality" with an info button that shows or hides the chosen variant's details. */
@Composable
private fun QualityHeader(showDetails: Boolean, onToggleDetails: () -> Unit) {
    val colors = YftTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Quality",
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelLarge,
        )
        YftIconButton(
            icon = YftIcons.Info,
            contentDescription = if (showDetails) "Hide details" else "Show details",
            onClick = onToggleDetails,
            modifier = Modifier
                .offset(x = 12.dp)
                .semantics { stateDescription = if (showDetails) "Shown" else "Hidden" }
                .testTag("preview-details-toggle"),
            tint = if (showDetails) colors.link else colors.textSecondary,
        )
    }
}

@Composable
private fun PlaybackErrorBanner(message: String) {
    val colors = YftTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(YftShapes.thumbnailSmall)
            .background(colors.coralSoft)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("preview-playback-error"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YftIcon(
            icon = YftIcons.Warning,
            contentDescription = null,
            tint = colors.coralText,
            size = 20.dp,
        )
        Text(
            text = message,
            modifier = Modifier.padding(start = 10.dp),
            color = colors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Radio rows, highest quality first, with the size on the right (design 03). */
@Composable
private fun VariantList(
    variants: List<MediaVariant>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    val ordered = remember(variants) { variants.sortedWith(QUALITY_ORDER) }
    val labels = remember(variants) { variants.qualityLabels() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bleed(ROW_BLEED)
            .selectableGroup()
            .testTag("preview-variants"),
    ) {
        ordered.forEach { variant ->
            VariantRow(
                variant = variant,
                label = labels.getValue(variant.id),
                selected = variant.id == selectedId,
                onClick = { onSelect(variant.id) },
            )
        }
    }
}

@Composable
private fun VariantRow(
    variant: MediaVariant,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = YftTheme.colors
    val enabled = variant.isPreviewable
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(YftShapes.thumbnailSmall)
            .background(if (selected) colors.accentSoft else Color.Transparent)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .testTag("preview-variant-${variant.id}")
            .padding(horizontal = ROW_BLEED, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YftRadioMark(
            selected = selected,
            modifier = if (enabled) Modifier else Modifier.alpha(DISABLED_ALPHA),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(
                text = label,
                color = if (enabled) colors.textPrimary else colors.textSecondary,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!enabled) {
                Text(
                    text = "Unsupported codec",
                    color = colors.coralText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        variant.sizeText()?.let { size ->
            Text(
                text = size,
                modifier = Modifier.padding(start = 12.dp),
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Same preference as Settings → Wi-Fi only; the row toggles as one switch. */
@Composable
private fun WifiOnlyRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = YftTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .testTag("preview-wifi-only")
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YftIcon(icon = YftIcons.Wifi, contentDescription = null, tint = colors.icon)
        Text(
            text = "Download over Wi-Fi only",
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp, end = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        YftSwitch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun DownloadAction(
    state: PreviewUiState.Ready,
    options: PreviewDownloadOptions,
    onDownload: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val colors = YftTheme.colors
    val status = state.downloadStatus
    YftPrimaryButton(
        text = if (status == PreviewDownloadStatus.Enqueuing) {
            "Queueing download…"
        } else {
            state.selectedVariant.downloadLabel()
        },
        onClick = onDownload,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag("preview-download"),
        icon = YftIcons.Download,
        enabled = state.canDownload,
        large = true,
    )
    Text(
        text = if (options.savesToSharedDownloads) {
            "Saves to Download/YFT"
        } else {
            "Saves to app storage"
        },
        modifier = Modifier
            .padding(top = 8.dp)
            .testTag("preview-save-location"),
        color = colors.textSecondary,
        style = MaterialTheme.typography.bodySmall,
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
                .testTag("preview-download-status"),
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
                .testTag("preview-open-downloads"),
            icon = YftIcons.Download,
        )
    }
}

/** Everything the resolver knows about the chosen variant, unknowns spelled out. */
@Composable
private fun DetailsCard(variant: MediaVariant) {
    val colors = YftTheme.colors
    YftCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag("preview-details"),
        contentPadding = PaddingValues(16.dp),
    ) {
        Text(
            text = "Details",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        )
        Text(
            text = variant.metadataLabel(),
            modifier = Modifier.padding(top = 8.dp),
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Empty, loading and error bodies: a round glyph (or spinner), a title and one line. */
@Composable
private fun SheetMessage(
    @DrawableRes icon: Int?,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    iconTint: Color = YftTheme.colors.icon,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = YftTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(colors.chip),
            contentAlignment = Alignment.Center,
        ) {
            if (icon == null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = colors.accent,
                    strokeWidth = 3.dp,
                )
            } else {
                YftIcon(icon = icon, contentDescription = null, tint = iconTint, size = 28.dp)
            }
        }
        Text(
            text = title,
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            modifier = Modifier.padding(top = 6.dp),
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Column(
            modifier = Modifier.padding(top = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = actions,
        )
    }
}

@Composable
internal fun MeteredDownloadDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = YftTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            YftIcon(icon = YftIcons.CellTower, contentDescription = null, tint = colors.icon)
        },
        title = { Text(text = "Download on mobile data?") },
        text = {
            Text(text = "You are not on Wi-Fi. This download may use your mobile data plan.")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("metered-confirm"),
                colors = ButtonDefaults.textButtonColors(contentColor = colors.link),
            ) { Text(text = "Download") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("metered-dismiss"),
                colors = ButtonDefaults.textButtonColors(contentColor = colors.link),
            ) { Text(text = "Cancel") }
        },
        containerColor = colors.card,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
    )
}

private val PreviewTab.label: String
    get() = when (this) {
        PreviewTab.VIDEO -> "Video"
        PreviewTab.AUDIO -> "Audio"
    }

private fun MediaAsset.displayTitle(): String =
    title?.trim()?.take(MAX_TITLE)?.takeIf(String::isNotEmpty) ?: "Detected media"

/** "archive.org · Video + audio": where the media came from and what the file will hold. */
internal fun sourceLine(asset: MediaAsset, variant: MediaVariant): String {
    val host = runCatching { URI(asset.sourcePageUrl).host }.getOrNull()
        ?.lowercase(Locale.US)
        ?.removePrefix("www.")
        ?.takeIf(String::isNotEmpty)
    return listOfNotNull(host, variant.trackLabel()).joinToString(" · ")
}

/**
 * The row title: "1080p · Full HD", "720p60 · HD", "480p", "360p · Data saver" for video and
 * "128 kbps · English" for audio. Rows that would read the same also show their bitrate.
 */
internal fun List<MediaVariant>.qualityLabels(): Map<String, String> {
    val base = associate { it.id to it.qualityLabel() }
    val repeated = base.values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    return associate { variant ->
        val label = base.getValue(variant.id)
        val bitrate = variant.bitrateBitsPerSecond
        variant.id to if (label in repeated && bitrate != null &&
            variant.trackType != MediaTrackType.AUDIO
        ) {
            "$label · ${bitrate.formatBitrate()}"
        } else {
            label
        }
    }
}

internal fun MediaVariant.qualityLabel(): String {
    if (trackType == MediaTrackType.AUDIO) return audioLabel()
    val stated = height ?: return label?.trim()?.take(MAX_LABEL)?.takeIf(String::isNotEmpty)
        ?: "Quality unknown"
    // P47: named after the short side like the Download sheet: a 720 × 1280 phone video is
    // "720p · HD", not "1280p · Full HD".
    val tall = YftQualityNames.standardHeight(width, stated)
    val rate = framesPerSecond?.takeIf { it > HIGH_FRAME_RATE }?.roundToInt()?.toString().orEmpty()
    return listOfNotNull("${tall}p$rate", resolutionName(tall)).joinToString(" · ")
}

private fun MediaVariant.audioLabel(): String {
    val name = label?.trim()?.take(MAX_LABEL)?.takeIf(String::isNotEmpty)
        ?: language?.trim()?.takeIf(String::isNotEmpty)
    val rate = bitrateBitsPerSecond
        ?.takeIf { name == null || !name.contains("kbps", ignoreCase = true) }
        ?.let { "${(it / 1_000.0).roundToInt()} kbps" }
    return listOfNotNull(rate, name).joinToString(" · ").ifEmpty { "Audio track" }
}

private fun resolutionName(height: Int): String? = when {
    height >= 2_160 -> "4K"
    height >= 1_440 -> "2K"
    height >= 1_080 -> "Full HD"
    height >= 720 -> "HD"
    height <= 360 -> "Data saver"
    else -> null
}

/** "96 MB" when the size is exact, "~96 MB" when estimated, nothing when unknown. */
internal fun MediaVariant.sizeText(): String? {
    val bytes = sizeBytes?.takeIf { it > 0 } ?: return null
    return when (sizeAccuracy) {
        MediaSizeAccuracy.EXACT -> YftFormat.bytes(bytes)
        MediaSizeAccuracy.ESTIMATED -> "~${YftFormat.bytes(bytes)}"
        null -> null
    }
}

internal fun MediaVariant.downloadLabel(): String =
    sizeText()?.let { "Download · $it" } ?: "Download"

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
    add("Duration: ${durationMillis?.let(YftFormat::duration) ?: "Unknown"}")
    add(sizeLabel())
    add("Format: ${container ?: mimeType ?: "Unknown"}")
    language?.let { add("Language: $it") }
}.joinToString("\n")

private fun MediaVariant.sizeLabel(): String = when (sizeAccuracy) {
    MediaSizeAccuracy.EXACT -> "Size: ${sizeBytes?.let(YftFormat::bytes) ?: "Unknown"}"
    MediaSizeAccuracy.ESTIMATED ->
        "Estimated size: ~${sizeBytes?.let(YftFormat::bytes) ?: "Unknown"}"
    null -> "Size: Unknown"
}

private fun Long.formatBitrate(): String = when {
    this >= 1_000_000 -> String.format(Locale.US, "%.2f Mbps", this / 1_000_000.0)
    else -> String.format(Locale.US, "%.0f kbps", this / 1_000.0)
}

private fun Double.formatDecimal(): String =
    String.format(Locale.US, if (this % 1.0 == 0.0) "%.0f" else "%.2f", this)

/** Measures [horizontal] wider on both sides than the parent allows and centres the overflow. */
private fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val extra = horizontal.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = constraints.maxWidth + extra,
        ),
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}

/** Highest picture first, then highest bitrate; unknown heights last. */
private val QUALITY_ORDER = compareByDescending<MediaVariant> { it.height ?: -1 }
    .thenByDescending { it.bitrateBitsPerSecond ?: -1L }

private const val SHEET_TITLE = "Download as"
private val SHEET_TOP_GAP = 48.dp
private val PLAYER_WIDTH = 176.dp
private val ROW_BLEED = 12.dp
private const val DISABLED_ALPHA = 0.38f
private const val HIGH_FRAME_RATE = 31.0
private const val MAX_TITLE = 120
private const val MAX_LABEL = 80
