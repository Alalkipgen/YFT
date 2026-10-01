package com.alal.yft.core.browser.detection

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.generic.classifier.MediaUrlClassifier

object BrowserObservationMapper {
    fun fromRequest(observation: RequestObservation): MediaCandidate? {
        if (!observation.method.equals("GET", ignoreCase = true)) return null
        val kind = MediaUrlClassifier.classify(observation.requestUrl) ?: return null
        return MediaCandidate(
            pageUrl = observation.pageUrl,
            mediaUrl = observation.requestUrl,
            sources = buildSet {
                add(CandidateSource.REQUEST)
                if (kind == MediaKind.HLS || kind == MediaKind.DASH) add(CandidateSource.MANIFEST)
            },
            kind = kind,
            requestContext = observation.requestContext(),
            confidence = if (kind == MediaKind.HLS || kind == MediaKind.DASH) {
                CandidateConfidence.HIGH
            } else {
                CandidateConfidence.MEDIUM
            },
            observedAtEpochMs = observation.observedAtEpochMs,
        )
    }

    fun fromDownload(observation: DownloadObservation): MediaCandidate? {
        if (observation.mediaUrl.startsWith("blob:", ignoreCase = true)) return null
        val kind = MediaUrlClassifier.classify(observation.mediaUrl, observation.mimeType)
            ?: MediaKind.UNKNOWN
        return MediaCandidate(
            pageUrl = observation.pageUrl,
            mediaUrl = observation.mediaUrl,
            sources = setOf(CandidateSource.DOWNLOAD_LISTENER),
            kind = kind,
            mimeType = observation.mimeType,
            title = filenameFromContentDisposition(observation.contentDisposition),
            contentLengthBytes = observation.contentLengthBytes,
            requestContext = BrowserRequestContext(
                pageUrl = observation.pageUrl,
                userAgent = observation.userAgent,
                cookie = observation.cookie,
            ),
            confidence = CandidateConfidence.HIGH,
            observedAtEpochMs = observation.observedAtEpochMs,
        )
    }

    fun fromDom(observation: DomMediaObservation): MediaCandidate? {
        if (observation.mediaUrl.startsWith("blob:", ignoreCase = true)) return null
        return MediaCandidate(
            pageUrl = observation.pageUrl,
            mediaUrl = observation.mediaUrl,
            sources = setOf(CandidateSource.DOM),
            kind = MediaUrlClassifier.classify(observation.mediaUrl, observation.mimeType)
                ?: MediaKind.UNKNOWN,
            mimeType = observation.mimeType,
            title = observation.title,
            thumbnailUrl = observation.thumbnailUrl,
            durationMillis = observation.durationMillis,
            requestContext = BrowserRequestContext(observation.pageUrl, null, null),
            confidence = CandidateConfidence.HIGH,
            observedAtEpochMs = observation.observedAtEpochMs,
        )
    }

    fun fromRedirect(observation: RedirectObservation): MediaCandidate? {
        val kind = MediaUrlClassifier.classify(observation.toUrl) ?: return null
        return MediaCandidate(
            pageUrl = observation.pageUrl,
            mediaUrl = observation.toUrl,
            sources = buildSet {
                add(CandidateSource.REDIRECT)
                if (kind == MediaKind.HLS || kind == MediaKind.DASH) add(CandidateSource.MANIFEST)
            },
            kind = kind,
            requestContext = BrowserRequestContext(
                pageUrl = observation.pageUrl,
                userAgent = observation.userAgent,
                cookie = observation.cookie,
                observedHeaders = observation.requestHeaders,
            ),
            confidence = CandidateConfidence.HIGH,
            observedAtEpochMs = observation.observedAtEpochMs,
        )
    }

    private fun RequestObservation.requestContext() = BrowserRequestContext(
        pageUrl = pageUrl,
        userAgent = userAgent,
        cookie = cookie,
        observedHeaders = headers,
    )

    private fun filenameFromContentDisposition(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val match = FILENAME_PATTERN.find(value) ?: return null
        return match.groupValues[1].trim().trim('"').takeIf(String::isNotBlank)
    }

    private val FILENAME_PATTERN = Regex("""(?i)filename\*?=(?:UTF-8''|)([^;]+)""")
}
