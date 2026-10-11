package com.alal.yft.master

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.alal.yft.detection.OkHttpExtractorClient
import com.alal.yft.detection.script.WebViewSolverEngine
import com.alal.yft.detection.script.YouTubePlayerScriptRunner
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.PlayerScriptChallenge
import com.alal.yft.extractor.api.PlayerScriptChallengeKind
import com.alal.yft.extractor.api.PlayerScriptRequest
import com.alal.yft.extractor.api.PlayerScriptResult
import com.alal.yft.extractor.master.android.solver.WebViewOwnSolverEngine
import com.alal.yft.extractor.master.solver.OwnPlayerScriptRunner
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Phase 1.2 N2 phone measurement (MASTER_KEY_PHASE1_2_PLAN.md §2 "Live check V in more detail"):
 * how YouTube answers one stream address with the right `n`, an unchanged `n`, a wrong `n` and a
 * wrong signature. Each variant reads the first 1 MiB at most; the report holds its status class,
 * the bytes read and the time, and the `n` rule it suggests.
 *
 * Debug build only, and only when the owner starts it: from `StreamCheckMeasureTest`
 * (`-e yft.streamCheckMeasure 1`) or from the owner tool `StreamCheckMeasureActivity` (no
 * computer needed). The values are solved by main's ejs runner and Master's own runner, which must
 * agree. The report (app files `own-solver/stream-check-measure-*.json`, also logged) never holds
 * an address, a header or a value.
 */
class StreamCheckMeasurement(private val context: Context) {
    class Outcome(val report: String, val rightOpened: Boolean, val nRule: String)

    /** A stop with a short reason that never carries an address or a value. */
    class Failure(message: String) : Exception(message)

