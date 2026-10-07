package com.alal.yft.feature.settings

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alal.yft.BuildConfig
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.core.model.settings.DownloadLocation
import com.alal.yft.core.model.settings.DownloadPreferences
import com.alal.yft.core.model.settings.QualityPreference
import com.alal.yft.core.model.settings.SearchEngine
import com.alal.yft.ui.components.YftCard
import com.alal.yft.ui.components.YftDivider
import com.alal.yft.ui.components.YftGroupLabel
import com.alal.yft.ui.components.YftIcon
import com.alal.yft.ui.components.YftRadioMark
import com.alal.yft.ui.components.YftScreenHeader
import com.alal.yft.ui.components.YftSegmentedControl
import com.alal.yft.ui.components.YftStepper
import com.alal.yft.ui.components.YftSwitch
import com.alal.yft.ui.components.YftTextButton
import com.alal.yft.ui.theme.YftIcons
import com.alal.yft.ui.theme.YftTheme

@Composable
fun SettingsRoute(
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onOpenAbout: () -> Unit = {},
    onOpenLicenses: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        themeMode = themeMode,
        sharedDownloadsSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
        onAction = viewModel::onAction,
        onThemeModeChanged = onThemeModeChanged,
        onOpenAbout = onOpenAbout,
        onOpenLicenses = onOpenLicenses,
    )
}

/** The list a choice dialog offers; Save files to, Preferred quality and Search engine. */
private enum class SettingsPicker { LOCATION, QUALITY, SEARCH_ENGINE }

