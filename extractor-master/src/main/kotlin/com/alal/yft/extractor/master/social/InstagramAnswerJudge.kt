/*
 * Provenance (social plan S2d part 1, copied, not moved; main 34a41890; listed in toolkit-provenance.tsv):
 *   extractor-sites/.../instagram/InstagramExtractor.kt  LOGIN_STATUSES (401 and 403: a login
 *                                        wall), LOGIN_REQUIRED_MESSAGE (`require_login`, else the
 *                                        message `login_required`: a login wall)
 *   extractor-sites/.../instagram/InstagramUrls.kt  isLoginWall (the login and checkpoint paths:
 *                                        both a sign-in wall, as main and §6.3)
 */
package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonReadResult
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asBooleanOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import java.net.URI
import java.util.Locale

/**
 * Social plan S2d part 1: what Instagram's ladder ([InstagramLadder]) read in one of main's
 * answers for the link's post: [rows], the rows of the link's item read by Instagram's recipe
 * from the post's own node ([byShape]: the recipe's key table read nothing, its open read found
 * them, anchored to the post); [carousel], the answer holds a carousel's items; [photos], the
 * post's node (every item) is marked a photo; [post], the answer names the link's post (its code);
 * [otherPost], a JSON answer's post is another one; [videoTyped], the post (or an item) is a video
 * by its type, files or not.
 */
internal data class InstagramReading(
    val rows: Int = 0,
    val byShape: Boolean = false,
    val carousel: Boolean = false,
    val photos: Boolean = false,
    val post: Boolean = false,
    val otherPost: Boolean = false,
    val videoTyped: Boolean = false,
)

/**
 * Social plan S2d part 1 (`MASTER_KEY_SOCIAL_PLAN.md` §6.4, §7.3, decision 19): Instagram's judge,
 * for main's own answers in this lookup (the app API when signed in, GraphQL, the post's page, the
 * embed page), which I2 reads with no new ask. Conservative, by status, address and shape; one
 * rate-limit wording (`Please wait a few minutes`, main asks in English) is the only words read.
 *
 * - Rows of the link's item, from the post's own node → the media checks (V, S6); rows by shape
 *   (a renamed key) only for a post with one item (a carousel's: its item unknown).
 * - A 429 or the rate-limit answer → RATE_LIMITED (the site stops, the host cools); main's own
 *   bot check → BOT_CHECK, so too.
 * - 401, 403, the login or checkpoint address, a login-required or checkpoint answer → LOGIN_WALL
 *   (main's rule, `isLoginWall`, and §6.3's: "`require_login` or a checkpoint → `LOGIN_WALL`"): the
 *   browser, where the user's own session answers it; no stop.
 * - Photos and no video by the post's own marks (every item) → PHOTO_ONLY (final): main's words.
 * - GraphQL's or the app API's 400 or `errors` → VALUE_REJECTED: the ask's values, not the post.
 * - No post in a JSON answer (`null`), the embed's `EmbedIsBroken` and a page without the post's
 *   data → NEEDS_PLAYBACK: removed, private, gated or not embeddable alike (signed out these look
 *   the same: the census's removed post answered 401 and no data); never final. **REMOVED is never
 *   judged** in part 1: none of these answers tells a removed post apart (decision 19 (vi): the
 *   judge's call from what was read, and nothing read tells it yet).
 * - The embed page's video without files (signed out, by its design; task 13, observation (d))
 *   → VIDEO_WITHOUT_FILES: the tab-fetch rung's (decision 24), never a shape change.
 * - Another host, another post, a video without files read elsewhere, not JSON → SHAPE_CHANGED;
 *   another status or no answer → TRANSIENT.
 * - Task 13: the tab-fetch rung's answer ([SiteWays.TAB_FETCH], a JSON way) by the same rules:
 *   its 400 or `errors` → VALUE_REJECTED (the query's values; the tab's own playback next, never
 *   a guessed value), no post (`null`: gated or gone alike) → NEEDS_PLAYBACK.
 *
 * Reads only; no request, no log.
 */
