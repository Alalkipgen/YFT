package com.alal.yft.extractor.master.social

import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterMediaValidator
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.MasterStage
import com.alal.yft.extractor.master.NOW
import com.alal.yft.extractor.master.RecordingValidator
import com.alal.yft.extractor.master.contract.CountingCapture
import com.alal.yft.extractor.master.contract.SiteContracts
import com.alal.yft.extractor.master.contract.assertCovers
import com.alal.yft.extractor.master.contract.contractRequest
import com.alal.yft.extractor.master.contract.mainRequest
import com.alal.yft.extractor.master.lookup.AnswerMark
import com.alal.yft.extractor.master.lookup.LookupAsk
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.lookup.LookupRecorder
import com.alal.yft.extractor.master.modules.youtube.ladder.InitBytes
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStart
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStartReader
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.master.modules.youtube.testing.Fixtures
import com.alal.yft.extractor.sites.facebook.FacebookExtractor
import com.alal.yft.extractor.sites.instagram.InstagramExtractor
import com.alal.yft.extractor.sites.tiktok.TikTokExtractor
import com.alal.yft.extractor.sites.x.XExtractor
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Social plan S2d: Instagram's ladder in Master's engine. I2 reads main's own answers in this
 * lookup ([MainPages]: the app API signed in, GraphQL, the post's page, the embed page; each way's
 * page or refused GET) with no new ask, judges each and gives one answer for the lookup: the link's
 * item's rows bound to the post (main's files, and the 360p file main drops), checked by V; a photo
 * post final with main's reason; a rate limit, a checkpoint, a login wall, no post, a broken embed
 * and the embed's video without files never final, never REMOVED; rows after main's
 * LOGIN_REQUIRED stay unused (the engine's rule). A renamed key: by shape, one item only. Part 2
 * (task 13), the tab-fetch rung: off by its switch, asked once after I2 gave no rows (beside I2's
 * rows only by its proof) and only signed out, recorded as Master's ask (a 429 stops the site, a
 * checkpoint cools it), said in one line; its rows bound to the post (its code or its pk) and to
 * the link's item go to V and the init check (at most 8) as the tab's own answer; a wall, another
 * post, a rejected value, a too-big answer, photos: no row, never final, main's reason. S7's
 * budget; SocialStages gives an Instagram lookup Instagram's ladder. Main's real Instagram adapter
 * over main's fixtures (one made-up embed without files); Master's client is never asked; no live
 * calls.
 */
class InstagramLadderTest {
    @Test
    fun `I2 reads main's GraphQL answer with no new ask - the reel's rows, main's files and the 360p file main drops`() = runTest {
        val record = record()
        val validator = RecordingValidator()
        val capture = CountingCapture()
        val master = FakeExtractorHttpClient()
        val main = main(SiteWays.GRAPHQL to json(fixture("graphql_reel.json")))

        val lookup = lookup(REEL, main, master, record, validator = validator, capture = capture)

        val mains = (lookup.main as SiteExtractionResult.Success).candidates
        val success = lookup.result as MasterResult.Success
        assertEquals(MasterStage.PAGE_DATA, success.stage)
        val rows = assertCovers("GraphQL", mains, success, validator, KEY)
        assertEquals(5, mains.size)
        assertEquals(6, rows.size)
        // Every main row with main's height and length; one more: the 360p file main drops.
        val extra = rows.filter { row -> mains.none { it.mediaUrl == row.mediaUrl } }
        assertEquals(listOf(640), extra.map { it.height })
        assertTrue(extra.single().mediaUrl, "/gql_vp360.mp4" in extra.single().mediaUrl)
        assertEquals(facts(mains), facts(rows - extra.toSet()))
        rows.forEach { assertEquals(15_200L, it.durationMillis) }
        assertEquals(6, validator.seen.size)
        assertEquals(0, capture.calls)
        assertNothingAsked(master)
        assertEquals(1, main.requestedUrls.size)
        assertEquals(1, record.mainAsks)
        assertEquals(0, record.masterAsks)
        val details = success.result.details
        assertTrue(details.toString(), "$LADDER after RESPONSE_CHANGED; main's answers read, no new ask" in details)
        assertTrue(details.toString(), "$I2 main's GraphQL: DIRECT (rows 6 of the link's item, bound to the post)" in details)
        assertTrue(details.toString(), "$I2: DIRECT (GraphQL: rows 6 of the link's item, bound to the post); next: CHECK_ROWS" in details)
        assertFalse("no probe in the lookup: no probe line", details.any { it.startsWith(PROBE) })
        assertBare(details)
    }

    @Test
    fun `each of main's ways - the embed page, the post's page, the app API signed in - in main's order, bound to the post`() = runTest {
        val cases = listOf(
            Way(
                "embed page", main(SiteWays.EMBED_PAGE to html(fixture("embed_captioned.html"))), null, 3, 1, 1,
                listOf(
                    "$I2 main's GraphQL: TRANSIENT (failed: HTTP_STATUS 404)",
                    "$I2 main's page: TRANSIENT (failed: HTTP_STATUS 404)",
                    "$I2 main's embed page: DIRECT (rows 1 of the link's item, bound to the post)",
                    "$I2: DIRECT (embed page: rows 1 of the link's item, bound to the post); next: CHECK_ROWS",
                ),
            ),
            Way(
                "page", main(SiteWays.PAGE to html(fixture("page_signed_in.html"))), null, 2, 6, 7,
                listOf(
                    "$I2 main's GraphQL: TRANSIENT (failed: HTTP_STATUS 404)",
                    "$I2 main's page: DIRECT (rows 8 of the link's item, bound to the post)",
                    "$I2: DIRECT (page: rows 8 of the link's item, bound to the post); next: CHECK_ROWS",
                ),
            ),
            Way(
                "app API", main(SiteWays.APP_API to json(fixture("api_info_reel.json"))), SESSION, 1, 6, 7,
                listOf(
                    "$I2 main's app API: DIRECT (rows 8 of the link's item, bound to the post)",
                    "$I2: DIRECT (app API: rows 8 of the link's item, bound to the post); next: CHECK_ROWS",
                ),
            ),
        )
        for (case in cases) {
            val validator = RecordingValidator()
            val capture = CountingCapture()
            val master = FakeExtractorHttpClient()

            val lookup = lookup(REEL, case.main, master, validator = validator, capture = capture, cookie = case.cookie)

            val mains = (lookup.main as SiteExtractionResult.Success).candidates
            assertEquals(case.name, case.mainRows, mains.size)
            val success = lookup.result as MasterResult.Success
            val rows = assertCovers(case.name, mains, success, validator, KEY)
            assertEquals(case.name, case.rows, rows.size)
            // Another post's file in the page is never the link's.
            assertFalse(case.name, rows.any { "/other.mp4" in it.mediaUrl })
            rows.filter { row -> mains.none { it.mediaUrl == row.mediaUrl } }.forEach { extra ->
                assertTrue(extra.mediaUrl, "_vp360.mp4" in extra.mediaUrl)
            }
            assertEquals(case.name, case.asks, case.main.requestedUrls.size)
            // Signed in, main's first ask is the app API's, for the code's media ID.
            if (case.cookie != null) assertEquals("/api/v1/media/$REEL_ID/info/", URI(case.main.requestedUrls.first()).path)
            assertEquals(case.name, 0, capture.calls)
            assertNothingAsked(master)
            val details = success.result.details
            val ladder = details.filter { it.startsWith("master: instagram I2") }
            assertEquals(case.name, case.lines, ladder)
            assertBare(details)
        }
    }

    @Test
    fun `only the link's item of a carousel - its img_index, else its first video, as main`() = runTest {
        val carousel = fixture("graphql_carousel.json")
        val cases = listOf(
            "$SIDECAR?img_index=3" to Triple("/side3.mp4", 720, 4_000L),
            SIDECAR to Triple("/side2.mp4", 1_080, 7_500L),
        )
        for ((link, item) in cases) {
            val validator = RecordingValidator()

            val lookup = lookup(link, main(SiteWays.GRAPHQL to json(carousel)), validator = validator)

            val mains = (lookup.main as SiteExtractionResult.Success).candidates
            val success = lookup.result as MasterResult.Success
            val row = success.result.candidates.single()
            assertEquals(link, mains.single().mediaUrl, row.mediaUrl)
            assertTrue(link, row.mediaUrl.contains(item.first))
            assertEquals(link, item.second, row.height)
            assertEquals(link, item.third, row.durationMillis)
            assertEquals(link, "instagram:$SIDE", row.videoId)
            assertTrue(
                success.result.details.toString(),
                "$I2: DIRECT (GraphQL: rows 1 of the link's item, bound to the post); next: CHECK_ROWS" in success.result.details,
            )
            assertBare(success.result.details)
        }

        // A renamed key in a carousel: no file read by shape for its item, no guess.
        val validator = RecordingValidator()
        val renamedBody = carousel.replace("video_url", "video_link")
        val renamed = lookup("$SIDECAR?img_index=3", main(SiteWays.GRAPHQL to json(renamedBody)), validator = validator)
        val details = (renamed.result as MasterResult.NeedsPlayback).details
        // Task 15 (F2): the shape change says what it read (counts and marks only).
        assertTrue(
            details.toString(),
            "$I2: SHAPE_CHANGED (GraphQL: a video, no files read; the answer: ${renamedBody.length} characters; " +
                "the post: yes; a video mark: yes; files: 0); next: NEXT_RUNG" in details,
        )
        assertTrue(validator.seen.isEmpty())
        assertBare(details)
    }

