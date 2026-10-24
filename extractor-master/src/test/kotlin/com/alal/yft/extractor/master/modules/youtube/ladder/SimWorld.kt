package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.PlayerScriptChallengeKind
import com.alal.yft.extractor.api.PlayerScriptRequest
import com.alal.yft.extractor.api.PlayerScriptResult
import com.alal.yft.extractor.api.PlayerScriptRunner
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.api.json.path
import com.alal.yft.extractor.master.modules.youtube.YouTubeClientProfiles
import com.alal.yft.extractor.master.modules.youtube.YouTubeParseResult
import com.alal.yft.extractor.master.modules.youtube.YouTubePlayerResponseParser
import com.alal.yft.extractor.master.modules.youtube.YouTubeUrls
import com.alal.yft.extractor.master.modules.youtube.testing.FakePlayerScriptRunner
import com.alal.yft.extractor.master.modules.youtube.testing.Fixtures
import com.alal.yft.extractor.master.solver.OwnSolverBreaker
import com.alal.yft.extractor.master.solver.PendingCheck
import com.alal.yft.extractor.master.solver.SignatureVerdicts
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/** Phase 1.3 D1: names and addresses of the change simulator's world (all made up). */
internal object Sim {
    const val VIDEO_ID = "Yft0Fixture"
    const val OTHER_VIDEO = "Yft0Decoy00"
    const val NOW = 1_791_000_000_000L
    const val SHARED_LINK = "https://youtu.be/$VIDEO_ID?si=sim"
    const val MOBILE_PAGE = "https://m.youtube.com/watch?v=$VIDEO_ID"
    const val EMBED_PAGE = "https://www.youtube.com/embed/$VIDEO_ID"
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Sim) Mobile Safari/537.36"
    const val COOKIE = "PREF=sim; SID=sim-session"
    const val DEVICE_HOST = "rr3---sn-simdevice.googlevideo.com"
    const val WEB_HOST = "rr1---sn-simweb.googlevideo.com"
    const val APP_HOST = "rr2---sn-simapp.googlevideo.com"
    const val MANIFEST_HOST = "manifest.googlevideo.com"
    const val FOREIGN_HOST = "media.example-cdn.test"
    const val NEW_VERSION = "1.03"
    const val PLAYER_ID = "f1x7ure0"
    val BASE_VERSION: String = YouTubeClientProfiles.VISION_OS.clientVersion

    /** The plan's 5 base answers (§4 D1), main's fixtures, read in place. */
    val BASES = listOf(
        "player_visionos.json",
        "player_visionos_ladder.json",
        "player_ok.json",
        "player_4k.json",
        "player_4k_av1_only.json",
    )

    val PAGE_FETCH: String get() = YouTubeUrls.watchPageFetchUrl(VIDEO_ID)

    fun enc(text: String): String = URLEncoder.encode(text, Charsets.UTF_8)

    fun query(url: String): Map<String, String> {
        val raw = runCatching { URI(url).rawQuery }.getOrNull() ?: return emptyMap()
        return raw.split('&').filter { '=' in it }.associate {
            URLDecoder.decode(it.substringBefore('='), Charsets.UTF_8) to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8)
        }
    }
}

/** The identity a player request names (`context.client.clientName`); PAGE is the page's inline answer. */
internal enum class SimClient(val clientName: String, val tag: String, val host: String, val script: Boolean) {
    VISION_OS("VISIONOS", "vo", Sim.DEVICE_HOST, false),
    ANDROID("ANDROID", "an", Sim.APP_HOST, false),
    EMBEDDED("WEB_EMBEDDED_PLAYER", "em", Sim.WEB_HOST, true),
    MWEB("MWEB", "mw", Sim.WEB_HOST, true),
    PAGE("(page)", "pg", Sim.WEB_HOST, true),
    OTHER("(other)", "ot", Sim.WEB_HOST, true),
    ;

    companion object {
        fun of(name: String): SimClient = entries.firstOrNull { it.clientName == name } ?: OTHER
    }
}

