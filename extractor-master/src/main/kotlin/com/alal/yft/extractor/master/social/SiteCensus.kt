package com.alal.yft.extractor.master.social

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterMediaValidator
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.NoPlaybackCaptureProvider
import com.alal.yft.extractor.master.ValidationResult
import com.alal.yft.extractor.master.contract.SiteContracts
import com.alal.yft.extractor.master.lookup.AnswerMark
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.lookup.RouteBHttpClient
import com.alal.yft.extractor.master.lookup.RouteBLookup
import com.alal.yft.extractor.master.policy.TerminalRules
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Social plan S0b: what the census needs of a link before main's lookup: main's identity of the
 * post ([page]) and the browser's identity for the link: the agent YFT's tab sends ([userAgent])
 * and the browser's cookies for the link ([cookie]). [toString] shows neither value.
 */
class LinkIdentity(
    val page: SitePageIdentity,
    val userAgent: String?,
    val cookie: String?,
) {
    override fun toString(): String =
        "LinkIdentity(site=${page.siteId}, agent=${!userAgent.isNullOrBlank()}, cookie=${!cookie.isNullOrBlank()})"
}

/**
 * Social plan 0.4 (§7.3): which item of the post main's answer is, [number] of [count], as main's
 * answer line says it ([of]). X: the place a `/video/N` link names (among the post's media, the
 * card's or the quoted post's) of the post's videos; Instagram: the carousel's item of the post's
 * items. Numbers only.
 */
data class PostItem(val number: Int, val count: Int) {
    companion object {
        private const val MAX = 1_000

        /** X's answer line (`XExtractor`): "answer: [the quoted post's |the card's ]video N, M in the post". */
        private val X_LINE = Regex("""^answer: (?:the quoted post's |the card's )?(?:video|GIF) (\d+), (\d+) in the post""")

        /** Instagram's item line (`InstagramExtractor`): "item N of M: ...". */
        private val ITEM_LINE = Regex("""^item (\d+) of (\d+): """)

        /** The item main's answer [details] name, or null (another site, no such line). */
        fun of(details: List<String>): PostItem? = details.firstNotNullOfOrNull { line ->
            val text = line.trim()
            val match = X_LINE.find(text) ?: ITEM_LINE.find(text) ?: return@firstNotNullOfOrNull null
            val number = match.groupValues[1].toIntOrNull()
            val count = match.groupValues[2].toIntOrNull()
            if (number != null && count != null && number in 1..MAX && count in 1..MAX) PostItem(number, count) else null
        }
    }
}

/** S0b: main's answer to one census lookup; [hiddenPage]: the hidden page's mark when main read it. */
sealed interface MainAnswer {
    val hiddenPage: AnswerMark?

    /** Main's [rows]; 0.4: [item], the post's item they are, when main's answer names it. */
    class Found(
        val rows: List<MediaCandidate>,
        override val hiddenPage: AnswerMark? = null,
        val item: PostItem? = null,
    ) : MainAnswer {
        override fun toString(): String = "MainAnswer.Found(rows=${rows.size}, hiddenPage=$hiddenPage, item=$item)"
    }

    /** Main's failure: its [reason] and the HTTP [statusCode] it gave. */
    class Failed(
        val reason: SiteExtractionFailure,
        val statusCode: Int? = null,
        override val hiddenPage: AnswerMark? = null,
    ) : MainAnswer {
        override fun toString(): String = "MainAnswer.Failed(reason=$reason, status=$statusCode, hiddenPage=$hiddenPage)"
    }

    /** No adapter of main looked the link up (an adapter turned off, say). */
    data object NotHandled : MainAnswer {
        override val hiddenPage: AnswerMark? get() = null
    }
}

/**
 * S0b: main's own lookup as the app builds it (the app's adapter coordinator: the four adapters,
 * TikTok's hidden page and download service, fresh). [lookup]'s asks go through a recorder
 * (`LookupRecorder`) into the caller's record ([LookupRecord], in the coroutine context); the
 * hidden page's own loads are not asks of a client, so only its mark comes back.
 */
