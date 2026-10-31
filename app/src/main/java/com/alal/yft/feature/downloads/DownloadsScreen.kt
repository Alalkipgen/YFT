package com.alal.yft.feature.downloads

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alal.yft.BuildConfig
import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.detection.Av1MergeSwitch
import com.alal.yft.download.BackgroundSettingsIntents
import com.alal.yft.download.policy.TransferNetworkState
import com.alal.yft.feature.library.AppPrivateDownloadProvider
import com.alal.yft.feature.library.LibraryIntents
import com.alal.yft.feature.library.LibraryItem
import com.alal.yft.feature.library.LibraryLocation
import com.alal.yft.feature.library.LibraryMimeTypes
import com.alal.yft.feature.library.LocalLibraryPlayback
import com.alal.yft.feature.library.rememberMediaDetails
import com.alal.yft.thumbnail.rememberDownloadThumbnail
import com.alal.yft.ui.LocalQuietRelease
import com.alal.yft.ui.components.YftCard
import com.alal.yft.ui.components.YftCircleButton
import com.alal.yft.ui.components.YftFilterChip
import com.alal.yft.ui.components.YftIcon
import com.alal.yft.ui.components.YftIconButton
import com.alal.yft.ui.components.YftMediaKind
import com.alal.yft.ui.components.YftOutlinedButton
import com.alal.yft.ui.components.YftProgressBar
import com.alal.yft.ui.components.YftScreenHeader
import com.alal.yft.ui.components.YftStatusChip
import com.alal.yft.ui.components.YftStatusTone
import com.alal.yft.ui.components.YftTextButton
import com.alal.yft.ui.components.YftThumbnail
import com.alal.yft.ui.navigation.YftDestination
import com.alal.yft.ui.theme.YftIcons
import com.alal.yft.ui.theme.YftShapes
import com.alal.yft.ui.theme.YftTheme
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@Composable
fun DownloadsRoute(
    onOpenSettings: () -> Unit = {},
    onOpenPlayer: () -> Unit = {},
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val deleteFileQuestion by viewModel.deleteFileQuestion.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    // P42: Android's "Allow YFT to delete?" for an item YFT no longer owns.
    val consent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onDeleteConsentResult(result.resultCode == Activity.RESULT_OK) }
    LaunchedEffect(viewModel) {
        viewModel.deleteConsentRequests.collect { request ->
            consent.launch(IntentSenderRequest.Builder(request).build())
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message -> snackbarHostState.showSnackbar(message) }
    }
    val playback = LocalLibraryPlayback.current
    // Back from Android's settings: the battery and notification cards follow what changed.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshBackground() }
    val backgroundActions = remember(viewModel, context) {
        BackgroundCardActions(
            onTurnOnNotifications = { BackgroundSettingsIntents.notifications(context) },
            onDismissNotifications = viewModel::hideNotificationsCard,
            onAllow = { BackgroundSettingsIntents.allowUnrestricted(context) },
            onOpenAppSettings = { BackgroundSettingsIntents.appDetails(context) },
            onNotNow = viewModel::hideBatteryCard,
        )
    }
    val openElsewhere: (DownloadRowUiState) -> Unit = { row ->
        val intent = DownloadIntents.view(context, row)
        if (intent == null || !LibraryIntents.start(context, intent)) {
            Toast.makeText(
                context,
                "No app on this device can open ${row.title}.",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    DownloadsScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        onPauseAll = viewModel::pauseAll,
        onOpen = openElsewhere,
        // The Library's player: audio in the mini player, video full screen.
        onPlay = { row ->
            val item = DownloadIntents.libraryItemOf(context, row)?.takeIf { it.isPlayable }
            if (playback == null || item == null) {
                openElsewhere(row)
            } else {
                playback.play(item)
                if (item.isVideo) onOpenPlayer()
            }
        },
        onOpenSettings = onOpenSettings,
        backgroundActions = backgroundActions,
        deleteFileQuestion = deleteFileQuestion,
        deleteFileActions = remember(viewModel) {
            DeleteFileActions(
                onDeleteFile = viewModel::onDeleteFile,
                onConfirm = viewModel::confirmDeleteFile,
                onCancel = viewModel::cancelDeleteFile,
            )
        },
        snackbarHostState = snackbarHostState,
        onResetYouTubeSession = viewModel::resetYouTubeSession,
        onBreakLink = viewModel::breakLink,
        selectionActions = remember(viewModel) {
            DownloadSelectionActions(
                onRemoveFromList = viewModel::removeFromList,
                onDeleteFiles = viewModel::onDeleteFiles,
            )
        },
    )
}

/** Item 5 (v1.0.2): what the select bar does with the picked finished downloads. */
data class DownloadSelectionActions(
    val onRemoveFromList: (List<String>) -> Unit,
    val onDeleteFiles: (List<String>) -> Unit,
) {
    companion object {
        val None = DownloadSelectionActions(onRemoveFromList = {}, onDeleteFiles = {})
    }
}

/** "Delete file" on a finished download and its question (P42). */
data class DeleteFileActions(
    val onDeleteFile: (String) -> Unit,
    val onConfirm: () -> Unit,
    val onCancel: () -> Unit,
) {
    companion object {
        val None = DeleteFileActions(onDeleteFile = {}, onConfirm = {}, onCancel = {})
    }
}

/**
 * Downloads from the design (`04-downloads`): the title with Pause all, filter chips, one card
 * per task with its progress or status, finished downloads under "Completed today" and
 * "Earlier", and the save location with its free space above the bottom bar.
 *
 * Tapping a card opens a menu with every control the task allows, and accessibility services
 * get the same controls as custom actions. A finished download's play button plays it in the
 * app ([onPlay]); its menu can also hand it to another app ([onOpen]).
 *
 * Item 5 (v1.0.2): a long press on a finished download starts select mode: taps pick finished
 * downloads, and the bar above the list removes them from the list or deletes their files
 * ([selectionActions]); Back or Cancel leaves it.
 */
@Composable
fun DownloadsScreen(
    uiState: DownloadsUiState,
    onAction: (DownloadAction, String) -> Unit,
    onPauseAll: () -> Unit,
    modifier: Modifier = Modifier,
    onOpen: (DownloadRowUiState) -> Unit = {},
    onPlay: (DownloadRowUiState) -> Unit = onOpen,
    onOpenSettings: () -> Unit = {},
    todayStartEpochMs: Long = remember { startOfDayEpochMs(System.currentTimeMillis()) },
    backgroundActions: BackgroundCardActions = BackgroundCardActions.None,
    deleteFileQuestion: DeleteFileQuestion? = null,
    deleteFileActions: DeleteFileActions = DeleteFileActions.None,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onResetYouTubeSession: () -> Unit = {},
    /** Track E (E-D9): the trial's tool that breaks a download's link; only the trial has it. */
    onBreakLink: (String) -> Unit = {},
    selectionActions: DownloadSelectionActions = DownloadSelectionActions.None,
) {
    val colors = YftTheme.colors
    var filter by rememberSaveable { mutableStateOf(DownloadsFilter.ALL) }
    // Item 5 (v1.0.2): the finished downloads picked in select mode, in the order picked; rows
    // that went or the filter hides drop out, and select mode ends with the last one.
    var picked by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val finishedIds = uiState.rowsFor(filter)
        .filter { it.status == DownloadTaskStatus.COMPLETED }
        .map { it.id }
    val selection = RowSelection(
        picked = picked.filter { it in finishedIds },
        onToggle = { id -> picked = if (id in picked) picked - id else picked + id },
    )
    BackHandler(enabled = selection.active) { picked = emptyList() }
    // Item 7 Track B3: "Reset YouTube session?" is open.
    var youTubeResetAsked by rememberSaveable { mutableStateOf(false) }
    val storage = uiState.storage
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (selection.active) {
                SelectionBar(
                    count = selection.picked.size,
                    allPicked = selection.picked.size == finishedIds.size,
                    onCancel = { picked = emptyList() },
                    onSelectAll = { picked = finishedIds },
                    onRemoveFromList = {
                        selectionActions.onRemoveFromList(selection.picked)
                        picked = emptyList()
                    },
                    onDeleteFiles = { selectionActions.onDeleteFiles(selection.picked) },
                )
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("downloads-list"),
                contentPadding = PaddingValues(bottom = if (storage != null) 80.dp else 16.dp),
            ) {
                item(key = "header") {
                    YftScreenHeader(
                        title = YftDestination.DOWNLOADS.title,
                        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp),
                    ) {
                        YftOutlinedButton(
                            text = "Pause all",
                            onClick = onPauseAll,
                            modifier = Modifier.testTag("downloads-pause-all"),
                            icon = YftIcons.Pause,
                            enabled = uiState.canPauseAll,
                            compact = true,
                        )
                    }
                }
                if (!uiState.isEmpty) {
                    item(key = "filters") {
                        FilterRow(
                            uiState = uiState,
                            selected = filter,
                            onSelect = {
                                filter = it
                                picked = emptyList()
                            },
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                val holdsWork = uiState.rows.any { it.status in NETWORK_HELD_STATUSES }
                if (uiState.network != TransferNetworkState.ALLOWED && holdsWork) {
                    item(key = "network") {
                        NetworkNotice(
                            network = uiState.network,
                            onOpenSettings = onOpenSettings,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp),
                        )
                    }
                }
                backgroundItems(uiState.background, backgroundActions)
                if (uiState.isEmpty) {
                    item(key = "empty") { EmptyQueue(Modifier.fillParentMaxHeight(EMPTY_HEIGHT)) }
                } else {
                    downloadItems(
                        uiState = uiState,
                        filter = filter,
                        todayStartEpochMs = todayStartEpochMs,
                        onAction = onAction,
                        onOpen = onOpen,
                        onPlay = onPlay,
                        onDeleteFile = deleteFileActions.onDeleteFile,
                        onResetYouTube = { youTubeResetAsked = true },
                        onBreakLink = onBreakLink,
                        selection = selection,
                    )
                }
            }
        }
        if (storage != null) {
            StoragePill(
                storage = storage,
                onOpenSettings = onOpenSettings,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (storage != null) 48.dp else 8.dp),
        )
    }
    deleteFileQuestion?.let { question ->
        DeleteFileDialog(
            question = question,
            onConfirm = {
                picked = emptyList()
                deleteFileActions.onConfirm()
            },
            onCancel = deleteFileActions.onCancel,
        )
    }
    if (youTubeResetAsked) {
        YouTubeResetDialog(
            onConfirm = {
                youTubeResetAsked = false
                onResetYouTubeSession()
            },
            onCancel = { youTubeResetAsked = false },
        )
    }
}

