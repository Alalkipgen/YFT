package com.alal.yft.feature.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alal.yft.core.data.history.BrowserHistoryEntry
import com.alal.yft.ui.components.YftDivider
import com.alal.yft.ui.components.YftIcon
import com.alal.yft.ui.components.YftIconButton
import com.alal.yft.ui.components.YftTextButton
import com.alal.yft.ui.theme.YftIcons
import com.alal.yft.ui.theme.YftShapes
import com.alal.yft.ui.theme.YftTheme
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** One day group of the History list (P31). */
internal data class HistoryGroup(val title: String, val pages: List<BrowserHistoryEntry>)

/** Today, Yesterday and Earlier by the phone's calendar days; pages stay newest first. */
internal fun historyGroups(
    pages: List<BrowserHistoryEntry>,
    nowEpochMs: Long,
    zone: TimeZone = TimeZone.getDefault(),
): List<HistoryGroup> {
    val today = startOfDay(nowEpochMs, zone)
    val yesterday = startOfDay(today - 1, zone)
    val byGroup = pages.sortedByDescending(BrowserHistoryEntry::lastVisitedAtEpochMs).groupBy {
        when {
            it.lastVisitedAtEpochMs >= today -> TODAY
            it.lastVisitedAtEpochMs >= yesterday -> YESTERDAY
            else -> EARLIER
        }
    }
    return listOf(TODAY, YESTERDAY, EARLIER).mapNotNull { title ->
        byGroup[title]?.let { HistoryGroup(title, it) }
    }
}

/** "14:05" for today and yesterday, else "3 Oct" (and the year when it is not this one). */
internal fun historyTime(
    visitedAtEpochMs: Long,
    nowEpochMs: Long,
    zone: TimeZone = TimeZone.getDefault(),
): String {
    val visited = Calendar.getInstance(zone, Locale.US).apply { timeInMillis = visitedAtEpochMs }
    if (visitedAtEpochMs >= startOfDay(startOfDay(nowEpochMs, zone) - 1, zone)) {
        return String.format(
            Locale.US,
            "%02d:%02d",
            visited.get(Calendar.HOUR_OF_DAY),
            visited.get(Calendar.MINUTE),
        )
    }
    val now = Calendar.getInstance(zone, Locale.US).apply { timeInMillis = nowEpochMs }
    val day = "${visited.get(Calendar.DAY_OF_MONTH)} ${MONTHS[visited.get(Calendar.MONTH)]}"
    val year = visited.get(Calendar.YEAR)
    return if (year == now.get(Calendar.YEAR)) day else "$day $year"
}

