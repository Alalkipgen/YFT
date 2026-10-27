package com.alal.yft.download

import android.content.pm.ServiceInfo
import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.download.policy.TransferNetworkState
import com.alal.yft.feature.downloads.DownloadAction
import com.alal.yft.feature.downloads.DownloadSpeedMeter
import com.alal.yft.feature.downloads.DownloadStatusChip
import com.alal.yft.feature.downloads.DownloadsFilter
import com.alal.yft.feature.downloads.DownloadsUiState
import com.alal.yft.feature.downloads.RENEWING_LINK_LABEL
import com.alal.yft.feature.downloads.TransferRateTracker
import com.alal.yft.feature.downloads.statusChip
import com.alal.yft.ui.components.YftStatusTone
import com.alal.yft.ui.theme.YftIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Track E (E1): a download whose link died while its page is looked up again
 * ([DownloadTaskStatus.RENEWING_LINK]) still counts as moving work: its row, its slot, the
 * service, its locks and the notification (G5).
 */
class RenewingLinkStatusTest {
    private var now = 100_000L
    private val wake = Lock()
    private val wifi = Lock()
    private val host = Host()

    @Test
    fun aDownloadGettingAFreshLinkStillUsesTheNetwork() {
        assertEquals(TaskWork.DOWNLOADING, renewing("a").work())
        assertTrue(BackgroundWork.of(listOf(renewing("a"))).downloading)
    }

    @Test
    fun theNotificationSaysAFreshLinkIsComing() {
        val one = activeNotificationContent(
            listOf(renewing("a")),
            emptyMap(),
            TransferNetworkState.ALLOWED,
        )
        assertEquals(RENEWING_LINK_LABEL, one.text)
        assertEquals("Getting a fresh link\u2026", one.text)

        val two = activeNotificationContent(
            listOf(renewing("a"), backgroundTask("b", downloaded = MB)),
            emptyMap(),
            TransferNetworkState.ALLOWED,
        )
        assertEquals(2, two.count)
        assertEquals("a \u2014 Getting a fresh link\u2026", two.lines.first())
    }

    @Test
    fun theServiceStaysUpWhileALinkIsRenewedAndTheNoticeComesOnceAtTheEnd() {
        val service = DownloadServiceController(
            host = host,
            locks = BackgroundWorkLocks(wake, wifi, nowMs = { now }),
            health = InMemoryBackgroundHealthStore(),
            speeds = DownloadSpeedMeter(TransferRateTracker(nowMs = { now })),
            nowMs = { now },
            sdkInt = 34,
            doneNotice = true,
            log = {},
        ).apply { onStarted(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) }

        service.onTasks(listOf(backgroundTask("a", downloaded = MB)), ALLOWED)
        now += 1_000
        service.onTasks(listOf(renewing("a")), ALLOWED)

        assertFalse(service.isStopped)
        assertTrue(wake.isHeld)
        assertTrue(wifi.isHeld)
        assertEquals(RENEWING_LINK_LABEL, host.active.last().text)
        assertTrue("no \"Download failed\" while renewing", host.finished.isEmpty())

        now += 1_000
        service.onTasks(listOf(backgroundTask("a", downloaded = 2 * MB)), ALLOWED)
        now += 1_000
        val done = backgroundTask("a", DownloadTaskStatus.COMPLETED, 100 * MB)
        service.onTasks(listOf(done), ALLOWED)

        assertTrue(service.isStopped)
        assertEquals(listOf(DownloadTaskStatus.COMPLETED), host.finished.map { it.status })
    }

    @Test
    fun aRenewingRowKeepsItsSlotShowsItsChipAndCanBePausedOrCancelled() {
        val row = DownloadsUiState.from(listOf(renewing("a"))).rows.single()

        assertTrue(row.isOccupyingQueue)
        assertFalse(row.isTerminal)
        assertEquals(setOf(DownloadAction.PAUSE, DownloadAction.CANCEL), row.availableActions)
        assertTrue(DownloadsFilter.ACTIVE.matches(row.status))
        assertEquals(
            DownloadStatusChip(RENEWING_LINK_LABEL, YftStatusTone.Active, YftIcons.Refresh),
            statusChip(row, ALLOWED),
        )
    }

    private fun renewing(id: String): StoredDownloadTask = backgroundTask(
        id = id,
        status = DownloadTaskStatus.RENEWING_LINK,
        downloaded = MB,
        failureReason = DownloadFailureReason.ACCESS_DENIED,
    )

    private class Host : DownloadServiceController.Host {
        val active = mutableListOf<ActiveNotificationContent>()
        val finished = mutableListOf<StoredDownloadTask>()

        override fun startForeground(serviceTypes: Int) = Unit

        override fun showActive(content: ActiveNotificationContent) {
            active += content
        }

        override fun showFinished(task: StoredDownloadTask) {
            finished += task
        }

        override fun showNotice(text: String) = Unit

        override fun stop() = Unit
    }

    private class Lock : WorkLock {
        override var isHeld = false

        override fun acquire(timeoutMs: Long) {
            isHeld = true
        }

        override fun release() {
            isHeld = false
        }
    }

    private companion object {
        val ALLOWED = TransferNetworkState.ALLOWED
    }
}