/** One player request as the world sees it. */
internal data class SimAsk(val client: SimClient, val version: String, val visitor: Boolean, val sts: Boolean)

internal sealed interface SimReply {
    data class Json(val body: String) : SimReply
    data class Status(val code: Int) : SimReply
    data object Network : SimReply
}

/**
 * V on a case's L rows (plan §4 D1), the files only a layout-aware reader can find (those the
 * copied parser cannot read, and with [SimWorld.pageLocated] those that need the moved page
 * signals): the media host answers them (pass), refuses them (fail) or cannot be reached.
 */
internal enum class VMode { PASS, FAIL, UNKNOWN }

internal enum class SolverMode { RIGHT, WRONG, UNSOLVED }

/** One media file of the world; a row is right only when its address opens this file as it is. */
internal data class SimMedia(
    val key: String,
    val host: String,
    val itag: Int,
    val videoId: String,
    val status: Int,
    val n: String?,
    val s: String?,
    val size: Long,
    val mime: String,
    val encrypted: Boolean,
)

/** HLS made in code: master and variant playlist and segments, with the F3 / V11 faults as knobs. */
internal class SimHls(private val lengthSeconds: Long, private val maxHeight: Int) {
    var present = true
    var master = OK
    var variant = OK
    var segment = OK
    var segmentHost = Sim.DEVICE_HOST
    var reachable = true
    var contentType = "video/mp2t"
    var startText = "G"
    var durationSeconds = lengthSeconds.toDouble()
    var key: String? = null

    /** D5c: the sound as its own rendition (`#EXT-X-MEDIA:TYPE=AUDIO` with a URI, as D0 saw live), not muxed. */
    var separateAudio = false

    val masterUrl = "https://${Sim.MANIFEST_HOST}/api/manifest/hls_variant/expire/4102444800/id/sim/source/youtube/yftsim/hls/file/index.m3u8"
    val variantUrl = "https://${Sim.MANIFEST_HOST}/api/manifest/hls_playlist/expire/4102444800/id/sim/itag/96/yftsim/hls96/file/index.m3u8"
    val audioUrl = "https://${Sim.MANIFEST_HOST}/api/manifest/hls_playlist/expire/4102444800/id/sim/itag/234/yftsim/hls234/file/index.m3u8"

    fun segmentUrl(index: Int) = "https://$segmentHost/videoplayback/id/sim/itag/96/sq/$index/yftsim/seg/file/seg.ts"

    /** An HLS row of this world plays: every step 2xx, a TS start, the video's length, no key. */
    val good: Boolean
        get() = present && reachable && master == OK && variant == OK && segment == OK &&
            segmentHost.endsWith(".googlevideo.com") && contentType == "video/mp2t" && startText == "G" &&
            Math.abs(durationSeconds - lengthSeconds) <= 2.0 && key == null

    fun masterText(): String = buildString {
        append("#EXTM3U\n#EXT-X-INDEPENDENT-SEGMENTS\n")
        key?.let { append("#EXT-X-SESSION-KEY:METHOD=$it,URI=\"https://${Sim.MANIFEST_HOST}/key\"\n") }
        if (separateAudio) append("#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"234\",NAME=\"Default\",DEFAULT=YES,AUTOSELECT=YES,URI=\"$audioUrl\"\n")
        for (height in listOf(maxHeight, 720, 360).filter { it <= maxHeight }.distinct()) {
            append("#EXT-X-STREAM-INF:BANDWIDTH=${height * 4_000},CODECS=\"avc1.4d401f,mp4a.40.2\",")
            append("RESOLUTION=${height * 16 / 9}x$height,FRAME-RATE=30")
            if (separateAudio) append(",AUDIO=\"234\"")
            append('\n')
            append(if (height == maxHeight) variantUrl else variantUrl.replace("itag/96", "itag/9$height")).append('\n')
        }
    }

