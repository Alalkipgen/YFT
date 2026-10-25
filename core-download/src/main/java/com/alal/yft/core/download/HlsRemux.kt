package com.alal.yft.core.download

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * D1: what the joined pieces of an HLS download hold, told by the first bytes of its first piece
 * (after the ID3 tags of packed audio), and the extension and type a file of it keeps when it is
 * not made into an MP4: MPEG-TS stays a `.ts`, never an `.mp4` by name only.
 */
internal enum class HlsMediaKind(val keptExtension: String?, val keptMimeType: String?) {
    /** MPEG-TS pieces: remuxed into an MP4 (an M4A for sound alone), or kept as `.ts`. */
    TRANSPORT_STREAM("ts", "video/mp2t"),

    /** Packed AAC sound (ADTS): remuxed into an M4A, or kept as `.aac`. */
    ADTS_AUDIO("aac", "audio/aac"),

    /** Packed MP3 sound: kept as the `.mp3` it is. */
    MPEG_AUDIO("mp3", "audio/mpeg"),

    /** Fragmented MP4 pieces (EXT-X-MAP): joined, they already are an MP4. */
    ISO_MP4(null, null),

    /** Anything else: joined under its planned name, as before. */
    UNKNOWN(null, null),
}

/** D1: one downloaded piece of an HLS download, read from [start] (after its ID3 tags) on. */
internal class HlsPiece(val file: File, val start: Long)

/**
 * D1: makes the pieces of an HLS download one MP4 (an M4A for sound alone) without re-encoding,
 * written into [output] from byte 0; returns the file's length. Throws [StreamCopyUnsupported]
 * for media it cannot remux (the caller then keeps the file in its own format) and an
 * [IOException] when reading a piece or writing fails.
 */
internal interface HlsRemuxer {
    suspend fun remux(
        pieces: List<HlsPiece>,
        kind: HlsMediaKind,
        audioOnly: Boolean,
        output: SeekableDownloadOutput,
    ): Long
}

/** D1: tells [HlsMediaKind] from a piece's first bytes; reads at most about 65 KiB. */
internal object HlsMediaSniffer {
    fun of(file: File, start: Long): HlsMediaKind {
        val head = try {
            readHead(file, start)
        } catch (_: IOException) {
            return HlsMediaKind.UNKNOWN
        }
        if (head.size >= TS_PACKET_BYTES && syncsAt(head, 0) >= neededSyncs(head, 0)) {
            return HlsMediaKind.TRANSPORT_STREAM
        }
        if (head.size >= 8 && String(head, 4, 4, Charsets.ISO_8859_1) in ISO_BOXES) {
            return HlsMediaKind.ISO_MP4
        }
        val at = afterId3Tags(head)
        if (at + 1 < head.size && head[at].u8() == 0xff) {
            val second = head[at + 1].u8()
            if (second and 0xf6 == 0xf0) return HlsMediaKind.ADTS_AUDIO
            if (second and 0xe0 == 0xe0 && second shr 1 and 0x03 != 0) {
                return HlsMediaKind.MPEG_AUDIO
            }
        }
        if (transportStreamStart(head) != null) return HlsMediaKind.TRANSPORT_STREAM
        return HlsMediaKind.UNKNOWN
    }

    /**
     * Where [file]'s MPEG-TS packets start from [start] on: there, or after a prefix of up to
     * 64 KiB (a piece disguised as an image); null when no packets follow.
     */
    fun transportStreamStart(file: File, start: Long): Long? {
        val head = try {
            readHead(file, start)
        } catch (_: IOException) {
            return null
        }
        return transportStreamStart(head)?.let { start + it }
    }

    /**
     * The first offset of [head] that starts MPEG-TS packets: a sync byte every 188 bytes, as
     * many as [head] holds up to five; five in a row after a prefix.
     */
    internal fun transportStreamStart(head: ByteArray): Int? {
        if (head.size < TS_PACKET_BYTES) return null
        if (syncsAt(head, 0) >= neededSyncs(head, 0)) return 0
        val last = head.size - CONFIRMATIONS * TS_PACKET_BYTES
        for (at in 1..last) {
            if (head[at].u8() == TS_SYNC && syncsAt(head, at) >= CONFIRMATIONS) return at
        }
        return null
    }

    private fun neededSyncs(head: ByteArray, at: Int): Int =
        minOf(CONFIRMATIONS, (head.size - at) / TS_PACKET_BYTES).coerceAtLeast(1)

    private fun syncsAt(head: ByteArray, at: Int): Int {
        var count = 0
        var position = at
        while (
            count < CONFIRMATIONS &&
            position + TS_PACKET_BYTES <= head.size &&
            head[position].u8() == TS_SYNC
        ) {
            count += 1
            position += TS_PACKET_BYTES
        }
        return count
    }

    private fun afterId3Tags(head: ByteArray): Int {
        var at = 0
        var tags = 0
        while (
            tags < MAX_ID3_TAGS && at + ID3_HEADER_BYTES <= head.size &&
            head[at].toInt() == 'I'.code && head[at + 1].toInt() == 'D'.code &&
            head[at + 2].toInt() == '3'.code
        ) {
            var size = 0
            for (index in 6..9) size = size shl 7 or (head[at + index].u8() and 0x7f)
            val footer = if (head[at + 5].u8() and 0x10 != 0) ID3_HEADER_BYTES else 0
            at += ID3_HEADER_BYTES + size + footer
            tags += 1
        }
        return at
    }

    private fun readHead(file: File, start: Long): ByteArray =
        RandomAccessFile(file, "r").use { input ->
            val length = (input.length() - start).coerceIn(0L, PROBE_BYTES.toLong()).toInt()
            val head = ByteArray(length)
            input.seek(start)
            input.readFully(head)
            head
        }

    private val ISO_BOXES = setOf(
        "ftyp", "styp", "moof", "moov", "sidx", "emsg", "prft", "free", "skip", "mdat",
    )
    private const val CONFIRMATIONS = 5
    private const val ID3_HEADER_BYTES = 10
    private const val MAX_ID3_TAGS = 4

    /** A 64 KiB prefix and five packets after it. */
    private const val PROBE_BYTES = 64 * 1_024 + CONFIRMATIONS * TS_PACKET_BYTES
}

internal const val TS_PACKET_BYTES = 188
internal const val TS_SYNC = 0x47

internal fun Byte.u8(): Int = toInt() and 0xff
