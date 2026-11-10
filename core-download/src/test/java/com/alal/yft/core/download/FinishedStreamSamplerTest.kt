package com.alal.yft.core.download

import com.alal.yft.core.download.EndedLiveSizes.Size
import com.alal.yft.core.download.EndedLiveSizes.Track
import com.alal.yft.core.model.media.BrowserRequestContext
import java.util.Collections
import kotlin.math.abs
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Finished stream size sample: a finished HLS playlist's files sized from the lengths of a spread
 * sample of their pieces, built like the owner's Roshy and jav.guru playlists (2026-11-08: a
 * master with `BANDWIDTH` only, finished quality playlists of MPEG-TS pieces, ranges honoured).
 */
class FinishedStreamSamplerTest {
    private val server = MockWebServer()
    private val asked = Collections.synchronizedList(mutableListOf<RecordedRequest>())
    private val lines = mutableListOf<String>()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun aFinishedPlaylistIsSizedFromTheLengthsOfASpreadOfItsPieces() = runTest {
        val stream = Stream(pieces = 400)
        server.dispatcher = stream.dispatcher()
        val heard = mutableMapOf<String, Size>()

        val outcome = sampler().estimate(stream.tracks()) { url, size -> heard[url] = size }

        assertEquals(2, outcome.sizes.size)
        assertEquals(outcome.sizes, heard)
        stream.qualities.forEach { quality ->
            val size = outcome.sizes.getValue(stream.playlist(quality))
            val truth = stream.total(quality)
            val error = abs(size.bytes - truth).toDouble() / truth
            assertTrue("$quality is off by $error", error < 0.03)
            assertTrue(size.lowBytes <= truth && truth <= size.highBytes)
            assertEquals(400, size.pieces)
            assertEquals(48, size.piecesRead)
        }
        // Each playlist once, then 48 of its pieces, one byte each.
        assertEquals(2 + 2 * 48, outcome.asks)
        assertEquals(outcome.asks, asked.size)
        val pieces = asked.filter { "/piece/" in it.path.orEmpty() }
        assertEquals(96, pieces.size)
        assertTrue(pieces.all { it.getHeader("Range") == "bytes=0-0" })
        assertTrue(outcome.bytesRead <= stream.playlistBytes() + pieces.size)
        assertNull(outcome.note)
        assertTrue(lines.single().startsWith("finished stream sizes: 2 of 2 files, 98 asks"))
        assertTrue(lines.single().none { it == '?' })
    }

    @Test
    fun aPlaylistWithNoEndIsNotSized() = runTest {
        val stream = Stream(pieces = 400, ended = false)
        server.dispatcher = stream.dispatcher()

        val outcome = sampler().estimate(stream.tracks())

        assertTrue(outcome.sizes.isEmpty())
        // A live window is not a finished recording: no piece is asked.
        assertEquals(2, outcome.asks)
        assertEquals("not a finished playlist", outcome.note)
    }

    @Test
    fun aServerThatIgnoresTheRangeStopsTheSample() = runTest {
        val stream = Stream(pieces = 400, honoursRange = false)
        server.dispatcher = stream.dispatcher()

        val outcome = sampler().estimate(stream.tracks())

        assertTrue(outcome.sizes.isEmpty())
        assertEquals("range ignored", outcome.note)
        // Only the asks already under way when the first whole piece came.
        assertTrue("${outcome.asks} asks", outcome.asks <= 2 + 6)
    }

    @Test
    fun aRefusalStopsTheSample() = runTest {
        val stream = Stream(pieces = 400, refusePieces = true)
        server.dispatcher = stream.dispatcher()

        val outcome = sampler().estimate(stream.tracks())

        assertTrue(outcome.sizes.isEmpty())
        assertEquals("HTTP 403", outcome.note)
        assertTrue("${outcome.asks} asks", outcome.asks <= 2 + 6)
    }

    @Test
    fun aFileWithFewPiecesReadIsNoSize() = runTest {
        val stream = Stream(pieces = 400, missingFrom = 100)
        server.dispatcher = stream.dispatcher()

        val outcome = sampler().estimate(stream.tracks())

        // Three in four of the asked pieces are missing: what was read is not spread enough.
        assertTrue(outcome.sizes.isEmpty())
        assertEquals("too few pieces read", outcome.note)
    }

    @Test
    fun statedByteRangesAreAddedUpWithoutAsking() = runTest {
        val lengths = List(120) { 9_000L + it * 13 % 700 }
        val body = buildString {
            append("#EXTM3U\n#EXT-X-VERSION:4\n#EXT-X-TARGETDURATION:6\n")
            append("#EXT-X-MAP:URI=\"all.mp4\",BYTERANGE=\"800@0\"\n")
            var offset = 800L
            lengths.forEach { length ->
                append("#EXTINF:6.0,\n#EXT-X-BYTERANGE:$length@$offset\nall.mp4\n")
                offset += length
            }
            append("#EXT-X-ENDLIST\n")
        }
        server.dispatcher = playlistOnly(body)
        val url = server.url("/v/index.m3u8?token=t").toString()

        val outcome = sampler().estimate(listOf(Track(url, context(), sound = false)))

        val size = outcome.sizes.getValue(url)
        assertEquals(800 + lengths.sum(), size.bytes)
        assertEquals(size.bytes, size.lowBytes)
        assertEquals(size.bytes, size.highBytes)
        assertEquals(120, size.piecesRead)
        assertEquals(1, outcome.asks)
    }

