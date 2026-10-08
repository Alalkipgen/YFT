package com.alal.yft.core.browser.detection

import com.alal.yft.core.model.media.AdSign
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageMediaRole
import java.net.URI

/**
 * P28: tells the ad a page's player plays first from the page's video. A player asks for an ad
 * break (a VAST or VMAP document, [BrowserObservationMapper.isAdBreakRequest]) and then fetches
 * the ad's file. A whole file (not a stream) first seen within [windowMillis] of such a request
 * in the same frame (the same `Referer` origin), from another site than the page's, is the
 * ad's: [PageMediaRole.PREVIEW], P43: with [AdSign.AD_BREAK]. A file seen before the request,
 * the page's own site's files, streams, and files a site adapter or the page named keep their
 * role. P43: an ad request is also told by its answer, a VAST or VMAP document ([onAnswer]),
 * when its address says nothing. One page at a time; safe to call from any thread.
 */
class VastAdTracker(
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    private val maxFiles: Int = MAX_FILES,
) {
    private val lock = Any()
    private var pageUrl: String? = null
    private var adBreakAt: Long? = null
    private var adBreakFrame: String? = null

    /** Each file's first sight on this page: whether it was the ad's. */
    private val decided = LinkedHashMap<String, Boolean>()

    fun beginPage(pageUrl: String) {
        synchronized(lock) {
            this.pageUrl = pageUrl
            adBreakAt = null
            adBreakFrame = null
            decided.clear()
        }
    }

    /** The same page under a new address keeps what was seen on it. */
    fun movePage(pageUrl: String) {
        synchronized(lock) {
            if (this.pageUrl != null) this.pageUrl = pageUrl
        }
    }

    /** Notes a request of the page that asks for an ad break. */
    fun onRequest(observation: RequestObservation) {
        if (!BrowserObservationMapper.isAdBreakRequest(observation.requestUrl)) return
        startBreak(observation)
    }

    /**
     * P43: notes the answer to a request of the page ([observation]) whose address said nothing:
     * one of [contentType] whose body starts with [bodyStart] that is a VAST or VMAP document
     * ([BrowserObservationMapper.isAdBreakAnswer]) asked for an ad break. The answer's body is
     * only looked at here, never kept or logged.
     */
    fun onAnswer(observation: RequestObservation, contentType: String?, bodyStart: String?) {
        if (!BrowserObservationMapper.isAdBreakAnswer(contentType, bodyStart)) return
        startBreak(observation)
    }

    private fun startBreak(observation: RequestObservation) {
        synchronized(lock) {
            if (observation.pageUrl != pageUrl) return
            adBreakAt = observation.observedAtEpochMs
            adBreakFrame = frameOf(observation)
        }
    }

    /**
     * [candidate] (the media file [observation] asked for), marked as the ad's file when it
     * came right after an ad break request.
     */
    fun marked(candidate: MediaCandidate, observation: RequestObservation): MediaCandidate {
        if (candidate.kind != MediaKind.DIRECT || candidate.videoId != null) return candidate
        if (candidate.pageRole != null) return candidate
        val ad = synchronized(lock) {
            if (candidate.pageUrl != pageUrl) return candidate
            decided[candidate.mediaUrl] ?: decide(candidate, observation).also { ad ->
                if (decided.size >= maxFiles) decided.remove(decided.keys.first())
                decided[candidate.mediaUrl] = ad
            }
        }
        return if (ad) {
            candidate.copy(pageRole = PageMediaRole.PREVIEW, adSign = AdSign.AD_BREAK)
        } else {
            candidate
        }
    }

    private fun decide(candidate: MediaCandidate, observation: RequestObservation): Boolean {
        val asked = adBreakAt ?: return false
        val gap = observation.observedAtEpochMs - asked
        if (gap < 0 || gap > windowMillis) return false
        if (frameOf(observation) != adBreakFrame) return false
        return site(candidate.mediaUrl).let { it != null && it != site(candidate.pageUrl) }
    }

    /** The frame that asked: its `Referer`'s origin, else the page's. */
    private fun frameOf(observation: RequestObservation): String? {
        val referer = observation.headers.entries
            .firstOrNull { it.key.equals("Referer", ignoreCase = true) }?.value
            ?.takeIf(String::isNotBlank)
        return origin(referer ?: observation.pageUrl)
    }

    private fun origin(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        return "${uri.scheme?.lowercase()}://$host"
    }

    /** The site of [url]: its host's last two labels, or three under `co.uk`-like names. */
    private fun site(url: String): String? {
        val host = runCatching { URI(url).host }.getOrNull()?.lowercase()?.trimEnd('.')
            ?.takeIf(String::isNotEmpty) ?: return null
        val labels = host.split('.')
        if (labels.size <= 2) return host
        val country = labels.last().length == 2 && labels[labels.size - 2].length <= 3
        return labels.takeLast(if (country) 3 else 2).joinToString(".")
    }

    companion object {
        /** P28: an ad's file follows its ad break request within a few seconds. */
        const val DEFAULT_WINDOW_MILLIS = 6_000L
        private const val MAX_FILES = 200
    }
}
