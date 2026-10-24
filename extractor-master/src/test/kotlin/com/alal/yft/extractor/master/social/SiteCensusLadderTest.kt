package com.alal.yft.extractor.master.social

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.SiteAdapterSelection
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asBooleanOrNull
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.master.MasterMediaValidator
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.ValidationResult
import com.alal.yft.extractor.master.contract.SiteContracts
import com.alal.yft.extractor.master.contract.answering
import com.alal.yft.extractor.master.lookup.LookupRecorder
import com.alal.yft.extractor.master.modules.youtube.ladder.InitBytes
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStart
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStartReader
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.master.modules.youtube.testing.Fixtures
import com.alal.yft.extractor.master.recipes.SignedRecipes
import com.alal.yft.extractor.master.social.SiteWaysTest.Companion.BROWSER_AGENT
import com.alal.yft.extractor.sites.facebook.FacebookExtractor
import com.alal.yft.extractor.sites.instagram.InstagramExtractor
import com.alal.yft.extractor.sites.tiktok.TikTokExtractor
import com.alal.yft.extractor.sites.x.XExtractor
import java.net.URI
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 6b (`MASTER_KEY_SOCIAL_PLAN.md` §7.3): the site census's ladder. In a social-ladder build a
 * ladder site's Master parts run the site's pack (its ladder on main's pages from the census's own
 * main lookup, S6's file check, S7's stops and budgets) instead of R8's way; each part names its
 * rungs and compares Master's rows with main's (wrong rows, unknown rows). Without a pack the
 * census is as before. Synthetic fixtures and made-up IDs only.
 */
class SiteCensusLadderTest {
    private var now = NOW_MS
    private val clock = { now }

    @Test
    fun `task 6b - a ladder site's Master parts run its ladder on main's pages, each rung named, its rows compared with main's`() = runTest {
        val links = mapOf("x" to mapOf(1 to X_POST, 2 to X_POST_2, 3 to X_POST_3))
        val http = xWidget(advance = MAIN_MILLIS)
        val files = Files()

        val outcome = census(AppMain(http), http, pack(http, files = files)).run(listOf(SiteWays.X), links, null)

        val site = json(outcome.json)["sites"][0]
        assertEquals(true, site["ladder"].asBooleanOrNull)
        val number = site["number"]
        assertEquals(listOf(3L, 3L, 3L, 3L, 0L, 0L), LADDER_NUMBERS.map { number[it].asLongOrNull })
        val rows = site["rows"].asArrayOrEmpty
        assertEquals(3, rows.size)
        rows.forEach { row ->
            val master = row["master"]
            assertEquals("trial", master["run"].asStringOrNull)
            assertEquals(true, master["ladder"].asBooleanOrNull)
            // The three mains took 24 s, each row's own 8 s: X's 20 s budget holds for every row
            // (the row's record clock stops between main's lookup and its Master part).
            assertEquals(listOf("X1 rows", "X3 not reached"), rungsOf(master))
            assertEquals("main", master["compare"].asStringOrNull)
            assertEquals(true, master["item"].asBooleanOrNull)
            assertEquals("yes", master["same"].asStringOrNull)
            assertEquals("yes", master["duration"].asStringOrNull)
            assertEquals(0L, master["wrong"].asLongOrNull)
            assertEquals(0L, master["unknown"].asLongOrNull)
            assertEquals("pass", master["v"]["verdict"].asStringOrNull)
            // X1 asks nothing (main's answer, kept); S6 reads each file's start once.
            assertEquals(0L, master["replayed"].asLongOrNull)
            assertEquals(master["asks"].asArrayOrEmpty.size.toLong(), master["new"].asLongOrNull)
        }
        assertEquals(3, http.requestedUrls.count { it.startsWith(WIDGET) })
        assertTrue(files.asked.isNotEmpty())
        val lines = outcome.report.lines()
        assertTrue(lines.any { it.startsWith("Ladder (a social-ladder build): ") && it.endsWith("no wrong row is not parity or release readiness.") })
        assertTrue(lines.contains("  ladder: Master parts 3, rows 3, the same item 3, V pass 3, wrong rows 0 (unknown 0)"))
        assertEquals(3, lines.count { it.startsWith("    Master trial (ladder): rows ") })
        val rungs = lines.filter { it.startsWith("      rungs: ") }
        assertEquals(3, rungs.size)
        rungs.forEach { line ->
            assertTrue(line, line.startsWith("      rungs: X1 rows · X3 not reached; Master's rows "))
            assertTrue(line, line.endsWith("; the same item: yes; duration within 1 s: yes; wrong rows 0 (unknown 0)"))
        }

        // Without a pack (every other build): main's parts the same, R8's way, no ladder field or line.
        now = NOW_MS
        val plainHttp = xWidget(advance = MAIN_MILLIS)
        val plain = census(AppMain(plainHttp), plainHttp).run(listOf(SiteWays.X), links, null)
        assertEquals(mainLines(plain.report), mainLines(outcome.report))
        val plainRows = json(plain.json)["sites"][0]["rows"].asArrayOrEmpty
        plainRows.zip(rows).forEach { (before, after) ->
            MAIN_FIELDS.forEach { field -> assertEquals(field, before["main"][field].asStringOrLong(), after["main"][field].asStringOrLong()) }
            assertEquals(before["main"]["asks"].asArrayOrEmpty.size, after["main"]["asks"].asArrayOrEmpty.size)
            assertNull(before["master"]["ladder"].asBooleanOrNull)
        }
        assertFalse(plain.json.contains("\"ladder"))
        assertFalse(plain.json.contains("\"rungs\""))
        assertFalse(plain.report.contains("ladder"))
        assertFalse(plain.report.contains("rungs:"))
    }

