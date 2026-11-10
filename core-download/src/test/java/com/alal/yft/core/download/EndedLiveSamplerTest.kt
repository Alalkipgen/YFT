package com.alal.yft.core.download

import com.alal.yft.core.download.EndedLiveSizes.Track
import com.alal.yft.core.model.media.BrowserRequestContext
import java.io.ByteArrayOutputStream
import java.util.Collections
import kotlin.math.abs
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
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
 * Ended live size sample: a replay's files sized from the heads of a spread sample of their
 * pieces, built like the owner's replay (2026-11-08: one `ftyp`, `moov`, `emsg`, `moof` and
 * `mdat` per piece, `X-Head-Seqnum` on every answer, ranges honoured).
 */
class EndedLiveSamplerTest {
    private val server = MockWebServer()
    private val asked = Collections.synchronizedList(mutableListOf<RecordedRequest>())
    private val lines = mutableListOf<String>()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun everyFileIsSizedFromTheHeadsOfAFewOfItsPieces() = runTest {
        val replay = Replay(pieces = 300)
        server.dispatcher = replay.dispatcher()
        val heard = mutableMapOf<String, EndedLiveSizes.Size>()

        val outcome = sampler().estimate(replay.tracks()) { url, size -> heard[url] = size }

        assertEquals(3, outcome.sizes.size)
        assertEquals(outcome.sizes, heard)
        replay.files.forEach { file ->
            val size = outcome.sizes.getValue(replay.url(file))
            val truth = replay.joined(file)
            val error = abs(size.bytes - truth).toDouble() / truth
            assertTrue("${file.itag} is off by $error", error < 0.05)
            assertTrue(size.lowBytes <= truth && truth <= size.highBytes)
            assertEquals(300, size.pieces)
        }
        // The sound's pieces weigh the same: its size is exact, without a range.
        val sound = outcome.sizes.getValue(replay.url(replay.files.last()))
        assertEquals(replay.joined(replay.files.last()), sound.bytes)
        assertEquals(sound.bytes, sound.lowBytes)
        // The opening piece, 64 of the light video, 24 of the heavy one and 4 of the sound.
        assertEquals(1 + 64 + 24 + 4, outcome.asks)
        assertEquals(outcome.asks, asked.size)
        assertTrue(asked.all { it.getHeader("Range") == "bytes=0-8191" })
        assertTrue(outcome.bytesRead <= outcome.asks * 8_192L)
        // The newest two pieces are never asked: the server may not serve them yet.
        assertTrue(asked.all { it.requestUrl!!.queryParameter("sq")!!.toInt() < 298 })
        assertNull(outcome.note)
        assertTrue(lines.single().startsWith("ended live sizes: 3 of 3 files, 93 asks"))
        assertTrue(lines.single().none { it == '?' })
    }

    @Test
    fun aServerThatIgnoresTheRangeStopsTheSample() = runTest {
        val replay = Replay(pieces = 300, honoursRange = false)
        server.dispatcher = replay.dispatcher()

        val outcome = sampler().estimate(replay.tracks())

        assertTrue(outcome.sizes.isEmpty())
        assertEquals("range ignored", outcome.note)
        // A whole piece shorter than the range is still read; the first longer one stops it,
        // with only the asks already under way after it (of the 93).
        assertTrue("${outcome.asks} asks", outcome.asks <= 1 + 2 * 6)
    }

    @Test
    fun aRefusalStopsTheSampleAndKeepsWhatWasSized() = runTest {
        val replay = Replay(pieces = 300, refuseItag = 136)
        server.dispatcher = replay.dispatcher()

        val outcome = sampler().estimate(replay.tracks())

        assertEquals("HTTP 403", outcome.note)
        // The light video and the sound were sized before the heavy one was asked.
        assertEquals(setOf(160, 140), outcome.sizes.keys.map { replay.itagOf(it) }.toSet())
    }

    @Test
    fun aPieceWhoseSamplesDoNotFillItsDataIsNoSize() = runTest {
        val replay = Replay(pieces = 300, brokenSamples = true)
        server.dispatcher = replay.dispatcher()

        val outcome = sampler().estimate(replay.tracks())

        assertTrue(outcome.sizes.isEmpty())
        assertEquals("first piece layout", outcome.note)
    }

    @Test
    fun aContentRangeThatDisagreesIsNoSize() = runTest {
        val replay = Replay(pieces = 300, statedTotalOffset = 1)
        server.dispatcher = replay.dispatcher()

        assertTrue(sampler().estimate(replay.tracks()).sizes.isEmpty())
    }

    @Test
    fun theSampleStopsAtItsLimit() = runTest {
        val replay = Replay(pieces = 300)
        server.dispatcher = replay.dispatcher()

        val outcome = sampler(EndedLiveSampler.Policy(maxAsks = 20)).estimate(replay.tracks())

        assertEquals("over 20 asks", outcome.note)
        assertEquals(20, asked.size)
    }

    @Test
    fun aFileOfNoLiveBroadcastAndAWebmFileAreNeverAsked() = runTest {
        val webm = server.url("/v?itag=248&source=yt_live_broadcast").toString()
        val outcome = sampler().estimate(
            listOf(
                Track(server.url("/v?itag=18").toString(), context(), sound = false),
                // Its pieces are no MP4 boxes.
                Track(webm, context(), sound = false, mimeType = "video/webm; codecs=\"vp9\""),
            ),
        )
        assertTrue(outcome.sizes.isEmpty())
        assertEquals(0, server.requestCount)
        assertTrue(lines.isEmpty())
    }

