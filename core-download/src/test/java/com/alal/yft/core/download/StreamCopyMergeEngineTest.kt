package com.alal.yft.core.download

import com.alal.yft.core.model.download.AudioVideoMuxDownloadPlan
import com.alal.yft.core.model.download.AudioVideoMuxResult
import com.alal.yft.core.model.download.DashDownloadPlan
import com.alal.yft.core.model.download.DashTransferCheckpoint
import com.alal.yft.core.model.download.DashTransferResult
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.download.StreamChunkCheckpoint
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaTrackType
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * P35 through the whole merge step: the engine merges two finished MP4 tracks by the stream
 * copy straight into the destination's file, and its one log line says how (path, sample
 * counts, the time of each phase, the merge thread's CPU time beside the wall time).
 */
class StreamCopyMergeEngineTest {
    @get:Rule
    val directory = TemporaryFolder()

    @Test
    fun `the merge line names the stream copy, its samples, phases and CPU time`() = runTest {
        val video = Mp4TestFiles.fragmented(Mp4TestFiles.video())
        val audio = Mp4TestFiles.fragmented(Mp4TestFiles.audio())
        val lines = mutableListOf<String>()
        val clock = AtomicLong()
        val cpu = AtomicLong()
        val parent = directory.newFolder("output")
        val completed = File(parent, "movie.mp4")
        val destination = FileDownloadDestination(File(parent, "movie.mp4.part"), completed)
        val engine = AudioVideoMuxEngine(
            dashTransfer = BytesTrackRunner(video, audio),
            muxer = StreamCopyAudioVideoMuxer(NoFallback, { _, _, _, _ -> null }),
            workspaceRoot = directory.newFolder("workspace"),
            clock = { 100 },
            sdkInt = 34,
            freeBytes = { null },
            elapsedMillis = { clock.addAndGet(1_500) },
            log = { line -> synchronized(lines) { lines += line } },
            threadCpuMillis = { cpu.addAndGet(400) },
        )

        val result = engine.transfer(plan = plan(), destination = destination)

        assertTrue("$result $lines", result is AudioVideoMuxResult.Completed)
        val done = lines.single { it.startsWith("Merge done") }
        val time = "[0-9.]+ m?s"
        assertTrue(
            done,
            Regex(
                "Merge done \\(in place\\): video $time, audio $time, merge 1.5 s, " +
                    "sync 1.5 s, commit 1.5 s; [0-9]+ KB; " +
                    "stream copy: 90 video \\+ 129 audio samples, parse $time, data $time, " +
                    "index $time, check $time; cpu 400 ms, wall 1.5 s",
            ).matches(done),
        )
        assertTrue("$lines", lines.none { "https" in it || "token" in it || "/" in it })
        val merged = Mp4Dump(completed.readBytes())
        assertEquals(listOf(90, 129), merged.tracks.map { it.samples.size })
    }

    /** Today's way must not run: the stream copy merges these tracks. */
    private object NoFallback : ProgressAudioVideoMuxer {
        override fun mux(
            videoFile: File,
            audioFile: File,
            outputFile: File,
            outputMimeType: String,
        ): LocalMuxResult = fail("today's way ran") as Nothing

        override fun mux(
            videoFile: File,
            audioFile: File,
            output: MuxOutput,
            outputMimeType: String,
            onProgress: MuxProgressListener,
        ): MuxAttempt = fail("today's way ran") as Nothing
    }

    /** A finished track download with the given bytes. */
    private class BytesTrackRunner(
        private val video: ByteArray,
        private val audio: ByteArray,
    ) : DashTransferRunner {
        override suspend fun transfer(
            plan: DashDownloadPlan,
            destination: DownloadDestination,
            resumeFrom: DashTransferCheckpoint?,
            onProgress: suspend (DownloadProgress) -> Unit,
            onCheckpoint: suspend (DashTransferCheckpoint) -> Unit,
        ): DashTransferResult {
            val isVideo = plan.trackType == MediaTrackType.VIDEO
            val bytes = if (isVideo) video else audio
            onProgress(DownloadProgress(0, bytes.size.toLong()))
            destination.prepare(bytes.size.toLong())
            destination.open().use { output ->
                output.write(0, bytes, 0, bytes.size)
                output.sync()
            }
            destination.commit()
            val checkpoint = DashTransferCheckpoint(
                manifestFingerprint = (if (isVideo) "a" else "b").repeat(64),
                chunks = listOf(
                    StreamChunkCheckpoint(
                        index = 0,
                        downloadedBytes = bytes.size.toLong(),
                        completed = true,
                    ),
                ),
            )
            onCheckpoint(checkpoint)
            return DashTransferResult.Completed(bytes.size.toLong(), checkpoint)
        }

        override suspend fun discard(plan: DashDownloadPlan) = Unit
    }

    private fun plan(): AudioVideoMuxDownloadPlan = AudioVideoMuxDownloadPlan(
        taskId = "stream-copy-task",
        video = trackPlan("video-task", MediaTrackType.VIDEO, "video/mp4", "avc1.4d401f"),
        audio = trackPlan("audio-task", MediaTrackType.AUDIO, "audio/mp4", "mp4a.40.2"),
        suggestedFileName = "movie.mp4",
    )

    private fun trackPlan(
        id: String,
        type: MediaTrackType,
        mimeType: String,
        codec: String,
    ): DashDownloadPlan = DashDownloadPlan(
        taskId = id,
        manifestUrl = "https://media.example.test/manifest.mpd",
        representationId = id,
        trackType = type,
        suggestedFileName = "$id.mp4",
        requestContext = BrowserRequestContext(
            pageUrl = "https://page.example.test/watch",
            userAgent = "YFT P35 test",
            cookie = null,
        ),
        mimeType = mimeType,
        codecs = listOf(codec),
    )
}
