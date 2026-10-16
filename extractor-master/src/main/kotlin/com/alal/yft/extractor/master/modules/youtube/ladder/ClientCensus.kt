package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.core.model.media.PageNavigationHeaders
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.PlayerScriptChallenge
import com.alal.yft.extractor.api.PlayerScriptChallengeKind
import com.alal.yft.extractor.api.PlayerScriptRequest
import com.alal.yft.extractor.api.PlayerScriptResult
import com.alal.yft.extractor.api.PlayerScriptRunner
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.api.json.path
import com.alal.yft.extractor.master.modules.youtube.StreamCheck
import com.alal.yft.extractor.master.modules.youtube.YouTubeClientProfile
import com.alal.yft.extractor.master.modules.youtube.YouTubeClientProfiles
import com.alal.yft.extractor.master.modules.youtube.YouTubePageSignals
import com.alal.yft.extractor.master.modules.youtube.YouTubePlayerResponseParser
import com.alal.yft.extractor.master.modules.youtube.YouTubeStream
import com.alal.yft.extractor.master.modules.youtube.YouTubeUrls
import com.alal.yft.extractor.master.modules.youtube.YouTubeVideo
import java.net.URI
import java.net.URLDecoder
import kotlinx.coroutines.CancellationException

/**
 * Phase 1.3 D0: the owner's client census and weekly check (MASTER_KEY_PHASE1_3_PLAN.md §4 D0).
 *
 * **Full census**, per video in list order: (1) visionOS as R6 asks it — class, counts, heights,
 * V on one AVC row, the shape fingerprint, and the H check with the token step when an HLS
 * address is present; (2)
 * visionOS again with the page's signature timestamp and visitor data, only when (1) carried
 * `signatureCipher` (S through the own solver, V); (3) video 1 only: each bundled alternate
 * version (none are bundled yet: NewPipe's 1.04 waits for the A check); (4) Android — class,
 * progressive only or not, V; (5) the embedded player with the page's version and embed context,
 * as main asks it — class, S through the own solver, V; (6) MWEB — class, S, V. After video 1 the
 * public version sources are read once ([CensusSources]); a new valid value newer than the
 * bundled one is tried once (`SH`: one request and V) on the first video in list order without a
 * bot check and with a watch page (B7, owner 2026-10-15), with MWEB's formats without an address
 * compared between the bundled and the newer version. Every answer is classed by D2's
 * [AnswerJudge].
 *
 * **Weekly check**: videos 1 and 5 visionOS (V on one row, H on video 1), then Android, embedded
 * and MWEB once on video 1 (no S), the version sources only when not read in 24 h, and the
 * version acceptance on video 1; ≤ 60 s, ≤ 2 MB and ≤ 20 requests. When video 1 met a bot check
 * (owner, 2026-10-15): H runs on its `R0+vd` answer when that is `DIRECT` (B5), and video 2 (no bot
 * check in valid runs 1–3) gets the rest under a soft budget ([weeklySecond]): visionOS for H only
 * when no H ran yet, then its watch page, Android and MWEB (B6) and the version acceptance (B7).
 *
 * **A check** (D5a, owner 2026-10-16, option 2; [aCheck]): video 2 only — visionOS 1.02 as R6 asks
 * it (`R0`, no visitor data) and, only when that answer is `DIRECT`, the same request once more
 * with only the version changed to NewPipe's 1.04 ([A_CHECK_ALTERNATE]; as the ladder's A rung
 * changes it), then V on one row of that answer; ≤ 4 requests and ≤ 2 MB. A bot check or HTTP 429
 * on either request stops it at once with no run for 30 min. The run is reported, not kept for
 * the diff.
 *
 * **Bot checks** (owner, 2026-10-15): after a bot check the census never sends a request R6 would
 * not send, and like R6 it answers each lookup (each video) on its own. A bot check on visionOS's
 * first request of a video (no visitor data: R6's own first request) is answered as R6 answers it
 * ([answerAsR6]): that video's watch page once and visionOS once more with the page's visitor data
 * (`R0+vd`, R6's `visionOsInChain` request), V on one row of that answer (a one-byte read of a row
 * the download reads) and, when it is `DIRECT`, the H check on its HLS address ((က+), owner
 * 2026-10-15: reads on `manifest.googlevideo.com` and the media host, no YouTube request), nothing
 * else for that video, and the run goes on with the next video. A
 * bot check on a request that already carried visitor data (`R0+vd`, or a client asked with the
 * page's visitor data) stops the run with no run for 30 min; so does HTTP 429 (no retry). A bot
 * check on any other request without visitor data ends that video only: R6 has no visitor data
 * to ask again with.
 *
 * Every request is built by the copied request builder (`YouTubeClientProfile.playerRequestBody`)
 * with R6's headers, without a cookie or a token (guardrail 3). V is the shared [StreamCheck]
 * (one-byte read, only on hosts this video's answers returned, the download's User-Agent and
 * Referer). The report and the state hold classes, counts, verdicts, times, bytes, public version
 * strings and key-path fingerprints, never an address, a value, visitor data or a video ID
 * (videos are numbered; [CensusSanitizer] is the last guard). It runs only while its owner tool
 * is open and after a tap (guardrail 11); it schedules nothing.
 */
