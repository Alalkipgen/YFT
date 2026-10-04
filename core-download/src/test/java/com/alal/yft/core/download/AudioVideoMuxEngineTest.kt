package com.alal.yft.core.download

import com.alal.yft.core.model.download.AudioVideoMuxDownloadPlan
import com.alal.yft.core.model.download.AudioVideoMuxResult
import com.alal.yft.core.model.download.AudioVideoMuxStage
import com.alal.yft.core.model.download.DashDownloadPlan
import com.alal.yft.core.model.download.DashTransferCheckpoint
import com.alal.yft.core.model.download.DashTransferResult
import com.alal.yft.core.model.download.DownloadFailure
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.download.StreamChunkCheckpoint
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaTrackType
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioVideoMuxEngineTest {
    @get:Rule
    val directory = TemporaryFolder()

    @Test
    fun `compatibility accepts AVC AAC MP4 tracks`() {
        val result = AudioVideoMuxCompatibility.evaluate(plan())

        assertEquals(MuxCompatibility.Compatible, result)
    }

    @Test
    fun `compatibility rejects unsupported container and codec explicitly`() {
        val webm = plan().copy(
            video = plan().video.copy(
                mimeType = "video/webm",
                codecs = listOf("vp09.00.10.08"),
            ),
        )
        val hevc = plan().copy(
            video = plan().video.copy(codecs = listOf("hvc1.1.6.L93.B0")),
        )
        val unknownAudio = plan().copy(audio = plan().audio.copy(codecs = emptyList()))

        assertEquals(
            MuxCompatibility.Incompatible(MuxIncompatibilityReason.VIDEO_CONTAINER),
            AudioVideoMuxCompatibility.evaluate(webm),
        )
        assertEquals(
            MuxCompatibility.Incompatible(MuxIncompatibilityReason.VIDEO_CODEC),
            AudioVideoMuxCompatibility.evaluate(hevc),
        )
        assertEquals(
            MuxCompatibility.Incompatible(MuxIncompatibilityReason.AUDIO_CODEC),
            AudioVideoMuxCompatibility.evaluate(unknownAudio),
        )
    }

    @Test
    fun `incompatible tracks fail before transfer or destination work`() = runTest {
        val runner = FakeDashRunner()
        val muxer = FakeMuxer()
        val workspace = directory.newFolder("incompatible-workspace")
        val files = files("incompatible.mp4")
        val incompatible = plan().copy(
            video = plan().video.copy(
                mimeType = "video/webm",
                codecs = listOf("vp09.00.10.08"),
            ),
        )

        val result = engine(runner, muxer, workspace).transfer(
            plan = incompatible,
            destination = files.destination,
        ) as AudioVideoMuxResult.Failure

        assertEquals(DownloadFailureReason.INCOMPATIBLE_TRACKS, result.failure.reason)
        assertEquals(0, runner.totalCalls())
        assertEquals(0, muxer.calls.get())
        assertFalse(files.partial.exists())
        assertFalse(files.completed.exists())
        assertTrue(workspace.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `compatible tracks mux and publish only completed output`() = runTest {
        val runner = FakeDashRunner()
        val muxer = FakeMuxer()
        val workspace = directory.newFolder("success-workspace")
        val files = files("success.mp4")
        val plan = plan()

        val result = engine(runner, muxer, workspace).transfer(
            plan = plan,
            destination = files.destination,
        ) as AudioVideoMuxResult.Completed

        val expected = muxer.outputFor(VIDEO_BYTES, AUDIO_BYTES)
        assertEquals(expected.size.toLong(), result.bytesWritten)
        assertEquals(AudioVideoMuxStage.COMPLETED, result.checkpoint.stage)
        assertArrayEquals(expected, files.completed.readBytes())
        assertFalse(files.partial.exists())
        assertEquals(1, runner.callsFor(plan.video))
        assertEquals(1, runner.callsFor(plan.audio))
        assertEquals(1, muxer.calls.get())
        assertTrue(workspace.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `retry resumes failed audio without downloading completed video again`() = runTest {
        val runner = FakeDashRunner(failFirstAudio = true)
        val muxer = FakeMuxer()
        val workspace = directory.newFolder("resume-workspace")
        val files = files("resume.mp4")
        val plan = plan()
        val engine = engine(runner, muxer, workspace)

        val first = engine.transfer(
            plan = plan,
            destination = files.destination,
        ) as AudioVideoMuxResult.Failure

        assertEquals(DownloadFailureReason.NETWORK, first.failure.reason)
        assertTrue(first.checkpoint.videoReady)
        assertFalse(first.checkpoint.audioReady)
        assertEquals(1, runner.callsFor(plan.video))
        assertEquals(1, runner.callsFor(plan.audio))
        assertEquals(0, muxer.calls.get())
        assertFalse(files.completed.exists())
        assertFalse(workspace.listFiles().orEmpty().isEmpty())

        val resumed = engine.transfer(
            plan = plan,
            destination = files.destination,
            resumeFrom = first.checkpoint,
        ) as AudioVideoMuxResult.Completed

        assertEquals(AudioVideoMuxStage.COMPLETED, resumed.checkpoint.stage)
        assertEquals(1, runner.callsFor(plan.video))
        assertEquals(2, runner.callsFor(plan.audio))
        assertEquals(1, muxer.calls.get())
        assertTrue(files.completed.isFile)
        assertTrue(workspace.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `progress has a total once both track lengths are known`() = runTest {
        val runner = FakeDashRunner(failFirstAudio = true, reportTotals = true)
        val workspace = directory.newFolder("totals-workspace")
        val files = files("totals.mp4")
        val plan = plan()
        val engine = engine(runner, FakeMuxer(), workspace)
        val total = (VIDEO_BYTES.size + AUDIO_BYTES.size).toLong()
        val firstProgress = mutableListOf<DownloadProgress>()

        val first = engine.transfer(
            plan = plan,
            destination = files.destination,
            onProgress = { firstProgress += it },
        ) as AudioVideoMuxResult.Failure

        assertNull(firstProgress.first().totalBytes)
        assertTrue(firstProgress.any { it.totalBytes == total })

        val resumedProgress = mutableListOf<DownloadProgress>()
        engine.transfer(
            plan = plan,
            destination = files.destination,
            resumeFrom = first.checkpoint,
            onProgress = { resumedProgress += it },
        ) as AudioVideoMuxResult.Completed

        assertEquals(total, resumedProgress.last().totalBytes)
        assertEquals(total, resumedProgress.last().downloadedBytes)
    }

    @Test
    fun `fatal mux failure never publishes and cleans track workspaces`() = runTest {
        val runner = FakeDashRunner()
        val muxer = FakeMuxer(failure = DownloadFailureReason.INCOMPATIBLE_TRACKS)
        val workspace = directory.newFolder("mux-failure-workspace")
        val files = files("mux-failure.mp4")
        val plan = plan()

        val result = engine(runner, muxer, workspace).transfer(
            plan = plan,
            destination = files.destination,
        ) as AudioVideoMuxResult.Failure

        assertEquals(DownloadFailureReason.INCOMPATIBLE_TRACKS, result.failure.reason)
        assertFalse(files.partial.exists())
        assertFalse(files.completed.exists())
        assertEquals(setOf(plan.video.taskId, plan.audio.taskId), runner.discarded.toSet())
        assertTrue(workspace.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `expired mux plan performs no transfer or storage work`() = runTest {
        val runner = FakeDashRunner()
        val muxer = FakeMuxer()
        val workspace = directory.newFolder("expired-workspace")
        val files = files("expired.mp4")
        val expired = plan(
            videoExpiry = 99,
            audioExpiry = 200,
        )

        val result = engine(runner, muxer, workspace).transfer(
            plan = expired,
            destination = files.destination,
        ) as AudioVideoMuxResult.Failure

        assertEquals(DownloadFailureReason.EXPIRED_URL, result.failure.reason)
        assertEquals(0, runner.totalCalls())
        assertEquals(0, muxer.calls.get())
        assertFalse(files.partial.exists())
        assertTrue(workspace.listFiles().orEmpty().isEmpty())
    }

    private fun engine(
        runner: FakeDashRunner,
        muxer: FakeMuxer,
        workspace: File,
    ): AudioVideoMuxEngine = AudioVideoMuxEngine(
        dashTransfer = runner,
        muxer = muxer,
        workspaceRoot = workspace,
        clock = { 100 },
    )

    private fun plan(
        videoExpiry: Long? = null,
        audioExpiry: Long? = null,
    ): AudioVideoMuxDownloadPlan = AudioVideoMuxDownloadPlan(
        taskId = "mux-task",
        video = trackPlan(
            taskId = "video-task",
            representationId = "v720",
            type = MediaTrackType.VIDEO,
            mimeType = "video/mp4",
            codecs = listOf("avc1.4d401f"),
            expiresAtEpochMs = videoExpiry,
        ),
        audio = trackPlan(
            taskId = "audio-task",
            representationId = "a-en",
            type = MediaTrackType.AUDIO,
            mimeType = "audio/mp4",
            codecs = listOf("mp4a.40.2"),
            expiresAtEpochMs = audioExpiry,
        ),
        suggestedFileName = "movie.mp4",
    )

    private fun trackPlan(
        taskId: String,
        representationId: String,
        type: MediaTrackType,
        mimeType: String,
        codecs: List<String>,
        expiresAtEpochMs: Long?,
    ): DashDownloadPlan = DashDownloadPlan(
        taskId = taskId,
        manifestUrl = "https://media.example.test/manifest.mpd?token=private",
        representationId = representationId,
        trackType = type,
        suggestedFileName = "$taskId.mp4",
        requestContext = BrowserRequestContext(
            pageUrl = "https://page.example.test/watch",
            userAgent = "YFT mux fixture",
            cookie = "session=private",
        ),
        mimeType = mimeType,
        codecs = codecs,
        expiresAtEpochMs = expiresAtEpochMs,
    )

    private fun files(name: String): DestinationFiles {
        val parent = directory.newFolder("output-${name.substringBefore('.')}")
        val partial = File(parent, "$name.part")
        val completed = File(parent, name)
        return DestinationFiles(
            partial = partial,
            completed = completed,
            destination = FileDownloadDestination(partial, completed),
        )
    }

    private class FakeDashRunner(
        private val failFirstAudio: Boolean = false,
        private val reportTotals: Boolean = false,
    ) : DashTransferRunner {
        private val calls = ConcurrentHashMap<String, AtomicInteger>()
        val discarded = mutableListOf<String>()

        override suspend fun transfer(
            plan: DashDownloadPlan,
            destination: DownloadDestination,
            resumeFrom: DashTransferCheckpoint?,
            onProgress: suspend (DownloadProgress) -> Unit,
            onCheckpoint: suspend (DashTransferCheckpoint) -> Unit,
        ): DashTransferResult {
            val attempt = calls.computeIfAbsent(plan.taskId) { AtomicInteger() }
                .incrementAndGet()
            val bytes = if (plan.trackType == MediaTrackType.VIDEO) {
                VIDEO_BYTES
            } else {
                AUDIO_BYTES
            }
            // Like a whole-file track, which knows its length before the first chunk arrives.
            if (reportTotals) onProgress(DownloadProgress(0, bytes.size.toLong()))
            if (
                failFirstAudio &&
                plan.trackType == MediaTrackType.AUDIO &&
                attempt == 1
            ) {
                val checkpoint = checkpoint(
                    fingerprint = "b",
                    bytes = 0,
                    completed = false,
                )
                onCheckpoint(checkpoint)
                return DashTransferResult.Failure(
                    failure = DownloadFailure(DownloadFailureReason.NETWORK),
                    checkpoint = checkpoint,
                )
            }

            destination.prepare(bytes.size.toLong())
            destination.open().use { output ->
                output.write(0, bytes, 0, bytes.size)
                output.sync()
            }
            destination.commit()
            val checkpoint = checkpoint(
                fingerprint = if (plan.trackType == MediaTrackType.VIDEO) "a" else "b",
                bytes = bytes.size.toLong(),
                completed = true,
            )
            onCheckpoint(checkpoint)
            return DashTransferResult.Completed(bytes.size.toLong(), checkpoint)
        }

        override suspend fun discard(plan: DashDownloadPlan) {
            synchronized(discarded) { discarded += plan.taskId }
        }

        fun callsFor(plan: DashDownloadPlan): Int = calls[plan.taskId]?.get() ?: 0

        fun totalCalls(): Int = calls.values.sumOf(AtomicInteger::get)

        private fun checkpoint(
            fingerprint: String,
            bytes: Long,
            completed: Boolean,
        ): DashTransferCheckpoint = DashTransferCheckpoint(
            manifestFingerprint = fingerprint.repeat(64),
            chunks = listOf(
                StreamChunkCheckpoint(
                    index = 0,
                    downloadedBytes = bytes,
                    completed = completed,
                ),
            ),
        )
    }

    private class FakeMuxer(
        private val failure: DownloadFailureReason? = null,
    ) : LocalAudioVideoMuxer {
        val calls = AtomicInteger()

        override fun mux(
            videoFile: File,
            audioFile: File,
            outputFile: File,
        ): LocalMuxResult {
            calls.incrementAndGet()
            failure?.let { return LocalMuxResult.Failure(it) }
            val bytes = outputFor(videoFile.readBytes(), audioFile.readBytes())
            outputFile.writeBytes(bytes)
            return LocalMuxResult.Completed(bytes.size.toLong())
        }

        fun outputFor(video: ByteArray, audio: ByteArray): ByteArray =
            "mux[".toByteArray() + video + "|".toByteArray() + audio + "]".toByteArray()
    }

    private data class DestinationFiles(
        val partial: File,
        val completed: File,
        val destination: FileDownloadDestination,
    )

    private companion object {
        val VIDEO_BYTES = "video-track".repeat(20).toByteArray()
        val AUDIO_BYTES = "audio-track".repeat(10).toByteArray()
    }
}
