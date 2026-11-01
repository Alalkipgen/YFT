package com.alal.yft.core.download

import com.alal.yft.core.model.download.AudioVideoMuxCheckpoint
import com.alal.yft.core.model.download.AudioVideoMuxDownloadPlan
import com.alal.yft.core.model.download.AudioVideoMuxResult
import com.alal.yft.core.model.download.DashDownloadPlan
import com.alal.yft.core.model.download.DashTransferCheckpoint
import com.alal.yft.core.model.download.DashTransferResult
import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DirectTransferResult
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.download.HlsDownloadPlan
import com.alal.yft.core.model.download.HlsTransferCheckpoint
import com.alal.yft.core.model.download.HlsTransferResult
import com.alal.yft.core.model.download.RemoteFileMetadata
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaTrackType
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Task 3 (phase-v102-easy): a finished DASH file's or merge's Details lines (a live video saved
 * a little short, or whose newest piece moved while it came) go to the task's Details.
 */
class DownloadTransferDispatcherNotesTest {
    @get:Rule
    val directory = TemporaryFolder()

    @Test
    fun `a finished DASH file's and merge's notes go to their task's Details`() = runTest {
        val lines = mutableListOf<Pair<String, String>>()
        val dispatcher = DefaultDownloadTransferDispatcher(
            direct = UnusedDirect,
            hls = UnusedHls,
            dash = NotedDash,
            mux = NotedMux,
            onDetail = { taskId, line -> lines += taskId to line },
        )
        val destination = FileDownloadDestination(
            partialFile = File(directory.root, "out.part"),
            completedFile = File(directory.root, "out.mp4"),
        )

        dispatcher.transfer(dashPlan("live-video"), null, destination, null)
        dispatcher.transfer(
            AudioVideoMuxDownloadPlan(
                taskId = "live-merge",
                video = dashPlan("merge-video"),
                audio = dashPlan("merge-audio").copy(trackType = MediaTrackType.AUDIO),
                suggestedFileName = "live.mp4",
            ),
            null,
            destination,
            null,
        )

        assertEquals(
            listOf(
                "live-video" to "Live video saved 54 s short",
                "live-merge" to "Live video saved 54 s short",
                "live-merge" to "Live sound saved 54 s short",
            ),
            lines,
        )
    }

    private fun dashPlan(id: String): DashDownloadPlan = DashDownloadPlan(
        taskId = id,
        manifestUrl = "https://media.example.test/live.mp4?source=yt_live_broadcast",
        representationId = "160",
        trackType = MediaTrackType.VIDEO,
        suggestedFileName = "$id.mp4",
        requestContext = BrowserRequestContext(
            pageUrl = "https://page.example.test/watch",
            userAgent = "YFT fixture",
            cookie = null,
        ),
        mimeType = "video/mp4",
    )

    private object NotedDash : DashTransferRunner {
        override suspend fun transfer(
            plan: DashDownloadPlan,
            destination: DownloadDestination,
            resumeFrom: DashTransferCheckpoint?,
            onProgress: suspend (DownloadProgress) -> Unit,
            onCheckpoint: suspend (DashTransferCheckpoint) -> Unit,
        ): DashTransferResult = DashTransferResult.Completed(
            bytesWritten = 1,
            checkpoint = DashTransferCheckpoint(null, emptyList()),
            notes = listOf("Live video saved 54 s short"),
        )

        override suspend fun discard(plan: DashDownloadPlan) = Unit
    }

    private object NotedMux : AudioVideoMuxRunner {
        override suspend fun transfer(
            plan: AudioVideoMuxDownloadPlan,
            destination: DownloadDestination,
            resumeFrom: AudioVideoMuxCheckpoint?,
            onProgress: suspend (DownloadProgress) -> Unit,
            onCheckpoint: suspend (AudioVideoMuxCheckpoint) -> Unit,
        ): AudioVideoMuxResult = AudioVideoMuxResult.Completed(
            bytesWritten = 1,
            checkpoint = AudioVideoMuxCheckpoint(),
            notes = listOf("Live video saved 54 s short", "Live sound saved 54 s short"),
        )

        override suspend fun discard(plan: AudioVideoMuxDownloadPlan) = Unit
    }

    private object UnusedDirect : DirectTransferRunner {
        override suspend fun transfer(
            plan: DirectDownloadPlan,
            metadata: RemoteFileMetadata,
            destination: DownloadDestination,
            resumeFrom: DirectTransferCheckpoint?,
            onProgress: suspend (DownloadProgress) -> Unit,
            onCheckpoint: suspend (DirectTransferCheckpoint) -> Unit,
        ): DirectTransferResult = error("not used")
    }

    private object UnusedHls : HlsTransferRunner {
        override suspend fun transfer(
            plan: HlsDownloadPlan,
            destination: DownloadDestination,
            resumeFrom: HlsTransferCheckpoint?,
            onProgress: suspend (DownloadProgress) -> Unit,
            onCheckpoint: suspend (HlsTransferCheckpoint) -> Unit,
        ): HlsTransferResult = error("not used")

        override suspend fun discard(plan: HlsDownloadPlan) = Unit
    }
}
