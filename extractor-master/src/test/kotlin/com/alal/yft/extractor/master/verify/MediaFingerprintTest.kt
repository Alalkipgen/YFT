package com.alal.yft.extractor.master.verify

import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.master.present.MasterMainPresentation
import com.alal.yft.extractor.master.verify.SegmentIndexReaderTest.Companion.fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R4: fingerprints from the HLS fixtures; grouping and dropping over checked files. */
class MediaFingerprintTest {
    private val hd = print("ladder-1080.m3u8")
    private val sd = print("ladder-720.m3u8")
    private val related = print("related.m3u8")
    private val ad = print("ad-grid.m3u8")

    @Test
    fun `one video's qualities agree and are the same video`() {
        assertEquals(true, hd.keyframesAgree(sd))
        assertTrue(hd.sameVideo(sd))
        assertTrue(hd.sameVideo(sd.copy(durationMillis = sd.durationMillis!! + 200)))
        assertFalse(hd.sameVideo(sd.copy(durationMillis = sd.durationMillis!! + 400)))
    }

    @Test
    fun `a related clip of nearly the same length disagrees`() {
        assertEquals(false, hd.keyframesAgree(related))
        assertTrue(hd.otherVideo(related))
        assertFalse(hd.sameVideo(related))
    }

    @Test
    fun `length alone or too few cues never decide`() {
        val bare = MediaFingerprint(hd.durationMillis)
        assertNull(hd.keyframesAgree(bare))
        assertFalse(hd.sameVideo(bare))
        assertFalse(hd.otherVideo(bare))
        val short = MediaFingerprint(60_000, listOf(2_000, 4_000))
        assertNull(short.keyframesAgree(short))
    }

    @Test
    fun `two videos cut on the same fixed grid group only within one frame of length`() {
        val grid = (1..29).map { it * 2_000L }
        val first = MediaFingerprint(60_000, grid)
        assertEquals(true, first.keyframesAgree(MediaFingerprint(60_100, grid)))
        assertFalse(first.sameVideo(MediaFingerprint(60_100, grid)))
        assertTrue(first.sameVideo(MediaFingerprint(60_020, grid)))
        assertEquals(true, ad.keyframesAgree(MediaFingerprint(15_000, (1..7).map { it * 2_000L })))
    }

    @Test
    fun `the ladder groups, the related clip is dropped and nothing is created`() {
        val files = listOf(media("hd"), media("related"), media("sd"))
        val prints = mapOf("hd" to hd, "related" to related, "sd" to sd)
        val groups = FingerprintGroups.of(files) { prints[name(it)] }
        assertEquals(listOf(listOf("hd", "sd")), groups.map { group -> group.map(::name) })
        assertTrue(groups.flatten().all { it in files })
    }

    @Test
    fun `an unconfirmed main or a second ladder keeps the other files as their own groups`() {
        val lone = FingerprintGroups.of(listOf(media("hd"), media("related"))) {
            mapOf("hd" to hd, "related" to related)[name(it)]
        }
        assertEquals(listOf(listOf("hd"), listOf("related")), lone.map { g -> g.map(::name) })

        val other = related.copy(durationMillis = related.durationMillis!! + 10)
        val ladders = FingerprintGroups.of(
            listOf(media("hd"), media("related"), media("sd"), media("related-sd")),
        ) { mapOf("hd" to hd, "sd" to sd, "related" to related, "related-sd" to other)[name(it)] }
        assertEquals(
            listOf(listOf("hd", "sd"), listOf("related", "related-sd")),
            ladders.map { g -> g.map(::name) },
        )
    }

    @Test
    fun `without fingerprints every file stays its own group in rank order`() {
        val files = listOf(media("a"), media("b"), media("c"))
        assertEquals(files.map { listOf(it) }, FingerprintGroups.of(files) { null })
    }

    @Test
    fun `presentation packs one key's files into one group, first group is main`() {
        val files = listOf(media("hd"), media("sd"), media("other"))
        val keyOf = { m: MediaCandidate -> if (name(m) == "other") "k2" else "k1" }
        val packed = MasterMainPresentation.from(files, keyOf)
        assertEquals(listOf("hd", "sd"), packed.main.candidates.map(::name))
        assertEquals(listOf(listOf("other")), packed.more.map { g -> g.candidates.map(::name) })
        val single = MasterMainPresentation.from(files)
        assertEquals(1, single.main.candidates.size)
        assertEquals(2, single.more.size)
    }

    private fun print(name: String): MediaFingerprint {
        val timeline = SegmentIndexReader.extinf(fixture(name))
        return MediaFingerprint.of(timeline, null)
    }

    private fun media(name: String) = MediaCandidate(
        "https://page.test/watch", "https://cdn.test/$name.m3u8", setOf(CandidateSource.REQUEST),
        MediaKind.HLS, mimeType = "application/vnd.apple.mpegurl",
    )

    private fun name(media: MediaCandidate) =
        media.mediaUrl.substringAfterLast('/').substringBefore('.')
}
