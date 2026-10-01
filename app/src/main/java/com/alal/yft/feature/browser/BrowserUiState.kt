package com.alal.yft.feature.browser

import com.alal.yft.core.model.media.MediaCandidate

data class BrowserUiState(
    val address: String = "",
    val currentUrl: String? = null,
    val pageTitle: String? = null,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val errorMessage: String? = null,
    val candidates: List<MediaCandidate> = emptyList(),
)