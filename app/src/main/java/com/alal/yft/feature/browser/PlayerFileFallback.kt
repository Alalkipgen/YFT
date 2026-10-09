package com.alal.yft.feature.browser

import com.alal.yft.core.browser.detection.RequestObservation
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.extractor.api.SiteExtractionFailure
import java.net.URI
import java.util.Locale

/**
 * P39 (R25): the browser never dead-ends on TikTok.
 *
 * When the TikTok adapter cannot read a page whose player already fetched TikTok media files,
 * the sheet offers the file the player plays instead of a failure: the fetched file that is the
 * on-screen `<video>`'s `currentSrc`, else the newest file the player fetched, with the cookies
 * and headers the player sent. Other adapters keep P12's behaviour. The files stay in memory for
 * the open page only, like the browser's session context.
 */
internal class PlayerFileFallback(
    private val isPlayerMedia: (adapterId: String, requestUrl: String) -> Boolean,
) {
    private val files = ArrayDeque<RequestObservation>()

    fun beginPage() = files.clear()

    /** Keeps [observation] when it is the TikTok player fetching a file on a TikTok page. */
    fun record(observation: RequestObservation) {
        if (!isTikTokPage(observation.pageUrl)) return
        if (!isPlayerMedia(TIKTOK, observation.requestUrl)) return
        files.removeAll { it.requestUrl == observation.requestUrl }
        files.addLast(observation)
        while (files.size > MAX_FILES) files.removeFirst()
    }

    /**
     * The row for the video at [videoPageUrl] after its lookup failed with [failure], or null
     * when the failure is another adapter's, a protected video, or the page fetched no file.
     */
    fun candidateFor(
        failure: SiteAdapterOutcome.Failed,
        videoPageUrl: String,
        title: String?,
        playerSrc: String? = null,
    ): MediaCandidate? {
        if (failure.adapterId != TIKTOK) return null
        if (failure.reason == SiteExtractionFailure.DRM_PROTECTED) return null
        val file = playerSrc?.let { src -> files.lastOrNull { it.requestUrl == src } }
            ?: files.lastOrNull()
            ?: return null
        val videoId = TIKTOK_VIDEO.find(videoPageUrl)?.groupValues?.get(1)
        return MediaCandidate(
            pageUrl = videoPageUrl,
            mediaUrl = file.requestUrl,
            sources = setOf(CandidateSource.REQUEST),
            kind = MediaKind.DIRECT,
            mimeType = MP4,
            title = title,
            requestContext = BrowserRequestContext(
                pageUrl = videoPageUrl,
                userAgent = file.userAgent,
                cookie = file.cookie,
                observedHeaders = file.headers,
            ),
            confidence = CandidateConfidence.MEDIUM,
            drmHint = false,
            observedAtEpochMs = file.observedAtEpochMs,
            videoId = videoId?.let { "$TIKTOK:$it" },
            pageRole = PageMediaRole.MAIN,
        )
    }

    private fun isTikTokPage(pageUrl: String): Boolean {
        val host = runCatching { URI(pageUrl).host }.getOrNull()?.lowercase(Locale.US)
            ?: return false
        return host == TIKTOK_DOMAIN || host.endsWith(".$TIKTOK_DOMAIN")
    }

    companion object {
        const val TIKTOK = "tiktok"
        const val NOTICE =
            "TikTok's page could not be read — showing the file its player is playing."
        private const val TIKTOK_DOMAIN = "tiktok.com"
        private const val MP4 = "video/mp4"
        private const val MAX_FILES = 12
        private val TIKTOK_VIDEO = Regex("/video/(\\d{15,22})(?:[/?#]|$)")
    }
}
