package com.alal.yft.extractor.master.solver

/**
 * Phase 1.1 S5: runs Master's own bundled solver (`yft-own-solver/`) over one encoded job.
 *
 * The engine is a sandbox boundary, like main's `SolverEngine`: it receives a JSON job, returns
 * the solver worker's JSON reply and offers the player code nothing else (no network, no
 * cookies, no storage, no host page). The Android engine is `WebViewOwnSolverEngine`; tests use
 * a fake.
 */
interface OwnSolverEngine {
    /** Whether this device can run the engine at all. */
    val isAvailable: Boolean

    /** Returns the worker's JSON reply, or null when the engine could not produce one. */
    suspend fun run(input: String): String?
}