    @Test
    fun `task 6b - Master's rows where main has none are wrong for a slot that wants none and unknown for one that downloads`() = runTest {
        val http = xWidget()
        val app = AppMain(http)
        // Main's lookup ran (its pages kept), but its answer is a failure: route B for slot 1, the
        // site's final answer (a trial) for the removed post's slot.
        val failing = object : MainLookup {
            override suspend fun identify(link: String): LinkIdentity? = app.identify(link)

            override suspend fun lookup(link: String, identity: LinkIdentity): MainAnswer {
                app.lookup(link, identity)
                return MainAnswer.Failed(
                    if (link == X_POST_8) SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE else SiteExtractionFailure.RESPONSE_CHANGED,
                )
            }
        }

        val outcome = census(failing, http, pack(http)).run(listOf(SiteWays.X), mapOf("x" to mapOf(1 to X_POST, X_REMOVED to X_POST_8)), null)

        val site = json(outcome.json)["sites"][0]
        val (routeB, removed) = site["rows"].asArrayOrEmpty.map { it["master"] }
        assertEquals("route B", routeB["run"].asStringOrNull)
        assertEquals("trial", removed["run"].asStringOrNull)
        val routeBRows = checkNotNull(routeB["rows"].asLongOrNull)
        val removedRows = checkNotNull(removed["rows"].asLongOrNull)
        assertTrue(routeBRows > 0 && removedRows > 0)
        assertEquals("main none", routeB["compare"].asStringOrNull)
        assertEquals(listOf(0L, routeBRows), listOf(routeB["wrong"].asLongOrNull, routeB["unknown"].asLongOrNull))
        assertEquals("slot wants none", removed["compare"].asStringOrNull)
        assertEquals(listOf(removedRows, 0L), listOf(removed["wrong"].asLongOrNull, removed["unknown"].asLongOrNull))
        assertNull(routeB["same"].asStringOrNull)
        assertEquals(removedRows, site["number"]["ladderWrong"].asLongOrNull)
        assertEquals(routeBRows, site["number"]["ladderUnknown"].asLongOrNull)
        val lines = outcome.report.lines()
        assertTrue(lines.contains("      rungs: X1 rows · X3 not reached; main's rows none: wrong rows 0 (unknown $routeBRows)"))
        assertTrue(lines.contains("      rungs: X1 rows · X3 not reached; main's rows none, the slot wants none: wrong rows $removedRows (unknown 0)"))
        assertTrue(lines.any { it.startsWith("    Master route B (ladder) after RESPONSE_CHANGED: rows ") })
    }

    @Test
    fun `task 6b - the rungs say why they were not asked - a cooling host, the lookup's budget, nothing to read, no tab`() = runTest {
        val http = FakeExtractorHttpClient()
        val links = mapOf("facebook" to mapOf(1 to FB_WATCH))
        val found = MainAnswer.Found(listOf(row(FB_FILE, page = FB_WATCH, key = "facebook:$FB_VIDEO")))
        val cooling = SocialCooldown().apply { note(listOf(FACEBOOK_HOST)) }

        val cooled = census(ScriptedMain { _, _ -> found }, http, pack(http, cooldown = cooling)).run(listOf(SiteWays.FACEBOOK), links, null)
        val recorder = LookupRecorder(http)
        val busy = ScriptedMain { _, identity ->
            repeat(SocialBudget.FACEBOOK.requests) { index -> recorder.get("$FB_PAGE$index", mapOf("User-Agent" to identity.userAgent.orEmpty()), 1_000L) }
            found
        }
        val spent = census(busy, http, pack(http)).run(listOf(SiteWays.FACEBOOK), links, null)

        val cooledMaster = json(cooled.json)["sites"][0]["rows"][0]["master"]
        assertEquals(listOf("F1 not run (cooldown)", "F2 not run (nothing to read)", "F3 not run (no tab)"), rungsOf(cooledMaster))
        assertEquals(0L, cooledMaster["rows"].asLongOrNull)
        assertNull(cooledMaster["compare"].asStringOrNull)
        assertEquals(0L, cooledMaster["wrong"].asLongOrNull)
        assertTrue(cooled.report.lines().contains("      rungs: F1 not run (cooldown) · F2 not run (nothing to read) · F3 not run (no tab)"))
        val spentMaster = json(spent.json)["sites"][0]["rows"][0]["master"]
        assertEquals(listOf("F1 not run (request budget)", "F2 not run (nothing to read)", "F3 not run (no tab)"), rungsOf(spentMaster))
        // Nothing went to Facebook's player: the cooling host and the spent budget are not asked.
        assertFalse(http.requestedUrls.any { it.contains("/plugins/video.php") })
        assertEquals(
            "  ladder: Master parts 1, rows 0, the same video 0, V pass 0, wrong rows 0 (unknown 0)",
            spent.report.lines().first { it.startsWith("  ladder: ") },
        )
    }

