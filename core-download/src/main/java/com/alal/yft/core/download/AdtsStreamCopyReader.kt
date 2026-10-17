package com.alal.yft.core.download

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Backlog item 5 (VP9 HLS with sound): reads an HLS sound joined as pure ADTS ([HlsPackedAudio])
 * as a stream-copy track (P35): every AAC frame without its ADTS header, 1024 samples each, at
 * the rate the headers name, with an `mp4a` sample description whose `esds` holds the matching
 * AudioSpecificConfig. Anything it does not expect — a broken header or size, more than one raw
 * block in a frame, a rate or channel layout an MP4 entry does not hold, a change of profile,
 * rate or channels — is "not supported" ([StreamCopyUnsupported]).
 */
internal class AdtsTrackReader(
    private val source: Mp4Source,
    private val maxSamples: Int = Mp4TrackReader.MAX_SAMPLES,
) {
    private val window = ByteArray(WINDOW_BYTES)
    private var windowStart = 0L
    private var windowLength = 0

    fun read(): Mp4Track {
        var offsets = LongArray(INITIAL_SAMPLES)
        var sizes = IntArray(INITIAL_SAMPLES)
        var count = 0
        var format: Int? = null
        var largest = 0
        var payloadBytes = 0L
        var at = 0L
        while (at < source.size) {
            if (source.size - at < HEADER_BYTES) unsupported("broken ADTS frame at the end")
            fill(at, MAX_HEADER_BYTES)
            val b1 = byte(at + 1)
            if (byte(at) != SYNC_BYTE || b1 and SYNC_MASK != SYNC_BITS) {
                unsupported("no ADTS sync")
            }
            val b2 = byte(at + 2)
            val b3 = byte(at + 3)
            val headerBytes = if (b1 and 1 == 1) HEADER_BYTES else MAX_HEADER_BYTES
            val frameBytes = (b3 and 0x03 shl 11) or (byte(at + 4) shl 3) or (byte(at + 5) shr 5)
            if (byte(at + 6) and 0x03 != 0) unsupported("several AAC blocks in an ADTS frame")
            if (frameBytes <= headerBytes || at + frameBytes > source.size) {
                unsupported("broken ADTS frame size")
            }
            // Profile, sampling frequency index and channel configuration, as one number.
            val channelBits = (b2 and 0x01 shl 2) or (b3 shr 6)
            val frameFormat = (b2 shr 6 shl 7) or (b2 shr 2 and 0x0f shl 3) or channelBits
            if (format == null) {
                format = frameFormat
            } else if (format != frameFormat) {
                unsupported("ADTS format changes")
            }
            if (count == maxSamples) unsupported("too many samples")
            if (count == offsets.size) {
                offsets = offsets.copyOf(count * 2)
                sizes = sizes.copyOf(count * 2)
            }
            val payload = frameBytes - headerBytes
            offsets[count] = at + headerBytes
            sizes[count] = payload
            count++
            largest = maxOf(largest, payload)
            payloadBytes += payload
            at += frameBytes
        }
        val packed = format ?: unsupported("no samples")
        val objectType = (packed shr 7 and 0x03) + 1
        val frequencyIndex = packed shr 3 and 0x0f
        val channels = packed and 0x07
        if (frequencyIndex >= SAMPLE_RATES.size) unsupported("ADTS rate")
        val rate = SAMPLE_RATES[frequencyIndex]
        if (rate > MAX_ENTRY_RATE) unsupported("ADTS rate")
        if (channels == 0) unsupported("ADTS channels")
        val bitrate = payloadBytes * BITS_PER_BYTE * rate / (count.toLong() * SAMPLES_PER_FRAME)
        return Mp4Track(
            kind = StreamCopyTrackKind.AUDIO,
            timescale = rate.toLong(),
            language = 0,
            sampleDescription = sampleDescription(
                objectType = objectType,
                frequencyIndex = frequencyIndex,
                channelConfiguration = channels,
                rate = rate,
                largestFrame = largest,
                bitrate = bitrate,
            ),
            nalLengthSize = null,
            width = 0,
            height = 0,
            matrix = null,
            editShift = 0L,
            firstDecodeTime = 0L,
            count = count,
            offsets = offsets.copyOf(count),
            sizes = sizes.copyOf(count),
            durations = IntArray(count) { SAMPLES_PER_FRAME },
            compositionOffsets = null,
            sync = null,
        )
    }

    /** Makes sure the window holds [length] bytes from [position] on, or up to the end. */
    private fun fill(position: Long, length: Int) {
        val end = minOf(source.size, position + length)
        if (position >= windowStart && end <= windowStart + windowLength) return
        val read = minOf(window.size.toLong(), source.size - position).toInt()
        source.readFully(ByteBuffer.wrap(window, 0, read), position)
        windowStart = position
        windowLength = read
    }

    private fun byte(position: Long): Int =
        window[(position - windowStart).toInt()].toInt() and 0xff

    companion object {
        /** Whether [source] starts like an ADTS frame: the 12-bit sync and layer 0. */
        fun isAdts(source: Mp4Source): Boolean {
            if (source.size < HEADER_BYTES) return false
            val start = ByteArray(2)
            source.readFully(ByteBuffer.wrap(start), 0L)
            return start[0].toInt() and 0xff == SYNC_BYTE &&
                start[1].toInt() and SYNC_MASK == SYNC_BITS
        }

        /** `stsd` with one `mp4a` entry and its `esds`, as the P35 writer copies it. */
        internal fun sampleDescription(
            objectType: Int,
            frequencyIndex: Int,
            channelConfiguration: Int,
            rate: Int,
            largestFrame: Int,
            bitrate: Long,
        ): ByteArray {
            val config = (objectType shl 11) or (frequencyIndex shl 7) or
                (channelConfiguration shl 3)
            val rateBits = bitrate.coerceIn(0L, UINT32_MAX).toInt()
            val esdsPayload = ByteBuffer.allocate(ESDS_PAYLOAD_BYTES)
                .putInt(0)
                .put(byteArrayOf(ES_TAG, ES_LENGTH, 0, 1, 0, CONFIG_TAG, CONFIG_LENGTH))
                .put(AAC_OBJECT_TYPE)
                .put(AUDIO_STREAM)
                .put((largestFrame shr 16).toByte())
                .putShort(largestFrame.toShort())
                .putInt(rateBits)
                .putInt(rateBits)
                .put(byteArrayOf(SPECIFIC_TAG, SPECIFIC_LENGTH))
                .putShort(config.toShort())
                .put(byteArrayOf(SL_TAG, 1, SL_PREDEFINED))
                .array()
            val esds = box("esds", esdsPayload)
            val entry = ByteBuffer.allocate(AUDIO_ENTRY_BYTES)
                .put(ByteArray(6))
                .putShort(1)
                .put(ByteArray(8))
                .putShort((if (channelConfiguration == 7) 8 else channelConfiguration).toShort())
                .putShort(SAMPLE_BITS)
                .putInt(0)
                .putInt(rate shl 16)
                .array()
            val mp4a = box("mp4a", entry, esds)
            return box("stsd", ByteBuffer.allocate(8).putInt(0).putInt(1).array(), mp4a)
        }

        private fun box(type: String, vararg payloads: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            val size = BOX_HEADER_BYTES + payloads.sumOf { it.size }
            out.write(ByteBuffer.allocate(4).putInt(size).array())
            out.write(type.toByteArray(Charsets.ISO_8859_1))
            payloads.forEach(out::write)
            return out.toByteArray()
        }

        private const val SYNC_BYTE = 0xff
        private const val SYNC_MASK = 0xf6
        private const val SYNC_BITS = 0xf0
        private const val HEADER_BYTES = 7
        private const val MAX_HEADER_BYTES = 9
        private const val WINDOW_BYTES = 64 * 1024
        private const val INITIAL_SAMPLES = 1024
        private const val SAMPLES_PER_FRAME = 1024
        private const val BITS_PER_BYTE = 8
        private const val MAX_ENTRY_RATE = 65_535
        private const val UINT32_MAX = 0xffffffffL
        private const val BOX_HEADER_BYTES = 8
        private const val AUDIO_ENTRY_BYTES = 28
        private const val ESDS_PAYLOAD_BYTES = 4 + 27
        private const val SAMPLE_BITS: Short = 16
        private const val ES_TAG: Byte = 3
        private const val ES_LENGTH: Byte = 25
        private const val CONFIG_TAG: Byte = 4
        private const val CONFIG_LENGTH: Byte = 17
        private const val AAC_OBJECT_TYPE: Byte = 0x40
        private const val AUDIO_STREAM: Byte = 0x15
        private const val SPECIFIC_TAG: Byte = 5
        private const val SPECIFIC_LENGTH: Byte = 2
        private const val SL_TAG: Byte = 6
        private const val SL_PREDEFINED: Byte = 2
        private val SAMPLE_RATES = intArrayOf(
            96_000, 88_200, 64_000, 48_000, 44_100, 32_000, 24_000, 22_050, 16_000, 12_000, 11_025,
            8_000, 7_350,
        )
    }
}
