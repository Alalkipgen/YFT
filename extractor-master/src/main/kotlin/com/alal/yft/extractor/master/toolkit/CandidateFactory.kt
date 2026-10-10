package com.alal.yft.extractor.master.toolkit

import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.CompanionAudio
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.generic.classifier.MediaUrlClassifier
import com.alal.yft.extractor.generic.manifest.ManifestReader
import com.alal.yft.extractor.master.MasterRequest

/** Builds one bounded candidate for a page; every layer uses the same rules. */
internal class CandidateFactory(private val request: MasterRequest) {
    fun candidate(
        url: String?,
        mime: String? = null,
        id: String? = null,
        role: PageMediaRole? = PageMediaRole.MAIN,
        source: CandidateSource = CandidateSource.MANIFEST,
        node: JsonValue? = null,
        key: String? = null,
    ): MediaCandidate? {
        val address = UrlPolicy.resolve(request.pageUrl, url) ?: return null
        val kind = MediaUrlClassifier.classify(address, mime) ?: return null
        if (kind == MediaKind.DIRECT && isSegment(address)) return null
        return MediaCandidate(
            pageUrl = request.pageUrl,
            mediaUrl = address,
            sources = setOf(source),
            kind = kind,
            mimeType = mime,
            title = node["title"].asStringOrNull ?: node["name"].asStringOrNull,
            durationMillis = ManifestReader.isoDurationMillis(
                node["duration"].asStringOrNull ?: node["duration"].asLongOrNull?.toString(),
            ),
            contentLengthBytes = node["contentLength"].asLongOrNull?.takeIf { it > 0 },
            requestContext = UrlPolicy.context(
                request.requestContext, request.requestContext.pageUrl.orEmpty(),
                address, request.pageUrl,
            ),
            confidence = CandidateConfidence.HIGH,
            expiresAtEpochMs = UrlPolicy.expiry(address),
            drmHint = null,
            observedAtEpochMs = request.nowEpochMs,
            codecs = codecs(mime),
            videoId = id?.let { UrlPolicy.videoKey(request.pageUrl, it) },
            width = dimension(node["width"]),
            height = dimension(node["height"]),
            bitrateBitsPerSecond = node["bitrate"].asLongOrNull?.takeIf { it > 0 },
            pageRole = role,
            pageVideoKey = key,
        )
    }

    companion object {
        private val CODECS = Regex("""(?i)codecs\s*=\s*"([^"]+)"""")

        fun codecs(mime: String?): List<String> =
            mime?.let { CODECS.find(it)?.groupValues?.get(1) }
                ?.split(',')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()

        fun dimension(value: JsonValue?): Int? =
            value.asLongOrNull?.takeIf { it in 1..Int.MAX_VALUE }?.toInt()

        fun companion(candidate: MediaCandidate) = CompanionAudio(
            mediaUrl = candidate.mediaUrl,
            mimeType = candidate.mimeType?.substringBefore(';') ?: "audio/mp4",
            codecs = candidate.codecs,
            requestContext = candidate.requestContext,
            contentLengthBytes = candidate.contentLengthBytes,
            bitrateBitsPerSecond = candidate.bitrateBitsPerSecond,
            expiresAtEpochMs = candidate.expiresAtEpochMs,
        )

        private fun isSegment(url: String): Boolean =
            url.substringBefore('?').substringAfterLast('.').lowercase() in setOf("ts", "m4s")
    }
}
