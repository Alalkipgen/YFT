package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.PlayerScriptChallengeKind
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.master.modules.youtube.YouTubeClientProfiles
import com.alal.yft.extractor.master.modules.youtube.YouTubePlayerResponseParser
import com.alal.yft.extractor.master.modules.youtube.ladder.ClientCensus.Mode
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.master.modules.youtube.testing.FakePlayerScriptRunner
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 1.3 D0: the client census and the weekly check against a scripted YouTube; no live calls. */
class ClientCensusTest {
    private var now = START

    @Test
    fun `a full census asks each client in order and records class, counts, V, S, H and versions`() = runTest {
        val http = CensusWorld().standard(Census.V1).sources().http()
        val scripts = FakePlayerScriptRunner()

        val outcome = census(http, scripts).run(Mode.FULL, mapOf(1 to Census.V1), null)

        assertEquals("FIRST RUN — nothing to compare yet", outcome.firstLine)
        assertNull(outcome.stopped)
        assertEquals(
            listOf("VISIONOS", "ANDROID", "WEB_EMBEDDED_PLAYER", "MWEB", "MWEB"),
            http.postedBodies.map(CensusWorld::clientOf),
        )
        val run = lastRun(outcome)
        val vision = ask(run, 1, "visionos", "R0")
        assertEquals(CensusClass.DIRECT, vision.finalClass)
        assertEquals("OK", vision.status)
        assertEquals(true, vision.bound)
        assertEquals(Census.LENGTH, vision.lengthSeconds)
        assertEquals(listOf(4, 0, 0, 0), listOf(vision.adaptive, vision.progressive, vision.protectedFormats, vision.missingSizes))
        assertEquals(listOf(720, 1080), vision.heights)
        assertEquals(listOf("avc1", "vp09"), vision.videoCodecs)
        assertEquals(listOf("mp4a"), vision.audioCodecs)
        assertEquals(listOf("PASS", "2xx", "avc1 720p"), listOf(vision.v, vision.vTarget, vision.vRow))
        assertTrue(vision.hls)
        assertTrue(Regex("^[0-9a-f]{12}$").matches(vision.shapeCore!!))
        assertTrue(vision.shapePaths!!.contains("streamingData.adaptiveFormats[].url"))

        val android = ask(run, 1, "android", "R0")
        assertEquals(listOf("DIRECT", "PASS", "progressive 360p"), listOf(android.finalClass.name, android.v, android.vRow))
        val embedded = ask(run, 1, "embedded", "R0")
        assertEquals(CensusClass.NEEDS_SCRIPT, embedded.answerClass)
        assertEquals(listOf("ok-sig+n", "PASS"), listOf(embedded.solve, embedded.v))
        assertEquals(Census.PAGE_VERSION, embedded.version)
        val mobile = ask(run, 1, "mweb", "R0")
        assertEquals(CensusClass.NEEDS_SCRIPT, mobile.answerClass)
        assertEquals(listOf("ok-n", "PASS"), listOf(mobile.solve, mobile.v))
        assertEquals(YouTubeClientProfiles.MOBILE_CLIENT_VERSION, mobile.version)

        val hls = run.hls.single()
        assertEquals("visionos", hls.client)
        assertEquals(
            HlsCheck.Result(
                master = "2xx", variant = "2xx", segment = "2xx", variants = 2, maxHeight = 1080, audio = "muxed",
                codecs = listOf("avc1", "mp4a"), key = "NONE", segments = 43, durationSeconds = 212.0,
                durationMatch = true, requests = 3,
            ),
            hls.result,
        )
        val page = run.pages.single()
        assertEquals(listOf("200", "MWEB", Census.PAGE_VERSION, Census.PLAYER_ID), listOf(page.http, page.clientName, page.clientVersion, page.playerId))
        assertTrue(page.signatureTimestamp && page.visitorData && page.apiKey)

        assertEquals(2, scripts.requests.size)
        assertTrue(scripts.requests.all { "/s/player/${Census.PLAYER_ID}/" in it.playerScriptUrl })
        assertEquals(
            setOf(PlayerScriptChallengeKind.SIGNATURE, PlayerScriptChallengeKind.RATE_PARAM),
            scripts.requests[0].challenges.map { it.kind }.toSet(),
        )
        assertEquals(listOf(PlayerScriptChallengeKind.RATE_PARAM), scripts.requests[1].challenges.map { it.kind })
        val solved = http.probedUrls[3]
        assertTrue(solved.contains("n=N" + Census.N_INPUT.reversed()) && solved.contains("sig=S" + Census.SIG_INPUT.reversed()))
        assertEquals(5, http.probedUrls.size)
        assertEquals(17, run.requests)
        assertTrue(run.notes.any { it.startsWith("alternates: none bundled") })
        // D7: the L steps on visionOS's DIRECT answer, in memory (no request counted above).
        val lLine = run.notes.single { it.startsWith("video 1 L (R0, ") }
        assertTrue(lLine, lLine.endsWith("round-trip same, L1 same, L2 same, L3 same, L5 n/a, L7 same"))
        assertTrue(lLine, ", V PASS): " in lLine)
        assertTrue(run.notes.contains("L steps on 1 visionOS answers (in memory, no request): round-trip 1/1 same, L1 1/1 same, L2 1/1 same, L3 1/1 same, L5 0/0 same (1 n/a), L7 1/1 same"))
    }

    @Test
    fun `the version sources are read once, judged against the bundled values, and the page's newer value is tried`() = runTest {
        val http = CensusWorld().standard(Census.V1).sources().http()

        val run = lastRun(census(http).run(Mode.FULL, mapOf(1 to Census.V1), null))

        fun fact(identity: String, source: String) = run.versions.single { it.identity == identity && it.source == source }
        assertEquals(listOf("1.02", "equal"), listOf(fact("visionos", "answer").value, fact("visionos", "answer").accepted))
        assertEquals("equal", fact("android", "answer").accepted)
        assertEquals("equal", fact("visionos", "ytdlp").accepted)
        assertEquals("equal", fact("android", "ytdlp").accepted)
        assertEquals("equal", fact("visionos", "youtubejs").accepted)
        val older = fact("android", "youtubejs")
        assertEquals(listOf("21.03.36", false, null), listOf(older.value, older.newer, older.accepted))
        val store = fact("visionos", "appstore")
        assertEquals(listOf(false, "21.40.4", "lists visionOS"), listOf(store.pattern, store.value, store.note))
        val play = fact("android", "playstore")
        assertEquals(listOf(false, "over 64 KiB"), listOf(play.reachable, play.note))
        assertEquals("yes", fact("embedded", "page").accepted)
        val mobile = fact("mweb", "page")
        assertEquals(listOf(Census.PAGE_VERSION, true, "yes"), listOf(mobile.value, mobile.newer, mobile.accepted))
        assertEquals(Census.PAGE_VERSION, ask(run, 1, "mweb", "SH").version)
        assertEquals(10, run.versions.size)
        val sources = CensusSources.DOCUMENTS.map { it.url }
        assertEquals(sources, http.requestedUrls.filter { it in sources })
    }

