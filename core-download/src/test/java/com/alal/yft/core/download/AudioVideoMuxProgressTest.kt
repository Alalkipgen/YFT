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
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A merged download shows the bytes its tracks write inside a range, before the range's
 * checkpoint, at most 4 times a second. Earlier the merge moved only on checkpoints, so a
 * long first range of a live recording showed 0 B for minutes.
 */
class AudioVideoMuxProgressTest {
    @get:Rule
    val directory = TemporaryFolder()

    @Test
    fun `a merged download shows the bytes a track writes before its checkpoint`() = runTest {
        val shown = CopyOnWriteArrayList<DownloadProgress>()
        var now = 0L
        val runner = InRangeRunner(
            videoSteps = listOf(64L, 128L, 192L),
            beforeStep = { now += 1_000 },
            shownBytes = { shown.lastOrNull()?.downloadedBytes ?: 0 },
        )

        val result = engine(runner, elapsed = { now }).transfer(
            plan = plan(),
            destination = destination("shown.mp4"),
            onProgress = { shown += it },
        )

        assertTrue(result is AudioVideoMuxResult.Completed)
        // Each step was on screen as soon as the track reported it, with no checkpoint yet.
        assertEquals(listOf(64L, 128L, 192L), runner.seenAfterSteps)
    }

    @Test
    fun `a merged download moves at most four times a second`() = runTest {
        val shown = CopyOnWriteArrayList<DownloadProgress>()
        var now = 0L
        val stepTimes = ArrayDeque(listOf(0L, 100L, 200L, 300L))
        val runner = InRangeRunner(
            videoSteps = listOf(64L, 128L, 192L, 220L),
            beforeStep = { now = stepTimes.removeFirst() },
            shownBytes = { shown.lastOrNull()?.downloadedBytes ?: 0 },
        )

        engine(runner, elapsed = { now }).transfer(
            plan = plan(),
            destination = destination("paced.mp4"),
            onProgress = { shown += it },
        )

        // 0 ms shows, 100 and 200 ms wait for the 250 ms pace, 300 ms shows again.
        assertEquals(listOf(64L, 64L, 64L, 220L), runner.seenAfterSteps)
    }

    @Test
    fun `a merged live adds its video's and its sound's estimated totals`() = runTest {
        val shown = CopyOnWriteArrayList<DownloadProgress>()
        val videoReady = CompletableDeferred<Unit>()
        val runner = LiveTracksRunner(shown = { shown.lastOrNull() }, videoReady = videoReady)

        val result = engine(runner, elapsed = { 0 }, progressIntervalMillis = 0).transfer(
            plan = plan(),
            destination = destination("live.mp4"),
            onProgress = { shown += it },
            onCheckpoint = { checkpoint -> if (checkpoint.videoReady) videoReady.complete(Unit) },
        )

        assertTrue(result is AudioVideoMuxResult.Completed)
        val seen = runner.seen
        // The video has an estimate, the sound not yet: no estimate.
        assertEquals(null, seen[0]?.estimatedTotalBytes)
        assertEquals(null, seen[1]?.estimatedTotalBytes)
        // Both have one: their sum, over the bytes of both.
        assertEquals(420L, seen[2]?.downloadedBytes)
        assertEquals(1_400L, seen[2]?.estimatedTotalBytes)
        assertEquals(null, seen[2]?.totalBytes)
        // The video is in: its real size stands in for its estimate.
        assertEquals(VIDEO_LIVE_LENGTH + 200L, seen[3]?.downloadedBytes)
        assertEquals(VIDEO_LIVE_LENGTH + 410L, seen[3]?.estimatedTotalBytes)
        // Both in: the real total, and no estimate.
        val last = shown.last()
        assertEquals(VIDEO_LIVE_LENGTH + AUDIO_LIVE_LENGTH.toLong(), last.totalBytes)
        assertEquals(null, last.estimatedTotalBytes)
    }

    private fun engine(
        runner: DashTransferRunner,
        elapsed: () -> Long,
        progressIntervalMillis: Long = 250,
    ) = AudioVideoMuxEngine(
        dashTransfer = runner,
        muxer = ConcatMuxer(),
        workspaceRoot = directory.newFolder(),
        clock = { 100 },
        sdkInt = 24,
        elapsedMillis = elapsed,
        progressIntervalMillis = progressIntervalMillis,
        log = {},
    )

    private fun destination(name: String): FileDownloadDestination {
        val parent = directory.newFolder()
        return FileDownloadDestination(File(parent, "$name.part"), File(parent, name))
    }

    private fun plan() = AudioVideoMuxDownloadPlan(
        taskId = "mux-progress",
        video = track("video", MediaTrackType.VIDEO, "video/mp4", "avc1.4d401f"),
        audio = track("audio", MediaTrackType.AUDIO, "audio/mp4", "mp4a.40.2"),
        suggestedFileName = "movie.mp4",
    )

    private fun track(id: String, type: MediaTrackType, mime: String, codec: String) =
        DashDownloadPlan(
            taskId = "$id-task",
            manifestUrl = "https://media.example.test/manifest.mpd",
            representationId = id,
            trackType = type,
            suggestedFileName = "$id.mp4",
            requestContext = BrowserRequestContext(
                pageUrl = "https://page.example.test/watch",
                userAgent = "YFT mux progress fixture",
                cookie = null,
            ),
            mimeType = mime,
            codecs = listOf(codec),
        )

