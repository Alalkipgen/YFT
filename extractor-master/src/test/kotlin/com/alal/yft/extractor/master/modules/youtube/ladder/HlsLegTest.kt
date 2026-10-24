package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.modules.youtube.YouTubePlayerResponseParser
import com.alal.yft.extractor.master.modules.youtube.ladder.HlsLeg.Verdict
import com.alal.yft.extractor.master.modules.youtube.ladder.HlsLeg.Why
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.3 D5c: the H leg (HlsLeg, §3 H steps 1–3) on the D1 simulator's HLS (master, variant
 * and segments made in code, with F3's faults as knobs); offline, no live calls.
 */
class HlsLegTest {
    private val vision = SimAsk(SimClient.VISION_OS, Sim.BASE_VERSION, visitor = false, sts = false)
    private val headers = mapOf(
        "User-Agent" to Sim.USER_AGENT,
        "Referer" to "https://www.youtube.com/",
        "Cookie" to Sim.COOKIE,
        "Authorization" to "Bearer sim",
        "X-Goog-AuthUser" to "0",
        "Range" to "bytes=0-",
    )

    /** What D6 hands the leg for visionOS's answer: steps 1 and 2. */
    private class Prepared(val address: String?, val length: Long?)

    private fun prepare(text: String): Prepared {
        val root = ShapeLocator.parse(text)
        val normalized = ShapeNormalizer.normalize(text, Sim.VIDEO_ID, Sim.NOW)
        val located = normalized.hls ?: root?.let { ShapeLocator.scan(it, Sim.VIDEO_ID, text).hls }
        val parsed = YouTubePlayerResponseParser.parse(text, Sim.NOW)
        return Prepared(HlsLeg.address(root, located), HlsLeg.length(parsed, Sim.VIDEO_ID, normalized, null))
    }

    private suspend fun leg(world: SimWorld, client: ExtractorHttpClient = world.http): HlsLeg.Outcome {
        val prepared = prepare(SimJson.write(world.answer(vision)))
        return HlsLeg(client).lookup().run(prepared.address, prepared.length, headers)
    }

    @Test
    fun `step 1 - the answer's HLS address, else the one located`() {
        val world = SimWorld("player_visionos.json")
        val master = world.hls.masterUrl
        fun root(vararg top: Pair<String, Any?>) = ShapeLocator.parse(SimJson.write(linkedMapOf(*top)))
        val canonical = root("streamingData" to linkedMapOf("hlsManifestUrl" to master))
        assertEquals(master, HlsLeg.address(canonical, located = null))
        assertEquals("the answer's own field wins", master, HlsLeg.address(canonical, located = "https://manifest.googlevideo.com/api/manifest/hls_playlist/x"))
        val foreign = root("streamingData" to linkedMapOf("hlsManifestUrl" to "https://${Sim.FOREIGN_HOST}/api/manifest/hls_variant/x"))
        assertNull("another host: no H", HlsLeg.address(foreign, located = master))
        assertNull(HlsLeg.address(root("streamingData" to linkedMapOf("hlsManifestUrl" to master.replace("https:", "http:"))), master))
        assertEquals(master, HlsLeg.address(root("playback" to linkedMapOf("hls" to master)), located = master))
        assertNull(HlsLeg.address(root("playback" to linkedMapOf("hls" to master)), located = null))
        assertNull(HlsLeg.address(null, located = null))

        val two = SimJson.write(linkedMapOf("a" to master, "b" to world.hls.variantUrl))
        assertNull("two located: none is the one", ShapeLocator.scan(ShapeLocator.parse(two)!!, Sim.VIDEO_ID, two).hls)
    }

    @Test
    fun `step 2 - the length from the copied parser, L's binding or the page, never after a verdict`() {
        val text = SimJson.write(SimWorld("player_visionos.json").answer(vision))
        val parsed = YouTubePlayerResponseParser.parse(text, Sim.NOW)
        val normalized = ShapeNormalizer.normalize(text, Sim.VIDEO_ID, Sim.NOW)
        val seconds = SimWorld("player_visionos.json").lengthSeconds
        assertEquals(seconds, HlsLeg.length(parsed, Sim.VIDEO_ID, null, null))
        assertNull("the answer is for another video", HlsLeg.length(parsed, Sim.OTHER_VIDEO, null, null))
        assertEquals(77L, HlsLeg.length(parsed, Sim.OTHER_VIDEO, null, 77L))

        val moved = SimJson.write(SimWorld("player_visionos.json").apply { edits += { _, json -> LShapeCases.moveStreamingData(json) } }.answer(vision))
        val bound = ShapeNormalizer.normalize(moved, Sim.VIDEO_ID, Sim.NOW)
        assertEquals(seconds, bound.lengthSeconds)
        assertEquals("L's binding", seconds, HlsLeg.length(null, Sim.VIDEO_ID, bound, 77L))
        assertEquals(seconds, HlsLeg.length(YouTubePlayerResponseParser.parse(moved, Sim.NOW), Sim.VIDEO_ID, bound, null))

        val login = SimJson.write(linkedMapOf("playabilityStatus" to linkedMapOf("status" to "LOGIN_REQUIRED", "reason" to "Sign in")))
        val stop = ShapeNormalizer.normalize(login, Sim.VIDEO_ID, Sim.NOW)
        assertTrue(stop is ShapeNormalizer.Stop)
        assertNull("a verdict: no H", HlsLeg.length(parsed, Sim.VIDEO_ID, stop, 77L))
        assertNull(HlsLeg.length(null, Sim.VIDEO_ID, null, null))
        assertNull(HlsLeg.length(null, Sim.VIDEO_ID, null, 0L))
        assertEquals(seconds, normalized.lengthSeconds)
    }

