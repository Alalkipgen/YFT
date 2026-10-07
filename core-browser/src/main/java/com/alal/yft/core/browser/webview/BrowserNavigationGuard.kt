package com.alal.yft.core.browser.webview

import androidx.annotation.MainThread
import com.alal.yft.core.browser.policy.AdRedirectPolicy

/** A navigation or window the browser kept from replacing the page (P32). */
data class BlockedNavigation(
    val url: String,
    val host: String,
    /** A new window (`window.open`, `target="_blank"`) rather than the tab itself. */
    val window: Boolean,
    val reason: AdRedirectPolicy.Reason,
)

/**
 * What the browser's WebView clients block (P32), shared by both clients and the screen.
 * [enabled] is Settings › Browser › Block pop-ups and ad redirects. Page timing and the user's
 * choices are main-thread state; [blocksResource] is read from request threads.
 */
class BrowserNavigationGuard(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    var enabled: Boolean = true

    private var pageStartedAt = 0L
    private var pageFinishedAt: Long? = null
    private var userChoiceAt: Long? = null
    private var userPageStarted = false

    @MainThread
    fun pageStarted() {
        pageStartedAt = clock()
        pageFinishedAt = null
        if (userChoiceAt != null) userPageStarted = true
    }

    @MainThread
    fun pageFinished() {
        if (pageFinishedAt == null) pageFinishedAt = clock()
    }

    /**
     * The user typed an address, picked a page or tapped Open on a notice: that page, its server
     * redirects and the forwards while it opens pass, also through a listed network.
     */
    @MainThread
    fun userNavigation() {
        userChoiceAt = clock()
        userPageStarted = false
    }

    /** A top-level navigation the page asked for; null when it may load. */
    @MainThread
    fun blockedNavigation(
        url: String,
        pageUrl: String?,
        hasGesture: Boolean,
        isRedirect: Boolean,
    ): BlockedNavigation? {
        if (!enabled || userChoiceActive()) return null
        val decision = AdRedirectPolicy.decide(url, pageUrl, hasGesture, isRedirect, pageOpen())
        return (decision as? AdRedirectPolicy.Decision.Block)?.let {
            BlockedNavigation(url, it.host, window = false, reason = it.reason)
        }
    }

    /** A new window: null when it opens in the current tab. */
    @MainThread
    fun blockedWindow(url: String, pageUrl: String?, isUserGesture: Boolean): BlockedNavigation? {
        if (!enabled) return null
        val decision = AdRedirectPolicy.decideWindow(url, pageUrl, isUserGesture)
        return (decision as? AdRedirectPolicy.WindowDecision.Block)?.let {
            BlockedNavigation(url, it.host, window = true, reason = it.reason)
        }
    }

    /** A script, frame or image of a listed network; any thread. */
    fun blocksResource(host: String?): Boolean = enabled && AdRedirectPolicy.blocksResource(host)

    /** The page finished a moment ago or has been loading for long: it is no redirect page. */
    private fun pageOpen(): Boolean {
        val now = clock()
        val finished = pageFinishedAt
        if (finished != null) return now - finished >= FORWARD_GRACE_MS
        return now - pageStartedAt >= LONG_LOAD_MS
    }

    /** The page the user chose has not opened yet: it has not finished, or only a moment ago. */
    private fun userChoiceActive(): Boolean {
        val chosen = userChoiceAt ?: return false
        val now = clock()
        if (now - chosen >= USER_CHOICE_MS) return false
        val finished = pageFinishedAt
        return !userPageStarted || finished == null || now - finished < FORWARD_GRACE_MS
    }

    companion object {
        /** A page that sends the tab on within this time after it finished is forwarding. */
        const val FORWARD_GRACE_MS = 1_500L

        /** A page still loading after this long counts as open (ads load while it loads). */
        const val LONG_LOAD_MS = 8_000L

        /** The longest time the user's own choice lets its redirects through. */
        const val USER_CHOICE_MS = 10_000L
    }
}
