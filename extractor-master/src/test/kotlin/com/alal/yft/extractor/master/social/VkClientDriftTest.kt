package com.alal.yft.extractor.master.social

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.master.NOW
import com.alal.yft.extractor.master.fixture
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.sites.vk.VkExtractor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

/**
 * VK-Master plan Appendix B step 1, drift at run time: main's VK adapter (`extractor-sites`, as the
 * app builds it) asks its token and `video.get` exactly as [VkClient] would — the same address,
 * body, headers in the same order and bounds. Main's values rotating, its order of fields or its
 * headers changing fails here, before the copy is trusted (the "# pending" provenance rows are not
 * checked by the drift script until the base moves past main's VK files).
 */
class VkClientDriftTest {
    @Test
    fun `main's path D asks the token and video get as VkClient does`() = runTest {
        val http = pathD()
        val extractor = VkExtractor(http, clockMillis = { 0L })
        val identity = checkNotNull(extractor.identify(PAGE))

        val result = extractor.extract(SiteExtractionRequest(identity, BrowserRequestContext(PAGE, AGENT, null), NOW))

        assertTrue(result.toString(), result is SiteExtractionResult.Success)
        assertEquals(listOf(VkClient.TOKEN_URL), http.postedUrls)
        assertEquals(listOf(VkClient.BUNDLED.tokenBody), http.postedBodies)
        assertEquals(listOf(VkClient.headers(AGENT, null).toList()), http.postedHeaders.map { it.toList() })
        assertEquals(listOf(VkClient.MAX_TOKEN_BYTES), http.postedBodyLimits)

        val link = checkNotNull(VkClient.Link.of(identity))
        assertEquals(2, http.requestedUrls.size)
        val embed = URI(http.requestedUrls[0])
        assertEquals(VkClient.COM_HOST, embed.host)
        assertEquals(VkClient.EMBED_PATH, embed.path)
        assertEquals(VkClient.BUNDLED.videoGetUrl(link), http.requestedUrls[1])
        val token = checkNotNull(VkClient.token(fixture("vk-pack/token_okay.json"), NOW))
        assertEquals(VkClient.headers(AGENT, token).toList(), http.requestedHeaders[1].toList())
        assertEquals(VkClient.MAX_API_BYTES, http.requestedBodyLimits[1])

        val files = (result as SiteExtractionResult.Success).candidates
        assertEquals(4, files.size)
        files.forEach { file ->
            assertTrue("main's files are VK's video servers by VkClient's rule", VkClient.mediaFile(file.mediaUrl))
            assertEquals(file.expiresAtEpochMs, VkClient.expiresAtOf(file.mediaUrl))
        }
    }

    @Test
    fun `a link's list goes into video get, and a vk_ru page's cookie never into path D`() = runTest {
        val http = pathD()
        val extractor = VkExtractor(http, clockMillis = { 0L })
        val page = "https://vk.ru/video-100000001_400000001?list=FIXTURE"
        val identity = checkNotNull(extractor.identify(page))

        extractor.extract(SiteExtractionRequest(identity, BrowserRequestContext(page, AGENT, "remixsid=FIXTURE"), NOW))

        val link = checkNotNull(VkClient.Link.of(identity))
        assertEquals("FIXTURE", link.list)
        assertEquals(VkClient.RU_HOST, URI(http.requestedUrls[0]).host)
        assertEquals(VkClient.BUNDLED.videoGetUrl(link), http.requestedUrls[1])
        assertEquals(listOf(VkClient.headers(AGENT, null).toList()), http.postedHeaders.map { it.toList() })
        (http.postedHeaders + http.requestedHeaders.drop(1)).forEach { headers ->
            assertFalse(headers.keys.any { it.equals("Cookie", ignoreCase = true) })
        }
    }

    @Test
    fun `main's identity names the video VkClient binds to, for every link shape main claims`() {
        val extractor = VkExtractor(FakeExtractorHttpClient(), clockMillis = { 0L })
        mapOf(
            "https://vk.com/video-100000001_400000001" to VkClient.Link(-100000001, 400000001),
            "https://m.vk.com/clip100000002_400000002" to VkClient.Link(100000002, 400000002),
            "https://vkvideo.ru/video-100000001_400000001?list=FIXTURE" to VkClient.Link(-100000001, 400000001, "FIXTURE"),
            "https://vk.com/feed?z=video-100000001_400000001%2Fpl_cat_trends" to VkClient.Link(-100000001, 400000001),
            "https://vk.com/video_ext.php?oid=-100000001&id=400000001&hash=abc" to VkClient.Link(-100000001, 400000001),
        ).forEach { (page, link) ->
            val identity = checkNotNull(extractor.identify(page)) { page }
            assertEquals(page, link, VkClient.Link.of(identity))
        }
    }

    private fun pathD() = FakeExtractorHttpClient(
        getResponder = { url, _ ->
            val uri = URI(url)
            when {
                uri.path == VkClient.EMBED_PATH -> FakeExtractorHttpClient.html(fixture("vk-pack/embed_adult.html"), url)
                uri.host == VkClient.API_HOST -> FakeExtractorHttpClient.json(fixture("vk-pack/video_get_mp4.json"), url)
                else -> null
            }
        },
        postResponder = { url, _ ->
            if (url == VkClient.TOKEN_URL) FakeExtractorHttpClient.json(fixture("vk-pack/token_okay.json"), url) else null
        },
    )

    private companion object {
        const val PAGE = "https://vk.com/video-100000001_400000001"
        const val AGENT = "Mozilla/5.0 (Linux; Android 14) FixtureBrowser/1.0"
    }
}
