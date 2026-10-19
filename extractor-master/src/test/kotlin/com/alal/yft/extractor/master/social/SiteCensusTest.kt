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
import com.alal.yft.extractor.master.ValidationResult
import com.alal.yft.extractor.master.lookup.AnswerMark
import com.alal.yft.extractor.master.lookup.LookupRecorder
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.master.modules.youtube.testing.Fixtures
import com.alal.yft.extractor.master.social.SiteWays.DESKTOP_PAGE
import com.alal.yft.extractor.master.social.SiteWays.DOWNLOAD_SERVICE
import com.alal.yft.extractor.master.social.SiteWays.EMBED_PLAYER
import com.alal.yft.extractor.master.social.SiteWays.HIDDEN_PAGE
import com.alal.yft.extractor.master.social.SiteWays.PHONE_PAGE
import com.alal.yft.extractor.master.social.SiteWays.TWEET_RESULT
import com.alal.yft.extractor.master.social.SiteWaysTest.Companion.BROWSER_AGENT
import com.alal.yft.extractor.master.social.SiteWaysTest.Companion.IG_REEL
import com.alal.yft.extractor.master.social.SiteWaysTest.Companion.TT_CHALLENGE
import com.alal.yft.extractor.master.social.SiteWaysTest.Companion.TT_EMBED
import com.alal.yft.extractor.master.social.SiteWaysTest.Companion.TT_EMBED_URL
import com.alal.yft.extractor.master.social.SiteWaysTest.Companion.TT_POST
import com.alal.yft.extractor.sites.facebook.FacebookExtractor
import com.alal.yft.extractor.sites.instagram.InstagramExtractor
import com.alal.yft.extractor.sites.tiktok.TikTokExtractor
import com.alal.yft.extractor.sites.x.XExtractor
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Social plan S0b: the census engine ([SiteCensus]) over fake sites: main's lookup (the app's
 * adapters over fixtures, or a script), V on the best rows, Master's R8 way once under route B's
 * rules (route B after a recoverable failure, a trial after rows or a final answer, never after a
 * browser-required reason, a time limit or for a short link), the judge, the stops (429, bot
 * check) with their cooldown, the budgets, and an export with no link, ID, handle or address.
 */
class SiteCensusTest {
    private var now = NOW_MS
    private val clock = { now }

    @Test
    fun `main's rows are checked by V, and Master's way runs once as a trial under route B's rules`() = runTest {
        val http = xSite()
        val steps = mutableListOf<String>()

        val outcome = census(AppMain(http, cookies = mapOf("x" to "auth_token=$COOKIE_VALUE")), http)
            .run(listOf(SiteWays.X), mapOf("x" to mapOf(1 to X_POST)), null, onStep = steps::add)

        val site = json(outcome.json)["sites"][0]
        val row = site["rows"][0]
        assertEquals("rows", row["class"].asStringOrNull)
        assertEquals(true, row["expected"].asBooleanOrNull)
        assertEquals("X's sign-in cookie was there", true, row["session"].asBooleanOrNull)
        val main = row["main"]
        assertEquals("rows", main["answer"].asStringOrNull)
        assertEquals(1280L, main["tallest"].asLongOrNull)
        assertEquals(listOf(TWEET_RESULT), main["asks"].asArrayOrEmpty.map { it["way"].asStringOrNull })
        assertEquals(listOf(AnswerMark.DATA.label), main["asks"].asArrayOrEmpty.map { it["mark"].asStringOrNull })
        assertEquals("pass", row["v"]["verdict"].asStringOrNull)
        assertEquals(206L, row["v"]["status"].asLongOrNull)
        val master = row["master"]
        assertEquals("trial", master["run"].asStringOrNull)
        assertNull("a trial is not after a failure", master["why"])
        assertEquals("main's identical answer, replayed", 1L, master["replayed"].asLongOrNull)
        assertEquals(0L, master["new"].asLongOrNull)
        assertEquals(0L, master["refused"].asLongOrNull)
        assertEquals("rows", master["answer"].asStringOrNull)
        assertEquals(3L, master["rows"].asLongOrNull)
        assertEquals("Master's media checks", 3L, master["checks"].asLongOrNull)
        assertEquals("pass", master["v"]["verdict"].asStringOrNull)
        assertEquals("the widget once, main's", 1, http.requestedUrls.size)
        assertEquals("V: one byte of main's tallest file, then of Master's", listOf(X_FILE, X_FILE), http.probedUrls)
        assertEquals("main's ask, V's two and Master's checks", 6L, row["req"].asLongOrNull)
        assertEquals(6L, site["req"].asLongOrNull)
        assertEquals(1L, site["number"]["trials"].asLongOrNull)
        assertEquals(1L, site["number"]["trialPass"].asLongOrNull)
        assertEquals(1L, site["number"]["vPass"].asLongOrNull)
        assertEquals(listOf("X slot 1: main", "X slot 1: V", "X slot 1: Master", "X slot 1: V"), steps)
        assertEquals("YFT site census: X recoverable 0 of 1, Master 0", outcome.firstLine)
        assertNull(outcome.stopped)
        val lines = outcome.report.lines()
        assertTrue(lines.toString(), lines.contains("  slot 1 video, session: rows 3 (tallest 1280) [rows], V pass 206 — as expected; requests 6, 0 ms"))
        assertTrue(lines.toString(), lines.contains("    main: tweet-result GET 200 data ${main["asks"][0]["shape"].asStringOrNull} 0 ms"))
        assertTrue(lines.toString(), lines.contains("    Master trial: rows 3 (tallest 1280), V pass 206; replayed 1, new 0, refused 0, checks 3, 0 ms"))
    }