/**
 * The Settings tab (`06`): APPEARANCE, DOWNLOADS, PRIVACY and ABOUT cards with one row per
 * setting, and the "No ads · No tracking · No account" promise at the end. As a tab it has no
 * back arrow.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    themeMode: ThemeMode,
    sharedDownloadsSupported: Boolean,
    onAction: (SettingsAction) -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    versionName: String = BuildConfig.VERSION_NAME,
    onOpenAbout: () -> Unit = {},
    onOpenLicenses: () -> Unit = {},
) {
    val colors = YftTheme.colors
    val snackbar = remember { SnackbarHostState() }
    var picker by rememberSaveable { mutableStateOf<SettingsPicker?>(null) }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        onAction(SettingsAction.MessageShown)
    }
    // Android 9 and older cannot add files to the shared Downloads folder without a permission
    // YFT does not ask for, so they always save to app storage.
    val location = if (sharedDownloadsSupported) {
        state.download.location
    } else {
        DownloadLocation.APP_STORAGE
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .testTag("settings-list")
                .padding(bottom = 16.dp),
        ) {
            YftScreenHeader(title = "Settings")
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                SettingsGroup(title = "Appearance") {
                    ThemeRow(themeMode = themeMode, onThemeModeChanged = onThemeModeChanged)
                }
                SettingsGroup(title = "Downloads") {
                    DownloadRows(
                        preferences = state.download,
                        location = location,
                        onAction = onAction,
                        onPick = { picker = it },
                    )
                }
                SettingsGroup(title = "Browser") {
                    BrowserRows(state = state, onPick = { picker = it }, onAction = onAction)
                }
                SettingsGroup(title = "Privacy") {
                    PrivacyRows(state = state, onAction = onAction)
                }
                SettingsGroup(title = "About") {
                    ValueRow(
                        icon = YftIcons.Info,
                        title = "Version",
                        value = versionName,
                        tag = "settings-open-about",
                        onClickLabel = "Open About",
                        onClick = onOpenAbout,
                    )
                    RowDivider()
                    ValueRow(
                        icon = YftIcons.Document,
                        title = "Licenses",
                        tag = "settings-open-licenses",
                        onClick = onOpenLicenses,
                    )
                }
                Footer()
            }
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    when (picker) {
        SettingsPicker.LOCATION -> ChoiceDialog(
            title = "Save files to",
            description = null,
            options = DownloadLocation.entries,
            selected = location,
            label = { it.label() },
            summary = { it.summary(sharedDownloadsSupported) },
            isEnabled = { sharedDownloadsSupported || it != DownloadLocation.SHARED_DOWNLOADS },
            tag = { "location-${it.name}" },
            onSelect = {
                picker = null
                onAction(SettingsAction.SetLocation(it))
            },
            onDismiss = { picker = null },
            modifier = Modifier.testTag("location-dialog"),
        )

        SettingsPicker.QUALITY -> ChoiceDialog(
            title = "Preferred quality",
            description = "Download as starts on this quality when a page offers it.",
            options = QualityPreference.entries,
            selected = state.download.defaultQuality,
            label = { it.label() },
            summary = { null },
            tag = { "quality-${it.name}" },
            onSelect = {
                picker = null
                onAction(SettingsAction.SetQuality(it))
            },
            onDismiss = { picker = null },
            modifier = Modifier.testTag("quality-dialog"),
        )

        SettingsPicker.SEARCH_ENGINE -> ChoiceDialog(
            title = "Search engine",
            description = "Words typed in the browser search with this engine.",
            options = SearchEngine.entries,
            selected = state.browser.searchEngine,
            label = { it.displayName },
            summary = { null },
            tag = { "search-engine-${it.name}" },
            onSelect = {
                picker = null
                onAction(SettingsAction.SetSearchEngine(it))
            },
            onDismiss = { picker = null },
            modifier = Modifier.testTag("search-engine-dialog"),
        )

        null -> Unit
    }

    state.confirmation?.let { confirmation ->
        ConfirmationDialog(
            confirmation = confirmation,
            finishedDownloads = state.finishedDownloads,
            onConfirm = { onAction(SettingsAction.Confirm) },
            onDismiss = { onAction(SettingsAction.Dismiss) },
        )
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    YftGroupLabel(text = title)
    YftCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp),
        content = content,
    )
}

@Composable
private fun ThemeRow(themeMode: ThemeMode, onThemeModeChanged: (ThemeMode) -> Unit) {
    val colors = YftTheme.colors
    BesideOrBelow(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = THEME_ROW_MIN_HEIGHT)
            .padding(horizontal = ROW_PADDING),
        belowIndent = ICON_SIZE + ICON_GAP,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                YftIcon(
                    icon = YftIcons.Theme,
                    contentDescription = null,
                    tint = colors.icon,
                    size = ICON_SIZE,
                )
                Text(
                    text = "Theme",
                    modifier = Modifier.padding(
                        start = ICON_GAP,
                        end = 8.dp,
                        top = 12.dp,
                        bottom = 12.dp,
                    ),
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        control = {
            YftSegmentedControl(
                options = ThemeMode.entries,
                selected = themeMode,
                onSelect = onThemeModeChanged,
                label = { it.label() },
                fillWidth = false,
                testTag = { "theme-${it.name}" },
                compact = true,
            )
        },
    )
}

@Composable
private fun DownloadRows(
    preferences: DownloadPreferences,
    location: DownloadLocation,
    onAction: (SettingsAction) -> Unit,
    onPick: (SettingsPicker) -> Unit,
) {
    ValueRow(
        icon = YftIcons.Folder,
        title = "Save files to",
        value = location.label(),
        tag = "settings-location",
        onClick = { onPick(SettingsPicker.LOCATION) },
    )
    RowDivider()
    SwitchRow(
        icon = YftIcons.Wifi,
        title = "Download over Wi-Fi only",
        checked = preferences.unmeteredOnly,
        tag = "unmetered-only",
        onToggle = { onAction(SettingsAction.SetUnmeteredOnly(it)) },
    )
    RowDivider()
    // While downloads wait for Wi-Fi they never use mobile data, so the question is moot; the
    // switch keeps its own value for when Wi-Fi only is turned off again.
    SwitchRow(
        icon = YftIcons.CellTower,
        title = "Ask before using mobile data",
        checked = preferences.confirmOnMeteredNetwork,
        tag = "confirm-metered",
        enabled = !preferences.unmeteredOnly,
        supporting = if (preferences.unmeteredOnly) "Not needed while Wi-Fi only is on" else null,
        onToggle = { onAction(SettingsAction.SetConfirmMetered(it)) },
    )
    RowDivider()
    SettingsRow(
        icon = YftIcons.Stacks,
        title = "Downloads at the same time",
        // The stepper draws its pill inside its touch targets, so less end padding lines the
        // pill up with the switches above.
        endPadding = ROW_PADDING - 6.dp,
    ) {
        YftStepper(
            value = preferences.maxConcurrentDownloads,
            range = DownloadPreferences.CONCURRENT_DOWNLOAD_RANGE,
            onValueChange = { onAction(SettingsAction.SetConcurrency(it)) },
            valueDescription = "${preferences.maxConcurrentDownloads} at the same time",
            decrementLabel = "Fewer downloads at the same time",
            incrementLabel = "More downloads at the same time",
            testTagPrefix = "concurrency",
        )
    }
    RowDivider()
    ValueRow(
        icon = YftIcons.Star,
        title = "Preferred quality",
        value = preferences.defaultQuality.label(),
        tag = "settings-quality",
        onClick = { onPick(SettingsPicker.QUALITY) },
    )
}

/**
 * Settings › Browser: the engine typed words search with (P30), the history (P31) and the
 * pop-up blocking (P32).
 */
