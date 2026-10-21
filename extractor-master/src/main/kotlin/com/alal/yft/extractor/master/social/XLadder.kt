/*
 * Provenance (social plan S2c, copied, not moved; main 34a41890; listed in toolkit-provenance.tsv):
 *   extractor-sites/.../x/XSyndication.kt  parse (the order: the post's own media, else its own
 *                                        card's, else the quoted post's; its photos counted),
 *                                        videosOf, videoOf (a video or a GIF: its MP4 files, its
 *                                        playlist, its length), isMediaUrl, PHOTO, MP4, HLS,
 *                                        VIDEO_HOST
 *   extractor-sites/.../x/XExtractor.kt  extract (the item: the link's `/video/N` among the post's
 *                                        own or card's media, else the first)
 *   extractor-sites/.../x/XUrls.kt  VIDEO_SUFFIX, videoNumberOf
 * Reviewed (social plan 0.1, main's X card fix on this branch, not at the base): main's
 *   cardVideosOf and cardMediaIds (the card's media in the card's own order); the design is
 *   reused, the code is new (no row: the base has no such symbol).
 */
package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonReadResult
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.api.json.path
import com.alal.yft.extractor.master.ContractAnswer
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.MasterStage
import com.alal.yft.extractor.master.PageSnapshot
import com.alal.yft.extractor.master.contract.SiteContracts
import com.alal.yft.extractor.master.layers.ContractReader
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.policy.TerminalRules
import com.alal.yft.extractor.master.recipes.ContractRecipe
import java.net.URI
import java.util.Locale
import kotlin.coroutines.coroutineContext

/**
 * Social plan S2c (`MASTER_KEY_SOCIAL_PLAN.md` §6.4, §7.3): X's ladder after main failed, in place
 * of R8's unjudged ask, inside the same rules as Facebook's ([FacebookLadder]) and TikTok's
 * ([TikTokLadder]). **X1, main's answer replayed:** the answer main's adapter got from X's
 * embedded-post widget in this lookup ([MainPages]: its page, or its refused GET), read with no new
 * ask (no token self-check, no X2: decision 2) and judged ([XAnswerJudge]).
 *
 * - **Rows, the link's item only** (decision 11): the post's own media, else its own card's, else
 *   the quoted post's (main's order, 0.1); the link's `/video/N` among the post's own or card's
 *   media, else the first. Its rows are bound to the link's post ID (a quoted post's files too)
 *   and go to the engine's media checks (V and the init check, S6) as main's answer for that one
 *   item, read with X's recipe: its MP4 files first, its playlist only without them and only with
 *   a known length (the item's playlist alone, no length: nothing to check). A renamed key: main's
 *   whole answer read by the recipe (L4 + L3 included), only when its rows are one item's.
 * - **Final:** a photo post (main's own answer, `NO_MEDIA_FOUND`, in main's words; photos and no
 *   video or GIF by the media's types, so a renamed key never makes one) and the widget's 404 (no
 *   such post: `PRIVATE_OR_UNAVAILABLE`).
 * - **Nothing to read** (null: the next stage, the visible tab's own playback, X3, and the user's
 *   browser; main's reason stays): a tombstone (an age-limited post's empty one; a deleted or a
 *   protected post's with X's notice, which one answer cannot tell apart), `{}`, another status,
 *   another post's answer or an unknown shape.
 *
 * S7: X's own budget ([SocialBudget.X]): the rows go to V only while main's and Master's asks, the
 * lookup's time and the answer's characters hold. Main's X code and reasons stay (decision 10).
 * Claims only an X lookup with its own record ([LookupRecord]: the social-ladder build's,
 * `-Pyft.socialLadder`); every other lookup keeps R8's path. [contractsOf] gives the recipe the
 * engine reads with, around [http] (never asked here); [identify] names a page's post (the app's
 * adapters). Lines: short labels and counts, never a link, an ID or the post's words; nothing is
 * logged.
 */
