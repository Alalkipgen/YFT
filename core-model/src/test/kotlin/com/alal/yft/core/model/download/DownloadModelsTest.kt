package com.alal.yft.core.model.download

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaTrackType
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
    fun `DASH and mux plan strings redact manifests selections and browser context`() {
        val video = dashPlan(
            taskId = "dash-video",
            representationId = "video-private-selection",
            trackType = MediaTrackType.VIDEO,
            expiresAtEpochMs = 200,
        )
        val audio = dashPlan(
            taskId = "dash-audio",
            representationId = "audio-private-selection",
            trackType = MediaTrackType.AUDIO,
            expiresAtEpochMs = 100,
        )

        val renderedTrack = video.toString()
        val renderedMux = AudioVideoMuxDownloadPlan(
            taskId = "mux-1",
            video = video,
            audio = audio,
            suggestedFileName = "movie.mp4",
        ).toString()

        listOf(renderedTrack, renderedMux).forEach { rendered ->
            assertFalse(rendered.contains("secret"))
            assertFalse(rendered.contains("session=private"))
            assertFalse(rendered.contains("private-selection"))
            assertTrue(rendered.contains("[REDACTED]"))
        }
        assertEquals(100L, AudioVideoMuxDownloadPlan(
            taskId = "mux-1",
            video = video,
            audio = audio,
            suggestedFileName = "movie.mp4",
        ).expiresAtEpochMs)
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

    @Test
    fun `DASH checkpoint reports completed chunks without exposing fingerprint`() {
        val checkpoint = DashTransferCheckpoint(
            manifestFingerprint = "b".repeat(64),
            chunks = listOf(
                StreamChunkCheckpoint(index = 0, downloadedBytes = 8, completed = true),
                StreamChunkCheckpoint(index = 1, downloadedBytes = 3, completed = true),
            ),
        )

        assertEquals(11L, checkpoint.downloadedBytes)
        assertEquals(2, checkpoint.completedChunkCount)
        assertFalse(checkpoint.toString().contains("b".repeat(64)))
        assertThrows(IllegalArgumentException::class.java) {
            checkpoint.copy(manifestFingerprint = "not-a-fingerprint")
        }
    }

    @Test
    fun `mux checkpoint requires complete ready tracks and reports combined bytes`() {
        val video = DashTransferCheckpoint(
            manifestFingerprint = "c".repeat(64),
            chunks = listOf(
                StreamChunkCheckpoint(index = 0, downloadedBytes = 10, completed = true),
            ),
        )
        val audio = DashTransferCheckpoint(
            manifestFingerprint = "d".repeat(64),
            chunks = listOf(
                StreamChunkCheckpoint(index = 0, downloadedBytes = 4, completed = true),
            ),
        )
        val checkpoint = AudioVideoMuxCheckpoint(
            video = video,
            audio = audio,
            videoReady = true,
            audioReady = true,
            stage = AudioVideoMuxStage.READY_TO_MUX,
        )

        assertEquals(14L, checkpoint.downloadedBytes)
        assertFalse(checkpoint.toString().contains("c".repeat(64)))
        assertThrows(IllegalArgumentException::class.java) {
            AudioVideoMuxCheckpoint(stage = AudioVideoMuxStage.MUXING)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AudioVideoMuxCheckpoint(
                video = video.copy(
                    chunks = listOf(
                        StreamChunkCheckpoint(
                            index = 0,
                            downloadedBytes = 0,
                            completed = false,
                        ),
                    ),
                ),
                videoReady = true,
            )
        }
    }

    private fun dashPlan(
        taskId: String,
        representationId: String,
        trackType: MediaTrackType,
        expiresAtEpochMs: Long?,
    ): DashDownloadPlan = DashDownloadPlan(
        taskId = taskId,
        manifestUrl = "https://cdn.example.test/manifest.mpd?token=secret",
        representationId = representationId,
        trackType = trackType,
        suggestedFileName = "$taskId.mp4",
        requestContext = BrowserRequestContext(
            pageUrl = "https://example.test/watch",
            userAgent = "fixture",
            cookie = "session=private",
        ),
        mimeType = if (trackType == MediaTrackType.AUDIO) "audio/mp4" else "video/mp4",
        codecs = if (trackType == MediaTrackType.AUDIO) {
            listOf("mp4a.40.2")
        } else {
            listOf("avc1.4d401f")
        },
        expiresAtEpochMs = expiresAtEpochMs,
    )
}