    @Test
    fun `identical runs say NO CHANGE and a visionOS answer that needs a signature says BROKEN`() = runTest {
        val world = CensusWorld().standard(Census.V1).sources()
        val census = census(world.http())

        val first = census.run(Mode.FULL, mapOf(1 to Census.V1), null)
        now += 2 * HOUR
        val second = census.run(Mode.FULL, mapOf(1 to Census.V1), first.stateJson)
        now += 8 * DAY
        world.answer(Census.V1, "VISIONOS", Census.visionOsCiphered(Census.V1))
        val third = census.run(Mode.FULL, mapOf(1 to Census.V1), second.stateJson)

        assertEquals("NO CHANGE — 0 days since the last run", second.firstLine)
        assertTrue(third.firstLine, third.firstLine.startsWith("BROKEN ("))
        assertTrue(third.firstLine, third.firstLine.endsWith("— 8 days since the last run"))
        assertTrue(third.report.contains("BROKEN visionos R0 video 1: class DIRECT → NEEDS_SCRIPT → §7 step 4"))
        assertTrue(third.report.contains("CHANGED visionos R0 video 1: HLS address gone → §7 step 7"))
        assertTrue(third.report.contains("BREAKING: streamingData.adaptiveFormats[].url"))
        assertEquals(3, CensusJson.readState(third.stateJson).runs.size)
    }

    @Test
    fun `visionOS is asked again with the page's signals only when its first answer carried a cipher`() = runTest {
        val world = CensusWorld().standard(Census.V5).sources()
        world.answer(Census.V5, "VISIONOS", Census.visionOsCiphered(Census.V5))
        val http = world.http()

        val run = lastRun(census(http).run(Mode.FULL, mapOf(5 to Census.V5), null))

        val asks = http.postedBodies.indices.filter { CensusWorld.clientOf(http.postedBodies[it]) == "VISIONOS" }
        assertEquals(2, asks.size)
        val (plain, again) = asks.map(http.postedBodies::get)
        assertEquals(YouTubeClientProfiles.VISION_OS.playerRequestBody(Census.V5, null, null), plain)
        assertTrue(again.contains("\"signatureTimestamp\":${Census.STS}"))
        assertTrue(again.contains("\"visitorData\":\"${Census.VISITOR}\""))
        assertEquals(Census.PLAYER_URL, http.postedUrls[asks[1]])
        assertEquals(Census.VISITOR, http.postedHeaders[asks[1]]["X-Goog-Visitor-Id"])
        val first = ask(run, 5, "visionos", "R0")
        assertEquals(listOf("NEEDS_SCRIPT", "skip-needs-S"), listOf(first.answerClass.name, first.v))
        assertEquals(2, first.protectedFormats)
        val signed = ask(run, 5, "visionos", "S")
        assertEquals(listOf("ok-sig+n", "PASS"), listOf(signed.solve, signed.v))

        val direct = CensusWorld().standard(Census.V1).http()
        census(direct).run(Mode.FULL, mapOf(1 to Census.V1), null)
        assertEquals(1, direct.postedBodies.count { CensusWorld.clientOf(it) == "VISIONOS" })
    }

    @Test
    fun `player requests are R6's, built by the copied builder, with no cookie, token or sign-in header`() = runTest {
        val http = CensusWorld().standard(Census.V1).sources().http()

        census(http).run(Mode.FULL, mapOf(1 to Census.V1), null)

        val signals = YouTubePlayerResponseParser.pageSignals(Census.page())
        val index = http.postedBodies.indices.groupBy { CensusWorld.clientOf(http.postedBodies[it]) }
        fun headers(client: String) = http.postedHeaders[index.getValue(client).first()]
        fun body(client: String) = http.postedBodies[index.getValue(client).first()]
        val common = mapOf("Accept" to "application/json", "Accept-Language" to "en-US,en;q=0.9", "Origin" to "https://www.youtube.com")
        assertEquals(
            common + mapOf(
                "User-Agent" to YouTubeClientProfiles.VISION_OS.userAgent!!,
                "X-YouTube-Client-Name" to "101",
                "X-YouTube-Client-Version" to "1.02",
            ),
            headers("VISIONOS"),
        )
        assertEquals(YouTubeClientProfiles.VISION_OS.playerRequestBody(Census.V1, null, null), body("VISIONOS"))
        assertEquals(Census.PLAYER_URL, http.postedUrls[index.getValue("VISIONOS").first()])

        assertEquals(
            common + mapOf(
                "User-Agent" to YouTubeClientProfiles.ANDROID.userAgent!!,
                "X-YouTube-Client-Name" to "3",
                "X-YouTube-Client-Version" to YouTubeClientProfiles.ANDROID.clientVersion,
                "X-Goog-Visitor-Id" to Census.VISITOR,
            ),
            headers("ANDROID"),
        )
        val embedded = YouTubeClientProfiles.embedded(signals)
        assertEquals(
            common + mapOf(
                "User-Agent" to Census.UA,
                "Referer" to "https://www.youtube.com/embed/${Census.V1}",
                "X-YouTube-Client-Name" to embedded.clientNameId.toString(),
                "X-YouTube-Client-Version" to Census.PAGE_VERSION,
                "X-Goog-Visitor-Id" to Census.VISITOR,
            ),
            headers("WEB_EMBEDDED_PLAYER"),
        )
        assertEquals(embedded.playerRequestBody(Census.V1, Census.VISITOR, Census.STS), body("WEB_EMBEDDED_PLAYER"))
        assertEquals(Census.PLAYER_URL + "&key=" + Census.API_KEY, http.postedUrls[index.getValue("WEB_EMBEDDED_PLAYER").first()])
        val mobile = YouTubeClientProfiles.mobileWeb()
        assertEquals(
            common + mapOf(
                "User-Agent" to mobile.userAgent!!,
                "Referer" to "https://www.youtube.com/watch?v=${Census.V1}",
                "X-YouTube-Client-Name" to mobile.clientNameId.toString(),
                "X-YouTube-Client-Version" to mobile.clientVersion,
                "X-Goog-Visitor-Id" to Census.VISITOR,
            ),
            headers("MWEB"),
        )
        assertEquals(mobile.playerRequestBody(Census.V1, Census.VISITOR, Census.STS), body("MWEB"))

        val signIn = setOf("cookie", "cookie2", "authorization", "x-goog-authuser", "x-origin", "x-goog-pageid")
        for (sent in http.postedHeaders + http.requestedHeaders + http.probedHeaders) {
            assertTrue(sent.keys.toString(), sent.keys.none { it.lowercase() in signIn })
        }
        assertTrue(http.postedBodies.none { "poToken" in it || "serviceIntegrityDimensions" in it })
        val download = mapOf("User-Agent" to Census.UA, "Referer" to "https://www.youtube.com/watch?v=${Census.V1}")
        assertTrue(http.probedHeaders.all { it == download })
        assertEquals(download, http.requestedHeaders[http.requestedUrls.indexOf(Census.MASTER)])
        assertTrue(http.probedUrls.all { it.startsWith("https://${Census.HOST}/") })
    }

    @Test
    fun `a 403 on the checked row makes the final class TOKEN_NEEDED`() = runTest {
        val world = CensusWorld().standard(Census.V1)
        world.probe = { url ->
            if ("itag=136" in url && "sig=" !in url && "n=" !in url) {
                ExtractorProbeResult.Refused(SiteExtractionFailure.HTTP_STATUS, 403, null)
            } else {
                ExtractorProbeResult.Answered(206, 1_000)
            }
        }

        val vision = ask(lastRun(census(world.http()).run(Mode.FULL, mapOf(1 to Census.V1), null)), 1, "visionos", "R0")

        assertEquals(CensusClass.DIRECT, vision.answerClass)
        assertEquals(CensusClass.TOKEN_NEEDED, vision.finalClass)
        assertEquals(listOf("FAIL", "403", "2xx"), listOf(vision.v, vision.vTarget, vision.vControl))
    }

