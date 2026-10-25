package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.master.modules.youtube.StreamCheck
import com.alal.yft.extractor.master.verify.CapturedMp4Facts

/**
 * Phase 1.3 D4: the **init check** on an L row (MASTER_KEY_PHASE1_3_PLAN.md guardrail 6, §4 D4).
 * It reads the bytes V already read for the row (from offset 0, the first 4 KiB, ≤ 64 KiB) and
 * makes no request of its own. It passes only when all four hold:
 *
 * 1. **Type:** the answer's content type is the row's type (`video/mp4`, `audio/mp4`,
 *    `video/webm` or `audio/webm`; parameters and case ignored).
 * 2. **Container start:** an MP4 type starts with an `ftyp`, `styp`, `moof` or `sidx` box of a
 *    sane size; a WebM type with the EBML magic `1A 45 DF A3`, and a doc type it states is `webm`
 *    or `matroska`.
 * 3. **No encryption marker** in the bytes read. MP4: a box `encv`, `enca`, `sinf`, `schm`,
 *    `tenc`, `pssh` or `senc` ([CapturedMp4Facts]' box walk, plus a scan of every byte, because a
 *    cut box ends the walk). WebM: a `ContentEncryption` element or a `ContentEncodingType` of 1
 *    in a track. WebM's element IDs are no unique byte pattern, so "none" is known only when the
 *    bytes read hold the whole `Tracks` element; otherwise the check is `unknown`.
 * 4. **Size:** the total in `Content-Range` equals the row's `contentLength` when the row has one.
 *
 * `fail` = the bytes prove the row wrong (a wrong type, wrong start bytes, an encryption marker, a
 * size mismatch); `unknown` = they cannot prove it right (no type, too few bytes, a WebM header
 * cut before its tracks, a row size without a total, a type the check does not know). A `fail`
 * found by any step wins over an `unknown`. The caller shows an L row only on `pass` (`unknown` is
 * never `pass`). Pure: no request and no log; a result names a reason only, never an address, a
 * header or a byte.
 */
internal object InitCheck {
    enum class Reason(val verdict: StreamCheck.Verdict) {
        OK(StreamCheck.Verdict.PASS),

        /** The answer's content type is another type than the row's. */
        TYPE_MISMATCH(StreamCheck.Verdict.FAIL),

        /** The first bytes are not the row's container. */
        START_MISMATCH(StreamCheck.Verdict.FAIL),

        /** An encryption marker in the bytes read. */
        ENCRYPTED(StreamCheck.Verdict.FAIL),

        /** `Content-Range`'s total is not the row's size. */
        SIZE_MISMATCH(StreamCheck.Verdict.FAIL),

        /** The row's type is none the check can read (only MP4 and WebM rows are L rows). */
        ROW_TYPE_UNSUPPORTED(StreamCheck.Verdict.UNKNOWN),

        /** The answer names no content type, or only a generic one. */
        TYPE_MISSING(StreamCheck.Verdict.UNKNOWN),

        /** Fewer than [MIN_BYTES] bytes read. */
        TOO_SHORT(StreamCheck.Verdict.UNKNOWN),

        /** A WebM start whose `Tracks` element the bytes read do not hold whole. */
        TRACKS_UNREAD(StreamCheck.Verdict.UNKNOWN),

        /** The row has a size but the answer gave no `Content-Range` total. */
        SIZE_UNKNOWN(StreamCheck.Verdict.UNKNOWN),
    }

    data class Result(val verdict: StreamCheck.Verdict, val reason: Reason)

    /**
     * What V saw for the row: the answer's content type, the bytes read from offset 0 and the
     * total size `Content-Range` named (see [totalOf]).
     */
    class Observed(val contentType: String?, val bytes: ByteArray, val totalBytes: Long?) {
        /** Never shows a byte or a header value. */
        override fun toString(): String =
            "InitCheck.Observed(type=${contentType != null}, bytes=${bytes.size}, total=${totalBytes != null})"
    }

