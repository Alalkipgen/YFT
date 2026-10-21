/*
 * Provenance (social plan S2c, copied, not moved; main 34a41890; listed in toolkit-provenance.tsv):
 *   extractor-sites/.../x/XSyndication.kt  HOST (the widget's answer host), TOMBSTONE, parse (an
 *                                        empty answer and a tombstone are no post: here judged
 *                                        by their shape)
 *   extractor-sites/.../x/XExtractor.kt  HTTP_NOT_FOUND (main's 404: no post to show)
 */
package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonReadResult
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import java.net.URI

/**
 * Social plan S2c: what X's ladder ([XLadder]) read in main's answer for the link's post: [rows],
 * the rows of the link's item bound to the post ID ([byShape]: main's whole answer read by X's
 * recipe, its rows one item's); [shapeItems], the items such rows name when not one (1: rows that
 * name no item); [otherPost], the answer names another post; [photos], the post has photos and no
 * video; [playlistOnly], the link's item has only a playlist and no length.
 */
internal data class XReading(
    val rows: Int = 0,
    val byShape: Boolean = false,
    val shapeItems: Int = 0,
    val otherPost: Boolean = false,
    val photos: Boolean = false,
    val playlistOnly: Boolean = false,
)

/**
 * Social plan S2c (`MASTER_KEY_SOCIAL_PLAN.md` §6.4, §7.3): X's judge, for main's answer from X's
 * embedded-post widget (`tweet-result`, asked with no X cookie) that X's ladder replays with no new
 * ask. Conservative, by the answer's shape only: the words are no evidence, in any language (live
 * shape check, 2026-10-21, signed out).
 *
 * - Rows of the link's item bound to the post ID → the media checks (V, S6); rows found by shape
 *   (a renamed key) only when they are one item's.
 * - A 404 from the widget's host → REMOVED (final): the widget answers so for an ID with no post
 *   (a deleted post's answer is a tombstone).
 * - Photos and no video → PHOTO_ONLY (final): main's own answer.
 * - An empty tombstone (`"tombstone":{}`, the age-limited post's) → AGE_LIMITED: the browser.
 * - A tombstone with X's notice link (`help.x.com/…/notices-on-x`): a deleted post's and a
 *   protected account's post's answers have the same shape and link, only their words differ, so
 *   one signed-out answer cannot tell them apart → NEEDS_PLAYBACK, never final (today's reason
 *   stays); another tombstone so too.
 * - `{}` → VALUE_REJECTED: X answers so without the widget's token (a wrong token still gets the
 *   whole answer), so it says nothing about the post. Its line says "check value": the Details
 *   lines drop every line with the word token.
 * - A 429 → the site stops; another status or no answer → TRANSIENT; another host, another
 *   post's answer and an unknown shape → SHAPE_CHANGED.
 *
 * Reads only; no request, no log.
 */
internal object XAnswerJudge {
    fun judge(answer: ExtractorHttpResult, reading: XReading = XReading()): Judged {
        val page = when (answer) {
            is ExtractorHttpResult.Failure -> return failure(answer)
            is ExtractorHttpResult.Success -> answer
        }
        if (!widgetHost(page.finalUrl)) return shapeChanged("final address: off the widget's host")
        if (reading.otherPost) return shapeChanged("names another post")
        if (reading.rows > 0) {
            return if (reading.byShape) {
                Judged(AnswerClass.PARTIAL, null, "rows ${reading.rows}, main's whole answer read, one item")
            } else {
                Judged(AnswerClass.DIRECT, null, "rows ${reading.rows}, bound to the post ID")
            }
        }
        val root = (BoundedJsonParser.read(page.body.trim()) as? JsonReadResult.Read)?.value
        if (root !is JsonValue.Object) return shapeChanged("not a JSON object")
        if (root.entries.isEmpty()) {
            return Judged(
                AnswerClass.VALUE_REJECTED,
                AnswerClass.VALUE_REJECTED.reason,
                "an empty answer, as without the widget's check value",
            )
        }
        if (tombstone(root)) {
            val stone = root["tombstone"]
            return when {
                stone is JsonValue.Object && stone.entries.isEmpty() ->
                    Judged(AnswerClass.AGE_LIMITED, AnswerClass.AGE_LIMITED.reason, "an empty tombstone")
                stone != null && notice(stone) -> Judged(
                    AnswerClass.NEEDS_PLAYBACK,
                    null,
                    "a tombstone with X's notice: removed, protected or suspended alike",
                )
                else -> Judged(AnswerClass.NEEDS_PLAYBACK, null, "a tombstone without X's notice")
            }
        }
        if (reading.photos) {
            return Judged(AnswerClass.PHOTO_ONLY, AnswerClass.PHOTO_ONLY.reason, "photos only, no video")
        }
        if (reading.playlistOnly) {
            return Judged(AnswerClass.NEEDS_PLAYBACK, null, "the item's playlist only, no length")
        }
        if (reading.shapeItems > 1) return shapeChanged("rows by shape for ${reading.shapeItems} items, not one")
        if (reading.shapeItems == 1) return shapeChanged("rows by shape, no item named")
        return shapeChanged("no video; no tombstone")
    }

