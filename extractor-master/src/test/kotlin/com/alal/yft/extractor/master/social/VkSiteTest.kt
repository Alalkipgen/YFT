package com.alal.yft.extractor.master.social

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionFailure.GEO_RESTRICTED
import com.alal.yft.extractor.api.SiteExtractionFailure.LOGIN_REQUIRED
import com.alal.yft.extractor.api.SiteExtractionFailure.NO_MEDIA_FOUND
import com.alal.yft.extractor.api.SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE
import com.alal.yft.extractor.master.NOW
import com.alal.yft.extractor.master.RecordingValidator
import com.alal.yft.extractor.master.ValidationResult
import com.alal.yft.extractor.master.contract.AGENT
import com.alal.yft.extractor.master.lookup.LookupAsk
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.modules.youtube.ladder.InitBytes
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStart
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStartReader
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.sites.vk.VkExtractor
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VK-Master plan §7, §10, Appendix B step 4: VK's site ([VkSite.SITE], in the census's table
 * since wave 2): V may ask VK's video servers only (`vk{N}-{N}.vkuser.net`, `vkvd{N}.okcdn.ru`),
 * never okcdn.ru's image host, plain HTTP, user info or another site's host; in a VK lookup the
 * shared row check (V) asks VK's media hosts only, never a manifest, and its reads are no way of
 * VK's (main's manifest reads are, [VkLabelsTest]); the table's placeholder slots (A finalizes);
 * VK's names as agent C's schema 2 reads them (`vk`; `V1`, `V2`, `V3`). JVM only, no network.
 */
class VkSiteTest {
    @Test
    fun `V may ask VK's video servers only - never okcdn's image host, plain http, user info or another site`() {
        listOf(
            "$API_FILES/720.mp4?expires=1900000000&srcAg=FIXTURE",
            "https://vk12-3.vkuser.net/fixture/360.mp4",
            "$EMBED_FILES/720.mp4?expires=1900000000&srcAg=FIXTURE",
            "https://vkvd12.okcdn.ru/fixture/720.mp4",
            "https://VKVD1.OKCDN.RU/fixture/360.mp4",
        ).forEach { assertTrue(it, VkSite.SITE.mediaHost(it)) }

        listOf(
            "https://iv.okcdn.ru/fixture/large.jpg",
            "https://iv.okcdn.ru/fixture/trailer.mp4",
            "http://vk1-1.vkuser.net/fixture/720.mp4",
            "http://vkvd1.okcdn.ru/fixture/720.mp4",
            "https://fixture@vk1-1.vkuser.net/fixture/720.mp4",
            "https://fixture:secret@vkvd1.okcdn.ru/fixture/720.mp4",
            "https://okcdn.ru/fixture/720.mp4",
            "https://vkuser.net/fixture/720.mp4",
            "https://cdn.vkuser.net/fixture/720.mp4",
            "https://vkvd1.okcdn.ru.example.test/fixture/720.mp4",
            "https://vk1-1.vkuser.net.example.test/fixture/720.mp4",
            "https://vk.com/video_ext.php?oid=-100000001&id=400000001",
            "https://cdn.example.test/fixture/720.mp4",
            "https://video.twimg.com/ext_tw_video/fixture/720.mp4",
            "https://v16.fixture.tiktokcdn.com/video/tos/useast5/fixture.mp4?mime_type=video_mp4",
            "not an address",
        ).forEach { assertFalse(it, VkSite.SITE.mediaHost(it)) }
    }

    @Test
    fun `V in a VK lookup asks VK's media hosts only - never a manifest, its reads no way of VK's`() = runTest {
        val files = Files { start() }
        val check = SocialRowCheck(RecordingValidator(), files, site = VkSite.SITE)
        val record = LookupRecord(labels = VkSite.SITE.labels())
        val vkRow = row("$API_FILES/720.mp4?expires=1900000000&srcAg=FIXTURE")
        val failoverRow = row("$EMBED_FILES/720.mp4?expires=1900000000&srcAg=FIXTURE")
        val posterRow = row("https://iv.okcdn.ru/fixture/large.mp4")
        val otherSiteRow = row("https://v16.fixture.tiktokcdn.com/video/tos/useast5/fixture.mp4?mime_type=video_mp4")
        val manifestRow = row("$API_FILES/video.m3u8?expires=1900000000&srcAg=FIXTURE", MediaKind.HLS)

        val checked = withContext(record + MainPages(site = VkClient.SITE_ID)) {
            listOf(vkRow, failoverRow, posterRow, otherSiteRow, manifestRow).map { check.validate(it, NOW) }
        }

        assertTrue(checked[0].toString(), checked[0] is ValidationResult.Valid)
        assertTrue(checked[1].toString(), checked[1] is ValidationResult.Valid)
        checked.drop(2).forEach { assertEquals(NO_MEDIA_FOUND, (it as ValidationResult.Rejected).reason) }
        assertEquals(listOf(vkRow.mediaUrl, failoverRow.mediaUrl), files.asked)
        assertEquals(2, check.refused(record))

        // Each read is Master's GET of the file, with the row's own agent and no credential; no way of VK's.
        val reads = record.asks()
        assertEquals(2, reads.size)
        reads.forEach { ask ->
            assertEquals(LookupRecord.Asker.MASTER, ask.asker)
            assertEquals(LookupAsk.GET, ask.method)
            assertFalse(ask.authorized)
            assertNull(ask.way)
            assertNull(ask.mark)
        }
        assertEquals(2, record.masterAsks)
    }