    @Test
    fun `main's recoverable failure goes to Master's way as route B, and its rows passing V are recovered`() = runTest {
        val http = FakeExtractorHttpClient(
            getResponder = { url, _ -> FakeExtractorHttpClient.html(TT_EMBED_CDN, url).takeIf { url == TT_EMBED_URL } },
            probeResponder = { _, _ -> ExtractorProbeResult.Answered(206, 5_000_000L) },
        )
        val main = ScriptedMain { _, _ -> MainAnswer.Failed(SiteExtractionFailure.RESPONSE_CHANGED) }

        val outcome = census(main, http).run(listOf(SiteWays.TIKTOK), mapOf("tiktok" to mapOf(1 to TT_POST)), null)

        val site = json(outcome.json)["sites"][0]
        val row = site["rows"][0]
        assertEquals("recoverable", row["class"].asStringOrNull)
        assertEquals(false, row["expected"].asBooleanOrNull)
        assertEquals("rows wanted", row["note"].asStringOrNull)
        val master = row["master"]
        assertEquals("route B", master["run"].asStringOrNull)
        assertEquals("RESPONSE_CHANGED", master["why"].asStringOrNull)
        assertEquals("rows", master["answer"].asStringOrNull)
        assertEquals(1L, master["new"].asLongOrNull)
        assertEquals(0L, master["refused"].asLongOrNull)
        assertEquals("Master's media check", 1L, master["checks"].asLongOrNull)
        assertEquals(listOf(EMBED_PLAYER), master["asks"].asArrayOrEmpty.map { it["way"].asStringOrNull })
        assertEquals("pass", master["v"]["verdict"].asStringOrNull)
        assertEquals(listOf(TT_EMBED_URL), http.requestedUrls)
        assertEquals(listOf(TT_CDN_FILE), http.probedUrls)
        assertEquals("the embed ask, the media check and V's", 3L, row["req"].asLongOrNull)
        assertEquals(3L, site["req"].asLongOrNull)
        assertEquals(1L, site["number"]["recoverable"].asLongOrNull)
        assertEquals(1L, site["number"]["master"].asLongOrNull)
        assertEquals(1L, site["number"]["routeB"].asLongOrNull)
        assertEquals("YFT site census: TikTok recoverable 1 of 1, Master 1", outcome.firstLine)
        assertTrue(
            outcome.report,
            outcome.report.lines().any { it.startsWith("    Master route B after RESPONSE_CHANGED: rows 1 (tallest 1280), V pass 206;") },
        )
    }

    @Test
    fun `route B never asks again an endpoint main asked with the session`() = runTest {
        val http = FakeExtractorHttpClient(
            getResponder = { url, _ -> FakeExtractorHttpClient.html(TT_EMBED_CDN, url).takeIf { url == TT_EMBED_URL } },
        )
        val recorder = LookupRecorder(http)
        val main = ScriptedMain(cookies = mapOf("tiktok" to "sessionid=$COOKIE_VALUE")) { _, identity ->
            recorder.get(TT_EMBED_URL, mapOf("Cookie" to identity.cookie.orEmpty()), 1_000_000)
            MainAnswer.Failed(SiteExtractionFailure.RESPONSE_CHANGED)
        }

        val outcome = census(main, http).run(listOf(SiteWays.TIKTOK), mapOf("tiktok" to mapOf(1 to TT_POST)), null)

        val site = json(outcome.json)["sites"][0]
        val master = site["rows"][0]["master"]
        assertEquals("route B", master["run"].asStringOrNull)
        assertEquals("main's answer had the session: not kept, and not asked again", 1L, master["refused"].asLongOrNull)
        assertEquals(0L, master["replayed"].asLongOrNull)
        assertEquals(0L, master["new"].asLongOrNull)
        assertEquals("needs playback", master["answer"].asStringOrNull)
        assertEquals("main's ask only", listOf(TT_EMBED_URL), http.requestedUrls)
        assertEquals(1L, site["number"]["recoverable"].asLongOrNull)
        assertEquals(0L, site["number"]["master"].asLongOrNull)
        assertEquals("YFT site census: TikTok recoverable 1 of 1, Master 0", outcome.firstLine)
    }

