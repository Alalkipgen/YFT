package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.api.json.path
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import java.net.URLEncoder

/**
 * Synthetic YouTube answers, pages, playlists and version sources for the D0 census tests. Every
 * address, key and value here is a fixture; stream hosts sit on `googlevideo.com` so V and H
 * accept them, and nothing reaches the network.
 */
internal object Census {
    const val HOST = "rr1---sn-census.googlevideo.com"
    const val HLS_HOST = "manifest.googlevideo.com"
    const val UA = "Mozilla/5.0 (Linux; Android 14; Census) AppleWebKit/537.36 Mobile Safari/537.36"
    const val VISITOR = "CgtDZW5zdXNWaXNpdG9yKMCAgIAG"
    const val API_KEY = "census-fixture-api-key-0123"
    const val PLAYER_ID = "abcd1234"
    const val STS = 20726
    const val PAGE_VERSION = "2.20261002.01.00"
    const val SIG_INPUT = "AOq0QJ8wRAIgCensusSignature0123456789"
    const val N_INPUT = "CensusNinput7"
    const val LENGTH = 212L

    const val V1 = "jNQXAC9IVRw"
    const val V2 = "dQw4w9WgXcQ"
    const val V5 = "Census0005x"

    const val MASTER = "https://$HLS_HOST/api/manifest/hls_variant/id/census/file/index.m3u8"
    const val VARIANT = "https://$HLS_HOST/api/manifest/hls_playlist/id/census/itag/96/index.m3u8"
    const val SEGMENT = "https://$HOST/videoplayback/id/census/itag/96/sq/0/seg.ts"

    const val PLAYER_URL = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"

    fun pageUrl(id: String) = "https://www.youtube.com/watch?v=$id&bpctr=9999999999&has_verified=1"

    fun media(itag: Int, n: String? = null): String =
        "https://$HOST/videoplayback?expire=4102444800&itag=$itag&source=youtube" + (n?.let { "&n=$it" } ?: "")

    fun cipher(itag: Int, n: String? = N_INPUT): String =
        "s=${enc(SIG_INPUT)}&sp=sig&url=${enc(media(itag, n))}"

    private fun enc(text: String) = URLEncoder.encode(text, "UTF-8")

    fun obj(vararg pairs: Pair<String, Any?>): String = pairs.filter { it.second != null }
        .joinToString(",", "{", "}") { (key, value) -> "\"$key\":" + json(value) }

    private fun json(value: Any?): String = when (value) {
        null -> "null"
        is Number, is Boolean -> value.toString()
        is Raw -> value.text
        else -> "\"" + value.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }

    class Raw(val text: String)

    fun video(itag: Int, height: Int, codec: String = "avc1.4d401f", url: String? = media(itag), cipher: String? = null, size: Long? = 1_000_000) =
        obj(
            "itag" to itag, "url" to url, "signatureCipher" to cipher,
            "mimeType" to "video/mp4; codecs=\"$codec\"", "width" to height * 16 / 9, "height" to height,
            "contentLength" to size?.toString(), "qualityLabel" to "${height}p",
            // D7: the length binding L needs (real answers carry it on every format).
            "approxDurationMs" to (LENGTH * 1000).toString(),
        )

    fun audio(itag: Int = 140, codec: String = "mp4a.40.2", url: String? = media(itag), cipher: String? = null) =
        obj(
            "itag" to itag, "url" to url, "signatureCipher" to cipher,
            "mimeType" to "audio/mp4; codecs=\"$codec\"", "contentLength" to "300000",
            "audioQuality" to "AUDIO_QUALITY_MEDIUM", "audioSampleRate" to "44100",
            // D7: the length binding L needs (real answers carry it on every format).
            "approxDurationMs" to (LENGTH * 1000).toString(),
        )

    fun progressive(itag: Int = 18, url: String? = media(itag), cipher: String? = null) =
        obj(
            "itag" to itag, "url" to url, "signatureCipher" to cipher,
            "mimeType" to "video/mp4; codecs=\"avc1.42001E, mp4a.40.2\"", "width" to 640, "height" to 360,
            "contentLength" to "2000000", "qualityLabel" to "360p", "audioQuality" to "AUDIO_QUALITY_LOW",
            "audioSampleRate" to "44100",
            // D7: the length binding L needs (real answers carry it on every format).
            "approxDurationMs" to (LENGTH * 1000).toString(),
        )

