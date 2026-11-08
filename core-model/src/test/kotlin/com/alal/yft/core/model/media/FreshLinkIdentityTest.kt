package com.alal.yft.core.model.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Fresh link renewal, Same-file check: a fresh link is the video whose link failed only when its
 * address proves it. A similar length alone never does, a query value that names the content
 * tells two videos apart, and a fresh signature of the same file is still that file. When
 * nothing proves it, no video is chosen.
 */
class FreshLinkIdentityTest {
    private val page = "https://clips.example.test/watch/5"

    private fun file(
        url: String,
        length: Long? = 600_000,
        source: CandidateSource = CandidateSource.DOM,
        height: Int? = null,
        key: String? = null,
    ) = MediaCandidate(
        pageUrl = page,
        mediaUrl = url,
        sources = setOf(source),
        kind = MediaKind.DIRECT,
        durationMillis = length,
        height = height,
        pageVideoKey = key,
    )

    private fun video(vararg files: MediaCandidate) =
        MediaGroup(key = files.first().mediaUrl, title = null, candidates = files.toList())

    @Test
    fun aDifferentVideoOfTheSameOrASimilarLengthIsNeverTheSameVideo() {
        val failed = video(file("https://cdn.example.test/old.mp4"))
        val same = video(file("https://cdn.example.test/other.mp4"))
        val close = video(file("https://cdn.example.test/near.mp4", length = 601_500))

        assertNull(MediaGroups.refreshed(failed, listOf(same, close)))
    }

    @Test
    fun aQueryValueThatNamesTheContentTellsTwoVideosApart() {
        val failed = video(file("https://cdn.example.test/video.mp4?video=one"))
        val other = video(file("https://cdn.example.test/video.mp4?video=two"))
        assertNull(MediaGroups.refreshed(failed, listOf(other)))

        val byId = video(file("https://cdn.example.test/get.php?id=17&token=a"))
        val otherId = video(file("https://cdn.example.test/get.php?id=18&token=b"))
        assertNull(MediaGroups.refreshed(byId, listOf(otherId)))
    }

    @Test
    fun aFreshSignatureOfTheSameFileIsThatFile() {
        val failed = video(file("https://media.example.test/v5/720.mp4?validto=1&hash=old"))
        val fresh = video(file("https://media.example.test/v5/720.mp4?validto=2&hash=new"))
        assertSame(fresh, MediaGroups.refreshed(failed, listOf(fresh)))

        // The content's own value stays; only the signature and the expiry change.
        val signed = video(
            file("https://cdn.example.test/get.php?id=17&token=a&expires=1700000000"),
        )
        val other = video(file("https://cdn.example.test/get.php?id=18&token=b&expires=1700009999"))
        val again = video(file("https://cdn.example.test/get.php?expires=1700009999&token=c&id=17"))
        assertSame(again, MediaGroups.refreshed(signed, listOf(other, again)))
    }

    @Test
    fun aFreshSignatureOfAFileWhoseKnownLengthDiffersIsNotThatVideo() {
        val failed = video(file("https://media.example.test/v5/720.mp4?hash=old"))
        val longer = video(file("https://media.example.test/v5/720.mp4?hash=new", 605_000))
        assertNull(MediaGroups.refreshed(failed, listOf(longer)))
    }

    @Test
    fun theSameVideosQualitiesAreFoundByAnyOfTheirFilesAndNotByAnotherVideosQuality() {
        val failed = video(
            file("https://cdn.example.test/v5/720.mp4?sig=a", height = 720, key = "player-0"),
            file("https://cdn.example.test/v5/480.mp4?sig=a", height = 480, key = "player-0"),
        )
        val another = video(file("https://cdn.example.test/v6/720.mp4?sig=b", height = 720))
        val onlyLower = video(file("https://cdn.example.test/v5/480.mp4?sig=b", height = 480))

        assertSame(onlyLower, MediaGroups.refreshed(failed, listOf(another, onlyLower)))
        assertNull(MediaGroups.refreshed(failed, listOf(another)))
    }

    @Test
    fun aValueTheCheckCannotNameNeedsOneSuchVideoOfTheSameLength() {
        val failed = video(file("https://cdn.example.test/v5/720.mp4?x=1"))
        val one = video(file("https://cdn.example.test/v5/720.mp4?x=2"))
        assertSame(one, MediaGroups.refreshed(failed, listOf(one)))

        // Two such videos: nothing tells which one it is.
        val two = video(file("https://cdn.example.test/v5/720.mp4?x=3"))
        assertNull(MediaGroups.refreshed(failed, listOf(one, two)))

        // Its length unknown, or another length: not proven.
        val unknown = video(file("https://cdn.example.test/v5/720.mp4?x=2", length = null))
        assertNull(MediaGroups.refreshed(failed, listOf(unknown)))
        val longer = video(file("https://cdn.example.test/v5/720.mp4?x=2", length = 640_000))
        assertNull(MediaGroups.refreshed(failed, listOf(longer)))
    }

    @Test
    fun thePlayersRequestReplacesOnlyTheSameFileNotAnotherContentValue() {
        val script = file("https://cdn.example.test/video.mp4?video=one")
        val request = file(
            "https://cdn.example.test/video.mp4?video=two",
            source = CandidateSource.REQUEST,
        ).copy(observedAtEpochMs = 9)
        val group = video(script)

        assertSame(group, FreshLinks.playerFirst(group, listOf(request)))

        val signed = file("https://cdn.example.test/video.mp4?video=one&hash=old")
        val resigned = file(
            "https://cdn.example.test/video.mp4?video=one&hash=new",
            source = CandidateSource.REQUEST,
        )
        assertEquals(
            listOf(resigned.mediaUrl),
            FreshLinks.playerFirst(video(signed), listOf(resigned)).candidates.map { it.mediaUrl },
        )
    }
}
