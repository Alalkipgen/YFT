package com.alal.yft.extractor.master.lookup

import com.alal.yft.extractor.api.NoPlayerScriptRunner
import com.alal.yft.extractor.api.PlayerScriptRequest
import com.alal.yft.extractor.api.PlayerScriptResult
import com.alal.yft.extractor.api.PlayerScriptRunner
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.solver.SignatureVerdicts
import kotlin.coroutines.coroutineContext

/**
 * Phase 1.4 item 7 (YouTube routing B) R2: Master's player-script runner (the own solver) in a
 * route-B build. The runner fetches the player script with its own client — in a route-B build
 * [RouteBScriptClient], so under a [RouteBLookup] each fetch follows route B's rules and is
 * counted — and under a [RouteBLookup] the runner is not asked at all after a 429 in the lookup
 * (`RATE_LIMITED`; a script fetch would be a new YouTube request); otherwise it answers as before.
 * The wrapper keeps the runner's kind: a runner that hands V its checks ([SignatureVerdicts])
 * still does, so the module's signature gate is exactly as without route B; no runner stays no
 * runner.
 */
object RouteBPlayerScripts {
    fun wrap(runner: PlayerScriptRunner): PlayerScriptRunner = when (runner) {
        NoPlayerScriptRunner -> runner
        is SignatureVerdicts -> Checked(runner, runner)
        else -> Gated(runner)
    }

    private open class Gated(private val inner: PlayerScriptRunner) : PlayerScriptRunner {
        override val isAvailable: Boolean get() = inner.isAvailable

        override suspend fun resolve(request: PlayerScriptRequest): PlayerScriptResult {
            val route = coroutineContext[RouteBLookup] ?: return inner.resolve(request)
            if (!route.scriptsAllowed()) return PlayerScriptResult.Failed(SiteExtractionFailure.RATE_LIMITED)
            return inner.resolve(request)
        }

        override fun toString(): String = "RouteBPlayerScripts"
    }

    private class Checked(inner: PlayerScriptRunner, verdicts: SignatureVerdicts) :
        Gated(inner), SignatureVerdicts by verdicts
}
