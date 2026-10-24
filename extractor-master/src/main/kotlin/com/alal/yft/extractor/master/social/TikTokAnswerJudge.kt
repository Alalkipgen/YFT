/*
 * Provenance (social plan S2b, copied, not moved; main 34a41890; listed in toolkit-provenance.tsv):
 *   extractor-sites/.../tiktok/TikTokPageParser.kt  kindOf (the login path, the home page),
 *                                        CHALLENGE_MARKERS (only without TikTok's data scripts)
 */
package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.recipes.TikTokStatusRecipe
import java.net.URI

/**
 * Social plan S2b (`MASTER_KEY_SOCIAL_PLAN.md` §6.2, §7.3): TikTok's judge, for T1's answer (the
 * embed player, asked without the user's cookies) and each of main's pages T2 reads. As
 * Facebook's, conservative: one anonymous answer says nothing final about what the user may watch
 * (main's P46 rule: TikTok's status on one page is not final), so only a photo post and DRM end the
 * lookup, and only as the reader states them ([photo], [drm]: the recipe's own paths for the asked
 * video). Rows bound to the item ID win over any page signal. A 429 stops the site, as does a bot
 * check (a page without TikTok's data that asks to verify: the user answers it with Show check);
 * TikTok's login address is the browser's. TikTok's status for the post (`TikTokStatusRecipe`:
 * removed, private, region) and an answer that names the video without a file are "needs
 * playback" (T2, then the visible tab's own playback), never final. The page's words are no
 * evidence. Reads only; no request, no log.
 */
internal object TikTokAnswerJudge {
    /**
     * [answer]: the rung's HTTP answer; [rows]: the rows the reader bound to the item ID; [drm]:
     * the reader saw the post's DRM flag; [photo]: the reader saw the post's photo list; [namesVideo]:
     * the answer names the video while no file was read for it.
     */
    fun judge(
        answer: ExtractorHttpResult,
        rows: Int = 0,
        drm: Boolean = false,
        photo: Boolean = false,
        namesVideo: Boolean = false,
    ): Judged {
        val page = when (answer) {
            is ExtractorHttpResult.Failure -> return failure(answer)
            is ExtractorHttpResult.Success -> answer
        }
        when (wallOf(page.finalUrl)) {
            Wall.LOGIN -> return Judged(AnswerClass.LOGIN_WALL, AnswerClass.LOGIN_WALL.reason, "final address: login")
            Wall.HOME -> return shapeChanged("final address: the home page")
            Wall.OFF_SITE -> return shapeChanged("final address: off TikTok")
            null -> Unit
        }
        if (rows > 0) return Judged(AnswerClass.DIRECT, null, "rows $rows, bound to the item ID")
        if (drm) return Judged(AnswerClass.DRM, AnswerClass.DRM.reason, "the reader: DRM")
        if (photo) return Judged(AnswerClass.PHOTO_ONLY, AnswerClass.PHOTO_ONLY.reason, "the reader: a photo post")
        if (botCheck(page.body)) return Judged(AnswerClass.BOT_CHECK, AnswerClass.BOT_CHECK.reason, "a verify page")
        status(page.body)?.let { status ->
            return Judged(AnswerClass.NEEDS_PLAYBACK, null, "TikTok status: ${kindOf(status)}; one anonymous answer")
        }
        if (namesVideo) return Judged(AnswerClass.NEEDS_PLAYBACK, null, "names the video, no file")
        return shapeChanged("no rows; no status")
    }

    /**
     * A bot check: none of TikTok's data scripts (main's page's, the embed's) and a verify or
     * captcha word in the page (main's `CHALLENGE_MARKERS`, any case).
     */
    fun botCheck(body: String): Boolean {
        if (DATA_SCRIPTS.any(body::contains)) return false
        val text = body.lowercase()
        return CHALLENGE_MARKERS.any(text::contains)
    }

    /** TikTok's first non-zero status number in the answer (`statusCode`, `statusCodeV2`), or null. */
    fun status(body: String): Long? = STATUS.findAll(body).firstNotNullOfOrNull { match ->
        match.groupValues[2].toLongOrNull()?.takeIf { it != 0L }
    }

    /** A status's kind for the evidence line: main's reading of it, never final here. */
    private fun kindOf(status: Long): String = when (TikTokStatusRecipe.failure(status)) {
        SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE -> "private or unavailable"
        SiteExtractionFailure.GEO_RESTRICTED -> "region"
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

    private enum class Wall { LOGIN, HOME, OFF_SITE }

    /** The final address's wall: TikTok's login path, its home page, or another host. */
    private fun wallOf(finalUrl: String): Wall? {
        val uri = runCatching { URI(finalUrl.trim()) }.getOrNull() ?: return Wall.OFF_SITE
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return Wall.OFF_SITE
        if (host != TIKTOK_HOST && !host.endsWith(".$TIKTOK_HOST")) return Wall.OFF_SITE
        val path = uri.rawPath.orEmpty().lowercase().trimEnd('/')
        return when {
            path == "/login" || path.startsWith("/login/") -> Wall.LOGIN
            path.isEmpty() || path == "/foryou" || path.startsWith("/foryou/") -> Wall.HOME
            else -> null
        }
    }

    private const val TIKTOK_HOST = "tiktok.com"
    private val DATA_SCRIPTS = listOf("__UNIVERSAL_DATA_FOR_REHYDRATION__", "SIGI_STATE", "__FRONTITY_CONNECT_STATE__")
    private val CHALLENGE_MARKERS = listOf("captcha", "verify", "waf", "please wait", "challenge")
    private val STATUS = Regex(""""statusCode(V2)?"\s*:\s*(-?[0-9]{1,12})(?![0-9])""")
}
