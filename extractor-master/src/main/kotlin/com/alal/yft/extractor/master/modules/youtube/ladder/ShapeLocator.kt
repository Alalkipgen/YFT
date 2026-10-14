package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.master.modules.youtube.YouTubeUrls
import java.net.URI
import java.net.URLDecoder

/**
 * Phase 1.3 D5b — rung **L**, step 1 (MASTER_KEY_PHASE1_3_PLAN.md §3 L): the **value-shape scan**
 * of one parsed answer. What makes a format is read from values; key names are never trusted:
 *
 * - **Format-like object** ([Candidate]): exactly one YouTube media-server address
 *   (`YouTubeUrls.isMediaServerRequest`) with an `itag` parameter — or, without one, exactly one
 *   cipher-like string (form fields `s` and `url`, the `url` such an address); an integer field (a
 *   number or 1–3 digit text, 1–999) equal to that `itag`; exactly one mime-like string
 *   (`video/…` or `audio/…` with `codecs="…"`), or one bare type plus one codec list (rebuilt into
 *   one). An address `mime` parameter must name the same type. Anything else is not a candidate.
 * - **Video object** ([VideoObject]): a direct text equal to the requested video ID and a
 *   positive integer `lengthSeconds` (number or digits).
 * - **Status object** ([StatusObject]): a direct text from the copied parser's status words.
 * - **HLS addresses**: https on `manifest.googlevideo.com` under `/api/manifest/hls_variant/` or
 *   `/api/manifest/hls_playlist/` (§3 H step 1).
 * - **DRM marker**: a `drm…` key with a value, or a `WIDEVINE` / `PLAYREADY` / `FAIRPLAY` /
 *   `DRM_TRACK_TYPE_…` text (the normalizer then stops, as the copied parser does on DRM).
 *
 * Bounded as ShapeFingerprint: an answer of at most [MAX_ANSWER_CHARS] characters (parsed with
 * the copied parser's node bound), containers opened to depth [MAX_DEPTH] and at most [MAX_NODES]
 * visited nodes. More nodes → the scan is incomplete. A container deeper than [MAX_DEPTH] is not
 * opened; the scan is then complete only when the raw answer mentions `googlevideo.com`, the
 * quoted video ID and the quoted status words no more often than the visited part does (so
 * nothing the normalizer judges can hide deeper). An incomplete scan gives no L rows.
 *
 * The scan binds nothing: candidates from every part of the answer are listed (another video's
 * too); binding to the video is ShapeNormalizer's step 2. D2's judge reuses it ([Scan.moved],
 * [Scan.partial], [Scan.hls]). No request, no log; `toString` shows counts and key paths only
 * (a key equal to the video ID or not key-like reads `*`).
 */
internal object ShapeLocator {
    const val MAX_ANSWER_CHARS: Int = 4 * 1024 * 1024
    const val MAX_PARSE_NODES: Int = 400_000
    const val MAX_DEPTH: Int = 16
    const val MAX_NODES: Int = 100_000
    const val MAX_ITAG: Int = 999

    /** Where the copied parser reads formats (ShapeFingerprint's path form). */
    val CANONICAL_LISTS: Set<String> = setOf("streamingData.formats[]", "streamingData.adaptiveFormats[]")

    /** The copied parser's playability words. */
    val STATUS_VALUES: Set<String> = setOf(
        "OK",
        "LOGIN_REQUIRED",
        "UNPLAYABLE",
        "ERROR",
        "AGE_VERIFICATION_REQUIRED",
        "AGE_CHECK_REQUIRED",
        "CONTENT_CHECK_REQUIRED",
        "LIVE_STREAM_OFFLINE",
    )

    private val VIDEO_CODECS = setOf("avc1", "avc3", "vp9", "vp09", "vp8", "vp08", "av01", "hev1", "hvc1", "dvh1", "dvhe")
    private val AUDIO_CODECS = setOf("mp4a", "opus", "vorbis", "ec-3", "ac-3", "flac", "mp3")

    private const val MEDIA_HOST = "googlevideo.com"
    private const val MEDIA_MARK = ".googlevideo.com/videoplayback"
    private const val HLS_PREFIX = "https://manifest.googlevideo.com/"
    private const val HLS_HOST = "manifest.googlevideo.com"
    private val HLS_PATHS = listOf("/api/manifest/hls_variant/", "/api/manifest/hls_playlist/")
    private const val LENGTH_KEY = "lengthSeconds"
    private const val MAX_ADDRESS_CHARS = 8 * 1024
    private const val MAX_MIME_CHARS = 256
    private val DRM_TEXTS = setOf("WIDEVINE", "PLAYREADY", "FAIRPLAY")
    private const val DRM_TRACK_PREFIX = "DRM_TRACK_TYPE_"

