package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import java.net.URI
import kotlinx.coroutines.CancellationException

/**
 * Phase 1.3 D0: the census's H check with the **token step** (MASTER_KEY_PHASE1_3_PLAN.md §4 D0,
 * guardrail 6): the master playlist and the highest-resolution variant playlist (≤ 256 KiB each)
 * and the first segment of that variant (the one-byte file check), each `2xx` or `403`; the
 * master's variants, highest resolution, audio form and codec families; any `#EXT-X-KEY` or
 * `#EXT-X-SESSION-KEY` other than `METHOD=NONE`; the variant's total duration against the video's
 * length (± [DURATION_TOLERANCE_SECONDS]).
 *
 * Every address must be HTTPS on a `googlevideo.com` host, else the step is `host` and nothing is
 * asked. At most 3 requests. The result holds statuses, counts and codec families only, never an
 * address or a playlist line. D4 adds the content-type and container checks of the lookup's H
 * check on top of this.
 */
internal class HlsCheck(
    private val http: ExtractorHttpClient,
    private val maxPlaylistBytes: Long = MAX_PLAYLIST_BYTES,
) {
    data class Result(
        val master: String,
        val variant: String,
        val segment: String,
        val variants: Int = 0,
        val maxHeight: Int? = null,
        /** `separate` (an audio group with its own playlist), `muxed`, `none` or `unknown`. */
        val audio: String = "unknown",
        val codecs: List<String> = emptyList(),
        /** `NONE` (no key, or only `METHOD=NONE`) or the other methods found, sorted. */
        val key: String = "NONE",
        val segments: Int = 0,
        val durationSeconds: Double? = null,
        val durationMatch: Boolean? = null,
        val requests: Int = 0,
    )

    /** Checks the playlist at [masterUrl] with [headers] (no cookie); [lengthSeconds] binds it. */
    suspend fun check(
        masterUrl: String,
        headers: Map<String, String>,
        lengthSeconds: Long?,
    ): Result {
        if (!isMediaHost(masterUrl)) return Result(master = HOST, variant = SKIPPED, segment = SKIPPED)
        val master = get(masterUrl, headers)
        if (master.status != OK) return Result(master = master.status, variant = SKIPPED, segment = SKIPPED, requests = 1)
        val body = master.body.orEmpty()
        if (!body.trimStart().startsWith(M3U_HEADER)) {
            return Result(master = NOT_PLAYLIST, variant = SKIPPED, segment = SKIPPED, requests = 1)
        }
        val parsed = Master.parse(body)
        val keyMethods = parsed.keyMethods.toMutableSet()
        val chosen = parsed.variants.maxWithOrNull(
            compareBy<Variant>({ it.height ?: -1 }, { it.bandwidth ?: -1L }),
        )
        val base = Result(
            master = OK,
            variant = SKIPPED,
            segment = SKIPPED,
            variants = parsed.variants.size,
            maxHeight = parsed.variants.mapNotNull(Variant::height).maxOrNull(),
            audio = parsed.audioForm(),
            codecs = parsed.variants.flatMap(Variant::codecs).toSortedSet().toList(),
            key = keyOf(keyMethods),
            requests = 1,
        )
        val variantUrl = chosen?.uri?.let { resolve(masterUrl, it) }
            ?: return base.copy(variant = NO_VARIANT)
        if (!isMediaHost(variantUrl)) return base.copy(variant = HOST)
        val variant = get(variantUrl, headers)
        if (variant.status != OK) return base.copy(variant = variant.status, requests = 2)
        val media = MediaPlaylist.parse(variant.body.orEmpty())
        keyMethods += media.keyMethods
        val duration = media.durationSeconds.takeIf { media.segments > 0 }
        val withVariant = base.copy(
            variant = OK,
            key = keyOf(keyMethods),
            segments = media.segments,
            durationSeconds = duration?.let { Math.round(it * 10) / 10.0 },
            durationMatch = if (duration != null && lengthSeconds != null && lengthSeconds > 0) {
                Math.abs(duration - lengthSeconds) <= DURATION_TOLERANCE_SECONDS
            } else {
                null
            },
            requests = 2,
        )
        val segmentUrl = media.firstSegment?.let { resolve(variantUrl, it) }
            ?: return withVariant.copy(segment = NO_SEGMENT)
        if (!isMediaHost(segmentUrl)) return withVariant.copy(segment = HOST)
        return withVariant.copy(segment = probe(segmentUrl, headers), requests = 3)
    }

    private class Fetched(val status: String, val body: String?)

    private suspend fun get(url: String, headers: Map<String, String>): Fetched {
        val result = try {
            http.get(url, headers, maxPlaylistBytes)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            return Fetched(NETWORK, null)
        }
        return when (result) {
            is ExtractorHttpResult.Success -> Fetched(OK, result.body)
            is ExtractorHttpResult.Failure -> Fetched(statusOf(result.statusCode, result.reason), null)
        }
    }

    private suspend fun probe(url: String, headers: Map<String, String>): String {
        val answer = try {
            http.probe(url, headers)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            return NETWORK
        }
        return when (answer) {
            is ExtractorProbeResult.Answered -> OK
            is ExtractorProbeResult.Refused -> statusOf(answer.statusCode, answer.reason)
            ExtractorProbeResult.Unsupported -> UNSUPPORTED
        }
    }

    private class Variant(
        val uri: String?,
        val height: Int?,
        val bandwidth: Long?,
        val codecs: List<String>,
        val audioGroup: String?,
    )

    private class Master(
        val variants: List<Variant>,
        val audioGroupsWithUri: Set<String>,
        val keyMethods: Set<String>,
    ) {
        fun audioForm(): String {
            if (variants.isEmpty()) return "unknown"
            val separate = variants.any { it.audioGroup != null && it.audioGroup in audioGroupsWithUri }
            if (separate) return "separate"
            val muxed = variants.any { variant -> variant.codecs.any { it in AUDIO_FAMILIES } }
            return if (muxed) "muxed" else "none"
        }

        companion object {
            fun parse(text: String): Master {
                val variants = ArrayList<Variant>()
                val audioGroups = HashSet<String>()
                val keys = HashSet<String>()
                var pending: Map<String, String>? = null
                for (raw in text.lineSequence()) {
                    val line = raw.trim()
                    when {
                        line.isEmpty() -> Unit
                        line.startsWith(STREAM_INF) -> pending = attributes(line.substring(STREAM_INF.length))
                        line.startsWith(MEDIA) -> {
                            val media = attributes(line.substring(MEDIA.length))
                            if (media["TYPE"] == "AUDIO" && media["URI"] != null) {
                                media["GROUP-ID"]?.let(audioGroups::add)
                            }
                        }
                        line.startsWith(SESSION_KEY) -> keys += methodOf(line.substring(SESSION_KEY.length))
                        line.startsWith(KEY) -> keys += methodOf(line.substring(KEY.length))
                        line.startsWith("#") -> Unit
                        else -> pending?.let { attributes ->
                            variants += Variant(
                                uri = line,
                                height = attributes["RESOLUTION"]?.substringAfter('x', "")?.toIntOrNull(),
                                bandwidth = attributes["BANDWIDTH"]?.toLongOrNull(),
                                codecs = familiesOf(attributes["CODECS"]),
                                audioGroup = attributes["AUDIO"],
                            )
                            pending = null
                        }
                    }
                }
                return Master(variants, audioGroups, keys)
            }
        }
    }

    private class MediaPlaylist(
        val segments: Int,
        val durationSeconds: Double,
        val firstSegment: String?,
        val keyMethods: Set<String>,
    ) {
        companion object {
            fun parse(text: String): MediaPlaylist {
                var segments = 0
                var duration = 0.0
                var first: String? = null
                var afterInf = false
                val keys = HashSet<String>()
                for (raw in text.lineSequence()) {
                    val line = raw.trim()
                    when {
                        line.isEmpty() -> Unit
                        line.startsWith(EXTINF) -> {
                            duration += line.substring(EXTINF.length).substringBefore(',').trim()
                                .toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: 0.0
                            afterInf = true
                        }
                        line.startsWith(KEY) -> keys += methodOf(line.substring(KEY.length))
                        line.startsWith("#") -> Unit
                        afterInf -> {
                            segments += 1
                            if (first == null) first = line
                            afterInf = false
                        }
                    }
                }
                return MediaPlaylist(segments, duration, first, keys)
            }
        }
    }

    companion object {
        const val MAX_PLAYLIST_BYTES: Long = 256L * 1024
        const val DURATION_TOLERANCE_SECONDS = 2.0

        const val OK = "2xx"
        const val SKIPPED = "skip"
        const val HOST = "host"
        const val NETWORK = "net"
        const val UNSUPPORTED = "unsupported"
        const val NOT_PLAYLIST = "not-m3u8"
        const val NO_VARIANT = "no-variant"
        const val NO_SEGMENT = "no-segment"
        const val TOO_LARGE = "too-large"

        private const val M3U_HEADER = "#EXTM3U"
        private const val STREAM_INF = "#EXT-X-STREAM-INF:"
        private const val MEDIA = "#EXT-X-MEDIA:"
        private const val SESSION_KEY = "#EXT-X-SESSION-KEY:"
        private const val KEY = "#EXT-X-KEY:"
        private const val EXTINF = "#EXTINF:"
        private const val MEDIA_HOST_SUFFIX = ".googlevideo.com"
        private val AUDIO_FAMILIES = setOf("mp4a", "opus", "ac-3", "ec-3", "flac", "alac")
        private val FAMILY = Regex("^[a-z0-9-]{2,8}$")
        private val METHOD = Regex("^[A-Z0-9-]{1,20}$")

        /** A status class: `2xx`, `403`, `4xx`, `5xx`, `too-large` or `net`. */
        fun statusOf(code: Int?, reason: SiteExtractionFailure?): String = when {
            code == 403 -> "403"
            code != null && code in 400..499 -> "4xx"
            code != null && code in 500..599 -> "5xx"
            code != null && code in 200..299 -> OK
            code != null -> "other"
            reason == SiteExtractionFailure.RESPONSE_TOO_LARGE -> TOO_LARGE
            else -> NETWORK
        }

        /** HTTPS on a `googlevideo.com` host, the only hosts an H check asks. */
        fun isMediaHost(url: String): Boolean {
            val uri = runCatching { URI(url) }.getOrNull() ?: return false
            val host = uri.host?.lowercase() ?: return false
            return uri.scheme.equals("https", ignoreCase = true) && host.endsWith(MEDIA_HOST_SUFFIX)
        }

        private fun resolve(base: String, reference: String): String? =
            runCatching { URI(base).resolve(reference.trim()).toString() }.getOrNull()

        private fun keyOf(methods: Set<String>): String {
            val other = methods.filter { it != "NONE" }.toSortedSet()
            return if (other.isEmpty()) "NONE" else other.joinToString("+")
        }

        private fun methodOf(attributeText: String): String =
            attributes(attributeText)["METHOD"]?.takeIf(METHOD::matches) ?: "UNKNOWN"

        private fun familiesOf(codecs: String?): List<String> =
            codecs.orEmpty().split(',').mapNotNull { codec ->
                codec.trim().substringBefore('.').lowercase().takeIf(FAMILY::matches)
            }.distinct()

        /** An HLS attribute list (`A=1,B="x,y"`), quotes removed. */
        fun attributes(text: String): Map<String, String> {
            val result = LinkedHashMap<String, String>()
            var index = 0
            while (index < text.length) {
                val equals = text.indexOf('=', index)
                if (equals < 0) break
                val name = text.substring(index, equals).trim().trimStart(',').trim()
                var valueEnd: Int
                val value: String
                if (equals + 1 < text.length && text[equals + 1] == '"') {
                    val close = text.indexOf('"', equals + 2)
                    valueEnd = if (close < 0) text.length else close + 1
                    value = text.substring(equals + 2, if (close < 0) text.length else close)
                } else {
                    val comma = text.indexOf(',', equals + 1)
                    valueEnd = if (comma < 0) text.length else comma
                    value = text.substring(equals + 1, valueEnd).trim()
                }
                if (name.isNotEmpty()) result[name] = value
                index = valueEnd
                if (index < text.length && text[index] == ',') index += 1
            }
            return result
        }
    }
}
