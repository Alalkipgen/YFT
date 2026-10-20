package com.alal.yft.master

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.detection.DeviceMergeSupport
import com.alal.yft.detection.HiddenPageReader
import com.alal.yft.detection.HiddenPageResult
import com.alal.yft.detection.MergeSupport
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.detection.SiteLookupCache
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.SiteAdapterSelection
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.master.lookup.AnswerMark
import com.alal.yft.extractor.master.social.LinkIdentity
import com.alal.yft.extractor.master.social.MainAnswer
import com.alal.yft.extractor.master.social.MainLookup
import com.alal.yft.extractor.sites.facebook.FacebookExtractor
import com.alal.yft.extractor.sites.instagram.InstagramExtractor
import com.alal.yft.extractor.sites.tiktok.TikTokAgents
import com.alal.yft.extractor.sites.tiktok.TikTokExtractor
import com.alal.yft.extractor.sites.x.XExtractor

/**
 * Social plan S0c (`MASTER_KEY_SOCIAL_PLAN.md` §7.1): main's own lookup for the site census, as
 * Home runs it: the app's adapter coordinator ([SiteAdapterCoordinator]) over [registry] (the
 * four adapters, their asks through a recorder into the row's record), TikTok's hidden page
 * ([hiddenPages]), the phone's merge rule, always fresh (a new answer cache per lookup, nothing
 * remembered). There is no tab, so no tab data: the page read, then the hidden page, as main does.
 *
 * The identity is the browser's: [userAgent] (the agent YFT's tab sends) and [cookies] (the
 * browser's cookies for the link; only whether a session was there reaches the report). The
 * hidden page's own loads are not asks of a client: only its mark comes back ([markOf]).
 */
class SiteCensusLookup(
    private val registry: SiteExtractorRegistry,
    private val hiddenPages: HiddenPageReader,
    private val userAgent: suspend () -> String?,
    private val cookies: (String) -> String?,
    private val mergeSupport: () -> MergeSupport = { DeviceMergeSupport() },
    private val clock: () -> Long = System::currentTimeMillis,
) : MainLookup {
    override suspend fun identify(link: String): LinkIdentity? {
        val page = (registry.select(link) as? SiteAdapterSelection.Matched)?.identity ?: return null
        val cookie = runCatching { cookies(link) }.getOrNull()?.takeIf(String::isNotBlank)
        return LinkIdentity(page, userAgent()?.takeIf(String::isNotBlank), cookie)
    }

    override suspend fun lookup(link: String, identity: LinkIdentity): MainAnswer {
        val marked = MarkedHiddenPages(hiddenPages)
        val outcome = SiteAdapterCoordinator(registry, mergeSupport(), SiteLookupCache(), marked).inspect(
            pageUrl = link,
            requestContext = BrowserRequestContext(link, identity.userAgent, identity.cookie),
            nowEpochMs = clock(),
            fresh = true,
        )
        return answerOf(outcome, marked.mark)
    }

    /** The hidden page as main reads it; [mark] is its answer's mark, null when it was not read. */
    private class MarkedHiddenPages(private val pages: HiddenPageReader) : HiddenPageReader {
        @Volatile
        var mark: AnswerMark? = null
            private set

        override fun handles(siteId: String): Boolean = pages.handles(siteId)

        override suspend fun read(link: String, postId: String?): HiddenPageResult =
            pages.read(link, postId).also { mark = markOf(it) }
    }

    companion object {
        private val ADAPTER_HTTP = Regex("""adapter HTTP (\d{3})""")

        /**
         * The census's four adapters as the app builds them (`SiteAdapterModule`): TikTok on its
         * own client ([tikTokHttp]: a short link's redirect cookies go on), the WebView's agent and
         * the public download service; Facebook, X and Instagram on [http].
         */
        fun registry(
            http: ExtractorHttpClient,
            tikTokHttp: ExtractorHttpClient,
            webViewAgent: suspend () -> String?,
        ): SiteExtractorRegistry = SiteExtractorRegistry(
            listOf(
                TikTokExtractor(
                    http = tikTokHttp,
                    agents = TikTokAgents(webViewAgent = webViewAgent),
                    askDownloadService = true,
                ),
                FacebookExtractor(http),
                XExtractor(http),
                InstagramExtractor(http),
            ),
        )

        /** Main's answer from the coordinator's [outcome]; the HTTP status from its Details line. */
        fun answerOf(outcome: SiteAdapterOutcome, hiddenPage: AnswerMark?): MainAnswer = when (outcome) {
            SiteAdapterOutcome.NotHandled -> MainAnswer.NotHandled
            is SiteAdapterOutcome.Detected -> MainAnswer.Found(outcome.candidates, hiddenPage)
            is SiteAdapterOutcome.Failed -> MainAnswer.Failed(
                reason = outcome.reason,
                statusCode = outcome.details.firstNotNullOfOrNull { line ->
                    ADAPTER_HTTP.matchEntire(line.trim())?.groupValues?.get(1)?.toIntOrNull()
                },
                hiddenPage = hiddenPage,
            )
        }

        /**
         * The hidden page's answer as a mark: the post's data; a check that stayed; TikTok's status
         * for the post (private, removed, region, sign-in) is the site's word that it is not shown;
         * no data (no WebView, a timeout, too large); turned off is not read.
         */
        fun markOf(result: HiddenPageResult): AnswerMark? = when (result) {
            is HiddenPageResult.Found -> AnswerMark.DATA
            is HiddenPageResult.NotFound -> when (result.reason) {
                null -> AnswerMark.NO_DATA
                SiteExtractionFailure.BOT_CHECK -> AnswerMark.BOT_CHECK
                else -> AnswerMark.NO_POST
            }
            HiddenPageResult.Off -> null
        }
    }
}
