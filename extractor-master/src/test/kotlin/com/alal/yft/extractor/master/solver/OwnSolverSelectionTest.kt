package com.alal.yft.extractor.master.solver

import com.alal.yft.extractor.api.NoPlayerScriptRunner
import com.alal.yft.extractor.api.PlayerScriptRequest
import com.alal.yft.extractor.api.PlayerScriptResult
import com.alal.yft.extractor.api.PlayerScriptRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnSolverSelectionTest {
    private object MainRunner : PlayerScriptRunner {
        override val isAvailable = true
        override suspend fun resolve(request: PlayerScriptRequest) = PlayerScriptResult.Unavailable
    }

    @Test
    fun `the own runner only when both opt-in flags are on`() {
        assertTrue(OwnSolverSelection.usesOwnSolver(ownSolver = true, masterCapture = true))
        assertFalse(OwnSolverSelection.usesOwnSolver(ownSolver = true, masterCapture = false))
        assertFalse(OwnSolverSelection.usesOwnSolver(ownSolver = false, masterCapture = true))
        assertFalse(OwnSolverSelection.usesOwnSolver(ownSolver = false, masterCapture = false))
    }

    @Test
    fun `flag off gives main's runner and never builds the own one`() {
        val built = mutableListOf<String>()
        listOf(false to false, false to true, true to false).forEach { (own, capture) ->
            val runner = OwnSolverSelection.runner(
                ownSolver = own,
                masterCapture = capture,
                own = { built += "own"; NoPlayerScriptRunner },
                main = { built += "main"; MainRunner },
            )
            assertSame(MainRunner, runner)
        }
        assertEquals(listOf("main", "main", "main"), built)
    }

    @Test
    fun `both flags on give the own runner and never build main's`() {
        val built = mutableListOf<String>()
        val own = object : PlayerScriptRunner {
            override val isAvailable = true
            override suspend fun resolve(request: PlayerScriptRequest) =
                PlayerScriptResult.Unavailable
        }
        val runner = OwnSolverSelection.runner(
            ownSolver = true,
            masterCapture = true,
            own = { built += "own"; own },
            main = { built += "main"; MainRunner },
        )
        assertSame(own, runner)
        assertEquals(listOf("own"), built)
    }
}