    @Test
    fun `a bot check on visionOS asked again with visitor data stops the run and no run starts for 30 minutes`() = runTest {
        val world = CensusWorld().standard(Census.V1).standard(Census.V2).sources().visionOsBotCheck(Census.V1, always = true)
        val http = world.http()
        val census = census(http)
        val videos = mapOf(1 to Census.V1, 2 to Census.V2)

        val stopped = census.run(Mode.FULL, videos, null)
        now += 10 * MINUTE
        val waiting = census.run(Mode.FULL, videos, stopped.stateJson)

        assertEquals("bot-check", stopped.stopped)
        assertEquals("BROKEN (2) — first run", stopped.firstLine)
        assertTrue(
            stopped.report.contains(
                "BROKEN bot check on visionos R0+vd video 1 (a request with visitor data) — the run stopped; no census for 30 min → §7 step 8",
            ),
        )
        assertTrue(
            stopped.report.contains("CHANGED visionos R0 video 1: bot check, answered as R6 (visionOS with visitor data: BOT_CHECK) → §7 step 8"),
        )
        val run = lastRun(stopped)
        assertEquals(CensusClass.BOT_CHECK, ask(run, 1, "visionos", "R0").answerClass)
        assertEquals(CensusClass.BOT_CHECK, ask(run, 1, "visionos", "R0+vd").answerClass)
        assertEquals(listOf("VISIONOS", "VISIONOS"), http.postedBodies.map(CensusWorld::clientOf))
        assertEquals(listOf(Census.pageUrl(Census.V1)), http.requestedUrls)
        assertTrue(http.probedUrls.isEmpty())
        assertEquals(START + 30 * MINUTE, CensusJson.readState(stopped.stateJson).cooldownUntil)
        assertEquals("COOLDOWN — a bot check stopped an earlier run; try again in 20 min", waiting.firstLine)
        assertEquals("cooldown", waiting.stopped)
        assertEquals(2, http.postedBodies.size)
        assertTrue(ClientCensus.statusOf(waiting.stateJson, now).endsWith("Bot check cooldown: 20 min left."))

        now += 21 * MINUTE
        census.run(Mode.FULL, videos, waiting.stateJson)
        assertEquals(4, http.postedBodies.size)
    }

    @Test
    fun `a bot check on visionOS's first request is answered as R6 answers it and the run goes on`() = runTest {
        val world = CensusWorld().standard(Census.V1).standard(Census.V2).sources(vision = "1.03").visionOsBotCheck(Census.V1)
        val http = world.http()

        val outcome = census(http).run(Mode.FULL, mapOf(1 to Census.V1, 2 to Census.V2), null)

        assertNull(outcome.stopped)
        assertEquals(0L, CensusJson.readState(outcome.stateJson).cooldownUntil)
        // B7 (owner, 2026-10-15): the newer values are tried on video 2, the first video without a bot check.
        assertEquals(
            listOf("VISIONOS", "VISIONOS", "VISIONOS", "ANDROID", "WEB_EMBEDDED_PLAYER", "MWEB", "VISIONOS", "MWEB"),
            http.postedBodies.map(CensusWorld::clientOf),
        )
        assertEquals(listOf("1.03"), http.postedBodies.map(CensusWorld::versionOf).filter { it == "1.03" })
        assertTrue(http.postedBodies.single { CensusWorld.versionOf(it) == "1.03" }.contains(Census.V2))
        val run = lastRun(outcome)
        assertEquals(listOf("visionos R0", "visionos R0+vd"), run.asks.filter { it.video == 1 }.map { "${it.client} ${it.step}" })
        assertEquals(CensusClass.BOT_CHECK, ask(run, 1, "visionos", "R0").answerClass)
        val again = ask(run, 1, "visionos", "R0+vd")
        assertEquals(listOf("DIRECT", "PASS"), listOf(again.finalClass.name, again.v))
        assertEquals(1, run.pages.count { it.video == 1 })
        // B5 ((က+)): the H check on the R0+vd answer, which is DIRECT.
        assertEquals(listOf(1 to "visionos", 2 to "visionos"), run.hls.map { it.video to it.client })
        assertEquals(listOf("2xx", "2xx", "2xx"), run.hls.first().result.let { listOf(it.master, it.variant, it.segment) })
        assertEquals(
            listOf("visionos R0", "android R0", "embedded R0", "mweb R0", "visionos SH", "mweb SH"),
            run.asks.filter { it.video == 2 }.map { "${it.client} ${it.step}" },
        )
        assertTrue(
            run.notes.contains(
                "video 1: bot check on visionOS without visitor data; answered as R6: the watch page once, " +
                    "visionOS once more with its visitor data, no other client for this video",
            ),
        )
        assertTrue(run.notes.contains("version acceptance: on video 2, the first video without a bot check and with a watch page"))
        assertTrue(
            run.notes.toString(),
            run.notes.contains("mweb video 2: formats without an address — bundled 2.20260708.05.00: 0, page's 2.20261002.01.00: 0"),
        )
        assertEquals("FIRST RUN — nothing to compare yet", outcome.firstLine)
        assertTrue(
            outcome.report.contains("INFO visionos R0 video 1: bot check, answered as R6 (visionOS with visitor data: DIRECT, V PASS)"),
        )
        assertTrue(outcome.report.contains(" · 2 videos (1, 2) · "))
        assertFalse(outcome.report.contains("§7 step 8"))
    }

    @Test
    fun `every video's bot check on visionOS's first request is answered as R6 and the run goes on`() = runTest {
        val world = CensusWorld().standard(Census.V1).standard(Census.V2).sources()
            .visionOsBotCheck(Census.V1).visionOsBotCheck(Census.V2)
        val http = world.http()

        val outcome = census(http).run(Mode.FULL, mapOf(1 to Census.V1, 2 to Census.V2), null)

        // R6 answers each lookup on its own (owner, 2026-10-15), and so does the census.
        assertNull(outcome.stopped)
        assertEquals(0L, CensusJson.readState(outcome.stateJson).cooldownUntil)
        assertEquals(List(4) { "VISIONOS" }, http.postedBodies.map(CensusWorld::clientOf))
        assertEquals(listOf(Census.pageUrl(Census.V1), Census.pageUrl(Census.V2)), http.requestedUrls.filter { it.contains("/watch?") })
        val run = lastRun(outcome)
        for (video in 1..2) {
            assertEquals(listOf("visionos R0", "visionos R0+vd"), run.asks.filter { it.video == video }.map { "${it.client} ${it.step}" })
            assertEquals(listOf("DIRECT", "PASS"), ask(run, video, "visionos", "R0+vd").let { listOf(it.finalClass.name, it.v) })
            assertTrue(
                run.notes.contains(
                    "video $video: bot check on visionOS without visitor data; answered as R6: the watch page once, " +
                        "visionOS once more with its visitor data, no other client for this video",
                ),
            )
            assertTrue(
                outcome.report.contains(
                    "INFO visionos R0 video $video: bot check, answered as R6 (visionOS with visitor data: DIRECT, V PASS)",
                ),
            )
        }
        assertEquals("FIRST RUN — nothing to compare yet", outcome.firstLine)
    }

