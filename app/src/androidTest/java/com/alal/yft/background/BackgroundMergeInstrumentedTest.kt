package com.alal.yft.background

import android.media.MediaExtractor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.UiDevice
import com.alal.yft.background.BackgroundTestKit.context
import com.alal.yft.background.BackgroundTestKit.diag
import com.alal.yft.background.BackgroundTestKit.notificationText
import com.alal.yft.background.BackgroundTestKit.serviceInForeground
import com.alal.yft.background.BackgroundTestKit.task
import com.alal.yft.background.BackgroundTestKit.waitFor
import com.alal.yft.core.download.AndroidMp4AudioVideoMuxer
import com.alal.yft.core.download.AudioVideoMuxEngine
import com.alal.yft.core.download.DashTransferRunner
import com.alal.yft.core.download.DownloadDestination
import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.core.download.FileDownloadDestination
import com.alal.yft.core.download.MuxAttempt
import com.alal.yft.core.download.MuxOutput
import com.alal.yft.core.download.MuxProgressListener
import com.alal.yft.core.download.ProgressAudioVideoMuxer
import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.model.download.AudioVideoMuxCheckpoint
import com.alal.yft.core.model.download.AudioVideoMuxDownloadPlan
import com.alal.yft.core.model.download.AudioVideoMuxStage
import com.alal.yft.core.model.download.DashDownloadPlan
import com.alal.yft.core.model.download.DashTransferCheckpoint
import com.alal.yft.core.model.download.DashTransferResult
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.download.StreamChunkCheckpoint
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.download.DownloadForegroundService
import com.alal.yft.download.DownloadNotificationFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P34 on the CI emulator: a merge keeps going after Home with the download service in the
 * foreground, its notification shows "Merging audio and video · N%", and the merged file has
 * both tracks. The real MediaMuxer merges the one-second test tracks; the test's muxer first
 * reports half of its progress over 20 s, as a long video would, so Home lands mid-merge.
 */
@RunWith(AndroidJUnit4::class)
class BackgroundMergeInstrumentedTest {
    private val device = UiDevice.getInstance(BackgroundTestKit.instrumentation)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val directory = File(context.cacheDir, "p34-merge")
    private var queue: DownloadQueue? = null

    @Before
    fun setUp() {
        BackgroundTestKit.allowNotifications()
        directory.deleteRecursively()
        directory.mkdirs()
    }

    @After
    fun tearDown() {
        BackgroundTestKit.cleanUp(queue)
        scope.cancel()
        directory.deleteRecursively()
    }

