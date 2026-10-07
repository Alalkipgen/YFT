package com.alal.yft.core.browser.detection

import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageMediaRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P28: the ad networks of free video sites, and the ad file a player fetches after VAST. */
class AdBreakTest {
    private val page = "https://tube.example.test/watch/77"

    private fun request(
        url: String,
        at: Long,
        referer: String? = null,
        pageUrl: String = page,
    ) = RequestObservation(
        pageUrl = pageUrl,
        requestUrl = url,
        method = "GET",
        headers = referer?.let { mapOf("Referer" to it) }.orEmpty(),
        userAgent = null,
        cookie = null,
        observedAtEpochMs = at,
    )

    @Test
    fun theConfirmedAdNetworksFilesAreAds() {
        listOf(
            "https://s.magsrv.com/v1/creative.mp4",
            "https://a.realsrv.com/video/1.mp4",
            "https://cdn.exoclick.com/x/2.mp4",
            "https://main.exosrv.com/3.mp4",
            "https://ht-cdn.trafficjunky.net/4.mp4",
            "https://ads.juicyads.com/5.mp4",
            "https://jads.co/6.mp4",
            "https://cdn.tsyndicate.com/7.mp4",
            "https://cdn.trafficstars.com/8.mp4",
            "https://cdn.adsterra.com/9.mp4",
        ).forEach { url ->
            assertEquals(url, PageMediaRole.PREVIEW, BrowserObservationMapper.adRole(url, null))
        }
        // Whole domains only: a name that merely contains one is not an ad network.
        assertNull(BrowserObservationMapper.adRole("https://xjads.com/v.mp4", null))
        assertNull(BrowserObservationMapper.adRole("https://tube.example.test/v/1.mp4", null))
        assertNull(BrowserObservationMapper.adRole("https://notmagsrv.com.example.test/a", null))
    }

    @Test
    fun anAdBreakIsAskedForByAVastOrVmapDocument() {
        listOf(
            "https://ads.example.test/vast.xml?zone=7",
            "https://ads.example.test/serve/vast",
            "https://ads.example.test/v1/vast3.xml",
            "https://ads.example.test/vmap.php?id=1",
        ).forEach { assertTrue(it, BrowserObservationMapper.isAdBreakRequest(it)) }
        listOf(
            "https://cdn.example.test/videos/vast-ocean.mp4",
            "https://cdn.example.test/the-vast-ocean/index.m3u8",
            "https://cdn.example.test/vastly/1.mp4",
            "ftp://ads.example.test/vast.xml",
        ).forEach { assertFalse(it, BrowserObservationMapper.isAdBreakRequest(it)) }
    }

    @Test
    fun theFileFetchedRightAfterAnAdBreakFromAnotherSiteIsTheAd() {
        // Sites by their last two labels: tube.example.test is example.test, the ad adnet.test.
        val tracker = VastAdTracker()
        tracker.beginPage(page)
        val before = request("https://cdn.other.test/early.mp4", at = 500)
        assertNull(marked(tracker, before))

        tracker.onRequest(request("https://ads.example.test/vast.xml", at = 1_000))
        val adFile = request("https://cdn.adnet.test/creative.mp4", at = 3_000)
        val ownFile = request("https://media.tube.example.test/v77.mp4", at = 3_100)
        val stream = request("https://cdn.adnet.test/ad.m3u8", at = 3_200)
        val late = request("https://cdn.other.test/late.mp4", at = 7_001)

        assertEquals(PageMediaRole.PREVIEW, marked(tracker, adFile))
        // The page's own site, streams, a file seen before and one too late are not marked.
        assertNull(marked(tracker, ownFile))
        assertNull(marked(tracker, stream))
        assertNull(marked(tracker, before.copy(observedAtEpochMs = 3_300)))
        assertNull(marked(tracker, late))
        // A file once marked stays marked when its next piece is fetched.
        val again = adFile.copy(observedAtEpochMs = 9_000)
        assertEquals(PageMediaRole.PREVIEW, marked(tracker, again))
    }

    @Test
    fun onlyTheFrameThatAskedForTheAdBreakFetchesItsAd() {
        val tracker = VastAdTracker()
        tracker.beginPage(page)
        val player = "https://player.example.test/"
        tracker.onRequest(request("https://ads.example.test/vast.xml", at = 1_000, player))

        val otherFrame = request("https://cdn.adnet.test/a.mp4", at = 2_000)
        val sameFrame = request(
            "https://cdn.adnet.test/b.mp4",
            at = 2_000,
            referer = "https://player.example.test/embed/77",
        )

        assertNull(marked(tracker, otherFrame))
        assertEquals(PageMediaRole.PREVIEW, marked(tracker, sameFrame))
        // Another page starts with nothing seen.
        tracker.beginPage("https://tube.example.test/watch/78")
        assertNull(marked(tracker, sameFrame.copy(pageUrl = "https://tube.example.test/watch/78")))
    }

    private fun marked(tracker: VastAdTracker, observation: RequestObservation): PageMediaRole? {
        val candidate = BrowserObservationMapper.fromRequest(observation) ?: return null
        if (candidate.kind != MediaKind.DIRECT && candidate.kind != MediaKind.HLS) return null
        return tracker.marked(candidate, observation).pageRole
    }
}
