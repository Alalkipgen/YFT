package com.alal.yft.extractor.master.solver

/**
 * Phase 1.2 N4: the rule that sends a run to strategy E (the page environment).
 *
 * E is only for environment errors: the player's code needed a browser object the worker does not
 * have (plan section 2), which shows as a `ReferenceError` (a missing global) or a `TypeError` (a
 * missing member, such as `document.createElement`). Every other failure (a SelfCheck refusal
 * like `n:disagree`, `no candidates`, a `SyntaxError`) stays a failure: a page would not change it.
 *
 * Failure strings are classes only: a reply's `failed` parts (`program:ReferenceError`), a
 * worker error class (`TypeError`) or a candidate's error (`0:ReferenceError`). The ladder (N5)
 * asks this before it runs E.
 */
internal object OwnSolverEnvironmentErrors {
    private val ENVIRONMENT_CLASSES = setOf("ReferenceError", "TypeError")

    /** Whether one failure string names an environment error class. */
    fun isEnvironmentFailure(failure: String): Boolean =
        failure.trim().substringAfterLast(':') in ENVIRONMENT_CLASSES

    /** Whether a decoded worker reply failed for its environment. */
    fun needsPage(decoded: OwnSolverProtocol.Decoded): Boolean = when (decoded) {
        is OwnSolverProtocol.Decoded.Failed -> isEnvironmentFailure(decoded.reason)
        is OwnSolverProtocol.Decoded.Solved -> decoded.failures.any(::isEnvironmentFailure)
    }
}
