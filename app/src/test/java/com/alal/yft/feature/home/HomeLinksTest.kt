package com.alal.yft.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeLinksTest {
    @Test
    fun pastesOnlyTheFirstWebLinkWithoutSentencePunctuation() {
        assertEquals(
            "https://example.com/v/1?t=30",
            HomeLinks.fromClipboard("Watch this: https://example.com/v/1?t=30. And this http://x"),
        )
        assertEquals(
            "https://example.com/a",
            HomeLinks.fromClipboard("(https://example.com/a)"),
        )
        assertEquals(
            "https://en.wikipedia.org/wiki/Film_(1999)",
            HomeLinks.fromClipboard("see https://en.wikipedia.org/wiki/Film_(1999))."),
        )
        assertEquals("HTTPS://EXAMPLE.COM/X", HomeLinks.fromClipboard("\"HTTPS://EXAMPLE.COM/X\""))
    }

    @Test
    fun textWithoutALinkKeepsItsFirstLineForTheBrowserToValidate() {
        assertEquals("example.com/video", HomeLinks.fromClipboard("  example.com/video \nnext"))
        assertNull(HomeLinks.fromClipboard(null))
        assertNull(HomeLinks.fromClipboard(" \n\t "))
    }

    @Test
    fun pastedLinksAreCappedAtTheBrowserLimit() {
        val long = "https://example.com/" + "a".repeat(5_000)

        assertEquals(HomeLinks.MAX_LENGTH, HomeLinks.fromClipboard(long)!!.length)
    }
}
