package com.alal.yft.core.download

import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DownloadSegment

/** Produces contiguous inclusive byte ranges without empty or overflowing segments. */
object SegmentPlanner {
    fun plan(
        totalBytes: Long?,
        supportsByteRanges: Boolean,
        preferredSegmentCount: Int,
    ): List<DownloadSegment> {
        require(totalBytes == null || totalBytes >= 0)
        require(preferredSegmentCount in 1..DirectDownloadPlan.MAX_SEGMENT_COUNT)

        if (totalBytes == 0L) return emptyList()
        if (totalBytes == null || !supportsByteRanges) {
            return listOf(
                DownloadSegment(
                    index = 0,
                    startByte = 0,
                    endByteInclusive = totalBytes?.minus(1),
                ),
            )
        }

        val segmentCount = minOf(totalBytes, preferredSegmentCount.toLong()).toInt()
        val baseLength = totalBytes / segmentCount
        val remainder = totalBytes % segmentCount
        var nextStart = 0L
        return List(segmentCount) { index ->
            val length = baseLength + if (index.toLong() < remainder) 1 else 0
            val segment = DownloadSegment(
                index = index,
                startByte = nextStart,
                endByteInclusive = nextStart + length - 1,
            )
            nextStart += length
            segment
        }
    }
}