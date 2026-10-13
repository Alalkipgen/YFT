package com.alal.yft.extractor.master.solver

import com.alal.yft.extractor.api.PlayerScriptRequest

/**
 * Phase 1.2 N5b: signatures a runner gave that still need the live check V before use
 * ([OwnPlayerScriptRunner] with ownSolverNext). The YouTube reader asks right after
 * `resolve` with the same request, checks one stream URL built with those signatures
 * (`StreamCheck`) and reports the verdict here; without a pass it drops every stream that needs
 * a signature. A pass is kept per player version and step (N5b A.5: and player text and locator
 * part, in the [OwnSolverVLedger]), so later lookups skip V.
 */
interface SignatureVerdicts {
    enum class Verdict { PASS, FAIL, UNKNOWN }

    /** The check [request]'s signatures still need, or null (none given, or V passed before). */
    fun pendingCheck(request: PlayerScriptRequest): PendingCheck?

    /** V's verdict: PASS is kept; FAIL (proven by a control) turns the step's signatures off. */
    fun record(check: PendingCheck, verdict: Verdict)
}

/**
 * Which player version and ladder step gave the signatures; names and hashes only, never a value.
 * N5b A.5: also the SHA-256 of the player text the step ran from ([scriptHash], empty when
 * unknown) and the hash of the locator part the run used ([locatorHash], [NO_LOCATOR] when it
 * used none, see [OwnSolverLocator.hashOf]): other text or another part is another key, so V runs
 * again.
 */
data class PendingCheck(
    val playerId: String,
    val step: OwnSolverBreaker.Step,
    val scriptHash: String = "",
    val locatorHash: String = NO_LOCATOR,
) {
    /** StreamCheck's daily budget key: the player version. */
    val budgetKey: String get() = playerId

    /**
     * StreamCheck's verdict key and the V ledger's ([OwnSolverVLedger]): player version, step,
     * script hash and locator hash (`player|step|scriptHash|locatorHash`).
     */
    val cacheKey: String get() = OwnSolverBreaker.key(playerId, step) + "|" + scriptHash + "|" + locatorHash

    /** The hashes stay out of logs: their presence only. */
    override fun toString(): String =
        "PendingCheck(playerId=$playerId, step=$step, script=${scriptHash.isNotEmpty()}, " +
            "locator=${locatorHash != NO_LOCATOR})"

    companion object {
        /** The run used no locator part. */
        const val NO_LOCATOR: String = "-"
    }
}
