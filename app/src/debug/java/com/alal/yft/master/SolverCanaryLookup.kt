package com.alal.yft.master

import android.content.Context
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.detection.OkHttpExtractorClient
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.PlayerScriptChallenge
import com.alal.yft.extractor.api.PlayerScriptChallengeKind
import com.alal.yft.extractor.api.PlayerScriptRequest
import com.alal.yft.extractor.api.PlayerScriptResult
import com.alal.yft.extractor.api.PlayerScriptRunner
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.android.solver.WebViewOwnSolverEngine
import com.alal.yft.extractor.master.android.solver.WebViewPageSolverEngine
import com.alal.yft.extractor.master.modules.youtube.YouTubeSignatureGuard
import com.alal.yft.extractor.master.solver.AtomicTextFile
import com.alal.yft.extractor.master.solver.OwnPlayerScriptRunner
import com.alal.yft.extractor.master.solver.OwnSolverBreaker
import com.alal.yft.extractor.master.solver.OwnSolverEngine
import com.alal.yft.extractor.master.solver.OwnSolverLocators
import com.alal.yft.extractor.master.solver.OwnSolverProbe
import com.alal.yft.extractor.master.solver.OwnSolverVLedger
import com.alal.yft.extractor.master.solver.PendingCheck
import com.alal.yft.extractor.master.solver.SignatureVerdicts
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

/**
 * Phase 1.2 N7, owner tool (debug build only): the app's YouTube lookup of one public video, V
 * included, timed from its start to the rows ("P (b) in total", MASTER_KEY_PHASE1_2_PLAN.md §3
 * N7). The runner is the app's [OwnPlayerScriptRunner] with the ladder, the app's breaker and the
 * tool's own locator and V files (under `filesDir/owner-canary/`, so the app's files stay as they
 * are); the reader asks YouTube as the N2 stream check does ([StreamCheckMeasurement]'s requests,
 * copied) and the app's gate runs V. Run first in a new process, its worker P job is the P hit
 * after a restart (the engine's cold start included).
 *
 * A lookup YouTube refused (or whose worker P the breaker has off) is "not measured" and the canary
 * goes on; a job without a readable reply (a timeout, a gone renderer, the engine, a broken reply)
 * stops the canary ([Outcome.stoppedAt]). An error class, a budget or the lookup cap does not (N7,
 * as the steps: [OwnSolverProbe.Run.stopped]).
 */
