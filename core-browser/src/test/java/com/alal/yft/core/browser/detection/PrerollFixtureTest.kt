package com.alal.yft.core.browser.detection

import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageVideoFacts
import com.alal.yft.core.model.media.PlayingVideo
import com.alal.yft.extractor.generic.normalizer.CandidateNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P28: the owner's Preview #4 case on a fixture page. The page states its video (16:24) in
 * JSON-LD naming an embed page, not a file; its player plays a 30 s 1080p ad first and loads
 * the page's HLS master afterwards. Download during the ad opens the HLS with the page's title
 * and picture; the ad stays under Other videos. On the old code the playing 0:30 ad wins.
 */
class PrerollFixtureTest {
    private val page = "https://tube.example.test/watch/77"
    private val ad = "https://cdn.adnet.example.test/creatives/spring-30s.mp4"
    private val master = "https://stream.example.test/v77/master.m3u8"

    @Test
    fun theHtmlStatesTheVideosLengthTitleAndPicture() {
        val result = HtmlMediaScanner().scan(fixture(), page, observedAtEpochMs = 0)

        assertEquals(
            PageVideoFacts(
                durationMillis = 984_000,
                title = "Harbour lights at dusk",
                thumbnailUrl = "https://img.example.test/v77/poster.jpg",
            ),
            result.facts,
        )
        // The embed page is not a file, so the page names no file of its own.
        assertTrue(result.candidates.none { it.mediaUrl.contains("/embed/") })
        assertTrue(result.candidates.any { it.mediaUrl == ad })
    }

    @Test
    fun downloadDuringTheAdOpensThePagesVideoWithItsTitleAndPicture() {
        val scan = HtmlMediaScanner().scan(fixture(), page, observedAtEpochMs = 0)
        val facts = scan.facts
        // What the browser adds while the ad plays: the element's length (its DOM probe), its
        // own request of the ad, then the page's HLS master and its manifest's length.
        val seen = scan.candidates.map { candidate ->
            if (candidate.mediaUrl == ad) candidate.copy(durationMillis = 30_000) else candidate
        } + listOf(
            request(ad, at = 1_000).copy(height = 1_080),
            request(master, at = 4_000, kind = MediaKind.HLS)
                .copy(durationMillis = 984_480, height = 720),
        )
        val candidates = MediaGroups.withPageRoles(
            CandidateNormalizer().normalize(page, seen),
            facts,
        )
        val videos = MediaGroups.pageVideos(candidates)
        val playing = PlayingVideo(url = ad, durationMillis = 30_000, height = 1_080)

        val main = MediaGroups.mainVideo(videos, playing, facts)
            ?.let { MediaGroups.withPageFacts(it, facts) }

        assertEquals(listOf(master), main?.candidates?.map { it.mediaUrl })
        assertEquals("Harbour lights at dusk", main?.title)
        assertEquals(
            "https://img.example.test/v77/poster.jpg",
            main?.candidates?.single()?.thumbnailUrl,
        )
        assertEquals(984_480L, main?.durationMillis)
        val list = MediaGroups.ofPage(videos, facts)
        assertEquals(listOf(master), list.videos.flatMap { it.candidates }.map { it.mediaUrl })
        assertTrue(list.previews.flatMap { it.candidates }.any { it.mediaUrl == ad })
        // The page's thumbnails' clips are previews too.
        assertTrue(list.previews.flatMap { it.candidates }.any { "/previews/" in it.mediaUrl })
    }

    private fun request(url: String, at: Long, kind: MediaKind = MediaKind.DIRECT) =
        MediaCandidate(
            pageUrl = page,
            mediaUrl = url,
            sources = setOf(CandidateSource.REQUEST),
            kind = kind,
            observedAtEpochMs = at,
        )

    private fun fixture(): String {
        val resource = requireNotNull(javaClass.getResourceAsStream("/fixtures/p28-preroll.html"))
        return resource.bufferedReader().use { it.readText() }
    }
}
