package com.alal.yft.feature.browser

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alal.yft.core.data.history.BrowserHistoryEntry
import com.alal.yft.core.model.settings.HomeSite
import com.alal.yft.core.model.settings.SearchEngine
import com.alal.yft.feature.home.SiteTile
import com.alal.yft.ui.components.YftCard
import com.alal.yft.ui.components.YftDivider
import com.alal.yft.ui.components.YftIcon
import com.alal.yft.ui.components.YftPrimaryButton
import com.alal.yft.ui.components.YftSectionHeader
import com.alal.yft.ui.components.YftTextButton
import com.alal.yft.ui.components.YftTonalButton
import com.alal.yft.ui.theme.YftIcons
import com.alal.yft.ui.theme.YftTheme

/** The sites "View sites" always offers, each drawn with its bundled logo (T09). */
internal val VIEW_SITES: List<HomeSite> = listOf(
    HomeSite("YouTube", "https://m.youtube.com"),
    HomeSite("Facebook", "https://m.facebook.com"),
    HomeSite("TikTok", "https://www.tiktok.com"),
    HomeSite("Instagram", "https://www.instagram.com"),
    HomeSite("X", "https://x.com"),
)

/**
 * "Search to download" (T13), the browser's start page. Words in the address field offer a
 * YouTube search and one with [searchEngine], the engine Settings › Browser chose (P30); a link
 * opens as before. The clipboard is read only when Download or
 * Use copied link is tapped. View sites lists the popular sites, and View all the full Your
 * sites list, with Add or edit going to Home where sites are managed.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun BrowserStartPage(
    query: String,
    sites: List<HomeSite>,
    copiedLinkHint: Boolean,
    onSearch: (url: String) -> Unit,
    onDownloadCopiedLink: () -> Unit,
    onUseCopiedLink: () -> Unit,
    onOpenSite: (HomeSite) -> Unit,
    onEditSites: () -> Unit,
    modifier: Modifier = Modifier,
    searchEngine: SearchEngine = BrowserSearch.engine,
    recentPages: List<BrowserHistoryEntry> = emptyList(),
    onOpenRecent: (BrowserHistoryEntry) -> Unit = {},
    onShowHistory: () -> Unit = {},
) {
    val colors = YftTheme.colors
    val words = BrowserSearch.wordsOrNull(query)
    var showAllSites by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("browser-start"),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Search to download",
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "Type words to search, or a link to open it.",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (words != null) {
            YftCard(
                modifier = Modifier.fillMaxWidth().testTag("browser-search-rows"),
                contentPadding = PaddingValues(0.dp),
            ) {
                SearchRow(
                    text = "Search YouTube for “$words”",
                    icon = YftIcons.Search,
                    onClick = { onSearch(BrowserSearch.youTubeUrl(words)) },
                    tag = "browser-search-youtube",
                )
                YftDivider()
                SearchRow(
                    text = "Search ${searchEngine.displayName} for “$words”",
                    icon = YftIcons.Globe,
                    onClick = { onSearch(BrowserSearch.webUrl(words, searchEngine)) },
                    tag = "browser-search-web",
                )
            }
        }
        if (recentPages.isNotEmpty()) {
            RecentPages(pages = recentPages, onOpen = onOpenRecent, onShowHistory = onShowHistory)
        }
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
                    "Download the video from your copied link, or open its page."
                } else {
                    "Copy a video link in another app, then come back to download it."
                },
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                YftPrimaryButton(
                    text = "Download",
                    onClick = onDownloadCopiedLink,
                    icon = YftIcons.Download,
                    compact = true,
                    modifier = Modifier.testTag("browser-copied-download"),
                )
                YftTonalButton(
                    text = if (copiedLinkHint) "Use copied link" else "Paste",
                    onClick = onUseCopiedLink,
                    icon = YftIcons.Paste,
                    compact = true,
                    modifier = Modifier.testTag("browser-use-copied-link"),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            YftSectionHeader(
                title = if (showAllSites) "Your sites" else "View sites",
                actionLabel = if (showAllSites) "Show less" else "View all",
                onAction = { showAllSites = !showAllSites },
                actionTestTag = "browser-sites-all",
            )
            val shown = if (showAllSites) sites else VIEW_SITES
            if (shown.isEmpty()) {
                Text(
                    text = "Add shortcuts on Home to see them here.",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(if (showAllSites) "browser-your-sites" else "browser-view-sites"),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    shown.forEach { site ->
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
            if (showAllSites) {
                YftTextButton(
                    text = "Add or edit sites",
                    onClick = onEditSites,
                    modifier = Modifier.testTag("browser-edit-sites"),
                )
            }
        }
        Text(
            text = "Media YFT can save appears here as the page loads.",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** P31: the last pages the browser opened; History lists them all. */
@Composable
private fun RecentPages(
    pages: List<BrowserHistoryEntry>,
    onOpen: (BrowserHistoryEntry) -> Unit,
    onShowHistory: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        YftSectionHeader(
            title = "Recent",
            actionLabel = "History",
            onAction = onShowHistory,
            actionTestTag = "browser-recent-history",
        )
        YftCard(
            modifier = Modifier.fillMaxWidth().testTag("browser-recent"),
            contentPadding = PaddingValues(0.dp),
        ) {
            pages.forEachIndexed { index, page ->
                if (index > 0) YftDivider()
                SearchRow(
                    text = page.title,
                    icon = YftIcons.History,
                    onClick = { onOpen(page) },
                    tag = "browser-recent-$index",
                    supporting = page.host,
                )
            }
        }
    }
}

@Composable
private fun SearchRow(
    text: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    tag: String,
    supporting: String? = null,
) {
    val colors = YftTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        YftIcon(icon = icon, contentDescription = null, tint = colors.icon, size = 20.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = text,
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = if (supporting == null) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
