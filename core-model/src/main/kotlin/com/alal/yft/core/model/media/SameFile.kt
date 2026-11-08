package com.alal.yft.core.model.media

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import kotlin.math.abs

/** Same-file check: how a fresh address is the file whose link failed; the strongest first. */
enum class FileMatch {
    /** The same address. */
    SAME_ADDRESS,

    /**
     * The same host and path, and the same value for every query name; only values that sign,
     * date or rate-limit a link, or bust a cache, differ.
     */
    RESIGNED,

    /**
     * The same path, but a query value whose name the check does not know differs, or only the
     * number of the host's edge server does. Not proof alone ([SameFile.videoIn]).
     */
    SAME_PATH,

    /** Another file: another host or path, or a query value that names the content differs. */
    DIFFERENT,
}

/**
 * Same-file check: what names a file in its address, without its signature: the address
 * without its query or fragment ([path], [FreshLinks.unsigned]) and its query values that do not
 * sign, date or rate-limit the link, each hashed ([query]), so a kept key holds no token. [query]
 * is null when it is unknown (a download's record written before keys were kept).
 */
data class FileKey(val path: String, val query: String?) {
    override fun toString(): String = "FileKey(queryKnown=${query != null})"
}

/** Same-file check: which of a page's videos is the one whose link failed. */
sealed interface SameVideo {
    /** The [videos] that hold the file, the strongest [match] first. */
    data class Found(val videos: List<MediaGroup>, val match: FileMatch) : SameVideo {
        val video: MediaGroup get() = videos.first()
    }

    /** A video shares the file's path, but nothing proves that it is the same video. */
    data object Unconfirmed : SameVideo

    /** No video holds the file. */
    data object Missing : SameVideo
}

/**
 * Same-file check: whether a fresh link is the file whose link failed, from addresses alone. A
 * length never proves it; a known length that differs disproves it. Query values a link's
 * signature, expiry or cache use may change; any other value must stay, and one that names the
 * content (`id`, `video`, `file`, `quality`, …) tells two files apart. Pure, so the rules are
 * unit-tested; nothing here returns a query value.
 */
object SameFile {
    /** Two stated lengths of one file (Page video finder's rule). */
    const val LENGTH_SLACK_MILLIS = 2_000L

    /** Query names that only sign, date or rate-limit a link, or bust a cache. */
    private val SIGNING_NAMES = setOf(
        "validfrom", "valid_from", "validto", "valid_to", "expires", "expire", "expiry",
        "expiration", "exp", "e", "x-expires", "deadline", "ttl", "t", "ts", "time", "timestamp",
        "token", "tkn", "access_token", "auth", "auth_key", "authkey", "authtoken", "hash", "sig",
        "signature", "sign", "signed", "md5", "hmac", "policy", "key-pair-id", "hdnts", "hdnea",
        "__gda__", "acl", "nonce", "secure", "wmsauthsign", "oh", "oe", "efg", "ccb", "lsig",
        "lsparams", "sparams", "ip", "ipbits", "rnd", "random", "cb", "cachebuster", "nocache",
        "_", "rate", "burst", "sv", "se", "sp", "spr", "srt", "st", "verify",
    )

    /** Name prefixes of signing values (cloud storage signatures, cache and campaign tags). */
    private val SIGNING_PREFIXES = listOf("x-amz-", "x-goog-", "x-oss-", "_nc_", "utm_")

    /** Query names whose value names the content: one that differs is another file. */
    private val CONTENT_NAMES = setOf(
        "id", "vid", "video", "video_id", "videoid", "v", "file", "filename", "f", "fid", "name",
        "src", "source", "path", "url", "media", "mediaid", "media_id", "mid", "item", "itemid",
        "item_id", "content", "contentid", "content_id", "cid", "clip", "clipid", "clip_id",
        "movie", "episode", "ep", "track", "quality", "q", "res", "resolution", "height", "itag",
        "format", "fmt", "type", "mime", "lang", "language", "bitrate", "br", "label",
        "rendition", "variant", "stream", "streamid", "playlist", "list", "index", "part", "seg",
        "segment", "chunk", "start", "end", "range", "bytestart", "byteend",
    )

    private const val HASH_HEX = 16

    /** [url]'s key, to keep without its signature. */
    fun key(url: String): FileKey = FileKey(FreshLinks.unsigned(url), queryKey(url))

    /** How [fresh] is the file at [old]. */
    fun match(old: String, fresh: String): FileMatch =
        if (old.substringBefore('#') == fresh.substringBefore('#')) {
            FileMatch.SAME_ADDRESS
        } else {
            match(key(old), fresh)
        }

    /** How [fresh] is the file [key] names; at best [FileMatch.RESIGNED]. */
    fun match(key: FileKey, fresh: String): FileMatch {
        val path = FreshLinks.unsigned(fresh)
        val samePath = path == key.path
        if (!samePath && !sameButEdge(key.path, path)) return FileMatch.DIFFERENT
        val known = key.query ?: return FileMatch.SAME_PATH
        val changed = changedNames(values(known), values(queryKey(fresh)))
        return when {
            changed.any { it in CONTENT_NAMES } -> FileMatch.DIFFERENT
            changed.isNotEmpty() || !samePath -> FileMatch.SAME_PATH
            else -> FileMatch.RESIGNED
        }
    }

