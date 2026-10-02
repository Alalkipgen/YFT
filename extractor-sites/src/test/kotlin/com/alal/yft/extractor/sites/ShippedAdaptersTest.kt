package com.alal.yft.extractor.sites

import com.alal.yft.extractor.api.SiteAdapterFlags
import com.alal.yft.extractor.api.SiteAdapterSelection
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.sites.facebook.FacebookExtractor
import com.alal.yft.extractor.sites.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.sites.tiktok.TikTokExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the adapter set the app ships: every adapter claims only its own site.
 *
 * Two adapters claiming one page is a configuration bug the registry refuses, so selection is
 * asserted here against the real adapters instead of fakes.
 */
class ShippedAdaptersTest {
    private val registry = SiteExtractorRegistry(shippedAdapters())

    @Test
    fun `each adapter claims only its own pages`() {
        val expectations = mapOf(
            "https://www.tiktok.com/@fixture_user/video/7311234567890123456" to "tiktok",
            "https://vm.tiktok.com/ZMabc123x" to "tiktok",
            "https://www.facebook.com/watch/?v=1234567890123456" to "facebook",
            "https://www.facebook.com/reel/7180001112223334" to "facebook",
            "https://fb.watch/aBc123dEf" to "facebook",
        )

        expectations.forEach { (url, adapterId) ->
            val selection = registry.select(url)
            assertEquals(url, adapterId, (selection as SiteAdapterSelection.Matched).extractor.id)
            assertEquals(url, adapterId, selection.identity.siteId)
        }
    }

    @Test
    fun `unclaimed pages fall through to the generic detector`() {
        listOf(
            "https://www.facebook.com/FixturePage/posts/1234567890123456",
            "https://www.tiktok.com/@fixture_user",
            "https://media.example.test/video.mp4",
        ).forEach { url ->
            assertTrue(url, registry.select(url) is SiteAdapterSelection.None)
        }
    }

    @Test
    fun `a disabled adapter reports itself instead of silently matching`() {
        val flags = SiteAdapterFlags { adapterId -> adapterId != "facebook" }
        val gated = SiteExtractorRegistry(shippedAdapters(), flags)

        val selection = gated.select("https://www.facebook.com/watch/?v=1234567890123456")

        assertEquals("facebook", (selection as SiteAdapterSelection.Disabled).adapterId)
        assertEquals(listOf("facebook"), gated.disabledAdapterIds())
        assertTrue(
            gated.select("https://www.tiktok.com/@fixture_user/video/7311234567890123456")
                is SiteAdapterSelection.Matched,
        )
    }

    private fun shippedAdapters() = FakeExtractorHttpClient().let { http ->
        listOf(TikTokExtractor(http), FacebookExtractor(http))
    }
}