/**
 * Item 7 Track B3: "Reset YouTube session?" before YFT's browser forgets YouTube's cookies, site
 * storage and remembered lookups; other sites keep their sign-ins.
 */
@Composable
internal fun YouTubeResetDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = YftTheme.colors
    AlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.testTag("download-reset-youtube-dialog"),
        title = { Text("$RESET_YOUTUBE_LABEL?", color = colors.textPrimary) },
        text = { Text(YOUTUBE_RESET_QUESTION, color = colors.textPrimary) },
        confirmButton = {
            YftTextButton("Reset", onConfirm, Modifier.testTag("download-reset-youtube-confirm"))
        },
        dismissButton = {
            YftTextButton("Cancel", onCancel, Modifier.testTag("download-reset-youtube-cancel"))
        },
        containerColor = colors.card,
    )
}

/**
 * "Delete this file?" before "Delete file" deletes (P42, `DELETE_CONFIRM`); item 5 (v1.0.2): or
 * "Delete 3 files?" for the downloads picked in select mode.
 */
@Composable
internal fun DeleteFileDialog(
    question: DeleteFileQuestion,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = YftTheme.colors
    val text = if (question.count == 1) {
        deleteFileQuestionText(question.fileName)
    } else {
        deleteFilesQuestionText(question.count)
    }
    AlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.testTag("download-delete-file-dialog"),
        title = { Text(deleteFilesTitle(question.count), color = colors.textPrimary) },
        text = { Text(text, color = colors.textPrimary) },
        // N5 (v1.0.2): Delete in the same red as the Library's.
        confirmButton = {
            YftTextButton(
                text = "Delete",
                onClick = onConfirm,
                modifier = Modifier.testTag("download-delete-file-confirm"),
                color = colors.coralText,
            )
        },
        dismissButton = {
            YftTextButton("Cancel", onCancel, Modifier.testTag("download-delete-file-cancel"))
        },
        containerColor = colors.card,
    )
}

