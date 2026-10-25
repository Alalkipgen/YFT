package com.alal.yft.detection.master

import com.alal.yft.extractor.api.PlayerScriptRequest
import com.alal.yft.extractor.api.PlayerScriptResult
import com.alal.yft.extractor.api.PlayerScriptRunner
import com.alal.yft.extractor.master.solver.PendingCheck
import com.alal.yft.extractor.master.solver.SignatureVerdicts

/**
 * Decision 30 (i): YouTube's player-script runner in a build with Master's own solver — the own
 * solver ([own], with its live check V: [SignatureVerdicts]) while the Backup finder is [on], and
 * main's ejs runner ([main]) while it is off, so the switch off leaves YouTube's reader as main
 * built it. Each is made on first use. V's questions go to the own solver only once it exists: a
 * request main's runner answered has no pending check.
 */
class BackupFinderPlayerScripts(
    private val on: () -> Boolean,
    own: () -> PlayerScriptRunner,
    main: () -> PlayerScriptRunner,
) : PlayerScriptRunner, SignatureVerdicts {
    private val ownRunner = lazy(own)
    private val mainRunner = lazy(main)

    override val isAvailable: Boolean
        get() = if (on()) ownRunner.value.isAvailable else mainRunner.value.isAvailable

    override suspend fun resolve(request: PlayerScriptRequest): PlayerScriptResult =
        if (on()) ownRunner.value.resolve(request) else mainRunner.value.resolve(request)

    override fun pendingCheck(request: PlayerScriptRequest): PendingCheck? =
        ownVerdicts()?.pendingCheck(request)

    override fun record(check: PendingCheck, verdict: SignatureVerdicts.Verdict) {
        ownVerdicts()?.record(check, verdict)
    }

    private fun ownVerdicts(): SignatureVerdicts? =
        if (ownRunner.isInitialized()) ownRunner.value as? SignatureVerdicts else null
}
