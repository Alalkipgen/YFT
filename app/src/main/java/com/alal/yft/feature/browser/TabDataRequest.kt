package com.alal.yft.feature.browser

import kotlinx.coroutines.CompletableDeferred

/**
 * P40 step 2: one read of the TikTok tab's own data for the post [postId]. The screen runs the
 * page script on the tab's WebView (main thread) and [answer]s with the script's result and
 * the tab's TikTok cookies (never logged); a read nobody waits for any more is skipped.
 */
class TabDataRequest(val postId: String) {
    private val reply = CompletableDeferred<Reply>()

    /** Whether the lookup still waits for this read. */
    val isWaiting: Boolean
        get() = reply.isActive

    fun answer(javascriptResult: String?, cookie: String?) {
        reply.complete(Reply(javascriptResult, cookie))
    }

    internal suspend fun await(): Reply = reply.await()

    internal fun giveUp() {
        reply.cancel()
    }

    override fun toString(): String = "TabDataRequest(waiting=$isWaiting)"

    class Reply(val javascriptResult: String?, val cookie: String?) {
        override fun toString(): String = "Reply(answered=${javascriptResult != null})"
    }
}