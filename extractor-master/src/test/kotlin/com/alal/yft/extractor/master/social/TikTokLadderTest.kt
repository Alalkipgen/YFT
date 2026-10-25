package com.alal.yft.extractor.master.social

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.MasterStage
import com.alal.yft.extractor.master.NOW
import com.alal.yft.extractor.master.RecordingValidator
import com.alal.yft.extractor.master.ValidationResult
import com.alal.yft.extractor.master.contract.AGENT
import com.alal.yft.extractor.master.contract.CountingCapture
import com.alal.yft.extractor.master.contract.SiteContracts
import com.alal.yft.extractor.master.contract.answering
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
import com.alal.yft.extractor.master.recipes.SignedRecipes
import com.alal.yft.extractor.sites.facebook.FacebookExtractor
import com.alal.yft.extractor.sites.tiktok.TikTokExtractor
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Social plan S2b: TikTok's ladder in Master's engine. A photo post stops the lookup with no
 * request (the link's or main's resolved address, the address main's page landed on, main's page
 * data); T1, the embed player, is asked once inside the lookup's record (route B's rules: never the
 * user's cookies, nothing new after a 429) and judged: rows bound to the item go to the media
 * checks, a photo post or DRM is final, a 429 or a bot check stops and cools its host; TikTok's
 * status, a login address and no evidence leave it to T2, main's pages read with no request (the
 * page with the most rows; another video's page and a page over the bound are not read). S7's
 * budget; a short link through main's resolved link; [SocialStages] gives each lookup its site's
 * ladder; V asks only TikTok's media hosts in a TikTok lookup. Without the lookup's record R8's path
 * is unchanged. Main's real TikTok adapter over main's fixtures; no live calls.
 */
