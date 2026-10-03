package com.alal.yft.ui.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alal.yft.ui.components.YftCountBadge
import com.alal.yft.ui.components.YftDivider
import com.alal.yft.ui.components.YftIcon
import com.alal.yft.ui.theme.YftIcons
import com.alal.yft.ui.theme.YftTheme

/**
 * The four-tab bar from the design: white (Night card in dark) with a hairline on top, a Mint
 * pill behind the selected tab's filled icon and a Coral count of moving downloads.
 */
@Composable
fun YftBottomBar(
    selected: YftDestination,
    activeDownloads: Int,
    onSelect: (YftDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YftTheme.colors
    Column(modifier = modifier) {
        YftDivider()
        NavigationBar(
            containerColor = colors.card,
            contentColor = colors.textPrimary,
            tonalElevation = 0.dp,
        ) {
            YftDestination.topLevel.forEach { destination ->
                val isSelected = destination == selected
                val badge = if (destination == YftDestination.DOWNLOADS) activeDownloads else 0
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onSelect(destination) },
                    icon = {
                        BadgedBox(badge = { YftCountBadge(count = badge, minSize = 16.dp) }) {
                            YftIcon(
                                icon = destination.tabIcon(isSelected),
                                contentDescription = null,
                            )
                        }
                    },
                    label = {
                        Text(
                            text = destination.title,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.onNavIndicator,
                        selectedTextColor = colors.textPrimary,
                        indicatorColor = colors.navIndicator,
                        unselectedIconColor = colors.textPrimary,
                        unselectedTextColor = colors.textPrimary,
                    ),
                    // The icon (and so the badge) is hidden from accessibility because the label
                    // already names the tab; the count is announced as the tab's state instead.
                    modifier = Modifier
                        .testTag("nav-${destination.route}")
                        .semantics {
                            if (badge > 0) stateDescription = activeDownloadsDescription(badge)
                        },
                )
            }
        }
    }
}

internal fun activeDownloadsDescription(count: Int): String =
    if (count == 1) "1 active download" else "$count active downloads"

@DrawableRes
private fun YftDestination.tabIcon(selected: Boolean): Int = when (this) {
    YftDestination.HOME -> if (selected) YftIcons.HomeFilled else YftIcons.Home
    YftDestination.DOWNLOADS -> if (selected) YftIcons.DownloadFilled else YftIcons.Download
    YftDestination.LIBRARY -> if (selected) YftIcons.LibraryFilled else YftIcons.Library
    YftDestination.SETTINGS -> if (selected) YftIcons.SettingsFilled else YftIcons.Settings
    else -> error("$this is not a bottom-bar tab")
}
