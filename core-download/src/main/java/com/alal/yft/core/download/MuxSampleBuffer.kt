package com.alal.yft.core.download

import com.alal.yft.core.model.download.DownloadFailureDetails

/**
 * Phase 1.4 item 1 — the sample buffer of today's merge (MediaExtractor → MediaMuxer, P6).
 *
 * A WebM track states no `KEY_MAX_INPUT_SIZE`, so the buffer was 256 KiB, and a 2K/4K VP9
 * keyframe can be larger: `MediaExtractor.readSampleData` then throws a bare
 * `IllegalArgumentException` (owner's phone, 2026-10-16: 1440p VP9 + Opus failed at MERGE). From
 * Android 9 (`getSampleSize`) the buffer grows to the next sample before it is read, at most
 * [MAX_BYTES]; before Android 9 the sizing stays as it was. The MP4 stream copy (P35) has no
 * such buffer and is not touched.
 */
internal object MuxSampleBuffer {
    /** The largest buffer a sample may get (32 MiB); a larger sample fails with its sizes. */
    const val MAX_BYTES: Int = 32 * 1_024 * 1_024

    /** A grown buffer is a whole number of these (256 KiB). */
    const val STEP_BYTES: Int = 256 * 1_024

    /**
     * The buffer for a sample of [sampleBytes] (negative: unknown, as at the end of a track) when
     * the buffer holds [currentBytes]: the same when the sample fits (never smaller than today);
     * else the larger of the sample and twice the buffer, rounded up to [STEP_BYTES], at most
     * [maxBytes]; null when the sample is larger than [maxBytes].
     */
    fun capacityFor(sampleBytes: Long, currentBytes: Int, maxBytes: Int = MAX_BYTES): Int? {
        if (sampleBytes <= currentBytes) return currentBytes
        if (sampleBytes > maxBytes) return null
        val wanted = maxOf(sampleBytes, currentBytes * 2L)
        val rounded = (wanted + STEP_BYTES - 1) / STEP_BYTES * STEP_BYTES
        return rounded.coerceAtMost(maxBytes.toLong()).toInt()
    }
}

/**
 * Where today's merge is (Phase 1.4 item 1), for a failure's Detail and the log: the platform
 * call under way ([call]: `addTrack video`, `readSampleData audio`, `stop`, …), the current
 * sample's size, the buffer's size, the largest sample read per track and how often the buffer
 * grew. Sizes only: no path, name or address.
 */
internal class MuxTrace {
    var call: String = OPEN
        private set

    /** The current sample's size in bytes, or [UNKNOWN] (before Android 9, or not read yet). */
    var sampleBytes: Long = UNKNOWN
        private set

    var bufferBytes: Int = 0
        private set

    var largestVideoBytes: Long = 0
        private set

    var largestAudioBytes: Long = 0
        private set

    var grown: Int = 0
        private set

    fun at(call: String) {
        this.call = call
    }

    /** A sample is next: its size when known ([UNKNOWN] otherwise), the buffer it goes into. */
    fun sample(bytes: Long, bufferBytes: Int) {
        sampleBytes = bytes
        this.bufferBytes = bufferBytes
    }

    fun grew(bufferBytes: Int) {
        this.bufferBytes = bufferBytes
        grown += 1
    }

    /** A sample of [bytes] was read from the video or the sound. */
    fun read(isVideo: Boolean, bytes: Int) {
        sampleBytes = bytes.toLong()
        if (isVideo) {
            largestVideoBytes = maxOf(largestVideoBytes, sampleBytes)
        } else {
            largestAudioBytes = maxOf(largestAudioBytes, sampleBytes)
        }
    }

    /**
     * The failure's Detail: "IllegalArgumentException at readSampleData video: sample 348 KiB,
     * buffer 256 KiB" — [error] (`DownloadFailureDetails.of`) cut first so the call and the sizes
     * stay, within [DETAIL_CHARS] (the room `withTimes` leaves before "took …").
     */
    fun detail(error: String): String {
        val sizes = if (call.startsWith(READ) || call.startsWith(WRITE)) {
            listOfNotNull(
                sampleBytes.takeIf { it > 0 }?.let { "sample ${kib(it)} KiB" },
                bufferBytes.takeIf { it > 0 }?.let { "buffer ${kib(it.toLong())} KiB" },
            ).joinToString(", ").takeIf(String::isNotEmpty)?.let { ": $it" }.orEmpty()
        } else {
            ""
        }
        val tail = " at $call$sizes"
        val room = (DETAIL_CHARS - tail.length).coerceAtLeast(MIN_ERROR_CHARS)
        val head = if (error.length <= room) error else error.take(room - 1).trimEnd() + "\u2026"
        return DownloadFailureDetails.sanitize(head + tail) ?: error
    }

    /** The log line: the Detail's facts and the largest sample per track, in bytes. */
    fun logLine(error: String): String =
        "Merge failed at $call: $error; sample $sampleBytes B, buffer $bufferBytes B, " +
            "largest video $largestVideoBytes B, largest audio $largestAudioBytes B, buffer grew $grown times"

    companion object {
        const val UNKNOWN: Long = -1
        const val OPEN = "open"
        const val ADD_VIDEO = "addTrack video"
        const val ADD_AUDIO = "addTrack audio"
        const val START = "start"
        const val READ = "readSampleData"
        const val READ_VIDEO = "readSampleData video"
        const val READ_AUDIO = "readSampleData audio"
        const val WRITE = "writeSampleData"
        const val WRITE_VIDEO = "writeSampleData video"
        const val WRITE_AUDIO = "writeSampleData audio"
        const val ADVANCE_VIDEO = "advance video"
        const val ADVANCE_AUDIO = "advance audio"
        const val STOP = "stop"

        /** The room `withTimes` leaves for the reason before "took …" (about 84 of 120). */
        const val DETAIL_CHARS = 84

        /** The error's class always shows ("IllegalArgumentException:" and "…" are 26). */
        const val MIN_ERROR_CHARS = 26

        private fun kib(bytes: Long): Long = (bytes + 1_023) / 1_024
    }
}
