package com.alal.yft.extractor.master.solver

import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.get
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnSolverProtocolTest {
    private val batch = OwnSolverProtocol.Batch(
        rateInputs = listOf("Qx7rTb2LmNc9Pd4s"),
        signatureInputs = listOf("AOq0QJ8w%2B==", "sig2"),
    )

    @Test
    fun `a raw job carries the player escaped exactly once and asks to keep the program`() {
        val player = "var a = \"q\\\\x\";\n\tb('\u2028');\u0001 // ü"
        val job = OwnSolverProtocol.encode(OwnSolverProtocol.Player.Raw(player), batch, true)
        val root = BoundedJsonParser.parse(job)

        assertEquals("player", (root["type"] as JsonValue.Text).value)
        assertEquals(JsonValue.Bool(true), root["keep_prepared"])
        assertEquals(player, (root["player"] as JsonValue.Text).value)
        assertEquals(listOf("Qx7rTb2LmNc9Pd4s"), texts(root["n"]))
        assertEquals(listOf("AOq0QJ8w%2B==", "sig2"), texts(root["sig"]))
        // Line and paragraph separators are escaped, so the job is safe JavaScript source too.
        assertFalse(job.contains('\u2028'))
        assertNull(root["program"])
    }

    @Test
    fun `a prepared job carries the program and never the raw player`() {
        val job = OwnSolverProtocol.encode(OwnSolverProtocol.Player.Prepared("prog"), batch, true)
        val root = BoundedJsonParser.parse(job)

        assertEquals("prepared", (root["type"] as JsonValue.Text).value)
        assertEquals("prog", (root["program"] as JsonValue.Text).value)
        assertNull(root["player"])
        assertNull(root["keep_prepared"])
    }

    @Test
    fun `next options ask for strategy B with or without the hint and for the tripwire, never by default`() {
        val raw = OwnSolverProtocol.Player.Raw("p")
        val plain = BoundedJsonParser.parse(OwnSolverProtocol.encode(raw, batch, false))
        assertNull(plain["strategy"])
        assertNull(plain["hint"])
        assertNull(plain["tripwire"])

        val b = OwnSolverProtocol.Options(strategyB = true, hint = false, tripwire = true)
        val root = BoundedJsonParser.parse(OwnSolverProtocol.encode(raw, batch, false, b))
        assertEquals("B", (root["strategy"] as JsonValue.Text).value)
        assertEquals(JsonValue.Bool(false), root["hint"])
        assertEquals(JsonValue.Bool(true), root["tripwire"])

        val hinted = OwnSolverProtocol.Options(strategyB = true, hint = true)
        val withHint = BoundedJsonParser.parse(OwnSolverProtocol.encode(raw, batch, false, hinted))
        assertEquals(JsonValue.Bool(true), withHint["hint"])
        assertNull(withHint["tripwire"])

        val tripwireOnly = OwnSolverProtocol.Options(tripwire = true)
        val prepared = OwnSolverProtocol.Player.Prepared("prog")
        val p = BoundedJsonParser.parse(OwnSolverProtocol.encode(prepared, batch, true, tripwireOnly))
        assertNull(p["strategy"])
        assertEquals(JsonValue.Bool(true), p["tripwire"])
    }

    @Test
    fun `strategy B never runs from a prepared program`() {
        val options = OwnSolverProtocol.Options(strategyB = true)
        val failure = runCatching {
            OwnSolverProtocol.encode(OwnSolverProtocol.Player.Prepared("prog"), batch, true, options)
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun `a deadline goes in milliseconds when the ladder sets one, never by default, never negative`() {
        val raw = OwnSolverProtocol.Player.Raw("player")
        assertNull(BoundedJsonParser.parse(OwnSolverProtocol.encode(raw, batch, false))["deadline_ms"])

        val worker = OwnSolverProtocol.Options(tripwire = true, deadlineMillis = 40_000)
        val root = BoundedJsonParser.parse(OwnSolverProtocol.encode(raw, batch, true, worker))
        assertEquals(40_000L, (root["deadline_ms"] as JsonValue.Number).asLong)
        assertEquals(JsonValue.Bool(true), root["tripwire"])

        val prepared = OwnSolverProtocol.Player.Prepared("program")
        val none = OwnSolverProtocol.Options(deadlineMillis = 0)
        val zero = BoundedJsonParser.parse(OwnSolverProtocol.encode(prepared, batch, false, none))
        assertEquals(0L, (zero["deadline_ms"] as JsonValue.Number).asLong)

        val negative = runCatching { OwnSolverProtocol.Options(deadlineMillis = -1) }.exceptionOrNull()
        assertTrue(negative is IllegalArgumentException)
    }

    @Test
    fun `a run that ran out of time is budget, per kind or whole, and no other failure is`() {
        fun decoded(failed: String) = OwnSolverProtocol.decode(ScriptedOwnEngine.result(failed = failed), batch)
            as OwnSolverProtocol.Decoded.Solved

        listOf("n:budget", "sig:budget", "n:budget,sig:budget", "budget", "sig:disagree,n:budget").forEach {
            assertTrue(it, decoded(it).budget)
        }
        assertTrue(decoded("budget").failedWhole)
        assertFalse(decoded("n:budget,sig:budget").failedWhole)
        listOf("sig:disagree", "no candidates", "n:budget-x", "timeout").forEach {
            assertFalse(it, decoded(it).budget)
        }
    }

    @Test
    fun `a reply keeps only answers to inputs that were asked`() {
        val reply = ScriptedOwnEngine.result(
            n = mapOf("Qx7rTb2LmNc9Pd4s" to "out1", "extra" to "smuggled"),
            sig = mapOf("AOq0QJ8w%2B==" to "s1", "sig2" to ""),
            prepared = "program",
        )
        val decoded = OwnSolverProtocol.decode(reply, batch) as OwnSolverProtocol.Decoded.Solved

        assertEquals(mapOf("Qx7rTb2LmNc9Pd4s" to "out1"), decoded.rate)
        assertEquals(mapOf("AOq0QJ8w%2B==" to "s1"), decoded.signature)
        assertEquals("program", decoded.preparedProgram)
        assertEquals(emptyList<String>(), decoded.failures)
        assertFalse(decoded.failedWhole)
    }

    @Test
    fun `a kind refused by SelfCheck is empty and named, the other kind stays`() {
        val reply = ScriptedOwnEngine.result(
            n = mapOf("Qx7rTb2LmNc9Pd4s" to "out1"),
            failed = "sig:disagree",
        )
        val decoded = OwnSolverProtocol.decode(reply, batch) as OwnSolverProtocol.Decoded.Solved

        assertEquals(1, decoded.rate.size)
        assertTrue(decoded.signature.isEmpty())
        assertEquals(listOf("sig:disagree"), decoded.failures)
        assertFalse(decoded.failedWhole)
    }

    @Test
    fun `a failure of the whole run is told apart from a kind's`() {
        listOf("no candidate functions", "program:TypeError", "n:shape,not prepared").forEach {
            val decoded = OwnSolverProtocol.decode(ScriptedOwnEngine.result(failed = it), batch)
                as OwnSolverProtocol.Decoded.Solved
            assertTrue(it, decoded.failedWhole)
            assertTrue(it, decoded.isEmpty)
        }
        val both = OwnSolverProtocol.decode(
            ScriptedOwnEngine.result(failed = "n:not-distinct,sig:encoding"),
            batch,
        ) as OwnSolverProtocol.Decoded.Solved
        assertFalse(both.failedWhole)
        assertEquals(listOf("n:not-distinct", "sig:encoding"), both.failures)
    }

    @Test
    fun `worker errors, foreign documents and oversized replies are not results`() {
        assertEquals(
            OwnSolverProtocol.Decoded.Failed("SyntaxError"),
            OwnSolverProtocol.decode(ScriptedOwnEngine.error("SyntaxError"), batch),
        )
        assertNull(OwnSolverProtocol.decode("not json", batch))
        assertNull(OwnSolverProtocol.decode("{\"type\":\"other\"}", batch))
        assertNull(OwnSolverProtocol.decode("[]", batch))
        val huge = "{\"type\":\"result\",\"x\":\"" +
            "a".repeat(OwnSolverProtocol.MAX_OUTPUT_CHARS) + "\"}"
        assertNull(OwnSolverProtocol.decode(huge, batch))
    }

    @Test
    fun `failure strings are cut to short classes so no value can ride along`() {
        val reply = ScriptedOwnEngine.result(failed = "sig:disagree <" + "x".repeat(200) + ">")
        val decoded = OwnSolverProtocol.decode(reply, batch) as OwnSolverProtocol.Decoded.Solved
        assertTrue(decoded.failures.single().length <= 64)
        assertFalse(decoded.failures.single().contains('<'))
    }

    @Test
    fun `printed jobs and results show counts only`() {
        val reply = ScriptedOwnEngine.result(n = mapOf("Qx7rTb2LmNc9Pd4s" to "secretOut"))
        val decoded = OwnSolverProtocol.decode(reply, batch)

        assertFalse(batch.toString().contains("Qx7rTb2LmNc9Pd4s"))
        assertFalse(decoded.toString().contains("secretOut"))
        assertFalse(decoded.toString().contains("Qx7rTb2LmNc9Pd4s"))
    }

    @Test
    fun `a reply's init errors decode as a count and class names, and a missing count stays unknown`() {
        val counted = OwnSolverProtocol.decode(
            ScriptedOwnEngine.result(
                n = mapOf("Qx7rTb2LmNc9Pd4s" to "out1"),
                initErrors = 3,
                initErrorClasses = listOf("TypeError", "RangeError"),
            ),
            batch,
        ) as OwnSolverProtocol.Decoded.Solved
        assertEquals(3, counted.initErrors)
        assertEquals(listOf("TypeError", "RangeError"), counted.initErrorClasses)

        val clean = OwnSolverProtocol.decode(ScriptedOwnEngine.result(initErrors = 0), batch)
            as OwnSolverProtocol.Decoded.Solved
        assertEquals(0, clean.initErrors)
        assertEquals(emptyList<String>(), clean.initErrorClasses)

        // A run that never instantiated (or an older core) has no count: unknown, never 0.
        val unknown = OwnSolverProtocol.decode(ScriptedOwnEngine.result(failed = "not prepared"), batch)
            as OwnSolverProtocol.Decoded.Solved
        assertNull(unknown.initErrors)
        assertEquals(emptyList<String>(), unknown.initErrorClasses)
    }

    @Test
    fun `init error counts that are not whole non-negative numbers are unknown, and classes are cut`() {
        fun decoded(count: String, classes: String = "[]") = OwnSolverProtocol.decode(
            "{\"type\":\"result\",\"n\":{},\"sig\":{},\"init_errors\":$count," +
                "\"init_error_classes\":$classes}",
            batch,
        ) as OwnSolverProtocol.Decoded.Solved

        assertNull(decoded("-1").initErrors)
        assertNull(decoded("1.5").initErrors)
        assertNull(decoded("\"2\"").initErrors)
        assertEquals(Int.MAX_VALUE, decoded("99999999999").initErrors)
        val names = (1..12).joinToString(",", "[", "]") { "\"Error$it <x>\"" }
        val cut = decoded("12", names).initErrorClasses
        assertEquals(9, cut.size)
        assertFalse(cut.any { it.contains('<') })
        assertEquals(listOf("TypeError"), decoded("1", "[\"TypeError\",7,null]").initErrorClasses)
    }

    private fun texts(value: JsonValue?): List<String> =
        (value as JsonValue.Array).items.map { (it as JsonValue.Text).value }
}