private fun startOfDay(epochMs: Long, zone: TimeZone): Long =
    Calendar.getInstance(zone, Locale.US).apply {
        timeInMillis = epochMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private const val TODAY = "Today"
private const val YESTERDAY = "Yesterday"
private const val EARLIER = "Earlier"
private val MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/**
 * The browser's History (P31), over the whole browser screen: a search box, the pages by day
 * (a tap opens one, its menu deletes it) and "Clear history" after a confirmation. Back closes
 * it. Composed after the browser, so its Back is handled first.
 */
@Composable
internal fun BrowserHistoryPanel(
    state: BrowserHistoryUiState,
    nowEpochMs: Long,
    onQueryChanged: (String) -> Unit,
    onOpen: (BrowserHistoryEntry) -> Unit,
    onDelete: (BrowserHistoryEntry) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YftTheme.colors
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onClose)
    val groups = remember(state.pages, nowEpochMs) { historyGroups(state.pages, nowEpochMs) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(SIDES))
            .testTag("browser-history"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YftIconButton(
                icon = YftIcons.ArrowBack,
                contentDescription = "Close history",
                onClick = onClose,
                modifier = Modifier.testTag("browser-history-close"),
            )
            Text(
                text = "History",
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
                    .semantics { heading() },
            )
            YftTextButton(
                text = "Clear history",
                onClick = { confirmClear = true },
                enabled = state.pages.isNotEmpty() || state.query.isNotEmpty(),
                modifier = Modifier.testTag("browser-history-clear"),
                color = colors.coralText,
            )
        }
        HistorySearchField(query = state.query, onQueryChanged = onQueryChanged)
        if (!state.saving) {
            Text(
                text = "Saving history is off in Settings › Browser.",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .testTag("browser-history-off"),
            )
        }
        if (groups.isEmpty()) {
            Text(
                text = if (state.query.isBlank()) {
                    "Pages you open in the browser appear here."
                } else {
                    "No pages match “${state.query.trim()}”."
                },
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .testTag("browser-history-empty"),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("browser-history-list"),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                groups.forEach { group ->
                    item(key = "group-${group.title}") {
                        Text(
                            text = group.title,
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier
                                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)
                                .semantics { heading() },
                        )
                    }
                    items(group.pages, key = { it.url }) { page ->
                        HistoryRow(
                            page = page,
                            time = historyTime(page.lastVisitedAtEpochMs, nowEpochMs),
                            onOpen = { onOpen(page) },
                            onDelete = { onDelete(page) },
                        )
                    }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(text = "Clear history?", color = colors.textPrimary) },
            text = {
                Text(
                    text = "Removes every page from the browser's history. Cookies, sign-ins " +
                        "and downloads are not affected.",
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                YftTextButton(
                    text = "Clear",
                    onClick = {
                        confirmClear = false
                        onClear()
                    },
                    modifier = Modifier.testTag("browser-history-clear-confirm"),
                    color = colors.coralText,
                )
            },
            dismissButton = {
                YftTextButton(
                    text = "Cancel",
                    onClick = { confirmClear = false },
                    modifier = Modifier.testTag("browser-history-clear-cancel"),
                )
            },
            modifier = Modifier.testTag("browser-history-clear-dialog"),
            containerColor = colors.card,
        )
    }
}

@Composable
private fun HistorySearchField(query: String, onQueryChanged: (String) -> Unit) {
    val colors = YftTheme.colors
    val focusManager = LocalFocusManager.current
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(YftShapes.pill)
            .background(colors.chipOnBackground)
            .padding(start = 14.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YftIcon(icon = YftIcons.Search, contentDescription = null, tint = colors.icon, size = 20.dp)
        Box(
            modifier = Modifier.weight(1f).padding(start = 10.dp, end = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) {
                Text(
                    text = SEARCH_PLACEHOLDER,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = SEARCH_PLACEHOLDER }
                    .testTag("browser-history-search"),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            )
        }
        if (query.isNotEmpty()) {
            YftIconButton(
                icon = YftIcons.Close,
                contentDescription = "Clear search",
                onClick = { onQueryChanged("") },
                modifier = Modifier.testTag("browser-history-search-clear"),
                tint = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun HistoryRow(
    page: BrowserHistoryEntry,
    time: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = YftTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
            .testTag("browser-history-page-${page.url}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YftIcon(icon = YftIcons.Globe, contentDescription = null, tint = colors.icon, size = 20.dp)
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                text = page.title,
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${page.host} · $time",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            YftIconButton(
                icon = YftIcons.MoreHoriz,
                contentDescription = "More for ${page.title}",
                onClick = { menuOpen = true },
                modifier = Modifier.testTag("browser-history-menu-${page.url}"),
                tint = colors.textSecondary,
            )
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                shape = YftShapes.card,
                containerColor = colors.card,
            ) {
                DropdownMenuItem(
                    text = { Text(text = "Delete", color = colors.textPrimary) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                    modifier = Modifier.testTag("browser-history-delete"),
                    leadingIcon = {
                        YftIcon(
                            icon = YftIcons.Delete,
                            contentDescription = null,
                            tint = colors.icon,
                        )
                    },
                )
            }
        }
    }
    YftDivider(modifier = Modifier.padding(start = 54.dp))
}

private const val SEARCH_PLACEHOLDER = "Search history"
private val SIDES =
    WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
