package com.alal.yft.core.browser.detection

import com.alal.yft.core.model.media.AdSign
import com.alal.yft.extractor.generic.classifier.MediaUrlClassifier
import java.net.URI

/**
 * P43: YFT's own short list of the ad servers and video-ad networks whose files a page's player
 * plays before the page's video, written for YFT from what these networks are known for; not a
 * copy of any filter list. A media file (MP4 or HLS) from one of these hosts is always an ad
 * ([AdSign.AD_HOST]); a file under an ad folder is one too ([AdSign.AD_ADDRESS]). It also tells
 * a request for an ad break: Google IMA's and DoubleClick's ad requests, a VAST or VMAP document
 * by its address, a `preroll` or `/ads/` request that is not a media file or a page asset, and
 * an answer whose body is a VAST or VMAP document ([isAdBreakAnswer]).
 */
object AdHosts {
    /** P24: whole host labels of ad servers: `ads.example.com`, `vast.example.net`. */
    private val AD_HOST_LABELS = setOf("ad", "ads", "adserver", "adservice", "adsystem", "vast")

    /** P24: names of ad servers and networks wherever they stand in a host. */
    private val AD_HOST_WORDS = setOf(
        "doubleclick", "googlesyndication", "googleadservices", "imasdk", "adnxs",
        "amazon-adsystem", "advertising", "pubmatic", "rubiconproject", "spotxchange",
        "springserve", "teads", "taboola", "outbrain", "criteo",
    )

    /**
     * P28, P43: video-ad networks of tube and adult sites without an adapter, by domain: their
     * own domains, the domain and every subdomain of it.
     */
    private val AD_DOMAINS = setOf(
        "exoclick.com", "exosrv.com", "exdynsrv.com", "magsrv.com", "realsrv.com",
        "trafficjunky.net", "trafficjunky.com", "juicyads.com", "jads.co", "tsyndicate.com",
        "trafficstars.com", "adsterra.com", "adtng.com",
    )

    /**
     * P43: the same networks' own names, as a whole host label under any suffix
     * (`cdn.adtng.com`, `syndication.realsrv.com`); none of them is an ordinary word.
     */
    private val AD_NETWORK_NAMES = setOf(
        "trafficjunky", "exoclick", "exosrv", "exdynsrv", "juicyads", "tsyndicate",
        "trafficstars", "magsrv", "adtng", "realsrv", "adsterra",
    )

    /** P43: Google IMA's and DoubleClick's hosts of video-ad requests, with their paths. */
    private val IMA_AD_REQUEST_HOSTS = setOf(
        "pubads.g.doubleclick.net", "securepubads.g.doubleclick.net",
        "googleads.g.doubleclick.net", "ad.doubleclick.net",
    )
    private val IMA_AD_REQUEST_PATHS = listOf(
        "/gampad/ads", "/gampad/live/ads", "/pagead/ads", "/ddm/pfadx/",
    )

    /**
     * P43: an ad's tracking request (its start, its end, a click) comes when its file already
     * played: it never starts an ad break, or the page's video right after the ad would look
     * like one.
     */
    private val TRACKING = Regex(
        "(?i)track|event|impression|beacon|pixel|click|complete|quartile|ping|error",
    )

    /** P24: whole folders of ad files: a video called "the-vast-ocean" is no ad. */
    private val AD_FOLDERS = setOf(
        "ad", "ads", "adserver", "adverts", "vast", "vpaid", "preroll", "midroll",
    )

    /** P28: a path part that names a VAST or VMAP document. */
    private val AD_BREAK_PART = Regex("""(vast|vmap)\d{0,2}(\.(xml|php|aspx?|jsp|json|cgi))?""")

    /** P43: a path part that asks for a pre-roll: `preroll`, `pre-roll.xml`, `prerolls`. */
    private val PREROLL_PART = Regex("""pre-?rolls?(\.[a-z]{2,5})?""")

