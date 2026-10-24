package com.alal.yft.core.download

import com.alal.yft.core.model.download.AudioVideoMuxDownloadPlan
import com.alal.yft.core.model.download.DashDownloadPlan
import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DownloadFailure
import com.alal.yft.core.model.download.DownloadFailureDetails
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadFailureStage
import com.alal.yft.core.model.download.DownloadPlan
import com.alal.yft.core.model.download.WholeFileTrack
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaTrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** F6: a failed YouTube media link's facts, without the link or any of its values. */
class GoogleVideoLinkFactsTest {
    @Test
    fun aDirectLinkSaysItsClientStreamTokenSignatureAndTimes() {
        val plan = direct(link(client = "WEB", itag = 18, pot = false, sig = true))

        val facts = GoogleVideoLinkFacts.describe(
            plan = plan.copy(expiresAtEpochMs = NOW + 100 * MINUTE),
            nowEpochMs = NOW,
            downloadedBytes = 0,
            ageMs = 3_400,
        )

        assertEquals(
            "WEB itag 18 · pot no · sig yes · ends 352 min (app 100) · 0 KB · 3 s",
            facts,
        )
        assertEquals("A sanitized copy reads the same", facts, DownloadFailureDetails.sanitize(facts))
    }

    @Test
    fun aMergedDownloadNamesTheVideoStreamFirstAndTheEarliestEnd() {
        val video = dash(link("MWEB", 136, pot = true, sig = false), MediaTrackType.VIDEO)
        val audio = dash(
            link("MWEB", 140, pot = true, sig = false, expireMinutes = -2),
            MediaTrackType.AUDIO,
        )
        val plan = AudioVideoMuxDownloadPlan("mux", video, audio, "Video.mp4")

        val facts = GoogleVideoLinkFacts.describe(plan, NOW, 5 * 1_024, 12 * MINUTE)

        assertEquals(
            "MWEB itag 136+140 · pot yes · sig no · ends -2 min (app none) · 5 KB · 12 min",
            facts,
        )
    }

    @Test
    fun otherServersAndBrokenLinksHaveNoFacts() {
        val other = direct("https://media.example.test/v.mp4?c=WEB&itag=18&expire=1")
        assertNull(GoogleVideoLinkFacts.describe(other, NOW, 0, 0))
        assertNull(GoogleVideoLinkFacts.describe(direct("not a link"), NOW, 0, 0))
        val lookalike = direct("https://googlevideo.com.example.test/videoplayback?c=WEB")
        assertNull(GoogleVideoLinkFacts.describe(lookalike, NOW, 0, 0))
    }

    @Test
    fun noValueOfTheLinkIsRepeated() {
        val url = "https://rr3---sn-abc.googlevideo.com/videoplayback?expire=notanumber" +
            "&c=web%3Dx&itag=18abc&pot=SECRETPOTVALUE123&sig=SECRETSIG456&ip=10.0.0.2"

        val facts = GoogleVideoLinkFacts.describe(direct(url), NOW, 0, 0).orEmpty()

        assertEquals("? itag ? · pot yes · sig yes · ends ? min (app none) · 0 KB · 0 s", facts)
        for (leak in listOf("SECRET", "10.0.0.2", "googlevideo", "web", "abc")) {
            assertFalse("$leak must not be repeated", facts.contains(leak))
        }
    }

    /**
     * Track B1: YouTube's media host refused a link that still had time left (HTTP 403 while
     * connecting): not an expired link. D7 runs 13 / 13b: `ends 359 min (app 358)`, yet the
     * download said "Link expired".
     */
    @Test
    fun aRefusalWhileConnectingWithTimeLeftIsYouTubesRefusal() {
        val visionOs = direct(link("VISIONOS", 18, pot = false, sig = true, expireMinutes = 359))
        assertTrue(refusal(visionOs.copy(expiresAtEpochMs = NOW + 358 * MINUTE)))
        // No end known to the app: the link's own end counts.
        assertTrue(refusal(visionOs))
        // A merged download: each link has a minute or more left (exactly one minute counts).
        assertTrue(refusal(merged(videoMinutes = 359, audioMinutes = 1)))
    }