    @Test
    fun `task 6b - a short link runs the ladder, which reads the video from main's pages, and names none when main read none`() = runTest {
        val http = FakeExtractorHttpClient()
        val main = ScriptedMain { _, _ -> MainAnswer.Failed(SiteExtractionFailure.RESPONSE_CHANGED) }
        val links = mapOf("tiktok" to mapOf(1 to TT_SHORT))

        val plain = census(main, http).run(listOf(SiteWays.TIKTOK), links, null)
        val ladder = census(main, http, pack(http)).run(listOf(SiteWays.TIKTOK), links, null)

        assertEquals("short link", json(plain.json)["sites"][0]["rows"][0]["master"]["why"].asStringOrNull)
        val master = json(ladder.json)["sites"][0]["rows"][0]["master"]
        assertEquals("route B", master["run"].asStringOrNull)
        assertEquals(listOf("T1 not run (no video)", "T2 not run (nothing to read)", "T3 not run (no tab)"), rungsOf(master))
        assertTrue(http.requestedUrls.isEmpty())
    }

    @Test
    fun `task 6b - each row is judged on its own - another video, item or length is wrong, what is not proven is unknown`() {
        val facebook = "facebook:$FB_VIDEO"
        val mains = listOf(row(FB_FILE, page = FB_WATCH, key = facebook, millis = 31_533))
        val compared = TrialCompare.compare(
            listOf(
                row(FB_FILE, page = FB_WATCH, key = facebook, millis = 31_900),
                row(FB_FILE, page = FB_WATCH, key = facebook, millis = 45_000),
                row(FB_FILE, page = FB_WATCH, key = "facebook:1000000000000099"),
                row(FB_FILE, page = FB_WATCH, key = null, millis = 31_533),
                row(FB_FILE, page = FB_WATCH, key = facebook),
            ),
            mains,
        ) { it }
        assertEquals(
            listOf(MATCH, WRONG, WRONG, UNKNOWN, MATCH),
            compared.verdicts,
        )
        assertEquals(listOf(2, 1), listOf(compared.wrong, compared.unknown))
        assertEquals("Master's rows 5, main's 1; the same video: no; duration within 1 s: no; heights 720 vs 720", compared.trialText())
        // Task 16 (F6): Facebook's line gives each side's heights, each once, tallest first (Master's,
        // then main's); a side whose rows state none: unknown.
        val sized = TrialCompare.compare(
            listOf(
                row(FB_FILE, page = FB_WATCH, key = facebook, height = 360),
                row(FB_FILE, page = FB_WATCH, key = facebook),
                row(FB_FILE, page = FB_WATCH, key = facebook, height = 0),
            ),
            mains + row(FB_FILE, page = FB_WATCH, key = facebook, height = 360) + row(FB_FILE, page = FB_WATCH, key = facebook),
        ) { it }
        assertTrue(sized.trialText(), sized.trialText().endsWith("; duration within 1 s: unknown; heights 720,360 vs 720,360"))
        val unsized = TrialCompare.compare(listOf(row(FB_FILE, page = FB_WATCH, key = facebook, height = 0)), mains) { it }
        assertTrue(unsized.trialText(), unsized.trialText().endsWith("; heights not stated vs 720"))

        // X: the item; a file on another host names none.
        val x = "x:1785999999999999999"
        val xMains = listOf(row(xFile(1), key = x, millis = 31_533))
        val xCompared = TrialCompare.compare(
            listOf(row(xFile(1), key = x, millis = 31_533), row(xFile(2), key = x), row(OTHER_FILE, key = x)),
            xMains,
        ) { it }
        assertEquals(listOf(MATCH, WRONG, UNKNOWN), xCompared.verdicts)
        assertEquals("Master's rows 3, main's 1; the same item: no; duration within 1 s: yes", xCompared.trialText())

        // Instagram: the 360p file main drops shares no file: "the same item: no", but unknown, not wrong.
        val instagram = "instagram:fixtureCode1"
        val igMains = listOf(row(IG_FILE, key = instagram, height = 1080))
        val alone = TrialCompare.compare(listOf(row(IG_360, key = instagram, height = 360)), igMains) { it }
        assertEquals(listOf(UNKNOWN), alone.verdicts)
        assertEquals("Master's rows 1, main's 1; the same item: no; duration within 1 s: unknown; tallest: Master's 360, main's 1080", alone.trialText())
        val shared = TrialCompare.compare(
            listOf(row(IG_FILE, key = instagram, height = 1080), row(IG_360, key = instagram, height = 360), row(IG_FILE, key = "instagram:fixtureCode2")),
            igMains,
        ) { it }
        assertEquals(listOf(MATCH, UNKNOWN, WRONG), shared.verdicts)
        assertTrue(shared.trialText().contains("the same item: no"))

        // Main's rows naming no one video prove nothing for a row naming one of them; a length far
        // from every one of main's, or a video none of theirs names, still proves another post's.
        val twoVideos = mains + row(FB_FILE, page = FB_WATCH, key = "facebook:1000000000000099", millis = 31_533)
        val none = TrialCompare.compare(
            listOf(
                row(FB_FILE, key = facebook, millis = 31_533),
                row(FB_FILE, key = facebook, millis = 60_000),
                row(FB_FILE, key = "facebook:1000000000000077", millis = 31_533),
            ),
            twoVideos,
        ) { it }
        assertEquals(listOf(UNKNOWN, WRONG, WRONG), none.verdicts)
        // X: main's files of two items; a row of a third is wrong, one of theirs unknown.
        val twoItems = TrialCompare.compare(
            listOf(row(xFile(1), key = x), row(xFile(3), key = x)),
            listOf(row(xFile(1), key = x), row(xFile(2), key = x)),
        ) { it }
        assertEquals(listOf(UNKNOWN, WRONG), twoItems.verdicts)
        // The lookup's resolved video: main's rows carry the link's key.
        val resolved = TrialCompare.compare(listOf(row(FB_FILE, key = facebook)), listOf(row(FB_FILE, key = "facebook:shareLink"))) { id ->
            if (id == "facebook:shareLink") facebook else id
        }
        assertEquals(listOf(MATCH), resolved.verdicts)
        assertTrue(resolved.same)
    }

