package com.alal.yft.extractor.sites.tiktok

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TikTokUrlsTest {
    @Test
    fun `standard post urls canonicalize and drop tracking parameters`() {
        val identity = TikTokUrls.identify(
            "https://www.tiktok.com/@fixture_user/video/7311234567890123456?is_from_webapp=1&sender_device=pc",
        )!!

        assertEquals("tiktok", identity.siteId)
        assertEquals("7311234567890123456", identity.contentId)
        assertEquals(
            "https://www.tiktok.com/@fixture_user/video/7311234567890123456",
            identity.canonicalPageUrl,
        )
        assertFalse(identity.requiresCanonicalResolution)
    }

    @Test
    fun `mobile and author-less forms resolve to the same canonical identity`() {
        // P36: the author-less address TikTok answers is /@/video/<id> (/video/<id> gives 404).
        val forms = listOf(
            "https://m.tiktok.com/v/7311234567890123456.html",
            "https://www.tiktok.com/video/7311234567890123456",
            "https://tiktok.com/video/7311234567890123456",
            "https://www.tiktok.com/@/video/7311234567890123456",
        )

        forms.forEach { url ->
            val identity = TikTokUrls.identify(url)!!
            assertEquals(url, "7311234567890123456", identity.contentId)
            assertEquals(
                url,
                "https://www.tiktok.com/@/video/7311234567890123456",
                identity.canonicalPageUrl,
            )
        }
    }

    @Test
    fun `short links are matched but explicitly marked unresolved`() {
        listOf("https://vm.tiktok.com/ZMabc123x", "https://vt.tiktok.com/ZSdef456y").forEach { url ->
            val identity = TikTokUrls.identify(url)!!
            assertTrue(url, identity.requiresCanonicalResolution)
            assertEquals(url, identity.canonicalPageUrl)
        }
    }

    @Test
    fun `photo posts are recognized so they can fail as having no video`() {
        val identity = TikTokUrls.identify(
            "https://www.tiktok.com/@fixture_user/photo/7311234567890123457",
        )!!

        assertTrue(TikTokUrls.isPhotoPost(identity.canonicalPageUrl))
        assertFalse(
            TikTokUrls.isPhotoPost(
                "https://www.tiktok.com/@fixture_user/video/7311234567890123456",
            ),
        )
    }

    @Test
    fun `unrelated, insecure and malformed urls are not claimed`() {
        val rejected = listOf(
            "http://www.tiktok.com/@fixture_user/video/7311234567890123456",
            "https://www.tiktok.com/@fixture_user",
            "https://www.tiktok.com/@fixture_user/video/not-a-number",
            "https://www.tiktok.com/@fixture_user/live",
            "https://www.tiktok.com/tag/fixture",
            "https://tiktok.com.evil.test/@fixture_user/video/7311234567890123456",
            "https://notyoutube.tiktok.evil.test/video/7311234567890123456",
            "https://vm.tiktok.com/",
            "https://vm.tiktok.com/code/extra",
            "https://user:secret@www.tiktok.com/@fixture_user/video/7311234567890123456",
            "https://www.facebook.com/watch/?v=1234567890",
            "not a url at all",
            "",
        )

        rejected.forEach { url -> assertNull(url, TikTokUrls.identify(url)) }
    }
}
