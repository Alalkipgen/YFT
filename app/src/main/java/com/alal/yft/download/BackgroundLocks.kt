package com.alal.yft.download

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager

/** A lock the download service holds while work runs (P34): a wake lock or a Wi-Fi lock. */
internal interface WorkLock {
    val isHeld: Boolean

    /** Takes the lock, or renews it, for at most [timeoutMs] where the lock has a timeout. */
    fun acquire(timeoutMs: Long)

    fun release()
}

/**
 * Holds a partial wake lock while any task runs (download, merge, MP3 conversion or save) and a
 * Wi-Fi lock while bytes are downloaded, and releases each as soon as its work ends (P34, G3).
 * The wake lock is taken with [timeoutMs] and renewed every [renewEveryMs] while work goes on,
 * so a lock the service forgets can never keep the phone awake for long.
 */
internal class BackgroundWorkLocks(
    private val wake: WorkLock,
    private val wifi: WorkLock,
    private val nowMs: () -> Long,
    private val timeoutMs: Long = LOCK_TIMEOUT_MS,
    private val renewEveryMs: Long = RENEW_EVERY_MS,
) {
    private var wakeTakenAt: Long? = null
    private var wifiTakenAt: Long? = null

    val wakeHeld: Boolean get() = wake.isHeld

    val wifiHeld: Boolean get() = wifi.isHeld

    fun update(work: BackgroundWork) {
        wakeTakenAt = hold(wake, work.running, wakeTakenAt)
        wifiTakenAt = hold(wifi, work.downloading, wifiTakenAt)
    }

    fun releaseAll() {
        update(BackgroundWork.Idle)
    }

    private fun hold(lock: WorkLock, wanted: Boolean, takenAt: Long?): Long? {
        if (!wanted) {
            if (lock.isHeld) lock.release()
            return null
        }
        val now = nowMs()
        if (takenAt != null && lock.isHeld && now - takenAt < renewEveryMs) return takenAt
        lock.acquire(timeoutMs)
        return now
    }

    companion object {
        const val WAKE_LOCK_TAG = "yft:downloads"
        const val WIFI_LOCK_TAG = "yft:downloads-wifi"
        const val LOCK_TIMEOUT_MS = 10L * 60 * 1_000
        const val RENEW_EVERY_MS = 60L * 1_000

        fun android(context: Context, nowMs: () -> Long): BackgroundWorkLocks =
            BackgroundWorkLocks(
                wake = AndroidWakeLock(context.applicationContext),
                wifi = AndroidWifiLock(context.applicationContext),
                nowMs = nowMs,
            )
    }
}

/** A partial wake lock that is not reference-counted, so one release always ends it. */
private class AndroidWakeLock(context: Context) : WorkLock {
    private val lock: PowerManager.WakeLock? = context.getSystemService(PowerManager::class.java)
        ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, BackgroundWorkLocks.WAKE_LOCK_TAG)
        ?.apply { setReferenceCounted(false) }

    override val isHeld: Boolean get() = lock?.isHeld == true

    override fun acquire(timeoutMs: Long) {
        lock?.acquire(timeoutMs)
    }

    override fun release() {
        if (lock?.isHeld == true) lock.release()
    }
}

/**
 * A high-performance Wi-Fi lock: the radio stays awake for the transfer while the screen is off.
 * It has no timeout of its own; the wake lock's timeout and the service's release end it.
 */
private class AndroidWifiLock(context: Context) : WorkLock {
    @Suppress("DEPRECATION")
    @SuppressLint("WifiManagerLeak")
    private val lock: WifiManager.WifiLock? = context.getSystemService(WifiManager::class.java)
        ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, BackgroundWorkLocks.WIFI_LOCK_TAG)
        ?.apply { setReferenceCounted(false) }

    override val isHeld: Boolean get() = lock?.isHeld == true

    override fun acquire(timeoutMs: Long) {
        if (lock?.isHeld == false) lock.acquire()
    }

    override fun release() {
        if (lock?.isHeld == true) lock.release()
    }
}