    fun variantText(): String = buildString {
        append("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:5\n#EXT-X-PLAYLIST-TYPE:VOD\n")
        var left = durationSeconds
        var index = 0
        while (left > 0.0005) {
            val piece = minOf(5.0, left)
            append("#EXTINF:").append(String.format(Locale.ROOT, "%.3f", piece)).append(",\n")
            append(segmentUrl(index++)).append('\n')
            left -= piece
        }
        append("#EXT-X-ENDLIST\n")
    }

    fun get(url: String): ExtractorHttpResult? = when {
        !reachable && (url == masterUrl || url == variantUrl || url == audioUrl || isSegment(url)) -> ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)
        url == masterUrl -> reply(master, masterText(), url, PLAYLIST)
        url == variantUrl -> reply(variant, variantText(), url, PLAYLIST)
        url == audioUrl && separateAudio -> reply(variant, variantText(), url, PLAYLIST)
        isSegment(url) -> reply(segment, startText + "@".repeat(187), url, contentType)
        else -> null
    }

    fun probe(url: String): ExtractorProbeResult? = when {
        !isSegment(url) -> null
        !reachable -> ExtractorProbeResult.Refused(SiteExtractionFailure.NETWORK)
        segment == OK -> ExtractorProbeResult.Answered(206, 188_000)
        else -> ExtractorProbeResult.Refused(SiteExtractionFailure.HTTP_STATUS, segment)
    }

    private fun isSegment(url: String) = "/yftsim/seg/" in url

    private fun reply(status: Int, body: String, url: String, type: String): ExtractorHttpResult =
        if (status == OK) {
            ExtractorHttpResult.Success(statusCode = OK, body = body, finalUrl = url, contentType = type)
        } else {
            ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, status)
        }

    companion object {
        const val OK = 200
        const val PLAYLIST = "application/vnd.apple.mpegurl"
    }
}

/**
 * Phase 1.3 D1: one simulated YouTube (MASTER_KEY_PHASE1_3_PLAN.md §4 D1), built from one of main's
 * base answers. Every identity answers from the same formats, each with its own made-up address:
 * visionOS direct on its host (with an HLS address), the script clients (embedded, MWEB, the
 * page's inline MWEB answer) with `signatureCipher` and `n` (their addresses open only after the
 * right values and, when asked, with the page's signature timestamp), Android with main's
 * progressive-only answer. A case changes it through the knobs. The world knows which file every
 * address opens ([truth]), so a row is judged by what the media host would do with it.
 */
internal class SimWorld(val baseName: String, val vMode: VMode = VMode.PASS) {
    private val base: Map<String, Any?> = SimJson.obj(SimJson.parse(Fixtures.read("youtube/$baseName")))
    private val androidBase: Map<String, Any?> = SimJson.obj(SimJson.parse(Fixtures.read("youtube/player_android.json")))

    val lengthSeconds: Long = (SimJson.at(base, "videoDetails", "lengthSeconds") as String).toLong()
    private val maxHeight: Int = listOf("formats", "adaptiveFormats")
        .flatMap { (SimJson.at(base, "streamingData", it) as? List<*>).orEmpty() }
        .mapNotNull { ((it as Map<*, *>)["height"] as? SimJson.Num)?.text?.toInt() }
        .maxOrNull() ?: DEFAULT_HEIGHT
    val hls = SimHls(lengthSeconds, minOf(maxHeight, HLS_MAX_HEIGHT))
    val media = LinkedHashMap<String, SimMedia>()
    val asks = mutableListOf<SimAsk>()
    val http = SimHttp(this)