@Composable
private fun BrowserRows(
    state: SettingsUiState,
    onPick: (SettingsPicker) -> Unit,
    onAction: (SettingsAction) -> Unit,
) {
    ValueRow(
        icon = YftIcons.Search,
        title = "Search engine",
        value = state.browser.searchEngine.displayName,
        tag = "settings-search-engine",
        onClick = { onPick(SettingsPicker.SEARCH_ENGINE) },
    )
    RowDivider()
    SwitchRow(
        icon = YftIcons.History,
        title = "Save browser history",
        checked = state.browser.saveHistory,
        tag = "settings-save-history",
        supporting = "Pages you open in the browser are listed in its History.",
        onToggle = { onAction(SettingsAction.SetSaveHistory(it)) },
    )
    RowDivider()
    ValueRow(
        icon = YftIcons.Delete,
        title = "Clear browser history",
        tag = "settings-clear-history",
        enabled = !state.working,
        onClick = { onAction(SettingsAction.Request(SettingsConfirmation.CLEAR_BROWSER_HISTORY)) },
    )
    RowDivider()
    SwitchRow(
        icon = YftIcons.Block,
        title = "Block pop-ups and ad redirects",
        checked = state.browser.blockPopups,
        tag = "settings-block-popups",
        supporting = "Pages can't open new windows or send you to another site you didn't tap.",
        onToggle = { onAction(SettingsAction.SetBlockPopups(it)) },
    )
}

@Composable
private fun PrivacyRows(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    SwitchRow(
        icon = YftIcons.Paste,
        title = "Check copied links when YFT opens",
        checked = state.checkCopiedLinks,
        tag = "check-copied-links",
        supporting = "Android shows a short \"pasted\" message when YFT reads a copied link.",
        onToggle = { onAction(SettingsAction.SetCheckCopiedLinks(it)) },
    )
    RowDivider()
    ValueRow(
        icon = YftIcons.Delete,
        title = "Clear browsing data",
        tag = "clear-browsing-data",
        enabled = !state.working,
        onClick = { onAction(SettingsAction.Request(SettingsConfirmation.CLEAR_BROWSING_DATA)) },
    )
    RowDivider()
    ValueRow(
        icon = YftIcons.History,
        title = "Clear download history",
        tag = "clear-download-history",
        enabled = !state.working && state.finishedDownloads > 0,
        supporting = if (state.finishedDownloads == 0) NOTHING_TO_CLEAR else null,
        onClick = {
            onAction(SettingsAction.Request(SettingsConfirmation.CLEAR_DOWNLOAD_HISTORY))
        },
    )
}

/** Leading icon, title (and an optional grey line under it) and a trailing control. */
@Composable
private fun SettingsRow(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supporting: String? = null,
    endPadding: Dp = ROW_PADDING,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = YftTheme.colors
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ROW_MIN_HEIGHT)
            .padding(start = ROW_PADDING, end = endPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YftIcon(
            icon = icon,
            contentDescription = null,
            tint = colors.icon.copy(alpha = alpha),
            size = ICON_SIZE,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = ICON_GAP, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                color = colors.textPrimary.copy(alpha = alpha),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        trailing()
    }
}

