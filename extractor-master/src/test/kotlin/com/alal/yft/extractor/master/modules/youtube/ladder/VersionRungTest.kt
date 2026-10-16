package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.path
import com.alal.yft.extractor.master.modules.youtube.MasterYouTubeModule
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.3 D5a — A, the self-healed version rung (MASTER_KEY_PHASE1_3_PLAN.md §4 D5a "As built
 * (A)", guardrail 5, §9 item 3), in the change simulator's worlds: the version cases (V1, Y1–Y6)
 * on every base answer, plus the rules A keeps (learned first, one source read a day, only the
 * version changes, kept only after OK + V, quiet after a bot check, none in a trial). Phase 1.4
 * item 3: visionOS's bundled alternate (NewPipe's 1.04, [MasterClientTable.VISION_OS_ALTERNATE])
 * goes last, once, after the learned and the self-healed version.
 */
class VersionRungTest {
    private val base = Sim.BASE_VERSION
    private val new = Sim.NEW_VERSION
    private val alternate = MasterClientTable.VISION_OS_ALTERNATE.version

    private class Post(val url: String, val body: String, val headers: Map<String, String>, val maxBodyBytes: Long) {
        val client: String? get() = ShapeLocator.parse(body).path("context", "client", "clientName").asStringOrNull
    }

    /** The world's HTTP, recording every player request (the chain's and A's) as it went out. */
    private class Recording(private val world: SimWorld) : ExtractorHttpClient by world.http {
        val posts = mutableListOf<Post>()

        override suspend fun postJson(url: String, body: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
            posts += Post(url, body, headers, maxBodyBytes)
            return world.http.postJson(url, body, headers, maxBodyBytes)
        }
    }

    private class Ran(
        val world: SimWorld,
        val health: ClientHealth,
        val lookups: List<SimLookup>,
        val notes: List<List<String>>,
        val sources: List<ClientLadderSimTest.SimSourceReader.Read>,
        val posts: List<Post>,
    ) {
        val visionAsks: List<String> get() = world.asks.filter { it.client == SimClient.VISION_OS }.map(SimAsk::version)
        val allNotes: List<String> get() = notes.flatten()
    }

    private suspend fun run(
        case: SimCase,
        lookups: Int = case.lookups,
        health: ClientHealth = ClientHealth(null) { Sim.NOW },
        withSources: Boolean = true,
        trial: LadderTrial = LadderTrial.NONE,
        setup: SimWorld.() -> Unit = {},
    ): Ran {
        val world = case.world().apply(setup)
        val http = Recording(world)
        val sources = ClientLadderSimTest.SimSourceReader(world)
        val ladder = MasterClientLadder(health, ClientLadderSimTest.SimStartReader(world), trial, sources.takeIf { withSources })
        val module = MasterYouTubeModule(http, ladder = ladder)
        val notes = mutableListOf<List<String>>()
        val judged = (0 until lookups).map {
            val from = world.http.log.size
            val result = module.extract(SimMeasure.request())
            val details = when (result) {
                is SiteExtractionResult.Success -> result.details
                is SiteExtractionResult.Failure -> result.details
            }
            notes += details.filter { it.startsWith("ladder ") }.map { it.removePrefix("ladder ") }
            SimMeasure.judge(world, case, result, from)
        }
        return Ran(world, health, judged, notes, sources.reads, http.posts)
    }

    /** R6 (no ladder) in the same world, for "never worse than R6". */
    private suspend fun r6(case: SimCase, lookups: Int = case.lookups, setup: SimWorld.() -> Unit = {}): List<SimLookup> {
        val world = case.world().apply(setup)
        val module = MasterYouTubeModule(world.http)
        return (0 until lookups).map {
            val from = world.http.log.size
            SimMeasure.judge(world, case, module.extract(SimMeasure.request()), from)
        }
    }

    private fun cases(klass: String): List<SimCase> = SimCases.all().filter { it.klass == klass }.also { assertTrue(klass, it.isNotEmpty()) }

    private fun rank(outcome: SimOutcome): Int = SimOutcome.entries.size - outcome.ordinal

