package com.alal.yft.core.download

import com.alal.yft.core.model.download.DownloadFailure
import com.alal.yft.core.model.download.DownloadFailureDetails
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadFailureStage
import com.alal.yft.core.model.download.DownloadFailureStage.CONNECT
import com.alal.yft.core.model.download.DownloadFailureStage.READ_SOURCE
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import okhttp3.HttpUrl
import okhttp3.Response
import okhttp3.ResponseBody

/**
 * A YouTube media file of a live broadcast, on air or recently ended (`source=yt_live_broadcast`,
 * or `yt_premiere_broadcast` for a premiere). After the stream its server cannot be trusted with
 * lengths: on the owner's phone a first range of 1 MiB came back as
 * `Content-Range: bytes 0-671324/671325` with `Content-Length: 1048576`, and the next build,
 * which waited for that Content-Length, timed out in the body. So such a file is read range after
 * range from byte 0 until the server has no more ([GrowingRead]): each answer up to its
 * Content-Range end, never waiting for its Content-Length. Every other file stays strict.
 */
internal object LiveOriginFile {
    private val SOURCES = setOf("yt_live_broadcast", "yt_premiere_broadcast")
    private val RANGE = Regex("""(?i)^bytes\s+(\d+)-(\d+)?""")

    fun matches(url: HttpUrl): Boolean =
        url.queryParameter("source")?.lowercase(Locale.US) in SOURCES

    /** The first byte of a Content-Range, whatever its end and total say. */
    fun rangeStart(header: String?): Long? = range(header, 1)

    /** The last byte of a Content-Range, null when it has none. */
    fun rangeEnd(header: String?): Long? = range(header, 2)

    private fun range(header: String?, group: Int): Long? =
        header?.trim()?.let(RANGE::find)?.groupValues?.get(group)?.toLongOrNull()

    /** "asked bytes 0-999, got bytes 0-399/2500, length 1000": what was asked and what came. */
    fun describe(start: Long, end: Long?, response: Response): String? =
        DownloadFailureDetails.sanitize(
            "asked bytes $start-${end ?: ""}, got " +
                (response.header("Content-Range") ?: "no Content-Range") +
                ", length " + (response.header("Content-Length") ?: "none"),
        )
}

/**
 * Reads a [LiveOriginFile] until its end, one ask after another from the next byte.
 *
 * Each answer must start at the asked byte; it is read up to its Content-Range end (its
 * Content-Length is never waited for) and closed. A body that stops for [stallMillis] or breaks
 * keeps what it brought and the next ask starts after it, so the file never starts over. The
 * file ends only at a 416 or an empty answer; one that comes after a full answer may only mean
 * the ask went past the end, so the ask is halved there, down to one byte. Any other 4xx fails:
 * a cut file is never kept as done. A failure's Detail says what was read and what the last
 * answer said ([summary]).
 */
internal class GrowingRead(firstAsk: Long, private val stallMillis: Long) {
    init {
        require(firstAsk > 0)
        require(stallMillis > 0)
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
    private var afterShortAnswer = false

    /** "206 bytes 0-399/400 len 1000 sq 7/9": the last answer's status and lengths. */
    private var lastAnswer = "none"

    /** What to do with an answer. */
    sealed interface Check

    /** The file has no more bytes here. */
    object End : Check

    /** Read at most [limit] bytes of the body. */
    class Read(val limit: Long) : Check

    /** Stop the file with [failure]. */
    class Fail(val failure: DownloadFailure) : Check

    /** Ask again, as another attempt may get it. */
    class Retry(val failure: DownloadFailure) : Check

    /** How one ask went. */
    sealed interface Outcome

    /** The answer was read to its end; it brought [bytes]. */
    class Answered(val bytes: Long) : Outcome

    /** The ask broke after [bytes] were written, with [failure]. */
    class Broken(val bytes: Long, val failure: DownloadFailure) : Outcome

    /** Decides what to do with the answer to the ask of [length] bytes at [offset]. */
    fun check(response: Response, offset: Long, length: Long): Check {
        val code = response.code
        val range = response.header("Content-Range")
        val contentLength = response.header("Content-Length")?.trim()
        val sequence = response.header("X-Sequence-Num")
        val head = response.header("X-Head-Seqnum")
        lastAnswer = "$code ${range?.trim() ?: "-"} len ${contentLength ?: "-"}" +
            if (sequence != null || head != null) " sq ${sequence ?: "-"}/${head ?: "-"}" else ""
        if (code in 500..599) {
            return Retry(failure(DownloadFailureReason.SERVER_ERROR, code))
        }
        if (code == 416 || code == 204) return End
        if (code != 206) return Fail(failure(code.toReason(), code))
        if (LiveOriginFile.rangeStart(range) != offset) {
            return Fail(failure(DownloadFailureReason.INTEGRITY_MISMATCH))
        }
        if (response.header("Content-Encoding")?.equals("identity", ignoreCase = true) == false) {
            return Fail(failure(DownloadFailureReason.MALFORMED_RESPONSE))
        }
        val mimeType = response.header("Content-Type")
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.US)
        if (mimeType == "text/html" || mimeType == "application/xhtml+xml") {
            return Fail(failure(DownloadFailureReason.UNSUPPORTED_SOURCE))
        }
        if (contentLength == "0") return End
        val end = LiveOriginFile.rangeEnd(range)?.takeIf { it >= offset }
        return Read(end?.let { minOf(it - offset + 1, length) } ?: length)
    }

