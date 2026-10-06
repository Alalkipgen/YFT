package com.alal.yft.feature.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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

        /**
         * P12: the page has several videos and no site adapter: the main one's sheet opens (the
         * one playing, else the largest), with a row that opens the others.
         */
        OPEN_MAIN_VIDEO,

        /**
         * P12: a site's video page (watch, shorts, reel, video, post): this video's sheet opens,
         * in its loading state while the page's own lookup runs. Never the found list.
         */
        OPEN_PAGE_VIDEO,

        /** A YouTube, Facebook or TikTok page: the video on screen is looked up (P5). */
        FIND_VIDEO_ON_SCREEN,
    }

    /**
     * Shown while the page has media YFT may save, and on YouTube, Facebook and TikTok pages
     * even before anything was found (P5: their feeds play no file of their own). A new page of
     * another site starts with no candidates, so the button goes until it finds some; DRM-only
     * pages have none savable; the expanded found sheet and typing an address hide it. A site's
     * video page (P12) always has it: it means that page's video.
     */
    fun isVisible(
        hasPage: Boolean,
        savableCount: Int,
        sheetExpanded: Boolean,
        editingAddress: Boolean,
        findsFocusedVideo: Boolean = false,
        sitePage: Boolean = false,
    ): Boolean = hasPage && (savableCount > 0 || findsFocusedVideo || sitePage) &&
        !sheetExpanded && !editingAddress

    /**
     * On a feed, whatever the page found may belong to any of its videos, so the button looks
     * for the one on screen. A site's video page means its own video whatever it found (P12):
     * 0, 1 or 4 found open that one sheet. Several videos elsewhere open the main one's sheet.
     */
    fun action(
        savableCount: Int,
        findsFocusedVideo: Boolean,
        feedPage: Boolean,
        sitePage: Boolean = false,
    ): Action? = when {
        sitePage -> Action.OPEN_PAGE_VIDEO
        findsFocusedVideo && (feedPage || savableCount == 0) -> Action.FIND_VIDEO_ON_SCREEN
        savableCount == 1 -> Action.OPEN_VIDEO
        savableCount > 1 -> Action.OPEN_MAIN_VIDEO
        else -> null
    }

    fun label(savableCount: Int, findsOnScreen: Boolean = false, sitePage: Boolean = false) =
        when {
            sitePage -> PAGE_VIDEO_LABEL
            findsOnScreen -> ON_SCREEN_LABEL
            else -> "Download video, $savableCount found"
        }

    const val ON_SCREEN_LABEL = "Download the video on screen"

    /** P12: the label on a site's video page. */
    const val PAGE_VIDEO_LABEL = "Download this video"

    /** P12: read with the label while the page's lookup runs. */
    const val LOOKING_UP_STATE = "Looking up the video"
}

/**
 * The Mint floating Download button, with a count badge when the page has several items. When it
 * looks for the video on screen ([findsOnScreen]) or means a site page's video ([sitePage]) it
 * counts nothing: the tap picks one video. [busy] shows a small spinner while the page's own
 * lookup runs (P12); the button still works and its sheet waits for that lookup.
 */
@Composable
internal fun BrowserDownloadButton(
    savableCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    findsOnScreen: Boolean = false,
    sitePage: Boolean = false,
    busy: Boolean = false,
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
                    contentDescription =
                        BrowserDownloadFab.label(savableCount, findsOnScreen, sitePage)
                    if (busy) stateDescription = BrowserDownloadFab.LOOKING_UP_STATE
                }
                .testTag("browser-download-fab"),
        ) {
            YftIcon(icon = YftIcons.Download, contentDescription = null)
        }
        if (busy) {
            // The button's state already says so, so the spinner is visual only.
            CircularProgressIndicator(
                color = colors.accent,
                strokeWidth = 2.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(16.dp)
                    .clearAndSetSemantics { testTag = "browser-download-fab-spinner" },
            )
        } else if (savableCount > 1 && !findsOnScreen && !sitePage) {
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
