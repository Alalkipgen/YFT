package com.alal.yft.extractor.sites.vimeo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VimeoUrlsTest {
    @Test
    fun `clip, channel, group, showcase and embed urls canonicalize onto one identity`() {
        listOf(
            "https://vimeo.com/123456789",
            "https://vimeo.com/123456789?share=copy",
            "https://www.vimeo.com/123456789/",
            "https://vimeo.com/channels/staffpicks/123456789",
            "https://vimeo.com/groups/motion/videos/123456789",
            "https://vimeo.com/showcase/7654321/video/123456789",
            "https://vimeo.com/album/7654321/video/123456789",
            "https://player.vimeo.com/video/123456789",
        ).forEach { url ->
            val identity = VimeoUrls.identify(url)
            assertEquals(url, "vimeo", identity?.siteId)
            assertEquals(url, "123456789", identity?.contentId)
            assertEquals(url, "https://vimeo.com/123456789", identity?.canonicalPageUrl)
            assertFalse(url, identity!!.requiresCanonicalResolution)
        }
    }

    @Test
    fun `an unlisted clip keeps its privacy hash`() {
        listOf(
            "https://vimeo.com/123456789/abcdef1234",
            "https://player.vimeo.com/video/123456789?h=abcdef1234",
            "https://player.vimeo.com/video/123456789/abcdef1234",
        ).forEach { url ->
            assertEquals(
                url,
                "https://vimeo.com/123456789/abcdef1234",
                VimeoUrls.identify(url)?.canonicalPageUrl,
            )
        }
        assertEquals(
            "abcdef1234",
            VimeoUrls.unlistedHashOf("https://vimeo.com/123456789/abcdef1234"),
        )
        assertNull(VimeoUrls.unlistedHashOf("https://vimeo.com/123456789"))
    }

    @Test
    fun `paid, live and unrelated urls are not claimed`() {
        listOf(
            "http://vimeo.com/123456789",
            "https://vimeo.com/ondemand/fixture-film",
            "https://vimeo.com/ondemand/fixture-film/123456789",
            "https://vimeo.com/event/123456789",
            "https://vimeo.com/fixturestudio",
            "https://vimeo.com/categories/animation",
            "https://vimeo.com/",
            "https://player.vimeo.com/video/notanid",
            "https://vimeo.com.evil.example/123456789",
            "not a url",
        ).forEach { url -> assertNull(url, VimeoUrls.identify(url)) }
    }

    @Test
    fun `only vimeo's own player configuration address may be followed`() {
        assertTrue(VimeoUrls.isConfigUrl("https://player.vimeo.com/video/123456789/config"))
        assertTrue(
            VimeoUrls.isConfigUrl(
                "https://player.vimeo.com/video/123456789/config?h=abcdef1234&s=signature",
            ),
        )
        assertFalse(VimeoUrls.isConfigUrl("http://player.vimeo.com/video/123456789/config"))
        assertFalse(VimeoUrls.isConfigUrl("https://vimeo.com/video/123456789/config"))
        assertFalse(
            VimeoUrls.isConfigUrl("https://evil.example/player.vimeo.com/video/1/config"),
        )
        assertFalse(VimeoUrls.isConfigUrl("https://player.vimeo.com/video/123456789"))
    }

    @Test
    fun `the configuration address carries the privacy hash when the clip has one`() {
        assertEquals(
            "https://player.vimeo.com/video/123456789/config",
            VimeoUrls.configUrl("123456789"),
        )
        assertEquals(
            "https://player.vimeo.com/video/123456789/config?h=abcdef1234",
            VimeoUrls.configUrl("123456789", "abcdef1234"),
        )
    }
}
