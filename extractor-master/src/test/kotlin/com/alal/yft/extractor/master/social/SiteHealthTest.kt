package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.master.solver.OwnSolverTextFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** VK Master plan §8: the social sites' health (data only; made-up values). */
class SiteHealthTest {
    private var now = START
    private val clock = { now }

    @Test
    fun `a pass is recorded per site and rung, and only rows that passed V count`() {
        val health = SiteHealth(clock = clock)
        health.record(VK, "V2", AnswerClass.DIRECT, rowsPassed = false)
        assertNull(health.rung(VK, "V2")?.lastPass)
        assertNull(health.goodRung(VK))
        now += MINUTE
        health.record(VK, "V2", AnswerClass.PARTIAL, rowsPassed = true)
        val rung = checkNotNull(health.rung(VK, "V2"))
        assertEquals(AnswerClass.PARTIAL, rung.lastClass)
        assertEquals(now, rung.lastPass)
        assertEquals(2, rung.asked)
        assertEquals(1, rung.passed)
        assertEquals("V2", health.goodRung(VK))
        assertTrue(health.passedWithin(VK, "V2", SiteHealth.DAY_MILLIS))
        // A class that carries no rows is never a pass, even when the caller says so.
        health.record("facebook", "F1", AnswerClass.LOGIN_WALL, rowsPassed = true)
        assertNull(health.rung("facebook", "F1")?.lastPass)
        assertNull(health.goodRung("facebook"))
    }

    @Test
    fun `unknown sites and rungs are not recorded`() {
        val file = MemoryFile()
        val health = SiteHealth(file, clock)
        health.record("youtube", "V1", AnswerClass.DIRECT, rowsPassed = true)
        health.record(VK, "F1", AnswerClass.DIRECT, rowsPassed = true)
        health.record(VK, "V9", AnswerClass.DIRECT, rowsPassed = true)
        health.stop("vimeo")
        assertNull(health.rung(VK, "F1"))
        assertFalse(health.mayAsk(VK, "V9"))
        assertNull(file.text)
        assertEquals(setOf("facebook", "tiktok", "x", "instagram", "vk"), SiteHealth.RUNGS.keys)
        assertEquals(listOf("I2", "probe", "I3"), SiteHealth.RUNGS.getValue("instagram"))
        assertEquals(listOf("V1", "V2", "V3"), SiteHealth.RUNGS.getValue(VK))
    }

    @Test
    fun `cooldowns - a rejected value 1 h, a changed shape 24 h, a pass or a new config ends them`() {
        val health = SiteHealth(clock = clock)
        health.record(VK, "V2", AnswerClass.VALUE_REJECTED)
        health.record(VK, "V1", AnswerClass.SHAPE_CHANGED)
        health.record(VK, "V3", AnswerClass.TRANSIENT)
        assertTrue(health.cooling(VK, "V2"))
        assertTrue(health.cooling(VK, "V1"))
        assertFalse(health.cooling(VK, "V3"))
        assertFalse(health.mayAsk(VK, "V2"))
        now += SiteHealth.HOUR_MILLIS
        assertFalse(health.cooling(VK, "V2"))
        assertTrue(health.cooling(VK, "V1"))
        health.record(VK, "V2", AnswerClass.VALUE_REJECTED)
        health.record(VK, "V2", AnswerClass.DIRECT, rowsPassed = true)
        assertFalse(health.cooling(VK, "V2"))
        health.clearCooldowns(VK)
        assertFalse(health.cooling(VK, "V1"))
        assertEquals(SiteHealth.HOUR_MILLIS, SiteHealth.cooldownOf(AnswerClass.VALUE_REJECTED))
        assertEquals(SiteHealth.DAY_MILLIS, SiteHealth.cooldownOf(AnswerClass.SHAPE_CHANGED))
        assertNull(SiteHealth.cooldownOf(AnswerClass.REMOVED))
    }

    @Test
    fun `a bot check or 429 quiets only the site's request-sending rungs, for 30 min`() {
        val health = SiteHealth(clock = clock)
        health.record(VK, "V1", AnswerClass.BOT_CHECK)
        assertTrue(health.stopped(VK))
        assertFalse(health.mayAsk(VK, "V2"))
        assertTrue("a rung that sends nothing (main's answer) still runs", health.mayAsk(VK, "V1", sendsRequest = false))
        assertFalse("another site is not stopped", health.stopped("x"))
        assertTrue(health.mayAsk("x", "X1"))
        assertFalse("no value read while stopped", health.claimRead(VK, "apiVersion"))
        now += SocialCooldown.COOLDOWN_MILLIS
        assertFalse(health.stopped(VK))
        assertTrue(health.mayAsk(VK, "V2"))
        health.record("tiktok", "T1", AnswerClass.RATE_LIMITED)
        assertTrue(health.stopped("tiktok"))
        health.stop(VK)
        assertTrue(health.stopped(VK))
    }