    @Test
    fun `a photo post is final with main's reason and no request, never captured`() = runTest {
        val validator = RecordingValidator()
        val capture = CountingCapture()
        val master = FakeExtractorHttpClient()
        val main = main(SiteWays.GRAPHQL to json(fixture("graphql_photo.json")))

        val lookup = lookup(PHOTO, main, master, validator = validator, capture = capture)

        val mainFailure = lookup.main as SiteExtractionResult.Failure
        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, mainFailure.reason)
        assertEquals(InstagramExtractor.PHOTOS_MESSAGE, mainFailure.message)
        val failure = lookup.result as MasterResult.Failure
        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, failure.reason)
        val details = failure.details
        assertTrue(details.toString(), "$LADDER after NO_MEDIA_FOUND; main's answers read, no new ask" in details)
        assertTrue(details.toString(), "$I2 main's GraphQL: PHOTO_ONLY (photos only, no video)" in details)
        assertTrue(details.toString(), "$I2: PHOTO_ONLY (GraphQL: photos only, no video); next: FINAL" in details)
        assertTrue(details.toString(), "master: page reports NO_MEDIA_FOUND" in details)
        assertTrue(validator.seen.isEmpty())
        assertEquals(0, capture.calls)
        assertNothingAsked(master)
        assertEquals(1, main.requestedUrls.size)
        assertBare(details)
    }

    @Test
    fun `a rate limit, a checkpoint, a login wall, no post and a broken embed are never final, never REMOVED`() = runTest {
        val cases = listOf(
            Never(
                main(SiteWays.GRAPHQL to json(fixture("graphql_login.json"))), SiteExtractionFailure.LOGIN_REQUIRED,
                "$I2: RATE_LIMITED (GraphQL: a rate-limit answer); next: STOP_SITE", cools = true,
            ),
            Never(
                main(SiteWays.GRAPHQL to refused(429, SiteExtractionFailure.RATE_LIMITED)), SiteExtractionFailure.RESPONSE_CHANGED,
                "$I2: RATE_LIMITED (GraphQL: status 429); next: STOP_SITE", cools = true,
            ),
            Never(
                main(SiteWays.GRAPHQL to html("<html><body></body></html>", final = CHECKPOINT_PAGE)), SiteExtractionFailure.LOGIN_REQUIRED,
                "$I2: LOGIN_WALL (GraphQL: final address: checkpoint); next: BROWSER",
            ),
            Never(
                main(SiteWays.GRAPHQL to refused(401), SiteWays.PAGE to refused(401), SiteWays.EMBED_PAGE to refused(401)),
                SiteExtractionFailure.LOGIN_REQUIRED, "$I2: LOGIN_WALL (GraphQL: status 401); next: BROWSER",
            ),
            Never(
                main(SiteWays.PAGE to html("<html><body></body></html>", final = LOGIN_PAGE)), SiteExtractionFailure.LOGIN_REQUIRED,
                "$I2: LOGIN_WALL (page: final address: login); next: BROWSER",
            ),
            Never(
                main(SiteWays.GRAPHQL to json(fixture("graphql_null.json")), SiteWays.EMBED_PAGE to html(fixture("embed_no_video.html"))),
                SiteExtractionFailure.RESPONSE_CHANGED,
                "$I2: NEEDS_PLAYBACK (GraphQL: no post in the answer: removed, private or gated alike); next: PLAYBACK",
            ),
            Never(
                main(SiteWays.EMBED_PAGE to html(fixture("embed_no_video.html"))), SiteExtractionFailure.RESPONSE_CHANGED,
                "$I2: NEEDS_PLAYBACK (embed page: not embeddable: removed, private or not embeddable alike); next: PLAYBACK",
            ),
            // Task 13 (observation d): the embed signed out names the video, no file: the tab-fetch
            // rung's (none in this lookup: the user's own playback), never a shape change.
            Never(
                embedNoFiles(), SiteExtractionFailure.RESPONSE_CHANGED,
                "$I2: VIDEO_WITHOUT_FILES (embed page: a video, no files read); next: NEXT_RUNG",
            ),
        )
        for (case in cases) {
            val validator = RecordingValidator()
            val capture = CountingCapture()
            val master = FakeExtractorHttpClient()
            val cooldown = SocialCooldown()

            val lookup = lookup(REEL, case.main, master, validator = validator, capture = capture, cooldown = cooldown)

            assertEquals(case.line, case.mainReason, (lookup.main as SiteExtractionResult.Failure).reason)
            val details = (lookup.result as MasterResult.NeedsPlayback).details
            assertTrue(details.toString(), case.line in details)
            assertFalse(details.toString(), details.any { "REMOVED" in it || "PRIVATE" in it })
            assertEquals(case.line, 1, capture.calls)
            assertTrue(case.line, validator.seen.isEmpty())
            assertNothingAsked(master)
            // S7: a rate limit cools Instagram's web host from the stage's end; main's checkpoint is a
            // sign-in wall (main's rule, §6.3): the browser, no stop.
            assertEquals(case.line, case.cools, cooldown.cooling(WEB_HOST))
            assertBare(details)
        }

        // Main's own 429 (main's reason RATE_LIMITED): Master is not asked at all, as for every site.
        val capture = CountingCapture()
        val limited = lookup(
            REEL,
            main(
                SiteWays.GRAPHQL to refused(429, SiteExtractionFailure.RATE_LIMITED),
                SiteWays.PAGE to refused(429, SiteExtractionFailure.RATE_LIMITED),
                SiteWays.EMBED_PAGE to refused(429, SiteExtractionFailure.RATE_LIMITED),
            ),
            capture = capture,
        )
        assertEquals(MasterResult.Skipped(SiteExtractionFailure.RATE_LIMITED), limited.result)
        assertEquals(0, capture.calls)
    }

    @Test
    fun `the real embed page signed out - I2 reads main's post, a video without files as main says, and a shape change says what it read`() = runTest {
        // Task 15 (F2, the phone session of 2026-10-23: slot 5 signed out, the rung off): main's
        // Details called the real embed page (about 227k characters) "video without files", I2's
        // "SHAPE_CHANGED (no video read)": I2's first node naming the code was the embed's own
        // context (a camelCase video mark), not the post. A page's post is now main's (a Master copy
        // of main's rule, main unchanged): VIDEO_WITHOUT_FILES, the class the playback words read.
        assertTrue(REAL_EMBED.length.toString(), REAL_EMBED.length in 220_000..235_000)
        val lookup = lookup(REEL, main(SiteWays.GRAPHQL to refused(401), SiteWays.EMBED_PAGE to html(REAL_EMBED)))

        val mains = lookup.main as SiteExtractionResult.Failure
        assertEquals(SiteExtractionFailure.LOGIN_REQUIRED, mains.reason)
        assertTrue(mains.details.toString(), "embed page: 200 (${REAL_EMBED.length} characters), video without files" in mains.details)
        val details = detailsOf(lookup.result)
        assertTrue(details.toString(), "$I2 main's embed page: VIDEO_WITHOUT_FILES (a video, no files read)" in details)
        assertTrue(details.toString(), "$I2: LOGIN_WALL (GraphQL: status 401); next: BROWSER" in details)
        assertTrue(details.toString(), details.none { "SHAPE_CHANGED" in it })
        assertBare(details)

        // Main's rule finds no post (the post's node gone) while the embed's context still names
        // the code: a shape change, saying what it read — counts and marks only.
        val contextOnly = realEmbed(post = false)
        val gone = lookup(REEL, main(SiteWays.GRAPHQL to refused(401), SiteWays.EMBED_PAGE to html(contextOnly)))
        assertTrue(
            (gone.main as SiteExtractionResult.Failure).details.toString(),
            "embed page: 200 (${contextOnly.length} characters), no post" in (gone.main as SiteExtractionResult.Failure).details,
        )
        val goneDetails = detailsOf(gone.result)
        assertTrue(
            goneDetails.toString(),
            "$I2 main's embed page: SHAPE_CHANGED (no video read; the page: ${contextOnly.length} characters; " +
                "documents: 1; main's post: no; a video mark: no; files: 0)" in goneDetails,
        )
        assertBare(goneDetails)

        // The same post's data on the post's own page: a video without files there is a shape
        // change, as before (only the embed page lists no file by its design), now with its counts.
        val page = lookup(REEL, main(SiteWays.GRAPHQL to refused(401), SiteWays.PAGE to html(REAL_EMBED)))
        assertTrue(
            (page.main as SiteExtractionResult.Failure).details.toString(),
            "page: 200 (${REAL_EMBED.length} characters), video without files" in (page.main as SiteExtractionResult.Failure).details,
        )
        val pageDetails = detailsOf(page.result)
        assertTrue(
            pageDetails.toString(),
            "$I2 main's page: SHAPE_CHANGED (a video, no files read; the page: ${REAL_EMBED.length} characters; " +
                "documents: 1; main's post: yes; a video mark: yes; files: 0)" in pageDetails,
        )
        assertBare(pageDetails)
    }

    @Test
    fun `a renamed key - one item's rows by shape go to V, rows after main's LOGIN_REQUIRED stay unused`() = runTest {
        // The embed's file key renamed: main finds a video without files; the recipe's open read, one item.
        val embed = fixture("embed_captioned.html").replace("video_url", "video_link")
        val validator = RecordingValidator()
        val one = lookup(REEL, main(SiteWays.EMBED_PAGE to html(embed)), validator = validator)
        assertEquals(SiteExtractionFailure.RESPONSE_CHANGED, (one.main as SiteExtractionResult.Failure).reason)
        val success = one.result as MasterResult.Success
        val row = success.result.candidates.single()
        assertTrue(row.mediaUrl, "/embed_prog640.mp4" in row.mediaUrl)
        assertEquals(KEY, row.videoId)
        assertEquals(listOf(row.mediaUrl), validator.seen.map { it.mediaUrl })
        val details = success.result.details
        assertTrue(details.toString(), "$I2 main's embed page: PARTIAL (rows 1 by shape (a renamed key), one item)" in details)
        assertTrue(details.toString(), "$I2: PARTIAL (embed page: rows 1 by shape (a renamed key), one item); next: CHECK_ROWS" in details)
        assertTrue(details.toString(), "master: instagram contract: 1 files by shape (key table changed)" in details)

        // GraphQL's file keys renamed: its six files by shape.
        val graphql = fixture("graphql_reel.json").replace("video_url", "video_link").replace("video_dash_manifest", "video_dash_mpd")
        val six = lookup(REEL, main(SiteWays.GRAPHQL to json(graphql)))
        val sixRows = (six.result as MasterResult.Success).result.candidates
        assertEquals(6, sixRows.size)
        sixRows.forEach { assertEquals(KEY, it.videoId) }
        assertTrue(
            six.result.toString(),
            "$I2: PARTIAL (GraphQL: rows 6 by shape (a renamed key), one item); next: CHECK_ROWS" in (six.result as MasterResult.Success).result.details,
        )

        // GraphQL's 401 makes main's reason LOGIN_REQUIRED: the rows are staged, the engine keeps
        // them for the user's own playback (its rule after LOGIN_REQUIRED), and asks for it.
        val unused = RecordingValidator()
        val capture = CountingCapture()
        val walled = lookup(REEL, main(SiteWays.GRAPHQL to refused(401), SiteWays.EMBED_PAGE to html(embed)), validator = unused, capture = capture)
        assertEquals(SiteExtractionFailure.LOGIN_REQUIRED, (walled.main as SiteExtractionResult.Failure).reason)
        val walledDetails = (walled.result as MasterResult.NeedsPlayback).details
        assertTrue(walledDetails.toString(), "$I2 main's GraphQL: LOGIN_WALL (status 401)" in walledDetails)
        assertTrue(walledDetails.toString(), "$I2: PARTIAL (embed page: rows 1 by shape (a renamed key), one item); next: CHECK_ROWS" in walledDetails)
        assertTrue(walledDetails.toString(), "master: authorized browser playback is required" in walledDetails)
        assertTrue(unused.seen.isEmpty())
        assertEquals(1, capture.calls)
        assertBare(details + walledDetails)
    }

    @Test
    fun `S7 - the rows go to V only within Instagram's budget, and only main's kept answers are read`() = runTest {
        val reel = fixture("graphql_reel.json")

        // Time: main's ask took the lookup's 30 seconds.
        var now = 1_000L
        val timed = LookupRecord(clock = { now }, labels = SiteWays.INSTAGRAM.labels())
        val slow = main(
            SiteWays.GRAPHQL to { url: String ->
                now += SocialBudget.INSTAGRAM.millis
                ExtractorHttpResult.Success(200, reel, url, JSON)
            },
        )
        val cases = listOf(
            Budget(slow, timed, InstagramLadder(FakeExtractorHttpClient()), "over the lookup's time budget"),
            Budget(main(SiteWays.GRAPHQL to json(reel)), busy(), InstagramLadder(FakeExtractorHttpClient()), "over the lookup's request budget"),
            Budget(
                main(SiteWays.GRAPHQL to json(reel)), record(),
                InstagramLadder(FakeExtractorHttpClient(), budget = SocialBudget.INSTAGRAM.copy(bytes = 64)),
                "over the lookup's byte budget",
            ),
        )
        for (case in cases) {
            val validator = RecordingValidator()
            val capture = CountingCapture()

            val lookup = lookup(REEL, case.main, record = case.record, validator = validator, capture = capture, ladder = case.ladder)

            val details = (lookup.result as MasterResult.NeedsPlayback).details
            assertTrue(details.toString(), "$I2: DIRECT (GraphQL: rows 6 of the link's item, bound to the post); next: CHECK_ROWS" in details)
            assertTrue(details.toString(), "$I2: ${case.why}; not checked" in details)
            assertTrue(case.why, validator.seen.isEmpty())
            assertEquals(case.why, 1, capture.calls)
            assertBare(details)
        }

        // No answer of main's for the post: main read another post, or the answer was over the bound.
        val elsewhere = withContext(record() + MainPages(site = SITE)) {
            InstagramExtractor(MainPageRecorder(LookupRecorder(main(SiteWays.GRAPHQL to json(fixture("graphql_carousel.json"))))))
                .extract(mainRequest(identify(SIDECAR)))
            engine(FakeExtractorHttpClient()).extract(contractRequest(REEL, identify(REEL), SiteExtractionFailure.RESPONSE_CHANGED))
        }
        assertTrue(elsewhere.toString(), "$I2: none kept for the post; nothing to read" in detailsOf(elsewhere))
        assertTrue(elsewhere is MasterResult.NeedsPlayback)
        // Task 12: main's answer remembered (P17), main asked nothing in this lookup: the line says so.
        for (reuse in LookupRecord.Reuse.entries) {
            val unasked = withContext(record().also { it.noteReuse(reuse) } + MainPages(site = SITE)) {
                engine(FakeExtractorHttpClient()).extract(contractRequest(REEL, identify(REEL), SiteExtractionFailure.RESPONSE_CHANGED))
            }
            assertTrue(
                unasked.toString(),
                "$I2: none kept for the post (main asked nothing in this lookup: ${reuse.label}); nothing to read" in detailsOf(unasked),
            )
            assertTrue(unasked is MasterResult.NeedsPlayback)
        }
        val bounded = lookup(REEL, main(SiteWays.GRAPHQL to json(reel)), pages = MainPages(maxChars = 64, site = SITE))
        val boundedDetails = detailsOf(bounded.result)
        assertTrue(boundedDetails.toString(), "$I2 main's GraphQL: not kept (over the bound)" in boundedDetails)
        assertTrue(boundedDetails.toString(), "$I2: none kept for the post; nothing to read" in boundedDetails)
        assertTrue(bounded.result is MasterResult.NeedsPlayback)
        assertBare(detailsOf(elsewhere) + boundedDetails)
    }

    @Test
    fun `the rung - off by its switch, on asked once after I2 gave no rows, its rows bound to the post go to V as the tab's own answer`() = runTest {
        // Off: one line, nothing asked, after I2's no rows and beside I2's rows alike.
        val off = Probe(on = false, proof = true) { error("not asked") }
        val quiet = lookup(REEL, embedNoFiles(), probe = off)
        val quietDetails = (quiet.result as MasterResult.NeedsPlayback).details
        assertTrue(quietDetails.toString(), "$PROBE: off" in quietDetails)
        val beside = lookup(REEL, main(SiteWays.GRAPHQL to json(fixture("graphql_reel.json"))), probe = off)
        assertEquals(6, (beside.result as MasterResult.Success).result.candidates.size)
        assertTrue("$PROBE: off" in detailsOf(beside.result))
        assertTrue(off.asked.isEmpty())

        // On, after I2's video without files (main's embed signed out): the tab's answer's row.
        val record = record()
        val master = FakeExtractorHttpClient()
        val validator = RecordingValidator()
        val capture = CountingCapture()
        val probe = Probe { answered(200, PROBE_VIDEO) }
        val lookup = lookup(REEL, embedNoFiles(), master, record, validator = validator, capture = capture, probe = probe)
        val success = lookup.result as MasterResult.Success
        assertEquals(MasterStage.PAGE_DATA, success.stage)
        val row = success.result.candidates.single()
        assertTrue(row.mediaUrl, "/probe.mp4" in row.mediaUrl)
        assertEquals(1280, row.height)
        assertEquals(KEY, row.videoId)
        assertEquals(listOf(row.mediaUrl), validator.seen.map { it.mediaUrl })
        assertEquals(0, capture.calls)
        val asked = probe.asked.single()
        assertEquals(REEL_CODE, asked.code)
        assertEquals(REEL_ID, asked.mediaId)
        assertEquals(REEL, asked.pageUrl)
        val details = success.result.details
        assertTrue(details.toString(), "$I2 main's embed page: VIDEO_WITHOUT_FILES (a video, no files read)" in details)
        assertTrue(details.toString(), "$I2: VIDEO_WITHOUT_FILES (embed page: a video, no files read); next: NEXT_RUNG" in details)
        val line = "$PROBE: HTTP 200, ${bytes(PROBE_VIDEO)} bytes; the post's video in the answer: yes; a wall on the page: no; rows 1 (DIRECT)"
        assertTrue(details.toString(), line in details)
        // The rung's line comes after I2's answer.
        assertTrue(details.indexOf(line) > details.indexOfFirst { it.startsWith("$I2:") })
        // The rung is Master's ask in the record, its query's, read as data; Master's client never asked.
        assertEquals(1, record.masterAsks)
        val ask = record.asks().last()
        assertEquals(LookupRecord.Asker.MASTER, ask.asker)
        assertEquals(SiteWays.TAB_FETCH, ask.way)
        assertEquals(AnswerMark.DATA, ask.mark)
        assertTrue(record.asked(probeAsk(REEL_ID)))
        assertNothingAsked(master)

        // The answer's post named by its pk alone: the same row, bound to the post.
        val byPk = lookup(REEL, embedNoFiles(), probe = Probe { answered(200, PROBE_BY_PK) })
        val pkRow = (byPk.result as MasterResult.Success).result.candidates.single()
        assertTrue(pkRow.mediaUrl, "/probe.mp4" in pkRow.mediaUrl)
        assertEquals(KEY, pkRow.videoId)
        assertTrue(
            detailsOf(byPk.result).toString(),
            "$PROBE: HTTP 200, ${bytes(PROBE_BY_PK)} bytes; the post's video in the answer: yes; a wall on the page: no; rows 1 (DIRECT)"
                in detailsOf(byPk.result),
        )

        // After main's LOGIN_REQUIRED (its signed-out GraphQL a 401): the tab's own answer, signed
        // out, its page without a wall, so its rows are used (I2's own rows would stay unused).
        val walled = lookup(
            REEL, main(SiteWays.GRAPHQL to refused(401), SiteWays.EMBED_PAGE to html(EMBED_NO_FILES)),
            probe = Probe { answered(200, PROBE_VIDEO) },
        )
        assertEquals(SiteExtractionFailure.LOGIN_REQUIRED, (walled.main as SiteExtractionResult.Failure).reason)
        val walledRow = (walled.result as MasterResult.Success).result.candidates.single()
        assertTrue(walledRow.mediaUrl, "/probe.mp4" in walledRow.mediaUrl)
        val walledDetails = detailsOf(walled.result)
        assertTrue(walledDetails.toString(), "$I2: LOGIN_WALL (GraphQL: status 401); next: BROWSER" in walledDetails)
        assertFalse(walledDetails.toString(), "master: authorized browser playback is required" in walledDetails)
        assertBare(quietDetails + detailsOf(beside.result) + details + detailsOf(byPk.result) + walledDetails)
    }

    @Test
    fun `the rung beside I2's rows - not asked, but when its proof sets them aside`() = runTest {
        val reel = main(SiteWays.GRAPHQL to json(fixture("graphql_reel.json")))
        val idle = Probe { error("not asked") }
        val kept = lookup(REEL, reel, probe = idle)
        val keptRows = (kept.result as MasterResult.Success).result.candidates
        assertEquals(6, keptRows.size)
        assertTrue(detailsOf(kept.result).toString(), "$PROBE: not asked (I2's rows)" in detailsOf(kept.result))
        assertTrue(idle.asked.isEmpty())

        // The proof: I2's rows set aside, the rung asked; its own row only.
        val record = record()
        val proof = Probe(proof = true) { answered(200, PROBE_VIDEO) }
        val proved = lookup(REEL, reel, record = record, probe = proof)
        val row = (proved.result as MasterResult.Success).result.candidates.single()
        assertTrue(row.mediaUrl, "/probe.mp4" in row.mediaUrl)
        val details = detailsOf(proved.result)
        val aside = "$PROBE: I2's rows set aside (the rung's proof)"
        val line = "$PROBE: HTTP 200, ${bytes(PROBE_VIDEO)} bytes; the post's video in the answer: yes; a wall on the page: no; rows 1 (DIRECT)"
        assertTrue(details.toString(), "$I2: DIRECT (GraphQL: rows 6 of the link's item, bound to the post); next: CHECK_ROWS" in details)
        assertTrue(details.toString(), details.indexOf(aside) in 0 until details.indexOf(line))
        assertEquals(InstagramLadder.PROOF, aside)
        assertEquals(1, proof.asked.size)
        assertEquals(1, record.masterAsks)

        // Task 15 (F4): signed in, the proof sets I2's rows aside and the rung is not asked (the
        // signed-out query): no row — no capture of the tab after it (signed in, the tab's playback
        // gave the phone's row, its file none of main's: "the same item: no") — and the proof's
        // stop says why, the lookup's last line (no "supply a fresh capture").
        val capture = CountingCapture()
        val signedIn = lookup(REEL, reel, capture = capture, probe = Probe(proof = true) { error("not asked") }, cookie = SESSION)
        val stopped = (signedIn.result as MasterResult.NeedsPlayback).details
        assertEquals(0, capture.calls)
        assertTrue(stopped.toString(), aside in stopped)
        assertTrue(stopped.toString(), "$PROBE: not asked (signed in: the rung is the signed-out query)" in stopped)
        assertEquals(stopped.toString(), InstagramLadder.PROOF_STOP, stopped.last { it.startsWith(PROBE) })
        assertTrue(stopped.toString(), stopped.indexOf(aside) < stopped.indexOf(InstagramLadder.PROOF_STOP))
        assertFalse(stopped.toString(), stopped.any { "capture" in it })
        // Signed out, the rung's answer without the post: the same stop, no row, no capture.
        val gone = lookup(REEL, reel, capture = capture, probe = Probe(proof = true) { answered(200, PROBE_NONE) })
        val goneLines = (gone.result as MasterResult.NeedsPlayback).details
        assertEquals(InstagramLadder.PROOF_STOP, goneLines.last { it.startsWith(PROBE) })
        assertEquals(0, capture.calls)
        // Without the proof, signed in: I2's rows, the rung not asked, as before.
        val own = lookup(REEL, reel, probe = Probe { error("not asked") }, cookie = SESSION)
        assertEquals(6, (own.result as MasterResult.Success).result.candidates.size)
        assertFalse(InstagramLadder.PROOF_STOP in detailsOf(own.result))
        assertBare(detailsOf(kept.result) + details + stopped + goneLines)
    }

    @Test
    fun `the rung's row states its video's size when its file states none`() = runTest {
        // Task 15 (F1): the phone's rung row had no height ("Quality unknown"); a file that states no
        // size takes its video's own (`original_width`/`original_height`, else `dimensions`), as
        // main's rows and I2's state one; a file's own size stays first.
        for (answer in listOf(PROBE_NO_SIZE, PROBE_NO_SIZE.replace(ORIGINAL_SIZE, "\"dimensions\":{\"width\":720,\"height\":1280},"))) {
            val found = lookup(REEL, embedNoFiles(), probe = Probe { answered(200, answer) })
            val row = (found.result as MasterResult.Success).result.candidates.single()
            assertTrue(row.mediaUrl, "/probe.mp4" in row.mediaUrl)
            assertEquals(answer, 720, row.width)
            assertEquals(answer, 1280, row.height)
        }
        val own = lookup(REEL, embedNoFiles(), probe = Probe { answered(200, PROBE_NO_SIZE.replace("\"type\":101,", "\"width\":540,\"height\":960,")) })
        assertEquals(960, (own.result as MasterResult.Success).result.candidates.single().height)
    }

    @Test
    fun `the rung's answer without a row - no post, a wall, another post, a rejected value, too big, photos - its line and main's reason`() = runTest {
        val other = "100000000000000001"
        val cases = listOf(
            answered(200, PROBE_NONE) to
                "no; a wall on the page: no; rows 0 (NEEDS_PLAYBACK: no post in the answer: removed, private or gated alike)",
            answered(200, PROBE_VIDEO, wall = true) to "yes; a wall on the page: yes; rows 0 (LOGIN_WALL: a wall on the page: never read)",
            answered(200, PROBE_VIDEO.replace(REEL_CODE, "C9fixtOTHR1")) to
                "no; a wall on the page: no; rows 0 (SHAPE_CHANGED: names another post)",
            answered(200, PROBE_BY_PK.replace(REEL_ID, other)) to "no; a wall on the page: no; rows 0 (SHAPE_CHANGED: names another post)",
            answered(400, REJECTED_ANSWER) to "no; a wall on the page: no; rows 0 (VALUE_REJECTED: status 400)",
            answered(200, ERRORS_ANSWER) to "no; a wall on the page: no; rows 0 (VALUE_REJECTED: the answer: errors)",
            answered(200, PROBE_PHOTO) to "no; a wall on the page: no; rows 0 (PHOTO_ONLY: photos only, no video)",
        )
        for ((reply, end) in cases) {
            val validator = RecordingValidator()
            val capture = CountingCapture()
            val lookup = lookup(REEL, embedNoFiles(), validator = validator, capture = capture, probe = Probe { reply })
            val details = (lookup.result as MasterResult.NeedsPlayback).details
            val line = "$PROBE: HTTP ${reply.status}, ${reply.bytes} bytes; the post's video in the answer: $end"
            assertTrue(details.toString(), line in details)
            assertTrue(end, validator.seen.isEmpty())
            assertEquals(end, 1, capture.calls)
            assertBare(details)
        }

        // Over the script's bound: no text handed back, never read.
        val big = InstagramTabProbe.Reply.Answered(200, InstagramProbeQuery.MAX_ANSWER_CHARS + 1L, null, JSON, PROBE_ENDPOINT, false)
        val tooBig = lookup(REEL, embedNoFiles(), probe = Probe { big })
        assertTrue(
            detailsOf(tooBig.result).toString(),
            "$PROBE: HTTP 200, ${InstagramProbeQuery.MAX_ANSWER_CHARS + 1} bytes; the post's video in the answer: no; " +
                "a wall on the page: no; rows 0 (TRANSIENT: failed: RESPONSE_TOO_LARGE 200)" in detailsOf(tooBig.result),
        )

        // Over the lookup's byte budget: its rows are not checked.
        val validator = RecordingValidator()
        val small = InstagramLadder(FakeExtractorHttpClient(), budget = SocialBudget.INSTAGRAM.copy(bytes = 64))
        val over = lookup(REEL, embedNoFiles(), validator = validator, ladder = small, probe = Probe { answered(200, PROBE_VIDEO) })
        assertTrue(
            detailsOf(over.result).toString(),
            "$PROBE: HTTP 200, ${bytes(PROBE_VIDEO)} bytes; the post's video in the answer: yes; a wall on the page: no; " +
                "rows 1 (DIRECT), not checked (over the lookup's byte budget)" in detailsOf(over.result),
        )
        assertTrue(over.result is MasterResult.NeedsPlayback)
        assertTrue(validator.seen.isEmpty())
        assertBare(detailsOf(tooBig.result) + detailsOf(over.result))
    }

    @Test
    fun `the rung reads only the link's item of a carousel - its img_index, else its first video`() = runTest {
        val main = main(SiteWays.EMBED_PAGE to html(EMBED_NO_FILES.replace(REEL_CODE, SIDE)))
        val cases = listOf(
            "$SIDECAR?img_index=2" to ("/item2.mp4" to 1920),
            SIDECAR to ("/item1.mp4" to 1280),
        )
        for ((link, item) in cases) {
            val probe = Probe { answered(200, PROBE_CAROUSEL) }
            val lookup = lookup(link, main, probe = probe)
            val row = (lookup.result as MasterResult.Success).result.candidates.single()
            assertTrue(link, item.first in row.mediaUrl)
            assertEquals(link, item.second, row.height)
            assertEquals(link, "instagram:$SIDE", row.videoId)
            assertEquals(SIDE, probe.asked.single().code)
            assertTrue(
                detailsOf(lookup.result).toString(),
                "$PROBE: HTTP 200, ${bytes(PROBE_CAROUSEL)} bytes; the post's video in the answer: yes; a wall on the page: no; rows 1 (DIRECT)"
                    in detailsOf(lookup.result),
            )
            assertBare(detailsOf(lookup.result))
        }
    }

    @Test
    fun `the rung's rows - V and the init check on at most 8`() = runTest {
        val files = Files { start() }
        val validator = SocialRowCheck(RecordingValidator(), files, site = SiteWays.INSTAGRAM)
        val record = record()
        val lookup = lookup(REEL, embedNoFiles(), record = record, validator = validator, probe = Probe { answered(200, PROBE_MANY) })
        val rows = (lookup.result as MasterResult.Success).result.candidates
        assertEquals(SocialRowCheck.MAX_READS, files.asked.size)
        assertTrue(rows.size.toString(), rows.size in 1..SocialRowCheck.MAX_READS)
        rows.forEach { assertTrue(it.mediaUrl, it.mediaUrl in files.asked) }
        assertTrue(
            detailsOf(lookup.result).toString(),
            "$PROBE: HTTP 200, ${bytes(PROBE_MANY)} bytes; the post's video in the answer: yes; a wall on the page: no; rows 10 (DIRECT)"
                in detailsOf(lookup.result),
        )
        assertBare(detailsOf(lookup.result))
    }

    @Test
    fun `the rung's stops - a 429 stops the site and cools the host, a checkpoint cools it, no answer in time counts as asked`() = runTest {
        val graphql = main(SiteWays.GRAPHQL to json(fixture("graphql_null.json")))

        // A 429: the record stops the host; the host cools from the stage's end.
        val limitedRecord = record()
        val limitedCooldown = SocialCooldown()
        val limited = lookup(
            REEL, graphql, record = limitedRecord, cooldown = limitedCooldown,
            probe = Probe { answered(429, RATE_ANSWER) },
        )
        assertTrue(
            detailsOf(limited.result).toString(),
            "$PROBE: HTTP 429, ${bytes(RATE_ANSWER)} bytes; the post's video in the answer: no; a wall on the page: no; " +
                "rows 0 (RATE_LIMITED: status 429)" in detailsOf(limited.result),
        )
        assertTrue(limitedRecord.rateLimited)
        assertEquals(listOf(WEB_HOST), limitedRecord.stopHosts())
        assertTrue(limitedCooldown.cooling(WEB_HOST))

        // A checkpoint, by its answer or by the tab's address: the host cools.
        val checkpoints = listOf(
            answered(400, CHECKPOINT_ANSWER) to
                "$PROBE: HTTP 400, ${bytes(CHECKPOINT_ANSWER)} bytes; the post's video in the answer: no; a wall on the page: no; " +
                "rows 0 (BOT_CHECK: a checkpoint: the site cools)",
            answered(200, "<html></html>", final = CHECKPOINT_PAGE, wall = true) to
                "$PROBE: HTTP 200, 13 bytes; the post's video in the answer: no; a wall on the page: yes; " +
                "rows 0 (BOT_CHECK: a checkpoint: the site cools)",
        )
        for ((reply, line) in checkpoints) {
            val cooldown = SocialCooldown()
            val checked = lookup(REEL, graphql, cooldown = cooldown, probe = Probe { reply })
            assertTrue(detailsOf(checked.result).toString(), line in detailsOf(checked.result))
            assertTrue(line, cooldown.cooling(WEB_HOST))
        }

        // No answer in time: it may have gone out, so it counts as asked (no second ask).
        val slowRecord = record()
        val slow = lookup(REEL, graphql, record = slowRecord, probe = Probe { awaitCancellation() })
        assertTrue(detailsOf(slow.result).toString(), "$PROBE: no answer in time" in detailsOf(slow.result))
        assertEquals(1, slowRecord.masterAsks)
        assertFalse(slowRecord.asks().last().answered)
        assertTrue(slowRecord.asked(probeAsk(REEL_ID)))

        // The tab's fetch failed: no HTTP answer.
        val failed = lookup(REEL, graphql, probe = Probe { answered(0, null) })
        assertTrue(
            detailsOf(failed.result).toString(),
            "$PROBE: no HTTP answer, 0 bytes; the post's video in the answer: no; a wall on the page: no; " +
                "rows 0 (TRANSIENT: failed: NETWORK)" in detailsOf(failed.result),
        )
        assertBare(detailsOf(limited.result) + detailsOf(slow.result) + detailsOf(failed.result))
    }

    @Test
    fun `the rung is not asked - the tab's reasons, signed in, after a final answer or a stop, cooling, the budget, twice`() = runTest {
        val graphql = main(SiteWays.GRAPHQL to json(fixture("graphql_null.json")))
        val tab = listOf(
            InstagramTabProbe.NO_LSD to "$PROBE: not asked (no LSD value on the page)",
            InstagramTabProbe.NOT_ON_POST to "$PROBE: not asked (the tab is not on the post)",
            InstagramTabProbe.NO_TAB to "$PROBE: not asked (no visible tab)",
            InstagramTabProbe.SIGNED_IN to "$PROBE: not asked (the tab is signed in)",
            "TypeError at https://www.instagram.com/reel/$REEL_CODE/?x=1" to "$PROBE: not asked (the tab's own reason)",
        )
        for ((why, line) in tab) {
            val record = record()
            val probe = Probe { InstagramTabProbe.Reply.NotAsked(why) }
            val lookup = lookup(REEL, graphql, record = record, probe = probe)
            assertTrue(detailsOf(lookup.result).toString(), line in detailsOf(lookup.result))
            assertEquals(line, 1, probe.asked.size)
            assertEquals(line, 0, record.masterAsks)
            assertBare(detailsOf(lookup.result))
        }

        val cooling = SocialCooldown().apply { note(listOf(WEB_HOST)) }
        val asked = record().apply { record(probeAsk(REEL_ID), ExtractorHttpResult.Success(200, PROBE_NONE, PROBE_ENDPOINT, JSON), NOW, LookupRecord.Asker.MASTER) }
        val cases = listOf(
            // Signed in: main's session; the rung is the signed-out query.
            Skip(graphql, record(), SocialCooldown(), SESSION, "$PROBE: not asked (signed in: the rung is the signed-out query)"),
            Skip(main(SiteWays.GRAPHQL to json(fixture("graphql_photo.json"))), record(), SocialCooldown(), null, "$PROBE: not asked (the answer is final)", PHOTO),
            Skip(
                main(SiteWays.GRAPHQL to json(fixture("graphql_login.json"))), record(), SocialCooldown(), null,
                "$PROBE: not asked (the site stopped in this lookup)",
            ),
            // Main's 429 beside rows: even the proof does not ask.
            Skip(
                main(SiteWays.GRAPHQL to refused(429, SiteExtractionFailure.RATE_LIMITED), SiteWays.EMBED_PAGE to html(fixture("embed_captioned.html").replace("video_url", "video_link"))),
                record(), SocialCooldown(), null, "$PROBE: not asked (a 429 in this lookup)", proof = true,
            ),
            Skip(graphql, record(), cooling, null, "$PROBE: not asked (cooling down after a 429 or a bot check)"),
            Skip(graphql, busy(), SocialCooldown(), null, "$PROBE: not asked (over the lookup's request budget)"),
            Skip(graphql, asked, SocialCooldown(), null, "$PROBE: not asked (already asked in this lookup)"),
        )
        for (case in cases) {
            val probe = Probe(proof = case.proof) { error("not asked") }
            val lookup = lookup(case.link, case.main, record = case.record, cooldown = case.cooldown, probe = probe, cookie = case.cookie)
            assertTrue(detailsOf(lookup.result).toString(), case.line in detailsOf(lookup.result))
            assertTrue(case.line, probe.asked.isEmpty())
            assertBare(detailsOf(lookup.result))
        }
    }
    @Test
    fun `SocialStages gives an Instagram lookup Instagram's ladder, and only an Instagram lookup inside its record`() = runTest {
        val http = FakeExtractorHttpClient()
        val stages = SocialStages(
            listOf(
                FacebookLadder(http, ::anyIdentity), TikTokLadder(http, ::anyIdentity), XLadder(http, ::anyIdentity), InstagramLadder(http),
            ),
        )
        val engine = MasterFallbackEngine(
            RecordingValidator(), CountingCapture(), MasterPolicy(enabled = true),
            contracts = SiteContracts(http), social = stages,
        )
        val main = main(SiteWays.GRAPHQL to json(fixture("graphql_reel.json")))

        val instagram = withContext(record() + MainPages(site = SITE)) {
            InstagramExtractor(MainPageRecorder(LookupRecorder(main))).extract(mainRequest(identify(REEL)))
            engine.extract(contractRequest(REEL, identify(REEL)))
        } as MasterResult.Success
        assertTrue(instagram.result.details.any { it.startsWith(I2) })
        assertFalse(instagram.result.details.any { line -> OTHERS.any { line.startsWith(it) } })
        assertNothingAsked(http)

        // An X lookup: X's ladder, never Instagram's.
        val x = checkNotNull(XExtractor(FakeExtractorHttpClient()).identify(X_PAGE))
        val onX = withContext(LookupRecord(labels = SiteWays.X.labels()) + MainPages(site = "x")) {
            engine.extract(contractRequest(X_PAGE, x))
        }
        assertTrue(detailsOf(onX).any { it.startsWith("master: x ") })
        assertFalse(detailsOf(onX).any { it.startsWith("master: instagram") })

        // Another site's lookup, or an Instagram lookup without the record: not claimed (R8's path).
        val other = SitePageIdentity("vimeo", "100000001", "https://vimeo.com/100000001")
        withContext(record() + MainPages()) {
            assertFalse(stages.claims(contractRequest(other.canonicalPageUrl, other)))
            assertTrue(stages.claims(contractRequest(REEL, identify(REEL))))
        }
        assertFalse(stages.claims(contractRequest(REEL, identify(REEL))))
        assertFalse(InstagramLadder(http).claims(contractRequest(REEL, identify(REEL))))
        assertEquals("InstagramLadder", InstagramLadder(http).toString())
    }

    @Test
    fun `wayOf names main's four asks for the post on Instagram's web host only`() {
        val graphql = "https://www.instagram.com/graphql/query/?doc_id=8845758582119845&variables=" +
            URLEncoder.encode("{\"shortcode\":\"$REEL_CODE\",\"fetch_tagged_user_count\":null}", StandardCharsets.UTF_8.name())
        assertEquals(SiteWays.APP_API, InstagramLadder.wayOf("https://www.instagram.com/api/v1/media/$REEL_ID/info/", REEL_CODE, REEL_ID))
        assertEquals(SiteWays.GRAPHQL, InstagramLadder.wayOf(graphql, REEL_CODE, REEL_ID))
        assertEquals(SiteWays.PAGE, InstagramLadder.wayOf("https://www.instagram.com/reel/$REEL_CODE/", REEL_CODE, REEL_ID))
        assertEquals(SiteWays.PAGE, InstagramLadder.wayOf("https://www.instagram.com/p/$REEL_CODE/", REEL_CODE, REEL_ID))
        assertEquals(SiteWays.PAGE, InstagramLadder.wayOf("https://WWW.instagram.com./tv/$REEL_CODE", REEL_CODE, REEL_ID))
        assertEquals(SiteWays.EMBED_PAGE, InstagramLadder.wayOf("https://www.instagram.com/p/$REEL_CODE/embed/captioned/", REEL_CODE, REEL_ID))
        listOf(
            graphql.replace(REEL_CODE, "C9fixtOTHER"),
            "https://www.instagram.com/graphql/query/?doc_id=8845758582119845",
            "https://www.instagram.com/p/C9fixtOTHER/",
            "https://www.instagram.com/p/$REEL_CODE/embed/",
            "https://www.instagram.com/stories/fixture.creator/$REEL_ID/",
            "https://www.instagram.com/api/v1/media/1/info/",
            "http://www.instagram.com/p/$REEL_CODE/",
            "https://instagram.com/p/$REEL_CODE/",
            "https://www.instagram.com.evil.test/p/$REEL_CODE/",
            PROBE_ENDPOINT,
            "not a link",
        ).forEach { url -> assertNull(url, InstagramLadder.wayOf(url, REEL_CODE, REEL_ID)) }
        // Without a media ID, no app API address is the post's.
        assertNull(InstagramLadder.wayOf("https://www.instagram.com/api/v1/media/$REEL_ID/info/", REEL_CODE, null))
    }

    @Test
    fun `mediaIdOf reads the code as main does, and the tab fetch is its own way in the record`() {
        assertEquals("908540701891980503", InstagramLadder.mediaIdOf("ybyPRoQWzX"))
        assertEquals("3310536259357233439", InstagramLadder.mediaIdOf("C3xYzAbCdEf"))
        assertEquals(InstagramLadder.mediaIdOf("C3xYzAbCdEf"), InstagramLadder.mediaIdOf("C3xYzAbCdEfPRIVATEtail"))
        assertNull(InstagramLadder.mediaIdOf("C3xYz.bCdEf"))
        assertNull(InstagramLadder.mediaIdOf("AAAA"))

        val labels = SiteWays.INSTAGRAM.labels()
        val ask = probeAsk(REEL_ID)
        assertEquals(SiteWays.TAB_FETCH, labels.way(ask))
        fun mark(body: String, final: String = PROBE_ENDPOINT) =
            labels.read(ask, SiteWays.TAB_FETCH, ExtractorHttpResult.Success(200, body, final, JSON))?.mark
        assertEquals(AnswerMark.DATA, mark(PROBE_VIDEO))
        assertEquals(AnswerMark.NO_POST, mark(PROBE_NONE))
        assertEquals(AnswerMark.NO_DATA, mark(PROBE_VIDEO.replace("video_versions", "image_versions")))
        assertEquals(AnswerMark.LOGIN_PAGE, mark(fixture("graphql_login.json")))
        assertEquals(AnswerMark.LOGIN_PAGE, mark("<html></html>", LOGIN_PAGE))
        assertEquals(AnswerMark.NOT_JSON, mark("<html></html>"))
        // Main's GraphQL ask stays GraphQL's.
        assertEquals(
            SiteWays.GRAPHQL,
            labels.way(LookupAsk(LookupAsk.GET, "https://www.instagram.com/graphql/query/?doc_id=8845758582119845", null)),
        )
    }

    /** One lookup: main's real Instagram adapter over [main] (its answers kept in the lookup), then Master's engine. */
    private suspend fun lookup(
        link: String,
        main: FakeExtractorHttpClient,
        master: FakeExtractorHttpClient = FakeExtractorHttpClient(),
        record: LookupRecord = record(),
        pages: MainPages = MainPages(site = SITE),
        validator: MasterMediaValidator = RecordingValidator(),
        capture: CountingCapture = CountingCapture(),
        cooldown: SocialCooldown = SocialCooldown(),
        ladder: InstagramLadder = InstagramLadder(master, cooldown = cooldown),
        probe: InstagramTabProbe? = null,
        cookie: String? = null,
    ): Lookup {
        val identity = identify(link)
        val context: CoroutineContext = record + pages + (probe ?: EmptyCoroutineContext)
        return withContext(context) {
            val answer = InstagramExtractor(MainPageRecorder(LookupRecorder(main))).extract(mainRequest(identity, cookie))
            // Main's rows set aside (the trial), else main's reason.
            val failure = (answer as? SiteExtractionResult.Failure)?.reason ?: SiteExtractionFailure.RESPONSE_CHANGED
            Lookup(answer, engine(master, validator, capture, ladder).extract(contractRequest(link, identity, failure, cookie)))
        }
    }

    private class Lookup(val main: SiteExtractionResult, val result: MasterResult)

    private class Way(
        val name: String,
        val main: FakeExtractorHttpClient,
        val cookie: String?,
        val asks: Int,
        val mainRows: Int,
        val rows: Int,
        val lines: List<String>,
    )

    private class Never(
        val main: FakeExtractorHttpClient,
        val mainReason: SiteExtractionFailure,
        val line: String,
        val cools: Boolean = false,
    )

    private class Budget(val main: FakeExtractorHttpClient, val record: LookupRecord, val ladder: InstagramLadder, val why: String)

    private class Skip(
        val main: FakeExtractorHttpClient,
        val record: LookupRecord,
        val cooldown: SocialCooldown,
        val cookie: String?,
        val line: String,
        val link: String = REEL,
        val proof: Boolean = false,
    )

    /** A fake tab: the rung's switch [on] and its [proof]; its [reply] to each ask; the asks, in order. */
    private class Probe(
        private val on: Boolean = true,
        private val proof: Boolean = false,
        private val reply: suspend (InstagramTabProbe.Request) -> InstagramTabProbe.Reply,
    ) : InstagramTabProbe {
        val asked = mutableListOf<InstagramTabProbe.Request>()

        override fun on(): Boolean = on

        override fun proof(): Boolean = proof

        override suspend fun ask(request: InstagramTabProbe.Request): InstagramTabProbe.Reply {
            asked += request
            return reply(request)
        }
    }

    /** A fake file reader (S6's route B): [answer] for each address; the addresses asked, in order. */
    private class Files(private val answer: (String) -> MediaStart) : MediaStartReader {
        val asked = mutableListOf<String>()

        override suspend fun read(url: String, headers: Map<String, String>, maxBytes: Int): MediaStart {
            asked += url
            return answer(url)
        }
    }

    private fun engine(
        http: FakeExtractorHttpClient,
        validator: MasterMediaValidator = RecordingValidator(),
        capture: CountingCapture = CountingCapture(),
        ladder: InstagramLadder = InstagramLadder(http),
    ) = MasterFallbackEngine(
        validator, capture, MasterPolicy(enabled = true),
        contracts = SiteContracts(http),
        social = ladder,
    )

    private fun record() = LookupRecord(labels = SiteWays.INSTAGRAM.labels())

    /** A record with twelve asks before main's (another site's pages): main's is the thirteenth. */
    private fun busy() = record().apply {
        repeat(SocialBudget.INSTAGRAM.requests - 1) {
            val url = "https://example.test/page-$it"
            record(LookupAsk(LookupAsk.GET, url, null), ExtractorHttpResult.Success(200, "{}", url, JSON), NOW)
        }
    }

    private fun detailsOf(result: MasterResult): List<String> = when (result) {
        is MasterResult.Success -> result.result.details
        is MasterResult.Failure -> result.details
        is MasterResult.NeedsPlayback -> result.details
        is MasterResult.NeedsSelection -> result.details
        is MasterResult.Skipped -> emptyList()
    }

    /** What the trial compares: each row's file, its picture's height and its length. */
    private fun facts(rows: List<MediaCandidate>) = rows.map { Triple(it.mediaUrl, it.height, it.durationMillis) }.toSet()

    private fun assertNothingAsked(http: FakeExtractorHttpClient) {
        assertTrue(http.requestedUrls.toString(), http.requestedUrls.isEmpty())
        assertTrue(http.postedUrls.toString(), http.postedUrls.isEmpty())
        assertTrue(http.probedUrls.toString(), http.probedUrls.isEmpty())
    }

    /** No code, ID, link, handle or post words in the lines; no query in Instagram's lines. */
    private fun assertBare(details: List<String>) {
        details.forEach { line ->
            listOf(
                "C9fixt", REEL_ID, "3310536259357233439", "fixture.creator", "Fixture Creator", "Embed caption",
                "instagram.com", "fbcdn", "csrf", "555", "TypeError",
            ).forEach { assertFalse("$line has $it", line.contains(it)) }
            if (line.startsWith("master: instagram")) assertFalse(line, line.contains('?'))
        }
    }

    private companion object {
        const val SITE = "instagram"
        const val REEL_CODE = "C9fixtREEL1"
        const val SIDE = "C9fixtSIDE1"
        const val REEL = "https://www.instagram.com/reel/$REEL_CODE/"
        const val SIDECAR = "https://www.instagram.com/p/$SIDE/"
        const val PHOTO = "https://www.instagram.com/p/C9fixtPHOTO/"
        const val KEY = "instagram:$REEL_CODE"
        const val WEB_HOST = "www.instagram.com"
        const val JSON = "application/json"
        const val HTML = "text/html"
        const val SESSION = "csrftoken=csrfFixture; sessionid=555%3Asecret; ds_user_id=555"
        const val LADDER = "master: instagram ladder"
        const val I2 = "master: instagram I2"
        const val PROBE = "master: instagram probe"
        const val CHECKPOINT_PAGE = "https://www.instagram.com/challenge/action/"
        const val LOGIN_PAGE = "https://www.instagram.com/accounts/login/"
        const val PROBE_ENDPOINT = InstagramProbeQuery.ENDPOINT
        const val X_PAGE = "https://x.com/fixture_user/status/1785999999999999999"
        val OTHERS = listOf("master: facebook", "master: tiktok", "master: x ")

        val REEL_ID: String = checkNotNull(InstagramLadder.mediaIdOf(REEL_CODE))

        /** The probe's answer when the query shows the post signed out (yt-dlp's path), with a video's files. */
        val PROBE_VIDEO = """{"data":{"xig_polaris_media":{"if_not_gated_logged_out":{"code":"$REEL_CODE",""" +
            """"video_versions":[{"width":720,"height":1280,"url":"https://instagram.fxyz1-1.fna.fbcdn.net/o1/v/t16/f2/m86/probe.mp4"}]}}},"status":"ok"}"""

        /** Task 15 (F1): the post's sizes in its own fields (the v1 item's), its file stating none. */
        const val ORIGINAL_SIZE = "\"original_width\":720,\"original_height\":1280,"

        /** Task 15 (F1): the probe's answer whose file states no size; the post states its own. */
        val PROBE_NO_SIZE = """{"data":{"xig_polaris_media":{"if_not_gated_logged_out":{"code":"$REEL_CODE","media_type":2,$ORIGINAL_SIZE""" +
            """"video_versions":[{"type":101,"url":"https://instagram.fxyz1-1.fna.fbcdn.net/o1/v/t16/f2/m86/probe.mp4"}]}}},"status":"ok"}"""

        /** The probe's answer without the post (gated or gone alike). */
        const val PROBE_NONE = """{"data":{"xig_polaris_media":{"if_not_gated_logged_out":null}},"status":"ok"}"""

        /** Instagram's rate-limit answer. */
        const val RATE_ANSWER = """{"message":"Please wait a few minutes before you try again.","require_login":true,"status":"fail"}"""

        /** Instagram's checkpoint answer. */
        const val CHECKPOINT_ANSWER = """{"message":"checkpoint_required","checkpoint_url":"/challenge/","lock":true,"status":"fail"}"""

        /** The query's answer naming the post by its pk alone (its `id` the pk and the owner's ID), with a video's file. */
        val PROBE_BY_PK = """{"data":{"xig_polaris_media":{"if_not_gated_logged_out":{"pk":"$REEL_ID","id":"${REEL_ID}_1234",""" +
            """"media_type":2,"video_versions":[{"width":720,"height":1280,"url":"https://instagram.fxyz1-1.fna.fbcdn.net/o1/v/t16/f2/m86/probe.mp4"}]}}},"status":"ok"}"""

        /** The query's answer for the carousel [SIDE]: two videos, each its own file. */
        val PROBE_CAROUSEL = """{"data":{"xig_polaris_media":{"if_not_gated_logged_out":{"code":"$SIDE","media_type":8,"carousel_media":[""" +
            """{"media_type":2,"video_versions":[{"width":720,"height":1280,"url":"https://instagram.fxyz1-1.fna.fbcdn.net/o1/v/t16/f2/m86/item1.mp4"}]},""" +
            """{"media_type":2,"video_versions":[{"width":1080,"height":1920,"url":"https://instagram.fxyz1-1.fna.fbcdn.net/o1/v/t16/f2/m86/item2.mp4"}]}""" +
            """]}}},"status":"ok"}"""

        /** The query's answer with ten files for the post's video (more than S6 reads). */
        val PROBE_MANY = """{"data":{"xig_polaris_media":{"if_not_gated_logged_out":{"code":"$REEL_CODE","media_type":2,"video_versions":[""" +
            (1..10).joinToString(",") {
                """{"width":${700 + it},"height":${1200 + it},"url":"https://instagram.fxyz1-1.fna.fbcdn.net/o1/v/t16/f2/m86/many$it.mp4?oe=7A000000&oh=00_fixture"}"""
            } + """]}}},"status":"ok"}"""

        /** The query's answer for a photo post. */
        val PROBE_PHOTO = """{"data":{"xig_polaris_media":{"if_not_gated_logged_out":{"code":"$REEL_CODE","media_type":1,""" +
            """"image_versions2":{"candidates":[{"width":1080,"height":1080,"url":"https://instagram.fxyz1-1.fna.fbcdn.net/v/photo.jpg"}]}}}},"status":"ok"}"""

        /** The query's 400: the value is rejected (no other value is tried). */
        const val REJECTED_ANSWER = """{"message":"fixture","status":"fail"}"""

        /** The query's answer with errors. */
        const val ERRORS_ANSWER = """{"errors":[{"message":"fixture","severity":"CRITICAL"}],"status":"ok"}"""

        /** Main's embed page signed out: the post a video by its type, its file not listed (decision 24's case). */
        val EMBED_NO_FILES: String = fixture("embed_captioned.html").let { page ->
            val start = page.indexOf("\\\"video_url\\\"")
            check(start > 0) { "embed fixture: no video_url" }
            page.removeRange(start, page.indexOf("\\\",", start) + 3)
        }

        /** A file's size for S6's fake reads (32 KiB). */
        const val TOTAL = 32_768L

        /**
         * Task 15 (F2): a made-up embed page signed out in the real page's shape (about 227k
         * characters, as slot 5's on the phone): styles and scripts first, then the embed's data —
         * its own context naming the post's code with a camelCase video mark, then (with [post])
         * the post's GraphQL node, a video by its type, its file not listed.
         */
        fun realEmbed(post: Boolean = true): String {
            val context = "\"context\":{\"media_id\":\"3310536259357233439\",\"shortcode\":\"$REEL_CODE\"," +
                "\"type\":\"GraphVideo\",\"isVideo\":true,\"captionTitle\":\"Embed caption\"}"
            val node = ",\"gql_data\":{\"shortcode_media\":{\"__typename\":\"GraphVideo\",\"shortcode\":\"$REEL_CODE\"," +
                "\"is_video\":true,\"dimensions\":{\"height\":1136,\"width\":640}," +
                "\"display_url\":\"https://instagram.fxyz1-1.fna.fbcdn.net/v/e.jpg\",\"video_duration\":15.2," +
                "\"owner\":{\"username\":\"fixture.creator\",\"full_name\":\"Fixture Creator\"}}}"
            val data = "{" + context + (if (post) node else "") + "}"
            val escaped = data.replace("\\", "\\\\").replace("\"", "\\\"")
            return buildString {
                append("<!DOCTYPE html><html><head><style>")
                repeat(5_400) { append(".e$it{margin:0;padding:0;color:#262626}") }
                append("</style></head><body><div class=\"Embed\"></div><script>")
                repeat(400) { append("function f$it(a){return a+$it;}") }
                append("requireLazy([\"TimeSliceImpl\",\"ServerJS\"],function(TimeSlice,ServerJS){var s=(new ServerJS());")
                append("s.handle({\"require\":[[\"PolarisEmbedSimple\",\"init\",[],[{\"contextJSON\":\"")
                append(escaped)
                append("\"}]]]});});</script></body></html>")
            }
        }

        /** Task 15 (F2): the real embed page's shape with the post's node. */
        val REAL_EMBED: String = realEmbed()


        fun fixture(name: String) = Fixtures.read("instagram/$name")

        /** Main's embed page only, signed out: a video without files (GraphQL and the post's page a 404). */
        fun embedNoFiles() = main(SiteWays.EMBED_PAGE to html(EMBED_NO_FILES))

        /** [text]'s bytes as UTF-8. */
        fun bytes(text: String) = text.toByteArray(StandardCharsets.UTF_8).size

        /** A file's first bytes as its host answers them: an MP4 start of a 32 KiB file. */
        fun start(bytes: ByteArray = InitBytes.mp4()) =
            MediaStart.Read(206, "video/mp4", "bytes 0-${bytes.size - 1}/$TOTAL", bytes.size.toLong(), bytes)

        fun identifyOrNull(url: String): SitePageIdentity? = InstagramExtractor(FakeExtractorHttpClient()).identify(url)

        fun identify(url: String): SitePageIdentity = checkNotNull(identifyOrNull(url)) { url }

        /** The app's adapters' identify, four sites. */
        fun anyIdentity(url: String): SitePageIdentity? = identifyOrNull(url)
            ?: XExtractor(FakeExtractorHttpClient()).identify(url)
            ?: TikTokExtractor(FakeExtractorHttpClient()).identify(url)
            ?: FacebookExtractor(FakeExtractorHttpClient()).identify(url)

        fun json(body: String): (String) -> ExtractorHttpResult = { url -> ExtractorHttpResult.Success(200, body, url, JSON) }

        fun html(body: String, final: String? = null): (String) -> ExtractorHttpResult =
            { url -> ExtractorHttpResult.Success(200, body, final ?: url, HTML) }

        fun refused(status: Int, reason: SiteExtractionFailure = SiteExtractionFailure.HTTP_STATUS): (String) -> ExtractorHttpResult =
            { ExtractorHttpResult.Failure(reason, status) }

        /** Main's client: each of main's ways answers its entry in [answers] (for any post); every other GET a 404. */
        fun main(vararg answers: Pair<String, (String) -> ExtractorHttpResult>): FakeExtractorHttpClient {
            val byWay = answers.toMap()
            return FakeExtractorHttpClient(getResponder = { url, _ -> byWay[mainWay(url)]?.invoke(url) })
        }

        /** Which of main's ways [url] is (main's addresses on Instagram's web host). */
        fun mainWay(url: String): String {
            val path = URI(url).path
            return when {
                path.startsWith("/graphql/") -> SiteWays.GRAPHQL
                path.endsWith("/embed/captioned/") -> SiteWays.EMBED_PAGE
                path.startsWith("/api/") -> SiteWays.APP_API
                else -> SiteWays.PAGE
            }
        }

        /** The tab's fetch went out and [status] came back with [body] (its bytes as UTF-8). */
        fun answered(status: Int, body: String?, final: String? = PROBE_ENDPOINT, wall: Boolean = false) =
            InstagramTabProbe.Reply.Answered(
                status, body?.toByteArray(StandardCharsets.UTF_8)?.size?.toLong() ?: 0L, body,
                body?.let { if (it.startsWith("{")) JSON else HTML }, final.takeIf { body != null }, wall,
            )

        /** The probe's ask as the record keys it (the page's LSD value never kept). */
        fun probeAsk(mediaId: String) = LookupAsk(
            LookupAsk.POST, PROBE_ENDPOINT, InstagramProbeQuery.formBody(mediaId, InstagramProbeQuery.LSD_PLACEHOLDER), InstagramProbeQuery.HEADERS,
        )
    }
}
