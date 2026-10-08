package com.alal.yft.extractor.sites.tiktok

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageNavigationHeaders
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.ResponseCookie
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.SitePageIdentity
import java.net.URI
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * TikTok adapter for public, non-DRM posts.
 *
 * The adapter only reads the page TikTok already serves to the user's browser and replays the
 * user's own session context to tiktok.com. It performs no signing, no DRM handling and no
 * paywall or access-control bypass: a post the user cannot open in the browser fails with a
 * structured reason.
 *
 * P39 (R25–R31): the phone page is read with the WebView's own agent, the desktop page with
 * desktop Chrome whenever the phone page fails for a reason another page can change or lists
 * fewer than two qualities, and the answer with more qualities that really open wins. Every
 * listed quality is checked with a one-byte request before it becomes a row, so the sheet never
 * offers a file that answers 403. Each lookup keeps non-sensitive Details lines (page kinds,
 * data keys, file checks, hosts) whether it succeeds or not.
 */
class TikTokExtractor(
    private val http: ExtractorHttpClient,
    private val maxPageBytes: Long = DEFAULT_MAX_PAGE_BYTES,
    private val agents: TikTokAgents = TikTokAgents(),
    /** Whether the desktop page may be asked when the phone page is not enough. */
    private val askDesktopPage: Boolean = true,
) : SiteExtractor {
    override val id: String = TikTokUrls.SITE_ID

    override val displayName: String = "TikTok"

    override fun identify(pageUrl: String): SitePageIdentity? = TikTokUrls.identify(pageUrl)

    override fun isPlayerMediaRequest(requestUrl: String): Boolean =
        TikTokUrls.isPlayerMedia(requestUrl)

    override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult {
        require(request.identity.siteId == id) { "This adapter only handles TikTok identities" }
        val lookup = Lookup(request)
        return try {
            lookup.run()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            // R25: an unexpected error is named by its class and step, never "changed format".
            SiteExtractionResult.Failure(
                reason = SiteExtractionFailure.MALFORMED_RESPONSE,
                details = lookup.details +
                    "error: ${failure.javaClass.simpleName.ifEmpty { "Exception" }} " +
                    "at step ${lookup.step}",
            )
        }
    }

    private enum class PageAgent(val label: String) { PHONE("phone"), DESKTOP("desktop") }

    private sealed interface PageRead {
        val which: PageAgent

        class Parsed(
            override val which: PageAgent,
            val post: TikTokPost,
            val userAgent: String?,
            /** The lookup's cookies when this answer arrived; its addresses need them. */
            val cookies: List<ResponseCookie>,
        ) : PageRead

        class Failed(
            override val which: PageAgent,
            val reason: SiteExtractionFailure,
            val statusCode: Int?,
            val identity: SitePageIdentity,
            val final: Boolean,
            val message: String? = null,
        ) : PageRead
    }

    private class Working(val quality: TikTokQuality, val address: String, val totalBytes: Long?)

    private class Checked(val answer: PageRead.Parsed, val working: List<Working>)

    /** One quality's file check: its addresses tried in order until one answers. */
    private class QualityCheck(val quality: TikTokQuality) {
        var next = 0
        var found: String? = null
        var totalBytes: Long? = null
        var unsupported = false
        val trail = mutableListOf<String>()

        val pending: Boolean
            get() = found == null && !unsupported && next < quality.addresses.size
    }

    private inner class Lookup(private val request: SiteExtractionRequest) {
        val details = mutableListOf<String>()
        var step = "start"
        private val context = request.requestContext

        /** R29: every TikTok cookie this lookup's answers set; a later value replaces older. */
        private val jar = LinkedHashMap<Triple<String, String, String>, ResponseCookie>()
        private var checksLeft = MAX_FILE_CHECKS

        suspend fun run(): SiteExtractionResult {
            step = "agent"
            val phoneAgent = agents.phone(context.userAgent)
            step = "phone page"
            val phone = readPage(PageAgent.PHONE, request.identity, phoneAgent)
            if (phone is PageRead.Failed && phone.final) return failure(phone)

            val desktopAgent = agents.desktop(phoneAgent)
            val wantsDesktop = askDesktopPage && desktopAgent != phoneAgent &&
                (phone !is PageRead.Parsed || phone.post.qualities.size < 2)
            val desktop = if (wantsDesktop) {
                step = "desktop page"
                val identity = when (phone) {
                    is PageRead.Parsed -> TikTokUrls.identify(
                        TikTokUrls.canonicalUrl(phone.post.authorHandle, phone.post.videoId),
                    ) ?: request.identity
                    is PageRead.Failed -> phone.identity
                }
                readPage(PageAgent.DESKTOP, identity, desktopAgent)
            } else {
                null
            }

            val answers = listOfNotNull(phone as? PageRead.Parsed, desktop as? PageRead.Parsed)
                .sortedByDescending { it.post.qualities.size }
            if (answers.isEmpty()) {
                val failures = listOfNotNull(phone as? PageRead.Failed, desktop as? PageRead.Failed)
                return failure(
                    failures.firstOrNull { it.final }
                        ?: failures.firstOrNull { it.reason !in GENERAL_REASONS }
                        ?: failures.first(),
                )
            }

            step = "file check"
            var best: Checked? = null
            for (answer in answers) {
                val known = best
                if (known != null && known.working.size >= answer.post.qualities.size) continue
                val checked = check(answer)
                if (known == null || checked.working.size > known.working.size) best = checked
            }
            val chosen = checkNotNull(best)
            val rows = chosen.working.ifEmpty { watermarked(answers) }
            if (rows.isEmpty()) {
                details += "answer: none · no file opened"
                return SiteExtractionResult.Failure(
                    reason = SiteExtractionFailure.NO_MEDIA_FOUND,
                    details = details.toList(),
                    message = FILES_REFUSED_MESSAGE,
                )
            }
            val answer = if (chosen.working.isEmpty()) {
                answers.first { it.post.watermarked != null }
            } else {
                chosen.answer
            }
            details += "answer: ${answer.which.label} · " + if (chosen.working.isEmpty()) {
                "watermarked file only"
            } else {
                "${rows.size} working qualities"
            }
            details += "hosts: " + rows.mapNotNull { hostOf(it.address) }.distinct()
                .joinToString(", ")
            step = "rows"
            return SiteExtractionResult.Success(
                candidates = rows.map { candidate(answer, it) },
                details = details.toList(),
            )
        }

        private suspend fun readPage(
            which: PageAgent,
            identity: SitePageIdentity,
            userAgent: String?,
        ): PageRead {
            val url = identity.canonicalPageUrl
            val response = when (
                val result = http.get(url, pageHeaders(userAgent, cookieFor(url)), maxPageBytes)
            ) {
                is ExtractorHttpResult.Failure -> {
                    val status = result.statusCode?.let { "HTTP $it" } ?: "no answer"
                    details += "page: ${which.label} · $status · ${result.reason}"
                    details += result.details
                    return PageRead.Failed(
                        which = which,
                        reason = result.reason,
                        statusCode = result.statusCode,
                        identity = identity,
                        final = false,
                    )
                }

                is ExtractorHttpResult.Success -> result
            }
            response.cookies.filter { TikTokUrls.isTikTokDomain(it.domain) }.forEach { cookie ->
                jar[Triple(cookie.name, cookie.domain, cookie.path)] = cookie
            }
            val resolved = resolvedIdentity(identity, response.finalUrl)
            val size = "${response.body.encodeToByteArray().size / 1_024} KB"
            if (TikTokUrls.isPhotoPost(resolved.canonicalPageUrl)) {
                details += "page: ${which.label} · HTTP ${response.statusCode} · $size · " +
                    "landed on: photo post"
                details += response.details
                return PageRead.Failed(
                    which = which,
                    reason = SiteExtractionFailure.NO_MEDIA_FOUND,
                    statusCode = null,
                    identity = resolved,
                    final = true,
                )
            }
            val parsed = TikTokPageParser.parse(
                html = response.body,
                finalUrl = response.finalUrl,
                expectedVideoId = resolved.contentId
                    .takeUnless { resolved.requiresCanonicalResolution },
            )
            val notes = parsed.notes
            details += "page: ${which.label} · HTTP ${response.statusCode} · $size · " +
                "landed on: ${notes.kind.label}"
            details += response.details
            details += "data: ${notes.dataKey}" + notes.json?.let { " · JSON: $it" }.orEmpty()
            details += "post id: ${notes.postId}"
            return when (parsed) {
                is TikTokParseResult.Failure -> PageRead.Failed(
                    which = which,
                    reason = parsed.reason,
                    statusCode = null,
                    identity = resolved,
                    final = parsed.final,
                    message = parsed.message,
                )

                is TikTokParseResult.Success -> {
                    val post = parsed.post
                    val labels = post.qualities.joinToString(", ") { it.label }
                    details += "qualities: ${post.qualities.size}" +
                        (if (labels.isEmpty()) "" else " ($labels)") +
                        " · play address: ${yesNo(post.hasPlayAddress)}" +
                        " · download address: ${yesNo(post.watermarked != null)}"
                    PageRead.Parsed(which, post, userAgent, jar.values.toList())
                }
            }
        }

        /**
         * R30: rounds of one-byte checks, the first address of every quality first, then the
         * next address of those that were refused, at most [MAX_FILE_CHECKS] per lookup and
         * [MAX_PARALLEL_CHECKS] at a time. One check is kept back for the watermarked file.
         */
        private suspend fun check(answer: PageRead.Parsed): Checked {
            val checks = answer.post.qualities.map(::QualityCheck)
            val reserve = if (answer.post.watermarked != null) 1 else 0
            while (true) {
                val room = checksLeft - reserve
                val round = checks.filter { it.pending }.take(room.coerceAtLeast(0))
                if (round.isEmpty()) break
                checksLeft -= round.size
                probeAll(answer, round)
            }
            val unsupported = checks.any { it.unsupported }
            if (unsupported) {
                details += "file check: not available"
                return Checked(
                    answer,
                    answer.post.qualities.map { Working(it, it.addresses.first(), null) },
                )
            }
            val tried = checks.filter { it.trail.isNotEmpty() }
            if (tried.isNotEmpty()) {
                details += "file check (${answer.which.label}): " + tried.joinToString(" · ") {
                    "${it.quality.label} ${it.trail.joinToString(" → ")}" +
                        if (it.found == null) " (left out)" else ""
                }
            }
            return Checked(
                answer,
                checks.mapNotNull { check ->
                    check.found?.let { Working(check.quality, it, check.totalBytes) }
                },
            )
        }

        private suspend fun probeAll(answer: PageRead.Parsed, round: List<QualityCheck>) {
            val permits = Semaphore(MAX_PARALLEL_CHECKS)
            coroutineScope {
                round.map { check ->
                    async { permits.withPermit { probe(answer, check) } }
                }.awaitAll()
            }
        }

        private suspend fun probe(answer: PageRead.Parsed, check: QualityCheck) {
            val address = check.quality.addresses[check.next]
            when (
                val result = http.probe(address, mediaHeaders(answer, address), PROBE_TIMEOUT_MILLIS)
            ) {
                is ExtractorProbeResult.Answered -> {
                    check.found = address
                    check.totalBytes = result.totalBytes
                    check.trail += result.statusCode.toString()
                }

                is ExtractorProbeResult.Refused -> check.trail +=
                    result.statusCode?.toString() ?: result.error ?: result.reason.name

                ExtractorProbeResult.Unsupported -> check.unsupported = true
            }
            if (!check.unsupported) check.next += 1
        }

        /** R31 (TT_WATERMARK=HIDE): the download address only when no other file opened. */
        private suspend fun watermarked(answers: List<PageRead.Parsed>): List<Working> {
            val answer = answers.firstOrNull { it.post.watermarked != null } ?: return emptyList()
            val quality = checkNotNull(answer.post.watermarked)
            val check = QualityCheck(quality)
            while (check.pending && checksLeft > 0) {
                checksLeft -= 1
                probe(answer, check)
            }
            if (check.unsupported) return listOf(Working(quality, quality.addresses.first(), null))
            if (check.trail.isNotEmpty()) {
                details += "file check (${answer.which.label}): ${quality.label} " +
                    check.trail.joinToString(" → ") + if (check.found == null) " (left out)" else ""
            }
            return listOfNotNull(check.found?.let { Working(quality, it, check.totalBytes) })
        }

        private fun candidate(answer: PageRead.Parsed, working: Working): MediaCandidate {
            val post = answer.post
            val quality = working.quality
            val pageUrl = TikTokUrls.canonicalUrl(post.authorHandle, post.videoId)
            return MediaCandidate(
                pageUrl = pageUrl,
                mediaUrl = working.address,
                sources = setOf(CandidateSource.MANIFEST),
                kind = MediaKind.DIRECT,
                mimeType = MP4_MIME_TYPE,
                title = displayTitle(post, quality.label),
                thumbnailUrl = post.thumbnailUrl,
                durationMillis = post.durationMillis,
                contentLengthBytes = working.totalBytes ?: quality.sizeBytes,
                requestContext = BrowserRequestContext(
                    pageUrl = pageUrl,
                    userAgent = answer.userAgent,
                    cookie = mediaCookie(answer, working.address),
                ),
                confidence = CandidateConfidence.HIGH,
                drmHint = false,
                observedAtEpochMs = request.nowEpochMs,
                codecs = listOfNotNull(quality.codec.codecTag),
                width = quality.width,
                height = quality.height,
                bitrateBitsPerSecond = quality.bitrateBitsPerSecond,
            )
        }

        private fun failure(read: PageRead.Failed) = SiteExtractionResult.Failure(
            reason = read.reason,
            httpStatusCode = read.statusCode,
            details = details.toList(),
            message = read.message,
        )

        /** The tab's cookies with this lookup's own answers' cookies for [url] put in. */
        private fun cookieFor(url: String): String? =
            mergedCookie(context.cookie, jar.values.filter { it.matches(url) })

        /**
         * TikTok's media host answers only with the cookies of the page answer that gave the
         * address (`tt_chain_token`; R20): the tab's cookie header with that answer's cookies a
         * browser would send to [url] put in. The resolver and the downloader send them to the
         * media URL's own origin only and drop them on any cross-origin redirect.
         */
        private fun mediaCookie(answer: PageRead.Parsed, url: String): String? =
            mergedCookie(context.cookie, answer.cookies.filter { it.matches(url) })

        private fun mediaHeaders(answer: PageRead.Parsed, url: String): Map<String, String> =
            buildMap {
                answer.userAgent?.takeIf(String::isNotBlank)?.let { put("User-Agent", it) }
                put("Referer", TIKTOK_REFERER)
                mediaCookie(answer, url)?.let { put("Cookie", it) }
            }
    }

    /**
     * Rebuilds the identity from the final URL after redirects.
     *
     * Short links only become addressable here, and a redirect that leaves TikTok is treated as a
     * changed response rather than being followed blindly into another site's content.
     */
    private fun resolvedIdentity(
        identity: SitePageIdentity,
        finalUrl: String,
    ): SitePageIdentity {
        if (!identity.requiresCanonicalResolution) return identity
        return TikTokUrls.identify(finalUrl)?.takeUnless { it.requiresCanonicalResolution }
            ?: identity
    }

    /** Title stays metadata-only: the caption, the author handle, then the quality label. */
    private fun displayTitle(post: TikTokPost, label: String?): String? {
        val base = post.title?.trim()?.takeIf(String::isNotEmpty)
            ?: post.authorHandle?.let { "TikTok @$it" }
            ?: return label
        return if (label.isNullOrBlank()) base else "$base — $label"
    }

    private fun pageHeaders(userAgent: String?, cookie: String?): Map<String, String> =
        PageNavigationHeaders.withDefaults(
            buildMap {
                userAgent?.takeIf(String::isNotBlank)?.let { put("User-Agent", it) }
                cookie?.takeIf(String::isNotBlank)?.let { put("Cookie", it) }
                put("Accept-Language", "en-US,en;q=0.9")
                put("Referer", TIKTOK_REFERER)
            },
        )

    private fun yesNo(value: Boolean) = if (value) "yes" else "no"

    private fun hostOf(url: String): String? =
        runCatching { URI(url).host?.lowercase(Locale.US) }.getOrNull()

    companion object {
        const val DEFAULT_MAX_PAGE_BYTES: Long = 3L * 1024 * 1024
        const val MAX_FILE_CHECKS = 8
        const val MAX_PARALLEL_CHECKS = 3
        const val PROBE_TIMEOUT_MILLIS = 5_000L
        const val FILES_REFUSED_MESSAGE =
            "TikTok did not let YFT open this video's files. Tap Details to see why, or Try again."
        private const val MP4_MIME_TYPE = "video/mp4"
        private const val TIKTOK_REFERER = "https://www.tiktok.com/"

        /** Reasons that say little about the post, so another answer's reason is preferred. */
        private val GENERAL_REASONS = setOf(
            SiteExtractionFailure.RESPONSE_CHANGED,
            SiteExtractionFailure.NETWORK,
            SiteExtractionFailure.MALFORMED_RESPONSE,
            SiteExtractionFailure.HTTP_STATUS,
        )

        /**
         * The [header]'s cookies with the [answer]'s ones put in: same-named pairs are replaced
         * in place, new ones are added at the end. Null when nothing is left.
         */
        internal fun mergedCookie(header: String?, answer: List<ResponseCookie>): String? {
            val pairs = LinkedHashMap<String, String>()
            header.orEmpty().split(';').map(String::trim).filter(String::isNotEmpty)
                .forEach { pair -> pairs.putIfAbsent(pair.substringBefore('=').trim(), pair) }
            answer.forEach { cookie -> pairs[cookie.name] = cookie.pair }
            return pairs.values.joinToString("; ").takeIf(String::isNotEmpty)
        }
    }
}
