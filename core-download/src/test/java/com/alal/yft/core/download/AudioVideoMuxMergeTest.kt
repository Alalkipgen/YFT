package com.alal.yft.core.download

import com.alal.yft.core.model.download.AudioVideoMuxCheckpoint
import com.alal.yft.core.model.download.AudioVideoMuxDownloadPlan
import com.alal.yft.core.model.download.AudioVideoMuxResult
import com.alal.yft.core.model.download.AudioVideoMuxStage
import com.alal.yft.core.model.download.DashDownloadPlan
import com.alal.yft.core.model.download.DashTransferCheckpoint
import com.alal.yft.core.model.download.DashTransferResult
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadFailureStage
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.download.StreamChunkCheckpoint
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaTrackType
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The step after a merged download's tracks are in (P27): its progress, the merge straight into
 * the destination's file, the fallback to app storage, the space check and the step times.
 */
class AudioVideoMuxMergeTest {
    @get:Rule
    val directory = TemporaryFolder()

    private val workspace by lazy { directory.newFolder("workspace") }

    @Test
    fun `progress rises through the merge and the copy to 100 and never stays at 99`() = runTest {
        val seen = SeenCheckpoints()
        val muxer = StepMuxer(steps = 10, seen = seen)
        val files = files("progress.mp4")

        // Android 7.x: today's path, so both the merge and the copy report.
        val result = engine(muxer, sdkInt = 25, bufferBytes = 1_024).transfer(
            plan = plan(),
            destination = files.destination,
            onCheckpoint = { seen.add(it) },
        )

        assertTrue("$result", result is AudioVideoMuxResult.Completed)
        val merging = seen.percents(AudioVideoMuxStage.MUXING).filterNotNull().distinct()
        assertEquals((1..10).map { it * 10 }, merging)
        val saving = seen.percents(AudioVideoMuxStage.SAVING).filterNotNull()
        assertTrue("copy steps: $saving", saving.distinct().size >= 50)
        assertEquals(saving.sorted(), saving)
        assertEquals(100, saving.last())
        val stages = seen.stages()
        assertEquals(AudioVideoMuxStage.COMPLETED, stages.last())
        assertTrue(
            "$stages",
            stages.lastIndexOf(AudioVideoMuxStage.MUXING) <
                stages.indexOf(AudioVideoMuxStage.SAVING),
        )
        assertArrayEquals(OUTPUT, files.completed.readBytes())
    }

    @Test
    fun `a destination with a file descriptor gets the merge in one write, no second copy`() =
        runTest {
            val muxer = StepMuxer()
            val files = files("in-place.mp4")
            val destination = CountingDestination(files.destination)

            val result = engine(muxer, sdkInt = 34).transfer(
                plan = plan(),
                destination = destination,
            )

            assertEquals(listOf("descriptor"), muxer.outputs)
            assertEquals("copies through open()", 0, destination.opens)
            assertEquals(1, destination.descriptors)
            assertEquals(
                OUTPUT.size.toLong(),
                (result as AudioVideoMuxResult.Completed).bytesWritten,
            )
            assertArrayEquals(OUTPUT, files.completed.readBytes())
            assertFalse(files.partial.exists())
            assertTrue(workspace.listFiles().orEmpty().isEmpty())
        }

    @Test
    fun `without a file descriptor the merge goes through app storage and is copied`() =
        runTest {
            // A destination that gives none, and Android 7.x with one that does.
            listOf(34 to false, 25 to true).forEach { (sdkInt, givesDescriptor) ->
                val muxer = StepMuxer()
                val files = files("copy-$sdkInt.mp4")
                var tracksAtCopy = -1
                val destination = CountingDestination(files.destination, givesDescriptor) {
                    tracksAtCopy = workspace.walkTopDown().count { it.name.endsWith(".ready") }
                }

                val result = engine(muxer, sdkInt).transfer(
                    plan = plan(),
                    destination = destination,
                )

                assertTrue("$result", result is AudioVideoMuxResult.Completed)
                assertEquals(listOf("file"), muxer.outputs)
                assertEquals(1, destination.opens)
                assertEquals(0, destination.descriptors)
                assertEquals("tracks left when the copy starts", 0, tracksAtCopy)
                assertArrayEquals(OUTPUT, files.completed.readBytes())
                assertTrue(workspace.listFiles().orEmpty().isEmpty())
            }
        }

