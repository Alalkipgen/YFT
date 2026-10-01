package com.alal.yft.extractor.generic.classifier

import com.alal.yft.core.model.media.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaUrlClassifierTest {
    @Test
    fun recognizesDirectHlsAndDashHints() {
        assertEquals(MediaKind.DIRECT, MediaUrlClassifier.classify("https://cdn.test/movie.mp4?token=x"))
        assertEquals(MediaKind.HLS, MediaUrlClassifier.classify("https://cdn.test/master.m3u8#live"))
        assertEquals(MediaKind.DASH, MediaUrlClassifier.classify("https://cdn.test/manifest", "application/dash+xml"))
        assertEquals(MediaKind.DIRECT, MediaUrlClassifier.classify("https://cdn.test/content", "video/webm; charset=utf-8"))
    }

    @Test
    fun rejectsBlobNonHttpAndUnhintedUrls() {
        assertNull(MediaUrlClassifier.classify("blob:https://example.test/id"))
        assertNull(MediaUrlClassifier.classify("file:///tmp/movie.mp4"))
        assertNull(MediaUrlClassifier.classify("https://example.test/watch"))
    }
}
