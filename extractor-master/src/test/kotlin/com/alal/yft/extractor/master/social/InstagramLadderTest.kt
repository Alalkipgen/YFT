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
 * Social plan S2d part 1: Instagram's ladder in Master's engine. I2 reads main's own answers in
 * this lookup ([MainPages]: the app API signed in, GraphQL, the post's page, the embed page; each
 * way's page or refused GET) with no new ask, judges each and gives one answer for the lookup:
 * the link's item's rows bound to the post (main's files, and the 360p file main drops), checked
 * by V; a photo post final with main's reason; a rate limit, a checkpoint, a login wall, no post
 * and a broken embed never final, never REMOVED; rows after main's LOGIN_REQUIRED stay unused
 * (the engine's rule). A renamed key: by shape, one item only. The tab-fetch probe: off by its
 * switch, asked once after I2 and only signed out, recorded as Master's ask (a 429 stops the site,
 * a checkpoint cools it), said in one line, never a row. S7's budget; SocialStages gives an
 * Instagram lookup Instagram's ladder. Main's real Instagram adapter over main's fixtures; Master's
 * client is never asked; no live calls.
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
        val renamed = lookup("$SIDECAR?img_index=3", main(SiteWays.GRAPHQL to json(carousel.replace("video_url", "video_link"))), validator = validator)
        val details = (renamed.result as MasterResult.NeedsPlayback).details
        assertTrue(details.toString(), "$I2: SHAPE_CHANGED (GraphQL: a video, no files read); next: NEXT_RUNG" in details)
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
        val bounded = lookup(REEL, main(SiteWays.GRAPHQL to json(reel)), pages = MainPages(maxChars = 64, site = SITE))
        val boundedDetails = detailsOf(bounded.result)
        assertTrue(boundedDetails.toString(), "$I2 main's GraphQL: not kept (over the bound)" in boundedDetails)
        assertTrue(boundedDetails.toString(), "$I2: none kept for the post; nothing to read" in boundedDetails)
        assertTrue(bounded.result is MasterResult.NeedsPlayback)
        assertBare(detailsOf(elsewhere) + boundedDetails)
    }

    @Test
    fun `the probe - off by its switch, on asked once after I2 by the tab, its answer one line and never a row`() = runTest {
        // Off: one line, nothing asked.
        val off = Probe(on = false) { error("not asked") }
        val quiet = lookup(REEL, main(SiteWays.GRAPHQL to json(fixture("graphql_reel.json"))), probe = off)
        assertTrue("$PROBE: off" in detailsOf(quiet.result))
        assertTrue(off.asked.isEmpty())

        // On, beside rows: the tab's answer holds the post's video; the rows stay main's answer's.
        val record = record()
        val master = FakeExtractorHttpClient()
        val probe = Probe { answered(200, PROBE_VIDEO) }
        val lookup = lookup(REEL, main(SiteWays.GRAPHQL to json(fixture("graphql_reel.json"))), master, record, probe = probe)
        val success = lookup.result as MasterResult.Success
        assertEquals(6, success.result.candidates.size)
        assertFalse(success.result.candidates.any { "/probe.mp4" in it.mediaUrl })
        val asked = probe.asked.single()
        assertEquals(REEL_CODE, asked.code)
        assertEquals(REEL_ID, asked.mediaId)
        assertEquals(REEL, asked.pageUrl)
        val details = success.result.details
        val bytes = PROBE_VIDEO.toByteArray(StandardCharsets.UTF_8).size
        assertTrue(
            details.toString(),
            "$PROBE: HTTP 200, $bytes bytes; the post's video in the answer: yes; a wall on the page: no" in details,
        )
        // The probe is Master's ask in the record, its query's, read as data; Master's client never asked.
        assertEquals(1, record.masterAsks)
        val ask = record.asks().last()
        assertEquals(LookupRecord.Asker.MASTER, ask.asker)
        assertEquals(SiteWays.TAB_FETCH, ask.way)
        assertEquals(AnswerMark.DATA, ask.mark)
        assertTrue(record.asked(probeAsk(REEL_ID)))
        assertNothingAsked(master)

        // On, after I2's no post: the tab's answer has none either, and its page shows a wall.
        val walled = Probe { answered(200, PROBE_NONE, wall = true) }
        val none = record()
        val nothing = lookup(
            REEL,
            main(SiteWays.GRAPHQL to json(fixture("graphql_null.json")), SiteWays.EMBED_PAGE to html(fixture("embed_no_video.html"))),
            record = none, probe = walled,
        )
        val noneDetails = (nothing.result as MasterResult.NeedsPlayback).details
        val noneBytes = PROBE_NONE.toByteArray(StandardCharsets.UTF_8).size
        assertTrue(
            noneDetails.toString(),
            "$PROBE: HTTP 200, $noneBytes bytes; the post's video in the answer: no; a wall on the page: yes" in noneDetails,
        )
        assertEquals(AnswerMark.NO_POST, none.asks().last().mark)
        // The probe's line comes after I2's answer.
        assertTrue(noneDetails.indexOfFirst { it.startsWith(PROBE) } > noneDetails.indexOfFirst { it.startsWith("$I2:") })
        assertBare(detailsOf(quiet.result) + details + noneDetails)
    }

    @Test
    fun `the probe's stops - a 429 stops the site and cools the host, a checkpoint cools it, no answer in time counts as asked`() = runTest {
        val graphql = main(SiteWays.GRAPHQL to json(fixture("graphql_null.json")))

        // A 429: the record stops the host; the host cools from the stage's end.
        val limitedRecord = record()
        val limitedCooldown = SocialCooldown()
        val limited = lookup(
            REEL, graphql, record = limitedRecord, cooldown = limitedCooldown,
            probe = Probe { answered(429, RATE_ANSWER) },
        )
        val rateBytes = RATE_ANSWER.toByteArray(StandardCharsets.UTF_8).size
        assertTrue(
            detailsOf(limited.result).toString(),
            "$PROBE: HTTP 429, $rateBytes bytes; the post's video in the answer: no; a wall on the page: no" in detailsOf(limited.result),
        )
        assertTrue(limitedRecord.rateLimited)
        assertEquals(listOf(WEB_HOST), limitedRecord.stopHosts())
        assertTrue(limitedCooldown.cooling(WEB_HOST))

        // A checkpoint, by its answer or by the tab's address: the host cools.
        val checkpoints = listOf(
            answered(400, CHECKPOINT_ANSWER) to
                "$PROBE: HTTP 400, ${CHECKPOINT_ANSWER.toByteArray(StandardCharsets.UTF_8).size} bytes; " +
                "the post's video in the answer: no; a wall on the page: no; a checkpoint: the site cools",
            answered(200, "<html></html>", final = CHECKPOINT_PAGE, wall = true) to
                "$PROBE: HTTP 200, 13 bytes; the post's video in the answer: no; a wall on the page: yes; a checkpoint: the site cools",
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
            "$PROBE: no HTTP answer, 0 bytes; the post's video in the answer: no; a wall on the page: no" in detailsOf(failed.result),
        )
        assertBare(detailsOf(limited.result) + detailsOf(slow.result) + detailsOf(failed.result))
    }

    @Test
    fun `the probe is not asked - the tab's reasons, signed in, after a final answer or a stop, cooling, the budget, twice`() = runTest {
        val graphql = main(SiteWays.GRAPHQL to json(fixture("graphql_null.json")))
        val tab = listOf(
            InstagramTabProbe.NO_LSD to "$PROBE: not asked (no LSD value on the page)",
            InstagramTabProbe.NOT_ON_POST to "$PROBE: not asked (the tab is not on the post)",
            InstagramTabProbe.NO_TAB to "$PROBE: not asked (no visible tab)",
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
            // Signed in: main's session; the probe is the signed-out query.
            Skip(
                main(SiteWays.APP_API to json(fixture("api_info_reel.json"))), record(), SocialCooldown(), SESSION,
                "$PROBE: not asked (signed in: the probe is the signed-out query)",
            ),
            Skip(main(SiteWays.GRAPHQL to json(fixture("graphql_photo.json"))), record(), SocialCooldown(), null, "$PROBE: not asked (the answer is final)", PHOTO),
            Skip(
                main(SiteWays.GRAPHQL to json(fixture("graphql_login.json"))), record(), SocialCooldown(), null,
                "$PROBE: not asked (the site stopped in this lookup)",
            ),
            Skip(
                main(SiteWays.GRAPHQL to refused(429, SiteExtractionFailure.RATE_LIMITED), SiteWays.EMBED_PAGE to html(fixture("embed_captioned.html").replace("video_url", "video_link"))),
                record(), SocialCooldown(), null, "$PROBE: not asked (a 429 in this lookup)",
            ),
            Skip(graphql, record(), cooling, null, "$PROBE: not asked (cooling down after a 429 or a bot check)"),
            Skip(graphql, busy(), SocialCooldown(), null, "$PROBE: not asked (over the lookup's request budget)"),
            Skip(graphql, asked, SocialCooldown(), null, "$PROBE: not asked (already asked in this lookup)"),
        )
        for (case in cases) {
            val probe = Probe { error("not asked") }
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
    )

    /** A fake tab: its switch [on]; its [reply] to each ask; the asks, in order. */
    private class Probe(
        private val on: Boolean = true,
        private val reply: suspend (InstagramTabProbe.Request) -> InstagramTabProbe.Reply,
    ) : InstagramTabProbe {
        val asked = mutableListOf<InstagramTabProbe.Request>()

        override fun on(): Boolean = on

        override suspend fun ask(request: InstagramTabProbe.Request): InstagramTabProbe.Reply {
            asked += request
            return reply(request)
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

        /** The probe's answer without the post (gated or gone alike). */
        const val PROBE_NONE = """{"data":{"xig_polaris_media":{"if_not_gated_logged_out":null}},"status":"ok"}"""

        /** Instagram's rate-limit answer. */
        const val RATE_ANSWER = """{"message":"Please wait a few minutes before you try again.","require_login":true,"status":"fail"}"""

        /** Instagram's checkpoint answer. */
        const val CHECKPOINT_ANSWER = """{"message":"checkpoint_required","checkpoint_url":"/challenge/","lock":true,"status":"fail"}"""


        fun fixture(name: String) = Fixtures.read("instagram/$name")

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
