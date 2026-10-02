package com.alal.yft.core.download

import com.alal.yft.core.model.download.AudioVideoMuxCheckpoint
import com.alal.yft.core.model.download.AudioVideoMuxStage
import com.alal.yft.core.model.download.DashTransferCheckpoint
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.HlsTransferCheckpoint
import com.alal.yft.core.model.download.StreamChunkCheckpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckpointPayloadCodecTest {
    @Test
    fun `stream checkpoints round trip as bounded non-sensitive payloads`() {
        val hls = HlsTransferCheckpoint(
            manifestFingerprint = "a".repeat(64),
            chunks = chunks(7, complete = true),
        )
        val dash = DashTransferCheckpoint(
            manifestFingerprint = "b".repeat(64),
            chunks = chunks(11, complete = false),
        )

        val encodedHls = CheckpointPayloadCodec.encode(hls)
        val encodedDash = CheckpointPayloadCodec.encode(dash)

        assertEquals(hls, CheckpointPayloadCodec.decode(DownloadPlanType.HLS, encodedHls))
        assertEquals(dash, CheckpointPayloadCodec.decode(DownloadPlanType.DASH, encodedDash))
        assertTrue(encodedHls.orEmpty().none { it == '?' || it == '&' })
        assertTrue(encodedDash.orEmpty().none { it == '?' || it == '&' })
    }

    @Test
    fun `mux checkpoint round trips both tracks and stage`() {
        val checkpoint = AudioVideoMuxCheckpoint(
            video = DashTransferCheckpoint(
                manifestFingerprint = "c".repeat(64),
                chunks = chunks(13, complete = true),
            ),
            audio = DashTransferCheckpoint(
                manifestFingerprint = "d".repeat(64),
                chunks = chunks(5, complete = true),
            ),
            videoReady = true,
            audioReady = true,
            stage = AudioVideoMuxStage.READY_TO_MUX,
        )

        val encoded = CheckpointPayloadCodec.encode(checkpoint)

        assertEquals(
            checkpoint,
            CheckpointPayloadCodec.decode(DownloadPlanType.AUDIO_VIDEO_MUX, encoded),
        )
    }

    @Test
    fun `malformed or mismatched payload fails closed`() {
        val mux = AudioVideoMuxCheckpoint()
        val direct = DirectTransferCheckpoint(
            totalBytes = null,
            entityTag = null,
            lastModified = null,
            segments = emptyList(),
        )

        assertNull(CheckpointPayloadCodec.encode(direct))
        assertNull(CheckpointPayloadCodec.decode(DownloadPlanType.DIRECT, "v1\npayload"))
        assertNull(CheckpointPayloadCodec.decode(DownloadPlanType.HLS, "v9\n-:"))
        assertNull(
            CheckpointPayloadCodec.decode(
                DownloadPlanType.HLS,
                CheckpointPayloadCodec.encode(mux),
            ),
        )
        assertNull(CheckpointPayloadCodec.decode(DownloadPlanType.DASH, "v1\nbad:0,-1,1"))
    }

    private fun chunks(bytes: Long, complete: Boolean): List<StreamChunkCheckpoint> =
        listOf(
            StreamChunkCheckpoint(
                index = 0,
                downloadedBytes = bytes,
                completed = complete,
            ),
        )
}