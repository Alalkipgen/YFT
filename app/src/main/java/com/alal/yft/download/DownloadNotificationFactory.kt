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
import com.alal.yft.feature.downloads.DownloadStage
import com.alal.yft.feature.downloads.mergeStageLabel
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
    }

    fun preparing(): Notification = builder()
        .setContentTitle(context.getString(R.string.download_notification_preparing))
        .setProgress(0, 0, true)
        .build()

    fun active(tasks: List<StoredDownloadTask>): Notification {
        val active = tasks.filter { it.status in FOREGROUND_STATUSES }
        val knownTotal = active.mapNotNull(StoredDownloadTask::totalBytes)
            .takeIf { it.size == active.size }
            ?.sum()
        val downloaded = active.sumOf(StoredDownloadTask::downloadedBytes)
        // One merged download merging or saving shows that step and its percent (P27).
        val single = active.singleOrNull()
        val stage = single?.let(DownloadStage::of)
        val stageText = if (single != null && stage != null) {
            mergeStageLabel(stage, single.destinationKind)
        } else {
            null
        }
        val progress = if (stage != null) {
            stage.percent
        } else {
            knownTotal
                ?.takeIf { it > 0 }
                ?.let { ((downloaded * 100.0) / it).toInt().coerceIn(0, 100) }
        }
        val summary = context.resources.getQuantityString(
            R.plurals.download_notification_active,
            active.size,
            active.size,
        )
        return builder()
            .setContentTitle(summary)
            .setContentText(
                stageText
                    ?: active.firstOrNull()?.displayName
                    ?: context.getString(R.string.download_notification_preparing),
            )
            .setProgress(100, progress ?: 0, progress == null)
            .setPublicVersion(
                builder()
                    .setContentTitle(summary)
                    .setProgress(100, progress ?: 0, progress == null)
                    .build(),
            )
            .addAction(
                0,
                context.getString(R.string.download_notification_pause_all),
                pauseAllIntent(),
            )
            .build()
    }

    private fun builder(): NotificationCompat.Builder = NotificationCompat.Builder(
        context,
        CHANNEL_ID,
    )
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
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

        val FOREGROUND_STATUSES = setOf(
            DownloadTaskStatus.QUEUED,
            DownloadTaskStatus.RUNNING,
            DownloadTaskStatus.PAUSING,
            DownloadTaskStatus.WAITING_FOR_NETWORK,
        )
    }
}