package com.alal.yft.feature.browser

import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.extractor.api.SiteExtractionFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P46: when the browser offers TikTok's check, and which pages the check may open. */
class SiteCheckTest {
    @Test
    fun aTikTokLookupThatEndedOnACheckOffersTheCheck() {
        assertEquals(PAGE, SiteCheck.urlFor(failed("tiktok"), PAGE))
    }

    @Test
    fun otherReasonsAndOtherSitesDoNot() {
        assertNull(
            SiteCheck.urlFor(
                failed("tiktok", SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE),
                PAGE,
            ),
        )
        assertNull(SiteCheck.urlFor(failed("youtube"), "https://www.youtube.com/watch?v=x"))
        assertNull(SiteCheck.urlFor(failed("tiktok"), "http://www.tiktok.com/@a/video/1"))
    }

    @Test
    fun onlyTikToksOwnHttpsPagesOpenInTheCheck() {
        assertTrue(isCheckPage(PAGE))
        assertTrue(isCheckPage("https://m.tiktok.com/v/1.html"))
        assertFalse(isCheckPage("http://www.tiktok.com/"))
        assertFalse(isCheckPage("https://tiktok.com.example.test/"))
        assertFalse(isCheckPage("https://example.test/?u=tiktok.com"))
        assertFalse(isCheckPage("snssdk1233://aweme/detail/1"))
    }

    private fun failed(
        site: String,
        reason: SiteExtractionFailure = SiteExtractionFailure.BOT_CHECK,
    ) = SiteAdapterOutcome.Failed(
        adapterId = site,
        reason = reason,
        message = "fixture",
        allowsGenericFallback = false,
    )

    private companion object {
        const val PAGE = "https://www.tiktok.com/@fixture/video/7311234567890123456"
    }
}
