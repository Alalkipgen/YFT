package com.alal.yft.feature.downloads

import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.download.DownloadPlanType
import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadSegment
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.download.HlsTransferCheckpoint
import com.alal.yft.core.model.download.StreamChunkCheckpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadsUiStateTest {
    @Test
    fun emptyQueueExposesNoRowsAndNoBulkPause() {
        val state = DownloadsUiState.from(emptyList())

        assertTrue(state.isEmpty)
        assertEquals(0, state.occupyingCount)
        assertFalse(state.canPauseAll)
    }

    @Test
    fun runningTaskExposesDeterminateProgressAndStopControls() {
        val state = DownloadsUiState.from(
            listOf(
                directTask(
                    id = "a",
                    status = DownloadTaskStatus.RUNNING,
                    downloaded = 512,
                    total = 2048,
                ),
            ),
        )

        val row = state.rows.single()
        assertEquals(0.25f, row.progressFraction)
        assertEquals(25, row.progressPercent)
        assertTrue(row.isOccupyingQueue)
        assertFalse(row.isTerminal)
        assertEquals(setOf(DownloadAction.PAUSE, DownloadAction.CANCEL), row.availableActions)
        assertTrue(state.canPauseAll)
        assertEquals(1, state.occupyingCount)
    }

    @Test
    fun unknownTotalSizeKeepsProgressIndeterminate() {
        val state = DownloadsUiState.from(
            listOf(hlsTask(id = "a", status = DownloadTaskStatus.RUNNING, downloaded = 4096)),
        )

        val row = state.rows.single()
        assertNull(row.progressFraction)
        assertEquals(0, row.progressPercent)
        assertEquals(DownloadPlanType.HLS, row.planType)
    }

    @Test
    fun pausedTaskOffersResumeAndCancelButNotPause() {
        val row = singleRow(
            directTask(
                id = "a",
                status = DownloadTaskStatus.PAUSED,
                downloaded = 10,
                total = 100,
            ),
        )

        assertEquals(setOf(DownloadAction.RESUME, DownloadAction.CANCEL), row.availableActions)
        assertFalse(row.isOccupyingQueue)
    }

    @Test
    fun pausingTaskCannotBePausedAgain() {
        val row = singleRow(
            directTask(
                id = "a",
                status = DownloadTaskStatus.PAUSING,
                downloaded = 10,
                total = 100,
            ),
        )

        assertEquals(setOf(DownloadAction.CANCEL), row.availableActions)
        assertTrue(row.isOccupyingQueue)
    }

    @Test
    fun failedTaskOffersRetryAndRemoval() {
        val row = singleRow(
            directTask(
                id = "a",
                status = DownloadTaskStatus.FAILED,
                downloaded = 10,
                total = 100,
                failureReason = DownloadFailureReason.NETWORK,
            ),
        )

        assertEquals(setOf(DownloadAction.RETRY, DownloadAction.DELETE), row.availableActions)
        assertTrue(row.isTerminal)
        assertEquals(DownloadFailureReason.NETWORK, row.failureReason)
    }

    @Test
    fun expiredLinkTaskOnlyOffersRemovalAndFlagsRefresh() {
        val row = singleRow(
            directTask(
                id = "a",
                status = DownloadTaskStatus.NEEDS_REFRESH,
                downloaded = 10,
                total = 100,
                requiresLinkRefresh = true,
                failureReason = DownloadFailureReason.EXPIRED_URL,
            ),
        )

        assertEquals(setOf(DownloadAction.DELETE), row.availableActions)
        assertTrue(row.requiresLinkRefresh)
    }

    @Test
    fun completedTaskReportsFullProgressAndOnlyRemoval() {
        val row = singleRow(
            directTask(
                id = "a",
                status = DownloadTaskStatus.COMPLETED,
                downloaded = 100,
                total = 100,
            ),
        )

        assertEquals(1f, row.progressFraction)
        assertEquals(100, row.progressPercent)
        assertEquals(setOf(DownloadAction.DELETE), row.availableActions)
    }

    @Test
    fun unfinishedWorkIsOrderedBeforeTerminalWorkAndThenByRecency() {
        val state = DownloadsUiState.from(
            listOf(
                directTask("done", DownloadTaskStatus.COMPLETED, 1, 1, updatedAt = 50),
                directTask("old-running", DownloadTaskStatus.RUNNING, 1, 10, updatedAt = 10),
                directTask("new-paused", DownloadTaskStatus.PAUSED, 1, 10, updatedAt = 40),
                directTask("cancelled", DownloadTaskStatus.CANCELLED, 0, 10, updatedAt = 90),
            ),
        )

        assertEquals(
            listOf("new-paused", "old-running", "cancelled", "done"),
            state.rows.map(DownloadRowUiState::id),
        )
    }

    @Test
    fun bulkPauseStaysAvailableWhileAnyTaskIsPausable() {
        val state = DownloadsUiState.from(
            listOf(
                directTask("a", DownloadTaskStatus.COMPLETED, 1, 1),
                directTask("b", DownloadTaskStatus.WAITING_FOR_NETWORK, 1, 10),
            ),
        )

        assertTrue(state.canPauseAll)
    }

    private fun singleRow(task: StoredDownloadTask): DownloadRowUiState =
        DownloadsUiState.from(listOf(task)).rows.single()

    private fun directTask(
        id: String,
        status: DownloadTaskStatus,
        downloaded: Long,
        total: Long?,
        requiresLinkRefresh: Boolean = false,
        failureReason: DownloadFailureReason? = null,
        updatedAt: Long = 1_000,
    ): StoredDownloadTask = StoredDownloadTask(
        id = id,
        displayName = "clip-$id.mp4",
        status = status,
        planType = DownloadPlanType.DIRECT,
        totalBytes = total,
        downloadedBytes = downloaded,
        mimeType = "video/mp4",
        destinationKind = DownloadDestinationKind.APP_PRIVATE,
        destinationUri = null,
        preferredSegmentCount = 4,
        requiresLinkRefresh = requiresLinkRefresh,
        failureReason = failureReason,
        checkpoint = DirectTransferCheckpoint(
            totalBytes = total,
            entityTag = null,
            lastModified = null,
            segments = listOf(
                DownloadSegment(
                    index = 0,
                    startByte = 0,
                    endByteInclusive = total?.minus(1),
                    downloadedBytes = downloaded,
                ),
            ),
        ),
        createdAtEpochMs = 1,
        updatedAtEpochMs = updatedAt,
    )

    private fun hlsTask(
        id: String,
        status: DownloadTaskStatus,
        downloaded: Long,
    ): StoredDownloadTask = StoredDownloadTask(
        id = id,
        displayName = "stream-$id.ts",
        status = status,
        planType = DownloadPlanType.HLS,
        totalBytes = null,
        downloadedBytes = downloaded,
        mimeType = "video/mp2t",
        destinationKind = DownloadDestinationKind.MEDIA_STORE,
        destinationUri = null,
        preferredSegmentCount = 1,
        requiresLinkRefresh = false,
        failureReason = null,
        checkpoint = HlsTransferCheckpoint(
            manifestFingerprint = null,
            chunks = listOf(
                StreamChunkCheckpoint(index = 0, downloadedBytes = downloaded, completed = false),
            ),
        ),
        createdAtEpochMs = 1,
        updatedAtEpochMs = 1_000,
    )
}
