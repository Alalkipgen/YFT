package com.alal.yft.core.download

import android.annotation.TargetApi
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.util.Log
import com.alal.yft.core.model.download.AudioVideoMuxCheckpoint
import com.alal.yft.core.model.download.AudioVideoMuxDownloadPlan
import com.alal.yft.core.model.download.AudioVideoMuxResult
import com.alal.yft.core.model.download.AudioVideoMuxStage
import com.alal.yft.core.model.download.DashTransferCheckpoint
import com.alal.yft.core.model.download.DashTransferResult
import com.alal.yft.core.model.download.DownloadFailure
import com.alal.yft.core.model.download.DownloadFailureDetails
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadFailureStage
import com.alal.yft.core.model.download.DownloadFailureStage.CONNECT
import com.alal.yft.core.model.download.DownloadFailureStage.MERGE
import com.alal.yft.core.model.download.DownloadFailureStage.OPEN_FILE
import com.alal.yft.core.model.download.DownloadFailureStage.PUBLISH
import com.alal.yft.core.model.download.DownloadFailureStage.VERIFY
import com.alal.yft.core.model.download.DownloadFailureStage.WRITE_FILE
import com.alal.yft.core.model.download.DownloadProgress
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class MuxIncompatibilityReason {
    OUTPUT_CONTAINER,

    /** The phone's Android release has no muxer for this output (P6: WebM with Opus). */
    ANDROID_VERSION,
    VIDEO_CONTAINER,
    AUDIO_CONTAINER,
    VIDEO_CODEC,
    AUDIO_CODEC,
}

sealed interface MuxCompatibility {
    data object Compatible : MuxCompatibility

    data class Incompatible(val reason: MuxIncompatibilityReason) : MuxCompatibility
}

/**
 * Deliberately conservative compatibility gate for Android's platform muxer.
 *
 * YFT does not bundle FFmpeg. Phase 4 therefore accepts only separate AVC/AAC ISO-BMFF tracks
 * that MediaExtractor can read and MediaMuxer can write on the minimum supported Android API.
 * AV1 video would be accepted from [AV1_MP4_MIN_SDK] (Phase 11 P4: Facebook's desktop ladder is
 * AV1), but [AV1_MP4_ENABLED] keeps it off until a phone proves the merge works. P6 adds WebM:
 * VP9 video with Opus (or Vorbis) sound from WebM files, written as one WebM from
 * [WEBM_OPUS_MIN_SDK], for YouTube's 2K and 4K.
 */
object AudioVideoMuxCompatibility {
    /**
     * Android 10: the oldest release YFT merges VP9 and Opus into WebM on. MediaMuxer writes VP9
     * WebM from Android 7, but Opus sound in its muxers is only documented with Android 10's Ogg
     * output, so older phones are not offered 2K/4K rather than handed a merge that may fail. The
     * merge itself is proven on the CI emulator (API 34, `AudioVideoMuxerInstrumentedTest`).
     */
    const val WEBM_OPUS_MIN_SDK = 29

    /** The merged output types: MPEG-4 for AVC/AAC (and AV1), WebM for VP9/Opus. */
    const val MP4_OUTPUT_MIME = "video/mp4"
    const val WEBM_OUTPUT_MIME = "video/webm"

    /** Android 14: the first release whose MediaMuxer may write AV1 into MPEG-4. */
    const val AV1_MP4_MIN_SDK = 34

    /**
     * AV1 merges are off (P4, 2026-10-05): on the CI emulator (API 34) Android's muxer did not
     * merge the AV1 test track with its AAC sound (`AudioVideoMuxerInstrumentedTest`, emulator
     * smoke #27 and #28: `LocalMuxResult.Failure`). Turn on only after a phone proves it works.
     */
    const val AV1_MP4_ENABLED = false

    /**
     * P35 (G2, `FAST_MERGE=ON`): MP4 merges copy the tracks' samples in large blocks
     * ([StreamCopyAudioVideoMuxer]) and fall back to MediaMuxer when that does not count.
     */
    const val FAST_MERGE_ENABLED = true

    fun evaluate(
        plan: AudioVideoMuxDownloadPlan,
        sdkInt: Int = Build.VERSION.SDK_INT,
        av1Enabled: Boolean = AV1_MP4_ENABLED,
    ): MuxCompatibility = when (plan.outputMimeType.normalizedMime()) {
        MP4_OUTPUT_MIME -> evaluateMp4(plan, sdkInt, av1Enabled)
        WEBM_OUTPUT_MIME -> evaluateWebm(
            videoMimeType = plan.video.mimeType,
            videoCodecs = plan.video.codecs,
            audioMimeType = plan.audio.mimeType,
            audioCodecs = plan.audio.codecs,
            sdkInt = sdkInt,
        )
        else -> MuxCompatibility.Incompatible(MuxIncompatibilityReason.OUTPUT_CONTAINER)
    }

    /**
     * Whether a VP9 WebM video file and its WebM sound merge into one WebM on Android [sdkInt]:
     * VP9 video (`vp9`, `vp09.…`) with Opus or Vorbis sound, from [WEBM_OPUS_MIN_SDK].
     */
    fun evaluateWebm(
        videoMimeType: String?,
        videoCodecs: List<String>,
        audioMimeType: String?,
        audioCodecs: List<String>,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): MuxCompatibility {
        if (videoMimeType.normalizedMime() != WEBM_VIDEO_MIME) {
            return MuxCompatibility.Incompatible(MuxIncompatibilityReason.VIDEO_CONTAINER)
        }
        if (audioMimeType.normalizedMime() != WEBM_AUDIO_MIME) {
            return MuxCompatibility.Incompatible(MuxIncompatibilityReason.AUDIO_CONTAINER)
        }
        if (videoCodecs.isEmpty() || videoCodecs.any { !isVp9(it) }) {
            return MuxCompatibility.Incompatible(MuxIncompatibilityReason.VIDEO_CODEC)
        }
        if (
            audioCodecs.isEmpty() ||
            audioCodecs.any { it.trim().lowercase(Locale.US) !in WEBM_AUDIO_CODECS }
        ) {
            return MuxCompatibility.Incompatible(MuxIncompatibilityReason.AUDIO_CODEC)
        }
        if (sdkInt < WEBM_OPUS_MIN_SDK) {
            return MuxCompatibility.Incompatible(MuxIncompatibilityReason.ANDROID_VERSION)
        }
        return MuxCompatibility.Compatible
    }

    /** Whether [codec] names VP9 video: `vp9` or the detailed `vp09.PP.LL.DD` form. */
    fun isVp9(codec: String): Boolean {
        val normalized = codec.trim().lowercase(Locale.US)
        return normalized == VP9_CODEC || normalized.startsWith(VP9_CODEC_PREFIX)
    }

    /** The merged file type a video file of [videoMimeType] is written as. */
    fun outputMimeTypeFor(videoMimeType: String?): String =
        if (videoMimeType.normalizedMime() == WEBM_VIDEO_MIME) WEBM_OUTPUT_MIME else MP4_OUTPUT_MIME