    @Test
    fun `a value is read at most once a day per name, claimed before the read`() {
        val file = MemoryFile()
        val health = SiteHealth(file, clock)
        assertTrue(health.claimRead(VK, "apiVersion"))
        assertFalse(health.claimRead(VK, "apiVersion"))
        assertTrue("another value has its own day", health.claimRead(VK, "clientId"))
        assertFalse("not a value name", health.claimRead(VK, "Api Version"))
        assertFalse(SiteHealth(file, clock).claimRead(VK, "apiVersion"))
        now += SiteHealth.DAY_MILLIS - 1
        assertFalse(health.mayRead(VK, "apiVersion"))
        now += 1
        assertTrue(health.claimRead(VK, "apiVersion"))
    }

    @Test
    fun `a learned value keeps its source and date, the value itself only when it is a public version or VK's client id`() {
        val file = MemoryFile()
        val health = SiteHealth(file, clock)
        health.learn(VK, "apiVersion", "vk-script", "5.999")
        health.learn(VK, "clientId", "vk-script", "12345678")
        health.learn(VK, "clientSecret", "vk-script", "AbCdEfGh12IjKlMn34Op")
        health.learn(VK, "appId", "https://vk.example/x", "1234567")
        health.learn(VK, "appVersion", "vk-script", "12345678")
        assertEquals("5.999", health.learned(VK, "apiVersion")?.value)
        assertEquals("vk-script", health.learned(VK, "clientId")?.source)
        assertEquals("D's wiring: VK's client id by its own pattern", "12345678", health.learned(VK, "clientId")?.value)
        assertNull(health.learned(VK, "clientSecret")?.value)
        assertNull("digits alone are not a version", health.learned(VK, "appVersion")?.value)
        assertNull("an address is never a source", health.learned(VK, "appId"))
        val text = checkNotNull(file.text)
        listOf("AbCdEfGh12IjKlMn34Op", "https", "vk.example").forEach { assertFalse(it, text.contains(it)) }
        assertTrue(text.contains("5.999"))
        assertTrue(text.contains("12345678"))

        val reloaded = SiteHealth(file, clock)
        assertEquals("5.999", reloaded.learned(VK, "apiVersion")?.value)
        assertEquals("12345678", reloaded.learned(VK, "clientId")?.value)
        assertEquals(START, reloaded.learned(VK, "clientId")?.at)
        reloaded.forget(VK, "apiVersion")
        assertNull(SiteHealth(file, clock).learned(VK, "apiVersion"))
    }

    @Test
    fun `D's wiring - VK's two values each by its own strict pattern, on learn and on load, nothing else of theirs`() {
        val kept = mapOf(
            "clientId" to listOf("123456", "1234567890"),
            "apiVersion" to listOf("5.0", "5.1234"),
        )
        val dropped = mapOf(
            "clientId" to listOf("12345", "12345678901", "5.999", "0x1e240", "-123456", "123456 ", "１２３４５６"),
            "apiVersion" to listOf("6.0", "5.12345", "5", "5.", "51.0", "5.0.1", " 5.0", "12345678"),
        )
        kept.forEach { (name, values) ->
            values.forEach { value ->
                assertTrue("$name $value", SiteHealth.keeps(VK, name, value))
                val file = MemoryFile()
                SiteHealth(file, clock).learn(VK, name, "vk-script", value)
                assertEquals("$name $value", value, SiteHealth(file, clock).learned(VK, name)?.value)
            }
        }
        dropped.forEach { (name, values) ->
            values.forEach { value ->
                assertFalse("$name $value", SiteHealth.keeps(VK, name, value))
                val health = SiteHealth(MemoryFile(), clock)
                health.learn(VK, name, "vk-script", value)
                assertNull("$name $value", health.learned(VK, name)?.value)
                val tampered = """{"v":1,"sites":{"vk":{"learned":{"$name":{"source":"vk-script","at":1,"value":"$value"}}}}}"""
                assertNull("$name $value on load", SiteHealth(MemoryFile(tampered), clock).learned(VK, name)?.value)
            }
        }
        assertTrue(SiteHealth.keepsValue(VK, "clientId"))
        assertTrue(SiteHealth.keepsValue(VK, "apiVersion"))
        assertFalse("never the secret", SiteHealth.keepsValue(VK, "clientSecret"))
        assertFalse(SiteHealth.keepsValue("instagram", "clientId"))
        assertFalse("another site's clientId: a version only", SiteHealth.keeps("instagram", "clientId", "12345678"))
        assertEquals(setOf(VK), SiteHealth.VALUES.keys)
        assertEquals(setOf("clientId", "apiVersion"), SiteHealth.VALUES.getValue(VK).keys)
    }