    private val FULL_MIME = Regex("^((?:video|audio)/[a-z0-9][a-z0-9.+-]*)\\s*;\\s*codecs=\"([^\"]+)\"\\s*$", RegexOption.IGNORE_CASE)
    private val BARE_TYPE = Regex("^(?:video|audio)/[a-z0-9][a-z0-9.+-]*$", RegexOption.IGNORE_CASE)
    private val CODEC_TOKEN = Regex("^[A-Za-z0-9-]+(?:\\.[A-Za-z0-9.]+)?$")

    /** The answer as a tree, or null when it is too large or not JSON. */
    fun parse(answer: String): JsonValue? =
        if (answer.length > MAX_ANSWER_CHARS) null else BoundedJsonParser.parse(answer, maxNodes = MAX_PARSE_NODES)

    /**
     * Scans [root]. [answer] is the raw text [root] was parsed from; without it a container deeper
     * than [MAX_DEPTH] makes the scan incomplete.
     */
    fun scan(root: JsonValue, videoId: String?, answer: String? = null): Scan {
        val walker = Walker(videoId?.takeIf(String::isNotEmpty))
        walker.walk(root, depth = 0)
        val complete = when {
            walker.capped -> false
            !walker.depthCut -> true
            answer == null -> false
            else -> censusHolds(root, walker.videoId, answer)
        }
        return Scan(
            complete = complete,
            nodes = minOf(walker.nodes, MAX_NODES),
            depthCut = walker.depthCut,
            candidates = walker.candidates,
            rejected = walker.rejected,
            videos = walker.videos,
            statuses = walker.statuses,
            hlsAddresses = walker.hls.toList(),
            drm = walker.drm,
        )
    }

    /** True for a codec of a video track (`avc1.640028`, `vp09.00.51.08`, `av01.0.08M.08`, …). */
    fun isVideoCodec(codec: String): Boolean = familyOf(codec) in VIDEO_CODECS

    /** True for a codec of an audio track (`mp4a.40.2`, `opus`, …). */
    fun isAudioCodec(codec: String): Boolean = familyOf(codec) in AUDIO_CODECS

    /** The codecs of a full mime, as the copied parser splits them. */
    fun codecsOf(mime: String): List<String> =
        mime.substringAfter("codecs=\"", "").substringBefore('"').split(',').map(String::trim).filter(String::isNotEmpty)

    private fun familyOf(codec: String): String = codec.trim().substringBefore('.').lowercase()

    /** One format-like object, unbound. [address] or [cipher] is set, never both. */
    class Candidate internal constructor(
        /** ShapeFingerprint-style key path of the object (`streamingData.adaptiveFormats[]`). */
        val path: String,
        val itag: Int,
        val address: String?,
        val cipher: String?,
        /** A full mime (`video/mp4; codecs="avc1.640028"`), rebuilt when it came split. */
        val mime: String,
        /** The address's `dur` parameter (seconds), when it has one. */
        val durationSeconds: Double?,
        /** Whether the copied parser reads this object as it is (`itag`, `url` or cipher, `mimeType`). */
        val canonicalKeys: Boolean,
        internal val node: JsonValue.Object,
        internal val group: Int,
        internal val family: Int,
        internal val familyNode: JsonValue.Object?,
    ) {
        /** The address or the cipher: what makes two candidates the same format. */
        val source: String get() = address ?: cipher.orEmpty()

        override fun toString(): String =
            "Candidate(path=$path, itag=$itag, ${if (address != null) "address" else "cipher"}, canonicalKeys=$canonicalKeys)"
    }

    class VideoObject internal constructor(val path: String, val lengthSeconds: Long, internal val node: JsonValue.Object) {
        override fun toString(): String = "VideoObject(path=$path, lengthSeconds=$lengthSeconds)"
    }

    class StatusObject internal constructor(val path: String, val value: String, internal val node: JsonValue.Object) {
        override fun toString(): String = "StatusObject(path=$path, value=$value)"
    }

