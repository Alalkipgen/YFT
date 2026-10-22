package com.alal.yft.extractor.master.social

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.CaptureResult
import com.alal.yft.extractor.master.CapturedRequest
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterMediaValidator
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.MasterStage
import com.alal.yft.extractor.master.NOW
import com.alal.yft.extractor.master.PageSnapshot
import com.alal.yft.extractor.master.RecordingValidator
import com.alal.yft.extractor.master.ValidationResult
import com.alal.yft.extractor.master.contract.AGENT
import com.alal.yft.extractor.master.contract.CountingCapture
import com.alal.yft.extractor.master.contract.SiteContracts
import com.alal.yft.extractor.master.contract.contractRequest
import com.alal.yft.extractor.master.contract.mainRequest
import com.alal.yft.extractor.master.lookup.LookupAsk
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.lookup.LookupRecorder
import com.alal.yft.extractor.master.modules.youtube.ladder.InitBytes
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStart
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStartReader
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.master.modules.youtube.testing.Fixtures
import com.alal.yft.extractor.sites.facebook.FacebookExtractor
import com.alal.yft.extractor.sites.tiktok.TikTokExtractor
import com.alal.yft.extractor.sites.x.XExtractor
import java.net.URI
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Social plan S2c: X's ladder in Master's engine. X1 replays main's answer from X's widget in this
 * lookup ([MainPages]: its page or its refused GET) with no new ask and judges it: the link's item
 * only (its `/video/N` among the post's own or card's media, else the first; a quoted post's video
 * bound to the link's post), its MP4 files first, its playlist only without them and with a known
 * length; a photo post is final; the widget's 404 (task 0: no proof of removal; the tab's own
 * playback next, main's reason stays), a tombstone (age-limited: the browser; X's notice, removed
 * or protected alike: playback), `{}`, another post, another status and a 429 are never final. A renamed key: main's whole answer by shape, only as one item's rows. S7's
 * budget; [SocialStages] gives an X lookup X's ladder; V asks X's media host only and cannot pass
 * a playlist. Main's real X adapter over main's fixtures; Master never asks; no live calls.
 */
class XLadderTest {
    @Test
    fun `X1 replays main's answer with no new ask - the post's own video, main's MP4 rows with their length`() = runTest {
        val record = record()
        val validator = RecordingValidator()
        val capture = CountingCapture()
        val master = FakeExtractorHttpClient()
        val main = main(VIDEO, Fixtures.read("x/tweet_video.json"))

        val lookup = lookup(page(VIDEO), main, master, record, validator = validator, capture = capture)

        val mains = (lookup.main as SiteExtractionResult.Success).candidates
        val success = lookup.result as MasterResult.Success
        assertEquals(MasterStage.PAGE_DATA, success.stage)
        val rows = success.result.candidates
        assertEquals(3, rows.size)
        assertEquals(facts(mains), facts(rows))
        rows.forEach { row ->
            assertEquals("x:$VIDEO", row.videoId)
            assertEquals(MediaKind.DIRECT, row.kind)
            assertEquals(31_533L, row.durationMillis)
            assertEquals("ext_tw_video/$VIDEO_FILES", XLadder.itemOf(row.mediaUrl))
        }
        assertFalse("the playlist is no row beside MP4 files", rows.any { ".m3u8" in it.mediaUrl })
        assertEquals(3, validator.seen.size)
        assertEquals(0, capture.calls)
        assertTrue(master.requestedUrls.isEmpty())
        assertEquals(1, main.requestedUrls.size)
        assertEquals(0, record.masterAsks)
        assertEquals(1, record.mainAsks)
        val details = success.result.details
        assertTrue(details.toString(), "$LADDER after RESPONSE_CHANGED; main's answer replayed, no new ask" in details)
        assertTrue(
            details.toString(),
            "$ITEM: the post's own video 1, 1 in the post; MP4 files 3; playlist yes; length known" in details,
        )
        assertTrue(details.toString(), "$X1: DIRECT (rows 3, bound to the post ID); next: CHECK_ROWS" in details)
        assertBare(details)
    }

