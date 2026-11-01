package com.alal.yft.extractor.master.social

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.CompanionAudio
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PostVideoItem
import com.alal.yft.extractor.master.ContractAnswer
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.MasterStage
import com.alal.yft.extractor.master.NoPlaybackCaptureProvider
import com.alal.yft.extractor.master.PageSnapshot
import com.alal.yft.extractor.master.RecordingValidator
import com.alal.yft.extractor.master.contract.SiteContracts
import com.alal.yft.extractor.master.contract.contractRequest
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.sites.instagram.InstagramExtractor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Social plan S6 (B+, [SocialFill]): a weak answer of main's (no HD row, no sound row) gets only
 * the rows it lacks; a strong one is never weak (no run); main's rows are never changed; only rows
 * that may fill the need go to V. Made-up posts and files: no real account, link or token.
 */
class SocialFillTest {
    @Test
    fun `a weak answer - no HD - gets only the missing rows, proven main's video, each height's best, the tallest first`() {
        val mains = listOf(main("sd.mp4", title = "Clip — SD"), audio("a.mp4"))
        val weak = SocialFill.weakness(mains)
        assertEquals(setOf(SocialFill.Weak.NO_HD), weak)
        val masters = listOf(
            master("hd-low.mp4", 1280, 720, bytes = 1_000_000),
            master("hd.mp4", 1280, 720, bytes = 4_000_000),
            master("small.mp4", 640, 360, bytes = 500_000),
            master("full.mp4", 1920, 1080, bytes = 8_000_000),
            // main's own file (another query), another post's, a row naming no video, a silent HD file.
            master("sd.mp4?_nc=other", 1280, 720, bytes = 900_000),
            master("other.mp4", 1280, 720, bytes = 3_000_000, videoId = "facebook:999"),
            master("anon.mp4", 1280, 720, bytes = 3_000_000, videoId = null),
            master("mute.mp4", 1280, 720, bytes = 9_000_000, codecs = listOf("avc1.64001F")),
            // A length another post's (more than 1 s off main's): never main's video.
            master("long.mp4", 1280, 720, bytes = 9_000_000, millis = 95_000),
        )
        val before = mains.toList()
        val fill = SocialFill.added(mains, masters, weak, video = ::sameVideo)
        assertNull(fill.why)
        assertEquals(masters.size, fill.offered)
        assertEquals(listOf("full.mp4", "hd.mp4"), fill.rows.map(::name))
        fill.rows.forEach { row ->
            assertEquals(KEY, row.videoId)
            assertEquals("Clip", row.title)
        }
        // Main's rows: never changed, never among the added.
        assertEquals(before, mains)
        assertTrue(fill.rows.none { row -> mains.any { it.mediaUrl == row.mediaUrl } })
        assertEquals(
            "master: facebook B+ (main's rows: no HD): Master added 2: 1080p, 720p",
            SocialFill.line(SITE, weak, fill.rows),
        )
    }

    @Test
    fun `a weak answer - no sound - gets only rows with sound, and both weak ones only HD that states its sound`() {
        val mute = listOf(main("mute.mp4", 1280, 720, codecs = listOf("avc1.64001F")))
        val noSound = SocialFill.weakness(mute)
        assertEquals(setOf(SocialFill.Weak.NO_SOUND), noSound)
        val masters = listOf(
            master("tall-mute.mp4", 1920, 1080, bytes = 8_000_000, codecs = listOf("avc1.640028")),
            master("merged.mp4", 1280, 720, bytes = 4_000_000, sound = "s.mp4"),
            master("whole.mp4", 854, 480, bytes = 2_000_000, codecs = listOf("avc1.4D401F", "mp4a.40.2")),
            master("unsaid.mp4", 1280, 720, bytes = 5_000_000),
            audio("sound.mp4", master = true),
        )
        val fill = SocialFill.added(mute, masters, noSound, video = ::sameVideo)
        assertEquals(listOf("merged.mp4", "whole.mp4", "sound.mp4"), fill.rows.map(::name))
        assertEquals(
            "master: facebook B+ (main's rows: no sound): Master added 3: 720p, 480p, sound",
            SocialFill.line(SITE, noSound, fill.rows),
        )

        val both = listOf(main("sd-mute.mp4", 640, 360, codecs = listOf("avc1.4D401E")))
        val weak = SocialFill.weakness(both)
        assertEquals(setOf(SocialFill.Weak.NO_HD, SocialFill.Weak.NO_SOUND), weak)
        val bothFill = SocialFill.added(both, masters, weak, video = ::sameVideo)
        // HD that does not state its sound is not added; HD with its sound and the sound are.
        assertEquals(listOf("merged.mp4", "whole.mp4", "sound.mp4"), bothFill.rows.map(::name))
        assertEquals(
            "master: facebook B+ (main's rows: no HD and no sound): Master added none (Master found no row)",
            SocialFill.none(SITE, weak, "Master found no row"),
        )
    }

