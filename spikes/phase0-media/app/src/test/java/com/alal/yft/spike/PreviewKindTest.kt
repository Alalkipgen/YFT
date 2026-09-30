package com.alal.yft.spike

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewKindTest {
    @Test fun recognizesHlsWithQuery() =
        assertEquals(PreviewKind.HLS, PreviewKind.fromUrl("https://cdn.example/live/master.m3u8?token=x"))

    @Test fun recognizesDashWithFragment() =
        assertEquals(PreviewKind.DASH, PreviewKind.fromUrl("https://cdn.example/vod/manifest.mpd#start"))

    @Test fun defaultsToDirectMedia() =
        assertEquals(PreviewKind.DIRECT, PreviewKind.fromUrl("https://cdn.example/video.mp4"))
}
