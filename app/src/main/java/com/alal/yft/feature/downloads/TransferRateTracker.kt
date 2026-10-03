package com.alal.yft.feature.downloads

import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.model.download.DownloadTaskStatus
import kotlin.math.roundToLong

/**
 * Estimates the speed of each running task from successive queue snapshots.
 *
 * Nothing is persisted: a speed exists only while this process watches a task run. Samples
 * closer together than [minIntervalMs] are folded into the next one, and each new measurement
 * moves the estimate by [smoothing] of the difference, so the label does not flicker. A task
 * that stops running, or whose byte count goes down (a restart), starts over.
 */
internal class TransferRateTracker(
    private val nowMs: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
    private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
    private val smoothing: Double = DEFAULT_SMOOTHING,
) {
    private class Sample(val bytes: Long, val atMs: Long, val bytesPerSecond: Double?)

    private val samples = mutableMapOf<String, Sample>()

    /** Bytes per second by task id, for running tasks with an estimate. */
    fun update(tasks: List<StoredDownloadTask>): Map<String, Long> {
        val now = nowMs()
        val running = tasks.filter { it.status == DownloadTaskStatus.RUNNING }
        samples.keys.retainAll(running.mapTo(mutableSetOf()) { it.id })
        val rates = mutableMapOf<String, Long>()
        for (task in running) {
            val previous = samples[task.id]
            val next = when {
                previous == null || task.downloadedBytes < previous.bytes ->
                    Sample(task.downloadedBytes, now, bytesPerSecond = null)

                now - previous.atMs < minIntervalMs -> previous

                else -> {
                    val elapsedMs = now - previous.atMs
                    val instant = (task.downloadedBytes - previous.bytes) * MILLIS_PER_SECOND /
                        elapsedMs
                    val smoothed = previous.bytesPerSecond
                        ?.let { it + smoothing * (instant - it) }
                        ?: instant
                    Sample(task.downloadedBytes, now, smoothed)
                }
            }
            samples[task.id] = next
            next.bytesPerSecond
                ?.roundToLong()
                ?.takeIf { it > 0L }
                ?.let { rates[task.id] = it }
        }
        return rates
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val MILLIS_PER_SECOND = 1_000.0
        const val DEFAULT_MIN_INTERVAL_MS = 500L
        const val DEFAULT_SMOOTHING = 0.3
    }
}
