package com.alal.yft.core.download

import com.alal.yft.core.model.download.DownloadFailureDetails
import com.alal.yft.core.model.download.DownloadFailureReason
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.Locale
import kotlinx.coroutines.CancellationException

/** v1.0.2 fix R6: a merge's output read back from its start; closing leaves a descriptor open. */
internal interface ReadableMergeOutput : Mp4Source, Closeable

/**
 * v1.0.2 fix R6: one track's length, as a merge input holds it or as a merged MP4's tables state
 * it — its [samples], their summed [durationUs] and the longest one; for a video input also the
 * non-sync samples before its first sync sample, which Android's MP4 writer leaves out.
 */
internal class MergeTrackLength(
    val samples: Int,
    val durationUs: Long,
    val longestSampleUs: Long,
    val leadingNonSync: Int = 0,
    val leadingNonSyncUs: Long = 0L,
)

/**
 * v1.0.2 fix R6: whether a merged MP4 holds all of its inputs. Android's MediaMuxer way takes
 * each sample's time from Android's MediaExtractor, whose fragment reader counts a track's time
 * in 32 bits of its ticks and reads no `tfdt`: a DASH video in 10 MHz ticks wraps after 7:09.5,
 * and a merge kept 28 MB of a 10:54 film as if it were whole. The merged file has to hold each
 * input's track with every sample but leading non-sync video frames, and its length to within a
 * second, or two of its longest samples.
 */
internal object MergedLength {
    /**
     * [source]'s one [kind] track (MP4 of any codec, or an ADTS sound) as Android's reader gives
     * it: `tfdt` jumps left out. Null when it cannot be read here; the check is then skipped.
     */
    fun ofInput(source: Mp4Source, kind: StreamCopyTrackKind): MergeTrackLength? {
        val track = try {
            if (kind == StreamCopyTrackKind.AUDIO && AdtsTrackReader.isAdts(source)) {
                AdtsTrackReader(source).read()
            } else {
                Mp4TrackReader(source, kind, anyCodec = true).read()
            }
        } catch (error: Exception) {
            return null
        }
        var longest = 0L
        for (index in 0 until track.count) longest = maxOf(longest, track.durations[index].toLong())
        var leading = 0
        var leadingTicks = 0L
        if (kind == StreamCopyTrackKind.VIDEO) {
            while (leading < track.count && !track.isSync(leading)) {
                leadingTicks += track.durations[leading]
                leading += 1
            }
            if (leading == track.count) return null
        }
        return MergeTrackLength(
            samples = track.count,
            durationUs = ticksToMicros(track.mediaDuration - track.gapTicks, track.timescale),
            longestSampleUs = ticksToMicros(longest, track.timescale),
            leadingNonSync = leading,
            leadingNonSyncUs = ticksToMicros(leadingTicks, track.timescale),
        )
    }

    /**
     * The tracks of the merged MP4 in [source] by their `hdlr` type ("vide", "soun"), from its
     * `moov` tables; none when it has no `moov`. Null when it cannot be read, or is fragmented.
     */
    fun ofMerged(source: Mp4Source): Map<String, MergeTrackLength>? = try {
        moovOf(source)?.let(::tracksOf) ?: emptyMap()
    } catch (error: Exception) {
        null
    }

    /** Null when [merged] holds [input]; else what it holds ("merged video ends at 7:09 …"). */
    fun shortfall(name: String, input: MergeTrackLength, merged: MergeTrackLength?): String? {
        val expected = input.samples - input.leadingNonSync
        if (merged == null || merged.samples == 0) return "merged file has no $name"
        val slackUs = maxOf(SLACK_US, 2 * input.longestSampleUs)
        val short = merged.durationUs + slackUs + input.leadingNonSyncUs < input.durationUs
        if (!short && merged.samples >= expected) return null
        return "merged $name ends at ${clock(merged.durationUs)} of ${clock(input.durationUs)}, " +
            "${merged.samples} of ${input.samples} samples"
    }