    @Test
    fun `only the link's item - its video N among the post's own or card's media, else the first, as main`() = runTest {
        val cases = listOf(
            Item(page(MIXED, 3), "the post's own video 3, 2 in the post; MP4 files 1; playlist no; length known", "amplify_video/1786000000000000003", 9_000),
            Item(page(MIXED), "the post's own video 2, 2 in the post; MP4 files 2; playlist no; length known", "amplify_video/1786000000000000002", 12_000),
            Item(page(MIXED, 1), "the post's own video 2, 2 in the post; MP4 files 2; playlist no; length known", "amplify_video/1786000000000000002", 12_000),
            Item(page(CARD), "the card's video 1, 1 in the post; MP4 files 3; playlist yes; length known", "amplify_video/1786000000000000081", 15_015),
            Item(page(CAROUSEL), "the card's video 1, 2 in the post; MP4 files 2; playlist yes; length known", "amplify_video/1786000000000000091", 12_012),
            Item(page(CAROUSEL, 2), "the card's video 2, 2 in the post; MP4 files 2; playlist yes; length known", "amplify_video/1786000000000000092", 9_009),
            Item(page(QUOTED), "the quoted post's video 1, 1 in the post; MP4 files 1; playlist no; length known", "ext_tw_video/9", 5_000),
            Item(page(QUOTED, 2), "the quoted post's video 1, 1 in the post; MP4 files 1; playlist no; length known", "ext_tw_video/9", 5_000),
            Item(page(GIF), "the post's own GIF 1, 1 in the post; MP4 files 1; playlist no; length unknown", "tweet_video/gif", null),
        )
        for (case in cases) {
            val id = identify(case.link).contentId
            val validator = RecordingValidator()
            val capture = CountingCapture()
            val master = FakeExtractorHttpClient()

            val lookup = lookup(case.link, main(id, Fixtures.read(FIXTURES.getValue(id))), master, validator = validator, capture = capture)

            val mains = (lookup.main as SiteExtractionResult.Success).candidates
            val success = lookup.result as MasterResult.Success
            val rows = success.result.candidates
            assertEquals(case.line, facts(mains), facts(rows))
            rows.forEach { row ->
                assertEquals(case.line, "x:$id", row.videoId)
                assertEquals(case.line, case.item, XLadder.itemOf(row.mediaUrl))
                assertEquals(case.line, case.millis, row.durationMillis)
            }
            assertEquals(case.line, rows.size, validator.seen.size)
            assertEquals(case.line, 0, capture.calls)
            assertTrue(case.line, master.requestedUrls.isEmpty())
            val details = success.result.details
            assertTrue(details.toString(), "$ITEM: ${case.line}" in details)
            assertTrue(details.toString(), "$X1: DIRECT (rows ${rows.size}, bound to the post ID); next: CHECK_ROWS" in details)
            assertBare(details)
        }
    }

    @Test
    fun `a photo post is final with no request, never captured (unchanged by task 0)`() = runTest {
        val cases = listOf(
            Final(
                main(PHOTOS, Fixtures.read("x/tweet_photos.json")), page(PHOTOS), SiteExtractionFailure.NO_MEDIA_FOUND,
                "$X1: PHOTO_ONLY (photos only, no video); next: FINAL",
            ),
        )
        for (case in cases) {
            val validator = RecordingValidator()
            val capture = CountingCapture()
            val master = FakeExtractorHttpClient()
            val pages = MainPages(site = SITE)

            val lookup = lookup(case.link, case.main, master, pages = pages, validator = validator, capture = capture)

            assertEquals(case.line, SiteExtractionFailure.NO_MEDIA_FOUND, (lookup.main as SiteExtractionResult.Failure).reason)
            val failure = lookup.result as MasterResult.Failure
            assertEquals(case.line, case.reason, failure.reason)
            assertTrue(failure.details.toString(), case.line in failure.details)
            assertTrue(failure.details.toString(), "master: page reports ${case.reason.name}" in failure.details)
            assertFalse(failure.details.toString(), failure.details.any { it.startsWith(ITEM) })
            assertTrue(case.line, validator.seen.isEmpty())
            assertEquals(case.line, 0, capture.calls)
            assertTrue(case.line, master.requestedUrls.isEmpty())
            assertEquals(case.line, 1, case.main.requestedUrls.size)
            assertBare(failure.details)
        }
    }

