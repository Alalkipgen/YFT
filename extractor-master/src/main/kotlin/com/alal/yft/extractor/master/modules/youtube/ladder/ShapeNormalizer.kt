package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asBooleanOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.api.json.path
import com.alal.yft.extractor.master.modules.youtube.StreamCheck
import com.alal.yft.extractor.master.modules.youtube.YouTubeParseResult
import com.alal.yft.extractor.master.modules.youtube.YouTubePlayerResponseParser
import com.alal.yft.extractor.master.modules.youtube.YouTubeStream
import com.alal.yft.extractor.master.modules.youtube.YouTubeVideo
import kotlinx.coroutines.CancellationException
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Phase 1.3 D5b — rung **L**, steps 2–6 (MASTER_KEY_PHASE1_3_PLAN.md §3 L, guardrails 2 and 9):
 * a changed answer → [ShapeLocator]'s scan → bind to the video → canonical JSON → the copied
 * `YouTubePlayerResponseParser.parse` (unchanged) → the offer set (≤ [MAX_OFFER]).
 *
 * 1. Playability (step 4): the answer's own `playabilityStatus.status` is the copied parser's
 *    verdict; without one, exactly one located status object. Anything but `OK` → [Stop] (the R6
 *    chain decides); none, or several that are all `OK` → no L rows.
 * 2. Binding (step 2): the video objects carrying the requested video ID must agree on one length
 *    (Plan adapted: one object, or copies of the same length such as a microformat; different
 *    lengths → ambiguous); a candidate binds when its address's `dur`, else its `approxDurationMs`,
 *    else an integer field of milliseconds lies within ±1.5 s of that length — others are dropped
 *    (another video's formats). All bound candidates must sit in lists or maps under one parent
 *    (`streamingData` and its lists), else ambiguous → no L rows. Duplicates (same address or
 *    cipher) count once.
 * 3. Field mapping (step 3): `itag`, address or cipher and mime come from [ShapeLocator]'s value
 *    rules. Plan adapted: the other fields are integers or words whose shape alone cannot tell them
 *    apart, so they are read under their canonical names with normalized types (digit text or
 *    number), and by shape only where the shape is distinctive (`1080p60` for `qualityLabel`,
 *    `AUDIO_QUALITY_…` for `audioQuality`). A renamed size stays missing (Z, the init check).
 * 4. Canonical JSON (step 5): `playabilityStatus`, `videoDetails`, `streamingData` (`formats` for
 *    candidates whose codecs name a video and an audio track, else `adaptiveFormats`,
 *    `hlsManifestUrl`, `expiresInSeconds`); a DRM marker or a live flag goes in too, so the copied
 *    parser stops as on the answer itself. Main's row rules apply unchanged.
 * 5. Offer set (step 6): the copied extractor's selection (mirrored; it is private), at most
 *    [MAX_OFFER] rows — companions first, then merged rows by quality, then progressive files.
 *    Every offered row needs V and the init check in this lookup ([LRows]).
 *
 * Steps 7–8 (shape map memory, page signals) are not here yet. No request, no log; outcomes
 * print reasons and counts only.
 */
internal object ShapeNormalizer {
    const val MAX_OFFER: Int = 8
    const val BIND_TOLERANCE_MILLIS: Long = 1_500L

    enum class Why {
        TOO_LARGE,
        UNREADABLE,
        INCOMPLETE_SCAN,
        NO_STATUS,
        AMBIGUOUS_STATUS,
        NO_VIDEO,
        AMBIGUOUS_VIDEO,
        NO_FORMATS,
        AMBIGUOUS_GROUPS,
        NO_ROWS,
    }

    sealed class Outcome {
        /** The located HLS address (exactly one), for the H leg; never printed. */
        abstract val hls: String?

        /** The bound length in seconds, for the H leg's binding; null when not bound. */
        abstract val lengthSeconds: Long?
    }

    /** L rows to check: [offer] (≤ [MAX_OFFER]) from [video], the copied parser's reading of [canonicalJson]. */
    class Rows internal constructor(
        val video: YouTubeVideo,
        val offer: List<YouTubeStream>,
        val canonicalJson: String,
        override val hls: String?,
        override val lengthSeconds: Long,
        /** Bound format-like objects. */
        val located: Int,
        /** Key paths of the bound objects (the shape map's located side; no values). */
        val paths: Set<String>,
    ) : Outcome() {
        override fun toString(): String =
            "ShapeNormalizer.Rows(rows=${video.progressive.size + video.adaptive.size}, offer=${offer.size}, " +
                "located=$located, paths=${paths.size}, hls=${hls != null})"
    }

    /** A verdict that is not `OK` (age check, private, bot check, DRM, live, …): the R6 chain decides. */
    class Stop internal constructor(val failure: YouTubeParseResult.Failure) : Outcome() {
        override val hls: String? get() = null
        override val lengthSeconds: Long? get() = null

        override fun toString(): String = "ShapeNormalizer.Stop(${failure.reason}, definite=${failure.definite})"
    }

    /** No L rows, and why. */
    class None internal constructor(val why: Why, override val hls: String?, override val lengthSeconds: Long?) : Outcome() {
        override fun toString(): String = "ShapeNormalizer.None($why, hls=${hls != null}, length=${lengthSeconds != null})"
    }

    fun normalize(answer: String, videoId: String, nowEpochMs: Long): Outcome {
        if (answer.length > ShapeLocator.MAX_ANSWER_CHARS) return None(Why.TOO_LARGE, null, null)
        val root = ShapeLocator.parse(answer) ?: return None(Why.UNREADABLE, null, null)
        return normalize(root, answer, videoId, nowEpochMs)
    }

    /** [answer] is the raw text of [root] (for the deep-cut check; without it a deep cut gives no rows). */
    fun normalize(root: JsonValue, answer: String?, videoId: String, nowEpochMs: Long): Outcome {
        val rootStatus = root["playabilityStatus"]
        val rootValue = rootStatus["status"].asStringOrNull
        if (rootValue != null && rootValue != OK) return Stop(verdictOf(rootStatus as JsonValue.Object, rootValue, nowEpochMs))

        val scan = ShapeLocator.scan(root, videoId, answer)
        if (!scan.complete) return None(Why.INCOMPLETE_SCAN, null, null)
        if (rootValue == null) {
            if (scan.statuses.isEmpty()) return None(Why.NO_STATUS, null, null)
            scan.statuses.firstOrNull { it.value != OK }?.let { return Stop(verdictOf(it.node, it.value, nowEpochMs)) }
            if (scan.statuses.size > 1) return None(Why.AMBIGUOUS_STATUS, null, null)
        }

        val videos = scan.videos
        if (videos.isEmpty()) return None(Why.NO_VIDEO, scan.hls, null)
        val length = videos.first().lengthSeconds
        if (videos.any { it.lengthSeconds != length }) return None(Why.AMBIGUOUS_VIDEO, scan.hls, null)
        val details = videos.first().node

        val live = details["isLive"].asBooleanOrNull == true
        if (scan.drm || live) {
            // As the copied parser reads the marker; were it ever to read on, still no L rows.
            val parsed = YouTubePlayerResponseParser.parse(canonical(details, videoId, length, emptyList(), null, null, scan.drm, live), nowEpochMs)
            return if (parsed is YouTubeParseResult.Failure) Stop(parsed) else None(Why.NO_ROWS, null, length)
        }

        val bound = scan.candidates.filter { binds(it, length) }.distinctBy { it.source }
        if (bound.isEmpty()) return None(Why.NO_FORMATS, scan.hls, length)
        if (bound.any { it.family != bound.first().family }) return None(Why.AMBIGUOUS_GROUPS, scan.hls, length)

        val json = canonical(details, videoId, length, bound, bound.first().familyNode, scan.hls, drm = false, live = false)
        return when (val parsed = YouTubePlayerResponseParser.parse(json, nowEpochMs)) {
            is YouTubeParseResult.Success -> {
                val offer = offerSet(parsed.video)
                if (offer.isEmpty()) {
                    None(Why.NO_ROWS, scan.hls, length)
                } else {
                    Rows(parsed.video, offer, json, scan.hls, length, bound.size, bound.mapTo(LinkedHashSet()) { it.path })
                }
            }

            is YouTubeParseResult.Failure -> if (parsed.definite) Stop(parsed) else None(Why.NO_ROWS, scan.hls, length)
        }
    }

    /**
     * The rows the app would offer from [video] — the copied `YouTubeExtractor.select` (private,
     * mirrored here: progressive MP4 files for qualities without a merged row, per quality the best
     * AVC (144p–1080p) or 8-bit VP9 with Opus / AV1 with AAC (1440p, 2160p), the best AAC and Opus
     * audio) — capped at [MAX_OFFER]: companions, then merged rows by quality, then files.
     */
    fun offerSet(video: YouTubeVideo): List<YouTubeStream> {
        val selected = select(video)
        if (selected.size <= MAX_OFFER) return selected
        val priority = selected.indices.sortedBy { index ->
            val row = selected[index]
            when {
                !row.hasVideo -> 0
                !row.hasAudio -> 1
                else -> 2
            }
        }
        val kept = priority.take(MAX_OFFER).toSet()
        return selected.filterIndexed { index, _ -> index in kept }
    }

    // --- binding ---

    private fun binds(candidate: ShapeLocator.Candidate, lengthSeconds: Long): Boolean {
        val target = lengthSeconds * MILLIS
        candidate.durationSeconds?.let { return abs((it * MILLIS).roundToLong() - target) <= BIND_TOLERANCE_MILLIS }
        val entries = candidate.node.entries
        integerOf(entries["approxDurationMs"])?.let { return abs(it - target) <= BIND_TOLERANCE_MILLIS }
        return entries.values.any { value -> integerOf(value)?.let { abs(it - target) <= BIND_TOLERANCE_MILLIS } == true }
    }

    // --- playability ---

    /** The copied parser's verdict on a status object (its status word set as `status`). */
    private fun verdictOf(node: JsonValue.Object, value: String, nowEpochMs: Long): YouTubeParseResult.Failure {
        val status = JsonValue.Object(LinkedHashMap(node.entries).apply { put("status", JsonValue.Text(value)) })
        val out = StringBuilder("{\"playabilityStatus\":")
        write(out, status)
        out.append('}')
        return YouTubePlayerResponseParser.parse(out.toString(), nowEpochMs) as? YouTubeParseResult.Failure
            ?: YouTubeParseResult.Failure(SiteExtractionFailure.RESPONSE_CHANGED, definite = false)
    }

    // --- canonical JSON ---

    private fun canonical(
        details: JsonValue.Object,
        videoId: String,
        lengthSeconds: Long,
        formats: List<ShapeLocator.Candidate>,
        family: JsonValue.Object?,
        hls: String?,
        drm: Boolean,
        live: Boolean,
    ): String {
        val out = StringBuilder(256 + formats.size * 512)
        out.append("{\"playabilityStatus\":{\"status\":\"OK\"},\"videoDetails\":{\"videoId\":")
        quote(out, videoId)
        details["title"].asStringOrNull?.let { out.append(",\"title\":"); quote(out, it) }
        out.append(",\"lengthSeconds\":\"").append(lengthSeconds).append('"')
        details["author"].asStringOrNull?.let { out.append(",\"author\":"); quote(out, it) }
        (details["thumbnail"] as? JsonValue.Object)?.let { out.append(",\"thumbnail\":"); write(out, it) }
        if (live) out.append(",\"isLive\":true")
        out.append("},\"streamingData\":{")
        var first = true
        fun field(name: String) {
            if (!first) out.append(',')
            first = false
            out.append('"').append(name).append("\":")
        }
        integerOf(family?.get("expiresInSeconds"))?.let { field("expiresInSeconds"); out.append('"').append(it).append('"') }
        val (progressive, adaptive) = formats.partition(::isProgressive)
        field("formats")
        list(out, progressive)
        field("adaptiveFormats")
        list(out, adaptive)
        hls?.let { field("hlsManifestUrl"); quote(out, it) }
        if (drm) {
            field("drmParams")
            out.append("\"located\"")
        }
        out.append("}}")
        return out.toString()
    }

    private fun isProgressive(candidate: ShapeLocator.Candidate): Boolean {
        val codecs = ShapeLocator.codecsOf(candidate.mime)
        return codecs.any(ShapeLocator::isVideoCodec) && codecs.any(ShapeLocator::isAudioCodec)
    }

    private fun list(out: StringBuilder, formats: List<ShapeLocator.Candidate>) {
        out.append('[')
        formats.forEachIndexed { index, candidate ->
            if (index > 0) out.append(',')
            format(out, candidate)
        }
        out.append(']')
    }

    private fun format(out: StringBuilder, candidate: ShapeLocator.Candidate) {
        val entries = candidate.node.entries
        out.append("{\"itag\":").append(candidate.itag)
        if (candidate.address != null) {
            out.append(",\"url\":")
            quote(out, candidate.address)
        } else {
            out.append(",\"signatureCipher\":")
            quote(out, candidate.source)
        }
        out.append(",\"mimeType\":")
        quote(out, candidate.mime)
        text(out, "qualityLabel", entries["qualityLabel"].asStringOrNull ?: shaped(candidate.node, QUALITY_LABEL))
        number(out, "bitrate", integerOf(entries["bitrate"]))
        number(out, "averageBitrate", integerOf(entries["averageBitrate"]))
        number(out, "width", integerOf(entries["width"]))
        number(out, "height", integerOf(entries["height"]))
        digits(out, "contentLength", integerOf(entries["contentLength"]))
        number(out, "fps", integerOf(entries["fps"]))
        text(out, "audioQuality", entries["audioQuality"].asStringOrNull ?: shaped(candidate.node, AUDIO_QUALITY))
        digits(out, "audioSampleRate", integerOf(entries["audioSampleRate"]))
        number(out, "audioChannels", integerOf(entries["audioChannels"]))
        entries["isDrc"].asBooleanOrNull?.let { out.append(",\"isDrc\":").append(it) }
        candidate.node.path("audioTrack", "audioIsDefault").asBooleanOrNull?.let {
            out.append(",\"audioTrack\":{\"audioIsDefault\":").append(it).append('}')
        }
        digits(out, "approxDurationMs", integerOf(entries["approxDurationMs"]))
        out.append('}')
    }

    private fun text(out: StringBuilder, name: String, value: String?) {
        value ?: return
        out.append(",\"").append(name).append("\":")
        quote(out, value)
    }

    private fun number(out: StringBuilder, name: String, value: Long?) {
        value ?: return
        out.append(",\"").append(name).append("\":").append(value)
    }

    private fun digits(out: StringBuilder, name: String, value: Long?) {
        value ?: return
        out.append(",\"").append(name).append("\":\"").append(value).append('"')
    }

    /** The one direct text of [node] matching [shape], or null (none or several). */
    private fun shaped(node: JsonValue.Object, shape: Regex): String? {
        var found: String? = null
        for (value in node.entries.values) {
            val text = (value as? JsonValue.Text)?.value ?: continue
            if (text.length > MAX_WORD || !shape.matches(text) || text == found) continue
            if (found != null) return null
            found = text
        }
        return found
    }

    /** A non-negative integer from a number or digit text, else null. */
    private fun integerOf(value: JsonValue?): Long? = when (value) {
        is JsonValue.Number -> value.asLong
        is JsonValue.Text -> value.value.takeIf { it.length in 1..18 && it.all { c -> c in '0'..'9' } }?.toLongOrNull()
        else -> null
    }?.takeIf { it >= 0 }

    private fun write(out: StringBuilder, value: JsonValue) {
        when (value) {
            JsonValue.Null -> out.append("null")
            is JsonValue.Bool -> out.append(value.value)
            is JsonValue.Number -> out.append(value.text)
            is JsonValue.Text -> quote(out, value.value)
            is JsonValue.Array -> {
                out.append('[')
                value.items.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    write(out, item)
                }
                out.append(']')
            }

            is JsonValue.Object -> {
                out.append('{')
                var index = 0
                for ((key, item) in value.entries) {
                    if (index++ > 0) out.append(',')
                    quote(out, key)
                    out.append(':')
                    write(out, item)
                }
                out.append('}')
            }
        }
    }

    private fun quote(out: StringBuilder, text: String) {
        out.append('"')
        for (char in text) {
            when {
                char == '"' -> out.append("\\\"")
                char == '\\' -> out.append("\\\\")
                char < ' ' -> out.append("\\u").append(String.format("%04x", char.code))
                else -> out.append(char)
            }
        }
        out.append('"')
    }

    // --- the copied YouTubeExtractor.select, mirrored ---

    private fun select(video: YouTubeVideo): List<YouTubeStream> {
        val progressive = video.progressive
            .filter { it.hasVideo && it.hasAudio && it.mimeType == VIDEO_MP4 }
            .distinctBy(YouTubeStream::itag)
            .sortedByDescending { it.height ?: 0 }
        val audio = bestAudio(video, AUDIO_MP4) { it.startsWith(AAC_CODEC_PREFIX) }
            ?.takeIf { stream -> progressive.none { it.itag == stream.itag } }
        val opus = bestAudio(video, AUDIO_WEBM) { it == OPUS_CODEC }
        val merged = video.adaptive
            .filter { stream -> stream.hasVideo && !stream.hasAudio && stream.codecs.isNotEmpty() }
            .groupBy { it.quality() }
            .mapNotNull { (quality, streams) ->
                when (quality) {
                    in MERGED_QUALITIES -> audio?.let { best(streams, VIDEO_MP4, ::isAvc) }
                    in HIGH_QUALITIES -> opus?.let { best(streams, VIDEO_WEBM, ::isSdrVp9) }
                        ?: audio?.let { best(streams, VIDEO_MP4, ::isAv1) }
                    else -> null
                }
            }
            .sortedByDescending { it.quality() }
        val mergedQualities = merged.mapNotNull { it.quality() }.toSet()
        val files = progressive.filter { it.quality() !in mergedQualities }
        val companions = listOfNotNull(
            audio,
            opus?.takeIf { merged.any { it.mimeType == VIDEO_WEBM } },
        )
        return if (audio == null && merged.isEmpty()) progressive else files + merged + companions
    }

    private fun bestAudio(video: YouTubeVideo, mimeType: String, codec: (String) -> Boolean): YouTubeStream? = video.adaptive
        .filter { stream -> !stream.hasVideo && stream.mimeType == mimeType && !stream.isDrc && stream.codecs.any(codec) }
        .maxWithOrNull(compareBy<YouTubeStream>({ it.isDefaultAudio != false }, { it.bitrate ?: 0L }))

    private fun best(streams: List<YouTubeStream>, mimeType: String, codec: (String) -> Boolean): YouTubeStream? = streams
        .filter { stream -> stream.mimeType == mimeType && stream.codecs.all(codec) }
        .maxByOrNull { it.bitrate ?: 0L }

    private fun isAvc(codec: String): Boolean = codec.startsWith(AVC_CODEC_PREFIX)

    private fun isAv1(codec: String): Boolean = codec.startsWith(AV1_CODEC_PREFIX)

    private fun isSdrVp9(codec: String): Boolean = codec == VP9_CODEC || codec.startsWith(VP9_PROFILE_0_PREFIX)

    private fun YouTubeStream.quality(): Int? =
        qualityLabel?.let(QUALITY_LABEL_NUMBER::find)?.groupValues?.get(1)?.toIntOrNull()
            ?: listOfNotNull(width, height).minOrNull()

    internal const val VIDEO_MP4 = "video/mp4"
    internal const val AUDIO_MP4 = "audio/mp4"
    internal const val VIDEO_WEBM = "video/webm"
    internal const val AUDIO_WEBM = "audio/webm"
    private const val AAC_CODEC_PREFIX = "mp4a."
    private const val AVC_CODEC_PREFIX = "avc1"
    private const val AV1_CODEC_PREFIX = "av01"
    private const val VP9_CODEC = "vp9"
    private const val VP9_PROFILE_0_PREFIX = "vp09.00."
    private const val OPUS_CODEC = "opus"
    private val MERGED_QUALITIES = setOf(144, 240, 360, 480, 720, 1080)
    private val HIGH_QUALITIES = setOf(1440, 2160)
    private val QUALITY_LABEL_NUMBER = Regex("^(\\d{3,4})p")
    private val QUALITY_LABEL = Regex("^[1-9]\\d{2,3}p\\d{0,3}(?: HDR)?$")
    private val AUDIO_QUALITY = Regex("^AUDIO_QUALITY_[A-Z_]{2,32}$")
    private const val MAX_WORD = 64
    private const val MILLIS = 1_000L
    private const val OK = "OK"
}

