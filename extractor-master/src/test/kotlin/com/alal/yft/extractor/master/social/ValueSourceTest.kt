package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.solver.OwnSolverTextFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** VK Master plan §8: S5's order for one named value (made-up values and texts only). */
class ValueSourceTest {
    private var now = START
    private val file = MemoryFile()
    private val health = SiteHealth(file) { now }

    @Test
    fun `the S5 order - signed, healed today, learned, bundled - and a value off its pattern is never sent`() = runTest {
        val signed = ValueSource(health, signed = SignedValues { kind -> "5.997".takeIf { kind.name == API.name } })
        assertEquals(listOf(ValueSource.Origin.SIGNED, "5.997", "r9"), describe(signed.value(API, "5.900")))
        val offPattern = ValueSource(health, signed = SignedValues { "5.x" })
        assertEquals(listOf(ValueSource.Origin.BUNDLED, "5.900", "bundled"), describe(offPattern.value(API, "5.900")))

        health.learn(VK, API.name, "vk-script", "5.996")
        health.learn(VK, CLIENT.name, "vk-script", "87654321")
        val source = ValueSource(health)
        assertEquals(listOf(ValueSource.Origin.LEARNED, "5.996", "learned"), describe(source.value(API, "5.900")))
        assertEquals(
            "D's wiring: VK's client id is learned with its value",
            listOf(ValueSource.Origin.LEARNED, "87654321", "learned"),
            describe(source.value(CLIENT, "12345678")),
        )
        health.learn(VK, "appId", "vk-script", "2345678")
        val appId = ValueKind(VK, "appId", Regex("[0-9]{6,10}"), Regex("app_id:\"([0-9]+)\""))
        assertEquals("another ID is never learned as a value", ValueSource.Origin.BUNDLED, source.value(appId, "1234567")?.origin)
        assertEquals("the signed value comes first", "5.997", signed.value(API, "5.900")?.value)
        assertNull("a bundled value off its pattern is not sent", ValueSource(SiteHealth { now }).value(API, "5.x"))

        val any = ValueKind(VK, "anything", Regex(".*"))
        listOf("https://vk.example/a", "a\"b", "a b", "x:y", "</script>", "").forEach { value ->
            assertNull(value, ValueSource(SiteHealth { now }).value(any, value))
        }
        assertEquals("5.0,a_b%3D+c-d", ValueSource(SiteHealth { now }).value(any, "5.0,a_b%3D+c-d")?.value)
    }

    @Test
    fun `a heal reads only after a rejection, once a day, and finds exactly one pattern-matched value`() = runTest {
        val reader = FakeReader(script("v=5.950"))
        val source = ValueSource(health, reader)
        val bundled = checkNotNull(source.value(API, "5.900"))
        assertEquals("not read (no value rejected today)", source.heal(API, USER_AGENT).why)
        assertEquals(0, reader.reads)

        source.rejected(bundled)
        assertNull(source.value(API, "5.900"))
        val healed = checkNotNull(source.next(API, "5.900", USER_AGENT))
        assertEquals(listOf(ValueSource.Origin.HEALED, "5.950", "vk-script"), describe(healed))
        assertEquals(1, reader.reads)
        assertEquals(USER_AGENT, reader.userAgent)
        assertEquals(API.maxBytes, reader.maxBytes)
        assertEquals("read today", source.heal(API, USER_AGENT).why)
        assertEquals("5.950", source.value(API, "5.900")?.value)
        assertEquals(1, reader.reads)
        assertFalse(healed.toString().contains("5.950"))
        assertFalse(source.heal(API, USER_AGENT).toString().contains("5.950"))
        assertNull("nothing is stored before OK + V", health.learned(VK, API.name))

        // Memory only: a new source (the app restarted) has no healed value, and the day's read is spent.
        val restarted = ValueSource(SiteHealth(file) { now }, reader)
        val again = checkNotNull(restarted.value(API, "5.900"))
        restarted.rejected(again)
        assertEquals("not read (read in the last day)", restarted.heal(API, USER_AGENT).why)
        assertEquals(1, reader.reads)
        assertEquals(listOf(ValueSource.Origin.ALTERNATE, "5.990", "alternate vk-docs"), describe(restarted.next(API, "5.900", USER_AGENT)))
    }

