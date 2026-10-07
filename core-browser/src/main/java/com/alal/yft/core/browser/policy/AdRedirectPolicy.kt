package com.alal.yft.core.browser.policy

import java.net.URI
import java.util.Locale

/**
 * Where a page may send the browser's tab (P32, decision F4). Pure: the WebView clients ask it
 * with what the WebView reports about a navigation.
 *
 * Blocked: a navigation to a host of [AdNetworks] (pop-up and redirect ad networks; also a hop
 * of a server redirect), and a navigation to another site that the page started by itself (no
 * tap, not a server redirect) once it has opened. Allowed: addresses the user typed or chose
 * (they never reach this policy), the user's taps on links (also to other sites), the same
 * site, server redirects, and a page that forwards to another site while it opens (link
 * shims such as `l.facebook.com`, `t.co` or Google's `/url`). App links stay with
 * [AppLinkPolicy].
 */
object AdRedirectPolicy {
    enum class Reason {
        /** The host is on YFT's list of pop-up and redirect ad networks. */
        AD_NETWORK,

        /** The page tried to send the tab to another site without the user's tap. */
        NO_TAP,
    }

    sealed interface Decision {
        data object Allow : Decision

        data class Block(val host: String, val reason: Reason) : Decision
    }

    /** What happens to a new window (`window.open`, `target="_blank"`). */
    sealed interface WindowDecision {
        /** The user's tap opened a page of the same site: it opens in the current tab. */
        data object OpenHere : WindowDecision

        data class Block(val host: String, val reason: Reason) : WindowDecision
    }

    /**
     * A top-level navigation the page asked for. [pageUrl] is the page in the tab now;
     * [pageOpen] is false while that page is still loading or has just finished (a page that
     * forwards as it opens is a redirect page, not an ad).
     */
    fun decide(
        url: String,
        pageUrl: String?,
        hasGesture: Boolean,
        isRedirect: Boolean,
        pageOpen: Boolean,
    ): Decision {
        val host = hostOf(url) ?: return Decision.Allow
        AdNetworks.find(host)?.let { return Decision.Block(host, Reason.AD_NETWORK) }
        if (hasGesture || isRedirect || !pageOpen) return Decision.Allow
        val pageHost = pageUrl?.let(::hostOf) ?: return Decision.Allow
        if (sameSite(host, pageHost)) return Decision.Allow
        return Decision.Block(host, Reason.NO_TAP)
    }

    /** A new window: only the user's tap to a page of the same site opens, in this tab. */
    fun decideWindow(url: String, pageUrl: String?, isUserGesture: Boolean): WindowDecision {
        val host = hostOf(url).orEmpty()
        if (host.isNotEmpty() && AdNetworks.find(host) != null) {
            return WindowDecision.Block(host, Reason.AD_NETWORK)
        }
        val pageHost = pageUrl?.let(::hostOf)
        val sameSite = host.isNotEmpty() && pageHost != null && sameSite(host, pageHost)
        return if (isUserGesture && sameSite) {
            WindowDecision.OpenHere
        } else {
            WindowDecision.Block(host, Reason.NO_TAP)
        }
    }

    /** A script, frame or image of a listed network gets an empty answer. */
    fun blocksResource(host: String?): Boolean =
        host != null && AdNetworks.find(host.lowercase(Locale.US))?.blockResources == true

    fun hostOf(url: String): String? = runCatching { URI(url.trim()).host }.getOrNull()
        ?.lowercase(Locale.US)
        ?.trimEnd('.')
        ?.takeIf(String::isNotEmpty)

    /** Two hosts of the same site: the same registrable name (`m.youtube.com`, `youtube.com`). */
    fun sameSite(first: String, second: String): Boolean = siteOf(first) == siteOf(second)

    /**
     * The registrable part of [host] without a public-suffix list: the last two labels, or the
     * last three under a known two-part suffix (`bbc.co.uk`, `example.com.mm`, `x.github.io`).
     */
    fun siteOf(host: String): String {
        val name = host.lowercase(Locale.US).trimEnd('.')
        if (name.all { it.isDigit() || it == '.' } || name.contains(':')) return name
        val labels = name.split('.')
        if (labels.size <= 2) return name
        val lastTwo = labels.takeLast(2).joinToString(".")
        val keep = if (lastTwo in TWO_PART_SUFFIXES) 3 else 2
        return labels.takeLast(keep).joinToString(".")
    }

    private val TWO_PART_SUFFIXES = setOf(
        // Country second levels.
        "com.mm", "net.mm", "org.mm", "edu.mm", "gov.mm",
        "co.uk", "org.uk", "ac.uk", "gov.uk", "me.uk",
        "com.au", "net.au", "org.au", "edu.au", "gov.au",
        "co.jp", "ne.jp", "or.jp", "ac.jp", "co.kr", "or.kr", "co.nz", "co.za", "co.in",
        "co.id", "co.th", "in.th", "com.br", "com.cn", "com.hk", "com.sg", "com.tw",
        "com.tr", "com.vn", "com.my", "com.ph", "com.mx", "com.ar", "com.sa", "com.eg",
        "com.pk", "com.bd", "com.np", "com.kh", "com.la",
        // Hosting names where every subdomain is somebody else's site.
        "github.io", "blogspot.com", "appspot.com", "herokuapp.com", "netlify.app",
        "vercel.app", "pages.dev", "web.app", "firebaseapp.com", "wordpress.com",
    )
}
