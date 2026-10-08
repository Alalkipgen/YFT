package com.alal.yft.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.alal.yft.download.BackgroundSystemState
import com.alal.yft.feature.downloads.BATTERY_CARD_TEXT
import com.alal.yft.feature.downloads.XIAOMI_STEPS
import com.alal.yft.ui.components.YftTextButton
import com.alal.yft.ui.theme.YftTheme

internal const val BACKGROUND_ALLOWED_TEXT =
    "YFT runs without battery limits, so downloads and merges keep going in the background."

/**
 * Settings › Downloads › Background downloads (P34): whether Android limits YFT's battery,
 * Allow while it does, and HyperOS's own steps on Xiaomi, Redmi and POCO phones.
 */
@Composable
internal fun BackgroundDownloadsDialog(
    system: BackgroundSystemState,
    onAllow: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = YftTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Background downloads", color = colors.textPrimary) },
        text = {
            Column {
                Text(
                    text = if (system.unrestricted) BACKGROUND_ALLOWED_TEXT else BATTERY_CARD_TEXT,
                    modifier = Modifier.testTag("settings-background-text"),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (system.xiaomi) {
                    Text(
                        text = XIAOMI_STEPS,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .testTag("settings-background-xiaomi"),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            if (system.unrestricted) {
                YftTextButton(
                    text = "Done",
                    onClick = onDismiss,
                    modifier = Modifier.testTag("settings-background-done"),
                )
            } else {
                YftTextButton(
                    text = "Allow",
                    onClick = {
                        onDismiss()
                        onAllow()
                    },
                    modifier = Modifier.testTag("settings-background-allow"),
                )
            }
        },
        dismissButton = {
            YftTextButton(
                text = "App settings",
                onClick = {
                    onDismiss()
                    onOpenAppSettings()
                },
                modifier = Modifier.testTag("settings-background-app-settings"),
                color = colors.textSecondary,
            )
        },
        modifier = Modifier.testTag("settings-background-dialog"),
        containerColor = colors.card,
    )
}
