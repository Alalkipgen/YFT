package com.alal.yft.core.model.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Live stream set aside and the Found lists' use of the page's stated length. */
class MediaGroupsStreamPiecesTest {
    private val page = "https://tube.test/watch/5"
    private val live = StreamPieces.of(true, listOf("https://cam.test/b-hls-03/41/x1.mp4"))
    private val ended = StreamPieces.of(false, listOf("https://vod.test/hls/5/seg-1.ts"))

    private fun hls(url: String, pieces: StreamPieces? = null, duration: Long? = null, variants: List<String> = emptyList()) =
        MediaCandidate(
            pageUrl = page,
            mediaUrl = url,
            sources = setOf(CandidateSource.REQUEST, CandidateSource.MANIFEST),
            kind = MediaKind.HLS,
            durationMillis = duration,
            streamPieces = pieces,
            hlsVariantPlaylists = variants,
        )

    // A page with a live cam widget in an ad frame next to its own finished video, as on a page
    // whose player shows the video's qualities while two live widgets play.
    private val camA = hls("https://cam-edge.test/hls/41/master/41_240p.m3u8", live)
    private val camB = hls("https://cam.test/b-hls-12/77/77_240p.m3u8", live)
    private val main = hls(
        "https://vod.test/hls/5/master.m3u8",
        ended,
        duration = 7_158_000,
        variants = listOf("https://vod.test/hls/5/index_854x480.m3u8"),
    )
    private val mainVariant = hls("https://vod.test/hls/5/index_854x480.m3u8", duration = 7_158_000)

    @Test
    fun liveStreamsYftCantSaveAreSetAsideNotCountedAndNotTheMainVideo() {
        val videos = MediaGroups.pageVideos(listOf(camA, camB, main, mainVariant))
        val list = MediaGroups.ofPage(videos, hideAds = true)

        assertEquals(listOf(main.mediaUrl), list.videos.map { it.candidates.first().mediaUrl })
        assertEquals(2, list.videos.single().candidates.size)
        // Still one tap away under "Other videos on this page".
        assertEquals(setOf(camA.mediaUrl, camB.mediaUrl), list.previews.map { it.candidates.single().mediaUrl }.toSet())
        assertEquals(main.mediaUrl, MediaGroups.mainVideo(videos)!!.candidates.first().mediaUrl)
        assertEquals(main.mediaUrl, MediaGroups.pageVideoAfterAd(videos, emptyList())!!.candidates.first().mediaUrl)
    }

    @Test
    fun aPageOfOnlyLiveStreamsListsThemAll() {
        val list = MediaGroups.ofPage(MediaGroups.pageVideos(listOf(camA, camB)), hideAds = true)

        assertEquals(2, list.videos.size)
        assertTrue(list.previews.isEmpty())
    }

    @Test
    fun aStreamWhoseManifestWasNotReadYetIsNotTakenForLive() {
        val unread = hls("https://cam.test/b-hls-12/77/77_240p.m3u8")
        val list = MediaGroups.ofPage(MediaGroups.pageVideos(listOf(unread, main)), hideAds = true)

        // Not proven live, and of unknown length while nothing names the page's video: it stays
        // one of the page's videos.
        assertEquals(setOf(unread.mediaUrl, main.mediaUrl), list.videos.map { it.candidates.first().mediaUrl }.toSet())
        assertTrue(list.previews.isEmpty())
    }

    @Test
    fun theFoundListKeepsAVideoOfThePagesStatedLengthEvenWhenATimingSignCalledItAnAd() {
        // The player fetched the page's own file right after an ad break request.
        val file = MediaCandidate(
            pageUrl = page,
            mediaUrl = "https://files.test/v/5.mp4",
            sources = setOf(CandidateSource.REQUEST),
            kind = MediaKind.DIRECT,
            durationMillis = 1_853_000,
            adSign = AdSign.AD_BREAK,
            pageRole = PageMediaRole.PREVIEW,
        )
        val facts = PageVideoFacts(durationMillis = 1_853_000)
        val videos = MediaGroups.pageVideos(listOf(file))

        assertTrue(MediaGroups.ofPage(videos, hideAds = true).all.isEmpty())
        assertEquals(1, MediaGroups.ofPage(videos, facts, hideAds = true).videos.size)
        // An ad network's file stays hidden whatever its length.
        val network = file.copy(adSign = AdSign.AD_HOST)
        assertTrue(MediaGroups.ofPage(MediaGroups.pageVideos(listOf(network)), facts, hideAds = true).all.isEmpty())
    }
}
