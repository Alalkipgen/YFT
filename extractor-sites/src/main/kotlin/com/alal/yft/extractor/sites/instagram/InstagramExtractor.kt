package com.alal.yft.extractor.sites.instagram

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.CompanionAudio
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageNavigationHeaders
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.asBooleanOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.api.json.path
import com.alal.yft.extractor.sites.facebook.FacebookDashManifests
import com.alal.yft.extractor.sites.facebook.FacebookDashOffers
import com.alal.yft.extractor.sites.facebook.FacebookDashTrack
import com.alal.yft.extractor.sites.facebook.FacebookUrls
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * P49: Instagram adapter for a post's or reel's video.
 *
 * It asks Instagram's own answers in order and stops at the first that holds the post: the app
 * API (`/api/v1/media/{id}/info/`, only with the browser's Instagram sign-in), the GraphQL post
 * query the web app sends, the post's page (with the browser's cookies, if any) and the public
 * embed page. A post's whole files (`video_versions`, with sound) are rows as they are; its DASH
 * manifest adds every other picture size merged with its best AAC sound, like Facebook's (P4),
 * and that sound alone for Audio. The cookie goes to www.instagram.com only, never to the CDN.
 * A post that needs sign-in fails with [SiteExtractionFailure.LOGIN_REQUIRED] and a message that
 * says where to sign in; a photo post with [SiteExtractionFailure.NO_MEDIA_FOUND].
 */