    @Test
    fun `a strong answer is never weak - HD, a manifest main did not read, an unknown height, several videos, a post's items`() {
        val strong = listOf(
            listOf(main("hd.mp4", title = "Clip — HD"), main("sd.mp4", title = "Clip — SD")),
            listOf(main("hd.mp4", 1280, 720)),
            listOf(main("sd.mp4", title = "Clip — 720p · HD")),
            listOf(main("sd.mp4", title = "Clip — SD"), main("dash.mpd", kind = MediaKind.DASH)),
            listOf(main("sd.mp4", title = "Clip — SD"), main("hls.m3u8", kind = MediaKind.HLS)),
            listOf(main("noname.mp4")),
            listOf(main("one.mp4", title = "Clip — SD"), main("two.mp4", title = "Clip — SD", videoId = "facebook:2")),
            listOf(main("item.mp4", title = "Clip — SD").copy(postItem = PostVideoItem(1, 2))),
            listOf(main("anon.mp4", title = "Clip — SD", videoId = null)),
            listOf(audio("a.mp4")),
            emptyList(),
        )
        strong.forEachIndexed { index, mains ->
            assertEquals("answer $index", emptySet<SocialFill.Weak>(), SocialFill.weakness(mains))
            val fill = SocialFill.added(mains, listOf(master("hd.mp4", 1280, 720, bytes = 4_000_000)), emptySet(), video = ::sameVideo)
            assertTrue(fill.rows.isEmpty())
            assertEquals("main's rows are not weak", fill.why)
        }
        // A manifest main read into rows (its rows name their codecs): the rows' heights count.
        val read = listOf(main("sd.mp4", 640, 360, codecs = listOf("avc1.4D401E", "mp4a.40.2")), main("hls.m3u8", kind = MediaKind.HLS))
        assertEquals(setOf(SocialFill.Weak.NO_HD), SocialFill.weakness(read))
    }

    @Test
    fun `V's rule - only rows that may fill the need are read - never main's files, a manifest, a silent picture`() {
        val mains = listOf(main("sd.mp4", title = "Clip — SD", sound = "main-sound.mp4"))
        val need = SocialFill.Need(mains, setOf(SocialFill.Weak.NO_HD))
        assertFalse(need.admits(master("sd.mp4", 1280, 720)))
        assertFalse(need.admits(master("sd.mp4?other=1", null, null)))
        assertFalse(need.admits(master("hls.m3u8", null, null, kind = MediaKind.HLS)))
        assertFalse(need.admits(audio("a.mp4", master = true)))
        assertFalse(need.admits(master("mute.mp4", 1280, 720, codecs = listOf("avc1.64001F"))))
        assertFalse(need.admits(master("small.mp4", 640, 360)))
        assertTrue(need.admits(master("hd.mp4", 1280, 720)))
        assertTrue(need.admits(master("unsized.mp4", null, null)))
        // Main's sound file may be another row's sound: only the row's own file counts.
        assertTrue(need.admits(master("hd2.mp4", 1280, 720, sound = "main-sound.mp4")))
        assertEquals(6, need.unread)
        val sound = SocialFill.Need(listOf(main("mute.mp4", 1280, 720, codecs = listOf("avc1.64001F"))), setOf(SocialFill.Weak.NO_SOUND))
        assertTrue(sound.admits(audio("a.mp4", master = true)))
        assertTrue(sound.admits(master("small.mp4", 640, 360)))
        assertFalse(sound.admits(master("mute2.mp4", 1920, 1080, codecs = listOf("avc1.640028"))))
    }

