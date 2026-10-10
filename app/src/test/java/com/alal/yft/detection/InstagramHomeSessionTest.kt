package com.alal.yft.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstagramHomeSessionTest {
    private val asked = mutableListOf<String>()
    private val cookies: (String) -> String? = { url ->
        asked += url
        "csrftoken=fixture; sessionid=secret"
    }

    @Test
    fun `an Instagram link gets the browser's Instagram cookies`() {
        listOf(
            "https://www.instagram.com/reel/C9fixtREEL1/",
            "https://instagram.com/p/C9fixtREEL1/",
            "https://m.instagram.com/reel/C9fixtREEL1/",
        ).forEach { link ->
            assertEquals(
                link,
                "csrftoken=fixture; sessionid=secret",
                InstagramHomeSession.cookieFor(link, cookies),
            )
        }
        assertEquals(List(3) { InstagramHomeSession.PAGE }, asked)
    }

    @Test
    fun `other links and an empty store get none`() {
        listOf(
            "https://www.tiktok.com/@scout/video/1",
            "https://instagram.com.evil.test/reel/C9fixtREEL1/",
            "https://notinstagram.com/reel/C9fixtREEL1/",
        ).forEach { link -> assertNull(link, InstagramHomeSession.cookieFor(link, cookies)) }
        assertEquals(emptyList<String>(), asked)
        assertNull(InstagramHomeSession.cookieFor("https://www.instagram.com/p/x/") { "" })
        assertNull(InstagramHomeSession.cookieFor("https://www.instagram.com/p/x/") { null })
    }
}
