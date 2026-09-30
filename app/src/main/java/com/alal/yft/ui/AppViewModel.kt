package com.alal.yft.ui

import androidx.lifecycle.ViewModel
import com.alal.yft.core.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class AppViewModel @Inject constructor() : ViewModel() {
    private val mutableUiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = mutableUiState.asStateFlow()

    fun onAction(action: AppAction) {
        mutableUiState.value = mutableUiState.value.reduce(action)
    }

    fun setThemeMode(themeMode: ThemeMode) {
        onAction(AppAction.ThemeModeChanged(themeMode))
    }
}
