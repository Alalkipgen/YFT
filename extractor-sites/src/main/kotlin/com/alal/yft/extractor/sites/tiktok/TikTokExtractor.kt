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
import com.alal.yft.extractor.api.SitePageData
import com.alal.yft.extractor.api.SitePageDataSource
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
 *
 * P40: given the post's data a page already holds ([SiteExtractionRequest.pageData]: the
 * user's tab or YFT's hidden page, the way TikTok's own player gets it), the rows come from that
 * data without asking for the page; the page is read only when the data names another post or
 * none of its files opens (never after the hidden page, which comes after the page read).
 *
 * P46: TikTok's status for the post on the phone page ("private" for some public posts the
 * phone page does not show) no longer ends the lookup: the desktop page is asked too, and
 * Details name the status. A tab's data with fewer than two qualities (TikTok's phone player
 * gets one) is joined by the desktop page's, and the answer with more working files wins.
 *
 * P47: answers are joined instead of one winning: a later answer adds the working files of a
 * height (and codec) the earlier ones did not list, so the phone page's 540p and the desktop
 * page's 720p are both rows. Files already listed are not checked again.
 */
class TikTokExtractor(
    private val http: ExtractorHttpClient,
    private val maxPageBytes: Long = DEFAULT_MAX_PAGE_BYTES,
    private val agents: TikTokAgents = TikTokAgents(),
    /** Whether the desktop page may be asked when the phone page is not enough. */
    private val askDesktopPage: Boolean = true,
    /**
     * P46 (owner's choice B, `TT_SERVICE=ON`): whether a public download service is asked
     * when TikTok's own pages give no file. Only the post's address goes there.
     */
    private val askDownloadService: Boolean = false,
    private val serviceEndpoint: String = TikTokDownloadService.ENDPOINT,
) : SiteExtractor {
    private val service = TikTokDownloadService(http, serviceEndpoint)

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
            val name = failure.javaClass.simpleName.ifEmpty { "Exception" }
            SiteExtractionResult.Failure(
                reason = SiteExtractionFailure.MALFORMED_RESPONSE,
                details = lookup.details + "error: $name at step ${lookup.step}",
            )
        }
    }

    private enum class PageAgent(val label: String) {
        PHONE("phone"),
        DESKTOP("desktop"),

        /** P40: the post's data from the user's tab. */
        TAB("tab"),

        /** P40: the post's data from YFT's hidden page. */
        HIDDEN("hidden page"),

        /** P46: the public download service; its files never get TikTok's cookies. */
        SERVICE(TikTokDownloadService.LABEL),
    }

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

    /** P47: a working file with the answer whose agent and cookies it needs. */
    private class Row(val answer: PageRead.Parsed, val working: Working)

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
            request.pageData?.let { data -> fromPageData(data)?.let { return it } }
            return readPages()
        }

        /**
         * P40: rows from the post's data a page already holds, checked like a page answer's
         * (P39 step 6) with the lookup's cookies and Referer `https://www.tiktok.com/`. Null when
         * the page should be read instead: the data names another post, cannot be read, or none
         * of its files opens. Data from the hidden page is the last word: the page was read
         * before it, so its failure is the lookup's.
         */
        private suspend fun fromPageData(data: SitePageData): SiteExtractionResult? {
            step = "page data"
            val label = data.source.label
            val hidden = data.source == SitePageDataSource.HIDDEN_PAGE
            val expected = request.identity.contentId
                .takeUnless { request.identity.requiresCanonicalResolution }
            if (expected == null) {
                details += "data: $label · not used (no post id in the link)"
                return null
            }
            val parsed = TikTokPageParser.parseItem(data.json, expected, label)
            val notes = parsed.notes
            details += "data: $label" + notes.json?.let { " · JSON: $it" }.orEmpty()
            details += "post id: ${notes.postId}"
            val post = when (parsed) {
                is TikTokParseResult.Failure -> {
                    if (parsed.final) {
                        return SiteExtractionResult.Failure(
                            reason = parsed.reason,
                            details = details.toList(),
                            message = parsed.message,
                        )
                    }
                    details += "data: not used" + if (hidden) "" else " · page read next"
                    return if (hidden) {
                        SiteExtractionResult.Failure(parsed.reason, details = details.toList())
                    } else {
                        null
                    }
                }

                is TikTokParseResult.Success -> parsed.post
            }
            val labels = post.qualities.joinToString(", ") { it.label }
            details += "qualities: ${post.qualities.size}" +
                (if (labels.isEmpty()) "" else " ($labels)") +
                " · play address: ${yesNo(post.hasPlayAddress)}" +
                " · download address: ${yesNo(post.watermarked != null)}"
            val which = if (hidden) PageAgent.HIDDEN else PageAgent.TAB
            val answer = PageRead.Parsed(which, post, context.userAgent, cookies = emptyList())
            step = "file check"
            val checked = check(answer)
            // One working height (P47: H.264 and H.265 of one height are one): the desktop page
            // may list more (none: the page read below asks it anyway).
            if (!hidden && checked.working.isNotEmpty() && heights(checked.working) < 2) {
                moreQualities(post, checked)?.let { joined ->
                    return success(joined, watermarkedOnly = false)
                }
            }
            val rows = checked.working.ifEmpty { watermarked(listOf(answer)) }
            if (rows.isNotEmpty()) return success(answer, rows, checked.working.isEmpty())
            details += "data: no file opened" + if (hidden) "" else " · page read next"
            if (hidden) {
                return SiteExtractionResult.Failure(
                    reason = SiteExtractionFailure.NO_MEDIA_FOUND,
                    details = details.toList(),
                    message = FILES_REFUSED_MESSAGE,
                )
            }
            // The page's answer gets its own file checks.
            checksLeft = MAX_FILE_CHECKS
            return null
        }

        /**
         * P46: the desktop page for a tab's [post] whose data opened one file ([fromTab]).
         * P47: the tab's row joined by the desktop page's working files of other heights, or
         * null when it has none (the tab's row stays).
         */
        private suspend fun moreQualities(post: TikTokPost, fromTab: Checked): List<Row>? {
            if (!askDesktopPage) return null
            step = "agent"
            val phoneAgent = agents.phone(context.userAgent)
            val desktopAgent = agents.desktop(phoneAgent)
            if (desktopAgent == phoneAgent) return null
            details += "desktop page: asked for more qualities " +
                "(tab: ${fromTab.working.size} working)"
            step = "desktop page"
            val identity = TikTokUrls.identify(
                TikTokUrls.canonicalUrl(post.authorHandle, post.videoId),
            )?.takeUnless { it.requiresCanonicalResolution } ?: request.identity
            val desktop = readPage(PageAgent.DESKTOP, identity, desktopAgent)
                as? PageRead.Parsed ?: return null
            val tabRows = fromTab.working.map { Row(fromTab.answer, it) }
            if (desktop.post.videoId != post.videoId ||
                desktop.post.qualities.all { tabRows.lists(it) }
            ) {
                details += "desktop page: no other quality"
                return null
            }
            step = "file check"
            checksLeft = MAX_FILE_CHECKS
            val more = joinedTo(tabRows, check(desktop, tabRows))
            return more.takeIf { it.size > tabRows.size }
        }

        private suspend fun readPages(): SiteExtractionResult {
            step = "agent"
            val phoneAgent = agents.phone(context.userAgent)
            step = "phone page"
            val phone = readPage(PageAgent.PHONE, request.identity, phoneAgent)
            if (phone is PageRead.Failed && phone.final) return failure(phone)

            val desktopAgent = agents.desktop(phoneAgent)
            // P47: two codecs of one height are still one quality to the user.
            val wantsDesktop = askDesktopPage && desktopAgent != phoneAgent &&
                (phone !is PageRead.Parsed || heightsOf(phone.post.qualities) < 2)
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
                // P46: a check on one page says more than TikTok's status on the other: the
                // post may open once the check is answered.
                val chosen = failures.firstOrNull { it.final }
                    ?: failures.firstOrNull { it.reason == SiteExtractionFailure.BOT_CHECK }
                    ?: failures.firstOrNull { it.reason !in GENERAL_REASONS }
                    ?: failures.first()
                if (chosen.final) return failure(chosen)
                return fromService(chosen.identity, failure(chosen))
            }

            step = "file check"
            // P47: every answer adds the working files of heights the earlier ones lack.
            var joined = emptyList<Row>()
            for (answer in answers) {
                if (answer.post.qualities.all { joined.lists(it) }) continue
                joined = joinedTo(joined, check(answer, joined))
            }
            if (joined.isNotEmpty()) return success(joined, watermarkedOnly = false)
            val rows = watermarked(answers)
            if (rows.isEmpty()) {
                details += "answer: none · no file opened"
                val post = answers.first().post
                val identity = TikTokUrls.identify(
                    TikTokUrls.canonicalUrl(post.authorHandle, post.videoId),
                )?.takeUnless { it.requiresCanonicalResolution } ?: request.identity
                return fromService(
                    identity,
                    SiteExtractionResult.Failure(
                        reason = SiteExtractionFailure.NO_MEDIA_FOUND,
                        details = details.toList(),
                        message = FILES_REFUSED_MESSAGE,
                    ),
                )
            }
            val answer = answers.first { it.post.watermarked != null }
            return success(answer, rows, watermarkedOnly = true)
        }

        /**
         * P47: [rows] and the working files of [checked] whose height and codec they do not
         * list yet, highest first; on a tie the earlier answer's file stays first.
         */
        private fun joinedTo(rows: List<Row>, checked: Checked): List<Row> {
            val added = checked.working.filterNot { rows.lists(it.quality) }
                .map { Row(checked.answer, it) }
            if (added.isEmpty()) return rows
            if (rows.isNotEmpty()) {
                details += "joined: ${checked.answer.which.label} adds " +
                    added.joinToString(", ") { it.working.quality.label }
            }
            return (rows + added).sortedWith(ROW_ORDER)
        }

        /**
         * P46: the download service for [identity]'s post when TikTok's own pages gave no file;
         * [otherwise] (with this step's Details) when it is off, finds nothing or its files do
         * not open.
         */
        private suspend fun fromService(
            identity: SitePageIdentity,
            otherwise: SiteExtractionResult.Failure,
        ): SiteExtractionResult {
            if (!askDownloadService) return otherwise
            step = "download service"
            val expected = identity.contentId.takeUnless { identity.requiresCanonicalResolution }
            val agent = agents.desktop(agents.phone(context.userAgent))
            val label = TikTokDownloadService.LABEL
            val post = when (
                val answer = service.lookUp(identity.canonicalPageUrl, expected, agent)
            ) {
                is TikTokDownloadService.Answer.NotFound -> {
                    details += "$label: ${answer.note}"
                    return otherwise.copy(details = details.toList())
                }

                is TikTokDownloadService.Answer.Found -> answer.post
            }
            details += "$label: found · qualities: ${post.qualities.size}" +
                " · watermarked file: ${yesNo(post.watermarked != null)}"
            val parsed = PageRead.Parsed(PageAgent.SERVICE, post, agent, cookies = emptyList())
            step = "file check"
            checksLeft = MAX_FILE_CHECKS
            val checked = check(parsed)
            val rows = checked.working.ifEmpty { watermarked(listOf(parsed)) }
            if (rows.isEmpty()) {
                details += "$label: no file opened"
                return otherwise.copy(details = details.toList())
            }
            return success(parsed, rows, watermarkedOnly = checked.working.isEmpty())
        }

        private fun success(
            answer: PageRead.Parsed,
            rows: List<Working>,
            watermarkedOnly: Boolean,
        ): SiteExtractionResult = success(rows.map { Row(answer, it) }, watermarkedOnly)

        private fun success(rows: List<Row>, watermarkedOnly: Boolean): SiteExtractionResult {
            val answers = rows.map { it.answer.which.label }.distinct().joinToString(" + ")
            details += "answer: $answers · " + if (watermarkedOnly) {
                "watermarked file only"
            } else {
                "${rows.size} working qualities"
            }
            details += "hosts: " + rows.mapNotNull { hostOf(it.working.address) }.distinct()
                .joinToString(", ")
            step = "rows"
            return SiteExtractionResult.Success(
                candidates = rows.map { candidate(it.answer, it.working) },
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
            details += "data: ${notes.dataKey}" + notes.json?.let { " · JSON: $it" }.orEmpty() +
                notes.status?.let { " · TikTok status $it" }.orEmpty()
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
        private suspend fun check(
            answer: PageRead.Parsed,
            known: List<Row> = emptyList(),
        ): Checked {
            // P47: a file of a height an earlier answer already lists is not checked again.
            val wanted = answer.post.qualities.filterNot { known.lists(it) }
            val checks = wanted.map(::QualityCheck)
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
                return Checked(answer, wanted.map { Working(it, it.addresses.first(), null) })
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
            val headers = mediaHeaders(answer, address)
            when (val result = http.probe(address, headers, PROBE_TIMEOUT_MILLIS)) {
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
                val outcome = if (check.found == null) " (left out)" else ""
                details += "file check (${answer.which.label}): ${quality.label} " +
                    check.trail.joinToString(" → ") + outcome
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
                title = displayTitle(post, quality.titleLabel),
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
        private fun mediaCookie(answer: PageRead.Parsed, url: String): String? {
            // P46: the download service's files never get the user's TikTok cookies.
            if (answer.which == PageAgent.SERVICE) return null
            return mergedCookie(context.cookie, answer.cookies.filter { it.matches(url) })
        }

        private fun mediaHeaders(answer: PageRead.Parsed, url: String): Map<String, String> =
            buildMap {
                answer.userAgent?.takeIf(String::isNotBlank)?.let { put("User-Agent", it) }
                if (answer.which != PageAgent.SERVICE) put("Referer", TIKTOK_REFERER)
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

    /** P47: how many heights [qualities] list; a file of unknown height counts as one. */
    private fun heightsOf(qualities: List<TikTokQuality>): Int =
        qualities.map { it.heightLabel }.distinct().size

    private fun heights(working: List<Working>): Int = heightsOf(working.map { it.quality })

    /**
     * P47: whether these rows already list [quality]'s file: the same height and codec (an
     * unknown codec matches either); a file of unknown height is listed once any row is.
     */
    private fun List<Row>.lists(quality: TikTokQuality): Boolean {
        val height = quality.heightLabel ?: return isNotEmpty()
        return any { row ->
            val listed = row.working.quality
            listed.heightLabel == height && (
                listed.codec == quality.codec ||
                    listed.codec == TikTokCodec.UNKNOWN ||
                    quality.codec == TikTokCodec.UNKNOWN
                )
        }
    }

    private fun hostOf(url: String): String? =
        runCatching { URI(url).host?.lowercase(Locale.US) }.getOrNull()

    companion object {
        const val DEFAULT_MAX_PAGE_BYTES: Long = 3L * 1024 * 1024
        const val MAX_FILE_CHECKS = 8
        const val MAX_PARALLEL_CHECKS = 3

        /** P47: joined rows as one answer orders them: highest, H.264 first, higher bitrate. */
        private val ROW_ORDER = compareByDescending<Row> { it.working.quality.heightLabel ?: 0 }
            .thenBy { it.working.quality.codec.order }
            .thenByDescending { it.working.quality.bitrateBitsPerSecond ?: 0L }
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
