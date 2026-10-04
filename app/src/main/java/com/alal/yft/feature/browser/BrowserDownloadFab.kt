package com.alal.yft.feature.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.alal.yft.ui.components.YftCountBadge
import com.alal.yft.ui.components.YftIcon
import com.alal.yft.ui.theme.YftIcons
import com.alal.yft.ui.theme.YftTheme

/** When the browser shows its floating Download button (T14), and what it says. */
internal object BrowserDownloadFab {
    /**
     * Shown while the page has media YFT may save. A new page starts with no candidates, so the
     * button goes until it finds some; DRM-only pages have none savable; the expanded found
     * sheet and typing an address hide it.
     */
    fun isVisible(
        hasPage: Boolean,
        savableCount: Int,
        sheetExpanded: Boolean,
        editingAddress: Boolean,
    ): Boolean = hasPage && savableCount > 0 && !sheetExpanded && !editingAddress

    fun label(savableCount: Int): String = "Download video, $savableCount found"
}

/** The Mint floating Download button, with a count badge when the page has several items. */
@Composable
internal fun BrowserDownloadButton(
    savableCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YftTheme.colors
    Box(modifier = modifier) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = colors.accent,
            contentColor = colors.onAccent,
            elevation = FloatingActionButtonDefaults.elevation(),
            modifier = Modifier
                .semantics { contentDescription = BrowserDownloadFab.label(savableCount) }
                .testTag("browser-download-fab"),
        ) {
            YftIcon(icon = YftIcons.Download, contentDescription = null)
        }
        if (savableCount > 1) {
            // The count is already in the button's label, so the badge is visual only.
            YftCountBadge(
                count = savableCount,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .clearAndSetSemantics { testTag = "browser-download-fab-badge" },
            )
        }
    }
}
