package com.alal.yft.download

import android.content.pm.ServiceInfo
import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.model.download.AudioVideoMuxStage
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.download.Mp3Encoding
import com.alal.yft.download.policy.TransferNetworkState
import com.alal.yft.feature.downloads.DownloadSpeedMeter
import com.alal.yft.feature.downloads.TransferRateTracker
import com.alal.yft.feature.downloads.mergedTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P34: the download service's decisions — foreground, locks, types, notices and freezes. */
class DownloadServiceControllerTest {
    private var now = 100_000L
    private val wake = FakeLock()
    private val wifi = FakeLock()
    private val health = InMemoryBackgroundHealthStore()
    private val host = FakeHost()
    private val logs = mutableListOf<String>()

    private fun controller(sdkInt: Int = 34, doneNotice: Boolean = true) =
        DownloadServiceController(
            host = host,
            locks = BackgroundWorkLocks(wake, wifi, nowMs = { now }),
            health = health,
            speeds = DownloadSpeedMeter(TransferRateTracker(nowMs = { now })),
            nowMs = { now },
            sdkInt = sdkInt,
            doneNotice = doneNotice,
            log = { logs += it },
        ).apply { onStarted(DATA_SYNC) }

    private fun DownloadServiceController.show(vararg tasks: StoredDownloadTask) {
        onTasks(tasks.toList(), TransferNetworkState.ALLOWED)
    }

    @Test
    fun staysInTheForegroundThroughDownloadMergeAndSaveThenStops() {
        val service = controller()

        service.show(mergedTask("m", AudioVideoMuxStage.DOWNLOADING_TRACKS))
        assertFalse(service.isStopped)
        assertTrue(wake.isHeld)
        assertTrue(wifi.isHeld)

        now += 1_000
        service.show(mergedTask("m", AudioVideoMuxStage.MUXING, 45, 100))
        assertFalse(service.isStopped)
        assertTrue(wake.isHeld)
        assertFalse(wifi.isHeld)
        assertEquals("Merging audio and video · 45%", host.active.last().text)

        now += 1_000
        service.show(mergedTask("m", AudioVideoMuxStage.SAVING, 80, 100))
        assertFalse(service.isStopped)
        assertTrue(wake.isHeld)
        assertEquals("Saving to Download/YFT · 80%", host.active.last().text)

        now += 1_000
        val done = mergedTask(
            "m",
            AudioVideoMuxStage.COMPLETED,
            status = DownloadTaskStatus.COMPLETED,
        )
        service.show(done)
        assertTrue(service.isStopped)
        assertEquals(1, host.stops)
        assertFalse(wake.isHeld)
        assertFalse(wifi.isHeld)
        assertEquals(listOf("m"), host.finished.map { it.id })
    }

    @Test
    fun mp3ConversionKeepsTheWakeLockWithoutWifi() {
        val service = controller()

        service.show(
            backgroundTask("song", downloaded = MB, total = MB, mimeType = Mp3Encoding.MIME_TYPE),
        )

        assertTrue(wake.isHeld)
        assertFalse(wifi.isHeld)
        assertEquals("Converting to MP3", host.active.last().text)
    }

    @Test
    fun locksAreReleasedWhenDownloadsPauseOrFail() {
        val service = controller()

        service.show(backgroundTask("a", downloaded = MB), backgroundTask("b", QUEUED))
        assertTrue(wake.isHeld)
        assertTrue(wifi.isHeld)

        service.show(backgroundTask("a", PAUSED, MB), backgroundTask("b", QUEUED))
        assertFalse(service.isStopped)
        assertFalse(wake.isHeld)
        assertFalse(wifi.isHeld)

        service.show(
            backgroundTask("a", PAUSED, MB),
            backgroundTask("b", FAILED, failureReason = DownloadFailureReason.NETWORK),
        )
        assertTrue(service.isStopped)
        assertFalse(wake.isHeld)
        // "b" never ran while this service watched, and "a" was paused by the user.
        assertTrue(host.finished.isEmpty())
    }

    @Test
    fun aFailedDownloadGetsItsNoticeUnlessNoticesAreOff() {
        val failed = backgroundTask("a", FAILED, failureReason = DownloadFailureReason.NETWORK)
        val service = controller()
        service.show(backgroundTask("a", downloaded = MB))
        service.show(failed)

        val quiet = FakeHost()
        val silent = DownloadServiceController(
            host = quiet,
            locks = BackgroundWorkLocks(FakeLock(), FakeLock(), nowMs = { now }),
            health = health,
            speeds = DownloadSpeedMeter(),
            nowMs = { now },
            sdkInt = 34,
            doneNotice = false,
        )
        silent.onTasks(listOf(backgroundTask("a", downloaded = MB)), TransferNetworkState.ALLOWED)
        silent.onTasks(listOf(failed), TransferNetworkState.ALLOWED)

        assertEquals(listOf(DownloadTaskStatus.FAILED), host.finished.map { it.status })
        assertTrue(quiet.finished.isEmpty())
        assertTrue(silent.isStopped)
    }

