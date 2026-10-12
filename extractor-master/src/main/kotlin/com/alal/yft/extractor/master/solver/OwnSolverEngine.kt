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

    /**
     * Phase 1.2 N5b (ownSolverNext only): whether [run] with events reports [Events.started], so
     * the runner can time "ready" (the page loaded and took the job) apart from the job itself.
     */
    val reportsReady: Boolean get() = false

    /**
     * Phase 1.2 N5b: [run], telling [events] when the job reached the solver and when the
     * solver's renderer was gone. An engine without these signals runs as [run].
     */
    suspend fun run(input: String, events: Events): String? = run(input)

    /** What the breaker needs from one run; called on any thread, at most once each. */
    interface Events {
        /** The page took the job: from here on only the job's own time counts. */
        fun started()

        /** The solver's renderer crashed or was killed during the run. */
        fun gone()
    }
}