    // Knobs a case sets.
    var acceptedVersions: Set<String> = setOf(Sim.BASE_VERSION)
    var visionCipher = false
    var solver = SolverMode.RIGHT
    var probesDown = false
    var pageReachable = true
    var pageInline = true
    /** The page's inline answer as raw text instead of the world's own (T and F3 cases). */
    var pageJson: String? = null
    var encrypted = false
    var visionVideo = Sim.VIDEO_ID
    /** L14: the page signals moved, so every file that needs the player script is an L row. */
    var pageLocated = false
    var override: (SimAsk) -> SimReply? = { null }
    var refused: (SimClient, Map<String, Any?>, String) -> Boolean = { _, _, _ -> false }
    val edits = mutableListOf<(SimClient, MutableMap<String, Any?>) -> Unit>()
    val pageEdits = mutableListOf<(String) -> String>()
    val sources = LinkedHashMap<String, SimReply>()
    private val unreachable = mutableSetOf<String>()

    fun answer(ask: SimAsk): MutableMap<String, Any?> {
        val client = ask.client
        val json = SimJson.obj(SimJson.copy(if (client == SimClient.ANDROID) androidBase else base))
        val cipher = client.script || (client == SimClient.VISION_OS && visionCipher)
        val stsOk = !cipher || ask.sts || client == SimClient.PAGE
        val video = if (client == SimClient.VISION_OS) visionVideo else Sim.VIDEO_ID
        val streaming = json["streamingData"] as? MutableMap<*, *>
        val made = mutableListOf<String>()
        if (streaming != null) {
            val data = SimJson.obj(streaming)
            var index = 0
            for (listKey in LIST_KEYS) {
                for (item in (data[listKey] as? List<*>).orEmpty()) {
                    val format = SimJson.obj(item)
                    if (!format.containsKey("url") && !format.containsKey("signatureCipher")) continue
                    val itag = (format["itag"] as SimJson.Num).text.toInt()
                    val variant = (if (cipher) "s" else "") + (if (stsOk) "" else "x")
                    val key = "${client.tag}$variant-$listKey-${index++}-$itag"
                    val mime = format["mimeType"] as? String ?: ""
                    val n = if (cipher || client.script) "n" + key.filter(Char::isLetterOrDigit) else null
                    val s = if (cipher) "s" + key.filter(Char::isLetterOrDigit) + "Q" else null
                    media[key] = SimMedia(
                        key = key,
                        host = client.host,
                        itag = itag,
                        videoId = video,
                        status = if (!stsOk || refused(client, format, listKey)) FORBIDDEN else OK,
                        n = n,
                        s = s,
                        size = (format["contentLength"] as? String)?.toLongOrNull() ?: (1_000_000L + itag * 1_000L),
                        mime = mime,
                        encrypted = encrypted,
                    )
                    made += key
                    val url = mediaUrl(client.host, itag, key, mime, n, lengthSeconds)
                    format.remove("url")
                    format.remove("signatureCipher")
                    format["approxDurationMs"] = (lengthSeconds * 1_000).toString()
                    if (cipher) format["signatureCipher"] = "s=${Sim.enc(s!!)}&sp=sig&url=${Sim.enc(url)}" else format["url"] = url
                }
            }
            if (client == SimClient.VISION_OS && hls.present) data["hlsManifestUrl"] = hls.masterUrl else data.remove("hlsManifestUrl")
        }
        (json["videoDetails"] as? MutableMap<*, *>)?.let(SimJson::obj)?.let { details ->
            details["videoId"] = video
            details["lengthSeconds"] = lengthSeconds.toString()
        }
        edits.forEach { it(client, json) }
        if (vMode != VMode.PASS) bindV(client, json, made)
        return json
    }

    /** V on the L rows: the files of this answer only L finds are refused (fail) or time out (unknown). */
    private fun bindV(client: SimClient, json: Map<String, Any?>, made: List<String>) {
        val text = SimJson.write(json)
        val readable = readableKeys(text)
        for (key in made) {
            val file = media.getValue(key)
            if (key in readable && !(pageLocated && file.n != null)) continue
            if (vMode == VMode.FAIL) media[key] = file.copy(status = FORBIDDEN) else unreachable += key
        }
        val moved = SimJson.at(json, "streamingData", "hlsManifestUrl") != hls.masterUrl && hls.masterUrl in text
        if (client == SimClient.VISION_OS && hls.present && moved) {
            if (vMode == VMode.FAIL) hls.master = FORBIDDEN else hls.reachable = false
        }
    }

