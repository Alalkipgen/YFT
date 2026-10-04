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

/** When the browser shows its floating Download button (T14), what it says and what it does. */
internal object BrowserDownloadFab {
    /** What a tap on the button does. */
    enum class Action {
        /** The page's one video: its download sheet opens (P3). */
        OPEN_VIDEO,

        /** The page has several videos: the found list opens. */
        SHOW_LIST,

        /** A YouTube, Facebook or TikTok page: the video on screen is looked up (P5). */
        FIND_VIDEO_ON_SCREEN,
    }

    /**
     * Shown while the page has media YFT may save, and on YouTube, Facebook and TikTok pages
     * even before anything was found (P5: their feeds play no file of their own). A new page of
     * another site starts with no candidates, so the button goes until it finds some; DRM-only
     * pages have none savable; the expanded found sheet and typing an address hide it.
     */
    fun isVisible(
        hasPage: Boolean,
        savableCount: Int,
        sheetExpanded: Boolean,
        editingAddress: Boolean,
        findsFocusedVideo: Boolean = false,
    ): Boolean = hasPage && (savableCount > 0 || findsFocusedVideo) && !sheetExpanded &&
        !editingAddress

    /**
     * On a feed, whatever the page found may belong to any of its videos, so the button looks
     * for the one on screen; so it does on a video page of those sites that found nothing yet.
     */
    fun action(savableCount: Int, findsFocusedVideo: Boolean, feedPage: Boolean): Action? =
        when {
            findsFocusedVideo && (feedPage || savableCount == 0) -> Action.FIND_VIDEO_ON_SCREEN
            savableCount == 1 -> Action.OPEN_VIDEO
            savableCount > 1 -> Action.SHOW_LIST
            else -> null
        }

    fun label(savableCount: Int, findsOnScreen: Boolean = false): String =
        if (findsOnScreen) ON_SCREEN_LABEL else "Download video, $savableCount found"

    const val ON_SCREEN_LABEL = "Download the video on screen"
}

/**
 * The Mint floating Download button, with a count badge when the page has several items. When it
 * looks for the video on screen ([findsOnScreen]) it counts nothing: the tap picks one video.
 */
@Composable
internal fun BrowserDownloadButton(
    savableCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    findsOnScreen: Boolean = false,
) {
    val colors = YftTheme.colors
    Box(modifier = modifier) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = colors.accent,
            contentColor = colors.onAccent,
            elevation = FloatingActionButtonDefaults.elevation(),
            modifier = Modifier
                .semantics {
                    contentDescription = BrowserDownloadFab.label(savableCount, findsOnScreen)
                }
                .testTag("browser-download-fab"),
        ) {
            YftIcon(icon = YftIcons.Download, contentDescription = null)
        }
        if (savableCount > 1 && !findsOnScreen) {
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