    @Test
    fun `an in-place merge that fails before its first sample falls back once`() = runTest {
        val muxer = StepMuxer(failInPlaceAfter = 0)
        val lines = mutableListOf<String>()
        val files = files("fallback.mp4")

        val result = engine(muxer, log = { synchronized(lines) { lines += it } }).transfer(
            plan = plan(),
            destination = files.destination,
        )

        assertTrue("$result", result is AudioVideoMuxResult.Completed)
        assertEquals(listOf("descriptor", "file"), muxer.outputs)
        assertArrayEquals(OUTPUT, files.completed.readBytes())
        assertTrue(
            "$lines",
            lines.any { it.startsWith("In-place merge failed before its first sample") },
        )
        assertTrue("$lines", lines.any { it.startsWith("Merge done (copy after in place)") })
    }

    @Test
    fun `an in-place merge that fails after a sample does not fall back`() = runTest {
        val muxer = StepMuxer(failInPlaceAfter = 2)
        val files = files("no-fallback.mp4")

        val result = engine(muxer).transfer(
            plan = plan(),
            destination = files.destination,
        ) as AudioVideoMuxResult.Failure

        assertEquals(listOf("descriptor"), muxer.outputs)
        assertEquals(DownloadFailureReason.STORAGE_UNAVAILABLE, result.failure.reason)
        assertEquals(DownloadFailureStage.MERGE, result.failure.stage)
        // The tracks stay for a retry, which merges again without downloading them.
        assertEquals(AudioVideoMuxStage.READY_TO_MUX, result.checkpoint.stage)
        assertFalse(files.completed.exists())
    }

    @Test
    fun `the space check fails early with insufficient storage at the merge`() = runTest {
        val trackBytes = (VIDEO_BYTES.size + AUDIO_BYTES.size).toLong()
        val inPlaceNeed = trackBytes + 8L * 1_024 * 1_024
        // Enough for a merge in place, not for today's path, which needs twice the tracks.
        val fits = engine(StepMuxer(), sdkInt = 34, freeBytes = { inPlaceNeed }).transfer(
            plan = plan(),
            destination = files("fits.mp4").destination,
        )
        assertTrue("$fits", fits is AudioVideoMuxResult.Completed)

        listOf(25 to inPlaceNeed, 34 to trackBytes).forEach { (sdkInt, free) ->
            val muxer = StepMuxer()
            val files = files("full-$sdkInt.mp4")

            val result = engine(muxer, sdkInt, freeBytes = { free }).transfer(
                plan = plan(),
                destination = files.destination,
            )

            assertTrue("$result", result is AudioVideoMuxResult.Failure)
            val failure = (result as AudioVideoMuxResult.Failure).failure
            assertEquals(DownloadFailureReason.INSUFFICIENT_STORAGE, failure.reason)
            assertEquals(DownloadFailureStage.MERGE, failure.stage)
            assertTrue("${failure.detail}", failure.detail.orEmpty().startsWith("Merging needs"))
            assertTrue(muxer.outputs.isEmpty())
            assertFalse("no space is used yet", files.partial.exists())
            assertFalse(files.completed.exists())
            assertEquals(AudioVideoMuxStage.READY_TO_MUX, result.checkpoint.stage)
        }
    }

    @Test
    fun `each step's time goes to the log and into a merge failure's details`() = runTest {
        val clock = AtomicLong()
        val lines = mutableListOf<String>()
        val log: (String) -> Unit = { synchronized(lines) { lines += it } }
        val elapsed = { clock.addAndGet(1_500) }

        engine(StepMuxer(), log = log, elapsedMillis = elapsed).transfer(
            plan = plan(),
            destination = files("timed.mp4").destination,
        )
        val failed = engine(StepMuxer(failInPlaceAfter = 2), log = log, elapsedMillis = elapsed)
            .transfer(plan = plan(), destination = files("timed-failure.mp4").destination)

        val done = lines.single { it.startsWith("Merge done") }
        val track = "[0-9.]+ s"
        assertTrue(
            done,
            Regex(
                "Merge done \\(in place\\): video $track, audio $track, merge 1.5 s, " +
                    "sync 1.5 s, commit 1.5 s; [0-9]+ KB",
            ).matches(done),
        )
        val detail = (failed as AudioVideoMuxResult.Failure).failure.detail.orEmpty()
        assertTrue(
            detail,
            Regex("Muxer stopped; took video $track, audio $track, merge 1.5 s").matches(detail),
        )
        assertTrue(
            "$lines",
            lines.any { it.startsWith("Merge failed (STORAGE_UNAVAILABLE, MERGE): video ") },
        )
        assertTrue("$lines", lines.none { "https" in it || "token" in it || "/" in it })
    }

    @Test
    fun `a stopped download stops the merge at its next sample`() = runTest {
        val muxer = EndlessMuxer()
        val files = files("stopped.mp4")
        val engine = engine(muxer)

        val job = launch(Dispatchers.IO) {
            engine.transfer(plan = plan(), destination = files.destination)
        }
        assertTrue("merge started", muxer.started.await(5, TimeUnit.SECONDS))
        job.cancel()
        job.join()

        assertTrue("${muxer.stoppedBy}", muxer.stoppedBy is CancellationException)
        assertFalse(files.completed.exists())
    }

