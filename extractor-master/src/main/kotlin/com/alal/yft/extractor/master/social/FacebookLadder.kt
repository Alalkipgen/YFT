package com.alal.yft.extractor.master.social

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
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.lookup.RouteBHttpClient
import com.alal.yft.extractor.master.lookup.RouteBLookup
import com.alal.yft.extractor.master.policy.TerminalRules
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
        val identity = if (asked.requiresCanonicalResolution) {
            coroutineContext[MainPages]?.resolved(asked, identify)
        } else {
            asked
        }
        if (identity == null) {
            details += "$LINE: a link main resolves first, no video in main's answer; not asked"
            return null
        }
        if (!contracts.reads(identity)) return null
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
        return when (judged.next) {
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
