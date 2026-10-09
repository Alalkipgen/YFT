package com.alal.yft.extractor.api

import com.alal.yft.core.model.media.BrowserRequestContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** P40 step 1: the post's data a page already holds, carried by the extraction request. */
class SitePageDataTest {
    @Test
    fun `page data is limited to 64 KB of UTF-8 and is never blank`() {
        val limit = "x".repeat(SitePageData.MAX_BYTES)
        assertEquals(limit, SitePageData(limit, SitePageDataSource.TAB_SCRIPT).json)
        // Each Burmese letter takes three bytes, so a third as many letters reach the limit.
        val wide = "က".repeat(SitePageData.MAX_BYTES / 3 + 1)

        assertNull(SitePageData.of(limit + "x", SitePageDataSource.TAB_SCRIPT))
        assertNull(SitePageData.of(wide, SitePageDataSource.TAB_API_ANSWER))
        assertNull(SitePageData.of(" ", SitePageDataSource.HIDDEN_PAGE))
        assertNull(SitePageData.of(null, SitePageDataSource.HIDDEN_PAGE))
        assertThrows(IllegalArgumentException::class.java) {
            SitePageData(limit + "x", SitePageDataSource.TAB_SCRIPT)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SitePageData("", SitePageDataSource.TAB_SCRIPT)
        }
    }

    @Test
    fun `page data never shows its text, which can hold signed addresses`() {
        val secret = "{\"playAddr\":\"https://cdn.example.test/v.mp4?signature=fixture-secret\"}"
        val data = SitePageData(secret, SitePageDataSource.TAB_API_ANSWER)
        val request = SiteExtractionRequest(
            identity = SitePageIdentity(
                "tiktok",
                "123456",
                "https://www.tiktok.com/@a/video/123456",
            ),
            requestContext = BrowserRequestContext("https://www.tiktok.com/", "agent", null),
            nowEpochMs = 1L,
            pageData = data,
        )

        assertFalse(data.toString().contains("fixture-secret"))
        assertFalse(request.toString().contains("fixture-secret"))
        assertTrue(request.toString().contains("pageData=SitePageData(source=TAB_API_ANSWER"))
        assertEquals(SitePageData(secret, SitePageDataSource.TAB_API_ANSWER), data)
        assertFalse(data == SitePageData(secret, SitePageDataSource.HIDDEN_PAGE))
    }

    @Test
    fun `each source names itself for Details`() {
        assertEquals("tab · page script", SitePageDataSource.TAB_SCRIPT.label)
        assertEquals("tab · API answer", SitePageDataSource.TAB_API_ANSWER.label)
        assertEquals("hidden page", SitePageDataSource.HIDDEN_PAGE.label)
    }
}