    private fun engine(
        muxer: LocalAudioVideoMuxer,
        sdkInt: Int = 34,
        bufferBytes: Int = 64 * 1_024,
        freeBytes: () -> Long? = { null },
        log: (String) -> Unit = {},
        elapsedMillis: () -> Long = { 0L },
    ): AudioVideoMuxEngine = AudioVideoMuxEngine(
        dashTransfer = TrackRunner(),
        muxer = muxer,
        workspaceRoot = workspace,
        bufferBytes = bufferBytes,
        clock = { 100 },
        sdkInt = sdkInt,
        freeBytes = freeBytes,
        elapsedMillis = elapsedMillis,
        log = log,
    )

    private fun files(name: String): DestinationFiles {
        val parent = directory.newFolder("output-${name.substringBefore('.')}")
        val partial = File(parent, "$name.part")
        val completed = File(parent, name)
        return DestinationFiles(partial, completed, FileDownloadDestination(partial, completed))
    }

    private fun plan(): AudioVideoMuxDownloadPlan = AudioVideoMuxDownloadPlan(
        taskId = "merge-task",
        video = trackPlan("video-task", MediaTrackType.VIDEO, "video/mp4", "avc1.4d401f"),
        audio = trackPlan("audio-task", MediaTrackType.AUDIO, "audio/mp4", "mp4a.40.2"),
        suggestedFileName = "movie.mp4",
    )

    private fun trackPlan(
        taskId: String,
        type: MediaTrackType,
        mimeType: String,
        codec: String,
    ): DashDownloadPlan = DashDownloadPlan(
        taskId = taskId,
        manifestUrl = "https://media.example.test/manifest.mpd?token=private",
        representationId = "$taskId-rep",
        trackType = type,
        suggestedFileName = "$taskId.mp4",
        requestContext = BrowserRequestContext(
            pageUrl = "https://page.example.test/watch",
            userAgent = "YFT merge fixture",
            cookie = null,
        ),
        mimeType = mimeType,
        codecs = listOf(codec),
    )

    /** The checkpoints a download reported, for the merge to wait until its step is seen. */
    private class SeenCheckpoints {
        private val lock = Object()
        private val checkpoints = mutableListOf<AudioVideoMuxCheckpoint>()

        fun add(checkpoint: AudioVideoMuxCheckpoint) = synchronized(lock) {
            checkpoints += checkpoint
            lock.notifyAll()
        }

        /** Waits until the merge's [done] is in a checkpoint; false after [timeoutMs]. */
        fun awaitMerged(done: Long, timeoutMs: Long = 2_000): Boolean = synchronized(lock) {
            val deadline = System.currentTimeMillis() + timeoutMs
            val merged = { checkpoint: AudioVideoMuxCheckpoint ->
                checkpoint.stage == AudioVideoMuxStage.MUXING && checkpoint.stepDone == done
            }
            while (checkpoints.none(merged)) {
                val left = deadline - System.currentTimeMillis()
                if (left <= 0) return false
                lock.wait(left)
            }
            true
        }

        fun percents(stage: AudioVideoMuxStage): List<Int?> = synchronized(lock) {
            checkpoints.filter { it.stage == stage }.map { it.stepPercent }
        }

        fun stages(): List<AudioVideoMuxStage> = synchronized(lock) {
            checkpoints.map { it.stage }.fold(mutableListOf()) { list, stage ->
                if (list.lastOrNull() != stage) list += stage
                list
            }
        }
    }

