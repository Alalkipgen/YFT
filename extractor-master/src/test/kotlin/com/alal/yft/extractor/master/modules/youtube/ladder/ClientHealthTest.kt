package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.master.solver.OwnSolverTextFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 1.3 D3: one test per health rule (MASTER_KEY_PHASE1_3_PLAN.md §4 D3; guardrails 4, 5, 7, 8). */
class ClientHealthTest {
    private var now = START
    private val minute = ClientHealth.MINUTE_MILLIS
    private val hour = ClientHealth.HOUR_MILLIS
    private val day = ClientHealth.DAY_MILLIS

    private fun health(file: OwnSolverTextFile? = null) = ClientHealth(file) { now }

    @Test
    fun `each cooldown has its length and a pass ends it`() {
        val expected = mapOf(
            CensusClass.REFUSED to hour,
            CensusClass.SABR_ONLY to day,
            CensusClass.TOKEN_NEEDED to day,
            CensusClass.VERSION_REJECTED to day,
            CensusClass.SHAPE_CHANGED to day,
        )
        for ((answerClass, length) in expected) {
            now = START
            val health = health()
            health.record("android", answerClass)
            assertTrue("$answerClass", health.cooling("android"))
            now = START + length - 1
            assertTrue("$answerClass", health.cooling("android"))
            now = START + length
            assertFalse("$answerClass", health.cooling("android"))
            now = START
            health.record("android", answerClass)
            health.record("android", CensusClass.DIRECT)
            assertFalse("$answerClass ends with a pass", health.cooling("android"))
        }
        for (answerClass in listOf(CensusClass.INCOMPLETE, CensusClass.NEEDS_SCRIPT, CensusClass.AGE_CHECK, CensusClass.VIDEO_VERDICT)) {
            val health = health()
            health.record("mweb", answerClass)
            assertFalse("$answerClass", health.cooling("mweb"))
        }
    }

    @Test
    fun `a class a rung repaired is a pass with that rung, so no cooldown starts`() {
        val health = health()
        health.record("visionos", CensusClass.SHAPE_CHANGED, ClientHealth.Rung.L)
        val client = health.client("visionos")!!
        assertFalse(health.cooling("visionos"))
        assertEquals(ClientHealth.Rung.L, client.variant)
        assertEquals(1, client.passed)
        assertTrue(health.passedWithin("visionos", hour))
        health.record("visionos", CensusClass.SABR_ONLY)
        health.repaired("visionos", ClientHealth.Rung.H)
        assertEquals(ClientHealth.Rung.H, health.client("visionos")!!.variant)
        assertEquals(2, health.client("visionos")!!.asked)
        assertFalse(health.cooling("visionos"))
    }

    @Test
    fun `a direct answer whose V did not pass is no pass`() {
        val health = health()
        health.record("visionos", CensusClass.DIRECT, passed = false)
        assertFalse(health.passedWithin("visionos", hour))
        assertFalse(health.cooling("visionos"))
        health.record("visionos", CensusClass.DIRECT)
        assertTrue(health.passedWithin("visionos", hour))
        now += hour + 1
        assertFalse(health.passedWithin("visionos", hour))
        assertTrue(health.passedWithin("visionos", day))
    }

    @Test
    fun `a bot check quiets only the request-sending rungs for 30 minutes`() {
        val health = health()
        health.record("android", CensusClass.REFUSED)
        health.record("visionos", CensusClass.BOT_CHECK)
        assertTrue(health.botChecked())
        assertFalse(health.mayAsk("visionos", sendsRequest = true))
        assertTrue(health.mayAsk("visionos", sendsRequest = false))
        assertNull("no cooling exception in a bot check", health.firstCooling(listOf("android")))
        assertFalse(health.cooling("visionos"))
        now += 30 * minute - 1
        assertTrue(health.botChecked())
        now += 1
        assertFalse(health.botChecked())
        assertTrue(health.mayAsk("visionos"))
    }

    @Test
    fun `the order is passed, then unknown, then cooling with the soonest end first`() {
        val health = health()
        health.record("android", CensusClass.SABR_ONLY)
        health.record("mweb", CensusClass.REFUSED)
        health.record("embedded", CensusClass.DIRECT)
        val ids = listOf("visionos", "android", "embedded", "mweb")
        assertEquals(listOf("embedded", "visionos", "mweb", "android"), health.order(ids))
        assertEquals("mweb", health.firstCooling(ids))
        assertFalse(health.mayAsk("mweb"))
        now += day + 1
        assertEquals(listOf("visionos", "android", "embedded", "mweb"), health.order(ids))
        assertNull(health.firstCooling(ids))
    }

