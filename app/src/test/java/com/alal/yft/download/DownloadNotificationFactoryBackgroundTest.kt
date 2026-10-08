package com.alal.yft.download

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadTaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** P34: the ongoing notification's lines, the finished notice and Android's pause notice. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadNotificationFactoryBackgroundTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val factory = DownloadNotificationFactory(context).apply { createChannel() }

    @Test
    fun oneDownloadShowsItsSpeedAndSeveralGetOneLineEach() {
        val one = factory.active(
            listOf(backgroundTask("clip", downloaded = 45 * MB)),
            bytesPerSecond = mapOf("clip" to SPEED_1_2_MB),
        )
        val several = factory.active(
            listOf(
                backgroundTask("a", downloaded = 30 * MB),
                backgroundTask("b", downloaded = 60 * MB),
            ),
            bytesPerSecond = mapOf("a" to SPEED_1_2_MB, "b" to SPEED_1_2_MB),
        )

        assertEquals("clip", one.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(
            "45% · 1.2 MB/s · 45 MB of 100 MB · 46 s left",
            one.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertEquals(
            "Downloading 2 videos · 45%",
            several.extras.getCharSequence(Notification.EXTRA_TITLE).toString(),
        )
        assertEquals(
            listOf("a — 30% · 1.2 MB/s", "b — 60% · 1.2 MB/s"),
            several.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.map(CharSequence::toString),
        )
        assertEquals(
            "2 active downloads · 45%",
            several.publicVersion.extras.getString(Notification.EXTRA_TITLE),
        )
    }

    @Test
    fun finishedNoticesAlertOnTheirOwnChannelAndHideTitlesOnALockedScreen() {
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(DownloadNotificationFactory.FINISHED_CHANNEL_ID)
        val done = factory.finished(backgroundTask("clip", DownloadTaskStatus.COMPLETED))
        val failed = factory.finished(
            backgroundTask(
                "clip",
                DownloadTaskStatus.FAILED,
                failureReason = DownloadFailureReason.NETWORK,
            ),
        )

        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertEquals("Finished downloads", channel.name.toString())
        assertNotNull(done)
        assertEquals(DownloadNotificationFactory.FINISHED_CHANNEL_ID, done!!.channelId)
        assertEquals("Downloaded · clip", done.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(
            "Download finished",
            done.publicVersion.extras.getString(Notification.EXTRA_TITLE),
        )
        assertEquals(
            "Download failed",
            failed!!.publicVersion.extras.getString(Notification.EXTRA_TITLE),
        )
        assertNull(factory.finished(backgroundTask("clip")))
    }

    @Test
    fun androidsPauseNoticeSaysToOpenYft() {
        val notice = factory.pausedByAndroid(DownloadNotificationFactory.TIMEOUT_NOTICE)

        assertEquals(
            "Android paused downloads after 6 hours. Open YFT to resume.",
            notice.extras.getString(Notification.EXTRA_TITLE),
        )
        assertEquals(DownloadNotificationFactory.FINISHED_CHANNEL_ID, notice.channelId)
    }
}
