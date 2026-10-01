package com.alal.yft.extractor.generic.normalizer

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.generic.classifier.MediaUrlClassifier
import java.net.URI

class CandidateNormalizer(
    private val policy: Policy = Policy(),
) {
    data class Policy(
        val maxCandidates: Int = 50,
        val tinyDirectAssetBytes: Long = 12 * 1024,
    ) {
        init {
            require(maxCandidates > 0)
            require(tinyDirectAssetBytes >= 0)
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

        return merged.values
            .sortedWith(
                compareByDescending<MediaCandidate> { it.confidence.ordinal }
                    .thenByDescending { it.observedAtEpochMs },
            )
            .take(policy.maxCandidates)
    }

    private fun sanitize(candidate: MediaCandidate): MediaCandidate? {
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
        return newest.copy(
            sources = first.sources + second.sources,
            kind = richerKind(first.kind, second.kind),
            mimeType = newest.mimeType ?: oldest.mimeType,
            title = newest.title ?: oldest.title,
            thumbnailUrl = newest.thumbnailUrl ?: oldest.thumbnailUrl,
            durationMillis = newest.durationMillis ?: oldest.durationMillis,
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
        )
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
        val stableQuery = uri.rawQuery
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
