package com.alal.yft.extractor.sites.facebook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FacebookUrlsTest {
    @Test
    fun `watch, video and profile urls canonicalize onto one identity`() {
        listOf(
            "https://www.facebook.com/watch/?v=1234567890123456&ref=sharing",
            "https://www.facebook.com/watch?v=1234567890123456",
            "https://m.facebook.com/video.php?v=1234567890123456&_rdr",
            "https://web.facebook.com/FixturePage/videos/1234567890123456/",
            "https://www.facebook.com/FixturePage/videos/fixture-title/1234567890123456/",
        ).forEach { url ->
            val identity = FacebookUrls.identify(url)
            assertEquals(url, "facebook", identity?.siteId)
            assertEquals(url, "1234567890123456", identity?.contentId)
            assertEquals(
                url,
                "https://www.facebook.com/watch/?v=1234567890123456",
                identity?.canonicalPageUrl,
            )
            assertFalse(url, identity!!.requiresCanonicalResolution)
        }
    }

    @Test
    fun `reels keep their own canonical path`() {
        listOf(
            "https://www.facebook.com/reel/7180001112223334",
            "https://www.facebook.com/reels/7180001112223334?s=single_unit",
            "https://m.facebook.com/reel/7180001112223334/",
        ).forEach { url ->
            assertEquals(
                url,
                "https://www.facebook.com/reel/7180001112223334",
                FacebookUrls.identify(url)?.canonicalPageUrl,
            )
        }
        assertEquals(
            FacebookUrls.PostKind.REEL,
            FacebookUrls.kindOf("https://www.facebook.com/reel/7180001112223334"),
        )
    }

    @Test
    fun `short and share links are matched but explicitly marked unresolved`() {
        val short = FacebookUrls.identify("https://fb.watch/aBc123dEf-/")!!
        assertEquals("aBc123dEf-", short.contentId)
        assertEquals("https://fb.watch/aBc123dEf-", short.canonicalPageUrl)
        assertTrue(short.requiresCanonicalResolution)

        val video = FacebookUrls.identify("https://www.facebook.com/share/v/aBc123dEf/?mi=1")!!
        assertEquals("aBc123dEf", video.contentId)
        assertEquals("https://www.facebook.com/share/v/aBc123dEf", video.canonicalPageUrl)
        assertTrue(video.requiresCanonicalResolution)

        val reel = FacebookUrls.identify("https://www.facebook.com/share/r/aBc123dEf/")!!
        assertEquals("https://www.facebook.com/share/r/aBc123dEf", reel.canonicalPageUrl)
        assertTrue(reel.requiresCanonicalResolution)
    }

    @Test
    fun `unrelated, non-video and malformed urls are not claimed`() {
        listOf(
            "http://www.facebook.com/watch/?v=1234567890123456",
            "https://www.facebook.com/watch/?v=123",
            "https://www.facebook.com/watch/",
            "https://www.facebook.com/FixturePage/posts/1234567890123456",
            "https://www.facebook.com/photo/?fbid=1234567890123456",
            "https://www.facebook.com/groups/1234567890/videos/9876543210987",
            "https://www.facebook.com/profile.php?id=1234567890123456",
            "https://www.facebook.com/share/p/aBc123dEf/",
            "https://www.facebook.com/",
            "https://www.tiktok.com/@fixture_user/video/7311234567890123456",
            "https://evil.example/www.facebook.com/watch/?v=1234567890123456",
            "not a url",
        ).forEach { url -> assertNull(url, FacebookUrls.identify(url)) }
    }

    @Test
    fun `media expiry is read only from a plausible cdn parameter`() {
        val media = "https://video.example-cdn.test/v/fixture.mp4"

        assertEquals(4_070_908_800_000L, FacebookUrls.mediaExpiryEpochMs("$media?oe=F2A52380&oh=x"))
        assertEquals(
            1_700_000_000_000L,
            FacebookUrls.mediaExpiryEpochMs("$media?_nc_exp=1700000000000"),
        )
        assertEquals(
            1_700_000_000_000L,
            FacebookUrls.mediaExpiryEpochMs("$media?expire=1700000000"),
        )
        assertNull(FacebookUrls.mediaExpiryEpochMs("$media?oe=zz"))
        assertNull(FacebookUrls.mediaExpiryEpochMs("$media?oe=5"))
        assertNull(FacebookUrls.mediaExpiryEpochMs(media))
    }

    @Test
    fun `login and checkpoint redirects are recognized as access walls`() {
        assertTrue(FacebookUrls.isAccessWall("https://www.facebook.com/login/?next=%2Fwatch"))
        assertTrue(FacebookUrls.isAccessWall("https://m.facebook.com/checkpoint/block/?u=1"))
        assertFalse(FacebookUrls.isAccessWall("https://www.facebook.com/watch/?v=1234567890123456"))
        assertFalse(FacebookUrls.isAccessWall("https://login.example/facebook"))
    }
}
