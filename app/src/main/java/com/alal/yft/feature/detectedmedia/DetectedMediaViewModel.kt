package com.alal.yft.feature.detectedmedia

import androidx.lifecycle.ViewModel
import com.alal.yft.core.media.session.PreviewSelectionStore
import com.alal.yft.core.model.media.MediaCandidate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class DetectedMediaViewModel @Inject constructor(
    private val store: DetectedMediaStore,
    private val previewSelectionStore: PreviewSelectionStore,
) : ViewModel() {
    val page: StateFlow<DetectedPage?> = store.page

    /**
     * Hands [candidate] to Preview through the in-memory selection. Returns false when the
     * candidate is no longer listed or carries a DRM hint, so nothing navigates.
     */
    fun selectForPreview(candidate: MediaCandidate): Boolean {
        val listed = store.page.value?.candidates.orEmpty()
        if (candidate !in listed || candidate.drmHint == true) return false
        previewSelectionStore.select(candidate)
        return true
    }

    fun clear() {
        store.clear()
    }
}