internal class SolverCanaryLookup(
    private val context: Context,
    private val client: OkHttpClient,
    private val meter: SolverCanaryHttpMeter,
    private val breaker: OwnSolverBreaker,
    private val folder: File,
    private val onStep: (String) -> Unit,
    private val clock: () -> Long,
) {
    class Failure(message: String) : Exception(message)

    class Outcome(val lookup: SolverCanaryReport.Lookup, val stoppedAt: String?)

    @Volatile
    private var lookupPlayer: String? = null

    suspend fun run(todaysPlayer: String?, video: String = StreamCheckMeasurement.DEFAULT_VIDEO): Outcome {
        require(StreamCheckMeasurement.VIDEO_ID.matches(video))
        val parts = mutableListOf<SolverCanaryReport.Part>()
        val jobs = mutableListOf<OwnSolverProbe.Summary>()
        val http = OkHttpExtractorClient(
            client = client,
            policy = OkHttpExtractorClient.Policy(callTimeoutSeconds = CALL_TIMEOUT_SECONDS),
        )
        val runner = OwnPlayerScriptRunner(
            http = TimedHttp(http, meter, parts, "player download", clock),
            engine = TimedEngine(WebViewOwnSolverEngine(context), jobs, clock),
            next = OwnPlayerScriptRunner.Next(
                page = TimedEngine(WebViewPageSolverEngine(context), jobs, clock),
                breaker = breaker,
                locators = OwnSolverLocators(AtomicTextFile { File(folder, LOCATORS_FILE) }),
                ledger = OwnSolverVLedger(AtomicTextFile { File(folder, V_LEDGER_FILE) }),
            ),
        )
        val tap = VerdictTap(runner)
        val reads = TimedHttp(http, meter, parts, "page", clock, postLabel = "player API")
        val guarded = YouTubeSignatureGuard.guard(TimedHttp(http, meter, parts, "V probe", clock), tap) { gated ->
            CanaryReader(video, reads, gated)
        }
        val pageUrl = "$ORIGIN/watch?v=$video"
        val request = SiteExtractionRequest(
            identity = SitePageIdentity(SITE_ID, video, pageUrl),
            requestContext = BrowserRequestContext(pageUrl, null, null),
            nowEpochMs = System.currentTimeMillis(),
        )

        val started = clock()
        val outcome = try {
            when (val result = guarded.extract(request)) {
                is SiteExtractionResult.Success -> "rows"
                is SiteExtractionResult.Failure -> "no rows (${result.reason.name})"
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Failure) {
            "not measured (${failure.message.orEmpty()})"
        } catch (failure: Exception) {
            "not measured (error ${failure.javaClass.simpleName})"
        }
        val totalMillis = clock() - started
        val failures = runner.lastFailures
        val stoppedAt = failures.firstOrNull { stops(it) }?.let { failure -> "lookup ($failure)" }
        val ran = synchronized(jobs) { jobs.toList() }
        val lookup = SolverCanaryReport.Lookup(
            outcome = if (stoppedAt != null && outcome == "rows") "rows, stopped" else outcome,
            totalMillis = totalMillis,
            parts = synchronized(parts) { parts.toList() },
            workerP = ran.firstOrNull { it.step == OwnSolverBreaker.Step.WORKER_P },
            v = tap.status,
            jobs = ran,
            failures = failures,
            samePlayer = lookupPlayer?.let { player -> todaysPlayer?.let { it == player } },
        )
        return Outcome(lookup, stoppedAt)
    }

    /** The lookup's reader: YouTube's page and player answer as N2's stream check asks them. */
    private inner class CanaryReader(
        private val video: String,
        private val http: ExtractorHttpClient,
        private val runner: PlayerScriptRunner,
    ) : SiteExtractor {
        override val id: String = SITE_ID
        override val displayName: String = "YouTube (owner canary)"

        override fun identify(pageUrl: String): SitePageIdentity? = null

        override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult {
            onStep("watch page")
            val page = http.get(
                "$ORIGIN/watch?v=$video&hl=en",
                mapOf("User-Agent" to USER_AGENT, "Accept-Language" to "en-US,en;q=0.9"),
                PAGE_LIMIT,
            ).successOr("watch page")
            val jsUrl = JS_URL.find(page.body)?.groupValues?.get(1) ?: throw Failure("no player address on the page")
            val player = PLAYER_IN_PATH.find(jsUrl)?.groupValues?.get(1) ?: throw Failure("no player version")
            val sts = STS.find(page.body)?.groupValues?.get(1)?.toInt() ?: throw Failure("no signature timestamp")
            val clientVersion = CLIENT_VERSION.find(page.body)?.groupValues?.get(1) ?: FALLBACK_CLIENT_VERSION
            val visitorData = VISITOR_DATA.find(page.body)?.groupValues?.get(1)
            val apiKey = API_KEY.find(page.body)?.groupValues?.get(1)

            // The embedded player first (no token, as in the YouTube module), then the mobile web
            // client: on 2026-10-11 the embedded client answered ERROR for every video tried.
            val tried = mutableListOf<String>()
            var chosen: Pair<Client, Stream>? = null
            for (client in CLIENTS) {
                onStep("2/4 player request (${client.name})")
                val answer = http.postJson(
                    "$ORIGIN/youtubei/v1/player?prettyPrint=false" + (apiKey?.let { "&key=$it" } ?: ""),
                    playerBody(client, video, clientVersion, visitorData, sts),
                    buildMap {
                        put("User-Agent", client.userAgent)
                        put("Accept", "application/json")
                        put("Origin", ORIGIN)
                        put("Referer", if (client.embedded) "$ORIGIN/embed/$video" else "$ORIGIN/watch?v=$video")
                        put("X-YouTube-Client-Name", client.id.toString())
                        put("X-YouTube-Client-Version", clientVersion)
                        visitorData?.let { put("X-Goog-Visitor-Id", it) }
                    },
                )
                val response = (answer as? ExtractorHttpResult.Success)?.let { runCatching { JSONObject(it.body) }.getOrNull() }
                if (response == null) {
                    val failure = answer as? ExtractorHttpResult.Failure
                    tried += "${client.name}: request ${failure?.reason ?: "not JSON"} ${failure?.statusCode ?: ""}".trim()
                    continue
                }
                val playability = response.optJSONObject("playabilityStatus")
                val status = playability?.optString("status").orEmpty().filter(Char::isLetter).take(40)
                if (status != "OK") {
                    tried += "${client.name}: $status ${clean(playability?.optString("reason").orEmpty())}".trim()
                    continue
                }
                val stream = pickStream(response)
                if (stream == null) {
                    tried += "${client.name}: OK but no stream address of at least 1 MiB (SABR only?)"
                    continue
                }
                tried += "${client.name}: OK"
                chosen = client to stream
                break
            }
            val (client, stream) = chosen ?: throw Failure("no client gave a stream: " + tried.joinToString("; "))
            lookupPlayer = player
            // Nothing to measure: the canary goes on (its P steps are breaker off too, B still runs).
            if (!breaker.allows(player, OwnSolverBreaker.Step.WORKER_P)) throw Failure("P breaker off")
            val challenges = buildList {
                stream.n?.let { add(PlayerScriptChallenge(N_KEY, PlayerScriptChallengeKind.RATE_PARAM, it)) }
                stream.s?.let { add(PlayerScriptChallenge(SIG_KEY, PlayerScriptChallengeKind.SIGNATURE, it)) }
            }
            if (challenges.isEmpty()) throw Failure("the stream needs no player values")
            val playerRequest = PlayerScriptRequest(
                playerScriptUrl = "$ORIGIN/s/player/$player/$PHONE_VARIANT",
                pageUrl = "$ORIGIN/watch?v=$video",
                challenges = challenges,
            )
            onStep("solver and V")
            val resolved = when (val result = runner.resolve(playerRequest)) {
                is PlayerScriptResult.Success -> result.resolved
                is PlayerScriptResult.Failed -> return SiteExtractionResult.Failure(result.reason)
                PlayerScriptResult.Unavailable ->
                    return SiteExtractionResult.Failure(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED)
            }
            val n = resolved[N_KEY]
            val sig = resolved[SIG_KEY]
            // As the app's reader: a value the runner did not give drops the stream.
            if ((stream.n != null && n == null) || (stream.s != null && sig == null)) {
                return SiteExtractionResult.Failure(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED)
            }
            val pageUrl = playerRequest.pageUrl
            return SiteExtractionResult.Success(
                listOf(
                    MediaCandidate(
                        pageUrl = pageUrl,
                        mediaUrl = stream.address(n, sig),
                        sources = setOf(CandidateSource.REQUEST),
                        kind = MediaKind.DIRECT,
                        requestContext = BrowserRequestContext(pageUrl, client.userAgent, null),
                    ),
                ),
            )
        }
    }

    // Copied from StreamCheckMeasurement (N2): the player request's body, the stream it picks and
    // the address it builds.
    private fun playerBody(client: Client, video: String, clientVersion: String, visitorData: String?, sts: Int): String =
        JSONObject()
            .put(
                "context",
                JSONObject()
                    .put(
                        "client",
                        JSONObject()
                            .put("clientName", client.name)
                            .put("clientVersion", clientVersion)
                            .put("hl", "en")
                            .put("timeZone", "UTC")
                            .put("utcOffsetMinutes", 0)
                            .apply { visitorData?.let { put("visitorData", it) } },
                    )
                    .apply { if (client.embedded) put("thirdParty", JSONObject().put("embedUrl", EMBED_URL)) },
            )
            .put("videoId", video)
            .put(
                "playbackContext",
                JSONObject().put(
                    "contentPlaybackContext",
                    JSONObject().put("html5Preference", "HTML5_PREF_WANTS").put("signatureTimestamp", sts),
                ),
            )
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
            .toString()

    private fun ExtractorHttpResult.successOr(step: String): ExtractorHttpResult.Success = when (this) {
        is ExtractorHttpResult.Success -> this
        is ExtractorHttpResult.Failure -> throw Failure("$step failed: $reason ${statusCode ?: ""}".trim())
    }

    /** The smallest adaptive mp4 video stream that holds 1 MiB, preferring one that needs both values. */
    private fun pickStream(response: JSONObject): Stream? {
        val formats = response.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats") ?: return null
        val streams = (0 until formats.length()).mapNotNull { index ->
            val format = formats.optJSONObject(index) ?: return@mapNotNull null
            if (!format.optString("mimeType").startsWith("video/mp4")) return@mapNotNull null
            val size = format.optString("contentLength").toLongOrNull() ?: return@mapNotNull null
            if (size < MEASURE_BYTES) return@mapNotNull null
            val cipher = format.optString("signatureCipher").takeIf(String::isNotEmpty)?.let(::formFields)
            val base = cipher?.get("url") ?: format.optString("url").takeIf(String::isNotEmpty) ?: return@mapNotNull null
            if (!isStreamHost(base)) return@mapNotNull null
            Stream(
                itag = format.optInt("itag"),
                size = size,
                base = base,
                n = queryValue(base, "n"),
                s = cipher?.get("s"),
                sp = cipher?.get("sp") ?: "signature",
            )
        }
        return streams.sortedWith(compareBy<Stream>({ it.s == null }, { it.n == null }, { it.size })).firstOrNull()
    }

    private fun isStreamHost(address: String): Boolean {
        val uri = runCatching { URI(address) }.getOrNull() ?: return false
        return uri.scheme == "https" && uri.userInfo == null &&
            uri.host?.lowercase()?.endsWith(".googlevideo.com") == true && uri.rawPath == "/videoplayback"
    }

    private fun formFields(descriptor: String): Map<String, String> = descriptor.split('&').mapNotNull { pair ->
        val separator = pair.indexOf('=')
        if (separator <= 0) {
            null
        } else {
            pair.substring(0, separator) to URLDecoder.decode(pair.substring(separator + 1), "UTF-8")
        }
    }.toMap()

    private fun queryValue(address: String, name: String): String? =
        runCatching { URI(address).rawQuery }.getOrNull()?.let(::formFields)?.get(name)

    private class Stream(
        val itag: Int,
        val size: Long,
        val base: String,
        val n: String?,
        val s: String?,
        val sp: String,
    ) {
        /** The base address with [n] in place of its `n` and, when a signature is needed, [sig]. */
        fun address(n: String?, sig: String?): String {
            val query = URI(base).rawQuery.orEmpty().split('&').filter(String::isNotEmpty).map { pair ->
                if (n != null && pair.substringBefore('=') == "n") "n=" + encode(n) else pair
            }
            val signed = if (s != null && sig != null) query + (encode(sp) + "=" + encode(sig)) else query
            return base.substringBefore('?') + "?" + signed.joinToString("&")
        }

        private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

        /** Never shows the address or a value. */
        override fun toString(): String = "Stream(itag=$itag, n=${n != null}, sig=${s != null})"
    }

    private class Client(val name: String, val id: Int, val userAgent: String, val embedded: Boolean)

    private fun clean(text: String): String =
        text.filter { it.isLetterOrDigit() || it in " .,:;()'-?" }.take(120)

    companion object {
        private const val SITE_ID = "youtube"
        private const val N_KEY = "n"
        private const val SIG_KEY = "sig"
        private const val CALL_TIMEOUT_SECONDS = 60L
        private const val LOCATORS_FILE = "lookup-locators.txt"
        private const val V_LEDGER_FILE = "lookup-v-ledger.txt"

        /** The runner's failure classes that stop the canary: no readable reply from the engine. */
        private val STOP_ENDS = listOf(" ready timeout", " job timeout", " gone", " engine", " reply")

        fun stops(failure: String): Boolean = STOP_ENDS.any(failure::endsWith)

        // Copied from StreamCheckMeasurement (N2).
        private const val ORIGIN = "https://www.youtube.com"
        private const val EMBED_URL = "https://github.com/Alalkipgen/YFT"
        private const val PHONE_VARIANT = "player-plasma-ias-phone-en_US.vflset/base.js"
        private const val FALLBACK_CLIENT_VERSION = "2.20260708.00.00"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36"
        private const val PAGE_LIMIT = 8L * 1024 * 1024
        private const val MEASURE_BYTES = 1024L * 1024
        private const val TABLET_USER_AGENT = "Mozilla/5.0 (iPad; CPU OS 16_7_10 like Mac OS X) " +
            "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1,gzip(gfe)"
        private val CLIENTS = listOf(
            Client("WEB_EMBEDDED_PLAYER", 56, USER_AGENT, embedded = true),
            Client("MWEB", 2, TABLET_USER_AGENT, embedded = false),
        )
        private val JS_URL = Regex("\"(?:jsUrl|PLAYER_JS_URL)\"\\s*:\\s*\"([^\"]{1,400})\"")
        private val PLAYER_IN_PATH = Regex("/s/player/([A-Za-z0-9_-]{4,32})/")
        private val STS = Regex("\"(?:STS|signatureTimestamp)\"\\s*:\\s*(\\d{4,7})\\b")
        private val CLIENT_VERSION = Regex("\"INNERTUBE_CLIENT_VERSION\"\\s*:\\s*\"(2\\.\\d{8}\\.\\d{2}\\.\\d{2})\"")
        private val VISITOR_DATA = Regex("\"(?:VISITOR_DATA|visitorData)\"\\s*:\\s*\"([A-Za-z0-9_\\-%=+/]{5,2000})\"")
        private val API_KEY = Regex("\"INNERTUBE_API_KEY\"\\s*:\\s*\"([A-Za-z0-9_-]{10,80})\"")
    }
}

/** Counts what the tool's OkHttp clients send and receive (headers and bodies) and its cache hits. */
internal class SolverCanaryHttpMeter {
    private val bytes = AtomicLong()
    private val calls = AtomicInteger()
    private val hits = AtomicInteger()

    val bytesSoFar: Long get() = bytes.get()
    val callsSoFar: Int get() = calls.get()
    val cacheHitsSoFar: Int get() = hits.get()

    val factory: EventListener.Factory = object : EventListener.Factory {
        override fun create(call: Call): EventListener = Listener()
    }

    private inner class Listener : EventListener() {
        override fun callStart(call: Call) {
            calls.incrementAndGet()
        }

        override fun cacheHit(call: Call, response: Response) {
            hits.incrementAndGet()
        }

        override fun requestHeadersEnd(call: Call, request: Request) {
            bytes.addAndGet(request.headers.byteCount())
        }

        override fun requestBodyEnd(call: Call, byteCount: Long) {
            bytes.addAndGet(byteCount)
        }

        override fun responseHeadersEnd(call: Call, response: Response) {
            bytes.addAndGet(response.headers.byteCount())
        }

        override fun responseBodyEnd(call: Call, byteCount: Long) {
            bytes.addAndGet(byteCount)
        }
    }
}

/** Times each request of [inner] as a report [SolverCanaryReport.Part] named [label] ([postLabel] for posts). */
internal class TimedHttp(
    private val inner: ExtractorHttpClient,
    private val meter: SolverCanaryHttpMeter,
    private val parts: MutableList<SolverCanaryReport.Part>,
    private val label: String,
    private val clock: () -> Long,
    private val postLabel: String = label,
) : ExtractorHttpClient {
    override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult =
        timed(label) { inner.get(url, headers, maxBodyBytes) }

    override suspend fun postJson(
        url: String,
        body: String,
        headers: Map<String, String>,
        maxBodyBytes: Long,
    ): ExtractorHttpResult = timed(postLabel) { inner.postJson(url, body, headers, maxBodyBytes) }

    override suspend fun probe(url: String, headers: Map<String, String>, timeoutMillis: Long): ExtractorProbeResult =
        timed(label) { inner.probe(url, headers, timeoutMillis) }

    private suspend fun <T> timed(name: String, call: suspend () -> T): T {
        val bytes = meter.bytesSoFar
        val hits = meter.cacheHitsSoFar
        val started = clock()
        try {
            return call()
        } finally {
            val part = SolverCanaryReport.Part(name, clock() - started, meter.bytesSoFar - bytes, meter.cacheHitsSoFar > hits)
            synchronized(parts) { parts += part }
        }
    }
}

/**
 * Times each job of [inner] (from sending it to its reply, the engine's start included) and keeps
 * it as [OwnSolverProbe.describe] sees it: kinds, parts and classes, never the job or its reply.
 */
internal class TimedEngine(
    private val inner: OwnSolverEngine,
    private val jobs: MutableList<OwnSolverProbe.Summary>,
    private val clock: () -> Long,
) : OwnSolverEngine {
    override val isAvailable: Boolean get() = inner.isAvailable
    override val reportsReady: Boolean get() = inner.reportsReady

    override suspend fun run(input: String): String? = timed(input, null)

    override suspend fun run(input: String, events: OwnSolverEngine.Events): String? = timed(input, events)

    private suspend fun timed(input: String, events: OwnSolverEngine.Events?): String? {
        val sent = clock()
        val ready = AtomicLong(NOT_YET)
        val gone = AtomicBoolean(false)
        val tap = events?.let { outer ->
            object : OwnSolverEngine.Events {
                override fun started() {
                    ready.compareAndSet(NOT_YET, clock() - sent)
                    outer.started()
                }

                override fun gone() {
                    gone.set(true)
                    outer.gone()
                }
            }
        }
        var reply: String? = null
        var cancelled = false
        try {
            reply = if (tap != null) inner.run(input, tap) else inner.run(input)
            return reply
        } catch (cancellation: CancellationException) {
            cancelled = true
            throw cancellation
        } finally {
            val total = clock() - sent
            val readyMillis = ready.get().takeIf { it != NOT_YET }
            OwnSolverProbe.describe(input, reply)?.let { summary ->
                val outcome = when {
                    gone.get() -> OwnSolverProbe.GONE
                    cancelled && tap != null && readyMillis == null -> OwnSolverProbe.READY_TIMEOUT
                    cancelled -> OwnSolverProbe.JOB_TIMEOUT
                    else -> summary.outcome
                }
                val timed = summary.copy(outcome = outcome, readyMillis = readyMillis, totalMillis = total)
                synchronized(jobs) { jobs += timed }
            }
        }
    }

    private companion object {
        const val NOT_YET = Long.MIN_VALUE
    }
}

/** The app's runner as the gate sees it, noting what V did with the lookup's signatures. */
private class VerdictTap(private val inner: OwnPlayerScriptRunner) : PlayerScriptRunner, SignatureVerdicts {
    @Volatile private var ran = false
    @Volatile private var asked = false
    @Volatile private var given = false
    @Volatile private var checked: Boolean? = null
    @Volatile private var verdict: SignatureVerdicts.Verdict? = null

    override val isAvailable: Boolean get() = inner.isAvailable

    override suspend fun resolve(request: PlayerScriptRequest): PlayerScriptResult {
        val keys = request.challenges
            .filter { it.kind == PlayerScriptChallengeKind.SIGNATURE }
            .mapTo(HashSet()) { it.key }
        ran = true
        asked = keys.isNotEmpty()
        return inner.resolve(request).also { result ->
            given = (result as? PlayerScriptResult.Success)?.resolved?.keys?.any { it in keys } == true
        }
    }

    override fun pendingCheck(request: PlayerScriptRequest): PendingCheck? =
        inner.pendingCheck(request).also { checked = it != null }

    override fun record(check: PendingCheck, verdict: SignatureVerdicts.Verdict) {
        this.verdict = verdict
        inner.record(check, verdict)
    }

    val status: SolverCanaryReport.VStatus
        get() = when {
            !ran -> SolverCanaryReport.VStatus.NOT_RUN
            !asked -> SolverCanaryReport.VStatus.NOT_NEEDED
            !given -> SolverCanaryReport.VStatus.NOT_RUN
            verdict == SignatureVerdicts.Verdict.PASS -> SolverCanaryReport.VStatus.PASS
            verdict == SignatureVerdicts.Verdict.FAIL -> SolverCanaryReport.VStatus.FAIL
            verdict == SignatureVerdicts.Verdict.UNKNOWN -> SolverCanaryReport.VStatus.UNKNOWN
            checked == false -> SolverCanaryReport.VStatus.SKIPPED_LEDGER
            else -> SolverCanaryReport.VStatus.NOT_RUN
        }
}