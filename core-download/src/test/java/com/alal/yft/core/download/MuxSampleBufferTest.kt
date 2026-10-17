package com.alal.yft.core.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.4 item 1 — the merge's sample buffer grows to a sample larger than it (a 2K/4K VP9
 * keyframe in a WebM track, owner's phone 2026-10-16), never above 32 MiB, never below today's
 * size; a failure's Detail names the call that threw and the sizes.
 */
class MuxSampleBufferTest {
    private val kib = 1_024
    private val mib = 1_024 * 1_024

    @Test
    fun `a sample that fits keeps the buffer, never smaller than today`() {
        assertEquals(256 * kib, MuxSampleBuffer.capacityFor(1L, 256 * kib))
        assertEquals(256 * kib, MuxSampleBuffer.capacityFor(256L * kib, 256 * kib))
        assertEquals(256 * kib, MuxSampleBuffer.capacityFor(MuxTrace.UNKNOWN, 256 * kib))
        assertEquals(8 * mib, MuxSampleBuffer.capacityFor(300L * kib, 8 * mib))
    }

    @Test
    fun `a larger sample grows the buffer to fit it, at least twice, in 256 KiB steps`() {
        // The phone's case: a 2K VP9 keyframe over the 256 KiB a WebM track gets.
        assertEquals(512 * kib, MuxSampleBuffer.capacityFor(355_713L, 256 * kib))
        assertEquals(768 * kib, MuxSampleBuffer.capacityFor(700L * kib, 256 * kib))
        assertEquals(1 * mib, MuxSampleBuffer.capacityFor(600L * kib, 512 * kib))
        assertEquals(768 * kib, MuxSampleBuffer.capacityFor(310_000L, 300_000))
        for (size in listOf(256L * kib + 1, 1L * mib, 5L * mib + 3, 32L * mib - 1, 32L * mib)) {
            val grown = requireNotNull(MuxSampleBuffer.capacityFor(size, 256 * kib))
            assertTrue("$size", grown >= size)
            assertTrue("$size", grown <= MuxSampleBuffer.MAX_BYTES)
            assertEquals("$size", 0, grown % MuxSampleBuffer.STEP_BYTES)
        }
    }

    @Test
    fun `never above 32 MiB - a larger sample gets no buffer`() {
        assertEquals(32 * mib, MuxSampleBuffer.MAX_BYTES)
        assertEquals(32 * mib, MuxSampleBuffer.capacityFor(20L * mib, 16 * mib))
        assertEquals(32 * mib, MuxSampleBuffer.capacityFor(32L * mib, 256 * kib))
        assertNull(MuxSampleBuffer.capacityFor(32L * mib + 1, 256 * kib))
        assertNull(MuxSampleBuffer.capacityFor(Long.MAX_VALUE, 8 * mib))
    }

    @Test
    fun `the Detail names the read that threw and the sizes, within 84 characters after sanitize`() {
        val trace = MuxTrace()
        trace.at(MuxTrace.READ_VIDEO)
        trace.sample(355_713L, 256 * kib)
        val detail = trace.detail("IllegalArgumentException")
        assertEquals("IllegalArgumentException at readSampleData video: sample 348 KiB, buffer 256 KiB", detail)
        assertTrue(detail.length <= MuxTrace.DETAIL_CHARS)
    }

    @Test
    fun `a call without a sample names the call only`() {
        val trace = MuxTrace()
        trace.at(MuxTrace.ADD_AUDIO)
        assertEquals(
            "IllegalStateException: Failed to add the track at addTrack audio",
            trace.detail("IllegalStateException: Failed to add the track"),
        )
        trace.at(MuxTrace.STOP)
        assertEquals("IllegalStateException at stop", trace.detail("IllegalStateException"))
    }

    @Test
    fun `before Android 9 a read names the buffer only`() {
        val trace = MuxTrace()
        trace.at(MuxTrace.READ_AUDIO)
        trace.sample(MuxTrace.UNKNOWN, 256 * kib)
        assertEquals("IllegalArgumentException at readSampleData audio: buffer 256 KiB", trace.detail("IllegalArgumentException"))
    }

    @Test
    fun `a long message is cut first so the call and the sizes stay`() {
        val trace = MuxTrace()
        trace.at(MuxTrace.READ_VIDEO)
        trace.sample(355_713L, 256 * kib)
        val detail = trace.detail("IllegalArgumentException: " + "x".repeat(200))
        assertEquals("IllegalArgumentException: x\u2026 at readSampleData video: sample 348 KiB, buffer 256 KiB", detail)
        assertEquals(MuxTrace.DETAIL_CHARS, detail.length)
    }

    @Test
    fun `the log line keeps the largest sample per track and the growths`() {
        val trace = MuxTrace()
        trace.read(isVideo = true, bytes = 355_713)
        trace.read(isVideo = true, bytes = 1_000)
        trace.read(isVideo = false, bytes = 900)
        trace.grew(512 * kib)
        trace.at(MuxTrace.WRITE_AUDIO)
        val line = trace.logLine("IllegalStateException")
        assertTrue(line, line.startsWith("Merge failed at writeSampleData audio: IllegalStateException; sample 900 B, buffer 524288 B, "))
        assertTrue(line, line.endsWith("largest video 355713 B, largest audio 900 B, buffer grew 1 times"))
    }
}