    private fun evaluateMp4(
        plan: AudioVideoMuxDownloadPlan,
        sdkInt: Int,
        av1Enabled: Boolean,
    ): MuxCompatibility {
        if (plan.video.mimeType.normalizedMime() !in VIDEO_MP4_MIMES) {
            return MuxCompatibility.Incompatible(MuxIncompatibilityReason.VIDEO_CONTAINER)
        }
        if (plan.audio.mimeType.normalizedMime() !in AUDIO_MP4_MIMES) {
            return MuxCompatibility.Incompatible(MuxIncompatibilityReason.AUDIO_CONTAINER)
        }
        if (
            plan.video.codecs.isEmpty() ||
            plan.video.codecs.any { codec -> !canWriteVideo(codec, sdkInt, av1Enabled) }
        ) {
            return MuxCompatibility.Incompatible(MuxIncompatibilityReason.VIDEO_CODEC)
        }
        if (
            plan.audio.codecs.isEmpty() ||
            plan.audio.codecs.any { codec ->
                AUDIO_CODEC_PREFIXES.none(codec.lowercase(Locale.US)::startsWith)
            }
        ) {
            return MuxCompatibility.Incompatible(MuxIncompatibilityReason.AUDIO_CODEC)
        }
        return MuxCompatibility.Compatible
    }

    /**
     * Whether the MP4 muxer of Android [sdkInt] writes video in [codec], such as `avc1.64001f`;
     * AV1 only while [av1Enabled] ([AV1_MP4_ENABLED]) and from [AV1_MP4_MIN_SDK].
     */
    fun canWriteVideo(
        codec: String,
        sdkInt: Int = Build.VERSION.SDK_INT,
        av1Enabled: Boolean = AV1_MP4_ENABLED,
    ): Boolean {
        val normalized = codec.trim().lowercase(Locale.US)
        return VIDEO_CODEC_PREFIXES.any(normalized::startsWith) ||
            (av1Enabled && normalized.startsWith(AV1_CODEC_PREFIX) && sdkInt >= AV1_MP4_MIN_SDK)
    }

    private fun String?.normalizedMime(): String? =
        this?.substringBefore(';')?.trim()?.lowercase(Locale.US)

    private const val AV1_CODEC_PREFIX = "av01"
    private const val VP9_CODEC = "vp9"
    private const val VP9_CODEC_PREFIX = "vp09."
    private const val WEBM_VIDEO_MIME = "video/webm"
    private const val WEBM_AUDIO_MIME = "audio/webm"
    private val WEBM_AUDIO_CODECS = setOf("opus", "vorbis")
    private val VIDEO_MP4_MIMES = setOf("video/mp4", "video/iso.segment")
    private val AUDIO_MP4_MIMES = setOf("audio/mp4", "audio/iso.segment")
    private val VIDEO_CODEC_PREFIXES = setOf("avc1", "avc3")
    private val AUDIO_CODEC_PREFIXES = setOf("mp4a.40.")
}

sealed interface LocalMuxResult {
    data class Completed(val bytesWritten: Long) : LocalMuxResult

    /** [detail]: the exception behind the failure, from [DownloadFailureDetails.of] (P21). */
    data class Failure(
        val reason: DownloadFailureReason,
        val detail: String? = null,
    ) : LocalMuxResult
}

interface LocalAudioVideoMuxer {
    /** Merges the two files into [outputFile], an MP4 or, for [outputMimeType] WebM, a WebM. */
    fun mux(
        videoFile: File,
        audioFile: File,
        outputFile: File,
        outputMimeType: String = AudioVideoMuxCompatibility.MP4_OUTPUT_MIME,
    ): LocalMuxResult
}

/**
 * Platform-only remuxer. Inputs must already be complete, unencrypted tracks: ISO-BMFF for an
 * MP4 output, WebM for a WebM output (P6).
 *
 * P35: an MP4 merge is a stream copy first ([fastMerge], [StreamCopyAudioVideoMuxer]) — the
 * tracks' samples copied in large blocks, then read back with MediaExtractor — and today's
 * MediaExtractor → MediaMuxer way only when the stream copy does not count. WebM goes the
 * MediaMuxer way.
 *
 * MediaExtractor/MediaMuxer behavior is device-dependent and remains an explicit device test.
 */
class AndroidMp4AudioVideoMuxer(
    private val maxSampleBufferBytes: Int = DEFAULT_MAX_SAMPLE_BUFFER_BYTES,
    /** P35: stream-copy MP4 merges first ([AudioVideoMuxCompatibility.FAST_MERGE_ENABLED]). */
    private val fastMerge: Boolean = AudioVideoMuxCompatibility.FAST_MERGE_ENABLED,
) : ProgressAudioVideoMuxer {
    init {
        require(maxSampleBufferBytes in MIN_SAMPLE_BUFFER_BYTES..MAX_SAMPLE_BUFFER_BYTES)
    }

    private val mediaMuxer = MediaMuxerAudioVideoMuxer(maxSampleBufferBytes)
    private val streamCopy = StreamCopyAudioVideoMuxer(
        fallback = mediaMuxer,
        check = MediaExtractorStreamCopyCheck(),
        outputs = AndroidStreamCopyOutputs,
    )

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

    /**
     * Merges into [output]: a new file, or (Android 8.0+) a seekable read-write descriptor that
     * this call neither empties nor closes. [onProgress] hears the merge move on (P27): the
     * stream copy after each chunk, today's way at most every 250 ms (P35).
     */
    override fun mux(
        videoFile: File,
        audioFile: File,
        output: MuxOutput,
        outputMimeType: String,
        onProgress: MuxProgressListener,
    ): MuxAttempt {
        val muxer: ProgressAudioVideoMuxer = if (fastMerge) streamCopy else mediaMuxer
        return muxer.mux(videoFile, audioFile, output, outputMimeType, onProgress)
    }

    private companion object {
        const val MIN_SAMPLE_BUFFER_BYTES = 256 * 1_024
        const val DEFAULT_MAX_SAMPLE_BUFFER_BYTES = 8 * 1_024 * 1_024
        const val MAX_SAMPLE_BUFFER_BYTES = 32 * 1_024 * 1_024
    }
}

/**
 * Today's merge (P6, P27): MediaExtractor reads each sample and MediaMuxer writes it. P35's
 * quick wins: a sample's time and flags are read once, one buffer serves every sample, progress
 * goes out at most every 250 ms, and the time goes to opening, reading, writing and finishing.
 */
