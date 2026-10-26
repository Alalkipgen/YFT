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
import kotlinx.coroutines.withContext
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
 *   VK's recipe; VK's deleted icon on a video VK does not play, or VK's region limit (API error
 *   13300), is the lookup's final answer; a 429 or a bot check stops the site (its host cools);
 *   anything else leaves nothing to read.
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
 *   go to V; its own 429 or bot check cools the host. With [VkClientValues.healing] (agent D's
 *   wiring, the app's): the value VK refused main is healed instead ([Heal]: healed today, learned,
 *   else one read of vkvideo.ru's page and its common script; on error 8 the API version too, for
 *   a second `video.get`), kept only after V passed the rows.
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
        RungsOff.line(V3).takeIf { RUNG_V3 in rungsOff(contracts) }

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
        val v1 = RUNG_V1 !in off
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
        // D: the value VK refused main — the API version on video.get's error 8, else the client id.
        val first = if (main.versionRefused) VkClientSource.API_VERSION else VkClientSource.CLIENT_ID
        return heal(record, request, read, link, policy, off, first, details)
    }

    /** One of main's answers, judged, with what [VkReader] read in it ([code]: `video.get`'s API error code). */
    private class Judgement(
        val judged: Judged,
        val read: VkReader.Read,
        val answer: ContractAnswer?,
        val host: String?,
        val code: Long? = null,
    )

    /** Main's answers for the link in this lookup: their lines, rows, a final answer, the stops, V2's trigger. */
    private class Main(
        val none: Boolean,
        val lines: List<String>,
        val rows: Judgement?,
        val final: SiteExtractionFailure?,
        val stopped: List<String>,
        val adult: Boolean,
        val rejected: Boolean,
        /** D: main's refused value was the API version (`video.get`'s error 8 after a usable token). */
        val versionRefused: Boolean = false,
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
                Judgement(
                    VkAnswerJudge.api(answer, read.reading),
                    read,
                    read.answer(link, page.finalUrl),
                    VkClient.API_HOST,
                    VkAnswerJudge.apiErrorCode(answer),
                )
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
            versionRefused = token?.answerClass != AnswerClass.VALUE_REJECTED &&
                api?.judged?.answerClass == AnswerClass.VALUE_REJECTED && api?.code == VkAnswerJudge.VERSION_ERROR,
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
     * [link]'s video; its rows staged for V, VK's final answer (the deleted icon, the region limit)
     * final, anything else null.
     */
    private suspend fun heal(
        record: LookupRecord,
        request: MasterRequest,
        read: MasterRequest,
        link: VkClient.Link,
        policy: MasterPolicy,
        off: Set<String>,
        first: ValueKind,
        details: MutableList<String>,
    ): SocialStage.Staged? {
        if (RUNG_V2 in off) {
            details += RungsOff.line(V2)
            return null
        }
        // D's wiring: the value VK refused, healed ([VkClientValues.healing]).
        (values as? HealingVkValues)?.let { healing -> return Heal(healing, record, request, read, link, policy, details).run(first) }
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

    /**
     * D's wiring (VK-Master plan §8, items 3 and 4): one V2 pass with the heal, for one lookup. The
     * value VK refused main ([run]'s first: the client id, else the API version — never the client
     * secret) is marked rejected for the day, and the next comes from [healing]'s source: healed
     * today, learned, else one read of vkvideo.ru's page and its common script in this [pass] (by
     * the lookup's rules, counted for the line), at most once a day per value. Then one token and
     * one `video.get`; on error 8 the API version comes from the same script (no new read) for a
     * second `video.get`. Each ask only while VK's budget holds — the requests with V's reads kept
     * for the rows, the bytes at each ask's bound, the lookup's time — else V2 stops and V3
     * answers. Rows go to V; a healed or learned value is kept only after V passed them
     * ([SocialStage.Staged.checked]); one VK refuses again is dropped for the day. Lines name the
     * value (client_id, v) and where it came from, never the value.
     */
    private inner class Heal(
        private val healing: HealingVkValues,
        private val record: LookupRecord,
        private val request: MasterRequest,
        private val read: MasterRequest,
        private val link: VkClient.Link,
        private val policy: MasterPolicy,
        private val details: MutableList<String>,
    ) {
        private val pass = VkClientSource.Pass(RouteBLookup(record))
        private val route = pass.route
        private val agent = request.requestContext.userAgent
        private val source = healing.source

        /** The values this pass sent that are not main's (kept after V's pass). */
        private val used = LinkedHashMap<String, ValueSource.Candidate>()

        /** The bytes this pass reserved, each ask at its bound. */
        private var bytes = 0L

        /** One answer of V2's `video.get`. */
        private inner class Asked(val answer: ExtractorHttpResult, val reading: VkReader.Read, val judged: Judged) {
            val versionRefused: Boolean
                get() = judged.answerClass == AnswerClass.VALUE_REJECTED &&
                    VkAnswerJudge.apiErrorCode(answer) == VkAnswerJudge.VERSION_ERROR
        }

        suspend fun run(first: ValueKind): SocialStage.Staged? {
            stop()?.let { why ->
                details += "$V2: $why; not asked"
                return null
            }
            var values = next(first, healing.main, HEAL_ASKS, VkClient.MAX_TOKEN_BYTES + VkClient.MAX_API_BYTES) ?: return null
            val token = token(values) ?: return null
            var asked = videoGet(values, token) ?: return null
            if (asked.versionRefused) {
                val version = VkClientSource.API_VERSION
                if (version.name in used) {
                    // The API version this pass sent is refused too: not sent again today.
                    used[version.name]?.let(source::rejected)
                    return null
                }
                values = next(version, values, 1, VkClient.MAX_API_BYTES) ?: return null
                asked = videoGet(values, token) ?: return null
                if (asked.versionRefused) {
                    used[version.name]?.let(source::rejected)
                    return null
                }
            }
            return answer(asked)
        }

        /**
         * [kind]'s value in [values] was refused: not sent again today; [values] with the next one
         * (its line written), or null (its line says why). [then]/[thenBytes]: the asks (and their
         * bounds) that must still follow a heal read for it to be of use.
         */
        private suspend fun next(kind: ValueKind, values: VkClient.Values, then: Int, thenBytes: Long): VkClient.Values? {
            val name = nameOf(kind)
            val refused = valueOf(values, kind)
            source.rejected(used[kind.name] ?: ValueSource.Candidate(kind, refused, ValueSource.Origin.BUNDLED, ValueSource.BUNDLED))
            val candidate = source.value(kind, refused)?.also { got ->
                details += "$V2 $name: ${known(got)}; no new ask"
            } ?: healRead(kind, then, thenBytes) ?: source.alternates(kind, refused).firstOrNull()?.also { got ->
                details += "$V2 $name: ${got.label}; no new ask"
            } ?: return null
            val next = withValue(values, kind, candidate.value) ?: run {
                details += "$V2 $name: not VK's shape; not asked"
                return null
            }
            used[kind.name] = candidate
            return next
        }

        /** One heal read of [kind] in this pass (the script read once for both values), its line written. */
        private suspend fun healRead(kind: ValueKind, then: Int, thenBytes: Long): ValueSource.Candidate? {
            val name = nameOf(kind)
            val cached = pass.read
            if (!cached) {
                over(VkClientSource.REQUESTS_PER_READ + then, READ_BYTES + thenBytes)?.let { why ->
                    details += "$V2 $name: $why; not read"
                    return null
                }
            }
            val wait = minOf(HEAL_READ_MS, policy.timeoutMillis, record.timeLeftMillis(budget.millis))
            if (!cached && wait < VkClientSource.REQUESTS_PER_READ * VkClient.MIN_REQUEST_MS) {
                details += "$V2 $name: out of the lookup's time; not read"
                return null
            }
            if (!cached) bytes += READ_BYTES
            val asks = pass.asks
            val got = pass.bytes
            val healed = withTimeoutOrNull(wait.coerceAtLeast(VkClient.MIN_REQUEST_MS)) {
                withContext(pass) { source.heal(kind, agent) }
            } ?: run {
                // The read was cut by the lookup's time: ValueSource gave the day's read back (≤ 3 a day).
                val back = if (source.mayRead(kind)) "; the day's read given back" else ""
                details += "$V2 $name: no answer in time$back"
                return null
            }
            val candidate = healed.candidate ?: run {
                details += "$V2 $name: no value (${healed.why})"
                return null
            }
            details += "$V2 $name: healed from ${candidate.label} " + when {
                pass.asks > asks -> "(the page and its common script: ${pass.asks - asks} asks, ${pass.bytes - got} bytes)"
                cached -> "(the same script; no new ask)"
                else -> "(${healed.why}; no new ask)"
            }
            return candidate
        }

        /** One token for [values] (held in memory for this pass only), or null (its line written). */
        private suspend fun token(values: VkClient.Values): VkClient.Token? {
            over(HEAL_ASKS, VkClient.MAX_TOKEN_BYTES + VkClient.MAX_API_BYTES)?.let { why ->
                details += "$V2 login.vk.ru: $why; not asked"
                return null
            }
            bytes += VkClient.MAX_TOKEN_BYTES
            val headers = VkClient.headers(agent, null)
            val ask = LookupAsk(LookupAsk.POST, VkClient.TOKEN_URL, values.tokenBody, headers)
            val sent = route.newRequests
            val replayed = route.replayed
            val answer = timed(record, policy, "$V2 login.vk.ru", details) {
                route.exchange(ask, VkClient.MAX_TOKEN_BYTES) {
                    http.postJson(VkClient.TOKEN_URL, values.tokenBody, headers, VkClient.MAX_TOKEN_BYTES)
                }
            } ?: return null
            val token = (answer as? ExtractorHttpResult.Success)?.let { VkClient.token(it.body, request.nowEpochMs) }
            VkAnswerJudge.token(answer, usable = token != null)?.let { judged ->
                details += "$V2 login.vk.ru: ${line(judged)}" + refusedBy(route)
                if (route.newRequests > sent && judged.next == Next.STOP_SITE) cooldown.note(listOf(VkClient.TOKEN_HOST))
                // The client id this pass sent is refused too: not sent again today.
                if (judged.answerClass == AnswerClass.VALUE_REJECTED) used[VkClientSource.CLIENT_ID.name]?.let(source::rejected)
                return null
            }
            details += "$V2 login.vk.ru: answered, held in memory for this ask only" +
                if (route.replayed > replayed) "; main's identical answer replayed" else ""
            return token
        }

        /** One `video.get` with [values] and [token], or null (its line written). */
        private suspend fun videoGet(values: VkClient.Values, token: VkClient.Token): Asked? {
            over(1, VkClient.MAX_API_BYTES)?.let { why ->
                details += "$V2 video.get: $why; not asked"
                return null
            }
            val url = values.videoGetUrl(link)
            val ask = LookupAsk(LookupAsk.GET, url, null, VkClient.headers(agent, null))
            if (record.asked(ask)) {
                details += "$V2: video.get with these values asked in this lookup already; not asked"
                return null
            }
            bytes += VkClient.MAX_API_BYTES
            val sent = route.newRequests
            val answer = timed(record, policy, "$V2 video.get", details) {
                route.exchange(ask, VkClient.MAX_API_BYTES) {
                    http.get(url, VkClient.headers(agent, token), VkClient.MAX_API_BYTES)
                }
            } ?: return null
            val reading = (answer as? ExtractorHttpResult.Success)?.let { VkReader.api(it.body, link) } ?: VkReader.Read()
            val judged = VkAnswerJudge.api(answer, reading.reading)
            details += "$V2 video.get: ${line(judged)}" + refusedBy(route)
            if (route.newRequests > sent && judged.next == Next.STOP_SITE) cooldown.note(listOf(VkClient.API_HOST))
            return Asked(answer, reading, judged)
        }

        /** [asked]'s rows for V (the values kept only after V passed them), VK's final answer, else null. */
        private fun answer(asked: Asked): SocialStage.Staged? = when (asked.judged.next) {
            Next.CHECK_ROWS -> (asked.answer as? ExtractorHttpResult.Success)?.let { asked.reading.answer(link, it.finalUrl) }
                ?.let { answer ->
                    details += "$V2 item: ${asked.reading.line()}"
                    val judgement = Judgement(asked.judged, asked.reading, answer, VkClient.API_HOST)
                    staged(record, request, read, judgement, MasterStage.CONTRACT, V2, details)?.let { staged ->
                        val kept = used.values.toList()
                        val answerClass = asked.judged.answerClass
                        if (kept.isEmpty()) {
                            staged
                        } else {
                            SocialStage.Staged(staged.stage, staged.snapshot, staged.request, staged.last) { passed ->
                                kept.forEach { source.kept(it, answerClass, passed) }
                            }
                        }
                    }
                }
            Next.FINAL -> asked.judged.reason?.let { final(request, read, it, MasterStage.CONTRACT) }
            Next.NEXT_RUNG, Next.PLAYBACK, Next.BROWSER, Next.STOP_SITE -> null
        }

        /** Why this pass may not ask at all: a 429 in the lookup, a VK host stopped or cooling, VK's budget. */
        private fun stop(): String? {
            val hosts = listOf(VkClient.TOKEN_HOST, VkClient.API_HOST)
            val stops = record.stopHosts()
            return when {
                record.rateLimited -> "a 429 in this lookup"
                hosts.any(stops::contains) -> "a VK host stopped in this lookup (a 429 or a bot check)"
                hosts.any(cooldown::cooling) -> "cooling down after a 429 or a bot check"
                else -> over(HEAL_ASKS, VkClient.MAX_TOKEN_BYTES + VkClient.MAX_API_BYTES)
            }
        }

        /**
         * Why [asks] more (their bounds [need]) do not fit VK's budget now, or null: main's and
         * Master's asks so far with V's reads kept for the rows, the lookup's time, the bytes this
         * pass reserved.
         */
        private fun over(asks: Int, need: Long): String? = when {
            record.mainAsks + record.masterAsks + asks + SocialRowCheck.MAX_READS > budget.requests ->
                "over the lookup's request budget"
            record.timeLeftMillis(budget.millis) < asks * VkClient.MIN_REQUEST_MS -> "over the lookup's time budget"
            bytes + need > budget.bytes -> "over the lookup's byte budget"
            else -> null
        }

        /** A value [got] the source had without a read: healed today, learned, signed. */
        private fun known(got: ValueSource.Candidate): String = when (got.origin) {
            ValueSource.Origin.HEALED -> "healed today from ${got.label}"
            ValueSource.Origin.LEARNED -> "learned from ${got.source}"
            else -> got.label
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
        /** Main's VK adapter's site ID (the video key's prefix; agent C's schema 2 names VK `vk`). */
        const val SITE_ID = VkClient.SITE_ID

        /** The rungs' names in the signed config (agent C's schema 2: `V1`, `V2`, `V3`; [RungsOff]). */
        const val RUNG_V1 = "V1"
        const val RUNG_V2 = "V2"
        const val RUNG_V3 = "V3"
        val RUNGS: List<String> = listOf(RUNG_V1, RUNG_V2, RUNG_V3)

        internal const val LINE = "master: vk ladder"
        internal const val V1 = "master: vk V1 main's answers"
        internal const val V2 = "master: vk V2 heal"

        /** V3, the visible tab's own playback (its line only when the config turns it off). */
        internal const val V3 = "master: vk V3"
        private const val ITEM = "master: vk V1 item"

        /** V2's asks: one token, one `video.get`. */
        private const val HEAL_ASKS = 2

        /** D: a heal read's bounds, the page and its common script. */
        private const val READ_BYTES = VkClientSource.PAGE_MAX_BYTES + VkClientSource.COMMON_MAX_BYTES

        /** D: a heal read's most time, its two GETs at main's per-ask bound. */
        private const val HEAL_READ_MS = VkClientSource.REQUESTS_PER_READ * VkClient.REQUEST_MS

        /** D: a value's name in V2's lines, VK's own parameter names (never the value). */
        private fun nameOf(kind: ValueKind): String = if (kind === VkClientSource.API_VERSION) "v" else "client_id"

        /** D: [kind]'s value in [values]. */
        private fun valueOf(values: VkClient.Values, kind: ValueKind): String =
            if (kind === VkClientSource.API_VERSION) values.apiVersion else values.clientId

        /** D: [values] with [kind]'s [value] (main's source and day kept), or null when it is not VK's shape. */
        private fun withValue(values: VkClient.Values, kind: ValueKind, value: String): VkClient.Values? = try {
            if (kind === VkClientSource.API_VERSION) values.copy(apiVersion = value) else values.copy(clientId = value)
        } catch (_: IllegalArgumentException) {
            null
        }

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