class TikTokLadderTest {
    @Test
    fun `T1's rows bound to the item are checked, one Master ask by the embed player`() = runTest {
        val record = record()
        val validator = RecordingValidator()
        val capture = CountingCapture()
        val embed = answering(EMBED_URL to EMBED, contentType = HTML)

        val lookup = lookup(PAGE, main(VIDEO_PAGE), embed, record = record, validator = validator, capture = capture)

        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, lookup.mainFailure)
        val success = lookup.result as MasterResult.Success
        assertEquals(MasterStage.CONTRACT, success.stage)
        val row = success.result.candidates.single()
        assertEquals(EMBED_FILE, row.mediaUrl)
        assertEquals("tiktok:$VIDEO", row.videoId)
        assertEquals(1, validator.seen.size)
        assertEquals(0, capture.calls)
        assertEquals(listOf(EMBED_URL), embed.requestedUrls)
        assertNull("never the user's cookies", embed.requestedHeaders.single()["Cookie"])
        assertEquals(1, record.masterAsks)
        val masters = record.asks().filter { it.asker == LookupRecord.Asker.MASTER }
        assertEquals(listOf(SiteWays.EMBED_PLAYER), masters.map { it.way })
        val details = success.result.details
        assertTrue(details.toString(), "$LADDER after NO_MEDIA_FOUND; replayed 0; new 1" in details)
        assertTrue(details.toString(), "$T1: DIRECT (rows 1, bound to the item ID); next: CHECK_ROWS" in details)
        assertFalse(details.toString(), details.any { it.startsWith(T2) })
        assertBare(details)
    }

    @Test
    fun `a photo post stops the lookup with no request, never captured`() = runTest {
        val photoPage = "$BASE$PHOTO"
        val short = identify(SHORT)
        val cases = listOf(
            Photo(PHOTO_LINK, MainPages(site = SITE), "the link: a photo address"),
            Photo(
                SHORT,
                MainPages(site = SITE).apply { note(short.canonicalPageUrl, BROWSER, html("<html></html>", PHOTO_LINK)) },
                "main's resolved link: a photo address",
            ),
            Photo(
                photoPage,
                MainPages(site = SITE).apply { note(photoPage, BROWSER, html("<html></html>", PHOTO_LINK)) },
                "main's page: landed on a photo address",
            ),
            Photo(
                photoPage,
                MainPages(site = SITE).apply { note(photoPage, BROWSER, html(Fixtures.read("tiktok/universal_photo.html"), photoPage)) },
                "main's page data: a photo post",
            ),
        )
        for (case in cases) {
            val http = answering(EMBED_URL to EMBED, "https://www.tiktok.com/embed/v2/$PHOTO" to EMBED, contentType = HTML)
            val record = record()
            val capture = CountingCapture()
            val validator = RecordingValidator()

            val result = withContext(record + case.pages) {
                engine(http, validator, capture)
                    .extract(contractRequest(case.link, identify(case.link), SiteExtractionFailure.NO_MEDIA_FOUND, COOKIE))
            }

            val failure = result as MasterResult.Failure
            assertEquals(case.why, SiteExtractionFailure.NO_MEDIA_FOUND, failure.reason)
            assertTrue(case.why, http.requestedUrls.isEmpty())
            assertEquals(case.why, 0, record.masterAsks)
            assertEquals(case.why, 0, capture.calls)
            assertTrue(case.why, validator.seen.isEmpty())
            assertTrue(failure.details.toString(), "$LADDER: PHOTO_ONLY (${case.why}); next: FINAL; not asked" in failure.details)
            assertBare(failure.details)
        }
    }

    @Test
    fun `T1's photo post and DRM are final, never captured`() = runTest {
        val drm = EMBED.replace("\"videoMeta\":", "\"isDrm\":true,\"videoMeta\":")
        val photo = EMBED.replace("\"urls\":[\"$EMBED_FILE\"]", "\"urls\":[]")
            .replace("\"imagePostInfo\":null", "\"imagePostInfo\":{\"images\":[{\"displayImage\":{}}]}")
        listOf(
            Triple(drm, SiteExtractionFailure.DRM_PROTECTED, "$T1: DRM (the reader: DRM); next: FINAL"),
            Triple(photo, SiteExtractionFailure.NO_MEDIA_FOUND, "$T1: PHOTO_ONLY (the reader: a photo post); next: FINAL"),
        ).forEach { (body, reason, line) ->
            val http = answering(EMBED_URL to body, contentType = HTML)
            val capture = CountingCapture()
            val validator = RecordingValidator()

            val result = withContext(record() + MainPages(site = SITE)) {
                engine(http, validator, capture).extract(contractRequest(PAGE, identify(PAGE), cookie = COOKIE))
            }

            val failure = result as MasterResult.Failure
            assertEquals(line, reason, failure.reason)
            assertEquals(line, listOf(EMBED_URL), http.requestedUrls)
            assertEquals(line, 0, capture.calls)
            assertTrue(line, validator.seen.isEmpty())
            assertTrue(failure.details.toString(), line in failure.details)
            assertBare(failure.details)
        }
    }

    @Test
    fun `TikTok's status, a login address or no evidence leave it to T2, main's pages with no request`() = runTest {
        val cases = listOf(
            html(Fixtures.read("tiktok/universal_private.html"), EMBED_URL) to
                "$T1: NEEDS_PLAYBACK (TikTok status: private or unavailable; one anonymous answer); next: PLAYBACK",
            html("<html><body>sign in</body></html>", LOGIN) to "$T1: LOGIN_WALL (final address: login); next: BROWSER",
            html(MISSING, EMBED_URL) to "$T1: SHAPE_CHANGED (no rows; no status); next: NEXT_RUNG",
        )
        for ((answer, line) in cases) {
            val embed = FakeExtractorHttpClient(responses = mapOf(EMBED_URL to answer))
            val capture = CountingCapture()

            val lookup = lookup(PAGE, main(VIDEO_PAGE), embed, capture = capture)

            val success = lookup.result as MasterResult.Success
            assertEquals(line, MasterStage.PAGE_DATA, success.stage)
            val rows = success.result.candidates
            assertEquals(line, BEST_FILE, rows.first().mediaUrl)
            rows.forEach { assertEquals(line, "tiktok:$VIDEO", it.videoId) }
            assertFalse(line, rows.any { "download" in it.mediaUrl })
            assertEquals(line, listOf(EMBED_URL), embed.requestedUrls)
            assertEquals(line, 0, capture.calls)
            val details = success.result.details
            assertTrue(details.toString(), line in details)
            assertTrue(details.toString(), details.any { it.startsWith("$T2: page 1: DIRECT (rows ") })
            assertTrue(details.toString(), "$T2: next: CHECK_ROWS" in details)
            assertBare(details)
        }

        // Main's page without rows: TikTok's status there is never final; the tab's own playback.
        val embed = FakeExtractorHttpClient(responses = mapOf(EMBED_URL to html(MISSING, EMBED_URL)))
        val pages = MainPages(site = SITE).apply { note(PAGE, BROWSER, html(Fixtures.read("tiktok/universal_private.html"), PAGE)) }
        val capture = CountingCapture()
        val result = withContext(record() + pages) {
            engine(embed, capture = capture).extract(contractRequest(PAGE, identify(PAGE), cookie = COOKIE))
        }
        val details = (result as MasterResult.NeedsPlayback).details
        assertEquals(1, capture.calls)
        assertTrue(details.toString(), "$T2: page 1: NEEDS_PLAYBACK (TikTok status: private or unavailable; one anonymous answer)" in details)
        assertTrue(details.toString(), "$T2: next: PLAYBACK" in details)
        assertBare(details)
    }

    @Test
    fun `T1's 429 or bot check stops and cools its host, T2 still reads with no request`() = runTest {
        val answers = listOf(
            ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429) to "$T1: RATE_LIMITED (status 429); next: STOP_SITE",
            html("<html><body>Please verify you are human</body></html>", EMBED_URL) to
                "$T1: BOT_CHECK (a verify page); next: STOP_SITE",
        )
        for ((answer, line) in answers) {
            val cooldown = SocialCooldown()
            val embed = FakeExtractorHttpClient(responses = mapOf(EMBED_URL to answer))
            val ladder = TikTokLadder(embed, ::identifyOrNull, cooldown = cooldown)

            val first = lookup(PAGE, main(VIDEO_PAGE), embed, ladder = ladder)

            val rows = (first.result as MasterResult.Success).result
            assertEquals(line, BEST_FILE, rows.candidates.first().mediaUrl)
            assertTrue(rows.details.toString(), line in rows.details)
            assertTrue(line, cooldown.cooling(TIKTOK_HOST))
            assertEquals(line, listOf(EMBED_URL), embed.requestedUrls)

            // The next lookup: T1 is not asked while its host cools; T2 still reads.
            val again = lookup(PAGE, main(VIDEO_PAGE), embed, ladder = ladder)
            val later = (again.result as MasterResult.Success).result
            assertEquals(line, BEST_FILE, later.candidates.first().mediaUrl)
            assertTrue(later.details.toString(), "$T1: cooling down after a 429 or a bot check; not asked" in later.details)
            assertEquals(line, listOf(EMBED_URL), embed.requestedUrls)
            assertBare(rows.details + later.details)
        }

        // Main's bot check on its phone page in this lookup: T1's host stopped, never asked; T2
        // reads main's desktop page (the verify page is judged, not read for rows).
        val embed = answering(EMBED_URL to EMBED, contentType = HTML)
        val record = record()
        val checked = lookup(PAGE, main(VERIFY, VIDEO_PAGE), embed, record = record)
        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, checked.mainFailure)
        assertTrue(record.stopHosts().toString(), TIKTOK_HOST in record.stopHosts())
        val success = checked.result as MasterResult.Success
        assertEquals(MasterStage.PAGE_DATA, success.stage)
        assertTrue(embed.requestedUrls.isEmpty())
        assertEquals(0, record.masterAsks)
        val details = success.result.details
        assertTrue(details.toString(), "$T1: the host stopped in this lookup (a 429 or a bot check); not asked" in details)
        assertTrue(details.toString(), "$T2: page 1: BOT_CHECK (a verify page)" in details)
        assertTrue(details.toString(), details.any { it.startsWith("$T2: page 2: DIRECT (rows ") })
        assertBare(details)

        // Main's 429 earlier in the lookup (its phone page; its desktop page answered): nothing new
        // goes out (route B's rule); T2 still reads.
        val untouched = answering(EMBED_URL to EMBED, contentType = HTML)
        val limited = record()
        limited.record(
            LookupAsk(LookupAsk.GET, PAGE, null, BROWSER),
            ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429),
            limited.now(),
            LookupRecord.Asker.MAIN,
        )
        val pages = MainPages(site = SITE).apply { note(PAGE, DESKTOP, html(VIDEO_PAGE, PAGE)) }
        val after = withContext(limited + pages) {
            engine(untouched).extract(contractRequest(PAGE, identify(PAGE), SiteExtractionFailure.NO_MEDIA_FOUND, COOKIE))
        }
        assertTrue(untouched.requestedUrls.isEmpty())
        assertEquals(0, limited.masterAsks)
        val afterDetails = (after as MasterResult.Success).result.details
        assertTrue(afterDetails.toString(), "$LADDER after NO_MEDIA_FOUND; replayed 0; new 0; refused 1" in afterDetails)
        assertEquals(BEST_FILE, after.result.candidates.first().mediaUrl)
    }

    @Test
    fun `T2 reads the page with the most rows, never another video's page or one over the bound`() = runTest {
        val reflow = Fixtures.read("tiktok/universal_reflow_video.html")
        val pages = MainPages(maxChars = (reflow.length + VIDEO_PAGE.length).toLong(), site = SITE).apply {
            note(PAGE, BROWSER, html(reflow, PAGE))
            note(PAGE, DESKTOP, html(VIDEO_PAGE, PAGE))
            note(PAGE, DESKTOP, html(OTHER_PAGE, "$BASE$OTHER"))
            note(PAGE, DESKTOP, html(VIDEO_PAGE, PAGE))
            note(PAGE, DESKTOP, html(VIDEO_PAGE, PAGE))
        }
        val embed = FakeExtractorHttpClient(responses = mapOf(EMBED_URL to html(MISSING, EMBED_URL)))

        val result = withContext(record() + pages) {
            engine(embed).extract(contractRequest(PAGE, identify(PAGE), cookie = COOKIE))
        }

        val success = result as MasterResult.Success
        assertEquals(MasterStage.PAGE_DATA, success.stage)
        assertEquals(BEST_FILE, success.result.candidates.first().mediaUrl)
        success.result.candidates.forEach { assertEquals("tiktok:$VIDEO", it.videoId) }
        val details = success.result.details
        assertTrue(details.toString(), "$T2: page 1: DIRECT (rows 1, bound to the item ID)" in details)
        assertTrue(details.toString(), details.any { it.startsWith("$T2: page 2: DIRECT (rows ") })
        assertTrue(details.toString(), "$T2: page 3: another video's page; not read" in details)
        assertTrue(details.toString(), "$T2: page 4: not kept (over the bound)" in details)
        assertTrue(details.toString(), "$T2: 1 more of main's pages not listed; not read" in details)
        assertTrue(details.toString(), "$T2: next: CHECK_ROWS" in details)
        assertBare(details)
    }

    @Test
    fun `S7 - over the lookup's request budget T1 is not asked, T2 still reads`() = runTest {
        val embed = answering(EMBED_URL to EMBED, contentType = HTML)
        val ladder = TikTokLadder(embed, ::identifyOrNull, budget = SocialBudget(requests = 1, bytes = 1L shl 20, millis = 30_000))

        val lookup = lookup(PAGE, main(VIDEO_PAGE), embed, ladder = ladder)

        assertTrue(embed.requestedUrls.isEmpty())
        val success = lookup.result as MasterResult.Success
        assertEquals(MasterStage.PAGE_DATA, success.stage)
        assertTrue(success.result.details.toString(), "$T1: over the lookup's request budget; not asked" in success.result.details)
        assertEquals(20, SocialBudget.TIKTOK.requests)
    }

    @Test
    fun `a short link is read through main's resolved link, else nothing is asked`() = runTest {
        val short = identify(SHORT)
        val pages = MainPages(site = SITE).apply { note(short.canonicalPageUrl, BROWSER, html("<html>main's page</html>", PAGE)) }
        val embed = answering(EMBED_URL to EMBED, contentType = HTML)

        val result = withContext(record() + pages) {
            engine(embed).extract(contractRequest(SHORT, short, cookie = COOKIE))
        }

        val row = (result as MasterResult.Success).result.candidates.single()
        assertEquals(EMBED_FILE, row.mediaUrl)
        assertEquals("tiktok:$VIDEO", row.videoId)
        assertEquals(listOf(EMBED_URL), embed.requestedUrls)
        assertBare(result.result.details)

        // Main read no page for it: nothing is asked, no guess; the tab's own playback.
        val unasked = answering(EMBED_URL to EMBED, contentType = HTML)
        val capture = CountingCapture()
        val answer = withContext(record() + MainPages(site = SITE)) {
            engine(unasked, capture = capture).extract(contractRequest(SHORT, short))
        }
        assertTrue(unasked.requestedUrls.isEmpty())
        assertEquals(1, capture.calls)
        val details = (answer as MasterResult.NeedsPlayback).details
        assertTrue(details.toString(), "$LADDER: a link main resolves first, no video in main's answer; not asked" in details)
        assertTrue(details.toString(), "$T2: no page main read for the video; nothing to read" in details)
    }

    @Test
    fun `without the lookup's record or after a sign-in failure the ladder asks nothing`() = runTest {
        val identity = identify(PAGE)
        // No record (every build but the social-ladder one): R8's ask, exactly as without a ladder.
        val r8Http = answering(EMBED_URL to EMBED, contentType = HTML)
        val r8 = MasterFallbackEngine(
            RecordingValidator(), CountingCapture(), MasterPolicy(enabled = true), contracts = SiteContracts(r8Http),
        ).extract(contractRequest(PAGE, identity)) as MasterResult.Success
        val http = answering(EMBED_URL to EMBED, contentType = HTML)
        val plain = engine(http).extract(contractRequest(PAGE, identity)) as MasterResult.Success
        assertEquals(r8.result.candidates, plain.result.candidates)
        assertEquals(r8.result.details, plain.result.details)
        assertEquals(r8Http.requestedUrls, http.requestedUrls)
        assertFalse(plain.result.details.any { it.startsWith(LADDER) || it.startsWith(T1) || it.startsWith(T2) })

        // Main's sign-in failure: the user's own playback, never an ask (as R8).
        val signIn = answering(EMBED_URL to EMBED, contentType = HTML)
        val record = record()
        val capture = CountingCapture()
        val result = withContext(record + MainPages(site = SITE)) {
            engine(signIn, capture = capture)
                .extract(contractRequest(PAGE, identity, SiteExtractionFailure.LOGIN_REQUIRED))
        }
        assertTrue(signIn.requestedUrls.isEmpty())
        assertEquals(0, record.masterAsks)
        assertEquals(1, capture.calls)
        assertTrue(result.toString(), result is MasterResult.NeedsPlayback)
    }

    @Test
    fun `SocialStages gives each lookup its own site's ladder`() = runTest {
        val watch = "https://www.facebook.com/watch/?v=$FACEBOOK_VIDEO"
        val facebook = checkNotNull(FacebookExtractor(FakeExtractorHttpClient()).identify(watch))
        val plugin = "https://www.facebook.com/plugins/video.php?href=" +
            URLEncoder.encode(facebook.canonicalPageUrl, StandardCharsets.UTF_8).replace("+", "%20")
        val http = FakeExtractorHttpClient(
            responses = mapOf(EMBED_URL to html(EMBED, EMBED_URL), plugin to html("<html>no data</html>", plugin)),
        )
        val stages = SocialStages(
            listOf(FacebookLadder(http, ::anyIdentity), TikTokLadder(http, ::anyIdentity)),
        )
        val engine = MasterFallbackEngine(
            RecordingValidator(), CountingCapture(), MasterPolicy(enabled = true),
            contracts = SiteContracts(http), social = stages,
        )

        val tiktok = withContext(record() + MainPages(site = SITE)) {
            engine.extract(contractRequest(PAGE, identify(PAGE)))
        } as MasterResult.Success
        val onFacebook = withContext(LookupRecord(labels = SiteWays.FACEBOOK.labels()) + MainPages(site = "facebook")) {
            engine.extract(contractRequest(watch, facebook))
        } as MasterResult.NeedsPlayback

        assertTrue(tiktok.result.details.any { it.startsWith(T1) })
        assertFalse(tiktok.result.details.any { it.startsWith("master: facebook") })
        assertTrue(onFacebook.details.any { it.startsWith("master: facebook F1") })
        assertFalse(onFacebook.details.any { it.startsWith(LADDER) || it.startsWith(T1) || it.startsWith(T2) })
        assertEquals(listOf(EMBED_URL, plugin), http.requestedUrls)

        // Another site's lookup: no ladder claims it (R8's path), even inside a record.
        val other = SitePageIdentity("vimeo", "100000001", "https://vimeo.com/100000001")
        withContext(record() + MainPages()) {
            assertFalse(stages.claims(contractRequest(other.canonicalPageUrl, other)))
            assertTrue(stages.claims(contractRequest(PAGE, identify(PAGE))))
        }
        assertFalse(stages.claims(contractRequest(PAGE, identify(PAGE))))
    }

    @Test
    fun `V in a TikTok lookup asks TikTok's media hosts only`() = runTest {
        val files = Files { start() }
        val check = SocialRowCheck(RecordingValidator(), files)
        val tiktokRow = row(TIKTOK_FILE)
        val facebookRow = row("https://video.fixture.fbcdn.net/v/fixture.mp4?oe=F2A52380&oh=fixture")

        val checked = withContext(record() + MainPages(site = SITE)) {
            listOf(check.validate(tiktokRow, NOW), check.validate(facebookRow, NOW))
        }

        assertTrue(checked.first().toString(), checked.first() is ValidationResult.Valid)
        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, (checked.last() as ValidationResult.Rejected).reason)
        assertEquals(listOf(TIKTOK_FILE), files.asked)

        // A lookup that names no site (built before S2b): the check's own site, Facebook's hosts.
        val before = withContext(LookupRecord(labels = SiteWays.FACEBOOK.labels()) + MainPages()) {
            check.validate(tiktokRow, NOW)
        }
        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, (before as ValidationResult.Rejected).reason)
        assertEquals(listOf(TIKTOK_FILE), files.asked)
    }

    @Test
    fun `S4 - T1, T2 and T3 off in the signed config - their lines, nothing asked for them, the next one runs`() = runTest {
        // T1 off: the embed player is never asked; T2 reads main's pages (no request).
        val embed = answering(EMBED_URL to EMBED, contentType = HTML)
        val record = record()
        val t1 = lookup(PAGE, main(VIDEO_PAGE), embed, record = record, ladder = TikTokLadder(embed, ::identifyOrNull, SignedRecipes.rungsOff("T1")))
        val success = t1.result as MasterResult.Success
        assertEquals(MasterStage.PAGE_DATA, success.stage)
        assertEquals(BEST_FILE, success.result.candidates.first().mediaUrl)
        assertTrue(embed.requestedUrls.isEmpty())
        assertEquals(0, record.masterAsks)
        val details = success.result.details
        assertTrue(details.toString(), "$T1: off (config); not run" in details)
        assertTrue(details.toString(), "$T2: next: CHECK_ROWS" in details)
        assertBare(details)

        // T2 off after T1's answer without rows: its line; the tab's own playback next.
        val missing = FakeExtractorHttpClient(responses = mapOf(EMBED_URL to html(MISSING, EMBED_URL)))
        val capture = CountingCapture()
        val t2 = lookup(PAGE, main(VIDEO_PAGE), missing, capture = capture, ladder = TikTokLadder(missing, ::identifyOrNull, SignedRecipes.rungsOff("T2")))
        val t2Details = (t2.result as MasterResult.NeedsPlayback).details
        assertTrue(t2Details.toString(), "$T1: SHAPE_CHANGED (no rows; no status); next: NEXT_RUNG" in t2Details)
        assertTrue(t2Details.toString(), "$T2: off (config); not run" in t2Details)
        assertFalse(t2Details.toString(), t2Details.any { it.startsWith("$T2: page ") })
        assertEquals(listOf(EMBED_URL), missing.requestedUrls)
        assertEquals(1, capture.calls)

        // T3 off too: no capture asked, the ladder's lines end the answer.
        val quiet = FakeExtractorHttpClient(responses = mapOf(EMBED_URL to html(MISSING, EMBED_URL)))
        val noCapture = CountingCapture()
        val t3 = lookup(
            PAGE, main(VIDEO_PAGE), quiet, capture = noCapture,
            ladder = TikTokLadder(quiet, ::identifyOrNull, SignedRecipes.rungsOff("T2", "T3")),
        )
        val t3Details = (t3.result as MasterResult.NeedsPlayback).details
        assertEquals(0, noCapture.calls)
        assertEquals("master: tiktok T3: off (config); not run", t3Details.last())
        assertFalse(t3Details.toString(), t3Details.any { it.startsWith("Open the page") || it == "master: one browser-assisted capture requested" })
        assertBare(t2Details + t3Details)

        // The photo check is no rung: with T1 off a photo post is still final, with no request.
        val photo = FakeExtractorHttpClient()
        val photoResult = withContext(record() + MainPages(site = SITE)) {
            engine(photo, ladder = TikTokLadder(photo, ::identifyOrNull, SignedRecipes.rungsOff("T1", "T3")))
                .extract(contractRequest(PHOTO_LINK, identify(PHOTO_LINK), SiteExtractionFailure.NO_MEDIA_FOUND, COOKIE))
        }
        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, (photoResult as MasterResult.Failure).reason)
        assertTrue(photo.requestedUrls.isEmpty())
    }

    /** One lookup: main's real adapter over [main] (its pages and asks kept in the lookup), then Master's engine. */
    private suspend fun lookup(
        link: String,
        main: FakeExtractorHttpClient,
        embed: FakeExtractorHttpClient,
        record: LookupRecord = record(),
        pages: MainPages = MainPages(site = SITE),
        validator: RecordingValidator = RecordingValidator(),
        capture: CountingCapture = CountingCapture(),
        ladder: TikTokLadder = TikTokLadder(embed, ::identifyOrNull),
    ): Lookup {
        val identity = identify(link)
        return withContext(record + pages) {
            val answer = TikTokExtractor(MainPageRecorder(LookupRecorder(main))).extract(mainRequest(identity, COOKIE))
            val failure = (answer as SiteExtractionResult.Failure).reason
            val result = engine(embed, validator, capture, ladder).extract(contractRequest(link, identity, failure, COOKIE))
            Lookup(failure, result)
        }
    }

    private class Lookup(val mainFailure: SiteExtractionFailure, val result: MasterResult)

    private class Photo(val link: String, val pages: MainPages, val why: String)

    private fun engine(
        http: FakeExtractorHttpClient,
        validator: RecordingValidator = RecordingValidator(),
        capture: CountingCapture = CountingCapture(),
        ladder: TikTokLadder = TikTokLadder(http, ::identifyOrNull),
    ) = MasterFallbackEngine(
        validator, capture, MasterPolicy(enabled = true),
        contracts = SiteContracts(http),
        social = ladder,
    )

    private fun record() = LookupRecord(labels = SiteWays.TIKTOK.labels())

    /** No ID, link, handle or page words in the lines. */
    private fun assertBare(details: List<String>) {
        details.forEach { line ->
            listOf(VIDEO, PHOTO, OTHER, SHORT_CODE, "fixture_user", "tiktok.com", "example-cdn", "Sunrise").forEach {
                assertFalse("$line has $it", line.contains(it))
            }
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
        const val SITE = "tiktok"
        const val VIDEO = "7311234567890123456"
        const val PHOTO = "7311234567890123457"
        const val OTHER = "7311234567890123458"
        const val FACEBOOK_VIDEO = "1000000000000003"
        const val SHORT_CODE = "ZMabcdef1"
        const val BASE = "https://www.tiktok.com/@fixture_user/video/"
        const val PAGE = "$BASE$VIDEO"
        const val PHOTO_LINK = "https://www.tiktok.com/@fixture_user/photo/$PHOTO"
        const val SHORT = "https://vm.tiktok.com/$SHORT_CODE/"
        const val LOGIN = "https://www.tiktok.com/login?redirect_url=fixture"
        const val TIKTOK_HOST = "www.tiktok.com"
        const val EMBED_URL = "https://www.tiktok.com/embed/v2/$VIDEO"
        const val COOKIE = "sessionid=fixture-cookie; tt_chain_token=fixture"
        const val HTML = "text/html"
        const val LADDER = "master: tiktok ladder"
        const val T1 = "master: tiktok T1 embed player"
        const val T2 = "master: tiktok T2 main's pages"
        const val EMBED_FILE =
            "https://v45.example-cdn.test/video/tos/useast5/embed.mp4?mime_type=video_mp4&expire=4102444800"
        const val BEST_FILE = "https://v16-webapp.example-cdn.test/video/play/fixture-1080.mp4?expire=4102444800"
        const val TIKTOK_FILE =
            "https://v16.fixture.tiktokcdn.com/video/tos/useast5/fixture.mp4?mime_type=video_mp4&expire=4102444800"
        const val TOTAL = 32_768L
        const val VERIFY = "<html><head><title>Security Check</title></head><body>Please verify you are human</body></html>"
        val BROWSER = mapOf("User-Agent" to AGENT, "Cookie" to "sessionid=fixture")
        val DESKTOP = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0) fixture", "Cookie" to "sessionid=fixture")

        /** Main's page for the video (main's fixture: three qualities, the play and download addresses). */
        val VIDEO_PAGE: String by lazy { Fixtures.read("tiktok/universal_video.html") }

        /** Main's page shape for another video (main's SIGI fixture). */
        val OTHER_PAGE: String by lazy { Fixtures.read("tiktok/sigi_video.html") }

        /** The embed page's shape (TikTokContractTest's, live 2026-10-10), with fixture addresses. */
        val EMBED = """
            <!DOCTYPE html><html><head><title>TikTok</title></head><body>
            <script id="__FRONTITY_CONNECT_STATE__" type="application/json">{"source":{"data":{"/embed/v2/$VIDEO":{"isFetching":false,"isReady":true,"route":"/embed/v2/$VIDEO/","code":200,"isError":false,"pageName":"video_v2","videoData":{"itemInfos":{"id":"$VIDEO","text":"Sunrise over the harbour","covers":["https://p19-common-sign.example-cdn.test/obj/cover-embed"],"video":{"urls":["$EMBED_FILE"],"videoMeta":{"width":720,"height":1280,"ratio":26,"duration":26}},"secret":false,"forFriend":false},"authorInfos":{"uniqueId":"fixture_user"},"imagePostInfo":null}},"strategy":{"page_context":{"biz_name":"Embed"}}}}}</script>
            </body></html>
        """.trimIndent()

        /** The embed's error shape for a post it does not show (TikTokContractTest's). */
        val MISSING = "<!DOCTYPE html><html><body><script id=\"__FRONTITY_CONNECT_STATE__\" " +
            "type=\"application/json\">{\"source\":{\"data\":{\"/embed/v2/$VIDEO\":{\"isReady\":true," +
            "\"errorCode\":10204,\"errorStatus\":400,\"isError\":true,\"pageName\":\"video_v2_error\"}}}}" +
            "</script></body></html>"

        fun identifyOrNull(url: String): SitePageIdentity? = TikTokExtractor(FakeExtractorHttpClient()).identify(url)

        fun identify(url: String): SitePageIdentity = checkNotNull(identifyOrNull(url)) { url }

        /** The app's adapters' identify, both sites. */
        fun anyIdentity(url: String): SitePageIdentity? =
            identifyOrNull(url) ?: FacebookExtractor(FakeExtractorHttpClient()).identify(url)

        fun html(body: String, finalUrl: String) = ExtractorHttpResult.Success(200, body, finalUrl, HTML)

        /** Main's client: its GETs of the link answered with [bodies] in order (the last one again), every file check refused. */
        fun main(vararg bodies: String): FakeExtractorHttpClient {
            var asked = 0
            return FakeExtractorHttpClient(
                getResponder = { url, _ -> if (url == PAGE) html(bodies[minOf(asked++, bodies.size - 1)], PAGE) else null },
                probeResponder = { _, _ -> ExtractorProbeResult.Refused(SiteExtractionFailure.HTTP_STATUS, 403) },
            )
        }

        /** A row Master offers in a TikTok lookup (an MP4 file unless named). */
        fun row(url: String) = MediaCandidate(
            pageUrl = PAGE,
            mediaUrl = url,
            sources = setOf(CandidateSource.DOM),
            kind = MediaKind.DIRECT,
            requestContext = BrowserRequestContext(PAGE, AGENT, null),
            observedAtEpochMs = NOW,
            pageRole = PageMediaRole.MAIN,
        )

        /** A file's first bytes as its host answers them: an MP4 start of a 32 KiB file. */
        fun start(bytes: ByteArray = InitBytes.mp4()) =
            MediaStart.Read(206, "video/mp4", "bytes 0-${bytes.size - 1}/$TOTAL", bytes.size.toLong(), bytes)
    }
}
