package com.alal.yft.core.download

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * HLS packed audio (RFC 8216 section 3.4): every piece is an ID3 tag that carries the
 * piece's timestamp, then raw ADTS frames. Joined byte for byte, the second piece's tag sits
 * inside the AAC stream and an AAC reader stops there, so the file played only its first
 * piece (about 5 s on the phone). [payloadStart] gives how many leading bytes are such tags,
 * so only the frames are written. A piece that is not ID3 + ADTS (TS, fMP4, an ID3 tag
 * followed by anything else) gives 0 and is written as it is.
 */
internal object HlsPackedAudio {
    private const val HEADER_BYTES = 10
    private const val FOOTER_FLAG = 0x10
    private const val MAX_TAGS = 4

    fun payloadStart(file: File): Long = try {
        tagsBeforeAdts(file)
    } catch (error: IOException) {
        0L
    }

    private fun tagsBeforeAdts(file: File): Long = RandomAccessFile(file, "r").use { input ->
        val length = input.length()
        val header = ByteArray(HEADER_BYTES)
        var start = 0L
        var tags = 0
        while (tags < MAX_TAGS && start + HEADER_BYTES <= length) {
            input.seek(start)
            input.readFully(header)
            val tag = tagLength(header) ?: break
            start += tag
            tags++
        }
        if (tags == 0 || start + 2 > length) return 0L
        input.seek(start)
        val first = input.read()
        val second = input.read()
        if (first == 0xFF && second != -1 && second and 0xF6 == 0xF0) start else 0L
    }

    /** Whole tag length (header, body, footer) when [header] starts a valid ID3v2 tag. */
    private fun tagLength(header: ByteArray): Long? {
        if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() ||
            header[2] != '3'.code.toByte()
        ) {
            return null
        }
        val major = header[3].toInt() and 0xFF
        val revision = header[4].toInt() and 0xFF
        if (major == 0xFF || revision == 0xFF) return null
        var size = 0L
        for (index in 6..9) {
            val part = header[index].toInt() and 0xFF
            if (part >= 0x80) return null
            size = size shl 7 or part.toLong()
        }
        val footer = if (header[5].toInt() and FOOTER_FLAG != 0) HEADER_BYTES else 0
        return HEADER_BYTES + size + footer
    }
}
