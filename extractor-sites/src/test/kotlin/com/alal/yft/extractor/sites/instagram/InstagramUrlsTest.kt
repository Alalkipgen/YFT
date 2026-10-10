package com.alal.yft.extractor.sites.instagram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramUrlsTest {
    @Test
    fun `posts and reels collapse onto one canonical address`() {
        mapOf(
            "https://www.instagram.com/reel/C9fixtREEL1/?igsh=MWZmaXh0dXJl" to
                "https://www.instagram.com/reel/C9fixtREEL1/",
            "https://instagram.com/reels/C9fixtREEL1" to
                "https://www.instagram.com/reel/C9fixtREEL1/",
            "https://m.instagram.com/reel/C9fixtREEL1/?utm_source=ig_web_copy_link" to
                "https://www.instagram.com/reel/C9fixtREEL1/",
            "https://www.instagram.com/p/C9fixtREEL1/" to
                "https://www.instagram.com/p/C9fixtREEL1/",
            "https://www.instagram.com/tv/C9fixtREEL1/" to
                "https://www.instagram.com/p/C9fixtREEL1/",
            "https://www.instagram.com/fixture.creator/p/C9fixtREEL1/" to
                "https://www.instagram.com/p/C9fixtREEL1/",
            "https://www.instagram.com/fixture.creator/reel/C9fixtREEL1/" to
                "https://www.instagram.com/reel/C9fixtREEL1/",
            "https://www.instagram.com/p/C9fixtREEL1/?img_index=3&igsh=abc" to
                "https://www.instagram.com/p/C9fixtREEL1/?img_index=3",
        ).forEach { (url, canonical) ->
            val identity = InstagramUrls.identify(url)
            assertEquals(url, canonical, identity?.canonicalPageUrl)
            assertEquals(url, "C9fixtREEL1", identity?.contentId)
            assertEquals(url, "instagram", identity?.siteId)
        }
    }

    @Test
    fun `profiles, stories, audio pages and other sites are not claimed`() {
        listOf(
            "https://www.instagram.com/fixture.creator/",
            "https://www.instagram.com/stories/fixture.creator/3310536259357233439/",
            "https://www.instagram.com/reels/audio/1234567890/",
            "https://www.instagram.com/explore/",
            "https://www.instagram.com/",
            "http://www.instagram.com/reel/C9fixtREEL1/",
            "https://www.instagram.com.evil.test/reel/C9fixtREEL1/",
            "https://www.threads.net/@fixture/post/C9fixtREEL1",
        ).forEach { url -> assertNull(url, InstagramUrls.identify(url)) }
    }

    @Test
    fun `a shortcode reads as its media ID`() {
        assertEquals("908540701891980503", InstagramUrls.mediaIdOf("ybyPRoQWzX"))
        assertEquals("3310536259357233439", InstagramUrls.mediaIdOf("C3xYzAbCdEf"))
        assertEquals(
            InstagramUrls.mediaIdOf("C3xYzAbCdEf"),
            InstagramUrls.mediaIdOf("C3xYzAbCdEfPRIVATEtail"),
        )
        assertNull(InstagramUrls.mediaIdOf("C3xYz.bCdEf"))
    }

    @Test
    fun `a carousel item and a login wall are read from addresses`() {
        val third = "https://www.instagram.com/p/C9fixtSIDE1/?img_index=3"
        assertEquals(3, InstagramUrls.itemOf(third))
        assertNull(InstagramUrls.itemOf("https://www.instagram.com/p/C9fixtSIDE1/"))
        assertTrue(InstagramUrls.isLoginWall("https://www.instagram.com/accounts/login/?next=%2F"))
        assertTrue(InstagramUrls.isLoginWall("https://www.instagram.com/challenge/?next=%2F"))
    }
}