    @Test
    fun `the file round-trips, and a tampered file keeps only what its patterns allow`() {
        val file = MemoryFile()
        val health = SiteHealth(file, clock)
        health.record(VK, "V2", AnswerClass.DIRECT, rowsPassed = true)
        health.record(VK, "V3", AnswerClass.VALUE_REJECTED)
        health.record("instagram", "probe", AnswerClass.VIDEO_WITHOUT_FILES)
        health.claimRead(VK, "apiVersion")
        health.learn(VK, "apiVersion", "vk-script", "5.999")
        val saved = checkNotNull(file.text)
        assertEquals(saved, SiteHealth(MemoryFile(saved), clock).toJson())

        val tampered = """{"v":1,"sites":{
            "vk":{"stopUntil":-5,"good":"F1","goodAt":1,
              "rungs":{"V1":{"asked":2,"passed":1,"at":3,"class":"NOT_A_CLASS","pass":4},"V7":{"asked":1},"V2":"x"},
              "reads":{"api version":1,"apiVersion":2},
              "learned":{"clientId":{"source":"https://vk.example","at":1},"token":{"source":"vk-script","at":1,"value":"abc.def"},
                "apiVersion":{"source":"vk-script","at":5,"value":"5.999"}}},
            "youtube":{"stopUntil":9}}}"""
        val read = SiteHealth(MemoryFile(tampered), clock)
        val v1 = checkNotNull(read.rung(VK, "V1"))
        assertNull(v1.lastClass)
        assertEquals(2, v1.asked)
        assertNull(read.rung(VK, "V7"))
        assertNull(read.rung(VK, "V2"))
        assertNull("a good rung of another site is dropped", read.goodRung(VK))
        assertFalse(read.stopped(VK))
        assertNull("an address is never a source", read.learned(VK, "clientId"))
        assertNull("only digits and dots are a version", read.learned(VK, "token")?.value)
        assertEquals("5.999", read.learned(VK, "apiVersion")?.value)
        assertFalse(read.toJson().contains("youtube"))
        assertFalse(read.toJson().contains("api version"))

        listOf("not json", "[]", """{"v":2,"sites":{"vk":{"stopUntil":${START + 1}}}}""").forEach { text ->
            assertFalse(text, SiteHealth(MemoryFile(text), clock).stopped(VK))
        }
        val stoppedFile = """{"v":1,"sites":{"vk":{"stopUntil":${START + 1}}}}"""
        assertTrue(SiteHealth(MemoryFile(stoppedFile), clock).stopped(VK))
    }

    @Test
    fun `describe names classes, rungs and sources, never a value`() {
        val health = SiteHealth(clock = clock)
        health.record(VK, "V2", AnswerClass.DIRECT, rowsPassed = true)
        assertTrue(health.claimRead(VK, "clientSecret"))
        health.record(VK, "V1", AnswerClass.BOT_CHECK)
        health.learn(VK, "apiVersion", "vk-script", "5.999")
        health.learn(VK, "clientSecret", "vk-script", "AbCdEfGh12IjKlMn34Op")
        now += 3 * MINUTE
        val lines = health.describe()
        assertTrue(lines.toString(), lines.contains("vk: stopped (bot check or 429), request-sending rungs quiet"))
        assertTrue(lines.toString(), lines.contains("vk: last good rung V2, 3 min ago"))
        assertTrue(lines.toString(), lines.contains("vk V2: DIRECT, 3 min ago, asked 1, passed 1"))
        assertTrue(lines.toString(), lines.contains("vk apiVersion: learned from vk-script, 2026-10-03"))
        assertTrue(lines.toString(), lines.contains("vk clientSecret: read 2026-10-03"))
        assertFalse(lines.toString().contains("5.999"))
        assertFalse(lines.toString().contains("AbCdEf"))
        assertNotNull(health.learned(VK, "clientSecret"))
    }

    @Test
    fun `day is the UTC calendar day without java time`() {
        assertEquals("1970-01-01", SiteHealth.day(0))
        assertEquals("2026-10-03", SiteHealth.day(START))
        assertEquals("2000-02-29", SiteHealth.day(951_782_400_000L))
        assertEquals("1999-12-31", SiteHealth.day(946_684_799_999L))
        assertEquals("2100-03-01", SiteHealth.day(4_107_542_400_000L))
    }

    private class MemoryFile(var text: String? = null) : OwnSolverTextFile {
        override fun read(): String? = text

        override fun write(text: String): Boolean {
            this.text = text
            return true
        }
    }

    private companion object {
        const val VK = SiteHealth.VK
        const val MINUTE = SiteHealth.MINUTE_MILLIS

        /** 2026-10-03 04:00 UTC. */
        const val START = 1_791_000_000_000L
    }
}