    /** "7:09", or "1h02m03s" (two colons would read as an address in a failure's detail). */
    fun clock(micros: Long): String {
        val seconds = maxOf(0L, micros) / MICROS
        val hours = seconds / 3_600
        val minutes = seconds / 60 % 60
        val rest = seconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%dh%02dm%02ds", hours, minutes, rest)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, rest)
        }
    }

    private fun moovOf(source: Mp4Source): Mp4Box? {
        val header = ByteBuffer.allocate(LARGE_BOX_HEADER)
        var moov: Mp4Box? = null
        var at = 0L
        while (at < source.size) {
            val remaining = source.size - at
            if (remaining < BOX_HEADER) unsupported("broken box at the end")
            header.clear()
            header.limit(minOf(remaining, LARGE_BOX_HEADER.toLong()).toInt())
            source.readFully(header, at)
            val size32 = header.getInt(0).toLong() and UINT32
            val type = fourcc(header, 4)
            var headerSize = BOX_HEADER
            val size = when (size32) {
                0L -> remaining
                1L -> {
                    if (header.limit() < LARGE_BOX_HEADER) unsupported("broken box at the end")
                    headerSize = LARGE_BOX_HEADER
                    header.getLong(BOX_HEADER)
                }
                else -> size32
            }
            if (size < headerSize || size > remaining) unsupported("broken size of $type")
            if (type == "moov") {
                if (moov != null) unsupported("two moov boxes")
                val length = size - headerSize
                if (length > Mp4TrackReader.MAX_MOOV_BYTES) unsupported("moov too large")
                val bytes = ByteBuffer.allocate(length.toInt())
                source.readFully(bytes, at + headerSize)
                moov = Mp4Box("moov", bytes, 0, length.toInt())
            }
            at += size
        }
        return moov
    }

    private fun tracksOf(moov: Mp4Box): Map<String, MergeTrackLength>? {
        val children = moov.children()
        if (children.any { it.type == "mvex" }) return null
        val tracks = LinkedHashMap<String, MergeTrackLength>()
        for (trak in children.filter { it.type == "trak" }) {
            val mdia = trak.children().first("mdia") ?: continue
            val media = mdia.children()
            val handler = media.first("hdlr")?.fourcc(8) ?: continue
            val mdhd = media.first("mdhd") ?: continue
            val timescale = mdhd.u32(if (mdhd.version == 1) 20 else 12)
            if (timescale <= 0) continue
            val stbl = media.first("minf")?.children()?.first("stbl") ?: continue
            val tables = stbl.children()
            val samples = (tables.first("stsz") ?: tables.first("stz2"))?.u32(8) ?: 0L
            var ticks = 0L
            var longest = 0L
            tables.first("stts")?.let { stts ->
                val entries = stts.u32(4)
                if (8 + entries * 8 > stts.payloadSize) unsupported("short stts box")
                for (entry in 0 until entries.toInt()) {
                    val count = stts.u32(8 + 8 * entry)
                    val delta = stts.u32(12 + 8 * entry)
                    ticks += count * delta
                    if (count > 0) longest = maxOf(longest, delta)
                }
            }
            if (samples > Int.MAX_VALUE) unsupported("too many samples")
            tracks.putIfAbsent(
                handler,
                MergeTrackLength(
                    samples = samples.toInt(),
                    durationUs = ticksToMicros(ticks, timescale),
                    longestSampleUs = ticksToMicros(longest, timescale),
                ),
            )
        }
        return tracks
    }

    private const val MICROS = 1_000_000L
    private const val SLACK_US = MICROS
}

/**
 * v1.0.2 fix R6: Android's MediaMuxer way with its MP4 output read back for its length
 * ([MergedLength]). An output that holds less than its inputs is emptied and fails as
 * incompatible tracks, with what it held in the detail ("merged video ends at 7:09 of 10:54,
 * 10298 of 15691 samples"); one that cannot be read keeps today's way. WebM goes through as it
 * is.
 */
internal class LengthCheckedAudioVideoMuxer(
    private val muxer: ProgressAudioVideoMuxer,
    private val outputs: StreamCopyOutputs = ChannelStreamCopyOutputs,
) : ProgressAudioVideoMuxer {
    override fun mux(
        videoFile: File,
        audioFile: File,
        outputFile: File,
        outputMimeType: String,
    ): LocalMuxResult = mux(
        videoFile = videoFile,
        audioFile = audioFile,
        output = MuxOutput.ToFile(outputFile),
        outputMimeType = outputMimeType,
        onProgress = { _, _ -> },
    ).result

    override fun mux(
        videoFile: File,
        audioFile: File,
        output: MuxOutput,
        outputMimeType: String,
        onProgress: MuxProgressListener,
    ): MuxAttempt {
        val attempt = muxer.mux(videoFile, audioFile, output, outputMimeType, onProgress)
        if (attempt.result !is LocalMuxResult.Completed) return attempt
        val outputType = outputMimeType.substringBefore(';').trim().lowercase(Locale.US)
        if (outputType != AudioVideoMuxCompatibility.MP4_OUTPUT_MIME) return attempt
        val shortfall = try {
            shortfall(videoFile, audioFile, output)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            null
        } catch (error: OutOfMemoryError) {
            null
        } ?: return attempt
        runCatching { outputs.discard(output) }
        return attempt.copy(
            result = LocalMuxResult.Failure(
                DownloadFailureReason.INCOMPATIBLE_TRACKS,
                DownloadFailureDetails.sanitize(shortfall),
            ),
            samplesWritten = maxOf(1L, attempt.samplesWritten),
        )
    }

    private fun shortfall(videoFile: File, audioFile: File, output: MuxOutput): String? {
        val video = inputLength(videoFile, StreamCopyTrackKind.VIDEO)
        val audio = inputLength(audioFile, StreamCopyTrackKind.AUDIO)
        if (video == null && audio == null) return null
        val merged = outputs.read(output).use(MergedLength::ofMerged) ?: return null
        video?.let { input ->
            MergedLength.shortfall("video", input, merged[VIDEO_HANDLER])?.let { return it }
        }
        audio?.let { input ->
            MergedLength.shortfall("audio", input, merged[AUDIO_HANDLER])?.let { return it }
        }
        return null
    }

    private fun inputLength(file: File, kind: StreamCopyTrackKind): MergeTrackLength? =
        RandomAccessFile(file, "r").use { input ->
            MergedLength.ofInput(FileChannelMp4Source(input.channel), kind)
        }

    private companion object {
        const val VIDEO_HANDLER = "vide"
        const val AUDIO_HANDLER = "soun"
    }
}
