package com.alal.yft.core.download

import java.io.ByteArrayOutputStream

/** The ID3v2.3 tag YFT writes before the first MP3 frame: the title only (T18). */
object Id3v2Tag {
    const val MAX_TITLE_CHARS = 250
    private const val HEADER_BYTES = 10
    private const val UTF16_WITH_BOM: Int = 1
    private const val SYNCSAFE_LIMIT = 1 shl 28

    /**
     * A tag with one TIT2 frame in UTF-16 (with a byte order mark), so any script reads back;
     * empty when there is no title. Control characters become spaces.
     */
    fun title(title: String?): ByteArray {
        val text = clean(title) ?: return ByteArray(0)
        val frameBody = ByteArrayOutputStream().apply {
            write(UTF16_WITH_BOM)
            write(0xFF)
            write(0xFE)
            write(text.toByteArray(Charsets.UTF_16LE))
        }.toByteArray()
        val frame = ByteArrayOutputStream().apply {
            write("TIT2".toByteArray(Charsets.ISO_8859_1))
            write(bigEndian(frameBody.size))
            write(byteArrayOf(0, 0))
            write(frameBody)
        }.toByteArray()
        check(frame.size < SYNCSAFE_LIMIT)
        return ByteArrayOutputStream(HEADER_BYTES + frame.size).apply {
            write("ID3".toByteArray(Charsets.ISO_8859_1))
            write(byteArrayOf(3, 0, 0))
            write(syncsafe(frame.size))
            write(frame)
        }.toByteArray()
    }

    private fun clean(title: String?): String? {
        val spaced = title.orEmpty()
            .map { if (it.isISOControl()) ' ' else it }
            .joinToString("")
            .trim()
        if (spaced.isEmpty()) return null
        var cut = spaced.take(MAX_TITLE_CHARS)
        // Never end on half of a surrogate pair.
        if (cut.length < spaced.length && cut.last().isHighSurrogate()) cut = cut.dropLast(1)
        return cut.trimEnd().takeIf(String::isNotEmpty)
    }

    private fun bigEndian(value: Int) = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte(),
    )

    private fun syncsafe(value: Int) = byteArrayOf(
        ((value ushr 21) and 0x7F).toByte(),
        ((value ushr 14) and 0x7F).toByte(),
        ((value ushr 7) and 0x7F).toByte(),
        (value and 0x7F).toByte(),
    )
}
