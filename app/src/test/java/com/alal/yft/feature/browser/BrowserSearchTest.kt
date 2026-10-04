package com.alal.yft.feature.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserSearchTest {
    @Test
    fun wordsSearchWhileAddressesAndEmptyInputDoNot() {
        assertEquals("cat videos", BrowserSearch.wordsOrNull("  cat videos "))
        assertEquals("lofi", BrowserSearch.wordsOrNull("lofi"))
        assertEquals("example.", BrowserSearch.wordsOrNull("example."))
        assertNull(BrowserSearch.wordsOrNull("example.test/watch"))
        assertNull(BrowserSearch.wordsOrNull("m.youtube.com?v=1"))
        assertNull(BrowserSearch.wordsOrNull("https://m.youtube.com/watch?v=1"))
        assertNull(BrowserSearch.wordsOrNull("http://example.test"))
        assertNull(BrowserSearch.wordsOrNull("about:blank"))
        assertNull(BrowserSearch.wordsOrNull("   "))
    }

    @Test
    fun searchAddressesEncodeTheWords() {
        assertEquals(
            "https://m.youtube.com/results?search_query=cats+%26+dogs",
            BrowserSearch.youTubeUrl("cats & dogs"),
        )
        assertEquals(
            "https://duckduckgo.com/?q=caf%C3%A9+%231%3F",
            BrowserSearch.webUrl(" café #1? "),
        )
    }
}
