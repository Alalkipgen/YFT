package com.alal.yft.feature.home

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.view.textclassifier.TextClassifier
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** What the clip's description says, read without touching the copied text. */
data class ClipPeek(
    val isText: Boolean,
    /** The system's URL confidence from Android 12, or null when the clip was not classified. */
    val urlConfidence: Float? = null,
    /** When the clip was copied (Android 8+), or null; tells clips with the same text apart. */
    val timestamp: Long? = null,
)

/** The clipboard as [CopiedLinkWatcher] uses it; a fake in tests. */
interface ClipboardAccess {
    fun peek(): ClipPeek?

    /** Reading the text is what Android announces as "pasted from your clipboard". */
    fun readText(): CharSequence?
}

/**
 * "Check copied links when YFT opens" (decision D1). Home calls [poll] whenever it is resumed
 * and its window has focus; Android 10+ hides the clipboard from apps without focus.
 *
 * The text is read only when the setting is on, the window has focus and the description says
 * the clip is text that is not known to be something other than a link. Each clip is handled
 * once: only its timestamp and a hash of its text are kept, in memory, never the text itself,
 * and nothing about the clip is logged.
 */
@Singleton
class CopiedLinkWatcher internal constructor(private val clipboard: ClipboardAccess) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(AndroidClipboardAccess(context))

    private var lastTimestamp: Long? = null
    private var lastTextHash: Int? = null

    /** The first web link of a clip not handled before, or null. */
    fun poll(enabled: Boolean, windowFocused: Boolean): String? {
        if (!enabled || !windowFocused) return null
        val peek = clipboard.peek() ?: return null
        if (!peek.isText) return null
        val confidence = peek.urlConfidence
        if (confidence != null && confidence < URL_CONFIDENCE) return null
        if (peek.timestamp != null && peek.timestamp == lastTimestamp) return null
        val text = clipboard.readText() ?: return null
        lastTimestamp = peek.timestamp
        val hash = text.toString().hashCode()
        if (hash == lastTextHash) return null
        lastTextHash = hash
        return HomeLinks.firstWebLink(text)
    }
}

private const val URL_CONFIDENCE = 0.5f

private class AndroidClipboardAccess(context: Context) : ClipboardAccess {
    private val manager = context.getSystemService(ClipboardManager::class.java)

    override fun peek(): ClipPeek? {
        val description = runCatching { manager?.primaryClipDescription }.getOrNull()
            ?: return null
        return ClipPeek(
            isText = description.isText(),
            urlConfidence = description.urlConfidence(),
            timestamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                description.timestamp
            } else {
                null
            },
        )
    }

    override fun readText(): CharSequence? = runCatching {
        manager?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text
    }.getOrNull()

    private fun ClipDescription.isText(): Boolean =
        hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
            hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST) ||
            hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)

    private fun ClipDescription.urlConfidence(): Float? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            classificationStatus == ClipDescription.CLASSIFICATION_COMPLETE
        ) {
            getConfidenceScore(TextClassifier.TYPE_URL)
        } else {
            null
        }
}
