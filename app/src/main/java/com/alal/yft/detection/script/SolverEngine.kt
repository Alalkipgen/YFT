package com.alal.yft.detection.script

/**
 * Runs the bundled solver over one encoded job.
 *
 * The engine is a sandbox boundary: it receives a JSON document, returns the solver's JSON
 * output and offers the solver nothing else, no network, no cookies and no storage.
 */
interface SolverEngine {
    /** Whether this device can run the engine at all. */
    val isAvailable: Boolean

    /** Returns the solver's JSON output, or null when the engine could not produce one. */
    suspend fun run(input: String): String?
}
