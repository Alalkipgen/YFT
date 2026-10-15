package com.alal.yft.extractor.master.modules.youtube.ladder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 1.3 D0: the weekly check's diff — each change kind, and identical runs say NO CHANGE. */
class CensusDiffTest {
    @Test
    fun `identical runs say NO CHANGE with the days since the last run`() {
        val result = CensusDiff.compare(listOf(run(0, direct())), run(7 * DAY, direct()))

        assertEquals("NO CHANGE — 7 days since the last run", result.headline)
        assertTrue(result.lines.isEmpty())
        assertEquals("FIRST RUN — nothing to compare yet", CensusDiff.compare(emptyList(), run(0, direct())).headline)
    }

    @Test
    fun `a worse class is BROKEN and names its runbook step, a better one is CHANGED`() {
        val worse = CensusDiff.compare(listOf(run(0, direct())), run(DAY, direct().copy(answerClass = CensusClass.NEEDS_SCRIPT, finalClass = CensusClass.NEEDS_SCRIPT, v = "skip-needs-S")))
        assertEquals("BROKEN (1) — 1 days since the last run", worse.headline)
        assertEquals(listOf("BROKEN visionos R0 video 1: class DIRECT → NEEDS_SCRIPT → §7 step 4"), worse.lines.map { it.render() })

        val better = CensusDiff.compare(
            listOf(run(0, direct().copy(answerClass = CensusClass.NEEDS_SCRIPT, finalClass = CensusClass.NEEDS_SCRIPT, v = null))),
            run(DAY, direct().copy(v = null)),
        )
        assertEquals(listOf("CHANGED visionos R0 video 1: class NEEDS_SCRIPT → DIRECT → §7 step 1"), better.lines.map { it.render() })

        val refused = CensusDiff.compare(listOf(run(0, direct())), run(DAY, direct().copy(answerClass = CensusClass.VERSION_REJECTED, finalClass = CensusClass.VERSION_REJECTED, v = null)))
        assertEquals(listOf("BROKEN visionos R0 video 1: class DIRECT → VERSION_REJECTED → §7 step 3"), refused.lines.map { it.render() })
    }

    @Test
    fun `V is compared through the final class when both runs checked`() {
        val token = CensusDiff.compare(listOf(run(0, direct())), run(DAY, direct().copy(finalClass = CensusClass.TOKEN_NEEDED, v = "FAIL", vTarget = "403")))
        assertEquals(listOf("BROKEN visionos R0 video 1: class DIRECT → TOKEN_NEEDED → §7 step 7"), token.lines.map { it.render() })

        val failed = CensusDiff.compare(listOf(run(0, direct())), run(DAY, direct().copy(v = "FAIL", vTarget = "4xx")))
        assertEquals(listOf("BROKEN visionos R0 video 1: V PASS → FAIL (4xx) → §7 step 2"), failed.lines.map { it.render() })

        val unknown = CensusDiff.compare(listOf(run(0, direct())), run(DAY, direct().copy(v = "UNKNOWN", vTarget = "net")))
        assertEquals(listOf("CHANGED visionos R0 video 1: V PASS → UNKNOWN (net) → §7 step 2"), unknown.lines.map { it.render() })
    }

    @Test
    fun `counts, the highest video and the HLS address are CHANGED lines`() {
        val result = CensusDiff.compare(
            listOf(run(0, direct())),
            run(DAY, direct().copy(adaptive = 3, missingSizes = 1, heights = listOf(720), hls = false)),
        )

        assertEquals(
            listOf(
                "CHANGED visionos R0 video 1: formats (progressive/adaptive/protected/no address/no size) 0/4/0/0/0 → 0/3/0/0/1 → §7 step 5",
                "CHANGED visionos R0 video 1: highest video 1080p → 720p → §7 step 2",
                "CHANGED visionos R0 video 1: HLS address gone → §7 step 7",
            ),
            result.lines.map { it.render() },
        )
        assertEquals("CHANGED (3) — 1 days since the last run", result.headline)
    }

    @Test
    fun `a removed path the parser reads is BREAKING, other shape changes are CHANGED, one line per client`() {
        val added = CensusDiff.compare(listOf(run(0, direct())), run(DAY, direct().copy(shapePaths = PATHS + "streamingData.newField")))
        assertEquals(listOf("CHANGED shape visionos R0: +1 −0 (+streamingData.newField) → §7 step 6"), added.lines.map { it.render() })

        val renamed = PATHS - "streamingData.adaptiveFormats[].url" + "streamingData.adaptiveFormats[].address"
        val breaking = CensusDiff.compare(
            listOf(run(0, direct(), direct(video = 2), slots = mapOf(1 to "a1b2", 2 to "c3d4"))),
            run(DAY, direct().copy(shapePaths = renamed), direct(video = 2).copy(shapePaths = renamed), slots = mapOf(1 to "a1b2", 2 to "c3d4")),
        )
        assertEquals(
            listOf(
                "BROKEN shape visionos R0: +1 −1 (+streamingData.adaptiveFormats[].address, −streamingData.adaptiveFormats[].url) " +
                    "BREAKING: streamingData.adaptiveFormats[].url → §7 step 6",
            ),
            breaking.lines.map { it.render() },
        )
    }

