package com.alal.yft.core.data.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserHistoryAddressTest {
    @Test
    fun `only HTTPS pages are kept and never the start page, about, data or plain HTTP`() {
        assertEquals(
            "https://example.com/watch",
            BrowserHistoryAddress.clean("https://example.com/watch"),
        )
        assertEquals("https://example.com/", BrowserHistoryAddress.clean("HTTPS://Example.COM"))
        assertNull(BrowserHistoryAddress.clean("about:blank"))
        assertNull(BrowserHistoryAddress.clean("data:text/html,<p>hi</p>"))
        assertNull(BrowserHistoryAddress.clean("http://example.com/"))
        assertNull(BrowserHistoryAddress.clean("file:///sdcard/page.html"))
        assertNull(BrowserHistoryAddress.clean("javascript:alert(1)"))
        assertNull(BrowserHistoryAddress.clean("blob:https://example.com/1234"))
        assertNull(BrowserHistoryAddress.clean("   "))
        assertNull(BrowserHistoryAddress.clean("https://"))
        assertNull(BrowserHistoryAddress.clean("https://example.com/" + "a".repeat(5_000)))
    }

    @Test
    fun `the fragment, a user name and tracking parameters are dropped`() {
        assertEquals(
            "https://m.youtube.com/watch?v=abc123&t=42",
            BrowserHistoryAddress.clean(
                "https://m.youtube.com/watch?v=abc123&utm_source=share&t=42&UTM_Medium=x#comments",
            ),
        )
        assertEquals(
            "https://www.facebook.com/reel/1545617074260365",
            BrowserHistoryAddress.clean("https://www.facebook.com/reel/1545617074260365?fbclid=1"),
        )
        assertEquals(
            "https://shop.example/item?id=7",
            BrowserHistoryAddress.clean(
                "https://shop.example/item?gclid=1&id=7&igshid=2&msclkid=3&dclid=4&yclid=5",
            ),
        )
        assertEquals(
            "https://example.com/page",
            BrowserHistoryAddress.clean("https://user:secret@example.com:443/page?fbclid=1"),
        )
        assertEquals(
            "https://example.com:8443/a%20b?q=caf%C3%A9",
            BrowserHistoryAddress.clean("https://example.com:8443/a%20b?q=caf%C3%A9#top"),
        )
        // A parameter that only looks like one keeps its place.
        assertEquals(
            "https://example.com/?utm=1&gclid_note=2",
            BrowserHistoryAddress.clean("https://example.com/?utm=1&gclid_note=2&utm_id=3"),
        )
    }

    @Test
    fun `the list shows the host without www`() {
        assertEquals("example.com", BrowserHistoryAddress.host("https://www.example.com/a"))
        assertEquals("m.youtube.com", BrowserHistoryAddress.host("https://m.youtube.com/"))
    }
}