internal class MediaMuxerAudioVideoMuxer(
    private val maxSampleBufferBytes: Int,
    private val nanoTime: () -> Long = System::nanoTime,
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
        val outputType = outputMimeType.substringBefore(';').trim().lowercase(Locale.US)
        val outputFormat = when (outputType) {
            AudioVideoMuxCompatibility.MP4_OUTPUT_MIME ->
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            AudioVideoMuxCompatibility.WEBM_OUTPUT_MIME ->
                MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
            else -> return notStarted(DownloadFailureReason.INCOMPATIBLE_TRACKS)
        }
        if (!videoFile.isFile || videoFile.length() <= 0) {
            return notStarted(DownloadFailureReason.INCOMPATIBLE_TRACKS)
        }
        if (!audioFile.isFile || audioFile.length() <= 0) {
            return notStarted(DownloadFailureReason.INCOMPATIBLE_TRACKS)
        }
        when (output) {
            is MuxOutput.ToFile -> {
                val parent = output.file.parentFile
                    ?: return notStarted(DownloadFailureReason.STORAGE_UNAVAILABLE)
                if (!parent.isDirectory && !parent.mkdirs()) {
                    return notStarted(DownloadFailureReason.STORAGE_UNAVAILABLE)
                }
                if (output.file.exists() && !output.file.delete()) {
                    return notStarted(DownloadFailureReason.STORAGE_UNAVAILABLE)
                }
            }
            is MuxOutput.ToDescriptor -> if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return notStarted(DownloadFailureReason.INCOMPATIBLE_TRACKS)
            }
        }

        val timing = MuxTiming(nanoTime)
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        val written = SampleCount()
        fun attempt(result: LocalMuxResult) = MuxAttempt(
            result = result,
            samplesWritten = written.samples,
            details = MuxDetails(
                path = MuxDetails.MEDIA_MUXER,
                videoSamples = written.video,
                audioSamples = written.audio,
                phases = timing.phases(),
            ),
        )
        return try {
            videoExtractor.setDataSource(videoFile.absolutePath)
            audioExtractor.setDataSource(audioFile.absolutePath)
            val videoTrack = videoExtractor.findTrack(VIDEO_MIME_PREFIX)
                ?: return notStarted(DownloadFailureReason.INCOMPATIBLE_TRACKS)
            val audioTrack = audioExtractor.findTrack(AUDIO_MIME_PREFIX)
                ?: return notStarted(DownloadFailureReason.INCOMPATIBLE_TRACKS)
            videoExtractor.selectTrack(videoTrack.index)
            audioExtractor.selectTrack(audioTrack.index)

            muxer = when (output) {
                is MuxOutput.ToFile -> MediaMuxer(output.file.absolutePath, outputFormat)
                is MuxOutput.ToDescriptor -> descriptorMuxer(output.descriptor, outputFormat)
            }
            val outputVideoTrack = muxer.addTrack(videoTrack.format)
            val outputAudioTrack = muxer.addTrack(audioTrack.format)
            muxer.start()
            muxerStarted = true
            timing.lap(MuxTiming.OPEN)
            copySamples(
                video = SampleSource(videoExtractor, outputVideoTrack, isVideo = true),
                audio = SampleSource(audioExtractor, outputAudioTrack, isVideo = false),
                muxer = muxer,
                bufferBytes = sampleBufferBytes(videoTrack.format, audioTrack.format),
                meter = ProgressMeter(
                    durationUs = durationUs(videoTrack.format, audioTrack.format),
                    trackBytes = videoFile.length() + audioFile.length(),
                    listener = onProgress,
                    nanoTime = nanoTime,
                ),
                written = written,
                timing = timing,
            )
            muxer.stop()
            muxerStarted = false
            muxer.release()
            muxer = null
            timing.lap(MuxTiming.FINISH)
            val bytes = when (output) {
                is MuxOutput.ToFile -> output.file.length()
                is MuxOutput.ToDescriptor -> Os.fstat(output.descriptor).st_size
            }
            if (bytes > 0) {
                attempt(LocalMuxResult.Completed(bytes))
            } else {
                output.delete()
                attempt(LocalMuxResult.Failure(DownloadFailureReason.INCOMPATIBLE_TRACKS))
            }
        } catch (cancellation: CancellationException) {
            output.delete()
            throw cancellation
        } catch (error: IOException) {
            output.delete()
            attempt(
                LocalMuxResult.Failure(error.toStorageReason(), DownloadFailureDetails.of(error)),
            )
        } catch (error: ErrnoException) {
            output.delete()
            attempt(
                LocalMuxResult.Failure(
                    DownloadFailureReason.STORAGE_UNAVAILABLE,
                    DownloadFailureDetails.of(error),
                ),
            )
        } catch (error: IllegalArgumentException) {
            output.delete()
            attempt(incompatible(error))
        } catch (error: IllegalStateException) {
            output.delete()
            attempt(incompatible(error))
        } catch (error: SecurityException) {
            output.delete()
            attempt(incompatible(error))
        } finally {
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            videoExtractor.release()
            audioExtractor.release()
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun descriptorMuxer(descriptor: FileDescriptor, format: Int): MediaMuxer =
        MediaMuxer(descriptor, format)

    /** A new file is deleted; a destination's descriptor is left to its owner. */
    private fun MuxOutput.delete() {
        if (this is MuxOutput.ToFile) file.delete()
    }

    private fun notStarted(reason: DownloadFailureReason): MuxAttempt =
        MuxAttempt(LocalMuxResult.Failure(reason), samplesWritten = 0)

    private fun incompatible(error: Exception): LocalMuxResult.Failure =
        LocalMuxResult.Failure(
            DownloadFailureReason.INCOMPATIBLE_TRACKS,
            DownloadFailureDetails.of(error),
        )

    /** The longer track's duration, or null when neither track states one. */
    private fun durationUs(video: MediaFormat, audio: MediaFormat): Long? =
        listOf(video, audio)
            .mapNotNull { format ->
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    format.getLong(MediaFormat.KEY_DURATION)
                } else {
                    null
                }
            }
            .maxOrNull()
            ?.takeIf { it > 0 }

    /**
     * Writes the samples of both tracks in time order. Each sample costs five platform calls
     * (P35; it was eight): its time and flags once, read, write, advance.
     */
    private fun copySamples(
        video: SampleSource,
        audio: SampleSource,
        muxer: MediaMuxer,
        bufferBytes: Int,
        meter: ProgressMeter,
        written: SampleCount,
        timing: MuxTiming,
    ) {
        val buffer = ByteBuffer.allocateDirect(bufferBytes)
        val info = MediaCodec.BufferInfo()
        video.load()
        audio.load()
        timing.lap(MuxTiming.READ)
        while (!video.finished || !audio.finished) {
            val source = when {
                video.finished -> audio
                audio.finished -> video
                video.timeUs <= audio.timeUs -> video
                else -> audio
            }
            if (source.flags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED != 0) {
                throw IllegalArgumentException("Encrypted samples cannot be muxed")
            }
            buffer.clear()
            val sampleBytes = source.extractor.readSampleData(buffer, 0)
            if (sampleBytes < 0) {
                source.finished = true
                continue
            }
            if (sampleBytes == 0 || sampleBytes > buffer.capacity()) {
                throw IllegalArgumentException("Invalid or oversized media sample")
            }
            timing.lap(MuxTiming.READ)
            info.set(0, sampleBytes, source.timeUs, bufferFlagsFor(source.flags))
            muxer.writeSampleData(source.outputTrack, buffer, info)
            timing.lap(MuxTiming.WRITE)
            written.add(source.isVideo)
            meter.wrote(source.timeUs, sampleBytes)
            source.advance()
            timing.lap(MuxTiming.READ)
        }
        meter.finished()
    }

    /** Samples written so far, kept when a merge fails part-way (P27), per track (P35). */
    private class SampleCount {
        var video = 0L
        var audio = 0L
        val samples: Long get() = video + audio

        fun add(isVideo: Boolean) {
            if (isVideo) video += 1 else audio += 1
        }
    }

    /** Where today's merge spends its time (P35): opening, reading, writing, finishing. */
    private class MuxTiming(private val nanoTime: () -> Long) {
        private val nanos = LongArray(4)
        private var last = nanoTime()

        fun lap(phase: Int) {
            val now = nanoTime()
            nanos[phase] += now - last
            last = now
        }

        fun phases(): List<Pair<String, Long>> =
            NAMES.mapIndexed { phase, name -> name to nanos[phase] / NANOS_PER_MILLI }

        companion object {
            const val OPEN = 0
            const val READ = 1
            const val WRITE = 2
            const val FINISH = 3
            val NAMES = listOf("open", "read", "write", "finish")
            const val NANOS_PER_MILLI = 1_000_000L
        }
    }

    /**
     * Turns written samples into progress: sample time of the tracks' duration, or bytes of the
     * tracks' bytes when the duration is unknown; at most every 250 ms (P35), then the end.
     */
    private class ProgressMeter(
        private val durationUs: Long?,
        private val trackBytes: Long,
        private val listener: MuxProgressListener,
        private val nanoTime: () -> Long,
    ) {
        private var timeUs = 0L
        private var bytes = 0L
        private var reportedAt: Long? = null

        fun wrote(presentationTimeUs: Long, sampleBytes: Int) {
            timeUs = maxOf(timeUs, presentationTimeUs)
            bytes += sampleBytes
            val now = nanoTime()
            val last = reportedAt
            if (last != null && now - last < PROGRESS_INTERVAL_NANOS) return
            reportedAt = now
            if (durationUs != null) {
                listener.onProgress(timeUs.coerceIn(0, durationUs), durationUs)
            } else if (trackBytes > 0) {
                listener.onProgress(bytes.coerceAtMost(trackBytes), trackBytes)
            }
        }

        fun finished() {
            val total = durationUs ?: trackBytes.takeIf { it > 0 } ?: return
            listener.onProgress(total, total)
        }

        private companion object {
            const val PROGRESS_INTERVAL_NANOS = 250L * 1_000_000
        }
    }

    /**
     * Translates extractor sample flags into muxer buffer flags.
     *
     * The two constant sets overlap numerically but do not mean the same thing:
     * `SAMPLE_FLAG_ENCRYPTED` shares a value with `BUFFER_FLAG_CODEC_CONFIG` and
     * `SAMPLE_FLAG_PARTIAL_FRAME` shares a value with `BUFFER_FLAG_END_OF_STREAM`, so forwarding
     * the raw value could mark a normal sample as codec config or end of stream. Encrypted samples
     * are already rejected before this point, and only the key-frame flag is carried over.
     */
    private fun bufferFlagsFor(sampleFlags: Int): Int =
        if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
            MediaCodec.BUFFER_FLAG_KEY_FRAME
        } else {
            0
        }

    private fun MediaExtractor.findTrack(mimePrefix: String): InputTrack? {
        repeat(trackCount) { index ->
            val format = getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith(mimePrefix, ignoreCase = true) == true) {
                return InputTrack(index, format)
            }
        }
        return null
    }

    private fun sampleBufferBytes(video: MediaFormat, audio: MediaFormat): Int =
        listOf(video, audio)
            .mapNotNull { format ->
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                } else {
                    null
                }
            }
            .maxOrNull()
            ?.coerceIn(MIN_SAMPLE_BUFFER_BYTES, maxSampleBufferBytes)
            ?: MIN_SAMPLE_BUFFER_BYTES

    private data class InputTrack(
        val index: Int,
        val format: MediaFormat,
    )

    /** One input track; its current sample's time and flags are read once (P35). */
    private class SampleSource(
        val extractor: MediaExtractor,
        val outputTrack: Int,
        val isVideo: Boolean,
    ) {
        var finished = false
        var timeUs = 0L
            private set
        var flags = 0
            private set

        fun load() {
            timeUs = extractor.sampleTime
            if (timeUs < 0) finished = true else flags = extractor.sampleFlags
        }

        fun advance() {
            if (extractor.advance()) load() else finished = true
        }
    }

    private companion object {
        const val VIDEO_MIME_PREFIX = "video/"
        const val AUDIO_MIME_PREFIX = "audio/"
        const val MIN_SAMPLE_BUFFER_BYTES = 256 * 1_024
    }
}

