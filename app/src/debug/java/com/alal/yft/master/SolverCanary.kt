package com.alal.yft.master

import android.content.Context
import android.net.TrafficStats
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.webkit.WebViewCompat
import com.alal.yft.BuildConfig
import com.alal.yft.detection.OkHttpExtractorClient
import com.alal.yft.detection.script.WebViewSolverEngine
import com.alal.yft.detection.script.YouTubePlayerScriptRunner
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.PlayerScriptChallenge
import com.alal.yft.extractor.api.PlayerScriptChallengeKind
import com.alal.yft.extractor.api.PlayerScriptRequest
import com.alal.yft.extractor.api.PlayerScriptResult
import com.alal.yft.extractor.master.android.solver.OwnSolverAppBreaker
import com.alal.yft.extractor.master.android.solver.WebViewOwnSolverEngine
import com.alal.yft.extractor.master.android.solver.WebViewPageSolverEngine
import com.alal.yft.extractor.master.solver.OwnSolverBreaker
import com.alal.yft.extractor.master.solver.OwnSolverEngine
import com.alal.yft.extractor.master.solver.OwnSolverProbe
import java.io.File
import java.util.Random
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient

/**
 * Phase 1.2 N7, owner tool (debug build only): the phone canary. On today's YouTube player
 * (phone build, then main build) it runs ejs (the reference) and each own solver step through
 * [OwnSolverProbe]: P twice (raw, then the prepared program with the parts the first run found
 * right), B twice (raw, then with its parts) and the page (page P, then page B when page P did not
 * answer every kind cleanly), with the inputs of the computer canary (`OwnSolverCanaryTest`). Before
 * them, the app's lookup of one public video with V ([SolverCanaryLookup]), first so that in a new
 * process it includes the engine's cold start.
 *
 * Every step uses the app's breaker ([OwnSolverAppBreaker], owner's option b): a step the breaker
 * has off is not run, and the first step that does not reply (or runs out of budget) stops the
 * canary, so the report says where it stopped. The probe's timed window is the job only (sending
 * it to its reply); the breaker's marker writes fall outside it.
 */
