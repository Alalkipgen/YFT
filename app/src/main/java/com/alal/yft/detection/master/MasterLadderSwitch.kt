package com.alal.yft.detection.master

import com.alal.yft.BuildConfig
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.NoPlayerScriptRunner
import com.alal.yft.extractor.api.PlayerScriptRunner
import com.alal.yft.extractor.master.modules.youtube.MasterYouTubeModule
import com.alal.yft.extractor.master.modules.youtube.ladder.MasterClientLadder
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStart
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStartReader
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSource

/**
 * Phase 1.3 D6 (MASTER_KEY_PHASE1_3_PLAN.md guardrail 1): Master's client ladder, only in a debug
 * build made with `-Pyft.clientLadder=true` and `-Pyft.masterCapture=true`; otherwise Master's
 * YouTube module is R6 exactly as before. Health in memory for now (its file comes with D7).
 */
internal object MasterLadderSwitch {
    /**
     * Master's YouTube module. With the ladder, D5a's S (Plan adapted) is the own solver
     * ([playerScripts], used only when the build's own solver is on — never main's runner) in the
     * copied extractor's challenge and apply code, behind the module's signature gate (V).
     */
    fun youTube(
        http: ExtractorHttpClient,
        client: OkHttpClient,
        playerScripts: PlayerScriptRunner?,
        enabled: Boolean = BuildConfig.CLIENT_LADDER_ENABLED,
        ownSolver: Boolean = BuildConfig.OWN_SOLVER_ENABLED,
    ): MasterYouTubeModule {
        if (!enabled) return MasterYouTubeModule(http)
        val ladder = MasterClientLadder(healthFile = null, reader = OkHttpMediaStartReader(client))
        val scripts = playerScripts?.takeIf { ownSolver } ?: NoPlayerScriptRunner
        return MasterYouTubeModule(http, scripts, ladder)
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