    class Scan internal constructor(
        /** False when a bound was hit and something could hide (no L rows then). */
        val complete: Boolean,
        val nodes: Int,
        val depthCut: Boolean,
        val candidates: List<Candidate>,
        /** Objects with a media address or cipher that are not format-like (no itag match, no single mime, …). */
        val rejected: Int,
        val videos: List<VideoObject>,
        val statuses: List<StatusObject>,
        /** Distinct HLS addresses, in answer order. */
        val hlsAddresses: List<String>,
        val drm: Boolean,
    ) {
        /** The HLS address when exactly one was located (§3 H step 1), else null. */
        val hls: String? get() = hlsAddresses.singleOrNull()

        /** Key paths of the format-like objects (the L shape map's located side). */
        val formatPaths: Set<String> get() = candidates.mapTo(LinkedHashSet(), Candidate::path)

        /** `moved` for D2: format-like objects outside the canonical lists. */
        val moved: Boolean get() = candidates.any { it.path !in CANONICAL_LISTS }

        /** `partial` for D2: format-like objects in the canonical lists that the copied parser cannot read as they are. */
        val partial: Boolean get() = candidates.any { it.path in CANONICAL_LISTS && !it.canonicalKeys }

        override fun toString(): String =
            "ShapeLocator.Scan(complete=$complete, nodes=$nodes, depthCut=$depthCut, candidates=${candidates.size}, " +
                "rejected=$rejected, videos=${videos.size}, statuses=${statuses.size}, hls=${hlsAddresses.size}, drm=$drm)"
    }

    private class Walker(val videoId: String?) {
        var nodes = 0
        var capped = false
        var depthCut = false
        val candidates = ArrayList<Candidate>()
        var rejected = 0
        val videos = ArrayList<VideoObject>()
        val statuses = ArrayList<StatusObject>()
        val hls = LinkedHashSet<String>()
        var drm = false

        private var nextId = 0
        private val ids = IntArray(MAX_DEPTH + 1)
        private val containers = arrayOfNulls<JsonValue>(MAX_DEPTH + 1)

        /** The key that led to each depth; null for an array item. */
        private val keys = arrayOfNulls<String>(MAX_DEPTH + 1)

        fun walk(value: JsonValue, depth: Int) {
            if (capped) return
            nodes += 1
            if (nodes > MAX_NODES) {
                capped = true
                return
            }
            when (value) {
                is JsonValue.Object -> {
                    if (value.entries.isEmpty()) return
                    if (depth >= MAX_DEPTH) {
                        depthCut = true
                        return
                    }
                    ids[depth] = nextId++
                    containers[depth] = value
                    inspect(value, depth)
                    for ((key, child) in value.entries) {
                        keys[depth + 1] = key
                        walk(child, depth + 1)
                        if (capped) return
                    }
                }

                is JsonValue.Array -> {
                    if (value.items.isEmpty()) return
                    if (depth >= MAX_DEPTH) {
                        depthCut = true
                        return
                    }
                    ids[depth] = nextId++
                    containers[depth] = value
                    for (item in value.items) {
                        keys[depth + 1] = null
                        walk(item, depth + 1)
                        if (capped) return
                    }
                }

                is JsonValue.Text -> leaf(value.value)
                else -> Unit
            }
        }

        private fun leaf(text: String) {
            if (text.length in HLS_PREFIX.length..MAX_ADDRESS_CHARS &&
                text.regionMatches(0, HLS_PREFIX, 0, HLS_PREFIX.length, ignoreCase = true) &&
                isHls(text)
            ) {
                hls += text
            }
            if (!drm && text.length in 8..48 &&
                (DRM_TEXTS.any { text.equals(it, ignoreCase = true) } || text.startsWith(DRM_TRACK_PREFIX))
            ) {
                drm = true
            }
        }

        private fun inspect(node: JsonValue.Object, depth: Int) {
            var address: String? = null
            var addresses = 0
            var cipher: String? = null
            var ciphers = 0
            var hasId = false
            var length: Long? = null
            var status: String? = null
            for ((key, child) in node.entries) {
                if (!drm && isDrmKey(key) && hasValue(child)) drm = true
                if (key == LENGTH_KEY) length = positiveLong(child)
                val text = (child as? JsonValue.Text)?.value ?: continue
                when {
                    isMediaAddress(text) -> if (text != address) {
                        address = text
                        addresses += 1
                    }

                    cipherAddress(text) != null -> if (text != cipher) {
                        cipher = text
                        ciphers += 1
                    }
                }
                if (text in STATUS_VALUES && (status == null || status == OK)) status = text
                if (videoId != null && text == videoId) hasId = true
            }
            if (hasId && length != null) videos += VideoObject(path(depth), length, node)
            if (status != null) statuses += StatusObject(path(depth), status, node)
            if (address == null && cipher == null) return
            val candidate = when {
                addresses == 1 && ciphers == 0 -> candidate(node, depth, address, null)
                addresses == 0 && ciphers == 1 -> candidate(node, depth, null, cipher)
                else -> null
            }
            if (candidate != null) candidates += candidate else rejected += 1
        }

