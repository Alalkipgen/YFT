package com.alal.yft.feature.library

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Intents that hand one finished download to another app.
 *
 * Both carry only a read grant for that one URI; nothing is shared without the user's tap.
 */
internal object LibraryIntents {
    private const val ANY_TYPE = "*/*"

    fun view(item: LibraryItem): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(item.uri), item.mimeType ?: ANY_TYPE)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun share(item: LibraryItem): Intent {
        val uri = Uri.parse(item.uri)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType ?: ANY_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            // The chooser forwards grants only for URIs in ClipData.
            clipData = ClipData.newRawUri(item.displayName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share ${item.displayName}")
    }

    /** Starts [intent]; false when no installed app can handle it. */
    fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
