package com.alal.yft.download

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DownloadSegment
import com.alal.yft.core.model.download.DownloadTaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DownloadNotificationFactoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val factory = DownloadNotificationFactory(context)

    @Test
    fun `channel is low importance and notification reports aggregate progress`() {
        factory.createChannel()

        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(DownloadNotificationFactory.CHANNEL_ID)
        val notification = factory.active(
            listOf(
                task("one", downloaded = 25),
                task("two", downloaded = 75),
            ),
        )

        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertEquals("2 active downloads", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(50, notification.extras.getInt(Notification.EXTRA_PROGRESS))
        assertFalse(notification.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
        assertEquals(Notification.FLAG_ONGOING_EVENT, notification.flags and Notification.FLAG_ONGOING_EVENT)
    }

    @Test
    fun `unknown total uses indeterminate progress`() {
        val unknown = task("unknown", downloaded = 0).copy(
            totalBytes = null,
            checkpoint = DirectTransferCheckpoint(
                totalBytes = null,
                entityTag = null,
                lastModified = null,
                segments = emptyList(),
            ),
        )

        val notification = factory.active(listOf(unknown))

        assertEquals("1 active download", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(true, notification.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
    }

    @Test
    fun `secure lock screens see counts but never titles`() {
        factory.createChannel()
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(DownloadNotificationFactory.CHANNEL_ID)

        val notification = factory.active(listOf(task("holiday", downloaded = 10)))
        val public = notification.publicVersion

        assertEquals(Notification.VISIBILITY_PRIVATE, channel.lockscreenVisibility)
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        assertEquals("holiday.bin", notification.extras.getString(Notification.EXTRA_TEXT))
        assertNotNull(public)
        assertEquals("1 active download", public.extras.getString(Notification.EXTRA_TITLE))
        assertNull(public.extras.getCharSequence(Notification.EXTRA_TEXT))
    }

    private fun task(id: String, downloaded: Long): StoredDownloadTask {
        val checkpoint = DirectTransferCheckpoint(
            totalBytes = 100,
            entityTag = "\"fixture\"",
            lastModified = null,
            segments = listOf(DownloadSegment(0, 0, 99, downloaded)),
        )
        return StoredDownloadTask(
            id = id,
            displayName = "$id.bin",
            status = DownloadTaskStatus.RUNNING,
            totalBytes = 100,
            downloadedBytes = downloaded,
            mimeType = "application/octet-stream",
            destinationKind = DownloadDestinationKind.APP_PRIVATE,
            destinationUri = null,
            preferredSegmentCount = 1,
            requiresLinkRefresh = false,
            failureReason = null,
            checkpoint = checkpoint,
            createdAtEpochMs = 1,
            updatedAtEpochMs = 1,
        )
    }
}