    @Test
    fun theStartPieceIsCountedOnce() = runTest {
        val stream = Stream(pieces = 400, startPiece = 1_500)
        server.dispatcher = stream.dispatcher()
        val tracks = stream.tracks().take(1)

        val outcome = sampler().estimate(tracks)

        val size = outcome.sizes.getValue(tracks.single().url)
        val truth = stream.total(stream.qualities.first())
        assertTrue(abs(size.bytes - truth).toDouble() / truth < 0.03)
        // The playlist, its start piece and 48 pieces.
        assertEquals(1 + 1 + 48, outcome.asks)
    }

    @Test
    fun aMasterOrALockedPlaylistIsNotSized() = runTest {
        server.dispatcher = playlistOnly(
            "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=786895,RESOLUTION=852x480\nindex-f1.m3u8\n",
        )
        val master = sampler().estimate(listOf(Track(url("/m.m3u8"), context(), sound = false)))
        assertTrue(master.sizes.isEmpty())
        assertEquals("not a finished playlist", master.note)

        server.dispatcher = playlistOnly(
            "#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXT-X-KEY:METHOD=AES-128,URI=\"k\"\n" +
                "#EXTINF:4.0,\na.ts\n#EXT-X-ENDLIST\n",
        )
        val locked = sampler().estimate(listOf(Track(url("/k.m3u8"), context(), sound = false)))
        assertTrue(locked.sizes.isEmpty())
        assertEquals("locked playlist", locked.note)
    }

    @Test
    fun theSampleStopsAtItsLimit() = runTest {
        val stream = Stream(pieces = 400)
        server.dispatcher = stream.dispatcher()

        val outcome = sampler(FinishedStreamSampler.Policy(maxAsks = 20)).estimate(stream.tracks())

        assertEquals("over 20 asks", outcome.note)
        assertEquals(20, asked.size)
        assertTrue(outcome.sizes.isEmpty())
    }

    @Test
    fun aShortPlaylistIsReadWhole() = runTest {
        val stream = Stream(pieces = 10)
        server.dispatcher = stream.dispatcher()

        val outcome = sampler().estimate(stream.tracks())

        stream.qualities.forEach { quality ->
            val size = outcome.sizes.getValue(stream.playlist(quality))
            assertEquals(stream.total(quality), size.bytes)
            assertEquals(size.bytes, size.lowBytes)
            assertEquals(10, size.piecesRead)
        }
    }

    private fun sampler(policy: FinishedStreamSampler.Policy = FinishedStreamSampler.Policy()) =
        FinishedStreamSampler(OkHttpClient(), policy, log = { lines += it })

    private fun context() = BrowserRequestContext("https://player.example/v/1", null, null)

    private fun url(path: String) = server.url(path).toString()

    private fun playlistOnly(body: String) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            asked += request
            if (".m3u8" !in request.path.orEmpty()) return MockResponse().setResponseCode(404)
            return MockResponse().setHeader("Content-Type", "application/vnd.apple.mpegurl")
                .setBody(body)
        }
    }

    /** A finished stream of [pieces] MPEG-TS pieces in two qualities, 480p and 1080p. */
    private inner class Stream(
        val pieces: Int,
        val ended: Boolean = true,
        val honoursRange: Boolean = true,
        val refusePieces: Boolean = false,
        /** Pieces from this one on answer 404 (one in four of a spread sample stay readable). */
        val missingFrom: Int? = null,
        /** A start piece (`EXT-X-MAP`) of this many bytes in each quality, when not null. */
        val startPiece: Int? = null,
    ) {
        val qualities = listOf(480, 1080)

        fun length(quality: Int, piece: Int): Long = when (quality) {
            480 -> 50_000L + piece * 7_919L % 30_000
            else -> 200_000L + piece * 104_729L % 60_000
        }

        fun total(quality: Int): Long =
            (startPiece ?: 0) + (0 until pieces).sumOf { length(quality, it) }

        fun playlist(quality: Int): String = url("/s/$quality/index.m3u8?token=abc")

        fun body(quality: Int): String = buildString {
            append("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:4\n")
            if (ended) append("#EXT-X-PLAYLIST-TYPE:VOD\n")
            startPiece?.let { append("#EXT-X-MAP:URI=\"init.mp4?token=abc\"\n") }
            repeat(pieces) { append("#EXTINF:4.000,\npiece/$it.ts?token=abc\n") }
            if (ended) append("#EXT-X-ENDLIST\n")
        }

        fun playlistBytes(): Long = qualities.sumOf { body(it).length.toLong() }

        fun tracks() = qualities.map { Track(playlist(it), context(), sound = false) }

        fun dispatcher() = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                asked += request
                val path = request.requestUrl!!.encodedPath
                val quality = path.split('/')[2].toInt()
                if (path.endsWith("index.m3u8")) {
                    return MockResponse()
                        .setHeader("Content-Type", "application/vnd.apple.mpegurl")
                        .setBody(body(quality))
                }
                val length = if (path.endsWith("init.mp4")) {
                    startPiece!!.toLong()
                } else {
                    val piece = path.substringAfterLast('/').removeSuffix(".ts").toInt()
                    if (refusePieces) return MockResponse().setResponseCode(403)
                    if (missingFrom != null && piece >= missingFrom) {
                        return MockResponse().setResponseCode(404)
                    }
                    length(quality, piece)
                }
                if (!honoursRange || request.getHeader("Range") != "bytes=0-0") {
                    return MockResponse().setBody(Buffer().write(ByteArray(length.toInt())))
                }
                return MockResponse().setResponseCode(206)
                    .setHeader("Content-Range", "bytes 0-0/$length")
                    .setBody(Buffer().writeByte(0x47))
            }
        }
    }
}