    fun answer(
        id: String,
        formats: List<String> = emptyList(),
        adaptive: List<String> = emptyList(),
        status: String = "OK",
        reason: String? = null,
        hls: String? = null,
        cver: String? = null,
        sabr: Boolean = false,
    ): String {
        val context = obj(
            "visitorData" to VISITOR,
            "serviceTrackingParams" to cver?.let {
                Raw("[" + obj("service" to "CSI", "params" to Raw("[" + obj("key" to "cver", "value" to it) + "]")) + "]")
            },
        )
        val streaming = if (status == "OK") {
            obj(
                "expiresInSeconds" to "21540",
                "formats" to formats.takeIf { it.isNotEmpty() }?.let { Raw(it.joinToString(",", "[", "]")) },
                "adaptiveFormats" to adaptive.takeIf { it.isNotEmpty() }?.let { Raw(it.joinToString(",", "[", "]")) },
                "hlsManifestUrl" to hls,
                "serverAbrStreamingUrl" to if (sabr) "https://$HOST/videoplayback?sabr=1" else null,
            )
        } else {
            null
        }
        return obj(
            "responseContext" to Raw(context),
            "playabilityStatus" to Raw(obj("status" to status, "reason" to reason)),
            "streamingData" to streaming?.let(::Raw),
            "videoDetails" to Raw(
                obj(
                    "videoId" to id, "title" to "Census video", "author" to "Census", "lengthSeconds" to LENGTH.toString(),
                    "thumbnail" to Raw("{\"thumbnails\":[{\"url\":\"https://i.ytimg.com/vi/census/hq.jpg\",\"width\":480}]}"),
                ),
            ),
        )
    }

    fun botCheck(id: String) = answer(id, status = "LOGIN_REQUIRED", reason = "Sign in to confirm you\u2019re not a bot")

    /** visionOS as it answers today: direct adaptive rows (AVC 720/1080, VP9, audio) and HLS. */
    fun visionOs(id: String, cver: String = "1.02") = answer(
        id,
        adaptive = listOf(video(137, 1080, "avc1.640028"), video(136, 720), video(248, 1080, "vp09.00.40.08"), audio()),
        hls = MASTER,
        cver = cver,
    )

    /** A music video's visionOS answer: every row behind a signature. */
    fun visionOsCiphered(id: String) = answer(
        id,
        adaptive = listOf(video(136, 720, url = null, cipher = cipher(136)), audio(url = null, cipher = cipher(140))),
        cver = "1.02",
    )

    fun android(id: String) = answer(
        id,
        formats = listOf(progressive()),
        adaptive = listOf(video(137, 1080, "avc1.640028"), audio()),
        cver = "21.26.364",
    )

    fun embedded(id: String) = answer(
        id,
        formats = listOf(progressive(url = null, cipher = cipher(18))),
        adaptive = listOf(video(136, 720, url = null, cipher = cipher(136)), audio(url = null, cipher = cipher(140))),
    )

    fun mobileWeb(id: String) = answer(
        id,
        adaptive = listOf(video(136, 720, url = media(136, N_INPUT)), audio(url = media(140, N_INPUT))),
    )

    fun page(clientName: String = "MWEB", version: String = PAGE_VERSION): String =
        "<html><head><script>ytcfg.set({\"INNERTUBE_API_KEY\":\"$API_KEY\",\"INNERTUBE_CLIENT_NAME\":\"$clientName\"," +
            "\"INNERTUBE_CLIENT_VERSION\":\"$version\",\"VISITOR_DATA\":\"$VISITOR\",\"STS\":$STS," +
            "\"PLAYER_JS_URL\":\"\\/s\\/player\\/$PLAYER_ID\\/player-plasma-es6-en_US.vflset\\/base.js\"});" +
            "</script></head><body></body></html>"

    fun masterPlaylist(): String = """
        #EXTM3U
        #EXT-X-INDEPENDENT-SEGMENTS
        #EXT-X-STREAM-INF:BANDWIDTH=1000000,CODECS="avc1.4d401f,mp4a.40.2",RESOLUTION=1280x720,FRAME-RATE=30
        https://$HLS_HOST/api/manifest/hls_playlist/id/census/itag/95/index.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=3000000,CODECS="avc1.640028,mp4a.40.2",RESOLUTION=1920x1080,FRAME-RATE=30
        $VARIANT
    """.trimIndent()

    fun variantPlaylist(seconds: Long = LENGTH, key: String? = null): String = buildString {
        append("#EXTM3U\n#EXT-X-TARGETDURATION:5\n#EXT-X-PLAYLIST-TYPE:VOD\n")
        key?.let { append(it).append('\n') }
        var left = seconds.toDouble()
        var index = 0
        while (left > 0) {
            val part = minOf(5.0, left)
            append("#EXTINF:").append(part).append(",\n")
            append(if (index == 0) SEGMENT else "https://$HOST/videoplayback/id/census/itag/96/sq/$index/seg.ts").append('\n')
            left -= part
            index += 1
        }
        append("#EXT-X-ENDLIST\n")
    }

