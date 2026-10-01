package com.alal.yft.core.browser.webview

import com.alal.yft.core.browser.detection.DownloadObservation
import com.alal.yft.core.browser.detection.RequestObservation

interface BrowserObservationSink {
    fun onPageStarted(url: String)
    fun onPageFinished(url: String, title: String?)
    fun onProgressChanged(progress: Int)
    fun onRequest(observation: RequestObservation)
    fun onDownload(observation: DownloadObservation)
    fun onDomProbeResult(pageUrl: String, result: String?)
    fun onMainFrameError(url: String?, description: String)
}
