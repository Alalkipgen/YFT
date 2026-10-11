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
 */
class MasterYouTubeModule(
    http: ExtractorHttpClient,
    playerScripts: PlayerScriptRunner = NoPlayerScriptRunner,
) : MasterSiteModule {
    private val module = SiteExtractorModule(
        YouTubeExtractor(http, playerScripts, NoPoTokenProvider),
    )

    override val siteId: String get() = module.siteId

    override fun identify(pageUrl: String): SitePageIdentity? = module.identify(pageUrl)

    override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult =
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
