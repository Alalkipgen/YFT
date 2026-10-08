package com.alal.yft.download

import android.annotation.SuppressLint
import android.content.pm.ServiceInfo
import android.os.Build
import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.download.policy.TransferNetworkState
import com.alal.yft.feature.downloads.DownloadSpeedMeter

/**
 * The download service's decisions (P34), apart from Android so they can be tested on the JVM:
 * - the service stays in the foreground while any task is queued, waits for a network or runs,
 *   at every stage (download, merge, MP3 conversion, save), and stops when none is left;
 * - a wake lock while anything runs, a Wi-Fi lock while bytes are downloaded ([locks]);
 * - the service types: data sync, plus media processing on Android 15 while a merge,
 *   conversion or save runs ([serviceTypes]);
 * - the notification, posted at most once a [minUpdateMs] with the speed of [speeds];
 * - a "Downloaded · …" or "Download failed · …" notice for each task this service saw run;
 * - a freeze: a tick more than 10 s late while the wake lock was held ([freezes]).
 */
internal class DownloadServiceController(
    private val host: Host,
    private val locks: BackgroundWorkLocks,
    private val health: BackgroundHealthStore,
    private val speeds: DownloadSpeedMeter,
    private val nowMs: () -> Long,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val doneNotice: Boolean = DONE_NOTICE,
    private val freezes: FreezeDetector = FreezeDetector(),
    private val minUpdateMs: Long = MIN_UPDATE_MS,
    private val log: (String) -> Unit = {},
) {
    /** What the controller asks of the service. */
    interface Host {
        /** Declares [serviceTypes] again with the current notification. */
        fun startForeground(serviceTypes: Int)

        fun showActive(content: ActiveNotificationContent)

        fun showFinished(task: StoredDownloadTask)

        fun showNotice(text: String)

        /** Removes the ongoing notification and stops the service. */
        fun stop()
    }

    private var tasks: List<StoredDownloadTask> = emptyList()
    private var network = TransferNetworkState.ALLOWED
    private var types: Int? = null
    private var shown: ActiveNotificationContent? = null
    private var shownAtMs: Long? = null
    private val watched = mutableSetOf<String>()
    private var stopped = false

    /** The service types declared last, or null before the first. */
    val declaredTypes: Int? get() = types

    val isStopped: Boolean get() = stopped

    /** The service has just started with [initialTypes]. */
    fun onStarted(initialTypes: Int) {
        types = initialTypes
    }

    /** A new snapshot of the queue. */
    fun onTasks(tasks: List<StoredDownloadTask>, network: TransferNetworkState) {
        if (stopped) return
        this.tasks = tasks
        this.network = network
        noticeEnded(tasks)
        val foreground = tasks.filter { it.status in FOREGROUND }
        if (foreground.isEmpty()) {
            stop()
            return
        }
        val work = BackgroundWork.of(tasks)
        if (work.running) health.markDownloadStarted()
        if (!work.running) freezes.reset()
        locks.update(work)
        declare(work)
        post(foreground)
    }

    /** Once a second while the service runs: the freeze check, lock renewal and notification. */
    fun onTick() {
        if (stopped) return
        val work = BackgroundWork.of(tasks)
        if (work.running) {
            freezes.tick(nowMs(), locks.wakeHeld, work.stage)?.let(::onFreeze)
        } else {
            freezes.reset()
        }
        locks.update(work)
        post(tasks.filter { it.status in FOREGROUND })
    }

    /** Android's time limit for the service type ran out (Android 15): stop and say so. */
    fun onTimeout() {
        host.showNotice(DownloadNotificationFactory.TIMEOUT_NOTICE)
        stop()
    }

    fun onDestroy() {
        stopped = true
        locks.releaseAll()
    }

    private fun onFreeze(freeze: BackgroundFreeze) {
        health.recordFreeze(freeze.lostMs)
        val stage = freeze.stage?.name?.lowercase() ?: "unknown"
        log("Background freeze: ${freeze.lostMs / MS_PER_SECOND} s lost while $stage")
    }

    private fun declare(work: BackgroundWork) {
        val wanted = serviceTypes(sdkInt, work.processing)
        if (wanted == types) return
        types = wanted
        host.startForeground(wanted)
    }

    private fun post(foreground: List<StoredDownloadTask>) {
        if (foreground.isEmpty()) return
        val content = activeNotificationContent(foreground, speeds.update(tasks), network)
        if (content == shown) return
        val now = nowMs()
        val last = shownAtMs
        if (last != null && now - last < minUpdateMs) return
        shown = content
        shownAtMs = now
        host.showActive(content)
    }

    /** A task this service saw run that has finished or failed gets its notice (G5). */
    private fun noticeEnded(tasks: List<StoredDownloadTask>) {
        val byId = tasks.associateBy(StoredDownloadTask::id)
        val ended = watched.filter { id ->
            val status = byId[id]?.status
            status == null || status !in FOREGROUND
        }
        ended.forEach { id ->
            watched.remove(id)
            val task = byId[id] ?: return@forEach
            if (doneNotice && task.status in NOTICE_STATUSES) host.showFinished(task)
        }
        tasks.filter { it.status == DownloadTaskStatus.RUNNING }
            .forEach { watched += it.id }
    }

    private fun stop() {
        if (stopped) return
        stopped = true
        locks.releaseAll()
        freezes.reset()
        host.stop()
    }

    companion object {
        /** G5 default: a notice when a download finishes or fails. */
        const val DONE_NOTICE = true
        const val MIN_UPDATE_MS = 1_000L
        private const val MS_PER_SECOND = 1_000L
        private val FOREGROUND = DownloadNotificationFactory.FOREGROUND_STATUSES

        private val NOTICE_STATUSES = setOf(
            DownloadTaskStatus.COMPLETED,
            DownloadTaskStatus.FAILED,
            DownloadTaskStatus.NEEDS_REFRESH,
        )

        /**
         * Data sync on Android 10+, plus media processing on Android 15+ while a merge, MP3
         * conversion or save runs; 0 below Android 10, where a service has no type.
         */
        @SuppressLint("InlinedApi")
        fun serviceTypes(sdkInt: Int, processing: Boolean): Int = when {
            sdkInt < Build.VERSION_CODES.Q -> 0
            sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM && processing ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            else -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
    }
}
