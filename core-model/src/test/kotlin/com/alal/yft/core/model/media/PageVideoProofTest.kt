package com.alal.yft.core.model.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P43 (R34): on a site without an adapter one rule tells the page's own video from an ad: the
 * page's player names it, or its length matches the page's (or the failed video's); an ad
 * network's file, a file fetched inside an ad break, a file marked as an ad or a short file on a
 * long page is an ad. STRICT offers only what is proven or measured; LENIENT is today's rule.
 */
class PageVideoProofTest {
    private val page = "https://clips.example.test/watch/43"
    private val tenOhFive = PageVideoFacts(durationMillis = 605_000)

    private fun file(
        url: String = "https://media.example.test/v43/720.mp4",
        length: Long? = null,
        role: PageMediaRole? = null,
        key: String? = null,
        adSign: AdSign? = null,
        videoId: String? = null,
    ) = MediaCandidate(
        pageUrl = page,
        mediaUrl = url,
        sources = setOf(CandidateSource.REQUEST),
        kind = MediaKind.DIRECT,
        durationMillis = length,
        pageRole = role,
        pageVideoKey = key,
        adSign = adSign,
        videoId = videoId,
    )

    private fun video(vararg files: MediaCandidate) = MediaGroup("v", null, files.toList())

    @Test
    fun aSiteAdaptersVideoIsThePagesWhateverItsLength() {
        val proof = PageVideoProof(facts = tenOhFive)

        val verdict = proof.of(video(file(length = 30_000, videoId = "site:1")))

        assertEquals(VideoProof.PageVideo(listOf(PageVideoReason.ADAPTER), 30_000), verdict)
        assertEquals(emptyList<String>(), proof.chosenLines(verdict))
    }

    @Test
    fun anAdNetworksFileIsAnAdEvenWhenThePlayerNamesIt() {
        val proof = PageVideoProof(facts = tenOhFive)

        val host = proof.of(video(file(length = 605_000, key = "main", adSign = AdSign.AD_HOST)))
        val address = proof.of(video(file(adSign = AdSign.AD_ADDRESS)))

        assertEquals(VideoProof.Ad(AdReason.AD_HOST, 605_000), host)
        assertEquals(VideoProof.Ad(AdReason.AD_ADDRESS, null), address)
        assertEquals("skipped: 10:05 ad (ad host)", proof.skippedLine(host))
        assertEquals("skipped: ad (ad address)", proof.skippedLine(address))
    }

    @Test
    fun aNamedVideoIsThePagesUnlessItsLengthContradictsThePage() {
        val proof = PageVideoProof(facts = tenOhFive, adSigns = true)

        val named = proof.of(video(file(key = "main")))
        val measured = proof.of(video(file(role = PageMediaRole.MAIN)), lengthMillis = 603_000)
        val contradicted = proof.of(video(file(key = "main")), lengthMillis = 480_000)

        assertEquals(VideoProof.PageVideo(listOf(PageVideoReason.NAMED), null), named)
        assertEquals(
            VideoProof.PageVideo(
                listOf(PageVideoReason.NAMED, PageVideoReason.PAGE_LENGTH),
                603_000,
            ),
            measured,
        )
        assertEquals(
            listOf("chosen: named by the page's player", "length 10:03 matches the page (10:05)"),
            proof.chosenLines(measured),
        )
        assertEquals(VideoProof.Ad(AdReason.LENGTH, 480_000), contradicted)
        assertEquals(
            "skipped: 8:00, not the page's length (10:05)",
            proof.skippedLine(contradicted),
        )
    }

    @Test
    fun theFailedVideosLengthProvesItsFreshLinkWhenThePageStatesNone() {
        val proof = PageVideoProof(failedLengthMillis = 605_000, adSigns = true)

        val verdict = proof.of(video(file(length = 601_000)))

        assertEquals(
            VideoProof.PageVideo(listOf(PageVideoReason.FAILED_LENGTH), 601_000),
            verdict,
        )
        assertEquals(
            listOf("length 10:01 matches the first video (10:05)"),
            proof.chosenLines(verdict),
        )
    }

    @Test
    fun anAdBreaksOrMarkedOrShortFileIsAnAd() {
        val proof = PageVideoProof(facts = tenOhFive)

        val inBreak = proof.of(video(file(length = 30_000, adSign = AdSign.AD_BREAK)))
        val marked = proof.of(video(file(role = PageMediaRole.PREVIEW)))
        val short = proof.of(video(file()), lengthMillis = 30_000)

        assertEquals(VideoProof.Ad(AdReason.AD_BREAK, 30_000), inBreak)
        assertEquals(VideoProof.Ad(AdReason.MARKED, null), marked)
        assertEquals(VideoProof.Ad(AdReason.SHORT, 30_000), short)
        assertEquals("skipped: 0:30 ad (short)", proof.skippedLine(short))
        assertEquals("skipped: ad (marked as an ad)", proof.skippedLine(marked))
    }

    @Test
    fun anAdBreaksFileOfThePagesLengthIsThePagesVideo() {
        val proof = PageVideoProof(facts = tenOhFive)

        val verdict = proof.of(video(file(length = 605_000, adSign = AdSign.AD_BREAK)))

        assertEquals(VideoProof.PageVideo(listOf(PageVideoReason.PAGE_LENGTH), 605_000), verdict)
    }

    @Test
    fun aShortFileIsAnAdNextToANamedLongVideoOnAPageThatStatesNoLength() {
        val candidates = listOf(
            file(url = "https://media.example.test/main.mp4", length = 605_000, key = "main"),
            file(url = "https://media.example.test/pre.mp4"),
        )
        val proof = PageVideoProof.forPage(candidates, facts = null)

        val verdict = proof.of(video(candidates[1]), lengthMillis = 20_000)

        assertEquals(605_000L, proof.namedLengthMillis)
        assertEquals(VideoProof.Ad(AdReason.SHORT, 20_000), verdict)
    }

    @Test
    fun nothingProvesAFileOfUnknownLengthOnAPageThatStatesNone() {
        val proof = PageVideoProof()

        val verdict = proof.of(video(file()))

        assertEquals(VideoProof.NotProven(null), verdict)
        assertEquals("skipped: length unknown", proof.skippedLine(verdict))
        assertEquals(listOf("chosen: length unknown, no ad sign"), proof.chosenLines(verdict))
    }

    @Test
    fun strictOffersOnlyWhatIsProvenOrMeasured() {
        val strict = PageVideoProof(facts = tenOhFive, adSigns = true, rule = AdRule.STRICT)
        val quiet = strict.copy(adSigns = false)

        assertTrue(strict.offers(VideoProof.PageVideo(listOf(PageVideoReason.NAMED), null)))
        assertFalse(strict.offers(VideoProof.Ad(AdReason.LENGTH, 480_000)))
        assertFalse(strict.offers(VideoProof.Ad(AdReason.SHORT, 30_000)))
        assertFalse(strict.offers(VideoProof.NotProven(null)))
        assertTrue(strict.offers(VideoProof.NotProven(300_000)))
        assertTrue(quiet.offers(VideoProof.NotProven(null)))
    }

    @Test
    fun lenientStillOffersAnotherLengthAndAnUnprovenFileButNoAd() {
        val lenient = PageVideoProof(facts = tenOhFive, adSigns = true, rule = AdRule.LENIENT)

        assertTrue(lenient.offers(VideoProof.Ad(AdReason.LENGTH, 480_000)))
        assertTrue(lenient.offers(VideoProof.NotProven(null)))
        assertFalse(lenient.offers(VideoProof.Ad(AdReason.SHORT, 30_000)))
        assertFalse(lenient.offers(VideoProof.Ad(AdReason.AD_HOST, null)))
        assertEquals(
            listOf("chosen: 8:00, the page states 10:05"),
            lenient.chosenLines(VideoProof.Ad(AdReason.LENGTH, 480_000)),
        )
    }

    @Test
    fun theDefaultRuleIsStrict() {
        assertEquals(AdRule.STRICT, PageVideoProof.DEFAULT_RULE)
        assertEquals(AdRule.STRICT, PageVideoProof().rule)
    }

    @Test
    fun aPageHasAdSignsWhenAFileIsMarkedOrSignedOrMissesItsLength() {
        val plain = listOf(file(length = 605_000), file(url = "https://media.example.test/b.mp4"))

        assertFalse(PageVideoProof.hasAdSigns(plain, tenOhFive))
        assertTrue(PageVideoProof.hasAdSigns(plain + file(role = PageMediaRole.PREVIEW), null))
        assertTrue(PageVideoProof.hasAdSigns(plain + file(adSign = AdSign.AD_BREAK), null))
        assertTrue(PageVideoProof.hasAdSigns(plain + file(length = 30_000), tenOhFive))
        assertFalse(PageVideoProof.hasAdSigns(listOf(file(length = 30_000)), null))
        assertFalse(
            PageVideoProof.hasAdSigns(listOf(file(length = 30_000, videoId = "site:1")), tenOhFive),
        )
        assertTrue(PageVideoProof.forPage(plain, tenOhFive, sawAd = true).adSigns)
    }

    @Test
    fun onlyAdOnlySignsProveAnAdForTheLists() {
        assertTrue(PageVideoProof.isProvenAd(video(file(adSign = AdSign.AD_HOST))))
        val inBreak = video(file(length = 30_000, adSign = AdSign.AD_BREAK))
        assertTrue(PageVideoProof.isProvenAd(inBreak))
        assertFalse(PageVideoProof.isProvenAd(video(file(role = PageMediaRole.PREVIEW))))
        assertFalse(PageVideoProof.isProvenAd(video(file(length = 30_000)), tenOhFive))
        assertFalse(PageVideoProof.isProvenAd(video(file(length = 605_000))))
    }

    @Test
    fun theSameLengthIsWithinFiveSecondsOrFivePercent() {
        assertTrue(PageVideoProof.sameLength(605_000, 600_000))
        assertFalse(PageVideoProof.sameLength(605_000, 570_000))
        assertTrue(PageVideoProof.sameLength(3_600_000, 3_430_000))
        assertFalse(PageVideoProof.sameLength(3_600_000, 3_400_000))
        assertFalse(PageVideoProof.sameLength(605_000, null))
        assertFalse(PageVideoProof.sameLength(605_000, 0))
    }

    @Test
    fun lengthsReadAsAClock() {
        assertEquals("0:30", PageVideoProof.clock(30_000))
        assertEquals("10:05", PageVideoProof.clock(605_400))
        assertEquals("1:02:03", PageVideoProof.clock(3_723_000))
        assertEquals("?", PageVideoProof.clock(null))
    }

    @Test
    fun thePagesListsCanLeaveOutProvenAds() {
        val main = file(url = "https://media.example.test/main.mp4", length = 605_000)
        val ad = file(url = "https://ads.example.test/pre.mp4", adSign = AdSign.AD_HOST)
        val groups = listOf(
            MediaGroup("main", null, listOf(main)),
            MediaGroup("ad", null, listOf(ad)),
        )

        val all = MediaGroups.ofPage(groups, tenOhFive)
        val hidden = MediaGroups.ofPage(groups, tenOhFive, hideAds = true)

        assertEquals(listOf("main", "ad"), (all.videos + all.previews).map { it.key })
        assertEquals(listOf("main"), (hidden.videos + hidden.previews).map { it.key })
    }
}
