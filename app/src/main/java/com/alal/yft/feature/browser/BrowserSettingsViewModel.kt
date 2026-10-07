package com.alal.yft.feature.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alal.yft.core.data.preferences.BrowserPreferencesRepository
import com.alal.yft.core.model.settings.BrowserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Settings › Browser as the browser screen reads them (P30): the search engine its start page
 * names and [BrowserSearch] searches with. Kept apart from [BrowserViewModel], which stays as it
 * is (Phase 13 contracts).
 */
@HiltViewModel
class BrowserSettingsViewModel @Inject constructor(
    repository: BrowserPreferencesRepository,
) : ViewModel() {
    val preferences: StateFlow<BrowserPreferences> = repository.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, BrowserPreferences())
}
