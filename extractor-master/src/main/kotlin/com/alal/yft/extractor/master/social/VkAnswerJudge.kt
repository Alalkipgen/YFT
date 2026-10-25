/*
 * Provenance (VK-Master plan §5, Appendix B step 2; copied, not moved; main 50fb8d0f: "# pending"
 * rows in toolkit-provenance.tsv's header, as VkClient's):
 *   extractor-sites/.../vk/VkEmbedParser.kt  NOT_FOUND_MARKER, SHELL_MARKERS, ADULT_CALLBACK,
 *                                         ADULT_WORDS, MAX_MESSAGE_CHARS, SPACES, isAdult,
 *                                         messageOf, decodeEntities, codePoint, ENTITY,
 *                                         NAMED_ENTITIES (the embed's adult answer, its message, its
 *                                         shell)
 *   extractor-sites/.../vk/VkExtractor.kt  TOKEN_ERROR (API error 5: the token refused)
 */
package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import java.util.Locale

/**
 * VK-Master plan §5: what VK's reader ([VkReader]) found in one answer for the link's video:
 * [rows], files bound to the video (`owner_id` + `id`) on VK's video servers ([byShape]: by the
 * shape scan, not VK's key table); [refused], files under VK's keys on other hosts; [item], an
 * entry names the video; [otherVideo], entries name other videos only; [live], a broadcast that is
 * not over; [restricted], VK's `restriction.can_play` 0 for the video, [deleted] with VK's own
 * deleted icon (`delete_outline`, a name, not words).
 */
internal data class VkReading(
    val rows: Int = 0,
    val byShape: Boolean = false,
    val refused: Int = 0,
    val item: Boolean = false,
    val otherVideo: Boolean = false,
    val live: Boolean = false,
    val restricted: Boolean = false,
    val deleted: Boolean = false,
)

/**
 * VK-Master plan §5: VK's judge, for main's answers that the ladder ([VkLadder]) re-reads (V1: the
 * embed pages, main's token ask by its status, main's `video.get`) and for V2's own two answers.
 * Conservative: a final answer only on positive evidence that does not depend on the page's
 * language; an unknown error is [AnswerClass.TRANSIENT], never final.
 *
 * - Rows bound to the video → the media checks (V): [AnswerClass.DIRECT], or
 *   [AnswerClass.PARTIAL] when found by the shape scan or beside files on unknown hosts.
 * - The embed's adult answer (`embedErrorCallback(9)`, or VK's adult text inside `video_ext_msg`)
 *   is main's trigger for path D, not a Master class: [AnswerClass.VIDEO_WITHOUT_FILES] (the embed
 *   lists no file by its own design), the next rung. Its other message ("Video not found.":
 *   deleted, private and list-only alike) → [AnswerClass.NEEDS_PLAYBACK], never final; VK's SPA
 *   shell → [AnswerClass.TRANSIENT].
 * - The token refused (HTTP 400 or 401, an answer without a token) or `video.get`'s errors for
 *   the web client's values ([VALUE_ERRORS]: 5 the token, …) → [AnswerClass.VALUE_REJECTED] (V2).
 * - A captcha (API error 14, the token answer's `captcha_sid`) → [AnswerClass.BOT_CHECK]; a 429 or
 *   VK's rate errors ([RATE_ERRORS]) → [AnswerClass.RATE_LIMITED]: the site stops and cools.
 * - `restriction.can_play` 0 with VK's deleted icon → [AnswerClass.REMOVED] (final); any other
 *   restriction → [AnswerClass.TRANSIENT].
 * - A live broadcast → [AnswerClass.VIDEO_WITHOUT_FILES] (main's live answer stays); not JSON,
 *   another host, another video's entry, no entry, no file or files on unknown hosts →
 *   [AnswerClass.SHAPE_CHANGED].
 *
 * Provisional until A's error-taxonomy recon (§10): [VALUE_ERRORS], [RATE_ERRORS] and the deleted
 * icon. Evidence: a status, a code, a marker's kind; never the page's words, a link or an ID, and
 * none of the words a diagnostic line is dropped for (DiagnosticTextSanitizer: a credential's name).
 * Reads only; no request, no log.
 */
internal object VkAnswerJudge {
    /** API error 14: VK asks for a captcha. */
    const val CAPTCHA_ERROR = 14L

    /** VK's API errors for the web client's values (provisional: A confirms). */
    val VALUE_ERRORS: Set<Long> = setOf(
        TOKEN_ERROR, // User authorization failed: the token
        8L, // Invalid request: as for an API version VK no longer takes
        28L, // Application authorization failed
        34L, // Client version deprecated
        101L, // Invalid application API ID
    )

    /** VK's API errors for too many requests: 6 per second, 9 flood control, 29 rate limit. */
    val RATE_ERRORS: Set<Long> = setOf(6L, 9L, 29L)

    /** VK's icon for a deleted video in `restriction` (provisional: A confirms). */
    const val DELETED_ICON = "delete_outline"

