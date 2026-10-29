package com.alal.yft.feature.downloads

import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.download.DownloadPlanType
import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DownloadFailure
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadFailureStage
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

    /** P21: the row carries the stored stage and detail for its Details dialog. */
    @Test
    fun failedTaskKeepsItsFailureDetailsForTheDialog() {
        val failure = DownloadFailure(
            reason = DownloadFailureReason.STORAGE_UNAVAILABLE,
            stage = DownloadFailureStage.WRITE_FILE,
            detail = "IOException: EIO (I/O error)",
        )
        val task = directTask(
            id = "a",
            status = DownloadTaskStatus.FAILED,
            downloaded = 10,
            total = 100,
            failureReason = failure.reason,
        )

        assertEquals(failure, singleRow(task.copy(failure = failure)).failure)
        assertEquals(
            "A task saved before P21 has only its reason",
            DownloadFailure(DownloadFailureReason.STORAGE_UNAVAILABLE),
            singleRow(task).failure,
        )
        assertEquals(
            "Details of another reason are not shown",
            DownloadFailure(DownloadFailureReason.STORAGE_UNAVAILABLE),
            singleRow(
                task.copy(failure = failure.copy(reason = DownloadFailureReason.NETWORK)),
            ).failure,
        )
        assertNull(singleRow(task.copy(failureReason = null, failure = null)).failure)
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
    fun unfinishedWorkComesFirstNewestAddedFirstThenTerminalWorkByRecency() {
        val tasks = listOf(
            added("done", DownloadTaskStatus.COMPLETED, createdAt = 5, updatedAt = 50),
            added("old-running", DownloadTaskStatus.RUNNING, createdAt = 10, updatedAt = 90),
            added("new-paused", DownloadTaskStatus.PAUSED, createdAt = 30, updatedAt = 40),
            added("cancelled", DownloadTaskStatus.CANCELLED, createdAt = 1, updatedAt = 90),
        )

        assertEquals(listOf("new-paused", "old-running", "cancelled", "done"), order(tasks))
    }

    @Test
    fun progressWritesNeverReorderTheActiveRows() {
        val tasks = listOf(
            added("first", DownloadTaskStatus.RUNNING, createdAt = 10, updatedAt = 100),
            added("second", DownloadTaskStatus.RUNNING, createdAt = 20, updatedAt = 100),
            added("third", DownloadTaskStatus.QUEUED, createdAt = 30),
        )
        val before = order(tasks)

        var now = 100L
        val written = (1..6).fold(tasks) { current, step ->
            current.map { task ->
                now += 7
                when {
                    task.id == "third" && step == 2 ->
                        task.copy(status = DownloadTaskStatus.RUNNING, updatedAtEpochMs = now)
                    task.id == "second" && step == 4 ->
                        task.copy(status = DownloadTaskStatus.PAUSED, updatedAtEpochMs = now)
                    task.status == DownloadTaskStatus.RUNNING -> added(
                        task.id,
                        task.status,
                        createdAt = task.createdAtEpochMs,
                        updatedAt = now,
                        downloaded = task.downloadedBytes + step,
                    )
                    else -> task
                }
            }.also { assertEquals(before, order(it)) }
        }

        assertEquals(listOf("third", "second", "first"), before)
        assertEquals(DownloadTaskStatus.PAUSED, written.single { it.id == "second" }.status)
    }

    @Test
    fun aNewDownloadComesFirst() {
        val running = listOf(
            added("older", DownloadTaskStatus.RUNNING, createdAt = 10, updatedAt = 900),
            added("newer", DownloadTaskStatus.RUNNING, createdAt = 20, updatedAt = 950),
        )
        val new = added("new", DownloadTaskStatus.QUEUED, createdAt = 30)

        assertEquals(
            listOf("new", "newer", "older"),
            order(running + new),
        )
        assertEquals(
            listOf("new", "newer", "older"),
            order(listOf(new) + running.reversed()),
        )
    }

    @Test
    fun rowsAddedAtTheSameTimeKeepOneOrderWhateverChanges() {
        val tasks = listOf(
            added("b", DownloadTaskStatus.RUNNING, createdAt = 50, updatedAt = 70),
            added("a", DownloadTaskStatus.RUNNING, createdAt = 50, updatedAt = 60),
            added("c", DownloadTaskStatus.QUEUED, createdAt = 50),
        )
        val expected = listOf("a", "b", "c")

        assertEquals(expected, order(tasks))
        assertEquals(expected, order(tasks.reversed()))
        assertEquals(
            expected,
            order(tasks.map { it.copy(updatedAtEpochMs = 1_000 - it.updatedAtEpochMs) }),
        )
    }

    @Test
    fun finishedAndFailedRowsGoBelowAndARetriedRowReturnsToItsPlace() {
        val tasks = listOf(
            added("oldest", DownloadTaskStatus.RUNNING, createdAt = 10, updatedAt = 500),
            added("middle", DownloadTaskStatus.RUNNING, createdAt = 20, updatedAt = 500),
            added("newest", DownloadTaskStatus.RUNNING, createdAt = 30, updatedAt = 500),
        )
        fun List<StoredDownloadTask>.change(
            id: String,
            edit: (StoredDownloadTask) -> StoredDownloadTask,
        ) = map { if (it.id == id) edit(it) else it }

        val middleFailed = tasks.change("middle") {
            it.copy(
                status = DownloadTaskStatus.FAILED,
                failureReason = DownloadFailureReason.NETWORK,
                updatedAtEpochMs = 600,
            )
        }
        assertEquals(listOf("newest", "oldest", "middle"), order(middleFailed))

        val newestDone = middleFailed.change("newest") {
            it.copy(status = DownloadTaskStatus.COMPLETED, updatedAtEpochMs = 700)
        }
        assertEquals(listOf("oldest", "newest", "middle"), order(newestDone))

        val middleRetried = newestDone.change("middle") {
            it.copy(
                status = DownloadTaskStatus.QUEUED,
                failureReason = null,
                updatedAtEpochMs = 800,
            )
        }
        assertEquals(listOf("middle", "oldest", "newest"), order(middleRetried))

        val oldestRenewingAndOneMore = middleRetried.change("oldest") {
            it.copy(status = DownloadTaskStatus.RENEWING_LINK, updatedAtEpochMs = 900)
        } + added("new", DownloadTaskStatus.QUEUED, createdAt = 40)
        assertEquals(
            listOf("new", "middle", "oldest", "newest"),
            order(oldestRenewingAndOneMore),
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

    @Test
    fun activeCountForTheTabBadgeIncludesOnlyMovingTransfers() {
        val tasks = DownloadTaskStatus.entries.mapIndexed { index, status ->
            directTask(id = "t$index", status = status, downloaded = 10, total = 100)
        }

        assertEquals(5, tasks.activeDownloadCount())
        assertEquals(
            setOf(
                DownloadTaskStatus.PROBING,
                DownloadTaskStatus.RUNNING,
                DownloadTaskStatus.PAUSING,
                DownloadTaskStatus.VERIFYING,
                DownloadTaskStatus.RENEWING_LINK,
            ),
            ACTIVE_STATUSES,
        )
        assertEquals(0, emptyList<StoredDownloadTask>().activeDownloadCount())
    }

    @Test
    fun everyStatusFallsUnderAllAndAtMostOneOtherFilter() {
        val expected = mapOf(
            DownloadTaskStatus.QUEUED to DownloadsFilter.QUEUED,
            DownloadTaskStatus.PROBING to DownloadsFilter.ACTIVE,
            DownloadTaskStatus.RUNNING to DownloadsFilter.ACTIVE,
            DownloadTaskStatus.PAUSING to DownloadsFilter.ACTIVE,
            DownloadTaskStatus.PAUSED to DownloadsFilter.ACTIVE,
            DownloadTaskStatus.WAITING_FOR_NETWORK to DownloadsFilter.QUEUED,
            DownloadTaskStatus.NEEDS_REFRESH to DownloadsFilter.FAILED,
            DownloadTaskStatus.VERIFYING to DownloadsFilter.ACTIVE,
            DownloadTaskStatus.COMPLETED to DownloadsFilter.DONE,
            DownloadTaskStatus.FAILED to DownloadsFilter.FAILED,
            DownloadTaskStatus.CANCELLED to null,
            DownloadTaskStatus.RENEWING_LINK to DownloadsFilter.ACTIVE,
        )

        DownloadTaskStatus.entries.forEach { status ->
            assertTrue(DownloadsFilter.ALL.matches(status))
            val others = DownloadsFilter.entries
                .filter { it != DownloadsFilter.ALL && it.matches(status) }
            assertEquals(status.name, listOfNotNull(expected.getValue(status)), others)
        }
    }

    @Test
    fun filterCountsAndRowsFollowTheStatuses() {
        val state = DownloadsUiState.from(
            listOf(
                directTask("run", DownloadTaskStatus.RUNNING, downloaded = 10, total = 100),
                directTask("held", DownloadTaskStatus.PAUSED, downloaded = 10, total = 100),
                directTask("next", DownloadTaskStatus.QUEUED, downloaded = 0, total = 100),
                directTask("done", DownloadTaskStatus.COMPLETED, downloaded = 100, total = 100),
                directTask("gone", DownloadTaskStatus.CANCELLED, downloaded = 0, total = 100),
            ),
        )

        assertEquals(5, state.count(DownloadsFilter.ALL))
        assertEquals(2, state.count(DownloadsFilter.ACTIVE))
        assertEquals(1, state.count(DownloadsFilter.QUEUED))
        assertEquals(1, state.count(DownloadsFilter.DONE))
        assertEquals(0, state.count(DownloadsFilter.FAILED))
        assertEquals(
            setOf("run", "held"),
            state.rowsFor(DownloadsFilter.ACTIVE).map { it.id }.toSet(),
        )
    }

    @Test
    fun rowsCarryNameFormatSpeedAndTimeLeft() {
        val task = directTask("a", DownloadTaskStatus.RUNNING, downloaded = 1_000, total = 4_000)
            .copy(displayName = "Forest Rain Sounds.m4a", mimeType = "audio/mp4")

        val row = DownloadsUiState.from(listOf(task), mapOf("a" to 1_000L)).rows.single()

        assertEquals("Forest Rain Sounds", row.title)
        assertEquals("M4A", row.format)
        assertTrue(row.isAudio)
        assertEquals(1_000L, row.bytesPerSecond)
        assertEquals(3L, row.secondsLeft)
        assertEquals(1_000L, row.updatedAtEpochMs)
        assertNull(singleRow(task).secondsLeft)
        assertNull(
            DownloadsUiState.from(listOf(task.copy(status = DownloadTaskStatus.PAUSED)))
                .rows.single().secondsLeft,
        )
        assertFalse(singleRow(directTask("v", DownloadTaskStatus.QUEUED, 0, null)).isAudio)
    }

    private fun singleRow(task: StoredDownloadTask): DownloadRowUiState =
        DownloadsUiState.from(listOf(task)).rows.single()

    /** The ids of [tasks]' rows, in the order the list shows them. */
    private fun order(tasks: List<StoredDownloadTask>): List<String> =
        DownloadsUiState.from(tasks).rows.map(DownloadRowUiState::id)

    /** A task added at [createdAt] and last written at [updatedAt]. */
    private fun added(
        id: String,
        status: DownloadTaskStatus,
        createdAt: Long,
        updatedAt: Long = createdAt,
        downloaded: Long = 1,
    ): StoredDownloadTask =
        directTask(id, status, downloaded, 1_000, updatedAt = updatedAt, createdAt = createdAt)

    private fun directTask(
        id: String,
        status: DownloadTaskStatus,
        downloaded: Long,
        total: Long?,
        requiresLinkRefresh: Boolean = false,
        failureReason: DownloadFailureReason? = null,
        updatedAt: Long = 1_000,
        createdAt: Long = 1,
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
        createdAtEpochMs = createdAt,
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