    @Test
    fun `the table's values - VK's ways, a new set of labels per record, the placeholder slots`() {
        val site = VkSite.SITE
        assertEquals(VkClient.SITE_ID, site.id)
        assertEquals(VkExtractor(FakeExtractorHttpClient()).displayName, site.name)
        assertEquals(listOf("embed page", "anonymous token", "video.get"), site.mainWays)
        assertEquals("video.get", site.masterWay)
        assertTrue(site.labels() is VkLabels)
        assertNotSame(site.labels(), site.labels())

        assertEquals(
            listOf(
                "18+ video with list", "18+ video with list", "18+ video", "video", "video", "clip",
                "finished live", "removed", "private or sign-in-only", "region-limited",
            ),
            site.slots.map { it.label },
        )
        val wantRows = site.slots.filter { it.expect.rows }.map { it.label }
        assertEquals(
            listOf("18+ video with list", "18+ video with list", "18+ video", "video", "video", "clip", "finished live"),
            wantRows,
        )
        assertTrue("no slot wants a height", site.slots.all { it.expect.minHeight == null })
        assertEquals(setOf(PRIVATE_OR_UNAVAILABLE, NO_MEDIA_FOUND), site.kind(8)?.expect?.reasons)
        val private = checkNotNull(site.kind(9)).expect
        assertEquals(setOf(LOGIN_REQUIRED), private.reasons)
        assertEquals(setOf(PRIVATE_OR_UNAVAILABLE, NO_MEDIA_FOUND), private.ambiguousWithoutSession)
        assertFalse(private.wantsRows(session = false))
        assertTrue(private.wantsRows(session = true))
        val region = checkNotNull(site.kind(10)).expect
        assertEquals(setOf(GEO_RESTRICTED), region.reasons)
        assertFalse(region.wantsRows(session = true))
        assertNull(site.kind(0))
        assertNull(site.kind(11))
        assertEquals("W's weekly slots: an 18+ video with list, a video, a removed one", listOf(1, 4, 8), SiteWeekly.SLOTS["vk"])
        assertFalse(SiteExtractionFailure.RESPONSE_CHANGED in site.slots.flatMap { it.expect.reasons })
    }

    @Test
    fun `VK's names are agent C's schema 2's - the site vk, the rungs V1, V2 and V3`() {
        listOf(VkClient.SITE_ID, VkLadder.SITE_ID, VkSite.SITE.id, VkRecipe.RECIPE.site).forEach { assertEquals("vk", it) }
        assertEquals(listOf("V1", "V2", "V3"), VkLadder.RUNGS)
    }

    /** A fake file reader: [answer] for each address; the addresses asked, in order. */
    private class Files(private val answer: (String) -> MediaStart) : MediaStartReader {
        val asked = mutableListOf<String>()

        override suspend fun read(url: String, headers: Map<String, String>, maxBytes: Int): MediaStart {
            asked += url
            return answer(url)
        }
    }

    private companion object {
        const val PAGE = "https://vk.com/video-100000001_400000001"
        const val API_FILES = "https://vk1-1.vkuser.net/fixture"
        const val EMBED_FILES = "https://vkvd1.okcdn.ru/fixture"
        const val TOTAL = 32_768L

        /** A row Master offers in a VK lookup (an MP4 file unless named). */
        fun row(url: String, kind: MediaKind = MediaKind.DIRECT) = MediaCandidate(
            pageUrl = PAGE,
            mediaUrl = url,
            sources = setOf(CandidateSource.DOM),
            kind = kind,
            requestContext = BrowserRequestContext(PAGE, AGENT, null),
            observedAtEpochMs = NOW,
            pageRole = PageMediaRole.MAIN,
        )

        /** A file's first bytes as its host answers them: an MP4 start of a 32 KiB file. */
        fun start(bytes: ByteArray = InitBytes.mp4()) =
            MediaStart.Read(206, "video/mp4", "bytes 0-${bytes.size - 1}/$TOTAL", bytes.size.toLong(), bytes)
    }
}
