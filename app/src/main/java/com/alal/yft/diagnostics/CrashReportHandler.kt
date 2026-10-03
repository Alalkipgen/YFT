package com.alal.yft.diagnostics

import android.os.Process
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/** Best-effort local recording must never replace Android's normal crash/termination handler. */
internal class CrashReportHandler(
    private val store: CrashReportStore,
    private val previous: Thread.UncaughtExceptionHandler,
) : Thread.UncaughtExceptionHandler {
    private val saving = AtomicBoolean(false)

    override fun uncaughtException(thread: Thread, error: Throwable) {
        if (saving.compareAndSet(false, true)) {
            try {
                store.write(thread, error)
            } catch (_: Throwable) {
                // Disk full, OOM or a broken Throwable must not prevent normal termination.
                // Never log the original error or report contents here.
            } finally {
                saving.set(false)
            }
        }
        previous.uncaughtException(thread, error)
    }

    companion object {
        fun install(store: CrashReportStore) {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            if (previous is CrashReportHandler) return
            val fallback = Thread.UncaughtExceptionHandler { _, _ ->
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
            val handler = CrashReportHandler(store, previous ?: fallback)
            Thread.setDefaultUncaughtExceptionHandler(handler)
        }
    }
}
