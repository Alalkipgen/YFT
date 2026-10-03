package com.alal.yft.feature.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.core.model.settings.DownloadLocation
import com.alal.yft.core.model.settings.DownloadPreferences
import com.alal.yft.core.model.settings.QualityPreference
import com.alal.yft.ui.components.YftTopBar

@Composable
fun SettingsRoute(
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenAbout: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        themeMode = themeMode,
        sharedDownloadsSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
        onAction = viewModel::onAction,
        onThemeModeChanged = onThemeModeChanged,
        onNavigateBack = onNavigateBack,
        onOpenAbout = onOpenAbout,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    themeMode: ThemeMode,
    sharedDownloadsSupported: Boolean,
    onAction: (SettingsAction) -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenAbout: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        onAction(SettingsAction.MessageShown)
    }

    Scaffold(
        topBar = {
            YftTopBar(
                title = "Settings",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .testTag("settings-list")
                .padding(vertical = 8.dp),
        ) {
            DownloadSection(state.download, sharedDownloadsSupported, onAction)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            AppearanceSection(themeMode, onThemeModeChanged)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            PrivacySection(state, onAction)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader("About")
            ActionRow(
                title = "About YFT",
                summary = "Version, privacy, product scope and open-source licenses.",
                actionLabel = "Open",
                enabled = true,
                tag = "settings-open-about",
                onClick = onOpenAbout,
            )
        }
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
private fun DownloadSection(
    preferences: DownloadPreferences,
    sharedDownloadsSupported: Boolean,
    onAction: (SettingsAction) -> Unit,
) {
    SectionHeader("Downloads")
    GroupLabel("Quality selected first in Preview")
    Column(modifier = Modifier.selectableGroup()) {
        QualityPreference.entries.forEach { quality ->
            ChoiceRow(
                title = quality.label(),
                summary = null,
                selected = preferences.defaultQuality == quality,
                enabled = true,
                tag = "quality-${quality.name}",
                onSelect = { onAction(SettingsAction.SetQuality(quality)) },
            )
        }
    }

    GroupLabel("Save finished files to")
    Column(modifier = Modifier.selectableGroup()) {
        val effective = if (sharedDownloadsSupported) {
            preferences.location
        } else {
            DownloadLocation.APP_STORAGE
        }
        DownloadLocation.entries.forEach { location ->
            val available = sharedDownloadsSupported ||
                location != DownloadLocation.SHARED_DOWNLOADS
            ChoiceRow(
                title = location.label(),
                summary = if (available) {
                    location.summary()
                } else {
                    "Needs Android 10 or newer. This device saves to app storage."
                },
                selected = effective == location,
                enabled = available,
                tag = "location-${location.name}",
                onSelect = { onAction(SettingsAction.SetLocation(location)) },
            )
        }
    }

    ToggleRow(
        title = "Download over Wi-Fi only",
        summary = "Transfers wait for Wi-Fi or another unmetered network.",
        checked = preferences.unmeteredOnly,
        enabled = true,
        tag = "unmetered-only",
        onToggle = { onAction(SettingsAction.SetUnmeteredOnly(it)) },
    )
    ToggleRow(
        title = "Ask before using mobile data",
        summary = if (preferences.unmeteredOnly) {
            "Not needed while downloads wait for Wi-Fi."
        } else {
            "Confirm each download that starts on a metered network."
        },
        checked = preferences.confirmOnMeteredNetwork && !preferences.unmeteredOnly,
        enabled = !preferences.unmeteredOnly,
        tag = "confirm-metered",
        onToggle = { onAction(SettingsAction.SetConfirmMetered(it)) },
    )

    GroupLabel("Downloads at the same time")
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DownloadPreferences.CONCURRENT_DOWNLOAD_RANGE.forEach { count ->
            FilterChip(
                selected = preferences.maxConcurrentDownloads == count,
                onClick = { onAction(SettingsAction.SetConcurrency(count)) },
                label = { Text(text = count.toString()) },
                modifier = Modifier.testTag("concurrency-$count"),
            )
        }
    }
}

@Composable
private fun AppearanceSection(
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
) {
    SectionHeader("Appearance")
    Column(modifier = Modifier.selectableGroup()) {
        ThemeMode.entries.forEach { mode ->
            ChoiceRow(
                title = mode.label(),
                summary = null,
                selected = themeMode == mode,
                enabled = true,
                tag = "theme-${mode.name}",
                onSelect = { onThemeModeChanged(mode) },
            )
        }
    }
}

@Composable
private fun PrivacySection(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    SectionHeader("Privacy")
    Text(
        text = "YFT has no ads, analytics, accounts or crash reporting. It only contacts the " +
            "sites you open and the media servers they point to.",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ActionRow(
        title = "Clear browsing data",
        summary = "Cookies, site storage, cache, saved sign-ins and the detected media list.",
        actionLabel = "Clear",
        enabled = !state.working,
        tag = "clear-browsing-data",
        onClick = { onAction(SettingsAction.Request(SettingsConfirmation.CLEAR_BROWSING_DATA)) },
    )
    ActionRow(
        title = "Clear download history",
        summary = when (state.finishedDownloads) {
            0 -> "No finished downloads in the list."
            1 -> "1 finished download in the list. Saved files are kept."
            else -> "${state.finishedDownloads} finished downloads in the list. " +
                "Saved files are kept."
        },
        actionLabel = "Clear",
        enabled = !state.working && state.finishedDownloads > 0,
        tag = "clear-download-history",
        onClick = {
            onAction(SettingsAction.Request(SettingsConfirmation.CLEAR_DOWNLOAD_HISTORY))
        },
    )
}

@Composable
private fun ConfirmationDialog(
    confirmation: SettingsConfirmation,
    finishedDownloads: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val (title, body) = when (confirmation) {
        SettingsConfirmation.CLEAR_BROWSING_DATA ->
            "Clear browsing data?" to "You will be signed out of every site opened in YFT. " +
                "Downloads and settings are not affected."

        SettingsConfirmation.CLEAR_DOWNLOAD_HISTORY ->
            "Clear download history?" to "Removes $finishedDownloads finished " +
                "${if (finishedDownloads == 1) "entry" else "entries"} from Downloads. " +
                "Files you already saved stay on the device."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = body) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm-action")) {
                Text(text = "Clear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("dismiss-action")) {
                Text(text = "Cancel")
            }
        },
        modifier = Modifier.testTag("confirm-dialog"),
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .testTag(tag)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        RowText(title = title, summary = summary, enabled = enabled)
    }
}

