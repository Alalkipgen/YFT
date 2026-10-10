package com.alal.yft.core.download

import com.alal.yft.core.model.download.AudioVideoMuxDownloadPlan
import com.alal.yft.core.model.download.DashDownloadPlan
import com.alal.yft.core.model.download.DashTransferCheckpoint
import com.alal.yft.core.model.download.DashTransferResult
import com.alal.yft.core.model.download.DownloadFailure
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.download.HlsDownloadPlan
import com.alal.yft.core.model.download.HlsTransferCheckpoint
import com.alal.yft.core.model.download.HlsTransferResult
import com.alal.yft.core.model.download.StreamChunkCheckpoint
import com.alal.yft.core.model.download.WholeFileTrack
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaTrackType
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** P50: the tracks of a merge that are HLS playlists are fetched as HLS. */
class PlaylistTrackTransferRunnerTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun `an HLS track is fetched as a playlist and its checkpoints carry over`() = runTest {
        val dash = RecordingDash()
        val hls = RecordingHls(
            result = HlsTransferResult.Completed(bytesWritten = 42, checkpoint = done),
        )
        val runner = PlaylistTrackTransferRunner(dash = dash, hls = hls)
        val saved = mutableListOf<DashTransferCheckpoint>()

        val result = runner.transfer(
            plan = track(hlsPlaylist = true),
            destination = destination(),
            resumeFrom = DashTransferCheckpoint(null, listOf(StreamChunkCheckpoint(0, 5, true))),
            onProgress = {},
            onCheckpoint = { saved += it },
        )

        val carried = DashTransferCheckpoint(null, done.chunks)
        assertEquals(DashTransferResult.Completed(42, carried), result)
        val plan = hls.plans.single()
        assertEquals("https://video.example.test/pl/720/video.m3u8", plan.playlistUrl)
        assertEquals("task-video", plan.taskId)
        assertEquals(listOf(StreamChunkCheckpoint(0, 5, true)), hls.resumes.single()?.chunks)
        assertEquals(listOf(carried), saved)
        assertTrue(dash.plans.isEmpty())

        runner.discard(track(hlsPlaylist = true))
        assertEquals(listOf("task-video"), hls.discarded)
    }

    @Test
    fun `a playlist failure keeps its checkpoint and other tracks stay with DASH`() = runTest {
        val failure = DownloadFailure(DownloadFailureReason.NETWORK)
        val hls = RecordingHls(result = HlsTransferResult.Failure(failure, done))
        val dash = RecordingDash()
        val runner = PlaylistTrackTransferRunner(dash = dash, hls = hls)

        val failed = runner.transfer(track(hlsPlaylist = true), destination())
        val file = runner.transfer(track(hlsPlaylist = false), destination())
        runner.discard(track(hlsPlaylist = false))

        val kept = DashTransferCheckpoint(null, done.chunks)
        assertEquals(DashTransferResult.Failure(failure, kept), failed)
        val nothing = DashTransferCheckpoint(null, emptyList())
        assertEquals(DashTransferResult.Completed(7, nothing), file)
        assertEquals(1, dash.plans.size)
        assertEquals(1, dash.discarded)
        assertTrue(hls.discarded.isEmpty())
    }

    @Test
    fun `HLS tracks merge by their codecs and never as one file`() {
        val plan = AudioVideoMuxDownloadPlan(
            taskId = "task",
            video = track(hlsPlaylist = true),
            audio = track(hlsPlaylist = true).copy(
                taskId = "task-audio",
                manifestUrl = "https://video.example.test/pl/mp4a/audio.m3u8",
                trackType = MediaTrackType.AUDIO,
                codecs = listOf("mp4a.40.2"),
            ),
            suggestedFileName = "clip.mp4",
        )
        val hevc = plan.copy(video = plan.video.copy(codecs = listOf("hvc1.1.6.L93.B0")))
        val ac3 = plan.copy(audio = plan.audio.copy(codecs = listOf("ac-3")))

        assertEquals(MuxCompatibility.Compatible, AudioVideoMuxCompatibility.evaluate(plan, 24))
        assertTrue(AudioVideoMuxCompatibility.evaluate(hevc, 24) is MuxCompatibility.Incompatible)
        assertTrue(AudioVideoMuxCompatibility.evaluate(ac3, 24) is MuxCompatibility.Incompatible)
        val notPlaylist = plan.video.copy(hlsPlaylist = false)
        assertTrue(
            AudioVideoMuxCompatibility.evaluate(plan.copy(video = notPlaylist), 24) is
                MuxCompatibility.Incompatible,
        )
        assertTrue(
            runCatching { plan.video.copy(wholeFile = WholeFileTrack()) }.isFailure,
        )
    }

    private val done = HlsTransferCheckpoint(
        manifestFingerprint = null,
        chunks = listOf(StreamChunkCheckpoint(0, 5, true), StreamChunkCheckpoint(1, 9, true)),
    )

    private fun track(hlsPlaylist: Boolean) = DashDownloadPlan(
        taskId = "task-video",
        manifestUrl = "https://video.example.test/pl/720/video.m3u8",
        representationId = "video",
        trackType = MediaTrackType.VIDEO,
        suggestedFileName = "clip.video.mp4",
        requestContext = BrowserRequestContext(
            pageUrl = "https://x.com/user/status/1",
            userAgent = "fixture-agent",
            cookie = null,
        ),
        mimeType = "application/x-mpegURL",
        codecs = listOf("avc1.64001f"),
        hlsPlaylist = hlsPlaylist,
    )

    private fun destination(): DownloadDestination {
        val parent = temporary.newFolder()
        return FileDownloadDestination(File(parent, "track.part"), File(parent, "track"))
    }

    private class RecordingHls(private val result: HlsTransferResult) : HlsTransferRunner {
        val plans = mutableListOf<HlsDownloadPlan>()
        val resumes = mutableListOf<HlsTransferCheckpoint?>()
        val discarded = mutableListOf<String>()

        override suspend fun transfer(
            plan: HlsDownloadPlan,
            destination: DownloadDestination,
            resumeFrom: HlsTransferCheckpoint?,
            onProgress: suspend (DownloadProgress) -> Unit,
            onCheckpoint: suspend (HlsTransferCheckpoint) -> Unit,
        ): HlsTransferResult {
            plans += plan
            resumes += resumeFrom
            if (result is HlsTransferResult.Completed) onCheckpoint(result.checkpoint)
            return result
        }

        override suspend fun discard(plan: HlsDownloadPlan) {
            discarded += plan.taskId
        }
    }

    private class RecordingDash : DashTransferRunner {
        val plans = mutableListOf<DashDownloadPlan>()
        var discarded = 0

        override suspend fun transfer(
            plan: DashDownloadPlan,
            destination: DownloadDestination,
            resumeFrom: DashTransferCheckpoint?,
            onProgress: suspend (DownloadProgress) -> Unit,
            onCheckpoint: suspend (DashTransferCheckpoint) -> Unit,
        ): DashTransferResult {
            plans += plan
            return DashTransferResult.Completed(7, DashTransferCheckpoint(null, emptyList()))
        }

        override suspend fun discard(plan: DashDownloadPlan) {
            discarded++
        }
    }
}
