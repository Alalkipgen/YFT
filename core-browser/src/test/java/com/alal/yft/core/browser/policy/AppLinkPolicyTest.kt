package com.alal.yft.core.browser.policy

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLinkPolicyTest {
    @Test
    fun plainHttpIsBlockedWithAMessage() {
        assertEquals(AppLinkPolicy.Decision.Insecure, AppLinkPolicy.decide("http://example.test/"))
    }

    @Test
    fun intentLinksLoadTheirHttpsFallbackPage() {
        val link = "intent://facebook.com/share/v/abc/#Intent;scheme=fb;" +
            "package=com.facebook.katana;" +
            "S.browser_fallback_url=" +
            "https%3A%2F%2Fm.facebook.com%2Fshare%2Fv%2Fabc%2F%3Fwtsid%3D1;end"

        assertEquals(
            AppLinkPolicy.Decision.Fallback("https://m.facebook.com/share/v/abc/?wtsid=1"),
            AppLinkPolicy.decide(link),
        )
    }

    @Test
    fun appLinksWithoutASafeWebPageAreIgnored() {
        val ignored = listOf(
            "fb://watch/?v=1",
            "market://details?id=com.facebook.katana",
            "snssdk1233://aweme/detail/1",
            "intent://watch/1#Intent;scheme=fb;package=com.facebook.katana;end",
            "intent://watch/1#Intent;S.browser_fallback_url=http%3A%2F%2Fexample.test;end",
            "intent://watch/1#Intent;S.browser_fallback_url=javascript%3Aalert(1);end",
            "javascript:alert(1)",
            "file:///sdcard/clip.mp4",
        )

        ignored.forEach { link ->
            assertEquals(link, AppLinkPolicy.Decision.Ignore, AppLinkPolicy.decide(link))
        }
    }
}