    @Test
    fun `task 6b - each rung's state comes from the ladder's own lines`() {
        fun failed(vararg lines: String) = MasterResult.Failure(SiteExtractionFailure.NO_MEDIA_FOUND, lines.toList())
        fun texts(site: String, result: MasterResult?) = LadderRungs.of(site, result)?.map { it.text() }

        val facebook = failed(
            "${FacebookLadder.LINE} after NO_MEDIA_FOUND; replayed 0; new 1; refused 2",
            "${FacebookLadder.F1}: NEEDS_PLAYBACK (the player names the video (no file)); next: PLAYBACK",
            "${FacebookLadder.F2}: page 1 (main's public page): no row",
            "${FacebookLadder.F2}: next: PLAYBACK",
            CAPTURE,
        )
        assertEquals(listOf("F1 run (NEEDS_PLAYBACK)", "F2 run (PLAYBACK)", "F3 not run (no tab)"), texts("facebook", facebook))
        assertEquals(2, LadderRungs.refusedOf("facebook", facebook))
        assertEquals(
            listOf("F1 not run (stop)", "F2 rows", "F3 not reached"),
            texts(
                "facebook",
                failed(
                    "${FacebookLadder.F1}: the host stopped in this lookup (a bot check); not asked",
                    "${FacebookLadder.F2}: next: CHECK_ROWS",
                ),
            ),
        )
        assertEquals(
            listOf("F1 run (no answer in time)", "F2 not run (nothing to read)", "F3 not reached"),
            texts(
                "facebook",
                failed(
                    "${FacebookLadder.LINE} after NO_MEDIA_FOUND; replayed 0; new 1",
                    "${FacebookLadder.F1}: no answer in time",
                    "${FacebookLadder.F2}: no page main read for the video; nothing to read",
                ),
            ),
        )
        assertEquals(
            listOf("T1 not run (final)", "T2 not reached", "T3 not reached"),
            texts("tiktok", failed("${TikTokLadder.LINE}: PHOTO_ONLY (main's pages: photos); next: FINAL; not asked")),
        )
        assertEquals(
            listOf("T1 not run (time budget)", "T2 rows", "T3 not reached"),
            texts("tiktok", failed("${TikTokLadder.T1}: over the lookup's time budget; not asked", "${TikTokLadder.T2}: next: CHECK_ROWS")),
        )
        assertEquals(
            listOf("X1 rows not checked (byte budget)", "X3 not run (no tab)"),
            texts(
                "x",
                failed(
                    "${XLadder.LINE} after NO_MEDIA_FOUND; main's answer replayed, no new ask",
                    "master: x X1 item: video 1 of 1",
                    "${XLadder.X1}: VIDEO (the widget's answer names the post's video); next: CHECK_ROWS",
                    "${XLadder.X1}: over the lookup's byte budget; not checked",
                    CAPTURE,
                ),
            ),
        )
        assertEquals(
            listOf("I2 not run (nothing to read)", "I3 not run (no tab)"),
            texts(
                "instagram",
                failed(
                    "${InstagramLadder.LINE} after NO_MEDIA_FOUND; main's answers read, no new ask",
                    "${InstagramLadder.I2} main's GraphQL: not kept (over the bound)",
                    "${InstagramLadder.I2}: none kept for the post; nothing to read",
                    CAPTURE,
                ),
            ),
        )
        assertEquals(
            listOf("I2 run (VALUE_REJECTED)", "I3 not run (no tab)"),
            texts(
                "instagram",
                failed(
                    "${InstagramLadder.I2} main's GraphQL: VALUE_REJECTED (an empty answer)",
                    "${InstagramLadder.I2}: VALUE_REJECTED (GraphQL: an empty answer); next: PLAYBACK",
                    CAPTURE,
                ),
            ),
        )
        assertEquals(listOf("X1 unknown (time limit)", "X3 unknown (time limit)"), texts("x", failed("master: bounded lookup timed out")))
        assertEquals(listOf("X1 unknown (error)", "X3 unknown (error)"), texts("x", null))
        assertEquals(listOf("X1 not reached", "X3 not reached"), texts("x", MasterResult.Skipped(SiteExtractionFailure.NO_MEDIA_FOUND)))
        assertNull(texts("vimeo", facebook))
    }