/**
 * Item 5 (v1.0.2): the finished downloads picked in select mode ([picked], in the order picked)
 * and how a card picks or drops itself.
 */
internal class RowSelection(
    val picked: List<String>,
    val onToggle: (String) -> Unit,
) {
    val active: Boolean
        get() = picked.isNotEmpty()

    companion object {
        val None = RowSelection(picked = emptyList(), onToggle = {})
    }
}

/**
 * Item 5 (v1.0.2): the bar above the list in select mode: Cancel, how many are picked, Select
 * all, then **Remove from list** (the files stay) and **Delete file** (one question first).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SelectionBar(
    count: Int,
    allPicked: Boolean,
    onCancel: () -> Unit,
    onSelectAll: () -> Unit,
    onRemoveFromList: () -> Unit,
    onDeleteFiles: () -> Unit,
) {
    val colors = YftTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(start = 4.dp, top = 4.dp, end = 16.dp, bottom = 8.dp)
            .testTag("downloads-select-bar"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            YftIconButton(
                icon = YftIcons.Close,
                contentDescription = "Cancel",
                onClick = onCancel,
                modifier = Modifier.testTag("downloads-select-cancel"),
            )
            Text(
                text = selectedCountLabel(count),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("downloads-select-count"),
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            YftTextButton(
                text = SELECT_ALL_LABEL,
                onClick = onSelectAll,
                modifier = Modifier.testTag("downloads-select-all"),
                enabled = !allPicked,
            )
        }
        FlowRow(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            YftOutlinedButton(
                text = menuLabel(DownloadAction.DELETE),
                onClick = onRemoveFromList,
                modifier = Modifier.testTag("downloads-select-remove"),
                icon = YftIcons.Remove,
                compact = true,
            )
            YftOutlinedButton(
                text = deleteFilesLabel(count),
                onClick = onDeleteFiles,
                modifier = Modifier.testTag("downloads-select-delete-file"),
                icon = YftIcons.Delete,
                compact = true,
            )
        }
    }
}

/** P34: the notifications card, then the battery card, under the network notice. */
private fun LazyListScope.backgroundItems(
    background: BackgroundCardsUiState,
    actions: BackgroundCardActions,
) {
    if (background.notificationsCard) {
        item(key = "background-notifications") {
            NotificationsCard(
                onTurnOn = actions.onTurnOnNotifications,
                onDismiss = actions.onDismissNotifications,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
            )
        }
    }
    val battery = background.batteryCard ?: return
    item(key = "background-battery") {
        BatteryCard(
            card = battery,
            onAllow = actions.onAllow,
            onOpenAppSettings = actions.onOpenAppSettings,
            onNotNow = actions.onNotNow,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
        )
    }
}

