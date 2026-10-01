package com.alal.yft.core.browser.webview

import android.webkit.DownloadListener
import com.alal.yft.core.browser.detection.DownloadObservation

class BrowserDownloadListener(
    private val pageUrlProvider: () -> String?,
    private val cookieProvider: (String) -> String?,
    private val sink: BrowserObservationSink,
    private val clock: () -> Long = System::currentTimeMillis,
) : DownloadListener {
    override fun onDownloadStart(
        url: String?,
        userAgent: String?,
        contentDisposition: String?,
        mimetype: String?,
        contentLength: Long,
    ) {
        val pageUrl = pageUrlProvider() ?: return
        val mediaUrl = url ?: return
        sink.onDownload(
            DownloadObservation(
                pageUrl = pageUrl,
                mediaUrl = mediaUrl,
                userAgent = userAgent,
                contentDisposition = contentDisposition,
                mimeType = mimetype,
                contentLengthBytes = contentLength.takeIf { it >= 0 },
                cookie = cookieProvider(mediaUrl),
                observedAtEpochMs = clock(),
            ),
        )
    }
}
