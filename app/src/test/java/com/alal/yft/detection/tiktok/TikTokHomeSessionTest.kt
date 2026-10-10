package com.alal.yft.detection.tiktok

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** P40 (G3): Home's TikTok lookups carry the browser's TikTok cookies, to TikTok only. */
class TikTokHomeSessionTest {
    private val cookies = object : TikTokCookies {
        val asked = mutableListOf<String>()

        override fun header(url: String): String? {
            asked += url
            return "ttwid=browser"
        }

        override fun clear(names: Set<String>) = Unit
    }

    @Test
    fun `TikTok links carry the browser's TikTok cookies`() {
        val links = listOf(
            "https://www.tiktok.com/@scout/video/7311234567890123456",
            "https://vt.tiktok.com/ZSabc123/",
            "https://m.tiktok.com/v/7311234567890123456.html",
            "https://TIKTOK.com./@scout",
        )

        links.forEach { link ->
            assertEquals(
                link,
                "ttwid=browser",
                TikTokHomeSession.cookieFor(link, TikTokPageSettings.OWNER, cookies),
            )
        }
        assertEquals(List(links.size) { TikTokPageScript.COOKIE_PAGE }, cookies.asked)
    }

    @Test
    fun `other sites never get them`() {
        val links = listOf(
            "https://www.youtube.com/watch?v=abc",
            "https://www.tiktok.com.example.com/@scout",
            "https://nottiktok.com/@scout",
            "https://example.com/?u=https://www.tiktok.com/",
            "not a link",
        )

        links.forEach { link ->
            assertNull(link, TikTokHomeSession.cookieFor(link, TikTokPageSettings.OWNER, cookies))
        }
        assertEquals(emptyList<String>(), cookies.asked)
    }

    @Test
    fun `TT_HOME_COOKIES=OFF keeps Home cookie-free`() {
        val off = TikTokPageSettings(homeCookies = false)

        assertNull(TikTokHomeSession.cookieFor("https://www.tiktok.com/@scout", off, cookies))
        assertEquals(emptyList<String>(), cookies.asked)
    }
}
