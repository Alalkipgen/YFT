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

    /** HLS delivery directives (`_HLS_` names) are dropped; look-alike names stay. */
    @Test
    fun deliveryDirectivesAreDroppedAndLookAlikesStay() {
        assertEquals(
            "https://cdn.test/live/a.m3u8?session=1",
            MediaFileUrls.wholeFile(
                "https://cdn.test/live/a.m3u8?_HLS_msn=7&session=1&_HLS_part=2&_HLS_skip=YES",
            ),
        )
        assertEquals(
            "https://cdn.test/live/a.m3u8",
            MediaFileUrls.wholeFile("https://cdn.test/live/a.m3u8?_HLS_msn=7"),
        )
        val lookAlike = "https://cdn.test/live/a.m3u8?hls_msn=7&x_HLS_part=2&_hls_push=1"
        assertEquals(lookAlike, MediaFileUrls.wholeFile(lookAlike))
        // A directive is no byte range.
        assertFalse(MediaFileUrls.hasByteRange("https://cdn.test/live/a.m3u8?_HLS_msn=7"))
        assertTrue(MediaFileUrls.hasByteRange("https://cdn.test/v.mp4?_HLS_msn=7&range=0-9"))
    }

    /** VK's pack proof: a closed `bytes=A-B` query value names a piece; nothing else does. */
    @Test
    fun aClosedBytesRangeInTheQueryNamesAPiece() {
        assertTrue(MediaFileUrls.isPiece("https://vkvd1.media.test/?expires=1&type=2&id=7&bytes=0-1427"))
        assertTrue(MediaFileUrls.isPiece("https://cdn.test/v.mp4?Bytes=100-199#t=3"))
        // The piece's address is left as it is: no whole file is made of it.
        val piece = "https://vkvd1.media.test/?type=2&bytes=0-1427"
        assertEquals(piece, MediaFileUrls.wholeFile(piece))
        assertFalse(MediaFileUrls.isPiece("https://cdn.test/v.mp4?bytes=0-"))
        assertFalse(MediaFileUrls.isPiece("https://cdn.test/v.mp4?bytes=all"))
        assertFalse(MediaFileUrls.isPiece("https://cdn.test/v.mp4"))
        assertFalse(MediaFileUrls.isPiece("https://cdn.test/v.mp4#bytes=0-99"))
        assertFalse(MediaFileUrls.isPiece("https://cdn.test/v.mp4?bytestart=0&byteend=99"))
        assertFalse(MediaFileUrls.isPiece("https://cdn.test/v.mp4?range=0-99"))
        assertFalse(MediaFileUrls.isPiece("https://cdn.test/bytes=0-99/v.mp4"))
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
