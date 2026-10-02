package com.alal.yft.detection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteScopeTest {
    @Test
    fun `hosts of one site share a session scope`() {
        listOf(
            "www.youtube.com" to "m.youtube.com",
            "www.youtube.com" to "consent.youtube.com",
            "vm.tiktok.com" to "www.tiktok.com",
            "WWW.Vimeo.com." to "player.vimeo.com",
            "news.bbc.co.uk" to "www.bbc.co.uk",
            "203.0.113.7" to "203.0.113.7",
        ).forEach { (first, second) ->
            assertTrue("$first -> $second", SiteScope.sameSite(first, second))
        }
    }

    @Test
    fun `a different site never receives the session`() {
        listOf(
            "www.youtube.com" to "www.google.com",
            "www.youtube.com" to "youtube.com.example.test",
            "fb.watch" to "www.facebook.com",
            "one.co.uk" to "two.co.uk",
            "203.0.113.7" to "203.0.113.8",
            "localhost" to "localhost.example.test",
        ).forEach { (first, second) ->
            assertFalse("$first -> $second", SiteScope.sameSite(first, second))
        }
    }

    @Test
    fun `only cookies and authorization count as credentials`() {
        assertTrue(SiteScope.isCredentialHeader("Cookie"))
        assertTrue(SiteScope.isCredentialHeader("authorization"))
        assertFalse(SiteScope.isCredentialHeader("User-Agent"))
        assertFalse(SiteScope.isCredentialHeader("Referer"))
    }
}
