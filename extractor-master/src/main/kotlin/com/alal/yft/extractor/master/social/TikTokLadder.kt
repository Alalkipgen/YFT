package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.ContractAnswer
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.MasterStage
import com.alal.yft.extractor.master.PageSnapshot
import com.alal.yft.extractor.master.contract.SiteContracts
import com.alal.yft.extractor.master.layers.ContractReader
import com.alal.yft.extractor.master.layers.Evidence
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.lookup.RouteBHttpClient
import com.alal.yft.extractor.master.lookup.RouteBLookup
import com.alal.yft.extractor.master.policy.TerminalRules
import java.net.URI
import java.util.WeakHashMap
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Social plan S2b (`MASTER_KEY_SOCIAL_PLAN.md` §6.2, §7.3): TikTok's ladder after main failed, in
 * place of R8's unjudged ask, built as Facebook's ([FacebookLadder]) and inside the same rules.
 *
 * - **The photo check first, no request:** a link (or the video main's resolved link names) at a
 *   photo address, one of main's pages for the link that landed on one, or main's page data for the
 *   item stating its photo list (the recipe's `imagePost`) → the lookup's final answer, the post
 *   has no video (`NO_MEDIA_FOUND`, main's own answer for a photo post): no ask, no capture.
 * - **T1, the embed player** (`embed/v2`, R8's recipe: the tab's own agent, never the user's
 *   cookies; its files open without any), asked once inside the lookup's record under route B's
 *   rules ([RouteBLookup]: never the user's credentials, nothing new after a 429, an endpoint asked
 *   once), read with the recipe's key table (rows bound to the item ID) and judged
 *   ([TikTokAnswerJudge]): rows → the engine's media checks (V and the init check, S6); a photo
 *   post or DRM → final; a 429 or a bot check → the site stops (no new request to its host in the
 *   lookup, its 30-minute cooldown, S7); TikTok's status, a login address, "names the video" and no
 *   evidence → nothing to read: T2.
 * - **T2, main's pages** (no request): the pages main read in this lookup for the link
 *   ([MainPages]: the phone page, the desktop page) read with the same key table, which also reads
 *   main's page shapes (`itemStruct`, `bitrateInfo`, the play address; the watermarked download
 *   address only without them): the data block is found by its content (the object with the item
 *   ID and a video), not by its script ID, and a renamed key falls to L4 + L3 on the same page.
 *   The page with the most rows → the engine's media checks (its own page data); DRM → final;
 *   TikTok's status on main's page is never final here (main's P46 rule). Else null: the next
 *   stage, the visible tab's own playback (T3). A page that names another video is not read.
 *
 * Why T1 before T2 (§7.4's note "T2 before T1", not chosen): TikTok's page files open only with
 * the cookies of the page answer that gave them (R20: `tt_chain_token`), which V never sends (S2)
 * and [MainPages] never keeps, so T2's rows would mostly fail V, while T1's embed files open
 * without cookies; T2 still reads after T1's 429 or bot check (no request). The embed's rows are at
 * most 1280 lines (documented in `docs/SUPPORT_MATRIX.md`).
 *
 * S7: T1 asks only when its host did not stop in this lookup (a 429 or a bot check, main's asks
 * included), it does not cool ([cooldown], across lookups) and the lookup's [budget] holds
 * ([SocialBudget.TIKTOK]: main's and Master's asks, the lookup's time, Master's answers'
 * characters); else T1 is not asked and T2 still reads. A short link: the video main's resolved
 * link names ([MainPages.resolved]), else nothing is asked.
 *
 * v1.0.2 (item 18): T1's asks paced in the app's session, at least the budget's gap apart
 * ([SocialBudget.gapMillis], 10 s; [cooldown]'s [SocialCooldown.pace], across lookups): a lookup
 * within the gap waits the rest when T1's ask still fits the lookup's time after it, else T1 is
 * not asked (T2 still reads); after a 429 or a bot check T1's host cools for 30 minutes (S7).
 *
 * S4: a rung the signed recipe config turns off ([RungsOff]: T1, T2, T3) is not run, its line
 * says so (the photo check is no rung: it still reads); the config is asked for (when due, on
 * its own) as the stage starts.
 *
 * Claims only a TikTok lookup with its own record ([LookupRecord]: the social-ladder build's,
 * `-Pyft.socialLadder`); every other lookup keeps R8's path. [identify] names a page's video (the
 * app's adapters); [contractsOf] builds the recipe source around the ladder's client. Lines: short
 * labels, never a link, an ID or the page's words; nothing is logged.
 */
class TikTokLadder(
    http: ExtractorHttpClient,
    private val identify: (String) -> SitePageIdentity?,
    contractsOf: (ExtractorHttpClient) -> SiteContracts = ::SiteContracts,
    private val cooldown: SocialCooldown = SocialCooldown(),
    private val budget: SocialBudget = SocialBudget.TIKTOK,
) : SocialStage {
    private val contracts = contractsOf(Keeping(RouteBHttpClient(http)))

    /** S7: the characters of Master's answers per lookup (its record), dropped with the record. */
    private val spent = WeakHashMap<LookupRecord, Long>()

    override suspend fun claims(request: MasterRequest): Boolean =
        request.identity?.siteId == SITE_ID && coroutineContext[LookupRecord] != null

    override suspend fun stage(
        request: MasterRequest,
        policy: MasterPolicy,
        details: MutableList<String>,
    ): SocialStage.Staged? {
        val record = coroutineContext[LookupRecord] ?: return null
        // S4: the signed config's ask when due (outside the lookup), so its rungs off apply here.
        contracts.refresh()
        // S7: the hosts that answered a 429 or a bot check in this lookup (main's asks included)
        // cool from the stage's end, whatever it answers.
        return try {
            climb(record, request, policy, details)
        } finally {
            cooldown.note(record.stopHosts())
        }
    }

    /** S4: T3, the visible tab's own playback, when the signed config turns it off. */
    override suspend fun captureOff(request: MasterRequest): String? =
        RungsOff.line(T3).takeIf { "T3" in contracts.rungsOff() }

    /** The ladder's steps for [request]'s lookup ([record]): the photo check, T1, then T2. */
    private suspend fun climb(
        record: LookupRecord,
        request: MasterRequest,
        policy: MasterPolicy,
        details: MutableList<String>,
    ): SocialStage.Staged? {
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
        val read = identity?.let { request.copy(identity = it, expectedContentId = it.contentId) } ?: request
        // Main's pages for the link, read once with no request: the photo check, then T2.
        val readings = pages?.let { readPages(it, asked, identity, read) }.orEmpty()
        photo(asked, identity, readings)?.let { why ->
            details += "$LINE: ${AnswerClass.PHOTO_ONLY.name} ($why); next: ${Next.FINAL.name}; not asked"
            return final(request, read, SiteExtractionFailure.NO_MEDIA_FOUND)
        }
        // S4: a rung the signed config turns off is not run.
        val off = contracts.rungsOff()
        if (identity != null && contracts.reads(identity)) {
            if ("T1" in off) {
                details += RungsOff.line(T1)
            } else {
                embedPlayer(record, request, identity, policy, details)?.let { return it }
            }
        }
        if (pages != null && "T2" in off) {
            details += RungsOff.line(T2)
            return null
        }
        // T2, main's pages (no request), when T1 left nothing to read.
        return pages?.let { mainPages(it, readings, request, read, details) }
    }

    /**
     * T1: the embed player asked once for [identity], judged: the rows to check or a final answer;
     * null when nothing is left to read (T2 next).
     */
    private suspend fun embedPlayer(
        record: LookupRecord,
        request: MasterRequest,
        identity: SitePageIdentity,
        policy: MasterPolicy,
        details: MutableList<String>,
    ): SocialStage.Staged? {
        // S7: a stopped or cooling host, or the lookup's budget spent: not asked.
        stopped(record)?.let { why ->
            details += "$T1: $why; not asked"
            return null
        }
        // v1.0.2 (item 18): T1's turn after its last ask; a stop while it waited: not asked.
        val gap = "${budget.gapMillis / MILLIS_PER_SECOND} s between its asks"
        val waited = pace(record, policy) ?: run {
            details += "$T1: paced ($gap), its wait over the lookup's time budget; not asked"
            return null
        }
        if (waited > 0) {
            val seconds = (waited + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND
            details += "$T1: paced, waited $seconds s ($gap)"
            stopped(record)?.let { why ->
                details += "$T1: $why; not asked"
                return null
            }
        }
        val read = request.copy(
            identity = identity,
            expectedContentId = request.expectedContentId?.let { identity.contentId },
        )
        val route = RouteBLookup(record)
        val kept = Kept(limit = bytesLeft(record))
        val wait = minOf(policy.contractTimeoutMillis, policy.timeoutMillis, record.timeLeftMillis(budget.millis))
        val outcome = withTimeoutOrNull(wait) {
            withContext(route + kept) { contracts.ask(read) }
        }
        // S7: what went out counts against the lookup's characters.
        if (route.newRequests > 0) spend(record, kept)
        details += "$LINE after ${request.primaryFailure.name}; ${route.summary()}"
        val answer = kept.result
        when (outcome) {
            null -> {
                details += "$T1: no answer in time"
                return null
            }
            is SiteContracts.Asked.Answer -> details += outcome.detail
            is SiteContracts.Asked.Unanswered -> outcome.detail?.let(details::add)
        }
        // Nothing went out (no endpoint for this video): nothing to judge.
        answer ?: return null
        val contract = (outcome as? SiteContracts.Asked.Answer)?.snapshot?.contract
        val recipe = contracts.recipeOf(SITE_ID)
        val evidence = if (contract != null && recipe != null) ContractReader(read, recipe, contract).read() else null
        val rows = evidence?.candidates.orEmpty().size
        val drm = evidence?.terminalFailure == SiteExtractionFailure.DRM_PROTECTED
        val photo = evidence?.terminalFailure == SiteExtractionFailure.NO_MEDIA_FOUND
        val names = contract != null && recipe != null && rows == 0 && !drm && !photo &&
            FacebookLadder.namesVideo(contract.body, identity.contentId, recipe.nodeIdFields)
        val judged = TikTokAnswerJudge.judge(answer, rows, drm = drm, photo = photo, namesVideo = names)
        details += "$T1: ${judged.answerClass.name} (${judged.evidence}); next: ${judged.next.name}"
        // S7: T1's own 429 or bot check cools T1's host too (the embed's answer is no page reading).
        if (route.newRequests > 0 && judged.next == Next.STOP_SITE) cooldown.note(hosts())
        return when (judged.next) {
            Next.CHECK_ROWS -> (outcome as? SiteContracts.Asked.Answer)?.let {
                SocialStage.Staged(MasterStage.CONTRACT, it.snapshot, read)
            }
            Next.FINAL -> judged.reason?.let { final(request, read, it, MasterStage.CONTRACT) }
            Next.NEXT_RUNG, Next.PLAYBACK, Next.BROWSER, Next.STOP_SITE -> null
        }
    }

    /**
     * S7: why T1 may not ask in [record]'s lookup, or null. After a 429 in the lookup route B's
     * rule refuses the ask (null here); else not asked: T1's host stopped in the lookup (a 429 or a
     * bot check), it still cools, or the lookup's request, time or byte budget is spent.
     */
    private fun stopped(record: LookupRecord): String? {
        if (record.rateLimited) return null
        val hosts = hosts()
        val stops = record.stopHosts()
        return when {
            hosts.any(stops::contains) -> "the host stopped in this lookup (a 429 or a bot check)"
            hosts.any(cooldown::cooling) -> "cooling down after a 429 or a bot check"
            record.mainAsks + record.masterAsks >= budget.requests -> "over the lookup's request budget"
            record.elapsedMillis() >= budget.millis -> "over the lookup's time budget"
            bytesLeft(record) <= 0 -> "over the lookup's byte budget"
            else -> null
        }
    }

    /**
     * v1.0.2 (item 18): T1's turn, at least [SocialBudget.gapMillis] after its last ask in the
     * process ([SocialCooldown.pace]): the millis it waited, or null when the wait would leave
     * T1's ask (its share of the lookup, R8's) no room in the lookup's time budget: not asked, no
     * turn taken. After a 429 in this lookup route B refuses the ask: nothing goes out, no turn.
     */
    private suspend fun pace(record: LookupRecord, policy: MasterPolicy): Long? {
        if (record.rateLimited) return 0L
        val ask = minOf(policy.contractTimeoutMillis, policy.timeoutMillis)
        val room = minOf(budget.gapMillis, record.timeLeftMillis(budget.millis) - ask)
        val wait = cooldown.pace(hosts(), budget.gapMillis, room) ?: return null
        if (wait > 0) delay(wait)
        return wait
    }

    /** T1's hosts: its recipe's answer hosts (its endpoint is one of them), lowercase. */
    private fun hosts(): Set<String> =
        contracts.recipeOf(SITE_ID)?.answerHosts.orEmpty().map { it.lowercase().trimEnd('.') }.toSet()

    /** S7: the characters left of [record]'s lookup's byte budget for Master's answers. */
    private fun bytesLeft(record: LookupRecord): Long =
        (budget.bytes - synchronized(spent) { spent[record] ?: 0L }).coerceAtLeast(0)

    /** S7: [kept]'s answer's characters (its whole bound when it was over it) spent in [record]'s lookup. */
    private fun spend(record: LookupRecord, kept: Kept) {
        val answer = kept.result
        val chars = when {
            answer is ExtractorHttpResult.Success -> answer.body.length.toLong()
            (answer as? ExtractorHttpResult.Failure)?.reason == SiteExtractionFailure.RESPONSE_TOO_LARGE -> kept.limit
            else -> 0L
        }
        if (chars > 0) synchronized(spent) { spent[record] = (spent[record] ?: 0L) + chars }
    }

    /**
     * One of main's pages for the link, as read with no request (null [evidence]: not read, [why]);
     * [other]: its final address names another post.
     */
    private class Reading(
        val page: MainPages.Page,
        val evidence: Evidence?,
        val why: String?,
        val other: Boolean = false,
    )

    /**
     * Main's pages for the link (the link's own page, the video's own page), each read with the
     * recipe's key table for [identity]'s item ([read]); another video's page, a page over the
     * bound and every page of a link main did not resolve are not read (their addresses still count
     * for the photo check).
     */
    private fun readPages(
        pages: MainPages,
        asked: SitePageIdentity,
        identity: SitePageIdentity?,
        read: MasterRequest,
    ): List<Reading> {
        val recipe = contracts.recipeOf(SITE_ID)
        return pages.pages().filter { forVideo(it, asked, identity) }.map { page ->
            val body = page.body
            when {
                otherVideo(page.finalUrl, identity) -> Reading(page, null, "another video's page; not read", other = true)
                body == null -> Reading(page, null, "not kept (over the bound)")
                identity == null || recipe == null -> Reading(page, null, "no video to read it for; not read")
                else -> Reading(page, ContractReader(read, recipe, ContractAnswer(SITE_ID, body, page.finalUrl)).read(), null)
            }
        }
    }

    /**
     * Why the post is a photo post with no request, or null: [asked]'s or [identity]'s address, a
     * page main read for the link that landed on a photo address, or main's page data for the item
     * stating its photo list (the recipe's no-video paths).
     */
    private fun photo(asked: SitePageIdentity, identity: SitePageIdentity?, readings: List<Reading>): String? = when {
        photoAddress(asked.canonicalPageUrl) -> "the link: a photo address"
        identity != null && photoAddress(identity.canonicalPageUrl) -> "main's resolved link: a photo address"
        readings.any { !it.other && photoAddress(it.page.finalUrl) } -> "main's page: landed on a photo address"
        readings.any { it.evidence?.terminalFailure == SiteExtractionFailure.NO_MEDIA_FOUND } ->
            "main's page data: a photo post"
        else -> null
    }

    /**
     * T2: main's pages for the link ([readings], no request), each judged as T1's answer is. The
     * page with the most rows → the page data's media checks (its own answer, read with the key
     * table); DRM → final; else null: the next stage (TikTok's status on main's page is never
     * final here).
     */
    private fun mainPages(
        pages: MainPages,
        readings: List<Reading>,
        request: MasterRequest,
        read: MasterRequest,
        details: MutableList<String>,
    ): SocialStage.Staged? {
        if (readings.isEmpty()) {
            details += "$T2: no page main read for the video; nothing to read"
            return null
        }
        var best: Pair<Reading, Int>? = null
        var drm = false
        readings.forEachIndexed { index, reading ->
            val line = "$T2: page ${index + 1}"
            val evidence = reading.evidence
            val body = reading.page.body
            if (evidence == null || body == null) {
                details += "$line: ${reading.why}"
                return@forEachIndexed
            }
            val rows = evidence.candidates.size
            val judged = TikTokAnswerJudge.judge(
                ExtractorHttpResult.Success(reading.page.statusCode, body, reading.page.finalUrl, reading.page.contentType),
                rows,
                drm = evidence.terminalFailure == SiteExtractionFailure.DRM_PROTECTED,
            )
            details += "$line: ${judged.answerClass.name} (${judged.evidence})"
            if (judged.answerClass == AnswerClass.DRM) drm = true
            if (judged.next == Next.CHECK_ROWS && rows > (best?.second ?: 0)) best = reading to rows
        }
        if (pages.unlisted > 0) details += "$T2: ${pages.unlisted} more of main's pages not listed; not read"
        if (drm) {
            details += "$T2: next: ${Next.FINAL.name}"
            return final(request, read, SiteExtractionFailure.DRM_PROTECTED)
        }
        val chosen = best?.first ?: run {
            details += "$T2: next: ${Next.PLAYBACK.name}"
            return null
        }
        details += "$T2: next: ${Next.CHECK_ROWS.name}"
        val page = chosen.page
        val answer = ContractAnswer(SITE_ID, checkNotNull(page.body), page.finalUrl)
        return SocialStage.Staged(
            MasterStage.PAGE_DATA,
            PageSnapshot(request.pageUrl, request.generation, contract = answer),
            read,
        )
    }

    /** A final answer [reason] for [request]'s page, read as [stage] for [read]. */
    private fun final(
        request: MasterRequest,
        read: MasterRequest,
        reason: SiteExtractionFailure,
        stage: MasterStage = MasterStage.PAGE_DATA,
    ) = SocialStage.Staged(stage, PageSnapshot(request.pageUrl, request.generation, accessFailure = reason), read)

    /** A page main read for the link: the link's own page, or the video's own page. */
    private fun forVideo(page: MainPages.Page, asked: SitePageIdentity, identity: SitePageIdentity?): Boolean =
        page.url == asked.canonicalPageUrl ||
            identity != null && videoOf(page.url)?.contentId == identity.contentId

    /** Whether [finalUrl] names a post other than [identity]'s (any post, for none). */
    private fun otherVideo(finalUrl: String, identity: SitePageIdentity?): Boolean =
        videoOf(finalUrl)?.let { it.contentId != identity?.contentId } == true

    /** The post [url] names (TikTok's, itself resolved), or null. */
    private fun videoOf(url: String): SitePageIdentity? =
        identify(url)?.takeIf { it.siteId == SITE_ID && !it.requiresCanonicalResolution }

    override fun toString(): String = "TikTokLadder"

    /**
     * The rung's one HTTP answer, kept for the judge (each ask its own, in its context); [limit]:
     * S7's characters left in the lookup, the most the ask reads.
     */
    private class Kept(val limit: Long = Long.MAX_VALUE) : AbstractCoroutineContextElement(Kept) {
        @Volatile
        var result: ExtractorHttpResult? = null

        companion object Key : CoroutineContext.Key<Kept>
    }

    /**
     * The ladder's client: each answer (a refusal included) also kept in the ask's [Kept]; S7: an
     * ask reads at most the lookup's characters left ([Kept.limit]).
     */
    private class Keeping(private val http: ExtractorHttpClient) : ExtractorHttpClient {
        override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult =
            http.get(url, headers, limited(maxBodyBytes)).also { coroutineContext[Kept]?.result = it }

        override suspend fun postJson(
            url: String,
            body: String,
            headers: Map<String, String>,
            maxBodyBytes: Long,
        ): ExtractorHttpResult =
            http.postJson(url, body, headers, limited(maxBodyBytes)).also { coroutineContext[Kept]?.result = it }

        override suspend fun probe(url: String, headers: Map<String, String>, timeoutMillis: Long): ExtractorProbeResult =
            http.probe(url, headers, timeoutMillis)

        private suspend fun limited(maxBodyBytes: Long): Long =
            coroutineContext[Kept]?.let { minOf(maxBodyBytes, it.limit) } ?: maxBodyBytes

        override fun toString(): String = "TikTokLadder.Keeping"
    }

    companion object {
        /** Main's TikTok adapter's site ID (the video key's prefix). */
        const val SITE_ID = "tiktok"

        internal const val LINE = "master: tiktok ladder"
        internal const val T1 = "master: tiktok T1 embed player"
        internal const val T2 = "master: tiktok T2 main's pages"

        /** S4: T3, the visible tab's own playback (its line only when the config turns it off). */
        internal const val T3 = "master: tiktok T3"

        private const val MILLIS_PER_SECOND = 1_000L

        /**
         * A TikTok photo address: a TikTok host whose path names a photo post (`/photo/{id}`,
         * after the author's handle or alone), as main's `TikTokUrls.isPhotoPost` reads a
         * canonical address.
         */
        internal fun photoAddress(url: String): Boolean {
            val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
            val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
            if (host != "tiktok.com" && !host.endsWith(".tiktok.com")) return false
            val segments = uri.rawPath.orEmpty().split('/').filter(String::isNotEmpty)
            return segments.zipWithNext().any { (kind, id) -> kind.lowercase() == "photo" && NUMERIC.matches(id) }
        }

        private val NUMERIC = Regex("^[0-9]{1,30}$")
    }
}
