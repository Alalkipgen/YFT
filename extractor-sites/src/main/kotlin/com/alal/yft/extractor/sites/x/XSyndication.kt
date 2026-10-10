package com.alal.yft.extractor.sites.x

import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonReadResult
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.api.json.path
import java.net.URI
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max

/** One MP4 file of a video, as X's embed answer lists it: the file has its sound. */
internal data class XVariant(
    val url: String,
    val bitrateBitsPerSecond: Long?,
    val width: Int?,
    val height: Int?,
)

internal enum class XMediaType { VIDEO, GIF }

/**
 * One video of a post: its MP4 files, highest bitrate first, and its HLS playlist. [position] is
 * its 1-based place among the post's media, the number a `/video/N` link names.
 */
internal data class XVideo(
    val position: Int,
    val type: XMediaType,
    val variants: List<XVariant>,
    val playlistUrl: String?,
    val durationMillis: Long?,
    val thumbnailUrl: String?,
    val width: Int?,
    val height: Int?,
)

/** A post's videos; [quoted] when they are the quoted post's, the post itself having none. */
internal data class XPost(
    val id: String?,
    val text: String?,
    val userName: String?,
    val screenName: String?,
    val videos: List<XVideo>,
    val photoCount: Int,
    val quoted: Boolean,
)

internal sealed interface XParseResult {
    data class Success(val post: XPost) : XParseResult

    /** [detail] is the Details line, [unavailable] when X said the post cannot be shown. */
    data class Failure(
        val detail: String,
        val unavailable: Boolean = false,
        val photos: Boolean = false,
    ) : XParseResult
}

/**
 * P48: X's embed answer for one post (`cdn.syndication.twimg.com/tweet-result`), the public
 * JSON its embedded-post widget reads. It needs no session; its [token] is the number the
 * widget's own code computes from the post ID, so the request is the widget's.
 */
internal object XSyndication {
    const val HOST = "cdn.syndication.twimg.com"

    fun url(postId: String): String =
        "https://$HOST/tweet-result?id=$postId&token=${token(postId)}&lang=en"

    /**
     * The embed widget's token: `((Number(id) / 1e15) * Math.PI).toString(36)` without its zeros
     * and point. [radixString] is JavaScript's own number-to-text in base 36, so the token is the
     * same text the widget sends.
     */
    fun token(postId: String): String {
        val value = (postId.toDouble() / 1e15) * Math.PI
        return radixString(value, 36).replace(ZEROS_OR_POINT, "")
    }

    /**
     * JavaScript's `Number.prototype.toString(radix)` for a finite double: the shortest digits
     * that read back as [value], rounded half to even, as V8's `DoubleToRadixCString` writes them.
     */
    fun radixString(value: Double, radix: Int): String {
        require(value.isFinite()) { "Only finite numbers have digits" }
        require(radix in 2..36)
        val negative = value < 0
        val magnitude = if (negative) -value else value
        var integer = floor(magnitude)
        var fraction = magnitude - integer
        // Fraction digits only as far as the double itself is precise.
        var delta = max(Double.MIN_VALUE, 0.5 * (Math.nextUp(magnitude) - magnitude))
        val fractionDigits = StringBuilder()
        if (fraction >= delta) {
            do {
                fraction *= radix
                delta *= radix
                val digit = fraction.toInt()
                fractionDigits.append(DIGITS[digit])
                fraction -= digit
                val roundsUp = fraction > HALF || (fraction == HALF && digit and 1 == 1)
                if (roundsUp && fraction + delta > 1) {
                    integer = carry(fractionDigits, integer, radix)
                    break
                }
            } while (fraction >= delta)
        }
        val integerDigits = StringBuilder()
        // Digits below the double's precision are written as zeros.
        while (Math.getExponent(integer / radix) - MANTISSA_BITS > 0) {
            integer /= radix
            integerDigits.append('0')
        }
        do {
            val remainder = integer % radix
            integerDigits.append(DIGITS[remainder.toInt()])
            integer = (integer - remainder) / radix
        } while (integer > 0)
        return buildString {
            if (negative) append('-')
            append(integerDigits.reverse())
            if (fractionDigits.isNotEmpty()) append('.').append(fractionDigits)
        }
    }

    /** Rounds the written fraction up by one last digit; returns the integer part after it. */
    private fun carry(digits: StringBuilder, integer: Double, radix: Int): Double {
        while (digits.isNotEmpty()) {
            val last = DIGITS.indexOf(digits.last())
            digits.setLength(digits.length - 1)
            if (last + 1 < radix) {
                digits.append(DIGITS[last + 1])
                return integer
            }
        }
        return integer + 1
    }

