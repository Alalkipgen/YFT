package com.alal.yft.feature.downloads

import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.download.DownloadPlanType
import com.alal.yft.core.model.download.DownloadFailure
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadFailureStage
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.settings.DownloadLocation
import com.alal.yft.download.policy.TransferNetworkState
import com.alal.yft.ui.components.YftStatusTone
import com.alal.yft.ui.theme.YftIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    /** P21: what Details shows and Copy details copies, without links or the file's name. */
    @Test
    fun failureDetailsNameTheReasonStageAndPhoneButNoLinks() {
        val failed = row(DownloadTaskStatus.FAILED, failure = DownloadFailureReason.NETWORK).copy(
            destinationKind = DownloadDestinationKind.MEDIA_STORE,
            destinationUri = "content://media/external/downloads/42",
            failure = DownloadFailure(
                reason = DownloadFailureReason.NETWORK,
                stage = DownloadFailureStage.READ_SOURCE,
                detail = "SocketException: reset by https://rr1.example.test/v?sig=1 at 10.0.0.2",
            ),
        )

        val text = failureDetailsText(
            row = failed,
            appVersion = "1.4.0",
            androidRelease = "14",
            sdkInt = 34,
        )

        assertEquals(
            listOf(
                "YFT download failure",
                "Reason: Network error (NETWORK)",
                "Stage: Reading from the server (READ_SOURCE)",
                "HTTP status: \u2014",
                "Detail: SocketException: reset by [link] at [ip]",
                "Download type: Direct",
                "Saved to: Downloads (MediaStore)",
                "App: YFT 1.4.0",
                "Android: 14 (API 34)",
            ).joinToString("\n"),
            text,
        )
        for (leak in listOf("http", "example.test", "10.0.0.2", "content://", "Mountain")) {
            assertFalse("$leak must not be copied", text.contains(leak))
        }
    }

    @Test
    fun failureDetailsOfATaskSavedBeforeP21SayWhatIsNotKnown() {
        val old = row(
            DownloadTaskStatus.FAILED,
            plan = DownloadPlanType.AUDIO_VIDEO_MUX,
            failure = DownloadFailureReason.ACCESS_DENIED,
        ).copy(failure = DownloadFailure(DownloadFailureReason.ACCESS_DENIED, 403))

        val text = failureDetailsText(old, appVersion = "", androidRelease = "", sdkInt = 26)

        assertTrue(text, text.contains("Reason: Access denied (ACCESS_DENIED)\n"))
        assertTrue(text, text.contains("Stage: \u2014\nHTTP status: 403\nDetail: \u2014\n"))
        assertTrue(text, text.contains("Download type: Merge (audio + video)\n"))
        assertTrue(text, text.contains("Saved to: App storage\n"))
        assertTrue(text, text.endsWith("App: YFT \u2014\nAndroid: \u2014 (API 26)"))
    }

    /** P41: a failed DASH or merged download says how long its start took. */
    @Test
    fun failureDetailsShowHowLongTheStartTook() {
        val failed = row(
            DownloadTaskStatus.FAILED,
            plan = DownloadPlanType.AUDIO_VIDEO_MUX,
            failure = DownloadFailureReason.NETWORK,
        ).copy(
            failure = DownloadFailure(
                reason = DownloadFailureReason.NETWORK,
                detail = "SocketTimeoutException",
                startTimeline = "start: plan 0.0 s \u00b7 length 0.4 s (from first range) " +
                    "\u00b7 first byte 0.9 s \u00b7 first progress 1.0 s",
            ),
        )

        val text = failureDetailsText(failed, appVersion = "", androidRelease = "", sdkInt = 35)

        assertTrue(
            text,
            text.contains(
                "Detail: SocketTimeoutException\nStart: plan 0.0 s \u00b7 length 0.4 s " +
                    "(from first range) \u00b7 first byte 0.9 s \u00b7 first progress 1.0 s\n" +
                    "Download type: Merge (audio + video)\n",
            ),
        )
        val withoutTimes = failed.copy(failure = failed.failure?.copy(startTimeline = null))
        assertFalse(failureDetailsText(withoutTimes, "", "", 35).contains("Start:"))
    }

    @Test
    fun failureDetailsNameEachDownloadTypeAndPlace() {
        val mp3 = row(DownloadTaskStatus.FAILED, name = "Song.mp3").copy(mimeType = "audio/mpeg")
        val hls = row(DownloadTaskStatus.FAILED, plan = DownloadPlanType.HLS)
        val dash = row(DownloadTaskStatus.FAILED, plan = DownloadPlanType.DASH)

        assertEquals("MP3", planKindLabel(mp3))
        assertEquals("HLS", planKindLabel(hls))
        assertEquals("DASH", planKindLabel(dash))
        assertEquals(
            "Chosen folder (SAF)",
            destinationKindLabel(DownloadDestinationKind.SAF_DOCUMENT),
        )
        assertEquals(
            "8 steps, 8 names",
            8,
            DownloadFailureStage.entries.map(::stageLabel).toSet().size,
        )
    }

    @Test
    fun audioMadeFromAVideoWhoseSoundIsNotAacSaysToDownloadTheVideo() {
        val audio = row(
            DownloadTaskStatus.FAILED,
            name = "Rain.m4a",
            failure = DownloadFailureReason.INCOMPATIBLE_TRACKS,
        )
        val mp3 = audio.copy(displayName = "Rain.mp3")
        val video = audio.copy(displayName = "Rain.mp4")
        val network = TransferNetworkState.ALLOWED

        assertEquals("Failed · Sound can't be saved as audio", statusChip(audio, network)?.text)
        assertEquals("Failed · Sound can't be saved as audio", statusChip(mp3, network)?.text)
        assertEquals("Failed · Incompatible tracks", statusChip(video, network)?.text)
        val details = failureDetailsText(audio, "1.0", "15", 35).lines()
        assertEquals(
            "What to do: This video's sound can't be saved as audio. Download the video instead.",
            details[2],
        )
        assertTrue(details[1].startsWith("Reason: Incompatible tracks"))
        assertFalse(failureDetailsText(video, "1.0", "15", 35).contains("What to do"))
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
