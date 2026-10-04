package com.alal.yft.extractor.generic.classifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFileUrlsTest {
    @Test
    fun byteRangeParametersAreDroppedAndEverythingElseIsKept() {
        assertEquals(
            "https://cdn.test/v.mp4?_nc_cat=1&oh=x",
            MediaFileUrls.wholeFile("https://cdn.test/v.mp4?_nc_cat=1&bytestart=0&byteend=99&oh=x"),
        )
        assertEquals(
            "https://cdn.test/v.mp4#t=3",
            MediaFileUrls.wholeFile("https://cdn.test/v.mp4?range=100-199#t=3"),
        )
        assertEquals(
            "https://cdn.test/v.mp4?ByteStart=x&range=all",
            MediaFileUrls.wholeFile("https://cdn.test/v.mp4?ByteStart=x&range=all"),
        )
        assertEquals("https://cdn.test/v.mp4", MediaFileUrls.wholeFile("https://cdn.test/v.mp4"))
        assertTrue(MediaFileUrls.hasByteRange("https://cdn.test/v.mp4?bytestart=5"))
        assertFalse(MediaFileUrls.hasByteRange("https://cdn.test/v.mp4?start=5"))
    }

    @Test
    fun onlyLongGeneratedMediaNamesAreOpaque() {
        assertTrue(
            MediaFileUrls.isOpaqueFile(
                "https://video.cdn.test/v/t42.1790-2/466237829_1093939455424573_21782459_n.mp4?a=1",
            ),
        )
        assertFalse(MediaFileUrls.isOpaqueFile("https://cdn.test/movie.mp4?quality=720"))
        assertFalse(MediaFileUrls.isOpaqueFile("https://cdn.test/AbCdEfGhIjKlMnOpQrStUvWxYz.jpg"))
        assertFalse(MediaFileUrls.isOpaqueFile("https://cdn.test/watch?v=AbCdEfGhIjKlMnOpQrStUv"))
    }

    @Test
    fun hlsPiecesShareAFamilyAndWholeFilesHaveNone() {
        assertEquals(
            "cdn.test/hls/720p/seg-#-v#-a#.ts",
            MediaFileUrls.segmentFamily("https://cdn.test/hls/720p/seg-12-v1-a1.ts?token=x"),
        )
        assertEquals(
            MediaFileUrls.segmentFamily("https://cdn.test/a/media_7.aac"),
            MediaFileUrls.segmentFamily("https://cdn.test/a/media_8.aac"),
        )
        assertNull(MediaFileUrls.segmentFamily("https://cdn.test/clips/clip-1.mp4"))
        assertNull(MediaFileUrls.segmentFamily("https://cdn.test/hls/whole.ts"))
    }
}
