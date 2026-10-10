package com.alal.yft.extractor.sites.x

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XUrlsTest {
    @Test
    fun `post addresses on every host collapse onto one canonical address`() {
        mapOf(
            "https://x.com/fixture_user/status/1785999999999999999" to
                "https://x.com/fixture_user/status/1785999999999999999",
            "https://twitter.com/fixture_user/status/1785999999999999999?s=20&t=abc" to
                "https://x.com/fixture_user/status/1785999999999999999",
            "https://mobile.twitter.com/fixture_user/status/1785999999999999999" to
                "https://x.com/fixture_user/status/1785999999999999999",
            "https://www.x.com/Fixture_User/statuses/1785999999999999999/" to
                "https://x.com/Fixture_User/status/1785999999999999999",
            "https://x.com/i/status/1785999999999999999" to
                "https://x.com/i/status/1785999999999999999",
            "https://x.com/i/web/status/1785999999999999999" to
                "https://x.com/i/status/1785999999999999999",
            "https://x.com/fixture_user/status/1785999999999999999/video/2" to
                "https://x.com/fixture_user/status/1785999999999999999/video/2",
            "https://x.com/fixture_user/status/1785999999999999999/photo/1" to
                "https://x.com/fixture_user/status/1785999999999999999",
            "https://x.com/fixture_user/status/1785999999999999999/analytics" to
                "https://x.com/fixture_user/status/1785999999999999999",
        ).forEach { (url, canonical) ->
            val identity = XUrls.identify(url)
            assertEquals(url, canonical, identity?.canonicalPageUrl)
            assertEquals(url, "1785999999999999999", identity?.contentId)
            assertEquals(url, "x", identity?.siteId)
        }
    }

    @Test
    fun `profiles, timelines, short links and other sites are not claimed`() {
        listOf(
            "https://x.com/fixture_user",
            "https://x.com/home",
            "https://x.com/search?q=video",
            "https://x.com/explore/status/1785999999999999999",
            "https://x.com/fixture_user/status/not-a-number",
            "https://x.com/fixture_user/likes",
            "https://t.co/fixture",
            "http://x.com/fixture_user/status/1785999999999999999",
            "https://fixupx.com/fixture_user/status/1785999999999999999",
            "https://x.com.evil.test/fixture_user/status/1785999999999999999",
        ).forEach { url -> assertNull(url, XUrls.identify(url)) }
    }

    @Test
    fun `a video link names its place among the post's media`() {
        assertEquals(
            2,
            XUrls.videoNumberOf("https://x.com/fixture_user/status/1785999999999999999/video/2"),
        )
        assertNull(XUrls.videoNumberOf("https://x.com/fixture_user/status/1785999999999999999"))
    }
}
