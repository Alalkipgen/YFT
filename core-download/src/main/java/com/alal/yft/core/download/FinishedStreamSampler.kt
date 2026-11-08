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
 * Finished stream size sample ([FinishedStreamSizes]): the lengths of a spread sample of a
 * finished HLS playlist's pieces, each file sized from them.
 *
 * The playlist is read and parsed as its download reads it ([HlsDownloadManifestParser]): a
 * finished playlist with no key, the same pieces. [Policy.pieces] of its pieces, the middle one
 * of each equal stretch ([PieceSample.positions]), are asked for their first byte; the answer's
 * `Content-Range` total (`bytes 0-0/LENGTH`) is the piece's length. A playlist that gives every
 * piece a byte range states the lengths, so it is added up without an ask. A file's size is its
 * start pieces (`EXT-X-MAP`) and the mean piece times its pieces ([PieceSample.direct]). It counts
 * the pieces as they are fetched: an MP4 made from MPEG-TS pieces came out about 2% smaller
 * (Roshy, 2026-11-08).
 *
 * A server that ignores the range or refuses (403, 429), or a sample over [Policy.maxAsks] or
 * [Policy.maxBytes], stops the sample. A file with under half of its asks read, or whose size is
 * unlikely within [Policy.maxMarginPercent] ([EndedLiveSizes.LIKELY_PERCENT] in 100), gets none.
 */