    @Test
    fun `V1 and Y1 complete through A on every base in two player requests`() = runTest(timeout = 5.minutes) {
        for (case in cases("V1") + cases("Y1")) {
            val ran = run(case)
            val lookup = ran.lookups.single()
            assertEquals("$case", SimOutcome.COMPLETE, lookup.outcome)
            assertEquals("$case", 0, lookup.wrong.size)
            assertEquals("$case players", 2, lookup.players)
            assertEquals("$case pages", 0, lookup.pages)
            assertEquals("$case sources", 1, lookup.sources)
            assertEquals("$case credentials", 0, lookup.credentials)
            assertEquals("$case", listOf(base, new), ran.visionAsks)
            assertEquals("$case", new, ran.health.learnedVersion("visionos"))
            val client = requireNotNull(ran.health.client("visionos"))
            assertEquals("$case", "yt-dlp", client.learnedSource)
            assertEquals("$case", ClientHealth.Rung.A, client.variant)
            assertFalse("$case", ran.health.cooling("visionos"))
            assertTrue("$case ${ran.allNotes}", ran.allNotes.any { it.startsWith("A: the yt-dlp version answered (") })
            assertTrue("$case R6", r6(case).single().outcome != SimOutcome.COMPLETE)
        }
    }

    @Test
    fun `A's request is the chain's with only the version changed`() = runTest(timeout = 5.minutes) {
        for (case in cases("V1") + cases("Y6")) {
            val ran = run(case)
            val vision = ran.posts.filter { it.client == "VISIONOS" }
            assertEquals("$case", 2, vision.size)
            val (chain, a) = vision
            assertEquals("$case", chain.url, a.url)
            assertEquals("$case", chain.maxBodyBytes, a.maxBodyBytes)
            assertEquals("$case", chain.body.replace("\"clientVersion\":\"$base\"", "\"clientVersion\":\"$new\""), a.body)
            assertEquals("$case", chain.headers.keys.toList(), a.headers.keys.toList())
            assertEquals("$case", chain.headers + (VersionSource.VERSION_HEADER to new), a.headers)
            assertEquals("$case", base, chain.headers[VersionSource.VERSION_HEADER])
            // The user agent and the device stay the profile's, whatever the source lists (Y6).
            assertTrue("$case", "\"deviceModel\":\"RealityDevice17,1\"" in a.body)
            assertFalse("$case", "Other" in a.body)
            assertEquals("$case", SimOutcome.COMPLETE, ran.lookups.single().outcome)
        }
    }

    @Test
    fun `the source read sends the user agent only, at most 64 KiB, once`() = runTest(timeout = 5.minutes) {
        val ran = run(cases("V1").first())
        val read = ran.sources.single()
        assertEquals(CensusSources.YT_DLP_URL, read.url)
        assertEquals(mapOf("User-Agent" to Sim.USER_AGENT), read.headers)
        assertEquals(CensusSources.MAX_SOURCE_BYTES, read.maxBodyBytes)
        assertFalse(ran.health.maySourceRead("visionos"))
    }

    @Test
    fun `Y5 three lookups, one source read, then the learned version first`() = runTest(timeout = 5.minutes) {
        for (case in cases("Y5")) {
            val ran = run(case)
            assertEquals("$case", listOf(SimOutcome.COMPLETE, SimOutcome.COMPLETE, SimOutcome.COMPLETE), ran.lookups.map(SimLookup::outcome))
            assertEquals("$case", listOf(1, 0, 0), ran.lookups.map(SimLookup::sources))
            assertEquals("$case", listOf(2, 2, 2), ran.lookups.map(SimLookup::players))
            assertEquals("$case", listOf(base, new, base, new, base, new), ran.visionAsks)
            assertEquals("$case", 1, ran.sources.size)
            assertTrue("$case", ran.notes[1].any { it.startsWith("A: the learned version answered (") })
            assertTrue("$case", ran.notes[2].any { it.startsWith("A: the learned version answered (") })
            assertEquals("$case", new, ran.health.learnedVersion("visionos"))
        }
    }

    @Test
    fun `Y4 a rejected source version is not stored, A cools down and sends nothing next lookup`() = runTest(timeout = 5.minutes) {
        for (case in cases("Y4")) {
            val ran = run(case, lookups = 2)
            val r6 = r6(case, lookups = 2)
            assertEquals("$case", r6.map(SimLookup::outcome), ran.lookups.map(SimLookup::outcome))
            assertTrue("$case", ran.lookups.all { it.wrong.isEmpty() })
            assertEquals("$case", listOf(base, new, alternate, base), ran.visionAsks)
            assertEquals("$case", 1, ran.sources.size)
            assertNull("$case", ran.health.learnedVersion("visionos"))
            assertTrue("$case", ran.health.cooling("visionos"))
            assertTrue("$case", "A: the yt-dlp version rejected too" in ran.notes[0])
            assertTrue("$case", "A: the bundled version rejected too" in ran.notes[0])
            assertTrue("$case", "A: no version answered" in ran.notes[0])
            assertTrue("$case", "A: not asked (cooldown or bot check)" in ran.notes[1])
            assertEquals("$case", r6[0].players + 2, ran.lookups[0].players)
        }
    }

