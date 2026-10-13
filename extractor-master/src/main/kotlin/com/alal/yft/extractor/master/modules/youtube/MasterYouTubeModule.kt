package com.alal.yft.extractor.master.modules.youtube

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.NoPlayerScriptRunner
import com.alal.yft.extractor.api.NoPoTokenProvider
import com.alal.yft.extractor.api.PlayerScriptRunner
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.modules.MasterSiteModule
import com.alal.yft.extractor.master.modules.SiteExtractorModule

/**
 * R6 (YT-1/YT-2/YT-4): Master's own YouTube reader, the copied [YouTubeExtractor] behind
 * [SiteExtractorModule]. It runs with no proof-of-origin provider and, by default, no
 * player-script runner, so streams that need n/sig are not offered by Master (main, flag off,
 * still covers them). It is asked without the user's cookie: a signed-in or age-checked answer
 * stays main's.
 *
 * Phase 1.1 S5: [playerScripts] may be the own solver's runner
 * (`solver.OwnPlayerScriptRunner`); the reader then asks visionOS first (no solver) and the own
 * solver only for streams that need n/sig, dropping every stream whose value is not verified.
 * Phase 1.2 N5b: its signatures are used only after V ([SignatureGate]; A.8: the app's own
 * reader gets the same gate through [YouTubeSignatureGuard]).
 */
class MasterYouTubeModule(
    http: ExtractorHttpClient,
    playerScripts: PlayerScriptRunner = NoPlayerScriptRunner,
) : MasterSiteModule {
    // Phase 1.2 N5b: V for the own solver's signatures (A.8: with ownSolverNext on or off; a
    // runner that hands over no checks gets no gate, and the reader runs as before).
    private val gate = SignatureGate.of(playerScripts, http)

    private val module = SiteExtractorModule(
        YouTubeExtractor(http, gate?.runner ?: playerScripts, NoPoTokenProvider),
    )

    override val siteId: String get() = module.siteId

    override fun identify(pageUrl: String): SitePageIdentity? = module.identify(pageUrl)

    override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult {
        val active = gate ?: return read(request)
        return active.around { read(request) }
    }

    private suspend fun read(request: SiteExtractionRequest): SiteExtractionResult =
        module.extract(
            request.copy(requestContext = request.requestContext.copy(
                cookie = null,
                observedHeaders = request.requestContext.observedHeaders.filterKeys {
                    !it.equals("Cookie", ignoreCase = true) &&
                        !it.equals("Authorization", ignoreCase = true)
                },
            )),
        )
}

/**
 * YT-2: Master's own client table, versioned data. The values live in the copied
 * [YouTubeClientProfiles]; a value changes only when the canary
 * (`scripts/master-youtube-canary.sh`) shows visionOS no longer answers with direct addresses.
 */
object MasterYouTubeClients {
    const val TABLE_VERSION: String = "1"
    const val SOURCE: String = "main 34a41890 / " + YouTubeClientProfiles.DEVICE_VALUES_SOURCE

    /** Asked in this order; visionOS is first and needs neither player script nor token. */
    internal val ORDER: List<YouTubeClientProfile> get() = YouTubeClientProfiles.DEVICE_CLIENTS

    /** The canary's pass line: visionOS alone gave a complete answer. */
    const val CANARY_DETAIL: String = "visionOS first: complete, no watch page"

    fun describe(): List<String> = listOf("table $TABLE_VERSION ($SOURCE)") +
        ORDER.map { "${it.clientName} ${it.clientVersion}" }
}
