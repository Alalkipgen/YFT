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

    @Test
    fun `R2 walled hosts match the site and its subdomains only`() {
        listOf(
            "https://youtube.com/watch?v=a", "https://www.youtube.com/shorts/a",
            "https://m.youtube.com/watch?v=a", "https://music.youtube.com/watch?v=a",
            "https://youtu.be/a", "https://www.youtube-nocookie.com/embed/a",
            "HTTPS://WWW.YOUTUBE.COM./watch?v=a", "https://user@www.youtube.com:443/watch",
            "https://www.reddit.com/r/a/comments/b/c/", "https://old.reddit.com/r/a",
            "https://redd.it/b", "https://v.redd.it/b/DASH_720.mp4",
        ).forEach { assertTrue(it, TerminalRules.walled(it)) }
        listOf(
            null, "", "youtube.com/watch", "https://notyoutube.com/watch",
            "https://youtube.com.evil.test/watch", "https://evil.test/?next=https://youtube.com",
            "https://www.youtube.com@evil.test/watch", "https://evil.test\\@www.youtube.com/",
            "https://notreddit.com/r/a", "https://reddit.com.evil.test/r/a",
            "https://www.tiktok.com/@a/video/1", "https://example.test/watch/fixture",
        ).forEach { assertFalse(it.toString(), TerminalRules.walled(it)) }
        assertTrue(TerminalRules.youtube("https://youtu.be/a"))
        assertFalse(TerminalRules.youtube("https://www.reddit.com/r/a"))
    }

    @Test
    fun `R2 browser-only failures are final on walled hosts and nowhere else`() {
        val walled = listOf("https://www.youtube.com/watch?v=a", "https://www.reddit.com/r/a")
        val open = listOf("https://www.tiktok.com/@a/video/1", "https://example.test/watch", null)
        SiteExtractionFailure.entries.forEach { failure ->
            walled.forEach {
                assertEquals(
                    failure in TerminalRules.NEVER_FALLBACK ||
                        failure in TerminalRules.BROWSER_REQUIRED,
                    TerminalRules.blocksFallback(failure, it),
                )
            }
            open.forEach {
                assertEquals(
                    TerminalRules.blocksFallback(failure),
                    TerminalRules.blocksFallback(failure, it),
                )
            }
        }
        listOf(
            SiteExtractionFailure.BOT_CHECK,
            SiteExtractionFailure.LOGIN_REQUIRED,
            SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED,
        ).forEach { assertTrue(TerminalRules.blocksFallback(it, "https://youtu.be/a")) }
        assertFalse(
            TerminalRules.blocksFallback(
                SiteExtractionFailure.NO_MEDIA_FOUND, "https://www.youtube.com/watch?v=a",
            ),
        )
    }
}
