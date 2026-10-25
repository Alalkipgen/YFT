package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonProblem
import com.alal.yft.extractor.api.json.JsonReadResult
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.master.lookup.AnswerMark
import com.alal.yft.extractor.master.lookup.AnswerMark.BOT_CHECK
import com.alal.yft.extractor.master.lookup.AnswerMark.DATA
import com.alal.yft.extractor.master.lookup.AnswerMark.NOT_JSON
import com.alal.yft.extractor.master.lookup.AnswerMark.NO_DATA
import com.alal.yft.extractor.master.lookup.AnswerMark.NO_POST
import com.alal.yft.extractor.master.lookup.AskLabels
import com.alal.yft.extractor.master.lookup.AskReading
import com.alal.yft.extractor.master.lookup.LookupAsk
import java.net.URI
import java.util.Locale

/**
 * VK-Master plan §10, Appendix B step 4: VK's ways for a labelled record (the site census; agent D
 * registers [VkSite.SITE]), from the ask's method and address alone — never a header's value, so
 * never a cookie or the `Authorization` bearer:
 *
 * - [EMBED]: a GET of `/video_ext.php` on vk.com or vk.ru (main's B, B again and B2);
 * - [TOKEN]: a POST of login.vk.ru's `?act=get_anonym_token` (main's path D, V2's);
 * - [VIDEO_GET]: api.vkvideo.ru's `/method/video.get` (main's path D, V2's);
 * - [MANIFEST]: a GET of a file on VK's video servers ([VkClient.mediaFile]) whose address names a
 *   manifest (its path ends `.m3u8` or `.mpd`, as [VkReader] types a file by its address): main's
 *   manifest reads (at most two, only for an answer without MP4);
 * - a file check: [SiteWays.FILE_CHECK].
 *
 * Any other GET of a file — V's one-row reads ([SocialRowCheck]: route B lists them as Master's GET
 * of the file, with the row's own headers, as main's manifest GET sends them), or a manifest whose
 * address does not show its kind — is no way of VK's (null), as on every other site: V reads MP4
 * and WebM rows only, so a manifest-shaped address is never V's, and an address that hides its
 * kind is left unlabelled, never mislabelled.
 *
 * Readings (a 2xx answer; a mark and a shape, nothing of the body kept): the embed page and
 * `video.get` by Master's own reader and judge ([VkReader], [VkAnswerJudge]) for the video the ask
 * names (`oid` + `id`; `owner_id` + `videos`): rows bound to it are [DATA]; the embed's adult
 * answer or VK's message ("not found": deleted, private and list-only alike), `video.get`'s
 * `restriction.can_play` 0 are [NO_POST]; `video.get`'s captcha (API error 14) is [BOT_CHECK];
 * anything else (VK's shell, a live broadcast, VK's other errors, rate ones included) is
 * [NO_DATA]. The token's answer: a token of VK's shape is [DATA] (checked and dropped, never kept),
 * VK's captcha [BOT_CHECK], anything else [NO_DATA]. A JSON way's answer that is not JSON is
 * [NOT_JSON]. A page's shape: `h` + 8 hex of [EMBED_MARKERS] it holds; JSON's: its key paths
 * ([SiteReadings.jsonShape]).
 */
internal class VkLabels : AskLabels {
    override fun way(ask: LookupAsk): String? {
        if (ask.method == LookupAsk.PROBE) return SiteWays.FILE_CHECK
        val uri = AskParts.uri(ask.url) ?: return null
        val path = uri.rawPath.orEmpty()
        return when {
            ask.method == LookupAsk.GET && AskParts.hostIn(uri, EMBED_HOSTS, subdomains = false) &&
                path == VkClient.EMBED_PATH -> EMBED
            ask.method == LookupAsk.POST && AskParts.hostIn(uri, listOf(VkClient.TOKEN_HOST), subdomains = false) &&
                VkLadder.queryOf(ask.url, TOKEN_PATH)?.get(ACT) == TOKEN_ACT -> TOKEN
            AskParts.hostIn(uri, listOf(VkClient.API_HOST), subdomains = false) && path == VkClient.VIDEO_GET_PATH -> VIDEO_GET
            ask.method == LookupAsk.GET && VkClient.mediaFile(ask.url) && manifestPath(path) -> MANIFEST
            else -> null
        }
    }

    override fun read(ask: LookupAsk, way: String?, result: ExtractorHttpResult.Success): AskReading? = when (way) {
        EMBED -> embed(ask, result)
        TOKEN -> token(result)
        VIDEO_GET -> videoGet(ask, result)
        else -> null
    }

    /** The embed page for the video its ask names: rows, the adult answer or VK's message, else none. */
    private fun embed(ask: LookupAsk, result: ExtractorHttpResult.Success): AskReading {
        val body = result.body
        val reading = embedLink(ask.url)?.let { VkReader.embed(body, it).reading } ?: VkReading()
        val judged = VkAnswerJudge.embed(result, reading)
        val mark = when {
            judged.next == Next.CHECK_ROWS -> DATA
            judged.answerClass == AnswerClass.VIDEO_WITHOUT_FILES && VkAnswerJudge.isAdult(body) -> NO_POST
            judged.answerClass == AnswerClass.NEEDS_PLAYBACK -> NO_POST
            else -> NO_DATA
        }
        return AskReading(mark, SiteReadings.pageShape(body, EMBED_MARKERS))
    }

