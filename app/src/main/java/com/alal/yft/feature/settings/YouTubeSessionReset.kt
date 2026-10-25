package com.alal.yft.feature.settings

import android.webkit.CookieManager
import android.webkit.WebStorage
import com.alal.yft.detection.SiteLookupCache
import java.net.URI
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Item 7 Track B3: "Reset YouTube session", offered on a download YouTube refused while its link
 * still had time left (`YOUTUBE_REFUSED`, B1). In D7 runs 13 / 13b only the app's Clear browsing
 * data fixed such a refusal, and that signs the user out of every site. This forgets YouTube's
 * session in YFT's browser and nothing of any other site: YouTube's cookies (the visitor ID and a
 * YouTube sign-in with them), YouTube's site storage and the YouTube lookups YFT remembers (P17).
 * Other sites, Google's accounts page included, keep their cookies and sign-ins. The HTTP cache
 * is shared by every site and stays (Clear browsing data clears it).
 */
fun interface YouTubeSessionReset {
    suspend fun reset(): YouTubeSessionResetResult

    companion object {
        /** For tests and previews without a browser: nothing to reset. */
        val Unavailable = YouTubeSessionReset { YouTubeSessionResetResult(0, 0) }
    }
}

/**
 * [cookiesRemoved] of YouTube's cookies went; [cookiesLeft] are still sent to YouTube after the
 * reset (a cookie the browser kept, which Clear browsing data still removes).
 */
data class YouTubeSessionResetResult(val cookiesRemoved: Int, val cookiesLeft: Int)

/** The browser's cookie jar as far as the reset needs it, so the reset is tested with a fake. */
interface SiteCookieJar {
    /** The names of the cookies the browser sends to [url]. */
    suspend fun names(url: String): Set<String>

    /** Stores the `Set-Cookie` value [setCookie] for [url]; an expired one deletes its cookie. */
    suspend fun set(url: String, setCookie: String)

    suspend fun flush()
}

/** The browser's site storage (local storage, IndexedDB and the like) of one origin. */
fun interface SiteStorage {
    suspend fun deleteOrigin(origin: String)
}

/**
 * Expires every cookie the browser sends to YouTube's pages, both as a cookie of that host only
 * and as one of `youtube.com` and its subdomains (a cookie only goes when its name, domain and
 * path match, and the browser does not tell which it is), then deletes YouTube's site storage
 * and forgets YouTube's remembered lookups.
 */
class CookieYouTubeSessionReset(
    private val cookies: SiteCookieJar,
    private val storage: SiteStorage,
    private val lookups: SiteLookupCache? = null,
) : YouTubeSessionReset {
    override suspend fun reset(): YouTubeSessionResetResult {
        val before = names()
        YOUTUBE_PAGES.forEach { page ->
            cookies.names(page).forEach { name -> expire(page, name) }
        }
        cookies.flush()
        YOUTUBE_PAGES.forEach { page -> storage.deleteOrigin(page.removeSuffix("/")) }
        lookups?.forgetSite(YOUTUBE_SITE_ID)
        val left = names()
        return YouTubeSessionResetResult(
            cookiesRemoved = (before - left).size,
            cookiesLeft = left.size,
        )
    }

    private suspend fun names(): Set<String> =
        YOUTUBE_PAGES.flatMapTo(mutableSetOf<String>()) { page -> cookies.names(page) }

    private suspend fun expire(page: String, name: String) {
        val host = URI(page).host
        cookies.set(page, "$name=; $EXPIRED")
        cookies.set(page, "$name=; $EXPIRED; Domain=$YOUTUBE_DOMAIN")
        // A cookie whose domain is the page's own host is neither of the two above.
        if (host != YOUTUBE_DOMAIN) cookies.set(page, "$name=; $EXPIRED; Domain=$host")
    }

    internal companion object {
        const val YOUTUBE_SITE_ID = "youtube"
        const val YOUTUBE_DOMAIN = "youtube.com"

        /** The pages whose cookies and storage go; other YouTube hosts share `youtube.com`'s. */
        val YOUTUBE_PAGES = listOf(
            "https://www.youtube.com/",
            "https://m.youtube.com/",
            "https://music.youtube.com/",
            "https://youtube.com/",
        )

        /** Gone now, sent only over HTTPS (as `__Secure-` and `__Host-` cookies need), for "/". */
        private const val EXPIRED =
            "Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Secure"
    }
}

/** WebView's process-wide cookie store, shared by the browser and the player-script host. */
object WebViewSiteCookieJar : SiteCookieJar {
    override suspend fun names(url: String): Set<String> = withContext(Dispatchers.Main) {
        CookieManager.getInstance().getCookie(url)
            .orEmpty()
            .split(';')
            .mapNotNullTo(mutableSetOf()) { pair ->
                pair.substringBefore('=').trim().takeIf(String::isNotEmpty)
            }
    }

    override suspend fun set(url: String, setCookie: String): Unit = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<Unit> { continuation ->
            CookieManager.getInstance().setCookie(url, setCookie) { continuation.resume(Unit) }
        }
    }

    override suspend fun flush(): Unit = withContext(Dispatchers.Main) {
        CookieManager.getInstance().flush()
    }
}

/** WebView's storage of an origin: local storage, IndexedDB, caches and the like. */
object WebViewSiteStorage : SiteStorage {
    override suspend fun deleteOrigin(origin: String): Unit = withContext(Dispatchers.Main) {
        WebStorage.getInstance().deleteOrigin(origin)
    }
}