    const val MIN_BYTES = 8

    /** Checks [observed] against the row's `mimeType` and `contentLength` (null = not known). */
    fun check(rowMimeType: String?, rowContentLength: Long?, observed: Observed): Result {
        val rowType = baseType(rowMimeType)
        val container = when (rowType) {
            in MP4_TYPES -> Container.MP4
            in WEBM_TYPES -> Container.WEBM
            else -> return result(Reason.ROW_TYPE_UNSUPPORTED)
        }
        val answerType = baseType(observed.contentType)
        val type = when {
            answerType == null || answerType in GENERIC_TYPES -> Reason.TYPE_MISSING
            answerType != rowType -> Reason.TYPE_MISMATCH
            else -> Reason.OK
        }
        val bytes = observed.bytes
        val content = when {
            bytes.size < MIN_BYTES -> Reason.TOO_SHORT
            container == Container.MP4 -> mp4(bytes)
            else -> webm(bytes)
        }
        val rowSize = rowContentLength?.takeIf { it > 0 }
        val size = when {
            rowSize == null -> Reason.OK
            observed.totalBytes == null -> Reason.SIZE_UNKNOWN
            observed.totalBytes != rowSize -> Reason.SIZE_MISMATCH
            else -> Reason.OK
        }
        val findings = listOf(type, content, size)
        val decided = findings.firstOrNull { it.verdict == StreamCheck.Verdict.FAIL }
            ?: findings.firstOrNull { it.verdict == StreamCheck.Verdict.UNKNOWN }
            ?: Reason.OK
        return result(decided)
    }

    /**
     * The total of a `Content-Range: bytes first-last/total` answer header; null when the header is
     * missing, names no total (`*`) or does not describe a range inside that total.
     */
    fun totalOf(contentRange: String?): Long? {
        val match = CONTENT_RANGE.matchEntire(contentRange?.trim() ?: return null) ?: return null
        val (first, last, total) = match.destructured
        val start = first.toLongOrNull() ?: return null
        val end = last.toLongOrNull() ?: return null
        val all = total.toLongOrNull() ?: return null
        return all.takeIf { start <= end && end < all }
    }

    private enum class Container { MP4, WEBM }

    private fun result(reason: Reason) = Result(reason.verdict, reason)

    private fun baseType(type: String?): String? =
        type?.substringBefore(';')?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    // MP4 (ISO BMFF).

    private fun mp4(bytes: ByteArray): Reason {
        val size = u32(bytes, 0)
        val sane = when (size) {
            0L -> true // the box runs to the end of the file
            1L -> if (bytes.size < EXTENDED_HEADER) return Reason.TOO_SHORT else u32(bytes, 8) > 0 || u32(bytes, 12) >= MIN_START_BOX
            else -> size >= MIN_START_BOX
        }
        if (fourCc(bytes, 4) !in MP4_STARTS || !sane) return Reason.START_MISMATCH
        val walked = runCatching { CapturedMp4Facts.read(bytes)?.protected == true }.getOrDefault(false)
        return if (walked || MP4_MARKERS.any { marker -> contains(bytes, marker) }) Reason.ENCRYPTED else Reason.OK
    }

    private fun u32(bytes: ByteArray, at: Int): Long =
        (0..3).fold(0L) { value, i -> (value shl 8) or (bytes[at + i].toLong() and 0xFF) }

    private fun fourCc(bytes: ByteArray, at: Int): String =
        String(bytes, at, 4, Charsets.ISO_8859_1)

    private fun contains(bytes: ByteArray, marker: ByteArray): Boolean {
        for (at in 0..bytes.size - marker.size) {
            if (marker.indices.all { bytes[at + it] == marker[it] }) return true
        }
        return false
    }

    // WebM (EBML / Matroska).

    private class Element(val id: Long, val body: Int, val end: Long, val unknownSize: Boolean)

    private enum class Tracks { CLEAN, ENCRYPTED, UNREAD }