    @Test
    fun aMergeKeepsGoingAtHomeAndGivesAFileWithBothTracks() = runBlocking<Unit> {
        val engine = AudioVideoMuxEngine(
            dashTransfer = FileTrackRunner(asset("mux/video-avc.mp4"), asset("mux/audio-aac.m4a")),
            muxer = PacedMuxer(AndroidMp4AudioVideoMuxer()),
            workspaceRoot = File(directory, "workspace"),
        )
        val queue = BackgroundTestKit.queue(BackgroundTestKit.dispatcher(mux = engine), scope)
        this@BackgroundMergeInstrumentedTest.queue = queue
        DownloadForegroundService.testQueue = queue
        val id = queue.enqueue(
            plan = AudioVideoMuxDownloadPlan(
                taskId = "p34-merge",
                video = trackPlan("video", MediaTrackType.VIDEO, "video/mp4", "avc1.42c00d"),
                audio = trackPlan("audio", MediaTrackType.AUDIO, "audio/mp4", "mp4a.40.2"),
                suggestedFileName = NAME,
            ),
            destination = FileDownloadDestination(
                partialFile = File(directory, "$NAME.part"),
                completedFile = File(directory, NAME),
            ),
        )
        DownloadForegroundService.start(context)
        waitFor(START_TIMEOUT_MS, "the merge to start") {
            task(queue, id)?.takeIf { it.muxStage() == AudioVideoMuxStage.MUXING }
        }
        device.pressHome()

        val first = mergeDone(queue, id)
        val samples = (1..SAMPLES).map {
            Thread.sleep(SAMPLE_MS)
            mergeDone(queue, id)
        }
        val atHome = listOf(first) + samples
        diag("merge-at-home", "done" to atHome.joinToString(","))
        assertTrue(
            "the merge moves on at Home: $atHome",
            atHome.zipWithNext().all { (before, after) -> after > before },
        )
        assertTrue("the service runs in the foreground", serviceInForeground())
        val shown = waitFor(NOTICE_TIMEOUT_MS, "the merge in the notification") {
            notificationText(DownloadNotificationFactory.NOTIFICATION_ID)
                ?.takeIf { "Merging audio and video · " in it && "%" in it }
        }
        diag("merge-notification", "text" to shown)

        waitFor(FINISH_TIMEOUT_MS, "the merge to finish") {
            task(queue, id)?.takeIf { it.status == DownloadTaskStatus.COMPLETED }
        }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(File(directory, NAME).absolutePath)
            assertEquals(2, extractor.trackCount)
        } finally {
            extractor.release()
        }
        val notice = waitFor(NOTICE_TIMEOUT_MS, "the finished notice") {
            notificationText(DownloadNotificationFactory.FINISHED_NOTIFICATION_ID, tag = id)
        }
        assertEquals("Downloaded · yft-p34-merge", notice)
    }

    private fun StoredDownloadTask.muxStage(): AudioVideoMuxStage? =
        (checkpoint as? AudioVideoMuxCheckpoint)?.stage

    private fun mergeDone(queue: DownloadQueue, id: String): Long {
        val checkpoint = task(queue, id)?.checkpoint as? AudioVideoMuxCheckpoint
        return if (checkpoint?.stage == AudioVideoMuxStage.MUXING) checkpoint.stepDone else -1L
    }

    private fun trackPlan(
        id: String,
        type: MediaTrackType,
        mimeType: String,
        codec: String,
    ): DashDownloadPlan = DashDownloadPlan(
        taskId = "p34-$id",
        manifestUrl = "https://media.example.test/p34/manifest.mpd",
        representationId = id,
        trackType = type,
        suggestedFileName = "$id.mp4",
        requestContext = BrowserRequestContext(
            pageUrl = "https://page.example.test/watch",
            userAgent = "YFT P34 test",
            cookie = null,
        ),
        mimeType = mimeType,
        codecs = listOf(codec),
    )

    private fun asset(name: String): File {
        val file = File(directory, name.substringAfterLast('/'))
        BackgroundTestKit.instrumentation.context.assets.open(name).use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return file
    }

    /** Reports half the merge over [PACED_STEPS] seconds, then lets the real muxer finish. */
    private class PacedMuxer(private val real: ProgressAudioVideoMuxer) :
        ProgressAudioVideoMuxer by real {
        override fun mux(
            videoFile: File,
            audioFile: File,
            output: MuxOutput,
            outputMimeType: String,
            onProgress: MuxProgressListener,
        ): MuxAttempt {
            val total = PACED_STEPS * 2L
            for (step in 0 until PACED_STEPS) {
                onProgress.onProgress(step.toLong(), total)
                Thread.sleep(STEP_MS)
            }
            return real.mux(videoFile, audioFile, output, outputMimeType) { done, all ->
                val merged = done * PACED_STEPS / all.coerceAtLeast(1)
                onProgress.onProgress(PACED_STEPS + merged, total)
            }
        }
    }

    /** A finished track download: the file is copied in, as if it had come from the network. */
    private class FileTrackRunner(private val video: File, private val audio: File) :
        DashTransferRunner {
        override suspend fun transfer(
            plan: DashDownloadPlan,
            destination: DownloadDestination,
            resumeFrom: DashTransferCheckpoint?,
            onProgress: suspend (DownloadProgress) -> Unit,
            onCheckpoint: suspend (DashTransferCheckpoint) -> Unit,
        ): DashTransferResult {
            val source = if (plan.trackType == MediaTrackType.VIDEO) video else audio
            val bytes = source.readBytes()
            onProgress(DownloadProgress(0, bytes.size.toLong()))
            destination.prepare(bytes.size.toLong())
            destination.open().use { output ->
                output.write(0, bytes, 0, bytes.size)
                output.sync()
            }
            destination.commit()
            val checkpoint = DashTransferCheckpoint(
                manifestFingerprint = (if (source == video) "a" else "b").repeat(64),
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

    private companion object {
        const val NAME = "yft-p34-merge.mp4"
        const val PACED_STEPS = 20
        const val STEP_MS = 1_000L
        const val SAMPLES = 3
        const val SAMPLE_MS = 3_000L
        const val START_TIMEOUT_MS = 30_000L
        const val NOTICE_TIMEOUT_MS = 10_000L
        const val FINISH_TIMEOUT_MS = 60_000L
    }
}