    /** login.vk.ru's answer: a token of VK's shape (checked, dropped), VK's captcha, else none. */
    private fun token(result: ExtractorHttpResult.Success): AskReading? = json(result) { root ->
        when {
            root !is JsonValue.Object -> NO_DATA
            VkClient.token(result.body.trim(), System.currentTimeMillis()) != null -> DATA
            VkAnswerJudge.captcha(root) -> BOT_CHECK
            else -> NO_DATA
        }
    }

    /** `video.get`'s answer for the video its ask names: rows, the captcha, `can_play` 0, else none. */
    private fun videoGet(ask: LookupAsk, result: ExtractorHttpResult.Success): AskReading? = json(result) {
        val read = videoGetLink(ask.url)?.let { VkReader.api(result.body, it) } ?: VkReader.Read()
        val judged = VkAnswerJudge.api(result, read.reading)
        when {
            judged.next == Next.CHECK_ROWS -> DATA
            judged.answerClass == AnswerClass.BOT_CHECK -> BOT_CHECK
            read.reading.restricted -> NO_POST
            else -> NO_DATA
        }
    }

    companion object {
        /** Main's embed page (B, B again, B2): `/video_ext.php` on vk.com or vk.ru. */
        const val EMBED = SiteWays.EMBED_PAGE

        /** login.vk.ru's anonymous token for signed-out viewers (main's path D, V2's). */
        const val TOKEN = "anonymous token"

        /** api.vkvideo.ru's `video.get` (main's path D, V2's). */
        const val VIDEO_GET = "video.get"

        /** Main's manifest read (an answer without MP4): a manifest-shaped file of VK's video servers. */
        const val MANIFEST = "manifest"

        /** The embed's markers a page's shape counts (key and element names, never a value). */
        val EMBED_MARKERS = listOf(
            "apiPrefetchCache", "\"files\"", "\"mp4_", "\"hls\"", "\"dash_sep\"", "failover_host",
            "video_ext_msg", "embedErrorCallback", "spa_root", "isRedirectChallenge",
        )

        private val EMBED_HOSTS = listOf(VkClient.COM_HOST, VkClient.RU_HOST)
        private const val TOKEN_PATH = "/"
        private const val ACT = "act"
        private const val TOKEN_ACT = "get_anonym_token"
        private val CONTENT_ID = Regex("^(-?[0-9]{1,19})_([0-9]{1,19})(?:_[A-Za-z0-9_-]{1,64})?$")
        private val NUMBER = Regex("^-?[0-9]{1,19}$")

        /** Longer JSON is not read ([SiteReadings.MAX_JSON_CHARS]). */
        private const val MAX_JSON_CHARS = SiteReadings.MAX_JSON_CHARS

        private fun manifestPath(path: String): Boolean = path.lowercase(Locale.ROOT).let { it.endsWith(".m3u8") || it.endsWith(".mpd") }

        /** The video an embed ask names: `oid` + `id`. */
        private fun embedLink(url: String): VkClient.Link? {
            val query = VkLadder.queryOf(url, VkClient.EMBED_PATH) ?: return null
            return linkOf(query["oid"], query["id"])
        }

        /** The video a `video.get` ask names: `videos`' first `{oid}_{id}`, with `owner_id` when given. */
        private fun videoGetLink(url: String): VkClient.Link? {
            val query = VkLadder.queryOf(url, VkClient.VIDEO_GET_PATH) ?: return null
            val match = query["videos"]?.let(CONTENT_ID::matchEntire) ?: return null
            val owner = match.groupValues[1]
            if (query["owner_id"]?.let { it != owner } == true) return null
            return linkOf(owner, match.groupValues[2])
        }

        private fun linkOf(owner: String?, id: String?): VkClient.Link? {
            val ownerId = owner?.takeIf(NUMBER::matches)?.toLongOrNull() ?: return null
            val videoId = id?.takeIf(NUMBER::matches)?.toLongOrNull() ?: return null
            if (ownerId == 0L || videoId <= 0L) return null
            return VkClient.Link(ownerId, videoId)
        }

        /** A JSON answer read by [mark]; not JSON is [NOT_JSON]; too deep or too big: not read (as [SiteReadings]). */
        private inline fun json(result: ExtractorHttpResult.Success, mark: (JsonValue) -> AnswerMark): AskReading? {
            if (result.body.length > MAX_JSON_CHARS) return null
            return when (val read = BoundedJsonParser.read(result.body.trim())) {
                is JsonReadResult.Read -> AskReading(mark(read.value), SiteReadings.jsonShape(read.value))
                is JsonReadResult.Failed -> when (read.problem) {
                    JsonProblem.MALFORMED, JsonProblem.TRAILING_TEXT -> AskReading(NOT_JSON)
                    JsonProblem.TOO_DEEP, JsonProblem.TOO_MANY_NODES -> null
                }
            }
        }
    }
}
