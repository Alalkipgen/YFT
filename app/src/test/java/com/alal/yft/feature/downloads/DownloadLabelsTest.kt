package com.alal.yft.feature.downloads

import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.download.DownloadPlanType
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.settings.DownloadLocation
import com.alal.yft.download.policy.TransferNetworkState
import com.alal.yft.ui.components.YftStatusTone
import com.alal.yft.ui.theme.YftIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadLabelsTest {
    @Test
    fun metaShowsTheFormatWhileMovingAndAddsTheSizeOnceStill() {
        assertEquals("MP4", metaLabel(row(DownloadTaskStatus.RUNNING, total = MB_96)))
        assertEquals(
            "HLS · MP4",
            metaLabel(row(DownloadTaskStatus.RUNNING, plan = DownloadPlanType.HLS)),
        )
        assertEquals(
            "M4A · 12 MB",
            metaLabel(
                row(DownloadTaskStatus.WAITING_FOR_NETWORK, name = "Rain.m4a", total = MB_12),
            ),
        )
        assertEquals(
            "M4A · 7 MB",
            metaLabel(
                row(
                    DownloadTaskStatus.COMPLETED,
                    name = "Waves.m4a",
                    downloaded = 7_340_032,
                    total = null,
                ),
            ),
        )
        assertEquals(
            "HLS",
            metaLabel(row(DownloadTaskStatus.RUNNING, name = "clip", plan = DownloadPlanType.HLS)),
        )
        assertEquals("Direct file", metaLabel(row(DownloadTaskStatus.QUEUED, name = "clip")))
    }

    @Test
    fun progressDetailCompactsTheUnitAndAddsSpeedAndTimeLeft() {
        val running = row(
            DownloadTaskStatus.RUNNING,
            downloaded = 63_963_136,
            total = MB_96,
            bytesPerSecond = 2_516_582,
        )

        assertEquals("61 of 96 MB · 2.4 MB/s · 15 s left", progressDetail(running))
        assertEquals("64%", percentLabel(running.copy(progressPercent = 64)))
    }

    @Test
    fun progressDetailNamesTheOtherStates() {
        assertEquals(
            "Downloading · 13 MB",
            progressDetail(row(DownloadTaskStatus.RUNNING, downloaded = 13_107_200, total = null)),
        )
        assertEquals("Downloading", progressDetail(row(DownloadTaskStatus.RUNNING, total = null)))
        assertEquals(
            "800 KB of 96 MB · Paused",
            progressDetail(row(DownloadTaskStatus.PAUSED, downloaded = 819_200, total = MB_96)),
        )
        assertEquals("Paused", progressDetail(row(DownloadTaskStatus.PAUSED, total = null)))
        assertEquals("Checking source", progressDetail(row(DownloadTaskStatus.PROBING)))
        assertEquals("Verifying file", progressDetail(row(DownloadTaskStatus.VERIFYING)))
        assertEquals("Pausing", progressDetail(row(DownloadTaskStatus.PAUSING)))
        assertNull(percentLabel(row(DownloadTaskStatus.RUNNING, total = null)))
        assertNull(percentLabel(row(DownloadTaskStatus.QUEUED, total = MB_96)))
    }

    @Test
    fun timeLeftRoundsUpAndStopsMakingSenseAfterAWeek() {
        assertEquals("1 s left", timeLeftLabel(0))
        assertEquals("59 s left", timeLeftLabel(59))
        assertEquals("1 min left", timeLeftLabel(60))
        assertEquals("2 min left", timeLeftLabel(61))
        assertEquals("1 h left", timeLeftLabel(3_600))
        assertEquals("1 h 5 min left", timeLeftLabel(3_900))
        assertNull(timeLeftLabel(8L * 24 * 60 * 60))
        assertEquals("512 KB/s", speedLabel(524_288))
        assertEquals("1.5 of 3 MB", amountOf(1_572_864, 3_145_728))
        assertEquals("512 B of 3 KB", amountOf(512, 3_072))
    }

    @Test
    fun statusChipsSayWhyATaskIsNotMoving() {
        assertEquals(
            DownloadStatusChip("Waiting for Wi-Fi", YftStatusTone.Waiting, YftIcons.Wifi),
            statusChip(
                row(DownloadTaskStatus.WAITING_FOR_NETWORK),
                TransferNetworkState.WAITING_FOR_UNMETERED,
            ),
        )
        assertEquals(
            "Waiting for network",
            statusChip(row(DownloadTaskStatus.WAITING_FOR_NETWORK), TransferNetworkState.OFFLINE)
                ?.text,
        )
        assertEquals(
            DownloadStatusChip("Failed · Link expired", YftStatusTone.Failed),
            statusChip(
                row(DownloadTaskStatus.FAILED, failure = DownloadFailureReason.EXPIRED_URL),
                TransferNetworkState.ALLOWED,
            ),
        )
        assertEquals(
            "Failed",
            statusChip(row(DownloadTaskStatus.FAILED), TransferNetworkState.ALLOWED)?.text,
        )
        assertEquals(
            "Link expired",
            statusChip(row(DownloadTaskStatus.NEEDS_REFRESH), TransferNetworkState.ALLOWED)?.text,
        )
        assertEquals(
            YftStatusTone.Neutral,
            statusChip(row(DownloadTaskStatus.QUEUED), TransferNetworkState.ALLOWED)?.tone,
        )
        assertNull(statusChip(row(DownloadTaskStatus.RUNNING), TransferNetworkState.ALLOWED))
        assertNull(statusChip(row(DownloadTaskStatus.COMPLETED), TransferNetworkState.ALLOWED))
    }

    @Test
    fun storageLabelNamesTheFolderAndTheFreeSpace() {
        assertEquals(
            "Download/YFT · 18 GB free",
            storageLabel(DownloadStorageSummary(DownloadLocation.SHARED_DOWNLOADS, 19_541_180_006)),
        )
        assertEquals(
            "App storage",
            storageLabel(DownloadStorageSummary(DownloadLocation.APP_STORAGE, freeBytes = null)),
        )
    }

    @Test
    fun menuLabelsSayThatRemovingKeepsTheFile() {
        assertEquals("Remove", actionLabel(DownloadAction.DELETE))
        assertEquals("Remove from list", menuLabel(DownloadAction.DELETE))
        assertEquals("Cancel download", menuLabel(DownloadAction.CANCEL))
        assertEquals("download-menu-retry-a", menuTag(DownloadAction.RETRY, "a"))
        assertEquals("download-action-pause-a", actionTag(DownloadAction.PAUSE, "a"))
    }

    private fun row(
        status: DownloadTaskStatus,
        name: String = "Mountain Lake 4K.mp4",
        downloaded: Long = 0,
        total: Long? = null,
        plan: DownloadPlanType = DownloadPlanType.DIRECT,
        failure: DownloadFailureReason? = null,
        bytesPerSecond: Long? = null,
    ) = DownloadRowUiState(
        id = "a",
        displayName = name,
        status = status,
        planType = plan,
        destinationKind = DownloadDestinationKind.APP_PRIVATE,
        downloadedBytes = downloaded,
        totalBytes = total,
        progressPercent = 0,
        requiresLinkRefresh = false,
        failureReason = failure,
        bytesPerSecond = bytesPerSecond,
    )

    private companion object {
        const val MB_96 = 100_663_296L
        const val MB_12 = 12_582_912L
    }
}