    @Test
    fun `H changes name the step, and a lost 2xx, a new key or a duration mismatch are BROKEN`() {
        val before = hls()
        fun lines(after: HlsCheck.Result) = CensusDiff.compare(
            listOf(run(0, direct(), hls = listOf(CensusHls(1, "visionos", before)))),
            run(DAY, direct(), hls = listOf(CensusHls(1, "visionos", after))),
        ).lines.map { it.render() }

        assertEquals(listOf("BROKEN H visionos video 1: segment 2xx → 403 → §7 step 7"), lines(before.copy(segment = "403")))
        assertEquals(listOf("BROKEN H visionos video 1: key NONE → AES-128 → §7 step 7"), lines(before.copy(key = "AES-128")))
        assertEquals(listOf("BROKEN H visionos video 1: duration no longer matches the video → §7 step 7"), lines(before.copy(durationMatch = false)))
        assertEquals(listOf("CHANGED H visionos video 1: highest 1080p → 720p → §7 step 7"), lines(before.copy(maxHeight = 720)))
        assertEquals(listOf("CHANGED H visionos video 1: audio muxed → separate → §7 step 7"), lines(before.copy(audio = "separate")))
        assertTrue(lines(before).isEmpty())
    }

    @Test
    fun `versions - a reference's value change is CHANGED, the page's own is not, accepted to rejected is BROKEN`() {
        val earlier = listOf(
            CensusVersion("visionos", "ytdlp", true, value = "1.02", pattern = true, accepted = "equal"),
            CensusVersion("mweb", "page", true, value = "2.20261002.01.00", pattern = true, accepted = "yes"),
            CensusVersion("android", "playstore", true, value = "21.26.364", pattern = true),
            CensusVersion("visionos", "youtubejs", true, value = "1.03", pattern = true, newer = true, accepted = "yes"),
        )
        val now = listOf(
            CensusVersion("visionos", "ytdlp", true, value = "1.03", pattern = true, newer = true, accepted = "not-tested"),
            CensusVersion("mweb", "page", true, value = "2.20261009.01.00", pattern = true, accepted = "yes"),
            CensusVersion("android", "playstore", false, note = "over 64 KiB"),
            CensusVersion("visionos", "youtubejs", true, value = "1.03", pattern = true, newer = true, accepted = "rejected"),
        )

        val result = CensusDiff.compare(listOf(run(0, direct(), versions = earlier)), run(DAY, direct(), versions = now))

        assertEquals(
            listOf(
                "CHANGED version visionos from ytdlp: 1.02 → 1.03 → §7 step 3",
                "CHANGED version android from playstore: unreachable (over 64 KiB) → §7 step 3",
                "BROKEN version visionos from youtubejs: accepted → rejected → §7 step 3",
            ),
            result.lines.map { it.render() },
        )
    }

    @Test
    fun `another video in a slot is noted and not compared`() {
        val result = CensusDiff.compare(
            listOf(run(0, direct(), slots = mapOf(1 to "a1b2"))),
            run(DAY, direct().copy(answerClass = CensusClass.REFUSED, finalClass = CensusClass.REFUSED, v = null), slots = mapOf(1 to "ffff")),
        )

        assertTrue(result.lines.isEmpty())
        assertEquals(listOf("video 1 is not the video of the last run; it is compared only with runs of the same video"), result.notes)
    }

    @Test
    fun `each answer is compared with the latest earlier run that asked it`() {
        val embedded = direct(client = "embedded").copy(answerClass = CensusClass.NEEDS_SCRIPT, finalClass = CensusClass.NEEDS_SCRIPT, solve = "ok-sig+n")
        val result = CensusDiff.compare(
            listOf(run(0, direct(), embedded), run(6 * DAY, direct())),
            run(7 * DAY, direct(), embedded.copy(v = "FAIL", vTarget = "4xx")),
        )

        assertEquals(listOf("BROKEN embedded R0 video 1: V PASS → FAIL (4xx) → §7 step 2"), result.lines.map { it.render() })
        assertEquals("BROKEN (1) — 1 days since the last run", result.headline)
    }