    /**
     * The video track reports [videoSteps] inside its one range and notes what the merge showed
     * after each; the sound waits until the video is done, so only the video moves.
     */
    private class InRangeRunner(
        private val videoSteps: List<Long>,
        private val beforeStep: () -> Unit,
        private val shownBytes: () -> Long,
    ) : DashTransferRunner {
        val seenAfterSteps = mutableListOf<Long>()
        private val videoDone = CompletableDeferred<Unit>()

        override suspend fun transfer(
            plan: DashDownloadPlan,
            destination: DownloadDestination,
            resumeFrom: DashTransferCheckpoint?,
            onProgress: suspend (DownloadProgress) -> Unit,
            onCheckpoint: suspend (DashTransferCheckpoint) -> Unit,
        ): DashTransferResult {
            val video = plan.trackType == MediaTrackType.VIDEO
            val bytes = ByteArray(if (video) VIDEO_LENGTH else AUDIO_LENGTH) { 7 }
            if (video) {
                videoSteps.forEach { step ->
                    beforeStep()
                    onProgress(DownloadProgress(step, VIDEO_LENGTH.toLong()))
                    seenAfterSteps += shownBytes()
                }
            } else {
                videoDone.await()
            }
            destination.prepare(bytes.size.toLong())
            destination.open().use { output ->
                output.write(0, bytes, 0, bytes.size)
                output.sync()
            }
            destination.commit()
            val checkpoint = DashTransferCheckpoint(
                manifestFingerprint = (if (video) "a" else "b").repeat(64),
                chunks = listOf(StreamChunkCheckpoint(0, bytes.size.toLong(), completed = true)),
            )
            onCheckpoint(checkpoint)
            if (video) videoDone.complete(Unit)
            return DashTransferResult.Completed(bytes.size.toLong(), checkpoint)
        }

        override suspend fun discard(plan: DashDownloadPlan) = Unit
    }

    /**
     * Two live tracks whose total is unknown, one step after the other: the video reports an
     * estimate, the sound none and then one, the video comes in ([videoReady]), and the sound
     * reports again. [seen] holds what the merge showed after each of the sound's steps.
     */
    private class LiveTracksRunner(
        private val shown: () -> DownloadProgress?,
        private val videoReady: CompletableDeferred<Unit>,
    ) : DashTransferRunner {
        val seen = CopyOnWriteArrayList<DownloadProgress?>()
        private val videoStarted = CompletableDeferred<Unit>()
        private val soundStarted = CompletableDeferred<Unit>()

        override suspend fun transfer(
            plan: DashDownloadPlan,
            destination: DownloadDestination,
            resumeFrom: DashTransferCheckpoint?,
            onProgress: suspend (DownloadProgress) -> Unit,
            onCheckpoint: suspend (DashTransferCheckpoint) -> Unit,
        ): DashTransferResult {
            val video = plan.trackType == MediaTrackType.VIDEO
            val bytes = ByteArray(if (video) VIDEO_LIVE_LENGTH else AUDIO_LIVE_LENGTH) { 7 }
            if (video) {
                onProgress(DownloadProgress(300, null, estimatedTotalBytes = 1_000))
                videoStarted.complete(Unit)
                soundStarted.await()
            } else {
                videoStarted.await()
                onProgress(DownloadProgress(50, null))
                seen += shown()
                onProgress(DownloadProgress(100, null))
                seen += shown()
                onProgress(DownloadProgress(120, null, estimatedTotalBytes = 400))
                seen += shown()
                soundStarted.complete(Unit)
                videoReady.await()
                onProgress(DownloadProgress(200, null, estimatedTotalBytes = 410))
                seen += shown()
            }
            destination.prepare(bytes.size.toLong())
            destination.open().use { output ->
                output.write(0, bytes, 0, bytes.size)
                output.sync()
            }
            destination.commit()
            val checkpoint = DashTransferCheckpoint(
                manifestFingerprint = (if (video) "c" else "d").repeat(64),
                chunks = listOf(StreamChunkCheckpoint(0, bytes.size.toLong(), completed = true)),
            )
            onCheckpoint(checkpoint)
            return DashTransferResult.Completed(bytes.size.toLong(), checkpoint)
        }

        override suspend fun discard(plan: DashDownloadPlan) = Unit
    }

    private class ConcatMuxer : LocalAudioVideoMuxer {
        override fun mux(
            videoFile: File,
            audioFile: File,
            outputFile: File,
            outputMimeType: String,
        ): LocalMuxResult {
            val bytes = videoFile.readBytes() + audioFile.readBytes()
            outputFile.writeBytes(bytes)
            return LocalMuxResult.Completed(bytes.size.toLong())
        }
    }

    private companion object {
        const val VIDEO_LENGTH = 220
        const val AUDIO_LENGTH = 110
        const val VIDEO_LIVE_LENGTH = 990
        const val AUDIO_LIVE_LENGTH = 405
    }
}