    @Test
    fun `a browser-required answer never runs Master, and a sign-in-only slot wants rows only with the session`() = runTest {
        val http = FakeExtractorHttpClient()
        val links = mapOf("instagram" to mapOf(SIGN_IN_ONLY_IG to IG_REEL))
        val login = MainAnswer.Failed(SiteExtractionFailure.LOGIN_REQUIRED)

        val alone = census(ScriptedMain { _, _ -> login }, http).run(listOf(SiteWays.INSTAGRAM), links, null)
        val signedIn = census(
            ScriptedMain(cookies = mapOf("instagram" to "csrftoken=a; sessionid=$COOKIE_VALUE")) { _, _ -> login },
            http,
        ).run(listOf(SiteWays.INSTAGRAM), links, null)

        val first = json(alone.json)["sites"][0]
        assertEquals("browser", first["rows"][0]["class"].asStringOrNull)
        assertEquals(true, first["rows"][0]["expected"].asBooleanOrNull)
        assertEquals("not run", first["rows"][0]["master"]["run"].asStringOrNull)
        assertEquals("browser-required", first["rows"][0]["master"]["why"].asStringOrNull)
        assertEquals(0L, first["number"]["download"].asLongOrNull)
        val second = json(signedIn.json)["sites"][0]
        assertEquals(true, second["rows"][0]["session"].asBooleanOrNull)
        assertEquals(false, second["rows"][0]["expected"].asBooleanOrNull)
        assertEquals("rows wanted", second["rows"][0]["note"].asStringOrNull)
        assertEquals(1L, second["number"]["download"].asLongOrNull)
        assertEquals(1L, second["number"]["browser"].asLongOrNull)
        assertEquals("browser-required", second["rows"][0]["master"]["why"].asStringOrNull)
        assertTrue("nothing asked", http.requestedUrls.isEmpty() && http.probedUrls.isEmpty())
        assertTrue(alone.report, alone.report.lines().contains("    Master not run: browser-required"))
    }

    @Test
    fun `the site's final answer is a trial for Master, a network failure is not, and the judge reads the slot`() = runTest {
        val http = FakeExtractorHttpClient()
        val main = ScriptedMain { link, _ ->
            when (link) {
                X_POST -> MainAnswer.Failed(SiteExtractionFailure.NETWORK)
                else -> MainAnswer.Failed(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, 404)
            }
        }

        val outcome = census(main, http).run(listOf(SiteWays.X), mapOf("x" to mapOf(1 to X_POST, X_REMOVED to X_POST_2)), null)

        val rows = json(outcome.json)["sites"][0]["rows"].asArrayOrEmpty
        assertEquals(listOf("final", "final"), rows.map { it["class"].asStringOrNull })
        assertEquals("a video slot wants rows", false, rows[0]["expected"].asBooleanOrNull)
        assertEquals("network", rows[0]["master"]["why"].asStringOrNull)
        assertEquals("a removed slot expects its answer", true, rows[1]["expected"].asBooleanOrNull)
        assertEquals("PRIVATE_OR_UNAVAILABLE", rows[1]["main"]["reason"].asStringOrNull)
        assertEquals(404L, rows[1]["main"]["status"].asLongOrNull)
        assertEquals("trial", rows[1]["master"]["run"].asStringOrNull)
        assertEquals("Master's own ask", 1L, rows[1]["master"]["new"].asLongOrNull)
        assertEquals(listOf(TWEET_RESULT), rows[1]["master"]["asks"].asArrayOrEmpty.map { it["way"].asStringOrNull })
        assertEquals(1, http.requestedUrls.size)
        assertTrue(http.requestedUrls.single().startsWith(WIDGET))
        val number = json(outcome.json)["sites"][0]["number"]
        assertEquals(1L, number["final"].asLongOrNull)
        assertEquals(1L, number["notAsExpected"].asLongOrNull)
        assertEquals(1L, number["trials"].asLongOrNull)
    }