    @Test
    fun `step 3 - a good HLS gives one row from 3 requests without credentials`() = runTest {
        val world = SimWorld("player_visionos.json")
        val recording = Recording(world.http)
        val row = leg(world, recording) as HlsLeg.Row
        assertNull(world.truth(row.master))
        assertEquals(world.hls.masterUrl, row.master)
        assertEquals(3, row.requests)
        assertEquals(3, world.http.count(SimHttp.Kind.HLS))
        assertEquals("the segment is read, not probed", 0, world.http.count(SimHttp.Kind.PROBE))
        assertTrue(world.http.log.none { it.credentials })
        for (sent in recording.calls) {
            val names = sent.headers.keys.map(String::lowercase)
            assertFalse(names.any { it in setOf("cookie", "authorization", "x-goog-authuser") })
            assertEquals(Sim.USER_AGENT, sent.headers["User-Agent"])
        }
        assertEquals(listOf(null, null, "bytes=0-65535"), recording.calls.map { it.headers["Range"] })
        assertEquals("ts", row.container)
        assertEquals(1080, row.maxHeight)
        assertEquals("muxed", row.audio)
        assertEquals("H: HLS (3 variants, ≤ 1080p, audio muxed, ts)", row.detail())

        val candidate = row.candidate(Sim.MOBILE_PAGE, "Title", null, Sim.USER_AGENT, Sim.NOW)
        assertEquals(MediaKind.HLS, candidate.kind)
        assertEquals(HlsLeg.HLS_MIME, candidate.mimeType)
        assertEquals(world.hls.masterUrl, candidate.mediaUrl)
        assertEquals(world.lengthSeconds * 1_000, candidate.durationMillis)
        assertNull(candidate.requestContext.cookie)
        assertEquals(4_102_444_800_000L, candidate.expiresAtEpochMs)
        assertEquals(1080, candidate.height)
        assertEquals(false, candidate.drmHint)

        for (printed in listOf(row.toString(), row.detail(), HlsLeg.None(Why.KEY, Verdict.FAIL, row.result).toString())) {
            assertFalse(printed, printed.contains("http") || printed.contains("googlevideo") || printed.contains("yftsim"))
        }
    }

