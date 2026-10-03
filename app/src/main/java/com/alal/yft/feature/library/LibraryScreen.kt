package com.alal.yft.feature.library

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alal.yft.ui.components.YftFilterChip
import com.alal.yft.ui.components.YftIcon
import com.alal.yft.ui.components.YftIconButton
import com.alal.yft.ui.components.YftMediaKind
import com.alal.yft.ui.components.YftPrimaryButton
import com.alal.yft.ui.components.YftScreenHeader
import com.alal.yft.ui.components.YftTextButton
import com.alal.yft.ui.components.YftThumbnail
import com.alal.yft.ui.format.YftFormat
import com.alal.yft.ui.theme.YftIcons
import com.alal.yft.ui.theme.YftShapes
import com.alal.yft.ui.theme.YftTheme
import java.util.Locale

@Composable
fun LibraryRoute(
    onOpenPlayer: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playing by viewModel.playing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Files can change while YFT is in the background, so the list is re-read on every resume.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val openElsewhere: (LibraryItem) -> Unit = { item ->
        if (!LibraryIntents.start(context, LibraryIntents.view(item))) {
            viewModel.showMessage("No app on this device can open ${item.displayName}.")
        }
    }
    val loaded = playing?.item
    val playingId = (uiState as? LibraryUiState.Ready)?.items
        ?.firstOrNull { it.isSameFileAs(loaded) }
        ?.id
    LibraryScreen(
        uiState = uiState,
        playingId = playingId,
        onRefresh = viewModel::refresh,
        onPlay = { item ->
            when {
                !item.isPlayable -> openElsewhere(item)
                item.isVideo -> {
                    viewModel.play(item)
                    onOpenPlayer()
                }
                else -> viewModel.play(item)
            }
        },
        onOpen = openElsewhere,
        onShare = { item ->
            if (!LibraryIntents.start(context, LibraryIntents.share(item))) {
                viewModel.showMessage("${item.displayName} could not be shared.")
            }
        },
        onRequestDelete = viewModel::requestDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onDismissDelete = viewModel::dismissDelete,
        onDismissMessage = viewModel::dismissMessage,
    )
}

/**
 * The Library tab (`05`): title with search and sort, All / Video / Audio, and a two-column grid
 * of saved files with their real thumbnail, length, "720p · 96 MB" and a ⋯ menu. Tapping a
 * tile plays it: audio in the mini player, video full screen.
 */
