package com.alal.yft.extractor.sites.vk

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

/**
 * VK adapter (VK_PLAN v1): public videos and clips through VK's own embed player page.
 *
 * Path B asks `video_ext.php` on the embed host that fits the page — vk.ru with a `*.vk.ru`
 * page's cookie, otherwise vk.com (with a `*.vk.com` page's cookie only) — and B2 asks the other
 * host, never with a cookie. VK's SPA shell is not an answer: the same host is asked once more.
 * At most three embed requests, and two manifest reads for an answer without MP4, all inside one
 * 15 s deadline (VK_PLAN §4.3–4.4).
 * No sign-in and no access-control bypass: VK's "Video not found." stays VK's answer.
 *
 * VK5 (path D, VK_PLAN §13): only when the embed answers that the video "may contain adult
 * content … can't be viewed on external sites" (VK's own site plays it signed out), the lookup
 * asks what vkvideo.ru's own player asks signed out — its anonymous token (login.vk.ru, kept in
 * memory only) and `video.get` on api.vkvideo.ru — inside the same deadline. It never sends the
 * page's cookie; when it finds nothing the answer is v1's.
 */
class VkExtractor(
    private val http: ExtractorHttpClient,
    private val clockMillis: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
) : SiteExtractor {
    override val id: String = VkUrls.SITE_ID

    override val displayName: String = "VK"

    /** Path D's anonymous token, in memory only (VK_PLAN §13). */
    private val tokens = VkApiTokenCache()

    override fun identify(pageUrl: String): SitePageIdentity? = VkUrls.identify(pageUrl)

    /** VK's video servers (`vkvd{N}.okcdn.ru`); images, stats and trackers never count. */
    override fun isPlayerMediaRequest(requestUrl: String): Boolean =
        VkUrls.isPlayerMediaUrl(requestUrl)

    override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult {
        require(request.identity.siteId == id) { "This adapter only handles VK identities" }
        val link = VkUrls.linkOf(request.identity.canonicalPageUrl)
            ?: return SiteExtractionResult.Failure(SiteExtractionFailure.UNSUPPORTED_URL)
        return Lookup(request, link).run()
    }

    private sealed interface Answer {
        data class Media(val video: VkVideo, val audioOnly: Boolean) : Answer

        data object Live : Answer

        data object NotFound : Answer

        /** VK5: the embed's adult-content answer; it opens path D. */
        data object Adult : Answer

        /** VK5: VK's API refused path D's token (error 5). */
        data object TokenRefused : Answer

        /** VK5: login.vk.ru gave no token, or VK's API another error (14: a captcha, …). */
        data object ApiRefused : Answer

        data object NoMedia : Answer

        data object Shell : Answer

        data object Changed : Answer

        data class Refused(val reason: SiteExtractionFailure, val statusCode: Int?) : Answer

        data object OutOfTime : Answer
    }

    /** One lookup: its deadline, its answers and its Details lines (never a URL or cookie). */
    private inner class Lookup(
        private val request: SiteExtractionRequest,
        private val link: VkUrls.VkLink,
    ) {
        private val startedAt = clockMillis()
        private val details = mutableListOf<String>()
        private val answers = mutableListOf<Answer>()

        private fun left(): Long = DEADLINE_MS - (clockMillis() - startedAt)

        suspend fun run(): SiteExtractionResult {
            val pageSite = VkUrls.cookieSiteOf(request.requestContext.pageUrl)
            val primary = if (pageSite == VkUrls.RU_HOST) VkUrls.RU_HOST else VkUrls.COM_HOST
            val other = if (primary == VkUrls.RU_HOST) VkUrls.COM_HOST else VkUrls.RU_HOST
            // The page's cookie goes only to the embed host of its own site, never across.
            val cookie = request.requestContext.cookie
                ?.takeIf { it.isNotBlank() && pageSite == primary }

            val first = ask("B", primary, cookie)
            (stop(first) ?: afterAdult(first))?.let { return it }
            // Only VK's SPA shell earns a second ask on the same host (429, 5xx and the rest go
            // straight to the other host); the other host gets one try.
            if (first == Answer.Shell) {
                val again = ask("B again", primary, cookie)
                (stop(again) ?: afterAdult(again))?.let { return it }
            }
            val second = ask("B2", other, cookie = null)
            (stop(second) ?: afterAdult(second))?.let { return it }
            return end()
        }

        /**
         * VK5: the embed's adult answer, on either host, opens path D; no further embed is asked
         * (both hosts gave the same answer, with and without `list`, at VK5's recon).
         */
        private suspend fun afterAdult(answer: Answer): SiteExtractionResult? =
            if (answer == Answer.Adult) pathD() else null

        /**
         * Path D (VK5): the anonymous token (kept in memory, else one new token) and `video.get`.
         * A token VK refuses (error 5) is dropped; for a kept one, one new token and one more
         * ask, if time allows. A video or a live video stops as in v1; anything else ends as v1
         * does ([end]: VK's "isn't available" message, or VK's own audio-only renditions).
         */
        private suspend fun pathD(): SiteExtractionResult {
            val kept = tokens.usable(request.nowEpochMs)
            val token = kept ?: newToken() ?: return end()
            var answer = videoGet(token, kept = kept != null)
            if (answer == Answer.TokenRefused) {
                tokens.drop(token)
                if (kept != null) {
                    val fresh = newToken() ?: return end()
                    answer = videoGet(fresh, kept = false)
                    if (answer == Answer.TokenRefused) tokens.drop(fresh)
                }
            }
            return stop(answer) ?: end()
        }

        /** One `get_anonym_token` request; null when VK gave no token (its line written). */
        private suspend fun newToken(): VkApiToken? {
            val sent = timed("vk: D token ${VkWebClientProfile.TOKEN_HOST}") {
                http.postJson(
                    VkWebClientProfile.TOKEN_URL,
                    VkWebClientProfile.tokenBody,
                    apiHeaders(token = null),
                    MAX_TOKEN_BYTES,
                )
            }
            if (sent is Sent.Unanswered) {
                answers += sent.answer
                return null
            }
            val answered = sent as Sent.Answered
            val token = VkApiTokenParser.parse(answered.page.body, request.nowEpochMs)
            if (token == null) {
                details += "${answered.head} · no token"
                answers += Answer.ApiRefused
                return null
            }
            tokens.keep(token, request.nowEpochMs)
            details += "${answered.head} · new token"
            return token
        }

        /** `video.get` for the link's video with [token] in the `Authorization` header. */
        private suspend fun videoGet(token: VkApiToken, kept: Boolean): Answer {
            val label = "vk: D video.get ${VkWebClientProfile.API_HOST}" +
                if (kept) " (token in memory)" else ""
            val sent = timed(label) {
                http.get(VkWebClientProfile.videoGetUrl(link), apiHeaders(token), MAX_API_BYTES)
            }
            val answer = when (sent) {
                is Sent.Unanswered -> sent.answer
                is Sent.Answered -> readApi(sent.head, sent.page)
            }
            answers += answer
            return answer
        }

        private suspend fun readApi(head: String, page: ExtractorHttpResult.Success): Answer =
            when (val api = VkEmbedParser.parseApi(page.body, link.ownerId, link.videoId)) {
                is VkApiAnswer.Found -> found(head, api.video)
                VkApiAnswer.Restricted -> Answer.NoMedia.also { details += "$head · restricted" }
                is VkApiAnswer.Error -> {
                    details += "$head · API error ${api.code ?: "unknown"}"
                    if (api.code == TOKEN_ERROR) Answer.TokenRefused else Answer.ApiRefused
                }

                is VkApiAnswer.Changed -> Answer.Changed.also { details += "$head · ${api.why}" }
            }

        /** One path D request inside the lookup's time; its line names no address or token. */
        private suspend fun timed(label: String, call: suspend () -> ExtractorHttpResult): Sent {
            currentCoroutineContext().ensureActive()
            val budget = minOf(REQUEST_MS, left())
            if (budget < MIN_REQUEST_MS) {
                details += "$label · not asked · out of time"
                return Sent.Unanswered(Answer.OutOfTime)
            }
            val askedAt = clockMillis()
            val result = withTimeoutOrNull(budget) { call() }
            val ms = clockMillis() - askedAt
            return when (result) {
                null -> {
                    details += "$label · timeout · $ms ms"
                    Sent.Unanswered(Answer.OutOfTime)
                }

                is ExtractorHttpResult.Failure -> {
                    details += "$label · ${statusOf(result)} · $ms ms · ${result.reason}" +
                        notesOf(result.details)
                    Sent.Unanswered(Answer.Refused(result.reason, result.statusCode))
                }

                is ExtractorHttpResult.Success -> Sent.Answered(
                    result,
                    "$label · ${result.statusCode} · $ms ms" + notesOf(result.details),
                )
            }
        }

        private suspend fun ask(path: String, host: String, cookie: String?): Answer {
            currentCoroutineContext().ensureActive()
            val label = "vk: $path $host" + if (cookie != null) " (page session)" else ""
            val budget = minOf(REQUEST_MS, left())
            if (budget < MIN_REQUEST_MS) {
                details += "$label · not asked · out of time"
                return Answer.OutOfTime.also(answers::add)
            }
            val askedAt = clockMillis()
            val result = withTimeoutOrNull(budget) {
                http.get(VkUrls.embedUrl(host, link), embedHeaders(cookie), MAX_EMBED_BYTES)
            }
            val ms = clockMillis() - askedAt
            val answer = when (result) {
                null -> Answer.OutOfTime.also { details += "$label · timeout · $ms ms" }
                is ExtractorHttpResult.Failure -> {
                    details += "$label · ${statusOf(result)} · $ms ms · ${result.reason}" +
                        notesOf(result.details)
                    Answer.Refused(result.reason, result.statusCode)
                }

                is ExtractorHttpResult.Success ->
                    read("$label · ${result.statusCode} · $ms ms" + notesOf(result.details), result)
            }
            answers += answer
            return answer
        }

        private suspend fun read(head: String, page: ExtractorHttpResult.Success): Answer =
            when (val embed = VkEmbedParser.parse(page.body, link.ownerId, link.videoId)) {
                VkEmbed.NotFound -> Answer.NotFound.also { details += "$head · not found" }
                VkEmbed.Adult -> Answer.Adult.also { details += "$head · adult content (embed)" }
                VkEmbed.Shell -> Answer.Shell.also { details += "$head · shell" }
                is VkEmbed.Changed -> Answer.Changed.also { details += "$head · ${embed.why}" }
                is VkEmbed.Found -> found(head, embed.video)
            }

        private suspend fun found(head: String, video: VkVideo): Answer {
            if (video.isLive) {
                details += "$head · live"
                return Answer.Live
            }
            if (video.renditions.isEmpty()) {
                return if (video.refusedAddresses > 0) {
                    details += "$head · files on unknown hosts: ${video.refusedAddresses}"
                    Answer.Changed
                } else {
                    details += "$head · no files"
                    Answer.NoMedia
                }
            }
            val counts = countsOf(video.renditions) + " (${video.foundBy})"
            if (video.renditions.any { it.kind == MediaKind.DIRECT }) {
                details += "$head · video: $counts"
                return Answer.Media(video, audioOnly = false)
            }
            details += "$head · manifests: $counts"
            return when (judge(video)) {
                VkManifestVerdict.AUDIO_ONLY -> Answer.Media(video, audioOnly = true)
                VkManifestVerdict.VIDEO -> Answer.Media(video, audioOnly = false)
                else -> {
                    // Manifests the judge cannot tell stay what VK names them: video.
                    details += "vk: manifests not judged"
                    Answer.Media(video, audioOnly = false)
                }
            }
        }

        /** Reads at most two manifests (one HLS, one DASH first); the first video one decides. */
        private suspend fun judge(video: VkVideo): VkManifestVerdict? {
            val manifests = video.renditions.filter { it.kind != MediaKind.DIRECT }
            val picks = (
                listOfNotNull(
                    manifests.firstOrNull { it.kind == MediaKind.HLS },
                    manifests.firstOrNull { it.kind == MediaKind.DASH },
                ) + manifests
                ).distinct().take(MAX_MANIFEST_READS)
            var verdict: VkManifestVerdict? = null
            for (manifest in picks) {
                currentCoroutineContext().ensureActive()
                val label = "vk: manifest ${manifest.key}"
                val budget = minOf(MANIFEST_MS, left())
                if (budget < MIN_REQUEST_MS) {
                    details += "$label · not asked · out of time"
                    break
                }
                val askedAt = clockMillis()
                val result = withTimeoutOrNull(budget) {
                    http.get(manifest.url, mediaContext().replayHeaders(), MAX_MANIFEST_BYTES)
                }
                val ms = clockMillis() - askedAt
                val read = when (result) {
                    null -> null.also { details += "$label · timeout · $ms ms" }
                    is ExtractorHttpResult.Failure -> null.also {
                        details += "$label · ${statusOf(result)} · $ms ms · ${result.reason}" +
                            notesOf(result.details)
                    }

                    is ExtractorHttpResult.Success -> VkJudge.judge(manifest.kind, result.body)
                        .also { verdict ->
                            details += "$label · ${result.statusCode} · $ms ms" +
                                notesOf(result.details) + " · ${verdict.label}"
                        }
                }
                when (read) {
                    VkManifestVerdict.VIDEO -> return VkManifestVerdict.VIDEO
                    VkManifestVerdict.AUDIO_ONLY -> verdict = VkManifestVerdict.AUDIO_ONLY
                    else -> Unit
                }
            }
            return verdict
        }

        /** A video answer or a live video ends the lookup; anything else lets it go on. */
        private fun stop(answer: Answer): SiteExtractionResult? = when {
            answer is Answer.Media && !answer.audioOnly -> success(answer.video)
            answer == Answer.Live -> SiteExtractionResult.Failure(
                SiteExtractionFailure.NO_MEDIA_FOUND,
                details = details.toList(),
                message = LIVE_MESSAGE,
            )

            else -> null
        }

        private fun end(): SiteExtractionResult {
            answers.firstNotNullOfOrNull { (it as? Answer.Media)?.takeIf(Answer.Media::audioOnly) }
                ?.let { partial ->
                    details += "vk: audio-only (partial)"
                    return success(partial.video)
                }
            return when {
                // VK's text, or (VK5) its adult answer when path D found nothing: v1's message.
                Answer.NotFound in answers || Answer.Adult in answers -> failure(
                    SiteExtractionFailure.NO_MEDIA_FOUND,
                    message = NOT_FOUND_MESSAGE,
                )

                Answer.NoMedia in answers -> failure(SiteExtractionFailure.NO_MEDIA_FOUND)
                // Shells on every try (or changed markup): a failure that lets Home read the page.
                Answer.Shell in answers || Answer.Changed in answers ->
                    failure(SiteExtractionFailure.RESPONSE_CHANGED)

                else -> answers.filterIsInstance<Answer.Refused>().lastOrNull()?.let { refused ->
                    failure(endpointReason(refused.reason), refused.statusCode)
                } ?: failure(SiteExtractionFailure.NETWORK, note = "vk: out of time")
            }
        }

        private fun failure(
            reason: SiteExtractionFailure,
            statusCode: Int? = null,
            message: String? = null,
            note: String? = null,
        ) = SiteExtractionResult.Failure(
            reason = reason,
            httpStatusCode = statusCode,
            details = details + listOfNotNull(note),
            message = message,
        )

        private fun success(video: VkVideo): SiteExtractionResult.Success {
            val pageUrl = request.identity.canonicalPageUrl
            val title = video.title ?: "VK video ${link.contentId}"
            val context = mediaContext()
            val candidates = video.renditions.map { rendition ->
                MediaCandidate(
                    pageUrl = pageUrl,
                    mediaUrl = rendition.url,
                    sources = setOf(CandidateSource.MANIFEST),
                    kind = rendition.kind,
                    mimeType = rendition.mimeType,
                    title = title,
                    thumbnailUrl = video.thumbnailUrl,
                    durationMillis = video.durationMillis,
                    requestContext = context,
                    confidence = CandidateConfidence.HIGH,
                    expiresAtEpochMs = rendition.expiresAtEpochMs,
                    drmHint = false,
                    observedAtEpochMs = request.nowEpochMs,
                    height = rendition.height,
                )
            }
            return SiteExtractionResult.Success(candidates, details.toList())
        }

        /** Media requests: the canonical page, the user agent, no cookie (VK0: none needed). */
        private fun mediaContext() = BrowserRequestContext(
            pageUrl = request.identity.canonicalPageUrl,
            userAgent = request.requestContext.userAgent?.takeIf(String::isNotBlank),
            cookie = null,
        )

        /**
         * Path D's requests (VK5): the browser's user agent (VK binds the files' `srcAg` to it, so
         * the media requests send the same), VK's web origin; never a cookie.
         */
        private fun apiHeaders(token: VkApiToken?): Map<String, String> = buildMap {
            request.requestContext.userAgent?.takeIf(String::isNotBlank)
                ?.let { put("User-Agent", it) }
            put("Accept", "*/*")
            put("Origin", VkWebClientProfile.ORIGIN)
            put("Referer", VkWebClientProfile.REFERER)
            token?.let { put("Authorization", "Bearer ${it.value}") }
        }

        private fun embedHeaders(cookie: String?): Map<String, String> =
            PageNavigationHeaders.withDefaults(
                buildMap {
                    request.requestContext.userAgent?.takeIf(String::isNotBlank)
                        ?.let { put("User-Agent", it) }
                    cookie?.let { put("Cookie", it) }
                },
            )
    }

    /** A path D request's outcome: VK's answer to read, or the answer it already is. */
    private sealed interface Sent {
        data class Answered(val page: ExtractorHttpResult.Success, val head: String) : Sent

        data class Unanswered(val answer: Answer) : Sent
    }

    private fun statusOf(failure: ExtractorHttpResult.Failure): String =
        failure.statusCode?.let { "HTTP $it" } ?: "error"

    /** The client's own short notes (an exception's class, headers left out) on the same line. */
    private fun notesOf(notes: List<String>): String =
        notes.take(MAX_NOTES).joinToString("") { " · $it" }

    /** One endpoint's HTTP status is not VK's verdict on the video (VK_PLAN §4.3). */
    private fun endpointReason(reason: SiteExtractionFailure): SiteExtractionFailure =
        when (reason) {
            SiteExtractionFailure.LOGIN_REQUIRED,
            SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
            SiteExtractionFailure.GEO_RESTRICTED,
            -> SiteExtractionFailure.HTTP_STATUS

            else -> reason
        }

    private fun countsOf(renditions: List<VkRendition>): String {
        val mp4 = renditions.filter { it.kind == MediaKind.DIRECT }
        return listOfNotNull(
            mp4.takeIf { it.isNotEmpty() }?.let { files ->
                "${files.size} mp4 up to ${files.maxOf { it.height ?: 0 }}p"
            },
            renditions.count { it.kind == MediaKind.HLS }.takeIf { it > 0 }?.let { "$it hls" },
            renditions.count { it.kind == MediaKind.DASH }.takeIf { it > 0 }?.let { "$it dash" },
        ).joinToString(", ")
    }

    companion object {
        const val DEADLINE_MS: Long = 15_000
        const val REQUEST_MS: Long = 8_000
        const val MANIFEST_MS: Long = 3_000
        const val MAX_EMBED_BYTES: Long = 1L * 1024 * 1024
        const val MAX_MANIFEST_BYTES: Long = 256L * 1024
        const val MAX_TOKEN_BYTES: Long = 16L * 1024
        const val MAX_API_BYTES: Long = 1L * 1024 * 1024
        const val NOT_FOUND_MESSAGE: String =
            "VK says this video isn't available. It may be deleted, private or need sign-in."
        const val LIVE_MESSAGE: String = "VK live streams can't be saved yet."

        private const val MIN_REQUEST_MS: Long = 250
        private const val MAX_MANIFEST_READS = 2
        private const val MAX_NOTES = 3

        /** VK's API error for a token it does not take ("User authorization failed"). */
        private const val TOKEN_ERROR = 5L
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