internal class SolverCanary(
    private val context: Context,
    private val stall: StallMeter,
    private val onStep: (String) -> Unit,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) {
    /** A visible WebView's longest pause during a page job (the in-app browser's stall). */
    interface StallMeter {
        suspend fun prepare()

        suspend fun reset()

        suspend fun read(): Long?
    }

    suspend fun run(): SolverCanaryReport.Report {
        val started = clock()
        val newProcess = RUNS_IN_PROCESS.incrementAndGet() == 1
        val trafficBefore = traffic()
        val meter = SolverCanaryHttpMeter()
        val base = OkHttpClient.Builder().eventListenerFactory(meter.factory).build()
        val folder = File(context.filesDir, FOLDER)
        val run = withContext(Dispatchers.IO) { nextRun(folder) }
        val http = OkHttpExtractorClient(
            client = base,
            policy = OkHttpExtractorClient.Policy(callTimeoutSeconds = CALL_TIMEOUT_SECONDS),
        )
        val breaker = OwnSolverAppBreaker.get(context)

        onStep("today's player")
        val player = todaysPlayer(http)
        val header = SolverCanaryReport.Header(
            run = run,
            newProcess = newProcess,
            android = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            webView = WebViewCompat.getCurrentWebViewPackage(context)?.versionName ?: "unknown",
            app = BuildConfig.VERSION_NAME,
            player = player,
        )

        val lookup = SolverCanaryLookup(
            context = context,
            client = base,
            meter = meter,
            breaker = breaker,
            folder = folder,
            onStep = { onStep("lookup: $it") },
            clock = clock,
        ).run(player)
        var stoppedAt = lookup.stoppedAt
        val builds = mutableListOf<SolverCanaryReport.Build>()
        if (stoppedAt == null && player != null) {
            val cached = base.newBuilder()
                .cache(Cache(File(context.cacheDir, HTTP_CACHE), HTTP_CACHE_BYTES))
                .build()
            val steps = Steps(
                player = player,
                http = MemoHttp(
                    OkHttpExtractorClient(
                        client = cached,
                        policy = OkHttpExtractorClient.Policy(callTimeoutSeconds = CALL_TIMEOUT_SECONDS),
                    ),
                ),
                meter = meter,
                probe = OwnSolverProbe(breaker),
                worker = WebViewOwnSolverEngine(context),
                page = WebViewPageSolverEngine(context),
            )
            for ((name, variant) in BUILDS) {
                val (build, stop) = steps.build(name, variant)
                builds += build
                if (stop != null) {
                    stoppedAt = stop
                    break
                }
            }
        }
        val off = player?.let { id -> OwnSolverBreaker.Step.entries.filterNot { breaker.allows(id, it) } }.orEmpty()
        val signaturesOff = player?.let { id ->
            OwnSolverBreaker.Step.entries.filterNot { breaker.signaturesAllowed(id, it) }
        }.orEmpty()
        val trafficAfter = traffic()
        return SolverCanaryReport.Report(
            header = header,
            lookup = lookup.lookup,
            builds = builds,
            stoppedAt = stoppedAt,
            breakerOff = off.map(SolverCanaryReport::stepName),
            signaturesOff = signaturesOff.map(SolverCanaryReport::stepName),
            data = SolverCanaryReport.Data(
                appBytes = if (trafficBefore != null && trafficAfter != null) trafficAfter - trafficBefore else null,
                httpBytes = meter.bytesSoFar,
                calls = meter.callsSoFar,
                cacheHits = meter.cacheHitsSoFar,
            ),
            totalMillis = clock() - started,
        )
    }

    /** One player's builds, step by step, on the same inputs. */
    private inner class Steps(
        private val player: String,
        private val http: ExtractorHttpClient,
        private val meter: SolverCanaryHttpMeter,
        private val probe: OwnSolverProbe,
        private val worker: OwnSolverEngine,
        private val page: OwnSolverEngine,
    ) {
        private val challenges = challenges(player)
        private val rates = inputsOf(PlayerScriptChallengeKind.RATE_PARAM)
        private val signatures = inputsOf(PlayerScriptChallengeKind.SIGNATURE)

        private fun inputsOf(kind: PlayerScriptChallengeKind) = challenges.filter { it.kind == kind }.map { it.input }

        /** [name]'s build and the step that stopped the canary (null: none did). */
        suspend fun build(name: String, variant: String): Pair<SolverCanaryReport.Build, String?> {
            val url = "$PLAYER_ORIGIN$player/$variant"
            onStep("$name: player download")
            val bytes = meter.bytesSoFar
            val hits = meter.cacheHitsSoFar
            val fetchedAt = clock()
            val fetched = http.get(url, mapOf("Referer" to PAGE), YouTubePlayerScriptRunner.DEFAULT_MAX_PLAYER_BYTES)
            val download = SolverCanaryReport.Part(
                name = "download",
                millis = clock() - fetchedAt,
                bytes = meter.bytesSoFar - bytes,
                cached = meter.cacheHitsSoFar > hits,
            )
            val source = (fetched as? ExtractorHttpResult.Success)?.body?.takeIf(String::isNotBlank)
                ?: return SolverCanaryReport.Build(
                    name = name,
                    download = download,
                    failure = "download " + ((fetched as? ExtractorHttpResult.Failure)?.reason?.name ?: "empty"),
                ) to null

            onStep("$name: ejs")
            val ejsAt = clock()
            val ejs = YouTubePlayerScriptRunner(http, WebViewSolverEngine(context))
                .resolve(PlayerScriptRequest(url, PAGE, challenges))
            val ejsMillis = clock() - ejsAt
            val resolved = (ejs as? PlayerScriptResult.Success)?.resolved.orEmpty()
            val truth = SolverCanaryReport.KINDS.associateWith { kind ->
                challenges.filter { it.kind == kind }.mapNotNull { challenge ->
                    resolved[challenge.key]?.let { challenge.input to it }
                }.toMap()
            }
            var build = SolverCanaryReport.Build(
                name = name,
                download = download,
                ejsMillis = ejsMillis,
                ejsSolved = truth.mapValues { it.value.size },
                total = mapOf(
                    PlayerScriptChallengeKind.RATE_PARAM to rates.size,
                    PlayerScriptChallengeKind.SIGNATURE to signatures.size,
                ),
            )

            suspend fun step(
                label: String,
                engine: OwnSolverEngine,
                step: OwnSolverBreaker.Step,
                fromPrepared: Boolean = false,
            ): Pair<SolverCanaryReport.StepLine, OwnSolverProbe.Run> {
                onStep("$name: $label")
                val paged = step == OwnSolverBreaker.Step.PAGE_P || step == OwnSolverBreaker.Step.PAGE_B
                if (paged) {
                    stall.prepare()
                    stall.reset()
                }
                val run = probe.run(engine, step, player, name, source, rates, signatures, fromPrepared)
                val stallMillis = if (paged) stall.read() else null
                val sent = run.summary.outcome != OwnSolverProbe.BREAKER_OFF &&
                    run.summary.outcome != OwnSolverProbe.NO_PREPARED
                val counts = if (sent) {
                    mapOf(
                        PlayerScriptChallengeKind.RATE_PARAM to SolverCanaryReport.judge(
                            rates,
                            truth[PlayerScriptChallengeKind.RATE_PARAM].orEmpty(),
                            run.rate,
                        ),
                        PlayerScriptChallengeKind.SIGNATURE to SolverCanaryReport.judge(
                            signatures,
                            truth[PlayerScriptChallengeKind.SIGNATURE].orEmpty(),
                            run.signature,
                        ),
                    )
                } else {
                    emptyMap()
                }
                return SolverCanaryReport.StepLine(label, run.summary, counts, stallMillis) to run
            }

            fun right(line: SolverCanaryReport.StepLine) = line.counts.filterValues { it.clean }.keys

            fun stop(line: SolverCanaryReport.StepLine): String? = line.summary.takeIf { it.stopped }?.let { summary ->
                "$name ${line.name} (${summary.outcome}${if (summary.budget) ", budget" else ""})"
            }

            val (p1, p1Run) = step("P1", worker, OwnSolverBreaker.Step.WORKER_P)
            build = build.copy(p1 = p1)
            stop(p1)?.let { return build to it }
            probe.learn(p1Run, right(p1))
            if (probe.hasPrepared(player, name)) {
                val (p2, _) = step("P2", worker, OwnSolverBreaker.Step.WORKER_P, fromPrepared = true)
                build = build.copy(p2 = p2)
                stop(p2)?.let { return build to it }
            }
            val (b1, b1Run) = step("B1", worker, OwnSolverBreaker.Step.WORKER_B)
            build = build.copy(b1 = b1)
            stop(b1)?.let { return build to it }
            probe.learn(b1Run, right(b1))
            val (b2, _) = step("B2", worker, OwnSolverBreaker.Step.WORKER_B)
            build = build.copy(b2 = b2)
            stop(b2)?.let { return build to it }
            val (pageP, _) = step("page P", page, OwnSolverBreaker.Step.PAGE_P)
            build = build.copy(pageP = pageP)
            stop(pageP)?.let { return build to it }
            if (!pageP.summary.answered.containsAll(SolverCanaryReport.KINDS)) {
                val (pageB, _) = step("page B", page, OwnSolverBreaker.Step.PAGE_B)
                build = build.copy(pageB = pageB)
                stop(pageB)?.let { return build to it }
            }
            return build to null
        }
    }

    /** Today's player version, as the computer canary finds it. */
    private suspend fun todaysPlayer(http: ExtractorHttpClient): String? {
        val page = http.get(IFRAME_API) as? ExtractorHttpResult.Success ?: return null
        return EMBED_PLAYER.find(page.body)?.groupValues?.get(1)?.takeIf(PLAYER_ID::matches)
    }

    /** This install's run count (the tool's own file). */
    private fun nextRun(folder: File): Int = runCatching {
        folder.mkdirs()
        val file = File(folder, RUNS_FILE)
        val next = (file.takeIf(File::isFile)?.readText()?.trim()?.toIntOrNull() ?: 0) + 1
        file.writeText(next.toString())
        next
    }.getOrDefault(0)

    /** The app's bytes over the network so far (null: the device does not count them). */
    private fun traffic(): Long? {
        val uid = Process.myUid()
        val received = TrafficStats.getUidRxBytes(uid)
        val sent = TrafficStats.getUidTxBytes(uid)
        return if (received < 0 || sent < 0) null else received + sent
    }

    /** The last player text fetched, so ejs reads the canary's download instead of a second one. */
    private class MemoHttp(private val inner: ExtractorHttpClient) : ExtractorHttpClient by inner {
        private var last: Pair<String, ExtractorHttpResult.Success>? = null

        override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
            last?.takeIf { it.first == url }?.let { return it.second }
            return inner.get(url, headers, maxBodyBytes).also { result ->
                if (result is ExtractorHttpResult.Success) last = url to result
            }
        }
    }

    companion object {
        private val RUNS_IN_PROCESS = AtomicInteger()
        private const val FOLDER = "owner-canary"
        private const val RUNS_FILE = "runs.txt"
        private const val HTTP_CACHE = "owner-canary-http"
        private const val HTTP_CACHE_BYTES = 16L * 1024 * 1024
        private const val CALL_TIMEOUT_SECONDS = 60L
        private const val PHONE_VARIANT = "player-plasma-ias-phone-en_US.vflset/base.js"
        private const val MAIN_VARIANT = "player_ias.vflset/en_US/base.js"
        private val BUILDS = listOf(
            SolverCanaryReport.PHONE to PHONE_VARIANT,
            SolverCanaryReport.MAIN to MAIN_VARIANT,
        )

        // From YouTubePlayerScriptRunner (main's player path) and OwnSolverCanaryTest (the
        // computer canary's page, today's player and its inputs).
        private const val PLAYER_ORIGIN = "https://www.youtube.com/s/player/"
        private const val IFRAME_API = "https://www.youtube.com/iframe_api"
        private const val PAGE = "https://www.youtube.com/watch?v=jNQXAC9IVRw"
        private const val N_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        private const val SIG_CHARS = "$N_CHARS="
        private val PLAYER_ID = Regex("[A-Za-z0-9_-]{4,32}")
        private val EMBED_PLAYER = Regex("\\\\/s\\\\/player\\\\/([A-Za-z0-9_-]{4,32})\\\\/")

        fun challenges(player: String): List<PlayerScriptChallenge> {
            val random = Random(player.hashCode().toLong())
            fun pick(chars: String, length: Int) =
                String(CharArray(length) { chars[random.nextInt(chars.length)] })
            val rates = listOf(16, 18, 19).mapIndexed { index, length ->
                PlayerScriptChallenge("n-$index", PlayerScriptChallengeKind.RATE_PARAM, pick(N_CHARS, length))
            }
            val signatures = listOf(104, 108, 107).mapIndexed { index, length ->
                PlayerScriptChallenge("sig-$index", PlayerScriptChallengeKind.SIGNATURE, pick(SIG_CHARS, length))
            }
            return rates + signatures
        }
    }
}