    /** The world's files the copied parser (R6) reads from [text] as usable formats. */
    private fun readableKeys(text: String): Set<String> {
        val video = (YouTubePlayerResponseParser.parse(text, Sim.NOW) as? YouTubeParseResult.Success)?.video
            ?: return emptySet()
        return (video.progressive + video.adaptive).filter { it.mimeType != null }.mapNotNull { stream ->
            val address = stream.url ?: stream.protectedDescriptor?.let { Sim.query("https://x.test/?$it")["url"] }
            address?.let { Sim.query(it)["yftsim"] }
        }.toSet()
    }

    fun mediaUrl(host: String, itag: Int, key: String, mime: String, n: String?, seconds: Long): String =
        "https://$host/videoplayback?expire=4102444800&id=o-sim&itag=$itag&source=youtube" +
            "&mime=${Sim.enc(mime.substringBefore(';').trim())}&dur=$seconds.000&yftsim=$key" + (n?.let { "&n=$it" } ?: "")

    /** Registers a file the world serves outside any answer (decoys); returns its address. */
    fun extraFile(key: String, itag: Int, mime: String, video: String, seconds: Long, status: Int = OK): String {
        media[key] = SimMedia(key, Sim.DEVICE_HOST, itag, video, status, null, null, 2_000_000L + itag, mime, encrypted)
        return mediaUrl(Sim.DEVICE_HOST, itag, key, mime, null, seconds)
    }

    fun pageHtml(): String {
        val inline = pageJson?.trim()
            ?: if (pageInline) SimJson.write(answer(SimAsk(SimClient.PAGE, "", visitor = true, sts = true))) else "null"
        var html = Fixtures.read("youtube/watch_page.html").replace("__PLAYER_RESPONSE__", inline)
        pageEdits.forEach { html = it(html) }
        return html
    }

    /** Why the media host would not give this video's file as it is at [url], or null when it would. */
    fun truth(url: String?): String? {
        url ?: return null
        if (url == hls.masterUrl) {
            return when {
                !hls.good -> "hls"
                visionVideo != Sim.VIDEO_ID -> "other video"
                else -> null
            }
        }
        val uri = runCatching { URI(url) }.getOrNull() ?: return "address"
        val params = Sim.query(url)
        val file = params["yftsim"]?.let(media::get) ?: return "unknown"
        return when {
            uri.host != file.host -> "host"
            file.n != null && params["n"] != FakePlayerScriptRunner.transform(PlayerScriptChallengeKind.RATE_PARAM, file.n) -> "n"
            file.s != null && params["sig"] != FakePlayerScriptRunner.transform(PlayerScriptChallengeKind.SIGNATURE, file.s) -> "sig"
            file.status != OK -> "403"
            file.key in unreachable -> "unchecked"
            file.videoId != Sim.VIDEO_ID -> "other video"
            file.encrypted -> "encrypted"
            else -> null
        }
    }