private fun LazyListScope.downloadItems(
    uiState: DownloadsUiState,
    filter: DownloadsFilter,
    todayStartEpochMs: Long,
    onAction: (DownloadAction, String) -> Unit,
    onOpen: (DownloadRowUiState) -> Unit,
    onPlay: (DownloadRowUiState) -> Unit,
    onDeleteFile: (String) -> Unit,
    onResetYouTube: () -> Unit,
    onBreakLink: (String) -> Unit,
    selection: RowSelection,
) {
    val visible = uiState.rowsFor(filter)
    if (visible.isEmpty()) {
        item(key = "filter-empty") { FilterEmpty(filter) }
        return
    }
    val (finished, unfinished) = visible.partition { it.status == DownloadTaskStatus.COMPLETED }
    val (today, earlier) = finished.partition { it.updatedAtEpochMs >= todayStartEpochMs }
    val cards: LazyListScope.(List<DownloadRowUiState>) -> Unit = { rows ->
        items(items = rows, key = { "row-${it.id}" }) { row ->
            DownloadCard(
                row = row,
                network = uiState.network,
                onAction = onAction,
                onOpen = onOpen,
                onPlay = onPlay,
                onDeleteFile = onDeleteFile,
                onResetYouTube = onResetYouTube,
                onBreakLink = onBreakLink,
                selection = selection,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
            )
        }
    }
    cards(unfinished)
    if (today.isNotEmpty()) {
        item(key = "completed-today") { SectionLabel("Completed today") }
        cards(today)
    }
    if (earlier.isNotEmpty()) {
        item(key = "completed-earlier") { SectionLabel("Earlier") }
        cards(earlier)
    }
}

@Composable
private fun FilterRow(
    uiState: DownloadsUiState,
    selected: DownloadsFilter,
    onSelect: (DownloadsFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DownloadsFilter.entries.forEach { option ->
            val count = when (option) {
                DownloadsFilter.ALL, DownloadsFilter.DONE -> null
                else -> uiState.count(option).takeIf { it > 0 }
            }
            YftFilterChip(
                label = option.label,
                selected = option == selected,
                onClick = { onSelect(option) },
                modifier = Modifier.testTag("downloads-filter-${option.name.lowercase(Locale.US)}"),
                count = count,
            )
        }
    }
}