internal object InstagramAnswerJudge {
    /** [answer], main's answer from [way] (a [SiteWays] label), judged with what the ladder [reading] found. */
    fun judge(way: String, answer: ExtractorHttpResult, reading: InstagramReading = InstagramReading()): Judged {
        val page = when (answer) {
            is ExtractorHttpResult.Failure -> return failure(way, answer)
            is ExtractorHttpResult.Success -> answer
        }
        val uri = runCatching { URI(page.finalUrl.trim()) }.getOrNull()
        if (uri == null || !instagramHost(uri)) return shapeChanged("final address: off Instagram's host")
        val path = uri.path.orEmpty().lowercase(Locale.US)
        if (path.startsWith(LOGIN_PATH)) {
            return Judged(AnswerClass.LOGIN_WALL, AnswerClass.LOGIN_WALL.reason, "final address: login")
        }
        if (path.startsWith(CHECKPOINT_PATH)) {
            return Judged(AnswerClass.LOGIN_WALL, AnswerClass.LOGIN_WALL.reason, "final address: checkpoint")
        }
        if (reading.otherPost) return shapeChanged("names another post")
        if (reading.rows > 0) {
            return when {
                !reading.byShape ->
                    Judged(AnswerClass.DIRECT, null, "rows ${reading.rows} of the link's item, bound to the post")
                !reading.carousel ->
                    Judged(AnswerClass.PARTIAL, null, "rows ${reading.rows} by shape (a renamed key), one item")
                else -> shapeChanged("rows by shape in a carousel: the link's item unknown")
            }
        }
        return if (way in JSON_WAYS) jsonAnswer(page, reading) else pageAnswer(way, page, reading)
    }

    /**
     * I2's answer from main's judged answers [judged] (in main's order): the first with rows bound
     * to the post (DIRECT), else the first by shape (PARTIAL); else a photo post only when no
     * answer shows a video ([readings]: a video by its type, or rows); else the strongest stop, in
     * this order: RATE_LIMITED, BOT_CHECK, AGE_LIMITED, LOGIN_WALL, VIDEO_WITHOUT_FILES (task 13),
     * NEEDS_PLAYBACK, VALUE_REJECTED, SHAPE_CHANGED, TRANSIENT. Null for none.
     */
    fun aggregate(judged: List<Pair<String, Judged>>, readings: List<InstagramReading>): Pair<String, Judged>? {
        if (judged.isEmpty()) return null
        judged.firstOrNull { it.second.answerClass == AnswerClass.DIRECT }?.let { return it }
        judged.firstOrNull { it.second.answerClass == AnswerClass.PARTIAL }?.let { return it }
        judged.firstOrNull { it.second.answerClass == AnswerClass.PHOTO_ONLY }?.let { photo ->
            if (readings.none { it.videoTyped || it.rows > 0 }) return photo
            return photo.first to shapeChanged("the answers disagree: photos in one, a video in another")
        }
        for (answerClass in STOPS) {
            judged.firstOrNull { it.second.answerClass == answerClass }?.let { return it }
        }
        return judged.first()
    }

    /** Whether [body] holds a carousel's items (the app API's, GraphQL's or a page's). */
    fun carousel(body: String): Boolean = CAROUSEL.any { body.contains(it) } || CAROUSEL_TYPE.containsMatchIn(body)

    /** Whether [uri] is on Instagram's own host (`instagram.com`, `www.`, `m.`), over HTTPS. */
    fun instagramHost(uri: URI): Boolean =
        uri.scheme?.lowercase(Locale.US) == "https" && uri.host?.lowercase(Locale.US)?.trimEnd('.') in HOSTS

    private fun jsonAnswer(page: ExtractorHttpResult.Success, reading: InstagramReading): Judged {
        val root = (BoundedJsonParser.read(page.body.trim()) as? JsonReadResult.Read)?.value
        if (root !is JsonValue.Object) return shapeChanged("not a JSON object")
        val message = root["message"].asStringOrNull
        // The rate-limit answer also says require_login: it is a stop first.
        if (message != null && RATE_WORDS.containsMatchIn(message)) {
            return Judged(AnswerClass.RATE_LIMITED, AnswerClass.RATE_LIMITED.reason, "a rate-limit answer")
        }
        if (message == CHECKPOINT_MESSAGE) {
            return Judged(AnswerClass.LOGIN_WALL, AnswerClass.LOGIN_WALL.reason, "the answer: checkpoint")
        }
        if (root["require_login"].asBooleanOrNull == true || message == LOGIN_REQUIRED_MESSAGE) {
            return Judged(AnswerClass.LOGIN_WALL, AnswerClass.LOGIN_WALL.reason, "the answer: login required")
        }
        if (reading.photos && !reading.videoTyped) {
            return Judged(AnswerClass.PHOTO_ONLY, AnswerClass.PHOTO_ONLY.reason, "photos only, no video")
        }
        if (root["errors"].asArrayOrEmpty.isNotEmpty()) {
            return Judged(AnswerClass.VALUE_REJECTED, AnswerClass.VALUE_REJECTED.reason, "the answer: errors")
        }
        if (!reading.post) {
            return Judged(AnswerClass.NEEDS_PLAYBACK, null, "no post in the answer: removed, private or gated alike")
        }
        return shapeChanged(if (reading.videoTyped) "a video, no files read" else "no video read")
    }

