package com.alal.yft.core.download

import com.alal.yft.core.model.download.DownloadFailureDetails
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.Locale
import kotlinx.coroutines.CancellationException

/** Reads a stream-copied file back before it counts (P35); Android's MediaExtractor on phones. */
internal fun interface StreamCopyCheck {
    /** Null when [output] reads back as [layout] says; else why not, in a few fixed words. */
    fun check(
        output: MuxOutput,
        layout: StreamCopyLayout,
        videoFile: File,
        audioFile: File,
    ): String?
}

/** Opens a merge's output for the stream copy and empties it for today's way (P35). */
internal interface StreamCopyOutputs {
    /** [output] for positioned writes; closing it leaves a descriptor open for its owner. */
    fun open(output: MuxOutput): OpenStreamCopyOutput

    /** Empties [output] after a stream copy that did not count: a file is deleted. */
    fun discard(output: MuxOutput)
}

internal interface OpenStreamCopyOutput : StreamCopyOutput, Closeable

/** Plain Java file channels: a new file, or a descriptor's channel that is never closed. */
internal object ChannelStreamCopyOutputs : StreamCopyOutputs {
    override fun open(output: MuxOutput): OpenStreamCopyOutput = when (output) {
        is MuxOutput.ToFile -> {
            val file = RandomAccessFile(output.file, "rw")
            val channel = FileChannelStreamCopyOutput(file.channel)
            object : OpenStreamCopyOutput, StreamCopyOutput by channel {
                override fun close() = file.close()
            }
        }
        is MuxOutput.ToDescriptor -> {
            // Closing this stream would close the destination's descriptor on a desktop JVM.
            val channel = FileChannelStreamCopyOutput(FileOutputStream(output.descriptor).channel)
            object : OpenStreamCopyOutput, StreamCopyOutput by channel {
                override fun close() = Unit
            }
        }
    }

    override fun discard(output: MuxOutput) {
        when (output) {
            is MuxOutput.ToFile -> output.file.delete()
            is MuxOutput.ToDescriptor -> {
                val channel = FileOutputStream(output.descriptor).channel
                channel.truncate(0L)
                channel.position(0L)
            }
        }
    }
}

/**
 * P35's fast merge (`FAST_MERGE=ON`): an MP4 video track and its MP4 sound are copied into one
 * MP4 in large blocks — no call per sample — and the result is read back ([check]) before it
 * counts. A track the stream copy does not support, a failed write or a failed check empties
 * the output and merges once with [fallback], today's MediaMuxer way; the reason goes into the
 * details and into a failure's detail. A WebM merge goes to [fallback] directly.
 */
internal class StreamCopyAudioVideoMuxer(
    private val fallback: ProgressAudioVideoMuxer,
    private val check: StreamCopyCheck,
    private val outputs: StreamCopyOutputs = ChannelStreamCopyOutputs,
    private val bufferBytes: Int = DEFAULT_BUFFER_BYTES,
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
        if (outputType != AudioVideoMuxCompatibility.MP4_OUTPUT_MIME) {
            return fallback.mux(videoFile, audioFile, output, outputMimeType, onProgress)
        }
        val note = try {
            return streamCopy(videoFile, audioFile, output, onProgress)
        } catch (cancellation: CancellationException) {
            runCatching { outputs.discard(output) }
            throw cancellation
        } catch (error: StreamCopyUnsupported) {
            "not supported, ${error.message}"
        } catch (error: StreamCopyCheckFailed) {
            "check failed, ${error.message}"
        } catch (error: Exception) {
            "failed, ${error.javaClass.simpleName}"
        } catch (error: OutOfMemoryError) {
            "failed, out of memory"
        }
        runCatching { outputs.discard(output) }
        val attempt = fallback.mux(videoFile, audioFile, output, outputMimeType, onProgress)
        return attempt.withNote(note)
    }

    private fun streamCopy(
        videoFile: File,
        audioFile: File,
        output: MuxOutput,
        onProgress: MuxProgressListener,
    ): MuxAttempt {
        val phases = mutableListOf<Pair<String, Long>>()
        var phaseStart = nanoTime()
        fun phase(name: String) {
            val now = nanoTime()
            phases += name to (now - phaseStart) / NANOS_PER_MILLI
            phaseStart = now
        }
        if (!videoFile.isFile || !audioFile.isFile) unsupported("missing track file")
        if (output is MuxOutput.ToFile) {
            val parent = output.file.parentFile
            if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
                unsupported("no output folder")
            }
            if (output.file.exists() && !output.file.delete()) unsupported("old output stays")
        }
        val buffer = ByteBuffer.allocateDirect(bufferBytes)
        val writer = StreamCopyWriter(buffer)
        var layout: StreamCopyLayout? = null
        val bytes = RandomAccessFile(videoFile, "r").use { videoInput ->
            RandomAccessFile(audioFile, "r").use { audioInput ->
                val video = FileChannelMp4Source(videoInput.channel)
                val audio = FileChannelMp4Source(audioInput.channel)
                val videoTrack = Mp4TrackReader(video, StreamCopyTrackKind.VIDEO).read()
                val audioTrack = Mp4TrackReader(audio, StreamCopyTrackKind.AUDIO).read()
                if (videoTrack.count.toLong() + audioTrack.count > Mp4TrackReader.MAX_SAMPLES) {
                    unsupported("too many samples")
                }
                val planned = StreamCopyLayout.of(videoTrack, audioTrack)
                layout = planned
                phase("parse")
                outputs.open(output).use { opened ->
                    writer.writeData(planned, video, audio, opened) { done, total ->
                        onProgress.onProgress(done, total)
                    }
                    phase("data")
                    val size = writer.writeMoov(planned, opened)
                    opened.truncate(size)
                    phase("index")
                    size
                }
            }
        }
        val planned = checkNotNull(layout)
        check.check(output, planned, videoFile, audioFile)?.let { reason ->
            throw StreamCopyCheckFailed(reason)
        }
        phase("check")
        onProgress.onProgress(planned.payloadBytes, planned.payloadBytes)
        val videoSamples = planned.video.track.count.toLong()
        val audioSamples = planned.audio.track.count.toLong()
        return MuxAttempt(
            result = LocalMuxResult.Completed(bytes),
            samplesWritten = videoSamples + audioSamples,
            details = MuxDetails(MuxDetails.STREAM_COPY, videoSamples, audioSamples, phases),
        )
    }

    /** Today's way merged after the stream copy did not count; [note] says why, also on failure. */
    private fun MuxAttempt.withNote(note: String): MuxAttempt {
        val noted = details?.copy(streamCopyNote = note)
            ?: MuxDetails(MuxDetails.MEDIA_MUXER, 0, 0, streamCopyNote = note)
        val failure = result as? LocalMuxResult.Failure ?: return copy(details = noted)
        val detail = listOfNotNull(failure.detail, "stream copy $note").joinToString("; ")
        return copy(
            result = failure.copy(detail = DownloadFailureDetails.sanitize(detail)),
            details = noted,
        )
    }

    private class StreamCopyCheckFailed(reason: String) : Exception(reason)

    companion object {
        const val DEFAULT_BUFFER_BYTES = 4 * 1_024 * 1_024
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
