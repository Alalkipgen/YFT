package com.alal.yft.feature.detectedmedia

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.ui.components.MediaCandidateCard
import com.alal.yft.ui.components.YftTopBar
import com.alal.yft.ui.navigation.YftDestination
import java.net.URI

@Composable
fun DetectedMediaRoute(
    onNavigateBack: () -> Unit,
    onOpenPreview: () -> Unit,
    onOpenBrowser: () -> Unit,
    viewModel: DetectedMediaViewModel = hiltViewModel(),
) {
    val page by viewModel.page.collectAsStateWithLifecycle()
    DetectedMediaScreen(
        page = page,
        onNavigateBack = onNavigateBack,
        onPreview = { candidate ->
            if (viewModel.selectForPreview(candidate)) onOpenPreview()
        },
        onOpenBrowser = onOpenBrowser,
        onClear = viewModel::clear,
    )
}

@Composable
fun DetectedMediaScreen(
    page: DetectedPage?,
    onNavigateBack: () -> Unit,
    onPreview: (MediaCandidate) -> Unit = {},
    onOpenBrowser: () -> Unit = {},
    onClear: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            YftTopBar(
                title = YftDestination.DETECTED_MEDIA.title,
                canNavigateBack = true,
                onNavigateBack = onNavigateBack,
                actions = {
                    if (page != null) {
                        IconButton(
                            onClick = onClear,
                            modifier = Modifier.testTag("detected-clear"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Clear list",
                            )
                        }
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
    Column(
        modifier = modifier
            .padding(horizontal = 32.dp)
            .testTag("detected-empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = "No media detected yet",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "Open a page in the browser. Media the page plays or links to is listed " +
                "here until you leave the app or clear browsing data.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onOpenBrowser,
            modifier = Modifier.testTag("detected-open-browser"),
        ) {
            Text("Open browser")
        }
    }
}

@Composable
private fun DetectedList(
    page: DetectedPage,
    onPreview: (MediaCandidate) -> Unit,
    onOpenBrowser: () -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .testTag("detected-list"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(
                modifier = Modifier.testTag("detected-page"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = page.pageTitle?.takeIf(String::isNotBlank) ?: "Untitled page",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    // Only the host is shown: the full page address can carry session tokens.
                    text = pageHost(page.pageUrl),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = candidateCountLabel(page.candidates.size),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (page.candidates.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.testTag("detected-none"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Nothing playable was found on this page yet. Start the video in " +
                            "the browser, then check again.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = onOpenBrowser,
                        modifier = Modifier.testTag("detected-open-browser"),
                    ) {
                        Text("Open browser")
                    }
                }
            }
        }
        itemsIndexed(
            items = page.candidates,
            key = { index, candidate -> "${candidate.kind}-$index-${candidate.observedAtEpochMs}" },
        ) { index, candidate ->
            MediaCandidateCard(
                candidate = candidate,
                onPreview = { onPreview(candidate) },
                modifier = Modifier.testTag("detected-item-$index"),
                previewTag = "detected-preview-$index",
            )
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