    private fun pageAnswer(way: String, page: ExtractorHttpResult.Success, reading: InstagramReading): Judged = when {
        reading.photos && !reading.videoTyped ->
            Judged(AnswerClass.PHOTO_ONLY, AnswerClass.PHOTO_ONLY.reason, "photos only, no video")
        page.body.contains(EMBED_BROKEN) ->
            Judged(AnswerClass.NEEDS_PLAYBACK, null, "not embeddable: removed, private or not embeddable alike")
        // Task 13 (observation d): the embed page signed out names the video and lists no file by
        // its design (main reads it so too): the tab-fetch rung's, not a shape change.
        reading.videoTyped && way == SiteWays.EMBED_PAGE ->
            Judged(AnswerClass.VIDEO_WITHOUT_FILES, null, "a video, no files read")
        reading.videoTyped -> shapeChanged("a video, no files read")
        reading.post -> shapeChanged("no video read")
        else -> Judged(AnswerClass.NEEDS_PLAYBACK, null, "no post data in the page")
    }

    private fun failure(way: String, answer: ExtractorHttpResult.Failure): Judged = when {
        answer.statusCode == HTTP_TOO_MANY || answer.reason == SiteExtractionFailure.RATE_LIMITED ->
            Judged(AnswerClass.RATE_LIMITED, AnswerClass.RATE_LIMITED.reason, "status 429")
        answer.reason == SiteExtractionFailure.BOT_CHECK ->
            Judged(AnswerClass.BOT_CHECK, AnswerClass.BOT_CHECK.reason, "a bot check")
        answer.statusCode in LOGIN_STATUSES ->
            Judged(AnswerClass.LOGIN_WALL, AnswerClass.LOGIN_WALL.reason, "status ${answer.statusCode}")
        answer.statusCode == HTTP_BAD_REQUEST && way in JSON_WAYS ->
            Judged(AnswerClass.VALUE_REJECTED, AnswerClass.VALUE_REJECTED.reason, "status 400")
        else -> Judged(
            AnswerClass.TRANSIENT,
            answer.reason,
            "failed: ${answer.reason}" + (answer.statusCode?.let { " $it" } ?: ""),
        )
    }

    private fun shapeChanged(evidence: String) =
        Judged(AnswerClass.SHAPE_CHANGED, AnswerClass.SHAPE_CHANGED.reason, evidence)

    /** Main's JSON ways (their answers are JSON documents); the others are pages. */
    val JSON_WAYS: Set<String> = setOf(SiteWays.APP_API, SiteWays.GRAPHQL, SiteWays.TAB_FETCH)

    private val HOSTS = setOf("instagram.com", "www.instagram.com", "m.instagram.com")
    private val LOGIN_STATUSES = setOf(401, 403)
    private const val LOGIN_REQUIRED_MESSAGE = "login_required"
    private const val CHECKPOINT_MESSAGE = "checkpoint_required"
    private const val LOGIN_PATH = "/accounts/login"
    private const val CHECKPOINT_PATH = "/challenge"
    private const val EMBED_BROKEN = "EmbedIsBroken"
    private const val HTTP_TOO_MANY = 429
    private const val HTTP_BAD_REQUEST = 400
    private val RATE_WORDS = Regex("(?i)wait a few minutes")
    private val CAROUSEL = listOf(
        "\"carousel_media\"", "\"edge_sidecar_to_children\"", "\"carousel_media_count\"", "GraphSidecar",
        "\\\"carousel_media\\\"", "\\\"edge_sidecar_to_children\\\"", "\\\"carousel_media_count\\\"",
    )
    private val CAROUSEL_TYPE = Regex("""\\?"media_type\\?"\s*:\s*8\b""")
    private val STOPS = listOf(
        AnswerClass.RATE_LIMITED, AnswerClass.BOT_CHECK, AnswerClass.AGE_LIMITED, AnswerClass.LOGIN_WALL,
        AnswerClass.VIDEO_WITHOUT_FILES, AnswerClass.NEEDS_PLAYBACK, AnswerClass.VALUE_REJECTED,
        AnswerClass.SHAPE_CHANGED, AnswerClass.TRANSIENT,
    )
}
