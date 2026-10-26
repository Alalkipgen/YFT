package com.alal.yft.extractor.master.social

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.SiteAdapterSelection
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.master.MasterMediaValidator
import com.alal.yft.extractor.master.ValidationResult
import com.alal.yft.extractor.master.lookup.LookupRecorder
import com.alal.yft.extractor.master.modules.youtube.ladder.ClientCensus
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.master.modules.youtube.testing.Fixtures
import com.alal.yft.extractor.master.social.SiteCensusDiff.Level
import com.alal.yft.extractor.master.social.SiteWaysTest.Companion.BROWSER_AGENT
import com.alal.yft.extractor.master.social.SiteWaysTest.Companion.IG_REEL
import com.alal.yft.extractor.master.social.SiteWeekly.Step
import com.alal.yft.extractor.sites.facebook.FacebookExtractor
import com.alal.yft.extractor.sites.instagram.InstagramExtractor
import com.alal.yft.extractor.sites.tiktok.TikTokExtractor
import com.alal.yft.extractor.sites.x.XExtractor
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Social plan S3 (task 8): the census's weekly check W ([SiteWeekly], `SiteCensus.run` with
 * [ClientCensus.Mode.WEEKLY]) over fake sites: its slots only, Master's part on slot 1 only, each
 * way once, its caps (requests, bytes, the time: each checked before every step, the time
 * bounding each step), the census's stops and cooldowns ("not run", never waited out or passed),
 * its own baselines, its header and first line, the diff's lines one per change with the runbook
 * step each names, and the runbook's entry for every step. Made-up links and IDs; no live calls.
 */
class SiteWeeklyTest {
    private var now = NOW_MS
    private val clock = { now }

    @Test
    fun `W runs its three slots, Master's part on slot 1 only, each way once, and keeps its own baseline`() = runTest {
        val http = xSite()
        val steps = mutableListOf<String>()
        val links = X_LINKS + mapOf("instagram" to mapOf(2 to IG_REEL))

        val outcome = census(MAIN, http).run(
            listOf(SiteWays.X, SiteWays.INSTAGRAM),
            links,
            null,
            mode = ClientCensus.Mode.WEEKLY,
            onStep = steps::add,
        )

        val export = json(outcome.json)
        assertEquals("a W run's export says so", "weekly", export["mode"].asStringOrNull)
        val x = export["sites"][0]
        val rows = x["rows"].asArrayOrEmpty
        assertEquals("W's slots only (1, 6, 8; not 2 or 3)", listOf(1L, 6L, 8L), rows.map { it["slot"].asLongOrNull })
        assertEquals(listOf("trial", "not run", "not run"), rows.map { it["master"]["run"].asStringOrNull })
        assertEquals(listOf(null, SiteWeekly.MAIN_ONLY, SiteWeekly.MAIN_ONLY), rows.map { it["master"]["why"].asStringOrNull })
        assertEquals(listOf("rows", "rows", "final"), rows.map { it["class"].asStringOrNull })
        assertEquals(
            listOf("X slot 1: main", "X slot 1: V", "X slot 6: main", "X slot 6: V", "X slot 8: main", "X slot 1: Master", "X slot 1: V"),
            steps,
        )
        assertEquals("Master's way once: the widget, on slot 1", 1, http.requestedUrls.size)
        assertEquals("V once on each best row: main's slots 1 and 6, Master's slot 1", 3, http.probedUrls.size)
        assertEquals(SiteWeekly.NO_LINKS, export["sites"][1]["skipped"].asStringOrNull)
        assertEquals(
            listOf("W slot 1 (single video): no link, not run", "W slot 8 (photo-only): no link, not run", "W slot 10 (removed): no link, not run"),
            export["sites"][1]["notes"].asArrayOrEmpty.map { it.asStringOrNull },
        )

        assertEquals("W (weekly check): FIRST RUN — nothing to compare yet · Instagram not run (no link in W's slots)", outcome.firstLine)
        val lines = outcome.report.lines()
        assertEquals(
            "Weekly check W (S3): per site at most 60 s, 2.0 MB and 24 requests, each checked before every step " +
                "(the time bounds each step too); each way once; Master's part on slot 1 only.",
            lines[0],
        )
        assertEquals("W's slots: Facebook 1, 8, 13 · TikTok 1, 6, 9 · Instagram 1, 8, 10 · X 1, 6, 8 · VK 1, 4, 8", lines[1])
        assertEquals("X: ran, ${x["req"].asLongOrNull} requests, bytes not measured, 0 s", lines[2])
        assertEquals("Instagram: not run (no link in W's slots)", lines[3])
        assertTrue(lines[4], lines[4].startsWith("A part not run is never a pass; each CHANGED or BREAKING line names its W runbook step"))
        assertTrue("the census's report follows", lines[6].startsWith("${SiteCensus.TOOL_LABEL} — "))
        assertTrue(lines.toString(), lines.contains("Diff: FIRST RUN — nothing to compare yet"))
        val state = json(outcome.stateJson)
        assertEquals("W's own baseline", listOf("x"), state["weekly"].asArrayOrEmpty.map { it["site"].asStringOrNull })
        assertEquals("not the census's", 0, state["baselines"].asArrayOrEmpty.size)

        now += MINUTE
        val again = census(MAIN, http).run(listOf(SiteWays.X), X_LINKS, outcome.stateJson, mode = ClientCensus.Mode.WEEKLY)
        assertEquals("W (weekly check): NO CHANGE — against the run of ${SiteCensusJson.dateOf(NOW_MS)}", again.firstLine)

        now += MINUTE
        val full = census(MAIN, http).run(listOf(SiteWays.X), X_LINKS, again.stateJson)
        assertTrue("the census never compares with W's baseline", full.report.lines().contains("Diff: FIRST RUN — nothing to compare yet"))
        assertFalse(full.report, full.report.startsWith("Weekly check W"))
        assertNull("the census's export as before", json(full.json)["mode"])
        assertEquals("every slot with a link, as before", 5, json(full.json)["sites"][0]["rows"].asArrayOrEmpty.size)
        val both = json(full.stateJson)
        assertEquals(listOf("x"), both["baselines"].asArrayOrEmpty.map { it["site"].asStringOrNull })
        assertEquals("W's baseline kept by the census's run", listOf("x"), both["weekly"].asArrayOrEmpty.map { it["site"].asStringOrNull })

        now += MINUTE
        val third = census(MAIN, http).run(listOf(SiteWays.X), X_LINKS, full.stateJson, mode = ClientCensus.Mode.WEEKLY)
        assertEquals(
            "W against its own baseline, not the census's later run",
            "W (weekly check): NO CHANGE — against the run of ${SiteCensusJson.dateOf(NOW_MS + MINUTE)}",
            third.firstLine,
        )
    }

