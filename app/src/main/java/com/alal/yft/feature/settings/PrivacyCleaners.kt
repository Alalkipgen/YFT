package com.alal.yft.feature.settings

import android.content.Context
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import com.alal.yft.core.data.history.BrowserHistoryRepository
import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.core.media.session.PreviewSelectionStore
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.detection.SiteLookupCache
import com.alal.yft.feature.detectedmedia.DetectedMediaStore
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Removes everything the in-app browser remembers about the sites the user visited. */
fun interface BrowsingDataCleaner {
    suspend fun clear()
}

/**
 * Clears WebView cookies, web storage, the HTTP cache, saved HTTP sign-ins and granted location
 * permissions. WebView state is process-wide, so a short-lived WebView is enough to clear the
 * cache that the browser and the player-script host share.
 */
class WebViewBrowsingDataCleaner(
    private val context: Context,
) : BrowsingDataCleaner {
    override suspend fun clear() = withContext(Dispatchers.Main) {
        val cookies = CookieManager.getInstance()
        suspendCancellableCoroutine { continuation ->
            cookies.removeAllCookies { continuation.resume(Unit) }
        }
        cookies.flush()
        WebStorage.getInstance().deleteAllData()
        GeolocationPermissions.getInstance().clearAll()
        WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword()
        val webView = WebView(context.applicationContext)
        try {
            webView.clearCache(true)
            webView.clearHistory()
        } finally {
            webView.destroy()
        }
    }
}

/** Runs [cleaners] in order, so web data and in-memory media lists are cleared together. */
class CompositeBrowsingDataCleaner(
    private val cleaners: List<BrowsingDataCleaner>,
) : BrowsingDataCleaner {
    override suspend fun clear() {
        cleaners.forEach { it.clear() }
    }
}

/**
 * Drops media found while browsing; its URLs can carry signed tokens of the cleared session.
 * P17: the remembered lookups go too, as they may have been read with that session.
 */
class SessionMediaCleaner(
    private val detectedMedia: DetectedMediaStore,
    private val previewSelection: PreviewSelectionStore,
    private val lookups: SiteLookupCache? = null,
) : BrowsingDataCleaner {
    override suspend fun clear() {
        detectedMedia.clear()
        previewSelection.clear()
        lookups?.clear()
    }
}

/** P31: the browser's history goes with the rest of the browsing data. */
class BrowserHistoryCleaner(
    private val history: BrowserHistoryRepository,
) : BrowsingDataCleaner {
    override suspend fun clear() {
        history.clear()
    }
}

/** The finished part of the download list, which the user may clear without touching files. */
interface DownloadHistory {
    val finishedCount: Flow<Int>

    /** Removes finished entries and returns how many were removed. Files are kept. */
    suspend fun clearFinished(): Int
}

class QueueDownloadHistory(
    private val queue: DownloadQueue,
) : DownloadHistory {
    override val finishedCount: Flow<Int> = queue.tasks
        .map { tasks -> tasks.count { it.status in FINISHED } }
        .distinctUntilChanged()

    override suspend fun clearFinished(): Int {
        queue.restore()
        val finished = queue.tasks.value.filter { it.status in FINISHED }
        finished.forEach { queue.deleteRecord(it.id) }
        return finished.size
    }

    companion object {
        /** Work that can no longer resume; paused and refreshable work stays in the list. */
        val FINISHED: Set<DownloadTaskStatus> = setOf(
            DownloadTaskStatus.COMPLETED,
            DownloadTaskStatus.FAILED,
            DownloadTaskStatus.CANCELLED,
        )
    }
}