    /**
     * Merges into [OUTPUT] in [steps] samples, each waiting until the download shows it; the
     * merge into a descriptor can fail after [failInPlaceAfter] samples (0: before its first,
     * having written a header, as MediaMuxer does).
     */
    private class StepMuxer(
        private val steps: Int = 4,
        private val seen: SeenCheckpoints? = null,
        private val failInPlaceAfter: Int? = null,
    ) : ProgressAudioVideoMuxer {
        val outputs = mutableListOf<String>()
        private var waiting = true

        override fun mux(
            videoFile: File,
            audioFile: File,
            outputFile: File,
            outputMimeType: String,
        ): LocalMuxResult {
            outputs += "file without progress"
            outputFile.writeBytes(OUTPUT)
            return LocalMuxResult.Completed(OUTPUT.size.toLong())
        }

        override fun mux(
            videoFile: File,
            audioFile: File,
            output: MuxOutput,
            outputMimeType: String,
            onProgress: MuxProgressListener,
        ): MuxAttempt {
            assertArrayEquals(VIDEO_BYTES, videoFile.readBytes())
            assertArrayEquals(AUDIO_BYTES, audioFile.readBytes())
            outputs += if (output is MuxOutput.ToFile) "file" else "descriptor"
            val failAfter = failInPlaceAfter.takeIf { output is MuxOutput.ToDescriptor }
            if (failAfter == 0) {
                output.write(ByteArray(OUTPUT.size * 2) { 7 })
                return MuxAttempt(failed(), samplesWritten = 0)
            }
            for (step in 1..steps) {
                if (failAfter != null && step > failAfter) {
                    return MuxAttempt(failed(), samplesWritten = (step - 1).toLong())
                }
                onProgress.onProgress(step * 10L, steps * 10L)
                if (waiting && seen != null) waiting = seen.awaitMerged(step * 10L)
            }
            output.write(OUTPUT)
            return MuxAttempt(LocalMuxResult.Completed(OUTPUT.size.toLong()), steps.toLong())
        }

        private fun failed() = LocalMuxResult.Failure(
            DownloadFailureReason.STORAGE_UNAVAILABLE,
            detail = "Muxer stopped",
        )

        private fun MuxOutput.write(bytes: ByteArray) = when (this) {
            is MuxOutput.ToFile -> file.writeBytes(bytes)
            // The descriptor stays open: its owner closes it.
            is MuxOutput.ToDescriptor -> FileOutputStream(descriptor).write(bytes)
        }
    }

    /** Reports progress until the download is stopped (the engine then throws), 10 s at most. */
    private class EndlessMuxer : ProgressAudioVideoMuxer {
        val started = CountDownLatch(1)

        @Volatile
        var stoppedBy: Throwable? = null

        override fun mux(
            videoFile: File,
            audioFile: File,
            outputFile: File,
            outputMimeType: String,
        ): LocalMuxResult = LocalMuxResult.Failure(DownloadFailureReason.INCOMPATIBLE_TRACKS)

        override fun mux(
            videoFile: File,
            audioFile: File,
            output: MuxOutput,
            outputMimeType: String,
            onProgress: MuxProgressListener,
        ): MuxAttempt {
            try {
                for (sample in 1L..10_000L) {
                    onProgress.onProgress(sample, 1_000_000)
                    started.countDown()
                    Thread.sleep(1)
                }
            } catch (stop: Throwable) {
                stoppedBy = stop
                throw stop
            }
            return MuxAttempt(LocalMuxResult.Failure(DownloadFailureReason.NETWORK), 10_000)
        }
    }

    /** Counts the copies through [open] and the descriptors given; may give none. */
    private class CountingDestination(
        private val delegate: FileDownloadDestination,
        private val givesDescriptor: Boolean = true,
        private val onOpen: () -> Unit = {},
    ) : DownloadDestination by delegate {
        var opens = 0
        var descriptors = 0

        override fun open(): SeekableDownloadOutput {
            opens += 1
            onOpen()
            return delegate.open()
        }

        override fun openFileDescriptorOutput(): FileDescriptorOutput? {
            if (!givesDescriptor) return null
            descriptors += 1
            return delegate.openFileDescriptorOutput()
        }
    }

    /** Downloads each track at once into its file. */
    private class TrackRunner : DashTransferRunner {
        override suspend fun transfer(
            plan: DashDownloadPlan,
            destination: DownloadDestination,
            resumeFrom: DashTransferCheckpoint?,
            onProgress: suspend (DownloadProgress) -> Unit,
            onCheckpoint: suspend (DashTransferCheckpoint) -> Unit,
        ): DashTransferResult {
            val video = plan.trackType == MediaTrackType.VIDEO
            val bytes = if (video) VIDEO_BYTES else AUDIO_BYTES
            onProgress(DownloadProgress(0, bytes.size.toLong()))
            destination.prepare(bytes.size.toLong())
            destination.open().use { output ->
                output.write(0, bytes, 0, bytes.size)
                output.sync()
            }
            destination.commit()
            val checkpoint = DashTransferCheckpoint(
                manifestFingerprint = (if (video) "a" else "b").repeat(64),
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

    private data class DestinationFiles(
        val partial: File,
        val completed: File,
        val destination: FileDownloadDestination,
    )

    private companion object {
        val VIDEO_BYTES = "video-track".repeat(20).toByteArray()
        val AUDIO_BYTES = "audio-track".repeat(10).toByteArray()

        /** About 64 KiB, so a 1 KiB copy buffer moves the copy on in many steps. */
        val OUTPUT = ("mux[".toByteArray() + VIDEO_BYTES + "|".toByteArray() + AUDIO_BYTES)
            .let { unit -> ByteArray(unit.size * 200) { index -> unit[index % unit.size] } }
    }
}