interface MainLookup {
    /** [link]'s post and the browser's identity for it, or null when no adapter of main handles it. */
    suspend fun identify(link: String): LinkIdentity?

    /** Main's fresh lookup of [link] with [identity]; nothing remembered, nothing retried. */
    suspend fun lookup(link: String, identity: LinkIdentity): MainAnswer
}

/**
 * Social plan S0b (`MASTER_KEY_SOCIAL_PLAN.md` §7.1): the site census's engine. It measures only:
 * main's lookups, Master's engine and its contracts stay as they are. For each site of a run, each
 * of the owner's links (its slot of [Site.slots]) is one row:
 *
 * - **Main:** [MainLookup.lookup], fresh, with the browser's identity, at most [ROW_MILLIS] (Home's
 *   limit), inside the row's own [LookupRecord] with the site's labels: each ask with its way,
 *   status, reading and shape; the ways main did not reach are named.
 * - **Master:** today's R8 way of the site (the engine with no module and no capture, its
 *   contracts on [masterHttp] through [RouteBHttpClient]), once, inside [RouteBLookup] over the
 *   same record (main's identical unauthenticated answer replayed, an endpoint main asked not asked
 *   again, nothing new after a 429; all counted). After main's failure with a reason Master's
 *   engine answers, as route B; after main's rows or the site's final answer about the post
 *   (private or removed, region), once as a trial (main's failure given as `NO_MEDIA_FOUND`).
 *   Not after a browser-required reason, a network failure, a rate limit, a bot check, main's time
 *   limit, nor for a link the contracts do not read (a short link).
 * - **V:** [SiteRowCheck] on main's best row and on Master's best row.
 * - **Judge:** the row's class from main's reason as Master's engine sees it (recoverable,
 *   browser, final) against the slot's [Expect]; a reason the slot calls ambiguous without the
 *   session ([Expect.ambiguousWithoutSession]) is "ambiguous (no session)", neither way (0.4).
 *
 * Social plan 0.4 (§7.3, the census fixes): each row also holds its setup time (main's identity
 * of the link) and its end-to-end time, the best row's duration and the post's item main's answer
 * names (main's and Master's), and the record's repeats and variants (its report key, beside the
 * endpoint rule). 0.4 (b): after its rules the report holds the diff against the last run the
 * state kept ([SiteCensusDiff]: per slot CHANGED / BREAKING lines).
 *
 * **Stops:** a 429 or a bot check (any ask, the hidden page included, V's too) stops the site for
 * the run and for [COOLDOWN_MILLIS] after (the state). **Budgets** (checked before each step): at
 * most [MAX_REQUESTS] requests (main's and Master's asks, V's requests, Master's media checks) and
 * [MAX_BYTES] ([meter]'s wire bytes, when measured) per site. The report, the export and the state
 * hold sites, slots, kinds, way labels, classes, counts, sizes and short fingerprints only; a last
 * guard masks any address and the links' values. It runs only when called (the owner's tap).
 */