    @Test
    fun `step 3 - faults, keys and containers`() = runTest {
        val cases = listOf<Triple<String, SimHls.() -> Unit, Pair<Verdict, Why>?>>(
            Triple("master 403", { master = SimWorld.FORBIDDEN }, Verdict.FAIL to Why.MASTER),
            Triple("variant 403", { variant = SimWorld.FORBIDDEN }, Verdict.FAIL to Why.VARIANT),
            Triple("unreachable", { reachable = false }, Verdict.UNKNOWN to Why.MASTER),
            Triple("master 503", { master = 503 }, Verdict.UNKNOWN to Why.MASTER),
            Triple("segment 403", { segment = SimWorld.FORBIDDEN }, Verdict.FAIL to Why.SEGMENT),
            Triple("segment type html", { contentType = "text/html" }, Verdict.FAIL to Why.SEGMENT_TYPE),
            Triple("segment start", { startText = "<" }, Verdict.FAIL to Why.SEGMENT_START),
            Triple("segment host", { segmentHost = Sim.FOREIGN_HOST }, Verdict.FAIL to Why.SEGMENT),
            Triple("duration", { durationSeconds += 2.5 }, Verdict.FAIL to Why.DURATION),
            Triple("duration within 2 s", { durationSeconds += 1.5 }, null),
            Triple("key", { key = "AES-128" }, Verdict.FAIL to Why.KEY),
            Triple("key and a 403 variant: the fail names the first", { key = "SAMPLE-AES"; variant = SimWorld.FORBIDDEN }, Verdict.FAIL to Why.VARIANT),
            Triple("key NONE", { key = "NONE" }, null),
            // The owner's (က), 2026-10-17: a generic binary type lets the start decide.
            Triple("octet-stream, ts start", { contentType = "application/octet-stream" }, null),
            Triple("no content type", { contentType = "" }, null),
            Triple("octet-stream, fragmented mp4 (live)", { contentType = "application/octet-stream"; startText = "\u0000\u0000\u0004\u0074moof" }, null),
            Triple("octet-stream, html start", { contentType = "application/octet-stream"; startText = "<" }, Verdict.FAIL to Why.SEGMENT_START),
            Triple("binary/octet-stream, no container", { contentType = "binary/octet-stream"; startText = "@" }, Verdict.FAIL to Why.SEGMENT_START),
            Triple("mp4 type, ts start", { contentType = "video/mp4" }, Verdict.FAIL to Why.SEGMENT_START),
            Triple("fragmented mp4", { contentType = "video/mp4"; startText = "\u0000\u0000\u0000\u0018styp" }, null),
            Triple("ts with charset", { contentType = "video/MP2T; charset=binary" }, null),
        )
        for ((label, fault, expected) in cases) {
            val world = SimWorld("player_visionos.json").apply { hls.fault() }
            val outcome = leg(world)
            if (expected == null) {
                assertTrue(label, outcome is HlsLeg.Row)
            } else {
                val none = outcome as? HlsLeg.None ?: throw AssertionError("$label: a row")
                assertEquals(label, expected, none.verdict to none.why)
            }
            assertTrue(label, outcome.requests <= 3)
            assertTrue(label, world.http.log.none { it.credentials })
        }
        val foreign = SimWorld("player_visionos.json").apply { hls.segmentHost = Sim.FOREIGN_HOST }
        leg(foreign)
        assertEquals("nothing is asked off googlevideo.com", 2, foreign.http.count(SimHttp.Kind.HLS))
    }

    /**
     * Phase 1.4 item 2, run 4 (live 2026-10-17): YouTube's HLS segment ignores the range and
     * answers 200 with the whole segment. Through the HTTP client that is "too large" (H: SEGMENT,
     * the phone's note); through the ladder's media reader only the first 64 KiB are kept.
     */
    @Test
    fun `run 4 - a host that ignores the range - the reader keeps the start`() = runTest {
        val world = SimWorld("player_visionos.json")
        val prepared = prepare(SimJson.write(world.answer(vision)))
        val wholeSegment = object : ExtractorHttpClient by world.http {
            override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult =
                if ("/yftsim/seg/" in url) ExtractorHttpResult.Failure(SiteExtractionFailure.RESPONSE_TOO_LARGE, 200)
                else world.http.get(url, headers, maxBodyBytes)
        }
        val before = HlsLeg(wholeSegment).lookup().run(prepared.address, prepared.length, headers) as HlsLeg.None
        assertEquals(Verdict.UNKNOWN to Why.SEGMENT, before.verdict to before.why)

        val asked = mutableListOf<Pair<Map<String, String>, Int>>()
        val reader = object : MediaStartReader {
            override suspend fun read(url: String, headers: Map<String, String>, maxBytes: Int): MediaStart {
                asked += headers to maxBytes
                val whole = ("G" + "@".repeat(1_048_575)).toByteArray(Charsets.ISO_8859_1)
                return MediaStart.Read(200, "video/mp2t", null, null, whole.copyOf(maxBytes))
            }
        }
        val row = HlsLeg(wholeSegment, reader = reader).lookup().run(prepared.address, prepared.length, headers) as HlsLeg.Row
        assertEquals("ts", row.container)
        // The live answer: 200, application/octet-stream, a moof start; the start decides (the owner's (က)).
        val live = object : MediaStartReader {
            override suspend fun read(url: String, headers: Map<String, String>, maxBytes: Int): MediaStart {
                val whole = ("\u0000\u0000\u0004\u0074moof" + "@".repeat(1_048_568)).toByteArray(Charsets.ISO_8859_1)
                return MediaStart.Read(200, "application/octet-stream", null, null, whole.copyOf(maxBytes))
            }
        }
        val liveRow = HlsLeg(wholeSegment, reader = live).lookup().run(prepared.address, prepared.length, headers) as HlsLeg.Row
        assertEquals("mp4", liveRow.container)
        assertEquals(3, row.requests)
        assertEquals(listOf(65_536), asked.map { it.second })
        val names = asked.single().first.keys.map(String::lowercase)
        assertFalse(names.toString(), names.any { it in setOf("cookie", "authorization", "x-goog-authuser", "range") })
    }

