package com.alal.yft.extractor.sites.vk

import com.alal.yft.extractor.sites.testing.FakeExtractorHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VkUrlsTest {
    @Test
    fun `video and clip pages on every vk host are claimed with a vk_com address`() {
        val video = "https://vk.com/video-50883936_456244102"
        val clip = "https://vk.com/clip30014565_456240946"
        mapOf(
            "https://vk.com/video-50883936_456244102" to video,
            "https://www.vk.com/video-50883936_456244102" to video,
            "https://m.vk.com/video-50883936_456244102" to video,
            "https://vk.ru/video-50883936_456244102" to video,
            "https://m.vk.ru/video-50883936_456244102" to video,
            "https://vkvideo.ru/video-50883936_456244102" to video,
            "https://www.vkvideo.ru/video-50883936_456244102" to video,
            "https://m.vkvideo.ru/video-50883936_456244102" to video,
            "https://VK.com:443/video-50883936_456244102/" to video,
            " https://vk.com/video-50883936_456244102?t=1m2s " to video,
            "https://vk.com/clip30014565_456240946" to clip,
            "https://vkvideo.ru/clip30014565_456240946" to clip,
            "https://m.vk.ru/clip30014565_456240946" to clip,
        ).forEach { (url, canonical) ->
            val identity = VkUrls.identify(url)
            assertEquals(url, "vk", identity?.siteId)
            assertEquals(url, canonical, identity?.canonicalPageUrl)
            assertFalse(url, checkNotNull(identity).requiresCanonicalResolution)
        }
        assertEquals("-50883936_456244102", VkUrls.identify(video)?.contentId)
        assertEquals("30014565_456240946", VkUrls.identify(clip)?.contentId)
    }

    @Test
    fun `a video opened over another page is claimed by its layer`() {
        mapOf(
            "https://vk.com/feed?z=video-50883936_456244102%2Fpl_post_-50883936_1" to
                "https://vk.com/video-50883936_456244102",
            "https://vk.com/wall-140332_1?z=clip30014565_456240946" to
                "https://vk.com/clip30014565_456240946",
            "https://vkvideo.ru/@club50883936?z=video-50883936_456244102%2Fclub50883936" to
                "https://vk.com/video-50883936_456244102",
            "https://vk.com/im?sel=1&z=video-140332_456239111" to
                "https://vk.com/video-140332_456239111",
            // The layer wins over the page under it.
            "https://vk.com/video1_2?z=video-50883936_456244102" to
                "https://vk.com/video-50883936_456244102",
        ).forEach { (url, canonical) ->
            assertEquals(url, canonical, VkUrls.identify(url)?.canonicalPageUrl)
        }
    }

    @Test
    fun `embeds are claimed and a link's list and hash stay`() {
        mapOf(
            "https://vk.com/video_ext.php?oid=-50883936&id=456244102&hash=0123abcd" to
                "https://vk.com/video-50883936_456244102?hash=0123abcd",
            "https://vk.ru/video_ext.php?oid=30014565&id=456240946&hd=2&autoplay=1" to
                "https://vk.com/video30014565_456240946",
            "https://vk.com/video-50883936_456244102?list=ln-AbC123_x" to
                "https://vk.com/video-50883936_456244102?list=ln-AbC123_x",
            "https://vk.com/video-1_2?hash=abc&list=ln-x" to
                "https://vk.com/video-1_2?list=ln-x&hash=abc",
            // A list or hash that is not VK's shape is dropped, never passed on.
            "https://vk.com/video-1_2?list=a%26b&hash=zz%3Bzz" to "https://vk.com/video-1_2",
            "https://vk.com/video-1_2?hash=" + "a".repeat(65) to "https://vk.com/video-1_2",
        ).forEach { (url, canonical) ->
            assertEquals(url, canonical, VkUrls.identify(url)?.canonicalPageUrl)
        }
    }

    @Test
    fun `embed addresses name their host and carry only checked values`() {
        val video = checkNotNull(
            VkUrls.linkOf("https://vk.com/video-50883936_456244102?list=ln-x&hash=abc"),
        )
        val clip = checkNotNull(VkUrls.linkOf("https://vkvideo.ru/clip30014565_456240946"))

        assertEquals(
            "https://vk.ru/video_ext.php?oid=-50883936&id=456244102&hash=abc&list=ln-x",
            VkUrls.embedUrl(VkUrls.RU_HOST, video),
        )
        assertEquals(
            "https://vk.com/video_ext.php?oid=30014565&id=456240946",
            VkUrls.embedUrl(VkUrls.COM_HOST, clip),
        )
        assertTrue(clip.isClip)
    }

    @Test
    fun `look-alikes, other pages and junk stay with the generic detector`() {
        listOf(
            "https://vk.com.example.org/video-1_2",
            "https://evilvk.com/video-1_2",
            "https://vk.co/video-1_2",
            "https://vkvideo.ru.example.org/video-1_2",
            "https://video.vk.com/video-1_2",
            "http://vk.com/video-1_2",
            "https://user@vk.com/video-1_2",
            "https://vk.com:8443/video-1_2",
            "https://vk.com/video-1_2/extra",
            "https://vk.com/Video-1_2",
            // Bounded digit strings only: 25 digits, 20 digits, past Long and zero are refused.
            "https://vk.com/video-1234567890123456789012345_1",
            "https://vk.com/video-1_1234567890123456789012345",
            "https://vk.com/video99999999999999999999_1",
            "https://vk.com/video9999999999999999999_1",
            "https://vk.com/video0_1",
            "https://vk.com/video-1_0",
            "https://vk.com/video_ext.php?oid=-1",
            "https://vk.com/video_ext.php?oid=-1&id=abc",
            "https://vk.com/video_ext.php?oid=0&id=1",
            // Broken % escapes.
            "https://vk.com/video-1_2?list=%E0%A4%A",
            "https://vk.com/feed?z=video%ZZ",
            "https://vk.com/video-1_2?z=%",
            // VK's link-out page names another site.
            "https://vk.com/away.php?to=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3DYft0Fixture",
            "https://vk.com/away.php?to=https%3A%2F%2Fvk.com%2Fvideo-1_2&z=video-1_2",
            // Messenger posts carry no video id in the address.
            "https://vk.com/im/channels/-239710230?cmid=2",
            "https://vk.ru/im/channels/-239710230?cmid=2",
            "https://m.vk.ru/mail/channels/-239710230?cmid=2",
            "https://vk.com/wall-50883936_1",
            "https://vk.com/club50883936",
            "https://vk.com/video",
            "https://vk.com/videos-50883936",
            "https://vk.com/video-1_2?pad=" + "x".repeat(10_240),
            "https://vk.com/video-1_2 with a space",
            "",
            "   ",
            "not a url",
        ).forEach { url ->
            assertNull(url.take(120), VkUrls.identify(url))
        }
    }

    @Test
    fun `files may sit on any okcdn_ru server but never on its image host`() {
        listOf(
            "https://vkvd741.okcdn.ru/mp4_720/FIXTURE?expires=1",
            "https://vkvd99.okcdn.ru/hls/FIXTURE",
            // A new server prefix or failover host under `files` still works.
            "https://vkvideo1.okcdn.ru/mp4_720/FIXTURE",
            "https://VKVD655.OKCDN.RU/dash_sep/FIXTURE",
        ).forEach { assertTrue(it, VkUrls.isMediaFileUrl(it)) }

        listOf(
            "https://iv.okcdn.ru/FIXTURE?id=1",
            "https://notokcdn.ru/mp4_720/FIXTURE",
            "https://okcdn.ru.example.org/mp4_720/FIXTURE",
            "https://okcdn.ru/mp4_720/FIXTURE",
            "http://vkvd741.okcdn.ru/mp4_720/FIXTURE",
            "https://user@vkvd741.okcdn.ru/mp4_720/FIXTURE",
            "https://sun9-70.userapi.com/FIXTURE/impg/FIXTURE",
            "https://vkvd741.okcdn.ru/" + "x".repeat(9_000),
            "vkvd741.okcdn.ru",
        ).forEach { assertFalse(it.take(120), VkUrls.isMediaFileUrl(it)) }
    }

    @Test
    fun `the browser's media check knows vk's video servers only`() {
        val extractor = VkExtractor(FakeExtractorHttpClient())

        listOf(
            "https://vkvd741.okcdn.ru/mp4_720/FIXTURE?expires=1",
            "https://vkvd5.okcdn.ru/hls/FIXTURE/index.m3u8",
        ).forEach { assertTrue(it, extractor.isPlayerMediaRequest(it)) }

        listOf(
            "https://sun9-70.userapi.com/FIXTURE/impg/FIXTURE.jpg",
            "https://iv.okcdn.ru/FIXTURE?id=1",
            "https://vkvideo1.okcdn.ru/mp4_720/FIXTURE",
            "https://stats.vk-portal.net/web-stats/FIXTURE",
            "https://fixture.ms.vk.ru/FIXTURE",
            "https://ad.mail.ru/FIXTURE",
            "http://vkvd741.okcdn.ru/mp4_720/FIXTURE",
            "https://vkvd741.okcdn.ru.example.org/mp4_720/FIXTURE",
            "https://vkvd1234567.okcdn.ru/mp4_720/FIXTURE",
        ).forEach { assertFalse(it, extractor.isPlayerMediaRequest(it)) }
    }

    @Test
    fun `a signed address's expiry reads from its query or its path`() {
        assertEquals(
            1_761_800_000_000L,
            VkUrls.expiresAtOf("https://vkvd741.okcdn.ru/mp4_720/F?expires=1761800000000&id=1"),
        )
        assertEquals(
            1_761_800_000_000L,
            VkUrls.expiresAtOf("https://vkvd504.okcdn.ru/hls_ondemand/expires/1761800000/type/2/x"),
        )
        assertEquals(
            1_761_800_000_000L,
            VkUrls.expiresAtOf("https://vkvd741.okcdn.ru/mp4_720/F?srcIp=1&expires=1761800000"),
        )
        listOf(
            "https://vkvd741.okcdn.ru/mp4_720/FIXTURE?expires=FIXTURE",
            "https://vkvd741.okcdn.ru/mp4_720/FIXTURE?expires=17618",
            "https://vkvd741.okcdn.ru/mp4_720/FIXTURE?expires=-1761800000",
            "https://vkvd504.okcdn.ru/hls_ondemand/expires/FIXTURE/type/2",
            "https://vkvd741.okcdn.ru/mp4_720/FIXTURE",
            "not a url %",
        ).forEach { assertNull(it, VkUrls.expiresAtOf(it)) }
    }

    @Test
    fun `a page's cookie belongs to its own vk site only`() {
        assertEquals("vk.ru", VkUrls.cookieSiteOf("https://vk.ru/video-1_2"))
        assertEquals("vk.ru", VkUrls.cookieSiteOf("https://m.vk.ru/video-1_2"))
        assertEquals("vk.com", VkUrls.cookieSiteOf("https://vk.com/feed"))
        assertEquals("vk.com", VkUrls.cookieSiteOf("https://m.vk.com/video-1_2"))
        listOf(
            "https://vkvideo.ru/video-1_2",
            "https://evilvk.com/",
            "https://evilvk.ru/",
            "https://vk.com.example.org/",
            "http://vk.com/",
            "about:blank",
            null,
        ).forEach { assertNull(it, VkUrls.cookieSiteOf(it)) }
    }
}