    @Test
    fun `task 0 - the widget's 404 is never final - no new ask, the tab's own playback next, main's reason stays`() = runTest {
        // (b) Nothing found by the tab's playback: needs playback (the existing fallback), no reason of
        // Master's own, so main's reason stays; never REMOVED, never final.
        val validator = RecordingValidator()
        val capture = CountingCapture()
        val master = FakeExtractorHttpClient()
        val main = FakeExtractorHttpClient() // every GET a 404: the widget's 404 for the post
        val pages = MainPages(site = SITE)
        val cooldown = SocialCooldown()

        val lookup = lookup(
            page(OTHER), main, master, pages = pages, validator = validator, capture = capture,
            ladder = XLadder(master, ::identifyOrNull, cooldown = cooldown),
        )

        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, (lookup.main as SiteExtractionResult.Failure).reason)
        val details = (lookup.result as MasterResult.NeedsPlayback).details
        assertTrue(details.toString(), NOT_FOUND_LINE in details)
        assertTrue(details.toString(), details.none { "REMOVED" in it || "next: FINAL" in it })
        assertTrue(details.toString(), details.none { "PRIVATE_OR_UNAVAILABLE" in it })
        assertEquals(1, capture.calls)
        assertTrue(validator.seen.isEmpty())
        // No new widget request, no X2, no token check: Master asks nothing; main asked once.
        assertTrue(master.requestedUrls.isEmpty())
        assertEquals(1, main.requestedUrls.size)
        // A 404 is no 429: the widget's host does not cool.
        assertFalse(cooldown.cooling(WIDGET_HOST))
        assertBare(details)
        // The 404 was kept as main's refused GET only: no page.
        assertTrue(pages.pages().isEmpty())
        assertEquals(404, pages.refusals().single().statusCode)