    @Test
    fun `Y2 and Y3 one read, only the bundled 1_04 asked, the outcome R6 gives`() = runTest(timeout = 5.minutes) {
        val why = mapOf(
            "garbage" to "A: source no newer version (no value)",
            "pattern" to "A: source no newer version (not the version pattern)",
            "older" to "A: source no newer version (not newer)",
            "same" to "A: source no newer version (the bundled value)",
            "down" to "A: source read failed (NETWORK)",
            "5xx" to "A: source read failed (HTTP_STATUS 503)",
        )
        for (case in cases("Y2") + cases("Y3")) {
            val ran = run(case)
            assertEquals("$case", r6(case).map(SimLookup::outcome), ran.lookups.map(SimLookup::outcome))
            assertTrue("$case", ran.lookups.all { it.wrong.isEmpty() })
            assertEquals("$case", listOf(base, alternate), ran.visionAsks)
            assertEquals("$case", 1, ran.lookups.single().sources)
            assertTrue("$case ${ran.allNotes}", why.getValue(case.variant) in ran.allNotes)
            assertTrue("$case", "A: the bundled version rejected too" in ran.allNotes)
            assertTrue("$case", "A: no version answered" in ran.allNotes)
            assertNull("$case", ran.health.learnedVersion("visionos"))
        }
    }

    @Test
    fun `an A answer without a V pass is dropped, not learned, never worse than R6`() = runTest(timeout = 5.minutes) {
        val closed: SimWorld.() -> Unit = { refused = { client, _, _ -> client == SimClient.VISION_OS } }
        for (case in cases("V1")) {
            val ran = run(case, setup = closed)
            val r6 = r6(case, setup = closed).single()
            val lookup = ran.lookups.single()
            assertEquals("$case", 0, lookup.wrong.size)
            assertTrue("$case ${r6.outcome} -> ${lookup.outcome}", rank(lookup.outcome) >= rank(r6.outcome))
            assertEquals("$case", listOf(base, new), ran.visionAsks)
            assertTrue("$case ${ran.allNotes}", ran.allNotes.any { it.startsWith("A: the yt-dlp version's answer dropped (") && it.endsWith(", no V pass)") })
            assertNull("$case", ran.health.learnedVersion("visionos"))
            // Dropped means unused: neither the chain nor H gets A's answer; visionOS cools down.
            assertTrue("$case ${ran.allNotes}", ran.allNotes.none { it.startsWith("H: one HLS row") })
            assertTrue("$case ${ran.allNotes}", ran.health.cooling("visionos"))
            assertTrue("$case", ran.health.client("visionos")?.variant != ClientHealth.Rung.A)
        }
    }

    @Test
    fun `after a bot check A sends nothing and reads nothing`() = runTest(timeout = 5.minutes) {
        val health = ClientHealth(null) { Sim.NOW }.apply { record("mweb", CensusClass.BOT_CHECK) }
        val case = cases("V1").first()
        val ran = run(case, health = health)
        assertEquals(listOf(base), ran.visionAsks)
        assertTrue(ran.sources.isEmpty())
        assertTrue("A: not asked (cooldown or bot check)" in ran.allNotes)
        assertNull(health.learnedVersion("visionos"))
        assertEquals(r6(case).map(SimLookup::outcome), ran.lookups.map(SimLookup::outcome))
    }

    @Test
    fun `the learned version goes first, with no source read`() = runTest(timeout = 5.minutes) {
        val health = ClientHealth(null) { Sim.NOW }.apply { learn("visionos", Sim.NEW_VERSION, "yt-dlp") }
        val ran = run(cases("V1").first(), health = health)
        assertEquals(listOf(base, new), ran.visionAsks)
        assertTrue(ran.sources.isEmpty())
        assertEquals(SimOutcome.COMPLETE, ran.lookups.single().outcome)
        assertTrue(ran.allNotes.any { it.startsWith("A: the learned version answered (") })
        assertEquals("yt-dlp", health.client("visionos")?.learnedSource)
    }