@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    modifier: Modifier = Modifier,
    playingId: String? = null,
    onRefresh: () -> Unit = {},
    onPlay: (LibraryItem) -> Unit = {},
    onOpen: (LibraryItem) -> Unit = {},
    onShare: (LibraryItem) -> Unit = {},
    onRequestDelete: (LibraryItem) -> Unit = {},
    onConfirmDelete: () -> Unit = {},
    onDismissDelete: () -> Unit = {},
    onDismissMessage: () -> Unit = {},
) {
    val colors = YftTheme.colors
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(LibrarySort.NEWEST) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    (uiState as? LibraryUiState.Ready)?.pendingDelete?.let { item ->
        DeleteDialog(item = item, onConfirm = onConfirmDelete, onDismiss = onDismissDelete)
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        YftScreenHeader(title = "Library") {
            YftIconButton(
                icon = YftIcons.Search,
                contentDescription = if (searching) "Close search" else "Search the library",
                onClick = {
                    searching = !searching
                    if (!searching) query = ""
                },
                modifier = Modifier.testTag("library-search"),
                tint = colors.icon,
            )
            SortButton(sort = sort, onSort = { sort = it })
        }
        if (searching) {
            SearchField(
                query = query,
                onQueryChange = { query = it },
                onClose = {
                    searching = false
                    query = ""
                },
            )
        }
        FilterRow(selected = filter, onSelect = { filter = it })
        val content = Modifier
            .fillMaxWidth()
            .weight(1f)
        when (uiState) {
            LibraryUiState.Loading -> CenteredStatus(content.testTag("library-loading")) {
                CircularProgressIndicator(
                    color = colors.accent,
                    modifier = Modifier.semantics { contentDescription = "Loading the library" },
                )
            }

            is LibraryUiState.Error -> CenteredStatus(content.testTag("library-error")) {
                Text(
                    text = uiState.message,
                    color = colors.coralText,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                YftPrimaryButton(
                    text = "Try again",
                    onClick = onRefresh,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .testTag("library-retry"),
                    icon = YftIcons.Refresh,
                )
            }

            is LibraryUiState.Ready -> ReadyLibrary(
                state = uiState,
                shown = remember(uiState.items, filter, sort, query) {
                    arrange(uiState.items, filter, sort, query)
                },
                filter = filter,
                query = query,
                playingId = playingId,
                modifier = content,
                onPlay = onPlay,
                onOpen = onOpen,
                onShare = onShare,
                onRequestDelete = onRequestDelete,
                onDismissMessage = onDismissMessage,
            )
        }
    }
}

@Composable
private fun ReadyLibrary(
    state: LibraryUiState.Ready,
    shown: List<LibraryItem>,
    filter: LibraryFilter,
    query: String,
    playingId: String?,
    modifier: Modifier,
    onPlay: (LibraryItem) -> Unit,
    onOpen: (LibraryItem) -> Unit,
    onShare: (LibraryItem) -> Unit,
    onRequestDelete: (LibraryItem) -> Unit,
    onDismissMessage: () -> Unit,
) {
    Column(modifier = modifier) {
        state.message?.let { message ->
            MessageRow(message = message, onDismiss = onDismissMessage)
        }
        when {
            state.items.isEmpty() -> CenteredStatus(
                Modifier
                    .fillMaxSize()
                    .testTag("library-empty"),
            ) {
                EmptyLibrary()
            }

            shown.isEmpty() -> CenteredStatus(
                Modifier
                    .fillMaxSize()
                    .testTag("library-no-match"),
            ) {
                Text(
                    text = noMatchText(filter, query),
                    color = YftTheme.colors.textSecondary,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("library-grid"),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 8.dp,
                    end = 16.dp,
                    bottom = 16.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
                verticalArrangement = Arrangement.spacedBy(GRID_ROW_GAP),
            ) {
                items(items = shown, key = LibraryItem::id) { item ->
                    LibraryTile(
                        item = item,
                        isPlaying = item.id == playingId,
                        onPlay = onPlay,
                        onOpen = onOpen,
                        onShare = onShare,
                        onRequestDelete = onRequestDelete,
                    )
                }
            }
        }
    }
}

/** One control in a tile's ⋯ menu and in its accessibility actions. */
private class TileAction(
    val label: String,
    val tag: String,
    @DrawableRes val icon: Int,
    val destructive: Boolean = false,
    val run: () -> Unit,
)

@Composable
private fun LibraryTile(
    item: LibraryItem,
    isPlaying: Boolean,
    onPlay: (LibraryItem) -> Unit,
    onOpen: (LibraryItem) -> Unit,
    onShare: (LibraryItem) -> Unit,
    onRequestDelete: (LibraryItem) -> Unit,
) {
    val colors = YftTheme.colors
    val details = rememberMediaDetails(item.uri, item.isAudio)
    val title = YftFormat.title(item.displayName)
    var menuOpen by remember { mutableStateOf(false) }
    val actions = buildList {
        if (item.isPlayable) {
            add(TileAction("Play", "library-play-${item.id}", YftIcons.Play) { onPlay(item) })
        }
        add(
            TileAction("Open with…", "library-open-${item.id}", YftIcons.OpenInNew) {
                onOpen(item)
            },
        )
        add(TileAction("Share", "library-share-${item.id}", YftIcons.Share) { onShare(item) })
        add(
            TileAction("Delete", "library-delete-${item.id}", YftIcons.Delete, destructive = true) {
                onRequestDelete(item)
            },
        )
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(YftShapes.thumbnailSmall)
                .clickable(
                    onClickLabel = if (item.isPlayable) "Play" else "Open with another app",
                    role = Role.Button,
                ) { if (item.isPlayable) onPlay(item) else onOpen(item) }
                .semantics(mergeDescendants = true) {
                    if (isPlaying) stateDescription = "Playing"
                    customActions = actions.drop(1).map { action ->
                        CustomAccessibilityAction(action.label) {
                            action.run()
                            true
                        }
                    }
                }
                .testTag("library-item-${item.id}"),
        ) {
            YftThumbnail(
                image = details?.image,
                kind = if (item.isAudio) YftMediaKind.Audio else YftMediaKind.Video,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(TILE_RATIO),
                shape = YftShapes.thumbnailSmall,
                durationLabel = details?.durationMs?.let(YftFormat::duration),
                iconSize = 44.dp,
            )
            Text(
                text = title,
                modifier = Modifier.padding(top = 6.dp, end = TITLE_END_ROOM),
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = libraryMeta(item, details),
                // The bottom padding keeps the tile's rounded corner off the first letter.
                modifier = Modifier
                    .padding(end = TITLE_END_ROOM, bottom = 6.dp)
                    .testTag("library-meta-${item.id}"),
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Outside the tile's clip, so the ⋯ sits at the tile's edge as drawn while keeping its
        // whole 48dp touch target; it lines up with the title.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = MORE_BUTTON_OFFSET, y = MORE_BUTTON_LIFT),
        ) {
            YftIconButton(
                icon = YftIcons.MoreHoriz,
                contentDescription = "More options for $title",
                onClick = { menuOpen = true },
                modifier = Modifier.testTag("library-more-${item.id}"),
                tint = colors.textSecondary,
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                actions.forEach { action ->
                    val tint = if (action.destructive) colors.coralText else colors.textPrimary
                    DropdownMenuItem(
                        text = { Text(text = action.label, color = tint) },
                        onClick = {
                            menuOpen = false
                            action.run()
                        },
                        leadingIcon = {
                            YftIcon(icon = action.icon, contentDescription = null, tint = tint)
                        },
                        modifier = Modifier.testTag(action.tag),
                    )
                }
            }
        }
    }
}

@Composable
private fun SortButton(sort: LibrarySort, onSort: (LibrarySort) -> Unit) {
    val colors = YftTheme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        YftIconButton(
            icon = YftIcons.Sort,
            contentDescription = "Sort, ${sort.label.lowercase(Locale.ROOT)}",
            onClick = { open = true },
            modifier = Modifier.testTag("library-sort"),
            tint = colors.icon,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LibrarySort.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            color = colors.textPrimary,
                            fontWeight = if (option == sort) FontWeight.Bold else null,
                        )
                    },
                    onClick = {
                        open = false
                        onSort(option)
                    },
                    trailingIcon = if (option == sort) {
                        {
                            YftIcon(
                                icon = YftIcons.Check,
                                contentDescription = "Selected",
                                tint = colors.link,
                            )
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.testTag(
                        "library-sort-${option.name.lowercase(Locale.ROOT)}",
                    ),
                )
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val colors = YftTheme.colors
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
    Row(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(YftShapes.pill)
            .background(colors.chipOnBackground)
            .padding(start = 14.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YftIcon(icon = YftIcons.Search, contentDescription = null, tint = colors.icon, size = 20.dp)
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) {
                Text(
                    text = "Search by name",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = "Search by name" }
                    .testTag("library-search-field"),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            )
        }
        YftIconButton(
            icon = YftIcons.Close,
            contentDescription = "Close search",
            onClick = onClose,
            modifier = Modifier.testTag("library-search-close"),
            tint = colors.textSecondary,
        )
    }
}

@Composable
private fun FilterRow(selected: LibraryFilter, onSelect: (LibraryFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LibraryFilter.entries.forEach { option ->
            YftFilterChip(
                label = option.label,
                selected = option == selected,
                onClick = { onSelect(option) },
                modifier = Modifier.testTag("library-filter-${option.name.lowercase(Locale.ROOT)}"),
                outlined = true,
            )
        }
    }
}

@Composable
private fun MessageRow(message: String, onDismiss: () -> Unit) {
    val colors = YftTheme.colors
    Row(
        modifier = Modifier
            .padding(start = 16.dp, top = 8.dp, end = 16.dp)
            .fillMaxWidth()
            .clip(YftShapes.thumbnailSmall)
            .background(colors.chipOnBackground)
            .padding(start = 14.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp)
                .testTag("library-message"),
            color = colors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
        )
        YftIconButton(
            icon = YftIcons.Close,
            contentDescription = "Dismiss message",
            onClick = onDismiss,
            modifier = Modifier.testTag("library-message-dismiss"),
            tint = colors.textSecondary,
        )
    }
}

