package com.alal.yft.feature.home

import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Build
import android.view.textclassifier.TextClassifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * Whether the clipboard seems to hold a link, for the Promptbox's "Use copied link" row.
 *
 * Only the clip's description is consulted, never its text: reading the text is what Android
 * announces as "pasted from your clipboard", so that happens only when the user taps Use or
 * Paste. The answer is refreshed when the clipboard changes and whenever the window regains
 * focus, because Android hides the clipboard from apps that are not in the foreground.
 */
@Composable
internal fun rememberCopiedLinkHint(): Boolean {
    val context = LocalContext.current
    val clipboard = remember(context) { context.getSystemService(ClipboardManager::class.java) }
    var hint by remember { mutableStateOf(false) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused

    DisposableEffect(clipboard) {
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            hint = clipboard?.primaryClipDescription.suggestsLink()
        }
        clipboard?.addPrimaryClipChangedListener(listener)
        onDispose { clipboard?.removePrimaryClipChangedListener(listener) }
    }
    LaunchedEffect(clipboard, windowFocused) {
        if (windowFocused) hint = clipboard?.primaryClipDescription.suggestsLink()
    }
    return hint
}

/**
 * Text clips suggest a link. From Android 12 the system may already have classified the text;
 * when it has, the hint appears only if it is likely a URL. Unclassified text still shows it.
 */
internal fun ClipDescription?.suggestsLink(): Boolean {
    if (this == null) return false
    val isText = hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
        hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST) ||
        hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
    if (!isText) return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        classificationStatus == ClipDescription.CLASSIFICATION_COMPLETE
    ) {
        return getConfidenceScore(TextClassifier.TYPE_URL) >= URL_CONFIDENCE
    }
    return true
}

private const val URL_CONFIDENCE = 0.5f
