package com.alal.yft.core.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.alal.yft.core.model.download.DownloadFailureReason
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Keeps only the sound of a complete local MP4 file (P3). */
interface LocalAudioExtractor {
    /** Writes [source]'s AAC track to [output] as an M4A; same results as the MP3 transcoder. */
    suspend fun extract(source: File, output: File): Mp3TranscodeResult
}

/**
 * Copies the AAC track of an MP4 into an audio-only MP4 (M4A) with MediaExtractor and
 * MediaMuxer, sample by sample, so nothing is re-encoded and the sound stays as it was.
 *
 * A file without an AAC track, or one the platform cannot read or write, fails as
 * [DownloadFailureReason.INCOMPATIBLE_TRACKS]; write errors fail as storage errors.
 */
class AndroidAudioExtractor : LocalAudioExtractor {
    override suspend fun extract(source: File, output: File): Mp3TranscodeResult {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        return try {
            extractor.setDataSource(source.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.lowercase(Locale.US) == MediaFormat.MIMETYPE_AUDIO_AAC
            } ?: return Mp3TranscodeResult.Failure(DownloadFailureReason.INCOMPATIBLE_TRACKS)
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mux = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mux
            val target = mux.addTrack(format)
            mux.start()
            started = true
            val capacity = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                    .coerceIn(MIN_BUFFER_BYTES, MAX_BUFFER_BYTES)
            } else {
                DEFAULT_BUFFER_BYTES
            }
            val buffer = ByteBuffer.allocateDirect(capacity)
            val info = MediaCodec.BufferInfo()
            var samples = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else {
                    0
                }
                info.set(0, size, extractor.sampleTime.coerceAtLeast(0), flags)
                mux.writeSampleData(target, buffer, info)
                samples += 1
                extractor.advance()
            }
            if (samples == 0) {
                return Mp3TranscodeResult.Failure(DownloadFailureReason.INCOMPATIBLE_TRACKS)
            }
            mux.stop()
            started = false
            Mp3TranscodeResult.Completed(output.length())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: IOException) {
            Mp3TranscodeResult.Failure(error.storageFailureReason())
        } catch (_: IllegalStateException) {
            Mp3TranscodeResult.Failure(DownloadFailureReason.INCOMPATIBLE_TRACKS)
        } catch (_: IllegalArgumentException) {
            Mp3TranscodeResult.Failure(DownloadFailureReason.INCOMPATIBLE_TRACKS)
        } finally {
            muxer?.let { mux ->
                if (started) runCatching { mux.stop() }
                runCatching { mux.release() }
            }
            extractor.release()
        }
    }

    private fun IOException.storageFailureReason(): DownloadFailureReason {
        val text = message.orEmpty().lowercase(Locale.US)
        return when {
            "enospc" in text || "no space left" in text ->
                DownloadFailureReason.INSUFFICIENT_STORAGE
            "permission" in text || "read-only" in text ->
                DownloadFailureReason.STORAGE_UNAVAILABLE
            // The platform reports a file it cannot parse as an I/O error.
            else -> DownloadFailureReason.INCOMPATIBLE_TRACKS
        }
    }

    private companion object {
        const val MIN_BUFFER_BYTES = 64 * 1_024
        const val DEFAULT_BUFFER_BYTES = 1_024 * 1_024
        const val MAX_BUFFER_BYTES = 8 * 1_024 * 1_024
    }
}
