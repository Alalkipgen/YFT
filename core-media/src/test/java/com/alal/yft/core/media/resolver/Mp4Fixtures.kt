package com.alal.yft.core.media.resolver

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/** Builds small but valid MP4 box trees for the header parser and resolver tests. */
internal object Mp4Fixtures {
    fun file(
        width: Int = 1280,
        height: Int = 720,
        video: Boolean = true,
        audio: Boolean = true,
        audioKbps: Int = 128,
        moovAtEnd: Boolean = false,
        mediaBytes: Int = 4_096,
        timescale: Int = 30_000,
        sampleDelta: Int = 1_001,
        durationSeconds: Int = 61,
    ): ByteArray {
        val tracks = buildList {
            if (video) add(videoTrack(width, height, timescale, sampleDelta, durationSeconds))
            if (audio) add(audioTrack(audioKbps, durationSeconds))
        }
        val moov = box("moov", mvhd(durationSeconds), *tracks.toTypedArray())
        val ftyp = box("ftyp", ascii("isom"), int(512), ascii("isomiso2avc1mp41"))
        val mdat = box("mdat", ByteArray(mediaBytes))
        return if (moovAtEnd) ftyp + mdat + moov else ftyp + moov + mdat
    }

    private fun mvhd(seconds: Int) = fullBox(
        "mvhd",
        int(0), int(0), int(1_000), int(seconds * 1_000), int(0x00010000),
        short(0x0100), ByteArray(10), ByteArray(36), ByteArray(24), int(3),
    )

    private fun videoTrack(width: Int, height: Int, timescale: Int, delta: Int, seconds: Int) =
        box(
            "trak",
            tkhd(width, height),
            box(
                "mdia",
                mdhd(timescale, seconds),
                hdlr("vide"),
                box(
                    "minf",
                    box(
                        "stbl",
                        fullBox("stsd", int(1), visualEntry(width, height)),
                        fullBox("stts", int(1), int(seconds * 30), int(delta)),
                    ),
                ),
            ),
        )

    private fun audioTrack(kbps: Int, seconds: Int) = box(
        "trak",
        tkhd(0, 0),
        box(
            "mdia",
            mdhd(44_100, seconds),
            hdlr("soun"),
            box(
                "minf",
                box("stbl", fullBox("stsd", int(1), audioEntry(kbps))),
            ),
        ),
    )

    private fun tkhd(width: Int, height: Int) = fullBox(
        "tkhd",
        int(0), int(0), int(1), int(0), int(0), ByteArray(8),
        short(0), short(0), short(0), short(0), ByteArray(36),
        int(width shl 16), int(height shl 16),
    )

    private fun mdhd(timescale: Int, seconds: Int) = fullBox(
        "mdhd",
        int(0), int(0), int(timescale), int(timescale * seconds), short(0x55C4), short(0),
    )

    private fun hdlr(handler: String) =
        fullBox("hdlr", int(0), ascii(handler), ByteArray(12), byteArrayOf(0))

    private fun visualEntry(width: Int, height: Int) = box(
        "avc1",
        ByteArray(6), short(1), ByteArray(16), short(width), short(height),
        int(0x00480000), int(0x00480000), int(0), short(1), ByteArray(32), short(24), short(-1),
    )

    private fun audioEntry(kbps: Int): ByteArray {
        val bitrate = kbps * 1_000
        val decoderSpecific = byteArrayOf(0x05, 0x02, 0x12, 0x10)
        val decoderConfig = byteArrayOf(0x04, (13 + decoderSpecific.size).toByte(), 0x40, 0x15) +
            byteArrayOf(0, 0, 0) + int(bitrate) + int(bitrate) + decoderSpecific
        val esDescriptor = byteArrayOf(0x03, (3 + decoderConfig.size).toByte()) +
            short(1) + byteArrayOf(0) + decoderConfig
        return box(
            "mp4a",
            ByteArray(6), short(1), ByteArray(8), short(2), short(16), short(0), short(0),
            int(44_100 shl 16),
            fullBox("esds", esDescriptor),
        )
    }

    fun box(type: String, vararg payload: ByteArray): ByteArray {
        val body = payload.fold(ByteArray(0)) { all, part -> all + part }
        return int(body.size + 8) + ascii(type) + body
    }

    private fun fullBox(type: String, vararg payload: ByteArray): ByteArray =
        box(type, int(0), *payload)

    private fun int(value: Int): ByteArray = ByteBuffer.allocate(4).putInt(value).array()

    private fun short(value: Int): ByteArray =
        ByteBuffer.allocate(2).putShort(value.toShort()).array()

    private fun ascii(text: String): ByteArray = text.toByteArray(Charsets.ISO_8859_1)

    /** A reader over [bytes] that counts its reads, like a server answering byte ranges. */
    class Reader(private val bytes: ByteArray) : RangeReader {
        var reads = 0
        val offsets = mutableListOf<Long>()

        override suspend fun read(offset: Long, length: Int): ByteArray? {
            reads += 1
            offsets += offset
            if (offset >= bytes.size) return null
            val end = minOf(bytes.size.toLong(), offset + length).toInt()
            return ByteArrayOutputStream().apply {
                write(bytes, offset.toInt(), end - offset.toInt())
            }.toByteArray()
        }
    }
}
