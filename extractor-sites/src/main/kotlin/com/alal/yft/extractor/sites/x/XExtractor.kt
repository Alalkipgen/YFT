package com.alal.yft.extractor.sites.x

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.SitePageIdentity

/**
 * P48: X (Twitter) adapter for a public post's video.
 *
 * X's own pages build their player with scripts and a signed-in API, so the adapter reads the
 * public answer X's embedded-post widget reads ([XSyndication]): the post's MP4 files, each a
 * whole file with its sound, at every size X made (320p to 1080p). It sends no X cookie: the
 * answer is public, and its host is not x.com. A post the answer does not show (age-restricted,
 * protected, deleted) or that has photos only fails with a reason that keeps the generic
 * detector, so the browser's own finds still count.
 */
class XExtractor(
    private val http: ExtractorHttpClient,
    private val maxAnswerBytes: Long = DEFAULT_MAX_ANSWER_BYTES,
) : SiteExtractor {
    override val id: String = XUrls.SITE_ID

    override val displayName: String = "X"

    override fun identify(pageUrl: String): SitePageIdentity? = XUrls.identify(pageUrl)

    override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult {
        require(request.identity.siteId == id) { "This adapter only handles X identities" }
        val identity = request.identity
        val answer = when (
            val result = http.get(
                url = XSyndication.url(identity.contentId),
                headers = answerHeaders(request.requestContext),
                maxBodyBytes = maxAnswerBytes,
            )
        ) {
            is ExtractorHttpResult.Failure -> return failed(result)
            is ExtractorHttpResult.Success -> result
        }
        val read = "embed answer GET ${answer.statusCode} (${answer.body.length} characters)"
        val post = when (val parsed = XSyndication.parse(answer.body)) {
            is XParseResult.Failure -> return SiteExtractionResult.Failure(
                reason = when {
                    parsed.unavailable || parsed.photos -> SiteExtractionFailure.NO_MEDIA_FOUND
                    else -> SiteExtractionFailure.RESPONSE_CHANGED
                },
                details = listOf(read, parsed.detail),
                message = when {
                    parsed.photos -> PHOTOS_MESSAGE
                    parsed.unavailable -> HIDDEN_MESSAGE
                    else -> null
                },
            )

            is XParseResult.Success -> parsed.post
        }
        val wanted = XUrls.videoNumberOf(identity.canonicalPageUrl)
        val video = post.videos.firstOrNull { !post.quoted && it.position == wanted }
            ?: post.videos.first()
        val pageUrl = identity.canonicalPageUrl
        val candidates = candidatesOf(post, video, pageUrl, request)
        val choice = buildString {
            append("answer: ")
            append(if (post.quoted) "the quoted post's " else "")
            append(if (video.type == XMediaType.GIF) "GIF" else "video")
            append(" ${video.position}, ${post.videos.size} in the post")
            append(" · ${video.variants.size} MP4 files")
            video.variants.mapNotNull(XVariant::height).takeIf(List<Int>::isNotEmpty)
                ?.let { heights -> append(" (${heights.joinToString("/")})") }
        }
        return SiteExtractionResult.Success(candidates, listOf(read, choice))
    }

    /**
     * One row per MP4 file; each states its picture from its address (`/vid/720x1280/`), so the
     * resolver only asks the CDN for its length. A GIF's file states nothing: the resolver reads
     * its header, which finds that it has no sound. The HLS playlist is a row only when X listed
     * no MP4 file.
     */
    private fun candidatesOf(
        post: XPost,
        video: XVideo,
        pageUrl: String,
        request: SiteExtractionRequest,
    ): List<MediaCandidate> {
        val context = BrowserRequestContext(
            pageUrl = pageUrl,
            userAgent = request.requestContext.userAgent,
            cookie = null,
        )
        val gif = video.type == XMediaType.GIF
        val files = video.variants.map { variant ->
            MediaCandidate(
                pageUrl = pageUrl,
                mediaUrl = variant.url,
                sources = setOf(CandidateSource.MANIFEST),
                kind = MediaKind.DIRECT,
                mimeType = MP4_MIME_TYPE,
                title = displayTitle(post),
                thumbnailUrl = video.thumbnailUrl,
                durationMillis = video.durationMillis,
                requestContext = context,
                confidence = CandidateConfidence.HIGH,
                drmHint = false,
                observedAtEpochMs = request.nowEpochMs,
                width = variant.width.takeUnless { gif },
                height = variant.height.takeUnless { gif },
                bitrateBitsPerSecond = variant.bitrateBitsPerSecond,
            )
        }
        if (files.isNotEmpty()) return files
        val playlist = requireNotNull(video.playlistUrl) { "A parsed video has a file" }
        return listOf(
            MediaCandidate(
                pageUrl = pageUrl,
                mediaUrl = playlist,
                sources = setOf(CandidateSource.MANIFEST),
                kind = MediaKind.HLS,
                mimeType = HLS_MIME_TYPE,
                title = displayTitle(post),
                thumbnailUrl = video.thumbnailUrl,
                durationMillis = video.durationMillis,
                requestContext = context,
                confidence = CandidateConfidence.HIGH,
                drmHint = false,
                observedAtEpochMs = request.nowEpochMs,
            ),
        )
    }

    private fun failed(result: ExtractorHttpResult.Failure): SiteExtractionResult.Failure {
        val missing = result.statusCode == HTTP_NOT_FOUND
        return SiteExtractionResult.Failure(
            reason = if (missing) SiteExtractionFailure.NO_MEDIA_FOUND else result.reason,
            httpStatusCode = result.statusCode,
            details = listOfNotNull(
                "embed answer GET failed (${result.reason}" +
                    (result.statusCode?.let { " $it" } ?: "") + ")",
            ) + result.details,
            message = if (missing) HIDDEN_MESSAGE else null,
        )
    }

    /** The post's first line without its trailing link, else "{name} on X". */
    private fun displayTitle(post: XPost): String? {
        val text = post.text?.replace(TRAILING_LINK, "")?.lineSequence()
            ?.map(String::trim)?.firstOrNull(String::isNotEmpty)
            ?.let { if (it.length > MAX_TITLE) it.take(MAX_TITLE).trimEnd() + "…" else it }
        return text
            ?: post.userName?.trim()?.takeIf(String::isNotEmpty)?.let { "$it on X" }
            ?: post.screenName?.let { "@$it on X" }
    }

    private fun answerHeaders(context: BrowserRequestContext): Map<String, String> = buildMap {
        context.userAgent?.takeIf(String::isNotBlank)?.let { put("User-Agent", it) }
        put("Accept", "application/json, text/plain;q=0.9")
        put("Accept-Language", "en-US,en;q=0.9")
        put("Origin", "https://platform.twitter.com")
        put("Referer", "https://platform.twitter.com/")
    }

    companion object {
        const val DEFAULT_MAX_ANSWER_BYTES: Long = 2L * 1024 * 1024
        private const val MP4_MIME_TYPE = "video/mp4"
        private const val HLS_MIME_TYPE = "application/vnd.apple.mpegurl"
        private const val HTTP_NOT_FOUND = 404
        private const val MAX_TITLE = 80
        private val TRAILING_LINK = Regex("""\s*https://t\.co/\S+\s*$""")

        const val HIDDEN_MESSAGE =
            "X shows this post only to signed-in viewers (it may be protected, age-restricted " +
                "or deleted). Open it in YFT's browser, sign in to X and play the video."
        const val PHOTOS_MESSAGE = "This post has photos only, no video."
    }
}