class InstagramExtractor(
    private val http: ExtractorHttpClient,
    private val maxPageBytes: Long = DEFAULT_MAX_PAGE_BYTES,
) : SiteExtractor {
    override val id: String = InstagramUrls.SITE_ID

    override val displayName: String = "Instagram"

    override fun identify(pageUrl: String): SitePageIdentity? = InstagramUrls.identify(pageUrl)

    override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult {
        require(request.identity.siteId == id) { "This adapter only handles Instagram identities" }
        val code = request.identity.contentId
        val context = request.requestContext
        val signedIn = cookieValue(context.cookie, SESSION_COOKIE) != null
        val details = mutableListOf(if (signedIn) "session: signed in" else "session: none")
        var loginWall = false
        var photoPost: InstagramPost? = null
        var lastFailure: ExtractorHttpResult.Failure? = null

        for (source in Source.entries) {
            if (source == Source.APP_API && !signedIn) continue
            val answer = ask(source, code, request)
            when (answer) {
                is Answer.Failed -> {
                    details += "${source.label} GET failed (${answer.failure.reason}" +
                        (answer.failure.statusCode?.let { " $it" } ?: "") + ")"
                    if (answer.failure.statusCode in LOGIN_STATUSES) loginWall = true
                    lastFailure = answer.failure
                }

                is Answer.LoginWall -> {
                    details += "${source.label}: ${answer.detail}"
                    loginWall = true
                }

                is Answer.Read -> {
                    val post = answer.post
                    when {
                        post == null -> details += "${source.label}: ${answer.detail}, no post"
                        post.hasVideo && !post.hasFiles ->
                            details += "${source.label}: ${answer.detail}, video without files"
                        !post.hasVideo -> {
                            details += "${source.label}: ${answer.detail}, photos only"
                            photoPost = post
                        }

                        else -> {
                            details += "${source.label}: ${answer.detail}"
                            return offers(post, request, details)
                        }
                    }
                }
            }
            if (photoPost != null) break
        }
        return when {
            photoPost != null -> SiteExtractionResult.Failure(
                SiteExtractionFailure.NO_MEDIA_FOUND,
                details = details,
                message = PHOTOS_MESSAGE,
            )

            loginWall -> SiteExtractionResult.Failure(
                SiteExtractionFailure.LOGIN_REQUIRED,
                details = details,
                message = if (signedIn) SIGNED_IN_REFUSED_MESSAGE else LOGIN_MESSAGE,
            )

            lastFailure != null && lastFailure.reason in LINE_FAILURES -> SiteExtractionResult
                .Failure(lastFailure.reason, lastFailure.statusCode, details)

            else -> SiteExtractionResult.Failure(
                SiteExtractionFailure.RESPONSE_CHANGED,
                details = details,
            )
        }
    }

    private enum class Source(val label: String) {
        APP_API("app API"),
        GRAPHQL("GraphQL"),
        PAGE("page"),
        EMBED("embed page"),
    }

    private sealed interface Answer {
        data class Read(val post: InstagramPost?, val detail: String) : Answer

        data class LoginWall(val detail: String) : Answer

        data class Failed(val failure: ExtractorHttpResult.Failure) : Answer
    }

    private suspend fun ask(
        source: Source,
        code: String,
        request: SiteExtractionRequest,
    ): Answer {
        val context = request.requestContext
        val pageUrl = request.identity.canonicalPageUrl.substringBefore('?')
        val (url, headers) = when (source) {
            Source.APP_API -> {
                val mediaId = InstagramUrls.mediaIdOf(code)
                    ?: return Answer.Read(null, "no media ID in the code")
                "$WEB/api/v1/media/$mediaId/info/" to apiHeaders(context, pageUrl)
            }

            Source.GRAPHQL -> graphQlUrl(code) to apiHeaders(context, pageUrl)
            Source.PAGE -> pageUrl to pageHeaders(context, withCookie = true)
            Source.EMBED -> InstagramUrls.embedUrl(code) to pageHeaders(context, withCookie = false)
        }
        val response = when (val result = http.get(url, headers, maxPageBytes)) {
            is ExtractorHttpResult.Failure -> return Answer.Failed(result)
            is ExtractorHttpResult.Success -> result
        }
        val read = "${response.statusCode} (${response.body.length} characters)"
        if (InstagramUrls.isLoginWall(response.finalUrl)) return Answer.LoginWall("login page")
        return when (source) {
            Source.APP_API, Source.GRAPHQL -> {
                val root = BoundedJsonParser.parse(response.body)
                    ?: return Answer.Read(null, "$read, not JSON")
                if (root["require_login"].asBooleanOrNull == true ||
                    root["message"].asStringOrNull == LOGIN_REQUIRED_MESSAGE
                ) {
                    return Answer.LoginWall("$read, login required")
                }
                val scope = if (source == Source.GRAPHQL) {
                    root.path("data", "xdt_shortcode_media")
                        ?: root.path("data", "shortcode_media")
                } else {
                    root
                }
                Answer.Read(scope?.let { InstagramMedia.find(it, code) }, read)
            }

            Source.PAGE, Source.EMBED -> {
                val posts = InstagramPageDocuments.posts(response.body, code)
                Answer.Read(posts.firstOrNull(InstagramPost::hasFiles) ?: posts.firstOrNull(), read)
            }
        }
    }

    /** The rows of the item the link names (a carousel's `img_index`), else its first video. */
    private fun offers(
        post: InstagramPost,
        request: SiteExtractionRequest,
        details: List<String>,
    ): SiteExtractionResult {
        val wanted = InstagramUrls.itemOf(request.identity.canonicalPageUrl)
        val index = wanted?.minus(1)?.takeIf { post.items.getOrNull(it)?.isPlayable == true }
            ?: post.items.indexOfFirst(InstagramItem::isPlayable)
        val item = post.items[index]
        val pageUrl = request.identity.canonicalPageUrl
        val context = BrowserRequestContext(pageUrl, request.requestContext.userAgent, null)
        val now = request.nowEpochMs
        val manifest = item.dashManifest?.let(FacebookDashManifests::parse)
        val durationMillis = item.durationMillis ?: manifest?.durationMillis
        val title = displayTitle(post)

        val files = item.files.filterNot { isExpired(it.url, now) }.map { file ->
            MediaCandidate(
                pageUrl = pageUrl,
                mediaUrl = file.url,
                sources = setOf(CandidateSource.MANIFEST),
                kind = MediaKind.DIRECT,
                mimeType = MP4_MIME_TYPE,
                title = title,
                thumbnailUrl = item.thumbnailUrl,
                durationMillis = durationMillis,
                requestContext = context,
                confidence = CandidateConfidence.HIGH,
                expiresAtEpochMs = FacebookUrls.mediaExpiryEpochMs(file.url),
                drmHint = false,
                observedAtEpochMs = now,
                width = file.width,
                height = file.height,
                bitrateBitsPerSecond = FacebookUrls.statedBitrate(file.url),
            )
        }
        val tracks = manifest?.tracks.orEmpty().filterNot { isExpired(it.url, now) }
        val merged = trackCandidates(tracks, title, item, durationMillis, context, pageUrl, now)
        val candidates = files + merged
        val summary = buildString {
            append("item ${index + 1} of ${post.items.size}: ")
            append("${files.size} files")
            files.mapNotNull(MediaCandidate::height).takeIf(List<Int>::isNotEmpty)
                ?.let { append(" (${it.joinToString("/")})") }
            append(", DASH ")
            append(if (manifest == null) "none" else FacebookDashOffers.summary(tracks))
        }
        if (candidates.isEmpty()) {
            val expired = (item.files.map(InstagramFile::url) +
                manifest?.tracks.orEmpty().map(FacebookDashTrack::url))
                .any { isExpired(it, now) }
            return SiteExtractionResult.Failure(
                if (expired) SiteExtractionFailure.EXPIRED_LINK else SiteExtractionFailure
                    .NO_MEDIA_FOUND,
                details = details + summary,
            )
        }
        return SiteExtractionResult.Success(candidates, details + summary)
    }

    /**
     * The manifest's picture sizes, each merged with the best AAC sound, and that sound alone
     * (P4's Facebook rows). A manifest without sound (a silent video) gives its pictures as they
     * are, without a stated size, so the resolver reads from each file's header that it has none.
     */
    private fun trackCandidates(
        tracks: List<FacebookDashTrack>,
        title: String?,
        item: InstagramItem,
        durationMillis: Long?,
        context: BrowserRequestContext,
        pageUrl: String,
        now: Long,
    ): List<MediaCandidate> {
        val videos = FacebookDashOffers.videos(tracks)
        val audio = FacebookDashOffers.audio(tracks)
        val audioBitrate = audio?.let { FacebookUrls.statedBitrate(it.url) }
            ?: audio?.bandwidthBitsPerSecond
        val companion = audio?.let {
            CompanionAudio(
                mediaUrl = it.url,
                mimeType = it.mimeType,
                codecs = listOf(it.codec),
                requestContext = context,
                contentLengthBytes = estimatedBytes(audioBitrate, durationMillis),
                bitrateBitsPerSecond = audioBitrate,
                expiresAtEpochMs = FacebookUrls.mediaExpiryEpochMs(it.url),
            )
        }
        val pictures = videos.map { video ->
            MediaCandidate(
                pageUrl = pageUrl,
                mediaUrl = video.url,
                sources = setOf(CandidateSource.MANIFEST),
                kind = MediaKind.DIRECT,
                mimeType = video.mimeType,
                title = title,
                thumbnailUrl = item.thumbnailUrl,
                durationMillis = durationMillis,
                requestContext = context,
                confidence = CandidateConfidence.HIGH,
                expiresAtEpochMs = listOfNotNull(
                    FacebookUrls.mediaExpiryEpochMs(video.url),
                    companion?.expiresAtEpochMs,
                ).minOrNull(),
                drmHint = false,
                observedAtEpochMs = now,
                codecs = if (companion != null) listOf(video.codec) else emptyList(),
                audioCompanion = companion,
                width = video.width.takeIf { companion != null },
                height = video.height.takeIf { companion != null },
                framesPerSecond = video.framesPerSecond.takeIf { companion != null },
                bitrateBitsPerSecond = FacebookUrls.statedBitrate(video.url),
            )
        }
        val sound = audio?.let {
            MediaCandidate(
                pageUrl = pageUrl,
                mediaUrl = it.url,
                sources = setOf(CandidateSource.MANIFEST),
                kind = MediaKind.DIRECT,
                mimeType = it.mimeType,
                title = title,
                thumbnailUrl = item.thumbnailUrl,
                durationMillis = durationMillis,
                requestContext = context,
                confidence = CandidateConfidence.HIGH,
                expiresAtEpochMs = companion?.expiresAtEpochMs,
                drmHint = false,
                observedAtEpochMs = now,
                codecs = listOf(it.codec),
                bitrateBitsPerSecond = audioBitrate,
            )
        }
        return pictures + listOfNotNull(sound)
    }

    private fun isExpired(url: String, nowEpochMs: Long): Boolean =
        FacebookUrls.mediaExpiryEpochMs(url)?.let { it <= nowEpochMs } == true

    private fun estimatedBytes(bitsPerSecond: Long?, durationMillis: Long?): Long? {
        val millis = durationMillis?.takeIf { it > 0 } ?: return null
        val bits = bitsPerSecond ?: return null
        return (bits * millis / BITS_PER_BYTE_MILLIS).takeIf { it > 0 }
    }

    /** The caption's first line, else "{name} on Instagram". */
    private fun displayTitle(post: InstagramPost): String? {
        val caption = post.caption?.lineSequence()?.map(String::trim)
            ?.firstOrNull(String::isNotEmpty)
            ?.let { if (it.length > MAX_TITLE) it.take(MAX_TITLE).trimEnd() + "…" else it }
        return caption
            ?: post.fullName?.trim()?.takeIf(String::isNotEmpty)?.let { "$it on Instagram" }
            ?: post.userName?.let { "@$it on Instagram" }
    }

    private fun graphQlUrl(code: String): String {
        val variables = "{\"shortcode\":\"$code\",\"fetch_tagged_user_count\":null," +
            "\"hoisted_comment_id\":null,\"hoisted_reply_id\":null}"
        return "$WEB/graphql/query/?doc_id=$GRAPHQL_DOC_ID&variables=" +
            URLEncoder.encode(variables, StandardCharsets.UTF_8.name())
    }

    /** The web app's own API headers; the session cookie and its CSRF token when signed in. */
    private fun apiHeaders(context: BrowserRequestContext, pageUrl: String) = buildMap {
        context.userAgent?.takeIf(String::isNotBlank)?.let { put("User-Agent", it) }
        context.cookie?.takeIf(String::isNotBlank)?.let { put("Cookie", it) }
        cookieValue(context.cookie, CSRF_COOKIE)?.let { put("X-CSRFToken", it) }
        put("Accept", "*/*")
        put("Accept-Language", "en-US,en;q=0.9")
        put("X-IG-App-ID", WEB_APP_ID)
        put("X-ASBD-ID", ASBD_ID)
        put("X-IG-WWW-Claim", "0")
        put("X-Requested-With", "XMLHttpRequest")
        put("Origin", WEB)
        put("Referer", pageUrl)
    }

    private fun pageHeaders(context: BrowserRequestContext, withCookie: Boolean) =
        PageNavigationHeaders.withDefaults(
            buildMap {
                context.userAgent?.takeIf(String::isNotBlank)?.let { put("User-Agent", it) }
                if (withCookie) {
                    context.cookie?.takeIf(String::isNotBlank)?.let { put("Cookie", it) }
                }
                put("Accept-Language", "en-US,en;q=0.9")
                put("Referer", "$WEB/")
            },
        )

    private fun cookieValue(cookie: String?, name: String): String? =
        cookie?.split(';')?.map(String::trim)
            ?.firstOrNull { it.startsWith("$name=") }
            ?.substringAfter('=')
            ?.takeIf { it.isNotBlank() && it.none(Char::isWhitespace) }

    companion object {
        const val DEFAULT_MAX_PAGE_BYTES: Long = 6L * 1024 * 1024
        private const val WEB = "https://www.instagram.com"
        private const val WEB_APP_ID = "936619743392459"
        private const val ASBD_ID = "129477"
        private const val GRAPHQL_DOC_ID = "8845758582119845"
        private const val SESSION_COOKIE = "sessionid"
        private const val CSRF_COOKIE = "csrftoken"
        private const val LOGIN_REQUIRED_MESSAGE = "login_required"
        private const val MP4_MIME_TYPE = "video/mp4"
        private const val BITS_PER_BYTE_MILLIS = 8_000L
        private const val MAX_TITLE = 80
        private val LOGIN_STATUSES = setOf(401, 403)
        private val LINE_FAILURES = setOf(
            SiteExtractionFailure.NETWORK,
            SiteExtractionFailure.RATE_LIMITED,
        )

        const val LOGIN_MESSAGE =
            "Instagram shows this post only to signed-in viewers. Log in to Instagram in " +
                "YFT's browser, then try again."
        const val SIGNED_IN_REFUSED_MESSAGE =
            "Instagram did not show this post to your account. Open it in YFT's browser " +
                "and play the video, then try again."
        const val PHOTOS_MESSAGE = "This post has photos only, no video."
    }
}
