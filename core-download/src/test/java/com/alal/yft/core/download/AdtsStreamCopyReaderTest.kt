package com.alal.yft.core.download

import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Backlog item 5: an HLS sound joined as ADTS, read as a stream-copy track. */
class AdtsStreamCopyReaderTest {
    @Test
    fun `frames become samples without their headers, 1024 ticks each at the header's rate`() {
        val audio = Mp4TestFiles.audio(perFragment = 400)
        val track = AdtsTrackReader(BytesSource(Mp4TestFiles.adts(audio, crcEvery = 3))).read()

        assertEquals(StreamCopyTrackKind.AUDIO, track.kind)
        assertEquals(44_100L, track.timescale)
        assertEquals(audio.samples.size, track.count)
        var at = 0L
        audio.samples.forEachIndexed { index, sample ->
            val header = if (index % 3 == 0) 9 else 7
            assertEquals("offset $index", at + header, track.offsets[index])
            assertEquals("size $index", sample.bytes.size, track.sizes[index])
            assertEquals(1_024, track.durations[index])
            at += header + sample.bytes.size
        }
        assertEquals(null, track.nalLengthSize)
        assertEquals("mp4a", track.sampleEntryType)
        // The esds names AAC LC, 44.1 kHz, stereo.
        assertTrue(track.sampleDescription.containsRun(byteArrayOf(5, 2, 0x12, 0x10)))
    }

    @Test
    fun `what the ADTS stream copy does not expect is not supported`() {
        val audio = Mp4TestFiles.audio(fragments = 1, perFragment = 4)
        val good = Mp4TestFiles.adts(audio)
        val other = Mp4TestFiles.adts(Mp4TestFiles.audio(1, 1, timescale = 48_000))
        val cases = mapOf(
            "no ADTS sync" to ByteArray(8),
            "broken ADTS frame size" to good.copyOf(good.size - 1),
            "several AAC blocks in an ADTS frame" to good.copyOf().also {
                it[6] = (it[6].toInt() or 1).toByte()
            },
            "ADTS format changes" to good + other,
            "no samples" to ByteArray(0),
        )
        for ((reason, bytes) in cases) {
            val error = assertThrows(reason, StreamCopyUnsupported::class.java) {
                AdtsTrackReader(BytesSource(bytes)).read()
            }
            assertEquals(reason, error.message)
        }
        assertTrue(AdtsTrackReader.isAdts(BytesSource(good)))
        assertFalse(AdtsTrackReader.isAdts(BytesSource(Mp4TestFiles.fragmented(audio))))
    }

    private class BytesSource(private val bytes: ByteArray) : Mp4Source {
        override val size: Long = bytes.size.toLong()

        override fun readFully(target: ByteBuffer, position: Long) {
            require(position + target.remaining() <= size) { "past the end" }
            target.put(bytes, position.toInt(), target.remaining())
        }
    }

    private fun ByteArray.containsRun(run: ByteArray): Boolean =
        (0..size - run.size).any { start -> run.indices.all { this[start + it] == run[it] } }
}
