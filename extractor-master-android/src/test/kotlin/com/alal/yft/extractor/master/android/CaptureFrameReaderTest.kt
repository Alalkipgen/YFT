package com.alal.yft.extractor.master.android

import org.junit.Assert.*
import org.junit.Test

class CaptureFrameReaderTest {
    private val raw = """{"pageUrl":"https://page.test/","generation":1,"requests":[]}"""

    @Test
    fun acceptsRawAndWebViewEncodedJson() {
        assertNotNull(CaptureFrameReader.read(raw))
        val encoded = "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        assertNotNull(CaptureFrameReader.read(encoded))
    }

    @Test
    fun nullMalformedAndTrailingJsonAreRejected() {
        for (text in listOf(null, "null", "{", "$raw extra", "[]", "\"not JSON\"")) {
            assertNull(CaptureFrameReader.read(text))
        }
    }

    @Test
    fun deeplyNestedAndOversizedDataAreRejectedWithoutPartialFrame() {
        val nested = "[".repeat(30) + raw + "]".repeat(30)
        assertNull(CaptureFrameReader.read(nested))
        assertNull(CaptureFrameReader.read(" ".repeat(CaptureFrameReader.MAX_CHARS + 1) + raw))
    }

    @Test
    fun missingGenerationAndUnboundedAddressAreRejected() {
        assertNull(CaptureFrameReader.read("""{"pageUrl":"https://page.test/"}"""))
        val huge = "https://page.test/" + "a".repeat(CaptureFrameReader.MAX_URL_CHARS)
        assertNull(CaptureFrameReader.read("""{"pageUrl":"$huge","generation":1}"""))
    }

    @Test
    fun capsRequestsAndPayloads() {
        val request = """{"url":"https://cdn.test/clip.mp4","mime":"video/mp4"}"""
        val requests = List(80) { request }.joinToString(",")
        val payloads = List(20) { "\"{}\"" }.joinToString(",")
        val frame = CaptureFrameReader.read(
            """{"pageUrl":"https://page.test/","generation":1,"requests":[$requests],""" +
                """"payloads":[$payloads]}""",
        )!!
        assertEquals(64, frame.requests.size)
        assertEquals(16, frame.payloads.size)
    }

    @Test
    fun absentOrInvalidPlayerEvidenceNeverClaimsPlaying() {
        val frame = CaptureFrameReader.read(
            """{"pageUrl":"https://page.test/","generation":1,"player":{"key":"video:0"}}""",
        )!!
        assertNull(frame.player)
    }
}