    @Test
    fun `a heal gives none for no value, two values, a value off its pattern, the rejected value or an oversized text`() = runTest {
        val cases = mapOf(
            "no value" to script("nothing here"),
            "more than one value" to script("v=5.950 v=5.951"),
            "not the value's pattern" to script("v=6.950"),
            "the rejected value" to script("v=5.900"),
            "over 64 bytes" to script("v=5.950 " + "x".repeat(64)),
            "read failed (HTTP_STATUS 404)" to ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 404),
        )
        val small = ValueKind(VK, API.name, API.pattern, Regex("v=([0-9.]+)"), publicVersion = true, maxBytes = 64)
        cases.forEach { (why, answer) ->
            val health = SiteHealth { now }
            val reader = FakeReader(answer)
            val source = ValueSource(health, reader)
            source.rejected(checkNotNull(source.value(small, "5.900")))
            assertEquals(why, why, source.heal(small, USER_AGENT).why)
            assertEquals(why, 1, reader.reads)
            assertEquals("$why: the read was claimed first", "not read (read in the last day)", source.heal(small, USER_AGENT).why)
            assertFalse(why, health.stopped(VK))
        }
        val repeated = ValueSource(SiteHealth { now }, FakeReader(script("v=5.950 and again v=5.950")))
        repeated.rejected(checkNotNull(repeated.value(API, "5.900")))
        assertEquals("one value, named twice", "5.950", repeated.heal(API, null).candidate?.value)
        assertEquals("no source pattern", ValueSource(health, FakeReader(script("v=5.950"))).heal(ValueKind(VK, "noAnchor", API.pattern), null).why)
        assertEquals("no source reader", ValueSource(health).heal(API, null).why)
        assertEquals("the source has no ID", ValueSource(health, FakeReader(script("v=5.950"), id = "https://vk.example")).heal(API, null).why)
    }

    @Test
    fun `a read cut short before any answer - a cancel, the deadline, no network - gives the day's read back, three times a day`() = runTest {
        val reader = FakeReader(script("v=5.950"))
        val source = ValueSource(health, reader)
        source.rejected(checkNotNull(source.value(API, "5.900")))

        // A cancel while reading (the page closed): the exception goes on, the read is given back.
        reader.cut = true
        assertTrue(runCatching { source.heal(API, USER_AGENT) }.exceptionOrNull() is CancellationException)
        assertTrue(source.mayRead(API))
        assertTrue("given back in the file too", SiteHealth(file) { now }.mayRead(VK, API.name))

        // The lookup's deadline over a read that never answers: given back.
        reader.cut = false
        reader.hang = true
        assertNull(withTimeoutOrNull(1_000) { source.heal(API, USER_AGENT) })
        assertTrue(source.mayRead(API))

        // No network (no status, the site said nothing): given back, and the line says so.
        reader.hang = false
        reader.answer = ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK, details = listOf("timeout"))
        assertEquals("read failed (NETWORK: timeout); given back", source.heal(API, USER_AGENT).why)
        assertTrue(source.mayRead(API))
        assertEquals(3, reader.reads)

        // The fourth the same day stays spent: a read that never finishes is not tried all day.
        reader.answer = ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)
        assertEquals("read failed (NETWORK)", source.heal(API, USER_AGENT).why)
        assertFalse(source.mayRead(API))
        assertEquals("not read (read in the last day)", source.heal(API, USER_AGENT).why)
        assertEquals(4, reader.reads)
        assertFalse(health.stopped(VK))

        // A day later: an answer of the site (a status) spends the read as before.
        now += SiteHealth.DAY_MILLIS
        source.rejected(checkNotNull(source.value(API, "5.900")))
        reader.answer = ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 403)
        assertEquals("read failed (HTTP_STATUS 403)", source.heal(API, USER_AGENT).why)
        assertEquals("not read (read in the last day)", source.heal(API, USER_AGENT).why)

        // Another day: three again, and a read that answers heals.
        now += SiteHealth.DAY_MILLIS
        source.rejected(checkNotNull(source.value(API, "5.900")))
        reader.cut = true
        assertTrue(runCatching { source.heal(API, USER_AGENT) }.exceptionOrNull() is CancellationException)
        reader.cut = false
        reader.answer = script("v=5.950")
        assertEquals(listOf(ValueSource.Origin.HEALED, "5.950", "vk-script"), describe(source.heal(API, USER_AGENT).candidate))
        assertEquals(7, reader.reads)
    }

    @Test
    fun `a bot check or 429 on a read stops the site, and nothing is read while it is stopped`() = runTest {
        listOf(
            ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429),
            ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 429),
            ExtractorHttpResult.Failure(SiteExtractionFailure.BOT_CHECK, 403),
        ).forEach { answer ->
            val health = SiteHealth { now }
            val reader = FakeReader(answer)
            val source = ValueSource(health, reader)
            source.rejected(checkNotNull(source.value(API, "5.900")))
            source.heal(API, USER_AGENT)
            assertTrue(answer.toString(), health.stopped(VK))
            source.rejected(checkNotNull(source.value(CLIENT, "12345678")))
            assertEquals("not read (bot check or 429)", source.heal(CLIENT, USER_AGENT).why)
            assertEquals(1, reader.reads)
        }
    }

    @Test
    fun `kept only after an OK answer whose rows passed V - a public version or VK's client id with its value, another ID by name and source only`() = runTest {
        val reader = FakeReader(script("v=5.950 client_id:\"87654321\" app_id:\"2345678\""))
        val source = ValueSource(health, reader)
        source.rejected(checkNotNull(source.value(API, "5.900")))
        val api = checkNotNull(source.heal(API, USER_AGENT).candidate)
        assertFalse(source.kept(api, AnswerClass.DIRECT, rowsPassed = false))
        assertFalse(source.kept(api, AnswerClass.SHAPE_CHANGED, rowsPassed = true))
        assertNull(health.learned(VK, API.name))
        assertTrue(source.kept(api, AnswerClass.PARTIAL, rowsPassed = true))
        assertEquals("5.950", health.learned(VK, API.name)?.value)
        assertEquals("vk-script", health.learned(VK, API.name)?.source)

        source.rejected(checkNotNull(source.value(CLIENT, "12345678")))
        val client = checkNotNull(source.heal(CLIENT, USER_AGENT).candidate)
        assertEquals("87654321", client.value)
        assertTrue(source.kept(client, AnswerClass.DIRECT, rowsPassed = true))
        assertEquals("vk-script", health.learned(VK, CLIENT.name)?.source)
        assertEquals("D's wiring: VK's client id with its value", "87654321", health.learned(VK, CLIENT.name)?.value)
        assertTrue(checkNotNull(file.text).contains("87654321"))

        val appId = ValueKind(VK, "appId", Regex("[0-9]{6,10}"), Regex("app_id:\"([0-9]+)\""))
        source.rejected(checkNotNull(source.value(appId, "1234567")))
        val app = checkNotNull(source.heal(appId, USER_AGENT).candidate)
        assertEquals("2345678", app.value)
        assertTrue(source.kept(app, AnswerClass.DIRECT, rowsPassed = true))
        assertEquals("vk-script", health.learned(VK, "appId")?.source)
        assertNull("another ID by name and source only", health.learned(VK, "appId")?.value)
        assertFalse(checkNotNull(file.text).contains("2345678"))
        assertFalse("a bundled value is the build's: nothing to keep", source.kept(checkNotNull(source.value(SECRET, "AbCdEfGh12IjKlMn34Op")), AnswerClass.DIRECT, true))
        assertNull(health.learned(VK, SECRET.name))
    }

    @Test
    fun `a rejected value is not sent again that day, and a learned one is forgotten`() {
        health.learn(VK, API.name, "vk-script", "5.996")
        val source = ValueSource(health)
        val learned = checkNotNull(source.value(API, "5.900"))
        assertEquals(ValueSource.Origin.LEARNED, learned.origin)
        source.rejected(learned)
        assertNull(health.learned(VK, API.name))
        val bundled = checkNotNull(source.value(API, "5.900"))
        assertEquals(ValueSource.Origin.BUNDLED, bundled.origin)
        source.rejected(bundled)
        assertNull(source.value(API, "5.900"))
        assertEquals(listOf("5.990", "5.980"), source.alternates(API, "5.900").map { it.value })
        now += SiteHealth.DAY_MILLIS
        assertEquals("a day later the bundled value is sent again", "5.900", source.value(API, "5.900")?.value)
    }

    @Test
    fun `next walks S5's order to its end and never invents a value`() = runTest {
        val reader = FakeReader(script("v=5.950"))
        val source = ValueSource(health, reader)
        val sent = mutableListOf<ValueSource.Candidate>()
        var candidate = source.value(API, "5.900")
        while (candidate != null && sent.size < 10) {
            sent += candidate
            source.rejected(candidate)
            candidate = source.next(API, "5.900", USER_AGENT)
        }
        assertEquals(listOf("5.900", "5.950", "5.990", "5.980"), sent.map { it.value })
        assertEquals(
            listOf(ValueSource.Origin.BUNDLED, ValueSource.Origin.HEALED, ValueSource.Origin.ALTERNATE, ValueSource.Origin.ALTERNATE),
            sent.map { it.origin },
        )
        assertEquals(1, reader.reads)
        assertEquals("at most two bundled alternates", 2, API.alternates.size)
        assertEquals("the bundled value is not its own alternate", listOf("5.980"), ValueSource(SiteHealth { now }).alternates(API, "5.990").map { it.value })
    }

    @Test
    fun `kinds and alternates are checked when made, and never show a value`() {
        assertThrows(IllegalArgumentException::class.java) { ValueKind("youtube", "apiVersion", API.pattern) }
        assertThrows(IllegalArgumentException::class.java) { ValueKind(VK, "api version", API.pattern) }
        assertThrows(IllegalArgumentException::class.java) { ValueKind(VK, "apiVersion", API.pattern, maxBytes = 0) }
        assertThrows(IllegalArgumentException::class.java) { ValueAlternate("5.990", "https://vk.example", "2026-10-01") }
        assertThrows(IllegalArgumentException::class.java) { ValueAlternate("5.990", "vk-docs", "Oct 1") }
        assertEquals("ValueAlternate(vk-docs, 2026-10-01)", API.alternates.first().toString())
        assertEquals("ValueKind(vk apiVersion)", API.toString())
        assertEquals("ValueSource(reader=false)", ValueSource(health).toString())
    }

    private fun describe(candidate: ValueSource.Candidate?): List<Any?> =
        listOf(candidate?.origin, candidate?.value, candidate?.label)

    private fun script(text: String): ExtractorHttpResult =
        ExtractorHttpResult.Success(200, "/* made-up */ var config = {$text};", "https://vk.example/app.js", "application/javascript")

    private class FakeReader(var answer: ExtractorHttpResult, override val id: String = "vk-script") : ValueReader {
        var reads = 0
        var userAgent: String? = null
        var maxBytes = 0L

        /** A cancel while reading (a closed page). */
        var cut = false

        /** A read that never answers (the caller's deadline cuts it). */
        var hang = false

        override suspend fun read(kind: ValueKind, userAgent: String?, maxBytes: Long): ExtractorHttpResult {
            reads += 1
            this.userAgent = userAgent
            this.maxBytes = maxBytes
            if (cut) throw CancellationException("made-up: the page closed")
            if (hang) awaitCancellation()
            return answer
        }
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
        const val START = 1_791_000_000_000L
        const val USER_AGENT = "Mozilla/5.0 (made-up)"

        val API = ValueKind(
            VK, "apiVersion", Regex("5\\.[0-9]{1,4}"), Regex("\\bv=([0-9.]+)"), publicVersion = true,
            alternates = listOf(
                ValueAlternate("5.990", "vk-docs", "2026-10-01"),
                ValueAlternate("5.980", "vk-docs", "2026-09-01"),
                ValueAlternate("5.970", "vk-docs", "2026-08-01"),
            ),
        )
        val CLIENT = ValueKind(VK, "clientId", Regex("[0-9]{6,10}"), Regex("client_id:\"([0-9]+)\""))
        val SECRET = ValueKind(VK, "clientSecret", Regex("[A-Za-z0-9]{16,32}"))
    }
}
