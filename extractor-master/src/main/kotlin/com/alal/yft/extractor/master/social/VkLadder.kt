package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.ContractAnswer
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.MasterStage
import com.alal.yft.extractor.master.PageSnapshot
import com.alal.yft.extractor.master.contract.SiteContracts
import com.alal.yft.extractor.master.lookup.AnswerMark
import com.alal.yft.extractor.master.lookup.LookupAsk
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.lookup.RouteBLookup
import com.alal.yft.extractor.master.policy.TerminalRules
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * VK-Master plan §4 (V1–V3), §5, §6: VK's ladder after main failed, in place of R8's unjudged ask,
 * inside the same rules as the four sites' ([XLadder], [InstagramLadder]; [RouteBLookup]: never
 * the user's credentials, nothing new after a 429, no endpoint twice).
 *
 * - **V1, main's answers re-read (no request):** the answers main's VK adapter got in this lookup —
 *   its embed pages (B, B again, B2) and its `video.get` answers ([MainPages]: the page, or the
 *   refused GET), and its token asks by their status and mark ([LookupRecord]; in a record
 *   labelled with [VkLabels], any ask of the token's way, main's other values too) — read by
 *   Master's own reader ([VkReader]: VK's key table, then the shape scan, bound to the link's
 *   `owner_id` + video id, never the trailer or the poster) and judged ([VkAnswerJudge]). Rows (`video.get`'s
 *   first, then the embed's) go to the engine's media checks (V) as one video's answer read with
 *   VK's recipe; VK's deleted icon on a video VK does not play is the lookup's final answer; a 429
 *   or a bot check stops the site (its host cools); anything else leaves nothing to read.
 * - **V2, the heal (at most two requests):** only after main's adult answer (the embed's, or main's
 *   path D asked at all) **and** a refused value (the token endpoint refused the web client's values,
 *   or `video.get` answered one of VK's errors for them): the first of [values]' values that is not
 *   main's ([VkClientValues]: the bundled values with a debug hook until agent C's live source)
 *   asks one token (login.vk.ru; kept in memory for this stage only, never written anywhere) and one
 *   `video.get` (api.vkvideo.ru), each with the lookup's own user agent and no cookie, each within
 *   the lookup's time. Not after a 429 or a bot check in the lookup, not while a VK host cools, not
 *   over VK's budget ([budget]); an endpoint asked in the lookup (main's included) is not asked again
 *   (route B's rule: main's identical answer is replayed, else the ask is refused), so values that
 *   differ from main's only on the token's side ask nothing once main asked `video.get`. Its rows
 *   go to V; its own 429 or bot check cools the host.
 * - **V3, the capture:** the visible tab's own playback, as the four sites' (the engine's next
 *   stage); [captureOff] when the config turns it off ([RungsOff]).
 *
 * A rung the config turns off ([rungsOff]: V1, V2, V3 — agent C's schema 2 names them) is not run,
 * its line says so: V1 off stages none of main's rows and no final answer (V2 still reads the
 * classes of main's answers for its trigger). Claims only a VK lookup with its own record
 * ([LookupRecord]); [contractsOf] gives the recipe source the engine reads with, around [http]
 * (never asked here). Lines: short labels, classes and counts, never a link, an ID, a value or the
 * page's words; nothing is logged.
 */
class VkLadder(
    private val http: ExtractorHttpClient,
    contractsOf: (ExtractorHttpClient) -> SiteContracts = ::SiteContracts,
    private val values: VkClientValues = VkClientValues.bundled(),
    private val cooldown: SocialCooldown = SocialCooldown(),
    private val budget: SocialBudget = VkSite.BUDGET,
    private val rungsOff: (SiteContracts) -> Set<String> = { it.rungsOff() },
) : SocialStage {
    private val contracts = contractsOf(http)

    override suspend fun claims(request: MasterRequest): Boolean =
        request.identity?.siteId == SITE_ID && coroutineContext[LookupRecord] != null

    override suspend fun stage(
        request: MasterRequest,
        policy: MasterPolicy,
        details: MutableList<String>,
    ): SocialStage.Staged? {
        val record = coroutineContext[LookupRecord] ?: return null
        // The signed config's ask when due (outside the lookup), so its rungs off apply here.
        contracts.refresh()
        // A host that answered a 429 or a bot check in this lookup (main's asks included) cools from
        // the stage's end, whatever it answers.
        return try {
            climb(record, request, policy, details)
        } finally {
            cooldown.note(record.stopHosts())
        }
    }

    /** V3, the visible tab's own playback, when the config turns it off. */
    override suspend fun captureOff(request: MasterRequest): String? =
        RungsOff.line(V3).takeIf { "V3" in rungsOff(contracts) }

    /** V1, then V2, for [request]'s lookup ([record]). */
    private suspend fun climb(
        record: LookupRecord,
        request: MasterRequest,
        policy: MasterPolicy,
        details: MutableList<String>,
    ): SocialStage.Staged? {
        val asked = request.identity?.takeIf { it.siteId == SITE_ID } ?: return null
        // As R8: a sign-in or bot-check failure is the user's own playback's; another video's page
        // is not read for.
        if (TerminalRules.needsAuthorizedPlayback(request.primaryFailure) ||
            request.expectedContentId?.let { it != asked.contentId } == true
        ) {
            return null
        }
        val off = rungsOff(contracts)
        details += "$LINE after ${request.primaryFailure.name}; main's answers re-read, no new ask"
        val link = VkClient.Link.of(asked)?.takeUnless { asked.requiresCanonicalResolution } ?: run {
            details += "$V1: no VK video in the link; nothing to read"
            return null
        }
        val read = request.copy(identity = asked, expectedContentId = asked.contentId)
        val main = mainAnswers(record, coroutineContext[MainPages], link)
        if (main.none) {
            details += "$V1: none kept for the video${MainPages.unasked(record)}; nothing to read"
            return null
        }
        val v1 = "V1" !in off
        // A host main's answer stopped (a 429, a bot check) cools whatever V1 reads.
        cooldown.note(main.stopped)
        if (v1) {
            main.lines.forEach(details::add)
            main.rows?.let { rows ->
                details += "$ITEM: ${rows.read.line()}"
                return staged(record, request, read, rows, MasterStage.PAGE_DATA, V1, details)
            }
            main.final?.let { reason -> return final(request, read, reason, MasterStage.PAGE_DATA) }
        } else {
            details += RungsOff.line(V1)
        }
        if (main.stopped.isNotEmpty() || !main.adult || !main.rejected) return null
        return heal(record, request, read, link, policy, off, details)
    }

    /** One of main's answers, judged, with what [VkReader] read in it. */
    private class Judgement(val judged: Judged, val read: VkReader.Read, val answer: ContractAnswer?, val host: String?)

    /** Main's answers for the link in this lookup: their lines, rows, a final answer, the stops, V2's trigger. */
    private class Main(
        val none: Boolean,
        val lines: List<String>,
        val rows: Judgement?,
        val final: SiteExtractionFailure?,
        val stopped: List<String>,
        val adult: Boolean,
        val rejected: Boolean,
    )

    /**
     * V1's read of main's answers for [link] (no request): the embed pages and `video.get`'s in
     * [pages] (their bodies, as kept; a refused GET by its status), main's token asks in [record]
     * (their status and mark: the answer is not read here) — the ask of the bundled values, or in a
     * record labelled with [VkLabels] any ask of the token's way ([VkLabels.TOKEN]), whose mark
     * (a token, VK's captcha, none) decides it.
     */
    private fun mainAnswers(record: LookupRecord, pages: MainPages?, link: VkClient.Link): Main {
        val kept = pages?.pages().orEmpty()
        val refused = pages?.refusals().orEmpty()
        val embeds = kept.filter { embedAsk(it.url, link) }.map { page ->
            val body = page.body
            val adult = body != null && VkAnswerJudge.isAdult(body)
            val judgement = if (body == null) {
                Judgement(Judged(AnswerClass.TRANSIENT, null, "not kept (over the bound)"), VkReader.Read(), null, null)
            } else {
                val read = VkReader.embed(body, link)
                val answer = ExtractorHttpResult.Success(page.statusCode, body, page.finalUrl, page.contentType)
                Judgement(VkAnswerJudge.embed(answer, read.reading), read, read.answer(link, page.finalUrl), hostOf(page.url))
            }
            adult to judgement
        } + refused.filter { embedAsk(it.url, link) }.map { refusal ->
            false to Judgement(
                VkAnswerJudge.embed(ExtractorHttpResult.Failure(refusal.reason, refusal.statusCode)),
                VkReader.Read(),
                null,
                hostOf(refusal.url),
            )
        }
        val apis = kept.filter { apiAsk(it.url, link) }.map { page ->
            val body = page.body
            if (body == null) {
                Judgement(Judged(AnswerClass.TRANSIENT, null, "not kept (over the bound)"), VkReader.Read(), null, null)
            } else {
                val read = VkReader.api(body, link)
                val answer = ExtractorHttpResult.Success(page.statusCode, body, page.finalUrl, page.contentType)
                Judgement(VkAnswerJudge.api(answer, read.reading), read, read.answer(link, page.finalUrl), VkClient.API_HOST)
            }
        } + refused.filter { apiAsk(it.url, link) }.map { refusal ->
            Judgement(
                VkAnswerJudge.api(ExtractorHttpResult.Failure(refusal.reason, refusal.statusCode)),
                VkReader.Read(),
                null,
                VkClient.API_HOST,
            )
        }
        // Main's token asks: the bundled values' ask, or (a labelled record) any ask VK's labels
        // name the token's way, so main's other values are read too.
        val tokenHash = LookupAsk(LookupAsk.POST, VkClient.TOKEN_URL, VkClient.BUNDLED.tokenBody).hash
        val tokens = record.asks().filter {
            it.asker == LookupRecord.Asker.MAIN && it.method == LookupAsk.POST &&
                (it.hash == tokenHash || it.way == VkLabels.TOKEN)
        }
        val token = tokens.lastOrNull()?.let { tokenJudged(it, apis.isNotEmpty()) }
        // The deciding answer of each kind: rows first, then (video.get) VK's final answer, then a
        // stop (a 429 or a bot check on either host stops the site), then (embed) the adult answer.
        val embed = embeds.firstOrNull { it.second.judged.next == Next.CHECK_ROWS }
            ?: embeds.firstOrNull { it.second.judged.next == Next.STOP_SITE }
            ?: embeds.firstOrNull { it.first }
            ?: embeds.lastOrNull()
        val api = apis.firstOrNull { it.judged.next == Next.CHECK_ROWS }
            ?: apis.firstOrNull { it.judged.next == Next.FINAL }
            ?: apis.firstOrNull { it.judged.next == Next.STOP_SITE }
            ?: apis.lastOrNull()
        val lines = buildList {
            embed?.let { add("$V1 embed: ${line(it.second.judged)}" + count(embeds.size)) }
            token?.let { add("$V1 login.vk.ru: ${line(it)}" + count(tokens.size)) }
            api?.let { add("$V1 video.get: ${line(it.judged)}" + count(apis.size)) }
        }
        val judged = listOfNotNull(embed?.second?.judged, token, api?.judged)
        val stopped = buildList {
            embed?.second?.takeIf { it.judged.next == Next.STOP_SITE }?.host?.let(::add)
            if (token?.next == Next.STOP_SITE) add(VkClient.TOKEN_HOST)
            if (api?.judged?.next == Next.STOP_SITE) add(VkClient.API_HOST)
        }
        return Main(
            none = embeds.isEmpty() && apis.isEmpty() && tokens.isEmpty(),
            lines = lines,
            rows = api?.takeIf { it.judged.next == Next.CHECK_ROWS && it.answer != null }
                ?: embed?.second?.takeIf { it.judged.next == Next.CHECK_ROWS && it.answer != null },
            final = api?.judged?.takeIf { it.next == Next.FINAL }?.reason,
            stopped = stopped,
            // Main's path D opens only after the embed's adult answer: its token or video.get is proof.
            adult = embeds.any { it.first } || tokens.isNotEmpty() || apis.isNotEmpty(),
            rejected = judged.any { it.answerClass == AnswerClass.VALUE_REJECTED },
        )
    }

    /**
     * Main's token ask [ask] by its mark (VK's labels read the answer: a usable token, a captcha,
     * none), else its status; a 2xx answer without a mark is usable when main went on to
     * `video.get` ([apiAsked]), else unknown. Null: a usable token.
     */
    private fun tokenJudged(ask: LookupRecord.Ask, apiAsked: Boolean): Judged? = when {
        ask.mark == AnswerMark.DATA -> null
        ask.mark == AnswerMark.BOT_CHECK -> Judged(AnswerClass.BOT_CHECK, AnswerClass.BOT_CHECK.reason, "a captcha, by the answer's mark")
        ask.mark == AnswerMark.NOT_JSON -> Judged(AnswerClass.SHAPE_CHANGED, AnswerClass.SHAPE_CHANGED.reason, "not JSON, by the answer's mark")
        ask.mark != null -> Judged(
            AnswerClass.VALUE_REJECTED,
            AnswerClass.VALUE_REJECTED.reason,
            "no value for the web client, by the answer's mark",
        )
        !ask.answered -> Judged(AnswerClass.TRANSIENT, null, "no answer in time")
        ask.failure != null -> VkAnswerJudge.token(ExtractorHttpResult.Failure(checkNotNull(ask.failure), ask.statusCode), usable = false)
        apiAsked -> null
        else -> Judged(AnswerClass.TRANSIENT, null, "status ${ask.statusCode}, the answer not read here")
    }

    /**
     * V2: one token and one `video.get` with the first of [values]' values that is not main's, for
     * [link]'s video; its rows staged for V, VK's deleted icon final, anything else null.
     */
    private suspend fun heal(
        record: LookupRecord,
        request: MasterRequest,
        read: MasterRequest,
        link: VkClient.Link,
        policy: MasterPolicy,
        off: Set<String>,
        details: MutableList<String>,
    ): SocialStage.Staged? {
        if ("V2" in off) {
            details += RungsOff.line(V2)
            return null
        }
        val value = valuesOrNone().firstOrNull { !it.sameAs(VkClient.BUNDLED) } ?: run {
            details += "$V2: no value other than main's; not asked"
            return null
        }
        stopped(record)?.let { why ->
            details += "$V2: $why; not asked"
            return null
        }
        val agent = request.requestContext.userAgent
        val url = value.videoGetUrl(link)
        // The record's ask is the address without the bearer: the token is Master's own anonymous
        // one (VK's web client's, no user's), never the user's credential; the address and the
        // lookup's user agent are the ask. An endpoint asked in the lookup is not asked again
        // (route B's rule 4): when main asked this `video.get` (only the token's values differ),
        // no token is asked for it either.
        val apiAsk = LookupAsk(LookupAsk.GET, url, null, VkClient.headers(agent, null))
        if (record.asked(apiAsk)) {
            details += "$V2: video.get with these values asked in this lookup already; not asked"
            return null
        }
        val label = "${value.source.label} values of ${value.date}"
        val route = RouteBLookup(record)
        val tokenHeaders = VkClient.headers(agent, null)
        val tokenAsk = LookupAsk(LookupAsk.POST, VkClient.TOKEN_URL, value.tokenBody, tokenHeaders)
        val tokenAnswer = timed(record, policy, "$V2 login.vk.ru", details) {
            route.exchange(tokenAsk, VkClient.MAX_TOKEN_BYTES) {
                http.postJson(VkClient.TOKEN_URL, value.tokenBody, tokenHeaders, VkClient.MAX_TOKEN_BYTES)
            }
        } ?: return null
        val token = (tokenAnswer as? ExtractorHttpResult.Success)?.let { VkClient.token(it.body, request.nowEpochMs) }
        VkAnswerJudge.token(tokenAnswer, usable = token != null)?.let { judged ->
            details += "$V2 login.vk.ru ($label): ${line(judged)}" + refusedBy(route)
            if (route.newRequests > 0 && judged.next == Next.STOP_SITE) cooldown.note(listOf(VkClient.TOKEN_HOST))
            return null
        }
        details += "$V2 login.vk.ru ($label): answered, held in memory for this ask only" +
            if (route.replayed > 0) "; main's identical answer replayed" else ""
        val apiAnswer = timed(record, policy, "$V2 video.get", details) {
            route.exchange(apiAsk, VkClient.MAX_API_BYTES) {
                http.get(url, VkClient.headers(agent, checkNotNull(token)), VkClient.MAX_API_BYTES)
            }
        } ?: return null
        val reading = (apiAnswer as? ExtractorHttpResult.Success)?.let { VkReader.api(it.body, link) } ?: VkReader.Read()
        val judged = VkAnswerJudge.api(apiAnswer, reading.reading)
        details += "$V2 video.get: ${line(judged)}" + refusedBy(route)
        if (route.newRequests > 0 && judged.next == Next.STOP_SITE) cooldown.note(listOf(VkClient.API_HOST))
        return when (judged.next) {
            Next.CHECK_ROWS -> {
                val answer = (apiAnswer as? ExtractorHttpResult.Success)?.let { reading.answer(link, it.finalUrl) }
                    ?: return null
                details += "$V2 item: ${reading.line()}"
                staged(record, request, read, Judgement(judged, reading, answer, VkClient.API_HOST), MasterStage.CONTRACT, V2, details)
            }
            Next.FINAL -> judged.reason?.let { final(request, read, it, MasterStage.CONTRACT) }
            Next.NEXT_RUNG, Next.PLAYBACK, Next.BROWSER, Next.STOP_SITE -> null
        }
    }

    /** [values]' values; none when the source fails (its failure is not the lookup's). */
    private suspend fun valuesOrNone(): List<VkClient.Values> = try {
        values.values()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * Why V2 may not ask in [record]'s lookup, or null: a 429 in the lookup, a VK host that stopped in
     * it or still cools, or VK's request, time or byte budget too small for its two asks.
     */
    private fun stopped(record: LookupRecord): String? {
        val hosts = listOf(VkClient.TOKEN_HOST, VkClient.API_HOST)
        val stops = record.stopHosts()
        return when {
            record.rateLimited -> "a 429 in this lookup"
            hosts.any(stops::contains) -> "a VK host stopped in this lookup (a 429 or a bot check)"
            hosts.any(cooldown::cooling) -> "cooling down after a 429 or a bot check"
            record.mainAsks + record.masterAsks + HEAL_ASKS > budget.requests -> "over the lookup's request budget"
            record.timeLeftMillis(budget.millis) < 2 * VkClient.MIN_REQUEST_MS -> "over the lookup's time budget"
            budget.bytes < VkClient.MAX_TOKEN_BYTES + VkClient.MAX_API_BYTES -> "over the lookup's byte budget"
            else -> null
        }
    }

    /**
     * One of V2's asks ([ask]) within the lookup's time: at most [VkClient.REQUEST_MS], the
     * policy's and what is left of VK's budget; under [VkClient.MIN_REQUEST_MS] left, not asked.
     * Null (its line written): not asked, or no answer in time.
     */
    private suspend fun timed(
        record: LookupRecord,
        policy: MasterPolicy,
        label: String,
        details: MutableList<String>,
        ask: suspend () -> ExtractorHttpResult,
    ): ExtractorHttpResult? {
        val wait = minOf(
            VkClient.REQUEST_MS,
            policy.contractTimeoutMillis,
            policy.timeoutMillis,
            record.timeLeftMillis(budget.millis),
        )
        if (wait < VkClient.MIN_REQUEST_MS) {
            details += "$label: out of the lookup's time; not asked"
            return null
        }
        return withTimeoutOrNull(wait) { ask() } ?: run {
            details += "$label: no answer in time"
            null
        }
    }

    /** [judgement]'s rows for V, read for [read], while VK's budget holds. */
    private fun staged(
        record: LookupRecord,
        request: MasterRequest,
        read: MasterRequest,
        judgement: Judgement,
        stage: MasterStage,
        rung: String,
        details: MutableList<String>,
    ): SocialStage.Staged? {
        val answer = judgement.answer ?: return null
        if (contracts.recipeOf(SITE_ID) == null) {
            details += "$rung: VK's recipe not in this build's recipes; not checked"
            return null
        }
        over(record, answer.body.length.toLong())?.let { why ->
            details += "$rung: $why; not checked"
            return null
        }
        return SocialStage.Staged(stage, PageSnapshot(request.pageUrl, request.generation, contract = answer), read)
    }

    /**
     * Why rows may not go to V in [record]'s lookup, or null: main's and Master's asks so far, the
     * lookup's time or the answer's [chars] over VK's budget.
     */
    private fun over(record: LookupRecord, chars: Long): String? = when {
        record.mainAsks + record.masterAsks >= budget.requests -> "over the lookup's request budget"
        record.elapsedMillis() >= budget.millis -> "over the lookup's time budget"
        chars > budget.bytes -> "over the lookup's byte budget"
        else -> null
    }

    /** A final answer [reason] for [request]'s page, read for [read] (no further stage). */
    private fun final(request: MasterRequest, read: MasterRequest, reason: SiteExtractionFailure, stage: MasterStage) =
        SocialStage.Staged(stage, PageSnapshot(request.pageUrl, request.generation, accessFailure = reason), read)

    override fun toString(): String = "VkLadder"

    companion object {
        /** Main's VK adapter's site ID (the video key's prefix). */
        const val SITE_ID = VkClient.SITE_ID

        internal const val LINE = "master: vk ladder"
        internal const val V1 = "master: vk V1 main's answers"
        internal const val V2 = "master: vk V2 heal"

        /** V3, the visible tab's own playback (its line only when the config turns it off). */
        internal const val V3 = "master: vk V3"
        private const val ITEM = "master: vk V1 item"

        /** V2's asks: one token, one `video.get`. */
        private const val HEAL_ASKS = 2

        private fun line(judged: Judged): String =
            "${judged.answerClass.name} (${judged.evidence}); next: ${judged.next.name}"

        private fun count(answers: Int): String = if (answers > 1) "; $answers answers, the deciding one shown" else ""

        private fun refusedBy(route: RouteBLookup): String =
            if (route.refused > 0) "; refused by the lookup's rules (asked already, or after a 429)" else ""

        private fun hostOf(url: String): String? = VkClient.httpsHostOf(url)

        /** Whether [url] is main's embed ask for [link]'s video (`/video_ext.php?oid=…&id=…` on vk.com or vk.ru). */
        private fun embedAsk(url: String, link: VkClient.Link): Boolean {
            val host = VkClient.httpsHostOf(url) ?: return false
            if (host != VkClient.COM_HOST && host != VkClient.RU_HOST) return false
            val query = queryOf(url, VkClient.EMBED_PATH) ?: return false
            return query["oid"] == link.ownerId.toString() && query["id"] == link.videoId.toString()
        }

        /** Whether [url] is a `video.get` ask for [link]'s video on api.vkvideo.ru (any values). */
        private fun apiAsk(url: String, link: VkClient.Link): Boolean {
            if (VkClient.httpsHostOf(url) != VkClient.API_HOST) return false
            val query = queryOf(url, VkClient.VIDEO_GET_PATH) ?: return false
            val videos = query["videos"] ?: return false
            return query["owner_id"] == link.ownerId.toString() &&
                (videos == link.contentId || videos.startsWith("${link.contentId}_"))
        }

        /** [url]'s query values (decoded; a repeated name: its last) when its path is [path], else null. */
        internal fun queryOf(url: String, path: String): Map<String, String>? {
            val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
            if (uri.rawPath != path) return null
            return uri.rawQuery.orEmpty().split('&').filter(String::isNotEmpty).associate { entry ->
                val value = runCatching {
                    URLDecoder.decode(entry.substringAfter('=', ""), StandardCharsets.UTF_8)
                }.getOrDefault("")
                entry.substringBefore('=') to value
            }
        }
    }
}