        // (b) The tab's own playback finds the post's video: the existing capture fallback answers.
        val file = "https://video.twimg.com/ext_tw_video/$VIDEO_FILES/pu/vid/avc1/720x1280/fixture.mp4"
        val played = CountingCapture(
            CaptureResult.Available(
                PageSnapshot(
                    page(OTHER), 1, requests = listOf(CapturedRequest(file, mimeType = "video/mp4")),
                    playingMediaUrl = file, authorizedPlayback = true,
                ),
            ),
        )
        val replayed = FakeExtractorHttpClient()
        val recovered = lookup(page(OTHER), FakeExtractorHttpClient(), replayed, capture = played)
        val success = recovered.result as MasterResult.Success
        assertEquals(MasterStage.PLAYBACK_CAPTURE, success.stage)
        assertEquals(file, success.result.candidates.single().mediaUrl)
        assertEquals(1, played.calls)
        assertTrue(replayed.requestedUrls.isEmpty())
    }

    @Test
    fun `the widget's 404, a tombstone, an empty answer, another post, another status and a 429 are never final`() = runTest {
        val video = Fixtures.read("x/tweet_video.json")
        val cases = listOf(
            Never(main(VIDEO) { ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 404) }, NOT_FOUND_LINE),
            Never(main(VIDEO, EMPTY), "$X1: VALUE_REJECTED (an empty answer, as without the widget's check value); next: NEXT_RUNG"),
            Never(main(VIDEO, EMPTY_TOMBSTONE), "$X1: AGE_LIMITED (an empty tombstone); next: BROWSER"),
            Never(
                main(VIDEO, NOTICE_TOMBSTONE),
                "$X1: NEEDS_PLAYBACK (a tombstone with X's notice: removed, protected or suspended alike); next: PLAYBACK",
            ),
            Never(main(VIDEO, Fixtures.read("x/tweet_tombstone.json")), "$X1: NEEDS_PLAYBACK (a tombstone without X's notice); next: PLAYBACK"),
            Never(main(VIDEO, video.replace("\"id_str\":\"$VIDEO\"", "\"id_str\":\"$OTHER\"")), "$X1: SHAPE_CHANGED (names another post); next: NEXT_RUNG"),
            Never(main(VIDEO, "<html><body>fixture</body></html>"), "$X1: SHAPE_CHANGED (not a JSON object); next: NEXT_RUNG"),
            Never(
                main(VIDEO) { ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 503) },
                "$X1: TRANSIENT (failed: HTTP_STATUS 503); next: NEXT_RUNG",
            ),
            Never(
                main(VIDEO) { ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 429) },
                "$X1: RATE_LIMITED (status 429); next: STOP_SITE",
            ),
        )
        for (case in cases) {
            val validator = RecordingValidator()
            val capture = CountingCapture()
            val master = FakeExtractorHttpClient()
            val cooldown = SocialCooldown()

            val lookup = lookup(
                page(VIDEO), case.main, master, validator = validator, capture = capture,
                ladder = XLadder(master, ::identifyOrNull, cooldown = cooldown),
            )

            val details = (lookup.result as MasterResult.NeedsPlayback).details
            assertTrue(details.toString(), case.line in details)
            assertEquals(case.line, 1, capture.calls)
            assertTrue(case.line, validator.seen.isEmpty())
            assertTrue(case.line, master.requestedUrls.isEmpty())
            assertEquals(case.line, 1, case.main.requestedUrls.size)
            // S7: a 429 to main's ask cools the widget's host from the stage's end.
            assertEquals(case.line, "RATE_LIMITED" in case.line, cooldown.cooling(WIDGET_HOST))
            assertBare(details)
        }

        // Main's own 429 (main's reason RATE_LIMITED): Master is not asked at all, as for every site.
        val capture = CountingCapture()
        val limited = lookup(
            page(VIDEO), main(VIDEO) { ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429) }, capture = capture,
        )
        assertEquals(MasterResult.Skipped(SiteExtractionFailure.RATE_LIMITED), limited.result)
        assertEquals(0, capture.calls)
    }

    @Test
    fun `a playlist-only item is read only with its length, and V never passes a playlist`() = runTest {
        val playlist = Fixtures.read("x/tweet_hls_only.json")

        // Staged: the playlist alone is the item's row, main's row, with its length.
        val staged = lookup(page(HLS_ONLY), main(HLS_ONLY, playlist))
        val mains = (staged.main as SiteExtractionResult.Success).candidates
        val row = (staged.result as MasterResult.Success).result.candidates.single()
        assertEquals(mains.single().mediaUrl, row.mediaUrl)
        assertEquals(MediaKind.HLS, row.kind)
        assertEquals(600_000L, row.durationMillis)
        assertTrue(
            staged.result.toString(),
            "$ITEM: the post's own video 1, 1 in the post; MP4 files 0; playlist yes; length known" in
                (staged.result as MasterResult.Success).result.details,
        )

        // V (S6): a playlist has no file start, so no pass, nothing asked; the tab's own playback.
        val files = Files { start() }
        val capture = CountingCapture()
        val checked = lookup(page(HLS_ONLY), main(HLS_ONLY, playlist), validator = SocialRowCheck(RecordingValidator(), files), capture = capture)
        val failure = checked.result as MasterResult.Failure
        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, failure.reason)
        assertTrue(failure.details.toString(), "$X1: DIRECT (rows 1, bound to the post ID); next: CHECK_ROWS" in failure.details)
        assertTrue(files.asked.isEmpty())
        assertEquals(1, capture.calls)

        // Without its length: nothing to check.
        val validator = RecordingValidator()
        val unknown = lookup(
            page(HLS_ONLY), main(HLS_ONLY, playlist.replace("\"duration_millis\":600000,", "")),
            validator = validator, capture = CountingCapture(),
        )
        val details = (unknown.result as MasterResult.NeedsPlayback).details
        assertTrue(details.toString(), "$ITEM: the post's own video 1, 1 in the post; MP4 files 0; playlist yes; length unknown" in details)
        assertTrue(details.toString(), "$X1: NEEDS_PLAYBACK (the item's playlist only, no length); next: PLAYBACK" in details)
        assertTrue(validator.seen.isEmpty())
        assertBare(details + failure.details)
    }

    @Test
    fun `a renamed key - main's whole answer by shape, only when its rows are one item's`() = runTest {
        // One item's files: main's whole answer, read by shape, goes to V.
        val renamed = Fixtures.read("x/tweet_video.json").replace("\"variants\":", "\"variants_v2\":")
        val validator = RecordingValidator()
        val one = lookup(page(VIDEO), main(VIDEO, renamed), validator = validator)
        val success = one.result as MasterResult.Success
        val rows = success.result.candidates
        assertTrue(rows.isNotEmpty())
        rows.forEach { assertEquals("ext_tw_video/$VIDEO_FILES", XLadder.itemOf(it.mediaUrl)) }
        rows.forEach { assertEquals("x:$VIDEO", it.videoId) }
        val details = success.result.details
        assertTrue(
            details.toString(),
            details.any { it.startsWith("$X1: PARTIAL (rows ") && it.endsWith(", main's whole answer read, one item); next: CHECK_ROWS") },
        )
        assertFalse(details.toString(), details.any { it.startsWith(ITEM) })

        // Two items' files: no guess, nothing to check.
        val mixed = Fixtures.read("x/tweet_mixed.json").replace("\"variants\":", "\"variants_v2\":")
        val unchecked = RecordingValidator()
        val two = lookup(page(MIXED), main(MIXED, mixed), validator = unchecked)
        val twoDetails = (two.result as MasterResult.NeedsPlayback).details
        assertTrue(twoDetails.toString(), "$X1: SHAPE_CHANGED (rows by shape for 2 items, not one); next: NEXT_RUNG" in twoDetails)
        assertTrue(unchecked.seen.isEmpty())
        assertBare(details + twoDetails)
    }

    @Test
    fun `S7 - the rows go to V only within X's budget, and only main's kept answer is read`() = runTest {
        val video = Fixtures.read("x/tweet_video.json")

        // Time: main's ask took the lookup's 20 seconds.
        var now = 1_000L
        val timed = LookupRecord(clock = { now }, labels = SiteWays.X.labels())
        val slow = main(VIDEO) { url ->
            now += SocialBudget.X.millis
            json(video, url)
        }
        // Asks: eight before main's (another site's pages), main's the ninth.
        val busy = record().apply {
            repeat(SocialBudget.X.requests - 1) {
                val url = "https://example.test/page-$it"
                record(LookupAsk(LookupAsk.GET, url, null), ExtractorHttpResult.Success(200, "{}", url, JSON), NOW)
            }
        }
        val cases = listOf(
            Budget(slow, timed, XLadder(FakeExtractorHttpClient(), ::identifyOrNull), "over the lookup's time budget"),
            Budget(main(VIDEO, video), busy, XLadder(FakeExtractorHttpClient(), ::identifyOrNull), "over the lookup's request budget"),
            Budget(
                main(VIDEO, video), record(),
                XLadder(FakeExtractorHttpClient(), ::identifyOrNull, budget = SocialBudget.X.copy(bytes = 64)),
                "over the lookup's byte budget",
            ),
        )
        for (case in cases) {
            val validator = RecordingValidator()
            val capture = CountingCapture()

            val lookup = lookup(page(VIDEO), case.main, record = case.record, validator = validator, capture = capture, ladder = case.ladder)

            val details = (lookup.result as MasterResult.NeedsPlayback).details
            assertTrue(details.toString(), "$X1: DIRECT (rows 3, bound to the post ID); next: CHECK_ROWS" in details)
            assertTrue(details.toString(), "$X1: ${case.why}; not checked" in details)
            assertTrue(case.why, validator.seen.isEmpty())
            assertEquals(case.why, 1, capture.calls)
            assertBare(details)
        }

        // No answer of main's for the post: none kept (main read another post), or over the bound.
        val elsewhere = withContext(record() + MainPages(site = SITE)) {
            XExtractor(MainPageRecorder(LookupRecorder(main(MIXED, Fixtures.read("x/tweet_mixed.json")))))
                .extract(mainRequest(identify(page(MIXED))))
            engine(FakeExtractorHttpClient()).extract(contractRequest(page(VIDEO), identify(page(VIDEO)), SiteExtractionFailure.NO_MEDIA_FOUND))
        }
        assertTrue(elsewhere.toString(), "$X1: none kept for the post; nothing to read" in detailsOf(elsewhere))
        // Task 12: main's answer remembered (P17), main asked nothing in this lookup: the line says so.
        for (reuse in LookupRecord.Reuse.entries) {
            val unasked = withContext(record().also { it.noteReuse(reuse) } + MainPages(site = SITE)) {
                engine(FakeExtractorHttpClient()).extract(contractRequest(page(VIDEO), identify(page(VIDEO)), SiteExtractionFailure.RESPONSE_CHANGED))
            }
            assertTrue(
                unasked.toString(),
                "$X1: none kept for the post (main asked nothing in this lookup: ${reuse.label}); nothing to read" in detailsOf(unasked),
            )
            assertTrue(unasked is MasterResult.NeedsPlayback)
        }
        val bounded = lookup(page(VIDEO), main(VIDEO, video), pages = MainPages(maxChars = 64, site = SITE))
        assertTrue(bounded.result.toString(), "$X1: not kept (over the bound); nothing to read" in detailsOf(bounded.result))
        assertTrue(bounded.result is MasterResult.NeedsPlayback)
        assertBare(detailsOf(elsewhere) + detailsOf(bounded.result))
    }

    @Test
    fun `SocialStages gives an X lookup X's ladder, and only an X lookup inside its record`() = runTest {
        val http = FakeExtractorHttpClient()
        val stages = SocialStages(
            listOf(FacebookLadder(http, ::anyIdentity), TikTokLadder(http, ::anyIdentity), XLadder(http, ::anyIdentity)),
        )
        val engine = MasterFallbackEngine(
            RecordingValidator(), CountingCapture(), MasterPolicy(enabled = true),
            contracts = SiteContracts(http), social = stages,
        )
        val main = main(VIDEO, Fixtures.read("x/tweet_video.json"))

        val x = withContext(record() + MainPages(site = SITE)) {
            XExtractor(MainPageRecorder(LookupRecorder(main))).extract(mainRequest(identify(page(VIDEO))))
            engine.extract(contractRequest(page(VIDEO), identify(page(VIDEO))))
        } as MasterResult.Success
        assertTrue(x.result.details.any { it.startsWith(X1) })
        assertFalse(x.result.details.any { it.startsWith("master: facebook") || it.startsWith("master: tiktok") })
        assertTrue("X's ladder asks nothing", http.requestedUrls.isEmpty())

        // A TikTok lookup: TikTok's ladder, never X's.
        val tiktok = checkNotNull(TikTokExtractor(FakeExtractorHttpClient()).identify(TIKTOK_PAGE))
        val onTikTok = withContext(LookupRecord(labels = SiteWays.TIKTOK.labels()) + MainPages(site = "tiktok")) {
            engine.extract(contractRequest(TIKTOK_PAGE, tiktok))
        }
        assertTrue(detailsOf(onTikTok).any { it.startsWith("master: tiktok") })
        assertFalse(detailsOf(onTikTok).any { it.startsWith("master: x") })

        // Another site's lookup, or an X lookup without the record: not claimed (R8's path).
        val other = SitePageIdentity("vimeo", "100000001", "https://vimeo.com/100000001")
        withContext(record() + MainPages()) {
            assertFalse(stages.claims(contractRequest(other.canonicalPageUrl, other)))
            assertTrue(stages.claims(contractRequest(page(VIDEO), identify(page(VIDEO)))))
        }
        assertFalse(stages.claims(contractRequest(page(VIDEO), identify(page(VIDEO)))))
        assertFalse(XLadder(http, ::anyIdentity).claims(contractRequest(page(VIDEO), identify(page(VIDEO)))))
    }

    @Test
    fun `V in an X lookup asks X's media host only`() = runTest {
        val files = Files { start() }
        val check = SocialRowCheck(RecordingValidator(), files)
        val xRow = row(X_FILE)
        val tiktokRow = row("https://v16.fixture.tiktokcdn.com/video/tos/useast5/fixture.mp4?mime_type=video_mp4&expire=4102444800")
        val playlistRow = row(X_PLAYLIST, MediaKind.HLS)

        val checked = withContext(record() + MainPages(site = SITE)) {
            listOf(xRow, tiktokRow, playlistRow).map { check.validate(it, NOW) }
        }

        assertTrue(checked.first().toString(), checked.first() is ValidationResult.Valid)
        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, (checked[1] as ValidationResult.Rejected).reason)
        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, (checked[2] as ValidationResult.Rejected).reason)
        assertEquals(listOf(X_FILE), files.asked)
    }

    @Test
    fun `itemOf names a file's video on X's video host only`() {
        assertEquals("amplify_video/1786000000000000002", XLadder.itemOf("https://video.twimg.com/amplify_video/1786000000000000002/vid/avc1/1920x1080/v2_1080.mp4?tag=16"))
        assertEquals("ext_tw_video/$VIDEO_FILES", XLadder.itemOf(X_PLAYLIST))
        assertEquals("tweet_video/gif", XLadder.itemOf("https://video.twimg.com/tweet_video/gif.mp4"))
        assertEquals("ext_tw_video/9", XLadder.itemOf("https://VIDEO.twimg.com./ext_tw_video/9/pu/vid/avc1/640x360/q360.mp4"))
        assertNull(XLadder.itemOf("http://video.twimg.com/ext_tw_video/9/pu/vid/q360.mp4"))
        assertNull(XLadder.itemOf("https://evil.example.test/amplify_video/1786000000000000003/v3.mp4"))
        assertNull(XLadder.itemOf("https://pbs.twimg.com/ext_tw_video_thumb/9/pu/img/q.jpg"))
        assertNull(XLadder.itemOf("https://video.twimg.com/file.mp4"))
        assertNull(XLadder.itemOf("not a link"))
        assertEquals(3, XLadder.videoNumberOf("https://x.com/fixture_user/status/$MIXED/video/3"))
        assertNull(XLadder.videoNumberOf("https://x.com/fixture_user/status/$MIXED"))
    }

    /** One lookup: main's real X adapter over [main] (its answer kept in the lookup), then Master's engine. */
    private suspend fun lookup(
        link: String,
        main: FakeExtractorHttpClient,
        master: FakeExtractorHttpClient = FakeExtractorHttpClient(),
        record: LookupRecord = record(),
        pages: MainPages = MainPages(site = SITE),
        validator: MasterMediaValidator = RecordingValidator(),
        capture: CountingCapture = CountingCapture(),
        ladder: XLadder = XLadder(master, ::identifyOrNull),
    ): Lookup {
        val identity = identify(link)
        return withContext(record + pages) {
            val answer = XExtractor(MainPageRecorder(LookupRecorder(main))).extract(mainRequest(identity))
            // Main's rows set aside (the trial), else main's reason.
            val failure = (answer as? SiteExtractionResult.Failure)?.reason ?: SiteExtractionFailure.RESPONSE_CHANGED
            Lookup(answer, engine(master, validator, capture, ladder).extract(contractRequest(link, identity, failure)))
        }
    }

    private class Lookup(val main: SiteExtractionResult, val result: MasterResult)

    private class Item(val link: String, val line: String, val item: String, val millis: Long?)

    private class Final(val main: FakeExtractorHttpClient, val link: String, val reason: SiteExtractionFailure, val line: String)

    private class Never(val main: FakeExtractorHttpClient, val line: String)

    private class Budget(val main: FakeExtractorHttpClient, val record: LookupRecord, val ladder: XLadder, val why: String)

    private fun engine(
        http: FakeExtractorHttpClient,
        validator: MasterMediaValidator = RecordingValidator(),
        capture: CountingCapture = CountingCapture(),
        ladder: XLadder = XLadder(http, ::identifyOrNull),
    ) = MasterFallbackEngine(
        validator, capture, MasterPolicy(enabled = true),
        contracts = SiteContracts(http),
        social = ladder,
    )

    private fun record() = LookupRecord(labels = SiteWays.X.labels())

    private fun detailsOf(result: MasterResult): List<String> = when (result) {
        is MasterResult.Success -> result.result.details
        is MasterResult.Failure -> result.details
        is MasterResult.NeedsPlayback -> result.details
        is MasterResult.NeedsSelection -> result.details
        is MasterResult.Skipped -> emptyList()
    }

    /** What the trial compares: each row's file, its picture's height and its length. */
    private fun facts(rows: List<MediaCandidate>) = rows.map { Triple(it.mediaUrl, it.height, it.durationMillis) }.toSet()

    /** No ID, link, handle or post words in the lines. */
    private fun assertBare(details: List<String>) {
        val ids = FIXTURES.keys + listOf(OTHER, VIDEO_FILES, "1786000000000000002", "1786000000000000003", "1786000000000000081")
        details.forEach { line ->
            (ids + listOf("x.com", "twimg", "fixture_user", "Morning run", "two clips", "look at this", "sunset", "live replay", "shop"))
                .forEach { assertFalse("$line has $it", line.contains(it)) }
        }
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
        const val SITE = "x"
        const val VIDEO = "1785999999999999999"
        const val VIDEO_FILES = "1785999990000000000"
        const val MIXED = "1786000000000000001"
        const val GIF = "1786000000000000010"
        const val QUOTED = "1786000000000000020"
        const val PHOTOS = "1786000000000000030"
        const val HLS_ONLY = "1786000000000000040"
        const val CARD = "1786000000000000080"
        const val CAROUSEL = "1786000000000000090"
        const val OTHER = "1786000000000000099"
        const val WIDGET_HOST = "cdn.syndication.twimg.com"
        const val JSON = "application/json"
        const val LADDER = "master: x ladder"
        const val X1 = "master: x X1 main's answer"

        /** Task 0: X1's line for the widget's 404 (never final; the tab's own playback next). */
        const val NOT_FOUND_LINE = "$X1: NEEDS_PLAYBACK (status 404: no post in the widget's answer, not proof of removal); next: PLAYBACK"
        const val ITEM = "master: x X1 item"
        const val TIKTOK_PAGE = "https://www.tiktok.com/@fixture_user/video/7311234567890123456"
        const val X_FILE = "https://video.twimg.com/ext_tw_video/$VIDEO_FILES/pu/vid/avc1/720x1280/a720.mp4?tag=12"
        const val X_PLAYLIST = "https://video.twimg.com/ext_tw_video/$VIDEO_FILES/pu/pl/fixture.m3u8?tag=12&v=1"
        const val TOTAL = 32_768L

        /** X's answer without the widget's token (live 2026-10-21). */
        const val EMPTY = "{}"

        /** The age-limited post's answer (live shape 2026-10-21): an empty tombstone. */
        const val EMPTY_TOMBSTONE = """{"__typename":"TweetTombstone","tombstone":{}}"""

        /** A deleted post's answer (live shape 2026-10-21; a protected post's is the same but its words). */
        const val NOTICE_TOMBSTONE =
            """{"__typename":"TweetTombstone","tombstone":{"text":{"text":"This Post was deleted by the Post author. Learn more",""" +
                """"entities":[{"from_index":41,"to_index":51,"ref":{"__typename":"TimelineUrl",""" +
                """"url":"https://help.x.com/rules-and-policies/notices-on-x","url_type":"ExternalUrl"}}],"rtl":false}}}"""

        /** Main's fixture for each post. */
        val FIXTURES = mapOf(
            VIDEO to "x/tweet_video.json",
            MIXED to "x/tweet_mixed.json",
            GIF to "x/tweet_gif.json",
            QUOTED to "x/tweet_quoted.json",
            PHOTOS to "x/tweet_photos.json",
            HLS_ONLY to "x/tweet_hls_only.json",
            CARD to "x/tweet_card_video.json",
            CAROUSEL to "x/tweet_card_carousel.json",
        )

        fun page(id: String, video: Int? = null) =
            "https://x.com/fixture_user/status/$id" + (video?.let { "/video/$it" } ?: "")

        fun identifyOrNull(url: String): SitePageIdentity? = XExtractor(FakeExtractorHttpClient()).identify(url)

        fun identify(url: String): SitePageIdentity = checkNotNull(identifyOrNull(url)) { url }

        /** The app's adapters' identify, three sites. */
        fun anyIdentity(url: String): SitePageIdentity? = identifyOrNull(url)
            ?: TikTokExtractor(FakeExtractorHttpClient()).identify(url)
            ?: FacebookExtractor(FakeExtractorHttpClient()).identify(url)

        fun json(body: String, url: String) = ExtractorHttpResult.Success(200, body, url, JSON)

        /** Main's client: X's widget answers [body] for post [id] (any token); every other GET a 404. */
        fun main(id: String, body: String) = main(id) { url -> json(body, url) }

        fun main(id: String, answer: (String) -> ExtractorHttpResult) = FakeExtractorHttpClient(
            getResponder = { url, _ -> if (asksFor(url, id)) answer(url) else null },
        )

        /** Whether [url] is the widget's answer for post [id]. */
        fun asksFor(url: String, id: String): Boolean {
            val uri = URI(url)
            return uri.host == WIDGET_HOST && uri.path == "/tweet-result" && "id=$id" in uri.rawQuery.orEmpty().split('&')
        }

        /** A row Master offers in an X lookup (an MP4 file unless named). */
        fun row(url: String, kind: MediaKind = MediaKind.DIRECT) = MediaCandidate(
            pageUrl = page(VIDEO),
            mediaUrl = url,
            sources = setOf(CandidateSource.DOM),
            kind = kind,
            requestContext = BrowserRequestContext(page(VIDEO), AGENT, null),
            observedAtEpochMs = NOW,
            pageRole = PageMediaRole.MAIN,
        )

        /** A file's first bytes as its host answers them: an MP4 start of a 32 KiB file. */
        fun start(bytes: ByteArray = InitBytes.mp4()) =
            MediaStart.Read(206, "video/mp4", "bytes 0-${bytes.size - 1}/$TOTAL", bytes.size.toLong(), bytes)
    }
}
