package com.alal.yft.extractor.master.solver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnSolverEnvironmentErrorsTest {
    private val batch = OwnSolverProtocol.Batch(rateInputs = listOf("Qx7rTb2LmNc9Pd4s"), signatureInputs = emptyList())

    private fun decoded(reply: String) = requireNotNull(OwnSolverProtocol.decode(reply, batch))

    @Test
    fun `a missing browser object is an environment error`() {
        listOf("ReferenceError", "TypeError", "program:ReferenceError", "program:TypeError", "0:ReferenceError", " 3:TypeError ")
            .forEach { assertTrue(it, OwnSolverEnvironmentErrors.isEnvironmentFailure(it)) }
    }

    @Test
    fun `refusals, missing candidates and syntax errors are not`() {
        listOf("n:disagree", "sig:probe", "n:shape", "no candidates", "not prepared", "compile:SyntaxError", "program:RangeError", "BadJob", "Error", "")
            .forEach { assertFalse(it, OwnSolverEnvironmentErrors.isEnvironmentFailure(it)) }
    }

    @Test
    fun `a worker reply needs the page only after an environment error`() {
        assertTrue(OwnSolverEnvironmentErrors.needsPage(decoded("""{"type":"error","error":"ReferenceError"}""")))
        assertTrue(
            OwnSolverEnvironmentErrors.needsPage(
                decoded("""{"type":"result","n":{},"sig":{},"failed":"program:TypeError"}"""),
            ),
        )
        assertFalse(OwnSolverEnvironmentErrors.needsPage(decoded("""{"type":"error","error":"BadJob"}""")))
        assertFalse(
            OwnSolverEnvironmentErrors.needsPage(
                decoded("""{"type":"result","n":{},"sig":{},"failed":"n:disagree,sig:probe"}"""),
            ),
        )
        assertFalse(
            OwnSolverEnvironmentErrors.needsPage(
                decoded("""{"type":"result","n":{"Qx7rTb2LmNc9Pd4s":"abc"},"sig":{}}"""),
            ),
        )
    }
}