    @Test
    fun `task 6b - the diff does not compare Master's parts across builds, and a ladder part's first wrong row is BREAKING`() {
        val plain = export(ladder = false)
        val clean = export(ladder = true)
        val wrong = export(ladder = true, same = "no", duration = "no", wrong = 2)

        val across = SiteCensusDiff.compare(plain, wrong)
        assertTrue(across.notes.contains("X: Master's parts not compared (the ladder in the later run only)"))
        assertTrue(across.lines.none { it.text.contains("Master") })
        assertTrue(SiteCensusDiff.compare(wrong, plain).notes.contains("X: Master's parts not compared (the ladder in the earlier run only)"))

        val worse = SiteCensusDiff.compare(clean, wrong)
        assertEquals(
            listOf(
                "BREAKING X slot 1 (video): Master trial: rows 2, V pass, same yes, duration yes, wrong 0 (unknown 0) → " +
                    "trial: rows 2, V pass, same no, duration no, wrong 2 (unknown 0)",
            ),
            worse.lines.map { it.render() },
        )
        assertEquals(1, SiteCensusDiff.compare(wrong, clean).lines.count { it.level == SiteCensusDiff.Level.CHANGED })
        assertTrue(SiteCensusDiff.compare(clean, clean).lines.isEmpty())
        // An older export (no ladder field) against another reads as before.
        assertTrue(SiteCensusDiff.compare(plain, plain).let { it.lines.isEmpty() && it.notes.isEmpty() })
    }

    @Test
    fun `task 9 - S6 refuses another site's file before any ask - the Master part counts it refused, not new, never NETWORK`() = runTest {
        val links = mapOf("tiktok" to mapOf(1 to TT_POST))
        val failed = ScriptedMain { _, _ -> MainAnswer.Failed(SiteExtractionFailure.RESPONSE_CHANGED) }
        // T1's embed answer names Facebook's file in a TikTok lookup.
        val http = answering(TT_EMBED_URL to tiktokEmbed(FB_FILE_OE), contentType = "text/html")
        val files = Files()

        val outcome = census(failed, http, pack(http, files = files)).run(listOf(SiteWays.TIKTOK), links, null)

        val master = json(outcome.json)["sites"][0]["rows"][0]["master"]
        assertEquals("route B", master["run"].asStringOrNull)
        assertEquals(true, master["ladder"].asBooleanOrNull)
        assertEquals(0L, master["rows"].asLongOrNull)
        assertTrue(master.toString(), master["reason"].asStringOrNull != SiteExtractionFailure.NETWORK.name)
        // T1's embed GET is the part's one new ask; S6 sent nothing for the file and counts it refused.
        assertEquals(listOf(1L, 1L), listOf(master["new"].asLongOrNull, master["refused"].asLongOrNull))
        assertEquals(listOf(SiteWays.EMBED_PLAYER), master["asks"].asArrayOrEmpty.map { it["way"].asStringOrNull })
        assertEquals(listOf(TT_EMBED_URL), http.requestedUrls)
        assertTrue(files.asked.isEmpty())
        val line = outcome.report.lines().first { it.startsWith("    Master route B (ladder) after RESPONSE_CHANGED: ") }
        assertTrue(line, line.contains("; replayed 0, new 1, refused 1, checks "))
        assertFalse(outcome.report, outcome.report.lines().any { it.contains(" GET NETWORK ") })

        // The same lookup with the file on TikTok's own host: S6 reads it once (new 2), none refused.
        now = NOW_MS
        val own = answering(TT_EMBED_URL to tiktokEmbed(TT_FILE), contentType = "text/html")
        val ownFiles = Files()
        val read = census(failed, own, pack(own, files = ownFiles)).run(listOf(SiteWays.TIKTOK), links, null)
        val readMaster = json(read.json)["sites"][0]["rows"][0]["master"]
        assertEquals(listOf(2L, 0L), listOf(readMaster["new"].asLongOrNull, readMaster["refused"].asLongOrNull))
        assertEquals(listOf(TT_FILE), ownFiles.asked)
        assertEquals(1L, readMaster["rows"].asLongOrNull)
    }