    @Test
    fun `a bot check on a request that already carries visitor data stops the run at once`() = runTest {
        val again = CensusWorld().standard(Census.V1).standard(Census.V2).sources()
            .visionOsBotCheck(Census.V1).visionOsBotCheck(Census.V2, always = true)
        val againHttp = again.http()
        val other = CensusWorld().standard(Census.V1).standard(Census.V2).sources()
        other.answer(Census.V1, "ANDROID", Census.botCheck(Census.V1))
        val otherHttp = other.http()
        val videos = mapOf(1 to Census.V1, 2 to Census.V2)

        val second = census(againHttp).run(Mode.FULL, videos, null)
        val android = census(otherHttp).run(Mode.FULL, videos, null)

        assertEquals("bot-check", second.stopped)
        assertEquals(List(4) { "VISIONOS" }, againHttp.postedBodies.map(CensusWorld::clientOf))
        assertEquals(Census.VISITOR, againHttp.postedHeaders.last()["X-Goog-Visitor-Id"])
        assertEquals(START + 30 * MINUTE, CensusJson.readState(second.stateJson).cooldownUntil)
        assertEquals("BROKEN (2) — first run", second.firstLine)
        assertTrue(second.report.contains("BROKEN bot check on visionos R0+vd video 2 (a request with visitor data)"))
        assertEquals("bot-check", android.stopped)
        assertEquals(listOf("VISIONOS", "ANDROID"), otherHttp.postedBodies.map(CensusWorld::clientOf))
        assertTrue(Census.pageUrl(Census.V2) !in otherHttp.requestedUrls)
        assertEquals(START + 30 * MINUTE, CensusJson.readState(android.stateJson).cooldownUntil)
        assertTrue(android.report.contains("BROKEN bot check on android R0 video 1 (a request with visitor data)"))
    }

    @Test
    fun `a bot check on another request without visitor data ends that video only`() = runTest {
        val world = CensusWorld().standard(Census.V1).standard(Census.V2).sources()
        world.get(Census.pageUrl(Census.V1), Census.page().replace("\"VISITOR_DATA\":\"${Census.VISITOR}\",", ""))
        world.answer(Census.V1, "ANDROID", Census.botCheck(Census.V1))
        val http = world.http()

        val outcome = census(http).run(Mode.FULL, mapOf(1 to Census.V1, 2 to Census.V2), null)

        assertNull(outcome.stopped)
        // Video 1 ended at its bot check, so the page's newer MWEB value is tried on video 2 (B7).
        assertEquals(
            listOf("VISIONOS", "ANDROID", "VISIONOS", "ANDROID", "WEB_EMBEDDED_PLAYER", "MWEB", "MWEB"),
            http.postedBodies.map(CensusWorld::clientOf),
        )
        val run = lastRun(outcome)
        assertEquals("mweb SH", run.asks.last().let { "${it.client} ${it.step}" })
        assertEquals(2, run.asks.last().video)
        assertTrue(run.notes.contains("video 1: bot check on android R0 without visitor data, so nothing else was asked for this video"))
        assertTrue(
            outcome.report.contains(
                "CHANGED android R0 video 1: bot check without visitor data, nothing else asked for this video → §7 step 8",
            ),
        )
        assertEquals(0L, CensusJson.readState(outcome.stateJson).cooldownUntil)
    }

    @Test
    fun `the weekly check answers each video's bot check as R6 and asks video 1 nothing more`() = runTest {
        val world = CensusWorld().standard(Census.V1).sources().visionOsBotCheck(Census.V1)
        world.get(Census.pageUrl(Census.V5), Census.page())
        world.visionOsBotCheck(Census.V5)
        val http = world.http()

        val outcome = census(http).run(Mode.WEEKLY, mapOf(1 to Census.V1, 5 to Census.V5), null)

        assertNull(outcome.stopped)
        assertTrue("${calls(http)} requests", calls(http) <= ClientCensus.WEEKLY_MAX_REQUESTS)
        assertEquals(List(4) { "VISIONOS" }, http.postedBodies.map(CensusWorld::clientOf))
        assertEquals(1, http.requestedUrls.count { it == Census.pageUrl(Census.V1) })
        assertEquals(1, http.requestedUrls.count { it == Census.pageUrl(Census.V5) })
        val run = lastRun(outcome)
        assertTrue(run.notes.contains("video 1: answered as R6 after its bot check, so Android, the embedded player and MWEB were not asked"))
        assertEquals(CensusClass.DIRECT, ask(run, 1, "visionos", "R0+vd").finalClass)
        assertEquals(CensusClass.BOT_CHECK, ask(run, 5, "visionos", "R0").answerClass)
        assertEquals(CensusClass.DIRECT, ask(run, 5, "visionos", "R0+vd").finalClass)
        assertEquals("FIRST RUN — nothing to compare yet", outcome.firstLine)
        // B5 ((က+)): H on video 1's R0+vd answer (DIRECT), the run's one H check.
        assertEquals(listOf(1), run.hls.map(CensusHls::video))
        assertTrue(run.notes.contains("video 1 met a bot check and video 2 is not set, so no other client was asked"))
        assertTrue(
            run.notes.contains(
                "version acceptance: no video without a bot check and with a watch page, so no newer version was tried " +
                    "(R6 sends no such request)",
            ),
        )
        assertTrue(outcome.report.contains(" · 2 videos (1, 5) · "))
    }

    @Test
    fun `after video 1's bot check the weekly check runs H on its R0+vd answer and asks Android and MWEB on video 2, in 20 requests`() = runTest {
        val world = CensusWorld().standard(Census.V1).standard(Census.V2).sources().visionOsBotCheck(Census.V1)
        world.get(Census.pageUrl(Census.V5), Census.page())
        world.visionOsBotCheck(Census.V5)
        val http = world.http()
        val sources = CensusSources.DOCUMENTS.map { it.url }

        val outcome = census(http).run(Mode.WEEKLY, mapOf(1 to Census.V1, 2 to Census.V2, 5 to Census.V5), null)

        // W 2's case (owner, 2026-10-15): video 1 as R6 with V and H (7), video 5 as R6 (4), the
        // sources (4), video 2's page, Android with V, MWEB and the page's newer MWEB value (5).
        assertNull(outcome.stopped)
        val run = lastRun(outcome)
        assertEquals(20, calls(http))
        assertEquals(calls(http), run.requests)
        assertEquals(
            listOf("VISIONOS", "VISIONOS", "VISIONOS", "VISIONOS", "ANDROID", "MWEB", "MWEB"),
            http.postedBodies.map(CensusWorld::clientOf),
        )
        val hls = run.hls.single()
        assertEquals(
            listOf<Any?>(1, "visionos", "2xx", "2xx", "2xx", true),
            listOf(hls.video, hls.client, hls.result.master, hls.result.variant, hls.result.segment, hls.result.durationMatch),
        )
        assertEquals(listOf("android R0", "mweb R0", "mweb SH"), run.asks.filter { it.video == 2 }.map { "${it.client} ${it.step}" })
        assertEquals(listOf("DIRECT", "PASS"), ask(run, 2, "android", "R0").let { listOf(it.finalClass.name, it.v) })
        assertEquals("skip-needs-S", ask(run, 2, "mweb", "R0").v)
        assertEquals(1, http.requestedUrls.count { it == Census.pageUrl(Census.V2) })
        assertTrue(http.postedBodies.none { Census.V2 in it && CensusWorld.clientOf(it) == "VISIONOS" })
        assertTrue(run.notes.contains("video 1: answered as R6 after its bot check, so Android, the embedded player and MWEB were not asked"))
        assertTrue(run.notes.contains("version acceptance: on video 2, the first video without a bot check and with a watch page"))
        assertTrue(run.notes.none { it.startsWith("within the weekly check") })
        assertEquals(sources, http.requestedUrls.filter { it in sources })
        assertEquals("FIRST RUN — nothing to compare yet", outcome.firstLine)
        assertTrue(outcome.report.contains("INFO visionos R0 video 5: bot check, answered as R6 (visionOS with visitor data: DIRECT, V PASS)"))
        assertTrue(outcome.report.contains(" · 3 videos (1, 2, 5) · "))
    }

