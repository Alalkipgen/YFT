package com.alal.yft.core.model.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P28: the page's stated length tells its video from the ad its player shows first. Without a
 * stated length P24's rules stay as they were; a site adapter's video is never touched.
 */
class MediaGroupsPageFactsTest {
    private val page = "https://tube.example.test/watch/77"
    private val ad = "https://cdn.adnet.example.test/creatives/spring-30s.mp4"
    private val master = "https://stream.example.test/v77/master.m3u8"
    private val stated = PageVideoFacts(
        durationMillis = 984_000,
        title = "Harbour lights at dusk",
        thumbnailUrl = "https://img.example.test/v77/poster.jpg",
    )

    private fun candidate(
        url: String,
        kind: MediaKind = MediaKind.DIRECT,
        durationMillis: Long? = null,
        height: Int? = null,
        role: PageMediaRole? = null,
        videoId: String? = null,
        pageVideoKey: String? = null,
        title: String? = null,
    ) = MediaCandidate(
        pageUrl = page,
        mediaUrl = url,
        sources = setOf(CandidateSource.REQUEST),
        kind = kind,
        title = title,
        durationMillis = durationMillis,
        height = height,
        pageRole = role,
        videoId = videoId,
        pageVideoKey = pageVideoKey,
    )

    private val adFile = candidate(ad, durationMillis = 30_000, height = 1080)
    private val stream = candidate(master, MediaKind.HLS, durationMillis = 984_500, height = 720)

    @Test
    fun theStatedLengthBeatsThePlayingAd() {
        // The owner's Preview #4 case: the 0:30 1080p ad plays; the 16:24 720p HLS is the video.
        val videos = MediaGroups.pageVideos(listOf(adFile, stream))
        val playing = PlayingVideo(url = ad, durationMillis = 30_000, height = 1080)

        val main = MediaGroups.mainVideo(videos, playing, stated)

        assertEquals(listOf(master), main?.candidates?.map { it.mediaUrl })
        val list = MediaGroups.ofPage(videos, stated)
        assertEquals(listOf(master), list.videos.flatMap { it.candidates }.map { it.mediaUrl })
        assertEquals(listOf(ad), list.previews.flatMap { it.candidates }.map { it.mediaUrl })
    }

    @Test
    fun withoutAStatedLengthThePlayingElementStillWins() {
        // P24 guard: no page facts, no change.
        val videos = MediaGroups.pageVideos(listOf(adFile, stream))
        val playing = PlayingVideo(url = ad, durationMillis = 30_000, height = 1080)

        assertEquals(listOf(ad), MediaGroups.mainVideo(videos, playing)?.candidates?.map {
            it.mediaUrl
        })
        assertEquals(
            listOf(ad),
            MediaGroups.mainVideo(videos, playing, PageVideoFacts(title = "Only a title"))
                ?.candidates?.map { it.mediaUrl },
        )
    }

    @Test
    fun aPlayingElementOfTheStatedLengthOrOfUnknownLengthIsStillThePlayer() {
        val other = candidate("https://cdn.example.test/v/long.mp4", durationMillis = 2_000_000)
        val own = candidate("https://cdn.example.test/v/own.mp4")
        val videos = MediaGroups.pageVideos(listOf(other, own))

        val unknown = MediaGroups.mainVideo(
            videos,
            PlayingVideo(url = own.mediaUrl),
            stated,
        )

        assertEquals(listOf(own.mediaUrl), unknown?.candidates?.map { it.mediaUrl })
    }

    @Test
    fun thePagesNamedVideoComesBeforeTheRestWhenNoLengthMatches() {
        val named = candidate(
            "https://cdn.example.test/v/720.mp4",
            role = PageMediaRole.MAIN,
            pageVideoKey = "player-0",
        )
        val long = candidate("https://cdn.example.test/v/other.mp4", durationMillis = 3_000_000)
        val videos = MediaGroups.pageVideos(listOf(long, named))

        val main = MediaGroups.mainVideo(videos, playing = null, facts = stated)

        assertEquals(listOf(named.mediaUrl), main?.candidates?.map { it.mediaUrl })
    }