        private fun candidate(node: JsonValue.Object, depth: Int, address: String?, cipher: String?): Candidate? {
            val media = address ?: cipher?.let(::cipherAddress) ?: return null
            val itag = itagOf(media) ?: return null
            var itagField = false
            var full: String? = null
            var fulls = 0
            var bare: String? = null
            var bares = 0
            var codecs: String? = null
            var codecLists = 0
            for (child in node.entries.values) {
                when (child) {
                    is JsonValue.Number -> if (child.asLong == itag.toLong()) itagField = true
                    is JsonValue.Text -> {
                        val text = child.value
                        if (smallInt(text) == itag) itagField = true
                        if (text.length > MAX_MIME_CHARS) continue
                        if (text.startsWith("video/", ignoreCase = true) || text.startsWith("audio/", ignoreCase = true)) {
                            if (FULL_MIME.matches(text)) {
                                if (text != full) {
                                    full = text
                                    fulls += 1
                                }
                            } else if (BARE_TYPE.matches(text)) {
                                if (text != bare) {
                                    bare = text
                                    bares += 1
                                }
                            }
                        } else if (isCodecList(text) && text != codecs) {
                            codecs = text
                            codecLists += 1
                        }
                    }

                    else -> Unit
                }
            }
            if (!itagField) return null
            val mime = when {
                fulls == 1 -> full
                fulls == 0 && bares == 1 && codecLists == 1 -> "${bare!!.trim()}; codecs=\"${codecs!!.trim()}\""
                else -> null
            } ?: return null
            val type = mime.substringBefore(';').trim().lowercase()
            val named = YouTubeUrls.queryParamOf(media, "mime")?.let(::decode)?.trim()?.lowercase()
            if (named != null && named != type) return null
            val duration = YouTubeUrls.queryParamOf(media, "dur")?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
            val entries = node.entries
            val canonical = entries["itag"].asLongOrNull == itag.toLong() &&
                (if (address != null) (entries["url"] as? JsonValue.Text)?.value == address else
                    (entries["signatureCipher"] as? JsonValue.Text)?.value == cipher ||
                        (entries["cipher"] as? JsonValue.Text)?.value == cipher) &&
                fulls == 1 && (entries["mimeType"] as? JsonValue.Text)?.value == mime
            return Candidate(
                path = path(depth),
                itag = itag,
                address = address,
                cipher = cipher,
                mime = mime,
                durationSeconds = duration,
                canonicalKeys = canonical,
                node = node,
                group = if (depth >= 1) ids[depth - 1] else -1,
                family = if (depth >= 2) ids[depth - 2] else -1,
                familyNode = if (depth >= 2) containers[depth - 2] as? JsonValue.Object else null,
            )
        }

        private fun path(depth: Int): String {
            val out = StringBuilder()
            for (level in 1..depth) {
                val key = keys[level]
                if (key == null) {
                    out.append("[]")
                } else {
                    if (out.isNotEmpty()) out.append('.')
                    out.append(if (isKeyLike(key) && key != videoId) key else "*")
                }
            }
            return out.toString()
        }
    }

    /** The deep-cut check: every mention in the raw answer was visited. */
    private fun censusHolds(root: JsonValue, videoId: String?, answer: String): Boolean {
        val seen = Census(videoId).also { it.walk(root, 0) }
        if (count(answer, MEDIA_HOST, ignoreCase = true) > seen.hosts) return false
        if (videoId != null && count(answer, "\"$videoId\"", ignoreCase = false) > seen.ids) return false
        return STATUS_VALUES.sumOf { count(answer, "\"$it\"", ignoreCase = false) } <= seen.statuses
    }

    private class Census(private val videoId: String?) {
        var hosts = 0
        var ids = 0
        var statuses = 0

        fun walk(value: JsonValue, depth: Int) {
            when (value) {
                is JsonValue.Object -> {
                    if (value.entries.isEmpty() || depth >= MAX_DEPTH) return
                    for ((key, child) in value.entries) {
                        mention(key)
                        walk(child, depth + 1)
                    }
                }

                is JsonValue.Array -> {
                    if (value.items.isEmpty() || depth >= MAX_DEPTH) return
                    for (item in value.items) walk(item, depth + 1)
                }

                is JsonValue.Text -> mention(value.value)
                else -> Unit
            }
        }