    private fun webm(bytes: ByteArray): Reason {
        if (u32(bytes, 0) != EBML_HEADER) return Reason.START_MISMATCH
        val header = element(bytes, 0, bytes.size)
        if (header == null || header.unknownSize || header.end > bytes.size) return Reason.TRACKS_UNREAD
        when (docTypeKnown(bytes, header)) {
            null -> return Reason.TRACKS_UNREAD
            false -> return Reason.START_MISMATCH
            true -> Unit
        }
        return when (segment(bytes, header.end.toInt())) {
            Tracks.CLEAN -> Reason.OK
            Tracks.ENCRYPTED -> Reason.ENCRYPTED
            Tracks.UNREAD -> Reason.TRACKS_UNREAD
        }
    }

    /** True when the header names no doc type or `webm` / `matroska`; false for another; null when unreadable. */
    private fun docTypeKnown(bytes: ByteArray, header: Element): Boolean? {
        var at = header.body
        val end = header.end.toInt()
        while (at < end) {
            val child = element(bytes, at, end) ?: return null
            if (child.unknownSize || child.end > end) return null
            if (child.id == DOC_TYPE) {
                val name = String(bytes, child.body, (child.end - child.body).toInt(), Charsets.ISO_8859_1)
                return name.trimEnd('\u0000') in DOC_TYPES
            }
            at = child.end.toInt()
        }
        return true
    }

    /** Finds the Segment after the header (EBML Void and CRC-32 may come between). */
    private fun segment(bytes: ByteArray, from: Int): Tracks {
        var at = from.toLong()
        var nodes = 0
        while (at < bytes.size && ++nodes <= MAX_EBML_NODES) {
            val element = element(bytes, at.toInt(), bytes.size) ?: return Tracks.UNREAD
            when (element.id) {
                SEGMENT -> return children(bytes, element)
                VOID, CRC32 -> if (element.unknownSize) return Tracks.UNREAD else at = element.end
                else -> return Tracks.UNREAD
            }
        }
        return Tracks.UNREAD
    }

    /** The Segment's children up to `Tracks`; a `Cluster` first or the end of the bytes read = unread. */
    private fun children(bytes: ByteArray, segment: Element): Tracks {
        val limit = if (segment.unknownSize) bytes.size.toLong() else minOf(segment.end, bytes.size.toLong())
        var at = segment.body.toLong()
        var nodes = 0
        while (at < limit && ++nodes <= MAX_EBML_NODES) {
            val element = element(bytes, at.toInt(), bytes.size) ?: return Tracks.UNREAD
            when (element.id) {
                TRACKS -> {
                    if (element.unknownSize || element.end > bytes.size) return Tracks.UNREAD
                    return tracks(bytes, element.body, element.end.toInt(), TRACKS, 0)
                }
                CLUSTER -> return Tracks.UNREAD
                else -> if (element.unknownSize) return Tracks.UNREAD else at = element.end
            }
        }
        return Tracks.UNREAD
    }

    /** Walks `Tracks` → `TrackEntry` → `ContentEncodings` → `ContentEncoding`, bounded. */
    private fun tracks(bytes: ByteArray, from: Int, to: Int, parent: Long, depth: Int): Tracks {
        var at = from
        var nodes = 0
        while (at < to) {
            if (++nodes > MAX_EBML_NODES) return Tracks.UNREAD
            val element = element(bytes, at, to) ?: return Tracks.UNREAD
            if (element.unknownSize || element.end > to) return Tracks.UNREAD
            val end = element.end.toInt()
            if (parent == CONTENT_ENCODING) {
                if (element.id == CONTENT_ENCRYPTION) return Tracks.ENCRYPTED
                if (element.id == CONTENT_ENCODING_TYPE) {
                    // 0 = compression (also the default of an empty element), 1 = encryption.
                    val kind = unsigned(bytes, element.body, end) ?: return Tracks.UNREAD
                    if (kind != 0L) return Tracks.ENCRYPTED
                }
            }
            val descend = when (parent) {
                TRACKS -> element.id == TRACK_ENTRY
                TRACK_ENTRY -> element.id == CONTENT_ENCODINGS
                CONTENT_ENCODINGS -> element.id == CONTENT_ENCODING
                else -> false
            }
            if (descend && depth < MAX_EBML_DEPTH) {
                val inner = tracks(bytes, element.body, end, element.id, depth + 1)
                if (inner != Tracks.CLEAN) return inner
            }
            at = end
        }
        return Tracks.CLEAN
    }

