package com.alal.yft.extractor.master.social

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.MasterMediaValidator
import com.alal.yft.extractor.master.ValidationResult
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.lookup.RouteBLookup
import com.alal.yft.extractor.master.lookup.RouteBMediaStartReader
import com.alal.yft.extractor.master.modules.youtube.StreamCheck
import com.alal.yft.extractor.master.modules.youtube.ladder.InitCheck
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStart
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStartReader
import com.alal.yft.extractor.master.toolkit.CandidateFactory
import com.alal.yft.extractor.master.toolkit.UrlPolicy
import java.net.URI
import java.util.WeakHashMap
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.withContext

/**
 * Social plan S1a (6), S6 (`MASTER_KEY_SOCIAL_PLAN.md` §4 S6, §5 L5, §6.1 "Row checks"): V and the
 * init check on every row Master offers in a social lookup (one with its own record and main's
 * pages: the social-ladder build's Facebook lookups, `-Pyft.socialLadder`; S2b: TikTok's too), in
 * place of the engine's file check for those rows: F1's, F2's, the tab's page data's and its own
 * playback's (TikTok: T1's, T2's, T3's), each file once (a row's sound file too, as the engine asks
 * it). S2b: the media hosts are the lookup's site's ([MainPages.site]: TikTok's hosts and its video
 * file paths), else [site]'s. Task 9: one check and one [reader] serve every site's lookups: each
 * read carries the site chosen for its lookup ([RowReadSite]), and the reader asks that site's
 * hosts only, never a site fixed when it was built.
 *
 * - **Lifetime** (`oe`): the row's link expiry ([UrlPolicy.expiry]: Facebook's `oe`, hex seconds)
 *   is the row's; a link that has expired or lives less than [MIN_LIFETIME_MILLIS] more fails
 *   unasked.
 * - **V:** one read of the file's first [START_BYTES] bytes (a `Range`, at most 64 KiB) with the
 *   download's own headers (never the user's session: no cookie, no credentials), only on the
 *   host the answer gave when it is one of the site's media hosts ([Site.mediaHost]) and did not
 *   stop in the lookup (S7); each file read once a lookup (a later row of the same file reuses
 *   the bytes), at most [maxReads] files. Each read follows route B's rules ([RouteBLookup] over
 *   the lookup's record): listed in the record as Master's, none after a 429 in the lookup.
 *   Task 9: a file off the site's media hosts ([RowReadSite.address]: another site's host, no
 *   HTTPS, another path) is **refused** before route B lists an ask: never sent, no new ask, never
 *   `NETWORK`; [refused] counts such files (each once a lookup; the site census's `refused`).
 * - **Init check** ([InitCheck]) on the same bytes: the row's type (its own, else its address's
 *   extension; Facebook's sound-only MP4 answers `video/mp4`, as the engine's V knows), the
 *   container start, no encryption marker, the size. Only `pass` passes: `unknown` (a generic or
 *   missing type, too few bytes, a size without a total, a manifest row, another host, no answer,
 *   the reads spent) is never a pass; an encryption marker is DRM (final, the engine's rule).
 * - **Length** (S1a (10)): a row that passes and states no length gets its file's own, from the
 *   same bytes ([StartDuration]: the movie header, a fragmented file's length or its index), so
 *   the Facebook trial's line can compare it with main's (the plugin player's answer names none).
 *   A row's own length stays; bytes without one leave it unknown.
 * - **Picture size** (task 16, F6): a picture row (its type `video/…`) that passes and states no
 *   width and no height gets its file's own, from the same bytes ([StartPicture]: the picture
 *   track's `tkhd`, else its visual sample entry in `stsd`; a turned track's swapped), so the sheet
 *   names it as main names that size (the plugin player's answer names none). No other read, no
 *   larger read, no new ask in the record. A row's own size stays (never overwritten); bytes
 *   without one, and a sound-only file, leave it unknown, as before.
 * - **VK's page manifest** (VK's pack proof, V3's retest 2026-10-26): in a VK lookup only, the DASH
 *   manifest VK's player fetched on its page (a capture row, [VkPageDash.pageManifest]; a ladder's
 *   manifest row stays unknown, unasked) is read whole (one read of at most 64 KiB, under the same
 *   rules: route B, VK's media hosts, once a lookup, counted in [maxReads]); a cut, live or
 *   unreadable one is unknown, a protected one DRM (final). Its row is its tallest picture file
 *   with its best sound of the same container ([VkPageDash.read]), both whole files: each gets V
 *   and the init check as any file row (its start read once, the sound's too, so the engine's
 *   check of the row's sound asks nothing new). The row passes with the picture file's address,
 *   type and size, the sound to merge, the manifest's length (else the file's own) and the
 *   files' own totals; an HLS manifest and every other site's manifest row stay unknown, unasked.
 *
 * - **VK's pieces' files** (V3's retest 2): in a VK lookup, a picture file row (the whole file of
 *   pieces VK's player asked for, [VkPieceFiles]; any VK picture row) that passes and states no
 *   codecs gets its file's own sample entry from the same bytes ([StartPicture.codec]), so the
 *   phone's merge gate reads it. No other read.
 *
 * Every other lookup: [validator] (the engine's own V), unchanged. Nothing is logged; a result
 * names a reason only, never an address, a header or a byte.
 */