class XLadder(
    http: ExtractorHttpClient,
    private val identify: (String) -> SitePageIdentity?,
    contractsOf: (ExtractorHttpClient) -> SiteContracts = ::SiteContracts,
    private val cooldown: SocialCooldown = SocialCooldown(),
    private val budget: SocialBudget = SocialBudget.X,
) : SocialStage {
    private val contracts = contractsOf(http)

    override suspend fun claims(request: MasterRequest): Boolean =
        request.identity?.siteId == SITE_ID && coroutineContext[LookupRecord] != null

    override suspend fun stage(
        request: MasterRequest,
        policy: MasterPolicy,
        details: MutableList<String>,
    ): SocialStage.Staged? {
        val record = coroutineContext[LookupRecord] ?: return null
        // S7: a host that answered main's ask with a 429 cools from the stage's end, as TikTok's.
        return try {
            climb(record, request, details)
        } finally {
            cooldown.note(record.stopHosts())
        }
    }

    /** X1 for [request]'s lookup ([record]): main's answer for the post, judged. */
    private suspend fun climb(
        record: LookupRecord,
        request: MasterRequest,
        details: MutableList<String>,
    ): SocialStage.Staged? {
        val asked = request.identity?.takeIf { it.siteId == SITE_ID } ?: return null
        // As R8: a sign-in or bot-check failure is the user's own playback's; another post's page
        // is not read for.
        if (TerminalRules.needsAuthorizedPlayback(request.primaryFailure) ||
            request.expectedContentId?.let { it != asked.contentId } == true
        ) {
            return null
        }
        val pages = coroutineContext[MainPages]
        details += "$LINE after ${request.primaryFailure.name}; main's answer replayed, no new ask"
        val identity = if (asked.requiresCanonicalResolution) pages?.resolved(asked, identify) else asked
        if (identity == null) {
            details += "$X1: a link main resolves first, no post in main's answer; nothing to read"
            return null
        }
        val read = request.copy(identity = identity, expectedContentId = identity.contentId)
        val kept = pages?.let { answerOf(it, identity.contentId) }
        if (kept == null) {
            details += "$X1: none kept for the post; nothing to read"
            return null
        }
        val answer = kept.answer ?: run {
            details += "$X1: not kept (over the bound); nothing to read"
            return null
        }
        val reading = (answer as? ExtractorHttpResult.Success)?.let { readAnswer(it, identity, read) } ?: Reading()
        reading.item?.let { details += "$ITEM: $it" }
        val judged = XAnswerJudge.judge(answer, reading.judged)
        details += "$X1: ${judged.answerClass.name} (${judged.evidence}); next: ${judged.next.name}"
        return when (judged.next) {
            Next.CHECK_ROWS -> {
                val staged = reading.staged ?: return null
                over(record, staged.body.length.toLong())?.let { why ->
                    details += "$X1: $why; not checked"
                    return null
                }
                SocialStage.Staged(
                    MasterStage.PAGE_DATA,
                    PageSnapshot(request.pageUrl, request.generation, contract = staged),
                    read,
                )
            }
            Next.FINAL -> judged.reason?.let { final(request, read, it) }
            Next.NEXT_RUNG, Next.PLAYBACK, Next.BROWSER, Next.STOP_SITE -> null
        }
    }

    /**
     * S7: why the rows may not go to V in [record]'s lookup, or null: main's and Master's asks so
     * far, the lookup's time or the answer's [chars] over X's budget.
     */
    private fun over(record: LookupRecord, chars: Long): String? = when {
        record.mainAsks + record.masterAsks >= budget.requests -> "over the lookup's request budget"
        record.elapsedMillis() >= budget.millis -> "over the lookup's time budget"
        chars > budget.bytes -> "over the lookup's byte budget"
        else -> null
    }

    /** Main's answer from the widget, as kept: null [answer] when its page was over the bound. */
    private class Kept(val answer: ExtractorHttpResult?)

    /**
     * Main's answer from the widget for [postId] in [pages]: its last page for the post (its body,
     * when kept), else its last refused GET for the post; null when main asked nothing for it.
     */
    private fun answerOf(pages: MainPages, postId: String): Kept? {
        pages.pages().lastOrNull { widgetAsk(it.url, postId) }?.let { page ->
            return Kept(page.body?.let { ExtractorHttpResult.Success(page.statusCode, it, page.finalUrl, page.contentType) })
        }
        return pages.refusals().lastOrNull { widgetAsk(it.url, postId) }?.let { refusal ->
            Kept(ExtractorHttpResult.Failure(refusal.reason, refusal.statusCode))
        }
    }

    /** What the ladder read in main's answer ([judged]), the answer it stages ([staged]) and the item's line. */
    private class Reading(
        val judged: XReading = XReading(),
        val staged: ContractAnswer? = null,
        val item: String? = null,
    )

    /**
     * Main's 2xx answer [main] for [identity]'s post, read for [read]: the link's item as main's
     * answer for it alone (its rows by the recipe), else main's whole answer by the recipe when its
     * rows are one item's. A tombstone, `{}`, another host and a non-object are the judge's alone.
     */
    private fun readAnswer(main: ExtractorHttpResult.Success, identity: SitePageIdentity, read: MasterRequest): Reading {
        if (!XAnswerJudge.widgetHost(main.finalUrl)) return Reading()
        val root = (BoundedJsonParser.read(main.body.trim()) as? JsonReadResult.Read)?.value as? JsonValue.Object
            ?: return Reading()
        if (root.entries.isEmpty() || XAnswerJudge.tombstone(root)) return Reading()
        val named = root["id_str"].asStringOrNull
        if (named != null && named != identity.contentId) return Reading(XReading(otherPost = true))
        val recipe = contracts.recipeOf(SITE_ID)
        val post = postOf(root)
        val wanted = videoNumberOf(identity.canonicalPageUrl)
        val video = post.videos.firstOrNull { post.source != Source.QUOTED && it.position == wanted }
            ?: post.videos.firstOrNull()
        if (video == null) {
            // A photo post by its media's types: a video whose files the reader finds none of (a
            // renamed key) does not make it one.
            if (post.photos > 0 && !post.videoTyped) return Reading(XReading(photos = true))
        } else {
            val line = "${post.source.label} ${video.kind} ${video.position}, ${post.videos.size} in the post; " +
                "MP4 files ${video.files}; playlist ${if (video.playlist) "yes" else "no"}; " +
                "length ${if (video.durationMillis != null) "known" else "unknown"}"
            if (!video.usable) return Reading(XReading(playlistOnly = true), item = line)
            val answer = ContractAnswer(SITE_ID, itemAnswer(identity.contentId, root, video.media), main.finalUrl)
            val rows = recipe?.let { ContractReader(read, it, answer).read().candidates.size } ?: 0
            if (rows > 0) return Reading(XReading(rows = rows), answer, line)
            // The recipe read nothing in the item: main's whole answer, as a renamed key would be.
            return whole(main, read, recipe).let { Reading(it.judged, it.staged, line) }
        }
        return whole(main, read, recipe)
    }

    /** Main's whole answer [main] read by [recipe] for [read]: staged only when its rows are one item's. */
    private fun whole(main: ExtractorHttpResult.Success, read: MasterRequest, recipe: ContractRecipe?): Reading {
        recipe ?: return Reading()
        val answer = ContractAnswer(SITE_ID, main.body, main.finalUrl)
        val rows = ContractReader(read, recipe, answer).read().candidates
        if (rows.isEmpty()) return Reading()
        val items = rows.map { itemOf(it.mediaUrl) }.distinct()
        return if (items.size == 1 && items[0] != null) {
            Reading(XReading(rows = rows.size, byShape = true), answer)
        } else {
            Reading(XReading(shapeItems = items.size))
        }
    }

    /** Where the post's videos come from, in main's order, with the item line's words for it. */
    private enum class Source(val label: String) { OWN("the post's own"), CARD("the card's"), QUOTED("the quoted post's") }

    /** One video of the post (main's `XVideo`, counted): its [media] node as the answer has it. */
    private class Video(
        val position: Int,
        val media: JsonValue,
        val kind: String,
        val files: Int,
        val playlist: Boolean,
        val durationMillis: Long?,
    ) {
        /** Rows to check: its MP4 files, else its playlist with a known length (S6). */
        val usable: Boolean get() = files > 0 || playlist && durationMillis != null
    }

    /**
     * The post's videos from one [source], its photos and [videoTyped]: whether any of its media
     * (its own, its card's, the quoted post's) is a video or a GIF by its type, files or not.
     */
    private class Post(val videos: List<Video>, val source: Source, val photos: Int, val videoTyped: Boolean)

    /** The post's videos as main reads them: its own media's, else its own card's, else the quoted post's. */
    private fun postOf(root: JsonValue): Post {
        val ownMedia = root["mediaDetails"].asArrayOrEmpty
        val cardMedia = cardMediaOf(root)
        val quotedMedia = root["quoted_tweet"]["mediaDetails"].asArrayOrEmpty
        val own = videosOf(ownMedia)
        val card = if (own.isEmpty()) videosOf(cardMedia) else emptyList()
        val quoted = if (own.isEmpty() && card.isEmpty()) videosOf(quotedMedia) else emptyList()
        val photos = maxOf(ownMedia.count { it["type"].asStringOrNull == PHOTO }, root["photos"].asArrayOrEmpty.size)
        val typed = (ownMedia + cardMedia + quotedMedia).any { it["type"].asStringOrNull in VIDEO_TYPES }
        return when {
            own.isNotEmpty() -> Post(own, Source.OWN, photos, typed)
            card.isNotEmpty() -> Post(card, Source.CARD, photos, typed)
            else -> Post(quoted, Source.QUOTED, photos, typed)
        }
    }

    private fun videosOf(media: List<JsonValue>): List<Video> =
        media.mapIndexedNotNull { index, item -> videoOf(index + 1, item) }

    /**
     * The media of the post's own card (`card.binding_values.unified_card`, a JSON text), in the
     * card's own media order (its components: a `media` component's item, a `swipeable_media`
     * component's items); an item the list does not name is not read.
     */
    private fun cardMediaOf(tweet: JsonValue): List<JsonValue> {
        val text = tweet.path("card", "binding_values", UNIFIED_CARD, "string_value").asStringOrNull
            ?: return emptyList()
        val card = (BoundedJsonParser.read(text) as? JsonReadResult.Read)?.value ?: return emptyList()
        val components = (card["component_objects"] as? JsonValue.Object)?.entries ?: return emptyList()
        val names = card["components"].asArrayOrEmpty.mapNotNull { it.asStringOrNull }
            .ifEmpty { components.keys.toList() }
        val ids = names.mapNotNull(components::get).flatMap { component ->
            val data = component["data"]
            when (component["type"].asStringOrNull) {
                "media" -> listOfNotNull(data["id"].asStringOrNull)
                "swipeable_media" -> data["media_list"].asArrayOrEmpty.mapNotNull { it["id"].asStringOrNull }
                else -> emptyList()
            }
        }.distinct()
        val entities = card["media_entities"]
        return ids.mapNotNull { id -> entities[id] }
    }

    /** A video or GIF with an MP4 file or a playlist on X's video host (main's `videoOf`), else null. */
    private fun videoOf(position: Int, media: JsonValue): Video? {
        val kind = when (media["type"].asStringOrNull) {
            VIDEO -> "video"
            GIF -> "GIF"
            else -> return null
        }
        val info = media["video_info"]
        val variants = info["variants"].asArrayOrEmpty
        val files = variants.filter { it["content_type"].asStringOrNull?.lowercase(Locale.US) == MP4 }
            .mapNotNull { variant -> variant["url"].asStringOrNull?.takeIf(::isMediaUrl) }
            .distinct().size
        val playlist = variants.firstOrNull { it["content_type"].asStringOrNull?.lowercase(Locale.US) == HLS }
            ?.get("url").asStringOrNull?.takeIf(::isMediaUrl) != null
        if (files == 0 && !playlist) return null
        return Video(position, media, kind, files, playlist, info["duration_millis"].asLongOrNull?.takeIf { it > 0 })
    }

    /** A final answer [reason] for [request]'s page, read for [read] (main's answer, no request). */
    private fun final(request: MasterRequest, read: MasterRequest, reason: SiteExtractionFailure) =
        SocialStage.Staged(
            MasterStage.PAGE_DATA,
            PageSnapshot(request.pageUrl, request.generation, accessFailure = reason),
            read,
        )

    override fun toString(): String = "XLadder"

    companion object {
        /** Main's X adapter's site ID (the video key's prefix). */
        const val SITE_ID = "x"

        private const val LINE = "master: x ladder"
        private const val X1 = "master: x X1 main's answer"
        private const val ITEM = "master: x X1 item"

        private const val WIDGET_PATH = "/tweet-result"
        private const val PHOTO = "photo"
        private const val VIDEO = "video"
        private const val GIF = "animated_gif"
        private val VIDEO_TYPES = setOf(VIDEO, GIF)
        private const val MP4 = "video/mp4"
        private const val HLS = "application/x-mpegurl"
        private const val VIDEO_HOST = "video.twimg.com"
        private const val GIF_PATH = "tweet_video"
        private const val UNIFIED_CARD = "unified_card"
        private val VIDEO_SUFFIX = Regex("/video/([1-4])$")

        /**
         * The item of X's video file [url] (its video host's path: `amplify_video/{id}`,
         * `ext_tw_video/{id}`, a GIF's `tweet_video/{name}`), so two rows tell whether they are one
         * video's files; null for another host or address.
         */
        fun itemOf(url: String): String? {
            val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
            if (uri.scheme?.lowercase(Locale.US) != "https" || uri.host?.lowercase(Locale.US)?.trimEnd('.') != VIDEO_HOST) {
                return null
            }
            val segments = uri.rawPath.orEmpty().split('/').filter(String::isNotEmpty)
            val kind = segments.getOrNull(0) ?: return null
            val name = segments.getOrNull(1) ?: return null
            return if (kind == GIF_PATH) "$kind/${name.substringBeforeLast('.')}" else "$kind/$name"
        }

        /** The 1-based video a canonical address names with `/video/N` (main's `XUrls.videoNumberOf`), or null. */
        internal fun videoNumberOf(canonicalPageUrl: String): Int? =
            VIDEO_SUFFIX.find(canonicalPageUrl)?.groupValues?.get(1)?.toIntOrNull()

        /** Only X's own video host (main's `isMediaUrl`): a changed answer cannot point a row elsewhere. */
        private fun isMediaUrl(url: String): Boolean {
            val uri = runCatching { URI(url) }.getOrNull() ?: return false
            return uri.scheme == "https" && uri.userInfo == null && uri.host?.lowercase(Locale.US) == VIDEO_HOST
        }

        /** Whether [url] is main's ask of the widget's answer for [postId]. */
        private fun widgetAsk(url: String, postId: String): Boolean {
            if (!XAnswerJudge.widgetHost(url)) return false
            val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
            return uri.rawPath == WIDGET_PATH && uri.rawQuery.orEmpty().split('&').any { it == "id=$postId" }
        }

        /**
         * Main's answer for the link's item alone: the post's ID and text (the row's title) and the
         * item's own node as the answer has it, as X's one-item post.
         */
        private fun itemAnswer(postId: String, root: JsonValue, media: JsonValue): String = buildString {
            append("{\"__typename\":\"Tweet\",\"id_str\":")
            quote(postId)
            (root["text"] as? JsonValue.Text)?.let { text ->
                append(",\"text\":")
                quote(text.value)
            }
            append(",\"mediaDetails\":[")
            write(media)
            append("]}")
        }

        private fun StringBuilder.write(value: JsonValue) {
            when (value) {
                JsonValue.Null -> append("null")
                is JsonValue.Bool -> append(value.value)
                is JsonValue.Number -> append(value.text)
                is JsonValue.Text -> quote(value.value)
                is JsonValue.Array -> {
                    append('[')
                    value.items.forEachIndexed { index, item ->
                        if (index > 0) append(',')
                        write(item)
                    }
                    append(']')
                }
                is JsonValue.Object -> {
                    append('{')
                    var first = true
                    for ((key, item) in value.entries) {
                        if (!first) append(',')
                        first = false
                        quote(key)
                        append(':')
                        write(item)
                    }
                    append('}')
                }
            }
        }

        private fun StringBuilder.quote(text: String) {
            append('"')
            for (char in text) {
                when {
                    char == '"' -> append("\\\"")
                    char == '\\' -> append("\\\\")
                    char < ' ' -> append("\\u").append(String.format(Locale.US, "%04x", char.code))
                    else -> append(char)
                }
            }
            append('"')
        }
    }
}
