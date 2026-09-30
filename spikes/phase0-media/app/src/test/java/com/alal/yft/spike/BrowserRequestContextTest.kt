package com.alal.yft.spike

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BrowserRequestContextTest {
    @Test fun buildsSafeReplayHeaders() {
        val headers = BrowserRequestContext(
            pageUrl = "https://example.com/watch",
            userAgent = "YFT-Test",
            cookie = "session=redacted-test-value",
            observedHeaders = mapOf("Accept" to "video/*", "Range" to "bytes=0-99", "Connection" to "keep-alive"),
        ).replayHeaders()

        assertEquals("video/*", headers["Accept"])
        assertEquals("YFT-Test", headers["User-Agent"])
        assertEquals("https://example.com/watch", headers["Referer"])
        assertFalse(headers.keys.any { it.equals("Range", true) || it.equals("Connection", true) })
    }

    @Test fun doesNotReplayHttpReferer() {
        val headers = BrowserRequestContext("http://example.com", null, null).replayHeaders()
        assertFalse(headers.keys.any { it.equals("Referer", true) })
    }
}