    @Test
    fun `no address or no length - no request, and one H check per lookup`() = runTest {
        val world = SimWorld("player_visionos.json")
        val lookup = HlsLeg(world.http).lookup()
        assertEquals(Why.NO_ADDRESS, (lookup.run(null, 212L, headers) as HlsLeg.None).why)
        assertEquals(Why.NO_LENGTH, (lookup.run(world.hls.masterUrl, null, headers) as HlsLeg.None).why)
        assertEquals(Why.NO_LENGTH, (lookup.run(world.hls.masterUrl, 0L, headers) as HlsLeg.None).why)
        assertTrue(world.http.log.isEmpty())

        val first = lookup.run(world.hls.masterUrl, world.lengthSeconds, headers)
        assertTrue(first is HlsLeg.Row)
        assertSame("the same address again: the first outcome, no request", first, lookup.run(world.hls.masterUrl, world.lengthSeconds, headers))
        assertEquals(Why.SPENT, (lookup.run(world.hls.variantUrl, world.lengthSeconds, headers) as HlsLeg.None).why)
        assertEquals(3, world.http.log.size)
        assertTrue("a new lookup checks again", HlsLeg(world.http).lookup().run(world.hls.masterUrl, world.lengthSeconds, headers) is HlsLeg.Row)
        assertEquals(6, world.http.log.size)

        val noLength = SimWorld("player_visionos.json").apply {
            edits += { _, json -> SimJson.obj(json["videoDetails"]).remove("lengthSeconds") }
        }
        val outcome = leg(noLength) as HlsLeg.None
        assertEquals("a missing length: no H rows (guardrail 6)", Why.NO_LENGTH, outcome.why)
        assertTrue(noLength.http.log.isEmpty())
    }

    @Test
    fun `H matrix - V4 to V6, V10, V11, L1 to L13, F3, key and length x 5 bases - never a wrong row`() = runTest {
        val klasses = setOf("V4", "V5", "V6", "V10", "V11", "F3") + (1..13).map { "L$it" }
        var runs = 0
        var rows = 0
        var extra = 0
        val wrong = ArrayList<String>()
        val missed = ArrayList<String>()
        val summary = LinkedHashMap<String, IntArray>()
        for (base in Sim.BASES) {
            val cases = SimCases.forBase(base).filter { it.klass in klasses }.map { it.id to it } +
                listOf(
                    "KEY/$base" to SimCase("KEY", "", base, VMode.PASS, emptySet()) { hls.key = "AES-128" },
                    "LENGTH/$base" to SimCase("LENGTH", "", base, VMode.PASS, emptySet()) {
                        edits += { _, json -> SimJson.obj(json["videoDetails"]).remove("lengthSeconds") }
                    },
                )
            for ((id, case) in cases) {
                val world = case.world()
                val outcome = leg(world)
                runs += 1
                val row = outcome as? HlsLeg.Row
                val key = case.klass + (if (case.variant.isNotEmpty()) "/" + case.variant else "") + " " + case.vMode.name.lowercase()
                val counts = summary.getOrPut(key) { IntArray(2) }
                counts[0] += 1
                if (row != null) {
                    rows += 1
                    counts[1] += 1
                    val why = world.truth(row.master) ?: if (case.terminal) "after a verdict" else null
                    if (why != null) wrong += "$id: $why"
                    if (Rung.H !in case.paths) extra += 1
                } else if (Rung.H in case.paths) {
                    missed += "$id: ${(outcome as HlsLeg.None).why}"
                }
                assertTrue(id, outcome.requests <= 3)
                assertTrue(id, world.http.log.none { it.credentials })
                assertEquals(id, 0, world.http.count(SimHttp.Kind.PROBE))
            }
        }
        println("H matrix: runs $runs, rows $rows (of them $extra where D1 lists no H path), wrong ${wrong.size}, missed ${missed.size}")
        println("H matrix rows per case (runs/rows): " + summary.entries.joinToString("; ") { "${it.key} ${it.value[0]}/${it.value[1]}" })
        assertEquals(wrong.joinToString("\n"), 0, wrong.size)
        assertEquals("every case with an H path completes through H: " + missed.joinToString("; "), 0, missed.size)
    }

    /** Records each request's headers (never printed); answers from [inner]. */
    private class Recording(private val inner: ExtractorHttpClient) : ExtractorHttpClient {
        class Call(val headers: Map<String, String>)

        val calls = ArrayList<Call>()

        override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
            calls += Call(headers)
            return inner.get(url, headers, maxBodyBytes)
        }

        override suspend fun postJson(url: String, body: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult =
            throw AssertionError("the H leg never posts")

        override suspend fun probe(url: String, headers: Map<String, String>, timeoutMillis: Long): ExtractorProbeResult =
            throw AssertionError("the H leg reads the segment instead")
    }
}