@Composable
private fun EmptyLibrary() {
    val colors = YftTheme.colors
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(colors.chipOnBackground),
        contentAlignment = Alignment.Center,
    ) {
        YftIcon(
            icon = YftIcons.Library,
            contentDescription = null,
            tint = colors.icon,
            size = 28.dp,
        )
    }
    Text(
        text = "No finished downloads yet",
        modifier = Modifier
            .padding(top = 16.dp)
            .semantics { heading() },
        color = colors.textPrimary,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
    Text(
        text = "Completed downloads saved to Download/YFT or to app storage appear here.",
        modifier = Modifier.padding(top = 6.dp),
        color = colors.textSecondary,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun DeleteDialog(item: LibraryItem, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = YftTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Delete this file?", color = colors.textPrimary) },
        text = {
            Text(
                text = "${item.displayName} will be removed from ${item.location.label}. " +
                    "This cannot be undone.",
                color = colors.textSecondary,
            )
        },
        confirmButton = {
            YftTextButton(
                text = "Delete",
                onClick = onConfirm,
                modifier = Modifier.testTag("library-delete-confirm"),
                color = colors.coralText,
            )
        },
        dismissButton = {
            YftTextButton(
                text = "Cancel",
                onClick = onDismiss,
                modifier = Modifier.testTag("library-delete-dismiss"),
            )
        },
        modifier = Modifier.testTag("library-delete-dialog"),
        containerColor = colors.card,
    )
}

@Composable
private fun CenteredStatus(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        content()
    }
}

private fun noMatchText(filter: LibraryFilter, query: String): String = when {
    query.isNotBlank() -> "Nothing matches \u201C${query.trim()}\u201D"
    filter == LibraryFilter.VIDEO -> "No videos yet"
    filter == LibraryFilter.AUDIO -> "No audio yet"
    else -> "Nothing to show"
}

private const val TILE_RATIO = 16f / 10f
private val GRID_GAP = 12.dp
private val GRID_ROW_GAP = 8.dp
private val MORE_BUTTON_OFFSET = 14.dp

/** Lifts the ⋯ button from the tile's bottom so its centre lines up with the title's. */
private val MORE_BUTTON_LIFT = (-11).dp

/** What the title and size leave for the visible ⋯ at the end of the line. */
private val TITLE_END_ROOM = 22.dp
