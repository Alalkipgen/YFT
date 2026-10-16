package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.path
import com.alal.yft.extractor.master.modules.youtube.YouTubeParseResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Phase 1.3 D5c — the **H leg** (MASTER_KEY_PHASE1_3_PLAN.md §3 H, steps 1–3; guardrail 6): one
 * HLS row that needs no format the copied parser can read.
 *
 * 1. The HLS address ([address]): the answer's `streamingData.hlsManifestUrl` (HTTPS on
 *    `googlevideo.com`), else the one address ShapeLocator located (`manifest.googlevideo.com`,
 *    `hls_variant` / `hls_playlist`); none or several → no H.
 * 2. The video's length ([length]): the copied parser's (its answer for the requested video), else
 *    L's binding, else the page's; none → no H rows. A verdict (ShapeNormalizer's `Stop`: not `OK`,
 *    DRM, live) → no H.
 * 3. The lookup's H check ([Lookup.run]): D0's [HlsCheck] (master playlist ≤ 256 KiB → highest
 *    variant → its playlist ≤ 1 MiB; no key other than `METHOD=NONE`; the variant's duration within
 *    ±2 s of the length), whose one file check reads the first segment's start instead (`Range`
 *    ≤ [MAX_SEGMENT_BYTES]): its content type and container start must match (MPEG-TS sync byte;
 *    `ftyp` / `styp` / `moof` / `sidx` / `emsg` / `prft`; EBML). Pass → one [Row] of kind HLS (the
 *    master address) for the app's existing HLS path; fail or unknown → none (unknown is never
 *    pass). Every address HTTPS on `googlevideo.com`; at most 3 requests and one H check per
 *    lookup; the media headers without cookies, credentials or a range.
 *
 * Step 4 (when the leg runs) is D6's wiring. Nothing is logged; outcomes print verdicts, reasons
 * and counts only, never an address or a playlist line.
 */