    @Test
    fun `a census-only state and export hold nothing of W, and the A check is not the census's`() = runTest {
        val http = xSite()

        val outcome = census(MAIN, http).run(listOf(SiteWays.X), X_LINKS, null)

        assertNull(json(outcome.json)["mode"])
        assertNull("no weekly list until W ran", json(outcome.stateJson)["weekly"])
        assertFalse(outcome.stateJson, "weekly" in outcome.stateJson)
        assertTrue(outcome.firstLine, outcome.firstLine.startsWith("${SiteCensus.TOOL_LABEL}: "))
        val refused = runCatching {
            census(MAIN, http).run(listOf(SiteWays.X), X_LINKS, null, mode = ClientCensus.Mode.A_CHECK)
        }.exceptionOrNull()
        assertTrue(refused.toString(), refused is IllegalArgumentException)
    }

    @Test
    fun `W's request and byte caps stop the site's rest with no cooldown, and nothing after them runs`() = runTest {
        val http = xSite()
        val recorder = LookupRecorder(http)
        val asking = ScriptedMain { link ->
            repeat(2) { recorder.get("$WIDGET?id=$it", emptyMap(), 1_024) }
            answerOf(link)
        }
        var bytes = 0L
        val heavy = ScriptedMain { link ->
            bytes += SiteWeekly.MAX_BYTES
            answerOf(link)
        }

        val byRequests = census(asking, http, caps = SiteWeekly.Caps(requests = 3))
            .run(listOf(SiteWays.X), X_LINKS, null, mode = ClientCensus.Mode.WEEKLY)
        val byBytes = census(heavy, http, meter = { bytes }).run(listOf(SiteWays.X), X_LINKS, null, mode = ClientCensus.Mode.WEEKLY)

        val requests = json(byRequests.json)["sites"][0]
        assertEquals(SiteWeekly.REQUESTS, requests["stop"].asStringOrNull)
        assertEquals("main's 2 asks and V's 1 on slot 1, then the cap", 3L, requests["req"].asLongOrNull)
        val rows = requests["rows"].asArrayOrEmpty
        assertEquals("pass", rows[0]["v"]["verdict"].asStringOrNull)
        assertEquals(listOf(null, SiteWeekly.REQUESTS, "site stopped (requests)"), rows.map { it["skipped"].asStringOrNull })
        assertEquals("site stopped", rows[0]["master"]["why"].asStringOrNull)
        assertEquals("W (weekly check): FIRST RUN — nothing to compare yet · X stopped (requests), the rest not run", byRequests.firstLine)
        assertTrue(byRequests.report, byRequests.report.lines().contains("X: stopped (requests): the rest not run, 3 requests, bytes not measured, 0 s"))
        assertEquals("X: requests", byRequests.stopped)

        val heavySite = json(byBytes.json)["sites"][0]
        assertEquals(SiteWeekly.BYTES, heavySite["stop"].asStringOrNull)
        assertEquals("the cap is checked before V", SiteWeekly.BYTES, heavySite["rows"][0]["vSkipped"].asStringOrNull)
        assertEquals("site stopped (bytes)", heavySite["rows"][1]["skipped"].asStringOrNull)
        assertTrue(byBytes.report, byBytes.report.lines().contains("X: stopped (bytes): the rest not run, 0 requests, 2.0 MB, 0 s"))

        for (outcome in listOf(byRequests, byBytes)) {
            val state = json(outcome.stateJson)
            assertNull("no cooldown after W's cap", state["stops"]["x"])
            assertNull("a stopped run is no baseline", state["weekly"])
        }
        assertEquals("V asked once (slot 1, under the request cap), never under the byte cap", 1, http.probedUrls.size)
    }

