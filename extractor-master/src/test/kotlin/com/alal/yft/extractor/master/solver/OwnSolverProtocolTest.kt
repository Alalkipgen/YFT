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

    private fun texts(value: JsonValue?): List<String> =
        (value as JsonValue.Array).items.map { (it as JsonValue.Text).value }
}