    fun post(body: String): Pair<ExtractorHttpResult, Boolean> {
        val root = BoundedJsonParser.parse(body, maxNodes = 2_000)
        val client = root.path("context", "client")
        val ask = SimAsk(
            client = SimClient.of(client["clientName"].asStringOrNull.orEmpty()),
            version = client["clientVersion"].asStringOrNull.orEmpty(),
            visitor = client["visitorData"].asStringOrNull != null,
            sts = root.path("playbackContext", "contentPlaybackContext", "signatureTimestamp") != null,
        )
        asks += ask
        return when (val reply = override(ask) ?: defaultReply(ask)) {
            is SimReply.Json -> json(reply.body) to isBotCheck(reply.body)
            is SimReply.Status -> ExtractorHttpResult.Failure(
                if (reply.code == TOO_MANY) SiteExtractionFailure.RATE_LIMITED else SiteExtractionFailure.HTTP_STATUS,
                reply.code,
            ) to (reply.code == TOO_MANY)
            SimReply.Network -> ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK) to false
        }
    }

    fun defaultReply(ask: SimAsk): SimReply =
        if (ask.client == SimClient.VISION_OS && ask.version !in acceptedVersions) {
            SimReply.Status(BAD_REQUEST)
        } else {
            SimReply.Json(SimJson.write(answer(ask)))
        }

    fun get(url: String): ExtractorHttpResult {
        hls.get(url)?.let { return it }
        sources[url]?.let { reply ->
            return when (reply) {
                is SimReply.Json -> ExtractorHttpResult.Success(statusCode = OK, body = reply.body, finalUrl = url, contentType = "text/plain")
                is SimReply.Status -> ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, reply.code)
                SimReply.Network -> ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)
            }
        }
        return when {
            url == Sim.PAGE_FETCH -> page(Sim.MOBILE_PAGE) { it }
            url.startsWith(Sim.EMBED_PAGE) -> page(url) {
                it.replace("\"INNERTUBE_CLIENT_NAME\":\"MWEB\"", "\"INNERTUBE_CLIENT_NAME\":\"WEB_EMBEDDED_PLAYER\"")
            }
            YouTubeUrls.isMediaServerRequest(url) -> mediaGet(url)
            else -> ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, NOT_FOUND)
        }
    }

    fun probe(url: String): ExtractorProbeResult {
        hls.probe(url)?.let { return it }
        val key = Sim.query(url)["yftsim"]
        if (probesDown || key in unreachable) return ExtractorProbeResult.Refused(SiteExtractionFailure.NETWORK)
        val file = key?.let(media::get)
            ?: return ExtractorProbeResult.Refused(SiteExtractionFailure.HTTP_STATUS, NOT_FOUND)
        return when (truth(url)) {
            null, "other video", "encrypted" -> ExtractorProbeResult.Answered(206, file.size)
            else -> ExtractorProbeResult.Refused(SiteExtractionFailure.HTTP_STATUS, FORBIDDEN)
        }
    }

    fun kindOf(url: String): SimHttp.Kind = when {
        url == Sim.PAGE_FETCH || url.startsWith(Sim.EMBED_PAGE) -> SimHttp.Kind.PAGE
        url in sources -> SimHttp.Kind.SOURCE
        hls.get(url) != null -> SimHttp.Kind.HLS
        YouTubeUrls.isMediaServerRequest(url) -> SimHttp.Kind.MEDIA
        else -> SimHttp.Kind.OTHER
    }

    /** The first bytes of a file, for the init check (D4): ISO BMFF or WebM, with an encryption box when protected. */
    private fun mediaGet(url: String): ExtractorHttpResult {
        val key = Sim.query(url)["yftsim"]
        if (probesDown || key in unreachable) return ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)
        val file = key?.let(media::get)
            ?: return ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, NOT_FOUND)
        return when (truth(url)) {
            null, "other video", "encrypted" -> {
                val type = file.mime.substringBefore(';').trim()
                val start = if (type.endsWith("webm")) "\u001AE\u00DF\u00A3\u0093B\u0082\u0088webm" else "\u0000\u0000\u0000\u0018ftypdash\u0000\u0000\u0000\u0000iso6"
                val box = if (file.encrypted) (if (type.endsWith("webm")) "ContentEncryption" else "moovtrakencvsinfpssh") else "moovtrak"
                ExtractorHttpResult.Success(statusCode = 206, body = start + box, finalUrl = url, contentType = type)
            }
            else -> ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, FORBIDDEN)
        }
    }

    private fun page(finalUrl: String, change: (String) -> String): ExtractorHttpResult =
        if (!pageReachable) {
            ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)
        } else {
            ExtractorHttpResult.Success(statusCode = OK, body = change(pageHtml()), finalUrl = finalUrl, contentType = "text/html; charset=utf-8")
        }

    private fun json(body: String) = ExtractorHttpResult.Success(
        statusCode = OK,
        body = body,
        finalUrl = YouTubeUrls.innerTubeUrl(null),
        contentType = "application/json; charset=utf-8",
    )

    private fun isBotCheck(body: String): Boolean = "not a bot" in body && "LOGIN_REQUIRED" in body

    companion object {
        const val OK = 200
        const val BAD_REQUEST = 400
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        const val TOO_MANY = 429
        const val DEFAULT_HEIGHT = 360
        const val HLS_MAX_HEIGHT = 1080
        val LIST_KEYS = listOf("formats", "adaptiveFormats")
    }
}

