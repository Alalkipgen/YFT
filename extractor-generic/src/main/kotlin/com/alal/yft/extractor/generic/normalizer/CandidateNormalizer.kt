package com.alal.yft.extractor.generic.normalizer

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.extractor.generic.classifier.MediaFileUrls
import com.alal.yft.extractor.generic.classifier.MediaUrlClassifier
import java.net.URI

class CandidateNormalizer(
    private val policy: Policy = Policy(),
) {
    data class Policy(
        val maxCandidates: Int = 50,
        val tinyDirectAssetBytes: Long = 12 * 1024,
        /** How many numbered files make a stream's pieces rather than separate videos. */
        val minStreamPieces: Int = 3,
    ) {
        init {
            require(maxCandidates > 0)
            require(tinyDirectAssetBytes >= 0)
            require(minStreamPieces >= 2)
        }
    }

    fun normalize(pageUrl: String, candidates: Iterable<MediaCandidate>): List<MediaCandidate> {
        val pageKey = canonicalPageKey(pageUrl) ?: return emptyList()
        val merged = linkedMapOf<String, MediaCandidate>()

        candidates.forEach { raw ->
            if (canonicalPageKey(raw.pageUrl) != pageKey) return@forEach
            val candidate = sanitize(raw) ?: return@forEach
            val key = canonicalMediaKey(candidate.mediaUrl, candidate.kind) ?: return@forEach
            merged[key] = merged[key]?.let { existing -> merge(existing, candidate) } ?: candidate
        }

        return withoutStreamPieces(merged.values.toList())
            .sortedWith(
                compareByDescending<MediaCandidate> { it.confidence.ordinal }
                    .thenByDescending { it.observedAtEpochMs },
            )
            .take(policy.maxCandidates)
    }

    /**
     * Numbered HLS pieces (`seg-1.ts`, `seg-2.ts`, …) are one stream, not videos (P3-FIX).
     * Next to the page's HLS or DASH manifest they drop out; without one the series keeps its
     * first piece. Numbered whole files, such as `clip-1.mp4` and `clip-2.mp4`, stay apart.
     */
    private fun withoutStreamPieces(candidates: List<MediaCandidate>): List<MediaCandidate> {
        val series = candidates.indices
            .filter { candidates[it].kind == MediaKind.DIRECT }
            .groupBy { MediaFileUrls.segmentFamily(candidates[it].mediaUrl) }
            .filter { (family, pieces) -> family != null && pieces.size >= policy.minStreamPieces }
            .values
        if (series.isEmpty()) return candidates
        val hasManifest = candidates.any { it.kind == MediaKind.HLS || it.kind == MediaKind.DASH }
        val dropped = series.flatMap { pieces -> if (hasManifest) pieces else pieces.drop(1) }
            .toSet()
        return candidates.filterIndexed { index, _ -> index !in dropped }
    }

    private fun sanitize(raw: MediaCandidate): MediaCandidate? {
        // A byte range names a piece of the file: the candidate is the whole file, and a length
        // read for the piece is not the file's.
        val wholeFile = MediaFileUrls.wholeFile(raw.mediaUrl)
        val candidate = if (wholeFile == raw.mediaUrl) {
            raw
        } else {
            raw.copy(mediaUrl = wholeFile, contentLengthBytes = null)
        }
        val uri = runCatching { URI(candidate.mediaUrl) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        if (uri.userInfo != null) return null
        if (candidate.mediaUrl.isLikelyTrackingAsset()) return null
        if (candidate.mimeType.isClearlyNonMedia()) return null

        val classifiedKind = MediaUrlClassifier.classify(candidate.mediaUrl, candidate.mimeType)
        val resolvedKind = when {
            candidate.kind != MediaKind.UNKNOWN -> candidate.kind
            classifiedKind != null -> classifiedKind
            else -> MediaKind.UNKNOWN
        }
        val trustedUnknown = candidate.sources.any {
            it == CandidateSource.DOM || it == CandidateSource.DOWNLOAD_LISTENER
        }
        if (resolvedKind == MediaKind.UNKNOWN && !trustedUnknown) return null
        if (
            resolvedKind == MediaKind.DIRECT &&
            candidate.contentLengthBytes != null &&
            candidate.contentLengthBytes in 0 until policy.tinyDirectAssetBytes
        ) return null

        return candidate.copy(
            kind = resolvedKind,
            mimeType = candidate.mimeType?.takeIf(String::isNotBlank),
            title = candidate.title?.trim()?.takeIf(String::isNotEmpty),
            thumbnailUrl = candidate.thumbnailUrl?.takeIf { it.startsWith("https://") },
            durationMillis = candidate.durationMillis?.takeIf { it > 0 },
            contentLengthBytes = candidate.contentLengthBytes?.takeIf { it >= 0 },
        )
    }

    private fun merge(first: MediaCandidate, second: MediaCandidate): MediaCandidate {
        val newest = if (second.observedAtEpochMs >= first.observedAtEpochMs) second else first
        val oldest = if (newest === second) first else second
        // P37 (R18): the address the page's player asked for beats a link its script names
        // again later; the script's copy may be a dead one for this phone.
        val address = when {
            first.isPlayerRequest() && !second.isPlayerRequest() -> first
            second.isPlayerRequest() && !first.isPlayerRequest() -> second
            else -> newest
        }
        return newest.copy(
            mediaUrl = address.mediaUrl,
            sources = first.sources + second.sources,
            kind = richerKind(first.kind, second.kind),
            mimeType = newest.mimeType ?: oldest.mimeType,
            title = newest.title ?: oldest.title,
            thumbnailUrl = newest.thumbnailUrl ?: oldest.thumbnailUrl,
            durationMillis = newest.durationMillis ?: oldest.durationMillis,
            codecs = newest.codecs.ifEmpty { oldest.codecs },
            audioCompanion = newest.audioCompanion ?: oldest.audioCompanion,
            videoId = newest.videoId ?: oldest.videoId,
            width = newest.width ?: oldest.width,
            height = newest.height ?: oldest.height,
            framesPerSecond = newest.framesPerSecond ?: oldest.framesPerSecond,
            bitrateBitsPerSecond = newest.bitrateBitsPerSecond ?: oldest.bitrateBitsPerSecond,
            contentLengthBytes = listOfNotNull(first.contentLengthBytes, second.contentLengthBytes).maxOrNull(),
            requestContext = first.requestContext.mergedWith(second.requestContext),
            confidence = maxOf(first.confidence, second.confidence),
            expiresAtEpochMs = listOfNotNull(first.expiresAtEpochMs, second.expiresAtEpochMs).minOrNull(),
            drmHint = when {
                first.drmHint == true || second.drmHint == true -> true
                first.drmHint == false || second.drmHint == false -> false
                else -> null
            },
            observedAtEpochMs = maxOf(first.observedAtEpochMs, second.observedAtEpochMs),
            pageRole = mergedRole(first.pageRole, second.pageRole),
            pageVideoKey = newest.pageVideoKey ?: oldest.pageVideoKey,
        )
    }

    private fun MediaCandidate.isPlayerRequest(): Boolean = sources.any { source ->
        source == CandidateSource.REQUEST || source == CandidateSource.REDIRECT ||
            source == CandidateSource.DOWNLOAD_LISTENER
    }

    /** P24: a file the page names as its own video stays its video wherever else it shows. */
    private fun mergedRole(first: PageMediaRole?, second: PageMediaRole?): PageMediaRole? = when {
        first == PageMediaRole.MAIN || second == PageMediaRole.MAIN -> PageMediaRole.MAIN
        first == PageMediaRole.PREVIEW || second == PageMediaRole.PREVIEW -> PageMediaRole.PREVIEW
        else -> null
    }

    private fun richerKind(first: MediaKind, second: MediaKind): MediaKind = when {
        first == second -> first
        first == MediaKind.UNKNOWN -> second
        second == MediaKind.UNKNOWN -> first
        else -> second
    }

    private fun canonicalPageKey(rawUrl: String): String? {
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme !in setOf("http", "https")) return null
        val host = uri.host?.lowercase() ?: return null
        val port = uri.normalizedPort(scheme)
        val path = uri.rawPath?.ifBlank { "/" } ?: "/"
        val authority = if (port == -1) host else "$host:$port"
        return "$scheme://$authority$path${uri.rawQuery?.let { "?$it" }.orEmpty()}"
    }

    private fun canonicalMediaKey(rawUrl: String, kind: MediaKind): String? {
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val port = uri.normalizedPort(scheme)
        val authority = if (port == -1) host else "$host:$port"
        val path = uri.rawPath?.ifBlank { "/" } ?: "/"
        // An opaque CDN file name is the file; its query only carries per-request values.
        val query = uri.rawQuery?.takeUnless { MediaFileUrls.isOpaqueFile(rawUrl) }
        val stableQuery = query
            ?.split('&')
            ?.filter(String::isNotBlank)
            ?.filterNot { parameter -> parameter.substringBefore('=').isVolatileParameter() }
            ?.sorted()
            ?.joinToString("&")
            ?.takeIf(String::isNotBlank)
        return buildString {
            append(kind.name)
            append('|')
            append(scheme)
            append("://")
            append(authority)
            append(path)
            stableQuery?.let { append('?').append(it) }
        }
    }

    private fun URI.normalizedPort(scheme: String): Int = when {
        port == -1 -> -1
        scheme == "https" && port == 443 -> -1
        scheme == "http" && port == 80 -> -1
        else -> port
    }

    private fun String.isVolatileParameter(): Boolean {
        val normalized = lowercase()
        return normalized in VOLATILE_PARAMETERS || normalized.startsWith("utm_")
    }

    private fun String.isLikelyTrackingAsset(): Boolean {
        val value = lowercase()
        return TRACKING_MARKERS.any(value::contains)
    }

    private fun String?.isClearlyNonMedia(): Boolean {
        val mime = this?.substringBefore(';')?.trim()?.lowercase() ?: return false
        return mime.startsWith("image/") ||
            mime.startsWith("font/") ||
            mime == "text/css" ||
            mime.contains("javascript")
    }

    private companion object {
        val VOLATILE_PARAMETERS = setOf(
            "access_token", "auth", "authorization", "expires", "exp", "key",
            "key-pair-id", "policy", "sig", "signature", "token",
        )
        val TRACKING_MARKERS = setOf(
            "/analytics", "/beacon", "/favicon", "/pixel", "/sprite", "/telemetry", "/tracking",
        )
    }
}
