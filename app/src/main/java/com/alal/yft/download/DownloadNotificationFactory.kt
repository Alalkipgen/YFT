package com.alal.yft.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.alal.yft.MainActivity
import com.alal.yft.R
import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.download.policy.TransferNetworkState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadNotificationFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.download_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.download_notification_channel_description)
                setShowBadge(false)
                // Titles name what the user is saving, so a secure lock screen shows only counts.
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
        // P34 (G5): "Downloaded · <title>" and "Download failed · …" once a download ends.
        manager.createNotificationChannel(
            NotificationChannel(
                FINISHED_CHANNEL_ID,
                FINISHED_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = FINISHED_CHANNEL_DESCRIPTION
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
    }

    fun preparing(): Notification = builder()
        .setContentTitle(context.getString(R.string.download_notification_preparing))
        .setProgress(0, 0, true)
        .build()

    /**
     * The one ongoing notification (P34): [bytesPerSecond] holds the measured speed of running
     * tasks by id and [network] says why waiting tasks wait.
     */
    fun active(
        tasks: List<StoredDownloadTask>,
        bytesPerSecond: Map<String, Long> = emptyMap(),
        network: TransferNetworkState = TransferNetworkState.ALLOWED,
    ): Notification = active(activeNotificationContent(tasks, bytesPerSecond, network))

    internal fun active(content: ActiveNotificationContent): Notification {
        val percent = content.percent
        val publicTitle = listOfNotNull(
            context.resources.getQuantityString(
                R.plurals.download_notification_active,
                content.count,
                content.count,
            ),
            percent?.let { "$it%" },
        ).joinToString(SEPARATOR)
        val builder = builder()
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setProgress(PROGRESS_MAX, percent ?: 0, percent == null)
            .setPublicVersion(
                builder()
                    .setContentTitle(publicTitle)
                    .setProgress(PROGRESS_MAX, percent ?: 0, percent == null)
                    .build(),
            )
            .addAction(
                0,
                context.getString(R.string.download_notification_pause_all),
                pauseAllIntent(),
            )
        if (content.lines.isNotEmpty()) {
            builder.setStyle(
                NotificationCompat.InboxStyle()
                    .setBigContentTitle(content.title)
                    .setSummaryText(content.text)
                    .also { style -> content.lines.forEach(style::addLine) },
            )
        }
        return builder.build()
    }

    /** "Downloaded · <title>" or "Download failed · <title> — <reason>"; null otherwise. */
    fun finished(task: StoredDownloadTask): Notification? {
        val text = finishedNoticeText(task) ?: return null
        return NotificationCompat.Builder(context, FINISHED_CHANNEL_ID)
            .setSmallIcon(
                if (task.status == DownloadTaskStatus.COMPLETED) {
                    android.R.drawable.stat_sys_download_done
                } else {
                    android.R.drawable.stat_notify_error
                },
            )
            .setContentTitle(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, FINISHED_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(
                        if (task.status == DownloadTaskStatus.COMPLETED) {
                            "Download finished"
                        } else {
                            "Download failed"
                        },
                    )
                    .build(),
            )
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
    }

    /** Android stopped the download service: what to do, without names (P34). */
    fun pausedByAndroid(text: String): Notification =
        // On the finished channel, which alerts: the downloads stopped until YFT is opened.
        NotificationCompat.Builder(context, FINISHED_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()

    private fun builder(): NotificationCompat.Builder = NotificationCompat.Builder(
        context,
        CHANNEL_ID,
    )
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        // Android 12+ may hold a new service's notification back for 10 s; downloads show it now.
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setContentIntent(openAppIntent())

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun pauseAllIntent(): PendingIntent = PendingIntent.getService(
        context,
        1,
        Intent(context, DownloadForegroundService::class.java).apply {
            action = DownloadForegroundService.ACTION_PAUSE_ALL
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CHANNEL_ID = "active_downloads"
        const val NOTIFICATION_ID = 4_001

        /** P34: the finished notice's channel and the id of Android's "paused" notice. */
        const val FINISHED_CHANNEL_ID = "finished_downloads"
        const val FINISHED_CHANNEL_NAME = "Finished downloads"
        const val FINISHED_NOTIFICATION_ID = 4_002
        const val PAUSED_NOTIFICATION_ID = 4_003
        private const val FINISHED_CHANNEL_DESCRIPTION =
            "A notice when a download has finished or failed"
        private const val PROGRESS_MAX = 100

        /** "Android paused downloads after 6 hours. Open YFT to resume." (onTimeout) */
        const val TIMEOUT_NOTICE = "Android paused downloads after 6 hours. Open YFT to resume."

        /** Android refused to start the download service from the background. */
        const val REFUSED_NOTICE =
            "Android paused downloads in the background. Open YFT to resume."

        val FOREGROUND_STATUSES = setOf(
            DownloadTaskStatus.QUEUED,
            DownloadTaskStatus.RUNNING,
            DownloadTaskStatus.PAUSING,
            DownloadTaskStatus.WAITING_FOR_NETWORK,
        )
    }
}