    /** The test's virtual time ([kotlinx.coroutines.test.TestCoroutineScheduler.currentTime]) is W's clock. */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `W's time bounds each step - main, V or Master's part cut at 60 s is not run, and the site's rest neither`() = runTest {
        val virtual = { NOW_MS + testScheduler.currentTime }
        val slowMain = ScriptedMain { link ->
            delay(25_000)
            answerOf(link)
        }
        val cutMain = SiteCensus(slowMain, xSite(), VALID, clock = virtual)
            .run(listOf(SiteWays.X), X_LINKS, null, mode = ClientCensus.Mode.WEEKLY)
        val mainSite = json(cutMain.json)["sites"][0]
        assertEquals(SiteWeekly.TIME, mainSite["stop"].asStringOrNull)
        assertEquals("25 s, 25 s, then slot 8's lookup cut at 60 s", 60_000L, mainSite["ms"].asLongOrNull)
        val mainRows = mainSite["rows"].asArrayOrEmpty
        assertEquals(listOf(null, null, SiteWeekly.TIME), mainRows.map { it["skipped"].asStringOrNull })
        assertEquals("site stopped", mainRows[0]["master"]["why"].asStringOrNull)

        val fast = ScriptedMain { link -> answerOf(link) }
        val cutV = SiteCensus(fast, Slow(xSite(), probeMillis = 70_000), VALID, clock = virtual)
            .run(listOf(SiteWays.X), X_LINKS, null, mode = ClientCensus.Mode.WEEKLY)
        val vSite = json(cutV.json)["sites"][0]
        assertEquals(SiteWeekly.TIME, vSite["stop"].asStringOrNull)
        assertEquals(60_000L, vSite["ms"].asLongOrNull)
        assertNull("V cut: no verdict", vSite["rows"][0]["v"])
        assertEquals(SiteWeekly.TIME, vSite["rows"][0]["vSkipped"].asStringOrNull)
        assertEquals("site stopped (time)", vSite["rows"][1]["skipped"].asStringOrNull)

        val mains = ScriptedMain { link ->
            delay(19_000)
            answerOf(link)
        }
        val cutMaster = SiteCensus(mains, Slow(xSite(), getMillis = 10_000), VALID, clock = virtual)
            .run(listOf(SiteWays.X), X_LINKS, null, mode = ClientCensus.Mode.WEEKLY)
        val masterSite = json(cutMaster.json)["sites"][0]
        assertNull("every W slot's main ran", masterSite["stop"])
        assertEquals("Master's part from 57 s, cut at 60 s", 60_000L, masterSite["ms"].asLongOrNull)
        assertEquals(SiteWeekly.TIME, masterSite["masterStop"].asStringOrNull)
        val master = masterSite["rows"][0]["master"]
        assertEquals("time limit", master["answer"].asStringOrNull)
        assertEquals(SiteWeekly.TIME, master["stopped"].asStringOrNull)
        assertTrue(cutMaster.firstLine, cutMaster.firstLine.endsWith("· trials incomplete (time)"))

        for (outcome in listOf(cutMain, cutV, cutMaster)) {
            val state = json(outcome.stateJson)
            assertNull("no cooldown after W's time", state["stops"]["x"])
            assertNull("no Master cooldown after W's time", state["masterStops"]["x"])
        }
    }