/** One line on why queued work is not moving; nothing is shown while transfers may run. */
/** One line on why queued work waits; with Wi-Fi only on, tapping it opens Settings. */
@Composable
private fun NetworkNotice(
    network: TransferNetworkState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YftTheme.colors
    val wifiOnly = when (network) {
        TransferNetworkState.ALLOWED -> return
        TransferNetworkState.WAITING_FOR_UNMETERED -> true
        TransferNetworkState.OFFLINE -> false
    }
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = colors.textPrimary, fontWeight = FontWeight.SemiBold)) {
            append(if (wifiOnly) "Wi-Fi only is on" else "No connection")
        }
        if (!wifiOnly) {
            withStyle(SpanStyle(color = colors.textSecondary)) {
                append(" · Downloads resume when you're online")
            }
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .heightIn(min = NOTICE_HEIGHT)
            .clip(YftShapes.card)
            .background(colors.chipOnBackground)
            .then(
                if (wifiOnly) {
                    Modifier.clickable(
                        onClickLabel = "Open Settings",
                        role = Role.Button,
                        onClick = onOpenSettings,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("downloads-network-banner")
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YftIcon(
            icon = if (wifiOnly) YftIcons.Wifi else YftIcons.WifiOff,
            contentDescription = null,
            tint = colors.icon,
            size = 18.dp,
        )
        Text(
            text = text,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
            style = MaterialTheme.typography.labelLarge,
        )
        if (wifiOnly) {
            Text(
                text = "Settings",
                modifier = Modifier.padding(start = 8.dp),
                color = colors.link,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
private fun EmptyQueue(modifier: Modifier = Modifier) {
    val colors = YftTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .testTag("downloads-empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(colors.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            YftIcon(
                icon = YftIcons.Download,
                contentDescription = null,
                tint = colors.link,
                size = 32.dp,
            )
        }
        Text(
            text = "No downloads yet",
            modifier = Modifier.padding(top = 16.dp),
            color = colors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Open a page in the browser, preview the media and start a download.",
            modifier = Modifier.padding(top = 6.dp),
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FilterEmpty(filter: DownloadsFilter) {
    Text(
        text = when (filter) {
            DownloadsFilter.ALL -> "No downloads yet"
            DownloadsFilter.ACTIVE -> "Nothing is downloading right now."
            DownloadsFilter.QUEUED -> "Nothing is waiting to start."
            DownloadsFilter.DONE -> "Finished downloads appear here."
            DownloadsFilter.FAILED -> "No failed downloads."
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 32.dp)
            .testTag("downloads-filter-empty"),
        color = YftTheme.colors.textSecondary,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .padding(start = 16.dp, top = 16.dp, end = 16.dp)
            .semantics { heading() },
        color = YftTheme.colors.textSecondary,
        style = MaterialTheme.typography.labelMedium,
    )
}

/** One control in a card's menu and in its accessibility actions. */
private class CardAction(
    val label: String,
    val tag: String,
    @DrawableRes val icon: Int,
    val run: () -> Unit,
)

private fun cardActions(
    row: DownloadRowUiState,
    onAction: (DownloadAction, String) -> Unit,
    onOpen: (DownloadRowUiState) -> Unit,
    onPlay: (DownloadRowUiState) -> Unit,
    onDeleteFile: (String) -> Unit,
    onDetails: () -> Unit,
    onResetYouTube: () -> Unit,
    showDetails: Boolean = row.showsDetails,
    onBreakLink: ((String) -> Unit)? = null,
): List<CardAction> = buildList {
    if (row.status == DownloadTaskStatus.COMPLETED) {
        add(CardAction("Play", "download-menu-play-${row.id}", YftIcons.Play) { onPlay(row) })
        add(
            CardAction("Open with…", "download-menu-open-${row.id}", YftIcons.OpenInNew) {
                onOpen(row)
            },
        )
    }
    DownloadAction.entries
        .filter { it in row.availableActions }
        .forEach { action ->
            add(
                CardAction(menuLabel(action), menuTag(action, row.id), actionIcon(action)) {
                    onAction(action, row.id)
                },
            )
            // P42: beside "Remove from list", which keeps the file.
            if (action == DownloadAction.DELETE && row.status == DownloadTaskStatus.COMPLETED) {
                add(
                    CardAction(DELETE_FILE_LABEL, deleteFileTag(row.id), YftIcons.Delete) {
                        onDeleteFile(row.id)
                    },
                )
            }
        }
    if (showDetails) {
        add(
            CardAction(
                label = if (row.showsFailureDetails) "Failure details" else "Details",
                tag = "download-menu-details-${row.id}",
                icon = YftIcons.Info,
                run = onDetails,
            ),
        )
    }
    if (row.isYouTubeRefusal()) {
        add(
            CardAction(
                label = RESET_YOUTUBE_LABEL,
                tag = "download-menu-reset-youtube-${row.id}",
                icon = YftIcons.Replay,
                run = onResetYouTube,
            ),
        )
    }
    // Track E (E-D9): the trial's own tool for the phone test, on a running or paused download.
    // Every other build drops it with its label (BuildConfig.SOCIAL_TRIAL is false there; the
    // release check rejects the label anywhere but the trial).
    if (BuildConfig.SOCIAL_TRIAL && onBreakLink != null && row.status in BREAKABLE_STATUSES) {
        add(
            CardAction(
                label = "Break this download's link",
                tag = "download-menu-break-link-${row.id}",
                icon = YftIcons.Link,
            ) { onBreakLink(row.id) },
        )
    }
}

/** Track E (E-D9): the downloads whose link the trial's tool breaks: they are using one. */
private val BREAKABLE_STATUSES = setOf(DownloadTaskStatus.RUNNING, DownloadTaskStatus.PAUSED)

@DrawableRes
private fun actionIcon(action: DownloadAction): Int = when (action) {
    DownloadAction.PAUSE -> YftIcons.Pause
    DownloadAction.RESUME -> YftIcons.Play
    DownloadAction.RETRY -> YftIcons.Replay
    DownloadAction.CANCEL -> YftIcons.Close
    DownloadAction.DELETE -> YftIcons.Delete
}

@Composable
private fun DownloadCard(
    row: DownloadRowUiState,
    network: TransferNetworkState,
    onAction: (DownloadAction, String) -> Unit,
    onOpen: (DownloadRowUiState) -> Unit,
    onPlay: (DownloadRowUiState) -> Unit,
    onDeleteFile: (String) -> Unit,
    onResetYouTube: () -> Unit,
    onBreakLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    selection: RowSelection = RowSelection.None,
) {
    val colors = YftTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    var detailsOpen by remember { mutableStateOf(false) }
    // Decision 30 (v): a quiet build shows a failure's Details only with Show details on; the
    // failed download's Retry stays (a function).
    val showDetails = row.showsDetails && !LocalQuietRelease.current
    // Track E (E-D9): the trial's tool, as the trial's VK value break (never in a quiet build).
    val trialTools = BuildConfig.SOCIAL_TRIAL && !LocalQuietRelease.current
    val actions = cardActions(
        row = row,
        onAction = onAction,
        onOpen = onOpen,
        onPlay = onPlay,
        onDeleteFile = onDeleteFile,
        onDetails = { detailsOpen = true },
        onResetYouTube = onResetYouTube,
        showDetails = showDetails,
        onBreakLink = onBreakLink.takeIf { trialTools },
    )
    // Item 5 (v1.0.2): finished downloads can be picked; in select mode a tap picks or drops
    // them, and the other rows wait.
    val selectable = row.status == DownloadTaskStatus.COMPLETED
    val selecting = selection.active
    val picked = selectable && row.id in selection.picked
    val toggle = { selection.onToggle(row.id) }
    Box(modifier = modifier.fillMaxWidth()) {
        YftCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("download-${row.id}")
                .semantics {
                    if (selecting) {
                        if (selectable) selected = picked
                    } else {
                        customActions = actions.map { action ->
                            CustomAccessibilityAction(action.label) {
                                action.run()
                                true
                            }
                        }
                    }
                },
            onClick = when {
                !selecting -> ({ menuOpen = true })
                selectable -> toggle
                else -> ({})
            },
            contentPadding = PaddingValues(10.dp),
            onLongClick = toggle.takeIf { selectable },
            onLongClickLabel = SELECT_LABEL.takeIf { selectable && !selecting },
            selected = picked && selecting,
        ) {
            when (row.status) {
                DownloadTaskStatus.COMPLETED -> FinishedRow(
                    row = row,
                    onPlay = onPlay,
                    selecting = selecting,
                    picked = picked,
                )
                in PROGRESS_STATUSES -> ProgressRow(row = row, onAction = onAction)
                else -> StatusRow(
                    row = row,
                    network = network,
                    onAction = onAction,
                    onDetails = { detailsOpen = true },
                    onResetYouTube = onResetYouTube,
                    showDetails = showDetails,
                )
            }
            // v1.0.2 item 28: the chosen folder could not take this file.
            if (row.folderUnavailable) {
                Text(
                    text = folderUnavailableNotice(row.destinationKind),
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .testTag("download-folder-note-${row.id}"),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Box(modifier = Modifier.align(Alignment.BottomEnd)) {
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                shape = YftShapes.card,
                containerColor = colors.card,
            ) {
                actions.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(text = action.label, color = colors.textPrimary) },
                        onClick = {
                            menuOpen = false
                            action.run()
                        },
                        modifier = Modifier.testTag(action.tag),
                        leadingIcon = {
                            YftIcon(
                                icon = action.icon,
                                contentDescription = null,
                                tint = colors.icon,
                            )
                        },
                    )
                }
            }
        }
    }
    if (detailsOpen && showDetails) {
        FailureDetailsDialog(row = row, onDismiss = { detailsOpen = false })
    }
}

/**
 * What failed, where and on which phone (P21), with Copy details for a bug report; Track E: or,
 * for a download that got a fresh link, how it went on. The text holds no file name, link or
 * address ([downloadDetailsText]).
 */
@Composable
internal fun FailureDetailsDialog(
    row: DownloadRowUiState,
    onDismiss: () -> Unit,
    appVersion: String = BuildConfig.VERSION_NAME,
    androidRelease: String = Build.VERSION.RELEASE.orEmpty(),
    sdkInt: Int = Build.VERSION.SDK_INT,
    // v1.0.2 item 26: the phone's AV1 decoders, only while a debug build's AV1 merges are on.
    av1Decoder: String? = remember { Av1MergeSwitch.detailLine() },
) {
    val colors = YftTheme.colors
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val text = remember(row, appVersion, androidRelease, sdkInt, av1Decoder) {
        downloadDetailsText(row, appVersion, androidRelease, sdkInt, av1Decoder)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("download-failure-dialog"),
        title = {
            Text(
                text = if (row.showsFailureDetails) "Failure details" else "Download details",
                color = colors.textPrimary,
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SelectionContainer {
                    Text(
                        text = text,
                        modifier = Modifier.testTag("download-failure-text"),
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            YftTextButton(
                text = "Copy details",
                onClick = {
                    clipboard.setText(AnnotatedString(text))
                    // Android 13 and later confirm a copy themselves.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(context, "Details copied", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.testTag("download-failure-copy"),
            )
        },
        dismissButton = {
            YftTextButton("Close", onDismiss, Modifier.testTag("download-failure-close"))
        },
        containerColor = colors.card,
    )
}

/** Running, paused or finishing: meta and percent, the bar, then amount, speed and time left. */
@Composable
private fun ProgressRow(
    row: DownloadRowUiState,
    onAction: (DownloadAction, String) -> Unit,
) {
    val colors = YftTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        RowThumbnail(row = row, modifier = Modifier.size(THUMBNAIL_SIZE))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            CardTitle(row.title)
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetaText(
                    text = metaLabel(row),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("download-meta-${row.id}"),
                )
                percentLabel(row)?.let { percent ->
                    Text(
                        text = percent,
                        modifier = Modifier.padding(start = 8.dp),
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
            }
            val fraction = row.progressFraction
            val stopped = row.status == DownloadTaskStatus.PAUSED ||
                row.status == DownloadTaskStatus.PAUSING
            YftProgressBar(
                progress = fraction ?: if (stopped) 0f else null,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .testTag(
                        if (fraction != null || stopped) {
                            "download-progress-${row.id}"
                        } else {
                            "download-progress-indeterminate-${row.id}"
                        },
                    ),
            )
            val detail = progressDetail(row)
            var tooLong by remember(detail) { mutableStateOf(false) }
            Text(
                text = if (tooLong) progressDetail(row, withSpeed = false) else detail,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .testTag(
                        if (row.stage != null) {
                            "download-stage-${row.id}"
                        } else {
                            "download-detail-${row.id}"
                        },
                    ),
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!tooLong && it.hasVisualOverflow) tooLong = true },
            )
        }
        val primary = when {
            DownloadAction.PAUSE in row.availableActions -> DownloadAction.PAUSE
            DownloadAction.RESUME in row.availableActions -> DownloadAction.RESUME
            else -> null
        }
        if (primary != null) {
            YftCircleButton(
                icon = if (primary == DownloadAction.PAUSE) YftIcons.Pause else YftIcons.Play,
                contentDescription = "${actionLabel(primary)} ${row.title}",
                onClick = { onAction(primary, row.id) },
                modifier = Modifier
                    .padding(start = 4.dp)
                    .testTag(actionTag(primary, row.id)),
                size = CIRCLE_SIZE,
            )
        }
    }
}

/**
 * Queued, waiting, failed or cancelled: meta, the status pill and Retry or Remove; a failed
 * download also offers its Details.
 */
@Composable
private fun StatusRow(
    row: DownloadRowUiState,
    network: TransferNetworkState,
    onAction: (DownloadAction, String) -> Unit,
    onDetails: () -> Unit,
    onResetYouTube: () -> Unit,
    showDetails: Boolean = row.showsFailureDetails,
) {
    val colors = YftTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        RowThumbnail(row = row, modifier = Modifier.size(THUMBNAIL_SIZE))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            CardTitle(row.title)
            val chip = statusChip(row, network)
            if (chip?.tone != YftStatusTone.Failed) {
                MetaText(
                    text = metaLabel(row),
                    modifier = Modifier.testTag("download-meta-${row.id}"),
                )
            }
            if (chip != null) {
                YftStatusChip(
                    text = chip.text,
                    tone = chip.tone,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .testTag("download-status-${row.id}"),
                    icon = chip.icon,
                )
            }
            if (row.requiresLinkRefresh || row.status == DownloadTaskStatus.NEEDS_REFRESH) {
                Text(
                    text = when {
                        row.isYouTubeRefusal() -> YOUTUBE_REFUSED_HINT
                        // Track E (E-D6): Retry looks the page up again while YFT has its source;
                        // a page read again without player data would be read the same way.
                        DownloadAction.RETRY in row.availableActions && !row.noPlayerOnPage ->
                            RENEWABLE_LINK_NOTE
                        else -> REOPEN_PAGE_NOTE
                    },
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .testTag("download-refresh-note-${row.id}"),
                    color = colors.coralText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (showDetails) {
                YftTextButton(
                    text = "Details",
                    onClick = onDetails,
                    modifier = Modifier.testTag("download-failure-details"),
                )
            }
            if (row.isYouTubeRefusal()) {
                YftTextButton(
                    text = RESET_YOUTUBE_LABEL,
                    onClick = onResetYouTube,
                    modifier = Modifier.testTag("download-reset-youtube-${row.id}"),
                )
            }
        }
        val textAction = when {
            DownloadAction.RETRY in row.availableActions -> DownloadAction.RETRY
            row.status != DownloadTaskStatus.QUEUED &&
                row.status != DownloadTaskStatus.WAITING_FOR_NETWORK &&
                DownloadAction.DELETE in row.availableActions -> DownloadAction.DELETE
            else -> null
        }
        if (textAction != null) {
            YftTextButton(
                text = actionLabel(textAction),
                onClick = { onAction(textAction, row.id) },
                modifier = Modifier
                    .align(Alignment.Bottom)
                    .testTag(actionTag(textAction, row.id)),
            )
        }
    }
}

/**
 * A finished download: the file's own frame or cover in a smaller tile, its picture size when
 * known, the green check and Play, which plays it in the app.
 */
@Composable
private fun FinishedRow(
    row: DownloadRowUiState,
    onPlay: (DownloadRowUiState) -> Unit,
    selecting: Boolean = false,
    picked: Boolean = false,
) {
    val colors = YftTheme.colors
    val context = LocalContext.current
    val uri = remember(row.destinationKind, row.destinationUri, row.displayName) {
        DownloadIntents.uriOf(context, row)?.toString()
    }
    val details = rememberMediaDetails(uri, row.isAudio)
    Row(verticalAlignment = Alignment.CenterVertically) {
        RowThumbnail(
            row = row,
            modifier = Modifier.size(width = 48.dp, height = 44.dp),
            iconSize = 22.dp,
            image = details?.image,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            CardTitle(row.title)
            MetaText(
                text = metaLabel(row, quality = details?.qualityLabel),
                modifier = Modifier.testTag("download-meta-${row.id}"),
            )
        }
        YftIcon(
            icon = YftIcons.CheckCircle,
            contentDescription = "Completed",
            modifier = Modifier.padding(start = 8.dp),
            tint = colors.success,
            size = 22.dp,
        )
        if (selecting) {
            // Item 5 (v1.0.2): the pick mark stands where Play was, so a tap only picks.
            Box(
                modifier = Modifier
                    .size(CIRCLE_SIZE)
                    .testTag("download-pick-${row.id}"),
                contentAlignment = Alignment.Center,
            ) {
                if (picked) {
                    YftIcon(
                        icon = YftIcons.Check,
                        contentDescription = "Selected",
                        tint = colors.link,
                        size = 24.dp,
                    )
                }
            }
        } else {
            YftCircleButton(
                icon = YftIcons.Play,
                contentDescription = "Play ${row.title}",
                onClick = { onPlay(row) },
                modifier = Modifier.testTag("download-open-${row.id}"),
                size = CIRCLE_SIZE,
            )
        }
    }
}

@Composable
private fun RowThumbnail(
    row: DownloadRowUiState,
    modifier: Modifier,
    iconSize: Dp = 28.dp,
    image: ImageBitmap? = null,
) {
    // P19: the picture saved when the download started, until the file's own frame exists.
    val saved = rememberDownloadThumbnail(row.id)
    YftThumbnail(
        image = image ?: saved,
        kind = if (row.isAudio) YftMediaKind.Audio else YftMediaKind.Video,
        modifier = modifier,
        shape = YftShapes.thumbnailSmall,
        iconSize = iconSize,
    )
}

@Composable
private fun CardTitle(text: String) {
    Text(
        text = text,
        color = YftTheme.colors.textPrimary,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun MetaText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = YftTheme.colors.textSecondary,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Where downloads go and the room left; tapping it opens Settings to change the location. */
@Composable
private fun StoragePill(
    storage: DownloadStorageSummary,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YftTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to colors.background.copy(alpha = 0f),
                    FADE_STOP to colors.background,
                ),
            )
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .height(STORAGE_PILL_HEIGHT)
                .clip(YftShapes.pill)
                .background(colors.chipOnBackground)
                .border(1.dp, colors.border, YftShapes.pill)
                .clickable(
                    onClickLabel = "Change in Settings",
                    role = Role.Button,
                    onClick = onOpenSettings,
                )
                .padding(horizontal = 16.dp)
                .testTag("downloads-storage"),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YftIcon(
                icon = YftIcons.Folder,
                contentDescription = null,
                tint = colors.textPrimary,
                size = 18.dp,
            )
            Text(
                text = storageLabel(storage),
                modifier = Modifier.padding(start = 8.dp),
                color = colors.textPrimary,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Hands one finished download to another app with a read grant for that file only. */
internal object DownloadIntents {
    private const val ANY_TYPE = "*/*"

    /** The saved file's `content://` address, or null when the task recorded none. */
    fun uriOf(context: Context, row: DownloadRowUiState): Uri? = when (row.destinationKind) {
        DownloadDestinationKind.APP_PRIVATE ->
            AppPrivateDownloadProvider.uriFor(context, row.displayName)

        DownloadDestinationKind.MEDIA_STORE, DownloadDestinationKind.SAF_DOCUMENT ->
            row.destinationUri?.let(Uri::parse)
    }

    fun view(context: Context, row: DownloadRowUiState): Intent? {
        val uri = uriOf(context, row) ?: return null
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeTypeOf(row) ?: ANY_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** The finished file as the Library's player takes it, or null when it has no address. */
    fun libraryItemOf(context: Context, row: DownloadRowUiState): LibraryItem? {
        val uri = uriOf(context, row) ?: return null
        return LibraryItem(
            id = "download-${row.id}",
            displayName = row.displayName,
            uri = uri.toString(),
            mimeType = mimeTypeOf(row),
            sizeBytes = row.totalBytes ?: row.downloadedBytes.takeIf { it > 0L },
            modifiedAtEpochMs = row.updatedAtEpochMs.takeIf { it > 0L },
            location = when (row.destinationKind) {
                DownloadDestinationKind.APP_PRIVATE -> LibraryLocation.APP_STORAGE
                DownloadDestinationKind.MEDIA_STORE -> LibraryLocation.SHARED_DOWNLOADS
                DownloadDestinationKind.SAF_DOCUMENT -> LibraryLocation.CHOSEN_FOLDER
            },
        )
    }

    private fun mimeTypeOf(row: DownloadRowUiState): String? =
        row.mimeType ?: LibraryMimeTypes.forFileName(row.displayName)
}

/** Local midnight before [nowEpochMs], which splits "Completed today" from "Earlier". */
internal fun startOfDayEpochMs(nowEpochMs: Long, zone: TimeZone = TimeZone.getDefault()): Long =
    Calendar.getInstance(zone).apply {
        timeInMillis = nowEpochMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private val THUMBNAIL_SIZE = 68.dp
private val CIRCLE_SIZE = 40.dp
private val NOTICE_HEIGHT = 40.dp
private val STORAGE_PILL_HEIGHT = 34.dp

/** Statuses the network rule holds back, which is when the notice explains why. */
private val NETWORK_HELD_STATUSES = setOf(
    DownloadTaskStatus.QUEUED,
    DownloadTaskStatus.WAITING_FOR_NETWORK,
)
private const val EMPTY_HEIGHT = 0.7f
private const val FADE_STOP = 0.35f
