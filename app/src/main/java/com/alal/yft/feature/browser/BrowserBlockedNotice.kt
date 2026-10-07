package com.alal.yft.feature.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alal.yft.core.browser.webview.BlockedNavigation
import com.alal.yft.core.browser.webview.BrowserObservationSink
import com.alal.yft.ui.components.YftIcon
import com.alal.yft.ui.components.YftTextButton
import com.alal.yft.ui.theme.YftIcons
import com.alal.yft.ui.theme.YftShapes
import com.alal.yft.ui.theme.YftTheme
import kotlinx.coroutines.delay

/** How long the notice of a blocked pop-up or redirect stays (P32). */
internal const val BLOCKED_NOTICE_MS = 4_000L

/** What the notice says: a window is a pop-up; a redirect names where it was going. */
internal fun blockedNoticeText(blocked: BlockedNavigation): String =
    if (blocked.window || blocked.host.isEmpty()) {
        "Pop-up blocked"
    } else {
        "Blocked a redirect to ${blocked.host}"
    }

/**
 * The page tried to open a window or send the tab elsewhere and the browser kept the page (P32):
 * a small notice for [BLOCKED_NOTICE_MS], with Open to go there in this tab after all.
 */
@Composable
internal fun BrowserBlockedNotice(
    blocked: BlockedNavigation,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YftTheme.colors
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(blocked) {
        delay(BLOCKED_NOTICE_MS)
        dismiss()
    }
    Surface(
        modifier = modifier
            .widthIn(max = 420.dp)
            .testTag("browser-blocked-notice")
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        shape = YftShapes.pill,
        color = colors.card,
        contentColor = colors.textPrimary,
        shadowElevation = 6.dp,
        border = if (colors.isDark) BorderStroke(1.dp, colors.border) else null,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YftIcon(
                icon = YftIcons.Block,
                contentDescription = null,
                tint = colors.icon,
                size = 20.dp,
            )
            Text(
                text = blockedNoticeText(blocked),
                modifier = Modifier
                    .padding(start = 10.dp, end = 4.dp)
                    .weight(1f, fill = false)
                    .testTag("browser-blocked-text"),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            YftTextButton(
                text = "Open",
                onClick = onOpen,
                modifier = Modifier.testTag("browser-blocked-open"),
            )
        }
    }
}

/** The browser's sink (P32): blocked navigations also reach the screen's notice. */
internal class BlockedNavigationSink(
    private val inner: BrowserObservationSink,
    private val onBlocked: (BlockedNavigation) -> Unit,
) : BrowserObservationSink by inner {
    override fun onNavigationBlocked(blocked: BlockedNavigation) {
        inner.onNavigationBlocked(blocked)
        onBlocked(blocked)
    }
}