    @Test
    fun `a 429 in W stops the site and cools it, and the next W reads it not run, never waited out or passed`() = runTest {
        var limited = false
        val http = FakeExtractorHttpClient(
            getResponder = { url, _ ->
                when {
                    !url.startsWith(WIDGET) -> null
                    limited -> ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429)
                    else -> FakeExtractorHttpClient.json(Fixtures.read("x/tweet_video.json"), url)
                }
            },
            probeResponder = { _, _ -> ExtractorProbeResult.Answered(206, 1_000L) },
        )
        val recorder = LookupRecorder(http)
        val main = ScriptedMain { link ->
            recorder.get("$WIDGET?id=1", emptyMap(), 1_024)
            if (limited) MainAnswer.Failed(SiteExtractionFailure.RATE_LIMITED, 429) else answerOf(link)
        }
        val census = census(main, http)

        val first = census.run(listOf(SiteWays.X), X_LINKS, null, mode = ClientCensus.Mode.WEEKLY)
        now += MINUTE
        limited = true
        val second = census.run(listOf(SiteWays.X), X_LINKS, first.stateJson, mode = ClientCensus.Mode.WEEKLY)
        now += 5 * MINUTE
        limited = false
        val asked = http.requestedUrls.size
        val third = census.run(listOf(SiteWays.X), X_LINKS, second.stateJson, mode = ClientCensus.Mode.WEEKLY)