    @Test
    fun `the sites, the heights and the lines - counts, heights and words only`() {
        assertEquals("facebook", SocialFill.siteOf("facebook:1"))
        assertEquals("instagram", SocialFill.siteOf("instagram:C9fixtREEL1"))
        assertEquals("vk", SocialFill.siteOf("vk:-1_2"))
        listOf("tiktok:1", "x:1", "youtube:abc", null, "").forEach { assertNull(it, SocialFill.siteOf(it)) }
        assertEquals(720, SocialFill.heightOf(main("a.mp4", title = "Clip — HD")))
        assertEquals(720, SocialFill.heightOf(main("a.mp4", title = "Clip — 720p · HD")))
        assertEquals(1080, SocialFill.heightOf(main("a.mp4", title = "Clip — 1080p60")))
        assertEquals(480, SocialFill.heightOf(main("a.mp4", title = "Clip — SD")))
        assertNull(SocialFill.heightOf(main("a.mp4", title = "Clip — Full HD")))
        assertNull(SocialFill.heightOf(main("a.mp4", title = "Clip — HD", videoId = null)))
        assertEquals(360, SocialFill.heightOf(main("a.mp4", 640, 360, title = "Clip — HD")))
        assertEquals("master: vk B+", SocialFill.label("vk"))
        val line = SocialFill.line(SITE, setOf(SocialFill.Weak.NO_HD), listOf(master("hd.mp4", 1280, 720)))
        listOf("http", "cdn", "hd.mp4", "facebook:1", "Clip").forEach { assertFalse(line, it in line) }
    }

    @Test
    fun `the run - the lookup's ladder when it claims the lookup, only admitted rows read, no capture`() = runTest {
        val identity = checkNotNull(InstagramExtractor(FakeExtractorHttpClient()).identify(REEL))
        val request = contractRequest(REEL, identity)
        val mains = listOf(row("main480.mp4", 480, 854, videoId = IG_KEY, host = IG_HOST))
        val need = SocialFill.Need(mains, SocialFill.weakness(mains))
        assertEquals(setOf(SocialFill.Weak.NO_HD), need.weak)

        // Not claimed: nothing asked, nothing read.
        val unread = RecordingValidator()
        val skipped = SocialFill.run(request, need, unread, SiteContracts(FakeExtractorHttpClient()), Rung(claims = false))
        assertTrue(skipped is MasterResult.Skipped)
        assertTrue(unread.seen.isEmpty())

        // Claimed: of the answer's four files, main's and the 480p one are never read.
        val validator = RecordingValidator()
        val rung = Rung(claims = true)
        val result = SocialFill.run(request, need, validator, SiteContracts(FakeExtractorHttpClient()), rung)
        assertTrue(result.toString(), result is MasterResult.Success)
        assertEquals(listOf("big.mp4", "x720.mp4"), validator.seen.map(::name))
        assertEquals(listOf("big.mp4", "x720.mp4"), (result as MasterResult.Success).result.candidates.map(::name))
        assertEquals(2, need.unread)
        assertEquals(MasterStage.PAGE_DATA, result.stage)
        // Its time and no capture: the policy B+ runs with.
        assertEquals(1, rung.policies.size)
        assertFalse(rung.policies.single().capture)

        // Instagram's same item (decision 19 (iv)): the answer offered main's file, so its other
        // files are main's item; B+ adds the HD ones, the tallest first, under main's video.
        assertTrue(need.sharesMainsFile)
        val rows = result.result.candidates
        val fill = SocialFill.added(mains, rows, need.weak, need.sharesMainsFile, video = ::sameVideo)
        assertEquals(listOf("big.mp4", "x720.mp4"), fill.rows.map(::name))
        fill.rows.forEach { assertEquals(IG_KEY, it.videoId) }
        // Without that proof (no file of main's in the answer), no Instagram row is proven main's.
        val unproven = SocialFill.added(mains, rows, need.weak, sharesMainsFile = false, video = ::sameVideo)
        assertTrue(unproven.rows.isEmpty())
        assertEquals("no row of Master's proven main's video", unproven.why)
        // An answer without main's file: the proof is the answer's own.
        val other = SocialFill.Need(listOf(row("gone480.mp4", 480, 854, videoId = IG_KEY, host = IG_HOST)), setOf(SocialFill.Weak.NO_HD))
        val otherResult = SocialFill.run(request, other, RecordingValidator(), SiteContracts(FakeExtractorHttpClient()), Rung(claims = true))
        assertTrue(otherResult.toString(), otherResult is MasterResult.Success)
        assertFalse(other.sharesMainsFile)

        // None admitted (the tall files are main's own): none read, no row, no capture asked.
        val owned = listOf(row("big.mp4", 1080, 1920, videoId = IG_KEY, host = IG_HOST), row("x720.mp4", 720, 1280, videoId = IG_KEY, host = IG_HOST))
        val none = RecordingValidator()
        val noRows = SocialFill.run(request, SocialFill.Need(owned, setOf(SocialFill.Weak.NO_HD)), none, SiteContracts(FakeExtractorHttpClient()), Rung(claims = true))
        assertFalse(noRows.toString(), noRows is MasterResult.Success)
        assertTrue(none.seen.isEmpty())
        assertFalse(linesOf(noRows).toString(), linesOf(noRows).any { "capture" in it })
    }