    /** Main's tombstone: X's `TweetTombstone` type or a `tombstone` field (main's `parse`). */
    fun tombstone(root: JsonValue): Boolean =
        root["__typename"].asStringOrNull == TOMBSTONE || root["tombstone"] != null

    /**
     * Whether [stone] links X's notice page (`help.x.com/…/notices-on-x`, earlier
     * `help.twitter.com/…/notices-on-twitter`), anywhere within a few levels: the link, never the
     * words.
     */
    fun notice(stone: JsonValue): Boolean {
        var seen = 0
        fun walk(value: JsonValue, depth: Int): Boolean {
            if (++seen > MAX_NODES || depth > MAX_DEPTH) return false
            return when (value) {
                is JsonValue.Text -> noticeLink(value.value)
                is JsonValue.Array -> value.items.any { walk(it, depth + 1) }
                is JsonValue.Object -> value.entries.values.any { walk(it, depth + 1) }
                else -> false
            }
        }
        return walk(stone, 1)
    }

    /** Whether [url] is the widget's answer host (main's `XSyndication.HOST`), over HTTPS. */
    fun widgetHost(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        return uri.scheme?.lowercase() == "https" && uri.host?.lowercase()?.trimEnd('.') == HOST
    }

    private fun noticeLink(text: String): Boolean {
        if (text.length > MAX_LINK_CHARS) return false
        val uri = runCatching { URI(text.trim()) }.getOrNull() ?: return false
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        return host in HELP_HOSTS && uri.rawPath.orEmpty().lowercase().contains(NOTICE_PATH)
    }

    private fun failure(answer: ExtractorHttpResult.Failure): Judged = when {
        answer.statusCode == 429 || answer.reason == SiteExtractionFailure.RATE_LIMITED ->
            Judged(AnswerClass.RATE_LIMITED, AnswerClass.RATE_LIMITED.reason, "status 429")
        answer.statusCode == HTTP_NOT_FOUND ->
            Judged(AnswerClass.REMOVED, AnswerClass.REMOVED.reason, "status 404: no such post")
        else -> Judged(
            AnswerClass.TRANSIENT,
            answer.reason,
            "failed: ${answer.reason}" + (answer.statusCode?.let { " $it" } ?: ""),
        )
    }

    private fun shapeChanged(evidence: String) =
        Judged(AnswerClass.SHAPE_CHANGED, AnswerClass.SHAPE_CHANGED.reason, evidence)

    const val HOST = "cdn.syndication.twimg.com"
    private const val TOMBSTONE = "TweetTombstone"
    private const val HTTP_NOT_FOUND = 404
    private val HELP_HOSTS = setOf("help.x.com", "help.twitter.com")
    private const val NOTICE_PATH = "/notices-on-"
    private const val MAX_DEPTH = 8
    private const val MAX_NODES = 256
    private const val MAX_LINK_CHARS = 512
}
