package com.alal.yft.core.model.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Same-file check: how a fresh address is the file whose link failed. */
class SameFileTest {
    @Test
    fun onlyASignatureExpiryOrCacheValueMayChange() {
        val old = "https://cdn.example.test/v/720.mp4?id=7&validto=1&hash=a&rnd=1&X-Amz-Date=2"
        assertEquals(FileMatch.SAME_ADDRESS, SameFile.match(old, "$old#t=3"))
        assertEquals(
            FileMatch.RESIGNED,
            SameFile.match(old, "https://cdn.example.test/v/720.mp4?hash=b&id=7&validto=9"),
        )
        assertEquals(
            FileMatch.DIFFERENT,
            SameFile.match(old, "https://cdn.example.test/v/720.mp4?id=8&validto=9&hash=b"),
        )
        assertEquals(
            FileMatch.DIFFERENT,
            SameFile.match(old, "https://cdn.example.test/v/480.mp4?id=7&validto=9&hash=b"),
        )
        assertEquals(
            FileMatch.DIFFERENT,
            SameFile.match(old, "https://cdn.other.test/v/720.mp4?id=7&validto=9&hash=b"),
        )
    }

    @Test
    fun aContentValueThatIsAddedOrDroppedIsAnotherFile() {
        val old = "https://cdn.example.test/get?file=a.mp4"
        assertEquals(FileMatch.DIFFERENT, SameFile.match(old, "https://cdn.example.test/get"))
        assertEquals(
            FileMatch.DIFFERENT,
            SameFile.match(old, "https://cdn.example.test/get?file=a.mp4&quality=480"),
        )
    }

    @Test
    fun anUnknownValueOrAnotherEdgeServerIsOnlyTheSamePath() {
        val old = "https://cdn3.example.test/v/720.mp4?x=1&token=a"
        assertEquals(
            FileMatch.SAME_PATH,
            SameFile.match(old, "https://cdn3.example.test/v/720.mp4?x=2&token=b"),
        )
        assertEquals(
            FileMatch.SAME_PATH,
            SameFile.match(old, "https://cdn7.example.test/v/720.mp4?x=1&token=b"),
        )
        // Not an edge number: another site, or another word in the host.
        assertEquals(
            FileMatch.DIFFERENT,
            SameFile.match("https://a1.test/v.mp4", "https://a2.test/v.mp4"),
        )
        assertEquals(
            FileMatch.DIFFERENT,
            SameFile.match(old, "https://img3.example.test/v/720.mp4?x=1&token=b"),
        )
    }

    @Test
    fun aKeptKeyHoldsNoSignatureAndAnOldRecordsPathIsOnlyTheSamePath() {
        val url = "https://cdn.example.test/v/720.mp4?id=7&token=SECRET123&sig=SIGNED456"
        val key = SameFile.key(url)

        assertEquals("https://cdn.example.test/v/720.mp4", key.path)
        assertFalse("$key", "SECRET123" in "$key" || "cdn" in "$key")
        assertFalse(key.query!!.contains("SECRET123") || key.query!!.contains("SIGNED456"))
        assertFalse(key.query!!.contains("token") || key.query!!.contains("sig="))
        assertFalse("the id's value is hashed", key.query!!.endsWith("=7"))
        assertEquals(
            FileMatch.RESIGNED,
            SameFile.match(key, "https://cdn.example.test/v/720.mp4?token=NEW&id=7"),
        )
        val old = FileKey(key.path, query = null)
        assertEquals(
            FileMatch.SAME_PATH,
            SameFile.match(old, "https://cdn.example.test/v/720.mp4?token=NEW&id=7"),
        )
    }

    @Test
    fun aProvenVideoIsFoundAndOnlyOneSamePathVideoOfTheSameLength() {
        val page = "https://clips.example.test/watch/5"
        fun video(url: String, length: Long?) = MediaGroup(
            url,
            null,
            listOf(
                MediaCandidate(
                    page,
                    url,
                    setOf(CandidateSource.DOM),
                    MediaKind.DIRECT,
                    durationMillis = length,
                ),
            ),
        )
        val resigned = video("https://cdn.example.test/v.mp4?hash=b", 600_000)
        val unknown = video("https://cdn.example.test/v.mp4?x=2", 600_500)
        val old = "https://cdn.example.test/v.mp4?hash=a&x=1"
        val matchOf = { fresh: String -> SameFile.match(old, fresh) }

        val found = SameFile.videoIn(listOf(unknown), 600_000, matchOf)
        assertTrue(found is SameVideo.Found && found.match == FileMatch.SAME_PATH)
        assertEquals(SameVideo.Unconfirmed, SameFile.videoIn(listOf(unknown), null, matchOf))
        assertEquals(
            SameVideo.Unconfirmed,
            SameFile.videoIn(listOf(unknown, unknown.copy(key = "2")), 600_000, matchOf),
        )
        assertEquals(
            SameVideo.Missing,
            SameFile.videoIn(listOf(video("https://cdn.example.test/w.mp4", 600_000)), 600_000,
                matchOf),
        )
        // A fresh signature is the file only with all its other values: x changed here.
        val proven = SameFile.videoIn(listOf(resigned), 600_000) { fresh ->
            SameFile.match("https://cdn.example.test/v.mp4?hash=a", fresh)
        }
        assertEquals(SameVideo.Found(listOf(resigned), FileMatch.RESIGNED), proven)
    }
}