    @Test
    fun `S4 - a rung the signed config turns off reads not run (config), a capture rung too`() {
        fun answer(vararg lines: String) = MasterResult.NeedsPlayback(lines.toList())
        fun texts(site: String, result: MasterResult?) = LadderRungs.of(site, result)?.map { it.text() }

        assertEquals(
            listOf("F1 not run (config)", "F2 not run (config)", "F3 not run (config)"),
            texts("facebook", answer(RungsOff.line(FacebookLadder.F1), RungsOff.line(FacebookLadder.F2), RungsOff.line(FacebookLadder.F3))),
        )
        assertEquals(
            listOf("F1 not run (config)", "F2 run (PLAYBACK)", "F3 not run (no tab)"),
            texts("facebook", answer(RungsOff.line(FacebookLadder.F1), "${FacebookLadder.F2}: next: PLAYBACK", CAPTURE)),
        )
        assertEquals(
            listOf("T1 not run (config)", "T2 rows", "T3 not reached"),
            texts("tiktok", answer(RungsOff.line(TikTokLadder.T1), "${TikTokLadder.T2}: next: CHECK_ROWS")),
        )
        assertEquals(
            listOf("X1 not run (config)", "X3 not run (config)"),
            texts("x", answer(RungsOff.line(XLadder.X1), RungsOff.line(XLadder.X3))),
        )
        assertEquals(
            listOf("I2 not run (config)", "I3 not run (no tab)"),
            texts("instagram", answer(RungsOff.line(InstagramLadder.I2), CAPTURE)),
        )
        // Another site's capture rung off is not this site's.
        assertEquals(listOf("X1 not run (config)", "X3 not reached"), texts("x", answer(RungsOff.line(XLadder.X1), RungsOff.line(TikTokLadder.T3))))
    }

    @Test
    fun `S4 - the census reads with the pack's recipes - a rung off is not run there, nothing asked for it`() = runTest {
        val links = mapOf("x" to mapOf(1 to X_POST))
        val http = xWidget()

        val outcome = census(AppMain(http), http, pack(http, contractsOf = SignedRecipes.rungsOff("X1")))
            .run(listOf(SiteWays.X), links, null, listOf("recipes: remote version 1, signed"))

        val master = json(outcome.json)["sites"][0]["rows"][0]["master"]
        assertEquals(listOf("X1 not run (config)", "X3 not run (no tab)"), rungsOf(master))
        assertEquals(0L, master["new"].asLongOrNull)
        assertEquals(1, http.requestedUrls.count { it.startsWith(WIDGET) })
        assertTrue(outcome.report, outcome.report.lines().any { it.startsWith("      rungs: X1 not run (config) · X3 not run (no tab)") })
        assertTrue(outcome.report, "recipes: remote version 1, signed" in outcome.report)
    }

    private fun census(main: MainLookup, http: ExtractorHttpClient, pack: SiteCensus.Pack? = null) =
        SiteCensus(main, http, VALID, SiteCensus.Meter.NONE, clock, pack = pack)

    /** The pack as a social-ladder build makes it, over Master's client [http]; S6 reads [files]. */
    private fun pack(
        http: ExtractorHttpClient,
        cooldown: SocialCooldown = SocialCooldown(),
        files: Files = Files(),
        contractsOf: (ExtractorHttpClient) -> SiteContracts = ::SiteContracts,
    ) = SiteCensus.Pack(
        stage = SocialStages(
            listOf(
                FacebookLadder(http, ::identityOf, contractsOf, cooldown = cooldown),
                TikTokLadder(http, ::identityOf, contractsOf, cooldown = cooldown),
                XLadder(http, ::identityOf, contractsOf, cooldown = cooldown),
                InstagramLadder(http, contractsOf, cooldown = cooldown),
            ),
        ),
        validator = { validator -> SocialRowCheck(validator, files) },
        identify = ::identityOf,
        sites = LadderRungs.SITES,
        cooldown = cooldown,
        contracts = contractsOf(http),
    )

