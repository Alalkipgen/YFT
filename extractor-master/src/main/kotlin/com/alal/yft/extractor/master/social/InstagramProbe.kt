/*
 * Provenance (social plan S2d part 1; data, not code (S9): no row in toolkit-provenance.tsv):
 *   yt-dlp yt_dlp/extractor/instagram.py, commit 1f1101d0 (2026-07-21), read for 0.2 (2026-10-20):
 *   the signed-out post query (`POST /api/graphql`: its doc ID, friendly name and caller class, its
 *   form's fields in their order and its headers; the web app's ID and the ASBD ID of its
 *   `_api_headers`; the answer's `data.xig_polaris_media.if_not_gated_logged_out`) and the two
 *   places of the page's LSD value (`<script id="__eqmc">`'s `l`, else `["LSD",[],{"token":…}]`),
 *   which the tab's own script reads.
 */
package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonReadResult
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.api.json.path
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.coroutines.CoroutineContext

/**
 * Social plan S2d part 1 (`MASTER_KEY_SOCIAL_PLAN.md` decision 19, 0.2's verdict (b)): the
 * signed-out post query that only the visible tab may send, with its own fetch (the tab's own
 * TLS, cookies and origin: S3 forbids imitating them), as yt-dlp sends it. Data only: the query's
 * names and values, its form's fields in yt-dlp's order, its headers, and the reading of its
 * answer ([videoInAnswer]). Its values may rotate; the probe's line says whether they still
 * answer (the owner's phone session), and part 2's rung, if any, is the owner's go.
 */
object InstagramProbeQuery {
    /** Where the query's values were read (S9). */
    const val SOURCE = "yt-dlp yt_dlp/extractor/instagram.py 1f1101d0"
    const val SOURCE_DATE = "2026-07-21"

    const val ENDPOINT = "https://www.instagram.com/api/graphql"
    const val DOC_ID = "27130156389949648"
    const val FRIENDLY_NAME = "PolarisLoggedOutDesktopWWWPostRootContentQuery"
    const val CALLER_CLASS = "RelayModern"
    const val APP_ID = "936619743392459"
    const val ASBD_ID = "359341"
    const val FORM_TYPE = "application/x-www-form-urlencoded"

    /** The record's stand-in for the page's LSD value in the probe's form (the value is never kept). */
    const val LSD_PLACEHOLDER = "{lsd}"

    /** Where the answer holds the post, when the query shows it signed out. */
    val ANSWER_PATH: List<String> = listOf("data", "xig_polaris_media", "if_not_gated_logged_out")

    /**
     * The query's headers (yt-dlp's `_api_headers` and its own), in order, without those the tab
     * sets itself (its agent, cookies, origin and referrer) and without the two values only the
     * tab has, which its script adds: `X-CSRFToken` (the tab's `csrftoken` cookie, when it has one)
     * and `X-FB-LSD` (the page's LSD value).
     */
    val HEADERS: Map<String, String> = linkedMapOf(
        "X-IG-App-ID" to APP_ID,
        "X-ASBD-ID" to ASBD_ID,
        "X-IG-WWW-Claim" to "0",
        "Accept" to "*/*",
        "X-FB-Friendly-Name" to FRIENDLY_NAME,
        "X-Requested-With" to "XMLHttpRequest",
        "Content-Type" to FORM_TYPE,
    )

    /** The query's variables for the post's [mediaId] (yt-dlp's compact JSON). */
    fun variables(mediaId: String): String = "{\"media_id\":\"$mediaId\"}"

    /** The form's fields in yt-dlp's order, with the page's LSD value [lsd]. */
    fun form(mediaId: String, lsd: String): List<Pair<String, String>> = listOf(
        "lsd" to lsd,
        "fb_api_caller_class" to CALLER_CLASS,
        "fb_api_req_friendly_name" to FRIENDLY_NAME,
        "server_timestamps" to "true",
        "variables" to variables(mediaId),
        "doc_id" to DOC_ID,
    )

    /** [form], URL-encoded as the tab's `URLSearchParams` sends it. */
    fun formBody(mediaId: String, lsd: String): String = form(mediaId, lsd).joinToString("&") { (name, value) ->
        "${encode(name)}=${encode(value)}"
    }