/** V and the init check on one L row, on the same bytes (guardrails 6 and 9; wired by D6). */
internal fun interface LRowCheck {
    suspend fun check(row: YouTubeStream): StreamCheck.Verdict
}

/**
 * Phase 1.3 D5b step 6: which offered L rows may be shown. Every row needs a [LRowCheck] `pass` in
 * this lookup; `fail` and `unknown` drop it (unknown is never pass). The audio companions are
 * checked first; a merged row is checked only when its companion passed (an MP4 row needs the AAC
 * track, a WebM row the Opus track), so no V is spent on a row that cannot be shown. A row without
 * a direct address (protected: S's work) is not checked here and not shown. At most
 * [ShapeNormalizer.MAX_OFFER] checks. Complete (P22): a merged row and its companion both passed.
 */
internal object LRows {
    class Checked internal constructor(
        val rows: List<YouTubeStream>,
        val complete: Boolean,
        val checks: Int,
        val failed: Int,
        val unknown: Int,
        /** Merged rows not checked because their companion did not pass. */
        val skipped: Int,
        /** Rows without a direct address, left to S. */
        val protectedRows: Int,
    ) {
        override fun toString(): String =
            "LRows.Checked(rows=${rows.size}, complete=$complete, checks=$checks, failed=$failed, unknown=$unknown, " +
                "skipped=$skipped, protected=$protectedRows)"
    }