    /** X's widget answering the video post's fixture for the post asked (its ID in the answer); each answer takes [advance] ms. */
    private fun xWidget(advance: Long = 0) = FakeExtractorHttpClient(
        getResponder = { url, _ ->
            if (url.startsWith(WIDGET)) {
                now += advance
                val id = URI(url).rawQuery.orEmpty().split('&').first { it.startsWith("id=") }.removePrefix("id=")
                FakeExtractorHttpClient.json(Fixtures.read("x/tweet_video.json").replace(FIXTURE_POST, id), url)
            } else {
                null
            }
        },
        probeResponder = { _, _ -> ExtractorProbeResult.Answered(206, 1_000L) },
    )

    private fun json(text: String): JsonValue = checkNotNull(BoundedJsonParser.parse(text)) { "not JSON: $text" }

    private fun rungsOf(master: JsonValue?): List<String> = master["rungs"].asArrayOrEmpty.map { rung ->
        "${rung["rung"].asStringOrNull} ${rung["state"].asStringOrNull}" + (rung["why"].asStringOrNull?.let { " ($it)" } ?: "")
    }

    private fun mainLines(report: String): List<String> = report.lines().filter { it.startsWith("    main: ") }

    private fun JsonValue?.asStringOrLong(): String? = asStringOrNull ?: asLongOrNull?.toString()

    /** An export of one X row (a trial with 2 rows, V pass), Master's part the ladder's when [ladder]. */
    private fun export(ladder: Boolean, same: String = "yes", duration: String = "yes", wrong: Int = 0): String {
        val fields = if (ladder) ",\"ladder\":true,\"compare\":\"main\",\"item\":true,\"same\":\"$same\",\"duration\":\"$duration\",\"wrong\":$wrong,\"unknown\":0" else ""
        return "{\"tool\":\"${SiteCensus.TOOL_LABEL}\",\"fmt\":2,\"date\":\"2026-10-22\",\"sites\":[{\"site\":\"x\",\"name\":\"X\",\"rows\":[" +
            "{\"slot\":1,\"kind\":\"video\",\"session\":false,\"class\":\"rows\",\"expected\":true," +
            "\"main\":{\"answer\":\"rows\",\"rows\":3,\"tallest\":1280},\"v\":{\"verdict\":\"pass\"}," +
            "\"master\":{\"run\":\"trial\",\"answer\":\"rows\",\"rows\":2,\"v\":{\"verdict\":\"pass\"}$fields}}]" +
            (if (ladder) ",\"ladder\":true" else "") + "}]}"
    }

    /** Main as the app builds it in a social-ladder build: the four adapters over a recorder keeping main's pages. */
    private class AppMain(http: ExtractorHttpClient) : MainLookup {
        private val recorder = MainPageRecorder(LookupRecorder(http))
        private val registry = SiteExtractorRegistry(
            listOf(FacebookExtractor(recorder), TikTokExtractor(recorder), InstagramExtractor(recorder), XExtractor(recorder)),
        )

        override suspend fun identify(link: String): LinkIdentity? =
            (registry.select(link) as? SiteAdapterSelection.Matched)?.identity?.let { LinkIdentity(it, BROWSER_AGENT, null) }

        override suspend fun lookup(link: String, identity: LinkIdentity): MainAnswer {
            val selected = registry.select(link) as? SiteAdapterSelection.Matched ?: return MainAnswer.NotHandled
            val request = SiteExtractionRequest(
                identity.page,
                BrowserRequestContext(identity.page.canonicalPageUrl, identity.userAgent, identity.cookie),
                NOW_MS,
            )
            // As the app's coordinator: a row without a key carries the link's.
            val key = "${selected.extractor.id}:${selected.identity.contentId}"
            return when (val result = selected.extractor.extract(request)) {
                is SiteExtractionResult.Success -> MainAnswer.Found(
                    result.candidates.map { if (it.videoId != null) it else it.copy(videoId = key) },
                    item = PostItem.of(result.details),
                )
                is SiteExtractionResult.Failure -> MainAnswer.Failed(result.reason, result.httpStatusCode)
            }
        }
    }

    /** Main as a script: main's own identities, the browser's agent, [answer]'s answer. */
    private class ScriptedMain(private val answer: suspend (link: String, identity: LinkIdentity) -> MainAnswer) : MainLookup {
        override suspend fun identify(link: String): LinkIdentity? = identityOf(link)?.let { LinkIdentity(it, BROWSER_AGENT, null) }

        override suspend fun lookup(link: String, identity: LinkIdentity): MainAnswer = answer(link, identity)
    }

    /** S6's file reads: an MP4 start for every file; the addresses asked. */
    private class Files : MediaStartReader {
        val asked = mutableListOf<String>()

        override suspend fun read(url: String, headers: Map<String, String>, maxBytes: Int): MediaStart {
            asked += url
            val bytes = InitBytes.mp4()
            return MediaStart.Read(206, "video/mp4", "bytes 0-${bytes.size - 1}/$TOTAL", bytes.size.toLong(), bytes)
        }
    }