    /**
     * Whether [body] (the query's answer) shows the post [code] ([mediaId]) signed out with a video
     * file or manifest: the answer's post ([ANSWER_PATH]) names the post and holds a video's key with
     * a value. Reads only; never a row (decision 19 (v): the probe only says yes or no).
     */
    fun videoInAnswer(body: String, code: String, mediaId: String?): Boolean {
        if (body.length > MAX_ANSWER_CHARS) return false
        val root = (BoundedJsonParser.read(body.trim()) as? JsonReadResult.Read)?.value ?: return false
        val post = root.path(*ANSWER_PATH.toTypedArray()) as? JsonValue.Object ?: return false
        val named = post["code"].asStringOrNull ?: post["shortcode"].asStringOrNull
        val pk = post["pk"].asStringOrNull ?: post["id"].asStringOrNull?.substringBefore('_')
        val ours = named == code || (named == null && mediaId != null && pk == mediaId)
        return ours && VIDEO.containsMatchIn(body)
    }

    /** Whether [body] is the query's checkpoint answer (Instagram's bot check). */
    fun checkpoint(body: String): Boolean {
        if (body.length > MAX_ANSWER_CHARS) return false
        val root = (BoundedJsonParser.read(body.trim()) as? JsonReadResult.Read)?.value ?: return false
        return root["message"].asStringOrNull == CHECKPOINT
    }

    private fun encode(text: String): String = URLEncoder.encode(text, StandardCharsets.UTF_8.name())

    /** The tab's script's bound on the answer it hands back: 1 MiB of text. */
    const val MAX_ANSWER_CHARS = 1024 * 1024
    private const val CHECKPOINT = "checkpoint_required"
    private val VIDEO = Regex("""\\?"(video_url|video_versions|video_dash_manifest)\\?"\s*:\s*(\\?"(https|<|\\u003[cC])|\[\s*\{)""")
}

/**
 * Social plan S2d part 1 (decision 19): the visible tab's own fetch of [InstagramProbeQuery], a
 * debug-only probe. The app's browser puts one in an Instagram lookup's context (a social-ladder
 * build only); Instagram's ladder ([InstagramLadder]) asks it at most once in a lookup, after
 * main failed and after I2, and reads its answer for one Details line only: no row, no download,
 * no other use (decision 19 (v): the rung only after the owner's phone result). [on]: the probe's
 * own switch (the census screen's; off by default).
 */
interface InstagramTabProbe : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key

    /** Whether the probe's own switch is on. */
    fun on(): Boolean

    /** The tab's own fetch for [request], or why nothing went out ([Reply.NotAsked]). */
    suspend fun ask(request: Request): Reply

    /** The post the probe asks about: its [code], its [mediaId] (the query's variable), the lookup's [pageUrl]. */
    class Request(val code: String, val mediaId: String, val pageUrl: String) {
        override fun toString(): String = "InstagramTabProbe.Request"
    }

    /** What the tab did. */
    sealed interface Reply {
        /** Nothing went out, for [why] (one of [REASONS]; another is said as the tab's own reason). */
        class NotAsked(val why: String) : Reply {
            override fun toString(): String = "NotAsked($why)"
        }

        /**
         * The tab's fetch went out: [status] (0: no answer, the fetch failed or was cut off), the
         * answer's [bytes] (UTF-8) and its text [body] (null over the script's bound,
         * [InstagramProbeQuery.MAX_ANSWER_CHARS]), its [contentType] and [finalUrl]; [wall]: the
         * tab's page shows a sign-in form or stands on a login or checkpoint address.
         */
        class Answered(
            val status: Int,
            val bytes: Long,
            val body: String?,
            val contentType: String?,
            val finalUrl: String?,
            val wall: Boolean,
        ) : Reply {
            override fun toString(): String = "Answered(status=$status, bytes=$bytes, wall=$wall)"
        }
    }

    companion object Key : CoroutineContext.Key<InstagramTabProbe> {
        /** No browser tab is open to fetch in. */
        const val NO_TAB = "no visible tab"

        /** The visible tab is not on the post's page on instagram.com. */
        const val NOT_ON_POST = "the tab is not on the post"

        /** The tab's page holds no LSD value. */
        const val NO_LSD = "no LSD value on the page"

        /** The tab's script gave no answer in time. */
        const val NO_ANSWER = "the tab gave no answer"

        /** The reasons a [Reply.NotAsked] may say in a Details line, word for word. */
        val REASONS: Set<String> = setOf(NO_TAB, NOT_ON_POST, NO_LSD, NO_ANSWER)
    }
}