        private fun mention(text: String) {
            hosts += count(text, MEDIA_HOST, ignoreCase = true)
            if (videoId != null && text == videoId) ids += 1
            if (text in STATUS_VALUES) statuses += 1
        }
    }

    private fun count(text: String, token: String, ignoreCase: Boolean): Int {
        var found = 0
        var from = 0
        while (true) {
            val at = text.indexOf(token, from, ignoreCase)
            if (at < 0) return found
            found += 1
            from = at + token.length
        }
    }

    private fun isMediaAddress(text: String): Boolean =
        text.length in MEDIA_MARK.length..MAX_ADDRESS_CHARS &&
            text.regionMatches(0, "https://", 0, 8, ignoreCase = true) &&
            text.contains(MEDIA_MARK, ignoreCase = true) &&
            YouTubeUrls.isMediaServerRequest(text) &&
            itagOf(text) != null

    /** The media address inside a cipher-like string, or null. */
    private fun cipherAddress(text: String): String? {
        if (text.length !in 16..MAX_ADDRESS_CHARS || text.startsWith("http", ignoreCase = true)) return null
        if (!text.contains("url=") || !text.contains(MEDIA_HOST, ignoreCase = true)) return null
        val fields = formFields(text)
        val url = fields["url"]?.takeIf(::isMediaAddress) ?: return null
        return if (fields["s"].isNullOrBlank()) null else url
    }

    /** The copied extractor's form-field reading: first value of each name, URL-decoded. */
    private fun formFields(text: String): Map<String, String> = buildMap {
        text.split('&').forEach { pair ->
            val separator = pair.indexOf('=')
            if (separator > 0) {
                val name = pair.substring(0, separator)
                val value = decode(pair.substring(separator + 1))
                if (value != null && name !in this) put(name, value)
            }
        }
    }

    private fun decode(value: String): String? = runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrNull()

    private fun itagOf(address: String): Int? = YouTubeUrls.queryParamOf(address, "itag")?.let(::smallInt)

    /** 1–3 digits, 1–[MAX_ITAG]. */
    private fun smallInt(text: String): Int? {
        if (text.length !in 1..3 || !text.all { it in '0'..'9' }) return null
        return text.toInt().takeIf { it in 1..MAX_ITAG }
    }

    private fun positiveLong(value: JsonValue?): Long? = when (value) {
        is JsonValue.Number -> value.asLong
        is JsonValue.Text -> value.value.takeIf { it.length in 1..12 && it.all { c -> c in '0'..'9' } }?.toLongOrNull()
        else -> null
    }?.takeIf { it > 0 }

    private fun isCodecList(text: String): Boolean {
        if (text.isEmpty() || text.length > MAX_MIME_CHARS || !text[0].isLetter()) return false
        val tokens = text.split(',').map(String::trim)
        return tokens.all { it.isNotEmpty() && CODEC_TOKEN.matches(it) && (isVideoCodec(it) || isAudioCodec(it)) }
    }

    private fun isHls(text: String): Boolean {
        val address = runCatching { URI(text) }.getOrNull() ?: return false
        return address.scheme.equals("https", ignoreCase = true) && address.userInfo == null &&
            address.host.equals(HLS_HOST, ignoreCase = true) && HLS_PATHS.any { address.rawPath.orEmpty().startsWith(it) }
    }

    private fun isDrmKey(key: String): Boolean =
        key.startsWith("drm", ignoreCase = true) || key.contains("Drm") || key.contains("DRM")

    private fun hasValue(value: JsonValue): Boolean = when (value) {
        is JsonValue.Text -> value.value.isNotBlank()
        is JsonValue.Array -> value.items.isNotEmpty()
        is JsonValue.Object -> value.entries.isNotEmpty()
        is JsonValue.Bool -> value.value
        is JsonValue.Number -> value.asDouble?.let { it != 0.0 } ?: true
        JsonValue.Null -> false
    }

    /** ShapeFingerprint's key rule. */
    private fun isKeyLike(key: String): Boolean =
        key.length in 1..64 && (key[0].isAsciiLetter() || key[0] == '_') &&
            key.all { it.isAsciiLetter() || it in '0'..'9' || it == '_' }

    private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

    private const val OK = "OK"
}