    @Test
    fun `main's time limit ends the row without Master, and the next row runs`() = runTest {
        val http = FakeExtractorHttpClient()
        val main = ScriptedMain { link, _ ->
            if (link == X_POST) delay(SiteCensus.ROW_MILLIS + 1_000)
            MainAnswer.Failed(SiteExtractionFailure.HTTP_STATUS, 500)
        }

        val outcome = census(main, http).run(listOf(SiteWays.X), mapOf("x" to mapOf(1 to X_POST, 2 to X_POST_2)), null)

        val rows = json(outcome.json)["sites"][0]["rows"].asArrayOrEmpty
        assertEquals(listOf("time limit", "recoverable"), rows.map { it["class"].asStringOrNull })
        assertEquals("time limit", rows[0]["main"]["answer"].asStringOrNull)
        assertEquals("not run", rows[0]["master"]["run"].asStringOrNull)
        assertEquals("time limit", rows[0]["master"]["why"].asStringOrNull)
        assertEquals("route B", rows[1]["master"]["run"].asStringOrNull)
        assertEquals("HTTP_STATUS", rows[1]["master"]["why"].asStringOrNull)
        assertEquals(1L, json(outcome.json)["sites"][0]["number"]["timeLimit"].asLongOrNull)
    }

    @Test
    fun `a bot check stops the site for the run and for 30 minutes, and the other sites run`() = runTest {
        val http = FakeExtractorHttpClient(
            getResponder = { url, _ ->
                when {
                    url.startsWith("https://www.tiktok.com/") -> FakeExtractorHttpClient.html(TT_CHALLENGE, url)
                    url.startsWith(WIDGET) -> FakeExtractorHttpClient.json(Fixtures.read("x/tweet_video.json"), url)
                    else -> null
                }
            },
            probeResponder = { _, _ -> ExtractorProbeResult.Answered(206) },
        )
        val census = census(AppMain(http), http)
        val links = mapOf("tiktok" to mapOf(1 to TT_POST, 2 to TT_POST_2), "x" to mapOf(1 to X_POST))

        val first = census.run(listOf(SiteWays.TIKTOK, SiteWays.X), links, null)

        val tiktok = json(first.json)["sites"][0]
        assertEquals("bot check", tiktok["stop"].asStringOrNull)
        assertEquals("BOT_CHECK", tiktok["rows"][0]["main"]["reason"].asStringOrNull)
        assertEquals(2L, tiktok["rows"][0]["main"]["botChecks"].asLongOrNull)
        assertEquals("site stopped", tiktok["rows"][0]["master"]["why"].asStringOrNull)
        assertEquals("site stopped (bot check)", tiktok["rows"][1]["skipped"].asStringOrNull)
        assertEquals("rows", json(first.json)["sites"][1]["rows"][0]["class"].asStringOrNull)
        assertEquals("TikTok: bot check", first.stopped)
        assertEquals(
            "YFT site census: TikTok recoverable 0 of 1, Master 0, stopped (bot check) · X recoverable 0 of 1, Master 0",
            first.firstLine,
        )
        val state = json(first.stateJson)
        assertEquals(NOW_MS + SiteCensus.COOLDOWN_MILLIS, state["stops"]["tiktok"]["until"].asLongOrNull)
        assertEquals("bot check", state["stops"]["tiktok"]["why"].asStringOrNull)
        assertEquals(NOW_MS, state["lastAt"].asLongOrNull)
        assertEquals("the last run's export", SiteCensus.TOOL_LABEL, state["last"]["tool"].asStringOrNull)
        val tiktokAsks = http.requestedUrls.count { it.startsWith("https://www.tiktok.com/") }

        now = NOW_MS + 10 * MINUTE
        assertEquals(
            "Last run: ${SiteCensusJson.dateOf(NOW_MS)}, 10 min ago. Stopped: TikTok 20 min (bot check).",
            SiteCensus.statusOf(first.stateJson, now),
        )
        val second = census.run(listOf(SiteWays.TIKTOK, SiteWays.X), links, first.stateJson)

        assertEquals("cooldown 20 min (bot check)", json(second.json)["sites"][0]["skipped"].asStringOrNull)
        assertTrue(second.firstLine, second.firstLine.startsWith("YFT site census: TikTok cooldown 20 min (bot check) · X "))
        assertEquals("TikTok is not asked", tiktokAsks, http.requestedUrls.count { it.startsWith("https://www.tiktok.com/") })
        assertEquals(
            "the stop is kept",
            NOW_MS + SiteCensus.COOLDOWN_MILLIS,
            json(second.stateJson)["stops"]["tiktok"]["until"].asLongOrNull,
        )

        now = NOW_MS + 31 * MINUTE
        val third = census.run(listOf(SiteWays.TIKTOK), links, second.stateJson)

        assertNull(json(third.json)["sites"][0]["skipped"])
        assertTrue(http.requestedUrls.count { it.startsWith("https://www.tiktok.com/") } > tiktokAsks)
        assertEquals(now + SiteCensus.COOLDOWN_MILLIS, json(third.stateJson)["stops"]["tiktok"]["until"].asLongOrNull)
    }

