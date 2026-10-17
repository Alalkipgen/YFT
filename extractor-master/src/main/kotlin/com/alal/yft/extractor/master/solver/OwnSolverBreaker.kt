package com.alal.yft.extractor.master.solver

import java.util.concurrent.ConcurrentHashMap

/**
 * Phase 1.2 N5b: the ladder's circuit breaker (ownSolverNext only; Phase 1.1 never makes one).
 *
 * - A job timeout or a gone renderer turns that step off until the process restarts, for every
 *   player. On the page (E) both page steps go off together: they share one page engine.
 * - V = fail turns that step's signatures off for that player version (until the process
 *   restarts); the ladder then looks further down for the signature.
 * - A persistent marker per player version and step is written before each job and cleared
 *   after it, whatever the job's outcome. A marker left behind means the process died during the
 *   job (killed while the renderer filled memory, or the crash took the app down). After
 *   [STRIKES] such deaths in a row the step stays off for that player version; after one it is
 *   tried once more.
 *
 * Keys are player IDs and step names only, never an input or a value.
 */
class OwnSolverBreaker(private val markers: Markers = MemoryMarkers()) {
    enum class Step { WORKER_P, WORKER_B, PAGE_P, PAGE_B }

    /** How a job ended, as far as the breaker cares. */
    enum class End { DONE, TIMEOUT, GONE }

    /** Persistent counts of jobs begun and not finished, per key; [set] reaches storage first. */
    interface Markers {
        fun count(key: String): Int
        fun set(key: String, count: Int)
    }

    /** Markers that end with the process (tests, and builds without storage). */
    class MemoryMarkers : Markers {
        private val counts = ConcurrentHashMap<String, Int>()
        override fun count(key: String): Int = counts[key] ?: 0
        override fun set(key: String, count: Int) {
            if (count <= 0) counts.remove(key) else counts[key] = count
        }
    }

    private val stepsOff = ConcurrentHashMap.newKeySet<Step>()
    private val signaturesOff = ConcurrentHashMap.newKeySet<String>()

    fun allows(playerId: String, step: Step): Boolean =
        step !in stepsOff && markers.count(key(playerId, step)) < STRIKES

    fun signaturesAllowed(playerId: String, step: Step): Boolean = key(playerId, step) !in signaturesOff

    /** Before the job: the marker goes to storage first. */
    fun begin(playerId: String, step: Step) {
        val key = key(playerId, step)
        markers.set(key, markers.count(key) + 1)
    }

    /** After the job, whatever happened: the marker is cleared; a timeout or a gone renderer trips. */
    fun end(playerId: String, step: Step, end: End) {
        markers.set(key(playerId, step), 0)
        if (end == End.DONE) return
        if (step == Step.PAGE_P || step == Step.PAGE_B) {
            stepsOff += Step.PAGE_P
            stepsOff += Step.PAGE_B
        } else {
            stepsOff += step
        }
    }

    /** V said this step's signature does not open the stream. */
    fun signatureFailed(playerId: String, step: Step) {
        signaturesOff += key(playerId, step)
    }

    companion object {
        /** Process deaths in a row during one player version's step before it stays off. */
        const val STRIKES: Int = 2

        fun key(playerId: String, step: Step): String = "$playerId|${step.name}"
    }
}
