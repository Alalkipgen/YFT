package com.alal.yft.core.download

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import com.alal.yft.core.model.download.DownloadFailureDetails
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.Mp3Encoding
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

sealed interface Mp3TranscodeResult {
    /** G2 (v1.0.2): [detail], the conversion's Details line ([Mp3Times.line]). */
    data class Completed(val bytesWritten: Long, val detail: String? = null) : Mp3TranscodeResult

    /** [detail]: the exception behind the failure, from [DownloadFailureDetails.of] (P21). */
    data class Failure(
        val reason: DownloadFailureReason,
        val detail: String? = null,
    ) : Mp3TranscodeResult
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

/** G2 (v1.0.2): the MP3 conversion's Details line. */
object Mp3Times {
    /** "MP3: 4:12 of sound, decode 1.2 s, encode 6.8 s". */
    fun line(soundMillis: Long, decodeMillis: Long, encodeMillis: Long): String =
        "MP3: ${length(soundMillis)} of sound, decode ${MergeTimes.duration(decodeMillis)}, " +
            "encode ${MergeTimes.duration(encodeMillis)}"

    /** "0:08", "4:12", "1:02:05". */
    fun length(millis: Long): String {
        val seconds = millis.coerceAtLeast(0) / 1_000
        val hours = seconds / 3_600
        val minutes = seconds % 3_600 / 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds % 60)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds % 60)
        }
    }
}

/**
 * Decodes the AAC track with MediaCodec and encodes MP3 with [encoders] (LAME by default).
 * R5 (v1.0.2): on two threads. The decoder runs in MediaCodec's asynchronous mode on its own
 * thread ([AsyncAacDecoder]): each input it frees is filled at once and each output is copied
 * and given back at once, so it never waits for LAME. LAME encodes on the caller's thread from
 * at most [PCM_QUEUE_BUFFERS] decoded buffers ([PcmHandoff]), so decoding and encoding run at
 * the same time. Before (G2), one thread did both in turn: the decoder got new input only
 * between LAME's frames, and each of its steps waited for MediaCodec's own thread. The Details
 * line's decode time is now the time LAME waited for sound; decode and encode add up to the
 * whole conversion.
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
        var decoder: AsyncAacDecoder? = null
        var encoder: Mp3FrameEncoder? = null
        var detail: String? = null
        return try {
            extractor.setDataSource(source.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.lowercase(Locale.US) == MediaFormat.MIMETYPE_AUDIO_AAC
            } ?: return Mp3TranscodeResult.Failure(DownloadFailureReason.INCOMPATIBLE_TRACKS)
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val handoff = PcmHandoff(PCM_QUEUE_BUFFERS)
            AsyncAacDecoder(extractor, handoff).also { decoder = it }.start(format)
            var stream = PcmStream(
                sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
            )
            val tag = Id3v2Tag.title(encoding.title)
            val file = FileOutputStream(output)
            BufferedOutputStream(file, OUTPUT_BUFFER_BYTES).use { sink ->
                sink.write(tag)
                var pair = ShortArray(0)
                var idleTakes = 0
                val loopStart = System.nanoTime()
                var encodeNanos = 0L
                var soundFrames = 0L
                encode@ while (true) {
                    currentCoroutineContext().ensureActive()
                    val sound = when (val item = handoff.take(TAKE_WAIT_MS)) {
                        null -> {
                            // A decoder that stops giving sound must not hang the download.
                            if (++idleTakes > MAX_IDLE_TAKES) {
                                return failure(DownloadFailureReason.INCOMPATIBLE_TRACKS)
                            }
                            continue@encode
                        }
                        PcmItem.End -> break@encode
                        is PcmItem.Failed -> {
                            val error = item.error
                                ?: return failure(DownloadFailureReason.INCOMPATIBLE_TRACKS)
                            throw error
                        }
                        is PcmItem.Sound -> item
                    }
                    idleTakes = 0
                    val next = PcmStream(sound.sampleRate, sound.channels)
                    // The encoder is fixed once it has started.
                    if (encoder != null && next != stream) {
                        return failure(DownloadFailureReason.INCOMPATIBLE_TRACKS)
                    }
                    stream = next
                    val encodeStart = System.nanoTime()
                    val active = encoder ?: encoders.open(
                        stream.sampleRate,
                        PcmLayout.encoderChannels(stream.channels),
                        encoding.bitrateKbps,
                    )?.also { encoder = it }
                        ?: return failure(DownloadFailureReason.INCOMPATIBLE_TRACKS)
                    val frames = sound.count / stream.channels
                    if (stream.channels <= 2) {
                        active.encode(sound.pcm, frames, sink)
                    } else {
                        pair = PcmLayout.frontPair(sound.pcm, frames, stream.channels, pair)
                        active.encode(pair, frames, sink)
                    }
                    encodeNanos += System.nanoTime() - encodeStart
                    soundFrames += frames
                    handoff.recycle(sound.pcm)
                }
                val active = encoder ?: return failure(DownloadFailureReason.INCOMPATIBLE_TRACKS)
                val flushStart = System.nanoTime()
                active.flush(sink)
                val ended = System.nanoTime()
                encodeNanos += ended - flushStart
                detail = Mp3Times.line(
                    soundMillis = soundFrames * 1_000 / stream.sampleRate,
                    decodeMillis = (ended - loopStart - encodeNanos) / 1_000_000,
                    encodeMillis = encodeNanos / 1_000_000,
                )
                sink.flush()
                file.fd.sync()
            }
            val bytes = output.length()
            if (bytes > tag.size) {
                Mp3TranscodeResult.Completed(bytes, detail)
            } else {
                failure(DownloadFailureReason.INTEGRITY_MISMATCH)
            }
        } catch (error: IOException) {
            Mp3TranscodeResult.Failure(
                error.storageReasonOrIncompatible(output),
                DownloadFailureDetails.of(error),
            )
        } catch (error: IllegalArgumentException) {
            incompatible(error)
        } catch (error: IllegalStateException) {
            incompatible(error)
        } catch (error: LinkageError) {
            // No LAME library for this ABI.
            Mp3TranscodeResult.Failure(
                DownloadFailureReason.UNSUPPORTED_SOURCE,
                DownloadFailureDetails.of(error),
            )
        } finally {
            // The decoder's thread stops first: until then it may read the extractor.
            runCatching { decoder?.close() }
            runCatching { encoder?.close() }
            runCatching { extractor.release() }
        }
    }

    private fun failure(reason: DownloadFailureReason) = Mp3TranscodeResult.Failure(reason)

    private fun incompatible(error: Exception) = Mp3TranscodeResult.Failure(
        DownloadFailureReason.INCOMPATIBLE_TRACKS,
        DownloadFailureDetails.of(error),
    )

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
        /** R5: how far the decoder may run ahead of LAME, about 0.7 s of 44.1 kHz sound. */
        const val PCM_QUEUE_BUFFERS = 32

        /** R5: LAME's wait for sound; 100 of them (5 s) without any is a stuck decoder. */
        const val TAKE_WAIT_MS = 50L
        const val MAX_IDLE_TAKES = 100
        const val OUTPUT_BUFFER_BYTES = 64 * 1_024
    }
}

