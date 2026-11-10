package com.alal.yft.core.download

import com.alal.yft.core.download.EndedLiveSizes.Outcome
import com.alal.yft.core.download.EndedLiveSizes.Size
import com.alal.yft.core.download.EndedLiveSizes.Track
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToLong
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okio.Buffer

/**
 * Ended live size sample ([EndedLiveSizes]): the start of a spread sample of a recently ended
 * live's pieces, each file sized from it.
 *
 * Piece 0 of the lightest video names the newest piece (`X-Head-Seqnum`). Then
 * [Policy.basePieces] pieces of that video, one from the middle of each equal stretch, and
 * [Policy.soundPieces] of each sound; every other video is read at [Policy.otherPieces] of the
 * same pieces, and its size is the lightest video's times what its pieces weigh against those
 * (on the owner's replay a piece of 720p weighed 13 to 27 pieces of 144p, steady through the
 * stream, while each quality's own pieces varied by half). A file's size is the joined pieces':
 * one `ftyp` and `moov`, then every piece from its `moof`. The newest [LivePieces.TAIL_PIECES]
 * pieces, which the server may not serve, are not read.
 *
 * A piece is read only when its boxes prove its length: one `moof` whose samples fill the `mdat`
 * after it exactly, and a `Content-Range` total, when stated, that says the same. A server that
 * ignores the range, refuses (403, 429) or a sample over [Policy.maxAsks] or [Policy.maxBytes]
 * stops the sample; a file whose size is unlikely within [Policy.maxMarginPercent] gets none.
 */
