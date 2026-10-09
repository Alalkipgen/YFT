package com.alal.yft.core.browser.detection

import com.alal.yft.core.model.media.AdSign
import com.alal.yft.core.model.media.PageMediaRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P43: the signs that a file is an ad's (its host, its folder), the requests that start an ad
 * break (Google IMA's, a VAST answer asked for by its query, a pre-roll request) and the answers
 * that are VAST or VMAP documents; the ad files after them carry [AdSign.AD_BREAK].
 */
class AdHostsTest {
    private val page = "https://tube.example.test/watch/43"

    private fun request(url: String, at: Long = 0, referer: String? = null) = RequestObservation(
        pageUrl = page,
        requestUrl = url,
        method = "GET",
        headers = referer?.let { mapOf("Referer" to it) }.orEmpty(),
        userAgent = null,
        cookie = null,
        observedAtEpochMs = at,
    )

    @Test
    fun aVideoAdNetworksFileIsAnAdUnderAnyOfItsHosts() {
        listOf(
            "https://cdn.adtng.com/a/30s.mp4",
            "https://video.adtng.test/creative/30s.mp4",
            "https://static.exdynsrv.com/v.mp4",
            "https://ads.example.test/v/1.mp4",
            "https://pubads.g.doubleclick.net/v/2.mp4",
        ).forEach { assertEquals(it, AdSign.AD_HOST, AdHosts.adSign(it)) }
        // A label that merely contains a network's name is not the network.
        assertNull(AdHosts.adSign("https://myadtngfan.example.test/v.mp4"))
        assertNull(AdHosts.adSign("https://media.tube.example.test/v/43/720.mp4"))
        assertFalse(AdHosts.isAdHost(null))
        assertFalse(AdHosts.isAdHost(""))
    }

    @Test
    fun aFileInAWholeAdFolderIsAnAd() {
        assertEquals(
            AdSign.AD_ADDRESS,
            AdHosts.adSign("https://media.tube.example.test/ads/pre.mp4"),
        )
        assertEquals(
            AdSign.AD_ADDRESS,
            AdHosts.adSign("https://media.tube.example.test/preroll/1.mp4"),
        )
        assertNull(AdHosts.adSign("https://media.tube.example.test/v/the-vast-ocean.mp4"))
        assertNull(AdHosts.adSign("not a url"))
    }

    @Test
    fun googleImaAndDoubleClickAdRequestsStartAnAdBreak() {
        listOf(
            "https://pubads.g.doubleclick.net/gampad/ads?iu=/1/video&sz=640x480&env=vp",
            "https://securepubads.g.doubleclick.net/gampad/live/ads?iu=/2/pre",
            "https://googleads.g.doubleclick.net/pagead/ads?client=ca-video-pub-1",
            "https://ad.doubleclick.net/ddm/pfadx/N1.site/B2;sz=0x0",
        ).forEach { assertTrue(it, AdHosts.isAdBreakRequest(it)) }
        // Their other requests (a view, a picture) are not ad requests.
        assertFalse(AdHosts.isAdBreakRequest("https://pubads.g.doubleclick.net/pagead/adview?x=1"))
        assertFalse(AdHosts.isAdBreakRequest("https://googleads.g.doubleclick.net/pagead/id"))
    }

    @Test
    fun aVastQueryOrAPrerollOrAdsRequestStartsAnAdBreak() {
        listOf(
            "https://player.example.test/api/serve?zone=7&output=vast",
            "https://player.example.test/api/serve?format=xml_vast4",
            "https://player.example.test/api/serve?type=vmap&id=1",
            "https://player.example.test/preroll?zone=1",
            "https://player.example.test/v2/pre-roll.xml",
            "https://player.example.test/ads/request?slot=pre",
        ).forEach { assertTrue(it, AdHosts.isAdBreakRequest(it)) }
    }

    @Test
    fun theAdsOwnFileItsTrackingAndPageAssetsDoNotStartAnAdBreak() {
        listOf(
            "https://media.tube.example.test/preroll/clip.mp4",
            "https://media.tube.example.test/ads/pre.m3u8",
            "https://player.example.test/ads/banner.js",
            "https://player.example.test/ads/logo.png",
            "https://player.example.test/ads/track/impression?zone=7",
            "https://player.example.test/preroll/event?type=complete",
            "https://player.example.test/v/the-vast-ocean.mp4",
            "https://player.example.test/ads",
            "ftp://player.example.test/vast.xml",
            "not a url",
        ).forEach { assertFalse(it, AdHosts.isAdBreakRequest(it)) }
    }

    @Test
    fun aVastOrVmapAnswerIsAnAdBreaks() {
        assertTrue(
            AdHosts.isAdBreakAnswer(
                "application/xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<VAST version=\"4.0\"><Ad>",
            ),
        )
        assertTrue(AdHosts.isAdBreakAnswer(null, "<!-- served --> <VMAP version=\"1.0\">"))
        assertTrue(
            AdHosts.isAdBreakAnswer(
                "text/xml; charset=utf-8",
                "\uFEFF  <vmap:VMAP xmlns:vmap=\"http://www.iab.net/videosuite/vmap\">",
            ),
        )
        assertTrue(AdHosts.isAdBreakAnswer("application/vast+xml", "<VAST version=\"3.0\"/>"))
        assertFalse(AdHosts.isAdBreakAnswer("application/json", "<VAST version=\"4.0\">"))
        assertFalse(AdHosts.isAdBreakAnswer("text/xml", "<html><body>VAST</body></html>"))
        assertFalse(AdHosts.isAdBreakAnswer("text/xml", "<VASTLY/>"))
        assertFalse(AdHosts.isAdBreakAnswer("text/xml", null))
    }

    @Test
    fun theMapperMarksAnAdNetworksRequestAndItsFramesFiles() {
        val own = BrowserObservationMapper.fromRequest(
            request("https://cdn.adtng.com/a/30s.mp4"),
        )!!
        val framed = BrowserObservationMapper.fromRequest(
            request("https://cdn.other.test/30s.mp4", referer = "https://ads.example.test/frame"),
        )!!
        val page = BrowserObservationMapper.fromRequest(
            request("https://media.tube.example.test/v/43/720.mp4"),
        )!!

        assertEquals(AdSign.AD_HOST, own.adSign)
        assertEquals(PageMediaRole.PREVIEW, own.pageRole)
        assertEquals(AdSign.AD_HOST, framed.adSign)
        assertNull(page.adSign)
        assertNull(page.pageRole)
        assertTrue(BrowserObservationMapper.isAdBreakAnswer("text/xml", "<VAST version=\"2.0\">"))
    }

    @Test
    fun theMapperKeepsWhatThePagesMarkupSaysAboutAFileItNames() {
        fun dom(url: String, role: PageMediaRole?) = DomMediaObservation(
            pageUrl = page,
            mediaUrl = url,
            mimeType = "video/mp4",
            title = null,
            thumbnailUrl = null,
            durationMillis = null,
            observedAtEpochMs = 0,
            pageRole = role,
        )

        val named = BrowserObservationMapper.fromDom(
            dom("https://ads.example.test/main.mp4", PageMediaRole.MAIN),
        )!!
        val ad = BrowserObservationMapper.fromDom(dom("https://cdn.adtng.com/a.mp4", null))!!

        assertNull(named.adSign)
        assertEquals(PageMediaRole.MAIN, named.pageRole)
        assertEquals(AdSign.AD_HOST, ad.adSign)
    }

    @Test
    fun theFileAfterAnImaRequestCarriesTheAdBreakSign() {
        val tracker = VastAdTracker()
        tracker.beginPage(page)
        val imaRequest = "https://pubads.g.doubleclick.net/gampad/ads?iu=/1/v"
        tracker.onRequest(request(imaRequest, at = 1_000))

        val ad = marked(tracker, request("https://cdn.adnet.test/creative.mp4", at = 2_000))

        assertEquals(AdSign.AD_BREAK, ad.adSign)
        assertEquals(PageMediaRole.PREVIEW, ad.pageRole)
    }

    @Test
    fun aVastAnswerStartsAnAdBreakWhenItsAddressSaysNothing() {
        val tracker = VastAdTracker()
        tracker.beginPage(page)
        val config = request("https://player.example.test/api/v1/config?id=7", at = 1_000)
        tracker.onAnswer(config, "application/json", "{\"vast\": true}")
        assertNull(marked(tracker, request("https://cdn.adnet.test/early.mp4", at = 1_500)).adSign)

        tracker.onAnswer(config, "text/xml", "<?xml version=\"1.0\"?><VAST version=\"4.1\">")
        val ad = marked(tracker, request("https://cdn.adnet.test/creative.mp4", at = 2_000))
        val own = marked(tracker, request("https://media.tube.example.test/v/43.mp4", at = 2_100))

        assertEquals(AdSign.AD_BREAK, ad.adSign)
        assertNull(own.adSign)
        assertNull(own.pageRole)
    }

    private fun marked(tracker: VastAdTracker, observation: RequestObservation) =
        tracker.marked(BrowserObservationMapper.fromRequest(observation)!!, observation)
}