    /**
     * Reads at most [limit] bytes of [body] into [write]; a body that stops for [stallMillis]
     * or breaks returns what it brought as [Broken].
     */
    suspend fun readBody(
        body: ResponseBody,
        limit: Long,
        bufferBytes: Int,
        write: suspend (ByteArray, Int) -> Unit,
    ): Outcome {
        val source = body.source()
        // Only the body: the first byte of an answer took 15 s on the owner's phone.
        source.timeout().timeout(stallMillis, TimeUnit.MILLISECONDS)
        val buffer = ByteArray(bufferBytes)
        var got = 0L
        while (got < limit) {
            currentCoroutineContext().ensureActive()
            val count = try {
                source.read(buffer, 0, minOf(buffer.size.toLong(), limit - got).toInt())
            } catch (error: IOException) {
                return Broken(
                    got,
                    DownloadFailure(
                        reason = DownloadFailureReason.NETWORK,
                        stage = READ_SOURCE,
                        detail = DownloadFailureDetails.of(error),
                    ),
                )
            }
            if (count == -1) break
            write(buffer, count)
            got += count
        }
        return Answered(got)
    }

    /**
     * Asks ([askOnce] with the offset and length) until the file ends and returns its length.
     * An ask that broke after bytes is asked again from the next byte, at most
     * [MAX_BROKEN_ANSWERS] times in a row; one that brought nothing, [maxAttempts] times in a
     * row with [backoffMillis]. [fail] throws the failure, its Detail the [summary].
     */
    suspend fun readUntilEnd(
        maxAttempts: Int,
        backoffMillis: (Int) -> Long,
        fail: (DownloadFailure) -> Nothing,
        askOnce: suspend (Long, Long) -> Outcome,
    ): Long {
        var failures = 0
        var broken = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            if (requests >= MAX_REQUESTS) {
                fail(failure(DownloadFailureReason.UNSUPPORTED_SOURCE, stage = READ_SOURCE))
            }
            requests += 1
            when (val outcome = askOnce(written, ask)) {
                is Answered -> {
                    written += outcome.bytes
                    failures = 0
                    broken = 0
                    if (!answered(outcome.bytes)) break
                }
                is Broken -> {
                    written += outcome.bytes
                    val tooMany = if (outcome.bytes > 0) {
                        failures = 0
                        ++broken > MAX_BROKEN_ANSWERS
                    } else {
                        ++failures >= maxAttempts
                    }
                    if (tooMany) {
                        fail(
                            outcome.failure.copy(
                                detail = summary(
                                    outcome.failure.detail
                                        ?.takeIf {
                                            outcome.failure.reason ==
                                                DownloadFailureReason.NETWORK
                                        }
                                        ?.substringBefore(':'),
                                ),
                            ),
                        )
                    }
                    if (outcome.bytes == 0L) delay(backoffMillis(failures - 1))
                }
            }
        }
        if (written == 0L) {
            fail(failure(DownloadFailureReason.INTEGRITY_MISMATCH, stage = READ_SOURCE))
        }
        return written
    }

    /** "live 400 B/2 asks, last 206 bytes 0-399/400 len 1000 · hint": for the Detail. */
    fun summary(hint: String? = null): String? =
        DownloadFailureDetails.sanitize(
            "live $written B/$requests asks, last $lastAnswer" + hint?.let { " · $it" }.orEmpty(),
        )

    /** Notes an answer read to its end with [bytes]; false when the file has ended. */
    private fun answered(bytes: Long): Boolean {
        if (bytes > 0) {
            afterShortAnswer = bytes < ask
            return true
        }
        if (afterShortAnswer || ask == 1L) return false
        ask = maxOf(1L, ask / 2)
        return true
    }

    private fun failure(
        reason: DownloadFailureReason,
        code: Int? = null,
        stage: DownloadFailureStage = CONNECT,
    ): DownloadFailure = DownloadFailure(reason, code, stage, summary())

    private fun Int.toReason(): DownloadFailureReason = when (this) {
        401 -> DownloadFailureReason.AUTHENTICATION_REQUIRED
        403 -> DownloadFailureReason.ACCESS_DENIED
        404 -> DownloadFailureReason.NOT_FOUND
        410 -> DownloadFailureReason.GONE
        else -> DownloadFailureReason.HTTP_STATUS
    }

    companion object {
        /** Asks one file may take: 50,000 of 10 MiB is far more than any stream. */
        const val MAX_REQUESTS = 50_000

        /** Answers in a row that may break after bytes before the file fails. */
        const val MAX_BROKEN_ANSWERS = 8
    }
}
