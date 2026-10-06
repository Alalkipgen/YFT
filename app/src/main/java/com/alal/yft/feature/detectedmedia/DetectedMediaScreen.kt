package com.alal.yft.feature.detectedmedia

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alal.yft.core.model.media.MediaGroup
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.ui.components.FoundMediaDividerInset
import com.alal.yft.ui.components.YftAllowedMediaNote
import com.alal.yft.ui.components.YftCard
import com.alal.yft.ui.components.YftDivider
import com.alal.yft.ui.components.YftFoundMediaRow
import com.alal.yft.ui.components.YftIcon
import com.alal.yft.ui.components.YftIconButton
import com.alal.yft.ui.components.YftOtherVideosHeader
import com.alal.yft.ui.components.YftPrimaryButton
import com.alal.yft.ui.components.YftTopBar
import com.alal.yft.ui.components.isSavable
import com.alal.yft.ui.components.protectedHiddenLabel
import com.alal.yft.ui.navigation.YftDestination
import com.alal.yft.ui.theme.YftIcons
import com.alal.yft.ui.theme.YftTheme
import java.net.URI

@Composable
fun DetectedMediaRoute(
    onNavigateBack: () -> Unit,
    onOpenQuickDownload: () -> Unit,
    onOpenBrowser: () -> Unit,
    viewModel: DetectedMediaViewModel = hiltViewModel(),
) {
    val page by viewModel.page.collectAsStateWithLifecycle()
    DetectedMediaScreen(
        page = page,
        onNavigateBack = onNavigateBack,
        onPreview = { video ->
            if (viewModel.selectForDownload(video)) onOpenQuickDownload()
        },
        onOpenBrowser = onOpenBrowser,
        onClear = viewModel::clear,
    )
}

/**
 * "Found on this page" as a full screen, opened from the Home link check: the page by title and
 * host, then the same rows as the browser sheet (`02`), one per video; Preview opens its
 * download sheet. DRM-protected candidates are never offered; a note says how many were left
 * out.
 */
@Composable
fun DetectedMediaScreen(
    page: DetectedPage?,
    onNavigateBack: () -> Unit,
    onPreview: (MediaGroup) -> Unit = {},
    onOpenBrowser: () -> Unit = {},
    onClear: () -> Unit = {},
) {
    val colors = YftTheme.colors
    Scaffold(
        containerColor = colors.background,
        contentColor = colors.textPrimary,
        topBar = {
            YftTopBar(
                title = YftDestination.DETECTED_MEDIA.title,
                canNavigateBack = true,
                onNavigateBack = onNavigateBack,
                actions = {
                    if (page != null) {
                        YftIconButton(
                            icon = YftIcons.Delete,
                            contentDescription = "Clear list",
                            onClick = onClear,
                            modifier = Modifier.testTag("detected-clear"),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (page == null) {
            NothingDetected(
                onOpenBrowser = onOpenBrowser,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        } else {
            DetectedList(
                page = page,
                onPreview = onPreview,
                onOpenBrowser = onOpenBrowser,
                contentPadding = padding,
            )
        }
    }
}

@Composable
private fun NothingDetected(
    onOpenBrowser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YftTheme.colors
    Column(
        modifier = modifier
            .padding(horizontal = 32.dp)
            .testTag("detected-empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        YftIcon(icon = YftIcons.Search, contentDescription = null, tint = colors.icon, size = 40.dp)
        Text(
            text = "No media found yet",
            color = colors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "Open a page in the browser. Media the page plays or links to is listed " +
                "here until you leave the app or clear browsing data.",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        YftPrimaryButton(
            text = "Open browser",
            onClick = onOpenBrowser,
            icon = YftIcons.Globe,
            modifier = Modifier
                .padding(top = 4.dp)
                .testTag("detected-open-browser"),
        )
    }
}

@Composable
private fun DetectedList(
    page: DetectedPage,
    onPreview: (MediaGroup) -> Unit,
    onOpenBrowser: () -> Unit,
    contentPadding: PaddingValues,
) {
    val colors = YftTheme.colors
    val savable = remember(page.candidates) { page.candidates.filter { it.isSavable } }
    // P24: the count counts the page's videos; previews and ads follow them under "Other
    // videos on this page".
    val list = remember(savable, page.adapterSite) {
        MediaGroups.ofPage(MediaGroups.pageVideos(savable, adapterSite = page.adapterSite))
    }
    val hiddenNote = protectedHiddenLabel(page.candidates.size - savable.size)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .testTag("detected-list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .testTag("detected-page"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = page.pageTitle?.takeIf(String::isNotBlank) ?: "Untitled page",
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    // Only the host is shown: the full page address can carry session tokens.
                    text = "${pageHost(page.pageUrl)} · ${candidateCountLabel(list.videos.size)}",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (savable.isEmpty()) {
            item {
                YftCard(modifier = Modifier.fillMaxWidth().testTag("detected-none")) {
                    Text(
                        text = hiddenNote?.let { "$it Try another page." }
                            ?: ("Nothing playable was found on this page yet. Start the video in " +
                                "the browser, then check again."),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    YftPrimaryButton(
                        text = "Open browser",
                        onClick = onOpenBrowser,
                        icon = YftIcons.Globe,
                        compact = true,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .testTag("detected-open-browser"),
                    )
                }
            }
        } else {
            item {
                YftCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    list.all.forEachIndexed { index, video ->
                        if (index == list.videos.size) {
                            YftOtherVideosHeader(
                                count = list.previews.size,
                                modifier = Modifier
                                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                                    .testTag("detected-other-videos"),
                            )
                        } else if (index > 0) {
                            YftDivider(modifier = Modifier.padding(start = FoundMediaDividerInset))
                        }
                        YftFoundMediaRow(
                            video = video,
                            onPreview = { onPreview(video) },
                            modifier = Modifier.testTag("detected-item-$index"),
                            previewTag = "detected-preview-$index",
                        )
                    }
                }
            }
            hiddenNote?.let { note ->
                item {
                    Text(
                        text = note,
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .testTag("detected-protected-note"),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item { YftAllowedMediaNote() }
        }
    }
}

internal fun pageHost(pageUrl: String): String =
    runCatching { URI(pageUrl).host }.getOrNull()?.takeIf(String::isNotBlank) ?: "Unknown site"

internal fun candidateCountLabel(count: Int): String = when (count) {
    0 -> "No media found"
    1 -> "1 media item found"
    else -> "$count media items found"
}
