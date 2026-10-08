package com.alal.yft.feature.downloads

import com.alal.yft.core.download.StoredDownloadTask
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one speed estimate of every running download (P34): the Downloads cards and the
 * notification both read this [TransferRateTracker], so they show the same number. Callers
 * pass the queue's latest snapshot; samples closer together than the tracker's interval fold.
 */
@Singleton
class DownloadSpeedMeter internal constructor(
    private val tracker: TransferRateTracker,
) {
    @Inject
    constructor() : this(TransferRateTracker())

    /** Bytes per second by task id, for running tasks with an estimate. */
    @Synchronized
    fun update(tasks: List<StoredDownloadTask>): Map<String, Long> = tracker.update(tasks)
}