interface AudioVideoMuxRunner {
    suspend fun transfer(
        plan: AudioVideoMuxDownloadPlan,
        destination: DownloadDestination,
        resumeFrom: AudioVideoMuxCheckpoint? = null,
        onProgress: suspend (DownloadProgress) -> Unit = {},
        onCheckpoint: suspend (AudioVideoMuxCheckpoint) -> Unit = {},
    ): AudioVideoMuxResult

    suspend fun discard(plan: AudioVideoMuxDownloadPlan)
}

/**
 * Downloads a video track and its sound, then merges them into the destination (P27): on
 * Android 8.0+ straight into the destination's own file when it gives a file descriptor, else
 * into app storage and then copied. The merge and the copy report progress in the checkpoint
 * ([AudioVideoMuxCheckpoint.stepDone]); each step's time goes to [log] and into a merge
 * failure's details.
 */
class AudioVideoMuxEngine(
    private val dashTransfer: DashTransferRunner,
    private val muxer: LocalAudioVideoMuxer,
    private val workspaceRoot: File,
    /** The copy buffer of today's path: 1 MiB (P27; it was 64 KiB). */
    private val bufferBytes: Int = DEFAULT_COPY_BUFFER_BYTES,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    /** Free bytes where the merge writes, or null when unknown (no check then). */
    private val freeBytes: () -> Long? = { workspaceRoot.usableSpace.takeIf { it > 0 } },
    /** A monotonic clock in milliseconds, for the step times. */
    private val elapsedMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    /** One line per merge with its step times; no addresses. */
    private val log: (String) -> Unit = ::logMergeLine,
    /** The calling thread's CPU time in milliseconds (P35), or null where it is unknown. */
    private val threadCpuMillis: () -> Long? = ::currentThreadCpuMillis,
    /** The shortest time between two progress updates of the tracks: 4 a second (P41). */
    private val progressIntervalMillis: Long = DEFAULT_PROGRESS_INTERVAL_MILLIS,
) : AudioVideoMuxRunner {
    init {
        require(bufferBytes in 1_024..1024 * 1_024)
    }

    override suspend fun transfer(
        plan: AudioVideoMuxDownloadPlan,
        destination: DownloadDestination,
        resumeFrom: AudioVideoMuxCheckpoint?,
        onProgress: suspend (DownloadProgress) -> Unit,
        onCheckpoint: suspend (AudioVideoMuxCheckpoint) -> Unit,
    ): AudioVideoMuxResult = withContext(Dispatchers.IO) {
        val emptyCheckpoint = AudioVideoMuxCheckpoint()
        if (plan.expiresAtEpochMs?.let { it <= clock() } == true) {
            return@withContext failure(
                DownloadFailure(DownloadFailureReason.EXPIRED_URL, stage = CONNECT),
                emptyCheckpoint,
            )
        }
        if (AudioVideoMuxCompatibility.evaluate(plan, sdkInt) !is MuxCompatibility.Compatible) {
            return@withContext failure(
                DownloadFailure(DownloadFailureReason.INCOMPATIBLE_TRACKS, stage = MERGE),
                emptyCheckpoint,
            )
        }
        val workspace = try {
            prepareWorkspace(plan.taskId)
        } catch (error: IOException) {
            return@withContext failure(error.toStorageFailure(OPEN_FILE), emptyCheckpoint)
        }
        val initial = try {
            reconcileCheckpoint(workspace, resumeFrom)
        } catch (error: IOException) {
            return@withContext failure(error.toStorageFailure(WRITE_FILE), emptyCheckpoint)
        }
        val tracker = MuxCheckpointTracker(
            initial = initial,
            progressIntervalMillis = progressIntervalMillis,
            elapsedMillis = elapsedMillis,
            onProgress = onProgress,
            onCheckpoint = onCheckpoint,
        )
        val times = MergeTimes(elapsedMillis)
        val starts = MuxStartTimes(
            expected = buildSet {
                if (!initial.videoReady) add(TrackKind.VIDEO)
                if (!initial.audioReady) add(TrackKind.AUDIO)
            },
            log = log,
        )
        try {
            tracker.emit()
            val trackFailures = coroutineScope {
                buildList {
                    if (!initial.videoReady) {
                        add(
                            async {
                                times.measure(MergeStep.VIDEO) {
                                    transferTrack(
                                        kind = TrackKind.VIDEO,
                                        plan = plan,
                                        workspace = workspace,
                                        resumeFrom = initial.video,
                                        tracker = tracker,
                                        starts = starts,
                                    )
                                }
                            },
                        )
                    }
                    if (!initial.audioReady) {
                        add(
                            async {
                                times.measure(MergeStep.AUDIO) {
                                    transferTrack(
                                        kind = TrackKind.AUDIO,
                                        plan = plan,
                                        workspace = workspace,
                                        resumeFrom = initial.audio,
                                        tracker = tracker,
                                        starts = starts,
                                    )
                                }
                            },
                        )
                    }
                }.awaitAll().filterNotNull()
            }
            starts.logOnce()
            val trackFailure = trackFailures.firstOrNull()
            if (trackFailure != null) {
                val checkpoint = tracker.snapshot()
                if (trackFailure.reason in NON_RESUMABLE_FAILURES) {
                    cleanupAll(plan, workspace)
                }
                return@withContext AudioVideoMuxResult.Failure(
                    trackFailure.withStart(starts),
                    checkpoint,
                )
            }

            tracker.setStage(AudioVideoMuxStage.READY_TO_MUX)
            val merge = Merge(
                plan = plan,
                workspace = workspace,
                destination = destination,
                tracker = tracker,
                times = times,
            )
            val bytesWritten = merge.run()
            tracker.setStage(AudioVideoMuxStage.COMPLETED)
            val completed = tracker.snapshot()
            log(
                "Merge done (${merge.path}): ${times.summary()}; " +
                    MergeTimes.size(bytesWritten) + merge.detailsSummary(),
            )
            cleanupWorkspace(workspace, strict = false)
            AudioVideoMuxResult.Completed(bytesWritten, completed)
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) {
                runCatching { tracker.emit() }
                runCatching { starts.logOnce() }
            }
            throw cancellation
        } catch (abort: MuxAbort) {
            File(workspace, MUX_OUTPUT_NAME).delete()
            val checkpoint = tracker.resetToReadyAfterMuxFailure()
            if (abort.failure.reason in NON_RESUMABLE_FAILURES) {
                cleanupAll(plan, workspace)
            }
            AudioVideoMuxResult.Failure(
                abort.failure.withTimes(times).withStart(starts),
                checkpoint,
            )
        } catch (error: IOException) {
            File(workspace, MUX_OUTPUT_NAME).delete()
            AudioVideoMuxResult.Failure(
                error.toStorageFailure(null).withTimes(times).withStart(starts),
                tracker.resetToReadyAfterMuxFailure(),
            )
        }
    }

    /** The failure with the tracks' start times (P41), when they have any. */
    private fun DownloadFailure.withStart(starts: MuxStartTimes): DownloadFailure =
        starts.summary()?.let { copy(startTimeline = it) } ?: this

    /**
     * The merge failure's details with the step times when the merge was reached (P27), within
     * [DownloadFailureDetails.MAX_CHARS]; the time also goes to the log.
     */
    private fun DownloadFailure.withTimes(times: MergeTimes): DownloadFailure {
        if (times.millis(MergeStep.MERGE) == null && stage != MERGE) return this
        val summary = times.summary().takeIf(String::isNotEmpty) ?: return this
        log("Merge failed (${reason.name}, ${stage?.name ?: "unknown stage"}): $summary")
        val took = "took $summary"
        val room = DownloadFailureDetails.MAX_CHARS - took.length - 2
        val reasonText = detail?.let { text ->
            if (text.length <= room) text else text.take((room - 1).coerceAtLeast(0)) + "\u2026"
        }
        val combined = listOfNotNull(reasonText?.takeIf { room > 1 }, took).joinToString("; ")
        return copy(detail = DownloadFailureDetails.sanitize(combined) ?: detail)
    }

    override suspend fun discard(plan: AudioVideoMuxDownloadPlan) =
        withContext(Dispatchers.IO) {
            var failure: Throwable? = null
            runCatching { dashTransfer.discard(plan.video) }
                .onFailure { failure = it }
            runCatching { dashTransfer.discard(plan.audio) }
                .onFailure { if (failure == null) failure = it }
            runCatching {
                cleanupWorkspace(workspaceFor(plan.taskId), strict = true)
            }.onFailure { if (failure == null) failure = it }
            failure?.let { throw IOException("Cannot discard mux transfer", it) }
            Unit
        }

    private suspend fun transferTrack(
        kind: TrackKind,
        plan: AudioVideoMuxDownloadPlan,
        workspace: File,
        resumeFrom: DashTransferCheckpoint?,
        tracker: MuxCheckpointTracker,
        starts: MuxStartTimes,
    ): DownloadFailure? {
        val trackPlan = if (kind == TrackKind.VIDEO) plan.video else plan.audio
        val result = dashTransfer.transfer(
            plan = trackPlan,
            destination = FileDownloadDestination(
                partialFile = partialFile(workspace, kind),
                completedFile = readyFile(workspace, kind),
            ),
            resumeFrom = resumeFrom,
            onProgress = { progress -> tracker.recordProgress(kind, progress) },
            onCheckpoint = { checkpoint -> tracker.update(kind, checkpoint) },
            onStartTimeline = { timeline -> starts.record(kind, timeline) },
        )
        return when (result) {
            is DashTransferResult.Completed -> {
                tracker.update(kind, result.checkpoint)
                tracker.markReady(kind)
                null
            }
            is DashTransferResult.Failure -> {
                tracker.update(kind, result.checkpoint)
                result.failure
            }
        }
    }

    private fun reconcileCheckpoint(
        workspace: File,
        saved: AudioVideoMuxCheckpoint?,
    ): AudioVideoMuxCheckpoint {
        var checkpoint = saved ?: AudioVideoMuxCheckpoint()
        val videoReady = checkpoint.videoReady &&
            readyFile(workspace, TrackKind.VIDEO).isNonEmptyFile()
        val audioReady = checkpoint.audioReady &&
            readyFile(workspace, TrackKind.AUDIO).isNonEmptyFile()
        if (!videoReady) clearTrackFiles(workspace, TrackKind.VIDEO)
        if (!audioReady) clearTrackFiles(workspace, TrackKind.AUDIO)
        File(workspace, MUX_OUTPUT_NAME).let { file ->
            if (file.exists() && !file.delete()) {
                throw IOException("Cannot reset mux output")
            }
        }
        checkpoint = checkpoint.copy(
            videoReady = videoReady,
            audioReady = audioReady,
            stage = AudioVideoMuxStage.DOWNLOADING_TRACKS,
            stepDone = 0,
            stepTotal = null,
        )
        return checkpoint
    }

    /**
     * One merge of two downloaded tracks into [destination] (P27): in place when the destination
     * gives a file descriptor on Android 8.0+, else today's path (a file in app storage, then a
     * copy); an in-place merge that fails before its first sample falls back to today's path
     * once. Each path first checks the free space it needs.
     */
    private inner class Merge(
        private val plan: AudioVideoMuxDownloadPlan,
        private val workspace: File,
        private val destination: DownloadDestination,
        private val tracker: MuxCheckpointTracker,
        private val times: MergeTimes,
    ) {
        private val videoFile = readyFile(workspace, TrackKind.VIDEO)
        private val audioFile = readyFile(workspace, TrackKind.AUDIO)
        private val trackBytes = videoFile.length() + audioFile.length()

        /** "in place" or "copy", for the log. */
        var path = "copy"
            private set

        /** How the last merge went and the merge thread's CPU time (P35), for the log. */
        private var details: MuxDetails? = null
        private var cpuMillis: Long? = null

        /**
         * "; stream copy: 1800 video + 2600 audio samples, parse 12 ms, …; cpu 1.1 s, wall 1.4 s"
         * when the muxer said how it merged (P35); a large gap between the CPU and the wall time
         * means the phone paused YFT (R24).
         */
        fun detailsSummary(): String {
            val details = details ?: return ""
            val cpu = cpuMillis
            val wall = times.millis(MergeStep.MERGE)
            val time = if (cpu != null && wall != null) {
                "; cpu ${MergeTimes.duration(cpu)}, wall ${MergeTimes.duration(wall)}"
            } else {
                ""
            }
            return "; ${details.summary()}$time"
        }

        suspend fun run(): Long {
            val progressMuxer = muxer as? ProgressAudioVideoMuxer
            if (progressMuxer != null && sdkInt >= IN_PLACE_MIN_SDK) {
                // Even the smaller need of a merge in place must fit before the file is touched.
                requireSpace(trackBytes + MERGE_HEADROOM_BYTES)
                val inPlace = openInPlaceOutput()
                if (inPlace != null) {
                    path = "in place"
                    mergeInPlace(progressMuxer, inPlace)?.let { return it }
                    log("In-place merge failed before its first sample; merging in app storage")
                    path = "copy after in place"
                    emptyDestination()
                }
            }
            return mergeThenCopy(progressMuxer)
        }

        /** The destination's own file, or null when it gives none or cannot open it. */
        private fun openInPlaceOutput(): FileDescriptorOutput? {
            try {
                destination.prepare(null)
            } catch (error: Exception) {
                if (!error.isStorageError()) throw error
                throw MuxAbort(error.toStorageFailure(OPEN_FILE))
            }
            return try {
                destination.openFileDescriptorOutput()
            } catch (error: Exception) {
                if (!error.isStorageError()) throw error
                log("No file descriptor for an in-place merge: ${error.javaClass.simpleName}")
                null
            }
        }

        /**
         * Merges into the destination's file, checks its length and commits; null when the
         * merge failed before its first sample (the caller then falls back once).
         */
        private suspend fun mergeInPlace(
            progressMuxer: ProgressAudioVideoMuxer,
            output: FileDescriptorOutput,
        ): Long? {
            val attempt = output.use { opened ->
                tracker.setStage(AudioVideoMuxStage.MUXING)
                val attempt = runMux(progressMuxer, MuxOutput.ToDescriptor(opened.fileDescriptor))
                if (attempt.result is LocalMuxResult.Completed) {
                    storageStep(WRITE_FILE) {
                        times.measure(MergeStep.SYNC) { opened.fileDescriptor.sync() }
                    }
                }
                attempt
            }
            val bytes = when (val result = attempt.result) {
                is LocalMuxResult.Completed -> result.bytesWritten
                is LocalMuxResult.Failure -> {
                    if (attempt.samplesWritten == 0L) return null
                    throw MuxAbort(
                        DownloadFailure(result.reason, stage = MERGE, detail = result.detail),
                    )
                }
            }
            tracker.setStep(AudioVideoMuxStage.SAVING, done = 0, total = null)
            commitChecked(bytes)
            return bytes
        }

        /** Today's path: merge into app storage, free the tracks, copy into the destination. */
        private suspend fun mergeThenCopy(progressMuxer: ProgressAudioVideoMuxer?): Long {
            requireSpace(2 * trackBytes + MERGE_HEADROOM_BYTES)
            val muxOutput = File(workspace, MUX_OUTPUT_NAME)
            if (muxOutput.exists() && !muxOutput.delete()) {
                throw MuxAbort(DownloadFailureReason.STORAGE_UNAVAILABLE, stage = MERGE)
            }
            tracker.setStage(AudioVideoMuxStage.MUXING)
            val result = if (progressMuxer != null) {
                runMux(progressMuxer, MuxOutput.ToFile(muxOutput)).result
            } else {
                times.measure(MergeStep.MERGE) {
                    muxer.mux(
                        videoFile = videoFile,
                        audioFile = audioFile,
                        outputFile = muxOutput,
                        outputMimeType = plan.outputMimeType,
                    )
                }
            }
            val muxBytes = when (result) {
                is LocalMuxResult.Completed -> result.bytesWritten
                is LocalMuxResult.Failure -> throw MuxAbort(
                    DownloadFailure(result.reason, stage = MERGE, detail = result.detail),
                )
            }
            if (muxBytes <= 0 || !muxOutput.isFile || muxOutput.length() != muxBytes) {
                throw MuxAbort(DownloadFailureReason.INTEGRITY_MISMATCH, stage = VERIFY)
            }
            // The tracks are in the merged file now; deleting them before the copy frees their
            // space. A failure from here on starts the download over.
            tracker.forgetTracks()
            listOf(TrackKind.VIDEO, TrackKind.AUDIO).forEach { kind ->
                runCatching { clearTrackFiles(workspace, kind) }
            }
            return copy(muxOutput)
        }

        /**
         * Runs the blocking merge while another coroutine moves its progress into the checkpoint;
         * a stopped download stops the merge at its next sample.
         */
        private suspend fun runMux(
            progressMuxer: ProgressAudioVideoMuxer,
            output: MuxOutput,
        ): MuxAttempt = coroutineScope {
            val job = coroutineContext[Job]
            val latest = MutableStateFlow<Pair<Long, Long>?>(null)
            var shown: Pair<Long, Long>? = null
            val reporter = launch {
                latest.collect { step ->
                    if (step != null && step != shown) {
                        tracker.setStep(AudioVideoMuxStage.MUXING, step.first, step.second)
                        shown = step
                    }
                }
            }
            var lastPercent = -1
            val cpuStart = threadCpuMillis()
            val attempt = try {
                times.measure(MergeStep.MERGE) {
                    progressMuxer.mux(
                        videoFile = videoFile,
                        audioFile = audioFile,
                        output = output,
                        outputMimeType = plan.outputMimeType,
                    ) { done, total ->
                        if (job?.isActive == false) throw CancellationException("Merge stopped")
                        val percent = percentOf(done, total)
                        if (percent != lastPercent) {
                            lastPercent = percent
                            latest.value = done to total
                        }
                    }
                }
            } finally {
                // The merge ran on this thread: no suspension since cpuStart.
                val cpuEnd = threadCpuMillis()
                if (cpuStart != null && cpuEnd != null) {
                    cpuMillis = (cpuMillis ?: 0L) + (cpuEnd - cpuStart).coerceAtLeast(0L)
                }
                reporter.cancelAndJoin()
            }
            details = attempt.details
            // The last progress, in case the reporter had not shown it yet.
            latest.value?.takeIf { it != shown }?.let { step ->
                tracker.setStep(AudioVideoMuxStage.MUXING, step.first, step.second)
            }
            attempt
        }

        /** Copies [source] into the destination with byte progress, syncs, checks, commits. */
        private suspend fun copy(source: File): Long {
            val totalBytes = source.length().takeIf { it > 0 }
                ?: throw MuxAbort(DownloadFailureReason.INTEGRITY_MISMATCH, stage = VERIFY)
            tracker.setStep(AudioVideoMuxStage.SAVING, done = 0, total = totalBytes)
            storageStep(OPEN_FILE) { destination.prepare(totalBytes) }
            val opened = storageStep(OPEN_FILE) { destination.open() }
            storageStep(WRITE_FILE) {
                opened.use { output ->
                    val buffer = ByteArray(bufferBytes)
                    var position = 0L
                    var lastPercent = 0
                    times.measure(MergeStep.COPY) {
                        source.inputStream().use { input ->
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(position, buffer, 0, read)
                                position += read
                                val percent = percentOf(position, totalBytes)
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    tracker.setStep(
                                        AudioVideoMuxStage.SAVING,
                                        done = position.coerceAtMost(totalBytes),
                                        total = totalBytes,
                                    )
                                }
                            }
                        }
                    }
                    times.measure(MergeStep.SYNC) { output.sync() }
                    if (position != totalBytes) {
                        throw MuxAbort(DownloadFailureReason.INTEGRITY_MISMATCH, stage = VERIFY)
                    }
                }
            }
            commitChecked(totalBytes)
            return totalBytes
        }

        /** Checks the destination holds [bytes] bytes, then publishes it. */
        private fun commitChecked(bytes: Long) {
            times.measure(MergeStep.COMMIT) {
                val length = storageStep(VERIFY) { destination.temporaryLength() }
                if (length != bytes) {
                    throw MuxAbort(DownloadFailureReason.INTEGRITY_MISMATCH, stage = VERIFY)
                }
                storageStep(PUBLISH) { destination.commit() }
            }
        }

        /** Empties what the failed in-place merge left, before today's path writes again. */
        private fun emptyDestination() {
            storageStep(OPEN_FILE) { destination.open().use { output -> output.setLength(0) } }
        }

        /** Fails early when the merge's path cannot fit (P27): no space is used yet. */
        private fun requireSpace(requiredBytes: Long) {
            val free = runCatching(freeBytes).getOrNull() ?: return
            if (free >= requiredBytes) return
            throw MuxAbort(
                DownloadFailure(
                    reason = DownloadFailureReason.INSUFFICIENT_STORAGE,
                    stage = MERGE,
                    detail = "Merging needs ${MergeTimes.size(requiredBytes)} and " +
                        "${MergeTimes.size(free.coerceAtLeast(0))} is free",
                ),
            )
        }
    }

    /** Runs a file step; a storage error becomes a storage failure at [stage]. */
    private inline fun <T> storageStep(stage: DownloadFailureStage, block: () -> T): T =
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (abort: MuxAbort) {
            throw abort
        } catch (error: Exception) {
            if (!error.isStorageError()) throw error
            throw MuxAbort(error.toStorageFailure(stage))
        }

    private fun prepareWorkspace(taskId: String): File {
        val root = workspaceRoot.canonicalFile
        if (!root.isDirectory && !root.mkdirs()) {
            throw IOException("Cannot create mux workspace")
        }
        val workspace = workspaceFor(taskId).canonicalFile
        if (workspace.parentFile != root) throw IOException("Unsafe mux workspace")
        if (!workspace.isDirectory && !workspace.mkdirs()) {
            throw IOException("Cannot create mux task workspace")
        }
        return workspace
    }

    private fun workspaceFor(taskId: String): File =
        File(workspaceRoot, DownloadWorkspaces.nameFor(DownloadWorkspaces.MUX_PREFIX, taskId))

    private suspend fun cleanupAll(
        plan: AudioVideoMuxDownloadPlan,
        workspace: File,
    ) {
        runCatching { dashTransfer.discard(plan.video) }
        runCatching { dashTransfer.discard(plan.audio) }
        cleanupWorkspace(workspace, strict = false)
    }

    private fun clearTrackFiles(workspace: File, kind: TrackKind) {
        listOf(partialFile(workspace, kind), readyFile(workspace, kind)).forEach { file ->
            if (file.exists() && !file.delete()) {
                throw IOException("Cannot reset mux track")
            }
        }
    }

    private fun cleanupWorkspace(workspace: File, strict: Boolean) {
        if (!workspace.exists()) return
        val root = workspaceRoot.canonicalFile
        val canonical = workspace.canonicalFile
        if (canonical.parentFile != root) throw IOException("Unsafe mux workspace")
        var failed = false
        canonical.walkBottomUp().forEach { file ->
            if (!file.delete()) failed = true
        }
        if (strict && failed) throw IOException("Cannot clean mux workspace")
    }

    private fun partialFile(workspace: File, kind: TrackKind): File =
        File(workspace, "${kind.fileStem}.part")

    private fun readyFile(workspace: File, kind: TrackKind): File =
        File(workspace, "${kind.fileStem}.ready")

    private fun failure(
        failure: DownloadFailure,
        checkpoint: AudioVideoMuxCheckpoint,
    ): AudioVideoMuxResult.Failure = AudioVideoMuxResult.Failure(failure, checkpoint)

    private class MuxAbort(val failure: DownloadFailure) : Exception() {
        constructor(
            reason: DownloadFailureReason,
            stage: DownloadFailureStage,
        ) : this(DownloadFailure(reason, stage = stage))
    }

    private enum class TrackKind(val fileStem: String) {
        VIDEO("video"),
        AUDIO("audio"),
    }

    /**
     * The start times of a merged download's tracks (P41): one log line, once every track that
     * downloads in this run has them, and the failure details.
     */
    private class MuxStartTimes(
        private val expected: Set<TrackKind>,
        private val log: (String) -> Unit,
    ) {
        private val timelines = mutableMapOf<TrackKind, DownloadStartTimeline>()
        private var logged = false

        @Synchronized
        fun record(kind: TrackKind, timeline: DownloadStartTimeline) {
            timelines[kind] = timeline
            if (timelines.keys.containsAll(expected)) logOnceLocked()
        }

        /** Logs the times known so far, when a track ended before it had all of them. */
        @Synchronized
        fun logOnce() {
            if (timelines.isNotEmpty()) logOnceLocked()
        }

        @Synchronized
        fun summary(): String? = timelines.takeIf { it.isNotEmpty() }
            ?.let { known -> combined(known).summary() }

        private fun logOnceLocked() {
            if (logged) return
            logged = true
            log("Merged download ${combined(timelines).summary()}")
        }

        private fun combined(known: Map<TrackKind, DownloadStartTimeline>) =
            DownloadStartTimeline.combine(known.mapKeys { (kind, _) -> kind.fileStem })
    }

    /**
     * Keeps the combined checkpoint and progress of both tracks.
     *
     * Progress has a total only once both track lengths are known: a finished track's length is
     * what it downloaded, and a track still downloading reports its length when it knows one.
     * The bytes count what each track reported as written (P41), at most every
     * [progressIntervalMillis] while the tracks download.
     */
    private class MuxCheckpointTracker(
        initial: AudioVideoMuxCheckpoint,
        private val progressIntervalMillis: Long,
        private val elapsedMillis: () -> Long,
        private val onProgress: suspend (DownloadProgress) -> Unit,
        private val onCheckpoint: suspend (AudioVideoMuxCheckpoint) -> Unit,
    ) {
        private val gate = Mutex()
        private var checkpoint = initial
        private var tracksDeleted = false
        private val trackTotals = mutableMapOf<TrackKind, Long>().apply {
            if (initial.videoReady) initial.video?.let { put(TrackKind.VIDEO, it.downloadedBytes) }
            if (initial.audioReady) initial.audio?.let { put(TrackKind.AUDIO, it.downloadedBytes) }
        }
        private val trackBytes = mutableMapOf<TrackKind, Long>()
        private var lastProgressAt: Long? = null

        /** Notes a track's progress: its length and the bytes it wrote (P41). */
        suspend fun recordProgress(kind: TrackKind, progress: DownloadProgress) =
            gate.withLock {
                progress.totalBytes?.takeIf { it > 0 }?.let { trackTotals[kind] = it }
                trackBytes[kind] = progress.downloadedBytes
                if (isDue()) emitProgressLocked()
            }

        suspend fun update(kind: TrackKind, track: DashTransferCheckpoint) = gate.withLock {
            checkpoint = when (kind) {
                TrackKind.VIDEO -> checkpoint.copy(video = track)
                TrackKind.AUDIO -> checkpoint.copy(audio = track)
            }
            if (isDue()) emitProgressLocked()
            onCheckpoint(checkpoint)
        }

        suspend fun markReady(kind: TrackKind) = gate.withLock {
            checkpoint = when (kind) {
                TrackKind.VIDEO -> checkpoint.copy(videoReady = true)
                TrackKind.AUDIO -> checkpoint.copy(audioReady = true)
            }
            val track = if (kind == TrackKind.VIDEO) checkpoint.video else checkpoint.audio
            track?.let { trackTotals[kind] = it.downloadedBytes }
            emitLocked()
        }

        suspend fun setStage(stage: AudioVideoMuxStage) = gate.withLock {
            checkpoint = checkpoint.copy(stage = stage, stepDone = 0, stepTotal = null)
            emitLocked()
        }

        /** Moves the merge or the copy on ([AudioVideoMuxCheckpoint.stepDone]) (P27). */
        suspend fun setStep(stage: AudioVideoMuxStage, done: Long, total: Long?) =
            gate.withLock {
                checkpoint = checkpoint.copy(
                    stage = stage,
                    stepDone = done.coerceAtLeast(0),
                    stepTotal = total?.takeIf { it > 0 },
                )
                emitLocked()
            }

        /** The tracks were deleted after the merge: a later failure starts over (P27). */
        suspend fun forgetTracks() = gate.withLock { tracksDeleted = true }

        suspend fun emit() = gate.withLock { emitLocked() }

        suspend fun snapshot(): AudioVideoMuxCheckpoint = gate.withLock { checkpoint }

        suspend fun resetToReadyAfterMuxFailure(): AudioVideoMuxCheckpoint = gate.withLock {
            checkpoint = when {
                tracksDeleted -> AudioVideoMuxCheckpoint()
                checkpoint.videoReady && checkpoint.audioReady -> checkpoint.copy(
                    stage = AudioVideoMuxStage.READY_TO_MUX,
                    stepDone = 0,
                    stepTotal = null,
                )
                else -> checkpoint.copy(stepDone = 0, stepTotal = null)
            }
            checkpoint
        }

        private fun isDue(): Boolean {
            val now = elapsedMillis()
            val last = lastProgressAt
            if (last != null && now - last < progressIntervalMillis) return false
            lastProgressAt = now
            return true
        }

        private suspend fun emitLocked() {
            emitProgressLocked()
            onCheckpoint(checkpoint)
        }

        private suspend fun emitProgressLocked() {
            val activeChunks = listOfNotNull(checkpoint.video, checkpoint.audio)
                .sumOf { track -> track.chunks.count { !it.completed } }
            val downloaded = TrackKind.entries.sumOf { kind ->
                val saved = when (kind) {
                    TrackKind.VIDEO -> checkpoint.video
                    TrackKind.AUDIO -> checkpoint.audio
                }?.downloadedBytes ?: 0L
                maxOf(saved, trackBytes[kind] ?: 0L)
            }
            val total = trackTotals.values.sum()
                .takeIf { trackTotals.size == TrackKind.entries.size && it >= downloaded }
            onProgress(
                DownloadProgress(
                    downloadedBytes = downloaded,
                    totalBytes = total,
                    activeSegmentCount = activeChunks,
                ),
            )
        }
    }

    private companion object {
        const val MUX_OUTPUT_NAME = "mux-output.mp4"
        const val DEFAULT_PROGRESS_INTERVAL_MILLIS = 250L
        const val DEFAULT_COPY_BUFFER_BYTES = 1_024 * 1_024
        const val IN_PLACE_MIN_SDK = 26
        const val MERGE_HEADROOM_BYTES = 8L * 1_024 * 1_024
        val NON_RESUMABLE_FAILURES = setOf(
            DownloadFailureReason.INVALID_URL,
            DownloadFailureReason.DRM_PROTECTED,
            DownloadFailureReason.UNSUPPORTED_SOURCE,
            DownloadFailureReason.INCOMPATIBLE_TRACKS,
            DownloadFailureReason.MALFORMED_RESPONSE,
            DownloadFailureReason.INTEGRITY_MISMATCH,
        )
    }
}

private fun File.isNonEmptyFile(): Boolean = isFile && length() > 0

/** Whole percent of [done] in [total], 0..100; 0 without a total. */
private fun percentOf(done: Long, total: Long?): Int {
    if (total == null || total <= 0) return 0
    return (done.coerceIn(0, total) * 100 / total).toInt()
}

private fun logMergeLine(message: String) {
    runCatching { Log.i("YftDownloads", message) }
}

/** This thread's CPU time (P35); null off Android, as in JVM tests. */
private fun currentThreadCpuMillis(): Long? =
    runCatching { SystemClock.currentThreadTimeMillis() }.getOrNull()
