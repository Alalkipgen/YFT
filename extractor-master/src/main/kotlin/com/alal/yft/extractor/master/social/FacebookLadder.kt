package com.alal.yft.extractor.master.social

import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.MasterStage
import com.alal.yft.extractor.master.PageSnapshot
import com.alal.yft.extractor.master.contract.SiteContracts
import com.alal.yft.extractor.master.layers.ContractReader
import com.alal.yft.extractor.master.layers.LayerStack
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.lookup.RouteBHttpClient
import com.alal.yft.extractor.master.lookup.RouteBLookup
import com.alal.yft.extractor.master.policy.SnapshotBudget
import com.alal.yft.extractor.master.policy.TerminalRules
import com.alal.yft.extractor.master.recipes.PayloadRecipes
import com.alal.yft.extractor.master.toolkit.PageScripts
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Social plan S1a (3) (`MASTER_KEY_SOCIAL_PLAN.md` §6.1, §7.3): Facebook's ladder after main
 * failed (the pilot), in place of R8's unjudged ask. F1: the plugin player (`plugins/video.php`,
 * R8's recipe: desktop Safari, no session) asked once for the video, inside the lookup's record
 * under route B's rules ([RouteBLookup]: never the user's credentials, nothing new after a 429,
 * an endpoint asked once), read with the recipe's key table (rows bound to the video ID) and
 * judged ([FacebookAnswerJudge]): rows → the engine's media checks; a final answer only on
 * positive evidence (the page's route: private or removed; DRM) → the lookup's answer; anything
 * else — "needs playback" (the player names the video, no file), a sign-in wall, a 429 or a bot
 * check, no evidence — leaves nothing to read: the next stage (F3, the visible tab's own
 * playback). A link main resolves first (a share link, a post) is read through main's resolved
 * link ([MainPages.resolved]); one main did not resolve to a video is not asked about.
 *
 * S1a (4): F2, main's pages, when F1 leaves nothing to read: the pages main read in this lookup
 * for the link ([MainPages]: the link's own page, the video's) are read with Master's layers, no
 * request (the data scripts that name the video; L4's keys and L3's shape search; rows bound to
 * the video ID), each judged as F1's answer is. Rows → the engine's media checks (the page data);
 * DRM → final; the page's own route → final only when every page main read for the link was read
 * and says the same and the plugin player did not name the video; else the next stage. A link
 * main did not resolve to a video: its pages are judged for the route only (no rows); another
 * video's page (its final address names another video) is not read.
 *
 * Claims only a Facebook lookup with its own record ([LookupRecord]: the social-ladder build's,
 * `-Pyft.socialLadder`); every other lookup keeps R8's path. [identify] names a page's video (the
 * app's adapters); [contractsOf] builds the recipe source around the ladder's client (the app's
 * signed config when its build has one). Lines: short labels, never a link, an ID or the page's
 * words; nothing is logged.
 */
class FacebookLadder(
    http: ExtractorHttpClient,
    private val identify: (String) -> SitePageIdentity?,
    contractsOf: (ExtractorHttpClient) -> SiteContracts = ::SiteContracts,
) : SocialStage {
    private val contracts = contractsOf(Keeping(RouteBHttpClient(http)))

    override suspend fun claims(request: MasterRequest): Boolean =
        request.identity?.siteId == SITE_ID && coroutineContext[LookupRecord] != null

    override suspend fun stage(
        request: MasterRequest,
        policy: MasterPolicy,
        details: MutableList<String>,
    ): SocialStage.Staged? {
        val record = coroutineContext[LookupRecord] ?: return null
        val asked = request.identity?.takeIf { it.siteId == SITE_ID } ?: return null
        // As R8: a sign-in or bot-check failure is the user's own playback's; another video's
        // page is not asked about.
        if (TerminalRules.needsAuthorizedPlayback(request.primaryFailure) ||
            request.expectedContentId?.let { it != asked.contentId } == true
        ) {
            return null
        }
        val pages = coroutineContext[MainPages]
        val identity = if (asked.requiresCanonicalResolution) {
            pages?.resolved(asked, identify)
        } else {
            asked
        }
        if (identity == null) {
            details += "$LINE: a link main resolves first, no video in main's answer; not asked"
        }
        val plugin = if (identity != null && contracts.reads(identity)) {
            pluginPlayer(record, request, identity, policy, details)
        } else {
            null
        }
        plugin?.staged?.let { return it }
        // S1a (4): F2, main's pages (no request), when F1 left nothing to read.
        return pages?.let { mainPages(it, request, asked, identity, plugin?.judged, policy, details) }
    }

    /** F1's judged answer: what it staged (the rows or a final answer; null: nothing to read). */
    private class Plugin(val staged: SocialStage.Staged?, val judged: Judged)

    /** F1: the plugin player asked once for [identity], judged; null when nothing was judged. */
    private suspend fun pluginPlayer(
        record: LookupRecord,
        request: MasterRequest,
        identity: SitePageIdentity,
        policy: MasterPolicy,
        details: MutableList<String>,
    ): Plugin? {
        val read = request.copy(
            identity = identity,
            expectedContentId = request.expectedContentId?.let { identity.contentId },
        )
        val route = RouteBLookup(record)
        val kept = Kept()
        val outcome = withTimeoutOrNull(minOf(policy.contractTimeoutMillis, policy.timeoutMillis)) {
            withContext(route + kept) { contracts.ask(read) }
        }
        details += "$LINE after ${request.primaryFailure.name}; ${route.summary()}"
        val answer = kept.result
        when (outcome) {
            null -> {
                details += "$F1: no answer in time"
                return null
            }
            is SiteContracts.Asked.Answer -> details += outcome.detail
            is SiteContracts.Asked.Unanswered -> outcome.detail?.let(details::add)
        }
        // Nothing went out (no endpoint for this video): nothing to judge.
        answer ?: return null
        val contract = (outcome as? SiteContracts.Asked.Answer)?.snapshot?.contract
        val recipe = contract?.let { contracts.recipeOf(it.site) }
        val evidence = if (contract != null && recipe != null) ContractReader(read, recipe, contract).read() else null
        val rows = evidence?.candidates?.size ?: 0
        val drm = evidence?.terminalFailure == SiteExtractionFailure.DRM_PROTECTED
        val needsPlayback = contract != null && recipe != null && rows == 0 && !drm &&
            namesVideo(contract.body, identity.contentId, recipe.nodeIdFields)
        val judged = FacebookAnswerJudge.judge(answer, rows, needsPlayback = needsPlayback, drm = drm)
        details += "$F1: ${judged.answerClass.name} (${judged.evidence}); next: ${judged.next.name}"
        val staged = when (judged.next) {
            Next.CHECK_ROWS -> (outcome as? SiteContracts.Asked.Answer)?.let {
                SocialStage.Staged(MasterStage.CONTRACT, it.snapshot, read)
            }
            Next.FINAL -> judged.reason?.let { reason ->
                SocialStage.Staged(
                    MasterStage.CONTRACT,
                    PageSnapshot(request.pageUrl, request.generation, accessFailure = reason),
                    read,
                )
            }
            Next.NEXT_RUNG, Next.PLAYBACK, Next.BROWSER, Next.STOP_SITE -> null
        }
        return Plugin(staged, judged)
    }

    /**
     * S1a (4): F2, the pages main read in this lookup for the link ([pages]), read without a
     * request: each page's data scripts that name the video ([documents]) through Master's layers
     * (L4's keys, L3's shape search: rows bound to the video ID), and the page judged as F1's
     * answer. Rows on a page → the page data's media checks; DRM → final; the page's own route →
     * final only when every page main read for the link was read and says the same, and the
     * plugin player ([plugin], F1's judgement) did not name the video. Else null: the next stage.
     */
    private fun mainPages(
        pages: MainPages,
        request: MasterRequest,
        asked: SitePageIdentity,
        identity: SitePageIdentity?,
        plugin: Judged?,
        policy: MasterPolicy,
        details: MutableList<String>,
    ): SocialStage.Staged? {
        val forLink = pages.pages().filter { forVideo(it, asked, identity) }
        if (forLink.isEmpty()) {
            details += "$F2: no page main read for the video; nothing to read"
            return null
        }
        val read = identity?.let { request.copy(identity = it, expectedContentId = it.contentId) } ?: request
        val layers = LayerStack.standard(contracts::recipeOf)
        val rows = LinkedHashSet<String>()
        val finals = mutableListOf<AnswerClass?>()
        var drm = false
        forLink.forEachIndexed { index, page ->
            val line = "$F2: page ${index + 1}"
            val body = page.body
            when {
                otherVideo(page.finalUrl, identity) -> {
                    details += "$line: another video's page; not read"
                    finals += null
                }
                body == null -> {
                    details += "$line: not kept (over the bound)"
                    finals += null
                }
                else -> {
                    val documents = documents(body, identity, policy)
                    val evidence = layers.collect(
                        read,
                        PageSnapshot(request.pageUrl, request.generation, apiResponses = documents),
                    )
                    val bound = identity?.let { "$SITE_ID:${it.contentId}" }
                    val found = if (bound == null || evidence.terminalFailure != null) {
                        0
                    } else {
                        evidence.candidates.count { it.pageRole == PageMediaRole.MAIN && it.videoId == bound }
                    }
                    val judged = FacebookAnswerJudge.judge(
                        ExtractorHttpResult.Success(page.statusCode, body, page.finalUrl, page.contentType),
                        found,
                        drm = evidence.terminalFailure == SiteExtractionFailure.DRM_PROTECTED,
                    )
                    details += "$line: ${judged.answerClass.name} (${judged.evidence})"
                    if (judged.answerClass == AnswerClass.DRM) drm = true
                    if (judged.next == Next.CHECK_ROWS) rows += documents
                    finals += judged.answerClass.takeIf { it.next == Next.FINAL }
                }
            }
        }
        if (pages.unlisted > 0) {
            details += "$F2: ${pages.unlisted} more of main's pages not listed; not read"
            finals += null
        }
        fun final(reason: SiteExtractionFailure): SocialStage.Staged {
            details += "$F2: next: ${Next.FINAL.name}"
            return SocialStage.Staged(
                MasterStage.PAGE_DATA,
                PageSnapshot(request.pageUrl, request.generation, accessFailure = reason),
                read,
            )
        }
        if (drm) return final(SiteExtractionFailure.DRM_PROTECTED)
        if (rows.isNotEmpty()) {
            details += "$F2: next: ${Next.CHECK_ROWS.name}"
            val snapshot = PageSnapshot(request.pageUrl, request.generation, apiResponses = bounded(rows, policy))
            return SocialStage.Staged(MasterStage.PAGE_DATA, snapshot, read)
        }
        // The page's own route: final only when every page main read for the link says the same.
        val agreed = finals.distinct().singleOrNull()?.reason
        if (agreed != null && plugin?.answerClass == AnswerClass.NEEDS_PLAYBACK) {
            details += "$F2: the plugin player names the video; next: ${Next.PLAYBACK.name}"
            return null
        }
        if (agreed != null) return final(agreed)
        details += "$F2: next: ${Next.PLAYBACK.name}"
        return null
    }

    /** A page main read for the link: the link's own page, or the video's own page. */
    private fun forVideo(page: MainPages.Page, asked: SitePageIdentity, identity: SitePageIdentity?): Boolean =
        page.url == asked.canonicalPageUrl ||
            identity != null && videoOf(page.url)?.contentId == identity.contentId

    /** Whether [finalUrl] names a video other than [identity]'s (any video, for none). */
    private fun otherVideo(finalUrl: String, identity: SitePageIdentity?): Boolean =
        videoOf(finalUrl)?.let { it.contentId != identity?.contentId } == true

    /** The video [url] names (Facebook's, itself resolved), or null. */
    private fun videoOf(url: String): SitePageIdentity? =
        identify(url)?.takeIf { it.siteId == SITE_ID && !it.requiresCanonicalResolution }

    /**
     * A page's data scripts (JSON by their own type) that name [identity]'s video ID, in page
     * order, within the snapshot's bounds; none for a link main did not resolve to a video.
     */
    private fun documents(body: String, identity: SitePageIdentity?, policy: MasterPolicy): List<String> {
        identity ?: return emptyList()
        val named = Regex("(?<![0-9A-Za-z_])${Regex.escape(identity.contentId)}(?![0-9A-Za-z_])")
        val scripts = PageScripts.dataScripts(body, PayloadRecipes.JSON_SCRIPT_TYPES)
        return bounded(scripts.map { it.body }.filter(named::containsMatchIn), policy)
    }

    /** At most [SnapshotBudget.MAX_PAYLOADS] documents within the policy's characters, in order. */
    private fun bounded(documents: Collection<String>, policy: MasterPolicy): List<String> {
        val kept = ArrayList<String>()
        var chars = 0L
        for (document in documents) {
            if (kept.size == SnapshotBudget.MAX_PAYLOADS) break
            if (chars + document.length > policy.maxSnapshotChars) continue
            kept += document
            chars += document.length
        }
        return kept
    }

    override fun toString(): String = "FacebookLadder"

    /** The rung's one HTTP answer, kept for the judge (each ask its own, in its context). */
    private class Kept : AbstractCoroutineContextElement(Kept) {
        @Volatile
        var result: ExtractorHttpResult? = null

        companion object Key : CoroutineContext.Key<Kept>
    }

    /** The ladder's client: each answer (a refusal included) also kept in the ask's [Kept]. */
    private class Keeping(private val http: ExtractorHttpClient) : ExtractorHttpClient {
        override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult =
            http.get(url, headers, maxBodyBytes).also { coroutineContext[Kept]?.result = it }

        override suspend fun postJson(
            url: String,
            body: String,
            headers: Map<String, String>,
            maxBodyBytes: Long,
        ): ExtractorHttpResult = http.postJson(url, body, headers, maxBodyBytes).also { coroutineContext[Kept]?.result = it }

        override suspend fun probe(url: String, headers: Map<String, String>, timeoutMillis: Long): ExtractorProbeResult =
            http.probe(url, headers, timeoutMillis)

        override fun toString(): String = "FacebookLadder.Keeping"
    }

    companion object {
        /** Main's Facebook adapter's site ID (the video key's prefix). */
        const val SITE_ID = "facebook"

        private const val LINE = "master: facebook ladder"
        private const val F1 = "master: facebook F1 plugin player"
        private const val F2 = "master: facebook F2 main's pages"

        /**
         * "Needs playback": the player's answer names the video (one of the recipe's ID fields
         * holds its ID) while no file was read for it.
         */
        internal fun namesVideo(body: String, contentId: String, fields: List<String>): Boolean {
            if (fields.isEmpty() || contentId.isBlank()) return false
            val names = fields.joinToString("|") { Regex.escape(it) }
            return Regex("\"(?:$names)\"\\s*:\\s*\"?${Regex.escape(contentId)}(?![0-9A-Za-z_])").containsMatchIn(body)
        }
    }
}
