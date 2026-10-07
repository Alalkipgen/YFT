package com.alal.yft.feature.browser

import androidx.annotation.MainThread
import com.alal.yft.core.browser.webview.BrowserObservationSink
import com.alal.yft.core.data.history.BrowserHistoryAddress

/**
 * Decides which of the WebView's page events are visits for the history (P31): a page that
 * finished loading without a main-frame error, and a single-page site's own address change
 * after its page finished (YouTube's mobile site opens a video that way). A redirect while a page
 * loads, the same document finishing twice, a fragment change and tracking parameters are no new
 * visit. The page's title follows as [onTitle] when it names itself later.
 *
 * Whether the visit is kept (Settings › Browser › Save browser history) is decided by the
 * receiver. Main thread only, like the WebView callbacks it reads.
 */
@MainThread
class BrowserHistoryRecorder(
    private val onVisit: (url: String, title: String?) -> Unit,
    private val onTitle: (url: String, title: String) -> Unit,
) {
    private var loading = false
    private var failedUrl: String? = null
    private var visitedUrl: String? = null
    private var visitedTitle: String? = null

    fun pageStarted(url: String) {
        loading = true
        failedUrl = null
        visitedUrl = null
        visitedTitle = null
    }

    fun mainFrameError(url: String?) {
        failedUrl = url ?: return
    }

    fun pageFinished(url: String, title: String?) {
        loading = false
        if (url == failedUrl || sameAddress(url, failedUrl)) return
        if (url == visitedUrl) return
        visit(url, title)
    }

    /** [currentTitle] is the page's title right now; the old page's title is not taken. */
    fun urlChanged(url: String, currentTitle: String?) {
        if (loading) return
        if (visitedUrl == null || sameAddress(url, visitedUrl)) return
        visit(url, currentTitle?.takeUnless { it == visitedTitle })
    }

    fun titleChanged(url: String, title: String?) {
        val named = title?.trim()?.takeIf(String::isNotEmpty) ?: return
        val visited = visitedUrl ?: return
        if (url != visited && !sameAddress(url, visited)) return
        if (named == visitedTitle) return
        visitedTitle = named
        onTitle(visited, named)
    }

    private fun visit(url: String, title: String?) {
        visitedUrl = url
        visitedTitle = title
        onVisit(url, title)
    }

    private fun sameAddress(first: String, second: String?): Boolean {
        second ?: return false
        val cleaned = BrowserHistoryAddress.clean(first) ?: return false
        return cleaned == BrowserHistoryAddress.clean(second)
    }
}

/**
 * Passes every event to [inner] unchanged, in the same order and on the same thread (the
 * detection's view model stays the WebView's sink), and tells [recorder] about page loads.
 * [currentTitle] reads the WebView's title on the main thread.
 */
class HistoryRecordingSink(
    private val inner: BrowserObservationSink,
    private val recorder: BrowserHistoryRecorder,
    private val currentTitle: () -> String?,
) : BrowserObservationSink by inner {
    override fun onPageStarted(url: String) {
        inner.onPageStarted(url)
        recorder.pageStarted(url)
    }

    override fun onPageFinished(url: String, title: String?) {
        inner.onPageFinished(url, title)
        recorder.pageFinished(url, title)
    }

    override fun onUrlChanged(url: String) {
        inner.onUrlChanged(url)
        recorder.urlChanged(url, currentTitle())
    }

    override fun onMainFrameError(url: String?, description: String) {
        inner.onMainFrameError(url, description)
        recorder.mainFrameError(url)
    }

    override fun onPageTitle(url: String, title: String?) {
        inner.onPageTitle(url, title)
        recorder.titleChanged(url, title)
    }
}
