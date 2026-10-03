package com.alal.yft.extractor.sites

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.PageNavigationHeaders
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.sites.facebook.FacebookExtractor
import com.alal.yft.extractor.sites.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.sites.tiktok.TikTokExtractor
import com.alal.yft.extractor.sites.vimeo.VimeoExtractor
import com.alal.yft.extractor.sites.youtube.YouTubeExtractor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiteNavigationHeadersTest {
    @Test
    fun facebookPageHasNavigationHeadersAndNoHeadlessCookie() = runTest {
        check("https://www.facebook.com/reel/1234567890123456") { FacebookExtractor(it) }
    }

    @Test
    fun tiktokPageHasNavigationHeadersAndNoHeadlessCookie() = runTest {
        check("https://www.tiktok.com/@fixture/video/7123456789012345678") {
            TikTokExtractor(it)
        }
    }

    @Test
    fun youtubePageHasNavigationHeadersAndNoHeadlessCookie() = runTest {
        check("https://www.youtube.com/watch?v=Yft0Fixture") { YouTubeExtractor(it) }
    }

    @Test
    fun vimeoPageHasNavigationHeadersAndNoHeadlessCookie() = runTest {
        check("https://vimeo.com/123456789") { VimeoExtractor(it) }
    }

    private suspend fun check(url: String, factory: (ExtractorHttpClient) -> SiteExtractor) {
        val http = FakeExtractorHttpClient()
        val extractor = factory(http)
        extractor.extract(
            SiteExtractionRequest(
                identity = requireNotNull(extractor.identify(url)),
                requestContext = BrowserRequestContext(url, USER_AGENT, cookie = null),
                nowEpochMs = 1_700_000_000_000,
            ),
        )

        val headers = http.requestedHeaders.single()
        PageNavigationHeaders.DEFAULTS.forEach { (name, value) ->
            assertEquals(value, headers[name])
        }
        assertEquals(USER_AGENT, headers["User-Agent"])
        assertNull(headers["Cookie"])
    }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) YFT/fixture"
    }
}