    @Test
    fun aShortReplayIsReadWhole() = runTest {
        val replay = Replay(pieces = 12)
        server.dispatcher = replay.dispatcher()

        val outcome = sampler().estimate(replay.tracks())

        // 10 pieces can be read; the newest two are counted at the mean of the others.
        val light = outcome.sizes.getValue(replay.url(replay.files.first()))
        assertEquals(10, light.piecesRead)
        assertEquals(12, light.pieces)
    }

    @Test
    fun piecesAreSpreadOverTheReplay() {
        assertEquals(listOf(12, 37, 62, 87), PieceSample.positions(4, 100))
        assertEquals(listOf(0, 1, 2), PieceSample.positions(64, 3))
        assertEquals(listOf(37, 87), PieceSample.subset(2, listOf(12, 37, 62, 87)))
        assertTrue(PieceSample.positions(4, 0).isEmpty())
    }

    @Test
    fun aHeavyFileIsTheLightOnesTotalTimesWhatItsPiecesWeigh() {
        val base = PieceSample.Estimate(fragmentTotal = 1_000.0, relativeError = 0.0, pieces = 100)
        val steady = PieceSample.ratio(listOf(30L to 10L, 60L to 20L, 90L to 30L), base)!!
        assertEquals(3_000.0, steady.fragmentTotal, 0.001)
        assertEquals(0.0, steady.relativeError, 0.0001)
        assertNull(PieceSample.ratio(listOf(30L to 10L, 60L to 20L), base))
    }

    private fun sampler(policy: EndedLiveSampler.Policy = EndedLiveSampler.Policy()) =
        EndedLiveSampler(OkHttpClient(), policy, log = { lines += it })

    private fun context() = BrowserRequestContext("https://www.youtube.com/watch?v=x", null, null)

    private class File(
        val itag: Int,
        val sound: Boolean,
        val bitrate: Long,
        val samplesOf: (Int) -> List<Int>,
    )

    /** A replay of [pieces] pieces in three files: 144p, 720p (4 to 6 times 144p), sound. */
    private inner class Replay(
        val pieces: Int,
        val honoursRange: Boolean = true,
        val refuseItag: Int? = null,
        val brokenSamples: Boolean = false,
        val statedTotalOffset: Long = 0,
    ) {
        val files = listOf(
            File(160, sound = false, 212_465) { sq -> List(15) { 300 + (sq * 37 + it * 11) % 400 } },
            File(136, sound = false, 2_684_050) { sq ->
                List(30) { 900 + (sq * 37 + it * 11) % 400 * 3 }
            },
            File(140, sound = true, 144_000) { List(43) { 372 } },
        )

        fun url(file: File) =
            server.url("/videoplayback?source=yt_live_broadcast&itag=${file.itag}").toString()

        fun itagOf(url: String) = url.toHttpUrl().queryParameter("itag")!!.toInt()

        fun tracks() = files.map { file ->
            Track(url(file), context(), sound = file.sound, bitrateBitsPerSecond = file.bitrate)
        }

        fun joined(file: File): Long =
            HEADER + (0 until pieces).sumOf { fragment(file.samplesOf(it)).size.toLong() }

        fun dispatcher() = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                asked += request
                val url = request.requestUrl!!
                val itag = url.queryParameter("itag")!!.toInt()
                if (itag == refuseItag) return MockResponse().setResponseCode(403)
                val file = files.first { it.itag == itag }
                val sq = url.queryParameter("sq")!!.toInt()
                val body = piece(file.samplesOf(sq), brokenSamples)
                val response = MockResponse().addHeader("X-Head-Seqnum", pieces - 1)
                val range = request.getHeader("Range")?.removePrefix("bytes=0-")?.toIntOrNull()
                if (!honoursRange || range == null) return response.setBody(Buffer().write(body))
                val end = minOf(range, body.size - 1)
                return response.setResponseCode(206)
                    .addHeader("Content-Range", "bytes 0-$end/${body.size + statedTotalOffset}")
                    .setBody(Buffer().write(body, 0, end + 1))
            }
        }
    }

    private companion object {
        const val HEADER = 28L + 676L

        fun box(type: String, vararg parts: ByteArray): ByteArray {
            val body = ByteArrayOutputStream().apply { parts.forEach(::write) }.toByteArray()
            return int(8 + body.size) + type.toByteArray(Charsets.ISO_8859_1) + body
        }

        fun int(value: Int) = byteArrayOf(
            (value ushr 24).toByte(),
            (value ushr 16).toByte(),
            (value ushr 8).toByte(),
            value.toByte(),
        )

        /** `moof` (`mfhd`, `traf` with `tfhd` and a `trun` stating each size) and `mdat`. */
        fun fragment(samples: List<Int>, broken: Boolean = false): ByteArray {
            val tfhd = box("tfhd", int(0x2000a), int(1), int(1), int(3_000))
            val sizes = samples.flatMap { listOf(int(it), int(0)) }.toTypedArray()
            val trun = box("trun", int(0x601), int(samples.size), int(0), *sizes)
            val moof = box("moof", box("mfhd", int(0), int(1)), box("traf", tfhd, trun))
            val data = ByteArray(samples.sum() + if (broken) 1 else 0)
            return moof + box("mdat", data)
        }

        fun piece(samples: List<Int>, broken: Boolean = false): ByteArray =
            box("ftyp", ByteArray(20)) + box("moov", ByteArray(668)) + box("emsg", ByteArray(286)) +
                fragment(samples, broken)
    }
}
