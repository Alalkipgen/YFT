package com.alal.yft.core.model.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserRequestContextTest {
    @Test
    fun replayHeadersPreserveRequiredContextAndStripTransportHeaders() {
        val context = BrowserRequestContext(
            pageUrl = "https://example.test/watch",
            userAgent = "YFT-Test",
            cookie = "session=fake-cookie",
            observedHeaders = mapOf(
                "Accept" to "video/*",
                "Range" to "bytes=0-99",
                "Connection" to "keep-alive",
                "X-Site-Header" to "fixture",
            ),
        )

        val headers = context.replayHeaders()

        assertEquals("video/*", headers["Accept"])
        assertEquals("YFT-Test", headers["User-Agent"])
        assertEquals("session=fake-cookie", headers["Cookie"])
        assertEquals("https://example.test/watch", headers["Referer"])
        assertEquals("fixture", headers["X-Site-Header"])
        assertFalse(headers.keys.any { it.equals("Range", true) || it.equals("Connection", true) })
    }

    @Test
    fun toStringNeverContainsCookieValueOrSignedQueryValue() {
        val context = BrowserRequestContext(
            pageUrl = "https://example.test/watch?token=fake-page-token",
            userAgent = "YFT-Test",
            cookie = "session=fake-cookie",
        )

        val rendered = context.toString()

        assertFalse(rendered.contains("fake-page-token"))
        assertFalse(rendered.contains("fake-cookie"))
        assertTrue(rendered.contains("[REDACTED]"))
    }

    @Test
    fun doesNotReplayInsecureReferer() {
        val headers = BrowserRequestContext("http://example.test", null, null).replayHeaders()

        assertFalse(headers.keys.any { it.equals("Referer", true) })
    }
}
