package com.alal.yft.download

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.model.download.AudioVideoMuxStage
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DownloadSegment
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.feature.downloads.mergedTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        // P34: the title counts the downloads with their percent; the text holds the speed.
        assertEquals(
            "Downloading 2 videos · 50%",
            notification.extras.getString(Notification.EXTRA_TITLE),
        )
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

        // P34: one download shows its own title.
        assertEquals("unknown", notification.extras.getString(Notification.EXTRA_TITLE))
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
        assertEquals("holiday", notification.extras.getString(Notification.EXTRA_TITLE))
        assertNotNull(public)
        assertEquals("1 active download · 10%", public.extras.getString(Notification.EXTRA_TITLE))
        assertNull(public.extras.getCharSequence(Notification.EXTRA_TEXT))
    }

    @Test
    fun `a merged download shows its merge and then its copy with their percent`() {
        val merging = factory.active(
            listOf(mergedTask("m", AudioVideoMuxStage.MUXING, stepDone = 45, stepTotal = 100)),
        )
        val saving = factory.active(
            listOf(mergedTask("s", AudioVideoMuxStage.SAVING, stepDone = 80, stepTotal = 100)),
        )
        val starting = factory.active(listOf(mergedTask("r", AudioVideoMuxStage.READY_TO_MUX)))

        assertEquals(
            "Merging audio and video · 45%",
            merging.extras.getString(Notification.EXTRA_TEXT),
        )
        assertEquals(45, merging.extras.getInt(Notification.EXTRA_PROGRESS))
        assertFalse(merging.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
        assertEquals(45, merging.publicVersion.extras.getInt(Notification.EXTRA_PROGRESS))
        assertNull(merging.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertEquals(
            "Saving to Download/YFT · 80%",
            saving.extras.getString(Notification.EXTRA_TEXT),
        )
        assertEquals(80, saving.extras.getInt(Notification.EXTRA_PROGRESS))
        assertEquals("Merging audio and video", starting.extras.getString(Notification.EXTRA_TEXT))
        assertTrue(starting.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
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