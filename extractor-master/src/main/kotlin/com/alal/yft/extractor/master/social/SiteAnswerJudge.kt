package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.api.json.path
import com.alal.yft.extractor.master.toolkit.BalancedJson
import java.net.URI

/**
 * Social plan S1a (`MASTER_KEY_SOCIAL_PLAN.md` §5 L2, §7.3): what comes after one answer a
 * social rung read. [CHECK_ROWS]: V and the init check on the rows; [NEXT_RUNG]: the next rung
 * (or L3 on the pages already read), else main's answer stays; [PLAYBACK]: the visible tab's own
 * playback (F3) or the browser; [BROWSER]: the user's browser (a wall only the user's session
 * passes); [STOP_SITE]: the host stop and the site's cooldown (S7); [FINAL]: the site's final
 * answer about the post.
 */
internal enum class Next { CHECK_ROWS, NEXT_RUNG, PLAYBACK, BROWSER, STOP_SITE, FINAL }

/**
 * S1a, L2: one class per answer ([next]; [reason], the answer's reason when the lookup ends on
 * it; null: the rows, or the failure's own reason). A [Next.FINAL] class needs positive evidence
 * (the judge of each site); [NEEDS_PLAYBACK] is S1a's "needs playback" (F3 or the browser).
 */
internal enum class AnswerClass(val next: Next, val reason: SiteExtractionFailure?) {
    DIRECT(Next.CHECK_ROWS, null),
    /** Rows, fewer heights than the page names: V on them; a later rung may add heights. */
    PARTIAL(Next.CHECK_ROWS, null),
    LOGIN_WALL(Next.BROWSER, SiteExtractionFailure.LOGIN_REQUIRED),
    BOT_CHECK(Next.STOP_SITE, SiteExtractionFailure.BOT_CHECK),
    RATE_LIMITED(Next.STOP_SITE, SiteExtractionFailure.RATE_LIMITED),
    REMOVED(Next.FINAL, SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE),
    PRIVATE(Next.FINAL, SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE),
    AGE_LIMITED(Next.BROWSER, SiteExtractionFailure.LOGIN_REQUIRED),
    REGION_LIMITED(Next.FINAL, SiteExtractionFailure.GEO_RESTRICTED),
    PHOTO_ONLY(Next.FINAL, SiteExtractionFailure.NO_MEDIA_FOUND),
    DRM(Next.FINAL, SiteExtractionFailure.DRM_PROTECTED),
    /** A stale value (a `doc_id`, a token); its self-healing is S1b's. */
    VALUE_REJECTED(Next.NEXT_RUNG, SiteExtractionFailure.RESPONSE_CHANGED),
    SHAPE_CHANGED(Next.NEXT_RUNG, SiteExtractionFailure.RESPONSE_CHANGED),
    /** A network failure or another HTTP status: no answer about the post. */
    TRANSIENT(Next.NEXT_RUNG, null),
    NEEDS_PLAYBACK(Next.PLAYBACK, null),
}

/**
 * S1a: a judged answer: its [answerClass], the [reason] the lookup ends with when it ends on this
 * answer (null: the rows), and the [evidence] — a short label (a status, an address's kind, a
 * route's kind), never the page's words, a link or an ID.
 */
internal data class Judged(val answerClass: AnswerClass, val reason: SiteExtractionFailure?, val evidence: String) {
    val next: Next get() = answerClass.next
}

/**
 * S1a, Facebook's judge (the pilot). Conservative: a final answer only on positive evidence that
 * does not depend on the page's language (§7.3 item 0.3): the page's own route in its JSON
 * (`initialRouteInfo.route`) — the error route with its root's `privacy: true` (Facebook's "this
 * content isn't available" page, in every language) → [AnswerClass.PRIVATE], the video's
 * not-found route → [AnswerClass.REMOVED]. The page's words, a shape hash, the reel's `video:
 * null` and a login form inside a page are no evidence: such an answer is
 * [AnswerClass.SHAPE_CHANGED] (the next rung), never final. Rows bound to the post's video ID win
 * over any page signal; the plugin player's "needs playback" goes before a route (two readings
 * that disagree are not final). A 429 stops the site; a login address is the browser's, a
 * checkpoint address a bot check. Region, age and DRM need evidence not seen yet (no such page
 * was read in 0.3): [drm] is the reader's, never the judge's guess. Reads only; no request, no log.
 */
internal object FacebookAnswerJudge {
    const val ERROR_ROUTE = "comet.fbweb.CometErrorRoute"
    const val NOT_FOUND_ROUTE = "comet.fbweb.CometVideoHomeVideoNotFoundRoute"
    private const val ROUTE_INFO = "\"initialRouteInfo\":"
    private const val MAX_ROUTE_INFOS = 4
    private const val MAX_ROUTE_CHARS = 256 * 1024
    private val FACEBOOK_HOSTS = setOf("facebook.com", "fb.com", "fb.watch")