/**
 * R5 (v1.0.2): MediaCodec's AAC decoder in asynchronous mode, on its own thread. Each input the
 * decoder frees is filled from [extractor] at once (after the last sample, the end of the
 * stream); each output is copied, given back to the decoder and handed to LAME through
 * [handoff]. The thread waits only while [handoff] is full. A failure is handed over as
 * [PcmItem.Failed], after which the thread does nothing.
 */
private class AsyncAacDecoder(
    private val extractor: MediaExtractor,
    private val handoff: PcmHandoff,
) : MediaCodec.Callback() {
    private val thread = HandlerThread(THREAD_NAME).apply { start() }
    private val handler = Handler(thread.looper)
    private var codec: MediaCodec? = null
    private var started = false

    /** Set by [close]: a callback after it returns at once, using neither codec nor extractor. */
    @Volatile
    private var stopped = false

    // The decoder thread's own, once the decoder has started.
    private var sampleRate = 0
    private var channels = 0
    private var inputDone = false
    private var finished = false

    /** Creates, configures and starts the decoder for [format]; its callbacks begin at once. */
    fun start(format: MediaFormat) {
        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val created = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec = created
        created.setCallback(this, handler)
        created.configure(format, null, null, 0)
        created.start()
        started = true
    }

    override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
        if (stopped || finished || inputDone) return
        guarded {
            val buffer = codec.getInputBuffer(index) ?: return fail(null)
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) {
                codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                inputDone = true
            } else {
                codec.queueInputBuffer(index, 0, size, extractor.sampleTime.coerceAtLeast(0), 0)
                extractor.advance()
            }
        }
    }

    override fun onOutputBufferAvailable(
        codec: MediaCodec,
        index: Int,
        info: MediaCodec.BufferInfo,
    ) {
        if (stopped || finished) return
        guarded {
            var sound: PcmItem.Sound? = null
            if (info.size > 0) {
                val buffer = codec.getOutputBuffer(index) ?: return fail(null)
                buffer.position(info.offset)
                buffer.limit(info.offset + info.size)
                val shorts = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
                val count = shorts.remaining()
                val pcm = handoff.array(count)
                shorts.get(pcm, 0, count)
                sound = PcmItem.Sound(pcm, count, sampleRate, channels)
            }
            // Given back first: the decoder goes on with its queued input while LAME catches up.
            codec.releaseOutputBuffer(index, false)
            if (sound != null && !handoff.put(sound)) return
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                finished = true
                handoff.put(PcmItem.End)
            }
        }
    }

    override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
        if (stopped || finished) return
        guarded {
            if (format.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                format.getInteger(MediaFormat.KEY_PCM_ENCODING) != AudioFormat.ENCODING_PCM_16BIT
            ) {
                return fail(null)
            }
            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        }
    }

    override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
        if (stopped || finished) return
        fail(e)
    }

    /**
     * Stops the decoder thread, then the decoder: a callback under way ends first and every
     * later one returns at once, so the extractor and LAME can go after this.
     */
    fun close() {
        stopped = true
        handoff.close()
        runCatching {
            val idle = CountDownLatch(1)
            if (handler.post { idle.countDown() }) idle.await(CLOSE_WAIT_MS, TimeUnit.MILLISECONDS)
        }
        codec?.let { decoder ->
            if (started) runCatching { decoder.stop() }
            runCatching { decoder.release() }
        }
        thread.quitSafely()
    }

    private inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            fail(error)
        }
    }

    /** Hands [error] over once (none: an incompatible track); the thread does nothing after. */
    private fun fail(error: Throwable?) {
        if (finished) return
        finished = true
        // Nothing may escape a callback: on the decoder's thread it would end the app.
        runCatching { handoff.put(PcmItem.Failed(error)) }
    }

    private companion object {
        const val THREAD_NAME = "yft-mp3-decode"

        /** How long [close] waits for a callback under way. */
        const val CLOSE_WAIT_MS = 5_000L
    }
}
