package com.alal.yft.spike

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource

@UnstableApi
object PreviewSourceFactory {
    fun create(
        url: String,
        kind: PreviewKind = PreviewKind.fromUrl(url),
        requestContext: BrowserRequestContext = BrowserRequestContext(null, null, null),
    ): MediaSource {
        require(url.startsWith("https://")) { "The Phase 0 spike accepts HTTPS media only" }
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(false)
            .setDefaultRequestProperties(requestContext.replayHeaders())
        val item = MediaItem.Builder()
            .setUri(url)
            .apply { kind.media3MimeType?.let(::setMimeType) }
            .build()
        return when (kind) {
            PreviewKind.DIRECT -> ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(item)
            PreviewKind.HLS -> HlsMediaSource.Factory(dataSourceFactory).createMediaSource(item)
            PreviewKind.DASH -> DashMediaSource.Factory(dataSourceFactory).createMediaSource(item)
        }
    }
}
