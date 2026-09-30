package com.alal.yft.spike

import androidx.media3.common.MimeTypes

enum class PreviewKind(val media3MimeType: String?) {
    DIRECT(null),
    HLS(MimeTypes.APPLICATION_M3U8),
    DASH(MimeTypes.APPLICATION_MPD);

    companion object {
        fun fromUrl(url: String): PreviewKind {
            val path = url.substringBefore('#').substringBefore('?').lowercase()
            return when {
                path.endsWith(".m3u8") -> HLS
                path.endsWith(".mpd") -> DASH
                else -> DIRECT
            }
        }
    }
}
