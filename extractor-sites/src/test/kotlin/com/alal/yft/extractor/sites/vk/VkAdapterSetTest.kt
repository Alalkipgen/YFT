package com.alal.yft.extractor.sites.vk

import com.alal.yft.extractor.api.SiteAdapterSelection
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.sites.facebook.FacebookExtractor
import com.alal.yft.extractor.sites.instagram.InstagramExtractor
import com.alal.yft.extractor.sites.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.sites.tiktok.TikTokExtractor
import com.alal.yft.extractor.sites.vimeo.VimeoExtractor
import com.alal.yft.extractor.sites.x.XExtractor
import com.alal.yft.extractor.sites.youtube.YouTubeExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VK next to the adapters the app already ships, in the app's order (SiteAdapterModule): the
 * registry refuses a page two adapters claim, so VK must claim only VK's pages and no other
 * adapter may claim them.
 */
class VkAdapterSetTest {
    private val registry = FakeExtractorHttpClient().let { http ->
        SiteExtractorRegistry(
            listOf(
                TikTokExtractor(http),
                FacebookExtractor(http),
                VimeoExtractor(http),
                YouTubeExtractor(http),
                XExtractor(http),
                InstagramExtractor(http),
                VkExtractor(http),
            ),
        )
    }

    @Test
    fun `vk's pages go to vk and only to vk`() {
        listOf(
            "https://vk.com/video-50883936_456244102",
            "https://vkvideo.ru/video-50883936_456244102",
            "https://vksport.vkvideo.ru/video-50883936_456244102",
            "https://m.vk.ru/clip30014565_456240946",
            "https://vk.com/feed?z=video-50883936_456244102%2Fpl_post_-50883936_1",
            "https://vk.com/video_ext.php?oid=-50883936&id=456244102&hash=0123abcd",
        ).forEach { url ->
            val selection = registry.select(url)
            assertEquals(url, "vk", (selection as SiteAdapterSelection.Matched).extractor.id)
            assertEquals(url, "vk", selection.identity.siteId)
        }
    }

    @Test
    fun `other sites keep their own adapters and vk claims none of them`() {
        mapOf(
            "https://www.tiktok.com/@fixture_user/video/7311234567890123456" to "tiktok",
            "https://www.facebook.com/watch/?v=1234567890123456" to "facebook",
            "https://vimeo.com/123456789" to "vimeo",
            "https://www.youtube.com/watch?v=Yft0Fixture" to "youtube",
            "https://x.com/fixture_user/status/1785999999999999999" to "x",
            "https://www.instagram.com/reel/C9fixtREEL1/" to "instagram",
        ).forEach { (url, adapterId) ->
            val selection = registry.select(url)
            assertEquals(url, adapterId, (selection as SiteAdapterSelection.Matched).extractor.id)
            assertEquals(url, null, VkUrls.identify(url))
        }
    }

    @Test
    fun `vk pages without a video id stay with the generic detector`() {
        listOf(
            "https://vk.com/im/channels/-239710230?cmid=2",
            "https://vk.com/away.php?to=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3DYft0Fixture",
            "https://vk.com/club50883936",
            "https://vkvideo.ru/",
            "https://vksport.vkvideo.ru/",
        ).forEach { url ->
            assertTrue(url, registry.select(url) is SiteAdapterSelection.None)
        }
    }
}