@Composable
private fun ToggleRow(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    tag: String,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onToggle,
            )
            .testTag(tag)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            RowText(title = title, summary = summary, enabled = enabled)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun ActionRow(
    title: String,
    summary: String,
    actionLabel: String,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            RowText(title = title, summary = summary, enabled = true)
        }
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.testTag(tag),
        ) {
            Text(text = actionLabel)
        }
    }
}

@Composable
private fun RowText(title: String, summary: String?, enabled: Boolean) {
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        )
        if (summary != null) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
    }
}

private const val DISABLED_ALPHA = 0.38f

internal fun QualityPreference.label(): String = when (this) {
    QualityPreference.HIGHEST -> "Highest available"
    QualityPreference.UP_TO_1080P -> "Up to 1080p"
    QualityPreference.UP_TO_720P -> "Up to 720p"
    QualityPreference.UP_TO_480P -> "Up to 480p"
    QualityPreference.LOWEST -> "Smallest file"
}

internal fun DownloadLocation.label(): String = when (this) {
    DownloadLocation.SHARED_DOWNLOADS -> "Downloads folder"
    DownloadLocation.APP_STORAGE -> "App storage"
}

private fun DownloadLocation.summary(): String = when (this) {
    DownloadLocation.SHARED_DOWNLOADS -> "Download/YFT, visible to your file manager and players."
    DownloadLocation.APP_STORAGE -> "Only YFT can open these files. They are removed with the app."
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "Follow system"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}