    const val YT_DLP_TEXT = "INNERTUBE_CLIENTS = {\n" +
        "    'android': {\n        'INNERTUBE_CONTEXT': {\n            'client': {\n" +
        "                'clientName': 'ANDROID',\n                'clientVersion': '21.26.364',\n" +
        "            },\n        },\n    },\n" +
        "    'visionos': {\n        'INNERTUBE_CONTEXT': {\n            'client': {\n" +
        "                'clientName': 'VISIONOS',\n                'clientVersion': '__VISION__',\n" +
        "            },\n        },\n    },\n}\n"

    fun ytDlp(vision: String = "1.02") = YT_DLP_TEXT.replace("__VISION__", vision)

    const val YOUTUBE_JS_TEXT = "export const CLIENTS = {\n  VISIONOS: {\n    NAME: 'VISIONOS',\n    VERSION: '1.02'\n  },\n" +
        "  ANDROID: {\n    NAME: 'ANDROID',\n    VERSION: '21.03.36',\n    SDK_VERSION: 33\n  }\n};\n"

    const val APP_STORE_TEXT = "{\"resultCount\":1,\"results\":[{\"version\":\"1.02.10\"," +
        "\"supportedDevices\":[\"AppleVisionPro-AppleVisionPro\"]}]}"
}

/** One scripted YouTube: answers per (video, client name), pages, playlists, sources and V. */
internal class CensusWorld {
    /** (video ID, client name) → the answer's responder (it sees the posted body). */
    val answers = HashMap<Pair<String, String>, (String) -> ExtractorHttpResult>()
    val gets = HashMap<String, ExtractorHttpResult>()
    var probe: (String) -> ExtractorProbeResult = { ExtractorProbeResult.Answered(206, 1_000_000) }
    var onPost: (String) -> Unit = {}

    fun answer(id: String, client: String, json: String) {
        answers[id to client] = { _ -> ExtractorHttpResult.Success(200, json, Census.PLAYER_URL, "application/json") }
    }

    fun get(url: String, body: String) {
        gets[url] = ExtractorHttpResult.Success(200, body, url, "text/plain")
    }

    /**
     * visionOS refuses [id] with a bot check unless the request carries the page's visitor data
     * ([always]: every time), as it refused the census's first run (2026-10-14).
     */
    fun visionOsBotCheck(id: String, always: Boolean = false): CensusWorld = apply {
        val check = Census.botCheck(id)
        val answer = Census.visionOs(id)
        answers[id to "VISIONOS"] = { body ->
            val json = if (always || Census.VISITOR !in body) check else answer
            ExtractorHttpResult.Success(200, json, Census.PLAYER_URL, "application/json")
        }
    }

    /** Every client answers [id] as it does today, with a page, an HLS playlist and V passing. */
    fun standard(id: String, page: Boolean = true): CensusWorld = apply {
        answer(id, "VISIONOS", Census.visionOs(id))
        answer(id, "ANDROID", Census.android(id))
        answer(id, "WEB_EMBEDDED_PLAYER", Census.embedded(id))
        answer(id, "MWEB", Census.mobileWeb(id))
        if (page) get(Census.pageUrl(id), Census.page())
        get(Census.MASTER, Census.masterPlaylist())
        get(Census.VARIANT, Census.variantPlaylist())
    }

    fun sources(vision: String = "1.02", store: String = Census.APP_STORE_TEXT): CensusWorld = apply {
        get(CensusSources.YT_DLP_URL, Census.ytDlp(vision))
        get(CensusSources.YOUTUBE_JS_URL, Census.YOUTUBE_JS_TEXT)
        get(CensusSources.APP_STORE_URL, store)
        gets[CensusSources.PLAY_STORE_URL] = ExtractorHttpResult.Failure(SiteExtractionFailure.RESPONSE_TOO_LARGE)
    }

    fun http(): FakeExtractorHttpClient = FakeExtractorHttpClient(
        postResponder = { _, body ->
            onPost(body)
            val root = BoundedJsonParser.parse(body, maxNodes = 1_000)
            val client = root.path("context", "client")["clientName"].asStringOrNull.orEmpty()
            val id = root["videoId"].asStringOrNull.orEmpty()
            answers[id to client]?.invoke(body)
        },
        getResponder = { url, _ -> gets[url] },
        probeResponder = { url, _ -> probe(url) },
    )

    companion object {
        fun clientOf(body: String): String =
            BoundedJsonParser.parse(body, maxNodes = 1_000).path("context", "client")["clientName"].asStringOrNull.orEmpty()

        fun versionOf(body: String): String =
            BoundedJsonParser.parse(body, maxNodes = 1_000).path("context", "client")["clientVersion"].asStringOrNull.orEmpty()
    }
}
