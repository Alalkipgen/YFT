package com.alal.yft.core.model.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaGroupsTest {
    private val page = "https://www.facebook.com/reel/1603698891196107/"

    private fun candidate(
        url: String,
        title: String? = "Morning swim — HD",
        kind: MediaKind = MediaKind.DIRECT,
        durationMillis: Long? = null,
        videoId: String? = null,
        pageUrl: String = page,
    ) = MediaCandidate(
        pageUrl = pageUrl,
        mediaUrl = url,
        sources = setOf(CandidateSource.MANIFEST),
        kind = kind,
        title = title,
        durationMillis = durationMillis,
        videoId = videoId,
    )

    @Test
    fun facebooksThreeRenditionsOfOneReelAreOneVideo() {
        val id = "facebook:1603698891196107"
        val groups = MediaGroups.of(
            listOf(
                candidate("https://video.example.test/hd.mp4", videoId = id),
                candidate("https://video.example.test/sd.mp4", "Morning swim — SD", videoId = id),
                candidate(
                    "https://video.example.test/manifest.mpd",
                    "Morning swim — Auto quality",
                    kind = MediaKind.DASH,
                    videoId = id,
                ),
            ),
        )

        assertEquals(1, groups.size)
        assertEquals("Morning swim", groups.single().title)
        assertEquals(3, groups.single().candidates.size)
    }

    @Test
    fun filesOfOnePageWithTheSameLengthAreOneVideo() {
        val groups = MediaGroups.of(
            listOf(
                candidate("https://cdn.test/a.mp4", "Clip — 720p", durationMillis = 61_200),
                candidate("https://cdn.test/b.mp4", "Clip — 480p", durationMillis = 61_900),
                candidate("https://cdn.test/c.mp4", "Other", durationMillis = 30_000),
            ),
        )

        assertEquals(listOf(2, 1), groups.map { it.candidates.size })
        assertEquals(listOf("Clip", "Other"), groups.map(MediaGroup::title))
        assertEquals(61_200L, groups.first().durationMillis)
    }

    @Test
    fun filesOfUnknownLengthOrOtherPagesStayApart() {
        val groups = MediaGroups.of(
            listOf(
                candidate("https://cdn.example.test/one.mp4", "Song — Live"),
                candidate("https://cdn.example.test/two.mp4", "Song — Live"),
                candidate(
                    "https://cdn.example.test/three.mp4",
                    durationMillis = 5_000,
                    pageUrl = "https://example.test/a",
                ),
                candidate(
                    "https://cdn.example.test/four.mp4",
                    durationMillis = 5_000,
                    pageUrl = "https://example.test/b",
                ),
            ),
        )

        assertEquals(4, groups.size)
        // A lone page file keeps its whole title: " — Live" is not a quality.
        assertEquals("Song — Live", groups.first().title)
        assertEquals(groups.size, groups.map(MediaGroup::key).distinct().size)
    }

    @Test
    fun aNamedVideoIsThePagesOnlyVideoAndThePlayersOwnFilesDoNotCount() {
        // P3-FIX: Facebook's story page; the player fetched the video's picture and sound
        // tracks, which generic detection saw as two more files of unknown length.
        val id = "facebook:post:1234567890123456"
        val named = listOf(
            candidate("https://video.example.test/hd.mp4", videoId = id),
            candidate("https://video.example.test/sd.mp4", "Morning swim — SD", videoId = id),
        )
        val played = listOf(
            candidate("https://cdn.example.test/v/track-video.mp4", title = null),
            candidate("https://cdn.example.test/v/track-audio.mp4", title = null),
        )

        val videos = MediaGroups.pageVideos(played + named)

        assertEquals(1, videos.size)
        assertEquals(named, videos.single().candidates)
        assertEquals(3, MediaGroups.of(played + named).size)
        // Without a named video every file is still its own video.
        assertEquals(2, MediaGroups.pageVideos(played).size)
        assertEquals(emptyList<MediaGroup>(), MediaGroups.pageVideos(emptyList()))
    }

    @Test
    fun anAdapterSiteNeverListsItsPlayersFilesAsVideos() {
        // P12: a YouTube or Facebook page whose lookup still runs, or failed, showed "Found on
        // this page 4" with the player's files; on an adapter's site only its video counts.
        val played = listOf(
            candidate("https://cdn.example.test/v/track-video.mp4", title = null),
            candidate("https://cdn.example.test/v/track-audio.mp4", title = null),
        )
        val named = candidate("https://video.example.test/hd.mp4", videoId = "youtube:abc")

        assertEquals(emptyList<MediaGroup>(), MediaGroups.pageVideos(played, adapterSite = true))
        assertEquals(
            listOf(named),
            MediaGroups.pageVideos(played + named, adapterSite = true).single().candidates,
        )
        assertEquals(2, MediaGroups.pageVideos(played, adapterSite = false).size)
    }

    @Test
    fun theMainVideoIsThePlayingOneElseTheLargest() {
        val small = candidate("https://cdn.example.test/small.mp4", title = "Small")
            .copy(contentLengthBytes = 1_000, height = 1080)
        val large = candidate("https://cdn.example.test/large.mp4", title = "Large")
            .copy(contentLengthBytes = 9_000, height = 360)
        val unknown = candidate("https://cdn.example.test/unknown.mp4", title = "Unknown")
        val videos = MediaGroups.pageVideos(listOf(small, unknown, large))
        assertEquals(3, videos.size)

        assertEquals(listOf(large), MediaGroups.mainVideo(videos)!!.candidates)
        assertEquals(
            listOf(small),
            MediaGroups.mainVideo(videos, playingUrl = small.mediaUrl)!!.candidates,
        )
        // A playing address no candidate has, such as a page-built stream, falls back.
        assertEquals(
            listOf(large),
            MediaGroups.mainVideo(videos, playingUrl = "https://cdn.example.test/x")!!.candidates,
        )
        // Without sizes the taller picture wins, and on a full tie the earlier video.
        val tall = candidate("https://cdn.example.test/tall.mp4", title = "Tall").copy(height = 720)
        val plain = MediaGroups.pageVideos(listOf(unknown, tall))
        assertEquals(listOf(tall), MediaGroups.mainVideo(plain)!!.candidates)
        val tie = MediaGroups.pageVideos(listOf(unknown, candidate("https://cdn.example.test/b")))
        assertEquals(listOf(unknown), MediaGroups.mainVideo(tie)!!.candidates)
        assertEquals(null, MediaGroups.mainVideo(emptyList()))
    }

    @Test
    fun titlesSplitIntoTheVideoAndTheAdaptersLabel() {
        assertEquals("Ocean waves", MediaGroups.baseTitle("Ocean waves — 720p"))
        assertEquals("720p", MediaGroups.titleLabel("Ocean waves — 720p"))
        assertEquals("Plain", MediaGroups.baseTitle(" Plain "))
        assertNull(MediaGroups.titleLabel("Plain"))
        assertNull(MediaGroups.baseTitle("  "))
    }

    @Test
    fun theGroupOfACandidateIsFound() {
        val id = "youtube:fixture0001"
        val first = candidate("https://cdn.example.test/1.mp4", videoId = id)
        val second = candidate("https://cdn.example.test/2.mp4", videoId = id)
        val other = candidate("https://cdn.example.test/3.mp4", "Other")
        val all = listOf(first, other, second)

        assertEquals(listOf(first, second), MediaGroups.containing(all, second)?.candidates)
        assertNull(MediaGroups.containing(listOf(first), other))
    }
}
