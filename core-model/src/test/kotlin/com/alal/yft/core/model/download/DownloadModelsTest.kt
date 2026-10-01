package com.alal.yft.core.model.download

import com.alal.yft.core.model.media.BrowserRequestContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadModelsTest {
    @Test
    fun `direct plan string redacts signed URL and browser context`() {
        val plan = DirectDownloadPlan(
            taskId = "task-1",
            sourceUrl = "https://cdn.example.test/movie.mp4?token=secret",
            suggestedFileName = "movie.mp4",
            requestContext = BrowserRequestContext(
                pageUrl = "https://example.test/watch",
                userAgent = "fixture",
                cookie = "session=private",
            ),
        )

        val rendered = plan.toString()

        assertFalse(rendered.contains("secret"))
        assertFalse(rendered.contains("session=private"))
        assertFalse(rendered.contains("cdn.example.test"))
        assertTrue(rendered.contains("[REDACTED]"))
    }

    @Test
    fun `HLS plan string redacts signed playlist URL and browser context`() {
        val plan = HlsDownloadPlan(
            taskId = "hls-1",
            playlistUrl = "https://cdn.example.test/track.m3u8?token=secret",
            suggestedFileName = "track.ts",
            requestContext = BrowserRequestContext(
                pageUrl = "https://example.test/watch",
                userAgent = "fixture",
                cookie = "session=private",
            ),
        )

        val rendered = plan.toString()

        assertFalse(rendered.contains("secret"))
        assertFalse(rendered.contains("session=private"))
        assertFalse(rendered.contains("cdn.example.test"))
        assertTrue(rendered.contains("[REDACTED]"))
    }

    @Test
    fun `segment validates progress and reports remaining bytes`() {
        val segment = DownloadSegment(
            index = 2,
            startByte = 100,
            endByteInclusive = 199,
            downloadedBytes = 25,
        )

        assertEquals(100L, segment.lengthBytes)
        assertEquals(75L, segment.remainingBytes)
        assertThrows(IllegalArgumentException::class.java) {
            segment.copy(downloadedBytes = 101)
        }
    }

    @Test
    fun `unknown segment length stays unknown`() {
        val segment = DownloadSegment(index = 0, startByte = 0, endByteInclusive = null)

        assertNull(segment.lengthBytes)
        assertNull(segment.remainingBytes)
    }

    @Test
    fun `progress is indeterminate without positive total`() {
        assertNull(DownloadProgress(downloadedBytes = 7, totalBytes = null).fraction)
        assertNull(DownloadProgress(downloadedBytes = 0, totalBytes = 0).fraction)
        assertEquals(
            0.25,
            DownloadProgress(downloadedBytes = 25, totalBytes = 100).fraction!!,
            0.0,
        )
    }

    @Test
    fun `remote metadata string redacts final URL and validators`() {
        val metadata = RemoteFileMetadata(
            finalUrl = "https://cdn.example.test/movie.mp4?token=secret",
            totalBytes = 100,
            supportsByteRanges = true,
            entityTag = "\"private-validator\"",
            lastModified = "Wed, 01 Oct 2026 00:00:00 GMT",
            contentType = "video/mp4",
            suggestedFileName = "movie.mp4",
        )

        val rendered = metadata.toString()

        assertFalse(rendered.contains("secret"))
        assertFalse(rendered.contains("private-validator"))
        assertTrue(rendered.contains("entityTagPresent=true"))
    }

    @Test
    fun `checkpoint requires ordered unique segment indexes`() {
        val first = DownloadSegment(0, 0, 9, downloadedBytes = 4)
        val second = DownloadSegment(1, 10, 19, downloadedBytes = 3)
        val checkpoint = DirectTransferCheckpoint(
            totalBytes = 20,
            entityTag = "\"private\"",
            lastModified = null,
            segments = listOf(first, second),
        )

        assertEquals(7L, checkpoint.downloadedBytes)
        assertFalse(checkpoint.toString().contains("\"private\""))
        assertThrows(IllegalArgumentException::class.java) {
            checkpoint.copy(segments = listOf(second, first))
        }
    }

    @Test
    fun `HLS checkpoint reports completed chunks without exposing fingerprint`() {
        val checkpoint = HlsTransferCheckpoint(
            manifestFingerprint = "a".repeat(64),
            chunks = listOf(
                StreamChunkCheckpoint(index = 0, downloadedBytes = 4, completed = true),
                StreamChunkCheckpoint(index = 1, downloadedBytes = 0, completed = false),
            ),
        )

        assertEquals(4L, checkpoint.downloadedBytes)
        assertEquals(1, checkpoint.completedChunkCount)
        assertFalse(checkpoint.toString().contains("a".repeat(64)))
        assertThrows(IllegalArgumentException::class.java) {
            checkpoint.copy(
                chunks = listOf(
                    StreamChunkCheckpoint(index = 1, downloadedBytes = 0, completed = false),
                ),
            )
        }
    }
}
