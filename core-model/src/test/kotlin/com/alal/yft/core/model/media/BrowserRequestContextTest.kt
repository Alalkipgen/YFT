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

    @Test
    fun aPagesLinkOnAnotherOriginIsAskedWithThePagesOriginAsOriginAndReferer() {
        // P45: a link the page's markup or script names, before any request of it was seen.
        val context = BrowserRequestContext.pageLink(
            pageUrl = "https://www.tube.example.test/view_video?key=1",
            mediaUrl = "https://cdn.example.test/v/1/master.m3u8?token=t",
            userAgent = "Mozilla/5.0 (Linux; Android 14) Chrome/130",
        )

        val headers = context.replayHeaders()

        assertEquals("https://www.tube.example.test", headers["Origin"])
        assertEquals("https://www.tube.example.test/", headers["Referer"])
        assertEquals("Mozilla/5.0 (Linux; Android 14) Chrome/130", headers["User-Agent"])
        assertFalse(headers.containsKey("Cookie"))
    }

    @Test
    fun aPagesLinkOnItsOwnOriginKeepsTheWholePageAsReferer() {
        val context = BrowserRequestContext.pageLink(
            pageUrl = "https://tube.example.test/watch/5",
            mediaUrl = "https://TUBE.example.test:443/files/5.mp4",
        )

        val headers = context.replayHeaders()

        assertEquals("https://tube.example.test/watch/5", headers["Referer"])
        assertFalse(headers.containsKey("Origin"))
        // No agent of its own: the request's own default stays.
        assertFalse(headers.containsKey("User-Agent"))
        assertEquals(
            "Agent",
            context.withUserAgent("Agent").replayHeaders()["User-Agent"],
        )
    }

    @Test
    fun whatTheBrowserSentStandsAndTheContextOnlyFillsWhatIsMissing() {
        val context = BrowserRequestContext(
            pageUrl = "https://tube.example.test/watch/5",
            userAgent = null,
            cookie = null,
            observedHeaders = mapOf(
                "referer" to "https://tube.example.test/",
                "User-Agent" to "Observed agent",
            ),
        )

        val headers = context.replayHeaders()

        assertEquals("https://tube.example.test/", headers["referer"])
        assertFalse(headers.containsKey("Referer"))
        assertEquals("Observed agent", headers["User-Agent"])
        assertEquals(context, context.withUserAgent(null))
        assertEquals("Own", context.copy(userAgent = "Own").withUserAgent("Other").userAgent)
    }
}
