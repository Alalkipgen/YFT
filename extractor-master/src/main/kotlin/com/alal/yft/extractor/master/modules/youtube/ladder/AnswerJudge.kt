package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.api.json.path
import com.alal.yft.extractor.master.modules.youtube.YouTubeClientProfile
import com.alal.yft.extractor.master.modules.youtube.YouTubeParseResult
import com.alal.yft.extractor.master.modules.youtube.YouTubePlayerResponse
import com.alal.yft.extractor.master.modules.youtube.YouTubePlayerResponseParser
import com.alal.yft.extractor.master.modules.youtube.YouTubeStream
import com.alal.yft.extractor.master.modules.youtube.YouTubeUrls
import com.alal.yft.extractor.master.modules.youtube.YouTubeVideo
import java.net.URLDecoder

/**
 * Phase 1.3 D2 — the answer judge (MASTER_KEY_PHASE1_3_PLAN.md §4 D2): one [CensusClass] for one
 * player answer, from the copied parser's verdict and summary, the HTTP result and ShapeLocator's
 * value-shape scan, plus [Verdict.hls] (an HLS address on a `googlevideo.com` host, from the field
 * or L's locate) and the `SHAPE_CHANGED` kind ([ShapeKind]). The census classes every answer with
 * it; D3's ladder will.
 *
 * An answer the copied parser refuses, first match:
 * 1. `BOT_CHECK`: YouTube's bot-check words anywhere in the verdict's text, or the copied parser's
 *    bot check;
 * 2. `VERSION_REJECTED`: YouTube asking for a newer app;
 * 3. `AGE_CHECK`: the copied parser's age check;
 * 4. `REFUSED`: "not available on this app";
 * 5. a definite verdict → `VIDEO_VERDICT`, except **V7** (owner, 2026-10-15): an `UNPLAYABLE` or
 *    `ERROR` screen the copied parser reads as private or unavailable whose text names none of
 *    [DEFINITE_MARKERS] (age, sign-in, membership, purchase, private, removed, copyright, region,
 *    DRM, live) is one client's refusal → `REFUSED`, not terminal: the O rungs may go on, and when
 *    they do not complete R6's verdict stays (video 6's visionOS "This video is not available";
 *    the embedded player's error 152-18);
 * 6. `LOGIN_REQUIRED` → `VIDEO_VERDICT` (a sign-in only the user's own session can give);
 * 7. an unreadable answer → `REFUSED`;
 * 8. DRM markers beside located formats → `VIDEO_VERDICT`;
 * 9. a shape change → `SHAPE_CHANGED` with its kind;
 * 10. SABR only → `SABR_ONLY`; 11. no status and no format list → `SHAPE_CHANGED`; 12. `REFUSED`
 *     (e.g. `OK` without streaming data).
 *
 * An answer the copied parser reads: a shape change → `SHAPE_CHANGED` (with DRM markers:
 * `VIDEO_VERDICT`); then v1's classes `NEEDS_SCRIPT`, `SABR_ONLY`, `INCOMPLETE`, `DIRECT`. HTTP 400
 * → `VERSION_REJECTED`, another HTTP failure → `REFUSED` ([ofFailure]); a 403 on the checked row →
 * `TOKEN_NEEDED` ([afterV]). The scan counts only when complete. No request and no log; text is
 * matched, never kept.
 */
internal object AnswerJudge {
    /** How the shape changed: formats outside the copied parser's lists, or their keys renamed. */
    enum class ShapeKind { MOVED, PARTIAL }

    class Verdict internal constructor(
        val answerClass: CensusClass,
        val shapeKind: ShapeKind?,
        /** The answer's HLS address on a `googlevideo.com` host (field or located), or null. */
        val hls: String?,
        val response: YouTubePlayerResponse?,
        val root: JsonValue?,
        val scan: ShapeLocator.Scan?,
    ) {
        /** A verdict about the video (age, sign-in, private, removed, region, DRM, live): stop. */
        val terminal: Boolean get() = answerClass.terminal

        /** The copied parser's reading, when it read one. */
        val video: YouTubeVideo? get() = (response?.result as? YouTubeParseResult.Success)?.video

        override fun toString(): String = "AnswerJudge.Verdict($answerClass, shape=$shapeKind, hls=${hls != null})"
    }

    /** An HTTP failure: 400 is the client's version, anything else a refusal of this request. */
    fun ofFailure(failure: ExtractorHttpResult.Failure): Verdict = Verdict(
        answerClass = if (failure.statusCode == HTTP_BAD_REQUEST) CensusClass.VERSION_REJECTED else CensusClass.REFUSED,
        shapeKind = null,
        hls = null,
        response = null,
        root = null,
        scan = null,
    )

