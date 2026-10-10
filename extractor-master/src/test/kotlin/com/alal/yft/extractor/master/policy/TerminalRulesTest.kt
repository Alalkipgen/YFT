package com.alal.yft.extractor.master.policy

import com.alal.yft.extractor.api.SiteExtractionFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalRulesTest {
    @Test
    fun `final answers and browser-only failures are fixed and disjoint`() {
        assertEquals(
            setOf(
                SiteExtractionFailure.ADAPTER_DISABLED,
                SiteExtractionFailure.DRM_PROTECTED,
                SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
                SiteExtractionFailure.GEO_RESTRICTED,
                SiteExtractionFailure.NETWORK,
                SiteExtractionFailure.RATE_LIMITED,
            ),
            TerminalRules.NEVER_FALLBACK,
        )
        assertEquals(
            setOf(
                SiteExtractionFailure.LOGIN_REQUIRED,
                SiteExtractionFailure.BOT_CHECK,
                SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED,
            ),
            TerminalRules.BROWSER_REQUIRED,
        )
        assertTrue(TerminalRules.NEVER_FALLBACK.intersect(TerminalRules.BROWSER_REQUIRED).isEmpty())
    }

    @Test
    fun `helpers agree with the sets for every failure`() {
        SiteExtractionFailure.entries.forEach {
            assertEquals(it in TerminalRules.NEVER_FALLBACK, TerminalRules.blocksFallback(it))
            assertEquals(
                it in TerminalRules.BROWSER_REQUIRED,
                TerminalRules.needsAuthorizedPlayback(it),
            )
        }
        assertFalse(TerminalRules.blocksFallback(SiteExtractionFailure.NO_MEDIA_FOUND))
    }
}