    @Test
    fun anEndingLinkAnotherServerOrAnotherFailureIsNotYouTubesRefusal() {
        val fresh = direct(link("WEB", 18, pot = false, sig = true))
        assertTrue("the fixture itself is a refusal", refusal(fresh))
        // The link's own end: under a minute away, now, or passed (merged: either link).
        val soon = "$HOST?expire=${(NOW + 59_000) / 1_000}&c=WEB&itag=18"
        assertFalse(refusal(direct(soon)))
        assertFalse(refusal(direct(link("WEB", 18, pot = false, sig = true, expireMinutes = 0))))
        assertFalse(refusal(direct(link("WEB", 18, pot = false, sig = true, expireMinutes = -5))))
        assertFalse(refusal(merged(videoMinutes = 359, audioMinutes = -2)))
        // The app's own end for the link: passed or under a minute away.
        assertFalse(refusal(fresh.copy(expiresAtEpochMs = NOW - MINUTE)))
        assertFalse(refusal(fresh.copy(expiresAtEpochMs = NOW + 30_000)))
        // Another server, a look-alike host, a YouTube link without its end, no link at all.
        val later = (NOW + 60 * MINUTE) / 1_000
        assertFalse(refusal(direct("https://media.example.test/v.mp4?expire=$later")))
        assertFalse(refusal(direct("https://googlevideo.com.example.test/videoplayback?expire=$later")))
        assertFalse(refusal(direct("$HOST?c=WEB&itag=18")))
        assertFalse(refusal(direct("not a link")))
        // Another failure: no status, not while connecting, another reason or status.
        assertFalse(refusal(fresh, REFUSED.copy(httpStatusCode = null)))
        assertFalse(refusal(fresh, REFUSED.copy(stage = DownloadFailureStage.READ_SOURCE)))
        assertFalse(refusal(fresh, REFUSED.copy(stage = null)))
        assertFalse(refusal(fresh, failure(DownloadFailureReason.GONE, 410)))
        assertFalse(refusal(fresh, failure(DownloadFailureReason.EXPIRED_URL, 403)))
        assertFalse(refusal(fresh, failure(DownloadFailureReason.NETWORK, null)))
    }

    private fun refusal(plan: DownloadPlan, failure: DownloadFailure = REFUSED): Boolean =
        GoogleVideoLinkFacts.refusedWithTimeLeft(plan, failure, NOW)

    private fun failure(reason: DownloadFailureReason, status: Int?) =
        DownloadFailure(reason, status, DownloadFailureStage.CONNECT)

    private fun merged(videoMinutes: Long, audioMinutes: Long) = AudioVideoMuxDownloadPlan(
        "mux",
        dash(link("VISIONOS", 134, pot = false, sig = true, expireMinutes = videoMinutes), MediaTrackType.VIDEO),
        dash(link("VISIONOS", 140, pot = false, sig = true, expireMinutes = audioMinutes), MediaTrackType.AUDIO),
        "Video.mp4",
    )

    private fun link(
        client: String,
        itag: Int,
        pot: Boolean,
        sig: Boolean,
        expireMinutes: Long = 352,
    ): String = buildString {
        append(HOST)
        append("?expire=")
        append((NOW + expireMinutes * MINUTE) / 1_000)
        append("&ei=fixture&c=")
        append(client)
        append("&itag=")
        append(itag)
        append("&n=fixture")
        if (sig) append("&sig=fixture")
        if (pot) append("&pot=fixture")
    }

    private fun direct(url: String) = DirectDownloadPlan(
        taskId = "direct",
        sourceUrl = url,
        suggestedFileName = "Video.mp4",
        requestContext = CONTEXT,
    )

    private fun dash(url: String, type: MediaTrackType) = DashDownloadPlan(
        taskId = "dash-$type",
        manifestUrl = url,
        representationId = type.name,
        trackType = type,
        suggestedFileName = "Video.mp4",
        requestContext = CONTEXT,
        wholeFile = WholeFileTrack(),
    )

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val MINUTE = 60_000L
        const val HOST = "https://rr1---sn-fixture.googlevideo.com/videoplayback"
        val REFUSED = DownloadFailure(
            reason = DownloadFailureReason.ACCESS_DENIED,
            httpStatusCode = 403,
            stage = DownloadFailureStage.CONNECT,
            detail = "403×4 at 1 MiB, 3 at once, 3 s",
        )
        val CONTEXT = BrowserRequestContext(
            pageUrl = "https://www.youtube.com/watch?v=fixture",
            userAgent = "fixture",
            cookie = null,
        )
    }
}
