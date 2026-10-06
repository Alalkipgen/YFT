package com.alal.yft.extractor.generic.manifest

import java.net.URI
import java.util.Locale

/**
 * P24: what an HLS or DASH manifest states about its video, read from the manifest's text alone
 * (no media is downloaded). [durationMillis] is the whole video's length: an HLS master states
 * none, so [firstPlaylistUrl] names the playlist that does; a live stream has no length.
 */
data class ManifestFacts(
    val durationMillis: Long? = null,
    /** The tallest picture the manifest lists, with its width. */
    val width: Int? = null,
    val height: Int? = null,
    /** True for an HLS master, which lists qualities rather than pieces. */
    val master: Boolean = false,
    /** An HLS master's first video playlist, as an absolute address; null otherwise. */
    val firstPlaylistUrl: String? = null,
)

/** Pure readers of [ManifestFacts]; unit-tested on fixture manifests. */
object ManifestReader {
    /** The facts of an HLS playlist at [manifestUrl], or null when [text] is not HLS. */
    fun hls(text: String, manifestUrl: String): ManifestFacts? {
        val lines = text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        if (lines.firstOrNull()?.removePrefix(BYTE_ORDER_MARK) != "#EXTM3U") return null
        var seconds = 0.0
        var sawPiece = false
        var ended = false
        var pendingStream: String? = null
        val streams = mutableListOf<Pair<String, String>>()
        lines.drop(1).forEach { line ->
            when {
                line.startsWith(STREAM_INF, ignoreCase = true) ->
                    pendingStream = line.substringAfter(':')
                pendingStream != null && !line.startsWith('#') -> {
                    streams += checkNotNull(pendingStream) to line
                    pendingStream = null
                }
                line.startsWith(EXTINF, ignoreCase = true) -> line.substringAfter(':')
                    .substringBefore(',')
                    .trim()
                    .toDoubleOrNull()
                    ?.takeIf { it.isFinite() && it >= 0 }
                    ?.let {
                        seconds += it
                        sawPiece = true
                    }
                line.equals(END_LIST, ignoreCase = true) ||
                    line.equals(VOD_TYPE, ignoreCase = true) -> ended = true
            }
        }
        if (streams.isNotEmpty()) {
            val pictures = streams.mapNotNull { (attributes, _) -> resolution(attributes) }
            val tallest = pictures.maxByOrNull { it.second }
            val first = streams.firstOrNull { (attributes, _) -> !audioOnly(attributes) }
                ?: streams.first()
            return ManifestFacts(
                width = tallest?.first,
                height = tallest?.second,
                master = true,
                firstPlaylistUrl = resolve(manifestUrl, first.second),
            )
        }
        val duration = (seconds * MILLIS_PER_SECOND).toLong()
            .takeIf { sawPiece && ended && it > 0 }
        return ManifestFacts(durationMillis = duration)
    }

    /** The facts of a DASH manifest, or null when [text] is not one. A live one has no length. */
    fun dash(text: String): ManifestFacts? {
        val root = MPD_TAG.find(text)?.groupValues?.get(1) ?: return null
        val dynamic = DYNAMIC.containsMatchIn(root)
        val duration = MEDIA_DURATION.find(root)?.groupValues?.get(1)
            ?.let(::isoDurationMillis)
            ?.takeUnless { dynamic }
        val tallest = REPRESENTATION.findAll(text)
            .mapNotNull { match ->
                val attributes = match.groupValues[1]
                val height = HEIGHT.find(attributes)?.groupValues?.get(1)?.toIntOrNull()
                    ?.takeIf { it > 0 } ?: return@mapNotNull null
                WIDTH.find(attributes)?.groupValues?.get(1)?.toIntOrNull() to height
            }
            .maxByOrNull { it.second }
        return ManifestFacts(
            durationMillis = duration,
            width = tallest?.first?.takeIf { it > 0 },
            height = tallest?.second,
        )
    }

    /**
     * An ISO 8601 length such as `PT12M5S` or `PT1H2M3.5S` (JSON-LD, DASH), or a plain number of
     * seconds, in milliseconds; null for anything else or a length of zero.
     */
    fun isoDurationMillis(value: String?): Long? {
        val text = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        text.toDoubleOrNull()?.let { seconds ->
            return (seconds * MILLIS_PER_SECOND).toLong().takeIf { seconds.isFinite() && it > 0 }
        }
        val match = ISO_DURATION.matchEntire(text.uppercase(Locale.US)) ?: return null
        val (days, hours, minutes, seconds) = match.destructured
        val total = listOf(
            days to SECONDS_PER_DAY,
            hours to SECONDS_PER_HOUR,
            minutes to SECONDS_PER_MINUTE,
            seconds to 1.0,
        ).sumOf { (amount, unit) -> (amount.toDoubleOrNull() ?: 0.0) * unit }
        return (total * MILLIS_PER_SECOND).toLong().takeIf { total.isFinite() && it > 0 }
    }

    private fun resolution(attributes: String): Pair<Int, Int>? {
        val value = RESOLUTION.find(attributes)?.groupValues ?: return null
        val width = value[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
        val height = value[2].toIntOrNull()?.takeIf { it > 0 } ?: return null
        return width to height
    }

    /** A stream whose codecs name only sound. */
    private fun audioOnly(attributes: String): Boolean {
        val codecs = CODECS.find(attributes)?.groupValues?.get(1)?.lowercase(Locale.US)
            ?: return false
        return codecs.split(',').map(String::trim).all { it.startsWith("mp4a") }
    }

    private fun resolve(base: String, reference: String): String? =
        runCatching { URI(base).resolve(reference.trim()).toString() }.getOrNull()
            ?.takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) }

    private const val BYTE_ORDER_MARK = "\uFEFF"
    private const val STREAM_INF = "#EXT-X-STREAM-INF:"
    private const val EXTINF = "#EXTINF:"
    private const val END_LIST = "#EXT-X-ENDLIST"
    private const val VOD_TYPE = "#EXT-X-PLAYLIST-TYPE:VOD"
    private const val MILLIS_PER_SECOND = 1_000.0
    private const val SECONDS_PER_MINUTE = 60.0
    private const val SECONDS_PER_HOUR = 3_600.0
    private const val SECONDS_PER_DAY = 86_400.0
    private val RESOLUTION = Regex("""(?i)RESOLUTION=(\d+)x(\d+)""")
    private val CODECS = Regex("""(?i)CODECS="([^"]*)"""")
    private val MPD_TAG = Regex("""(?is)<MPD\b([^>]*)>""")
    private val DYNAMIC = Regex("""(?i)\btype\s*=\s*["']dynamic["']""")
    private val MEDIA_DURATION = Regex("""(?i)\bmediaPresentationDuration\s*=\s*["']([^"']+)["']""")
    private val REPRESENTATION = Regex("""(?is)<Representation\b([^>]*)>""")
    private val HEIGHT = Regex("""(?i)\bheight\s*=\s*["'](\d+)["']""")
    private val WIDTH = Regex("""(?i)\bwidth\s*=\s*["'](\d+)["']""")
    private val ISO_DURATION = Regex(
        """P(?:(\d+(?:\.\d+)?)D)?(?:T(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?""" +
            """(?:(\d+(?:\.\d+)?)S)?)?""",
    )
}