/** A row that opens something: its current value, if any, and a chevron. */
@Composable
private fun ValueRow(
    @DrawableRes icon: Int,
    title: String,
    tag: String,
    onClick: () -> Unit,
    value: String? = null,
    enabled: Boolean = true,
    supporting: String? = null,
    onClickLabel: String? = null,
) {
    val colors = YftTheme.colors
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    SettingsRow(
        icon = icon,
        title = title,
        modifier = Modifier
            .clickable(
                enabled = enabled,
                onClickLabel = onClickLabel,
                role = Role.Button,
                onClick = onClick,
            )
            .testTag(tag),
        enabled = enabled,
        supporting = supporting,
        // The chevron glyph sits inside its box, so a smaller end padding lines it up with the
        // switches.
        endPadding = ROW_PADDING - 6.dp,
    ) {
        if (value != null) {
            Text(
                text = value,
                modifier = Modifier
                    .widthIn(max = VALUE_MAX_WIDTH)
                    .padding(end = 2.dp),
                color = colors.textPrimary.copy(alpha = alpha),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
            )
        }
        YftIcon(
            icon = YftIcons.ChevronRight,
            contentDescription = null,
            tint = colors.textSecondary.copy(alpha = alpha),
            size = ICON_SIZE,
        )
    }
}

