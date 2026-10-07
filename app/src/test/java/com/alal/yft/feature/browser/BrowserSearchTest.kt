package com.alal.yft.feature.browser

import com.alal.yft.core.model.settings.SearchEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserSearchTest {
    @After
    fun backToTheDefaultEngine() {
        BrowserSearch.engine = SearchEngine.GOOGLE
    }

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
            "https://www.google.com/search?q=caf%C3%A9+%231%3F",
            BrowserSearch.webUrl(" café #1? "),
        )
    }

    @Test
    fun theWebSearchIsGoogleWithEveryCharacterEncoded() {
        assertEquals(
            "https://www.google.com/search?q=cats+%26+dogs+at+50%25",
            BrowserSearch.webUrl("  cats & dogs at 50% "),
        )
        // "Burmese songs" in Burmese: each UTF-8 byte is percent-encoded.
        val burmese = "%E1%80%99%E1%80%BC%E1%80%94%E1%80%BA%E1%80%99%E1%80%AC+" +
            "%E1%80%9E%E1%80%AE%E1%80%81%E1%80%BB%E1%80%84%E1%80%BA%E1%80%B8"
        assertEquals(
            "https://www.google.com/search?q=$burmese",
            BrowserSearch.webUrl("မြန်မာ သီချင်း"),
        )
        assertEquals(
            "https://www.google.com/search?q=a%3Db%26c%3Dd%2Fe%3Ff",
            BrowserSearch.webUrl("a=b&c=d/e?f"),
        )
    }

    @Test
    fun duckDuckGoAndBingSearchTheSameWords() {
        assertEquals(
            "https://duckduckgo.com/?q=cats+%26+dogs",
            BrowserSearch.webUrl("cats & dogs", SearchEngine.DUCKDUCKGO),
        )
        assertEquals(
            "https://www.bing.com/search?q=cats+%26+dogs",
            BrowserSearch.webUrl("cats & dogs", SearchEngine.BING),
        )
        assertEquals(
            "https://www.google.com/search?q=cats+%26+dogs",
            BrowserSearch.webUrl("cats & dogs", SearchEngine.GOOGLE),
        )
    }

    @Test
    fun theChosenEngineIsWhatTypedWordsSearch() {
        BrowserSearch.engine = SearchEngine.BING
        assertEquals("https://www.bing.com/search?q=lofi", BrowserSearch.webUrl("lofi"))

        BrowserSearch.engine = SearchEngine.DUCKDUCKGO
        assertEquals("https://duckduckgo.com/?q=lofi", BrowserSearch.webUrl("lofi"))
    }
}