    @Test
    fun aFileOfTheStatedLengthIsTheVideoEvenWhenAnAdListMarkedIt() {
        val marked = candidate(master, MediaKind.HLS, 984_000, role = PageMediaRole.PREVIEW)

        val roles = MediaGroups.withPageRoles(listOf(adFile, marked), stated)

        assertEquals(listOf(PageMediaRole.PREVIEW, PageMediaRole.MAIN), roles.map { it.pageRole })
        val group = MediaGroups.of(listOf(marked)).single()
        assertFalse(MediaGroups.looksLikePreview(group, listOf(group), stated))
        assertTrue(MediaGroups.looksLikePreview(group, listOf(group)))
    }

    @Test
    fun withoutAStatedLengthThePagesRolesStay() {
        val files = listOf(adFile, stream)

        assertSame(files, MediaGroups.withPageRoles(files, null))
        assertSame(files, MediaGroups.withPageRoles(files, PageVideoFacts(title = "T")))
    }

    @Test
    fun aSiteAdaptersVideoIsNeverTouched() {
        val named = candidate(
            "https://cdn.example.test/v/yt.mp4",
            durationMillis = 30_000,
            videoId = "youtube:AAAAAAAAAAA",
            title = "Adapter title",
        )

        assertEquals(listOf(named), MediaGroups.withPageRoles(listOf(named), stated))
        val group = MediaGroups.of(listOf(named)).single()
        assertFalse(MediaGroups.looksLikePreview(group, listOf(group), stated))
        assertFalse(MediaGroups.mayBeAdBefore(group, stated))
        assertSame(group, MediaGroups.withPageFacts(group, stated))
        assertEquals(group, MediaGroups.mainVideo(listOf(group), playing = null, facts = stated))
    }

    @Test
    fun onlyAFarShorterOrMarkedVideoMayBeTheAdBeforeThePagesVideo() {
        fun group(vararg files: MediaCandidate) = MediaGroups.of(files.toList()).single()

        assertTrue(MediaGroups.mayBeAdBefore(group(adFile), stated))
        assertTrue(
            MediaGroups.mayBeAdBefore(
                group(candidate(ad, role = PageMediaRole.PREVIEW)),
                stated,
            ),
        )
        assertFalse(MediaGroups.mayBeAdBefore(group(stream), stated))
        // Of unknown length and unmarked: it may be the page's own file.
        assertFalse(MediaGroups.mayBeAdBefore(group(candidate(ad)), stated))
        // A page that states a short video or nothing has no ad to wait out.
        val short = PageVideoFacts(durationMillis = 90_000)
        assertFalse(MediaGroups.mayBeAdBefore(group(adFile), short))
        assertFalse(MediaGroups.mayBeAdBefore(group(adFile), null))
    }

    @Test
    fun aPlayerSetupsQualitiesAreOneVideo() {
        val files = listOf(
            candidate("https://cdn.example.test/v/480.mp4", height = 480, pageVideoKey = "p-0"),
            candidate("https://cdn.example.test/v/720.mp4", height = 720, pageVideoKey = "p-0"),
            candidate("https://cdn.example.test/v/other.mp4", pageVideoKey = "p-1"),
        )

        val groups = MediaGroups.of(files)

        assertEquals(listOf(2, 1), groups.map { it.candidates.size })
    }

    @Test
    fun thePagesTitleAndPictureFillAVideoThatNamesNone() {
        val group = MediaGroups.of(listOf(stream)).single()

        val filled = MediaGroups.withPageFacts(group, stated)

        assertEquals("Harbour lights at dusk", filled.title)
        assertEquals(stated.thumbnailUrl, filled.candidates.single().thumbnailUrl)
        assertEquals("Harbour lights at dusk", filled.candidates.single().title)
        assertSame(group, MediaGroups.withPageFacts(group, null))
        val titled = MediaGroups.of(listOf(stream.copy(title = "Own title"))).single()
        assertEquals("Own title", MediaGroups.withPageFacts(titled, stated).title)
        assertNull(MediaGroups.withPageFacts(group, PageVideoFacts(984_000)).title)
    }
}
