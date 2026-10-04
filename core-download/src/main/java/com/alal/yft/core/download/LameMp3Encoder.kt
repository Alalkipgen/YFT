package com.alal.yft.core.download

import java.io.Closeable
import java.io.IOException
import java.io.OutputStream

/** Encodes 16-bit PCM (interleaved when stereo) as MP3 frames. */
interface Mp3FrameEncoder : Closeable {
    @Throws(IOException::class)
    fun encode(pcm: ShortArray, samplesPerChannel: Int, sink: OutputStream)

    /** Writes the last frames; call once after the final [encode]. */
    @Throws(IOException::class)
    fun flush(sink: OutputStream)
}

/** Opens an encoder for one stream, or returns null when the format cannot be encoded. */
fun interface Mp3EncoderFactory {
    fun open(sampleRate: Int, channels: Int, bitrateKbps: Int): Mp3FrameEncoder?
}

/**
 * LAME 3.100 through JNI (`libmp3lame.so` and `libyft_mp3.so`, T18): constant bitrate, joint
 * stereo or mono, no Xing tag. Not thread-safe; one instance encodes one file.
 */
class LameMp3Encoder private constructor(private var handle: Long) : Mp3FrameEncoder {
    private var buffer = ByteArray(MIN_OUTPUT_BYTES)

    override fun encode(pcm: ShortArray, samplesPerChannel: Int, sink: OutputStream) {
        check(handle != 0L) { "Encoder is closed" }
        require(samplesPerChannel >= 0)
        // LAME's documented worst case: 1.25 bytes per sample plus 7200.
        val needed = samplesPerChannel + samplesPerChannel / 4 + LAME_SLACK_BYTES
        if (buffer.size < needed) buffer = ByteArray(needed)
        write(LameNative.encode(handle, pcm, samplesPerChannel, buffer), sink)
    }

    override fun flush(sink: OutputStream) {
        check(handle != 0L) { "Encoder is closed" }
        write(LameNative.flush(handle, buffer), sink)
    }

    override fun close() {
        val open = handle
        handle = 0L
        if (open != 0L) LameNative.close(open)
    }

    private fun write(written: Int, sink: OutputStream) {
        if (written < 0) throw IOException("MP3 encoder error $written")
        sink.write(buffer, 0, written)
    }

    companion object : Mp3EncoderFactory {
        private const val LAME_SLACK_BYTES = 7_200
        private const val MIN_OUTPUT_BYTES = 16 * 1_024

        override fun open(sampleRate: Int, channels: Int, bitrateKbps: Int): Mp3FrameEncoder? {
            if (sampleRate <= 0 || channels !in 1..2) return null
            val handle = LameNative.open(sampleRate, channels, bitrateKbps)
            return if (handle == 0L) null else LameMp3Encoder(handle)
        }
    }
}

/** The JNI entry points in `yft_mp3_jni.c`. Loading fails on an ABI the APK does not ship. */
internal object LameNative {
    init {
        System.loadLibrary("mp3lame")
        System.loadLibrary("yft_mp3")
    }

    external fun open(sampleRate: Int, channels: Int, bitrateKbps: Int): Long

    external fun encode(
        handle: Long,
        pcm: ShortArray,
        samplesPerChannel: Int,
        output: ByteArray,
    ): Int

    external fun flush(handle: Long, output: ByteArray): Int

    external fun close(handle: Long)
}