class FinishedStreamSampler(
    client: OkHttpClient,
    private val policy: Policy = Policy(),
    private val elapsedMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    /** One line per sample, what it cost and found; never addresses. */
    private val log: (String) -> Unit = ::logFinishedSampleLine,
    /**
     * Where each playlist and piece is read. Only the reads move there: sizes are published on
     * the caller's own dispatcher (the sheet's).
     */
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : FinishedStreamSizes {
    data class Policy(
        /** Pieces asked of each file; on Roshy 48 of 885 came within 1.4% of every piece. */
        val pieces: Int = 48,
        val maxPlaylistBytes: Int = 1_024 * 1_024,
        val maxChunks: Int = 20_000,
        val maxAsks: Int = 400,
        val maxBytes: Long = 8L * 1_024 * 1_024,
        val parallel: Int = 6,
        val callTimeoutSeconds: Long = 20,
        val maxRedirects: Int = 5,
        val maxMarginPercent: Int = 25,
    ) {
        init {
            require(pieces >= 2 && maxPlaylistBytes in 1_024..4 * 1_024 * 1_024)
            require(maxChunks > 0 && maxAsks > 0 && maxBytes > 0 && parallel > 0)
            require(callTimeoutSeconds > 0 && maxRedirects >= 0 && maxMarginPercent in 1..100)
        }
    }

    private val http = SecureDownloadHttp(client, policy.maxRedirects, policy.callTimeoutSeconds)

    override suspend fun estimate(
        tracks: List<Track>,
        onSize: (String, Size) -> Unit,
    ): Outcome {
        val files = tracks.distinctBy { it.url }
            .mapNotNull { track -> track.url.toSafeDownloadUrl()?.let { File(track, it) } }
        if (files.isEmpty()) return Outcome(emptyMap())
        val started = elapsedMillis()
        val run = Run(policy)
        val gate = Semaphore(policy.parallel)
        val sizes = linkedMapOf<String, Size>()
        coroutineScope {
            files.map { file ->
                async {
                    val size = sizeOf(run, gate, file) ?: return@async
                    synchronized(sizes) { sizes[file.track.url] = size }
                    onSize(file.track.url, size)
                }
            }.awaitAll()
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
                "finished stream sizes: %d of %d files, %d asks, %.1f MB read, %.1f s%s",
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

    private suspend fun sizeOf(run: Run, gate: Semaphore, file: File): Size? {
        val chunks = gate.withPermit { withContext(io) { playlist(run, file) } } ?: return null
        val media = chunks.filter { it.type == HlsChunkType.MEDIA }
        val starts = chunks.filter { it.type == HlsChunkType.INITIALIZATION }
        val stated = media.all { it.byteRange != null }
        val at = if (stated) emptyList() else PieceSample.positions(policy.pieces, media.size)
        val lengths = coroutineScope {
            (starts + at.map(media::get)).map { chunk ->
                async {
                    chunk.byteRange?.length
                        ?: gate.withPermit { withContext(io) { length(run, file, chunk) } }
                }
            }.awaitAll()
        }
        val startBytes = lengths.take(starts.size)
        if (startBytes.any { it == null }) return run.note("start piece unread")
        val estimate = if (stated) {
            PieceSample.direct(media.map { it.byteRange!!.length }, media.size)
        } else {
            val read = lengths.drop(starts.size).filterNotNull()
            if (read.size < at.size && read.size < maxOf(MIN_READS, (at.size + 1) / 2)) {
                return run.note("too few pieces read")
            }
            PieceSample.direct(read, media.size)
        } ?: return run.note("too few pieces read")
        val margin = PieceSample.LIKELY_Z * estimate.relativeError
        if (margin * 100 > policy.maxMarginPercent) return run.note("pieces vary too much")
        val bytes = (startBytes.sumOf { it!! } + estimate.fragmentTotal).roundToLong()
            .takeIf { it > 0 } ?: return null
        val read = if (stated) media.size else lengths.drop(starts.size).count { it != null }
        return Size(
            bytes = bytes,
            lowBytes = (bytes * (1 - margin)).roundToLong().coerceIn(1, bytes),
            highBytes = (bytes * (1 + margin)).roundToLong().coerceAtLeast(bytes),
            piecesRead = read.coerceIn(1, media.size),
            pieces = media.size,
        )
    }

    /** The playlist's pieces as its download reads them; null, with a note, when it cannot. */
    private suspend fun playlist(run: Run, file: File): List<HlsDownloadChunk>? {
        if (!run.take()) return null
        val result = try {
            http.execute(file.url, file.url, file.track.requestContext) {
                get()
                header("Accept", HLS_ACCEPT)
            }
        } catch (error: IOException) {
            return run.note("playlist network")
        }
        val response = when (result) {
            is SecureDownloadHttp.Result.Failed -> return run.note("playlist ${result.reason.name}")
            is SecureDownloadHttp.Result.Completed -> result.response
        }
        val text = response.use {
            if (response.code != 200) {
                if (response.code == 403 || response.code == 429) run.stop("HTTP ${response.code}")
                return run.note("playlist HTTP ${response.code}")
            }
            val source = response.body?.source() ?: return run.note("playlist empty")
            val buffer = Buffer()
            val limit = policy.maxPlaylistBytes + 1L
            try {
                while (buffer.size < limit && source.read(buffer, limit - buffer.size) != -1L) Unit
            } catch (error: IOException) {
                return run.note("playlist network")
            } finally {
                run.bytes.addAndGet(buffer.size)
            }
            if (buffer.size > policy.maxPlaylistBytes) return run.note("playlist too long")
            buffer.readUtf8()
        }
        return when (
            val parsed = HlsDownloadManifestParser.parse(text, result.finalUrl, policy.maxChunks)
        ) {
            is HlsDownloadManifestParser.Result.Parsed -> parsed.chunks
            HlsDownloadManifestParser.Result.DrmProtected -> run.note("locked playlist")
            HlsDownloadManifestParser.Result.Unsupported -> run.note("not a finished playlist")
            HlsDownloadManifestParser.Result.TooManyChunks -> run.note("too many pieces")
            HlsDownloadManifestParser.Result.Malformed -> run.note("playlist unreadable")
        }
    }

    /** A piece's length from the `Content-Range` total of its first byte; null when unread. */
    private suspend fun length(run: Run, file: File, chunk: HlsDownloadChunk): Long? {
        if (!run.take()) return null
        val result = try {
            http.execute(file.url, chunk.url, file.track.requestContext) {
                get()
                header("Range", "bytes=0-0")
            }
        } catch (error: IOException) {
            return null
        }
        val response = when (result) {
            is SecureDownloadHttp.Result.Failed -> return null
            is SecureDownloadHttp.Result.Completed -> result.response
        }
        return response.use {
            when (response.code) {
                206 -> Unit
                200 -> return run.stop("range ignored")
                403, 429 -> return run.stop("HTTP ${response.code}")
                else -> return null
            }
            val total = response.header("Content-Range")?.trim()
                ?.let(FIRST_BYTE::matchEntire)?.groupValues?.get(1)?.toLongOrNull()
            val source = response.body?.source() ?: return null
            val buffer = Buffer()
            try {
                while (buffer.size < 2 && source.read(buffer, 2 - buffer.size) != -1L) Unit
            } catch (error: IOException) {
                return null
            } finally {
                run.bytes.addAndGet(buffer.size)
            }
            if (buffer.size > 1) return run.stop("range ignored")
            total?.takeIf { it > 0 }
        }
    }

    private class File(val track: Track, val url: HttpUrl)

    /** What the sample may still ask, what it cost, and why a file got no size. */
    private class Run(private val policy: Policy) {
        val asks = AtomicInteger()
        val bytes = AtomicLong()
        val note = AtomicReference<String?>()
        private val stopped = AtomicReference<String?>()

        fun take(): Boolean {
            if (stopped.get() != null) return false
            if (bytes.get() >= policy.maxBytes) {
                stop("over ${policy.maxBytes / 1_024} KiB")
                return false
            }
            if (asks.incrementAndGet() > policy.maxAsks) {
                asks.decrementAndGet()
                stop("over ${policy.maxAsks} asks")
                return false
            }
            return true
        }

        /** Stops every file's sample; always null, for the caller's return. */
        fun stop(why: String): Nothing? {
            stopped.compareAndSet(null, why)
            note.compareAndSet(null, why)
            return null
        }

        /** Why this file got no size, the first one heard; always null, for the caller's return. */
        fun note(why: String): Nothing? {
            note.compareAndSet(null, why)
            return null
        }
    }

    private companion object {
        /** Fewest pieces read for a size, when that many were asked. */
        const val MIN_READS = 3
        const val HLS_ACCEPT = "application/vnd.apple.mpegurl, application/x-mpegurl, */*;q=0.1"
        val FIRST_BYTE = Regex("""(?i)^bytes\s+0-0/(\d+)$""")
    }
}

private fun logFinishedSampleLine(message: String) {
    runCatching { android.util.Log.i("YftSizes", message) }
}
