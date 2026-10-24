package com.alal.yft.extractor.master.solver

import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.master.solver.OwnSolverLadderRule.Kind
import com.alal.yft.extractor.master.solver.OwnSolverLadderRule.Live
import com.alal.yft.extractor.master.solver.OwnSolverLadderRule.Source
import com.alal.yft.extractor.master.solver.OwnSolverLadderRule.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnSolverLadderRuleTest {
    private val batch = OwnSolverProtocol.Batch(
        rateInputs = listOf("Qx7rTb2LmNc9Pd4s", "n2"),
        signatureInputs = listOf("AOq0QJ8w%2B=="),
    )

    @Test
    fun `every case of the shared table (also read by ladder test mjs)`() {
        val root = checkNotNull(
            BoundedJsonParser.parse(
                checkNotNull(javaClass.getResource("/own-solver/ladder-rule-cases.json")).readText(),
            ),
        )
        val cases = (root["cases"] as JsonValue.Array).items
        assertTrue(cases.size >= 15)
        val seen = mutableSetOf<Pair<Kind, Source>>()
        cases.forEach { case ->
            val name = (case["name"] as JsonValue.Text).value
            val workers = (case["worker"] as JsonValue.Array).items.map { stepOf(it) }
            val page = case["page"].takeIf { it is JsonValue.Object }?.let { stepOf(it) }
            val live = Live.valueOf((case["live"] as JsonValue.Text).value.uppercase())
            listOf(Kind.RATE to "n", Kind.SIGNATURE to "sig").forEach { (kind, key) ->
                val expected = sourceOf((case["expect"][key] as JsonValue.Text).value)
                assertEquals("$name ($key)", expected, OwnSolverLadderRule.decide(kind, workers, page, live))
                seen += kind to expected
            }
        }
        // The table reaches every source for both kinds.
        Kind.entries.forEach { kind -> Source.entries.forEach { assertTrue("$kind $it", (kind to it) in seen) } }
    }

    @Test
    fun `the page is needed after an environment error, an init error or answers without a count`() {
        assertTrue(OwnSolverLadderRule.needsPage(Step(emptySet(), 0, listOf("program:ReferenceError"))))
        assertTrue(OwnSolverLadderRule.needsPage(Step(emptySet(), null, listOf("TypeError"))))
        assertTrue(OwnSolverLadderRule.needsPage(Step(setOf(Kind.RATE), 2)))
        assertTrue(OwnSolverLadderRule.needsPage(Step(setOf(Kind.SIGNATURE), null)))
        assertFalse(OwnSolverLadderRule.needsPage(Step(setOf(Kind.RATE, Kind.SIGNATURE), 0)))
        assertFalse(OwnSolverLadderRule.needsPage(Step(emptySet(), 0, listOf("n:disagree", "sig:disagree"))))
        assertFalse(OwnSolverLadderRule.needsPage(Step(emptySet(), null, listOf("not prepared"))))
    }

    @Test
    fun `a decoded reply answers a kind only with a value for every asked input`() {
        fun step(reply: String) = OwnSolverLadderRule.step(requireNotNull(OwnSolverProtocol.decode(reply, batch)), batch)

        val full = step(
            ScriptedOwnEngine.result(
                n = mapOf("Qx7rTb2LmNc9Pd4s" to "a", "n2" to "b"),
                sig = mapOf("AOq0QJ8w%2B==" to "s"),
                initErrors = 0,
            ),
        )
        assertEquals(Step(setOf(Kind.RATE, Kind.SIGNATURE), 0, emptyList()), full)

        val partial = step(
            ScriptedOwnEngine.result(
                n = mapOf("Qx7rTb2LmNc9Pd4s" to "a"),
                failed = "sig:disagree",
                initErrors = 3,
                initErrorClasses = listOf("TypeError"),
            ),
        )
        assertEquals(Step(emptySet(), 3, listOf("sig:disagree")), partial)

        val unknown = step(ScriptedOwnEngine.result(n = mapOf("Qx7rTb2LmNc9Pd4s" to "a", "n2" to "b")))
        assertEquals(Step(setOf(Kind.RATE), null, emptyList()), unknown)
        assertTrue(OwnSolverLadderRule.needsPage(unknown))

        val failed = step(ScriptedOwnEngine.error("ReferenceError"))
        assertEquals(Step(emptySet(), null, listOf("ReferenceError")), failed)
        assertTrue(OwnSolverLadderRule.needsPage(failed))
    }

    @Test
    fun `a kind that was not asked is never answered`() {
        val rateOnly = OwnSolverProtocol.Batch(rateInputs = listOf("Qx7rTb2LmNc9Pd4s"), signatureInputs = emptyList())
        val decoded = requireNotNull(
            OwnSolverProtocol.decode(ScriptedOwnEngine.result(n = mapOf("Qx7rTb2LmNc9Pd4s" to "a"), initErrors = 0), rateOnly),
        )
        val step = OwnSolverLadderRule.step(decoded, rateOnly)
        assertEquals(setOf(Kind.RATE), step.answered)
        assertEquals(Source.NONE, OwnSolverLadderRule.decide(Kind.SIGNATURE, listOf(step), null, Live.PASS))
    }

    @Test
    fun `the candidate is the step whose signature V checks, and every signature needs V`() {
        val clean = Step(setOf(Kind.RATE, Kind.SIGNATURE), 0)
        assertEquals(Source.P, OwnSolverLadderRule.candidate(Kind.SIGNATURE, listOf(clean), null))
        assertEquals(Source.NONE, OwnSolverLadderRule.decide(Kind.SIGNATURE, listOf(clean), null, Live.UNKNOWN))
        assertEquals(Source.P, OwnSolverLadderRule.decide(Kind.SIGNATURE, listOf(clean), null, Live.PASS))
        assertEquals(Source.P, OwnSolverLadderRule.decide(Kind.RATE, listOf(clean), null, Live.UNKNOWN))
    }

    private fun stepOf(node: JsonValue): Step = Step(
        answered = (node["answered"] as JsonValue.Array).items.map {
            when ((it as JsonValue.Text).value) {
                "n" -> Kind.RATE
                "sig" -> Kind.SIGNATURE
                else -> error("unknown kind")
            }
        }.toSet(),
        initErrors = (node["init"] as? JsonValue.Number)?.asLong?.toInt(),
        failures = (node["failed"] as JsonValue.Array).items.map { (it as JsonValue.Text).value },
    )

    private fun sourceOf(text: String): Source = if (text == "none") Source.NONE else Source.valueOf(text)
}
