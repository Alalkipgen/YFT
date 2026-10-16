package com.alal.yft.core.download

import com.alal.yft.core.model.download.DownloadFailureDetails
import java.util.Locale
import okhttp3.HttpUrl
import okhttp3.Response

/**
 * A YouTube media file of a live broadcast, on air or recently ended (`source=yt_live_broadcast`,
 * or `yt_premiere_broadcast` for a premiere). After the stream its server cannot be trusted with
 * lengths: on the owner's phone a first range of 1 MiB came back as
 * `Content-Range: bytes 0-671324/671325` with `Content-Length: 1048576`, the full range. Only the
 * Content-Length is honest, so such a file is read range after range from byte 0 until the server
 * has no more ([GrowingRead]); its stated length, Content-Range end and total and validators are
 * never compared. Every other file stays strict.
 */
internal object LiveOriginFile {
    private val SOURCES = setOf("yt_live_broadcast", "yt_premiere_broadcast")
    private val RANGE_START = Regex("""(?i)^bytes\s+(\d+)-""")

    fun matches(url: HttpUrl): Boolean =
        url.queryParameter("source")?.lowercase(Locale.US) in SOURCES

    /** The first byte of a Content-Range, whatever its end and total say. */
    fun rangeStart(header: String?): Long? =
        header?.trim()?.let(RANGE_START::find)?.groupValues?.get(1)?.toLongOrNull()

    /** "asked bytes 0-999, got bytes 0-399/2500, length 1000": what was asked and what came. */
    fun describe(start: Long, end: Long?, response: Response): String? =
        DownloadFailureDetails.sanitize(
            "asked bytes $start-${end ?: ""}, got " +
                (response.header("Content-Range") ?: "no Content-Range") +
                ", length " + (response.header("Content-Length") ?: "none"),
        )
}

/**
 * Reads a [LiveOriginFile] until its end, one ask after another from the next byte; what a
 * failed ask wrote is kept, so it never starts over. The file ends at a 416 or an empty answer
 * (or any 4xx after a short one); one that comes after a full answer may only mean the ask went
 * past the end, so the ask is halved there, down to one byte.
 */
internal class GrowingRead(firstAsk: Long) {
    init {
        require(firstAsk > 0)
    }

    /** Bytes in the file so far: the next ask starts here. */
    var written = 0L
        private set

    /** How many bytes the next ask asks for. */
    var ask = firstAsk
        private set

    var requests = 0
        private set

    /** The last answer was shorter than its ask. */
    var afterShortAnswer = false
        private set

    /** What the answer that ended the file said, for the Detail when nothing came at all. */
    var endDetail: String? = null

    /** The file holds [total] bytes now, also after an ask that failed half way. */
    fun keep(total: Long) {
        require(total >= written)
        written = total
    }

    /** Notes a finished ask that brought [bytes] (0: none); false when the file has ended. */
    fun answered(bytes: Long): Boolean {
        requests += 1
        if (bytes > 0) {
            afterShortAnswer = bytes < ask
            return true
        }
        if (afterShortAnswer || ask == 1L) return false
        ask = maxOf(1L, ask / 2)
        return true
    }

    companion object {
        /** Asks one file may take: 50,000 of 10 MiB is far more than any stream. */
        const val MAX_REQUESTS = 50_000
    }
}