    @Test
    fun `a bot check or HTTP 429 is BROKEN even on a first run, another stop is a note`() {
        val bot = CensusDiff.compare(emptyList(), run(0, stopped = "bot-check"))
        assertEquals("BROKEN (1) — first run", bot.headline)
        assertEquals(listOf("BROKEN bot check — the run stopped; no census for 30 min → §7 step 8"), bot.lines.map { it.render() })

        val limited = CensusDiff.compare(listOf(run(0, direct())), run(DAY, direct(), stopped = "rate-limited"))
        assertEquals(listOf("BROKEN rate limited (HTTP 429) — the run stopped; no census for 30 min → §7 step 8"), limited.lines.map { it.render() })

        val budget = CensusDiff.compare(listOf(run(0, direct())), run(DAY, direct(), stopped = "budget"))
        assertEquals("NO CHANGE — 1 days since the last run", budget.headline)
        assertEquals(listOf("the run stopped early (budget); later steps were not measured"), budget.notes)
        assertTrue(budget.text().endsWith("note: the run stopped early (budget); later steps were not measured"))
    }

    @Test
    fun `each video's answered bot check is shown, the one that stopped the run is BROKEN, and no bot check is a class or shape change`() {
        val bot = botCheck()
        val slots = mapOf(1 to "a1b2", 2 to "c3d4")
        val earlier = run(0, direct(), direct(video = 2), slots = slots)
        val current = run(
            DAY, bot, direct(step = ClientCensus.AGAIN), bot.copy(video = 2), bot.copy(video = 2, step = ClientCensus.AGAIN),
            slots = slots, stopped = "bot-check",
        )

        val result = CensusDiff.compare(listOf(earlier), current)

        assertEquals(
            listOf(
                "BROKEN bot check on visionos R0+vd video 2 (a request with visitor data) — the run stopped; no census for 30 min → §7 step 8",
                "CHANGED visionos R0 video 2: bot check, answered as R6 (visionOS with visitor data: BOT_CHECK) → §7 step 8",
            ),
            result.lines.map { it.render() },
        )
        assertEquals(listOf("visionos R0 video 1: bot check, answered as R6 (visionOS with visitor data: DIRECT, V PASS)"), result.info)
        assertEquals("BROKEN (2) — 1 days since the last run", result.headline)
    }

    @Test
    fun `a bot check R6 answered with a DIRECT, V-passed answer is an INFO line, not a change (owner, 2026-10-15)`() {
        val slots = mapOf(1 to "a1b2", 5 to "e5f6")
        fun weekly(at: Long) = run(
            at, botCheck(), direct(step = ClientCensus.AGAIN), botCheck(video = 5), direct(video = 5, step = ClientCensus.AGAIN),
            slots = slots,
        )
        val info = listOf(
            "visionos R0 video 1: bot check, answered as R6 (visionOS with visitor data: DIRECT, V PASS)",
            "visionos R0 video 5: bot check, answered as R6 (visionOS with visitor data: DIRECT, V PASS)",
        )

        val first = CensusDiff.compare(emptyList(), weekly(0))
        assertEquals("FIRST RUN — nothing to compare yet", first.headline)
        assertTrue(first.lines.isEmpty())
        assertEquals(info, first.info)

        val same = CensusDiff.compare(listOf(weekly(0)), weekly(HOUR))
        assertEquals("NO CHANGE — 0 days since the last run", same.headline)
        assertTrue(same.lines.isEmpty())
        assertEquals((listOf("NO CHANGE — 0 days since the last run") + info.map { "INFO $it" }).joinToString("\n"), same.text())

        // Anything short of a DIRECT, V-passed answer stays a CHANGED line with its runbook step.
        fun lines(vararg asks: CensusAsk) = CensusDiff.compare(emptyList(), run(0, *asks)).lines.map { it.render() }
        val again = direct(step = ClientCensus.AGAIN)
        assertEquals(
            listOf("CHANGED visionos R0 video 1: bot check, answered as R6 (visionOS with visitor data: DIRECT, V UNKNOWN) → §7 step 8"),
            lines(botCheck(), again.copy(v = "UNKNOWN", vTarget = "net")),
        )
        assertEquals(
            listOf("CHANGED visionos R0 video 1: bot check, answered as R6 (visionOS with visitor data: TOKEN_NEEDED, V FAIL) → §7 step 8"),
            lines(botCheck(), again.copy(finalClass = CensusClass.TOKEN_NEEDED, v = "FAIL", vTarget = "403")),
        )
        assertEquals(
            listOf("CHANGED visionos R0 video 1: bot check, answered as R6 (visionOS not asked again) → §7 step 8"),
            lines(botCheck()),
        )
    }

