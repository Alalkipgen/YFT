package com.alal.yft.core.download

import java.util.Locale

/** How a download learned its length (P41), for [DownloadStartTimeline]. */
enum class StartLengthSource(val label: String) {
    /** The plan stated it, such as YouTube's clen. */
    KNOWN("known"),

    /** A one-byte request asked for it before the first range. */
    PROBE("probe"),

    /** The first range's Content-Range gave it. */
    FIRST_RANGE("from first range"),

    /** A segment list without a total length. */
    NONE("none"),
}

/**
 * When a download's first steps happened (P41), in milliseconds after it started: its plan
 * (the manifest or the file layout), its length, its first byte and its first progress update.
 * It holds times only, never an address; [summary] is the log line and the failure Details.
 */
data class DownloadStartTimeline(
    val planMillis: Long? = null,
    val lengthMillis: Long? = null,
    val lengthSource: String? = null,
    val firstByteMillis: Long? = null,
    val firstProgressMillis: Long? = null,
) {
    /** "start: plan 0.0 s · length 0.4 s (probe) · first byte 0.9 s · first progress 1.0 s" */
    fun summary(): String = listOf(
        "plan ${seconds(planMillis)}",
        "length ${seconds(lengthMillis)}" + (lengthSource?.let { " ($it)" } ?: ""),
        "first byte ${seconds(firstByteMillis)}",
        "first progress ${seconds(firstProgressMillis)}",
    ).joinToString(separator = " \u00b7 ", prefix = "start: ")

    companion object {
        /**
         * One download's times from its tracks' (a merged video): the plan and the length once
         * every track has them, the first byte and the first progress of the quickest track.
         */
        fun combine(tracks: Map<String, DownloadStartTimeline>): DownloadStartTimeline {
            val timelines = tracks.values
            val sources = tracks.mapNotNull { (name, timeline) ->
                timeline.lengthSource?.let { name to it }
            }
            val source = when {
                sources.isEmpty() -> null
                sources.map { it.second }.distinct().size == 1 -> sources.first().second
                else -> sources.joinToString(", ") { (name, label) -> "$name $label" }
            }
            return DownloadStartTimeline(
                planMillis = timelines.maxOfAllOrNull(DownloadStartTimeline::planMillis),
                lengthMillis = timelines.maxOfAllOrNull(DownloadStartTimeline::lengthMillis),
                lengthSource = source,
                firstByteMillis = timelines.mapNotNull { it.firstByteMillis }.minOrNull(),
                firstProgressMillis = timelines.mapNotNull { it.firstProgressMillis }.minOrNull(),
            )
        }

        private fun Collection<DownloadStartTimeline>.maxOfAllOrNull(
            time: (DownloadStartTimeline) -> Long?,
        ): Long? {
            val times = map(time)
            if (times.isEmpty() || times.any { it == null }) return null
            return times.filterNotNull().max()
        }

        private fun seconds(millis: Long?): String {
            if (millis == null) return "\u2013"
            return String.format(Locale.US, "%.1f s", millis.coerceAtLeast(0) / 1_000.0)
        }
    }
}

/** Notes [DownloadStartTimeline]'s steps as they happen, with a monotonic clock. */
internal class StartTimelineRecorder(private val elapsedMillis: () -> Long) {
    private val startedAt = elapsedMillis()

    @Volatile
    var timeline = DownloadStartTimeline()
        private set

    @Synchronized
    fun planned() {
        if (timeline.planMillis == null) timeline = timeline.copy(planMillis = sinceStart())
    }

    @Synchronized
    fun lengthKnown(source: StartLengthSource) {
        if (timeline.lengthMillis != null) return
        timeline = timeline.copy(lengthMillis = sinceStart(), lengthSource = source.label)
    }

    @Synchronized
    fun firstByte() {
        if (timeline.firstByteMillis == null) {
            timeline = timeline.copy(firstByteMillis = sinceStart())
        }
    }

    /** Notes the first progress; true only the first time. */
    @Synchronized
    fun firstProgress(): Boolean {
        if (timeline.firstProgressMillis != null) return false
        timeline = timeline.copy(firstProgressMillis = sinceStart())
        return true
    }

    private fun sinceStart(): Long = (elapsedMillis() - startedAt).coerceAtLeast(0)
}
