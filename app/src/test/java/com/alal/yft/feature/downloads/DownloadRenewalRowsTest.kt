package com.alal.yft.feature.downloads

import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.download.DownloadPlanType
import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DownloadFailure
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadFailureStage
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.download.DownloadRenewals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Track E (E-D6): the rows of downloads whose link YFT can renew, and their Details. */
class DownloadRenewalRowsTest {
    @Test
    fun onlyALinkExpiredRowYftCanLookUpAgainOffersRetry() {
        val state = DownloadsUiState.from(
            listOf(
                task("renewable", DownloadTaskStatus.NEEDS_REFRESH),
                task("restored", DownloadTaskStatus.NEEDS_REFRESH),
                task("failed", DownloadTaskStatus.FAILED),
                task("running", DownloadTaskStatus.RUNNING),
            ),
            renewals = DownloadRenewals(renewable = setOf("renewable", "running")),
        )

        val rows = state.rows.associateBy(DownloadRowUiState::id)
        assertTrue(rows.getValue("renewable").canRenew)
        assertEquals(
            setOf(DownloadAction.RETRY, DownloadAction.DELETE),
            rows.getValue("renewable").availableActions,
        )
        assertFalse(rows.getValue("restored").canRenew)
        assertEquals(setOf(DownloadAction.DELETE), rows.getValue("restored").availableActions)
        // A failed download keeps its Retry whether or not YFT has its source.
        assertTrue(DownloadAction.RETRY in rows.getValue("failed").availableActions)
        // A running one renews by itself: no Retry while it runs.
        assertFalse(DownloadAction.RETRY in rows.getValue("running").availableActions)
    }

    /** v1.0.2: a row whose page, read again, had no player data is marked; Retry stays. */
    @Test
    fun aRowWhosePageReadAgainHadNoPlayerIsMarkedAndKeepsRetry() {
        val state = DownloadsUiState.from(
            listOf(
                task("notice", DownloadTaskStatus.NEEDS_REFRESH),
                task("renewable", DownloadTaskStatus.NEEDS_REFRESH),
            ),
            renewals = DownloadRenewals(
                renewable = setOf("notice", "renewable"),
                noPlayer = setOf("notice"),
            ),
        )

        val rows = state.rows.associateBy(DownloadRowUiState::id)
        assertTrue(rows.getValue("notice").noPlayerOnPage)
        assertTrue(DownloadAction.RETRY in rows.getValue("notice").availableActions)
        assertFalse(rows.getValue("renewable").noPlayerOnPage)
    }

    @Test
    fun aDownloadKeepsItsFreshLinkLinesWhileItRunsAndAfterItCompleted() {
        val line = "Fresh link (403 at 41.0 MB): resumed at 41.0 MB"
        val state = DownloadsUiState.from(
            listOf(
                task("running", DownloadTaskStatus.RUNNING),
                task("done", DownloadTaskStatus.COMPLETED),
                task("plain", DownloadTaskStatus.RUNNING),
            ),
            renewals = DownloadRenewals(
                notes = mapOf("running" to listOf(line), "done" to listOf(line, line)),
            ),
        )

        val rows = state.rows.associateBy(DownloadRowUiState::id)
        assertEquals(listOf(line), rows.getValue("running").renewalLines)
        assertTrue(rows.getValue("running").showsDetails)
        assertFalse(rows.getValue("running").showsFailureDetails)
        assertEquals(listOf(line, line), rows.getValue("done").renewalLines)
        assertTrue(rows.getValue("done").showsDetails)
        assertFalse(rows.getValue("plain").showsDetails)
    }

    @Test
    fun aRenewedDownloadsDetailsListHowEachFreshLinkWentWithoutLinks() {
        val row = row(DownloadTaskStatus.RUNNING).copy(
            renewalLines = listOf(
                "Fresh link (403 at 41.0 MB): resumed at 41.0 MB",
                "Fresh link (410 at 80.0 MB): https://rr1.example.test/v?sig=1 from 10.0.0.2",
            ),
        )

        val text = downloadDetailsText(row, "1.0.1", "15", 35)

        assertEquals(
            listOf(
                "YFT download",
                "Status: RUNNING",
                "Fresh link (403 at 41.0 MB): resumed at 41.0 MB",
                "Fresh link (410 at 80.0 MB): [link] from [ip]",
                "Download type: Direct",
                "Saved to: Downloads (MediaStore)",
                "App: YFT 1.0.1",
                "Android: 15 (API 35)",
            ).joinToString("\n"),
            text,
        )
        for (leak in listOf("http", "example.test", "10.0.0.2", "sig=")) {
            assertFalse("$leak must not be copied", text.contains(leak))
        }
    }

    @Test
    fun aFreshLinkThatFailedIsListedInTheFailuresDetails() {
        val row = row(DownloadTaskStatus.NEEDS_REFRESH).copy(
            requiresLinkRefresh = true,
            failureReason = DownloadFailureReason.EXPIRED_URL,
            failure = DownloadFailure(
                reason = DownloadFailureReason.EXPIRED_URL,
                httpStatusCode = 403,
                stage = DownloadFailureStage.CONNECT,
                detail = "Fresh link: the fresh file did not answer (HTTP 403)",
            ),
            renewalLines = listOf("Fresh link (403 at 41.0 MB): resumed at 41.0 MB"),
            canRenew = true,
        )

        val lines = downloadDetailsText(row, "1.0.1", "15", 35).lines()

        assertEquals("YFT download failure", lines.first())
        assertTrue(lines.contains("Detail: Fresh link: the fresh file did not answer (HTTP 403)"))
        val note = lines.indexOf("Fresh link (403 at 41.0 MB): resumed at 41.0 MB")
        assertTrue(lines.toString(), note > 0)
        assertEquals("Download type: Direct", lines[note + 1])
    }

    private fun task(id: String, status: DownloadTaskStatus) = StoredDownloadTask(
        id = id,
        displayName = "$id.mp4",
        status = status,
        planType = DownloadPlanType.DIRECT,
        totalBytes = 4_096,
        downloadedBytes = 0,
        mimeType = "video/mp4",
        destinationKind = DownloadDestinationKind.MEDIA_STORE,
        destinationUri = null,
        preferredSegmentCount = 4,
        requiresLinkRefresh = status == DownloadTaskStatus.NEEDS_REFRESH,
        failureReason = DownloadFailureReason.EXPIRED_URL.takeIf {
            status == DownloadTaskStatus.NEEDS_REFRESH || status == DownloadTaskStatus.FAILED
        },
        checkpoint = DirectTransferCheckpoint(null, null, null, emptyList()),
        createdAtEpochMs = 1,
        updatedAtEpochMs = 1,
    )

    private fun row(status: DownloadTaskStatus) = DownloadRowUiState(
        id = "a",
        displayName = "a.mp4",
        status = status,
        planType = DownloadPlanType.DIRECT,
        destinationKind = DownloadDestinationKind.MEDIA_STORE,
        downloadedBytes = 1_024,
        totalBytes = 4_096,
        progressPercent = 25,
        requiresLinkRefresh = false,
        failureReason = null,
    )
}