class EndedLiveSampler(
    client: OkHttpClient,
    private val policy: Policy = Policy(),
    private val elapsedMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    /** One line per sample, what it cost and found; never addresses. */
    private val log: (String) -> Unit = ::logSampleLine,
    /**
     * Where each piece is read. Only the reads move there: sizes are published, and the sample
     * reckoned, on the caller's own dispatcher (the sheet's).
     */
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : EndedLiveSizes {
    data class Policy(
        /** How much of each piece is fetched: enough for its boxes up to the `mdat` header. */
        val headBytes: Int = 8 * 1_024,
        val basePieces: Int = 64,
        val otherPieces: Int = 24,
        val soundPieces: Int = 4,
        val maxAsks: Int = 400,
        val maxBytes: Long = 5L * 1_024 * 1_024,
        val parallel: Int = 6,
        val callTimeoutSeconds: Long = 20,
        val maxRedirects: Int = 5,
        val maxMarginPercent: Int = 25,
    ) {
        init {
            require(headBytes in 64..1_024 * 1_024)
            require(basePieces >= 2 && otherPieces >= 3 && soundPieces >= 2)
            require(maxAsks > 0 && maxBytes > 0 && parallel > 0)
            require(callTimeoutSeconds > 0 && maxRedirects >= 0 && maxMarginPercent in 1..100)
        }
    }

    private val http = SecureDownloadHttp(client, policy.maxRedirects, policy.callTimeoutSeconds)

    override suspend fun estimate(
        tracks: List<Track>,
        onSize: (String, Size) -> Unit,
    ): Outcome {
        val files = tracks.distinctBy { it.url }.filterNot { LivePieces.isWebm(it.mimeType) }
            .mapNotNull { track ->
                track.url.toSafeDownloadUrl()
                    ?.takeIf(LiveOriginFile::matches)
                    ?.let { File(track, it) }
            }
        if (files.isEmpty()) return Outcome(emptyMap())
        val started = elapsedMillis()
        val run = Run(policy)
        val videos = files.filterNot { it.track.sound }
            .sortedBy { it.track.bitrateBitsPerSecond ?: Long.MAX_VALUE }
        val sounds = files.filter { it.track.sound }
        val sizes = linkedMapOf<String, Size>()
        val publish = { file: File, size: Size? ->
            if (size != null) {
                synchronized(sizes) { sizes[file.track.url] = size }
                onSize(file.track.url, size)
            }
        }
        val first = videos.firstOrNull() ?: sounds.first()
        val opening = read(run, first, 0)
        val head = (opening as? PieceRead.Read)?.head
        if (head == null) {
            run.stop(if (opening is PieceRead.Read) "no newest piece named" else run.why(opening))
        } else {
            val pieces = head + 1
            val usable = pieces - LivePieces.TAIL_PIECES
            if (usable < 1) run.stop("too few pieces")
            if (run.going) sample(run, videos, sounds, pieces, usable, publish)
        }
        val outcome = Outcome(
            sizes = synchronized(sizes) { sizes.toMap() },
            asks = run.asks.get(),
            bytesRead = run.bytes.get(),
            note = run.note.get(),
        )
        log(
            String.format(
                Locale.US,
                "ended live sizes: %d of %d files, %d asks, %.1f MB read, %.1f s%s",
                outcome.sizes.size,
                files.size,
                outcome.asks,
                outcome.bytesRead / 1_000_000.0,
                (elapsedMillis() - started) / 1_000.0,
                outcome.note?.let { ", $it" }.orEmpty(),
            ),
        )
        return outcome
    }

    private suspend fun sample(
        run: Run,
        videos: List<File>,
        sounds: List<File>,
        pieces: Int,
        usable: Int,
        publish: (File, Size?) -> Unit,
    ) = coroutineScope {
        val gate = Semaphore(policy.parallel)
        suspend fun readAll(file: File, at: List<Int>): Map<Int, PieceRead.Read> = at.map { sq ->
            async { gate.withPermit { read(run, file, sq) } }
        }.awaitAll().filterIsInstance<PieceRead.Read>().associateBy { it.sq }

        val base = PieceSample.positions(policy.basePieces, usable)
        val soundAt = PieceSample.positions(policy.soundPieces, usable)
        val soundJobs = sounds.map { file -> async { file to readAll(file, soundAt) } }
        val lightest = videos.firstOrNull()
        val baseReads = lightest?.let { readAll(it, base) }.orEmpty()
        val baseSize = lightest?.let { file ->
            val read = base.mapNotNull { baseReads[it]?.fragmentBytes }
            PieceSample.direct(read, pieces.of(baseReads))
                ?.also { publish(file, size(it, baseReads, policy.basePieces, run)) }
        }
        soundJobs.awaitAll().forEach { (file, reads) ->
            val estimate = PieceSample.direct(
                soundAt.mapNotNull { reads[it]?.fragmentBytes },
                pieces.of(reads),
            )
            publish(file, estimate?.let { size(it, reads, policy.soundPieces, run) })
        }
        if (lightest == null || baseSize == null) return@coroutineScope
        val shared = PieceSample.subset(policy.otherPieces, base.filter(baseReads::containsKey))
        // The most wanted rows first: the heaviest video is sized first.
        videos.drop(1).reversed().map { file ->
            async {
                val reads = readAll(file, shared)
                val pairs = shared.mapNotNull { sq ->
                    val own = reads[sq]?.fragmentBytes ?: return@mapNotNull null
                    own to (baseReads[sq]?.fragmentBytes ?: return@mapNotNull null)
                }
                val estimate = PieceSample.ratio(pairs, baseSize)
                    ?.let { estimate ->
                        val own = pieces.of(reads)
                        if (own != pieces) estimate.scaledTo(own, pieces) else estimate
                    }
                publish(file, estimate?.let { size(it, reads, policy.otherPieces, run) })
            }
        }.awaitAll()
    }

    /** The file's own piece count when its pieces name a newer one, else the opening's. */
    private fun Int.of(reads: Map<Int, PieceRead.Read>): Int =
        maxOf(this, (reads.values.mapNotNull { it.head }.maxOrNull() ?: -1) + 1)

    private fun size(
        estimate: PieceSample.Estimate,
        reads: Map<Int, PieceRead.Read>,
        asked: Int,
        run: Run,
    ): Size? {
        val margin = PieceSample.LIKELY_Z * estimate.relativeError
        if (reads.size < minOf(asked, estimate.pieces).coerceAtMost(MIN_READS)) return null
        if (margin * 100 > policy.maxMarginPercent) {
            run.note.compareAndSet(null, "pieces vary too much")
            return null
        }
        val header = reads.values.first().headerBytes
        val bytes = (header + estimate.fragmentTotal).roundToLong().takeIf { it > 0 } ?: return null
        return Size(
            bytes = bytes,
            lowBytes = (bytes * (1 - margin)).roundToLong().coerceIn(1, bytes),
            highBytes = (bytes * (1 + margin)).roundToLong().coerceAtLeast(bytes),
            piecesRead = reads.size.coerceAtMost(estimate.pieces),
            pieces = estimate.pieces,
        )
    }

    private suspend fun read(run: Run, file: File, sq: Int): PieceRead =
        withContext(io) { readPiece(run, file, sq) }

    private suspend fun readPiece(run: Run, file: File, sq: Int): PieceRead {
        if (!run.take(policy)) return PieceRead.Stop(run.note.get() ?: "sample limit")
        val result = try {
            http.execute(file.url, LivePieces.pieceUrl(file.url, sq), file.track.requestContext) {
                header("Range", "bytes=0-${policy.headBytes - 1}")
            }
        } catch (error: IOException) {
            return PieceRead.Missed("network")
        }
        val response = when (result) {
            is SecureDownloadHttp.Result.Failed -> return PieceRead.Missed(result.reason.name)
            is SecureDownloadHttp.Result.Completed -> result.response
        }
        return response.use {
            when (response.code) {
                200, 206 -> Unit
                403, 429 -> return run.stop("HTTP ${response.code}")
                else -> return PieceRead.Missed("HTTP ${response.code}")
            }
            val source = response.body?.source() ?: return PieceRead.Missed("no body")
            val buffer = Buffer()
            val limit = policy.headBytes + 1L
            try {
                while (buffer.size < limit && source.read(buffer, limit - buffer.size) != -1L) Unit
            } catch (error: IOException) {
                return PieceRead.Missed("network")
            } finally {
                run.bytes.addAndGet(buffer.size)
            }
            if (buffer.size > policy.headBytes) return run.stop("range ignored")
            val data = buffer.readByteArray()
            val layout = PieceLayout.read(data) ?: return PieceRead.Missed("layout")
            val whole = data.size < policy.headBytes
            val total = LiveOriginFile.rangeTotal(response.header("Content-Range"))
            val stated = total != null && total != layout.bytes
            if (stated || whole && data.size.toLong() != layout.bytes) {
                return PieceRead.Missed("length disagrees")
            }
            PieceRead.Read(sq, layout.fragmentBytes, layout.headerBytes, LivePieces.head(response))
        }
    }

    private class File(val track: Track, val url: HttpUrl)

    private sealed interface PieceRead {
        class Read(
            val sq: Int,
            val fragmentBytes: Long,
            val headerBytes: Long,
            val head: Int?,
        ) : PieceRead

        class Missed(val why: String) : PieceRead

        class Stop(val why: String) : PieceRead
    }

    /** What the sample may still ask, what it cost, and why it stopped. */
    private class Run(policy: Policy) {
        val asks = AtomicInteger()
        val bytes = AtomicLong()
        val note = AtomicReference<String?>()
        private val stopped = AtomicReference<String?>()
        private val maxBytes = policy.maxBytes

        val going: Boolean get() = stopped.get() == null

        fun take(policy: Policy): Boolean {
            if (!going) return false
            if (bytes.get() + policy.headBytes > maxBytes) {
                stop("over ${maxBytes / 1_024} KiB")
                return false
            }
            if (asks.incrementAndGet() > policy.maxAsks) {
                asks.decrementAndGet()
                stop("over ${policy.maxAsks} asks")
                return false
            }
            return true
        }

        fun stop(why: String): PieceRead.Stop {
            stopped.compareAndSet(null, why)
            note.compareAndSet(null, why)
            return PieceRead.Stop(why)
        }

        fun why(read: PieceRead): String = when (read) {
            is PieceRead.Stop -> read.why
            is PieceRead.Missed -> "first piece ${read.why}"
            is PieceRead.Read -> "read"
        }
    }

    private companion object {
        /** Fewest pieces read for a size, when that many were asked. */
        const val MIN_READS = 3
    }
}

/** Where a sampled piece is: [PieceSample.positions] and its size from a spread sample. */
internal object PieceSample {
    /** About [EndedLiveSizes.LIKELY_PERCENT] in 100 sizes fall within this many errors. */
    const val LIKELY_Z = 1.645

    /**
     * [fragmentTotal] bytes of fragments over [pieces] pieces, with its relative standard error;
     * 0 when every piece was read.
     */
    data class Estimate(val fragmentTotal: Double, val relativeError: Double, val pieces: Int) {
        fun scaledTo(own: Int, of: Int): Estimate =
            copy(fragmentTotal = fragmentTotal * own / of, pieces = own)
    }

    /** [count] pieces of `0 until usable`: the middle one of each of [count] equal stretches. */
    fun positions(count: Int, usable: Int): List<Int> {
        if (usable <= 0) return emptyList()
        if (count >= usable) return (0 until usable).toList()
        return (0 until count).map { ((it + 0.5) * usable / count).toInt() }.distinct()
    }

    /** [count] of [of], spread the same way; all of them when there are no more. */
    fun subset(count: Int, of: List<Int>): List<Int> {
        if (count >= of.size) return of
        return (0 until count).map { of[((it + 0.5) * of.size / count).toInt()] }.distinct()
    }

    /** The total from a mean of [fragments] read of [pieces]; null under 2 reads. */
    fun direct(fragments: List<Long>, pieces: Int): Estimate? {
        val n = fragments.size
        if (n < 2 || pieces < n) return if (n == pieces && n > 0) exact(fragments) else null
        if (n == pieces) return exact(fragments)
        val mean = fragments.average()
        if (mean <= 0) return null
        val deviation = sqrt(fragments.sumOf { (it - mean) * (it - mean) } / (n - 1))
        val error = deviation / (mean * sqrt(n.toDouble())) * sqrt(1 - n.toDouble() / pieces)
        return Estimate(mean * pieces, error, pieces)
    }

    /**
     * The lightest file's [base] total times what [pairs] (this file's fragment, the lightest's,
     * at the same pieces) weigh against each other; null under 3 pairs.
     */
    fun ratio(pairs: List<Pair<Long, Long>>, base: Estimate): Estimate? {
        val n = pairs.size
        if (n < 3) return null
        val own = pairs.sumOf { it.first }.toDouble()
        val lightest = pairs.sumOf { it.second }.toDouble()
        if (own <= 0 || lightest <= 0) return null
        val ratio = own / lightest
        val residual = pairs.map { (x, y) -> x - ratio * y }
        val deviation = sqrt(residual.sumOf { it * it } / (n - 1))
        val fraction = (n.toDouble() / base.pieces).coerceAtMost(1.0)
        val error = deviation / (ratio * (lightest / n) * sqrt(n.toDouble())) * sqrt(1 - fraction)
        val combined = sqrt(base.relativeError * base.relativeError + error * error)
        return Estimate(base.fragmentTotal * ratio, combined, base.pieces)
    }

    private fun exact(fragments: List<Long>) =
        Estimate(fragments.sum().toDouble(), 0.0, fragments.size)
}

/**
 * The length of one live piece from its first bytes: `ftyp` and `moov` (the joined file keeps
 * them once, [headerBytes]), then one `moof` whose samples fill the `mdat` after it, which ends
 * the piece ([bytes]); [fragmentBytes] from the `moof` on. Null for anything else, also when the
 * `moof`'s samples do not add up to the `mdat`, or its sizes are not stated.
 */
internal class PieceLayout private constructor(
    val bytes: Long,
    val fragmentBytes: Long,
    val headerBytes: Long,
) {
    companion object {
        fun read(data: ByteArray): PieceLayout? {
            var at = 0L
            var header = 0L
            var moof = -1L
            var samples: Long? = null
            while (at + BOX_HEADER <= data.size) {
                val box = Box.at(data, at, data.size.toLong()) ?: return null
                when (box.type) {
                    "ftyp", "moov" -> if (moof < 0) header += box.size
                    "moof" -> {
                        if (moof >= 0) return null
                        moof = at
                        if (box.end > data.size) return null
                        samples = sampleBytes(data, box.bodyStart, box.end) ?: return null
                    }
                    "mdat" -> {
                        if (moof < 0 || samples != box.size - box.headerSize) return null
                        return PieceLayout(box.end, box.end - moof, header)
                    }
                }
                at = box.end
            }
            return null
        }

        /** The bytes a `moof`'s samples take: each `trun`'s sizes, or its `tfhd` default. */
        private fun sampleBytes(data: ByteArray, from: Long, to: Long): Long? {
            var total = 0L
            var trafs = 0
            forEachBox(data, from, to) { traf ->
                if (traf.type != "traf") return@forEachBox true
                trafs++
                var defaultSize: Long? = null
                var runs = 0
                forEachBox(data, traf.bodyStart, traf.end) { box ->
                    when (box.type) {
                        "tfhd" -> defaultSize = tfhdDefaultSize(data, box)
                        "trun" -> {
                            total += trunBytes(data, box, defaultSize) ?: return@forEachBox false
                            runs++
                        }
                    }
                    true
                } ?: return null
                runs > 0
            } ?: return null
            return total.takeIf { trafs > 0 }
        }

        private fun tfhdDefaultSize(data: ByteArray, box: Box): Long? {
            val flags = u32(data, box.bodyStart) and 0xFFFFFF
            var at = box.bodyStart + 8
            if (flags and 0x1 != 0L) at += 8
            if (flags and 0x2 != 0L) at += 4
            if (flags and 0x8 != 0L) at += 4
            if (flags and 0x10 == 0L || at + 4 > box.end) return null
            return u32(data, at)
        }

        private fun trunBytes(data: ByteArray, box: Box, defaultSize: Long?): Long? {
            if (box.bodyStart + 8 > box.end) return null
            val flags = u32(data, box.bodyStart) and 0xFFFFFF
            val count = u32(data, box.bodyStart + 4)
            if (count > MAX_SAMPLES) return null
            var at = box.bodyStart + 8
            if (flags and 0x1 != 0L) at += 4
            if (flags and 0x4 != 0L) at += 4
            val fields = listOf(0x100L, 0x200L, 0x400L, 0x800L).count { flags and it != 0L }
            if (at + count * fields * 4 > box.end) return null
            if (flags and 0x200 == 0L) return defaultSize?.let { it * count }
            val sizeAt = if (flags and 0x100 != 0L) 4 else 0
            var sum = 0L
            for (index in 0 until count) sum += u32(data, at + index * fields * 4 + sizeAt)
            return sum
        }

        /** Each box in `from until to`; null when one does not fit or [block] says false. */
        private inline fun forEachBox(
            data: ByteArray,
            from: Long,
            to: Long,
            block: (Box) -> Boolean,
        ): Unit? {
            var at = from
            while (at < to) {
                val box = Box.at(data, at, to) ?: return null
                if (box.end > to || !block(box)) return null
                at = box.end
            }
            return Unit
        }

        private class Box(val type: String, val size: Long, val headerSize: Int, start: Long) {
            val bodyStart = start + headerSize
            val end = start + size

            companion object {
                fun at(data: ByteArray, at: Long, limit: Long): Box? {
                    if (at + BOX_HEADER > limit) return null
                    var size = u32(data, at)
                    val type = String(data, (at + 4).toInt(), 4, Charsets.ISO_8859_1)
                    var headerSize = BOX_HEADER
                    if (size == 1L) {
                        if (at + 16 > limit) return null
                        size = (u32(data, at + 8) shl 32) or u32(data, at + 12)
                        headerSize = 16
                    }
                    if (size < headerSize) return null
                    return Box(type, size, headerSize, at)
                }
            }
        }

        private fun u32(data: ByteArray, at: Long): Long {
            val i = at.toInt()
            return (data[i].toLong() and 0xFF shl 24) or (data[i + 1].toLong() and 0xFF shl 16) or
                (data[i + 2].toLong() and 0xFF shl 8) or (data[i + 3].toLong() and 0xFF)
        }

        private const val BOX_HEADER = 8
        private const val MAX_SAMPLES = 100_000L
    }
}

private fun logSampleLine(message: String) {
    runCatching { android.util.Log.i("YftSizes", message) }
}