class ClientCensus(
    private val http: ExtractorHttpClient,
    private val scripts: PlayerScriptRunner,
    private val browserUserAgent: String,
    private val meter: Meter = Meter.NONE,
    private val clock: () -> Long = System::currentTimeMillis,
    private val toolLabel: String = TOOL_LABEL,
) {
    /** [A_CHECK]: D5a's A check (owner, 2026-10-16), whether visionOS 1.04 may be bundled; not a census run. */
    enum class Mode { FULL, WEEKLY, A_CHECK }

    /** Wire bytes the census's HTTP client moved so far; negative when nothing measures them. */
    fun interface Meter {
        fun bytes(): Long

        companion object {
            val NONE: Meter = Meter { -1L }
        }
    }

    /** [report] and [stateJson] are sanitized; [firstLine] is the diff's headline. */
    class Outcome(
        val firstLine: String,
        val report: String,
        val stateJson: String,
        val stopped: String?,
    ) {
        override fun toString(): String = "ClientCensus.Outcome(firstLine=$firstLine, stopped=$stopped)"
    }

    /**
     * Runs [mode] over [videos] (slot 1–10 → video ID) against the stored [stateJson] (the file
     * `census-last.json`, or null). [context] lines (the network type) go into the run's notes;
     * [onStep] gets short progress labels.
     */
    suspend fun run(
        mode: Mode,
        videos: Map<Int, String>,
        stateJson: String?,
        context: List<String> = emptyList(),
        onStep: (String) -> Unit = {},
    ): Outcome {
        val state = CensusJson.readState(stateJson)
        val startedAt = clock()
        if (state.cooldownUntil > startedAt) {
            val minutes = (state.cooldownUntil - startedAt + MINUTE_MILLIS - 1) / MINUTE_MILLIS
            val line = "COOLDOWN — a bot check stopped an earlier run; try again in $minutes min"
            return Outcome(line, line, CensusJson.state(state), "cooldown")
        }
        val chosen = videos.filter { (slot, id) -> slot in 1..MAX_VIDEOS && VIDEO_ID.matches(id) }.toSortedMap()
        val session = Session(mode, startedAt, onStep)
        session.notes += context.map { CensusSanitizer.reason(it, max = 80) ?: "" }.filter(String::isNotEmpty)
        videos.keys.filter { it !in chosen.keys }.sorted().forEach { session.notes += "video $it: not a video ID, skipped" }
        when (mode) {
            Mode.FULL -> full(session, chosen)
            Mode.WEEKLY -> weekly(session, chosen, state)
            Mode.A_CHECK -> aCheck(session, chosen)
        }
        val run = CensusRun(
            tool = toolLabel,
            mode = mode.name,
            atMillis = startedAt,
            millis = clock() - startedAt,
            bytes = session.bytes(),
            metered = session.metered,
            requests = session.requests,
            stopped = session.stopped,
            slots = chosen.mapValues { CensusJson.tagOf(it.value) },
            pages = session.pages,
            asks = session.asks,
            hls = session.hls,
            versions = session.versions,
            notes = session.notes,
        )
        val secrets = chosen.values + session.secrets
        val summary = "$toolLabel · ${mode.name} · ${CensusJson.dateOf(startedAt)} · ${videosAsked(run)} · " +
            "${run.requests} requests · ${kilobytes(run.bytes)} KB${if (run.metered) "" else " (bodies only)"} · " +
            "${run.millis / SECOND_MILLIS} s" + (run.stopped?.let { " · stopped: $it" } ?: "")
        if (mode == Mode.A_CHECK) {
            // Not a census run: no diff, and the stored runs stay as they were; only a cooldown is kept.
            val kept = state.copy(cooldownUntil = if (session.cooldown) clock() + COOLDOWN_MILLIS else state.cooldownUntil)
            val headline = aCheckHeadline(run)
            val report = buildString {
                append(headline).append('\n')
                append(summary).append('\n')
                append(A_CHECK_RULE).append('\n')
                append(CensusJson.run(run))
            }
            return Outcome(
                firstLine = CensusSanitizer.clean(headline, secrets),
                report = CensusSanitizer.clean(report, secrets),
                stateJson = CensusSanitizer.clean(CensusJson.state(kept), secrets),
                stopped = run.stopped,
            )
        }
        val diff = CensusDiff.compare(state.runs, run)
        val next = CensusState(
            runs = (state.runs + run).sortedBy(CensusRun::atMillis).takeLast(CensusJson.MAX_RUNS),
            cooldownUntil = if (session.cooldown) clock() + COOLDOWN_MILLIS else state.cooldownUntil,
            sourcesAt = if (session.sourcesRead) startedAt else state.sourcesAt,
        )
        val report = buildString {
            append(diff.text()).append('\n')
            append(summary).append('\n')
            append(MasterClientTable.describe()).append('\n')
            append(CensusJson.run(run))
        }
        return Outcome(
            firstLine = diff.headline,
            report = CensusSanitizer.clean(report, secrets),
            stateJson = CensusSanitizer.clean(CensusJson.state(next), secrets),
            stopped = run.stopped,
        )
    }

    // ---- the modes ----------------------------------------------------------------------------

    private suspend fun full(session: Session, videos: Map<Int, String>) {
        var sourcesDone = false
        var accepted = false
        for ((slot, id) in videos) {
            if (session.stopped != null) break
            val video = fullVideo(session, slot, id)
            if (!sourcesDone && session.stopped == null) {
                sourcesDone = true
                readSources(session, video)
            }
            if (sourcesDone && !accepted && session.stopped == null && acceptable(session, video)) {
                accepted = true
                accept(session, video)
            }
        }
        if (sourcesDone && !accepted && session.stopped == null) accept(session, null)
        lSummary(session)
    }

    /**
     * D5b / D7 census L steps on each live `DIRECT` visionOS answer (full census only; in memory,
     * no request): (1) the L round-trip — the canonical JSON's offer set is the direct parse's;
     * (2) L1, L2, L3, L5 and L7 ([LadderTrial.mutate]) on the answer — L finds the same rows
     * (itag and address) again. L's rows are the direct parse's own addresses, so the answer's V
     * (one row, above) stands for them; the app's ladder checks every L row itself.
     * No rows reads `none (<why>)`, or `none (stop <reason>)` when L stops as the copied parser
     * would (D7, 2026-10-15); a stop on DRM names where L's DRM marker sits (a key path, never a
     * value).
     */
    private fun lSteps(session: Session, video: Video, asked: Asked) {
        if (session.mode != Mode.FULL || asked.record.answerClass != CensusClass.DIRECT) return
        val root = asked.root as? JsonValue.Object ?: return
        val expected = asked.video?.let(ShapeNormalizer::offerSet)?.map { it.itag to it.url }?.toSet().orEmpty()
        if (expected.isEmpty()) return
        val now = clock()
        fun found(answer: String): String = when (val outcome = ShapeNormalizer.normalize(answer, video.id, now)) {
            is ShapeNormalizer.Rows -> if (outcome.offer.map { it.itag to it.url }.toSet() == expected) "same" else "differs"
            is ShapeNormalizer.None -> "none (${outcome.why})"
            is ShapeNormalizer.Stop -> "none (stop ${outcome.failure.reason}" +
                (if (outcome.failure.reason == SiteExtractionFailure.DRM_PROTECTED) drmMarker(answer, video.id) else "") + ")"
        }
        val results = linkedMapOf("round-trip" to found(LadderJson.text(root)))
        for (id in LadderTrial.MUTATIONS) {
            results[id] = LadderTrial.mutate(id, root)?.let { found(LadderJson.text(it)) } ?: "n/a"
        }
        session.lSteps += results
        session.notes += "video ${video.slot} L (${asked.record.step}, ${expected.size} rows, V ${asked.record.v ?: "none"}): " +
            results.entries.joinToString(", ") { "${it.key} ${it.value}" }
    }

    /** Where L's DRM marker fired on [answer] (ShapeLocator's key path, `*` for a key that is not key-like), or "". */
    private fun drmMarker(answer: String, videoId: String): String {
        val root = ShapeLocator.parse(answer) ?: return ""
        val path = ShapeLocator.scan(root, videoId, answer).drmPath ?: return ""
        return "; DRM marker at $path"
    }

    /** The L steps' counts for the report: per step, how many answers L read as the direct parse. */
    private fun lSummary(session: Session) {
        if (session.lSteps.isEmpty()) return
        val total = session.lSteps.size
        val steps = session.lSteps.first().keys
        session.notes += "L steps on $total visionOS answers (in memory, no request): " + steps.joinToString(", ") { step ->
            val values = session.lSteps.map { it[step] }
            val same = values.count { it == "same" }
            val skipped = values.count { it == "n/a" }
            "$step $same/${total - skipped} same" + if (skipped > 0) " ($skipped n/a)" else ""
        }
    }

    private suspend fun fullVideo(session: Session, slot: Int, id: String): Video {
        val video = Video(slot, id)
        session.step("video $slot · visionOS")
        val first = ask(session, video, VISION_OS, R0, NO_PAGE) ?: return video
        video.visionOs = first
        if (first.record.answerClass == CensusClass.BOT_CHECK) {
            session.finish(first)
            answerAsR6(session, video)
            return video
        }
        directCheck(session, video, first, preferProgressive = false)
        lSteps(session, video, first)
        hlsCheck(session, video, first)
        session.finish(first)
        if (session.stopped != null) return video

        session.step("video $slot · watch page")
        val page = page(session, video)
        video.page = page

        if (first.record.protectedFormats > 0 && session.open(video)) {
            session.step("video $slot · visionOS with the page's signals")
            val signals = NO_PAGE.copy(
                visitorData = page?.visitorData,
                signatureTimestamp = page?.signatureTimestamp,
            )
            ask(session, video, VISION_OS.copy(usesPlayerScript = true), "S", signals)?.let { again ->
                solveAndCheck(session, video, again, page)
                session.finish(again)
            }
        }
        if (slot == FIRST_VIDEO && ALTERNATES.isEmpty()) {
            session.notes += "alternates: none bundled (yt-dlp ${YouTubeClientProfiles.DEVICE_VALUES_SOURCE.substringAfter(' ')} " +
                "and YouTube.js list visionOS 1.02 only), so step 3 asks nothing"
        }
        for ((index, version) in ALTERNATES.withIndex()) {
            if (slot != FIRST_VIDEO || !session.open(video)) break
            ask(session, video, VISION_OS.copy(clientVersion = version), "A${index + 1}", NO_PAGE)?.let { alternate ->
                directCheck(session, video, alternate, preferProgressive = false)
                session.finish(alternate)
            }
        }

        if (session.open(video)) {
            session.step("video $slot · Android")
            ask(session, video, ANDROID, R0, page ?: NO_PAGE)?.let { android ->
                video.android = android
                directCheck(session, video, android, preferProgressive = true)
                session.finish(android)
            }
        }
        if (page == null && session.open(video)) {
            session.notes += "video $slot: no watch page, so the embedded player and MWEB were not asked"
            return video
        }
        if (page != null && session.open(video)) {
            session.step("video $slot · embedded")
            ask(session, video, YouTubeClientProfiles.embedded(page), R0, page)?.let { embedded ->
                video.embedded = embedded
                solveAndCheck(session, video, embedded, page)
                session.finish(embedded)
            }
        }
        if (page != null && session.open(video)) {
            session.step("video $slot · MWEB")
            ask(session, video, YouTubeClientProfiles.mobileWeb(), R0, page)?.let { mobile ->
                video.mweb = mobile
                solveAndCheck(session, video, mobile, page)
                session.finish(mobile)
            }
        }
        return video
    }

    private suspend fun weekly(session: Session, videos: Map<Int, String>, state: CensusState) {
        val firstId = videos[FIRST_VIDEO]
        if (firstId == null) {
            session.notes += "video 1 is not set, so the weekly check asked nothing"
            return
        }
        val first = Video(FIRST_VIDEO, firstId)
        session.step("video 1 · visionOS")
        ask(session, first, VISION_OS, R0, NO_PAGE)?.let { answer ->
            first.visionOs = answer
            firstAnswer(session, first, answer) {
                directCheck(session, first, answer, preferProgressive = false)
                hlsCheck(session, first, answer)
            }
        }
        val fifthId = videos[WEEKLY_SECOND_VIDEO]
        if (fifthId == null) {
            session.notes += "video $WEEKLY_SECOND_VIDEO is not set, so the weekly check skipped it"
        } else if (session.stopped == null) {
            val fifth = Video(WEEKLY_SECOND_VIDEO, fifthId)
            session.step("video $WEEKLY_SECOND_VIDEO · visionOS")
            ask(session, fifth, VISION_OS, R0, NO_PAGE)?.let { answer ->
                firstAnswer(session, fifth, answer) { directCheck(session, fifth, answer, preferProgressive = false) }
            }
        }
        if (session.stopped != null) return
        if (first.slot in session.answeredAsR6) {
            session.notes += "video 1: answered as R6 after its bot check, so Android, the embedded player and MWEB were not asked"
        } else {
            weeklyClients(session, first)
        }
        if (session.stopped != null) return
        if (startedAgo(state.sourcesAt) >= DAY_MILLIS) {
            readSources(session, first)
        } else {
            session.notes += "version sources: read less than 24 h ago, not read again"
        }
        if (session.stopped != null) return
        if (first.slot in session.answeredAsR6) {
            weeklySecond(session, videos)
        } else {
            accept(session, first.takeIf { acceptable(session, it) })
        }
    }

    /**
     * The weekly check after video 1's bot check (owner, 2026-10-15): video 1 was answered as R6,
     * so video 2 (no bot check in valid runs 1–3) gets what video 1 could not. visionOS is asked
     * on it as R6 asks it (V on one row) for the H check only when no `R0+vd` answer gave one (B5);
     * then its watch page, Android (V) and MWEB (V on a row without a script) — B6 — and the version
     * acceptance on it (B7). Its own bot check is answered as R6, and then no other client is
     * asked. Under a soft budget: what does not fit in the 20 requests is not asked, the run goes
     * on (not stopped), and a note names what was left out.
     */
    private suspend fun weeklySecond(session: Session, videos: Map<Int, String>) {
        val id = videos[WEEKLY_HLS_VIDEO]
        if (id == null) {
            val h = if (session.hls.isEmpty()) "no H check ran and " else ""
            session.notes += "video 1 met a bot check and video $WEEKLY_HLS_VIDEO is not set, so ${h}no other client was asked"
            accept(session, null)
            return
        }
        val video = Video(WEEKLY_HLS_VIDEO, id)
        session.soft = true
        try {
            if (session.hls.isEmpty()) {
                session.step("video $WEEKLY_HLS_VIDEO · visionOS")
                ask(session, video, VISION_OS, R0, NO_PAGE)?.let { answer ->
                    video.visionOs = answer
                    firstAnswer(session, video, answer) {
                        directCheck(session, video, answer, preferProgressive = false)
                        hlsCheck(session, video, answer)
                    }
                }
                if (session.stopped != null) return
                session.notes += when {
                    session.hls.any { it.video == video.slot } -> "H: video 1 met a bot check, so the H check ran on video $WEEKLY_HLS_VIDEO"
                    video.slot in session.answeredAsR6 -> "H: video 1 and video $WEEKLY_HLS_VIDEO met a bot check and gave no H check"
                    else -> "H: video 1 met a bot check and video $WEEKLY_HLS_VIDEO's visionOS answer had no HLS address, so no H check ran"
                }
            }
            if (video.slot in session.answeredAsR6 || video.closed) {
                session.notes += "video $WEEKLY_HLS_VIDEO: answered as R6 after its bot check, so Android and MWEB were not asked"
                accept(session, null)
                return
            }
            session.step("video $WEEKLY_HLS_VIDEO · watch page")
            val page = page(session, video)
            video.page = page
            if (session.open(video)) {
                session.step("video $WEEKLY_HLS_VIDEO · Android")
                ask(session, video, ANDROID, R0, page ?: NO_PAGE)?.let { answer ->
                    video.android = answer
                    directCheck(session, video, answer, preferProgressive = true)
                    session.finish(answer)
                }
            }
            if (page != null && session.open(video)) {
                session.step("video $WEEKLY_HLS_VIDEO · MWEB")
                ask(session, video, YouTubeClientProfiles.mobileWeb(), R0, page)?.let { answer ->
                    video.mweb = answer
                    checkWithoutScript(session, video, answer)
                    session.finish(answer)
                }
            } else if (page == null && session.open(video) && session.left.isEmpty()) {
                session.notes += "video $WEEKLY_HLS_VIDEO: no watch page, so MWEB was not asked"
            }
            if (session.stopped == null) accept(session, video.takeIf { acceptable(session, it) })
        } finally {
            session.soft = false
            if (session.left.isNotEmpty()) {
                session.notes += "within the weekly check's $WEEKLY_MAX_REQUESTS requests, not asked: " +
                    session.left.joinToString(", ") + " (the run went on)"
            }
        }
    }

    /** The weekly check's other clients on video 1: its watch page, Android, embedded and MWEB. */
    private suspend fun weeklyClients(session: Session, first: Video) {
        session.step("video 1 · watch page")
        val page = page(session, first)
        first.page = page
        if (session.open(first)) {
            session.step("video 1 · Android")
            ask(session, first, ANDROID, R0, page ?: NO_PAGE)?.let { answer ->
                first.android = answer
                directCheck(session, first, answer, preferProgressive = true)
                session.finish(answer)
            }
        }
        if (page != null && !first.closed) {
            for (profile in listOf(YouTubeClientProfiles.embedded(page), YouTubeClientProfiles.mobileWeb())) {
                if (!session.open(first)) break
                session.step("video 1 · ${profile.clientName}")
                ask(session, first, profile, R0, page)?.let { answer ->
                    if (profile.id == "embedded") first.embedded = answer else first.mweb = answer
                    checkWithoutScript(session, first, answer)
                    session.finish(answer)
                }
            }
        } else if (page == null) {
            session.notes += "video 1: no watch page, so the embedded player and MWEB were not asked"
        }
    }

    private fun startedAgo(at: Long): Long = if (at <= 0) Long.MAX_VALUE else clock() - at

    /**
     * D5a's A check (owner, 2026-10-16, option 2): may NewPipe's visionOS 1.04 be bundled as
     * visionOS's alternate (guardrail 5)? On video 2 only (no bot check in valid runs 1–3):
     * visionOS 1.02 as R6 asks it (`R0`, no visitor data, no page); only when that answer is
     * `DIRECT` (YouTube answers this address now), the same request once more with only the
     * version changed (the body's `clientVersion` and `X-YouTube-Client-Version`, as the ladder's
     * A rung changes them), then, when that answer is `DIRECT`, V on one of its rows (the AVC row
     * and a control). A bot check or HTTP 429 on either request stops it at once ([ask]): no
     * watch page, no `R0+vd`, no run for 30 min. Nothing else is asked, and 1.04 is not bundled
     * here: the owner's phone result decides ([aCheckHeadline]).
     */
    private suspend fun aCheck(session: Session, videos: Map<Int, String>) {
        val id = videos[A_CHECK_VIDEO]
        if (id == null) {
            session.notes += "video $A_CHECK_VIDEO is not set, so the A check asked nothing"
            return
        }
        val video = Video(A_CHECK_VIDEO, id)
        session.step("video $A_CHECK_VIDEO · visionOS ${VISION_OS.clientVersion}")
        val bundled = ask(session, video, VISION_OS, R0, NO_PAGE) ?: return
        session.finish(bundled)
        if (session.stopped != null) return
        if (bundled.record.answerClass != CensusClass.DIRECT) {
            session.notes += "A check: visionOS ${VISION_OS.clientVersion} answered ${bundled.record.answerClass}, " +
                "so ${A_CHECK_ALTERNATE.version} was not asked"
            return
        }
        session.step("video $A_CHECK_VIDEO · visionOS ${A_CHECK_ALTERNATE.version}")
        val alternate = ask(session, video, VISION_OS.copy(clientVersion = A_CHECK_ALTERNATE.version), A_CHECK_STEP, NO_PAGE) ?: return
        if (session.stopped == null && alternate.record.answerClass == CensusClass.DIRECT) {
            directCheck(session, video, alternate, preferProgressive = false)
        }
        session.finish(alternate)
    }

    /** The A check's first line: what visionOS 1.02 and 1.04 answered, and whether 1.04 may be bundled. */
    private fun aCheckHeadline(run: CensusRun): String {
        val head = "A CHECK — "
        val alternate = A_CHECK_ALTERNATE.version
        val cooldown = "; no census for ${COOLDOWN_MILLIS / MINUTE_MILLIS} min"
        val bundled = run.asks.firstOrNull { it.step == R0 }
        val asked = run.asks.firstOrNull { it.step == A_CHECK_STEP }
        fun answered(ask: CensusAsk): String =
            "visionOS ${ask.version} ${ask.finalClass}" + if (ask.http == HTTP_OK) "" else " (http ${ask.http})"
        fun stop(ask: CensusAsk, why: String?): String {
            val what = if (why == "bot-check") "bot check" else "HTTP 429"
            val then = if (ask === bundled) ", so $alternate was not asked" else " (${bundled?.let(::answered)})"
            return "${head}stopped: $what on visionOS ${ask.version}$then$cooldown"
        }
        val stopped = run.stopped
        return when {
            bundled == null && stopped == null -> "${head}video $A_CHECK_VIDEO is not set, so nothing was asked"
            bundled == null -> "${head}stopped ($stopped) before visionOS was asked"
            asked == null && (stopped == "bot-check" || stopped == "rate-limited") -> stop(bundled, stopped)
            bundled.answerClass != CensusClass.DIRECT -> "$head${answered(bundled)}, so $alternate was not asked: nothing decided"
            asked == null -> "$head${answered(bundled)}; $alternate not asked (stopped: $stopped)"
            stopped == "bot-check" || stopped == "rate-limited" -> stop(asked, stopped)
            asked.finalClass != CensusClass.DIRECT -> "$head${answered(bundled)}; ${answered(asked)}: not an alternate"
            asked.bound == false -> "$head${answered(bundled)}; visionOS $alternate answered about another video: not an alternate"
            asked.v == StreamCheck.Verdict.PASS.name ->
                "$head${answered(bundled)}; ${answered(asked)}, V PASS: $alternate can be visionOS's bundled alternate " +
                    "(${A_CHECK_SOURCE_NAME}, ${A_CHECK_ALTERNATE.date})"
            asked.v == StreamCheck.Verdict.FAIL.name -> "$head${answered(bundled)}; ${answered(asked)}, V FAIL: not an alternate"
            else -> "$head${answered(bundled)}; ${answered(asked)}, V ${asked.v ?: "none"}: nothing decided (no V pass)"
        }
    }

    // ---- after a bot check (owner, 2026-10-15) ----------------------------------------------

    /** Records visionOS's first [answer] after its [checks]; a bot check goes to [answerAsR6]. */
    private suspend fun firstAnswer(session: Session, video: Video, answer: Asked, checks: suspend () -> Unit) {
        if (answer.record.answerClass == CensusClass.BOT_CHECK) {
            session.finish(answer)
            answerAsR6(session, video)
            return
        }
        checks()
        session.finish(answer)
    }

    /**
     * What R6 sends after a bot check on its first request (the copied extractor's watch page GET
     * and `visionOsInChain`), and nothing more: the watch page once, then visionOS once more with
     * the page's visitor data, in the body and as `X-Goog-Visitor-Id`, otherwise as before the
     * page; without visitor data R6 does not ask again, and neither does the census. V reads one
     * byte of one row of that answer (a row the download reads) and, when it is `DIRECT`, the H
     * check reads its HLS address ((က+), B5; no YouTube request). No S, alternate, Android,
     * embedded player or MWEB for this video, and no version is tried on it. A bot check
     * on that request, which carries visitor data, stops the run ([ask]). R6 answers each lookup
     * on its own, so this runs for every video whose first request met a bot check (owner,
     * 2026-10-15). Only reached while the run is not stopped.
     */
    private suspend fun answerAsR6(session: Session, video: Video) {
        if (session.stopped != null) return
        session.answeredAsR6 += video.slot
        session.notes += "video ${video.slot}: bot check on visionOS without visitor data; " +
            "answered as R6: the watch page once, visionOS once more with its visitor data, no other client for this video"
        session.step("video ${video.slot} · watch page")
        val page = video.page ?: page(session, video).also { video.page = it }
        if (session.stopped != null) return
        val visitorData = page?.visitorData?.takeIf(String::isNotBlank)
        if (visitorData == null) {
            val why = if (page == null) "no watch page" else "the watch page gave no visitor data"
            session.notes += "video ${video.slot}: $why, so visionOS was not asked again (as R6)"
            return
        }
        session.step("video ${video.slot} · visionOS again, with visitor data")
        val again = ask(session, video, VISION_OS, AGAIN, NO_PAGE.copy(visitorData = visitorData)) ?: return
        if (again.record.answerClass != CensusClass.BOT_CHECK) video.visionOs = again
        directCheck(session, video, again, preferProgressive = false)
        lSteps(session, video, again)
        if (again.record.answerClass == CensusClass.DIRECT) hlsCheck(session, video, again)
        session.finish(again)
    }

    // ---- one player request -----------------------------------------------------------------

    private suspend fun ask(
        session: Session,
        video: Video,
        profile: YouTubeClientProfile,
        step: String,
        signals: YouTubePageSignals,
    ): Asked? {
        if (!session.allow()) return null
        val started = clock()
        val answer = safely(NETWORK_FAILURE) {
            http.postJson(
                url = YouTubeUrls.innerTubeUrl(signals.apiKey),
                body = profile.playerRequestBody(
                    videoId = video.id,
                    visitorData = signals.visitorData,
                    signatureTimestamp = signals.signatureTimestamp,
                ),
                headers = playerHeaders(profile, video.id, signals),
                maxBodyBytes = MAX_PLAYER_BYTES,
            )
        }
        session.requests += 1
        val millis = clock() - started
        val base = CensusAsk(
            video = video.slot,
            client = profile.id,
            step = step,
            version = profile.clientVersion,
            http = "",
            answerClass = CensusClass.REFUSED,
            millis = millis,
        )
        val asked = when (answer) {
            is ExtractorHttpResult.Failure -> {
                if (answer.statusCode == HTTP_TOO_MANY || answer.reason == SiteExtractionFailure.RATE_LIMITED) {
                    session.stop("rate-limited")
                    session.cooldown = true
                }
                val kind = AnswerJudge.ofFailure(answer).answerClass
                Asked(
                    profile, video, null, null,
                    base.copy(http = answer.statusCode?.toString() ?: answer.reason.name, answerClass = kind, finalClass = kind),
                )
            }

            is ExtractorHttpResult.Success -> {
                session.bodyBytes += answer.body.length
                judged(profile, video, signals, answer, base)
            }
        }
        video.hosts += asked.hosts()
        if (asked.record.answerClass == CensusClass.BOT_CHECK) {
            // R6 answers each lookup on its own (owner, 2026-10-15): a request that already carried
            // visitor data is where R6's answer ends, so the run stops; visionOS's first request of
            // a video is answered as R6 answers it (the callers' answerAsR6); any other request
            // without visitor data ends this video only, as R6 has none to ask again with.
            when {
                // The A check (owner, 2026-10-16): a bot check on either of its requests stops it at once.
                signals.visitorData != null || session.mode == Mode.A_CHECK -> {
                    session.stop("bot-check")
                    session.cooldown = true
                }

                profile == VISION_OS && step == R0 -> Unit

                else -> {
                    video.closed = true
                    session.notes += "video ${video.slot}: bot check on ${profile.id} $step without visitor data, " +
                        "so nothing else was asked for this video"
                }
            }
        }
        return asked
    }

    private fun judged(
        profile: YouTubeClientProfile,
        video: Video,
        signals: YouTubePageSignals,
        answer: ExtractorHttpResult.Success,
        base: CensusAsk,
    ): Asked {
        val body = answer.body
        val response = YouTubePlayerResponseParser.inspect(body, clock())
        val root = BoundedJsonParser.parse(body, maxDepth = MAX_JSON_DEPTH, maxNodes = MAX_JSON_NODES)
        val verdict = AnswerJudge.judge(profile, response, root, body, video.id)
        val parsed = verdict.video
        val streams = parsed?.let { it.progressive + it.adaptive }.orEmpty()
        val summary = response.summary
        val shape = root?.let { ShapeFingerprint.of(it, redact = setOfNotNull(video.id, signals.visitorData)) }
        val details = root["videoDetails"]
        val status = root.path("playabilityStatus", "status").asStringOrNull?.takeIf(STATUS_NAME::matches)
            ?: if (summary.readable) "none" else "unreadable"
        val kind = verdict.answerClass
        val record = base.copy(
            http = answer.statusCode.toString(),
            answerClass = kind,
            finalClass = kind,
            status = status,
            reason = CensusSanitizer.reason(summary.reason),
            bound = details["videoId"].asStringOrNull?.let { it == video.id },
            lengthSeconds = details["lengthSeconds"].asLongOrNull?.takeIf { it > 0 },
            progressive = summary.progressiveWithUrl,
            adaptive = summary.adaptiveWithUrl,
            protectedFormats = summary.protectedFormats,
            unaddressed = parsed?.unaddressedFormats ?: summary.adaptiveWithoutAddress,
            missingSizes = streams.count { (it.contentLengthBytes ?: 0L) <= 0L },
            sabr = summary.sabr,
            hls = verdict.hls != null,
            heights = streams.filter(YouTubeStream::hasVideo).mapNotNull(YouTubeStream::height).distinct().sorted(),
            videoCodecs = streams.filter(YouTubeStream::hasVideo).mapNotNull { familyOf(it.codecs.firstOrNull()) }.distinct().sorted(),
            audioCodecs = streams.filter { it.hasAudio && !it.hasVideo }.mapNotNull { familyOf(it.codecs.firstOrNull()) }.distinct().sorted(),
            videoRows = parsed?.adaptive?.count(YouTubeStream::hasVideo) ?: 0,
            audioRows = parsed?.adaptive?.count { it.hasAudio && !it.hasVideo } ?: 0,
            shapeCore = shape?.coreHash,
            shapeFull = shape?.fullHash,
            shapePaths = shape?.core,
            shapeTruncated = shape?.truncated ?: false,
            shapeKind = verdict.shapeKind?.name?.lowercase(),
            kilobytes = kilobytes(body.length.toLong()),
        )
        return Asked(profile, video, root, parsed, record, verdict.hls)
    }

    /** R6's player headers (the copied extractor's `playerHeaders`), without a cookie. */
    private fun playerHeaders(
        profile: YouTubeClientProfile,
        videoId: String,
        signals: YouTubePageSignals,
    ): Map<String, String> = buildMap {
        (profile.userAgent ?: browserUserAgent.takeIf(String::isNotBlank))?.let { put("User-Agent", it) }
        put("Accept", "application/json")
        put("Accept-Language", ACCEPT_LANGUAGE)
        put("Origin", ORIGIN)
        if (profile.device == null) {
            put(
                "Referer",
                if (profile.thirdPartyEmbedUrl != null) YouTubeUrls.embedUrl(videoId) else YouTubeUrls.canonicalUrl(videoId),
            )
        }
        put("X-YouTube-Client-Name", profile.clientNameId.toString())
        put("X-YouTube-Client-Version", profile.clientVersion)
        signals.visitorData?.takeIf(String::isNotBlank)?.let { put("X-Goog-Visitor-Id", it) }
    }

    /** The download's headers for V and H: the user's own User-Agent and the page, no cookie. */
    private fun mediaHeaders(videoId: String): Map<String, String> = buildMap {
        browserUserAgent.takeIf(String::isNotBlank)?.let { put("User-Agent", it) }
        put("Referer", YouTubeUrls.canonicalUrl(videoId))
    }

    // ---- the watch page ---------------------------------------------------------------------

    private suspend fun page(session: Session, video: Video): YouTubePageSignals? {
        if (!session.allow()) return null
        val started = clock()
        val result = safely(NETWORK_FAILURE) {
            http.get(
                url = YouTubeUrls.watchPageFetchUrl(video.id),
                headers = PageNavigationHeaders.withDefaults(
                    buildMap {
                        browserUserAgent.takeIf(String::isNotBlank)?.let { put("User-Agent", it) }
                        put("Accept-Language", ACCEPT_LANGUAGE)
                    },
                ),
                maxBodyBytes = MAX_PAGE_BYTES,
            )
        }
        session.requests += 1
        val millis = clock() - started
        return when (result) {
            is ExtractorHttpResult.Failure -> {
                session.pages += CensusPage(
                    video = video.slot,
                    http = result.statusCode?.toString() ?: result.reason.name,
                    millis = millis,
                )
                if (result.statusCode == HTTP_TOO_MANY || result.reason == SiteExtractionFailure.RATE_LIMITED) {
                    session.stop("rate-limited")
                    session.cooldown = true
                }
                null
            }

            is ExtractorHttpResult.Success -> {
                session.bodyBytes += result.body.length
                val signals = YouTubePlayerResponseParser.pageSignals(result.body)
                signals.visitorData?.let(session.secrets::add)
                signals.apiKey?.let(session.secrets::add)
                session.pages += CensusPage(
                    video = video.slot,
                    http = result.statusCode.toString(),
                    clientName = signals.clientName?.takeIf(STATUS_NAME::matches),
                    clientVersion = signals.clientVersion?.takeIf(PUBLIC_VERSION::matches),
                    playerId = signals.playerId?.takeIf(PLAYER_ID::matches),
                    signatureTimestamp = signals.signatureTimestamp != null,
                    visitorData = signals.visitorData != null,
                    apiKey = signals.apiKey != null,
                    kilobytes = kilobytes(result.body.length.toLong()),
                    millis = millis,
                )
                signals.copy(playerResponseJson = null)
            }
        }
    }

    // ---- V, S and H -------------------------------------------------------------------------

    /** V on one direct row: an AVC video (or, for [preferProgressive], the progressive file). */
    private suspend fun directCheck(session: Session, video: Video, asked: Asked, preferProgressive: Boolean) {
        if (asked.record.answerClass in NO_ROWS) return
        val direct = asked.streams.filter { it.url != null }
        val progressive = asked.video?.progressive.orEmpty().filter { it.url != null }
        val avc = direct.filter { it.hasVideo && !it.hasAudio && isAvc(it) }.sortedBy { it.height ?: Int.MAX_VALUE }
        val target = (if (preferProgressive) progressive.firstOrNull() ?: avc.firstOrNull() else avc.firstOrNull() ?: progressive.firstOrNull())
            ?: direct.firstOrNull()
        if (target == null) {
            asked.record = asked.record.copy(v = if (asked.record.protectedFormats > 0) "skip-needs-S" else "skip-no-row")
            return
        }
        val control = direct.firstOrNull { it !== target && it.url != target.url }?.url
        check(session, video, asked, target.url!!, labelOf(target), control, StreamCheck.Kind.SIG)
        if (asked.record.v == StreamCheck.Verdict.PASS.name && video.passedDirect == null) {
            video.passedDirect = target.url
        }
    }

    /** The weekly check's V for a script client: only on a row that needs no script. */
    private suspend fun checkWithoutScript(session: Session, video: Video, asked: Asked) {
        if (asked.record.answerClass in NO_ROWS) return
        val row = asked.streams.firstOrNull { stream -> prepare(stream, asked.profile)?.challenges()?.isEmpty() == true }
        if (row == null) {
            asked.record = asked.record.copy(v = if (asked.streams.isEmpty()) "skip-no-row" else "skip-needs-S")
            return
        }
        check(session, video, asked, row.url!!, labelOf(row), video.passedDirect, StreamCheck.Kind.SIG)
    }

    /** S through the own solver on one row (sig and/or n), then V on the solved address. */
    private suspend fun solveAndCheck(session: Session, video: Video, asked: Asked, page: YouTubePageSignals?) {
        if (asked.record.answerClass in NO_ROWS) return
        val streams = asked.streams
        if (streams.isEmpty()) {
            asked.record = asked.record.copy(v = "skip-no-row")
            return
        }
        val avc = streams.filter { it.hasVideo && !it.hasAudio && isAvc(it) }.sortedBy { it.height ?: Int.MAX_VALUE }
        val stream = avc.firstOrNull { it.isProtected } ?: avc.firstOrNull()
            ?: asked.video?.progressive?.firstOrNull() ?: streams.first()
        val pending = prepare(stream, asked.profile)
        if (pending == null) {
            asked.record = asked.record.copy(solve = "unreadable", v = "skip-no-row")
            return
        }
        val challenges = pending.challenges()
        val control = video.passedDirect ?: streams.firstOrNull { it.url != null && it !== stream }?.url
        if (challenges.isEmpty()) {
            asked.record = asked.record.copy(solve = "none-needed")
            check(session, video, asked, pending.baseUrl, labelOf(stream), control, StreamCheck.Kind.SIG)
            return
        }
        val playerId = page?.playerId
        if (playerId == null) {
            asked.record = asked.record.copy(solve = "no-player", v = "skip-needs-S")
            return
        }
        if (!scripts.isAvailable) {
            asked.record = asked.record.copy(solve = "unavailable", v = "skip-needs-S")
            return
        }
        if (!session.allow()) return
        session.step("video ${video.slot} · ${asked.profile.clientName} · own solver")
        val result = safely<PlayerScriptResult>(PlayerScriptResult.Failed(SiteExtractionFailure.NETWORK)) {
            scripts.resolve(
                PlayerScriptRequest(
                    playerScriptUrl = YouTubeUrls.playerScriptUrl(playerId),
                    pageUrl = YouTubeUrls.canonicalUrl(video.id),
                    challenges = challenges,
                ),
            )
        }
        when (result) {
            is PlayerScriptResult.Success -> {
                val solved = finalUrl(pending, result.resolved)
                if (solved == null) {
                    asked.record = asked.record.copy(solve = "implausible", v = "skip-needs-S")
                } else {
                    asked.record = asked.record.copy(solve = "ok-" + pending.kinds())
                    val kind = if (pending.signatureInput != null) StreamCheck.Kind.SIG else StreamCheck.Kind.N
                    check(session, video, asked, solved, labelOf(stream), control, kind)
                }
            }

            PlayerScriptResult.Unavailable -> asked.record = asked.record.copy(solve = "unavailable", v = "skip-needs-S")
            is PlayerScriptResult.Failed -> asked.record = asked.record.copy(solve = "failed-" + result.reason.name, v = "skip-needs-S")
        }
    }

    private suspend fun check(
        session: Session,
        video: Video,
        asked: Asked,
        target: String,
        row: String,
        control: String?,
        kind: StreamCheck.Kind,
    ) {
        if (!session.allow(cost = if (control != null) 2 else 1, what = "V")) {
            asked.record = asked.record.copy(v = "skip-budget")
            return
        }
        val key = session.nextCheckKey()
        val result = session.streamCheck.check(
            StreamCheck.Request(
                target = target,
                control = control,
                returnedHosts = video.hosts,
                headers = mediaHeaders(video.id),
                kinds = setOf(kind),
                budgetKey = key,
                cacheKey = key,
                userStarted = true,
            ),
        )
        session.requests += result.requests
        val final = AnswerJudge.afterV(asked.record.answerClass, result.target == StreamCheck.StatusClass.FORBIDDEN_403)
        asked.record = asked.record.copy(
            finalClass = final,
            v = result.verdict.name,
            vTarget = statusName(result.target),
            vControl = statusName(result.control),
            vReason = result.reason.name,
            vRow = row,
        )
    }

    /** H on [asked]'s HLS address (the judge's: the field or located): ≤ 1 a video, ≤ 1 a weekly run. */
    private suspend fun hlsCheck(session: Session, video: Video, asked: Asked) {
        val address = asked.hls ?: return
        if (session.hls.any { it.video == video.slot } || (session.mode == Mode.WEEKLY && session.hls.isNotEmpty())) return
        if (!session.allow(cost = 3, what = "H")) return
        session.step("video ${video.slot} · HLS")
        val result = HlsCheck(http).check(address, mediaHeaders(video.id), asked.record.lengthSeconds)
        session.requests += result.requests
        session.hls += CensusHls(video.slot, asked.profile.id, result)
    }

    // ---- version sources --------------------------------------------------------------------

    private suspend fun readSources(session: Session, first: Video) {
        session.sourcesRead = true
        session.step("version sources")
        first.visionOs?.root?.let { root ->
            session.versions += CensusSources.fact(
                "visionos", "answer", true, null, CensusSources.answerValues(root), VISION_OS.clientVersion,
            )
        }
        first.android?.root?.let { root ->
            session.versions += CensusSources.fact(
                "android", "answer", true, null, CensusSources.answerValues(root), ANDROID.clientVersion,
            )
        }
        first.page?.let { page ->
            val name = page.clientName?.takeIf(STATUS_NAME::matches) ?: "unknown"
            val embedded = CensusSources.fact(
                "embedded", "page", true, "page client $name", listOfNotNull(page.clientVersion),
                YouTubeClientProfiles.FALLBACK_CLIENT_VERSION,
            )
            session.versions += embedded.copy(accepted = acceptedBy(first.embedded) ?: embedded.accepted)
            if (name == MOBILE_CLIENT_NAME) {
                session.versions += CensusSources.fact(
                    "mweb", "page", true, null, listOfNotNull(page.clientVersion),
                    YouTubeClientProfiles.MOBILE_CLIENT_VERSION,
                )
            } else {
                session.versions += CensusVersion("mweb", "page", reachable = true, note = "page client $name, not MWEB")
            }
        }
        for (document in CensusSources.DOCUMENTS) {
            if (!session.allow()) break
            val result = safely(NETWORK_FAILURE) {
                http.get(
                    url = document.url,
                    headers = buildMap {
                        browserUserAgent.takeIf(String::isNotBlank)?.let { put("User-Agent", it) }
                        put("Accept-Language", ACCEPT_LANGUAGE)
                    },
                    maxBodyBytes = CensusSources.MAX_SOURCE_BYTES,
                )
            }
            session.requests += 1
            for (reader in document.readers) {
                val bundled = if (reader.identity == "visionos") VISION_OS.clientVersion else ANDROID.clientVersion
                session.versions += when (result) {
                    is ExtractorHttpResult.Success -> {
                        session.bodyBytes += result.body.length
                        val note = if (document.id == "appstore") {
                            if (CensusSources.appStoreListsVision(result.body)) "lists visionOS" else "no visionOS device listed"
                        } else {
                            null
                        }
                        CensusSources.fact(reader.identity, document.id, true, note, reader.values(result.body), bundled)
                    }

                    is ExtractorHttpResult.Failure -> CensusVersion(
                        identity = reader.identity,
                        source = document.id,
                        reachable = false,
                        note = if (result.reason == SiteExtractionFailure.RESPONSE_TOO_LARGE) {
                            "over 64 KiB"
                        } else {
                            result.statusCode?.let { "HTTP $it" } ?: result.reason.name
                        },
                    )
                }
            }
        }
    }

    /** B7: a video the version acceptance may use — no bot check answered as R6, not closed, a page. */
    private fun acceptable(session: Session, video: Video): Boolean =
        video.slot !in session.answeredAsR6 && !video.closed && video.page != null

    /**
     * The version acceptance on [video], the first video without a bot check and with a watch page
     * (B7, owner 2026-10-15), or a note that none had one; nothing when no source gave a newer value.
     */
    private suspend fun accept(session: Session, video: Video?) {
        if (session.versions.none { it.pattern && it.newer == true }) return
        if (video == null) {
            session.notes += "version acceptance: no video without a bot check and with a watch page, so no newer version " +
                "was tried (R6 sends no such request)"
            return
        }
        if (video.slot != FIRST_VIDEO) {
            session.notes += "version acceptance: on video ${video.slot}, the first video without a bot check and with a watch page"
        }
        acceptanceTests(session, video)
    }

    /** One request (and V) on [first] for each identity's newest valid value newer than the bundled one. */
    private suspend fun acceptanceTests(session: Session, first: Video) {
        for (identity in listOf("visionos", "android", "mweb")) {
            if (session.stopped != null) return
            val candidates = session.versions.filter { it.identity == identity && it.pattern && it.newer == true }
            val value = candidates.mapNotNull(CensusVersion::value).distinct()
                .sortedWith { a, b -> CensusSources.compare(a, b) ?: 0 }
                .lastOrNull() ?: continue
            val page = first.page
            val profile = when (identity) {
                "visionos" -> VISION_OS.copy(clientVersion = value)
                "android" -> ANDROID.copy(clientVersion = value)
                else -> YouTubeClientProfiles.mobileWeb().copy(clientVersion = value)
            }
            if (identity == "mweb" && page == null) continue
            session.step("video ${first.slot} · ${profile.clientName} $value")
            val signals = if (identity == "visionos") NO_PAGE else page ?: NO_PAGE
            val asked = ask(session, first, profile, "SH", signals) ?: return
            if (identity == "mweb") checkWithoutScript(session, first, asked) else directCheck(session, first, asked, identity == "android")
            session.finish(asked)
            val bundled = first.mweb
            if (identity == "mweb" && bundled != null && asked.video != null) {
                session.notes += "mweb video ${first.slot}: formats without an address — bundled " +
                    "${YouTubeClientProfiles.MOBILE_CLIENT_VERSION}: ${bundled.record.unaddressed}, page's $value: ${asked.record.unaddressed}"
            }
            val verdict = when {
                asked.record.answerClass == CensusClass.BOT_CHECK -> "bot-check"
                asked.record.answerClass == CensusClass.VERSION_REJECTED -> "rejected"
                identity == "mweb" && asked.record.status == "OK" -> "yes"
                asked.record.status == "OK" && asked.record.v == StreamCheck.Verdict.PASS.name -> "yes"
                else -> "no"
            }
            session.versions.replaceAll { fact ->
                if (fact.identity == identity && fact.value == value && fact.pattern) fact.copy(accepted = verdict) else fact
            }
            // A bot check says nothing about the version; no newer version is tried after it.
            if (first.closed) return
        }
    }

    private fun acceptedBy(asked: Asked?): String? = when {
        asked == null -> null
        asked.record.answerClass == CensusClass.VERSION_REJECTED -> "rejected"
        asked.record.status == "OK" -> "yes"
        else -> "no"
    }

    // ---- helpers ------------------------------------------------------------------------------

    private inner class Session(val mode: Mode, val startedAt: Long, private val onStep: (String) -> Unit) {
        private val meterStart = meter.bytes()
        val metered: Boolean = meterStart >= 0
        val streamCheck = StreamCheck(http, StreamCheck.Policy(cacheMillis = 0), StreamCheck.MemoryLedger(), clock)
        var requests = 0
        var bodyBytes = 0L
        var stopped: String? = null
        var cooldown = false
        var sourcesRead = false

        /** Videos whose first answer was a bot check, so only R6's next requests were sent for them. */
        val answeredAsR6 = HashSet<Int>()
        val pages = ArrayList<CensusPage>()
        val asks = ArrayList<CensusAsk>()
        val hls = ArrayList<CensusHls>()
        val versions = ArrayList<CensusVersion>()
        val notes = ArrayList<String>()
        /** The L steps' results per visionOS answer (step → same / differs / none / n/a); memory only. */
        val lSteps = ArrayList<Map<String, String>>()
        val secrets = HashSet<String>()
        private var checks = 0
        private var lastStep = ""

        /** The weekly check's video 2 part: over the request budget means "not asked", not a stop. */
        var soft = false

        /** What the soft budget left out (step labels without the video, in order). */
        val left = LinkedHashSet<String>()

        fun bytes(): Long = if (metered) (meter.bytes() - meterStart).coerceAtLeast(0) else bodyBytes

        fun step(label: String) {
            lastStep = label
            onStep(label)
        }

        fun finish(asked: Asked) {
            asks += asked.record
        }

        fun stop(why: String) {
            if (stopped == null) stopped = why
        }

        /** Whether more may be asked about [video]: the run goes on and no bot check ended the video. */
        fun open(video: Video): Boolean = stopped == null && !video.closed

        fun nextCheckKey(): String {
            checks += 1
            return "census-$checks"
        }

        /**
         * Whether [cost] more requests fit the mode's budget; stops the run when they do not, except
         * over the weekly request count while [soft]: then [what] (default: the step) is left out.
         */
        fun allow(cost: Int = 1, what: String? = null): Boolean {
            if (stopped != null) return false
            val maxBytes = if (mode == Mode.FULL) FULL_MAX_BYTES else WEEKLY_MAX_BYTES
            if (bytes() >= maxBytes) {
                stop("budget")
                return false
            }
            if (mode == Mode.A_CHECK && requests + cost > A_CHECK_MAX_REQUESTS) {
                stop("requests")
                return false
            }
            if (mode == Mode.WEEKLY) {
                if (requests + cost > WEEKLY_MAX_REQUESTS) {
                    if (soft) {
                        val step = lastStep.substringAfter(" · ")
                        left += if (what == null) step else "$step $what".trim()
                        return false
                    }
                    stop("requests")
                    return false
                }
                if (clock() - startedAt >= WEEKLY_MAX_MILLIS) {
                    stop("time")
                    return false
                }
            }
            return true
        }
    }

    private class Video(val slot: Int, val id: String) {
        val hosts = HashSet<String>()
        var page: YouTubePageSignals? = null
        var visionOs: Asked? = null
        var android: Asked? = null
        var embedded: Asked? = null
        var mweb: Asked? = null
        var passedDirect: String? = null

        /** A bot check on a request without visitor data, other than visionOS's first: nothing more. */
        var closed = false

        override fun toString(): String = "Video(slot=$slot)"
    }

    private class Asked(
        val profile: YouTubeClientProfile,
        val owner: Video,
        val root: JsonValue?,
        val video: YouTubeVideo?,
        var record: CensusAsk,
        /** The judge's HLS address (field or located, a `googlevideo.com` host), or null. */
        val hls: String? = null,
    ) {
        val streams: List<YouTubeStream>
            get() = video?.let { it.progressive + it.adaptive }.orEmpty()

        /** The stream hosts this answer returned (direct addresses and those inside a cipher). */
        fun hosts(): Set<String> = streams.mapNotNullTo(HashSet()) { stream ->
            val address = stream.url ?: stream.protectedDescriptor?.let { formFields(it)["url"] }
            address?.let { runCatching { URI(it).host?.lowercase() }.getOrNull() }
        }

        override fun toString(): String = "Asked(${record.key})"
    }

    private class PendingRow(
        val baseUrl: String,
        val signatureInput: String?,
        val signatureParam: String,
        val rateInput: String?,
    ) {
        fun challenges(): List<PlayerScriptChallenge> = listOfNotNull(
            signatureInput?.let { PlayerScriptChallenge(SIGNATURE_KEY, PlayerScriptChallengeKind.SIGNATURE, it) },
            rateInput?.let { PlayerScriptChallenge(RATE_KEY, PlayerScriptChallengeKind.RATE_PARAM, it) },
        )

        fun kinds(): String = listOfNotNull("sig".takeIf { signatureInput != null }, "n".takeIf { rateInput != null })
            .joinToString("+")

        override fun toString(): String = "PendingRow(sig=${signatureInput != null}, n=${rateInput != null})"
    }

    companion object {
        const val TOOL_LABEL: String = "YFT client census"
        const val MAX_VIDEOS: Int = 10

        /** Videos 1–3 of `MASTER_KEY_PLAN.md` §4.3: short progressive, 1080 ladder, a Short. */
        val FIXED_VIDEOS: Map<Int, String> = mapOf(1 to "jNQXAC9IVRw", 2 to "dQw4w9WgXcQ", 3 to "BGQWPY4IigY")

        /** Video 4's default: the 2160p60 video the stream check used (the owner may change it). */
        const val DEFAULT_VIDEO_4: String = "aqz-KE-bpKQ"

        /**
         * Video 7's default: a public 3:30 lyric video whose owner disabled embedding (yt-dlp's
         * "Non-age-gated non-embeddable video" test). Checked 2026-10-15: oEmbed answers 401 and
         * the embed page says "Playback on other websites has been disabled by the video owner".
         */
        const val DEFAULT_VIDEO_7: String = "MeJVWBSsPAY"

        /** The slots the tool fills when the owner left them empty (the owner may change them). */
        val DEFAULT_VIDEOS: Map<Int, String> = mapOf(4 to DEFAULT_VIDEO_4, 7 to DEFAULT_VIDEO_7)

        /** What the owner should pick for each slot. */
        val SLOT_HINTS: Map<Int, String> = mapOf(
            1 to "short progressive (fixed)",
            2 to "1080 ladder (fixed)",
            3 to "a Short (fixed)",
            4 to "a 2160p video",
            5 to "a music video whose streams need a signature",
            6 to "a made-for-kids video",
            7 to "a video that disallows embedding",
            8 to "an ordinary video",
            9 to "an ordinary video",
            10 to "an ordinary video",
        )

        val VIDEO_ID: Regex = Regex("^[A-Za-z0-9_-]{11}$")

        const val COOLDOWN_MILLIS: Long = 30L * 60 * 1000
        const val FULL_MAX_BYTES: Long = 6L * 1024 * 1024
        const val WEEKLY_MAX_BYTES: Long = 2L * 1024 * 1024
        const val WEEKLY_MAX_REQUESTS: Int = 20
        const val WEEKLY_MAX_MILLIS: Long = 60_000

        /** The A check: visionOS 1.02, 1.04 and V on one row of 1.04 (a target and a control). */
        const val A_CHECK_MAX_REQUESTS: Int = 4

        private const val FIRST_VIDEO = 1
        private const val WEEKLY_SECOND_VIDEO = 5

        /** The weekly H check's video after video 1's bot check: no bot check in valid runs 1–3. */
        private const val WEEKLY_HLS_VIDEO = 2

        /** A client's request as R6 first asks it. */
        internal const val R0 = "R0"

        /** visionOS asked again with the page's visitor data, as R6 asks it after a refusal. */
        internal const val AGAIN = "R0+vd"

        /** The A check's 1.04 request (R0 with only the version changed). */
        internal const val A_CHECK_STEP = "AC"

        /** The A check's video: video 2 (the 1080 ladder), with no bot check in valid runs 1–3. */
        private const val A_CHECK_VIDEO = 2
        private const val A_CHECK_SOURCE_NAME = "NewPipe"
        private const val HTTP_OK = "200"
        private const val MOBILE_CLIENT_NAME = "MWEB"
        private const val ORIGIN = "https://www.youtube.com"
        private const val ACCEPT_LANGUAGE = "en-US,en;q=0.9"
        private const val MAX_PLAYER_BYTES: Long = 2L * 1024 * 1024
        private const val MAX_PAGE_BYTES: Long = 4L * 1024 * 1024
        private const val MAX_JSON_DEPTH = 64
        private const val MAX_JSON_NODES = 400_000
        private const val HTTP_TOO_MANY = 429
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
        private const val MINUTE_MILLIS = 60_000L
        private const val SECOND_MILLIS = 1_000L
        private const val SIGNATURE_KEY = "sig-0"
        private const val RATE_KEY = "n-0"
        private const val RATE_PARAM = "n"
        private const val DEFAULT_SIGNATURE_PARAM = "signature"
        private const val FAILED_RATE_PREFIX = "enhanced_except"

        private val VISION_OS = YouTubeClientProfiles.VISION_OS
        private val ANDROID = YouTubeClientProfiles.ANDROID

        /**
         * Bundled alternate visionOS versions (guardrail 5: ≤ 2, from a public reference with
         * source and date). None today: yt-dlp (2026.08.19 table, `_base.py`) and YouTube.js
         * (`Constants.ts`) both list `1.02`, the bundled value, and no second one; NewPipe's 1.04
         * ([A_CHECK_ALTERNATE]) is bundled only after the owner's A check.
         */
        private val ALTERNATES: List<String> = emptyList()

        /**
         * The A check's version (D5a, owner 2026-10-16, option 2): NewPipe Extractor's visionOS
         * value (`ClientsConstants.java`: 1.04 since 2026-08-06, after 1.02 from 2026-06-07), the
         * only public value other than the bundled 1.02 (yt-dlp and YouTube.js list 1.02 only; no
         * older one exists). Bundled as visionOS's alternate only after the owner's A check on the
         * phone finds it `DIRECT` with a V pass; until then nothing asks it but the A check.
         */
        internal val A_CHECK_ALTERNATE = MasterClientTable.Alternate(
            version = "1.04",
            source = "NewPipe Extractor ClientsConstants.java",
            date = "2026-08-06",
        )

        /** The A check's rule, in the report under the summary. */
        private val A_CHECK_RULE = "A check rule (owner, 2026-10-16): video $A_CHECK_VIDEO only; visionOS 1.02 as R6 asks it, " +
            "1.04 once only when 1.02 is DIRECT; 1.04 is bundled as visionOS's alternate only when it is DIRECT with a V pass " +
            "(R0 stays 1.02); a bot check or HTTP 429 stops it at once, with no census for 30 min; this run is not kept for the diff."

        /** What R6 asks before the watch page (the copied extractor's `NO_PAGE`). */
        private val NO_PAGE = YouTubePageSignals(
            playerResponseJson = null,
            playerId = null,
            apiKey = null,
            clientName = null,
            clientVersion = null,
            visitorData = null,
            signatureTimestamp = null,
        )

        private val NO_ROWS = setOf(CensusClass.BOT_CHECK, CensusClass.AGE_CHECK, CensusClass.VIDEO_VERDICT)
        private val STATUS_NAME = Regex("^[A-Z][A-Z_]{1,39}$")
        private val PUBLIC_VERSION = Regex("^[0-9]{1,4}(\\.[0-9]{1,10}){1,4}$")
        private val PLAYER_ID = Regex("^[A-Za-z0-9_-]{6,16}$")
        private val SIGNATURE_PARAM = Regex("^[A-Za-z]{1,16}$")
        private val RATE_OUTPUT = Regex("^[A-Za-z0-9_-]{2,128}$")
        private val CODEC_FAMILY = Regex("^[a-z0-9-]{2,8}$")

        /** The video ID from an ID or a YouTube address (watch, youtu.be, shorts, embed, live). */
        fun videoIdOf(text: String): String? {
            val trimmed = text.trim()
            if (VIDEO_ID.matches(trimmed)) return trimmed
            val uri = runCatching { URI(trimmed.let { if ("://" in it) it else "https://$it" }) }.getOrNull() ?: return null
            val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.")?.removePrefix("music.") ?: return null
            val segments = uri.rawPath.orEmpty().split('/').filter(String::isNotEmpty)
            val candidate = when {
                host == "youtu.be" -> segments.firstOrNull()
                host == "youtube.com" || host == "youtube-nocookie.com" -> when (segments.firstOrNull()) {
                    "watch" -> YouTubeUrls.queryParam(uri.rawQuery, "v")
                    "shorts", "embed", "live", "v" -> segments.getOrNull(1)
                    else -> null
                }
                else -> null
            }
            return candidate?.takeIf(VIDEO_ID::matches)
        }

        /** The tool's status line: the last run and any cooldown, from the stored state. */
        fun statusOf(stateJson: String?, nowMillis: Long): String {
            val state = CensusJson.readState(stateJson)
            val last = state.runs.maxByOrNull(CensusRun::atMillis)
            val lastLine = if (last == null) {
                "No run yet."
            } else {
                val days = Math.floorDiv(nowMillis - last.atMillis, DAY_MILLIS).coerceAtLeast(0)
                "Last run: ${CensusJson.dateOf(last.atMillis)} (${last.mode.lowercase()}), $days days ago."
            }
            val cooldown = if (state.cooldownUntil > nowMillis) {
                " Bot check cooldown: ${(state.cooldownUntil - nowMillis + MINUTE_MILLIS - 1) / MINUTE_MILLIS} min left."
            } else {
                ""
            }
            return lastLine + cooldown
        }

        /** [block], or [fallback] when it throws (never a cancellation). */
        private suspend fun <T> safely(fallback: T, block: suspend () -> T): T = try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            fallback
        }

        private val NETWORK_FAILURE: ExtractorHttpResult = ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)

        private fun kilobytes(bytes: Long): Long = (bytes + 1023) / 1024

        /** B8: the videos the run asked anything about, e.g. `3 videos (1, 2, 5)` or `10 videos (1–10)`. */
        private fun videosAsked(run: CensusRun): String {
            val asked = (run.asks.map(CensusAsk::video) + run.pages.map(CensusPage::video) + run.hls.map(CensusHls::video))
                .toSortedSet().toList()
            if (asked.isEmpty()) return "0 videos"
            val parts = ArrayList<String>()
            var start = 0
            while (start < asked.size) {
                var end = start
                while (end + 1 < asked.size && asked[end + 1] == asked[end] + 1) end += 1
                if (end - start >= 2) {
                    parts += "${asked[start]}\u2013${asked[end]}"
                } else {
                    (start..end).forEach { parts += asked[it].toString() }
                }
                start = end + 1
            }
            return "${asked.size} video${if (asked.size == 1) "" else "s"} (${parts.joinToString(", ")})"
        }

        private fun isAvc(stream: YouTubeStream): Boolean = stream.codecs.any { it.startsWith("avc1") }

        private fun familyOf(codec: String?): String? =
            codec?.trim()?.substringBefore('.')?.lowercase()?.takeIf(CODEC_FAMILY::matches)

        private fun labelOf(stream: YouTubeStream): String {
            val family = familyOf(stream.codecs.firstOrNull()) ?: "row"
            return when {
                stream.hasVideo && stream.hasAudio -> "progressive ${stream.height ?: 0}p"
                stream.hasVideo -> "$family ${stream.height ?: 0}p"
                else -> "$family audio"
            }
        }

        private fun statusName(status: StreamCheck.StatusClass?): String? = when (status) {
            null -> null
            StreamCheck.StatusClass.OK_2XX -> "2xx"
            StreamCheck.StatusClass.FORBIDDEN_403 -> "403"
            StreamCheck.StatusClass.OTHER_4XX -> "4xx"
            StreamCheck.StatusClass.SERVER_5XX -> "5xx"
            StreamCheck.StatusClass.OTHER_STATUS -> "other"
            StreamCheck.StatusClass.NETWORK -> "net"
            StreamCheck.StatusClass.UNSUPPORTED -> "unsupported"
        }

        private fun rateInput(url: String): String? = YouTubeUrls.queryParamOf(url, RATE_PARAM)
            ?.let(::decode)
            ?.takeIf(String::isNotBlank)

        /** The copied extractor's `prepare`: the address and what S must compute for it. */
        private fun prepare(stream: YouTubeStream, profile: YouTubeClientProfile): PendingRow? {
            val descriptor = stream.protectedDescriptor
            if (descriptor == null) {
                val url = stream.url ?: return null
                return PendingRow(url, null, DEFAULT_SIGNATURE_PARAM, if (profile.usesPlayerScript) rateInput(url) else null)
            }
            val fields = formFields(descriptor)
            val url = fields["url"]?.takeIf { it.startsWith("https://") } ?: return null
            val signature = fields["s"]?.takeIf(String::isNotBlank) ?: return null
            val param = fields["sp"]?.takeIf(SIGNATURE_PARAM::matches) ?: DEFAULT_SIGNATURE_PARAM
            return PendingRow(url, signature, param, rateInput(url))
        }

        /** The copied extractor's `finalUrl`, with its plausibility rules. */
        private fun finalUrl(pending: PendingRow, solved: Map<String, String>): String? {
            var url = pending.baseUrl
            pending.rateInput?.let { input ->
                val output = solved[RATE_KEY]
                    ?.takeIf { it != input && RATE_OUTPUT.matches(it) && !it.startsWith(FAILED_RATE_PREFIX) }
                    ?: return null
                url = YouTubeUrls.replaceQueryParam(url, RATE_PARAM, output)
            }
            pending.signatureInput?.let {
                val output = solved[SIGNATURE_KEY]
                    ?.takeIf { value -> value.length in 8..512 && value.none(Char::isWhitespace) }
                    ?: return null
                url = YouTubeUrls.appendQueryParam(url, pending.signatureParam, output)
            }
            return url
        }

        private fun formFields(descriptor: String): Map<String, String> = buildMap {
            descriptor.split('&').forEach { pair ->
                val separator = pair.indexOf('=')
                if (separator > 0) {
                    val name = pair.substring(0, separator)
                    val value = decode(pair.substring(separator + 1))
                    if (value != null && name !in this) put(name, value)
                }
            }
        }

        private fun decode(value: String): String? =
            runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrNull()
    }
}