    suspend fun check(offer: List<YouTubeStream>, check: LRowCheck): Checked {
        val rows = offer.take(ShapeNormalizer.MAX_OFFER)
        val verdicts = arrayOfNulls<StreamCheck.Verdict>(rows.size)
        var checks = 0
        var failed = 0
        var unknown = 0
        var skipped = 0
        var protectedRows = 0

        suspend fun run(index: Int) {
            val row = rows[index]
            if (row.url == null) {
                protectedRows += 1
                return
            }
            checks += 1
            val verdict = try {
                check.check(row)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                StreamCheck.Verdict.UNKNOWN
            }
            verdicts[index] = verdict
            when (verdict) {
                StreamCheck.Verdict.FAIL -> failed += 1
                StreamCheck.Verdict.UNKNOWN -> unknown += 1
                StreamCheck.Verdict.PASS -> Unit
            }
        }

        val aac = rows.indexOfFirst { !it.hasVideo && it.mimeType == ShapeNormalizer.AUDIO_MP4 }
        val opus = rows.indexOfFirst { !it.hasVideo && it.mimeType == ShapeNormalizer.AUDIO_WEBM }
        fun companionOf(row: YouTubeStream): Int = if (row.mimeType == ShapeNormalizer.VIDEO_WEBM) opus else aac
        fun isMerged(row: YouTubeStream): Boolean = row.hasVideo && !row.hasAudio

        for (index in rows.indices) if (!rows[index].hasVideo) run(index)
        for (index in rows.indices) {
            val row = rows[index]
            if (!isMerged(row)) continue
            val companion = companionOf(row)
            if (companion >= 0 && verdicts[companion] == StreamCheck.Verdict.PASS) run(index) else skipped += 1
        }
        for (index in rows.indices) if (rows[index].hasVideo && rows[index].hasAudio) run(index)

        fun passed(index: Int): Boolean = index >= 0 && verdicts[index] == StreamCheck.Verdict.PASS
        val complete = rows.indices.any { index -> isMerged(rows[index]) && passed(index) && passed(companionOf(rows[index])) }
        return Checked(
            rows = rows.filterIndexed { index, _ -> passed(index) },
            complete = complete,
            checks = checks,
            failed = failed,
            unknown = unknown,
            skipped = skipped,
            protectedRows = protectedRows,
        )
    }
}