    @Test
    fun `the engine - a rung's pick only orders and chooses among the rows V passed`() = runTest {
        val identity = checkNotNull(InstagramExtractor(FakeExtractorHttpClient()).identify(REEL))
        val request = contractRequest(REEL, identity)
        suspend fun answer(pick: ((List<MediaCandidate>) -> List<MediaCandidate>)?): Pair<List<String>, List<String>> {
            val engine = MasterFallbackEngine(
                RecordingValidator(), NoPlaybackCaptureProvider, MasterPolicy(enabled = true),
                modules = emptyList(), contracts = SiteContracts(FakeExtractorHttpClient()), social = Rung(claims = true, pick = pick),
            )
            val result = engine.extract(request)
            assertTrue(result.toString(), result is MasterResult.Success)
            return (result as MasterResult.Success).result.candidates.map(::name) to result.result.details
        }
        fun moved(lines: List<String>) = lines.any { "best first" in it || "rows picked" in it }

        val (all, plain) = answer(null)
        assertTrue(all.toString(), all.size >= 2)
        assertFalse(plain.toString(), moved(plain))
        val (reversed, reversedLines) = answer { it.reversed() }
        assertEquals(all.reversed(), reversed)
        assertTrue(reversedLines.toString(), "master: each height's best first" in reversedLines)
        val (two, twoLines) = answer { it.take(2) }
        assertEquals(all.take(2), two)
        assertTrue(twoLines.toString(), "master: rows picked: 2 of ${all.size}" in twoLines)
        val (same, sameLines) = answer { it }
        assertEquals(all, same)
        assertFalse(sameLines.toString(), moved(sameLines))

        // A row V did not pass, one twice, a copy, none, or a throw: V's rows as they are, no line.
        listOf<(List<MediaCandidate>) -> List<MediaCandidate>>(
            { rows -> rows + rows.first().copy(mediaUrl = "https://$IG_HOST/v/t42/other.mp4") },
            { rows -> listOf(rows.first(), rows.first()) },
            { rows -> rows.map { it.copy() } },
            { emptyList() },
            { error("fixture") },
        ).forEach { pick ->
            val (rows, lines) = answer(pick)
            assertEquals(all, rows)
            assertFalse(lines.toString(), moved(lines))
        }
    }

    private fun linesOf(result: MasterResult): List<String> = when (result) {
        is MasterResult.Success -> result.result.details
        is MasterResult.Failure -> result.details
        is MasterResult.NeedsPlayback -> result.details
        is MasterResult.NeedsSelection -> result.details
        is MasterResult.Skipped -> emptyList()
    }

