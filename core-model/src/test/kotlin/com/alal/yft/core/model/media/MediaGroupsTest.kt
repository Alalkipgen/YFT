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

        // P24 (plan step 2): the picture height ranks before the stated size, so the small
        // 1080p file now wins over the large 360p one.
        assertEquals(listOf(small), MediaGroups.mainVideo(videos)!!.candidates)
        assertEquals(
            listOf(large),
            MediaGroups.mainVideo(videos, playingUrl = large.mediaUrl)!!.candidates,
        )
        // A playing address no candidate has, such as a page-built stream, falls back.
        assertEquals(
            listOf(small),
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

    private val sitePage = "https://videos.example.test/watch/42"

    /** P24: the owner's case: one long HLS video and 40 short preview clips around it. */
    private fun previewGrid(): List<MediaCandidate> {
        val master = candidate(
            "https://stream.example.test/v42/master.m3u8",
            title = "Long walk",
            kind = MediaKind.HLS,
            pageUrl = sitePage,
        ).copy(pageRole = PageMediaRole.MAIN, observedAtEpochMs = 1_000)
        val previews = (1..40).map { number ->
            candidate(
                "https://media.example.test/clips/$number.mp4",
                title = "Clip $number",
                durationMillis = (5 + number % 26) * 1_000L,
                pageUrl = sitePage,
            ).copy(pageRole = PageMediaRole.PREVIEW, contentLengthBytes = 2_000_000L + number)
        }
        return previews.take(20) + master + previews.drop(20)
    }

    @Test
    fun aPagesPreviewsAreSetApartFromItsVideo() {
        val videos = MediaGroups.pageVideos(previewGrid())
        val list = MediaGroups.ofPage(videos)

        // Every preview is a clip of its own, even two of the same length.
        assertEquals(41, videos.size)
        assertEquals(1, list.videos.size)
        assertEquals("Long walk", list.videos.single().title)
        assertEquals(40, list.previews.size)
        assertEquals(list.videos + list.previews, list.all)
        assertEquals("Long walk", MediaGroups.mainVideo(videos)!!.title)

        // Without the page's marks: a clip under a minute next to a long video, and an address
        // that names a preview.
        val long = candidate("https://cdn.example.test/v.mp4", "Long", durationMillis = 754_000)
        val short = candidate("https://cdn.example.test/c.mp4", "Short", durationMillis = 29_000)
        val teaser = candidate("https://cdn.example.test/teasers/t.mp4", "Teaser")
        val plain = MediaGroups.ofPage(MediaGroups.pageVideos(listOf(short, teaser, long)))
        assertEquals(listOf("Long"), plain.videos.map { it.title })
        assertEquals(listOf("Short", "Teaser"), plain.previews.map { it.title })

        // A page of previews only keeps them all as its videos.
        val clips = MediaGroups.pageVideos(previewGrid().filter { it.kind == MediaKind.DIRECT })
        assertEquals(40, MediaGroups.ofPage(clips).videos.size)
        assertEquals(emptyList<MediaGroup>(), MediaGroups.ofPage(clips).previews)
        // A site adapter's video is never a preview.
        val named = candidate("https://cdn.example.test/preview.mp4", videoId = "youtube:abc")
        assertEquals(false, MediaGroups.looksLikePreview(MediaGroups.of(listOf(named)).single(),
            emptyList()))
    }

    @Test
    fun aPageBuiltPlayerIsMatchedByItsLengthElseByTheManifestItStartedWith() {
        val first = candidate(
            "https://stream.example.test/a/master.m3u8",
            "First",
            kind = MediaKind.HLS,
            durationMillis = 754_000,
        ).copy(observedAtEpochMs = 1_000)
        val second = candidate(
            "https://stream.example.test/b/master.m3u8",
            "Second",
            kind = MediaKind.HLS,
            durationMillis = 300_000,
        ).copy(observedAtEpochMs = 9_000, height = 1080)
        val clip = candidate("https://cdn.example.test/c.mp4", "Clip", durationMillis = 29_000)
        val videos = MediaGroups.pageVideos(listOf(first, second, clip))

        // A blob: player 753.4 s long is the first stream (within 2 s).
        val blob = PlayingVideo(pageBuilt = true, durationMillis = 753_400)
        assertEquals("First", MediaGroups.mainVideo(videos, blob)!!.title)
        assertEquals("Second", MediaGroups.mainVideo(videos, blob.copy(durationMillis = 301_000))!!
            .title)

        // Without a length: the manifest loaded last before the player started.
        val unknown = videos.map { video ->
            video.copy(candidates = video.candidates.map { it.copy(durationMillis = null) })
        }
        val started = PlayingVideo(pageBuilt = true, startedAtEpochMs = 5_000)
        assertEquals("First", MediaGroups.mainVideo(unknown, started)!!.title)
        val later = started.copy(startedAtEpochMs = 9_500)
        assertEquals("Second", MediaGroups.mainVideo(unknown, later)!!.title)

        // A muted looping clip that plays is not the page's player.
        val loop = PlayingVideo(url = clip.mediaUrl, muted = true, loop = true)
        assertEquals("First", MediaGroups.mainVideo(videos, loop)!!.title)
        assertEquals("Clip", MediaGroups.mainVideo(videos, PlayingVideo(url = clip.mediaUrl))!!
            .title)
    }

    @Test
    fun withoutAMatchALongLengthBeatsASizeAndAPreviewNeverWins() {
        val long = candidate("https://cdn.example.test/long.mp4", "Long", durationMillis = 754_000)
            .copy(height = 480)
        val big = candidate("https://cdn.example.test/big.mp4", "Big", durationMillis = 29_000)
            .copy(contentLengthBytes = 90_000_000, height = 1080)
        assertEquals("Long", MediaGroups.mainVideo(MediaGroups.pageVideos(listOf(big, long)))!!
            .title)

        // Of two long videos the longer; a preview with a long length still loses.
        val longer = long.copy(mediaUrl = "https://cdn.example.test/longer.mp4", title = "Longer",
            durationMillis = 1_200_000)
        val marked = longer.copy(mediaUrl = "https://cdn.example.test/m.mp4", title = "Marked",
            durationMillis = 3_600_000, pageRole = PageMediaRole.PREVIEW)
        val videos = MediaGroups.pageVideos(listOf(long, marked, longer))
        assertEquals("Longer", MediaGroups.mainVideo(videos)!!.title)
    }

    @Test
    fun aPageThatNamesItsVideoListsTheRestAsItsOtherVideosUnlessOneIsLong() {
        // P24 (live check): a stock-video page names its video in og:video and JSON-LD, and
        // links other videos' files of unknown length in its own data.
        val road = "https://cdn.example.test/v/1080p.mp4"
        val named = candidate(road, "Road", durationMillis = 25_000)
            .copy(pageRole = PageMediaRole.MAIN)
        val others = (1..10).map { number ->
            candidate("https://cdn.example.test/o/$number/720p.mp4", "Other $number")
        }
        val videos = MediaGroups.pageVideos(others.take(5) + named + others.drop(5))
        val list = MediaGroups.ofPage(videos)
        assertEquals(listOf("Road"), list.videos.map { it.title })
        assertEquals(10, list.previews.size)
        assertEquals("Road", MediaGroups.mainVideo(videos)!!.title)

        // A video known to be a minute or more long stays one of the page's videos.
        val full = "https://cdn.example.test/v/full.mp4"
        val long = candidate(full, "Full", durationMillis = 754_000)
        val both = MediaGroups.ofPage(MediaGroups.pageVideos(listOf(named, long) + others))
        assertEquals(listOf("Road", "Full"), both.videos.map { it.title })
        assertEquals(10, both.previews.size)
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