    @Test
    fun `video 2's part of the weekly check leaves out what does not fit in 20 requests and the run goes on`() = runTest {
        // visionOS's checked row (AVC 720p) is refused, so each V also reads its control: one request more.
        val world = CensusWorld().standard(Census.V1).standard(Census.V2).sources().visionOsBotCheck(Census.V1)
        world.get(Census.pageUrl(Census.V5), Census.page())
        world.visionOsBotCheck(Census.V5)
        world.probe = { url ->
            if ("itag=136" in url) ExtractorProbeResult.Refused(SiteExtractionFailure.HTTP_STATUS, 403) else ExtractorProbeResult.Answered(206, 1_000_000)
        }
        val http = world.http()

        val outcome = census(http).run(Mode.WEEKLY, mapOf(1 to Census.V1, 2 to Census.V2, 5 to Census.V5), null)

        assertNull(outcome.stopped)
        val run = lastRun(outcome)
        assertTrue("${calls(http)} requests", calls(http) <= ClientCensus.WEEKLY_MAX_REQUESTS)
        assertEquals(calls(http), run.requests)
        val left = run.notes.single { it.startsWith("within the weekly check's 20 requests, not asked: ") }
        assertTrue(left, left.endsWith(" (the run went on)"))
        assertEquals("within the weekly check's 20 requests, not asked: Android V, MWEB 2.20261002.01.00 (the run went on)", left)
        assertEquals("skip-budget", ask(run, 2, "android", "R0").v)
        assertEquals(listOf("android R0", "mweb R0"), run.asks.filter { it.video == 2 }.map { "${it.client} ${it.step}" })
    }

    @Test
    fun `the weekly check keeps H on video 1 when video 1 met no bot check and then asks video 2 nothing`() = runTest {
        val world = CensusWorld().standard(Census.V1).standard(Census.V2).sources()
        world.answer(Census.V5, "VISIONOS", Census.visionOs(Census.V5))
        val http = world.http()

        val outcome = census(http).run(Mode.WEEKLY, mapOf(1 to Census.V1, 2 to Census.V2, 5 to Census.V5), null)

        assertNull(outcome.stopped)
        assertTrue("${calls(http)} requests", calls(http) <= ClientCensus.WEEKLY_MAX_REQUESTS)
        val run = lastRun(outcome)
        assertEquals(1, run.hls.single().video)
        assertTrue(run.asks.none { it.video == 2 })
        assertTrue(http.postedBodies.none { Census.V2 in it })
        assertTrue(run.notes.none { it.startsWith("H: ") })
    }

    @Test
    fun `when video 2 met a bot check too it is answered as R6, its R0+vd answer gets H and Android and MWEB are not asked`() = runTest {
        // Video 1's R0+vd answer has no HLS address, so video 2 is asked for H (as R6 asks it).
        val world = CensusWorld().standard(Census.V1).standard(Census.V2).sources().visionOsBotCheck(Census.V2)
        val noHls = Census.answer(Census.V1, adaptive = listOf(Census.video(136, 720), Census.audio()), cver = "1.02")
        world.answers[Census.V1 to "VISIONOS"] = { body ->
            ExtractorHttpResult.Success(200, if (Census.VISITOR in body) noHls else Census.botCheck(Census.V1), Census.PLAYER_URL, "application/json")
        }
        val http = world.http()

        val outcome = census(http).run(Mode.WEEKLY, mapOf(1 to Census.V1, 2 to Census.V2), null)

        assertNull(outcome.stopped)
        val run = lastRun(outcome)
        assertEquals(listOf(2), run.hls.map(CensusHls::video))
        assertEquals(listOf("visionos R0", "visionos R0+vd"), run.asks.filter { it.video == 2 }.map { "${it.client} ${it.step}" })
        assertEquals(CensusClass.DIRECT, ask(run, 2, "visionos", "R0+vd").finalClass)
        assertEquals(List(4) { "VISIONOS" }, http.postedBodies.map(CensusWorld::clientOf))
        assertTrue(run.notes.contains("H: video 1 met a bot check, so the H check ran on video 2"))
        assertTrue(run.notes.contains("video 2: answered as R6 after its bot check, so Android and MWEB were not asked"))
        assertTrue("${calls(http)} requests", calls(http) <= ClientCensus.WEEKLY_MAX_REQUESTS)
    }

    @Test
    fun `HTTP 429 stops the run like a bot check`() = runTest {
        val world = CensusWorld().standard(Census.V1)
        world.answers[Census.V1 to "VISIONOS"] = { _ -> ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 429) }
        val http = world.http()

        val outcome = census(http).run(Mode.FULL, mapOf(1 to Census.V1), null)