    /**
     * The same video in [videos] as the file whose link failed, [matchOf] telling how each address
     * is that file, and [lengthMillis] its known length. A video of a known length that differs
     * is not it unless it has the same address. Any proven video ([FileMatch.RESIGNED] or better)
     * is it; a video that only shares the path is it when it is the only one and has the same
     * known length; otherwise none is ([SameVideo.Unconfirmed]).
     */
    fun videoIn(
        videos: List<MediaGroup>,
        lengthMillis: Long?,
        matchOf: (String) -> FileMatch,
    ): SameVideo {
        val matched = videos.mapNotNull { video ->
            val best = video.candidates.minOf { matchOf(it.mediaUrl) }
            when {
                best == FileMatch.DIFFERENT -> null
                best != FileMatch.SAME_ADDRESS && lengthsDiffer(lengthMillis, video.durationMillis)
                -> null
                else -> video to best
            }
        }
        if (matched.isEmpty()) return SameVideo.Missing
        val proven = matched.filter { (_, match) -> match <= FileMatch.RESIGNED }
            .sortedBy { (_, match) -> match }
        if (proven.isNotEmpty()) {
            return SameVideo.Found(proven.map { it.first }, proven.first().second)
        }
        val only = matched.singleOrNull()?.first ?: return SameVideo.Unconfirmed
        val sameLength = lengthMillis != null && only.durationMillis?.let { length ->
            abs(length - lengthMillis) <= LENGTH_SLACK_MILLIS
        } == true
        return if (sameLength) SameVideo.Found(listOf(only), FileMatch.SAME_PATH) else
            SameVideo.Unconfirmed
    }

    /** [target]'s video in [videos]: any of its files, with its known length. */
    fun videoIn(target: MediaGroup, videos: List<MediaGroup>): SameVideo {
        val files = target.candidates.map { it.mediaUrl }
        return videoIn(videos, target.durationMillis) { fresh ->
            files.minOf { old -> match(old, fresh) }
        }
    }

    private fun lengthsDiffer(first: Long?, second: Long?): Boolean =
        first != null && second != null && abs(first - second) > LENGTH_SLACK_MILLIS

    /**
     * The same scheme, port and path on a host that differs only in its first label's numbers
     * (`cdn3.example.test`, `cdn7.example.test`): another edge server of one service.
     */
    private fun sameButEdge(first: String, second: String): Boolean {
        val a = runCatching { URI(first) }.getOrNull() ?: return false
        val b = runCatching { URI(second) }.getOrNull() ?: return false
        if (a.scheme != b.scheme || a.port != b.port || a.rawPath != b.rawPath) return false
        val hostA = a.host?.lowercase()?.split('.') ?: return false
        val hostB = b.host?.lowercase()?.split('.') ?: return false
        if (hostA.size < MIN_EDGE_LABELS || hostA.size != hostB.size) return false
        if (hostA.drop(1) != hostB.drop(1) || hostA[0] == hostB[0]) return false
        return hostA[0].replace(DIGITS, "#") == hostB[0].replace(DIGITS, "#")
    }

    private const val MIN_EDGE_LABELS = 3
    private val DIGITS = Regex("[0-9]+")

    /** [url]'s query values that name the file, hashed: `name=hash&…`, sorted; empty for none. */
    private fun queryKey(url: String): String {
        val query = url.substringBefore('#').substringAfter('?', "")
        return query.split('&').mapNotNull { parameter ->
            if (parameter.isEmpty()) return@mapNotNull null
            val name = decoded(parameter.substringBefore('=')).lowercase()
            if (name.isEmpty() || isSigning(name)) return@mapNotNull null
            val value = decoded(parameter.substringAfter('=', ""))
            "${URLEncoder.encode(name, UTF_8)}=${hash(value)}"
        }.sorted().joinToString("&")
    }

    private fun values(key: String): Map<String, List<String>> =
        key.split('&').filter(String::isNotEmpty).groupBy(
            keySelector = { decoded(it.substringBefore('=')) },
            valueTransform = { it.substringAfter('=', "") },
        )

    private fun changedNames(
        old: Map<String, List<String>>,
        fresh: Map<String, List<String>>,
    ): Set<String> = (old.keys + fresh.keys).filterTo(mutableSetOf()) { name ->
        old[name].orEmpty().sorted() != fresh[name].orEmpty().sorted()
    }

    private fun isSigning(name: String): Boolean =
        name in SIGNING_NAMES || SIGNING_PREFIXES.any(name::startsWith)

    private fun decoded(text: String): String =
        runCatching { URLDecoder.decode(text, UTF_8) }.getOrDefault(text)

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
            .take(HASH_HEX)

    private const val UTF_8 = "UTF-8"
}
