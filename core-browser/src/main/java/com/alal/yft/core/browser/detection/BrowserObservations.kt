package com.alal.yft.core.browser.detection

import com.alal.yft.core.model.media.PageMediaRole

data class RequestObservation(
    val pageUrl: String,
    val requestUrl: String,
    val method: String,
    val headers: Map<String, String>,
    val userAgent: String?,
    val cookie: String?,
    val observedAtEpochMs: Long,
)

data class DownloadObservation(
    val pageUrl: String,
    val mediaUrl: String,
    val userAgent: String?,
    val contentDisposition: String?,
    val mimeType: String?,
    val contentLengthBytes: Long?,
    val cookie: String?,
    val observedAtEpochMs: Long,
)

data class DomMediaObservation(
    val pageUrl: String,
    val mediaUrl: String,
    val mimeType: String?,
    val title: String?,
    val thumbnailUrl: String?,
    val durationMillis: Long?,
    val observedAtEpochMs: Long,
    /** P24: what the page's markup says the file is ([PageMediaRole]); null when nothing. */
    val pageRole: PageMediaRole? = null,
)

data class RedirectObservation(
    val pageUrl: String,
    val fromUrl: String,
    val toUrl: String,
    val requestHeaders: Map<String, String>,
    val userAgent: String?,
    val cookie: String?,
    val observedAtEpochMs: Long,
)
