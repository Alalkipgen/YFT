package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.path
import com.alal.yft.extractor.master.modules.youtube.StreamCheck
import com.alal.yft.extractor.master.modules.youtube.YouTubeClientProfiles
import com.alal.yft.extractor.master.modules.youtube.YouTubeParseResult
import com.alal.yft.extractor.master.modules.youtube.YouTubePlayerResponseParser
import com.alal.yft.extractor.master.modules.youtube.YouTubeStream
import com.alal.yft.extractor.master.modules.youtube.YouTubeUrls
import com.alal.yft.extractor.master.modules.youtube.YouTubeVideo
import com.alal.yft.extractor.master.solver.OwnSolverTextFile
import java.net.URI
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Phase 1.3 D6 — the client ladder (MASTER_KEY_PHASE1_3_PLAN.md §3, §4 D3 and D6), Master-only,
 * behind `-Pyft.clientLadder` (debug only; the app passes one only when that flag and
 * masterCapture are on; `null` = R6, unchanged).
 *
 * Plan adapted (2026-10-15): the ladder works in the R6 chain's HTTP path. R6 (the copied
 * extractor, unchanged) still asks every client and builds every row, so rows come only from the
 * copied parser — on YouTube's answer or on L's canonical JSON — and the row-builder parity holds by
 * construction. Per lookup ([LadderLookup]):
 *  - every request of the chain goes out through the same-lookup memo ([LookupMemo.chain]);
 *  - a player answer about another video is dropped (F4: `RESPONSE_CHANGED`, the chain goes on);
 *  - each visionOS answer the chain gets (its first request, or R6's `R0+vd` after a bot check;
 *    (က+)) is judged ([AnswerJudge]). On `SHAPE_CHANGED` the chain gets L's canonical JSON holding
 *    only the rows that passed V and the init check in this lookup ([ShapeNormalizer], [LRows],
 *    [InitCheck] on the bytes [MediaStartReader] read; guardrails 6 and 9). On `INCOMPLETE` it
 *    gets the answer with the offered rows' sizes from the one-byte probe ([ZRung]) and without the
 *    other sizeless formats (they would not be offered). A direct answer (and Z's rows) gets V
 *    when visionOS has no pass in the last hour ([StreamCheck], one per lookup): a list whose
 *    checked row got 403 is refused by address. Anything else reaches the chain as it came;
 *  - after the chain (and the own solver's V): the refused rows are removed (merge rule); a
 *    verdict about the video (age, private, removed, DRM, geo) keeps the chain's result; a complete
 *    result (P22) too; otherwise the H leg ([HlsLeg], at most one per lookup) on the visionOS
 *    answer's HLS address, bound to the video's length, adds one HLS row (merge rule, guardrail 9:
 *    the chain's rows stay, the ladder adds only a format they lack) with the chain's video key.
 *    In the `h-only` trial (D5c's phone download, debug builds only; [LadderTrial]) the result is
 *    that row alone, or no row.
 *
 * A (D5a, built after the close; §4 D5a "As built (A)"): when visionOS's version is rejected
 * (`VERSION_REJECTED`), the chain's request goes out again with only the version changed —
 * learned, then self-healed ([VersionSource]: one read a day of a reference table on
 * `raw.githubusercontent.com` through [sources]), then the bundled alternates — at most
 * [LadderLookup.MAX_A_PER_LOOKUP] player requests, once per lookup, never in cooldown, after a
 * bot check ((က+), guardrail 4) or in a live-check trial. A is provisional: its answer gets L, Z
 * and V as visionOS's own and reaches the chain only with rows that passed V (or L rows); only then
 * is the version learned (guardrail 5). Otherwise the chain gets its own answer and the H leg may
 * use A's HLS address. Health ([ClientHealth]) records every visionOS class and the rung that
 * repaired it.
 */
class MasterClientLadder internal constructor(
    internal val health: ClientHealth,
    internal val reader: MediaStartReader?,
    /** D7's live-check switch (debug builds only; [LadderTrial.NONE] everywhere else). */
    internal val trial: LadderTrial = LadderTrial.NONE,
    /** A's version-source reader (null: A uses the learned version and the bundled alternates only). */
    internal val sources: VersionSourceReader? = null,
) {
    /** V's day budget for direct answers (guardrail 6: the client ID is the key). */
    internal val vLedger = StreamCheck.MemoryLedger()

    /** A's versions (D5a): learned, self-healed (today's value in memory), bundled alternates. */
    internal val versions = VersionSource(health, sources)

    /**
     * The app's ladder: its health in [healthFile] (null: memory only); L rows read by [reader];
     * A's version sources read by [sources]. With a live-check [trial] the health stays in memory
     * only — the file is never read or written — so the normal build's health never holds what a
     * changed answer taught (D7); A does not run in a trial.
     */
    constructor(
        healthFile: OwnSolverTextFile?,
        reader: MediaStartReader?,
        trial: LadderTrial = LadderTrial.NONE,
        sources: VersionSourceReader? = null,
    ) : this(ClientHealth(healthFile.takeIf { trial === LadderTrial.NONE }), reader, trial, sources)

    internal fun lookup(http: ExtractorHttpClient, request: SiteExtractionRequest): LadderLookup =
        LadderLookup(this, http, request)

    /** Health lines for a debug screen: classes, times and rungs; never an address or value. */
    fun describe(): List<String> = health.describe()

    override fun toString(): String =
        "MasterClientLadder(reader=${reader != null}, sources=${sources != null}, trial=${trial.label})"
}

/** Reads the first bytes of a media file: an L row's V and its init check on the same bytes (guardrail 6). */
fun interface MediaStartReader {
    /** A GET of [url] with [headers] (no cookie) and `Range: bytes=0-(maxBytes - 1)`. */
    suspend fun read(url: String, headers: Map<String, String>, maxBytes: Int): MediaStart
}

/** What [MediaStartReader] read; values stay in memory and never reach a log. */
sealed interface MediaStart {
    /** A 2xx answer: its headers as they came and at most the asked number of [bytes]. */
    class Read(
        val statusCode: Int,
        val contentType: String?,
        val contentRange: String?,
        val contentLength: Long?,
        val bytes: ByteArray,
    ) : MediaStart {
        override fun toString(): String = "MediaStart.Read(status=$statusCode, bytes=${bytes.size})"
    }

    /** No 2xx answer: [statusCode] when the server answered, null for a network failure or another host. */
    class Failed(val statusCode: Int?) : MediaStart {
        override fun toString(): String = "MediaStart.Failed(status=$statusCode)"
    }
}

/** One lookup of the ladder: [chain] is the R6 chain's HTTP client, [finish] its last step. */
internal class LadderLookup(
    private val ladder: MasterClientLadder,
    http: ExtractorHttpClient,
    private val request: SiteExtractionRequest,
) {
    private val memo = LookupMemo(http)
    private val videoId = request.identity.contentId
    private val now = request.nowEpochMs
    private val sizes = ZRung(memo.ladder).lookup()
    private val hls = HlsLeg(memo.ladder, reader = ladder.reader).lookup()
    private val streamCheck = StreamCheck(memo.ladder, V_POLICY, ladder.vLedger) { now }
    private val mutex = Mutex()
    private var vision: AnswerJudge.Verdict? = null
    private var visionBody: String? = null
    private var normalized: ShapeNormalizer.Outcome? = null
    private var verdictAboutVideo = false
    private var lReads = 0
    private val vChecks = HashMap<String, Int>()
    private var aDone = false
    private var aRequests = 0

    /** Addresses V proved refused in this lookup (merge rule, guardrail 9); memory only, never logged. */
    private val refused = HashSet<String>()

    /** What the ladder did, for the result's details: rungs, counts and reasons; never a value. */
    val notes = mutableListOf<String>()

    val chain: ExtractorHttpClient = object : ExtractorHttpClient {
        override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult =
            memo.chain.get(url, headers, maxBodyBytes)

        override suspend fun probe(url: String, headers: Map<String, String>, timeoutMillis: Long): ExtractorProbeResult =
            memo.chain.probe(url, headers, timeoutMillis)

        override suspend fun postJson(
            url: String,
            body: String,
            headers: Map<String, String>,
            maxBodyBytes: Long,
        ): ExtractorHttpResult {
            val result = memo.chain.postJson(url, body, headers, maxBodyBytes)
            val client = playerClient(url, body) ?: return result
            return mutex.withLock { answer(client, result, Asked(url, body, headers, maxBodyBytes)) }
        }

        override fun toString(): String = "LadderLookup.chain"
    }

    /** The chain's [result] after the ladder's last step: the H leg and the merge rule. */
    suspend fun finish(result: SiteExtractionResult): SiteExtractionResult = try {
        mutex.withLock { merge(result) }
    } finally {
        memo.clear()
    }

    /** The client name of a player request for this video, or null for any other request. */
    private fun playerClient(url: String, body: String): String? {
        if (!url.contains(PLAYER_PATH)) return null
        val root = ShapeLocator.parse(body) ?: return null
        if (root.path("videoId").asStringOrNull != videoId) return null
        return root.path("context", "client", "clientName").asStringOrNull
    }

    /** Every player answer: one about another video is dropped (F4); visionOS's goes to its rungs. */
    private suspend fun answer(client: String, result: ExtractorHttpResult, asked: Asked): ExtractorHttpResult {
        val success = result as? ExtractorHttpResult.Success
        val root = success?.let { ShapeLocator.parse(it.body) }
        val answered = root.path("videoDetails", "videoId").asStringOrNull
        if (answered != null && answered != videoId) {
            notes += "$client: answer about another video dropped"
            return ExtractorHttpResult.Failure(SiteExtractionFailure.RESPONSE_CHANGED, success?.statusCode)
        }
        if (client == YouTubeClientProfiles.VISION_OS.clientName) {
            if (success == null || ladder.trial === LadderTrial.NONE) return repair(result, success, root, asked)
            return tried(success, root)
        }
        if (success == null) return result
        return other(client, success, root)
    }

    /** D7's live-check switch on visionOS's answer, in memory before the judge (debug builds only). */
    private suspend fun tried(success: ExtractorHttpResult.Success, root: JsonValue?): ExtractorHttpResult {
        val trial = ladder.trial
        if (trial.skip == LadderTrial.SKIP_VISION_OS) {
            notes += "trial ${trial.label}: visionOS's answer not used"
            return ExtractorHttpResult.Failure(SiteExtractionFailure.RESPONSE_CHANGED, success.statusCode)
        }
        if (trial.hOnly) {
            notes += "trial ${trial.label}: visionOS's answer as it came"
            return repair(success, success, root)
        }
        val changed = (root as? JsonValue.Object)?.let(trial::apply)
        if (changed == null) {
            notes += "trial ${trial.label}: visionOS's answer unchanged (nothing to change)"
            return repair(success, success, root)
        }
        notes += "trial ${trial.label}: visionOS's answer changed in memory"
        val mutated = success.copy(body = LadderJson.text(changed))
        return repair(mutated, mutated, changed)
    }

    /**
     * Another client of the chain (Android, MWEB, embedded: the O rungs R6 asks; Plan adapted):
     * judged for health, and V on its direct rows (guardrail 9; a provisional client on every
     * lookup). Its answer reaches the chain as it came; a refused list is removed by [merge].
     */
    private suspend fun other(client: String, success: ExtractorHttpResult.Success, root: JsonValue?): ExtractorHttpResult {
        val entry = MasterClientTable.ENTRIES.firstOrNull { it.profile.clientName == client } ?: return success
        val verdict = AnswerJudge.judge(entry.profile, YouTubePlayerResponseParser.inspect(success.body, now), root, success.body, videoId)
        val answerClass = verdict.answerClass
        if (verdict.terminal) {
            ladder.health.record(entry.id, answerClass)
            return success
        }
        val step = vDirect(entry.id, verdict.video, hourSkip = entry.status == MasterClientTable.Status.LIVE)
        val recorded = if (step?.tokenNeeded == true) AnswerJudge.afterV(answerClass, targetForbidden = true) else answerClass
        ladder.health.record(entry.id, recorded, passed = step?.passed ?: (answerClass == CensusClass.DIRECT))
        return success
    }

    private suspend fun repair(
        result: ExtractorHttpResult,
        success: ExtractorHttpResult.Success?,
        root: JsonValue?,
        asked: Asked? = null,
    ): ExtractorHttpResult {
        val profile = YouTubeClientProfiles.VISION_OS
        val verdict = when {
            success == null -> AnswerJudge.ofFailure(result as ExtractorHttpResult.Failure)
            else -> AnswerJudge.judge(profile, YouTubePlayerResponseParser.inspect(success.body, now), root, success.body, videoId)
        }
        if (verdict.answerClass == CensusClass.VERSION_REJECTED && asked != null) {
            versionRung(asked)?.let { healed -> healedAnswer(healed)?.let { return it } }
        }
        vision = verdict
        visionBody = success?.body
        val answerClass = verdict.answerClass
        if (answerClass == CensusClass.AGE_CHECK || answerClass == CensusClass.VIDEO_VERDICT) verdictAboutVideo = true
        if (success == null) {
            ladder.health.record(VISION_OS, answerClass)
            return result
        }
        var body: String? = null
        var rung: ClientHealth.Rung? = null
        when (answerClass) {
            CensusClass.SHAPE_CHANGED -> lRows(verdict, success.body)?.let { body = it; rung = ClientHealth.Rung.L }
            CensusClass.INCOMPLETE -> zSizes(verdict, success.body)?.let { body = it; rung = ClientHealth.Rung.Z }
            else -> Unit
        }
        // V when needed (guardrail 9): the answer's own addresses, from a client without a pass in the last hour.
        var recorded = answerClass
        var passed = rung != null || answerClass == CensusClass.DIRECT
        if (answerClass == CensusClass.DIRECT || rung == ClientHealth.Rung.Z) {
            val step = vDirect(VISION_OS, verdict.video, hourSkip = true)
            if (step != null) {
                passed = step.passed
                if (step.tokenNeeded) recorded = AnswerJudge.afterV(answerClass, targetForbidden = true)
            }
        }
        ladder.health.record(VISION_OS, recorded, rung.takeIf { passed }, passed)
        return body?.let { success.copy(body = it) } ?: result
    }

    /** The chain's player request as it went out: A sends it again with only the version changed. */
    private class Asked(val url: String, val body: String, val headers: Map<String, String>, val maxBodyBytes: Long)

    /** A version A sent that was not rejected: its answer and the answer's verdict. */
    private class Healed(val candidate: VersionSource.Candidate, val success: ExtractorHttpResult.Success, val verdict: AnswerJudge.Verdict)

    /**
     * A (D5a; guardrail 5's order): visionOS's version was rejected. Once per lookup, outside a
     * live-check trial, when visionOS may be asked (no cooldown, no bot check): the chain's request
     * again with only the version changed — the learned version, then a self-healed one (at most one
     * source read a day), then the bundled alternates — until one is not rejected, at most
     * [MAX_A_PER_LOOKUP] requests. A bot check (or 429) stops A and quiets the request-sending rungs.
     * Null when no version answered.
     */
    private suspend fun versionRung(asked: Asked): Healed? {
        if (aDone || ladder.trial !== LadderTrial.NONE) return null
        aDone = true
        val entry = MasterClientTable.entry(VISION_OS) ?: return null
        val allowed = entry.variants.any { it.variant == MasterClientTable.Variant.A && it.status != MasterClientTable.Status.NOT_ACTIVE }
        if (!allowed) return null
        if (!ladder.health.mayAsk(VISION_OS, sendsRequest = true)) return null.also { notes += "A: not asked (cooldown or bot check)" }
        val profile = entry.profile
        val bundled = profile.clientVersion
        val sent = VersionSource.versionOf(asked.body, profile.clientName)
            ?: return null.also { notes += "A: no version in the request" }
        val versions = ladder.versions
        val tried = mutableSetOf(sent, bundled)
        var offered = 0
        var stop = false

        suspend fun ask(candidate: VersionSource.Candidate): Healed? {
            if (stop || candidate.version in tried) return null
            if (aRequests >= MAX_A_PER_LOOKUP) return null.also { stop = true }
            tried += candidate.version
            offered += 1
            val edited = VersionSource.withVersion(asked.body, asked.headers, profile.clientName, sent, candidate.version)
                ?: return null.also { notes += "A: no single version field to change"; stop = true }
            aRequests += 1
            val got = memo.ladder.postJson(asked.url, edited.body, edited.headers, asked.maxBodyBytes)
            val success = got as? ExtractorHttpResult.Success
            val root = success?.let { ShapeLocator.parse(it.body) }
            val answered = root.path("videoDetails", "videoId").asStringOrNull
            if (answered != null && answered != videoId) {
                notes += "A: answer about another video dropped"
                stop = true
                return null
            }
            val verdict = when (success) {
                null -> AnswerJudge.ofFailure(got as ExtractorHttpResult.Failure)
                else -> AnswerJudge.judge(profile, YouTubePlayerResponseParser.inspect(success.body, now), root, success.body, videoId)
            }
            val failure = got as? ExtractorHttpResult.Failure
            return when {
                verdict.answerClass == CensusClass.VERSION_REJECTED -> null.also {
                    versions.rejected(VISION_OS, candidate)
                    notes += "A: the ${candidate.label} version rejected too"
                }
                verdict.answerClass == CensusClass.BOT_CHECK || failure?.reason == SiteExtractionFailure.RATE_LIMITED -> null.also {
                    ladder.health.record(VISION_OS, CensusClass.BOT_CHECK)
                    notes += "A: bot check, A stops"
                    stop = true
                }
                success == null -> null.also {
                    notes += "A: no answer (${failure?.reason?.name ?: "failure"})"
                    stop = true
                }
                else -> Healed(candidate, success, verdict)
            }
        }

        versions.learned(entry, bundled)?.let { candidate -> ask(candidate)?.let { return it } }
        if (!stop) {
            val read = versions.selfHealed(entry, bundled, request.requestContext.userAgent)
            val candidate = read.candidate
            if (candidate == null) notes += "A: source ${read.why}" else ask(candidate)?.let { return it }
        }
        for (candidate in versions.alternates(entry, bundled)) ask(candidate)?.let { return it }
        if (!stop) notes += if (offered == 0) "A: no version to try" else "A: no version answered"
        return null
    }

    /**
     * A's answer, provisional (guardrail 9: V on every answer): its rungs as for visionOS's own
     * answer — L on a shape change, Z on missing sizes, V on direct rows (no last-hour skip). It
     * reaches the chain only with rows that passed V (or L rows), and only then is the version
     * learned (guardrail 5) and A the working variant. Otherwise it is dropped (null): the chain,
     * H and the health get visionOS's own answer, the rejection (its 24 h cooldown).
     */
    private suspend fun healedAnswer(healed: Healed): ExtractorHttpResult? {
        val verdict = healed.verdict
        val success = healed.success
        val candidate = healed.candidate
        val answerClass = verdict.answerClass
        val before = normalized
        var body: String? = null
        var passed = false
        when (answerClass) {
            CensusClass.SHAPE_CHANGED -> lRows(verdict, success.body)?.let { body = it; passed = true }
            CensusClass.INCOMPLETE -> zSizes(verdict, success.body)?.let { body = it }
            else -> Unit
        }
        if (answerClass == CensusClass.DIRECT || (answerClass == CensusClass.INCOMPLETE && body != null)) {
            passed = vDirect(VISION_OS, verdict.video, hourSkip = false)?.passed == true
        }
        if (!passed) {
            normalized = before
            notes += "A: the ${candidate.label} version's answer dropped ($answerClass, no V pass)"
            return null
        }
        vision = verdict
        visionBody = success.body
        notes += "A: the ${candidate.label} version answered ($answerClass)"
        if (candidate.kind != VersionSource.Kind.LEARNED) ladder.health.learn(VISION_OS, candidate.version, candidate.source)
        ladder.health.record(VISION_OS, CensusClass.VERSION_REJECTED, ClientHealth.Rung.A, passed = true)
        return body?.let { success.copy(body = it) } ?: success
    }

    private class VStep(val passed: Boolean, val tokenNeeded: Boolean)

    /**
     * V on a direct answer of client [id] (guardrails 6 and 9; D4 rule): with [hourSkip] skipped
     * when that client passed in the last hour. One target (the best adaptive video row to offer, else a progressive row), one
     * control from the other list. The rows of a list whose checked row got 403 are refused for
     * this lookup — the chain still gets the answer as it came (its requests stay R6's) and
     * [merge] removes them by address (a 403 with an open control: `TOKEN_NEEDED`); any other
     * `unknown` keeps the rows as R6 shows them, without a pass. Null when no V ran.
     */
    private suspend fun vDirect(id: String, video: YouTubeVideo?, hourSkip: Boolean): VStep? {
        video ?: return null
        if (hourSkip && ladder.health.passedWithin(id, ClientHealth.HOUR_MILLIS)) return null
        if ((vChecks[id] ?: 0) >= MAX_V_PER_LOOKUP) return VStep(passed = false, tokenNeeded = false)
        fun direct(rows: List<YouTubeStream>) = rows.filter { row -> row.url?.let(YouTubeUrls::isMediaServerRequest) == true }
        val adaptive = direct(video.adaptive)
        val progressive = direct(video.progressive)
        val offered = ShapeNormalizer.offerSet(video)
        val target = offered.firstOrNull { row -> row.hasVideo && adaptive.any { it.url == row.url } }
            ?: adaptive.firstOrNull { it.hasVideo } ?: adaptive.firstOrNull() ?: progressive.firstOrNull() ?: return null
        val targetList = if (adaptive.any { it.url == target.url }) ADAPTIVE else FORMATS
        val control = (if (targetList == ADAPTIVE) progressive else adaptive.filter { it.url != target.url }).firstOrNull()
        val hosts = (adaptive + progressive).mapNotNull { row -> row.url?.let(::hostOf) }.toSet()
        vChecks[id] = (vChecks[id] ?: 0) + 1
        val checked = streamCheck.check(
            StreamCheck.Request(
                target = requireNotNull(target.url),
                control = control?.url,
                returnedHosts = hosts,
                headers = mediaHeaders(),
                kinds = setOf(StreamCheck.Kind.SIG),
                budgetKey = id,
                cacheKey = id,
                userStarted = true,
            ),
        )
        val dropped = buildList {
            if (checked.target == StreamCheck.StatusClass.FORBIDDEN_403) add(targetList)
            if (checked.control == StreamCheck.StatusClass.FORBIDDEN_403) add(if (targetList == ADAPTIVE) FORMATS else ADAPTIVE)
        }.distinct()
        notes += "V $id: ${checked.verdict} (${checked.reason}" + (if (dropped.isEmpty()) ")" else "; $dropped refused)")
        for (list in dropped) (if (list == ADAPTIVE) adaptive else progressive).mapNotNullTo(refused, YouTubeStream::url)
        return VStep(passed = checked.verdict == StreamCheck.Verdict.PASS, tokenNeeded = checked.strategyFailed)
    }

    /** L: canonical JSON with only the rows that passed V and the init check in this lookup. */
    private suspend fun lRows(verdict: AnswerJudge.Verdict, body: String): String? {
        val root = verdict.root ?: return null
        if (LocatedShapeMap.record(ladder.health, VISION_OS, root, verdict.scan, videoId)) notes += "L: shape map recorded"
        val outcome = ShapeNormalizer.normalize(root, body, videoId, now)
        normalized = outcome
        val rows = outcome as? ShapeNormalizer.Rows ?: return null.also { notes += "L: ${outcome.javaClass.simpleName}" }
        val reader = ladder.reader ?: return null.also { notes += "L: no byte read, so no L row" }
        val checked = LRows.check(rows.offer) { row -> check(reader, row) }
        notes += "L: ${checked.rows.size} of ${rows.offer.size} rows passed V and the init check"
        if (checked.rows.isEmpty()) return null
        return LadderJson.keepRows(rows.canonicalJson, checked.rows.mapNotNull(YouTubeStream::url).toSet())
    }

    /** An L row's V and init check on one read of its first bytes (≤ 8 a lookup, the host YouTube returned). */
    private suspend fun check(reader: MediaStartReader, row: YouTubeStream): StreamCheck.Verdict {
        val url = row.url ?: return StreamCheck.Verdict.UNKNOWN
        if (!YouTubeUrls.isMediaServerRequest(url) || lReads >= ShapeNormalizer.MAX_OFFER) return StreamCheck.Verdict.UNKNOWN
        lReads += 1
        return when (val read = reader.read(url, mediaHeaders(), START_BYTES)) {
            is MediaStart.Failed -> if (read.statusCode in FAIL_STATUSES) StreamCheck.Verdict.FAIL else StreamCheck.Verdict.UNKNOWN
            is MediaStart.Read -> {
                if (read.statusCode !in OK_STATUSES) return StreamCheck.Verdict.UNKNOWN
                val total = InitCheck.totalOf(read.contentRange) ?: read.contentLength.takeIf { read.statusCode == HTTP_OK }
                InitCheck.check(row.mimeType, row.contentLengthBytes, InitCheck.Observed(read.contentType, read.bytes, total)).verdict
            }
        }
    }

    /** Z: the offered rows' missing sizes from the one-byte probe; other sizeless formats left out. */
    private suspend fun zSizes(verdict: AnswerJudge.Verdict, body: String): String? {
        val video = verdict.video ?: return null
        val offer = ShapeNormalizer.offerSet(video)
        val filled = sizes.fill(offer, mediaHeaders())
        notes += "Z: ${filled.filled} sizes from ${filled.probes} probes"
        if (filled.filled == 0) return null
        val known = filled.rows.mapNotNull { row ->
            val url = row.url ?: return@mapNotNull null
            row.contentLengthBytes?.takeIf { it > 0 }?.let { url to it }
        }.toMap()
        return LadderJson.withSizes(body, known)
    }

    private suspend fun merge(result: SiteExtractionResult): SiteExtractionResult {
        if (ladder.trial.hOnly) return hOnly(result)
        if (verdictAboutVideo) return withNotes(result)
        if (result is SiteExtractionResult.Failure && result.reason in VERDICTS) return withNotes(result)
        val chainRows = (result as? SiteExtractionResult.Success)?.candidates.orEmpty()
        val rows = chainRows.filter { row -> row.mediaUrl !in refused && row.audioCompanion?.mediaUrl !in refused }
        val kept = if (rows.size == chainRows.size) result else keptOnly(result as SiteExtractionResult.Success, rows)
        if (complete(rows)) return withNotes(kept)
        val verdict = vision ?: return withNotes(kept)
        if (verdict.terminal) return withNotes(kept)
        val h = hRow(verdict) ?: return withNotes(kept)
        if (rows.any { it.mediaUrl == h.candidate.mediaUrl }) return withNotes(kept)
        ladder.health.repaired(VISION_OS, ClientHealth.Rung.H)
        notes += "H: one HLS row (${h.row.detail()})"
        return SiteExtractionResult.Success(rows + h.candidate, detailsOf(kept) + notes.map { "ladder $it" })
    }

    /**
     * D5c's phone check (the `h-only` trial, debug builds only): the result is H's one HLS row
     * alone — the chain's rows are left out, complete or not, and nothing is recorded as repaired —
     * so the owner's download goes through H. Without an H row: no row (the chain's failure, else
     * "no media found"), with H's reason in the notes.
     */
    private suspend fun hOnly(result: SiteExtractionResult): SiteExtractionResult {
        val chainRows = (result as? SiteExtractionResult.Success)?.candidates.orEmpty()
        val left = chainRows.size
        notes += "trial ${ladder.trial.label}: the chain's $left ${if (left == 1) "row" else "rows"} left out"
        val verdict = vision
        val h = when {
            verdict == null -> null.also { notes += "H: no visionOS answer" }
            verdict.terminal -> null.also { notes += "H: visionOS's answer ${verdict.answerClass}" }
            else -> hRow(verdict)
        }
        if (h == null) {
            val failure = result as? SiteExtractionResult.Failure
                ?: SiteExtractionResult.Failure(SiteExtractionFailure.NO_MEDIA_FOUND, details = detailsOf(result))
            return withNotes(failure)
        }
        notes += "H: one HLS row (${h.row.detail()})"
        return SiteExtractionResult.Success(listOf(h.candidate), detailsOf(result) + notes.map { "ladder $it" })
    }

    /** H's row and the candidate the app gets for it. */
    private class HRow(val row: HlsLeg.Row, val candidate: MediaCandidate)

    /**
     * The H leg on visionOS's answer [verdict] (one H check per lookup): its HLS address bound to
     * the video's length (the copied parser's, else L's binding). The candidate carries the video
     * key the module tags the chain's rows with ("site:contentId"), so the app's browser hook
     * focuses it with them. Null, with H's reason in the notes, when the check gives no row.
     */
    private suspend fun hRow(verdict: AnswerJudge.Verdict): HRow? {
        val master = verdict.hls ?: return null.also { notes += "H: no HLS address" }
        val parsed = verdict.response?.result
        val bound = normalized ?: verdict.root?.takeIf { (parsed as? YouTubeParseResult.Success)?.video?.durationMillis == null }
            ?.let { ShapeNormalizer.normalize(it, visionBody, videoId, now) }
        val length = HlsLeg.length(parsed, videoId, bound, null)
        val outcome = hls.run(master, length, mediaHeaders())
        val row = outcome as? HlsLeg.Row
        if (row == null) {
            notes += "H: ${(outcome as? HlsLeg.None)?.why ?: "none"}"
            return null
        }
        val video = verdict.video
        val candidate = row.candidate(
            pageUrl = YouTubeUrls.canonicalUrl(videoId),
            title = video?.title,
            thumbnailUrl = video?.thumbnailUrl,
            userAgent = request.requestContext.userAgent,
            nowEpochMs = now,
        ).copy(videoId = "${request.identity.siteId}:$videoId")
        return HRow(row, candidate)
    }

    private fun detailsOf(result: SiteExtractionResult): List<String> = when (result) {
        is SiteExtractionResult.Success -> result.details
        is SiteExtractionResult.Failure -> result.details
    }

    /** The chain's result without the refused rows; none left → no media found. */
    private fun keptOnly(result: SiteExtractionResult.Success, rows: List<MediaCandidate>): SiteExtractionResult {
        notes += "merge: ${result.candidates.size - rows.size} refused rows removed"
        return if (rows.isEmpty()) {
            SiteExtractionResult.Failure(SiteExtractionFailure.NO_MEDIA_FOUND, details = result.details)
        } else {
            result.copy(candidates = rows)
        }
    }

    private fun withNotes(result: SiteExtractionResult): SiteExtractionResult = when {
        notes.isEmpty() -> result
        result is SiteExtractionResult.Success -> result.copy(details = result.details + notes.map { "ladder $it" })
        result is SiteExtractionResult.Failure -> result.copy(details = result.details + notes.map { "ladder $it" })
        else -> result
    }

    /** P22: a video merged with its audio track and the audio track itself, or an HLS row. */
    private fun complete(rows: List<MediaCandidate>): Boolean =
        rows.any { it.kind == MediaKind.HLS } || (
            rows.any { it.audioCompanion != null } &&
                rows.any { it.audioCompanion == null && it.mimeType.orEmpty().startsWith("audio/") }
            )

    /** The download's headers without a cookie (guardrail 6). */
    private fun mediaHeaders(): Map<String, String> = buildMap {
        request.requestContext.userAgent?.takeIf(String::isNotBlank)?.let { put("User-Agent", it) }
        put("Referer", YouTubeUrls.canonicalUrl(videoId))
    }

    private fun hostOf(url: String): String? = runCatching { URI(url).host?.lowercase() }.getOrNull()

    companion object {
        const val VISION_OS = "visionos"
        const val PLAYER_PATH = "/youtubei/v1/player"
        const val START_BYTES = 4096
        const val HTTP_OK = 200
        const val MAX_V_PER_LOOKUP = 1

        /** A's player requests in one lookup (LookupMemo: ≤ 3 player requests of the ladder's own). */
        const val MAX_A_PER_LOOKUP = 3
        const val ADAPTIVE = "adaptiveFormats"
        const val FORMATS = "formats"
        val OK_STATUSES = setOf(200, 206)
        val FAIL_STATUSES = 400..499

        /** V's verdicts are never reused across lookups: the health's last-hour pass decides. */
        val V_POLICY = StreamCheck.Policy(cacheMillis = 0)
        val VERDICTS = setOf(
            SiteExtractionFailure.LOGIN_REQUIRED,
            SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
            SiteExtractionFailure.GEO_RESTRICTED,
            SiteExtractionFailure.DRM_PROTECTED,
        )
    }
}

/** The JSON edits the ladder hands the chain: rows kept by address, sizes set; nothing else changes. */
internal object LadderJson {
    private val LISTS = listOf("formats", "adaptiveFormats")

    /** [json] with only the formats whose address is in [urls] (a format without one is left out). */
    fun keepRows(json: String, urls: Set<String>): String? =
        edit(json) { format -> format.takeIf { (it.entries["url"] as? JsonValue.Text)?.value in urls } }

    /** [json] with [sizes] (by address) as `contentLength`; a format still without a size is left out. */
    fun withSizes(json: String, sizes: Map<String, Long>): String? = edit(json) { format ->
        val url = (format.entries["url"] as? JsonValue.Text)?.value
        val size = url?.let(sizes::get)
        when {
            size != null -> JsonValue.Object(LinkedHashMap(format.entries).apply { put("contentLength", JsonValue.Text(size.toString())) })
            url != null && !hasSize(format) -> null
            else -> format
        }
    }

    private fun hasSize(format: JsonValue.Object): Boolean = when (val value = format.entries["contentLength"]) {
        is JsonValue.Text -> (value.value.toLongOrNull() ?: 0L) > 0L
        is JsonValue.Number -> (value.text.toLongOrNull() ?: 0L) > 0L
        else -> false
    }

    private fun edit(json: String, keep: (JsonValue.Object) -> JsonValue.Object?): String? {
        val root = ShapeLocator.parse(json) as? JsonValue.Object ?: return null
        val streaming = root.entries["streamingData"] as? JsonValue.Object ?: return null
        val data = LinkedHashMap(streaming.entries)
        for (key in LISTS) {
            val list = data[key] as? JsonValue.Array ?: continue
            data[key] = JsonValue.Array(
                list.items.mapNotNull { item -> (item as? JsonValue.Object)?.let(keep) ?: item.takeIf { it !is JsonValue.Object } },
            )
        }
        val top = LinkedHashMap(root.entries).apply { put("streamingData", JsonValue.Object(data)) }
        return StringBuilder().also { write(it, JsonValue.Object(top)) }.toString()
    }

    /** [value] as compact JSON text (the trial's changed answer). */
    fun text(value: JsonValue): String = StringBuilder().also { write(it, value) }.toString()

    private fun write(out: StringBuilder, value: JsonValue) {
        when (value) {
            JsonValue.Null -> out.append("null")
            is JsonValue.Bool -> out.append(value.value)
            is JsonValue.Number -> out.append(value.text)
            is JsonValue.Text -> quote(out, value.value)
            is JsonValue.Array -> {
                out.append('[')
                value.items.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    write(out, item)
                }
                out.append(']')
            }
            is JsonValue.Object -> {
                out.append('{')
                var index = 0
                for ((key, item) in value.entries) {
                    if (index++ > 0) out.append(',')
                    quote(out, key)
                    out.append(':')
                    write(out, item)
                }
                out.append('}')
            }
        }
    }

    private fun quote(out: StringBuilder, text: String) {
        out.append('"')
        for (char in text) {
            when {
                char == '"' -> out.append("\\\"")
                char == '\\' -> out.append("\\\\")
                char < ' ' -> out.append("\\u").append(String.format("%04x", char.code))
                else -> out.append(char)
            }
        }
        out.append('"')
    }
}