    /** An EBML unsigned integer of 0 to 8 bytes (empty = 0); null when longer. */
    private fun unsigned(bytes: ByteArray, from: Int, to: Int): Long? {
        if (to - from > 8) return null
        var value = 0L
        for (at in from until to) value = (value shl 8) or (bytes[at].toLong() and 0xFF)
        return value
    }

    /** One element header at [at] (ID with its marker bits, then a size); null when cut or invalid. */
    private fun element(bytes: ByteArray, at: Int, limit: Int): Element? {
        if (at < 0 || at >= limit) return null
        val idLength = vintLength(bytes[at].toInt() and 0xFF, MAX_ID_BYTES) ?: return null
        if (at + idLength > limit) return null
        var id = 0L
        for (i in 0 until idLength) id = (id shl 8) or (bytes[at + i].toLong() and 0xFF)
        val sizeAt = at + idLength
        if (sizeAt >= limit) return null
        val first = bytes[sizeAt].toInt() and 0xFF
        val sizeLength = vintLength(first, MAX_SIZE_BYTES) ?: return null
        if (sizeAt + sizeLength > limit) return null
        var size = (first and (0xFF ushr sizeLength)).toLong()
        for (i in 1 until sizeLength) size = (size shl 8) or (bytes[sizeAt + i].toLong() and 0xFF)
        val unknown = size == (1L shl (7 * sizeLength)) - 1
        val body = sizeAt + sizeLength
        return Element(id, body, if (unknown) Long.MAX_VALUE else body + size, unknown)
    }

    private fun vintLength(first: Int, max: Int): Int? {
        if (first == 0) return null
        val length = Integer.numberOfLeadingZeros(first) - 24 + 1
        return length.takeIf { it <= max }
    }

    private val MP4_TYPES = setOf("video/mp4", "audio/mp4")
    private val WEBM_TYPES = setOf("video/webm", "audio/webm")
    private val GENERIC_TYPES = setOf("application/octet-stream", "binary/octet-stream")
    private val MP4_STARTS = setOf("ftyp", "styp", "moof", "sidx")
    private val MP4_MARKERS = listOf("encv", "enca", "sinf", "schm", "tenc", "pssh", "senc")
        .map { it.toByteArray(Charsets.ISO_8859_1) }
    private val DOC_TYPES = setOf("webm", "matroska")
    private val CONTENT_RANGE = Regex("(?i)bytes\\s+(\\d{1,18})-(\\d{1,18})/(\\d{1,18})")

    private const val MIN_START_BOX = 16L
    private const val EXTENDED_HEADER = 16
    private const val MAX_ID_BYTES = 4
    private const val MAX_SIZE_BYTES = 8
    private const val MAX_EBML_NODES = 2_048
    private const val MAX_EBML_DEPTH = 4

    private const val EBML_HEADER = 0x1A45DFA3L
    private const val DOC_TYPE = 0x4282L
    private const val VOID = 0xECL
    private const val CRC32 = 0xBFL
    private const val SEGMENT = 0x18538067L
    private const val TRACKS = 0x1654AE6BL
    private const val CLUSTER = 0x1F43B675L
    private const val TRACK_ENTRY = 0xAEL
    private const val CONTENT_ENCODINGS = 0x6D80L
    private const val CONTENT_ENCODING = 0x6240L
    private const val CONTENT_ENCODING_TYPE = 0x5033L
    private const val CONTENT_ENCRYPTION = 0x5035L
}