    /**
     * A ladder stand-in: claims the lookup or not; its one answer holds [ANSWER]'s four files; [pick]:
     * its order of the rows V passed (L2), none by default.
     */
    private class Rung(
        private val claims: Boolean,
        private val pick: ((List<MediaCandidate>) -> List<MediaCandidate>)? = null,
    ) : SocialStage {
        val policies = mutableListOf<MasterPolicy>()

        override suspend fun claims(request: MasterRequest): Boolean = claims

        override suspend fun stage(request: MasterRequest, policy: MasterPolicy, details: MutableList<String>): SocialStage.Staged {
            policies += policy
            details += "master: instagram ladder after ${request.primaryFailure.name}; fixture"
            return SocialStage.Staged(
                MasterStage.PAGE_DATA,
                // As the ladder hands a JSON answer to the recipe: a page's data script.
                PageSnapshot(
                    request.pageUrl, request.generation, authorizedPlayback = true,
                    contract = ContractAnswer("instagram", "<script type=\"application/json\">$ANSWER</script>", InstagramProbeQuery.ENDPOINT),
                ),
                request,
                pick = pick,
            )
        }

        override fun toString(): String = "Rung(claims=$claims)"
    }

    private fun sameVideo(id: String?): String? = id

    private fun name(row: MediaCandidate) = row.mediaUrl.substringAfterLast('/')

    private fun main(
        file: String,
        width: Int? = null,
        height: Int? = null,
        title: String? = null,
        codecs: List<String> = emptyList(),
        kind: MediaKind = MediaKind.DIRECT,
        videoId: String? = KEY,
        sound: String? = null,
    ) = row(file, width, height, title, codecs, kind, videoId, sound)

    private fun master(
        file: String,
        width: Int? = null,
        height: Int? = null,
        bytes: Long? = null,
        codecs: List<String> = emptyList(),
        kind: MediaKind = MediaKind.DIRECT,
        videoId: String? = KEY,
        sound: String? = null,
        millis: Long = MILLIS,
    ) = row(file, width, height, null, codecs, kind, videoId, sound, bytes, millis)

    private fun audio(file: String, master: Boolean = false) = row(
        file, null, null, if (master) null else "Clip — Audio", listOf("mp4a.40.2"), MediaKind.DIRECT, KEY, null, mime = "audio/mp4",
    )

    private fun row(
        file: String,
        width: Int? = null,
        height: Int? = null,
        title: String? = null,
        codecs: List<String> = emptyList(),
        kind: MediaKind = MediaKind.DIRECT,
        videoId: String? = KEY,
        sound: String? = null,
        bytes: Long? = null,
        millis: Long = MILLIS,
        mime: String = "video/mp4",
        host: String = FB_HOST,
    ) = MediaCandidate(
        pageUrl = PAGE,
        mediaUrl = "https://$host/v/t42/$file",
        sources = setOf(CandidateSource.MANIFEST),
        kind = kind,
        mimeType = if (kind == MediaKind.DIRECT) mime else null,
        title = title,
        durationMillis = millis,
        contentLengthBytes = bytes,
        codecs = codecs,
        width = width,
        height = height,
        videoId = videoId,
        audioCompanion = sound?.let {
            CompanionAudio("https://$host/v/t42/$it", "audio/mp4", listOf("mp4a.40.2"), BrowserRequestContext(PAGE, null, null))
        },
    )

    private companion object {
        const val SITE = "facebook"
        const val KEY = "facebook:1"
        const val PAGE = "https://www.facebook.com/watch/?v=1"
        const val FB_HOST = "video.fxyz1-1.fna.fbcdn.net"
        const val IG_HOST = "instagram.fxyz1-1.fna.fbcdn.net"
        const val REEL = "https://www.instagram.com/reel/C9fixtREEL1/"
        const val IG_KEY = "instagram:C9fixtREEL1"
        const val MILLIS = 30_000L

        /** A made-up answer naming the reel: main's 480p file, another 480p one, a 1080p and a 720p. */
        val ANSWER = """{"data":{"xig_polaris_media":{"if_not_gated_logged_out":{"code":"C9fixtREEL1","media_type":2,""" +
            """"video_duration":30.0,"video_versions":[""" +
            listOf("main480.mp4" to (480 to 854), "small.mp4" to (480 to 854), "big.mp4" to (1080 to 1920), "x720.mp4" to (720 to 1280))
                .joinToString(",") { (file, size) -> """{"width":${size.first},"height":${size.second},"url":"https://$IG_HOST/v/t42/$file"}""" } +
            """]}}},"status":"ok"}"""
    }
}
