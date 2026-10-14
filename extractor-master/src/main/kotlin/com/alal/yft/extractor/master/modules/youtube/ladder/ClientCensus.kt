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
import com.alal.yft.extractor.master.modules.youtube.YouTubeParseResult
import com.alal.yft.extractor.master.modules.youtube.YouTubePlayerResponse
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
 * version (none are bundled: the references list no second visionOS value); (4) Android — class,
 * progressive only or not, V; (5) the embedded player with the page's version and embed context,
 * as main asks it — class, S through the own solver, V; (6) MWEB — class, S, V. After video 1 the
 * public version sources are read once ([CensusSources]); a new valid value newer than the
 * bundled one is tried once on video 1 (`SH`: one request and V).
 *
 * **Weekly check**: videos 1 and 5 visionOS (V on one row, H on video 1), then Android, embedded
 * and MWEB once on video 1 (no S), the version sources only when not read in 24 h; ≤ 60 s,
 * ≤ 2 MB and ≤ 20 requests.
 *
 * **Bot checks** (owner, 2026-10-15): after a bot check the census never sends a request R6 would
 * not send, and like R6 it answers each lookup (each video) on its own. A bot check on visionOS's
 * first request of a video (no visitor data: R6's own first request) is answered as R6 answers it
 * ([answerAsR6]): that video's watch page once and visionOS once more with the page's visitor data
 * (`R0+vd`, R6's `visionOsInChain` request), V on one row of that answer (a one-byte read of a row
 * the download reads), nothing else for that video, and the run goes on with the next video. A
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
    enum class Mode { FULL, WEEKLY }

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
        val diff = CensusDiff.compare(state.runs, run)
        val next = CensusState(
            runs = (state.runs + run).sortedBy(CensusRun::atMillis).takeLast(CensusJson.MAX_RUNS),
            cooldownUntil = if (session.cooldown) clock() + COOLDOWN_MILLIS else state.cooldownUntil,
            sourcesAt = if (session.sourcesRead) startedAt else state.sourcesAt,
        )
        val secrets = chosen.values + session.secrets
        val summary = "$toolLabel · ${mode.name} · ${CensusJson.dateOf(startedAt)} · ${chosen.size} videos · " +
            "${run.requests} requests · ${kilobytes(run.bytes)} KB${if (run.metered) "" else " (bodies only)"} · " +
            "${run.millis / SECOND_MILLIS} s" + (run.stopped?.let { " · stopped: $it" } ?: "")
        val report = buildString {
            append(diff.text()).append('\n')
            append(summary).append('\n')
            append(CensusJson.run(run))
        }
        return Outcome(
            firstLine = diff.headline,
            report = CensusSanitizer.clean(report, secrets),
            stateJson = CensusSanitizer.clean(CensusJson.state(next), secrets),
            stopped = run.stopped,
        )
    }

    // ---- the two modes ----------------------------------------------------------------------

    private suspend fun full(session: Session, videos: Map<Int, String>) {
        var sourcesDone = false
        for ((slot, id) in videos) {
            if (session.stopped != null) break
            val video = fullVideo(session, slot, id)
            if (!sourcesDone && session.stopped == null) {
                sourcesDone = true
                readSources(session, video)
            }
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
                    if (profile.id == "embedded") first.embedded = answer
                    checkWithoutScript(session, first, answer)
                    session.finish(answer)
                }
            }
        } else if (page == null) {
            session.notes += "video 1: no watch page, so the embedded player and MWEB were not asked"
        }
    }

    private fun startedAgo(at: Long): Long = if (at <= 0) Long.MAX_VALUE else clock() - at

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
     * byte of one row of that answer (a row the download reads). No S, alternate, H check,
     * Android, embedded player or MWEB for this video, and no version is tried on it. A bot check
     * on that request, which carries visitor data, stops the run ([ask]). R6 answers each lookup
     * on its own, so this runs for every video whose first request met a bot check (owner,
     * 2026-10-15). Only reached while the run is not stopped.
     */
    private suspend fun answerAsR6(session: Session, video: Video) {
        if (session.stopped != null) return
        session.answeredAsR6 += video.slot
        session.notes += "video ${video.slot}: bot check on visionOS without visitor data; " +
            "answered as R6: the watch page once, visionOS once more with its visitor data, nothing else for this video"
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
                val rejected = answer.statusCode == HTTP_BAD_REQUEST
                if (answer.statusCode == HTTP_TOO_MANY || answer.reason == SiteExtractionFailure.RATE_LIMITED) {
                    session.stop("rate-limited")
                    session.cooldown = true
                }
                val kind = if (rejected) CensusClass.VERSION_REJECTED else CensusClass.REFUSED
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
                signals.visitorData != null -> {
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
        val parsed = (response.result as? YouTubeParseResult.Success)?.video
        val streams = parsed?.let { it.progressive + it.adaptive }.orEmpty()
        val summary = response.summary
        val shape = root?.let { ShapeFingerprint.of(it, redact = setOfNotNull(video.id, signals.visitorData)) }
        val details = root["videoDetails"]
        val status = root.path("playabilityStatus", "status").asStringOrNull?.takeIf(STATUS_NAME::matches)
            ?: if (summary.readable) "none" else "unreadable"
        val kind = classify(profile, response, parsed)
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
            hls = hlsAddressOf(root) != null,
            heights = streams.filter(YouTubeStream::hasVideo).mapNotNull(YouTubeStream::height).distinct().sorted(),
            videoCodecs = streams.filter(YouTubeStream::hasVideo).mapNotNull { familyOf(it.codecs.firstOrNull()) }.distinct().sorted(),
            audioCodecs = streams.filter { it.hasAudio && !it.hasVideo }.mapNotNull { familyOf(it.codecs.firstOrNull()) }.distinct().sorted(),
            videoRows = parsed?.adaptive?.count(YouTubeStream::hasVideo) ?: 0,
            audioRows = parsed?.adaptive?.count { it.hasAudio && !it.hasVideo } ?: 0,
            shapeCore = shape?.coreHash,
            shapeFull = shape?.fullHash,
            shapePaths = shape?.core,
            shapeTruncated = shape?.truncated ?: false,
            kilobytes = kilobytes(body.length.toLong()),
        )
        return Asked(profile, video, root, parsed, record)
    }

    /** v2's classes from the answer itself (D2's judge refines them). */
    private fun classify(
        profile: YouTubeClientProfile,
        response: YouTubePlayerResponse,
        video: YouTubeVideo?,
    ): CensusClass {
        val summary = response.summary
        val reason = summary.reason.orEmpty()
        return when (val result = response.result) {
            is YouTubeParseResult.Failure -> when {
                result.reason == SiteExtractionFailure.BOT_CHECK -> CensusClass.BOT_CHECK
                VERSION_REFUSAL.containsMatchIn(reason) -> CensusClass.VERSION_REJECTED
                result.ageCheck -> CensusClass.AGE_CHECK
                APP_REFUSAL.containsMatchIn(reason) -> CensusClass.REFUSED
                result.definite -> CensusClass.VIDEO_VERDICT
                !summary.readable -> CensusClass.REFUSED
                summary.sabrOnly || result.reason == SiteExtractionFailure.NO_MEDIA_FOUND -> CensusClass.SABR_ONLY
                result.reason == SiteExtractionFailure.RESPONSE_CHANGED -> CensusClass.SHAPE_CHANGED
                else -> CensusClass.REFUSED
            }

            is YouTubeParseResult.Success -> {
                val parsed = video ?: result.video
                val streams = parsed.progressive + parsed.adaptive
                val needsScript = streams.any(YouTubeStream::isProtected) ||
                    (profile.usesPlayerScript && streams.any { stream -> stream.url?.let(::rateInput) != null })
                when {
                    needsScript -> CensusClass.NEEDS_SCRIPT
                    parsed.adaptive.isEmpty() && summary.sabr -> CensusClass.SABR_ONLY
                    parsed.unaddressedFormats > 0 ||
                        streams.any { (it.contentLengthBytes ?: 0L) <= 0L } ||
                        parsed.title == null || parsed.durationMillis == null -> CensusClass.INCOMPLETE
                    else -> CensusClass.DIRECT
                }
            }
        }
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
        if (!session.allow(cost = if (control != null) 2 else 1)) {
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
        val final = if (result.target == StreamCheck.StatusClass.FORBIDDEN_403) {
            CensusClass.TOKEN_NEEDED
        } else {
            asked.record.answerClass
        }
        asked.record = asked.record.copy(
            finalClass = final,
            v = result.verdict.name,
            vTarget = statusName(result.target),
            vControl = statusName(result.control),
            vReason = result.reason.name,
            vRow = row,
        )
    }

    private suspend fun hlsCheck(session: Session, video: Video, asked: Asked) {
        val address = hlsAddressOf(asked.root) ?: return
        if (!session.allow(cost = 3)) return
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
        acceptanceTests(session, first)
    }

    /** One request (and V) on video 1 for the newest valid value newer than the bundled one. */
    private suspend fun acceptanceTests(session: Session, first: Video) {
        if (first.slot in session.answeredAsR6 || first.closed) {
            if (session.versions.any { it.pattern && it.newer == true }) {
                session.notes += "version acceptance: video ${first.slot} met a bot check, so no newer version was tried " +
                    "(R6 sends no such request)"
            }
            return
        }
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
        val secrets = HashSet<String>()
        private var checks = 0

        fun bytes(): Long = if (metered) (meter.bytes() - meterStart).coerceAtLeast(0) else bodyBytes

        fun step(label: String) = onStep(label)

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

        /** Whether [cost] more requests fit the mode's budget; stops the run when they do not. */
        fun allow(cost: Int = 1): Boolean {
            if (stopped != null) return false
            val maxBytes = if (mode == Mode.FULL) FULL_MAX_BYTES else WEEKLY_MAX_BYTES
            if (bytes() >= maxBytes) {
                stop("budget")
                return false
            }
            if (mode == Mode.WEEKLY) {
                if (requests + cost > WEEKLY_MAX_REQUESTS) {
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

        private const val FIRST_VIDEO = 1
        private const val WEEKLY_SECOND_VIDEO = 5

        /** A client's request as R6 first asks it. */
        internal const val R0 = "R0"

        /** visionOS asked again with the page's visitor data, as R6 asks it after a refusal. */
        internal const val AGAIN = "R0+vd"
        private const val MOBILE_CLIENT_NAME = "MWEB"
        private const val ORIGIN = "https://www.youtube.com"
        private const val ACCEPT_LANGUAGE = "en-US,en;q=0.9"
        private const val MAX_PLAYER_BYTES: Long = 2L * 1024 * 1024
        private const val MAX_PAGE_BYTES: Long = 4L * 1024 * 1024
        private const val MAX_JSON_DEPTH = 64
        private const val MAX_JSON_NODES = 400_000
        private const val HTTP_BAD_REQUEST = 400
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
         * (`Constants.ts`) both list `1.02`, the bundled value, and no second one.
         */
        private val ALTERNATES: List<String> = emptyList()

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

        /** YouTube asking for a newer app: the version, not the video. */
        private val VERSION_REFUSAL = Regex(
            "(?i)(update (the|your|to the latest)|no longer supported|" +
                "(app|client|version) (is )?(not|no longer) supported|unsupported (app|client|version))",
        )

        /** A refusal of this app rather than a verdict about the video. */
        private val APP_REFUSAL = Regex("(?i)(not available on this app|isn['\u2019]t available on this app)")

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

        private fun hlsAddressOf(root: JsonValue?): String? =
            root.path("streamingData", "hlsManifestUrl").asStringOrNull?.takeIf { it.startsWith("https://") }

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