    fun parse(body: String): XParseResult {
        val root = when (val read = BoundedJsonParser.read(body)) {
            is JsonReadResult.Failed -> {
                val problem = read.problem.label
                return XParseResult.Failure("answer: $problem at char ${read.position}")
            }
            is JsonReadResult.Read -> read.value
        }
        if (root !is JsonValue.Object) return XParseResult.Failure("answer: not an object")
        // X answers an empty object for a post it does not show without a session.
        if (root.entries.isEmpty()) return XParseResult.Failure("answer: empty", unavailable = true)
        val typeName = root["__typename"].asStringOrNull
        if (typeName == TOMBSTONE || root["tombstone"] != null) {
            return XParseResult.Failure("answer: X shows no post (tombstone)", unavailable = true)
        }
        val own = videosOf(root)
        val quoted = root["quoted_tweet"]
        val quotedVideos = if (own.isEmpty()) videosOf(quoted) else emptyList()
        val user = root["user"]
        val post = XPost(
            id = root["id_str"].asStringOrNull,
            text = root["text"].asStringOrNull,
            userName = user["name"].asStringOrNull,
            screenName = user["screen_name"].asStringOrNull,
            videos = own.ifEmpty { quotedVideos },
            photoCount = max(
                root["mediaDetails"].asArrayOrEmpty.count { it["type"].asStringOrNull == PHOTO },
                root["photos"].asArrayOrEmpty.size,
            ),
            quoted = own.isEmpty() && quotedVideos.isNotEmpty(),
        )
        if (post.videos.isEmpty()) {
            return XParseResult.Failure(
                if (post.photoCount > 0) "answer: photos only" else "answer: no video",
                photos = post.photoCount > 0,
            )
        }
        return XParseResult.Success(post)
    }

    private fun videosOf(tweet: JsonValue?): List<XVideo> =
        tweet["mediaDetails"].asArrayOrEmpty.mapIndexedNotNull { index, media ->
            videoOf(index + 1, media)
        }

    private fun videoOf(position: Int, media: JsonValue): XVideo? {
        val type = when (media["type"].asStringOrNull) {
            "video" -> XMediaType.VIDEO
            "animated_gif" -> XMediaType.GIF
            else -> return null
        }
        val info = media["video_info"]
        val variants = info["variants"].asArrayOrEmpty
        val files = variants
            .filter { it["content_type"].asStringOrNull?.lowercase(Locale.US) == MP4 }
            .mapNotNull { variant ->
                val url = variant["url"].asStringOrNull?.takeIf(::isMediaUrl)
                    ?: return@mapNotNull null
                val size = SIZE_IN_PATH.find(url)
                XVariant(
                    url = url,
                    bitrateBitsPerSecond = variant["bitrate"].asLongOrNull?.takeIf { it > 0 },
                    width = size?.groupValues?.get(1)?.toIntOrNull(),
                    height = size?.groupValues?.get(2)?.toIntOrNull(),
                )
            }
            .distinctBy(XVariant::url)
            .sortedByDescending { it.bitrateBitsPerSecond ?: 0L }
        val playlist = variants.firstOrNull {
            it["content_type"].asStringOrNull?.lowercase(Locale.US) == HLS
        }?.get("url").asStringOrNull?.takeIf(::isMediaUrl)
        if (files.isEmpty() && playlist == null) return null
        return XVideo(
            position = position,
            type = type,
            variants = files,
            playlistUrl = playlist,
            durationMillis = info["duration_millis"].asLongOrNull?.takeIf { it > 0 },
            thumbnailUrl = media["media_url_https"].asStringOrNull?.takeIf(::isHttps),
            width = media.path("original_info", "width").asLongOrNull?.toInt(),
            height = media.path("original_info", "height").asLongOrNull?.toInt(),
        )
    }

    /** Only X's own video host: a changed answer cannot point a download anywhere else. */
    fun isMediaUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme == "https" && uri.userInfo == null &&
            uri.host?.lowercase(Locale.US) == VIDEO_HOST
    }

    private fun isHttps(url: String): Boolean = url.startsWith("https://")

    private const val DIGITS = "0123456789abcdefghijklmnopqrstuvwxyz"
    private const val HALF = 0.5
    private const val MANTISSA_BITS = 52
    private const val TOMBSTONE = "TweetTombstone"
    private const val PHOTO = "photo"
    private const val MP4 = "video/mp4"
    private const val HLS = "application/x-mpegurl"
    private const val VIDEO_HOST = "video.twimg.com"
    private val ZEROS_OR_POINT = Regex("0+|\\.")
    private val SIZE_IN_PATH = Regex("/vid/(?:[a-z0-9]+/)?([0-9]{2,5})x([0-9]{2,5})/")
}
