package com.alal.yft.core.media.session

import com.alal.yft.core.model.media.MediaCandidate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps the selected browser candidate in memory so credential-bearing URLs never enter a route,
 * saved state, database, or log.
 */
@Singleton
class PreviewSelectionStore @Inject constructor() {
    private val mutableSelection = MutableStateFlow<MediaCandidate?>(null)
    val selection: StateFlow<MediaCandidate?> = mutableSelection.asStateFlow()

    fun select(candidate: MediaCandidate) {
        mutableSelection.value = candidate
    }

    fun clear() {
        mutableSelection.value = null
    }
}