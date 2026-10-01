package com.alal.yft.core.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentPlannerTest {
    @Test
    fun `balanced ranges cover file exactly once`() {
        val segments = SegmentPlanner.plan(
            totalBytes = 103,
            supportsByteRanges = true,
            preferredSegmentCount = 8,
        )

        assertEquals(8, segments.size)
        assertEquals(listOf(13L, 13L, 13L, 13L, 13L, 13L, 13L, 12L), segments.map { it.lengthBytes })
        assertEquals(0L, segments.first().startByte)
        assertEquals(102L, segments.last().endByteInclusive)
        segments.zipWithNext().forEach { (left, right) ->
            assertEquals(left.endByteInclusive!! + 1, right.startByte)
        }
        assertEquals(103L, segments.sumOf { it.lengthBytes!! })
    }

    @Test
    fun `tiny files never create empty segments`() {
        val segments = SegmentPlanner.plan(
            totalBytes = 3,
            supportsByteRanges = true,
            preferredSegmentCount = 8,
        )

        assertEquals(3, segments.size)
        assertTrue(segments.all { it.lengthBytes == 1L })
    }

    @Test
    fun `zero byte file has no transfer segments`() {
        assertTrue(SegmentPlanner.plan(0, true, 4).isEmpty())
    }

    @Test
    fun `unknown size creates one open ended segment`() {
        val segment = SegmentPlanner.plan(null, true, 4).single()

        assertEquals(0L, segment.startByte)
        assertNull(segment.endByteInclusive)
    }

    @Test
    fun `server without range support creates one segment`() {
        val segment = SegmentPlanner.plan(1_024, false, 8).single()

        assertEquals(0L, segment.startByte)
        assertEquals(1_023L, segment.endByteInclusive)
    }

    @Test
    fun `long max file planning does not overflow`() {
        val segments = SegmentPlanner.plan(Long.MAX_VALUE, true, 32)

        assertEquals(32, segments.size)
        assertEquals(Long.MAX_VALUE - 1, segments.last().endByteInclusive)
        assertEquals(Long.MAX_VALUE, segments.sumOf { it.lengthBytes!! })
    }

    @Test
    fun `invalid inputs fail fast`() {
        assertThrows(IllegalArgumentException::class.java) {
            SegmentPlanner.plan(-1, true, 4)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SegmentPlanner.plan(100, true, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SegmentPlanner.plan(100, true, 33)
        }
    }
}