    @Test
    fun `a path one video gained and another lost is not a shape change (owner, 2026-10-15)`() {
        val url = "streamingData.adaptiveFormats[].url"
        val slots = mapOf(2 to "c3d4", 6 to "e5f6")
        fun mweb(video: Int, paths: List<String>) = direct(video = video, client = "mweb").copy(shapePaths = paths)
        val earlier = run(0, mweb(2, PATHS), mweb(6, PATHS - url), slots = slots)

        val swapped = CensusDiff.compare(listOf(earlier), run(DAY, mweb(2, PATHS - url), mweb(6, PATHS), slots = slots))
        assertEquals("NO CHANGE — 1 days since the last run", swapped.headline)
        assertTrue(swapped.lines.isEmpty())

        val more = CensusDiff.compare(
            listOf(earlier),
            run(DAY, mweb(2, PATHS - url), mweb(6, PATHS + "streamingData.newField"), slots = slots),
        )
        assertEquals(listOf("CHANGED shape mweb R0: +1 −0 (+streamingData.newField) → §7 step 6"), more.lines.map { it.render() })
    }

    @Test
    fun `the state file round-trips every field`() {
        val ask = direct().copy(
            reason = "Sign in to confirm you’re not a bot \"quoted\"\nnext",
            bound = true, lengthSeconds = 212, protectedFormats = 2, unaddressed = 1, sabr = true,
            videoCodecs = listOf("avc1", "vp09"), audioCodecs = listOf("mp4a"), videoRows = 3, audioRows = 1,
            solve = "ok-sig+n", vControl = "2xx", vReason = "CHECKED", vRow = "avc1 720p",
            shapeCore = "0123456789ab", shapeFull = "ba9876543210", shapeTruncated = true, kilobytes = 41, millis = 812,
        )
        val run = CensusRun(
            tool = ClientCensus.TOOL_LABEL, mode = "FULL", atMillis = 1_791_000_000_000L, millis = 61_000, bytes = 5_000_000,
            metered = true, requests = 57, stopped = "budget", slots = mapOf(1 to "a1b2", 10 to "ffff"),
            pages = listOf(CensusPage(1, "200", "MWEB", "2.20261002.01.00", "abcd1234", true, true, true, 900, 1_200)),
            asks = listOf(ask, direct(client = "android", step = "SH").copy(shapePaths = null, heights = emptyList())),
            hls = listOf(CensusHls(1, "visionos", hls())),
            versions = listOf(CensusVersion("visionos", "ytdlp", true, "note", 1, "1.02", true, false, true, "equal")),
            notes = listOf("alternates: none bundled", "ünïcode — note"),
        )
        val state = CensusState(runs = listOf(run, run.copy(atMillis = run.atMillis + DAY)), cooldownUntil = 42, sourcesAt = 7)

        assertEquals(state, CensusJson.readState(CensusJson.state(state)))
        assertEquals(CensusState(), CensusJson.readState("not json"))
        assertEquals(CensusState(), CensusJson.readState("{\"fmt\":2,\"runs\":[]}"))
        assertEquals(CensusState(), CensusJson.readState(null))
    }

    private fun direct(video: Int = 1, client: String = "visionos", step: String = "R0") = CensusAsk(
        video = video,
        client = client,
        step = step,
        version = "1.02",
        http = "200",
        answerClass = CensusClass.DIRECT,
        status = "OK",
        adaptive = 4,
        heights = listOf(720, 1080),
        v = "PASS",
        vTarget = "2xx",
        hls = true,
        shapePaths = PATHS,
    )

    /** visionOS's first request refused with a bot check (no rows, two status paths). */
    private fun botCheck(video: Int = 1) = direct(video = video).copy(
        answerClass = CensusClass.BOT_CHECK, finalClass = CensusClass.BOT_CHECK, status = "LOGIN_REQUIRED", adaptive = 0,
        heights = emptyList(), v = null, vTarget = null, hls = false, shapePaths = listOf("playabilityStatus", "playabilityStatus.status"),
    )

    private fun hls() = HlsCheck.Result(
        master = "2xx", variant = "2xx", segment = "2xx", variants = 2, maxHeight = 1080, audio = "muxed",
        codecs = listOf("avc1", "mp4a"), key = "NONE", segments = 43, durationSeconds = 212.0, durationMatch = true,
        requests = 3,
    )

    private fun run(
        at: Long,
        vararg asks: CensusAsk,
        hls: List<CensusHls> = emptyList(),
        versions: List<CensusVersion> = emptyList(),
        slots: Map<Int, String> = mapOf(1 to "a1b2"),
        stopped: String? = null,
    ) = CensusRun(
        tool = ClientCensus.TOOL_LABEL,
        mode = "WEEKLY",
        atMillis = at,
        stopped = stopped,
        slots = slots,
        asks = asks.toList(),
        hls = hls,
        versions = versions,
    )

    private companion object {
        const val HOUR = 60L * 60 * 1000
        const val DAY = 24 * HOUR
        val PATHS = listOf(
            "playabilityStatus", "playabilityStatus.status", "streamingData", "streamingData.adaptiveFormats",
            "streamingData.adaptiveFormats[].itag", "streamingData.adaptiveFormats[].url", "videoDetails",
            "videoDetails.videoId",
        )
    }
}