    @Test
    fun `a 429 to main or to V stops the site with a cooldown`() = runTest {
        val limited = FakeExtractorHttpClient(
            getResponder = { url, _ ->
                ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429).takeIf { url.startsWith(WIDGET) }
            },
        )
        val vLimited = xSite(probe = ExtractorProbeResult.Refused(SiteExtractionFailure.RATE_LIMITED, 429))
        val links = mapOf("x" to mapOf(1 to X_POST, 2 to X_POST_2))

        val mainStop = census(AppMain(limited), limited).run(listOf(SiteWays.X), links, null)
        val vStop = census(AppMain(vLimited), vLimited).run(listOf(SiteWays.X), links, null)

        for (outcome in listOf(mainStop, vStop)) {
            val site = json(outcome.json)["sites"][0]
            assertEquals(outcome.json, "429", site["stop"].asStringOrNull)
            assertEquals("site stopped", site["rows"][0]["master"]["why"].asStringOrNull)
            assertEquals("site stopped (429)", site["rows"][1]["skipped"].asStringOrNull)
            assertEquals("429", json(outcome.stateJson)["stops"]["x"]["why"].asStringOrNull)
            assertTrue(outcome.report, outcome.report.lines().any { it.endsWith("; stopped: 429 (30 min)") })
        }
        assertEquals("one ask, then nothing", 1, limited.requestedUrls.size)
        val v = json(vStop.json)["sites"][0]["rows"][0]["v"]
        assertEquals("fail", v["verdict"].asStringOrNull)
        assertEquals(true, v["rateLimited"].asBooleanOrNull)
        assertEquals(1, vLimited.requestedUrls.size)
        assertEquals(1, vLimited.probedUrls.size)
    }

    @Test
    fun `the request and byte budgets stop the site without a cooldown`() = runTest {
        val http = FakeExtractorHttpClient()
        val recorder = LookupRecorder(http)
        val many = ScriptedMain { _, _ ->
            repeat(SiteCensus.MAX_REQUESTS) { recorder.get("$WIDGET?id=$it", emptyMap(), 1_024) }
            MainAnswer.Found(listOf(row(X_FILE, 1280)))
        }
        var bytes = 0L
        val heavy = ScriptedMain { _, _ ->
            bytes += SiteCensus.MAX_BYTES + 1
            MainAnswer.Found(listOf(row(X_FILE, 1280)))
        }
        val links = mapOf("x" to mapOf(1 to X_POST, 2 to X_POST_2))

        val byRequests = census(many, http).run(listOf(SiteWays.X), links, null)
        val byBytes = census(heavy, http, meter = { bytes }).run(listOf(SiteWays.X), links, null)

        for (outcome in listOf(byRequests, byBytes)) {
            val site = json(outcome.json)["sites"][0]
            assertEquals(outcome.json, "budget", site["stop"].asStringOrNull)
            assertEquals("budget", site["rows"][0]["vSkipped"].asStringOrNull)
            assertEquals("site stopped", site["rows"][0]["master"]["why"].asStringOrNull)
            assertEquals("site stopped (budget)", site["rows"][1]["skipped"].asStringOrNull)
            assertEquals("no cooldown after a budget", null, json(outcome.stateJson)["stops"]["x"])
            assertTrue(outcome.report, outcome.report.lines().any { it.endsWith("; stopped: budget") })
            assertEquals("budget", outcome.stopped?.substringAfter("X: "))
        }
        assertEquals(SiteCensus.MAX_REQUESTS.toLong(), json(byRequests.json)["sites"][0]["req"].asLongOrNull)
        assertEquals(-1L, json(byRequests.json)["sites"][0]["bytes"].asLongOrNull)
        assertEquals(SiteCensus.MAX_BYTES + 1, json(byBytes.json)["sites"][0]["bytes"].asLongOrNull)
        assertTrue(byBytes.report, "40.0 MB" in byBytes.report)
        assertTrue("V never asked", http.probedUrls.isEmpty())
    }

    @Test
    fun `links no adapter handles, another site's links and slots outside the list ask nothing`() = runTest {
        val http = FakeExtractorHttpClient()
        val links = mapOf(
            "x" to mapOf(1 to "https://example.com/clip.mp4", 2 to TT_POST, 3 to "   ", 9 to X_POST),
        )

        val outcome = census(AppMain(http), http).run(listOf(SiteWays.FACEBOOK, SiteWays.X), links, null)

        val sites = json(outcome.json)["sites"].asArrayOrEmpty
        assertEquals("no links", sites[0]["skipped"].asStringOrNull)
        assertEquals(listOf(1L, 2L), sites[1]["rows"].asArrayOrEmpty.map { it["slot"].asLongOrNull })
        assertEquals(
            listOf(SiteCensus.NOT_HANDLED, SiteCensus.ANOTHER_SITE),
            sites[1]["rows"].asArrayOrEmpty.map { it["skipped"].asStringOrNull },
        )
        assertEquals(
            listOf("slot 9: outside the site's list, skipped"),
            sites[1]["notes"].asArrayOrEmpty.map { it.asStringOrNull },
        )
        assertEquals("YFT site census: Facebook no links · X recoverable 0 of 0, Master 0", outcome.firstLine)
        assertTrue("nothing asked", http.requestedUrls.isEmpty() && http.probedUrls.isEmpty())
        assertTrue(outcome.report, outcome.report.lines().contains("  slot 2 video: not looked up (another site's link)"))
    }

    @Test
    fun `a short link is never given to Master's contracts`() = runTest {
        val http = FakeExtractorHttpClient()
        val main = ScriptedMain { _, _ -> MainAnswer.Failed(SiteExtractionFailure.NO_MEDIA_FOUND) }

        val outcome = census(main, http).run(listOf(SiteWays.TIKTOK), mapOf("tiktok" to mapOf(1 to TT_SHORT)), null)

        val row = json(outcome.json)["sites"][0]["rows"][0]
        assertEquals("recoverable", row["class"].asStringOrNull)
        assertEquals("not run", row["master"]["run"].asStringOrNull)
        assertEquals("short link", row["master"]["why"].asStringOrNull)
        assertTrue(http.requestedUrls.isEmpty())
    }

    @Test
    fun `a slot's least height is judged on main's tallest row, and V never asks another host`() = runTest {
        val http = FakeExtractorHttpClient(probeResponder = { _, _ -> ExtractorProbeResult.Answered(206) })
        var height = 540
        val main = ScriptedMain { _, _ -> MainAnswer.Found(listOf(row(OTHER_FILE, height, TT_POST))) }
        val links = mapOf("tiktok" to mapOf(1 to TT_POST, DESKTOP_HD to TT_POST_2))

        val low = census(main, http).run(listOf(SiteWays.TIKTOK), links, null)
        height = 1080
        val high = census(main, http).run(listOf(SiteWays.TIKTOK), links, null)

        val lowRows = json(low.json)["sites"][0]["rows"].asArrayOrEmpty
        assertEquals(listOf(true, false), lowRows.map { it["expected"].asBooleanOrNull })
        assertEquals("tallest below 720", lowRows[1]["note"].asStringOrNull)
        assertEquals("unknown", lowRows[0]["v"]["verdict"].asStringOrNull)
        assertEquals(SiteRowCheck.ANOTHER_HOST, lowRows[0]["v"]["why"].asStringOrNull)
        val highRows = json(high.json)["sites"][0]["rows"].asArrayOrEmpty
        assertEquals(listOf(true, true), highRows.map { it["expected"].asBooleanOrNull })
        assertTrue("V asked nothing", http.probedUrls.isEmpty())
    }

    @Test
    fun `the hidden page's mark names it reached, and its bot check stops the site`() = runTest {
        val http = FakeExtractorHttpClient()
        val main = ScriptedMain { link, _ ->
            if (link == TT_POST) {
                MainAnswer.Found(listOf(row(OTHER_FILE, 1080, TT_POST)), hiddenPage = AnswerMark.DATA)
            } else {
                MainAnswer.Failed(SiteExtractionFailure.NO_MEDIA_FOUND, hiddenPage = AnswerMark.BOT_CHECK)
            }
        }

        val outcome = census(main, http).run(
            listOf(SiteWays.TIKTOK),
            mapOf("tiktok" to mapOf(1 to TT_POST, 2 to TT_POST_2, 3 to TT_POST_3)),
            null,
        )

        val rows = json(outcome.json)["sites"][0]["rows"].asArrayOrEmpty
        assertEquals("data", rows[0]["main"]["hiddenPage"].asStringOrNull)
        assertEquals(
            listOf(PHONE_PAGE, DESKTOP_PAGE, DOWNLOAD_SERVICE),
            rows[0]["main"]["notReached"].asArrayOrEmpty.map { it.asStringOrNull },
        )
        assertEquals("bot check", rows[1]["main"]["hiddenPage"].asStringOrNull)
        assertEquals("site stopped", rows[1]["master"]["why"].asStringOrNull)
        assertEquals("site stopped (bot check)", rows[2]["skipped"].asStringOrNull)
        assertEquals("bot check", json(outcome.stateJson)["stops"]["tiktok"]["why"].asStringOrNull)
        assertTrue(
            outcome.report,
            outcome.report.lines().contains("    hidden page: data; not reached: phone page, desktop page, download service"),
        )
        assertFalse(HIDDEN_PAGE in rows[1]["main"]["notReached"].asArrayOrEmpty.map { it.asStringOrNull })
    }

    @Test
    fun `a lookup that throws is an error row, and the run goes on`() = runTest {
        val http = FakeExtractorHttpClient()
        val main = ScriptedMain { link, _ ->
            check(link != X_POST) { "fixture failure $X_POST" }
            MainAnswer.Found(listOf(row(X_FILE, 720)))
        }

        val outcome = census(main, http).run(listOf(SiteWays.X), mapOf("x" to mapOf(1 to X_POST, 2 to X_POST_2)), null)

        val rows = json(outcome.json)["sites"][0]["rows"].asArrayOrEmpty
        assertEquals(listOf("error", "rows"), rows.map { it["class"].asStringOrNull })
        assertEquals("error", rows[0]["main"]["answer"].asStringOrNull)
        assertEquals("error", rows[0]["master"]["why"].asStringOrNull)
        assertFalse("the error's text is not kept", "fixture failure" in outcome.json + outcome.report)
    }

    @Test
    fun `the report, the export and the state hold no link, ID, handle, cookie or address`() = runTest {
        val http = xSite()
        val main = AppMain(http, cookies = mapOf("x" to "auth_token=$COOKIE_VALUE"))

        val outcome = census(main, http).run(
            listOf(SiteWays.X),
            mapOf("x" to mapOf(1 to X_POST, 2 to X_POST_2)),
            null,
            context = listOf("network: Wi-Fi"),
        )

        val texts = listOf(outcome.firstLine, outcome.report, outcome.json, outcome.stateJson, outcome.toString())
        for (text in texts) {
            for (secret in listOf("fixture_user", "1785999999999999999", "1785999999999999998", COOKIE_VALUE, "http", "twimg", "x.com")) {
                assertFalse("$secret in $text", secret in text)
            }
        }
        assertEquals(listOf("network: Wi-Fi"), json(outcome.json)["notes"].asArrayOrEmpty.map { it.asStringOrNull })
        assertTrue("the run was real", "V pass 206" in outcome.report)
        assertTrue(
            CensusText.secretsOf(X_POST, null).containsAll(listOf(X_POST, "fixture_user", "1785999999999999999")),
        )
        assertFalse("a short number is the report's own", "12345" in CensusText.secretsOf("$X_POST?s=12345", null))
    }

    @Test
    fun `a broken, foreign or old state file is no state, and a first run says so`() {
        assertEquals("No run yet.", SiteCensus.statusOf(null, NOW_MS))
        for (text in listOf("", "{", "[]", "{\"fmt\":2,\"stops\":{\"x\":{\"until\":9999999999999,\"why\":\"429\"}}}")) {
            val state = SiteCensusState.read(text)
            assertTrue(text, state.stops.isEmpty())
            assertEquals(text, 0L, state.lastAtMillis)
        }
        val state = SiteCensusState.read(
            "{\"fmt\":1,\"stops\":{\"youtube\":{\"until\":9999999999999,\"why\":\"429\"}," +
                "\"x\":{\"until\":9999999999999,\"why\":\"<script>\"}},\"lastAt\":5}",
        )
        assertEquals("only the census's sites", setOf("x"), state.stops.keys)
        assertEquals("only the census's reasons", "stop", state.stops.getValue("x").why)
        assertEquals(5L, state.lastAtMillis)
    }

    private fun census(
        main: MainLookup,
        http: ExtractorHttpClient,
        meter: SiteCensus.Meter = SiteCensus.Meter.NONE,
    ) = SiteCensus(main, http, VALID, meter, clock)

    /** X's widget answering the video post's fixture, and its files answering V's [probe]. */
    private fun xSite(probe: ExtractorProbeResult = ExtractorProbeResult.Answered(206, 1_000L)) = FakeExtractorHttpClient(
        getResponder = { url, _ ->
            FakeExtractorHttpClient.json(Fixtures.read("x/tweet_video.json"), url).takeIf { url.startsWith(WIDGET) }
        },
        probeResponder = { _, _ -> probe },
    )

    private fun json(text: String): JsonValue = checkNotNull(BoundedJsonParser.parse(text)) { "not JSON: $text" }

    /** Main as the app builds it over [http]: the four adapters, each ask through the recorder. */
    private class AppMain(
        http: ExtractorHttpClient,
        private val cookies: Map<String, String> = emptyMap(),
    ) : MainLookup {
        private val recorder = LookupRecorder(http)
        private val registry = SiteExtractorRegistry(
            listOf(FacebookExtractor(recorder), TikTokExtractor(recorder), InstagramExtractor(recorder), XExtractor(recorder)),
        )

        override suspend fun identify(link: String): LinkIdentity? =
            (registry.select(link) as? SiteAdapterSelection.Matched)?.identity?.let { identity ->
                LinkIdentity(identity, BROWSER_AGENT, cookies[identity.siteId])
            }

        override suspend fun lookup(link: String, identity: LinkIdentity): MainAnswer {
            val selected = registry.select(link) as? SiteAdapterSelection.Matched ?: return MainAnswer.NotHandled
            val request = SiteExtractionRequest(
                identity.page,
                BrowserRequestContext(identity.page.canonicalPageUrl, identity.userAgent, identity.cookie),
                NOW_MS,
            )
            return when (val result = selected.extractor.extract(request)) {
                is SiteExtractionResult.Success -> MainAnswer.Found(result.candidates)
                is SiteExtractionResult.Failure -> MainAnswer.Failed(result.reason, result.httpStatusCode)
            }
        }
    }

    /** Main as a script: main's own identities, the browser's identity, [answer]'s answer. */
    private class ScriptedMain(
        private val cookies: Map<String, String> = emptyMap(),
        private val answer: suspend (link: String, identity: LinkIdentity) -> MainAnswer,
    ) : MainLookup {
        override suspend fun identify(link: String): LinkIdentity? =
            identityOf(link)?.let { LinkIdentity(it, BROWSER_AGENT, cookies[it.siteId]) }

        override suspend fun lookup(link: String, identity: LinkIdentity): MainAnswer = answer(link, identity)
    }

    private companion object {
        const val NOW_MS = 1_791_000_000_000L
        const val MINUTE = 60_000L
        const val COOKIE_VALUE = "fixtureCookieValue"
        const val WIDGET = "https://cdn.syndication.twimg.com/tweet-result"
        const val X_POST = "https://x.com/fixture_user/status/1785999999999999999"
        const val X_POST_2 = "https://x.com/fixture_user/status/1785999999999999998"
        const val X_FILE = "https://video.twimg.com/ext_tw_video/1785999990000000000/pu/vid/avc1/720x1280/a720.mp4?tag=12"
        const val TT_POST_2 = "https://www.tiktok.com/@fixture_user/video/7311234567890123457"
        const val TT_POST_3 = "https://www.tiktok.com/@fixture_user/video/7311234567890123458"
        const val TT_SHORT = "https://vm.tiktok.com/ZMfixture1/"
        const val TT_CDN_FILE = "https://v16-webapp-prime.tiktokcdn.com/video/tos/useast5/fixture/embed.mp4" +
            "?mime_type=video_mp4&expire=4102444800"
        const val OTHER_FILE = "https://v16m.example-cdn.test/hd/fixture.mp4"

        /** The slots of the site table these tests use (SiteWays). */
        const val X_REMOVED = 8
        const val SIGN_IN_ONLY_IG = 9
        const val DESKTOP_HD = 10

        /** Master's embed player's shape with a file on TikTok's media host, its size stated. */
        val TT_EMBED_CDN: String = TT_EMBED
            .replace("https://v45.example-cdn.test/video/tos/useast5/embed.mp4?mime_type=video_mp4", TT_CDN_FILE)
            .replace("\"urls\":", "\"videoMeta\":{\"width\":720,\"height\":1280,\"duration\":12},\"urls\":")

        val VALID = MasterMediaValidator { candidate, _ -> ValidationResult.Valid(candidate) }

        private val ADAPTERS = FakeExtractorHttpClient().let { none ->
            SiteExtractorRegistry(listOf(FacebookExtractor(none), TikTokExtractor(none), InstagramExtractor(none), XExtractor(none)))
        }

        fun identityOf(link: String): SitePageIdentity? = (ADAPTERS.select(link) as? SiteAdapterSelection.Matched)?.identity

        fun row(url: String, height: Int, page: String = X_POST) = MediaCandidate(
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