    /** Main's embed answer (B, B again, B2) for the link's video, read into [reading]. */
    fun embed(answer: ExtractorHttpResult, reading: VkReading = VkReading()): Judged {
        val page = when (answer) {
            is ExtractorHttpResult.Failure -> return failure(answer)
            is ExtractorHttpResult.Success -> answer
        }
        if (!embedHost(page.finalUrl)) return shapeChanged("final address: off VK's embed")
        rows(reading)?.let { return it }
        if (reading.otherVideo) return shapeChanged("names another video")
        if (reading.live) return live()
        val html = page.body
        return when {
            isAdult(html) -> Judged(
                AnswerClass.VIDEO_WITHOUT_FILES,
                null,
                if (ADULT_CALLBACK.containsMatchIn(html)) "the embed's adult answer (callback 9)" else "the embed's adult answer",
            )
            NOT_FOUND_MARKER in html -> Judged(
                AnswerClass.NEEDS_PLAYBACK,
                null,
                "the embed's message: deleted, private and list-only alike, not proof",
            )
            SHELL_MARKERS.any { it in html } -> Judged(AnswerClass.TRANSIENT, null, "VK's SPA shell, not an answer")
            reading.refused > 0 -> shapeChanged("files on unknown hosts: ${reading.refused}")
            reading.item -> shapeChanged("an entry, no file under VK's keys")
            else -> shapeChanged("no player data for the video")
        }
    }

    /**
     * login.vk.ru's answer to a token ask: null when it gave a usable token ([usable], VkClient's
     * read), else its class.
     */
    fun token(answer: ExtractorHttpResult, usable: Boolean): Judged? {
        val page = when (answer) {
            is ExtractorHttpResult.Failure -> return when (answer.statusCode) {
                400, 401 -> Judged(
                    AnswerClass.VALUE_REJECTED,
                    AnswerClass.VALUE_REJECTED.reason,
                    "status ${answer.statusCode}, the web client's values refused",
                )
                else -> failure(answer)
            }
            is ExtractorHttpResult.Success -> answer
        }
        if (VkClient.httpsHostOf(page.finalUrl) != VkClient.TOKEN_HOST) return shapeChanged("final address: off login.vk.ru")
        if (usable) return null
        val root = BoundedJsonParser.parse(page.body.trim()) as? JsonValue.Object
            ?: return shapeChanged("not a JSON object")
        return when {
            captcha(root) -> Judged(AnswerClass.BOT_CHECK, AnswerClass.BOT_CHECK.reason, "a captcha")
            root["type"].asStringOrNull == OKAY -> shapeChanged("okay, its value not of VK's shape")
            else -> Judged(
                AnswerClass.VALUE_REJECTED,
                AnswerClass.VALUE_REJECTED.reason,
                "no value for the web client, its values refused",
            )
        }
    }

    /** `video.get`'s answer (main's or V2's) for the link's video, read into [reading]. */
    fun api(answer: ExtractorHttpResult, reading: VkReading = VkReading()): Judged {
        val page = when (answer) {
            is ExtractorHttpResult.Failure -> return failure(answer)
            is ExtractorHttpResult.Success -> answer
        }
        if (VkClient.httpsHostOf(page.finalUrl) != VkClient.API_HOST) return shapeChanged("final address: off VK's API host")
        rows(reading)?.let { return it }
        val root = BoundedJsonParser.parse(page.body.trim()) as? JsonValue.Object
            ?: return shapeChanged("not a JSON object")
        (root["error"] as? JsonValue.Object)?.let { error -> return apiError(error["error_code"].asLongOrNull) }
        return when {
            !reading.item && reading.otherVideo -> shapeChanged("names another video")
            !reading.item -> shapeChanged("no entry for the video")
            reading.restricted && reading.deleted ->
                Judged(AnswerClass.REMOVED, AnswerClass.REMOVED.reason, "restriction: can_play 0, VK's deleted icon")
            reading.restricted -> Judged(AnswerClass.TRANSIENT, null, "restriction: can_play 0, its kind unknown")
            reading.live -> live()
            reading.refused > 0 -> shapeChanged("files on unknown hosts: ${reading.refused}")
            else -> shapeChanged("an entry, no file under VK's keys")
        }
    }

    /** VK's API error [code]: the class its kind names; an unknown one is TRANSIENT. */
    fun apiError(code: Long?): Judged = when (code) {
        null -> Judged(AnswerClass.TRANSIENT, null, "API error, no code")
        CAPTCHA_ERROR -> Judged(AnswerClass.BOT_CHECK, AnswerClass.BOT_CHECK.reason, "API error $code (captcha)")
        in RATE_ERRORS -> Judged(AnswerClass.RATE_LIMITED, AnswerClass.RATE_LIMITED.reason, "API error $code (rate)")
        in VALUE_ERRORS -> Judged(
            AnswerClass.VALUE_REJECTED,
            AnswerClass.VALUE_REJECTED.reason,
            "API error $code (the web client's values)",
        )
        else -> Judged(AnswerClass.TRANSIENT, null, "API error $code")
    }

