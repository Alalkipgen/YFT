package com.alal.yft.detection.master

import com.alal.yft.BuildConfig
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.NoPlayerScriptRunner
import com.alal.yft.extractor.api.PlayerScriptRunner
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.modules.youtube.MasterYouTubeModule
import com.alal.yft.extractor.master.modules.youtube.ladder.LadderTrial
import com.alal.yft.extractor.master.modules.youtube.ladder.MasterClientLadder
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStart
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStartReader
import com.alal.yft.extractor.master.modules.youtube.ladder.VersionSourceReader
import com.alal.yft.extractor.master.solver.AtomicTextFile
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSource

/**
 * Phase 1.3 D6 (MASTER_KEY_PHASE1_3_PLAN.md guardrail 1): Master's client ladder, only in a debug
 * build made with `-Pyft.clientLadder=true` and `-Pyft.masterCapture=true`; otherwise Master's
 * YouTube module is R6 exactly as before. D7: one ladder per process, its health in the app's
 * `files/master/client-health.json` (data only; the file is found on first use, off this thread),
 * and the owner's live-check switch (`-Pyft.ladderSkip` / `-Pyft.ladderMutate`, debug only).
 * D5a's A reads its version source through [OkHttpVersionSourceReader] (at most once a day).
 */
internal object MasterLadderSwitch {
    private const val HEALTH_FILE = "master/client-health.json"

    @Volatile
    private var shared: MasterClientLadder? = null

    /**
     * Master's YouTube module. With the ladder, D5a's S (Plan adapted) is the own solver
     * ([playerScripts], used only when the build's own solver is on — never main's runner) in the
     * copied extractor's challenge and apply code, behind the module's signature gate (V).
     * [filesDir] locates the app's files directory for the health file (null: memory only).
     * Phase 1.4 item 7 route B R2: in a route-B build ([routeB], `-Pyft.youtubeRouteB`, debug only)
     * the module's client, the own solver's runner and the ladder's media reader are route B's
     * ([YouTubeRouteB]); they only pass through until the engine runs the module after main's
     * recoverable failure (R3). Otherwise every piece is as before.
     */
    fun youTube(
        http: ExtractorHttpClient,
        client: OkHttpClient,
        playerScripts: PlayerScriptRunner?,
        filesDir: (() -> File)? = null,
        enabled: Boolean = BuildConfig.CLIENT_LADDER_ENABLED,
        ownSolver: Boolean = BuildConfig.OWN_SOLVER_ENABLED,
        routeB: Boolean = YouTubeRouteB.enabled,
    ): MasterYouTubeModule {
        val masterHttp = YouTubeRouteB.masterClient(http, routeB)
        if (!enabled) return MasterYouTubeModule(masterHttp)
        val scripts = YouTubeRouteB.masterScripts(playerScripts?.takeIf { ownSolver } ?: NoPlayerScriptRunner, routeB)
        return MasterYouTubeModule(masterHttp, scripts, ladder(client, filesDir, routeB))
    }

    /**
     * Phase 1.4 item 2: the h-only live-check APK (the ladder on, `-Pyft.ladderSkip=h-only`):
     * a found YouTube page goes through Master, so H's row is what the owner downloads.
     */
    val hOnly: Boolean
        get() = BuildConfig.CLIENT_LADDER_ENABLED &&
            LadderTrial.of(BuildConfig.LADDER_SKIP, BuildConfig.LADDER_MUTATE).hOnly

    /**
     * The process's ladder: every tab shares its health (thread-safe), V ledger and switch. Its
     * reader is made once, as the build's route-B switch says (route B R2).
     */
    private fun ladder(client: OkHttpClient, filesDir: (() -> File)?, routeB: Boolean): MasterClientLadder =
        shared ?: synchronized(this) {
            shared ?: MasterClientLadder(
                healthFile = filesDir?.let { dir -> AtomicTextFile { File(dir(), HEALTH_FILE) } },
                reader = YouTubeRouteB.masterReader(OkHttpMediaStartReader(client), routeB),
                trial = LadderTrial.of(BuildConfig.LADDER_SKIP, BuildConfig.LADDER_MUTATE),
                sources = OkHttpVersionSourceReader(client),
            ).also { shared = it }
        }
}

/**
 * The first bytes of an L row's media file (guardrail 6): one GET with `Range: bytes=0-(n - 1)`,
 * HTTPS on a `googlevideo.com` host only (also after redirects), without cookie or credentials,
 * at most [MediaStartReader.read]'s `maxBytes` read. Nothing is logged.
 */