    @Test
    fun `a rejected learned version is forgotten, then the source's version goes`() = runTest(timeout = 5.minutes) {
        val health = ClientHealth(null) { Sim.NOW }.apply { learn("visionos", "1.04", "yt-dlp") }
        val ran = run(cases("V1").first(), health = health)
        assertEquals(listOf(base, "1.04", new), ran.visionAsks)
        assertEquals(1, ran.sources.size)
        assertEquals(SimOutcome.COMPLETE, ran.lookups.single().outcome)
        assertTrue("A: the learned version rejected too" in ran.allNotes)
        assertTrue(ran.allNotes.any { it.startsWith("A: the yt-dlp version answered (") })
        assertEquals(new, health.learnedVersion("visionos"))
        assertTrue(ran.lookups.single().players <= 1 + LadderLookup.MAX_A_PER_LOOKUP)
    }

    @Test
    fun `without a source reader A reads nothing, asks the bundled 1_04 once, and R6's outcome stays`() = runTest(timeout = 5.minutes) {
        val case = cases("V1").first()
        val ran = run(case, withSources = false)
        assertEquals(listOf(base, alternate), ran.visionAsks)
        assertEquals(0, ran.lookups.single().sources)
        assertTrue("A: source not read (no source reader)" in ran.allNotes)
        assertTrue("A: the bundled version rejected too" in ran.allNotes)
        assertTrue("A: no version answered" in ran.allNotes)
        assertEquals(r6(case).map(SimLookup::outcome), ran.lookups.map(SimLookup::outcome))
        assertTrue(ran.health.maySourceRead("visionos"))
    }

    @Test
    fun `no A in a live-check trial`() = runTest(timeout = 5.minutes) {
        val ran = run(cases("V1").first(), trial = LadderTrial.of("", "L5"))
        assertEquals(listOf(base), ran.visionAsks)
        assertTrue(ran.sources.isEmpty())
        assertTrue(ran.allNotes.none { it.startsWith("A: ") })
    }

    @Test
    fun `a closed visionOS reads the source once, asks the bundled 1_04 once and cools down`() = runTest(timeout = 5.minutes) {
        for (case in cases("H1")) {
            val ran = run(case)
            assertEquals("$case", r6(case).map(SimLookup::outcome), ran.lookups.map(SimLookup::outcome))
            assertEquals("$case", listOf(base, alternate, base), ran.visionAsks)
            assertEquals("$case", 1, ran.sources.size)
            assertTrue("$case ${ran.notes[0]}", "A: source read failed (HTTP_STATUS 404)" in ran.notes[0])
            assertTrue("$case", "A: not asked (cooldown or bot check)" in ran.notes[1])
        }
    }

    @Test
    fun `1_02 rejected and no newer source value - the bundled 1_04 is asked once, checked by V and learned`() = runTest(timeout = 5.minutes) {
        val accepts: SimWorld.() -> Unit = { acceptedVersions = setOf(alternate) }
        for (case in cases("Y2").filter { it.variant == "same" }) {
            val ran = run(case, setup = accepts)
            val lookup = ran.lookups.single()
            assertEquals("$case", SimOutcome.COMPLETE, lookup.outcome)
            assertEquals("$case", 0, lookup.wrong.size)
            assertEquals("$case", listOf(base, alternate), ran.visionAsks)
            assertEquals("$case", 1, ran.sources.size)
            assertTrue("$case ${ran.allNotes}", ran.allNotes.any { it.startsWith("A: the bundled version answered (") })
            assertEquals("$case", alternate, ran.health.learnedVersion("visionos"))
            assertTrue("$case", lookup.players <= 1 + LadderLookup.MAX_A_PER_LOOKUP)
            assertTrue("$case R6", r6(case, setup = accepts).single().outcome != SimOutcome.COMPLETE)
        }
    }

    @Test
    fun `after a bot check the bundled 1_04 is not asked`() = runTest(timeout = 5.minutes) {
        val health = ClientHealth(null) { Sim.NOW }.apply { record("mweb", CensusClass.BOT_CHECK) }
        val case = cases("Y2").first { it.variant == "same" }
        val ran = run(case, health = health, setup = { acceptedVersions = setOf(alternate) })
        assertEquals(listOf(base), ran.visionAsks)
        assertTrue(ran.sources.isEmpty())
        assertTrue("A: not asked (cooldown or bot check)" in ran.allNotes)
        assertNull(health.learnedVersion("visionos"))
    }

    @Test
    fun `the healthy path never asks the bundled 1_04`() = runTest(timeout = 5.minutes) {
        val case = cases("V1").first()
        val ran = run(case, setup = { acceptedVersions = setOf(base) })
        assertEquals(listOf(base), ran.visionAsks)
        assertTrue(ran.sources.isEmpty())
        assertTrue(ran.allNotes.none { it.startsWith("A: ") })
    }
}