    /** P43: a query value that asks for a VAST or VMAP answer (`output=vast`, `xml_vast4`). */
    private val AD_BREAK_QUERY = Regex("""(?i)(^|&)(output|format|fmt|type)=(xml_)?(vast|vmap)""")

    /** Page assets: a banner script or picture under `/ads/` is not an ad break. */
    private val ASSET_EXTENSIONS = setOf(
        "css", "gif", "ico", "jpeg", "jpg", "js", "map", "png", "svg", "webp", "woff", "woff2",
    )

    /** The start of a VAST or VMAP document, after an XML declaration and comments. */
    private val AD_DOCUMENT = Regex(
        """^(?:\uFEFF)?\s*(?:<\?xml[^>]*\?>\s*)?(?:<!--.*?-->\s*)*<(?:[a-z]+:)?(?:VAST|VMAP)\b""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    /** Whether [host] is an ad server's or a video-ad network's. */
    fun isAdHost(host: String?): Boolean {
        val name = host?.lowercase()?.trimEnd('.')?.takeIf(String::isNotEmpty) ?: return false
        val labels = name.split('.')
        return labels.any { it in AD_HOST_LABELS || it in AD_NETWORK_NAMES } ||
            AD_HOST_WORDS.any { name.contains(it) } ||
            AD_DOMAINS.any { name == it || name.endsWith(".$it") }
    }

    /**
     * What shows that [url] is an ad's: its host ([AdSign.AD_HOST]) or a whole folder of its
     * path ([AdSign.AD_ADDRESS]); null when nothing does.
     */
    fun adSign(url: String): AdSign? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (isAdHost(uri.host)) return AdSign.AD_HOST
        val folders = uri.path.orEmpty().lowercase().split('/')
        return AdSign.AD_ADDRESS.takeIf { folders.any { it in AD_FOLDERS } }
    }

    /**
     * Whether [url] asks for an ad break: a VAST or VMAP document by a whole path part
     * (`vast`, `vast3.xml`, `vmap.php`; never `vast-ocean.mp4`), P43: Google IMA's or
     * DoubleClick's ad request, a query asking for a VAST answer, or a `preroll` or `/ads/`
     * request that is neither a media file (that is the ad's file itself) nor a page asset.
     * An ad's tracking request never is.
     */
    fun isAdBreakRequest(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") return false
        val parts = uri.path.orEmpty().lowercase().split('/')
        if (parts.any { AD_BREAK_PART.matches(it) }) return true
        val path = uri.path.orEmpty().lowercase()
        if (TRACKING.containsMatchIn(path)) return false
        val host = uri.host?.lowercase().orEmpty()
        if (host in IMA_AD_REQUEST_HOSTS && IMA_AD_REQUEST_PATHS.any(path::startsWith)) return true
        if (uri.rawQuery?.let(AD_BREAK_QUERY::containsMatchIn) == true) return true
        if (MediaUrlClassifier.classify(url) != null) return false
        val extension = parts.last().substringAfterLast('.', missingDelimiterValue = "")
        if (extension in ASSET_EXTENSIONS) return false
        return parts.any { PREROLL_PART.matches(it) } || parts.dropLast(1).any { it == "ads" }
    }

    /**
     * P43: whether an answer is an ad break's: its content type is XML (or unknown) and its body
     * ([bodyStart], its first characters) is a VAST or VMAP document.
     */
    fun isAdBreakAnswer(contentType: String?, bodyStart: String?): Boolean {
        val type = contentType?.substringBefore(';')?.trim()?.lowercase()
        val xml = type == null || type.isEmpty() || type == "application/xml" ||
            type == "text/xml" || type.endsWith("+xml") || type == "text/plain"
        return xml && bodyStart != null && AD_DOCUMENT.containsMatchIn(bodyStart.take(MAX_SNIFF))
    }

    private const val MAX_SNIFF = 2_048
}