    @Test
    fun `a learned version is public text only and is dropped after one failure`() {
        val health = health()
        health.learn("visionos", "0.1.2", "raw.githubusercontent.com")
        assertEquals("0.1.2", health.learnedVersion("visionos"))
        health.learn("visionos", "1.0; drop", "raw.githubusercontent.com")
        health.learn("visionos", "2.0", "https://example.com/x")
        assertEquals("0.1.2", health.learnedVersion("visionos"))
        health.forgetVersion("visionos")
        assertNull(health.learnedVersion("visionos"))
        health.learn("someone", "1.2", "src")
        assertNull(health.client("someone"))
    }

    @Test
    fun `shape maps keep paths only, bounded, and a failed one is dropped`() {
        val health = health()
        health.rememberShape("visionos", "abcdef01", listOf("streamingData.formats[*].url", "has space", "a\"b"))
        assertEquals(listOf("streamingData.formats[*].url"), health.shape("visionos", "abcdef01"))
        health.rememberShape("visionos", "not-hex!", listOf("x.y"))
        assertNull(health.shape("visionos", "not-hex!"))
        for (index in 0 until 6) health.rememberShape("visionos", "0000000$index", (0 until 100).map { "p$it" })
        val maps = health.client("visionos")!!.shapeMaps
        assertEquals(ClientHealth.MAX_SHAPES, maps.size)
        assertTrue(maps.values.all { it.size == ClientHealth.MAX_PATHS })
        health.dropShape("visionos", "00000005")
        assertNull(health.shape("visionos", "00000005"))
    }

    @Test
    fun `a version source is read at most once a day per identity`() {
        val health = health()
        assertTrue(health.maySourceRead("visionos"))
        health.sourceRead("visionos")
        assertFalse(health.maySourceRead("visionos"))
        assertTrue(health.maySourceRead("android"))
        now += day - 1
        assertFalse(health.maySourceRead("visionos"))
        now += 1
        assertTrue(health.maySourceRead("visionos"))
    }

    @Test
    fun `the file round-trips and holds no address, visitor data or video ID`() {
        val file = MemoryFile()
        val health = health(file)
        health.record("visionos", CensusClass.SHAPE_CHANGED, ClientHealth.Rung.L)
        health.record("android", CensusClass.TOKEN_NEEDED)
        health.record("mweb", CensusClass.BOT_CHECK)
        health.learn("visionos", "0.1", "raw.githubusercontent.com")
        health.rememberShape("visionos", "0123abcd", listOf("a.b[*].c"))
        health.sourceRead("visionos")
        val text = requireNotNull(file.text)
        for (forbidden in listOf("http", "googlevideo", "visitor", "videoId", Sim.VIDEO_ID)) {
            assertFalse(forbidden, text.contains(forbidden))
        }
        val again = health(file)
        assertEquals(health.describe(), again.describe())
        assertEquals(health.toJson(), again.toJson())
        assertTrue(again.botChecked())
        assertEquals(listOf("a.b[*].c"), again.shape("visionos", "0123abcd"))
        assertFalse(again.maySourceRead("visionos"))
    }

    @Test
    fun `a file that cannot be read starts empty and foreign entries are left out`() {
        assertEquals(emptyList<String>(), health(MemoryFile("{not json")).describe())
        val foreign = MemoryFile(
            "{\"v\":1,\"botCheckUntil\":0,\"clients\":{\"someone\":{\"asked\":1,\"passed\":1,\"at\":1}," +
                "\"visionos\":{\"asked\":2,\"passed\":1,\"at\":1,\"version\":\"https://x\",\"source\":\"UPPER\"," +
                "\"shapes\":{\"zz\":[\"a\"],\"abcdef01\":[\"ok.path\",\"bad path\"]}}},\"sources\":{\"someone\":5}}",
        )
        val health = health(foreign)
        assertNull(health.client("someone"))
        val vision = health.client("visionos")!!
        assertNull(vision.learnedVersion)
        assertNull(vision.learnedSource)
        assertEquals(mapOf("abcdef01" to listOf("ok.path")), vision.shapeMaps)
    }

    private class MemoryFile(var text: String? = null) : OwnSolverTextFile {
        override fun read(): String? = text

        override fun write(text: String): Boolean {
            this.text = text
            return true
        }
    }

    private companion object {
        const val START = 1_791_000_000_000L
    }
}
