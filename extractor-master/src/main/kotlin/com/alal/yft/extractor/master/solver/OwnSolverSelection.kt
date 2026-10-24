package com.alal.yft.extractor.master.solver

import com.alal.yft.extractor.api.PlayerScriptRunner

/**
 * Phase 1.1 S5: which player-script runner YouTube gets.
 *
 * The own runner is used only when both opt-in flags are on (`-Pyft.ownSolver` and
 * `-Pyft.masterCapture`); otherwise main's ejs runner is used, exactly as main builds it. Only
 * the chosen factory is called, so with a flag off the own engine is never even created.
 */
object OwnSolverSelection {
    fun usesOwnSolver(ownSolver: Boolean, masterCapture: Boolean): Boolean =
        ownSolver && masterCapture

    fun runner(
        ownSolver: Boolean,
        masterCapture: Boolean,
        own: () -> PlayerScriptRunner,
        main: () -> PlayerScriptRunner,
    ): PlayerScriptRunner = if (usesOwnSolver(ownSolver, masterCapture)) own() else main()
}
