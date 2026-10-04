package com.alal.yft.core.download

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.Mp3Encoding
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteOrder
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

sealed interface Mp3TranscodeResult {
    data class Completed(val bytesWritten: Long) : Mp3TranscodeResult

    data class Failure(val reason: DownloadFailureReason) : Mp3TranscodeResult
}

/** Converts a complete local AAC file into an MP3 file. */
interface LocalMp3Transcoder {
    suspend fun transcode(source: File, output: File, encoding: Mp3Encoding): Mp3TranscodeResult
}

/** Interleaved 16-bit PCM helpers, kept pure so they are unit-tested. */
internal object PcmLayout {
    /** LAME takes mono or stereo; more channels keep their front left and right. */
    fun encoderChannels(channels: Int): Int = if (channels <= 1) 1 else 2

    /** Copies the first two channels of [frames] frames of [channels]-channel PCM. */
    fun frontPair(pcm: ShortArray, frames: Int, channels: Int, reuse: ShortArray): ShortArray {
        require(channels > 2 && frames >= 0 && frames * channels <= pcm.size)
        val output = if (reuse.size >= frames * 2) reuse else ShortArray(frames * 2)
        for (frame in 0 until frames) {
            output[frame * 2] = pcm[frame * channels]
            output[frame * 2 + 1] = pcm[frame * channels + 1]
        }
        return output
    }
}

/**
 * Decodes the AAC track with MediaCodec and encodes MP3 with [encoders] (LAME by default).
 *
 * The output starts with an ID3v2 title tag. A file without an AAC track, a decoder error or a
 * sample rate LAME rejects fails as [DownloadFailureReason.INCOMPATIBLE_TRACKS]; write errors
 * fail as storage errors.
 */
class AndroidMp3Transcoder(
    private val encoders: Mp3EncoderFactory = LameMp3Encoder,
) : LocalMp3Transcoder {
    override suspend fun transcode(
        source: File,
        output: File,
        encoding: Mp3Encoding,
    ): Mp3TranscodeResult {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: Mp3FrameEncoder? = null
        var started = false
        return try {
            extractor.setDataSource(source.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.lowercase(Locale.US) == MediaFormat.MIMETYPE_AUDIO_AAC
            } ?: return Mp3TranscodeResult.Failure(DownloadFailureReason.INCOMPATIBLE_TRACKS)
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            decoder = codec
            codec.configure(format, null, null, 0)
            codec.start()
            started = true
            var stream = PcmStream(
                sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
            )
            val tag = Id3v2Tag.title(encoding.title)
            val file = FileOutputStream(output)
            BufferedOutputStream(file, OUTPUT_BUFFER_BYTES).use { sink ->
                sink.write(tag)
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var pcm = ShortArray(0)
                var pair = ShortArray(0)
                var idleAfterInput = 0
                while (true) {
                    currentCoroutineContext().ensureActive()
                    if (!inputDone) inputDone = queueInput(extractor, codec)
                    val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                    if (index == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone) {
                        // A decoder that never signals the end must not hang the download.
                        if (++idleAfterInput > MAX_IDLE_POLLS) {
                            return failure(DownloadFailureReason.INCOMPATIBLE_TRACKS)
                        }
                        continue
                    }
                    idleAfterInput = 0
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val changed = codec.outputFormat
                        if (changed.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            changed.getInteger(MediaFormat.KEY_PCM_ENCODING) !=
                            AudioFormat.ENCODING_PCM_16BIT
                        ) {
                            return failure(DownloadFailureReason.INCOMPATIBLE_TRACKS)
                        }
                        val next = PcmStream(
                            sampleRate = changed.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                            channels = changed.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                        )
                        // The encoder is fixed once it has started.
                        if (encoder != null && next != stream) {
                            return failure(DownloadFailureReason.INCOMPATIBLE_TRACKS)
                        }
                        stream = next
                        continue
                    }
                    if (index < 0) continue
                    if (info.size > 0) {
                        val buffer = codec.getOutputBuffer(index)
                            ?: return failure(DownloadFailureReason.INCOMPATIBLE_TRACKS)
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        val shorts = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
                        val count = shorts.remaining()
                        if (pcm.size < count) pcm = ShortArray(count)
                        shorts.get(pcm, 0, count)
                        val active = encoder ?: encoders.open(
                            stream.sampleRate,
                            PcmLayout.encoderChannels(stream.channels),
                            encoding.bitrateKbps,
                        )?.also { encoder = it }
                            ?: return failure(DownloadFailureReason.INCOMPATIBLE_TRACKS)
                        val frames = count / stream.channels
                        if (stream.channels <= 2) {
                            active.encode(pcm, frames, sink)
                        } else {
                            pair = PcmLayout.frontPair(pcm, frames, stream.channels, pair)
                            active.encode(pair, frames, sink)
                        }
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
                val active = encoder ?: return failure(DownloadFailureReason.INCOMPATIBLE_TRACKS)
                active.flush(sink)
                sink.flush()
                file.fd.sync()
            }
            val bytes = output.length()
            if (bytes > tag.size) {
                Mp3TranscodeResult.Completed(bytes)
            } else {
                failure(DownloadFailureReason.INTEGRITY_MISMATCH)
            }
        } catch (error: IOException) {
            Mp3TranscodeResult.Failure(error.storageReasonOrIncompatible(output))
        } catch (_: IllegalArgumentException) {
            Mp3TranscodeResult.Failure(DownloadFailureReason.INCOMPATIBLE_TRACKS)
        } catch (_: IllegalStateException) {
            Mp3TranscodeResult.Failure(DownloadFailureReason.INCOMPATIBLE_TRACKS)
        } catch (_: LinkageError) {
            // No LAME library for this ABI.
            Mp3TranscodeResult.Failure(DownloadFailureReason.UNSUPPORTED_SOURCE)
        } finally {
            runCatching { encoder?.close() }
            if (started) runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { extractor.release() }
        }
    }

    /** Feeds one compressed sample; true once the end of the stream is queued. */
    private fun queueInput(extractor: MediaExtractor, codec: MediaCodec): Boolean {
        val index = codec.dequeueInputBuffer(TIMEOUT_US)
        if (index < 0) return false
        val buffer = codec.getInputBuffer(index) ?: return false
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) {
            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            return true
        }
        codec.queueInputBuffer(index, 0, size, extractor.sampleTime.coerceAtLeast(0), 0)
        extractor.advance()
        return false
    }

    private fun failure(reason: DownloadFailureReason) = Mp3TranscodeResult.Failure(reason)

    /** A read error means the source is not a readable AAC file; a write error is storage. */
    private fun IOException.storageReasonOrIncompatible(output: File): DownloadFailureReason {
        val text = message.orEmpty().lowercase(Locale.US)
        return when {
            "enospc" in text || "no space left" in text || "disk full" in text ->
                DownloadFailureReason.INSUFFICIENT_STORAGE
            output.exists() -> DownloadFailureReason.STORAGE_UNAVAILABLE
            else -> DownloadFailureReason.INCOMPATIBLE_TRACKS
        }
    }

    private data class PcmStream(val sampleRate: Int, val channels: Int) {
        init {
            require(sampleRate > 0 && channels > 0)
        }
    }

    private companion object {
        const val TIMEOUT_US = 10_000L
        const val MAX_IDLE_POLLS = 500
        const val OUTPUT_BUFFER_BYTES = 64 * 1_024
    }
}