internal class OkHttpMediaStartReader(client: OkHttpClient) : MediaStartReader {
    private val http = client.newBuilder()
        .cookieJar(CookieJar.NO_COOKIES)
        .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override suspend fun read(url: String, headers: Map<String, String>, maxBytes: Int): MediaStart {
        require(maxBytes in 1..MAX_BYTES)
        val address = url.toHttpUrlOrNull()?.takeIf { it.isHttps && isMediaHost(it.host) }
            ?: return MediaStart.Failed(null)
        val builder = Request.Builder().url(address).get()
        headers.forEach { (name, value) -> if (name.trim().lowercase() !in DROPPED) builder.header(name, value) }
        builder.header("Range", "bytes=0-${maxBytes - 1}")
        return withContext(Dispatchers.IO) {
            try {
                http.newCall(builder.build()).execute().use { response ->
                    when {
                        !response.request.url.isHttps || !isMediaHost(response.request.url.host) -> MediaStart.Failed(null)
                        !response.isSuccessful -> MediaStart.Failed(response.code)
                        else -> MediaStart.Read(
                            statusCode = response.code,
                            contentType = response.header("Content-Type"),
                            contentRange = response.header("Content-Range"),
                            contentLength = response.header("Content-Length")?.toLongOrNull(),
                            bytes = response.body?.source()?.let { readAtMost(it, maxBytes) } ?: ByteArray(0),
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                MediaStart.Failed(null)
            }
        }
    }

    private fun readAtMost(source: BufferedSource, maxBytes: Int): ByteArray {
        source.request(maxBytes.toLong())
        return source.buffer.readByteArray(minOf(source.buffer.size, maxBytes.toLong()))
    }

    companion object {
        const val MAX_BYTES = 64 * 1024
        const val TIMEOUT_SECONDS = 10L
        private const val MEDIA_HOST = "googlevideo.com"
        private val DROPPED = setOf("cookie", "authorization", "range", "proxy-authorization")

        fun isMediaHost(host: String): Boolean {
            val name = host.lowercase().trimEnd('.')
            return name == MEDIA_HOST || name.endsWith(".$MEDIA_HOST")
        }
    }
}

/**
 * A's version source (D5a; §9 item 3): one GET of a public source text, HTTPS on
 * `raw.githubusercontent.com` only ([allowed]), with the user's User-Agent and no other header (no
 * cookie, no credentials), **no redirect followed** (a 3xx is a failure), at most
 * `maxBodyBytes` (≤ 64 KiB) read as UTF-8 text, a 10 s call timeout. Nothing is logged.
 */
internal class OkHttpVersionSourceReader(
    client: OkHttpClient,
    private val allowed: (HttpUrl) -> Boolean = { isSourceAddress(it) },
) : VersionSourceReader {
    private val http = client.newBuilder()
        .cookieJar(CookieJar.NO_COOKIES)
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override suspend fun read(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
        val limit = maxBodyBytes.coerceIn(1L, MAX_BYTES)
        val address = url.toHttpUrlOrNull()?.takeIf(allowed)
            ?: return ExtractorHttpResult.Failure(SiteExtractionFailure.UNSUPPORTED_URL)
        val builder = Request.Builder().url(address).get()
        headers.forEach { (name, value) -> if (name.trim().lowercase() in SENT) builder.header(name, value) }
        return withContext(Dispatchers.IO) {
            try {
                http.newCall(builder.build()).execute().use { response ->
                    val code = response.code
                    if (!response.isSuccessful) return@use ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, code)
                    val body = response.body
                        ?: return@use ExtractorHttpResult.Failure(SiteExtractionFailure.MALFORMED_RESPONSE, code)
                    val source = body.source()
                    if (body.contentLength() > limit || source.request(limit + 1)) {
                        ExtractorHttpResult.Failure(SiteExtractionFailure.RESPONSE_TOO_LARGE, code)
                    } else {
                        ExtractorHttpResult.Success(
                            statusCode = code,
                            body = source.readUtf8(),
                            finalUrl = address.toString(),
                            contentType = response.header("Content-Type"),
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)
            }
        }
    }

    companion object {
        const val MAX_BYTES = 64 * 1024L
        const val TIMEOUT_SECONDS = 10L
        private const val SOURCE_HOST = "raw.githubusercontent.com"
        private val SENT = setOf("user-agent")

        fun isSourceAddress(url: HttpUrl): Boolean = url.isHttps && url.host == SOURCE_HOST && url.port == HTTPS_PORT

        private const val HTTPS_PORT = 443
    }
}