    /**
     * Main's adult answer (VK5): the embed's `video_ext_msg` holds VK's adult-content text, or the
     * page calls `embedErrorCallback(9)` beside it. Free words elsewhere in the page never count.
     */
    fun isAdult(html: String): Boolean {
        val message = messageOf(html) ?: return false
        return ADULT_CALLBACK.containsMatchIn(html) || ADULT_WORDS.any { it in message }
    }

    /** A token answer's captcha: VK's `captcha_sid`, or its `need_captcha` error (names, not words). */
    fun captcha(root: JsonValue.Object): Boolean =
        root["captcha_sid"] != null || root["error"].asStringOrNull == NEED_CAPTCHA ||
            root["data"]["captcha_sid"] != null

    /** Whether [url] is main's embed host (vk.com, vk.ru or a subdomain) over HTTPS. */
    fun embedHost(url: String): Boolean {
        val host = VkClient.httpsHostOf(url) ?: return false
        return listOf(VkClient.COM_HOST, VkClient.RU_HOST).any { host == it || host.endsWith(".$it") }
    }

    /** HTML's basic entities (`&amp;`, `&quot;`, `&#39;`, `&#x27;`, …) in one pass (main's). */
    fun decodeEntities(text: String): String {
        if ('&' !in text) return text
        return ENTITY.replace(text) { match ->
            val name = match.groupValues[1]
            when {
                name.startsWith("#x") || name.startsWith("#X") ->
                    name.substring(2).toIntOrNull(16)?.let(::codePoint)
                name.startsWith("#") -> name.substring(1).toIntOrNull()?.let(::codePoint)
                else -> NAMED_ENTITIES[name]
            } ?: match.value
        }
    }

    private fun rows(reading: VkReading): Judged? {
        if (reading.rows <= 0) return null
        return when {
            reading.byShape -> Judged(AnswerClass.PARTIAL, null, "rows ${reading.rows} by the shape scan, bound to the video")
            reading.refused > 0 -> Judged(
                AnswerClass.PARTIAL,
                null,
                "rows ${reading.rows}, bound to the video; ${reading.refused} on unknown hosts left out",
            )
            else -> Judged(AnswerClass.DIRECT, null, "rows ${reading.rows}, bound to the video")
        }
    }

    private fun live() = Judged(AnswerClass.VIDEO_WITHOUT_FILES, null, "a live broadcast, not over")

    /** The text of `<div id="video_ext_msg">`, entities decoded, spaces folded, lower case. */
    private fun messageOf(html: String): String? {
        val at = html.indexOf(NOT_FOUND_MARKER).takeIf { it >= 0 } ?: return null
        val open = html.indexOf('>', at).takeIf { it >= 0 } ?: return null
        val close = html.indexOf("</div>", open).takeIf { it >= 0 } ?: return null
        if (close - open > MAX_MESSAGE_CHARS) return null
        return decodeEntities(html.substring(open + 1, close))
            .replace('\u00A0', ' ')
            .replace(SPACES, " ")
            .trim()
            .lowercase(Locale.ROOT)
    }

    private fun codePoint(value: Int): String? =
        if (value in 1..0x10FFFF && value !in 0xD800..0xDFFF) {
            String(Character.toChars(value))
        } else {
            null
        }

    private fun failure(answer: ExtractorHttpResult.Failure): Judged = when {
        answer.statusCode == HTTP_TOO_MANY || answer.reason == SiteExtractionFailure.RATE_LIMITED ->
            Judged(AnswerClass.RATE_LIMITED, AnswerClass.RATE_LIMITED.reason, "status 429")
        answer.reason == SiteExtractionFailure.BOT_CHECK ->
            Judged(AnswerClass.BOT_CHECK, AnswerClass.BOT_CHECK.reason, "the client: bot check")
        else -> Judged(
            AnswerClass.TRANSIENT,
            answer.reason,
            "failed: ${answer.reason}" + (answer.statusCode?.let { " $it" } ?: ""),
        )
    }

    private fun shapeChanged(evidence: String) =
        Judged(AnswerClass.SHAPE_CHANGED, AnswerClass.SHAPE_CHANGED.reason, evidence)

    /** VK's API error for a token it does not take ("User authorization failed"). */
    private const val TOKEN_ERROR = 5L
    private const val HTTP_TOO_MANY = 429
    private const val OKAY = "okay"
    private const val NEED_CAPTCHA = "need_captcha"
    private const val NOT_FOUND_MARKER = "id=\"video_ext_msg\""
    private val SHELL_MARKERS = listOf("spa_root", "isRedirectChallenge")

    /** VK5: the embed's error callback for adult content (VK's "Video not found." calls 7). */
    private val ADULT_CALLBACK = Regex("embedErrorCallback\\s*(?:\\?\\.)?\\s*\\(\\s*9\\s*\\)")

    /** VK5: VK's adult-content text inside `video_ext_msg`, in English and Russian. */
    private val ADULT_WORDS = listOf("adult content", "для взрослых")
    private const val MAX_MESSAGE_CHARS = 2_000
    private val SPACES = Regex("\\s+")
    private val ENTITY = Regex("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z]{2,6});")
    private val NAMED_ENTITIES = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " ",
    )
}
