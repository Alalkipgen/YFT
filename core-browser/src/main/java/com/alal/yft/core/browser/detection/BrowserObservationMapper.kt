package com.alal.yft.core.browser.detection

import com.alal.yft.core.model.media.AdSign
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.extractor.generic.classifier.MediaFileUrls
import com.alal.yft.extractor.generic.classifier.MediaUrlClassifier
import java.net.URI

/**
 * Turns what the browser saw into candidates. A player that fetches one file in byte ranges
 * (`bytestart`/`byteend`, `range=`) makes one candidate for the whole file, not one per piece,
 * and its size is probed for the whole file (P3-FIX).
 */
object BrowserObservationMapper {
    fun fromRequest(observation: RequestObservation): MediaCandidate? {
        if (!observation.method.equals("GET", ignoreCase = true)) return null
        val kind = MediaUrlClassifier.classify(observation.requestUrl) ?: return null
        return MediaCandidate(
            pageUrl = observation.pageUrl,
            mediaUrl = MediaFileUrls.wholeFile(observation.requestUrl),
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
            pageRole = adRole(observation.requestUrl, observation.referer()),
            adSign = adSign(observation.requestUrl, observation.referer()),
        )
    }

    /**
     * P24: [PageMediaRole.PREVIEW] for an ad's file: its address, or the address of the frame
     * that asked for it ([frameUrl], the request's `Referer`), names an ad server or ad words.
     * The page's own player asks with the page as its `Referer`, so its files keep no role.
     */
    fun adRole(mediaUrl: String, frameUrl: String?): PageMediaRole? =
        PageMediaRole.PREVIEW.takeIf { adSign(mediaUrl, frameUrl) != null }

    /**
     * P43: what shows that [mediaUrl] is an ad's ([AdHosts.adSign]): its own address first,
     * else the address of the frame that asked for it ([frameUrl]); null when nothing does.
     */
    fun adSign(mediaUrl: String, frameUrl: String?): AdSign? =
        AdHosts.adSign(mediaUrl) ?: frameUrl?.let(AdHosts::adSign)

    /**
     * P28: whether [url] asks for an ad break ([AdHosts.isAdBreakRequest]): a VAST or VMAP
     * document by a whole path part (`vast`, `vast3.xml`, `vmap.php`; never `vast-ocean.mp4`),
     * P43: also Google IMA's ad requests, `preroll` and `/ads/` requests. The file the player
     * fetches right after one is the ad's ([VastAdTracker]).
     */
    fun isAdBreakRequest(url: String): Boolean = AdHosts.isAdBreakRequest(url)

    /**
     * P43: whether an answer of the page's ([contentType], its body's first characters
     * [bodyStart]) is an ad break's: a VAST or VMAP document ([AdHosts.isAdBreakAnswer]).
     */
    fun isAdBreakAnswer(contentType: String?, bodyStart: String?): Boolean =
        AdHosts.isAdBreakAnswer(contentType, bodyStart)

    private fun RequestObservation.referer(): String? =
        headers.entries.firstOrNull { it.key.equals("Referer", ignoreCase = true) }?.value
            ?.takeIf { it.isNotBlank() && it.substringBefore('#') != pageUrl.substringBefore('#') }

    /**
     * Returns a bounded-probe input for strongly hinted opaque endpoints as well as URLs that
     * already classify as media. Ordinary page assets are intentionally excluded.
     */
    fun forMetadataProbe(observation: RequestObservation): MediaCandidate? {
        if (!observation.method.equals("GET", ignoreCase = true)) return null
        val uri = runCatching { URI(observation.requestUrl) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.userInfo != null) return null
        val kind = MediaUrlClassifier.classify(observation.requestUrl)
        if (kind == null && !observation.hasStrongMediaHint(uri)) return null
        return fromRequest(observation) ?: MediaCandidate(
            pageUrl = observation.pageUrl,
            mediaUrl = MediaFileUrls.wholeFile(observation.requestUrl),
            sources = setOf(CandidateSource.REQUEST),
            kind = MediaKind.UNKNOWN,
            requestContext = observation.requestContext(),
            confidence = CandidateConfidence.LOW,
            observedAtEpochMs = observation.observedAtEpochMs,
            // P43: a file an ad network serves is an ad, also when only its probe tells it.
            pageRole = adRole(observation.requestUrl, observation.referer()),
            adSign = adSign(observation.requestUrl, observation.referer()),
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
            pageRole = observation.pageRole ?: adRole(observation.mediaUrl, null),
            // P43: what the page's markup says about a file it names stands.
            adSign = AdHosts.adSign(observation.mediaUrl)
                .takeUnless { observation.pageRole == PageMediaRole.MAIN },
        )
    }

    fun fromRedirect(observation: RedirectObservation): MediaCandidate? {
        val kind = MediaUrlClassifier.classify(observation.toUrl) ?: return null
        return MediaCandidate(
            pageUrl = observation.pageUrl,
            mediaUrl = MediaFileUrls.wholeFile(observation.toUrl),
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

    private fun RequestObservation.hasStrongMediaHint(uri: URI): Boolean {
        val urlValue = buildString {
            append(uri.path.orEmpty())
            uri.query?.let { append('?').append(it) }
        }.lowercase()
        if (TRACKING_MARKERS.any(urlValue::contains)) return false
        val extension = uri.path
            ?.substringAfterLast('/', missingDelimiterValue = "")
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
        if (extension in STATIC_ASSET_EXTENSIONS) return false

        val accept = headers.entries
            .firstOrNull { it.key.equals("Accept", ignoreCase = true) }
            ?.value
            ?.lowercase()
            .orEmpty()
        return MEDIA_HINT_PATTERN.containsMatchIn(urlValue) ||
            accept.contains("video/") ||
            accept.contains("audio/") ||
            accept.contains("mpegurl") ||
            accept.contains("dash+xml")
    }

    private fun filenameFromContentDisposition(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val match = FILENAME_PATTERN.find(value) ?: return null
        return match.groupValues[1].trim().trim('"').takeIf(String::isNotBlank)
    }

    private val FILENAME_PATTERN = Regex("""(?i)filename\*?=(?:UTF-8''|)([^;]+)""")
    private val MEDIA_HINT_PATTERN = Regex(
        """(?:^|[/_.?=&-])(audio|download|manifest|media|playback|playlist|stream|video)(?:$|[/_.?=&-])""",
    )
    private val STATIC_ASSET_EXTENSIONS = setOf(
        "css", "gif", "ico", "jpeg", "jpg", "js", "json", "map", "png", "svg", "webp",
        "woff", "woff2",
    )
    private val TRACKING_MARKERS = setOf(
        "/analytics", "/beacon", "/pixel", "/telemetry", "/tracking",
    )
}