        assertEquals("rate-limited", outcome.stopped)
        assertTrue(outcome.report.contains("rate limited (HTTP 429)"))
        val refused = ask(lastRun(outcome), 1, "visionos", "R0")
        assertEquals(listOf("429", "REFUSED"), listOf(refused.http, refused.answerClass.name))
        assertEquals(START + 30 * MINUTE, CensusJson.readState(outcome.stateJson).cooldownUntil)
        assertEquals(1, http.postedBodies.size)
    }

    @Test
    fun `the weekly check stays within 20 requests, never runs S, and reads the sources at most once a day`() = runTest {
        val world = CensusWorld().standard(Census.V1).sources()
        world.answer(Census.V5, "VISIONOS", Census.visionOs(Census.V5))
        val http = world.http()
        val scripts = FakePlayerScriptRunner()
        val census = census(http, scripts)
        val videos = mapOf(1 to Census.V1, 5 to Census.V5)
        val sources = CensusSources.DOCUMENTS.map { it.url }.toSet()

        val first = census.run(Mode.WEEKLY, videos, null)
        val firstCalls = calls(http)
        val firstGets = http.requestedUrls.size
        now += HOUR
        val second = census.run(Mode.WEEKLY, videos, first.stateJson)
        val secondGets = http.requestedUrls.size
        now += 25 * HOUR
        census.run(Mode.WEEKLY, videos, second.stateJson)

        assertNull(first.stopped)
        assertTrue("$firstCalls requests", firstCalls <= ClientCensus.WEEKLY_MAX_REQUESTS)
        assertEquals(firstCalls, lastRun(first).requests)
        assertEquals(
            listOf("VISIONOS", "VISIONOS", "ANDROID", "WEB_EMBEDDED_PLAYER", "MWEB", "MWEB"),
            http.postedBodies.take(6).map(CensusWorld::clientOf),
        )
        assertTrue(scripts.requests.isEmpty())
        assertEquals(sources, http.requestedUrls.take(firstGets).filter { it in sources }.toSet())
        assertTrue(http.requestedUrls.subList(firstGets, secondGets).none { it in sources })
        assertTrue(lastRun(second).notes.contains("version sources: read less than 24 h ago, not read again"))
        assertEquals("NO CHANGE — 0 days since the last run", second.firstLine)
        assertEquals(sources, http.requestedUrls.drop(secondGets).filter { it in sources }.toSet())
        assertEquals("skip-needs-S", ask(lastRun(first), 1, "embedded", "R0").v)
        assertEquals(CensusClass.DIRECT, ask(lastRun(first), 5, "visionos", "R0").finalClass)
    }

    @Test
    fun `the weekly check never goes over 20 requests even when every stream is refused`() = runTest {
        val world = CensusWorld().standard(Census.V1).sources()
        world.answer(Census.V5, "VISIONOS", Census.visionOs(Census.V5))
        world.probe = { ExtractorProbeResult.Refused(SiteExtractionFailure.HTTP_STATUS, 403, null) }
        val http = world.http()

        val outcome = census(http).run(Mode.WEEKLY, mapOf(1 to Census.V1, 5 to Census.V5), null)

        assertTrue("${calls(http)} requests", calls(http) <= ClientCensus.WEEKLY_MAX_REQUESTS)
        assertEquals(CensusClass.TOKEN_NEEDED, ask(lastRun(outcome), 1, "visionos", "R0").finalClass)
    }

    @Test
    fun `the weekly check stops at 60 seconds`() = runTest {
        val world = CensusWorld().standard(Census.V1).sources()
        world.answer(Census.V5, "VISIONOS", Census.visionOs(Census.V5))
        var ticks = 0L
        val census = ClientCensus(world.http(), FakePlayerScriptRunner(), Census.UA, clock = { START + 7_000 * ticks++ })

        val outcome = census.run(Mode.WEEKLY, mapOf(1 to Census.V1, 5 to Census.V5), null)

        assertEquals("time", outcome.stopped)
        assertTrue(outcome.report.contains("stopped: time"))
    }

    @Test
    fun `a full census stops at its byte budget, measured by the meter`() = runTest {
        var wire = 0L
        val world = CensusWorld().standard(Census.V1).standard(Census.V2)
        world.onPost = { wire += 7L * 1024 * 1024 }
        val http = world.http()
        val census = ClientCensus(http, FakePlayerScriptRunner(), Census.UA, meter = { wire }, clock = { now })

        val outcome = census.run(Mode.FULL, mapOf(1 to Census.V1, 2 to Census.V2), null)

        assertEquals("budget", outcome.stopped)
        assertEquals(1, http.postedBodies.size)
        val run = lastRun(outcome)
        assertTrue(run.metered)
        assertEquals(7L * 1024 * 1024, run.bytes)
        assertEquals("skip-budget", ask(run, 1, "visionos", "R0").v)
        assertTrue(outcome.report.contains("note: the run stopped early (budget)"))

        val unmetered = census(CensusWorld().http()).run(Mode.FULL, mapOf(1 to Census.V1), null)
        assertTrue(unmetered.report.contains("KB (bodies only)"))
    }

    @Test
    fun `a newer visionOS value from a source is tried once on video 1 and marked accepted`() = runTest {
        val http = CensusWorld().standard(Census.V1).sources(vision = "1.03").http()

        val run = lastRun(census(http).run(Mode.FULL, mapOf(1 to Census.V1), null))

        assertEquals(1, http.postedBodies.count { CensusWorld.clientOf(it) == "VISIONOS" && CensusWorld.versionOf(it) == "1.03" })
        val tried = ask(run, 1, "visionos", "SH")
        assertEquals(listOf("1.03", "PASS"), listOf(tried.version, tried.v))
        val fact = run.versions.single { it.identity == "visionos" && it.source == "ytdlp" }
        assertEquals(listOf("1.03", true, "yes"), listOf(fact.value, fact.newer, fact.accepted))
    }

    @Test
    fun `a newer value YouTube refuses is marked rejected and the next diff says BROKEN`() = runTest {
        val world = CensusWorld().standard(Census.V1).sources(vision = "1.03")
        val census = census(world.http())
        val accepted = census.run(Mode.FULL, mapOf(1 to Census.V1), null)
        val answer = Census.visionOs(Census.V1)
        world.answers[Census.V1 to "VISIONOS"] = { body ->
            if (CensusWorld.versionOf(body) == "1.03") {
                ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 400)
            } else {
                ExtractorHttpResult.Success(200, answer, Census.PLAYER_URL, "application/json")
            }
        }
        now += DAY

        val rejected = census.run(Mode.FULL, mapOf(1 to Census.V1), accepted.stateJson)

        val fact = lastRun(rejected).versions.single { it.identity == "visionos" && it.source == "ytdlp" }
        assertEquals("rejected", fact.accepted)
        assertEquals(CensusClass.VERSION_REJECTED, ask(lastRun(rejected), 1, "visionos", "SH").answerClass)
        assertTrue(rejected.report.contains("BROKEN version visionos from ytdlp: accepted → rejected → §7 step 3"))
    }

    @Test
    fun `the report, the state and the progress never show an address, a video ID, visitor data or a value`() = runTest {
        val world = CensusWorld().standard(Census.V1).standard(Census.V2).standard(Census.V5).sources()
        world.answer(Census.V5, "VISIONOS", Census.visionOsCiphered(Census.V5))
        val steps = mutableListOf<String>()

        val outcome = census(world.http()).run(
            Mode.FULL,
            mapOf(1 to Census.V1, 2 to Census.V2, 5 to Census.V5),
            null,
        ) { steps += it }

        val secrets = listOf(
            Census.V1, Census.V2, Census.V5, Census.VISITOR, Census.API_KEY, Census.SIG_INPUT, Census.N_INPUT,
            Census.SIG_INPUT.reversed(), Census.N_INPUT.reversed(), "googlevideo", "https://", "http://",
            "videoplayback", "ytimg", "Census video",
        )
        for ((name, text) in listOf("report" to outcome.report, "state" to outcome.stateJson, "steps" to steps.joinToString("\n"))) {
            for (secret in secrets) assertFalse("$name shows $secret", text.contains(secret))
        }
        val tags = listOf(1 to Census.V1, 2 to Census.V2, 5 to Census.V5).joinToString(",") { (slot, id) -> "\"$slot\":\"${CensusJson.tagOf(id)}\"" }
        assertTrue(outcome.report.contains("\"slots\":{$tags}"))
        assertTrue(steps.contains("video 5 · visionOS with the page's signals"))
    }

    @Test
    fun `the sanitizer replaces addresses, media hosts and secrets`() {
        val text = "see https://www.youtube.com/watch?v=jNQXAC9IVRw and rr3---sn-abc.googlevideo.com, hrefhttp://x.y/z, visitor Cgt0"
        val clean = CensusSanitizer.clean(text, listOf("jNQXAC9IVRw", "Cgt0", "ab"))

        assertEquals("see [address] and [host], href[address] visitor *", clean)
        assertEquals("Sign in to confirm you’re not a bot", CensusSanitizer.reason("Sign in to confirm\n you’re not a bot{}<>\"="))
        assertNull(CensusSanitizer.reason("   "))
        assertEquals(120, CensusSanitizer.reason("a".repeat(500))!!.length)
    }

    @Test
    fun `without the own solver the script clients are classed but not checked`() = runTest {
        val scripts = FakePlayerScriptRunner(isAvailable = false)

        val run = lastRun(census(CensusWorld().standard(Census.V1).http(), scripts).run(Mode.FULL, mapOf(1 to Census.V1), null))

        for (client in listOf("embedded", "mweb")) {
            val asked = ask(run, 1, client, "R0")
            assertEquals(listOf("unavailable", "skip-needs-S"), listOf(asked.solve, asked.v))
        }
        assertTrue(scripts.requests.isEmpty())
    }

    @Test
    fun `a video without a watch page skips the embedded player and MWEB`() = runTest {
        val http = CensusWorld().standard(Census.V1, page = false).http()

        val run = lastRun(census(http).run(Mode.FULL, mapOf(1 to Census.V1), null))

        assertEquals(listOf("VISIONOS", "ANDROID"), http.postedBodies.map(CensusWorld::clientOf))
        assertEquals("404", run.pages.single().http)
        assertTrue(run.notes.contains("video 1: no watch page, so the embedded player and MWEB were not asked"))
    }

    @Test
    fun `slots that are not video IDs are skipped with a note`() = runTest {
        val http = CensusWorld().http()

        val run = lastRun(census(http).run(Mode.FULL, mapOf(4 to "not an id", 11 to Census.V1), null))

        assertTrue(http.postedBodies.isEmpty())
        assertTrue(run.notes.containsAll(listOf("video 4: not a video ID, skipped", "video 11: not a video ID, skipped")))
        assertTrue(run.slots.isEmpty())
    }

    @Test
    fun `the state keeps the last 8 runs and the status line names the last one`() = runTest {
        val census = census(CensusWorld().http())
        var state: String? = null
        assertEquals("No run yet.", ClientCensus.statusOf(state, now))

        repeat(10) {
            state = census.run(Mode.WEEKLY, emptyMap(), state).stateJson
            now += HOUR
        }

        val runs = CensusJson.readState(state).runs
        assertEquals(CensusJson.MAX_RUNS, runs.size)
        assertEquals(START + 2 * HOUR, runs.first().atMillis)
        now += 3 * DAY
        assertEquals(
            "Last run: ${CensusJson.dateOf(START + 9 * HOUR)} (weekly), 3 days ago.",
            ClientCensus.statusOf(state, now),
        )
        assertTrue(runs.last().notes.contains("video 1 is not set, so the weekly check asked nothing"))
    }

    @Test
    fun `video IDs are read from IDs and YouTube addresses only`() {
        val id = Census.V1
        for (text in listOf(
            id, "  $id ", "https://www.youtube.com/watch?v=$id&t=10", "https://youtu.be/$id?si=abc",
            "https://m.youtube.com/shorts/$id", "youtube.com/embed/$id", "https://music.youtube.com/watch?v=$id",
            "https://www.youtube.com/live/$id",
        )) {
            assertEquals(text, id, ClientCensus.videoIdOf(text))
        }
        for (text in listOf("https://example.com/watch?v=$id", "not a video", "https://www.youtube.com/watch?v=short", "")) {
            assertNull(text, ClientCensus.videoIdOf(text))
        }
    }

    @Test
    fun `B8 - the header lists the videos asked, three or more in a row as a range`() = runTest {
        val world = CensusWorld().sources()
        for (id in listOf(Census.V1, Census.V2, V3, Census.V5)) world.standard(id)

        val outcome = census(world.http()).run(Mode.FULL, mapOf(1 to Census.V1, 2 to Census.V2, 3 to V3, 5 to Census.V5), null)

        assertNull(outcome.stopped)
        assertEquals(listOf(1, 2, 3, 5), lastRun(outcome).asks.map(CensusAsk::video).distinct())
        assertTrue(outcome.report, outcome.report.contains(" · 4 videos (1\u20133, 5) · "))

        val none = census(CensusWorld().http()).run(Mode.FULL, mapOf(1 to "not a video"), null)
        assertTrue(none.report, none.report.contains(" · 0 videos · "))
        assertTrue(lastRun(none).notes.contains("video 1: not a video ID, skipped"))
        assertEquals(0, lastRun(none).requests)
    }

    @Test
    fun `V7 - video 6's visionOS refusal and the embedded player's 152-18 are REFUSED, not terminal, and the run goes on`() = runTest {
        val world = CensusWorld().standard(V6).standard(V8).standard(V9).sources()
        // Video 6 (made for kids) as visionOS answered it in valid runs 1-3: no formats, the video's details.
        world.answer(V6, "VISIONOS", Census.answer(V6, status = "UNPLAYABLE", reason = "This video is not available"))
        world.answer(V6, "WEB_EMBEDDED_PLAYER", embeddedError152(V6))
        world.answer(V9, "VISIONOS", Census.answer(V9, status = "LOGIN_REQUIRED", reason = "Sign in to confirm your age"))
        val http = world.http()

        val outcome = census(http).run(Mode.FULL, mapOf(6 to V6, 8 to V8, 9 to V9), null)

        assertNull(outcome.stopped)
        val run = lastRun(outcome)
        val vision = ask(run, 6, "visionos", "R0")
        assertEquals(listOf("REFUSED", "REFUSED", "UNPLAYABLE"), listOf(vision.answerClass.name, vision.finalClass.name, vision.status))
        assertFalse(vision.finalClass.terminal)
        assertEquals(true, vision.bound)
        assertEquals("skip-no-row", vision.v)
        val embedded = ask(run, 6, "embedded", "R0")
        assertEquals(listOf("REFUSED", "ERROR"), listOf(embedded.answerClass.name, embedded.status))
        assertFalse(embedded.finalClass.terminal)
        // The other clients answered video 6, as Android and MWEB did in valid runs 1-3, and the run went on.
        assertEquals(listOf("DIRECT", "PASS"), ask(run, 6, "android", "R0").let { listOf(it.finalClass.name, it.v) })
        assertEquals(listOf("NEEDS_SCRIPT", "ok-n", "PASS"), ask(run, 6, "mweb", "R0").let { listOf(it.answerClass.name, it.solve, it.v) })
        assertEquals(CensusClass.DIRECT, ask(run, 8, "visionos", "R0").finalClass)
        // An age check stays a verdict about the video.
        val age = ask(run, 9, "visionos", "R0")
        assertEquals(CensusClass.AGE_CHECK, age.finalClass)
        assertTrue(age.finalClass.terminal)
        assertEquals("LOGIN_REQUIRED", age.status)
        assertTrue(outcome.report.contains(" · 3 videos (6, 8, 9) · "))
    }

    @Test
    fun `the report names the client table in one line after the summary`() = runTest {
        val outcome = census(CensusWorld().standard(Census.V1).sources().http()).run(Mode.FULL, mapOf(1 to Census.V1), null)

        val lines = outcome.report.lines()
        val summary = lines.indexOfFirst { it.startsWith("${ClientCensus.TOOL_LABEL} · FULL · ") }
        assertTrue(outcome.report, summary >= 0)
        assertEquals(MasterClientTable.describe(), lines[summary + 1])
        assertEquals(1, lines.count { it.startsWith("client table v") })
    }

    @Test
    fun `the shape kind is stored and kept through the state's round trip`() = runTest {
        val world = CensusWorld().standard(Census.V1).sources()
        val moved = SimJson.obj(SimJson.parse(Census.visionOs(Census.V1))).also(LShapeCases::moveStreamingData)
        world.answer(Census.V1, "VISIONOS", SimJson.write(moved))

        val outcome = census(world.http()).run(Mode.FULL, mapOf(1 to Census.V1), null)

        val vision = ask(lastRun(outcome), 1, "visionos", "R0")
        assertEquals(CensusClass.SHAPE_CHANGED, vision.answerClass)
        assertEquals("moved", vision.shapeKind)
        assertTrue(outcome.stateJson.contains("\"kind\":\"moved\""))
        val stored = CensusJson.readState(CensusJson.state(CensusJson.readState(outcome.stateJson)))
        assertEquals("moved", ask(stored.runs.single(), 1, "visionos", "R0").shapeKind)
        assertNull(ask(stored.runs.single(), 1, "android", "R0").shapeKind)

        // Only the judge's two kinds are read back.
        val run = CensusRun(
            tool = ClientCensus.TOOL_LABEL,
            mode = Mode.FULL.name,
            atMillis = START,
            asks = listOf(
                CensusAsk(1, "visionos", "R0", "1.02", "200", CensusClass.SHAPE_CHANGED, shapeKind = "partial"),
                CensusAsk(2, "visionos", "R0", "1.02", "200", CensusClass.SHAPE_CHANGED, shapeKind = "sideways"),
                CensusAsk(3, "visionos", "R0", "1.02", "200", CensusClass.DIRECT),
            ),
        )
        val read = CensusJson.readRun(BoundedJsonParser.parse(CensusJson.run(run))!!)!!
        assertEquals(listOf("partial", null, null), read.asks.map(CensusAsk::shapeKind))
    }

    @Test
    fun `the L steps read a live-like visionOS answer as its direct parse, and an L stop on DRM names the marker's place`() = runTest {
        // D7 (2026-10-15): the owner's census read every L step on live visionOS answers as
        // "none (no rows)" — L stopped, its old DRM marker wider than the copied parser's;
        // DRM-named player settings like these tripped it.
        fun live(formatExtra: Map<String, Any?> = emptyMap()): String {
            val json = SimJson.obj(SimJson.parse(Census.visionOs(Census.V1)))
            val streaming = SimJson.obj(json["streamingData"])
            SimJson.list(streaming["adaptiveFormats"]).map(SimJson::obj).forEach { it.putAll(formatExtra) }
            streaming["serverAbrStreamingUrl"] = "https://${Census.HOST}/videoplayback?expire=4102444800&source=youtube&sabr=1"
            json["playbackTracking"] = linkedMapOf<String, Any?>(
                "videostatsPlaybackUrl" to linkedMapOf<String, Any?>("baseUrl" to "https://s.youtube.com/api/stats/playback?cl=1"),
            )
            json["playerConfig"] = LShapeCases.playerConfig()
            return SimJson.write(json)
        }
        suspend fun notes(answer: String): List<String> {
            val world = CensusWorld().standard(Census.V1).sources()
            world.answer(Census.V1, "VISIONOS", answer)
            val run = lastRun(census(world.http()).run(Mode.FULL, mapOf(1 to Census.V1), null))
            assertEquals(CensusClass.DIRECT, ask(run, 1, "visionos", "R0").answerClass)
            return run.notes
        }

        val plain = notes(live())
        val line = plain.single { it.startsWith("video 1 L (R0, ") }
        assertTrue(line, line.endsWith("round-trip same, L1 same, L2 same, L3 same, L5 n/a, L7 same"))
        assertTrue(plain.contains(L_SUMMARY + "round-trip 1/1 same, L1 1/1 same, L2 1/1 same, L3 1/1 same, L5 0/0 same (1 n/a), L7 1/1 same"))

        // A DRM text in a renamed key of each format (as the simulator's L12), which the copied
        // parser does not read: L stops, and the note names where its marker sits (no value).
        val drm = notes(live(mapOf("drmFamiliesV2" to mutableListOf<Any?>("WIDEVINE"))))
        val stopped = drm.single { it.startsWith("video 1 L (R0, ") }
        val stop = "none (stop DRM_PROTECTED; DRM marker at"
        val expected = "round-trip $stop streamingData.adaptiveFormats[].drmFamiliesV2[]), " +
            "L1 $stop playback.streamingData.adaptiveFormats[].drmFamiliesV2[]), " +
            "L2 $stop streamingData.separateFormats[].drmFamiliesV2[]), " +
            "L3 $stop streamingData.adaptiveFormats[].drmFamiliesV2[]), L5 n/a, " +
            "L7 $stop streamingData.adaptiveFormats.*.drmFamiliesV2[])"
        assertTrue(stopped, stopped.endsWith(expected))
        assertFalse(stopped, stopped.contains("WIDEVINE") || stopped.contains("://"))
        assertTrue(drm.contains(L_SUMMARY + "round-trip 0/1 same, L1 0/1 same, L2 0/1 same, L3 0/1 same, L5 0/0 same (1 n/a), L7 0/1 same"))
    }

    /** The embedded player's error 152-18 (census videos 2 and 6): an error screen with a button. */
    private fun embeddedError152(id: String): String =
        "{\"playabilityStatus\":{\"status\":\"ERROR\",\"reason\":\"This video is unavailable\"," +
            "\"errorScreen\":{\"playerErrorMessageRenderer\":{\"subreason\":{\"runs\":[{\"text\":\"Error code: 152 - 18\"}]}," +
            "\"proceedButton\":{\"buttonRenderer\":{\"text\":{\"simpleText\":\"Watch video on YouTube\"}}}}}}," +
            "\"videoDetails\":{\"videoId\":\"$id\",\"title\":\"Census video\",\"lengthSeconds\":\"212\"}}"

    private fun census(
        http: FakeExtractorHttpClient,
        scripts: FakePlayerScriptRunner = FakePlayerScriptRunner(),
    ) = ClientCensus(http, scripts, Census.UA, clock = { now })

    private fun calls(http: FakeExtractorHttpClient) = http.postedUrls.size + http.requestedUrls.size + http.probedUrls.size

    private fun lastRun(outcome: ClientCensus.Outcome): CensusRun =
        CensusJson.readState(outcome.stateJson).runs.maxByOrNull(CensusRun::atMillis)!!

    private fun ask(run: CensusRun, video: Int, client: String, step: String): CensusAsk =
        run.asks.single { it.video == video && it.client == client && it.step == step }

    private companion object {
        const val START = 1_791_000_000_000L
        const val V3 = "Census0003x"
        const val V6 = "Census0006x"
        const val V8 = "Census0008x"
        const val V9 = "Census0009x"
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
        const val L_SUMMARY = "L steps on 1 visionOS answers (in memory, no request): "
    }
}
