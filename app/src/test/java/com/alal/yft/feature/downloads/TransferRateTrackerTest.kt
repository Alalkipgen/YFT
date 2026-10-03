package com.alal.yft.feature.downloads

import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.download.DownloadPlanType
import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DownloadSegment
import com.alal.yft.core.model.download.DownloadTaskStatus
import kotlin.math.roundToLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferRateTrackerTest {
    private var now = 0L
    private val tracker = TransferRateTracker(nowMs = { now })

    @Test
    fun theFirstSnapshotHasNoSpeedAndTheSecondMeasuresIt() {
        assertTrue(tracker.update(listOf(task(bytes = 0))).isEmpty())

        now = 1_000
        val rates = tracker.update(listOf(task(bytes = MIB)))

        assertEquals(mapOf("a" to MIB), rates)
    }

    @Test
    fun snapshotsTooCloseTogetherKeepTheLastEstimate() {
        tracker.update(listOf(task(bytes = 0)))
        now = 1_000
        tracker.update(listOf(task(bytes = MIB)))

        now = 1_200
        val rates = tracker.update(listOf(task(bytes = 5 * MIB)))

        assertEquals(mapOf("a" to MIB), rates)
    }

    @Test
    fun newMeasurementsAreSmoothed() {
        tracker.update(listOf(task(bytes = 0)))
        now = 1_000
        tracker.update(listOf(task(bytes = MIB)))

        now = 2_000
        val rates = tracker.update(listOf(task(bytes = 4 * MIB)))

        // 1 MiB/s moved 30% of the way towards the measured 3 MiB/s.
        assertEquals(mapOf("a" to (MIB + 0.3 * 2 * MIB).roundToLong()), rates)
    }

    @Test
    fun aRestartOrAStopStartsOver() {
        tracker.update(listOf(task(bytes = 0)))
        now = 1_000
        tracker.update(listOf(task(bytes = 2 * MIB)))

        now = 2_000
        assertTrue(tracker.update(listOf(task(bytes = MIB))).isEmpty())

        now = 3_000
        val paused = task(DownloadTaskStatus.PAUSED, bytes = 2 * MIB)
        assertTrue(tracker.update(listOf(paused)).isEmpty())
        now = 4_000
        assertTrue(tracker.update(listOf(task(bytes = 2 * MIB))).isEmpty())
        now = 5_000
        assertEquals(mapOf("a" to MIB), tracker.update(listOf(task(bytes = 3 * MIB))))
    }

    @Test
    fun aStalledTransferWindsDown() {
        tracker.update(listOf(task(bytes = 0)))
        now = 1_000
        tracker.update(listOf(task(bytes = MIB)))

        now = 2_000
        val rates = tracker.update(listOf(task(bytes = MIB)))

        assertEquals(mapOf("a" to (0.7 * MIB).roundToLong()), rates)
    }

    private fun task(
        status: DownloadTaskStatus = DownloadTaskStatus.RUNNING,
        bytes: Long,
    ) = StoredDownloadTask(
        id = "a",
        displayName = "a.mp4",
        status = status,
        planType = DownloadPlanType.DIRECT,
        totalBytes = TOTAL,
        downloadedBytes = bytes,
        mimeType = "video/mp4",
        destinationKind = DownloadDestinationKind.APP_PRIVATE,
        destinationUri = null,
        preferredSegmentCount = 1,
        requiresLinkRefresh = false,
        failureReason = null,
        checkpoint = DirectTransferCheckpoint(
            totalBytes = TOTAL,
            entityTag = null,
            lastModified = null,
            segments = listOf(
                DownloadSegment(
                    index = 0,
                    startByte = 0,
                    endByteInclusive = TOTAL - 1,
                    downloadedBytes = bytes,
                ),
            ),
        ),
        createdAtEpochMs = 1,
        updatedAtEpochMs = 1,
    )

    private companion object {
        const val MIB = 1_048_576L
        const val TOTAL = 100 * MIB
    }
}
