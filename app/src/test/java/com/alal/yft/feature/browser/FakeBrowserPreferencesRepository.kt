package com.alal.yft.feature.browser

import com.alal.yft.core.data.preferences.BrowserPreferencesRepository
import com.alal.yft.core.model.settings.BrowserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Settings › Browser held in memory for browser and settings tests. */
internal class FakeBrowserPreferencesRepository(
    initial: BrowserPreferences = BrowserPreferences(),
) : BrowserPreferencesRepository {
    private val state = MutableStateFlow(initial)
    override val preferences: StateFlow<BrowserPreferences> = state

    fun set(preferences: BrowserPreferences) {
        state.value = preferences
    }

    override suspend fun update(transform: (BrowserPreferences) -> BrowserPreferences) {
        state.update(transform)
    }
}
