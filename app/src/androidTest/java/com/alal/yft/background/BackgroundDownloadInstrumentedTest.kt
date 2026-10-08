package com.alal.yft.background

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.UiDevice
import com.alal.yft.background.BackgroundTestKit.context
import com.alal.yft.background.BackgroundTestKit.diag
import com.alal.yft.background.BackgroundTestKit.notificationText
import com.alal.yft.background.BackgroundTestKit.serviceInForeground
import com.alal.yft.background.BackgroundTestKit.task
import com.alal.yft.background.BackgroundTestKit.waitFor
import com.alal.yft.core.download.DirectTransferEngine
import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.core.download.FileDownloadDestination
import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.download.DownloadForegroundService
import com.alal.yft.download.DownloadNotificationFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P34 on the CI emulator: a download of about 40 s keeps going after Home with the download
 * service in the foreground, its notification shows the percent and the speed, and the
 * "Downloaded · …" notice follows. The process under instrumentation is never frozen, so this
 * proves the service and its notification, not HyperOS's freezer (the owner's phone does).
 */
@RunWith(AndroidJUnit4::class)
class BackgroundDownloadInstrumentedTest {
    private val device = UiDevice.getInstance(BackgroundTestKit.instrumentation)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val directory = File(context.cacheDir, "p34-download")
    private var queue: DownloadQueue? = null

    @Before
    fun setUp() {
        BackgroundTestKit.allowNotifications()
        directory.deleteRecursively()
        directory.mkdirs()
    }

    @After
    fun tearDown() {
        BackgroundTestKit.cleanUp(queue)
        scope.cancel()
        directory.deleteRecursively()
    }

    @Test
    fun aDownloadKeepsGoingAtHomeWithItsPercentAndSpeedInTheNotification() = runBlocking<Unit> {
        val engine = DirectTransferEngine(
            client = BackgroundTestKit.slowClient(LENGTH, BYTES_PER_SECOND),
            policy = DirectTransferEngine.Policy(callTimeoutSeconds = CALL_TIMEOUT_SECONDS),
        )
        val queue = BackgroundTestKit.queue(BackgroundTestKit.dispatcher(direct = engine), scope)
        this@BackgroundDownloadInstrumentedTest.queue = queue
        DownloadForegroundService.testQueue = queue
        val id = queue.enqueue(
            plan = DirectDownloadPlan(
                taskId = "p34-download",
                sourceUrl = SOURCE_URL,
                suggestedFileName = NAME,
                requestContext = BrowserRequestContext(
                    pageUrl = null,
                    userAgent = null,
                    cookie = null,
                ),
                mimeType = BackgroundTestKit.VIDEO_MP4,
                expectedBytes = LENGTH,
                preferredSegmentCount = 1,
            ),
            metadata = BackgroundTestKit.metadata(SOURCE_URL, LENGTH, NAME),
            destination = FileDownloadDestination(
                partialFile = File(directory, "$NAME.part"),
                completedFile = File(directory, NAME),
            ),
        )
        DownloadForegroundService.start(context)
        device.pressHome()

        val first = waitFor(START_TIMEOUT_MS, "the first bytes") {
            task(queue, id)?.downloadedBytes?.takeIf { it > 0L }
        }
        val samples = (1..SAMPLES).map {
            Thread.sleep(SAMPLE_MS)
            task(queue, id)?.downloadedBytes ?: 0L
        }
        val atHome = listOf(first) + samples
        diag("download-at-home", "bytes" to atHome.joinToString(","))
        assertTrue(
            "bytes rise for 20 s at Home: $atHome",
            atHome.zipWithNext().all { (before, after) -> after > before },
        )
        assertTrue("the service runs in the foreground", serviceInForeground())
        val shown = waitFor(NOTICE_TIMEOUT_MS, "percent and speed in the notification") {
            notificationText(DownloadNotificationFactory.NOTIFICATION_ID)
                ?.takeIf { "%" in it && "/s" in it }
        }
        diag("notification", "text" to shown)
        assertTrue(shown, shown.startsWith("yft-p34-clip | "))

        waitFor(FINISH_TIMEOUT_MS, "the download to finish") {
            task(queue, id)?.takeIf { it.status == DownloadTaskStatus.COMPLETED }
        }
        assertEquals(LENGTH, File(directory, NAME).length())
        val notice = waitFor(NOTICE_TIMEOUT_MS, "the finished notice") {
            notificationText(DownloadNotificationFactory.FINISHED_NOTIFICATION_ID, tag = id)
        }
        assertEquals("Downloaded · yft-p34-clip", notice)
        waitFor(NOTICE_TIMEOUT_MS, "the service to stop") {
            Unit.takeIf {
                !serviceInForeground() &&
                    notificationText(DownloadNotificationFactory.NOTIFICATION_ID) == null
            }
        }
    }

    private companion object {
        const val NAME = "yft-p34-clip.mp4"
        const val SOURCE_URL = "https://media.example.test/p34/clip.mp4"
        const val BYTES_PER_SECOND = 200L * 1_024
        const val LENGTH = 8L * 1_024 * 1_024
        const val CALL_TIMEOUT_SECONDS = 300L
        const val SAMPLES = 4
        const val SAMPLE_MS = 5_000L
        const val START_TIMEOUT_MS = 15_000L
        const val NOTICE_TIMEOUT_MS = 10_000L
        const val FINISH_TIMEOUT_MS = 90_000L
    }
}
