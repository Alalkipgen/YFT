package com.alal.yft.extractor.master.solver

import com.alal.yft.extractor.api.PlayerScriptRequest

/**
 * Phase 1.2 N5b: signatures a runner gave that still need the live check V before use
 * ([OwnPlayerScriptRunner] with ownSolverNext). The YouTube reader asks right after
 * `resolve` with the same request, checks one stream URL built with those signatures
 * (`StreamCheck`) and reports the verdict here; without a pass it drops every stream that needs
 * a signature. A pass is kept per player version and step, so later lookups skip V.
 */
interface SignatureVerdicts {
    enum class Verdict { PASS, FAIL, UNKNOWN }

    /** The check [request]'s signatures still need, or null (none given, or V passed before). */
    fun pendingCheck(request: PlayerScriptRequest): PendingCheck?

    /** V's verdict: PASS is kept; FAIL (proven by a control) turns the step's signatures off. */
    fun record(check: PendingCheck, verdict: Verdict)
}

/** Which player version and ladder step gave the signatures; names only, never a value. */
data class PendingCheck(val playerId: String, val step: OwnSolverBreaker.Step) {
    /** StreamCheck's daily budget key: the player version. */
    val budgetKey: String get() = playerId

    /** StreamCheck's verdict key: the player version and the step that solved it. */
    val cacheKey: String get() = OwnSolverBreaker.key(playerId, step)
}