    /** [onStep] gets short progress labels, on the caller's thread. */
    suspend fun run(video: String = DEFAULT_VIDEO, onStep: (String) -> Unit = {}): Outcome {
        if (!VIDEO_ID.matches(video)) throw Failure("not a video ID")
        val http = OkHttpExtractorClient(
            client = OkHttpClient(),
            policy = OkHttpExtractorClient.Policy(callTimeoutSeconds = 60),
        )

        onStep("1/4 watch page")
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

        onStep("2/4 player request")
        val answer = http.postJson(
            "$ORIGIN/youtubei/v1/player?prettyPrint=false" + (apiKey?.let { "&key=$it" } ?: ""),
            playerBody(video, clientVersion, visitorData, sts),
            buildMap {
                put("User-Agent", USER_AGENT)
                put("Accept", "application/json")
                put("Origin", ORIGIN)
                put("Referer", "$ORIGIN/embed/$video")
                put("X-YouTube-Client-Name", "56")
                put("X-YouTube-Client-Version", clientVersion)
                visitorData?.let { put("X-Goog-Visitor-Id", it) }
            },
        ).successOr("player request")
        val response = runCatching { JSONObject(answer.body) }.getOrNull() ?: throw Failure("player answer is not JSON")
        val playability = response.optJSONObject("playabilityStatus")?.optString("status").orEmpty()
        if (playability != "OK") {
            throw Failure("not playable for the embedded client: " + playability.filter(Char::isLetter).take(40))
        }
        val stream = pickStream(response) ?: throw Failure("no adaptive video stream of at least 1 MiB")

        onStep("3/4 solving n and sig (ejs and own)")
        val challenges = buildList {
            stream.n?.let { add(PlayerScriptChallenge("n-0", PlayerScriptChallengeKind.RATE_PARAM, it)) }
            stream.s?.let { add(PlayerScriptChallenge("sig-0", PlayerScriptChallengeKind.SIGNATURE, it)) }
        }
        if (challenges.isEmpty()) throw Failure("the stream needs neither n nor a signature")
        val request = PlayerScriptRequest(
            playerScriptUrl = "$ORIGIN/s/player/$player/$PHONE_VARIANT",
            pageUrl = "$ORIGIN/watch?v=$video",
            challenges = challenges,
        )
        val ejs = YouTubePlayerScriptRunner(http, WebViewSolverEngine(context)).resolve(request)
        val own = OwnPlayerScriptRunner(http, WebViewOwnSolverEngine(context)).resolve(request)
        val agree = challenges.all { challenge ->
            val value = ejs.valueOf(challenge.key)
            value != null && value == own.valueOf(challenge.key)
        }
        if (!agree) throw Failure("ejs (${ejs.label()}) and own (${own.label()}) do not agree; nothing measured")
        val rightN = stream.n?.let { ejs.valueOf("n-0") }
        val rightSig = stream.s?.let { ejs.valueOf("sig-0") }

        val variants = buildList {
            add("right" to stream.address(rightN, rightSig))
            if (stream.s != null) add("wrongSig" to stream.address(rightN, rightSig?.let(::changeOne)))
            if (stream.n != null) {
                add("unchangedN" to stream.address(stream.n, rightSig))
                add("wrongN" to stream.address(rightN?.let(::changeOne), rightSig))
            }
            add("rightAgain" to stream.address(rightN, rightSig))
        }
        val media = OkHttpClient.Builder()
            .callTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
        val measured = LinkedHashMap<String, Read>()
        variants.forEachIndexed { index, (name, address) ->
            onStep("4/4 reading the stream ${index + 1}/${variants.size} ($name)")
            measured[name] = read(media, address)
        }
        val reference = measured.getValue("right").millis.coerceAtLeast(1)

        val wrongN = measured["wrongN"]?.statusClass
        val unchangedN = measured["unchangedN"]?.statusClass
        val nRule = when {
            wrongN == null -> "PENDING"
            wrongN == "403" && unchangedN == "403" -> "JUDGES_N"
            wrongN == "2xx" -> "SIG_ONLY"
            else -> "PENDING"
        }
        val rightOpened = measured.getValue("right").statusClass == "2xx" &&
            measured.getValue("rightAgain").statusClass == "2xx"
        val report = buildString {
            append("{\"measure\":\"stream-check\",\"video\":\"").append(video).append('"')
            append(",\"player\":\"").append(player).append('"')
            append(",\"itag\":").append(stream.itag)
            append(",\"hasN\":").append(stream.n != null)
            append(",\"hasSig\":").append(stream.s != null)
            append(",\"agree\":").append(agree)
            append(",\"variants\":{")
            append(
                measured.entries.joinToString(",") { (name, outcome) ->
                    val speed = if (outcome.statusClass == "2xx") {
                        if (outcome.millis > reference * SLOW_FACTOR) "slow" else "fast"
                    } else {
                        "none"
                    }
                    "\"$name\":{\"status\":\"${outcome.statusClass}\",\"code\":${outcome.code}," +
                        "\"bytes\":${outcome.bytes},\"ms\":${outcome.millis},\"speed\":\"$speed\"}"
                },
            )
            append("},\"sigRule\":\"").append(measured["wrongSig"]?.statusClass ?: "not-measured").append('"')
            append(",\"nRule\":\"").append(if (rightOpened) nRule else "PENDING").append('"')
            append(",\"valid\":").append(rightOpened).append('}')
        }
        val folder = File(checkNotNull(context.getExternalFilesDir(null)), "own-solver")
        folder.mkdirs()
        File(folder, "stream-check-measure-${System.currentTimeMillis()}.json").writeText(report)
        Log.i(TAG, report)
        return Outcome(report, rightOpened, if (rightOpened) nRule else "PENDING")
    }