    @Test
    fun mediaProcessingIsDeclaredOnlyOnAndroid15WhileAMergeRuns() {
        val android14 = controller(sdkInt = 34)
        android14.show(mergedTask("m", AudioVideoMuxStage.MUXING, 45, 100))
        assertTrue(host.types.isEmpty())
        assertEquals(DATA_SYNC, android14.declaredTypes)

        val android15 = controller(sdkInt = 35)
        android15.show(backgroundTask("a", downloaded = MB))
        assertTrue(host.types.isEmpty())
        android15.show(mergedTask("m", AudioVideoMuxStage.MUXING, 45, 100))
        android15.show(backgroundTask("a", downloaded = 2 * MB))

        assertEquals(listOf(DATA_SYNC or MEDIA_PROCESSING, DATA_SYNC), host.types)
        assertEquals(0, DownloadServiceController.serviceTypes(28, processing = true))
        assertEquals(DATA_SYNC, DownloadServiceController.serviceTypes(29, processing = true))
    }

    @Test
    fun theNotificationChangesAtMostOnceASecondWithTheMeasuredSpeed() {
        val service = controller()

        service.show(backgroundTask("a", downloaded = 10 * MB))
        now += 300
        service.show(backgroundTask("a", downloaded = 11 * MB))
        assertEquals(1, host.active.size)
        assertEquals("10% · 10 MB of 100 MB", host.active.last().text)

        now += 700
        service.onTick()
        assertEquals(2, host.active.size)
        assertEquals("11% · 1.0 MB/s · 11 MB of 100 MB · 2 min left", host.active.last().text)

        now += 500
        service.show(backgroundTask("a", downloaded = 12 * MB))
        assertEquals(2, host.active.size)
    }

    @Test
    fun aLateTickWhileTheWakeLockIsHeldIsAFreezeAndBringsTheCardBack() {
        health.markDownloadStarted()
        health.hideBatteryCard()
        val service = controller()
        service.show(backgroundTask("a", downloaded = MB))

        service.onTick()
        now += 1_000
        service.onTick()
        assertEquals(0, health.health.value.freezeCount)

        now += 101_000
        service.onTick()

        val recorded = health.health.value
        assertEquals(1, recorded.freezeCount)
        assertEquals(100_000L, recorded.lastFreezeLostMs)
        assertFalse(recorded.batteryCardHidden)
        assertEquals(listOf("Background freeze: 100 s lost while downloading"), logs)
        assertEquals(
            "Your phone paused YFT in the background for 1 min 40 s.",
            freezeMessage(recorded.lastFreezeLostMs!!),
        )
    }

    @Test
    fun noFreezeIsCountedWhileNothingRuns() {
        val service = controller()
        service.show(backgroundTask("q", QUEUED))

        service.onTick()
        now += 120_000
        service.onTick()

        assertEquals(0, health.health.value.freezeCount)
        assertFalse(wake.isHeld)
    }

    @Test
    fun androidsTimeLimitStopsTheServiceWithANotice() {
        val service = controller()
        service.show(backgroundTask("a", downloaded = MB))

        service.onTimeout()

        assertEquals(listOf(DownloadNotificationFactory.TIMEOUT_NOTICE), host.notices)
        assertTrue(service.isStopped)
        assertFalse(wake.isHeld)
        assertFalse(wifi.isHeld)
    }

    @Test
    fun locksAreRenewedEveryMinuteWithATimeout() {
        val service = controller()
        service.show(backgroundTask("a", downloaded = MB))
        repeat(59) {
            now += 1_000
            service.onTick()
        }
        assertEquals(1, wake.acquired)

        now += 1_000
        service.onTick()

        assertEquals(2, wake.acquired)
        assertEquals(BackgroundWorkLocks.LOCK_TIMEOUT_MS, wake.lastTimeoutMs)
        assertEquals(0, health.health.value.freezeCount)
        service.onDestroy()
        assertFalse(wake.isHeld)
    }

    private class FakeHost : DownloadServiceController.Host {
        val types = mutableListOf<Int>()
        val active = mutableListOf<ActiveNotificationContent>()
        val finished = mutableListOf<StoredDownloadTask>()
        val notices = mutableListOf<String>()
        var stops = 0

        override fun startForeground(serviceTypes: Int) {
            types += serviceTypes
        }

        override fun showActive(content: ActiveNotificationContent) {
            active += content
        }

        override fun showFinished(task: StoredDownloadTask) {
            finished += task
        }

        override fun showNotice(text: String) {
            notices += text
        }

        override fun stop() {
            stops += 1
        }
    }

    private class FakeLock : WorkLock {
        override var isHeld = false
        var acquired = 0
        var lastTimeoutMs = 0L

        override fun acquire(timeoutMs: Long) {
            isHeld = true
            acquired += 1
            lastTimeoutMs = timeoutMs
        }

        override fun release() {
            isHeld = false
        }
    }

    private companion object {
        const val DATA_SYNC = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        const val MEDIA_PROCESSING = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        val QUEUED = DownloadTaskStatus.QUEUED
        val PAUSED = DownloadTaskStatus.PAUSED
        val FAILED = DownloadTaskStatus.FAILED
    }
}
