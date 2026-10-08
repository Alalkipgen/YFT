package com.alal.yft.download

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.detection.SiteLookupCache
import com.alal.yft.download.policy.DownloadNetworkStatus
import com.alal.yft.feature.downloads.DownloadSpeedMeter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps YFT running while downloads, merges, MP3 conversions and saves go on (P34): a
 * foreground service with the ongoing notification, a wake lock and a Wi-Fi lock while work
 * runs, the media-processing type on Android 15 while merging, and a once-a-second tick that
 * renews the locks, updates the notification and notices when the phone froze YFT.
 * [DownloadServiceController] makes the decisions.
 */
@AndroidEntryPoint
class DownloadForegroundService : Service() {
    @Inject
    lateinit var injectedQueue: DownloadQueue

    @Inject
    lateinit var notifications: DownloadNotificationFactory

    @Inject
    lateinit var policy: DownloadNetworkStatus

    /** P17: a download whose links stopped working drops its video's remembered lookup. */
    @Inject
    lateinit var lookups: SiteLookupCache

    @Inject
    lateinit var health: BackgroundHealthStore

    @Inject
    lateinit var speeds: DownloadSpeedMeter

    @Inject
    @DownloadApplicationScope
    lateinit var applicationScope: CoroutineScope

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null
    private var ticker: Job? = null
    private var controller: DownloadServiceController? = null
    private var current: Notification? = null
    private var lastStartId = 0

    private val queue: DownloadQueue get() = testQueue ?: injectedQueue

    override fun onCreate() {
        super.onCreate()
        notifications.createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val running = controller?.takeUnless { it.isStopped }
        val types = running?.declaredTypes
            ?: DownloadServiceController.serviceTypes(Build.VERSION.SDK_INT, processing = false)
        val notification = current ?: notifications.preparing()
        if (!enterForeground(notification, types) && running == null) {
            // Android refused (for example a sticky restart from the background): the queue keeps
            // its tasks and YFT resumes them when it is opened again.
            showNotice(DownloadNotificationFactory.REFUSED_NOTICE)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        current = notification
        lastStartId = startId
        if (intent?.action == ACTION_PAUSE_ALL) {
            serviceScope.launch { queue.pauseAll() }
        }
        if (running == null) {
            // First start, or a start that came in while this instance was stopping.
            observer?.cancel()
            ticker?.cancel()
            val control = newController().also {
                controller = it
                it.onStarted(types)
            }
            observer = serviceScope.launch {
                queue.restore()
                policy.ensureApplied()
                combine(queue.tasks, policy.state, ::Pair).collectLatest { (tasks, network) ->
                    lookups.forgetBrokenDownloads(tasks)
                    control.onTasks(tasks, network)
                }
            }
            ticker = serviceScope.launch {
                while (isActive) {
                    delay(FreezeDetector.TICK_MS)
                    control.onTick()
                }
            }
        }
        return START_STICKY
    }

    /** Android 15: a service type's daily limit (6 h for data sync) ran out. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        // The pause must outlive this service, which stops within seconds.
        applicationScope.launch { queue.pauseAll() }
        controller?.onTimeout() ?: run {
            showNotice(DownloadNotificationFactory.TIMEOUT_NOTICE)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        controller?.onDestroy()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun newController() = DownloadServiceController(
        host = object : DownloadServiceController.Host {
            override fun startForeground(serviceTypes: Int) {
                enterForeground(current ?: notifications.preparing(), serviceTypes)
            }

            override fun showActive(content: ActiveNotificationContent) {
                val notification = notifications.active(content)
                current = notification
                notify(DownloadNotificationFactory.NOTIFICATION_ID, notification)
            }

            override fun showFinished(task: StoredDownloadTask) {
                val notice = notifications.finished(task) ?: return
                notify(DownloadNotificationFactory.FINISHED_NOTIFICATION_ID, notice, tag = task.id)
            }

            override fun showNotice(text: String) = this@DownloadForegroundService.showNotice(text)

            override fun stop() {
                ticker?.cancel()
                current = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                // A start that arrived meanwhile keeps the service: it has a newer id.
                stopSelf(lastStartId)
            }
        },
        locks = BackgroundWorkLocks.android(this, SystemClock::elapsedRealtime),
        health = health,
        speeds = speeds,
        nowMs = SystemClock::elapsedRealtime,
        log = { line -> Log.w(TAG, line) },
    )

    /** Starts or updates the foreground state; false when Android refuses it. */
    private fun enterForeground(notification: Notification, serviceTypes: Int): Boolean =
        try {
            ServiceCompat.startForeground(
                this,
                DownloadNotificationFactory.NOTIFICATION_ID,
                notification,
                serviceTypes,
            )
            true
        } catch (error: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (Android 12+) is an IllegalStateException.
            Log.w(TAG, "Foreground start refused: ${error.javaClass.simpleName}")
            false
        } catch (error: SecurityException) {
            Log.w(TAG, "Foreground type refused: ${error.javaClass.simpleName}")
            false
        }

    private fun showNotice(text: String) {
        val notice = notifications.pausedByAndroid(text)
        notify(DownloadNotificationFactory.PAUSED_NOTIFICATION_ID, notice)
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun notify(id: Int, notification: Notification, tag: String? = null) {
        if (!canPostNotifications()) return
        try {
            NotificationManagerCompat.from(this).notify(tag, id, notification)
        } catch (_: SecurityException) {
            // The foreground service remains valid when notification permission changes at runtime.
        }
    }

    companion object {
        const val ACTION_PAUSE_ALL = "com.alal.yft.action.PAUSE_ALL_DOWNLOADS"
        private const val TAG = "YftDownloads"

        /** Instrumented tests run the service on their own queue (P34); always null in the app. */
        @VisibleForTesting
        @Volatile
        internal var testQueue: DownloadQueue? = null

        /**
         * Starts the service; when Android refuses a start from the background, the downloads
         * stay queued and a notice says to open YFT (P34).
         */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, DownloadForegroundService::class.java),
                )
            } catch (error: IllegalStateException) {
                Log.w(TAG, "Service start refused: ${error.javaClass.simpleName}")
                postRefusedNotice(context.applicationContext)
            }
        }

        @SuppressLint("MissingPermission")
        private fun postRefusedNotice(context: Context) {
            val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            if (!permitted) return
            val notifications = DownloadNotificationFactory(context)
            notifications.createChannel()
            try {
                NotificationManagerCompat.from(context).notify(
                    DownloadNotificationFactory.PAUSED_NOTIFICATION_ID,
                    notifications.pausedByAndroid(DownloadNotificationFactory.REFUSED_NOTICE),
                )
            } catch (_: SecurityException) {
                // Without the permission the queue still keeps its tasks.
            }
        }
    }
}