    /** A page's initial route: its [name] and whether its root's props say [privacy]. */
    data class Route(val name: String, val privacy: Boolean)

    /**
     * [answer]: the rung's HTTP answer; [rows]: the rows the reader bound to the post's video ID
     * (L3; another video's rows never count); [fewerHeights]: fewer heights than the page names;
     * [needsPlayback]: the plugin player answered without files; [drm]: the reader saw a DRM
     * system named for this video.
     */
    fun judge(
        answer: ExtractorHttpResult,
        rows: Int = 0,
        fewerHeights: Boolean = false,
        needsPlayback: Boolean = false,
        drm: Boolean = false,
    ): Judged {
        val page = when (answer) {
            is ExtractorHttpResult.Failure -> return failure(answer)
            is ExtractorHttpResult.Success -> answer
        }
        when (wallOf(page.finalUrl)) {
            Wall.LOGIN -> return Judged(AnswerClass.LOGIN_WALL, AnswerClass.LOGIN_WALL.reason, "final address: login")
            Wall.CHECKPOINT -> return Judged(AnswerClass.BOT_CHECK, AnswerClass.BOT_CHECK.reason, "final address: checkpoint")
            Wall.OFF_SITE -> return shapeChanged("final address: off Facebook")
            null -> Unit
        }
        if (rows > 0) {
            val answerClass = if (fewerHeights) AnswerClass.PARTIAL else AnswerClass.DIRECT
            return Judged(answerClass, null, "rows $rows, bound to the video ID")
        }
        if (drm) return Judged(AnswerClass.DRM, AnswerClass.DRM.reason, "the reader: DRM")
        if (needsPlayback) return Judged(AnswerClass.NEEDS_PLAYBACK, null, "the plugin player: needs playback")
        val route = route(page.body)
        return when {
            route?.name == ERROR_ROUTE && route.privacy ->
                Judged(AnswerClass.PRIVATE, AnswerClass.PRIVATE.reason, "route: error (privacy)")
            route?.name == NOT_FOUND_ROUTE ->
                Judged(AnswerClass.REMOVED, AnswerClass.REMOVED.reason, "route: video not found")
            route != null -> shapeChanged("no rows; route: ${kindOf(route)}")
            else -> shapeChanged("no rows; no route")
        }
    }

    /**
     * The page's initial route, or null when the page names none, a broken one, or two that
     * disagree (at most [MAX_ROUTE_INFOS] are read, each at most [MAX_ROUTE_CHARS]).
     */
    fun route(html: String): Route? {
        val routes = LinkedHashSet<Route>()
        var at = html.indexOf(ROUTE_INFO)
        var read = 0
        while (at >= 0 && read < MAX_ROUTE_INFOS) {
            read += 1
            var start = at + ROUTE_INFO.length
            while (start < html.length && html[start].isWhitespace()) start += 1
            val info = BalancedJson.objectAt(html, start, MAX_ROUTE_CHARS)
                ?.let { BalancedJson.lenientParse(it) }
            info?.let(::routeOf)?.let(routes::add)
            at = html.indexOf(ROUTE_INFO, start)
        }
        return routes.singleOrNull()
    }

    private fun routeOf(info: JsonValue): Route? {
        val route = info["route"] as? JsonValue.Object ?: return null
        val name = route["canonicalRouteName"].asStringOrNull?.takeIf(String::isNotBlank) ?: return null
        // Strict: only a JSON true counts (a string "true" is not the page's flag).
        val privacy = (route.path("rootView", "props", "privacy") as? JsonValue.Bool)?.value == true
        return Route(name, privacy)
    }

    /** A route's kind for the evidence line: "error", "video not found" or "another". */
    private fun kindOf(route: Route): String = when (route.name) {
        ERROR_ROUTE -> "error (no privacy)"
        NOT_FOUND_ROUTE -> "video not found"
        else -> "another"
    }

    private fun failure(answer: ExtractorHttpResult.Failure): Judged = when {
        answer.statusCode == 429 || answer.reason == SiteExtractionFailure.RATE_LIMITED ->
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

    private enum class Wall { LOGIN, CHECKPOINT, OFF_SITE }

    /** The final address's wall: Facebook's login or checkpoint path, or another host. */
    private fun wallOf(finalUrl: String): Wall? {
        val uri = runCatching { URI(finalUrl.trim()) }.getOrNull() ?: return Wall.OFF_SITE
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return Wall.OFF_SITE
        if (FACEBOOK_HOSTS.none { host == it || host.endsWith(".$it") }) return Wall.OFF_SITE
        val path = uri.rawPath.orEmpty().lowercase()
        return when {
            path == "/login" || path.startsWith("/login/") || path.startsWith("/login.php") -> Wall.LOGIN
            path == "/checkpoint" || path.startsWith("/checkpoint/") -> Wall.CHECKPOINT
            else -> null
        }
    }
}
