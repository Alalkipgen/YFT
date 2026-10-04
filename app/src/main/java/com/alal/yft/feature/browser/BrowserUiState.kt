package com.alal.yft.feature.browser

import com.alal.yft.core.model.media.MediaCandidate
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
)