    private fun playerBody(video: String, clientVersion: String, visitorData: String?, sts: Int): String =
        JSONObject()
            .put(
                "context",
                JSONObject()
                    .put(
                        "client",
                        JSONObject()
                            .put("clientName", "WEB_EMBEDDED_PLAYER")
                            .put("clientVersion", clientVersion)
                            .put("hl", "en")
                            .put("timeZone", "UTC")
                            .put("utcOffsetMinutes", 0)
                            .apply { visitorData?.let { put("visitorData", it) } },
                    )
                    .put("thirdParty", JSONObject().put("embedUrl", EMBED_URL)),
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

    private suspend fun read(client: OkHttpClient, address: String): Read = withContext(Dispatchers.IO) {
        if (!isStreamHost(address)) throw Failure("only the stream host YouTube returned is asked")
        val started = SystemClock.elapsedRealtime()
        try {
            val request = Request.Builder()
                .url(address)
                .header("User-Agent", USER_AGENT)
                .header("Origin", ORIGIN)
                .header("Referer", "$ORIGIN/")
                .header("Range", "bytes=0-${MEASURE_BYTES - 1}")
                .header("Accept-Encoding", "identity")
                .build()
            client.newCall(request).execute().use { response ->
                var bytes = 0L
                if (response.isSuccessful) {
                    val source = checkNotNull(response.body).byteStream()
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (bytes < MEASURE_BYTES) {
                        val count = source.read(buffer, 0, minOf(BUFFER_BYTES.toLong(), MEASURE_BYTES - bytes).toInt())
                        if (count < 0) break
                        bytes += count
                    }
                }
                Read(statusClass(response.code), response.code, bytes, SystemClock.elapsedRealtime() - started)
            }
        } catch (failure: IOException) {
            Read("network", 0, 0, SystemClock.elapsedRealtime() - started)
        }
    }

    private fun statusClass(code: Int): String = when (code) {
        in 200..299 -> "2xx"
        403 -> "403"
        in 400..499 -> "4xx"
        in 500..599 -> "5xx"
        else -> "other"
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

    private fun PlayerScriptResult.valueOf(key: String): String? =
        (this as? PlayerScriptResult.Success)?.resolved?.get(key)

    private fun PlayerScriptResult.label(): String = when (this) {
        is PlayerScriptResult.Success -> "ok"
        is PlayerScriptResult.Failed -> reason.name
        else -> "unavailable"
    }

    /** A value of the same length and alphabet with one character changed. */
    private fun changeOne(value: String): String {
        val index = value.length / 2
        val replacement = if (value[index] == 'A') 'B' else 'A'
        return value.substring(0, index) + replacement + value.substring(index + 1)
    }

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

    private class Read(val statusClass: String, val code: Int, val bytes: Long, val millis: Long)

    companion object {
        const val DEFAULT_VIDEO = "aqz-KE-bpKQ"
        private const val TAG = "YftStreamCheck"
        private const val ORIGIN = "https://www.youtube.com"
        private const val EMBED_URL = "https://github.com/Alalkipgen/YFT"
        private const val PHONE_VARIANT = "player-plasma-ias-phone-en_US.vflset/base.js"
        private const val FALLBACK_CLIENT_VERSION = "2.20260708.00.00"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36"
        private const val PAGE_LIMIT = 8L * 1024 * 1024
        private const val MEASURE_BYTES = 1024L * 1024
        private const val BUFFER_BYTES = 64 * 1024
        private const val READ_TIMEOUT_SECONDS = 60L
        private const val SLOW_FACTOR = 3
        val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
        private val JS_URL = Regex("\"(?:jsUrl|PLAYER_JS_URL)\"\\s*:\\s*\"([^\"]{1,400})\"")
        private val PLAYER_IN_PATH = Regex("/s/player/([A-Za-z0-9_-]{4,32})/")
        private val STS = Regex("\"(?:STS|signatureTimestamp)\"\\s*:\\s*(\\d{4,7})\\b")
        private val CLIENT_VERSION = Regex("\"INNERTUBE_CLIENT_VERSION\"\\s*:\\s*\"(2\\.\\d{8}\\.\\d{2}\\.\\d{2})\"")
        private val VISITOR_DATA = Regex("\"(?:VISITOR_DATA|visitorData)\"\\s*:\\s*\"([A-Za-z0-9_\\-%=+/]{5,2000})\"")
        private val API_KEY = Regex("\"INNERTUBE_API_KEY\"\\s*:\\s*\"([A-Za-z0-9_-]{10,80})\"")
    }
}
