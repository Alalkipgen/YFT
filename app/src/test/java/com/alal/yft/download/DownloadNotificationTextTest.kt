package com.alal.yft.download

import com.alal.yft.core.model.download.AudioVideoMuxStage
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.download.Mp3Encoding
import com.alal.yft.download.policy.TransferNetworkState
import com.alal.yft.feature.downloads.failureLabel
import com.alal.yft.feature.downloads.mergedTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P34: what the ongoing notification and the finished notice say (R22). */
class DownloadNotificationTextTest {
    private fun content(
        vararg tasks: com.alal.yft.core.download.StoredDownloadTask,
        speeds: Map<String, Long> = emptyMap(),
        network: TransferNetworkState = TransferNetworkState.ALLOWED,
    ) = activeNotificationContent(tasks.toList(), speeds, network)

    @Test
    fun oneDownloadShowsPercentSpeedSizeAndTimeLeft() {
        val shown = content(
            backgroundTask("Holiday clip", downloaded = 45 * MB),
            speeds = mapOf("Holiday clip" to SPEED_1_2_MB),
        )

        assertEquals("Holiday clip", shown.title)
        assertEquals("45% · 1.2 MB/s · 45 MB of 100 MB · 46 s left", shown.text)
        assertEquals(45, shown.percent)
        assertTrue(shown.lines.isEmpty())
        assertEquals(1, shown.count)
    }

    @Test
    fun beforeTheFirstSpeedOnlyPercentAndSizeShow() {
        val shown = content(backgroundTask("a", downloaded = 45 * MB))

        assertEquals("45% · 45 MB of 100 MB", shown.text)
    }

    @Test
    fun unknownSizeShowsTheBytesSoFarAndTheSpeed() {
        val shown = content(
            backgroundTask("live", downloaded = 61 * MB, total = null),
            speeds = mapOf("live" to SPEED_1_2_MB),
        )

        assertEquals("61 MB · 1.2 MB/s", shown.text)
        assertNull(shown.percent)
    }

    @Test
    fun severalDownloadsShowTheirCountTotalAndOneLineEach() {
        val shown = content(
            backgroundTask("a", downloaded = 30 * MB),
            backgroundTask("b", downloaded = 60 * MB),
            backgroundTask("c", status = DownloadTaskStatus.QUEUED),
            speeds = mapOf("a" to SPEED_1_2_MB, "b" to SPEED_1_2_MB),
        )

        assertEquals("Downloading 3 videos · 30%", shown.title)
        assertEquals("2.4 MB/s · 2 min left", shown.text)
        assertEquals(
            listOf("a — 30% · 1.2 MB/s", "b — 60% · 1.2 MB/s", "c — Queued"),
            shown.lines,
        )
        assertEquals(30, shown.percent)
        assertEquals(3, shown.count)
    }

    @Test
    fun manyDownloadsListAtMostFiveLines() {
        val tasks = (1..7).map { backgroundTask("t$it", downloaded = 50 * MB) }

        val shown = content(*tasks.toTypedArray())

        assertEquals("Downloading 7 videos · 50%", shown.title)
        assertEquals(MAX_LINES, shown.lines.size)
    }

    @Test
    fun mergeSaveAndConversionStagesSayWhatRuns() {
        val merging = content(mergedTask("m", AudioVideoMuxStage.MUXING, 45, 100))
        val saving = content(mergedTask("s", AudioVideoMuxStage.SAVING, 80, 100))
        val converting = content(
            backgroundTask("song", downloaded = 5 * MB, total = 5 * MB, mimeType = MP3),
        )
        val copying = content(backgroundTask("clip", downloaded = 5 * MB, total = 5 * MB))

        assertEquals("Merging audio and video · 45%", merging.text)
        assertEquals(45, merging.percent)
        assertEquals("Saving to Download/YFT · 80%", saving.text)
        assertEquals(80, saving.percent)
        assertEquals("Converting to MP3", converting.text)
        assertNull(converting.percent)
        assertTrue(copying.text, copying.text.startsWith("Saving to Download/YFT"))
        assertNull(copying.percent)
    }

    @Test
    fun waitingAndQueuedDownloadsSayWhy() {
        val waiting = backgroundTask("w", status = DownloadTaskStatus.WAITING_FOR_NETWORK)

        assertEquals(
            "Waiting for Wi-Fi",
            content(waiting, network = TransferNetworkState.WAITING_FOR_UNMETERED).text,
        )
        assertEquals(
            "Waiting for network",
            content(waiting, network = TransferNetworkState.OFFLINE).text,
        )
        assertEquals(
            "Queued",
            content(backgroundTask("q", status = DownloadTaskStatus.QUEUED)).text,
        )
        assertEquals(
            "Pausing",
            content(backgroundTask("p", status = DownloadTaskStatus.PAUSING)).text,
        )
    }

    @Test
    fun finishedNoticeNamesTheVideoAndAShortReasonButNoPath() {
        val done = backgroundTask("Holiday clip", status = DownloadTaskStatus.COMPLETED)
        val failed = backgroundTask(
            "Holiday clip",
            status = DownloadTaskStatus.FAILED,
            failureReason = DownloadFailureReason.NETWORK,
        )
        val expired = backgroundTask("Holiday clip", status = DownloadTaskStatus.NEEDS_REFRESH)

        assertEquals("Downloaded · Holiday clip", finishedNoticeText(done))
        assertEquals(
            "Download failed · Holiday clip — ${failureLabel(DownloadFailureReason.NETWORK)}",
            finishedNoticeText(failed),
        )
        assertEquals("Download failed · Holiday clip — link expired", finishedNoticeText(expired))
        assertFalse(finishedNoticeText(done).orEmpty().contains("/"))
        assertNull(finishedNoticeText(backgroundTask("r")))
    }

    @Test
    fun longTitlesAreCutToOneLine() {
        val title = shortTitle(backgroundTask("x", displayName = "${"a".repeat(60)}.mp4"))

        assertEquals(40, title.length)
        assertTrue(title.endsWith("…"))
    }

    private companion object {
        const val MP3 = Mp3Encoding.MIME_TYPE
    }
}
