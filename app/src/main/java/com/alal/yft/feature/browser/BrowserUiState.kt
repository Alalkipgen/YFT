package com.alal.yft.feature.browser

import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.PageVideoFacts
import com.alal.yft.core.model.settings.HomeSite
import com.alal.yft.core.model.settings.HomeSites

data class BrowserUiState(
    val address: String = "",
    val currentUrl: String? = null,
    val pageTitle: String? = null,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val errorMessage: String? = null,
    val candidates: List<MediaCandidate> = emptyList(),
    val sites: List<HomeSite> = HomeSites.DEFAULTS,
    /**
     * Explanation from a site adapter that matched this page but could not extract media.
     *
     * Kept separate from [errorMessage] so a site-specific outcome never looks like a page load
     * failure, and so the generic detector can still surface candidates alongside it.
     */
    val siteNotice: String? = null,
    /** Whether the notice offers Try again, because asking again can change the answer. */
    val canRetrySiteLookup: Boolean = false,
    /**
     * P5: the page is on YouTube, Facebook or TikTok, so the Download button shows even before a
     * file was found and can look for the video on screen.
     */
    val findsFocusedVideo: Boolean = false,
    /**
     * P5: the page is one of those sites' feeds, not one video's own page, so whatever it found
     * may belong to any video in the feed and the button looks for the one on screen.
     */
    val feedPage: Boolean = false,
    /** P5: the video on screen is being looked for or looked up. */
    val findingFocusedVideo: Boolean = false,
    /** P5: a short message about the video on screen, such as none being in view. */
    val focusNotice: String? = null,
    /** P10: a focused lookup failed on the network; Retry repeats that video only. */
    val canRetryFocusedLookup: Boolean = false,
    /**
     * P12: a site adapter reads this page's own video (a watch, shorts, reel, video or post
     * page), so the Download button always means that video.
     */
    val sitePage: Boolean = false,
    /** P12: the page's own lookup is running; the Download button shows a small spinner. */
    val pageLookupRunning: Boolean = false,
    /**
     * P37: counts the sheet's "Reload page and try again"; each new count reloads the tab once
     * without its cache.
     */
    val reloadRequest: Int = 0,
    /** P37: the tab's reload without its cache has not finished yet. */
    val noCacheLoad: Boolean = false,
    /**
     * P28: what the page states about its own video (its length, title and picture). The
     * [candidates] already carry its word: a file of that length is the page's video, one far
     * shorter its ad.
     */
    val pageFacts: PageVideoFacts? = null,
)