@Composable
private fun SwitchRow(
    @DrawableRes icon: Int,
    title: String,
    checked: Boolean,
    tag: String,
    onToggle: (Boolean) -> Unit,
    enabled: Boolean = true,
    supporting: String? = null,
) {
    SettingsRow(
        icon = icon,
        title = title,
        modifier = Modifier
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onToggle,
            )
            .testTag(tag),
        enabled = enabled,
        supporting = supporting,
    ) {
        YftSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Divider between rows, starting under the titles as drawn. */
@Composable
private fun RowDivider() {
    YftDivider(modifier = Modifier.padding(start = ROW_PADDING + ICON_SIZE + ICON_GAP))
}

/**
 * Puts [control] beside [label] when both fit on one line and under it (lined up with the
 * label's text) when they do not, e.g. with large fonts on a narrow phone.
 */
@Composable
private fun BesideOrBelow(
    label: @Composable () -> Unit,
    control: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    belowIndent: Dp = 0.dp,
) {
    Layout(
        content = {
            label()
            control()
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val (labelPart, controlPart) = measurables
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
        val beside = !constraints.hasBoundedWidth ||
            labelPart.maxIntrinsicWidth(Constraints.Infinity) +
            controlPart.maxIntrinsicWidth(Constraints.Infinity) <= width
        if (beside) {
            val control = controlPart.measure(loose)
            val label = labelPart.measure(
                loose.copy(maxWidth = (loose.maxWidth - control.width).coerceAtLeast(0)),
            )
            val layoutWidth = if (constraints.hasBoundedWidth) {
                width
            } else {
                label.width + control.width
            }
            val height = maxOf(label.height, control.height, constraints.minHeight)
            layout(layoutWidth, height) {
                label.placeRelative(0, (height - label.height) / 2)
                control.placeRelative(layoutWidth - control.width, (height - control.height) / 2)
            }
        } else {
            val indent = belowIndent.roundToPx()
            val label = labelPart.measure(loose)
            val control = controlPart.measure(
                loose.copy(maxWidth = (width - indent).coerceAtLeast(0)),
            )
            val height = maxOf(label.height + control.height, constraints.minHeight)
            layout(width, height) {
                label.placeRelative(0, 0)
                control.placeRelative(indent, label.height)
            }
        }
    }
}

@Composable
private fun Footer() {
    val colors = YftTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp)
            .semantics(mergeDescendants = true) {}
            .testTag("settings-footer"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YftIcon(
            icon = YftIcons.Shield,
            contentDescription = null,
            tint = colors.textSecondary,
            size = 14.dp,
        )
        Text(
            text = "No ads · No tracking · No account",
            modifier = Modifier.padding(start = 6.dp),
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** A single choice that applies as soon as it is tapped, like the platform's list settings. */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    description: String?,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    summary: (T) -> String?,
    tag: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: (T) -> Boolean = { true },
) {
    val colors = YftTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            YftTextButton(
                text = "Cancel",
                onClick = onDismiss,
                modifier = Modifier.testTag("choice-cancel"),
            )
        },
        modifier = modifier,
        title = { Text(text = title, color = colors.textPrimary) },
        text = {
            Column {
                if (description != null) {
                    Text(
                        text = description,
                        modifier = Modifier.padding(bottom = 8.dp),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Column(modifier = Modifier.selectableGroup()) {
                    options.forEach { option ->
                        ChoiceRow(
                            title = label(option),
                            summary = summary(option),
                            selected = option == selected,
                            enabled = isEnabled(option),
                            tag = tag(option),
                            onSelect = { onSelect(option) },
                        )
                    }
                }
            }
        },
        containerColor = colors.card,
    )
}

@Composable
private fun ChoiceRow(
    title: String,
    summary: String?,
    selected: Boolean,
    enabled: Boolean,
    tag: String,
    onSelect: () -> Unit,
) {
    val colors = YftTheme.colors
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .testTag(tag)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        YftRadioMark(selected = selected, modifier = Modifier.alpha(alpha))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                color = colors.textPrimary.copy(alpha = alpha),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ConfirmationDialog(
    confirmation: SettingsConfirmation,
    finishedDownloads: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = YftTheme.colors
    val (title, body) = when (confirmation) {
        SettingsConfirmation.CLEAR_BROWSING_DATA ->
            "Clear browsing data?" to "Removes cookies, site storage, the cache, saved " +
                "sign-ins, the browser's history and the found media list. You will be " +
                "signed out of every site opened in YFT. Downloads and settings are not affected."

        SettingsConfirmation.CLEAR_BROWSER_HISTORY ->
            "Clear browser history?" to "Removes every page from the browser's history. " +
                "Cookies, sign-ins and downloads are not affected."

        SettingsConfirmation.CLEAR_DOWNLOAD_HISTORY ->
            "Clear download history?" to "Removes $finishedDownloads finished " +
                "${if (finishedDownloads == 1) "entry" else "entries"} from Downloads. " +
                "Files you already saved stay on the device."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, color = colors.textPrimary) },
        text = { Text(text = body, color = colors.textSecondary) },
        confirmButton = {
            YftTextButton(
                text = "Clear",
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm-action"),
                color = colors.coralText,
            )
        },
        dismissButton = {
            YftTextButton(
                text = "Cancel",
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss-action"),
            )
        },
        modifier = Modifier.testTag("confirm-dialog"),
        containerColor = colors.card,
    )
}

private val ROW_MIN_HEIGHT = 48.dp
private val THEME_ROW_MIN_HEIGHT = 56.dp
private val ROW_PADDING = 12.dp
private val ICON_SIZE = 20.dp
private val ICON_GAP = 12.dp
private val VALUE_MAX_WIDTH = 160.dp
private const val DISABLED_ALPHA = 0.38f
private const val NOTHING_TO_CLEAR = "No finished downloads in the list"

internal fun QualityPreference.label(): String = when (this) {
    QualityPreference.HIGHEST -> "Highest available"
    QualityPreference.UP_TO_1080P -> "Up to 1080p"
    QualityPreference.UP_TO_720P -> "Up to 720p"
    QualityPreference.UP_TO_480P -> "Up to 480p"
    QualityPreference.LOWEST -> "Smallest file"
}

internal fun DownloadLocation.label(): String = when (this) {
    DownloadLocation.SHARED_DOWNLOADS -> "Download/YFT"
    DownloadLocation.APP_STORAGE -> "App storage"
}

private fun DownloadLocation.summary(sharedDownloadsSupported: Boolean): String = when (this) {
    DownloadLocation.SHARED_DOWNLOADS -> if (sharedDownloadsSupported) {
        "The Downloads folder, where your file manager and players find the files."
    } else {
        "Needs Android 10 or newer. This device saves to app storage."
    }

    DownloadLocation.APP_STORAGE -> "Only YFT can open these files. They are removed with the app."
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}