    /** Judges [body], the answer [profile] got for [videoId]. */
    fun judge(profile: YouTubeClientProfile, body: String, videoId: String, nowEpochMs: Long): Verdict =
        judge(profile, YouTubePlayerResponseParser.inspect(body, nowEpochMs), ShapeLocator.parse(body), body, videoId)

    /** Judges an answer already read: [response] by the copied parser, [root] its tree, [body] its text. */
    fun judge(
        profile: YouTubeClientProfile,
        response: YouTubePlayerResponse,
        root: JsonValue?,
        body: String?,
        videoId: String,
    ): Verdict {
        val scan = root?.let { ShapeLocator.scan(it, videoId, body) }?.takeIf(ShapeLocator.Scan::complete)
        val (answerClass, kind) = when (val result = response.result) {
            is YouTubeParseResult.Failure -> refused(result, response, root, scan)
            is YouTubeParseResult.Success -> read(profile, result.video, response, root, scan)
        }
        return Verdict(answerClass, kind, HlsLeg.address(root, scan?.hls), response, root, scan)
    }

    /** The class after V: a 403 on the checked row means YouTube wants a token for it. */
    fun afterV(answerClass: CensusClass, targetForbidden: Boolean): CensusClass =
        if (targetForbidden) CensusClass.TOKEN_NEEDED else answerClass

    // ---- the copied parser refused -----------------------------------------------------------

    private fun refused(
        result: YouTubeParseResult.Failure,
        response: YouTubePlayerResponse,
        root: JsonValue?,
        scan: ShapeLocator.Scan?,
    ): Pair<CensusClass, ShapeKind?> {
        val status = root.path("playabilityStatus", "status").asStringOrNull
        val text = reasonText(root["playabilityStatus"])
        val summary = response.summary
        val answerClass = when {
            result.reason == SiteExtractionFailure.BOT_CHECK || BOT_WORDS.containsMatchIn(text) -> CensusClass.BOT_CHECK
            VERSION_REFUSAL.containsMatchIn(text) -> CensusClass.VERSION_REJECTED
            result.ageCheck -> CensusClass.AGE_CHECK
            APP_REFUSAL.containsMatchIn(text) -> CensusClass.REFUSED
            result.definite -> if (oneClientRefusal(result, status, text)) CensusClass.REFUSED else CensusClass.VIDEO_VERDICT
            status == LOGIN_REQUIRED -> CensusClass.VIDEO_VERDICT
            !summary.readable -> CensusClass.REFUSED
            scan != null && scan.drm && scan.candidates.isNotEmpty() -> CensusClass.VIDEO_VERDICT
            else -> null
        }
        if (answerClass != null) return answerClass to null
        shapeKind(root, scan, video = null)?.let { return CensusClass.SHAPE_CHANGED to it }
        return when {
            summary.sabrOnly || result.reason == SiteExtractionFailure.NO_MEDIA_FOUND -> CensusClass.SABR_ONLY
            result.reason == SiteExtractionFailure.RESPONSE_CHANGED -> CensusClass.SHAPE_CHANGED
            else -> CensusClass.REFUSED
        } to null
    }

    /** V7: an error screen of one client, not a verdict about the video (no marker in its text). */
    private fun oneClientRefusal(result: YouTubeParseResult.Failure, status: String?, text: String): Boolean =
        (status == UNPLAYABLE || status == ERROR) &&
            result.reason == SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE &&
            !DEFINITE_MARKERS.containsMatchIn(text)

    // ---- the copied parser read it -------------------------------------------------------------

    private fun read(
        profile: YouTubeClientProfile,
        video: YouTubeVideo,
        response: YouTubePlayerResponse,
        root: JsonValue?,
        scan: ShapeLocator.Scan?,
    ): Pair<CensusClass, ShapeKind?> {
        shapeKind(root, scan, video)?.let { kind ->
            return if (scan?.drm == true) CensusClass.VIDEO_VERDICT to null else CensusClass.SHAPE_CHANGED to kind
        }
        val streams = video.progressive + video.adaptive
        val needsScript = streams.any(YouTubeStream::isProtected) ||
            (profile.usesPlayerScript && streams.any { stream -> stream.url?.let(::rateInput) != null })
        return when {
            needsScript -> CensusClass.NEEDS_SCRIPT
            video.adaptive.isEmpty() && response.summary.sabr -> CensusClass.SABR_ONLY
            video.unaddressedFormats > 0 ||
                streams.any { (it.contentLengthBytes ?: 0L) <= 0L } ||
                video.title == null || video.durationMillis == null -> CensusClass.INCOMPLETE
            else -> CensusClass.DIRECT
        } to null
    }

