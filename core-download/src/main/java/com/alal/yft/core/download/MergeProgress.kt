package com.alal.yft.core.download

import java.io.File
import java.io.FileDescriptor
import java.util.Locale

/** Where a merge writes the merged file (P27). */
sealed interface MuxOutput {
    /** A new file in app storage, copied into the destination afterwards (today's path). */
    data class ToFile(val file: File) : MuxOutput

    /** The destination's own file (Android 8.0+), written once: no second copy. */
    class ToDescriptor(val descriptor: FileDescriptor) : MuxOutput
}

/**
 * Called for every sample a merge writes (P27): [done] of [total], in microseconds of the
 * tracks' duration, or in bytes of the tracks when their duration is unknown. It may throw a
 * cancellation to stop the merge.
 */
fun interface MuxProgressListener {
    fun onProgress(done: Long, total: Long)
}

/**
 * A merge's result and how many samples it wrote before it ended (P27); [details] say how it
 * merged, for the merge's log line (P35).
 */
data class MuxAttempt(
    val result: LocalMuxResult,
    val samplesWritten: Long,
    val details: MuxDetails? = null,
)

/**
 * How a merge went (P35), for its log line: the way it merged ([STREAM_COPY] or [MEDIA_MUXER]),
 * the samples of each track, each phase's time and, when today's way merged after the stream
 * copy could not, why not. No addresses: only counts, times and short fixed words.
 */
data class MuxDetails(
    val path: String,
    val videoSamples: Long,
    val audioSamples: Long,
    /** Each phase's name and milliseconds, in order: "parse" to 120, "data" to 3_400. */
    val phases: List<Pair<String, Long>> = emptyList(),
    /** Why the stream copy was not kept, when today's way merged instead. */
    val streamCopyNote: String? = null,
) {
    /** "stream copy: 1800 video + 2600 audio samples, parse 12 ms, data 1.2 s, check 40 ms". */
    fun summary(): String {
        val way = streamCopyNote?.let { note -> "$path (stream copy: $note)" } ?: path
        val samples = "$videoSamples video + $audioSamples audio samples"
        val times = phases.map { (name, millis) -> "$name ${MergeTimes.duration(millis)}" }
        return (listOf("$way: $samples") + times).joinToString(", ")
    }

    companion object {
        const val STREAM_COPY = "stream copy"
        const val MEDIA_MUXER = "MediaMuxer"
    }
}

/**
 * A muxer that reports its progress and can write into a file descriptor (P27). A muxer that
 * is only a [LocalAudioVideoMuxer] merges into app storage without progress.
 */
interface ProgressAudioVideoMuxer : LocalAudioVideoMuxer {
    fun mux(
        videoFile: File,
        audioFile: File,
        output: MuxOutput,
        outputMimeType: String,
        onProgress: MuxProgressListener,
    ): MuxAttempt
}

/** The steps of a merged download that [MergeTimes] measures (P27). */
enum class MergeStep(val label: String) {
    VIDEO("video"),
    AUDIO("audio"),
    MERGE("merge"),
    COPY("copy"),
    SYNC("sync"),
    COMMIT("commit"),
}

/**
 * How long each step of a merged download took (P27), for the log and for a merge failure's
 * details: "video 1 min 2 s, audio 20 s, merge 35 s, sync 0.2 s, commit 80 ms".
 */
class MergeTimes(val elapsedMillis: () -> Long) {
    private val steps = mutableMapOf<MergeStep, Long>()

    @Synchronized
    fun add(step: MergeStep, millis: Long) {
        steps[step] = (steps[step] ?: 0L) + millis.coerceAtLeast(0L)
    }

    @Synchronized
    fun millis(step: MergeStep): Long? = steps[step]

    inline fun <T> measure(step: MergeStep, block: () -> T): T {
        val start = elapsedMillis()
        try {
            return block()
        } finally {
            add(step, elapsedMillis() - start)
        }
    }

    @Synchronized
    fun summary(): String = MergeStep.entries
        .mapNotNull { step -> steps[step]?.let { "${step.label} ${duration(it)}" } }
        .joinToString(", ")

    companion object {
        /** "80 ms", "4.2 s", "35 s", "2 min 5 s". */
        fun duration(millis: Long): String = when {
            millis < 1_000 -> "$millis ms"
            millis < 10_000 -> String.format(Locale.US, "%.1f s", millis / 1_000.0)
            millis < 60_000 -> "${millis / 1_000} s"
            else -> "${millis / 60_000} min ${(millis % 60_000) / 1_000} s"
        }

        /** "1.3 GB", "783 MB", "12 KB". */
        fun size(bytes: Long): String = when {
            bytes >= GIB -> String.format(Locale.US, "%.1f GB", bytes / GIB.toDouble())
            bytes >= MIB -> "${bytes / MIB} MB"
            bytes >= KIB -> "${bytes / KIB} KB"
            else -> "$bytes B"
        }

        private const val KIB = 1_024L
        private const val MIB = 1_024L * KIB
        private const val GIB = 1_024L * MIB
    }
}
