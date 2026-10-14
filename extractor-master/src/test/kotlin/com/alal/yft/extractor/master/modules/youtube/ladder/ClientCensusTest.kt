package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.PlayerScriptChallengeKind
import com.alal.yft.extractor.api.SiteExtractionFailure
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
    fun `a bot check stops the whole run and no run starts for 30 minutes`() = runTest {
        val world = CensusWorld().standard(Census.V1).standard(Census.V2).sources()
        world.answer(Census.V1, "VISIONOS", Census.botCheck(Census.V1))
        val http = world.http()
        val census = census(http)

        val stopped = census.run(Mode.FULL, mapOf(1 to Census.V1, 2 to Census.V2), null)
        now += 10 * MINUTE
        val waiting = census.run(Mode.FULL, mapOf(1 to Census.V1, 2 to Census.V2), stopped.stateJson)

        assertEquals("bot-check", stopped.stopped)
        assertEquals("BROKEN (1) — first run", stopped.firstLine)
        assertTrue(stopped.report.contains("BROKEN bot check — the run stopped; no census for 30 min → §7 step 8"))
        assertEquals(CensusClass.BOT_CHECK, ask(lastRun(stopped), 1, "visionos", "R0").answerClass)
        assertEquals(1, http.postedBodies.size)
        assertTrue(http.requestedUrls.isEmpty() && http.probedUrls.isEmpty())
        assertEquals(START + 30 * MINUTE, CensusJson.readState(stopped.stateJson).cooldownUntil)
        assertEquals("COOLDOWN — a bot check stopped an earlier run; try again in 20 min", waiting.firstLine)
        assertEquals("cooldown", waiting.stopped)
        assertEquals(1, http.postedBodies.size)
        assertTrue(ClientCensus.statusOf(waiting.stateJson, now).endsWith("Bot check cooldown: 20 min left."))

        now += 21 * MINUTE
        census.run(Mode.FULL, mapOf(1 to Census.V1, 2 to Census.V2), waiting.stateJson)
        assertEquals(2, http.postedBodies.size)
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
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }
}
