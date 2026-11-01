package com.alal.yft.feature.browser

import com.alal.yft.extractor.master.social.InstagramTabProbe
import kotlinx.coroutines.CompletableDeferred

/**
 * Instagram tab-fetch step (debug only): one ask of its probe for [probe]'s post, which the browser
 * screen runs in its tab ([com.alal.yft.detection.reads.InstagramProbeScript]) and answers; the
 * lookup waits for it at most as long as Instagram's ladder allows.
 */
class InstagramProbeRequest(val probe: InstagramTabProbe.Request) {
    private val reply = CompletableDeferred<InstagramTabProbe.Reply>()

    /** Whether the lookup still waits for this probe. */
    val isWaiting: Boolean
        get() = reply.isActive

    fun answer(value: InstagramTabProbe.Reply) {
        reply.complete(value)
    }

    internal suspend fun await(): InstagramTabProbe.Reply = reply.await()

    internal fun giveUp() {
        reply.cancel()
    }

    override fun toString(): String = "InstagramProbeRequest(waiting=$isWaiting)"
}
