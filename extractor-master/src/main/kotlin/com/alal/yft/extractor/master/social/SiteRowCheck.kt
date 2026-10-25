package com.alal.yft.extractor.master.social

import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.toolkit.InlineDashReader
import java.net.URI

/**
 * Social plan S0b (`MASTER_KEY_SOCIAL_PLAN.md` §7.1, V): the census's one-row check. One row of an
 * answer (main's best, Master's best: [best]) gets a one-byte file check
 * ([ExtractorHttpClient.probe]) with the headers the download sends for that row
 * ([com.alal.yft.core.model.media.BrowserRequestContext.replayHeaders]), and only on the site's
 * media hosts ([Site.mediaHost]). An HLS row or any other host is [Verdict.UNKNOWN], never a pass;
 * an expired row fails unasked; a DASH row's manifest is read (one bounded GET on a media host,
 * read by Master's own [InlineDashReader]) and its tallest video file is checked. A client that
 * cannot check files leaves the row unknown. Nothing is retried or logged; [Checked] holds no
 * address or header.
 */
class SiteRowCheck(
    private val http: ExtractorHttpClient,
    private val timeoutMillis: Long = ExtractorHttpClient.DEFAULT_PROBE_TIMEOUT_MILLIS,
) {
    enum class Verdict(val label: String) { PASS("pass"), FAIL("fail"), UNKNOWN("unknown") }

    /**
     * The [verdict], [why] in a short label (a failure's name, "HLS", "another host"...), the
     * file's or manifest's [statusCode], the [requests] sent (0 to 2) and whether one answered 429
     * or a rate limit ([rateLimited]: the census stops the site).
     */
    data class Checked(
        val verdict: Verdict,
        val why: String? = null,
        val statusCode: Int? = null,
        val requests: Int = 0,
        val rateLimited: Boolean = false,
    )

    /** V on [row] of [site]'s answer, at [nowEpochMs] (the row's expiry). */
    suspend fun check(site: Site, row: MediaCandidate, nowEpochMs: Long): Checked {
        if (hls(row)) return Checked(Verdict.UNKNOWN, HLS)
        if (expired(row.expiresAtEpochMs, nowEpochMs)) return Checked(Verdict.FAIL, EXPIRED)
        if (!site.mediaHost(row.mediaUrl)) return Checked(Verdict.UNKNOWN, ANOTHER_HOST)
        val headers = row.requestContext.replayHeaders()
        if (!dash(row)) return probe(row.mediaUrl, headers, asked = 0)
        val manifest = http.get(row.mediaUrl, headers, MAX_MANIFEST_BYTES)
        if (manifest is ExtractorHttpResult.Failure) {
            return Checked(
                Verdict.FAIL, manifest.reason.name, manifest.statusCode, 1,
                limited(manifest.statusCode, manifest.reason),
            )
        }
        manifest as ExtractorHttpResult.Success
        val read = InlineDashReader.read(
            manifest.body,
            MasterRequest(manifest.finalUrl, 0, nowEpochMs.coerceAtLeast(0), SiteExtractionFailure.NO_MEDIA_FOUND),
            contentId = null,
            key = DASH_KEY,
        )
        if (read.drm) return Checked(Verdict.UNKNOWN, DRM, manifest.statusCode, 1)
        val file = best(read.candidates.filter(::picture))
            ?: return Checked(Verdict.UNKNOWN, NO_FILE, manifest.statusCode, 1)
        if (expired(file.expiresAtEpochMs, nowEpochMs)) return Checked(Verdict.FAIL, EXPIRED, null, 1)
        if (!site.mediaHost(file.mediaUrl)) return Checked(Verdict.UNKNOWN, ANOTHER_HOST, null, 1)
        return probe(file.mediaUrl, headers, asked = 1)
    }

    private suspend fun probe(url: String, headers: Map<String, String>, asked: Int): Checked =
        when (val result = http.probe(url, headers, timeoutMillis)) {
            is ExtractorProbeResult.Answered -> Checked(Verdict.PASS, null, result.statusCode, asked + 1)
            is ExtractorProbeResult.Refused -> Checked(
                Verdict.FAIL, result.reason.name, result.statusCode, asked + 1,
                limited(result.statusCode, result.reason),
            )
            ExtractorProbeResult.Unsupported -> Checked(Verdict.UNKNOWN, NO_FILE_CHECK, null, asked)
        }

    companion object {
        const val HLS = "HLS"
        const val EXPIRED = "expired"
        const val ANOTHER_HOST = "another host"
        const val NO_FILE = "no whole file"
        const val NO_FILE_CHECK = "no file check"
        const val DRM = "DRM"

        /** A DASH manifest's cap (as Master's [InlineDashReader] reads). */
        const val MAX_MANIFEST_BYTES: Long = 256L * 1024
        private const val HTTP_TOO_MANY = 429
        private const val DASH_KEY = "census"

        /**
         * The row V checks: picture rows (a sound-only row only when all are), the tallest first,
         * then the highest bitrate, in the answer's order otherwise; null for no rows.
         */
        fun best(rows: List<MediaCandidate>): MediaCandidate? {
            val pictures = rows.filter(::picture).ifEmpty { rows }
            return pictures.sortedWith(
                compareByDescending<MediaCandidate> { it.height ?: -1 }
                    .thenByDescending { it.bitrateBitsPerSecond ?: -1L },
            ).firstOrNull()
        }

        /** The tallest picture row's height, or null when no row states one. */
        fun tallest(rows: List<MediaCandidate>): Int? = rows.filter(::picture).mapNotNull { it.height }.maxOrNull()

        private fun picture(row: MediaCandidate): Boolean = row.mimeType?.lowercase()?.startsWith("audio/") != true

        private fun hls(row: MediaCandidate): Boolean =
            row.kind == MediaKind.HLS || row.mimeType?.lowercase()?.contains("mpegurl") == true ||
                pathOf(row.mediaUrl).endsWith(".m3u8")

        private fun dash(row: MediaCandidate): Boolean =
            row.kind == MediaKind.DASH || row.mimeType?.lowercase() == "application/dash+xml" ||
                pathOf(row.mediaUrl).endsWith(".mpd")

        private fun expired(expiresAt: Long?, now: Long): Boolean = expiresAt != null && expiresAt <= now

        private fun limited(status: Int?, reason: SiteExtractionFailure): Boolean =
            status == HTTP_TOO_MANY || reason == SiteExtractionFailure.RATE_LIMITED

        private fun pathOf(url: String): String =
            runCatching { URI(url.trim()).rawPath }.getOrNull().orEmpty().lowercase()
    }
}
