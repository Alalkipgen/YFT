package com.alal.yft.download

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.alal.yft.core.download.DownloadQueue
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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var connectivityManager: ConnectivityManager
    private var observer: Job? = null
    private var callbackRegistered = false

    override fun onCreate() {
        super.onCreate()
        notifications.createChannel()
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        registerNetworkCallback()
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
                queue.setNetworkAvailable(connectivityManager.hasValidatedNetwork())
                queue.tasks.collectLatest { tasks ->
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
        if (callbackRegistered) {
            runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerNetworkCallback() {
        if (callbackRegistered) return
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        callbackRegistered = true
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateNetworkState()
        }

        override fun onLost(network: Network) {
            updateNetworkState()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) {
            updateNetworkState()
        }
    }

    private fun updateNetworkState() {
        serviceScope.launch {
            queue.setNetworkAvailable(connectivityManager.hasValidatedNetwork())
        }
    }

    private fun ConnectivityManager.hasValidatedNetwork(): Boolean {
        val capabilities = getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

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