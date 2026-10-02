package com.alal.yft.download

import android.content.Context
import com.alal.yft.core.download.DirectRangeProbe
import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DirectProbeResult

/** Adapts the concrete range probe to the narrow boundary used by [DownloadEnqueuer]. */
class DirectRangeMetadataProbe(
    private val probe: DirectRangeProbe,
) : DirectMetadataProbe {
    override suspend fun probe(plan: DirectDownloadPlan): DirectProbeResult = probe.probe(plan)
}

/** Starts the foreground transfer service from application context. */
class ForegroundDownloadServiceStarter(
    private val context: Context,
) : DownloadServiceStarter {
    override fun start() {
        DownloadForegroundService.start(context)
    }
}