class SiteCensus(
    private val main: MainLookup,
    masterHttp: ExtractorHttpClient,
    private val validator: MasterMediaValidator,
    private val meter: Meter = Meter.NONE,
    private val clock: () -> Long = System::currentTimeMillis,
    private val rowCheck: SiteRowCheck = SiteRowCheck(masterHttp),
) {
    /** Wire bytes the census's clients moved so far; negative when nothing measures them. */
    fun interface Meter {
        fun bytes(): Long

        companion object {
            val NONE: Meter = Meter { -1L }
        }
    }

    /** [report], [json] and [stateJson] are sanitized; [stopped] names each site that stopped, or null. */
    class Outcome(
        val firstLine: String,
        val report: String,
        val json: String,
        val stateJson: String,
        val stopped: String?,
    ) {
        override fun toString(): String = "SiteCensus.Outcome(firstLine=$firstLine, stopped=$stopped)"
    }

    private val contracts = SiteContracts(RouteBHttpClient(masterHttp))

    /**
     * Runs [sites] (their order) over [links] (site ID → slot → link) against the stored
     * [stateJson] (the screen's state file, or null). [context] lines (the network type) go into
     * the notes; [onStep] gets short progress labels (site, slot, step; never a link).
     */
    suspend fun run(
        sites: List<Site>,
        links: Map<String, Map<Int, String>>,
        stateJson: String?,
        context: List<String> = emptyList(),
        onStep: (String) -> Unit = {},
    ): Outcome {
        val startedAt = clock()
        val state = SiteCensusState.read(stateJson)
        val stops = state.stops.filterValues { it.untilMillis > startedAt }.toMutableMap()
        val secrets = HashSet<String>()
        val results = sites.distinctBy(Site::id).map { site ->
            val active = stops[site.id]
            if (active != null) {
                val minutes = (active.untilMillis - startedAt + MINUTE_MILLIS - 1) / MINUTE_MILLIS
                CensusSiteResult(site, skipped = "cooldown $minutes min (${active.why})")
            } else {
                runSite(site, links[site.id].orEmpty(), secrets, onStep).also { result ->
                    if (result.cooldownFrom != null && result.stop != null) {
                        stops[site.id] = SiteCensusState.Stop(result.cooldownFrom + COOLDOWN_MILLIS, result.stop)
                    }
                }
            }
        }
        val run = CensusRunResult(
            atMillis = startedAt,
            millis = (clock() - startedAt).coerceAtLeast(0),
            notes = context.mapNotNull { CensusText.note(it) },
            sites = results,
        )
        val json = CensusText.clean(SiteCensusJson.run(run), secrets)
        // 0.4 (b): this run against the last one the state kept (its export).
        val diff = SiteCensusDiff.compare(state.last, SiteCensusDiff.parse(json))
        val stopped = results.filter { it.stop != null }.joinToString("; ") { "${it.site.name}: ${it.stop}" }
        return Outcome(
            firstLine = CensusText.clean(SiteCensusReport.headline(run), secrets),
            report = CensusText.clean(SiteCensusReport.text(run, diff), secrets),
            json = json,
            stateJson = SiteCensusState.write(SiteCensusState(stops, startedAt, json)),
            stopped = stopped.ifEmpty { null },
        )
    }

    private suspend fun runSite(
        site: Site,
        links: Map<Int, String>,
        secrets: MutableSet<String>,
        onStep: (String) -> Unit,
    ): CensusSiteResult {
        val startedAt = clock()
        val budget = Budget(meter)
        val notes = links.keys.filter { it !in 1..site.slots.size }.sorted()
            .map { "slot $it: outside the site's list, skipped" }
        val chosen = links.filter { (slot, link) -> slot in 1..site.slots.size && link.isNotBlank() }.toSortedMap()
        chosen.values.forEach { secrets += CensusText.secretsOf(it, null) }
        if (chosen.isEmpty()) return CensusSiteResult(site, skipped = "no links", notes = notes)
        val rows = ArrayList<CensusRowResult>()
        var stop: String? = null
        var cooldownFrom: Long? = null
        for ((slot, link) in chosen) {
            val kind = checkNotNull(site.kind(slot))
            if (stop != null) {
                rows += CensusRowResult(slot, kind, skipped = "site stopped ($stop)")
                continue
            }
            budget.over()?.let { over ->
                stop = over
                rows += CensusRowResult(slot, kind, skipped = over)
            } ?: run {
                val row = Row(site, slot, kind, link.trim(), budget, secrets, onStep).run()
                rows += row.result
                row.stop?.let {
                    stop = it
                    if (it != Budget.STOP) cooldownFrom = clock()
                }
            }
        }
        return CensusSiteResult(
            site = site,
            rows = rows,
            requests = budget.requests,
            bytes = budget.bytes(),
            stop = stop,
            cooldownFrom = cooldownFrom,
            millis = (clock() - startedAt).coerceAtLeast(0),
            notes = notes,
        )
    }

    /** One row of [site]: main, V on main's best, Master's way, V on Master's best, the judge. */
    private inner class Row(
        private val site: Site,
        private val slot: Int,
        private val kind: Kind,
        private val link: String,
        private val budget: Budget,
        private val secrets: MutableSet<String>,
        private val onStep: (String) -> Unit,
    ) {
        var stop: String? = null
            private set
        lateinit var result: CensusRowResult
            private set

        private fun step(what: String) = onStep("${site.name} slot $slot: $what")

        suspend fun run(): Row {
            val rowStarted = clock()
            step("main")
            val identity = guarded { main.identify(link) }
            val setup = (clock() - rowStarted).coerceAtLeast(0)
            if (identity == null) {
                result = CensusRowResult(slot, kind, skipped = NOT_HANDLED)
                return this
            }
            secrets += CensusText.secretsOf(link, identity.page)
            if (identity.page.siteId != site.id) {
                result = CensusRowResult(slot, kind, skipped = ANOTHER_SITE)
                return this
            }
            val session = sessionOf(site.id, identity.cookie)
            val record = LookupRecord(clock = clock, labels = site.labels())
            val started = clock()
            val answer = withContext(record) {
                guarded { withTimeoutOrNull(ROW_MILLIS) { Looked.Answer(main.lookup(link, identity)) } ?: Looked.TimeLimit }
                    ?: Looked.Error
            }
            val mainPart = mainPart(record, answer, (clock() - started).coerceAtLeast(0))
            budget.add(record.mainAsks)
            stop = stopOf(record, mainPart.reason, mainPart.hiddenPage, botChecksBefore = 0)

            val rows = ((answer as? Looked.Answer)?.answer as? MainAnswer.Found)?.rows.orEmpty()
            val (v, vSkipped) = checkBest(rows)
            val master = master(identity, answer, record)
            val rowClass = classOf(answer)
            val (expected, note) = judge(rowClass, mainPart, session)
            result = CensusRowResult(
                slot = slot,
                kind = kind,
                session = session,
                main = mainPart,
                rowClass = rowClass,
                expected = expected,
                note = note,
                v = v,
                vSkipped = vSkipped,
                master = master,
                requests = record.mainAsks + record.masterAsks + (v?.requests ?: 0) +
                    (master.v?.requests ?: 0) + master.checks,
                setupMillis = setup,
                millis = (clock() - rowStarted).coerceAtLeast(0),
            )
            return this
        }

        /** V on the best of [rows], unless the site stopped or the budget is spent. */
        private suspend fun checkBest(rows: List<MediaCandidate>): Pair<SiteRowCheck.Checked?, String?> {
            val best = SiteRowCheck.best(rows) ?: return null to null
            stop?.let { return null to "site stopped" }
            budget.over()?.let { over ->
                stop = over
                return null to over
            }
            step("V")
            val checked = guarded { rowCheck.check(site, best, clock()) }
                ?: SiteRowCheck.Checked(SiteRowCheck.Verdict.UNKNOWN, "error")
            budget.add(checked.requests)
            if (checked.rateLimited) stop = STOP_429
            return checked to null
        }

        private suspend fun master(identity: LinkIdentity, answer: Looked, record: LookupRecord): MasterPart {
            val plan = planOf(identity, answer)
            if (plan.notRun != null) return MasterPart(MasterRun.NOT_RUN, plan.notRun)
            budget.over()?.let { over ->
                stop = over
                return MasterPart(MasterRun.NOT_RUN, over)
            }
            step("Master")
            val checks = RowValidator(validator, record)
            val engine = MasterFallbackEngine(
                checks,
                NoPlaybackCaptureProvider,
                POLICY,
                modules = emptyList(),
                contracts = contracts,
            )
            val page = identity.page.canonicalPageUrl
            val request = MasterRequest(
                pageUrl = page,
                generation = slot.toLong(),
                nowEpochMs = clock().coerceAtLeast(0),
                primaryFailure = checkNotNull(plan.failure),
                requestContext = BrowserRequestContext(page, identity.userAgent, identity.cookie),
                identity = identity.page,
            )
            val route = RouteBLookup(record)
            val botChecks = record.botChecks
            val started = clock()
            val result = withContext(route) { guarded { engine.extract(request) } }
            val millis = (clock() - started).coerceAtLeast(0)
            budget.add(record.masterAsks + checks.calls)
            stop = stop ?: stopOf(record, null, null, botChecks) ?: STOP_429.takeIf { checks.rateLimited }
            val rows = (result as? MasterResult.Success)?.result?.candidates.orEmpty()
            val (v, vSkipped) = checkBest(rows)
            return MasterPart(
                run = plan.run,
                why = plan.failure?.takeIf { plan.run == MasterRun.ROUTE_B }?.name,
                answer = answerOf(result),
                reason = when (result) {
                    is MasterResult.Failure -> result.reason
                    is MasterResult.Skipped -> result.reason
                    else -> null
                },
                rows = rows.size,
                tallest = SiteRowCheck.tallest(rows),
                bestMillis = SiteRowCheck.best(rows)?.durationMillis,
                replayed = route.replayed,
                newRequests = route.newRequests,
                refused = route.refused,
                checks = checks.calls,
                asks = record.asks().filter { it.asker == LookupRecord.Asker.MASTER },
                v = v,
                vSkipped = vSkipped,
                millis = millis,
            )
        }

        private fun planOf(identity: LinkIdentity, answer: Looked): Plan {
            stop?.let { return Plan.notRun("site stopped") }
            val main = when (answer) {
                Looked.TimeLimit -> return Plan.notRun("time limit")
                Looked.Error -> return Plan.notRun("error")
                is Looked.Answer -> answer.answer
            }
            if (main is MainAnswer.NotHandled) return Plan.notRun(NOT_HANDLED)
            if (!contracts.reads(identity.page)) {
                return Plan.notRun(if (identity.page.requiresCanonicalResolution) "short link" else "no contract")
            }
            val reason = (main as? MainAnswer.Failed)?.reason ?: return Plan.TRIAL
            return when {
                reason in TerminalRules.BROWSER_REQUIRED -> Plan.notRun("browser-required")
                reason == SiteExtractionFailure.NETWORK -> Plan.notRun("network")
                reason == SiteExtractionFailure.RATE_LIMITED -> Plan.notRun("rate limit")
                reason in TRIAL_AFTER -> Plan.TRIAL
                reason in TerminalRules.NEVER_FALLBACK -> Plan.notRun("final")
                else -> Plan(MasterRun.ROUTE_B, reason, null)
            }
        }

        /** Whether the row is as its slot expects; null with [AMBIGUOUS]: neither way (0.4). */
        private fun judge(rowClass: RowClass, main: MainPart, session: Boolean): Pair<Boolean?, String?> {
            val expect = kind.expect
            return when {
                expect.wantsRows(session) && rowClass == RowClass.ROWS -> {
                    val min = expect.minHeight
                    if (min == null || (main.tallest ?: 0) >= min) true to null else false to "tallest below $min"
                }
                expect.wantsRows(session) -> false to "rows wanted"
                rowClass == RowClass.ROWS -> false to "rows found"
                main.reason != null && main.reason in expect.reasons -> true to null
                !session && main.reason != null && main.reason in expect.ambiguousWithoutSession -> null to AMBIGUOUS
                else -> false to "another answer"
            }
        }

        private fun mainPart(record: LookupRecord, answer: Looked, millis: Long): MainPart {
            val main = (answer as? Looked.Answer)?.answer
            val asks = record.asks().filter { it.asker == LookupRecord.Asker.MAIN }
            val seen = asks.mapNotNullTo(HashSet()) { it.way }
            val hidden = main?.hiddenPage
            val found = main as? MainAnswer.Found
            val rows = found?.rows.orEmpty()
            return MainPart(
                answer = when (main) {
                    null -> if (answer == Looked.TimeLimit) "time limit" else "error"
                    is MainAnswer.Found -> "rows"
                    is MainAnswer.Failed -> "failed"
                    MainAnswer.NotHandled -> NOT_HANDLED
                },
                reason = (main as? MainAnswer.Failed)?.reason,
                statusCode = (main as? MainAnswer.Failed)?.statusCode,
                rows = rows.size,
                tallest = SiteRowCheck.tallest(rows),
                bestMillis = SiteRowCheck.best(rows)?.durationMillis,
                item = found?.item,
                millis = millis,
                asks = asks,
                notReached = site.mainWays.filter { way ->
                    way !in seen && !(way == SiteWays.HIDDEN_PAGE && hidden != null)
                },
                hiddenPage = hidden,
                repeated = record.repeatedAsks,
                repeats = record.repeatAsks,
                variants = record.variantAsks,
                afterStop = record.asksAfterStop,
                botChecks = record.botChecks,
                stopHosts = record.stopHosts().size,
            )
        }
    }

    /** A site's spend so far: requests counted by the census, bytes by [meter] (when it measures). */
    private class Budget(private val meter: Meter) {
        private val start = meter.bytes()
        var requests = 0
            private set

        fun add(count: Int) {
            requests += count
        }

        /** Bytes since the site's start, or -1 when not measured. */
        fun bytes(): Long = if (start < 0) -1 else (meter.bytes() - start).coerceAtLeast(0)

        /** [STOP] when a cap is reached, else null. */
        fun over(): String? = STOP.takeIf { requests >= MAX_REQUESTS || bytes() >= MAX_BYTES }

        companion object {
            const val STOP = "budget"
        }
    }

    /** Master's media checks in one row, counted; none after the row's 429 (route B's rule 3). */
    private class RowValidator(
        private val validator: MasterMediaValidator,
        private val record: LookupRecord,
    ) : MasterMediaValidator {
        private val count = AtomicInteger()

        @Volatile
        var rateLimited = false
            private set

        val calls: Int get() = count.get()

        override suspend fun validate(candidate: MediaCandidate, nowEpochMs: Long): ValidationResult {
            if (rateLimited || record.rateLimited) return ValidationResult.Rejected(SiteExtractionFailure.RATE_LIMITED)
            count.incrementAndGet()
            return validator.validate(candidate, nowEpochMs).also {
                if (it is ValidationResult.Rejected && it.reason == SiteExtractionFailure.RATE_LIMITED) rateLimited = true
            }
        }
    }

    /** Main's lookup as the row saw it: an answer, the row's time limit, or an error. */
    private sealed interface Looked {
        class Answer(val answer: MainAnswer) : Looked
        data object TimeLimit : Looked
        data object Error : Looked
    }

    /** How Master's way runs for a row: [run], the [failure] it is given, or why it is [notRun]. */
    private class Plan(val run: MasterRun, val failure: SiteExtractionFailure?, val notRun: String?) {
        companion object {
            val TRIAL = Plan(MasterRun.TRIAL, SiteExtractionFailure.NO_MEDIA_FOUND, null)
            fun notRun(why: String) = Plan(MasterRun.NOT_RUN, null, why)
        }
    }

    companion object {
        const val TOOL_LABEL: String = "YFT site census"

        /** Home's limit for one lookup (`LinkInspector.TIMEOUT_MILLIS`). */
        const val ROW_MILLIS: Long = 90_000
        const val COOLDOWN_MILLIS: Long = 30L * 60 * 1000
        const val MAX_REQUESTS: Int = 150
        const val MAX_BYTES: Long = 40L * 1024 * 1024

        const val NOT_HANDLED = "not handled"
        const val ANOTHER_SITE = "another site's link"
        const val STOP_429 = "429"
        const val STOP_BOT_CHECK = "bot check"
        const val STOP_RATE_LIMIT = "rate limit"

        /** 0.4: the judge's word for a reason that, without the session, says nothing about the slot. */
        const val AMBIGUOUS = "ambiguous (no session)"
        private const val MINUTE_MILLIS = 60_000L

        /** Master's engine as route B builds it: on, no module, no capture, the R8 time shares. */
        private val POLICY = MasterPolicy(enabled = true, timeoutMillis = 20_000, contractTimeoutMillis = 6_000)

        /** The site's final answers about the post after which Master's way runs once as a trial. */
        private val TRIAL_AFTER = setOf(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, SiteExtractionFailure.GEO_RESTRICTED)

        /**
         * The sites' sign-in cookies: the report says only whether one was there (a sign-in-only
         * slot downloads with it). Instagram's is the one main's adapter reads itself.
         */
        private val SESSION_COOKIES = mapOf(
            SiteWays.FACEBOOK.id to "c_user",
            SiteWays.TIKTOK.id to "sessionid",
            SiteWays.INSTAGRAM.id to "sessionid",
            SiteWays.X.id to "auth_token",
        )

        /** Whether [cookie] holds [siteId]'s sign-in cookie with a value. */
        fun sessionOf(siteId: String, cookie: String?): Boolean {
            val name = SESSION_COOKIES[siteId] ?: return false
            return cookie.orEmpty().split(';').any { pair ->
                pair.substringBefore('=').trim() == name && pair.substringAfter('=', "").isNotBlank()
            }
        }

        /** The screen's status line: the last run and each site's cooldown. */
        fun statusOf(stateJson: String?, nowMillis: Long): String {
            val state = SiteCensusState.read(stateJson)
            val last = if (state.lastAtMillis <= 0) {
                "No run yet."
            } else {
                val minutes = ((nowMillis - state.lastAtMillis) / MINUTE_MILLIS).coerceAtLeast(0)
                "Last run: ${SiteCensusJson.dateOf(state.lastAtMillis)}, $minutes min ago."
            }
            val stops = state.stops.filterValues { it.untilMillis > nowMillis }.entries.joinToString("; ") { (id, stop) ->
                val minutes = (stop.untilMillis - nowMillis + MINUTE_MILLIS - 1) / MINUTE_MILLIS
                "${SiteWays.site(id)?.name ?: id} $minutes min (${stop.why})"
            }
            return if (stops.isEmpty()) last else "$last Stopped: $stops."
        }

        private fun classOf(answer: Looked): RowClass = when (answer) {
            Looked.TimeLimit -> RowClass.TIME_LIMIT
            Looked.Error -> RowClass.ERROR
            is Looked.Answer -> when (val main = answer.answer) {
                is MainAnswer.Found -> RowClass.ROWS
                is MainAnswer.Failed -> RowClass.of(main.reason)
                MainAnswer.NotHandled -> RowClass.NOT_HANDLED
            }
        }

        /**
         * Why the row stops its site, or null: a 429 in the record, a bot check (an ask read as
         * one since [botChecksBefore], the hidden page, main's reason) or main's rate limit.
         */
        private fun stopOf(
            record: LookupRecord,
            reason: SiteExtractionFailure?,
            hiddenPage: AnswerMark?,
            botChecksBefore: Int,
        ): String? = when {
            record.rateLimited -> STOP_429
            record.botChecks > botChecksBefore || hiddenPage == AnswerMark.BOT_CHECK ||
                reason == SiteExtractionFailure.BOT_CHECK -> STOP_BOT_CHECK
            reason == SiteExtractionFailure.RATE_LIMITED -> STOP_RATE_LIMIT
            else -> null
        }

        private fun answerOf(result: MasterResult?): String = when (result) {
            null -> "error"
            is MasterResult.Success -> "rows"
            is MasterResult.Failure -> "failed"
            is MasterResult.NeedsPlayback -> "needs playback"
            is MasterResult.NeedsSelection -> "needs selection"
            is MasterResult.Skipped -> "skipped"
        }

        /** [block]'s value, or null when it throws; the caller's own cancellation still ends the run. */
        private suspend fun <T> guarded(block: suspend () -> T): T? = try {
            block()
        } catch (cancellation: CancellationException) {
            currentCoroutineContext().ensureActive()
            null
        } catch (_: Exception) {
            null
        }
    }
}
