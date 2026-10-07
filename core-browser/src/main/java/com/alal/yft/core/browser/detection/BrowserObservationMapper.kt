package com.alal.yft.core.browser.detection

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
        )
    }

    /**
     * P24: [PageMediaRole.PREVIEW] for an ad's file: its address, or the address of the frame
     * that asked for it ([frameUrl], the request's `Referer`), names an ad server or ad words.
     * The page's own player asks with the page as its `Referer`, so its files keep no role.
     */
    fun adRole(mediaUrl: String, frameUrl: String?): PageMediaRole? {
        val ad = looksLikeAd(mediaUrl) || frameUrl?.let(::looksLikeAd) == true
        return PageMediaRole.PREVIEW.takeIf { ad }
    }

    private fun looksLikeAd(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase().orEmpty()
        val labels = host.split('.')
        if (labels.any { it in AD_HOST_LABELS } || AD_HOSTS.any { host.contains(it) }) return true
        // P28: the ad networks of video sites without an adapter, by their whole domain.
        if (AD_DOMAINS.any { host == it || host.endsWith(".$it") }) return true
        // Whole folders only: a video called "the-vast-ocean" is no ad.
        return uri.path.orEmpty().lowercase().split('/').any { it in AD_FOLDERS }
    }

    /**
     * P28: whether [url] asks for an ad break: a VAST or VMAP document, by a whole path part
     * (`vast`, `vast3.xml`, `vmap.php`; never `vast-ocean.mp4`). The file the player fetches
     * right after one is the ad's ([VastAdTracker]).
     */
    fun isAdBreakRequest(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") return false
        return uri.path.orEmpty().lowercase().split('/').any { AD_BREAK_PART.matches(it) }
    }

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

    /** P24: host labels and hosts of ad servers, and the folders of ad files. */
    private val AD_HOST_LABELS = setOf("ad", "ads", "adserver", "adservice", "adsystem", "vast")
    private val AD_HOSTS = setOf(
        "doubleclick", "googlesyndication", "googleadservices", "imasdk", "adnxs",
        "amazon-adsystem", "advertising", "pubmatic", "rubiconproject", "spotxchange",
        "springserve", "teads", "taboola", "outbrain", "criteo",
    )
    /**
     * P28: ad networks seen serving the pre-roll on video sites without an adapter, by domain.
     * Only networks whose ads were confirmed; a domain is never guessed from its name.
     */
    private val AD_DOMAINS = setOf(
        "exoclick.com", "exosrv.com", "magsrv.com", "realsrv.com", "trafficjunky.net",
        "trafficjunky.com", "juicyads.com", "jads.co", "tsyndicate.com", "trafficstars.com",
        "adsterra.com",
    )

    /** P28: a path part that names a VAST or VMAP document. */
    private val AD_BREAK_PART = Regex("""(vast|vmap)\d{0,2}(\.(xml|php|aspx?|jsp|json|cgi))?""")
    private val AD_FOLDERS = setOf(
        "ad", "ads", "adserver", "adverts", "vast", "vpaid", "preroll", "midroll",
    )
}