    /**
     * The shape change the scan shows, or null: formats with renamed keys in the copied parser's
     * lists, or this video's length the copied parser could not read (L6) → [ShapeKind.PARTIAL];
     * formats outside its lists, the status object or the HLS address elsewhere, while the copied
     * parser read no adaptive video and audio pair → [ShapeKind.MOVED].
     */
    private fun shapeKind(root: JsonValue?, scan: ShapeLocator.Scan?, video: YouTubeVideo?): ShapeKind? {
        scan ?: return null
        val pair = video != null && video.adaptive.any { it.hasVideo && !it.hasAudio } &&
            video.adaptive.any { it.hasAudio && !it.hasVideo }
        return when {
            scan.partial -> ShapeKind.PARTIAL
            scan.moved && !pair -> ShapeKind.MOVED
            video != null && video.durationMillis == null && scan.videos.isNotEmpty() -> ShapeKind.PARTIAL
            root.path("playabilityStatus", "status") == null && scan.statuses.isNotEmpty() -> ShapeKind.MOVED
            root.path("streamingData", "hlsManifestUrl") == null && scan.hls != null && !pair -> ShapeKind.MOVED
            else -> null
        }
    }

    // ---- the verdict's words -----------------------------------------------------------------

    /** The copied parser's reason text (its keys, depth and part bounds), markup removed, bounded. */
    internal fun reasonText(status: JsonValue?): String {
        val parts = ArrayList<String>()
        collect(status, key = null, depth = 0, into = parts)
        return parts.joinToString(" ").replace(MARKUP_TAG, " ").replace(WHITESPACE, " ").trim().take(MAX_TEXT_CHARS)
    }

    private fun collect(value: JsonValue?, key: String?, depth: Int, into: MutableList<String>) {
        if (depth > MAX_TEXT_DEPTH || into.size >= MAX_TEXT_PARTS) return
        when (value) {
            is JsonValue.Text -> if (key in TEXT_KEYS) into += value.value
            is JsonValue.Array -> value.items.forEach { collect(it, key, depth + 1, into) }
            is JsonValue.Object -> value.entries.forEach { (childKey, child) -> collect(child, childKey, depth + 1, into) }
            else -> Unit
        }
    }

    private fun rateInput(url: String): String? = YouTubeUrls.queryParamOf(url, RATE_PARAM)
        ?.let { runCatching { URLDecoder.decode(it, Charsets.UTF_8.name()) }.getOrNull() }
        ?.takeIf(String::isNotBlank)

    private const val HTTP_BAD_REQUEST = 400
    private const val LOGIN_REQUIRED = "LOGIN_REQUIRED"
    private const val UNPLAYABLE = "UNPLAYABLE"
    private const val ERROR = "ERROR"
    private const val RATE_PARAM = "n"
    private const val MAX_TEXT_DEPTH = 8
    private const val MAX_TEXT_PARTS = 32
    private const val MAX_TEXT_CHARS = 2_000

    private val TEXT_KEYS = setOf("reason", "subreason", "messages", "simpleText", "text")
    private val MARKUP_TAG = Regex("<[^<>]{0,400}>")
    private val WHITESPACE = Regex("\\s+")

    /** YouTube words its bot check as a sign-in prompt; either apostrophe may appear. */
    private val BOT_WORDS = Regex("confirm\\s+you['\u2019]re\\s+not\\s+a\\s+bot", RegexOption.IGNORE_CASE)

    /** YouTube asking for a newer app: the version, not the video. */
    internal val VERSION_REFUSAL = Regex(
        "(?i)(update (the|your|to the latest)|no longer supported|" +
            "(app|client|version) (is )?(not|no longer) supported|unsupported (app|client|version))",
    )

    /** A refusal of this app rather than a verdict about the video. */
    internal val APP_REFUSAL = Regex("(?i)(not available on this app|isn['\u2019]t available on this app)")

    /** Words that make an error screen a verdict about the video itself (V7). */
    internal val DEFINITE_MARKERS = Regex(
        "(?i)(\\bage\\b|inappropriate for some users|\\bsign(ed)?[ -]?in\\b|\\blog(ged)?[ -]?in\\b|" +
            "\\bmembers?\\b|members-only|\\bmembership\\b|\\bpremium\\b|\\bpurchas\\w*|\\bpayment\\b|\\brent(al|ed)?\\b|" +
            "\\bprivate\\b|\\bremoved\\b|\\bdeleted\\b|\\bterminated\\b|no longer available|available any ?more|" +
            "\\bcopyright\\w*|\\bviolat\\w*|\\bcountry\\b|\\bcountries\\b|\\bregion\\b|\\bdrm\\b|\\bprotected\\b|" +
            "\\blicen[cs]\\w*|\\blive\\b|\\bpremiere\\w*)",
    )
}