    private companion object {
        const val NOW_MS = 1_791_000_000_000L
        const val MAIN_MILLIS = 8_000L
        const val TOTAL = 32_768L
        const val WIDGET = "https://cdn.syndication.twimg.com/tweet-result"
        const val FIXTURE_POST = "1785999999999999999"
        const val X_POST = "https://x.com/fixture_user/status/1785999999999999999"
        const val X_POST_2 = "https://x.com/fixture_user/status/1785999999999999998"
        const val X_POST_3 = "https://x.com/fixture_user/status/1785999999999999997"
        const val X_POST_8 = "https://x.com/fixture_user/status/1785999999999999992"
        const val X_REMOVED = 8
        const val TT_SHORT = "https://vm.tiktok.com/ZMfixture1/"
        const val TT_VIDEO = "7311234567890123456"
        const val TT_POST = "https://www.tiktok.com/@fixture_user/video/$TT_VIDEO"
        const val TT_EMBED_URL = "https://www.tiktok.com/embed/v2/$TT_VIDEO"
        const val TT_FILE = "https://v16m.tiktokcdn.com/video/tos/useast5/fixture-embed.mp4?mime_type=video_mp4&expire=4102444800"
        const val FB_FILE_OE = "https://video.fixture.fbcdn.net/v/t42/fixture-embed.mp4?oe=F2A52380&oh=fixture"
        const val FB_VIDEO = "1000000000000012"
        const val FB_WATCH = "https://www.facebook.com/watch/?v=$FB_VIDEO"
        const val FB_PAGE = "https://www.facebook.com/fixture/page/"
        const val FB_FILE = "https://video.fixture.fbcdn.net/v/t42/fixture.mp4?oh=fixture"
        const val FACEBOOK_HOST = "www.facebook.com"
        const val OTHER_FILE = "https://v16m.example-cdn.test/hd/fixture.mp4"
        const val IG_FILE = "https://scontent.cdninstagram.com/o1/v/t16/f2/m86/fixture1080.mp4?efg=fixture"
        const val IG_360 = "https://scontent.cdninstagram.com/o1/v/t16/f2/m86/fixture360.mp4?efg=fixture"
        const val CAPTURE = "master: one browser-assisted capture requested"
        val MATCH = TrialCompare.Verdict.MATCH
        val WRONG = TrialCompare.Verdict.WRONG
        val UNKNOWN = TrialCompare.Verdict.UNKNOWN
        val LADDER_NUMBERS = listOf("ladderParts", "ladderRows", "ladderSame", "ladderPass", "ladderWrong", "ladderUnknown")
        val MAIN_FIELDS = listOf("answer", "reason", "rows", "tallest", "bestS", "item", "items", "ms", "repeated", "repeats", "variants")
        val VALID = MasterMediaValidator { candidate, _ -> ValidationResult.Valid(candidate) }

        private val ADAPTERS = FakeExtractorHttpClient().let { none ->
            SiteExtractorRegistry(listOf(FacebookExtractor(none), TikTokExtractor(none), InstagramExtractor(none), XExtractor(none)))
        }

        fun identityOf(link: String): SitePageIdentity? = (ADAPTERS.select(link) as? SiteAdapterSelection.Matched)?.identity

        fun xFile(item: Int) = "https://video.twimg.com/ext_tw_video/178599999000000000$item/pu/vid/avc1/720x1280/a720.mp4?tag=12"

        /** TikTok's embed page (TikTokLadderTest's shape) for [TT_VIDEO], its one file [file]. */
        fun tiktokEmbed(file: String) = """
            <!DOCTYPE html><html><head><title>TikTok</title></head><body>
            <script id="__FRONTITY_CONNECT_STATE__" type="application/json">{"source":{"data":{"/embed/v2/$TT_VIDEO":{"isFetching":false,"isReady":true,"route":"/embed/v2/$TT_VIDEO/","code":200,"isError":false,"pageName":"video_v2","videoData":{"itemInfos":{"id":"$TT_VIDEO","text":"Sunrise over the harbour","covers":["https://p19-common-sign.example-cdn.test/obj/cover-embed"],"video":{"urls":["$file"],"videoMeta":{"width":720,"height":1280,"ratio":26,"duration":26}},"secret":false,"forFriend":false},"authorInfos":{"uniqueId":"fixture_user"},"imagePostInfo":null}},"strategy":{"page_context":{"biz_name":"Embed"}}}}}</script>
            </body></html>
        """.trimIndent()

        fun row(url: String, page: String = X_POST, key: String?, millis: Long? = null, height: Int = 720) = MediaCandidate(
            pageUrl = page,
            mediaUrl = url,
            sources = setOf(CandidateSource.MANIFEST),
            kind = MediaKind.DIRECT,
            mimeType = "video/mp4",
            requestContext = BrowserRequestContext(page, BROWSER_AGENT, null),
            height = height,
            videoId = key,
            durationMillis = millis,
        )
    }
}
