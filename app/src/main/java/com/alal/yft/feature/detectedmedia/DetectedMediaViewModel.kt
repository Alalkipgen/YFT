package com.alal.yft.feature.detectedmedia

import androidx.lifecycle.ViewModel
import com.alal.yft.core.model.media.MediaGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class DetectedMediaViewModel @Inject constructor(
    private val store: DetectedMediaStore,
) : ViewModel() {
    val page: StateFlow<DetectedPage?> = store.page

    /**
     * Hands [video] to the download sheet. Returns false when the page no longer lists every
     * one of its candidates, or one carries a DRM hint, so nothing navigates.
     */
    fun selectForDownload(video: MediaGroup): Boolean {
        val listed = store.page.value?.candidates.orEmpty()
        if (video.candidates.any { it !in listed || it.drmHint == true }) return false
        store.select(video)
        return true
    }

    fun clear() {
        store.clear()
    }
}
