package com.alal.yft.extractor.sites.facebook

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.sites.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.sites.testing.Fixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FacebookPublicReelTest {
    @Test
    fun publicReelWithACertificateButNoLicenceYieldsHdSdAndDash() = runTest {
        val identity = requireNotNull(
            FacebookUrls.identify("https://www.facebook.com/reel/1603698891196107"),
        )
        val http = FakeExtractorHttpClient.serving(
            identity.canonicalPageUrl,
            Fixtures.read("facebook/public_reel_empty_licences.html"),
        )

        val result = FacebookExtractor(http).extract(
            SiteExtractionRequest(
                identity,
                BrowserRequestContext(identity.canonicalPageUrl, "YFT/fixture", cookie = null),
                1_791_000_000_000,
            ),
        )

        assertTrue("Empty licence metadata is not DRM", result is SiteExtractionResult.Success)
        result as SiteExtractionResult.Success
        assertEquals(listOf(MediaKind.DIRECT, MediaKind.DIRECT, MediaKind.DASH),
            result.candidates.map { it.kind })
        assertTrue(result.candidates[0].title.orEmpty().endsWith("HD"))
        assertTrue(result.candidates[1].title.orEmpty().endsWith("SD"))
    }
    @Test
    fun videoAndReelShareLinksAcceptTheResolvedIdWithoutSelectingASuggestedVideo() = runTest {
        val target = Fixtures.read("facebook/public_reel_empty_licences.html")
        val suggested = """<script type="application/json">{"id":"9999999999999999",
            "videoDeliveryLegacyFields":{"browser_native_hd_url":"https://cdn.test/suggested.mp4"}}
            </script>""".trimIndent()
        for (share in listOf("v", "r")) {
            for (resolved in listOf("reel/1603698891196107", "watch/?v=1603698891196107")) {
                val identity = requireNotNull(
                    FacebookUrls.identify("https://www.facebook.com/share/$share/fixtureCode/"),
                )
                val final = "https://www.facebook.com/$resolved"
                val http = FakeExtractorHttpClient.serving(
                    identity.canonicalPageUrl, suggested + target, finalUrl = final,
                )
                val result = FacebookExtractor(http).extract(
                    SiteExtractionRequest(identity,
                        BrowserRequestContext(identity.canonicalPageUrl, "YFT/fixture", null), 1L),
                ) as SiteExtractionResult.Success

                assertTrue(result.candidates.all { it.pageUrl == final })
                assertFalse(result.candidates.any { it.mediaUrl.contains("suggested") })
                assertEquals("navigate", http.requestedHeaders.first()["Sec-Fetch-Mode"])
                assertTrue(http.requestedHeaders.none { it.containsKey("Cookie") })
                // P23: a page with HD/SD files only asks the reel's own page for the AVC
                // ladder, once; a /watch/ page is never asked, Safari gets it without the video.
                val ladder = listOf(final).filter { resolved.startsWith("reel/") }
                assertEquals(listOf(identity.canonicalPageUrl) + ladder, http.requestedUrls)
            }
        }
    }

}
