package com.alal.yft.download

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.detection.SiteLookupCache
import com.alal.yft.download.policy.DownloadPolicyGate
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DownloadForegroundService : Service() {
    @Inject
    lateinit var queue: DownloadQueue

    @Inject
    lateinit var notifications: DownloadNotificationFactory

    @Inject
    lateinit var policy: DownloadPolicyGate

    /** P17: a download whose links stopped working drops its video's remembered lookup. */
    @Inject
    lateinit var lookups: SiteLookupCache

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null

    override fun onCreate() {
        super.onCreate()
        notifications.createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            DownloadNotificationFactory.NOTIFICATION_ID,
            notifications.preparing(),
        )
        if (intent?.action == ACTION_PAUSE_ALL) {
            serviceScope.launch { queue.pauseAll() }
        }
        if (observer == null) {
            observer = serviceScope.launch {
                queue.restore()
                policy.ensureApplied()
                queue.tasks.collectLatest { tasks ->
                    lookups.forgetBrokenDownloads(tasks)
                    val foreground = tasks.filter {
                        it.status in DownloadNotificationFactory.FOREGROUND_STATUSES
                    }
                    if (foreground.isEmpty()) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        updateNotification(foreground)
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun updateNotification(
        tasks: List<com.alal.yft.core.download.StoredDownloadTask>,
    ) {
        if (!canPostNotifications()) return
        try {
            NotificationManagerCompat.from(this)
                .notify(
                    DownloadNotificationFactory.NOTIFICATION_ID,
                    notifications.active(tasks),
                )
        } catch (_: SecurityException) {
            // The foreground service remains valid when notification permission changes at runtime.
        }
    }

    companion object {
        const val ACTION_PAUSE_ALL = "com.alal.yft.action.PAUSE_ALL_DOWNLOADS"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, DownloadForegroundService::class.java),
            )
        }
    }
}