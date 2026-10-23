/*
 * Provenance (social plan S2d part 1, copied, not moved; main 34a41890; listed in toolkit-provenance.tsv):
 *   extractor-sites/.../instagram/InstagramUrls.kt  mediaIdOf, ALPHABET, MEDIA_ID_CHARS (the
 *                                        post's numeric media ID: its code read as base 64),
 *                                        embedUrl (main's embed ask's address)
 *   extractor-sites/.../instagram/InstagramExtractor.kt  ask (main's four asks' addresses, in
 *                                        main's order: the app API signed in, GraphQL, the post's
 *                                        page, the embed page), graphQlUrl (its variables name the
 *                                        post's code)
 * The tab-fetch rung's query is data from yt-dlp ([InstagramProbeQuery]: S9, no row).
 */
package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asBooleanOrNull
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.api.json.path
import com.alal.yft.extractor.master.ContractAnswer
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.MasterStage
import com.alal.yft.extractor.master.PageSnapshot
import com.alal.yft.extractor.master.contract.SiteContracts
import com.alal.yft.extractor.master.layers.ContractReader
import com.alal.yft.extractor.master.lookup.LookupAsk
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.recipes.ContractRecipe
import com.alal.yft.extractor.master.recipes.PayloadRecipes
import com.alal.yft.extractor.master.toolkit.BalancedJson
import com.alal.yft.extractor.master.toolkit.PageScripts
import java.math.BigInteger
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Social plan S2d part 1 (`MASTER_KEY_SOCIAL_PLAN.md` §6.4, §7.3, decision 19): Instagram's ladder
 * after main failed, in place of R8's unjudged ask (no repeat of main's embed ask), inside the
 * same rules as X's ([XLadder]). **I2, main's answers judged:** the answers main's adapter got in
 * this lookup ([MainPages]: the app API when signed in, GraphQL, the post's page, the embed page;
 * each way's last page, else its last refused GET), each read with no new ask by Instagram's
 * recipe and judged ([InstagramAnswerJudge]), then one answer for the lookup (main's order).
 *
 * - **Rows, the link's item only** (decision 11): the post's own node (its code), its item the
 *   link names (`img_index`), else its first item with files, as main; bound to the post and
 *   checked by V and the init check (S6) as main's answer for that item. A renamed key: the
 *   recipe's open read, anchored to the post, only for a post with one item.
 * - **Final:** a photo post by the post's own marks (`NO_MEDIA_FOUND`, main's words), only when
 *   no answer shows a video.
 * - **Nothing to read** (null: the visible tab's own playback, then the user's browser; main's
 *   reason stays): a login wall (the checkpoint too: main's rule and §6.3's), a rate limit (the
 *   host cools, S7), no post in an answer, the embed's `EmbedIsBroken`, a changed shape. Never
 *   REMOVED (decision 19 (vi): no answer read tells a removed post apart).
 *
 * **The tab-fetch rung** (S2d part 2, decision 24, task 13; its switch off by default): with an
 * [InstagramTabProbe] in the lookup's context and its switch on, after I2 gave no rows (not after
 * a final answer or a stop), once, the last request rung: the visible tab's own fetch of yt-dlp's
 * signed-out query ([InstagramProbeQuery]), recorded in the lookup's record (Master's ask: the
 * budget, a 429 stops the site and cools the host, a checkpoint cools it: signed out, Instagram's
 * challenge of Master's own ask is a bot check for S7; no second ask), read by the same recipe and
 * judge as I2 (rows bound to the post by its code or its pk, the link's item only; its 400 or
 * `errors` is VALUE_REJECTED: no other value is tried) and said in one line (its status and size,
 * whether its answer holds the post's video, whether the tab shows a wall, its rows). Its rows go
 * to V and the init check (S6) as the tab's own answer; a page with a wall is never read. Signed
 * in (main's session), it is not asked: it is the signed-out query. With I2's rows it is not asked
 * either, but when its debug proof is on ([InstagramTabProbe.proof]: I2's rows set aside). In a
 * census or W there is no tab: it is never asked there (as I3).
 *
 * S4: a rung the signed recipe config turns off ([RungsOff]: I2, the tab-fetch rung `probe`, I3)
 * is not run, its line says so: I2 off, the tab-fetch rung next; the rung off wins over its
 * switch (its line wherever a probe is in the lookup's context). The config is asked for (when
 * due, on its own) as the stage starts.
 *
 * S7: Instagram's own budget ([SocialBudget.INSTAGRAM]). Main's Instagram code and reasons stay
 * (decision 10). Claims only an Instagram lookup with its own record ([LookupRecord]: the
 * social-ladder build's, `-Pyft.socialLadder`); every other lookup keeps R8's path. [contractsOf]
 * gives the recipe the engine reads with, around [http] (never asked here). Lines: short labels
 * and counts, never a link, an ID or the post's words; nothing is logged.
 */
class InstagramLadder(
    http: ExtractorHttpClient,
    contractsOf: (ExtractorHttpClient) -> SiteContracts = ::SiteContracts,
    private val cooldown: SocialCooldown = SocialCooldown(),
    private val budget: SocialBudget = SocialBudget.INSTAGRAM,
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
        // S4: the signed config's ask when due (outside the lookup), so its rungs off apply here.
        contracts.refresh()
        // S7: a host that answered a 429 (main's or the rung's) cools from the stage's end.
        return try {
            climb(record, request, policy, details)
        } finally {
            cooldown.note(record.stopHosts())
        }
    }

    /** S4: I3, the visible tab's own playback, when the signed config turns it off. */
    override suspend fun captureOff(request: MasterRequest): String? =
        RungsOff.line(I3).takeIf { "I3" in contracts.rungsOff() }

    /** S4: whether the signed config turns the tab-fetch rung off (over its switch). */
    private fun probeOff(): Boolean = PROBE_RUNG in contracts.rungsOff()

    /** S4: the tab-fetch rung's line when a probe is in the lookup's context: off by the config, else [line]. */
    private fun probeLine(line: String): String = if (probeOff()) RungsOff.line(PROBE) else line

    /** I2 for [request]'s lookup ([record]), then the tab-fetch rung when I2 gave no rows. */
    private suspend fun climb(
        record: LookupRecord,
        request: MasterRequest,
        policy: MasterPolicy,
        details: MutableList<String>,
    ): SocialStage.Staged? {
        val identity = request.identity?.takeIf { it.siteId == SITE_ID } ?: return null
        // A bot check or the player script is the user's own playback's; another post's page is
        // not read for. A login wall is read (the census's lines; the engine then keeps the rows
        // for the user's own playback, its rule after LOGIN_REQUIRED).
        if (request.primaryFailure in SKIPPED || request.expectedContentId?.let { it != identity.contentId } == true) {
            return null
        }
        val code = identity.contentId
        val mediaId = mediaIdOf(code)
        val read = request.copy(identity = identity, expectedContentId = code)
        val recipe = contracts.recipeOf(SITE_ID)
        // S4: I2 off in the signed config: not run; the tab-fetch rung next.
        if ("I2" in contracts.rungsOff()) {
            details += RungsOff.line(I2)
            return rung(record, request, policy, code, mediaId, read, recipe, details)
        }
        details += "$LINE after ${request.primaryFailure.name}; main's answers read, no new ask"
        val judged = ArrayList<Pair<String, Judged>>()
        val readings = ArrayList<InstagramReading>()
        val staged = HashMap<String, ContractAnswer>()
        val kept = coroutineContext[MainPages]?.let { answersOf(it, code, mediaId) }.orEmpty()
        for ((way, answer) in kept) {
            if (answer == null) {
                details += "$I2 main's $way: not kept (over the bound)"
                continue
            }
            val reading = (answer as? ExtractorHttpResult.Success)?.let { readAnswer(way, it, code, mediaId, read, recipe) }
                ?: Read()
            val verdict = InstagramAnswerJudge.judge(way, answer, reading.reading)
            details += "$I2 main's $way: ${verdict.answerClass.name} (${verdict.evidence})"
            judged += way to verdict
            readings += reading.reading
            reading.staged?.let { staged[way] = it }
        }
        val chosen = InstagramAnswerJudge.aggregate(judged, readings)
        if (chosen == null) {
            details += "$I2: none kept for the post${MainPages.unasked(record)}; nothing to read"
            return rung(record, request, policy, code, mediaId, read, recipe, details)
        }
        val (way, verdict) = chosen
        details += "$I2: ${verdict.answerClass.name} ($way: ${verdict.evidence}); next: ${verdict.next.name}"
        return when (verdict.next) {
            Next.CHECK_ROWS -> {
                val answer = staged[way]
                val why = answer?.let { over(record, it.body.length.toLong()) }
                why?.let { details += "$I2: $it; not checked" }
                if (answer == null || why != null) return rung(record, request, policy, code, mediaId, read, recipe, details)
                // Decision 24: I2's rows; the rung is not asked, but when its proof (debug) sets them aside.
                val probe = coroutineContext[InstagramTabProbe]
                when {
                    probe == null -> Unit
                    probeOff() -> details += RungsOff.line(PROBE)
                    !probe.on() -> details += "$PROBE: off"
                    !probe.proof() -> details += "$PROBE: not asked (I2's rows)"
                    else -> {
                        details += PROOF
                        // Task 15 (F4): the proof tests the rung alone — when it gives no rows the
                        // lookup ends without one (no capture of the tab: signed in, its playback's
                        // file is none of main's, so its row said "the same item: no").
                        return rung(record, request, policy, code, mediaId, read, recipe, details)
                            ?: proofStop(request, read, details)
                    }
                }
                SocialStage.Staged(
                    MasterStage.PAGE_DATA,
                    PageSnapshot(request.pageUrl, request.generation, contract = answer),
                    read,
                )
            }
            Next.FINAL -> {
                if (coroutineContext[InstagramTabProbe] != null) {
                    details += probeLine("$PROBE: not asked (the answer is final)")
                }
                judged(verdict, request, read)
            }
            Next.STOP_SITE -> {
                // S7: the site stops; its host cools (main's 429 is in the record too).
                cooldown.note(listOf(WEB_HOST))
                if (coroutineContext[InstagramTabProbe] != null) {
                    details += probeLine("$PROBE: not asked (the site stopped in this lookup)")
                }
                null
            }
            Next.NEXT_RUNG, Next.PLAYBACK, Next.BROWSER -> rung(record, request, policy, code, mediaId, read, recipe, details)
        }
    }

    /** I2's final answer [verdict] for [request]'s page, read for [read]. */
    private fun judged(verdict: Judged, request: MasterRequest, read: MasterRequest): SocialStage.Staged? =
        verdict.reason?.let { final(request, read, it) }

    /**
     * S7: why the rows may not go to V in [record]'s lookup, or null: main's and Master's asks so
     * far, the lookup's time or the answer's [chars] over Instagram's budget.
     */
    private fun over(record: LookupRecord, chars: Long): String? = when {
        record.mainAsks + record.masterAsks >= budget.requests -> "over the lookup's request budget"
        record.elapsedMillis() >= budget.millis -> "over the lookup's time budget"
        chars > budget.bytes -> "over the lookup's byte budget"
        else -> null
    }

    /**
     * Decision 24 (S2d part 2): the tab-fetch rung for the post [code] ([mediaId]) in [record]'s
     * lookup, the last request rung, once, after I2 gave no rows (or its proof set them aside): the
     * visible tab's own fetch of the signed-out query ([InstagramProbeQuery]), recorded as Master's
     * ask (the budget; a 429 stops the site and cools the host, a checkpoint cools it; no second
     * ask), read by Instagram's recipe for [read] ([recipe]: rows bound to the post, by its code or
     * its pk, and to the link's item) and said in one line: its status and size, whether its answer
     * holds the post's video, whether the tab shows a wall, and its rows. An answer whose page shows
     * a wall is never read (decision 19 (iv)); its 400 or `errors` is VALUE_REJECTED: no other value
     * is tried. Its rows go to V and the init check (S6) as the tab's own answer
     * ([PageSnapshot.authorizedPlayback]: the user's own tab, signed out, its page without a wall),
     * so main's signed-out LOGIN_REQUIRED does not set them aside; none → null (the tab's own
     * playback, I3; main's reason stays). Nothing without a probe in the lookup's context.
     */
    private suspend fun rung(
        record: LookupRecord,
        request: MasterRequest,
        policy: MasterPolicy,
        code: String,
        mediaId: String?,
        read: MasterRequest,
        recipe: ContractRecipe?,
        details: MutableList<String>,
    ): SocialStage.Staged? {
        val probe = coroutineContext[InstagramTabProbe] ?: return null
        // S4: the signed config's rung off wins over the switch.
        if (probeOff() || !probe.on()) {
            details += probeLine("$PROBE: off")
            return null
        }
        val ask = mediaId?.let { id ->
            LookupAsk(
                LookupAsk.POST,
                InstagramProbeQuery.ENDPOINT,
                InstagramProbeQuery.formBody(id, InstagramProbeQuery.LSD_PLACEHOLDER),
                InstagramProbeQuery.HEADERS,
            )
        }
        val why = when {
            record.rateLimited -> "a 429 in this lookup"
            WEB_HOST in record.stopHosts() -> "the site stopped in this lookup (a 429 or a bot check)"
            cooldown.cooling(WEB_HOST) -> "cooling down after a 429 or a bot check"
            record.mainAsks + record.masterAsks >= budget.requests -> "over the lookup's request budget"
            record.elapsedMillis() >= budget.millis -> "over the lookup's time budget"
            ask == null || mediaId == null -> "no media ID in the code"
            signedIn(request.requestContext.cookie) -> "signed in: the rung is the signed-out query"
            record.asked(ask) -> "already asked in this lookup"
            else -> null
        }
        if (why != null || ask == null || mediaId == null) {
            details += "$PROBE: not asked (${why ?: "no media ID in the code"})"
            return null
        }
        val startedAt = record.now()
        val wait = minOf(PROBE_TIMEOUT_MILLIS, policy.contractTimeoutMillis, policy.timeoutMillis, record.timeLeftMillis(budget.millis))
        val reply = withTimeoutOrNull(wait) {
            probe.ask(InstagramTabProbe.Request(code, mediaId, request.pageUrl))
        }
        when (reply) {
            null -> {
                // It may have gone out: it counts as asked (no second ask).
                record.unanswered(ask, startedAt, LookupRecord.Asker.MASTER)
                details += "$PROBE: no answer in time"
                return null
            }
            is InstagramTabProbe.Reply.NotAsked -> {
                val said = reply.why.takeIf { it in InstagramTabProbe.REASONS } ?: "the tab's own reason"
                details += "$PROBE: not asked ($said)"
                return null
            }
            is InstagramTabProbe.Reply.Answered -> {
                val result = resultOf(reply)
                record.record(ask, result, startedAt, LookupRecord.Asker.MASTER)
                val checkpoint = checkpoint(reply)
                // S7: a checkpoint is a bot check: the host cools (a 429 cools by the record).
                if (checkpoint) cooldown.note(listOf(WEB_HOST))
                val body = reply.body
                val video = body != null && InstagramProbeQuery.videoInAnswer(body, code, mediaId)
                // Decision 19 (iv): the answer of a post whose page shows a wall is never read.
                val reading = (result as? ExtractorHttpResult.Success)?.takeUnless { checkpoint || reply.wall }
                    ?.let { readAnswer(SiteWays.TAB_FETCH, it, code, mediaId, read, recipe) } ?: Read()
                val verdict = when {
                    checkpoint -> Judged(AnswerClass.BOT_CHECK, AnswerClass.BOT_CHECK.reason, "a checkpoint: the site cools")
                    reply.wall -> Judged(AnswerClass.LOGIN_WALL, AnswerClass.LOGIN_WALL.reason, "a wall on the page: never read")
                    else -> InstagramAnswerJudge.judge(SiteWays.TAB_FETCH, result, reading.reading)
                }
                val answer = reading.staged?.takeIf { verdict.next == Next.CHECK_ROWS }
                val over = answer?.let { over(record, it.body.length.toLong()) }
                val rows = when {
                    answer == null -> "rows 0 (${verdict.answerClass.name}: ${verdict.evidence})"
                    over != null -> "rows ${reading.reading.rows} (${verdict.answerClass.name}), not checked ($over)"
                    else -> "rows ${reading.reading.rows} (${verdict.answerClass.name})"
                }
                details += "$PROBE: " + (if (reply.status > 0) "HTTP ${reply.status}" else "no HTTP answer") +
                    ", ${reply.bytes.coerceAtLeast(0)} bytes; the post's video in the answer: ${yes(video)}; " +
                    "a wall on the page: ${yes(reply.wall)}; $rows"
                if (answer == null || over != null) return null
                return SocialStage.Staged(
                    MasterStage.PAGE_DATA,
                    PageSnapshot(request.pageUrl, request.generation, authorizedPlayback = true, contract = answer),
                    read,
                )
            }
        }
    }

    /** Main's answer for one way, as kept: null when its page was over the bound. */
    private class Read(val reading: InstagramReading = InstagramReading(), val staged: ContractAnswer? = null)

    /**
     * Main's answers for the post [code] ([mediaId]) in [pages], in main's order: per way its last
     * page (its body, when kept; null over the bound), else its last refused GET; a way main did
     * not ask is not listed.
     */
    private fun answersOf(pages: MainPages, code: String, mediaId: String?): List<Pair<String, ExtractorHttpResult?>> {
        val listed = pages.pages()
        val refused = pages.refusals()
        return MAIN_ORDER.mapNotNull { way ->
            listed.lastOrNull { wayOf(it.url, code, mediaId) == way }?.let { page ->
                return@mapNotNull way to page.body?.let { ExtractorHttpResult.Success(page.statusCode, it, page.finalUrl, page.contentType) }
            }
            refused.lastOrNull { wayOf(it.url, code, mediaId) == way }?.let { refusal ->
                way to ExtractorHttpResult.Failure(refusal.reason, refusal.statusCode)
            }
        }
    }

    /**
     * Main's 2xx answer [main] from [way] for the post [code] ([mediaId]), read for [read] by
     * [recipe]: the post's node (a JSON answer's post; a page's post by main's rule, task 15 (F2),
     * else its node with the post's code), whether it is a video by type and a carousel, main's
     * rule's counts (a page's documents, the post's files; task 15), and the recipe's rows of the
     * link's item (by shape when the key table read none). Another host and another post are the
     * judge's alone.
     * Task 13: the tab-fetch rung's answer ([SiteWays.TAB_FETCH]) so too; its post may name only
     * its pk ([namedPost]).
     */
    private fun readAnswer(
        way: String,
        main: ExtractorHttpResult.Success,
        code: String,
        mediaId: String?,
        read: MasterRequest,
        recipe: ContractRecipe?,
    ): Read {
        val uri = runCatching { URI(main.finalUrl.trim()) }.getOrNull()
        if (uri == null || !InstagramAnswerJudge.instagramHost(uri)) return Read()
        val json = way in InstagramAnswerJudge.JSON_WAYS
        // Task 15 (F2): a page's post is main's (a Master copy of main's rule), else, when main's
        // rule finds none, the page's first node naming the code (a renamed media key: a shape
        // change); a JSON answer's post node is read by main's rule too (its items' files and marks).
        val page = if (json) null else InstagramPageRule.page(main.body, code)
        val found = if (json) jsonPost(way, main.body, code, mediaId) else page?.post?.let { Found(it.node) } ?: pagePost(main.body, code)
        val rule = if (json) (found.node as? JsonValue.Object)?.let(InstagramPageRule::post) else page?.post
        val base = InstagramReading(
            carousel = InstagramAnswerJudge.carousel(main.body),
            post = found.node != null,
            otherPost = found.other,
            videoTyped = rule?.video == true || found.node?.let(::videoTyped) == true,
            documents = page?.documents ?: 0,
            rulePost = page?.post != null,
            files = rule?.files ?: 0,
        )
        if (found.other || recipe == null) return Read(base)
        // The recipe binds rows to a node naming the post's code: a post named by its pk alone gets it.
        val body = if (found.byPk && mediaId != null) namedPost(main.body, code, mediaId) ?: return Read(base) else main.body
        val answer = ContractAnswer(SITE_ID, if (json) wrapped(body) else body, main.finalUrl)
        val evidence = ContractReader(read, recipe, answer).read()
        val rows = evidence.candidates.size
        return Read(
            base.copy(
                rows = rows,
                byShape = rows > 0 && evidence.details.any { it.endsWith(BY_SHAPE) },
                photos = evidence.terminalFailure == SiteExtractionFailure.NO_MEDIA_FOUND,
            ),
            answer.takeIf { rows > 0 },
        )
    }

    /**
     * A post found in an answer: its [node], or [other]: the answer's post is another one; [byPk]:
     * the rung's post names no code, its pk is the post's media ID.
     */
    private class Found(val node: JsonValue? = null, val other: Boolean = false, val byPk: Boolean = false)

    /**
     * The app API's first item, GraphQL's shortcode media (main's scopes), the rung's signed-out
     * post ([InstagramProbeQuery.ANSWER_PATH]), when it is the post [code]; the rung's post naming
     * no code is the post when its pk is [mediaId], another post by another pk.
     */
    private fun jsonPost(way: String, body: String, code: String, mediaId: String?): Found {
        val root = BalancedJson.lenientParse(body.trim(), MAX_NODES) ?: return Found()
        val node = when (way) {
            SiteWays.APP_API -> root["items"].asArrayOrEmpty.firstOrNull()
            SiteWays.TAB_FETCH -> root.path(*InstagramProbeQuery.ANSWER_PATH.toTypedArray())
            else -> root.path("data", "xdt_shortcode_media")?.takeIf { it is JsonValue.Object }
                ?: root.path("data", "shortcode_media")
        }
        if (node !is JsonValue.Object) return Found()
        val named = node["code"].asStringOrNull ?: node["shortcode"].asStringOrNull
        if (named != null) return if (named != code) Found(other = true) else Found(node)
        if (way != SiteWays.TAB_FETCH) return Found(node)
        return when (InstagramProbeQuery.pkOf(node)) {
            null -> Found()
            mediaId -> Found(node, byPk = true)
            else -> Found(other = true)
        }
    }

    /**
     * The rung's answer [body] whose post names only its pk ([mediaId], the post [code]'s) with the
     * code named first in that post, so the recipe binds its rows to it (its own rule); null when the
     * named answer's post is not the post by both (no row).
     */
    private fun namedPost(body: String, code: String, mediaId: String): String? {
        if (!CODE.matches(code)) return null
        val at = POST_START.find(body) ?: return null
        val named = body.substring(0, at.range.last + 1) + "\"code\":\"$code\"," + body.substring(at.range.last + 1)
        val root = BalancedJson.lenientParse(named.trim(), MAX_NODES) ?: return null
        val post = root.path(*InstagramProbeQuery.ANSWER_PATH.toTypedArray()) as? JsonValue.Object ?: return null
        return named.takeIf { post["code"].asStringOrNull == code && InstagramProbeQuery.pkOf(post) == mediaId }
    }

    /**
     * A page's first node naming the post [code] (`shortcode` or `code`), in its data scripts and
     * texts. Task 15 (F2): only where main's rule ([InstagramPageRule]) finds no post — on the real
     * embed page this node was the embed's own context (a camelCase video mark), not the post.
     */
    private fun pagePost(body: String, code: String): Found {
        val documents = (
            PageScripts.embeddedDocuments(body) +
                PageScripts.scripts(body).filter { it.type in PayloadRecipes.JSON_SCRIPT_TYPES }.map { it.body }
            ).take(MAX_DOCUMENTS)
        var visited = 0
        for (text in documents) {
            val root = BalancedJson.lenientParse(text, MAX_NODES) ?: continue
            val pending = ArrayDeque<JsonValue>().apply { add(root) }
            while (pending.isNotEmpty() && visited < MAX_NODES) {
                visited += 1
                when (val value = pending.removeLast()) {
                    is JsonValue.Array -> value.items.asReversed().forEach(pending::add)
                    is JsonValue.Object -> {
                        if (value["shortcode"].asStringOrNull == code || value["code"].asStringOrNull == code) {
                            return Found(value)
                        }
                        value.entries.values.toList().asReversed().forEach(pending::add)
                    }
                    else -> Unit
                }
            }
        }
        return Found()
    }

    /**
     * Task 15 (F4): the rung's proof's stop for [request]'s page, read for [read]: I2's rows were set
     * aside and the rung gave none, so the lookup ends with no row ([SocialStage.Staged.last]: no
     * page data, no capture of the tab after it) and [PROOF_STOP] says why.
     */
    private fun proofStop(request: MasterRequest, read: MasterRequest, details: MutableList<String>): SocialStage.Staged {
        details += PROOF_STOP
        return SocialStage.Staged(MasterStage.PAGE_DATA, PageSnapshot(request.pageUrl, request.generation), read, last = true)
    }

    /** A final answer [reason] for [request]'s page, read for [read] (main's answers, no request). */
    private fun final(request: MasterRequest, read: MasterRequest, reason: SiteExtractionFailure) =
        SocialStage.Staged(
            MasterStage.PAGE_DATA,
            PageSnapshot(request.pageUrl, request.generation, accessFailure = reason),
            read,
        )

    override fun toString(): String = "InstagramLadder"

    companion object {
        /** Main's Instagram adapter's site ID (the video key's prefix). */
        const val SITE_ID = "instagram"

        internal const val LINE = "master: instagram ladder"
        internal const val I2 = "master: instagram I2"

        /** S4: I3, the visible tab's own playback (its line only when the config turns it off). */
        internal const val I3 = "master: instagram I3"

        /** S4: the tab-fetch rung's name in the signed config's `rungsOff`. */
        internal const val PROBE_RUNG = "probe"
        /** The tab-fetch rung's line (S2d part 1's probe's label, decision 24's rung: its Details line). */
        const val PROBE = "master: instagram probe"

        /** Decision 24: the rung's proof's line (debug): I2's rows set aside for the rung. */
        const val PROOF = "$PROBE: I2's rows set aside (the rung's proof)"

        /** Task 15 (F4): the proof's stop: I2's rows set aside, the rung gave none, so no row. */
        const val PROOF_STOP = "$PROBE: no row (the proof set I2's rows aside and the rung gave none)"
        private const val WEB_HOST = "www.instagram.com"
        private const val BY_SHAPE = "by shape (key table changed)"
        private const val SESSION_COOKIE = "sessionid"
        private const val PROBE_TIMEOUT_MILLIS = 5_000L
        private val CODE = Regex("[A-Za-z0-9_-]{1,64}")
        private val POST_START = Regex(""""if_not_gated_logged_out"\s*:\s*\{""")
        private const val MAX_NODES = 200_000
        private const val MAX_DOCUMENTS = 64
        private val SKIPPED = setOf(SiteExtractionFailure.BOT_CHECK, SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED)
        private val POST_KINDS = setOf("p", "reel", "reels", "tv")

        /** Main's ways in main's order (its `Source` entries). */
        private val MAIN_ORDER = listOf(SiteWays.APP_API, SiteWays.GRAPHQL, SiteWays.PAGE, SiteWays.EMBED_PAGE)

        private const val ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        private const val MEDIA_ID_CHARS = 11

        /**
         * The post's numeric media ID, which Instagram's own app API asks with: its shortcode read
         * as base 64 in Instagram's alphabet (the first 11 characters; longer private codes carry
         * more after them). Main's `InstagramUrls.mediaIdOf`.
         */
        internal fun mediaIdOf(code: String): String? {
            var value = BigInteger.ZERO
            val base = BigInteger.valueOf(ALPHABET.length.toLong())
            code.take(MEDIA_ID_CHARS).forEach { character ->
                val digit = ALPHABET.indexOf(character).takeIf { it >= 0 } ?: return null
                value = value.multiply(base).add(BigInteger.valueOf(digit.toLong()))
            }
            return value.takeIf { it.signum() > 0 }?.toString()
        }

        /**
         * Which of main's asks [url] is for the post [code] ([mediaId]): main's addresses on
         * Instagram's web host (main's `ask`): the app API's media info, GraphQL's query naming
         * the code, the post's page, the embed page (main's `embedUrl`); else null.
         */
        internal fun wayOf(url: String, code: String, mediaId: String?): String? {
            val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
            if (uri.scheme?.lowercase(Locale.US) != "https" || uri.host?.lowercase(Locale.US)?.trimEnd('.') != WEB_HOST) {
                return null
            }
            val path = uri.rawPath.orEmpty()
            val segments = path.split('/').filter(String::isNotEmpty)
            return when {
                mediaId != null && path == "/api/v1/media/$mediaId/info/" -> SiteWays.APP_API
                path == "/graphql/query/" || path == "/graphql/query" -> SiteWays.GRAPHQL.takeIf { namesCode(uri, code) }
                path == "/p/$code/embed/captioned/" -> SiteWays.EMBED_PAGE
                segments.size == 2 && segments[0].lowercase(Locale.US) in POST_KINDS && segments[1] == code -> SiteWays.PAGE
                else -> null
            }
        }

        /** Whether GraphQL's ask [uri] names the post [code] in its variables (main's `graphQlUrl`). */
        private fun namesCode(uri: URI, code: String): Boolean = uri.rawQuery.orEmpty().split('&').any { part ->
            part.startsWith("variables=") && runCatching {
                URLDecoder.decode(part.removePrefix("variables="), StandardCharsets.UTF_8.name())
            }.getOrNull()?.contains("\"shortcode\":\"$code\"") == true
        }

        /** Whether [cookie] holds main's session (main's signed-in rule: a `sessionid` value). */
        private fun signedIn(cookie: String?): Boolean =
            cookie?.split(';')?.map(String::trim)?.any { part ->
                part.startsWith("$SESSION_COOKIE=") && part.substringAfter('=').let { it.isNotBlank() && it.none(Char::isWhitespace) }
            } == true

        /** Whether [node] (or one of its items) is a video by its type, files or not. */
        private fun videoTyped(node: JsonValue): Boolean {
            val items = node["carousel_media"].asArrayOrEmpty +
                node.path("edge_sidecar_to_children", "edges").asArrayOrEmpty.mapNotNull { it["node"] }
            return (listOf(node) + items).any { item ->
                item["is_video"].asBooleanOrNull == true ||
                    item["media_type"].asLongOrNull == VIDEO_TYPE ||
                    item["__typename"].asStringOrNull?.endsWith(GRAPH_VIDEO) == true ||
                    VIDEO_KEYS.any { key -> item[key].let { it != null && it != JsonValue.Null } }
            }
        }

        private const val VIDEO_TYPE = 2L
        private const val GRAPH_VIDEO = "GraphVideo"
        private val VIDEO_KEYS = listOf("video_versions", "video_url", "video_dash_manifest")

        /** A JSON answer as a page's data script, so the recipe (a page's) reads it as one document. */
        private fun wrapped(body: String): String =
            "<script type=\"application/json\">" + body.replace("</", "<\\/") + "</script>"

        /** The probe's [reply] as the record's answer: 2xx with its text, a 429, another status, none. */
        private fun resultOf(reply: InstagramTabProbe.Reply.Answered): ExtractorHttpResult = when {
            reply.status in 200..299 -> reply.body?.let {
                ExtractorHttpResult.Success(reply.status, it, reply.finalUrl ?: InstagramProbeQuery.ENDPOINT, reply.contentType)
            } ?: ExtractorHttpResult.Failure(SiteExtractionFailure.RESPONSE_TOO_LARGE, reply.status)
            reply.status == 429 -> ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429)
            reply.status > 0 -> ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, reply.status)
            else -> ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)
        }

        /** Whether the probe's [reply] is Instagram's checkpoint (its address or its answer). */
        private fun checkpoint(reply: InstagramTabProbe.Reply.Answered): Boolean {
            val path = reply.finalUrl?.let { runCatching { URI(it.trim()).path }.getOrNull() }?.lowercase(Locale.US)
            return path?.startsWith("/challenge") == true || reply.body?.let(InstagramProbeQuery::checkpoint) == true
        }

        private fun yes(value: Boolean) = if (value) "yes" else "no"
    }
}
