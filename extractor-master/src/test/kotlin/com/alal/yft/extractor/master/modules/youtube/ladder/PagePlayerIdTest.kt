package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.master.modules.youtube.MasterYouTubeModule
import com.alal.yft.extractor.master.modules.youtube.YouTubePlayerResponseParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 1.4 item 4: the last good player ID when the page names none (never after a bot check). */
class PagePlayerIdTest {
    @Test
    fun `the copied reader reads the last good player ID put in front of a page that names none`() {
        val page = "<html><script>ytcfg.set({\"PLAYER_JS_URL\":\"/s/player/abcd1234/player_ias.vflset/en_US/base.js\"})</script></html>"
        assertEquals("abcd1234", PagePlayerId.named(page))
        assertEquals("abcd1234", YouTubePlayerResponseParser.pageSignals(page).playerId)
        val none = page.replace("PLAYER_JS_URL", "PLAYER_SCRIPT_V2")
        val odd = page.replace("/s/player/", "/s/elsewhere/")
        for (body in listOf(none, odd)) {
            assertNull(PagePlayerId.named(body))
            assertNull(YouTubePlayerResponseParser.pageSignals(body).playerId)
            val fixed = PagePlayerId.withPlayerId(body, Sim.PLAYER_ID)
            assertEquals(Sim.PLAYER_ID, PagePlayerId.named(fixed))
            assertEquals(Sim.PLAYER_ID, YouTubePlayerResponseParser.pageSignals(fixed).playerId)
            assertTrue(fixed.endsWith(body))
        }
    }

    @Test
    fun `a successful lookup keeps the page's player ID`() = runTest {
        val world = SimWorld(Sim.BASES.first()).apply { visionCipher = true }
        val ladder = MasterClientLadder(ClientHealth(null) { Sim.NOW }, ClientLadderSimTest.SimStartReader(world))
        val result = MasterYouTubeModule(world.http, SimSolver(world), ladder).extract(SimMeasure.request())
        assertTrue(result is SiteExtractionResult.Success)
        assertEquals(Sim.PLAYER_ID, ladder.health.lastPlayerId())
    }

    @Test
    fun `S uses the last good player ID when the page names none, never after a bot check`() = runTest {
        val world = SimWorld(Sim.BASES.first()).apply {
            visionCipher = true
            pageEdits += { html -> html.replace("\"PLAYER_JS_URL\"", "\"PLAYER_SCRIPT_V2\"").replace("\"jsUrl\"", "\"scriptV2\"") }
        }
        val solver = SimSolver(world)
        val ladder = MasterClientLadder(ClientHealth(null) { Sim.NOW }, ClientLadderSimTest.SimStartReader(world))
        val module = MasterYouTubeModule(world.http, solver, ladder)
        fun details(result: SiteExtractionResult) = when (result) {
            is SiteExtractionResult.Success -> result.details
            is SiteExtractionResult.Failure -> result.details
        }
        fun wrong(result: SiteExtractionResult) = (result as? SiteExtractionResult.Success)?.candidates.orEmpty()
            .mapNotNull { row -> world.truth(row.mediaUrl) ?: row.audioCompanion?.let { world.truth(it.mediaUrl) } }

        val first = module.extract(SimMeasure.request())
        assertTrue(solver.requests.isEmpty())
        assertTrue(details(first).toString(), details(first).any { "names no player ID; none kept" in it })
        assertNull(ladder.health.lastPlayerId())

        ladder.health.rememberPlayerId(Sim.PLAYER_ID)
        val second = module.extract(SimMeasure.request())
        assertTrue(details(second).toString(), details(second).any { "the last good one used" in it })
        assertTrue(solver.requests.isNotEmpty())
        assertTrue(solver.requests.all { Sim.PLAYER_ID in it.playerScriptUrl })
        assertTrue(second is SiteExtractionResult.Success)
        assertEquals(emptyList<String>(), wrong(second))
        // As if the page had named it: the same rows as a page that names the player ID.
        val named = SimWorld(Sim.BASES.first()).apply { visionCipher = true }
        val reference = MasterYouTubeModule(named.http, SimSolver(named), MasterClientLadder(ClientHealth(null) { Sim.NOW }, ClientLadderSimTest.SimStartReader(named)))
            .extract(SimMeasure.request())
        val rows = { result: SiteExtractionResult -> (result as? SiteExtractionResult.Success)?.candidates?.map { it.kind to it.mimeType }.orEmpty() }
        assertEquals(rows(reference), rows(second))

        val asked = solver.requests.size
        ladder.health.record("visionos", CensusClass.BOT_CHECK)
        val third = module.extract(SimMeasure.request())
        assertTrue(details(third).toString(), details(third).any { "not used after a bot check" in it })
        assertEquals(asked, solver.requests.size)
        assertEquals(emptyList<String>(), wrong(third))
    }

    @Test
    fun `the health file keeps the player ID and reads back only a valid one`() {
        val file = object : com.alal.yft.extractor.master.solver.OwnSolverTextFile {
            var text: String? = null
            override fun read(): String? = text
            override fun write(text: String): Boolean = true.also { this.text = text }
        }
        val health = ClientHealth(file) { Sim.NOW }
        health.rememberPlayerId("not a player/id")
        assertNull(health.lastPlayerId())
        health.rememberPlayerId(Sim.PLAYER_ID)
        assertEquals(Sim.PLAYER_ID, ClientHealth(file) { Sim.NOW }.lastPlayerId())
        file.text = file.text!!.replace(Sim.PLAYER_ID, "bad/value")
        assertNull(ClientHealth(file) { Sim.NOW }.lastPlayerId())
    }
}
