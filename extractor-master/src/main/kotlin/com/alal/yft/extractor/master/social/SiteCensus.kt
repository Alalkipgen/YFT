package com.alal.yft.extractor.master.social

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
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
import com.alal.yft.extractor.master.modules.youtube.ladder.ClientCensus
import com.alal.yft.extractor.master.policy.TerminalRules
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
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
 * Social plan 0.4 (c) (§7.4 step 2): a site runs in three phases: (i) every slot's main and V on
 * main's best row, in slot order; (ii) route B after main's failures; (iii) the trials, after the
 * site's final answers about the post first, then after main's rows, each in slot order. A slot's
 * Master part runs in the slot's own record (the replay rules unchanged): each record keeps at
 * most [LookupRecord.MAX_KEPT] answers and [LookupRecord.MAX_KEPT_CHARS] characters for the whole
 * run, and drops them once no Master part can replay them. A row's time is the sum of its own
 * steps. The diff compares each site with its own baseline ([SiteCensusState]: the run's VPN, the
 * site's session; the last run whose (i) completed); a slot whose link changed (a local
 * fingerprint, never exported) reads "new link".
 *
 * **Stops:** a 429 or a bot check in (i) (any ask, the hidden page included, V's too) stops the
 * site for the run and for [COOLDOWN_MILLIS] after (the state); in (ii) or (iii) (Master's asks,
 * its media checks, V on its rows) it skips only the remaining Master parts and keeps main's
 * results (the site's Master-phase stop), and for [COOLDOWN_MILLIS] after only the site's Master
 * parts are skipped (main runs). **Budgets** (checked before each step, shared by the phases): at
 * most [MAX_REQUESTS] requests (main's and Master's asks, V's requests, Master's media checks) and
 * [MAX_BYTES] ([meter]'s wire bytes, when measured) per site. The report, the export and the state
 * hold sites, slots, kinds, way labels, classes, counts, sizes and short fingerprints only; a last
 * guard masks any address and the links' values. It runs only when called (the owner's tap).
 *
 * **Task 6b, the ladder** (a social-ladder build: the screen gives a [Pack]): a site of the
 * pack's [Pack.sites] runs its Master parts with the site's ladder instead of the R8 way — the
 * engine as the app builds it (the pack's [Pack.stage] first; R8's contracts after a ladder that
 * does not claim the lookup; no tab), with S6's file check around the census's media checks
 * ([Pack.validator]) and S7's stops and budgets, over the row's own record and main's pages
 * ([MainPages], kept by main's clients during main's lookup in (i): at most [MainPages.MAX_PAGES]
 * pages and [MainPages.MAX_CHARS] characters per row, dropped once the row's Master part ran or
 * will not run). The row's record clock stops between main's lookup and the Master part, so the
 * ladder's time budget counts the row's own time. A link main resolves first (a short link) runs
 * too (the ladder reads the video from main's pages). Main's 429s and bot checks cool in the
 * pack's [Pack.cooldown] (the app's), as in the app. Each such part names each rung's state
 * ([LadderRungs]) and compares Master's rows with main's ([TrialCompare]: the same video or item,
 * the duration, the rows proven another post's and those proven neither way; [LadderPart]). Main's
 * parts, the phases, the stops and the budgets are unchanged; without a pack (every other build)
 * the census is exactly as before, its export and report byte for byte.
 *
 * **Task 8, the weekly check W** (S3, §7.5; [run] with [ClientCensus.Mode.WEEKLY], the screen's W
 * button in a social-ladder build): the same steps on [SiteWeekly.SLOTS] only (three per site, by case kind), Master's part
 * on [SiteWeekly.MASTER_SLOT] only, per site [weeklyCaps] (at most 60 s, 2 MB and
 * [SiteWeekly.MAX_REQUESTS] requests, each checked before every step; the time left also bounds
 * each step), W's own baselines in the state and the diff's lines one per change, each with its
 * runbook step ([SiteWeekly.Step]). A cap reached stops the site's rest with no cooldown; the
 * census's stops and cooldowns are W's ("not run", never waited out). [ClientCensus.Mode.FULL]
 * (the default) is the census as before, its report, export and state byte for byte.
 */
class SiteCensus(
    private val main: MainLookup,
    masterHttp: ExtractorHttpClient,
    private val validator: MasterMediaValidator,
    private val meter: Meter = Meter.NONE,
    private val clock: () -> Long = System::currentTimeMillis,
    private val rowCheck: SiteRowCheck = SiteRowCheck(masterHttp),
    private val pack: Pack? = null,
    private val weeklyCaps: SiteWeekly.Caps = SiteWeekly.Caps(),
) {
    /** Wire bytes the census's clients moved so far; negative when nothing measures them. */
    fun interface Meter {
        fun bytes(): Long

        companion object {
            val NONE: Meter = Meter { -1L }
        }
    }

    /**
     * Task 6b: the social ladders, as a social-ladder build of the app builds them: [stage], the
     * sites' ladders (Master's own client, the app's cooldown); [validator], S6's file check around
     * the census's media checks; [identify], main's adapters (the video a resolved link names);
     * [sites], the sites whose Master parts the ladder runs; [cooldown], where main's 429s and bot
     * checks cool (the app's; null: nowhere).
     */
    class Pack(
        val stage: SocialStage,
        val validator: (MasterMediaValidator) -> MasterMediaValidator,
        val identify: (String) -> SitePageIdentity?,
        val sites: Set<String>,
        val cooldown: SocialCooldown? = null,
    ) {
        override fun toString(): String = "SiteCensus.Pack(sites=$sites)"
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
     * the notes; 0.4 (c): [vpn], whether Android's network is a VPN (the screen reads it), keys each
     * site's baseline with the site's session; [onStep] gets short progress labels (site, slot,
     * step; never a link). Task 8: [mode] [ClientCensus.Mode.WEEKLY] runs the weekly check W
     * ([SiteWeekly]); [ClientCensus.Mode.A_CHECK] is YouTube's only.
     */
    suspend fun run(
        sites: List<Site>,
        links: Map<String, Map<Int, String>>,
        stateJson: String?,
        context: List<String> = emptyList(),
        vpn: Boolean = false,
        mode: ClientCensus.Mode = ClientCensus.Mode.FULL,
        onStep: (String) -> Unit = {},
    ): Outcome {
        require(mode != ClientCensus.Mode.A_CHECK) { "the A check is YouTube's only" }
        val weekly = mode == ClientCensus.Mode.WEEKLY
        val startedAt = clock()
        val state = SiteCensusState.read(stateJson)
        val stops = state.stops.filterValues { it.untilMillis > startedAt }.toMutableMap()
        val masterStops = state.masterStops.filterValues { it.untilMillis > startedAt }.toMutableMap()
        val secrets = HashSet<String>()
        val results = sites.distinctBy(Site::id).map { site ->
            val active = stops[site.id]
            if (active != null) {
                CensusSiteResult(site, skipped = "cooldown ${minutesLeft(active, startedAt)} min (${active.why})")
            } else {
                // 0.4 (c): a Master-phase stop's cooldown skips only the site's Master parts.
                val masterCooldown = masterStops[site.id]?.let { "cooldown ${minutesLeft(it, startedAt)} min (${it.why})" }
                runSite(site, links[site.id].orEmpty(), masterCooldown, secrets, onStep, weekly).also { result ->
                    if (result.cooldownFrom != null && result.stop != null) {
                        stops[site.id] = SiteCensusState.Stop(result.cooldownFrom + COOLDOWN_MILLIS, result.stop)
                    }
                    if (result.masterCooldownFrom != null && result.masterStop != null) {
                        masterStops[site.id] = SiteCensusState.Stop(result.masterCooldownFrom + COOLDOWN_MILLIS, result.masterStop)
                    }
                }
            }
        }
        val run = CensusRunResult(
            atMillis = startedAt,
            millis = (clock() - startedAt).coerceAtLeast(0),
            notes = context.mapNotNull { CensusText.note(it) },
            sites = results,
            vpn = vpn,
            weekly = weekly,
        )
        val json = CensusText.clean(SiteCensusJson.run(run), secrets)
        val export = SiteCensusDiff.parse(json)
        val exported = export["sites"].asArrayOrEmpty.associateBy { it["site"].asStringOrNull }
        // 0.4 (c): each site against its own baseline (the run's VPN, the site's session); the
        // last run whose (i) completed, pass or fail, becomes it. A slot whose link's fingerprint
        // changed reads "new link" and starts again in the new baseline. Task 8: W keeps its own
        // baselines (its slots and caps), apart from the full census's.
        val kept = if (weekly) state.weekly else state.baselines
        val baselines = kept.toMutableMap()
        val compared = LinkedHashMap<String, SiteCensusDiff.Baseline>()
        val notes = ArrayList<String>()
        for (result in results) {
            if (result.skipped != null) continue
            val key = SiteCensusState.Key(result.site.id, vpn, result.session)
            val prints = links[result.site.id].orEmpty()
                .filter { (slot, link) -> slot in 1..result.site.slots.size && link.isNotBlank() }
                .let { if (weekly) SiteWeekly.linksOf(result.site, it) else it }
                .mapValues { (_, link) -> fingerprintOf(link) }
            val completed = result.stop == null
            val baseline = kept[key]
            if (baseline == null) {
                notes += "${result.site.name}: no baseline yet (${key.label})" + if (completed) "; this run starts it" else ""
            } else {
                val newLinks = baseline.links.filter { (slot, print) -> prints[slot]?.let { it != print } == true }.keys
                compared[result.site.id] = SiteCensusDiff.Baseline(baseline.site, baseline.date, newLinks)
            }
            val site = exported[result.site.id]
            if (!completed) {
                notes += "${result.site.name}: stopped before every slot's main (${result.stop}): not a baseline"
            } else if (site != null) {
                baselines[key] = SiteCensusState.Baseline(startedAt, SiteCensusJson.dateOf(startedAt), prints, site)
            }
        }
        val diff = SiteCensusDiff.compare(export, compared, notes, firstRun = kept.isEmpty(), weekly = weekly)
        val stopped = results.filter { it.stop != null }.joinToString("; ") { "${it.site.name}: ${it.stop}" }
        val firstLine = if (weekly) SiteWeekly.headline(run, diff) else SiteCensusReport.headline(run)
        val report = (if (weekly) SiteWeekly.header(run, weeklyCaps) else "") + SiteCensusReport.text(run, diff)
        return Outcome(
            firstLine = CensusText.clean(firstLine, secrets),
            report = CensusText.clean(report, secrets),
            json = json,
            stateJson = SiteCensusState.write(
                SiteCensusState(
                    stops,
                    startedAt,
                    json,
                    masterStops = masterStops,
                    baselines = if (weekly) state.baselines else baselines,
                    weekly = if (weekly) baselines else state.weekly,
                ),
            ),
            stopped = stopped.ifEmpty { null },
        )
    }

    private suspend fun runSite(
        site: Site,
        links: Map<Int, String>,
        masterCooldown: String?,
        secrets: MutableSet<String>,
        onStep: (String) -> Unit,
        weekly: Boolean = false,
    ): CensusSiteResult {
        val startedAt = clock()
        val outside = links.keys.filter { it !in 1..site.slots.size }.sorted()
            .map { "slot $it: outside the site's list, skipped" }
        val listed = links.filter { (slot, link) -> slot in 1..site.slots.size && link.isNotBlank() }.toSortedMap()
        listed.values.forEach { secrets += CensusText.secretsOf(it, null) }
        // Task 8: W runs its slots only (a W slot the owner left empty: a note).
        val chosen = if (weekly) SiteWeekly.linksOf(site, listed).toSortedMap() else listed
        val notes = if (weekly) outside + SiteWeekly.missing(site, listed) else outside
        if (chosen.isEmpty()) return CensusSiteResult(site, skipped = if (weekly) SiteWeekly.NO_LINKS else "no links", notes = notes)
        val phases = Phases(site, Budget(meter, clock, weeklyCaps.takeIf { weekly }), masterCooldown, secrets, onStep, weekly)
        // (i): every slot's main and V on main's best row, in slot order; a stop here stops the site.
        val rows = ArrayList<Row>()
        for ((slot, link) in chosen) {
            val row = Row(phases, slot, checkNotNull(site.kind(slot)), link.trim())
            rows += row
            val stop = phases.stop
            if (stop != null) {
                row.skip("site stopped ($stop)")
                continue
            }
            phases.budget.over()?.let { over ->
                phases.stopSite(over, clock())
                row.skip(over)
            } ?: row.main()
        }
        // (ii): route B after main's failures; (iii): the trials, after the site's final answers
        // about the post first, then after main's rows; each in slot order.
        rows.forEach(Row::planMaster)
        val routeB = rows.filter { it.runs == MasterRun.ROUTE_B }
        val (afterFinal, afterRows) = rows.filter { it.runs == MasterRun.TRIAL }.partition(Row::failed)
        for (row in routeB + afterFinal + afterRows) row.master()
        return CensusSiteResult(
            site = site,
            rows = rows.map(Row::result),
            requests = phases.budget.requests,
            bytes = phases.budget.bytes(),
            stop = phases.stop,
            cooldownFrom = phases.cooldownFrom,
            millis = (clock() - startedAt).coerceAtLeast(0),
            notes = notes,
            masterStop = phases.masterStop,
            masterCooldownFrom = phases.masterCooldownFrom,
            masterCooldown = masterCooldown.takeIf { phases.cooled },
            ladder = ladderOf(site) != null,
        )
    }

    /** Task 6b: the pack when [site]'s Master parts are its ladder's, else null. */
    private fun ladderOf(site: Site): Pack? = pack?.takeIf { site.id in it.sites }

    /**
     * Task 6b: a ladder row's record clock: [pause]d after main's lookup, [resume]d for the row's
     * Master part, so the ladder's time budget counts the row's own time (not the other slots').
     */
    private class RowClock(private val clock: () -> Long) {
        private val lock = Any()
        private var pausedAt: Long? = null
        private var offset = 0L

        fun now(): Long = synchronized(lock) { pausedAt ?: (clock() - offset) }

        fun pause() {
            synchronized(lock) {
                if (pausedAt == null) pausedAt = clock() - offset
            }
        }

        fun resume() {
            synchronized(lock) {
                pausedAt?.let { at ->
                    offset = clock() - at
                    pausedAt = null
                }
            }
        }
    }

    /**
     * 0.4 (c): one site's run across its phases: the budget, the stop in (i) ([stop]: the site
     * stops) and the stop in (ii) or (iii) ([masterStop]: only the remaining Master parts are
     * skipped), and an earlier Master-phase stop's [cooldown] (the site's Master parts skipped).
     * Task 8: [weekly], a W run (Master's part on [SiteWeekly.MASTER_SLOT] only).
     */
    private class Phases(
        val site: Site,
        val budget: Budget,
        private val cooldown: String?,
        val secrets: MutableSet<String>,
        val onStep: (String) -> Unit,
        val weekly: Boolean = false,
    ) {
        var stop: String? = null
            private set
        var cooldownFrom: Long? = null
            private set
        var masterStop: String? = null
            private set
        var masterCooldownFrom: Long? = null
            private set

        /** Whether [cooldown] skipped a Master part of this run. */
        var cooled = false
            private set

        /** (i)'s stop at [now], the first one only; the budget's (task 8: and W's caps') has no cooldown. */
        fun stopSite(why: String?, now: Long) {
            if (why == null || stop != null) return
            stop = why
            if (why !in Budget.STOPS) cooldownFrom = now
        }

        /** (ii)'s or (iii)'s stop at [now], the first one only; the budget's (task 8: and W's caps') has no cooldown. */
        fun stopMaster(why: String?, now: Long) {
            if (why == null || masterStop != null) return
            masterStop = why
            if (why !in Budget.STOPS) masterCooldownFrom = now
        }

        /** Why a Master part cannot run now ([why]) and the stop it is not compared by ([word]), or null. */
        fun masterSkip(): Pair<String, String>? {
            masterStop?.let { return "stopped ($it)" to it }
            return cooldown?.let {
                cooled = true
                it to COOLDOWN
            }
        }
    }

    /**
     * One row of a site: in (i) main and V on main's best row ([main]); in (ii) or (iii) Master's
     * way and V on Master's best row ([master]); then the judge ([result]).
     */
    private inner class Row(
        private val phases: Phases,
        private val slot: Int,
        private val kind: Kind,
        private val link: String,
    ) {
        private val site: Site get() = phases.site
        private var skipped: String? = null
        private var identity: LinkIdentity? = null
        private var record: LookupRecord? = null
        private var answer: Looked = Looked.Error
        private var mainPart: MainPart? = null
        private var session = false
        private var setup = 0L

        /** 0.4 (c): the row's time, the sum of its own steps (setup, main, V, Master, V). */
        private var millis = 0L
        private var v: SiteRowCheck.Checked? = null
        private var vSkipped: String? = null
        private var plan: Plan? = null
        private var masterPart: MasterPart? = null

        /** Task 6b: the pack when the site's ladder runs the row's Master part; main's pages and the record's clock. */
        private val ladder: Pack? = ladderOf(phases.site)
        private var pages: MainPages? = null
        private var rowClock: RowClock? = null

        /** How Master's way runs for the row (after [planMaster]); null for a row not looked up. */
        val runs: MasterRun? get() = plan?.run

        /** Whether main failed (a trial after the site's final answer about the post), not gave rows. */
        val failed: Boolean get() = mainPart?.reason != null

        private fun step(what: String) = phases.onStep("${site.name} slot $slot: $what")

        fun skip(why: String) {
            skipped = why
        }

        /** (i): main's lookup, then V on main's best row. */
        suspend fun main() {
            val rowStarted = clock()
            step("main")
            val identity = guarded { main.identify(link) }
            setup = (clock() - rowStarted).coerceAtLeast(0)
            millis = setup
            if (identity == null) {
                skipped = NOT_HANDLED
                return
            }
            phases.secrets += CensusText.secretsOf(link, identity.page)
            if (identity.page.siteId != site.id) {
                skipped = ANOTHER_SITE
                return
            }
            this.identity = identity
            session = sessionOf(site.id, identity.cookie)
            // Task 6b: a ladder row's record runs on its own clock and keeps main's pages.
            val rowClock = ladder?.let { RowClock(clock) }
            this.rowClock = rowClock
            val record = LookupRecord(
                clock = rowClock?.let { it::now } ?: clock,
                maxKept = LookupRecord.MAX_KEPT,
                maxKeptChars = LookupRecord.MAX_KEPT_CHARS,
                labels = site.labels(),
            )
            this.record = record
            val pages = ladder?.let { MainPages(site = site.id) }
            this.pages = pages
            val started = clock()
            // Task 8: W's time left bounds main's lookup too (shorter than main's own limit).
            val limit = phases.budget.limit(ROW_MILLIS)
            val answer = withContext(if (pages != null) record + pages else record) {
                guarded { withTimeoutOrNull(limit) { Looked.Answer(main.lookup(link, identity)) } ?: Looked.TimeLimit }
                    ?: Looked.Error
            }
            rowClock?.pause()
            // Task 6b, S7: main's 429s and bot checks cool the hosts in the pack's cooldown, as in the app.
            ladder?.cooldown?.let { cooldown -> record.stopHosts().takeIf { it.isNotEmpty() }?.let(cooldown::note) }
            this.answer = answer
            val lookupMillis = (clock() - started).coerceAtLeast(0)
            millis += lookupMillis
            val mainPart = mainPart(record, answer, lookupMillis)
            this.mainPart = mainPart
            phases.budget.add(record.mainAsks)
            phases.stopSite(stopOf(record, mainPart.reason, mainPart.hiddenPage, botChecksBefore = 0), clock())
            if (answer == Looked.TimeLimit && limit < ROW_MILLIS) {
                // Task 8: W's time cut main's lookup: the slot is not run (never a pass), the site's rest neither.
                phases.budget.cut()
                phases.stopSite(SiteWeekly.TIME, clock())
                skipped = SiteWeekly.TIME
                return
            }

            val rows = ((answer as? Looked.Answer)?.answer as? MainAnswer.Found)?.rows.orEmpty()
            val (v, vSkipped) = checkBest(rows, master = false)
            this.v = v
            this.vSkipped = vSkipped
        }

        /** After (i): how Master's way runs; a row whose Master part will not run drops its kept answers. */
        fun planMaster() {
            val identity = identity ?: return
            // Task 8: W's Master part runs on its slot 1 only (each way once); its other slots: main only.
            val plan = if (phases.weekly && slot != SiteWeekly.MASTER_SLOT) Plan.notRun(SiteWeekly.MAIN_ONLY) else planOf(identity, answer)
            this.plan = plan
            if (plan.notRun != null) {
                record?.clear()
                pages = null
            }
        }

        /** (ii) or (iii): Master's way once (unless the Master phase stopped), then V on Master's best. */
        suspend fun master() {
            val identity = checkNotNull(identity)
            val plan = checkNotNull(plan)
            val record = checkNotNull(record)
            masterPart = runMaster(identity, plan, record)
            // 0.4 (c): nothing replays main's answers after the slot's Master part (task 6b: nor reads its pages).
            record.clear()
            pages = null
        }

        fun result(): CensusRowResult {
            skipped?.let { return CensusRowResult(slot, kind, skipped = it) }
            val mainPart = checkNotNull(mainPart)
            val record = checkNotNull(record)
            val master = masterPart ?: MasterPart(MasterRun.NOT_RUN, plan?.notRun)
            val rowClass = classOf(answer)
            val (expected, note) = judge(rowClass, mainPart, session)
            return CensusRowResult(
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
                millis = millis,
            )
        }

        /**
         * V on the best of [rows], unless the site stopped or the budget is spent; Master's V
         * ([master]) also not after the Master phase's stop, and its 429 is that phase's stop.
         */
        private suspend fun checkBest(rows: List<MediaCandidate>, master: Boolean): Pair<SiteRowCheck.Checked?, String?> {
            val best = SiteRowCheck.best(rows) ?: return null to null
            phases.stop?.let { return null to "site stopped" }
            if (master) phases.masterSkip()?.let { (why, _) -> return null to why }
            phases.budget.over()?.let { over ->
                if (master) phases.stopMaster(over, clock()) else phases.stopSite(over, clock())
                return null to over
            }
            step("V")
            val started = clock()
            val within = guarded { phases.budget.within { rowCheck.check(site, best, clock()) } }
            millis += (clock() - started).coerceAtLeast(0)
            if (within == null && phases.budget.timeUp()) {
                // Task 8: W's time cut V: not run (never a pass); the phase's rest stops.
                if (master) phases.stopMaster(SiteWeekly.TIME, clock()) else phases.stopSite(SiteWeekly.TIME, clock())
                return null to SiteWeekly.TIME
            }
            val checked = within ?: SiteRowCheck.Checked(SiteRowCheck.Verdict.UNKNOWN, "error")
            phases.budget.add(checked.requests)
            if (checked.rateLimited) {
                if (master) phases.stopMaster(STOP_429, clock()) else phases.stopSite(STOP_429, clock())
            }
            return checked to null
        }

        private suspend fun runMaster(identity: LinkIdentity, plan: Plan, record: LookupRecord): MasterPart {
            phases.masterSkip()?.let { (why, word) -> return MasterPart(MasterRun.NOT_RUN, why, stopped = word) }
            phases.budget.over()?.let { over ->
                phases.stopMaster(over, clock())
                return MasterPart(MasterRun.NOT_RUN, over, stopped = over)
            }
            step("Master")
            val checks = RowValidator(validator, record)
            // Task 9: the pack's file check (S6), kept so its refused files count in the part.
            val fileCheck = ladder?.validator?.invoke(checks)
            val engine = MasterFallbackEngine(
                fileCheck ?: checks,
                NoPlaybackCaptureProvider,
                POLICY,
                modules = emptyList(),
                contracts = contracts,
                social = ladder?.stage,
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
            val replayed = record.replayedCount
            // Task 6b: the ladder reads the row's record and main's pages from the lookup's context.
            val context: CoroutineContext = if (ladder != null) route + record + (pages ?: MainPages(site = site.id)) else route
            rowClock?.resume()
            val started = clock()
            val result = withContext(context) { guarded { phases.budget.within { engine.extract(request) } } }
            val masterMillis = (clock() - started).coerceAtLeast(0)
            millis += masterMillis
            phases.budget.add(record.masterAsks + checks.calls)
            phases.stopMaster(stopOf(record, null, null, botChecks) ?: STOP_429.takeIf { checks.rateLimited }, clock())
            // Task 8: W's time cut Master's part: not finished (never a pass); Master's rest stops.
            val cut = result == null && phases.budget.timeUp()
            if (cut) phases.stopMaster(SiteWeekly.TIME, clock())
            val rows = (result as? MasterResult.Success)?.result?.candidates.orEmpty()
            val (v, vSkipped) = checkBest(rows, master = true)
            val ladderPart = ladder?.let { pack -> ladderPart(pack, identity, result, rows) }
            // Task 9: S6's files off the site's media hosts (refused before any ask) count as refused.
            val fileRefused = (fileCheck as? SocialRowCheck)?.refused(record) ?: 0
            return MasterPart(
                run = plan.run,
                why = plan.failure?.takeIf { plan.run == MasterRun.ROUTE_B }?.name,
                answer = if (cut) "time limit" else answerOf(result),
                reason = when (result) {
                    is MasterResult.Failure -> result.reason
                    is MasterResult.Skipped -> result.reason
                    else -> null
                },
                rows = rows.size,
                tallest = SiteRowCheck.tallest(rows),
                bestMillis = SiteRowCheck.best(rows)?.durationMillis,
                // Task 6b: the ladder's rungs and S6 ask through their own route B over the record.
                replayed = if (ladder != null) record.replayedCount - replayed else route.replayed,
                newRequests = if (ladder != null) record.masterAsks else route.newRequests,
                refused = route.refused + (if (ladder != null) LadderRungs.refusedOf(site.id, result) + fileRefused else 0),
                checks = checks.calls,
                asks = record.asks().filter { it.asker == LookupRecord.Asker.MASTER },
                v = v,
                vSkipped = vSkipped,
                millis = masterMillis,
                // 0.4 (c): this part's own ask, check or V met the Master phase's stop.
                stopped = phases.masterStop,
                ladder = ladderPart,
            )
        }

        /**
         * Task 6b: the ladder's part of [result]: each rung's state and Master's [rows] against
         * main's (main's rows carry the link's key: a link main resolves first counts as the video
         * its resolved link names, as in the app); without main's rows, every row is wrong where the
         * slot wants none and proven neither way where it downloads.
         */
        private fun ladderPart(pack: Pack, identity: LinkIdentity, result: MasterResult?, rows: List<MediaCandidate>): LadderPart {
            val rungs = LadderRungs.of(site.id, result).orEmpty()
            if (rows.isEmpty()) return LadderPart(rungs)
            val mains = ((answer as? Looked.Answer)?.answer as? MainAnswer.Found)?.rows.orEmpty()
            if (mains.isEmpty()) {
                val wantsNone = !kind.expect.wantsRows(false) && !kind.expect.wantsRows(true)
                return LadderPart(
                    rungs = rungs,
                    compare = if (wantsNone) LadderPart.COMPARE_NONE_WANTED else LadderPart.COMPARE_MAIN_NONE,
                    wrong = if (wantsNone) rows.size else 0,
                    unknown = if (wantsNone) 0 else rows.size,
                )
            }
            val key = "${identity.page.siteId}:${identity.page.contentId}"
            val resolved = pages?.resolved(identity.page, pack.identify)?.let { "${it.siteId}:${it.contentId}" } ?: key
            val compared = TrialCompare.compare(rows, mains) { id -> if (id != null && id == key) resolved else id }
            return LadderPart(
                rungs = rungs,
                compare = LadderPart.COMPARE_MAIN,
                item = compared.item,
                same = if (compared.same) "yes" else "no",
                duration = compared.duration,
                wrong = compared.wrong,
                unknown = compared.unknown,
                mainRows = mains.size,
            )
        }

        private fun planOf(identity: LinkIdentity, answer: Looked): Plan {
            phases.stop?.let { return Plan.notRun("site stopped") }
            val main = when (answer) {
                Looked.TimeLimit -> return Plan.notRun("time limit")
                Looked.Error -> return Plan.notRun("error")
                is Looked.Answer -> answer.answer
            }
            if (main is MainAnswer.NotHandled) return Plan.notRun(NOT_HANDLED)
            // Task 6b: the ladder reads a link main resolves first from main's pages.
            if (ladder == null && !contracts.reads(identity.page)) {
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

    /**
     * A site's spend so far: requests counted by the census, bytes by [meter] (when it measures).
     * Task 8: in W, [caps] instead of the census's (and the time, from the site's start by [clock]).
     */
    private class Budget(private val meter: Meter, private val clock: () -> Long, private val caps: SiteWeekly.Caps? = null) {
        private val start = meter.bytes()
        /** Task 8: W's start (the census reads no clock here). */
        private val startedAt = if (caps != null) clock() else 0L
        var requests = 0
            private set

        /** Task 8: W's time cut a step. */
        private var cut = false

        fun add(count: Int) {
            requests += count
        }

        /** Bytes since the site's start, or -1 when not measured. */
        fun bytes(): Long = if (start < 0) -1 else (meter.bytes() - start).coerceAtLeast(0)

        /** Task 8: W's time left for the site; the census has no such cap. */
        private fun millisLeft(): Long = caps?.let { (it.millis - (clock() - startedAt)).coerceAtLeast(0) } ?: Long.MAX_VALUE

        /** Task 8: [max], or W's time left when that is shorter. */
        fun limit(max: Long): Long = minOf(max, millisLeft())

        /** Task 8: whether W's time is spent (or cut a step); never in the census. */
        fun timeUp(): Boolean = caps != null && (cut || millisLeft() == 0L)

        /** Task 8: W's time cut a step. */
        fun cut() {
            if (caps != null) cut = true
        }

        /** Task 8: [block] within W's time left (null: the time cut it); in the census, [block] as it is. */
        suspend fun <T : Any> within(block: suspend () -> T): T? {
            if (caps == null) return block()
            val value = withTimeoutOrNull(millisLeft()) { block() }
            if (value == null) cut = true
            return value
        }

        /** [STOP] when a cap is reached, else null; task 8: in W, the cap's own word. */
        fun over(): String? {
            val caps = caps ?: return STOP.takeIf { requests >= MAX_REQUESTS || bytes() >= MAX_BYTES }
            return when {
                requests >= caps.requests -> SiteWeekly.REQUESTS
                bytes() >= caps.bytes -> SiteWeekly.BYTES
                timeUp() -> SiteWeekly.TIME
                else -> null
            }
        }

        companion object {
            const val STOP = "budget"

            /** The caps' stops (the census's budget, W's caps): none cools the site. */
            val STOPS = setOf(STOP, SiteWeekly.REQUESTS, SiteWeekly.BYTES, SiteWeekly.TIME)
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

        /** 0.4 (c): the word of a Master part skipped by an earlier Master-phase stop's cooldown. */
        const val COOLDOWN = "cooldown"
        private const val MINUTE_MILLIS = 60_000L
        private const val FINGERPRINT_BYTES = 8

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

        /** The screen's status line: the last run and each site's cooldown (0.4 (c): and its Master parts'). */
        fun statusOf(stateJson: String?, nowMillis: Long): String {
            val state = SiteCensusState.read(stateJson)
            val last = if (state.lastAtMillis <= 0) {
                "No run yet."
            } else {
                val minutes = ((nowMillis - state.lastAtMillis) / MINUTE_MILLIS).coerceAtLeast(0)
                "Last run: ${SiteCensusJson.dateOf(state.lastAtMillis)}, $minutes min ago."
            }
            val stops = cooling(state.stops, nowMillis)
            val master = cooling(state.masterStops, nowMillis)
            return last + (if (stops.isEmpty()) "" else " Stopped: $stops.") +
                (if (master.isEmpty()) "" else " Master's parts stopped: $master.")
        }

        /** 0.4 (c): a link's local fingerprint (the state's only, never exported): 16 hex digits of its SHA-256. */
        internal fun fingerprintOf(link: String): String =
            MessageDigest.getInstance("SHA-256").digest(link.trim().toByteArray(Charsets.UTF_8))
                .take(FINGERPRINT_BYTES).joinToString("") { "%02x".format(it) }

        private fun cooling(stops: Map<String, SiteCensusState.Stop>, nowMillis: Long): String =
            stops.filterValues { it.untilMillis > nowMillis }.entries.joinToString("; ") { (id, stop) ->
                "${SiteWays.site(id)?.name ?: id} ${minutesLeft(stop, nowMillis)} min (${stop.why})"
            }

        private fun minutesLeft(stop: SiteCensusState.Stop, nowMillis: Long): Long =
            (stop.untilMillis - nowMillis + MINUTE_MILLIS - 1) / MINUTE_MILLIS

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
