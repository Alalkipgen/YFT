package com.alal.yft.feature.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alal.yft.core.model.settings.HomeSite
import com.alal.yft.feature.home.SiteTile
import com.alal.yft.ui.components.YftCard
import com.alal.yft.ui.components.YftTonalButton
import com.alal.yft.ui.theme.YftIcons
import com.alal.yft.ui.theme.YftTheme

/** No native browser, clipboard preview or automatic lookup exists on this start page. */
@Composable
internal fun BrowserStartPage(
    sites: List<HomeSite>,
    copiedLinkHint: Boolean,
    onUseCopiedLink: () -> Unit,
    onOpenSite: (HomeSite) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YftTheme.colors
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("browser-start"),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            text = "Start browsing",
            color = colors.textPrimary,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        YftCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Link you copied",
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (copiedLinkHint) {
                    "Open your copied link to look for media."
                } else {
                    "Paste a link or enter an address above."
                },
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            YftTonalButton(
                text = if (copiedLinkHint) "Use copied link" else "Paste",
                onClick = onUseCopiedLink,
                icon = YftIcons.Paste,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("browser-use-copied-link"),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Your sites",
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            if (sites.isEmpty()) {
                Text(
                    text = "Add shortcuts on Home to see them here.",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    items(items = sites, key = HomeSite::url) { site ->
                        SiteTile(
                            site = site,
                            editing = false,
                            onOpen = { onOpenSite(site) },
                            onRemove = {},
                            tag = "browser-site-${site.url}",
                        )
                    }
                }
            }
        }
        Text(
            text = "Media YFT can save appears here as the page loads. Manage shortcuts on Home.",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}