/** The world's HTTP: answers from [SimWorld] and a log of every request by kind (never a value). */
internal class SimHttp(private val world: SimWorld) : ExtractorHttpClient {
    enum class Kind { PLAYER, PAGE, HLS, SOURCE, PROBE, MEDIA, OTHER }

    data class Entry(val kind: Kind, val terminal: Boolean, val credentials: Boolean)

    val log = mutableListOf<Entry>()

    override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
        log += Entry(world.kindOf(url), false, credentials(headers))
        return limited(world.get(url), maxBodyBytes)
    }

    override suspend fun postJson(
        url: String,
        body: String,
        headers: Map<String, String>,
        maxBodyBytes: Long,
    ): ExtractorHttpResult {
        val (result, terminal) = world.post(body)
        log += Entry(Kind.PLAYER, terminal, credentials(headers))
        return limited(result, maxBodyBytes)
    }

    override suspend fun probe(url: String, headers: Map<String, String>, timeoutMillis: Long): ExtractorProbeResult {
        log += Entry(Kind.PROBE, false, credentials(headers))
        return world.probe(url)
    }

    fun count(kind: Kind, from: Int = 0): Int = log.drop(from).count { it.kind == kind }

    /** Requests made after the first bot check or 429 answer (0 when none came). */
    fun afterTerminal(from: Int = 0): Int {
        val entries = log.drop(from)
        val first = entries.indexOfFirst(Entry::terminal)
        return if (first < 0) 0 else entries.size - first - 1
    }

    private fun limited(result: ExtractorHttpResult, maxBodyBytes: Long): ExtractorHttpResult =
        if (result is ExtractorHttpResult.Success && result.body.length > maxBodyBytes) {
            ExtractorHttpResult.Failure(SiteExtractionFailure.RESPONSE_TOO_LARGE)
        } else {
            result
        }

    private fun credentials(headers: Map<String, String>): Boolean = headers.keys.any {
        it.equals("Cookie", ignoreCase = true) || it.equals("Authorization", ignoreCase = true)
    }
}

/**
 * The own solver as the reader sees it, with V: the right values ([FakePlayerScriptRunner]'s
 * transform, which the world's addresses expect), wrong ones (F1) or none.
 */
internal class SimSolver(private val world: SimWorld) : PlayerScriptRunner, SignatureVerdicts {
    override val isAvailable: Boolean = true
    val requests = mutableListOf<PlayerScriptRequest>()
    val verdicts = mutableListOf<SignatureVerdicts.Verdict>()

    override suspend fun resolve(request: PlayerScriptRequest): PlayerScriptResult {
        requests += request
        return when (world.solver) {
            SolverMode.RIGHT -> FakePlayerScriptRunner.transformAll(request)
            SolverMode.WRONG -> PlayerScriptResult.Success(request.challenges.associate { it.key to "W" + it.input.reversed() + "x" })
            SolverMode.UNSOLVED -> PlayerScriptResult.Failed(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED)
        }
    }

    override fun pendingCheck(request: PlayerScriptRequest): PendingCheck =
        PendingCheck(Sim.PLAYER_ID, OwnSolverBreaker.Step.WORKER_P)

    override fun record(check: PendingCheck, verdict: SignatureVerdicts.Verdict) {
        verdicts += verdict
    }
}