        assertTrue(second.firstLine, second.firstLine.startsWith("W (weekly check): BREAKING ("))
        assertTrue(second.firstLine, second.firstLine.endsWith(" · X stopped (429), the rest not run"))
        assertTrue(second.report, second.report.lines().contains("  BREAKING X: stopped (429) → W runbook step 1 (the site's stop)"))
        assertEquals("429", json(second.stateJson)["stops"]["x"]["why"].asStringOrNull)
        assertEquals("the cooling site asks nothing", asked, http.requestedUrls.size)
        assertEquals("cooldown 25 min (429)", json(third.json)["sites"][0]["skipped"].asStringOrNull)
        assertEquals(
            "W (weekly check): NOT COMPARED — no baseline yet for this run's sites · X not run (cooldown 25 min (429))",
            third.firstLine,
        )
        assertFalse("never NO CHANGE", "NO CHANGE" in third.firstLine + third.report)
        assertTrue(third.report, third.report.lines().contains("X: not run (cooldown 25 min (429))"))
        assertEquals(
            "W's baseline stays the first run's (the stopped run is none)",
            listOf(NOW_MS),
            json(third.stateJson)["weekly"].asArrayOrEmpty.map { it["at"].asLongOrNull },
        )
    }

    @Test
    fun `F5 - a W run whose every ask got the login wall keeps its BREAKING lines and one note, and is never the baseline`() = runTest {
        val walled = HashSet<String>()
        val (http, main) = walledSite(walled)
        val census = census(main, http)
        val first = census.run(listOf(SiteWays.X), X_LINKS, null, mode = ClientCensus.Mode.WEEKLY)

        now += MINUTE
        walled += W_POSTS
        val asked = http.requestedUrls.size
        val second = census.run(listOf(SiteWays.X), X_LINKS, first.stateJson, mode = ClientCensus.Mode.WEEKLY)
        val site = checkNotNull(json(second.json)["sites"][0])
        val asks = site["rows"].asArrayOrEmpty.flatMap { it["main"]["asks"].asArrayOrEmpty + it["master"]["asks"].asArrayOrEmpty }
        assertEquals("each ask listed", http.requestedUrls.size - asked, asks.size)
        assertTrue("main's three W slots at least", asks.size >= 3)
        assertEquals("every ask got the wall (a login status)", List(asks.size) { "LOGIN_REQUIRED" }, asks.map { it["failure"].asStringOrNull })
        assertTrue(SiteWeekly.walled(site))
        assertTrue(second.firstLine, second.firstLine.startsWith("W (weekly check): BREAKING ("))
        val lines = second.report.lines()
        assertTrue(
            "the BREAKING lines kept: $lines",
            lines.any { it.startsWith("  BREAKING X slot 1 (video): main rows 1") && "failed LOGIN_REQUIRED 401" in it },
        )
        assertEquals(
            "one note, the baseline kept",
            listOf("  note: X: every ask walled: likely this network's address, §7.5; not a baseline"),
            lines.filter { it.startsWith("  note: ") && ("walled" in it || "baseline" in it) },
        )
        assertEquals("W's baseline stays the first run's", listOf(NOW_MS), json(second.stateJson)["weekly"].asArrayOrEmpty.map { it["at"].asLongOrNull })

        now += MINUTE
        walled.clear()
        val third = census.run(listOf(SiteWays.X), X_LINKS, second.stateJson, mode = ClientCensus.Mode.WEEKLY)
        assertEquals(
            "a good address again: NO CHANGE against the first run, never CHANGED against the walled one",
            "W (weekly check): NO CHANGE — against the run of ${SiteCensusJson.dateOf(NOW_MS)}",
            third.firstLine,
        )

        now += MINUTE
        walled += X_POST
        val partly = census.run(listOf(SiteWays.X), X_LINKS, third.stateJson, mode = ClientCensus.Mode.WEEKLY)
        assertFalse(SiteWeekly.walled(checkNotNull(json(partly.json)["sites"][0])))
        assertTrue(partly.firstLine, partly.firstLine.startsWith("W (weekly check): BREAKING ("))
        assertFalse("a partly walled run: no note", "walled" in partly.report)
        assertEquals("and it is the baseline, as before", listOf(now), json(partly.stateJson)["weekly"].asArrayOrEmpty.map { it["at"].asLongOrNull })
    }

    @Test
    fun `F5 - a stored W baseline whose every ask was walled is set aside, and the next run from a good address starts it`() = runTest {
        val walled = HashSet<String>(W_POSTS)
        val (http, main) = walledSite(walled)
        val census = census(main, http)
        val walledRun = census.run(listOf(SiteWays.X), X_LINKS, null, mode = ClientCensus.Mode.WEEKLY)
        assertEquals("no baseline yet, none started", 0, json(walledRun.stateJson)["weekly"].asArrayOrEmpty.size)
        assertTrue(
            walledRun.report,
            walledRun.report.lines().containsAll(
                listOf(
                    "  note: X: no baseline yet (VPN off, signed out)",
                    "  note: X: every ask walled: likely this network's address, §7.5; not a baseline",
                ),
            ),
        )
        // An earlier APK's state: the walled run kept as W's baseline (2026-10-23, 02:24 UTC).
        val prints = SiteWeekly.linksOf(SiteWays.X, X_LINKS.getValue("x")).mapValues { (_, link) -> SiteCensus.fingerprintOf(link) }
        val stored = SiteCensusState.write(
            SiteCensusState(
                emptyMap(),
                weekly = mapOf(
                    SiteCensusState.Key("x", vpn = false, session = false) to
                        SiteCensusState.Baseline(NOW_MS, DATE, prints, checkNotNull(json(walledRun.json)["sites"][0])),
                ),
            ),
        )

        now += MINUTE
        val again = census.run(listOf(SiteWays.X), X_LINKS, stored, mode = ClientCensus.Mode.WEEKLY)
        assertTrue(
            again.report,
            again.report.lines().containsAll(
                listOf(
                    "  note: X: the baseline of $DATE (VPN off, signed out) had every ask walled: set aside",
                    "  note: X: every ask walled: likely this network's address, §7.5; not a baseline",
                ),
            ),
        )
        assertEquals("set aside, none kept", 0, json(again.stateJson)["weekly"].asArrayOrEmpty.size)

        now += MINUTE
        walled.clear()
        val good = census.run(listOf(SiteWays.X), X_LINKS, stored, mode = ClientCensus.Mode.WEEKLY)
        assertEquals("W (weekly check): NOT COMPARED — no baseline yet for this run's sites", good.firstLine)
        assertTrue(
            "never CHANGED (or BREAKING) against the walled run: ${good.report}",
            good.report.lines().none { it.startsWith("  CHANGED ") || it.startsWith("  BREAKING ") },
        )
        assertTrue(
            good.report,
            good.report.lines().contains("  note: X: the baseline of $DATE (VPN off, signed out) had every ask walled: set aside; this run starts it"),
        )
        assertEquals("this run is the baseline", listOf(now), json(good.stateJson)["weekly"].asArrayOrEmpty.map { it["at"].asLongOrNull })
    }

    @Test
    fun `W's diff - a line per change, each naming its runbook step - and the census's diff joins them as before`() {
        val before = site(
            "x", "X",
            row(1, master = master("trial", rows = 3, v = "pass", wrong = 0)),
            row(SEVERAL, kind = "several videos", item = 1 to 2, bestS = "31.5"),
            row(
                X_REMOVED, kind = "removed", cls = "final", answer = "failed", reason = "PRIVATE_OR_UNAVAILABLE", v = null,
                master = master("trial", answer = "failed", reason = "NO_MEDIA_FOUND", wrong = 0),
            ),
            ladder = true,
        )
        val after = site(
            "x", "X",
            row(
                1, cls = "recoverable", expected = false, note = "rows wanted", answer = "failed", reason = "RESPONSE_CHANGED", v = null,
                master = master("route B", answer = "failed", reason = "NO_MEDIA_FOUND"),
            ),
            row(SEVERAL, kind = "GIF", session = true, item = 2 to 2, bestS = "12.0", v = "fail"),
            row(
                X_REMOVED, kind = "removed", cls = "final", answer = "failed", reason = "PRIVATE_OR_UNAVAILABLE", v = null,
                master = master("trial", rows = 2, wrong = 2),
            ),
            stop = "429",
            ladder = true,
        )
        val later = SiteCensusDiff.parse(export(after))
        val baselines = mapOf("x" to SiteCensusDiff.Baseline(checkNotNull(SiteCensusDiff.parse(before)), DATE))

        val weekly = SiteCensusDiff.compare(later, baselines, emptyList(), firstRun = false, weekly = true)
        val census = SiteCensusDiff.compare(later, baselines, emptyList(), firstRun = false)

        assertEquals("BREAKING (11) — against the run of $DATE", weekly.headline)
        assertEquals(
            listOf(
                Level.BREAKING to Step.SITE_STOP,
                Level.BREAKING to Step.MAIN,
                Level.BREAKING to Step.VERDICT,
                Level.CHANGED to Step.FILE_CHECK,
                Level.CHANGED to Step.MASTER,
                Level.CHANGED to Step.CONDITIONS,
                Level.CHANGED to Step.CONDITIONS,
                Level.BREAKING to Step.FILE_CHECK,
                Level.CHANGED to Step.POST,
                Level.CHANGED to Step.POST,
                Level.BREAKING to Step.WRONG_ROWS,
            ),
            weekly.lines.map { it.level to it.step },
        )
        assertEquals("every kind of line has its step", Step.entries.toSet(), weekly.lines.mapNotNull { it.step }.toSet())
        assertEquals(
            listOf(
                "BREAKING X: stopped (429) → W runbook step 1 (the site's stop)",
                "BREAKING X slot 1 (video): main rows 3 (tallest 1280) [rows] → failed RESPONSE_CHANGED [recoverable] " +
                    "→ W runbook step 2 (main's answer)",
                "BREAKING X slot 1 (video): as expected → not as expected (rows wanted) → W runbook step 3 (the slot's verdict)",
                "CHANGED X slot 1 (video): V pass → not run → W runbook step 4 (V)",
                "CHANGED X slot 1 (video): Master trial: rows 3, V pass, wrong 0 → route B: failed NO_MEDIA_FOUND " +
                    "→ W runbook step 6 (Master's part)",
                "CHANGED X slot 6 (GIF): kind several videos → GIF → W runbook step 8 (the run's conditions)",
                "CHANGED X slot 6 (GIF): session no → yes → W runbook step 8 (the run's conditions)",
                "BREAKING X slot 6 (GIF): V pass → fail → W runbook step 4 (V)",
                "CHANGED X slot 6 (GIF): item 1 of 2 → 2 of 2 → W runbook step 5 (the item or the length)",
                "CHANGED X slot 6 (GIF): best 31.5 s → 12.0 s → W runbook step 5 (the item or the length)",
                "BREAKING X slot 8 (removed): Master trial: failed NO_MEDIA_FOUND, wrong 0 → trial: rows 2, wrong 2 " +
                    "→ W runbook step 7 (Master's wrong rows)",
            ),
            weekly.lines.map(SiteCensusDiff.Line::render),
        )
        assertEquals("BREAKING (4) — against the run of $DATE", census.headline)
        assertEquals(
            listOf(
                "BREAKING X: stopped (429)",
                "BREAKING X slot 1 (video): main rows 3 (tallest 1280) [rows] → failed RESPONSE_CHANGED [recoverable]; " +
                    "as expected → not as expected (rows wanted); V pass → not run; " +
                    "Master trial: rows 3, V pass, wrong 0 → route B: failed NO_MEDIA_FOUND",
                "BREAKING X slot 6 (GIF): kind several videos → GIF; session no → yes; V pass → fail; item 1 of 2 → 2 of 2; " +
                    "best 31.5 s → 12.0 s",
                "BREAKING X slot 8 (removed): Master trial: failed NO_MEDIA_FOUND, wrong 0 → trial: rows 2, wrong 2",
            ),
            census.lines.map(SiteCensusDiff.Line::render),
        )
        assertTrue("the census's lines name no step", census.lines.all { it.step == null })
    }

    @Test
    fun `W's caps and slots are as documented, and every runbook step has its entry in the plan's section 7_5`() {
        assertEquals(60_000L, SiteWeekly.MAX_MILLIS)
        assertEquals(2L * 1024 * 1024, SiteWeekly.MAX_BYTES)
        assertEquals(24, SiteWeekly.MAX_REQUESTS)
        assertEquals(listOf("facebook", "tiktok", "instagram", "x", "vk"), SiteWeekly.SLOTS.keys.toList())
        for (site in SiteWays.ALL) {
            val slots = checkNotNull(SiteWeekly.SLOTS[site.id])
            assertEquals("${site.name}: three slots, Master's slot first", SiteWeekly.MASTER_SLOT, slots.first())
            assertEquals(3, slots.toSet().size)
            assertTrue("${site.name}: W's Master slot downloads", checkNotNull(site.kind(SiteWeekly.MASTER_SLOT)).expect.rows)
            slots.forEach { slot -> assertTrue("${site.name} slot $slot is on its list", site.kind(slot) != null) }
        }
        assertEquals("one step per kind of line, numbered 1 to 8", (1..8).toList(), Step.entries.map { it.number })

        val plan = listOf("../MASTER_KEY_SOCIAL_PLAN.md", "MASTER_KEY_SOCIAL_PLAN.md").map(::File).first(File::isFile).readText()
        val section = plan.substringAfter("\n### 7.5 ", "").substringBefore("\n## ")
        assertTrue("§7.5 is W's runbook", section.startsWith("W's runbook"))
        for (step in Step.entries) {
            assertTrue("§7.5 lacks ${step.label}", section.contains("\n${step.number}. **${step.label}**"))
        }
        assertTrue("§7.5 says what to do with a walled run (F5)", section.contains("note: <Site>: ${SiteWeekly.WALLED}; not a baseline"))
    }

    private fun census(
        main: MainLookup,
        http: ExtractorHttpClient,
        meter: SiteCensus.Meter = SiteCensus.Meter.NONE,
        caps: SiteWeekly.Caps = SiteWeekly.Caps(),
    ) = SiteCensus(main, http, VALID, meter, clock, weeklyCaps = caps)

    /** X's widget answering the video post's fixture, and its files answering V. */
    private fun xSite() = FakeExtractorHttpClient(
        getResponder = { url, _ ->
            FakeExtractorHttpClient.json(Fixtures.read("x/tweet_video.json"), url).takeIf { url.startsWith(WIDGET) }
        },
        probeResponder = { _, _ -> ExtractorProbeResult.Answered(206, 1_000L) },
    )

    /**
     * F5: X's widget (main's ask and Master's) answering the fixture, or a login status (401) for
     * the posts in [walled]; main's answer for those the wall too (LOGIN_REQUIRED).
     */
    private fun walledSite(walled: Set<String>): Pair<FakeExtractorHttpClient, MainLookup> {
        fun post(url: String): String? = W_POSTS.firstOrNull { it.substringAfterLast('/') in url }
        val http = FakeExtractorHttpClient(
            getResponder = { url, _ ->
                when {
                    !url.startsWith(WIDGET) -> null
                    post(url) in walled -> ExtractorHttpResult.Failure(SiteExtractionFailure.LOGIN_REQUIRED, 401)
                    else -> FakeExtractorHttpClient.json(Fixtures.read("x/tweet_video.json"), url)
                }
            },
            probeResponder = { _, _ -> ExtractorProbeResult.Answered(206, 1_000L) },
        )
        val recorder = LookupRecorder(http)
        val main = ScriptedMain { link ->
            recorder.get("$WIDGET?id=${link.substringAfterLast('/')}", emptyMap(), 1_024)
            if (link in walled) MainAnswer.Failed(SiteExtractionFailure.LOGIN_REQUIRED, 401) else answerOf(link)
        }
        return http to main
    }

    private fun json(text: String): JsonValue = checkNotNull(BoundedJsonParser.parse(text)) { "not JSON: $text" }

    private fun export(vararg sites: String): String = SiteCensusJson.obj {
        field("tool", SiteCensus.TOOL_LABEL)
        field("fmt", SiteCensusState.FORMAT)
        field("at", NOW_MS)
        field("date", DATE)
        field("ms", 1_000)
        strings("notes", emptyList())
        list("sites", sites.toList())
    }

    private fun site(id: String, name: String, vararg rows: String, stop: String? = null, ladder: Boolean = false): String =
        SiteCensusJson.obj {
            field("site", id)
            field("name", name)
            field("stop", stop)
            if (ladder) field("ladder", true)
            field("req", 10)
            list("rows", rows.toList())
        }

    private fun row(
        slot: Int,
        kind: String = "video",
        session: Boolean = false,
        cls: String = "rows",
        expected: Boolean? = true,
        note: String? = null,
        answer: String = "rows",
        reason: String? = null,
        bestS: String? = null,
        item: Pair<Int, Int>? = null,
        v: String? = "pass",
        master: String = master("trial", rows = 3, v = "pass"),
    ): String = SiteCensusJson.obj {
        field("slot", slot)
        field("kind", kind)
        field("session", session)
        field("class", cls)
        field("expected", expected)
        field("note", note)
        field("req", 5)
        raw(
            "main",
            SiteCensusJson.obj {
                field("answer", answer)
                field("reason", reason)
                field("rows", if (answer == "rows") 3 else 0)
                field("tallest", 1280.takeIf { answer == "rows" })
                bestS?.let { raw("bestS", it) }
                field("item", item?.first)
                field("items", item?.second)
            },
        )
        v?.let { raw("v", SiteCensusJson.obj { field("verdict", it) }) }
        raw("master", master)
    }

    private fun master(run: String, answer: String = "rows", rows: Int = 0, reason: String? = null, v: String? = null, wrong: Int? = null) =
        SiteCensusJson.obj {
            field("run", run)
            field("answer", answer)
            field("reason", reason)
            field("rows", rows)
            wrong?.let { field("wrong", it) }
            v?.let { raw("v", SiteCensusJson.obj { field("verdict", it) }) }
        }

    /** [inner] after [getMillis] per page ask and [probeMillis] per file check (the test's virtual time). */
    private class Slow(
        private val inner: ExtractorHttpClient,
        private val getMillis: Long = 0,
        private val probeMillis: Long = 0,
    ) : ExtractorHttpClient {
        override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
            delay(getMillis)
            return inner.get(url, headers, maxBodyBytes)
        }

        override suspend fun postJson(url: String, body: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
            delay(getMillis)
            return inner.postJson(url, body, headers, maxBodyBytes)
        }

        override suspend fun probe(url: String, headers: Map<String, String>, timeoutMillis: Long): ExtractorProbeResult {
            delay(probeMillis)
            return inner.probe(url, headers, timeoutMillis)
        }
    }

    /** Main as a script: main's own identities, the browser's identity, [answer]'s answer. */
    private class ScriptedMain(private val answer: suspend (link: String) -> MainAnswer) : MainLookup {
        override suspend fun identify(link: String): LinkIdentity? = identityOf(link)?.let { LinkIdentity(it, BROWSER_AGENT, null) }

        override suspend fun lookup(link: String, identity: LinkIdentity): MainAnswer = answer(link)
    }

    private companion object {
        const val NOW_MS = 1_791_000_000_000L
        const val MINUTE = 60_000L
        const val DATE = "2026-10-22 10:00 UTC"
        const val WIDGET = "https://cdn.syndication.twimg.com/tweet-result"
        const val X_POST = "https://x.com/fixture_user/status/1785999999999999999"
        const val X_POST_2 = "https://x.com/fixture_user/status/1785999999999999998"
        const val X_POST_3 = "https://x.com/fixture_user/status/1785999999999999997"
        const val X_POST_6 = "https://x.com/fixture_user/status/1785999999999999994"
        const val X_POST_8 = "https://x.com/fixture_user/status/1785999999999999992"
        const val X_FILE = "https://video.twimg.com/ext_tw_video/1785999990000000000/pu/vid/avc1/720x1280/a720.mp4?tag=12"

        /** X's slots these tests use (SiteWays): W's are 1, 6 and 8. */
        const val SEVERAL = 6
        const val X_REMOVED = 8

        val X_LINKS = mapOf("x" to mapOf(1 to X_POST, 2 to X_POST_2, 3 to X_POST_3, SEVERAL to X_POST_6, X_REMOVED to X_POST_8))

        /** X's W slots' posts (1, 6, 8). */
        val W_POSTS = listOf(X_POST, X_POST_6, X_POST_8)

        val VALID = MasterMediaValidator { candidate, _ -> ValidationResult.Valid(candidate) }

        private val ADAPTERS = FakeExtractorHttpClient().let { none ->
            SiteExtractorRegistry(listOf(FacebookExtractor(none), TikTokExtractor(none), InstagramExtractor(none), XExtractor(none)))
        }

        fun identityOf(link: String): SitePageIdentity? = (ADAPTERS.select(link) as? SiteAdapterSelection.Matched)?.identity

        /** Main's answer per link: rows, the removed post's 404 on slot 8. */
        fun answerOf(link: String): MainAnswer =
            if (link == X_POST_8) {
                MainAnswer.Failed(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, 404)
            } else {
                MainAnswer.Found(listOf(candidate(X_FILE, 1280)))
            }

        /** Main's scripted answer for every link: rows, the removed post's 404. */
        val MAIN = ScriptedMain { link -> answerOf(link) }

        fun candidate(url: String, height: Int, page: String = X_POST) = MediaCandidate(
            pageUrl = page,
            mediaUrl = url,
            sources = setOf(CandidateSource.MANIFEST),
            kind = MediaKind.DIRECT,
            mimeType = "video/mp4",
            requestContext = BrowserRequestContext(page, BROWSER_AGENT, null),
            height = height,
        )
    }
}