class SocialRowCheck(
    private val validator: MasterMediaValidator,
    reader: MediaStartReader,
    private val site: Site = SiteWays.FACEBOOK,
    private val maxReads: Int = MAX_READS,
) : MasterMediaValidator {
    /** Each read under route B's rules (in the lookup's record, as Master's). */
    private val reader = RouteBMediaStartReader(reader)

    /** Each lookup's files read (its record's), dropped with the record. */
    private val lookups = WeakHashMap<LookupRecord, Reads>()

    override suspend fun validate(candidate: MediaCandidate, nowEpochMs: Long): ValidationResult {
        val context = coroutineContext
        val record = context[LookupRecord]
        val pages = context[MainPages]
        if (record == null || pages == null) return validator.validate(candidate, nowEpochMs)
        // S2b: the lookup's own site's media hosts (its MainPages names it), else the check's site.
        return check(record, candidate, nowEpochMs, pages.site?.let(SiteWays::site) ?: site)
    }

    /** S6 on [row] in [record]'s lookup at [now], [site]'s media hosts only. */
    private suspend fun check(record: LookupRecord, row: MediaCandidate, now: Long, site: Site): ValidationResult {
        if (row.drmHint == true) return rejected(SiteExtractionFailure.DRM_PROTECTED)
        val expires = listOfNotNull(row.expiresAtEpochMs, UrlPolicy.expiry(row.mediaUrl)).minOrNull()
        if (expires != null && expires - now < MIN_LIFETIME_MILLIS) return rejected(SiteExtractionFailure.EXPIRED_LINK)
        // Unknown, so no pass: a manifest row (no file start; VK's page manifest aside), a host that
        // stopped in the lookup, a type the check cannot read, another host (refused, task 9), the
        // lookup's reads spent. None is asked.
        if (hostOf(row.mediaUrl) in record.stopHosts()) return rejected(SiteExtractionFailure.NO_MEDIA_FOUND)
        if (manifest(row)) {
            return if (site.id == VkClient.SITE_ID && !hls(row) && VkPageDash.pageManifest(row)) {
                pageManifest(record, row, now, site, expires)
            } else {
                rejected(SiteExtractionFailure.NO_MEDIA_FOUND)
            }
        }
        val type = typeOf(row) ?: return rejected(SiteExtractionFailure.NO_MEDIA_FOUND)
        val read = start(record, row.mediaUrl, row.requestContext, RowReadSite(site))
            ?: return rejected(SiteExtractionFailure.NO_MEDIA_FOUND)
        if (read is MediaStart.Failed) return rejected(failure(read.statusCode))
        read as MediaStart.Read
        if (read.statusCode !in OK_STATUSES) return rejected(SiteExtractionFailure.HTTP_STATUS)
        val total = InitCheck.totalOf(read.contentRange) ?: read.contentLength.takeIf { read.statusCode == HTTP_OK }
        val checked = InitCheck.check(type, row.contentLengthBytes, InitCheck.Observed(answerType(type, read.contentType), read.bytes, total))
        return when (checked.verdict) {
            StreamCheck.Verdict.PASS -> {
                // Task 16 (F6): the file's own picture size when the row states none (read, never asked).
                val picture = if (stated(row.width) == null && stated(row.height) == null && type.startsWith(VIDEO)) {
                    StartPicture.of(read.bytes)
                } else {
                    null
                }
                // VK's pack proof (V3's retest 2): a VK picture row that states no codecs names its
                // file's own (its sample entry, as the phone's merge gate reads them).
                val codecs = if (row.codecs.isEmpty() && site.id == VkClient.SITE_ID && type.startsWith(VIDEO)) {
                    StartPicture.codec(read.bytes)?.let(::listOf) ?: row.codecs
                } else {
                    row.codecs
                }
                ValidationResult.Valid(
                    row.copy(
                        mimeType = row.mimeType ?: type,
                        contentLengthBytes = row.contentLengthBytes ?: total,
                        expiresAtEpochMs = expires,
                        // S1a (10): the file's own length when the row states none (read, never asked).
                        durationMillis = row.durationMillis ?: StartDuration.millis(read.bytes),
                        width = picture?.width ?: row.width,
                        height = picture?.height ?: row.height,
                        codecs = codecs,
                    ),
                )
            }
            StreamCheck.Verdict.FAIL -> rejected(
                if (checked.reason == InitCheck.Reason.ENCRYPTED) {
                    SiteExtractionFailure.DRM_PROTECTED
                } else {
                    SiteExtractionFailure.RESPONSE_CHANGED
                },
            )
            StreamCheck.Verdict.UNKNOWN -> rejected(SiteExtractionFailure.NO_MEDIA_FOUND)
        }
    }

    /**
     * VK's page manifest [row] (see the class notes) in [record]'s lookup at [now], on [site]'s
     * media hosts: read whole, its tallest picture file and its sound checked, the row of both.
     */
    private suspend fun pageManifest(
        record: LookupRecord,
        row: MediaCandidate,
        now: Long,
        site: Site,
        expires: Long?,
    ): ValidationResult {
        val read = start(record, row.mediaUrl, row.requestContext, RowReadSite(site), OkHttpRowReader.MAX_BYTES)
            ?: return rejected(SiteExtractionFailure.NO_MEDIA_FOUND)
        if (read is MediaStart.Failed) return rejected(failure(read.statusCode))
        read as MediaStart.Read
        if (read.statusCode !in OK_STATUSES) return rejected(SiteExtractionFailure.HTTP_STATUS)
        // The whole manifest only: an answer short of its total is cut, so unknown.
        val total = InitCheck.totalOf(read.contentRange) ?: read.contentLength.takeIf { read.statusCode == HTTP_OK }
        if (total != null && total > read.bytes.size) return rejected(SiteExtractionFailure.NO_MEDIA_FOUND)
        val text = VkPageDash.text(read.bytes) ?: return rejected(SiteExtractionFailure.NO_MEDIA_FOUND)
        val files = when (val found = VkPageDash.read(text, row.mediaUrl, now)) {
            is VkPageDash.Read.None -> return rejected(found.reason)
            is VkPageDash.Read.Files -> found
        }
        val picture = when (val checked = file(record, row, files.picture, now, site)) {
            is FileCheck.Failed -> return rejected(checked.reason)
            is FileCheck.Passed -> checked
        }
        val sound = when (val checked = files.sound?.let { file(record, row, it, now, site) }) {
            null -> null
            is FileCheck.Failed -> return rejected(checked.reason)
            is FileCheck.Passed -> checked.file
        }
        val video = picture.file
        // The file's own picture size when the manifest states none (read, never asked).
        val size = if (stated(video.width) == null && stated(video.height) == null) StartPicture.of(picture.bytes) else null
        return ValidationResult.Valid(
            row.copy(
                mediaUrl = video.mediaUrl,
                sources = row.sources + CandidateSource.MANIFEST,
                kind = MediaKind.DIRECT,
                mimeType = video.mimeType,
                codecs = video.codecs,
                width = stated(video.width) ?: size?.width,
                height = stated(video.height) ?: size?.height,
                durationMillis = row.durationMillis ?: files.durationMillis ?: StartDuration.millis(picture.bytes),
                contentLengthBytes = video.contentLengthBytes,
                requestContext = video.requestContext,
                expiresAtEpochMs = listOfNotNull(expires, video.expiresAtEpochMs).minOrNull(),
                audioCompanion = sound?.let(CandidateFactory::companion),
            ),
        )
    }

    /** One of [manifest]'s whole [file]s: V and the init check on its start, as a file row's. */
    private suspend fun file(
        record: LookupRecord,
        manifest: MediaCandidate,
        file: MediaCandidate,
        now: Long,
        site: Site,
    ): FileCheck {
        val expires = UrlPolicy.expiry(file.mediaUrl)
        if (expires != null && expires - now < MIN_LIFETIME_MILLIS) return FileCheck.Failed(SiteExtractionFailure.EXPIRED_LINK)
        if (hostOf(file.mediaUrl) in record.stopHosts()) return FileCheck.Failed(SiteExtractionFailure.NO_MEDIA_FOUND)
        val type = typeOf(file) ?: return FileCheck.Failed(SiteExtractionFailure.NO_MEDIA_FOUND)
        // The manifest's own headers on its own host (its page as Referer, the download's agent, as
        // the manifest's read sent them); another host gets the public ones only.
        val context = if (UrlPolicy.origin(file.mediaUrl) == UrlPolicy.origin(manifest.mediaUrl)) {
            manifest.requestContext
        } else {
            UrlPolicy.context(manifest.requestContext, manifest.mediaUrl, file.mediaUrl, manifest.pageUrl, captured = true)
        }
        val read = start(record, file.mediaUrl, context, RowReadSite(site))
            ?: return FileCheck.Failed(SiteExtractionFailure.NO_MEDIA_FOUND)
        if (read is MediaStart.Failed) return FileCheck.Failed(failure(read.statusCode))
        read as MediaStart.Read
        if (read.statusCode !in OK_STATUSES) return FileCheck.Failed(SiteExtractionFailure.HTTP_STATUS)
        val total = InitCheck.totalOf(read.contentRange) ?: read.contentLength.takeIf { read.statusCode == HTTP_OK }
        val checked = InitCheck.check(type, null, InitCheck.Observed(answerType(type, read.contentType), read.bytes, total))
        return when (checked.verdict) {
            StreamCheck.Verdict.PASS -> FileCheck.Passed(
                file.copy(
                    pageUrl = manifest.pageUrl,
                    mimeType = type,
                    requestContext = context,
                    contentLengthBytes = total?.takeIf { it > 0 },
                    expiresAtEpochMs = expires,
                ),
                read.bytes,
            )
            StreamCheck.Verdict.FAIL -> FileCheck.Failed(
                if (checked.reason == InitCheck.Reason.ENCRYPTED) {
                    SiteExtractionFailure.DRM_PROTECTED
                } else {
                    SiteExtractionFailure.RESPONSE_CHANGED
                },
            )
            StreamCheck.Verdict.UNKNOWN -> FileCheck.Failed(SiteExtractionFailure.NO_MEDIA_FOUND)
        }
    }

    /** A page manifest's file after its check: passed (the file, its start's bytes) or failed. */
    private sealed interface FileCheck {
        class Passed(val file: MediaCandidate, val bytes: ByteArray) : FileCheck
        class Failed(val reason: SiteExtractionFailure) : FileCheck
    }

    /**
     * [url]'s first [bytes] bytes (sent with [context]'s headers) in [record]'s lookup, read on
     * [site]'s media hosts only: read once (a later row of the same file gets the same bytes),
     * while fewer than [maxReads] files were read; null when they are spent, or (task 9) when the
     * file is off [site]'s media hosts: refused here, before route B lists an ask, and counted
     * ([refused]).
     */
    private suspend fun start(
        record: LookupRecord,
        url: String,
        context: BrowserRequestContext,
        site: RowReadSite,
        bytes: Int = START_BYTES,
    ): MediaStart? {
        val reads = synchronized(lookups) { lookups.getOrPut(record) { Reads() } }
        synchronized(reads) {
            reads.files[url]?.let { return it }
            if (site.address(url) == null) {
                reads.refused += url
                return null
            }
            if (reads.asked >= maxReads) return null
            reads.asked++
        }
        val headers = context.replayHeaders().filterKeys { it.trim().lowercase() !in UNSENT }
        // Task 9: the read carries its lookup's site; the reader asks that site's hosts only.
        val read = withContext(RouteBLookup(record) + site) { reader.read(url, headers, bytes) }
        synchronized(reads) { reads.files[url] = read }
        return read
    }

    /**
     * Task 9: the files [record]'s lookup refused (off its site's media hosts, never sent), each
     * once; 0 for a lookup this check never saw.
     */
    fun refused(record: LookupRecord): Int {
        val reads = synchronized(lookups) { lookups[record] } ?: return 0
        return synchronized(reads) { reads.refused.size }
    }

    /** One lookup's reads: the files read (by address), how many were asked, the files refused. */
    private class Reads {
        val files = HashMap<String, MediaStart>()
        var asked = 0
        val refused = HashSet<String>()
    }

    override fun toString(): String = "SocialRowCheck(${site.id})"

    companion object {
        /** S6: the rows' reads a lookup (§6.1: at most 8 rows). */
        const val MAX_READS = 8

        /** The bytes V reads of a file, from its start (the YouTube ladder's L rows read as many). */
        const val START_BYTES = 4096

        /** A link that lives less than this more fails unasked (the download would not start in time). */
        const val MIN_LIFETIME_MILLIS = 60_000L

        private const val HTTP_OK = 200
        private const val HTTP_GONE = 410
        private const val HTTP_TOO_MANY = 429
        private val OK_STATUSES = setOf(200, 206)

        /** Never sent to a media host (S2), and the range is V's own. */
        private val UNSENT = setOf("cookie", "authorization", "proxy-authorization", "range")
        private val TYPES = setOf("video/mp4", "audio/mp4", "video/webm", "audio/webm")
        private const val VIDEO = "video/"

        /** A side a row states (above 0), else null. */
        private fun stated(side: Int?): Int? = side?.takeIf { it > 0 }

        private fun rejected(reason: SiteExtractionFailure) = ValidationResult.Rejected(reason)

        private fun failure(status: Int?): SiteExtractionFailure = when (status) {
            null -> SiteExtractionFailure.NETWORK
            HTTP_GONE -> SiteExtractionFailure.EXPIRED_LINK
            HTTP_TOO_MANY -> SiteExtractionFailure.RATE_LIMITED
            else -> SiteExtractionFailure.HTTP_STATUS
        }

        /** The row's own type, else its address's extension's; null when the check cannot read it. */
        private fun typeOf(row: MediaCandidate): String? {
            val stated = row.mimeType?.substringBefore(';')?.trim()?.lowercase()?.takeIf(String::isNotEmpty)
            val type = stated ?: when (pathOf(row.mediaUrl).substringAfterLast('.', "")) {
                "mp4", "m4v" -> "video/mp4"
                "m4a" -> "audio/mp4"
                "webm" -> "video/webm"
                else -> null
            }
            return type?.takeIf { it in TYPES }
        }

        /** A sound row's file answered with the picture type of its container counts as its own type. */
        private fun answerType(rowType: String, answered: String?): String? {
            val base = answered?.substringBefore(';')?.trim()?.lowercase()
            return if (rowType.startsWith("audio/") && base == "video/" + rowType.substringAfter('/')) rowType else answered
        }

        private fun hls(row: MediaCandidate): Boolean =
            row.kind == MediaKind.HLS || "mpegurl" in row.mimeType?.lowercase().orEmpty() ||
                pathOf(row.mediaUrl).endsWith(".m3u8")

        private fun manifest(row: MediaCandidate): Boolean {
            val mime = row.mimeType?.lowercase().orEmpty()
            val path = pathOf(row.mediaUrl)
            return row.kind == MediaKind.HLS || row.kind == MediaKind.DASH || "mpegurl" in mime ||
                mime.startsWith("application/dash+xml") || path.endsWith(".m3u8") || path.endsWith(".mpd")
        }

        private fun pathOf(url: String): String =
            runCatching { URI(url.trim()).rawPath }.getOrNull().orEmpty().lowercase()

        private fun hostOf(url: String): String? = runCatching { URI(url.trim()).host }.getOrNull()
            ?.lowercase()?.trimEnd('.')?.takeIf(String::isNotEmpty)
    }
}