internal class HlsLeg(
    private val http: ExtractorHttpClient,
    private val maxSegmentBytes: Long = MAX_SEGMENT_BYTES,
) {
    enum class Verdict { PASS, FAIL, UNKNOWN }

    /** Why there is no H row. */
    enum class Why {
        /** Step 1: no HLS address, or several. */
        NO_ADDRESS,

        /** Step 2: no known length (guardrail 6). */
        NO_LENGTH,

        /** This lookup's one H check went to another address. */
        SPENT,
        MASTER,
        VARIANT,

        /** `#EXT-X-KEY` or `#EXT-X-SESSION-KEY` with a method other than `NONE`. */
        KEY,

        /** The variant's duration is unknown or not within ±2 s of the length. */
        DURATION,

        /** The first segment did not answer, or is missing or off `googlevideo.com`. */
        SEGMENT,
        SEGMENT_TYPE,
        SEGMENT_START,
    }

    sealed class Outcome {
        /** Requests this outcome cost (master, variant, segment). */
        abstract val requests: Int
    }

    /** The H check passed: the one HLS row. [master] is never printed. */
    class Row internal constructor(
        val master: String,
        val lengthSeconds: Long,
        /** D0's facts: variants, highest resolution, audio form, codec families. */
        val result: HlsCheck.Result,
        /** `ts`, `mp4` or `webm`: the first segment's container. */
        val container: String,
    ) : Outcome() {
        override val requests: Int get() = result.requests

        val maxHeight: Int? get() = result.maxHeight

        /** `separate`, `muxed`, `none` or `unknown`, from the master playlist. */
        val audio: String get() = result.audio

        /** The details line: HLS, resolution and audio form from the master playlist. */
        fun detail(): String =
            "H: HLS (${result.variants} variants, ${maxHeight?.let { "≤ ${it}p" } ?: "resolution unknown"}, audio ${result.audio}, $container)"

        /** The row for the app's HLS path (`DefaultVariantResolver` picks the variant). */
        fun candidate(pageUrl: String, title: String?, thumbnailUrl: String?, userAgent: String?, nowEpochMs: Long): MediaCandidate =
            MediaCandidate(
                pageUrl = pageUrl,
                mediaUrl = master,
                sources = setOf(CandidateSource.MANIFEST),
                kind = MediaKind.HLS,
                mimeType = HLS_MIME,
                title = title,
                thumbnailUrl = thumbnailUrl,
                durationMillis = lengthSeconds * MILLIS,
                requestContext = BrowserRequestContext(pageUrl = pageUrl, userAgent = userAgent, cookie = null),
                confidence = CandidateConfidence.HIGH,
                expiresAtEpochMs = expiryOf(master),
                drmHint = false,
                observedAtEpochMs = nowEpochMs,
                height = maxHeight?.takeIf { it > 0 },
            )

        override fun toString(): String =
            "HlsLeg.Row(variants=${result.variants}, maxHeight=$maxHeight, audio=${result.audio}, container=$container, requests=$requests)"
    }

    /** No H row; [verdict] is null when no check ran. */
    class None internal constructor(val why: Why, val verdict: Verdict?, val result: HlsCheck.Result?) : Outcome() {
        override val requests: Int get() = result?.requests ?: 0

        override fun toString(): String = "HlsLeg.None($why, verdict=$verdict, requests=$requests)"
    }

    /** One lookup's leg: one H check, never shared between lookups. */
    fun lookup(): Lookup = Lookup()

    inner class Lookup internal constructor() {
        private val mutex = Mutex()
        private var asked: String? = null
        private var first: Outcome? = null

        /**
         * Step 3: the H check of [master] bound to [lengthSeconds], with the download's
         * [headers]. A repeat of the same address returns the first outcome (no request).
         */
        suspend fun run(master: String?, lengthSeconds: Long?, headers: Map<String, String>): Outcome = mutex.withLock {
            if (master == null) return@withLock None(Why.NO_ADDRESS, null, null)
            if (lengthSeconds == null || lengthSeconds <= 0) return@withLock None(Why.NO_LENGTH, null, null)
            asked?.let { previous -> return@withLock if (previous == master) first ?: None(Why.SPENT, null, null) else None(Why.SPENT, null, null) }
            asked = master
            val sent = headers.filterKeys { it.trim().lowercase() !in DROPPED_HEADERS }
            val reading = SegmentReading(http, maxSegmentBytes)
            val result = HlsCheck(reading).check(master, sent, lengthSeconds)
            judge(master, lengthSeconds, result, reading).also { first = it }
        }
    }

    private fun judge(master: String, lengthSeconds: Long, result: HlsCheck.Result, reading: SegmentReading): Outcome {
        val fails = ArrayList<Why>()
        val unknowns = ArrayList<Why>()
        fun step(status: String, why: Why) {
            when (status) {
                HlsCheck.OK, HlsCheck.SKIPPED -> Unit
                in UNKNOWN_STATUSES -> unknowns += why
                else -> fails += why
            }
        }
        step(result.master, Why.MASTER)
        step(result.variant, Why.VARIANT)
        if (result.key != NO_KEY) fails += Why.KEY
        if (result.variant == HlsCheck.OK) {
            when (result.durationMatch) {
                true -> Unit
                false -> fails += Why.DURATION
                null -> unknowns += Why.DURATION
            }
        }
        step(result.segment, Why.SEGMENT)
        var container: String? = null
        if (result.segment == HlsCheck.OK) {
            when (val start = startOf(reading.contentType, reading.start)) {
                is Start.Pass -> container = start.container
                is Start.Fail -> fails += start.why
                is Start.Unknown -> unknowns += start.why
            }
        }
        return when {
            fails.isNotEmpty() -> None(fails.first(), Verdict.FAIL, result)
            unknowns.isNotEmpty() -> None(unknowns.first(), Verdict.UNKNOWN, result)
            container == null -> None(Why.SEGMENT, Verdict.UNKNOWN, result)
            else -> Row(master, lengthSeconds, result, container)
        }
    }

    private sealed class Start {
        class Pass(val container: String) : Start()
        class Fail(val why: Why) : Start()
        class Unknown(val why: Why) : Start()
    }

    /** The first segment's content type and start bytes (as the client decoded them) agree. */
    private fun startOf(contentType: String?, start: String?): Start {
        val type = contentType?.substringBefore(';')?.trim()?.lowercase()
        val expected = when (type) {
            null, "", "application/octet-stream", "binary/octet-stream" -> null
            in TS_TYPES -> TS
            in MP4_TYPES -> MP4
            in WEBM_TYPES -> WEBM
            else -> return Start.Fail(Why.SEGMENT_TYPE)
        }
        val text = start.orEmpty()
        val found = when {
            text.startsWith(TS_SYNC) -> TS
            text.length >= BOX_END && text.substring(BOX_TYPE, BOX_END) in MP4_BOXES -> MP4
            text.startsWith(EBML_UTF8) || text.startsWith(EBML_LATIN1) -> WEBM
            else -> return Start.Fail(Why.SEGMENT_START)
        }
        return when (expected) {
            null -> Start.Unknown(Why.SEGMENT_TYPE)
            found -> Start.Pass(found)
            else -> Start.Fail(Why.SEGMENT_START)
        }
    }

    /**
     * [HlsCheck]'s client for one check: playlists go out as asked; its one file check (the first
     * segment) reads the segment's start instead, with `Range` ≤ [maxBytes], and keeps only its
     * content type and first characters. HlsCheck never posts.
     */
    private class SegmentReading(private val http: ExtractorHttpClient, private val maxBytes: Long) : ExtractorHttpClient {
        var contentType: String? = null
        var start: String? = null

        override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult =
            http.get(url, headers, maxBodyBytes)

        override suspend fun postJson(url: String, body: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult =
            ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)

        override suspend fun probe(url: String, headers: Map<String, String>, timeoutMillis: Long): ExtractorProbeResult =
            when (val answer = http.get(url, headers + (RANGE to "bytes=0-${maxBytes - 1}"), maxBytes)) {
                is ExtractorHttpResult.Success -> {
                    contentType = answer.contentType
                    start = answer.body.take(START_CHARS)
                    ExtractorProbeResult.Answered(answer.statusCode)
                }

                is ExtractorHttpResult.Failure -> ExtractorProbeResult.Refused(answer.reason, answer.statusCode)
            }
    }

    companion object {
        const val MAX_SEGMENT_BYTES: Long = 64L * 1024
        const val HLS_MIME: String = "application/vnd.apple.mpegurl"

        /**
         * Step 1: the answer's `streamingData.hlsManifestUrl` when it is HTTPS on `googlevideo.com`
         * (another host: no H), else [located] (ShapeLocator's one HLS address, or null).
         */
        fun address(root: JsonValue?, located: String?): String? {
            val canonical = root.path("streamingData", "hlsManifestUrl").asStringOrNull
            return if (canonical != null) canonical.takeIf(HlsCheck::isMediaHost) else located
        }

        /**
         * Step 2: the copied parser's length when its answer is for [videoId], else L's bound
         * length, else [pageSeconds]; a verdict from L ([ShapeNormalizer.Stop]) gives none.
         */
        fun length(parsed: YouTubeParseResult?, videoId: String, normalized: ShapeNormalizer.Outcome?, pageSeconds: Long?): Long? {
            if (normalized is ShapeNormalizer.Stop) return null
            val video = (parsed as? YouTubeParseResult.Success)?.video
            val fromParser = video?.takeIf { it.videoId == videoId }?.durationMillis?.let { (it + MILLIS / 2) / MILLIS }
            return listOf(fromParser, normalized?.lengthSeconds, pageSeconds).firstOrNull { it != null && it > 0 }
        }

        /** `…/expire/<seconds>/…` in an HLS address, as epoch milliseconds. */
        fun expiryOf(master: String): Long? =
            EXPIRE.find(master)?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it in 1..MAX_EPOCH_SECONDS }?.times(MILLIS)

        private const val MILLIS = 1_000L
        private const val MAX_EPOCH_SECONDS = 9_999_999_999L
        private const val NO_KEY = "NONE"
        private const val RANGE = "Range"
        private const val START_CHARS = 16
        private const val BOX_TYPE = 4
        private const val BOX_END = 8
        private const val TS = "ts"
        private const val MP4 = "mp4"
        private const val WEBM = "webm"
        private const val TS_SYNC = "\u0047"
        private const val EBML_UTF8 = "\u001A\u0045\u07E3"
        private const val EBML_LATIN1 = "\u001A\u0045\u00DF\u00A3"
        private val EXPIRE = Regex("/expire/(\\d{1,10})(?:/|$)")
        private val TS_TYPES = setOf("video/mp2t", "video/mp2ts", "video/m2ts")
        private val MP4_TYPES = setOf("video/mp4", "audio/mp4", "video/iso.segment", "audio/iso.segment")
        private val WEBM_TYPES = setOf("video/webm", "audio/webm")
        private val MP4_BOXES = setOf("ftyp", "styp", "moof", "sidx", "emsg", "prft")
        private val UNKNOWN_STATUSES = setOf("5xx", "other", HlsCheck.NETWORK, HlsCheck.TOO_LARGE, HlsCheck.UNSUPPORTED)
        private val DROPPED_HEADERS = setOf("cookie", "cookie2", "authorization", "proxy-authorization", "x-goog-authuser", "range")
    }
}
