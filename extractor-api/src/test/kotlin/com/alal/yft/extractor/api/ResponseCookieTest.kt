package com.alal.yft.extractor.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseCookieTest {
    @Test
    fun domainCookiesMatchSubdomainsAndHostOnlyCookiesOnlyTheirHost() {
        val domain = ResponseCookie("ttwid", "fixture-value", "tiktok.com", hostOnly = false)
        val hostOnly = ResponseCookie("csrf", "fixture-value", "www.tiktok.com", hostOnly = true)

        assertTrue(domain.matches("https://tiktok.com"))
        assertTrue(domain.matches("https://v16-webapp-prime.us.tiktok.com/video/a.mp4?x=1"))
        assertTrue(domain.matches("https://V16.TikTok.com./video"))
        assertFalse(domain.matches("https://nottiktok.com/video"))
        assertFalse(domain.matches("https://tiktok.com.other.test/video"))
        assertFalse(domain.matches("http://www.tiktok.com/video"))
        assertFalse(domain.matches("https://user@www.tiktok.com/video"))
        assertFalse(domain.matches("not a url"))

        assertTrue(hostOnly.matches("https://www.tiktok.com/@fixture/video/1"))
        assertFalse(hostOnly.matches("https://v16.www.tiktok.com/video"))
        assertFalse(hostOnly.matches("https://tiktok.com/video"))
    }

    @Test
    fun pathsMatchOnlyOnSegmentBoundaries() {
        val cookie = ResponseCookie("a", "b", "tiktok.com", hostOnly = false, path = "/video")
        val directory = ResponseCookie("a", "b", "tiktok.com", hostOnly = false, path = "/video/")

        assertTrue(cookie.matches("https://www.tiktok.com/video"))
        assertTrue(cookie.matches("https://www.tiktok.com/video/tos/a.mp4"))
        assertFalse(cookie.matches("https://www.tiktok.com/videos/a.mp4"))
        assertFalse(cookie.matches("https://www.tiktok.com/"))
        assertTrue(directory.matches("https://www.tiktok.com/video/a.mp4"))
    }

    @Test
    fun printedCookiesAndResultsNeverShowValues() {
        val cookie = ResponseCookie("tt_chain_token", "secret-fixture", "tiktok.com", false)
        val success = ExtractorHttpResult.Success(
            200, "<html></html>", "https://www.tiktok.com/", cookies = listOf(cookie),
        )

        assertEquals("tt_chain_token=secret-fixture", cookie.pair)
        listOf(cookie.toString(), success.toString(), listOf(cookie).toString()).forEach { text ->
            assertFalse(text, text.contains("secret-fixture"))
            assertTrue(text, text.contains("tt_chain_token"))
        }
    }
}