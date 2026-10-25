package com.alal.yft.extractor.master.modules.youtube

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.PlayerScriptRunner
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractor

/**
 * Phase 1.2 N5b A.8: V on the app's YouTube path. The app reads YouTube with main's reader
 * (extractor-sites), not with [MasterYouTubeModule], so the module's [SignatureGate] never saw the
 * app's lookups: with the own solver (ownSolver, with or without ownSolverNext) the reader used
 * its signatures with no V. [guard] puts the same gate around any reader: the reader gets the
 * gate's runner, and each lookup runs inside the gate, V after it.
 *
 * A runner that hands over no checks (main's ejs runner: flag off) gets the reader exactly as
 * [reader] builds it, so the flag-off path stays main's.
 */
object YouTubeSignatureGuard {
    fun guard(
        http: ExtractorHttpClient,
        playerScripts: PlayerScriptRunner,
        reader: (PlayerScriptRunner) -> SiteExtractor,
    ): SiteExtractor {
        val gate = SignatureGate.of(playerScripts, http) ?: return reader(playerScripts)
        return Guarded(reader(gate.runner), gate)
    }

    /** [inner] as it is, except that each lookup runs through [gate]. */
    private class Guarded(
        private val inner: SiteExtractor,
        private val gate: SignatureGate,
    ) : SiteExtractor by inner {
        override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult =
            gate.around { inner.extract(request) }
    }
}
