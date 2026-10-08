package com.alal.yft.feature.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.alal.yft.ui.components.YftCard
import com.alal.yft.ui.components.YftIcon
import com.alal.yft.ui.components.YftOutlinedButton
import com.alal.yft.ui.components.YftTextButton
import com.alal.yft.ui.theme.YftIcons
import com.alal.yft.ui.theme.YftTheme

/** "Turn on notifications to see download progress outside YFT", with Turn on and Dismiss. */
@Composable
internal fun NotificationsCard(
    onTurnOn: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YftTheme.colors
    YftCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag(NOTIFICATIONS_CARD_TAG),
        contentPadding = PaddingValues(start = 14.dp, top = 10.dp, end = 6.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            YftIcon(
                icon = YftIcons.Info,
                contentDescription = null,
                tint = colors.icon,
                size = 18.dp,
            )
            Text(
                text = NOTIFICATIONS_CARD_TEXT,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
                color = colors.textPrimary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            YftTextButton(
                text = "Dismiss",
                onClick = onDismiss,
                modifier = Modifier.testTag("$NOTIFICATIONS_CARD_TAG-dismiss"),
                color = colors.textSecondary,
            )
            YftTextButton(
                text = "Turn on",
                onClick = onTurnOn,
                modifier = Modifier.testTag("$NOTIFICATIONS_CARD_TAG-open"),
            )
        }
    }
}

/**
 * "Downloads and merges may stop when YFT is in the background. Allow YFT to run without battery
 * limits.", after a freeze with how long the phone paused YFT, and HyperOS's steps on Xiaomi.
 */
@Composable
internal fun BatteryCard(
    card: BatteryCardUiState,
    onAllow: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YftTheme.colors
    YftCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag(BATTERY_CARD_TAG),
        contentPadding = PaddingValues(start = 14.dp, top = 12.dp, end = 6.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            YftIcon(
                icon = YftIcons.Warning,
                contentDescription = null,
                tint = colors.icon,
                size = 18.dp,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                card.freezeMessage?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier.testTag("$BATTERY_CARD_TAG-freeze"),
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Text(
                    text = BATTERY_CARD_TEXT,
                    color = if (card.freezeMessage == null) {
                        colors.textPrimary
                    } else {
                        colors.textSecondary
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                if (card.xiaomiSteps) {
                    Text(
                        text = XIAOMI_STEPS,
                        modifier = Modifier.testTag("$BATTERY_CARD_TAG-xiaomi"),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YftTextButton(
                text = "Not now",
                onClick = onNotNow,
                modifier = Modifier.testTag("$BATTERY_CARD_TAG-not-now"),
                color = colors.textSecondary,
            )
            if (card.canAllow) {
                YftOutlinedButton(
                    text = "Allow",
                    onClick = onAllow,
                    modifier = Modifier.testTag("$BATTERY_CARD_TAG-allow"),
                    compact = true,
                )
            } else {
                // Battery limits are already off, yet the phone froze YFT: its own settings.
                YftTextButton(
                    text = "App settings",
                    onClick = onOpenAppSettings,
                    modifier = Modifier.testTag("$BATTERY_CARD_TAG-app-settings"),
                )
            }
        }
    }
}

internal const val NOTIFICATIONS_CARD_TAG = "downloads-notifications-card"
internal const val BATTERY_CARD_TAG = "downloads-battery-card"
