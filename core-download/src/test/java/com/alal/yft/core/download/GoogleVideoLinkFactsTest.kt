package com.alal.yft.core.download

import com.alal.yft.core.model.download.AudioVideoMuxDownloadPlan
import com.alal.yft.core.model.download.DashDownloadPlan
import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DownloadFailureDetails
import com.alal.yft.core.model.download.WholeFileTrack
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaTrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    private fun link(
        client: String,
        itag: Int,
        pot: Boolean,
        sig: Boolean,
        expireMinutes: Long = 352,
    ): String = buildString {
        append("https://rr1---sn-fixture.googlevideo.com/videoplayback?expire=")
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
        val CONTEXT = BrowserRequestContext(
            pageUrl = "https://www.youtube.com/watch?v=fixture",
            userAgent = "fixture",
            cookie = null,
        )
    }
}
