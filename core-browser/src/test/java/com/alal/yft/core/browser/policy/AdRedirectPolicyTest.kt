package com.alal.yft.core.browser.policy

import com.alal.yft.core.browser.policy.AdRedirectPolicy.Decision
import com.alal.yft.core.browser.policy.AdRedirectPolicy.Reason
import com.alal.yft.core.browser.policy.AdRedirectPolicy.WindowDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdRedirectPolicyTest {
    private data class Row(
        val name: String,
        val url: String,
        val pageUrl: String? = PAGE,
        val hasGesture: Boolean = false,
        val isRedirect: Boolean = false,
        val pageOpen: Boolean = true,
        val expected: Decision,
    )

    @Test
    fun topLevelNavigationsFollowTheTable() {
        val rows = listOf(
            Row("a tap on a link to another site", "https://news.other.test/story",
                hasGesture = true, expected = Decision.Allow),
            Row("the page sends the tab to another site by itself", "https://win.other.test/",
                expected = Decision.Block("win.other.test", Reason.NO_TAP)),
            Row("the same site without a tap", "https://m.example.test/watch?v=1",
                expected = Decision.Allow),
            Row("a subdomain of the same site", "https://video.example.test/1",
                pageUrl = "https://www.example.test/", expected = Decision.Allow),
            Row("a server redirect of the user's navigation", "https://login.other.test/",
                isRedirect = true, expected = Decision.Allow),
            Row("a page that forwards while it opens", "https://target.other.test/",
                pageOpen = false, expected = Decision.Allow),
            Row("a listed network after a tap", "https://popads.net/click?id=1",
                hasGesture = true, expected = Decision.Block("popads.net", Reason.AD_NETWORK)),
            Row("a subdomain of a listed network", "https://c2.onclkds.com/pop",
                hasGesture = true,
                expected = Decision.Block("c2.onclkds.com", Reason.AD_NETWORK)),
            Row("a listed redirect hop", "https://www.adsterra.com/landing",
                isRedirect = true,
                expected = Decision.Block("www.adsterra.com", Reason.AD_NETWORK)),
            Row("a listed network while the page opens", "https://notix.io/allow",
                pageOpen = false, expected = Decision.Block("notix.io", Reason.AD_NETWORK)),
            Row("a host that only ends like a listed one", "https://notpopads.net/",
                hasGesture = true, expected = Decision.Allow),
            Row("before any page", "https://start.other.test/", pageUrl = null,
                expected = Decision.Allow),
            Row("an address without a host", "about:blank", expected = Decision.Allow),
            Row("a country second level: same site", "https://shop.example.com.mm/item",
                pageUrl = "https://www.example.com.mm/", expected = Decision.Allow),
            Row("a country second level: other site", "https://other.com.mm/",
                pageUrl = "https://www.example.com.mm/",
                expected = Decision.Block("other.com.mm", Reason.NO_TAP)),
            Row("bbc.co.uk and another .co.uk site", "https://spam.co.uk/",
                pageUrl = "https://www.bbc.co.uk/news",
                expected = Decision.Block("spam.co.uk", Reason.NO_TAP)),
            Row("two people's github.io sites", "https://bob.github.io/",
                pageUrl = "https://alice.github.io/",
                expected = Decision.Block("bob.github.io", Reason.NO_TAP)),
        )
        for (row in rows) {
            assertEquals(
                row.name,
                row.expected,
                AdRedirectPolicy.decide(
                    row.url,
                    row.pageUrl,
                    row.hasGesture,
                    row.isRedirect,
                    row.pageOpen,
                ),
            )
        }
    }

    @Test
    fun newWindowsOpenHereOnlyForTheUsersTapToTheSameSite() {
        assertEquals(
            WindowDecision.OpenHere,
            AdRedirectPolicy.decideWindow("https://m.example.test/live", PAGE, true),
        )
        assertEquals(
            WindowDecision.Block("news.other.test", Reason.NO_TAP),
            AdRedirectPolicy.decideWindow("https://news.other.test/", PAGE, true),
        )
        assertEquals(
            WindowDecision.Block("m.example.test", Reason.NO_TAP),
            AdRedirectPolicy.decideWindow("https://m.example.test/live", PAGE, false),
        )
        assertEquals(
            WindowDecision.Block("go.clickadu.com", Reason.AD_NETWORK),
            AdRedirectPolicy.decideWindow("https://go.clickadu.com/", CLICKADU_PAGE, true),
        )
        assertEquals(
            WindowDecision.Block("", Reason.NO_TAP),
            AdRedirectPolicy.decideWindow("https://", PAGE, true),
        )
    }

    @Test
    fun onlyTheListedNetworksThatDoNotServeOtherAdsLoseTheirScripts() {
        assertTrue(AdRedirectPolicy.blocksResource("c1.popads.net"))
        assertTrue(AdRedirectPolicy.blocksResource("POPCASH.NET"))
        assertFalse(AdRedirectPolicy.blocksResource("syndication.exoclick.com"))
        assertFalse(AdRedirectPolicy.blocksResource("cdn.example.test"))
        assertFalse(AdRedirectPolicy.blocksResource(null))
    }

    @Test
    fun sitesAreTheirRegistrableName() {
        assertEquals("youtube.com", AdRedirectPolicy.siteOf("m.youtube.com"))
        assertEquals("bbc.co.uk", AdRedirectPolicy.siteOf("www.bbc.co.uk."))
        assertEquals("example.com.mm", AdRedirectPolicy.siteOf("a.b.example.com.mm"))
        assertEquals("10.0.0.2", AdRedirectPolicy.siteOf("10.0.0.2"))
        assertEquals("localhost", AdRedirectPolicy.siteOf("localhost"))
        assertEquals("example.test", AdRedirectPolicy.hostOf(" https://Example.TEST/a "))
        assertNull(AdRedirectPolicy.hostOf("not a url"))
    }

    @Test
    fun everyListedNetworkSaysWhyAndNoHostIsListedTwice() {
        val hosts = AdNetworks.all.flatMap { it.hosts }
        assertEquals(hosts.size, hosts.toSet().size)
        assertTrue(hosts.size in 20..60)
        for (network in AdNetworks.all) {
            assertTrue(network.name, network.reason.isNotBlank())
            for (host in network.hosts) {
                assertEquals(host, host.lowercase())
                assertNotNull(host, AdNetworks.find("x.$host"))
            }
        }
    }

    private companion object {
        const val PAGE = "https://m.example.test/watch?v=0"
        const val CLICKADU_PAGE = "https://www.clickadu.com/"
    }
}
