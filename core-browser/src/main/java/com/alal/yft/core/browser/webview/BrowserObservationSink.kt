package com.alal.yft.core.browser.webview

import com.alal.yft.core.browser.detection.DownloadObservation
import com.alal.yft.core.browser.detection.RequestObservation

interface BrowserObservationSink {
    fun onPageStarted(url: String)
    fun onPageFinished(url: String, title: String?)

    /**
     * The page's address changed without loading a new document: `history.pushState` or
     * `replaceState`, a fragment change, or back/forward within the same document. Single-page
     * sites such as YouTube's mobile site open a video this way, so no page-started or
     * page-finished callback follows.
     */
    fun onUrlChanged(url: String)
    fun onProgressChanged(progress: Int)
    fun onRequest(observation: RequestObservation)
    fun onDownload(observation: DownloadObservation)
    fun onDomProbeResult(pageUrl: String, result: String?)
    fun onMainFrameError(url: String?, description: String)
}
