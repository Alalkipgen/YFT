package com.alal.yft.core.data.preferences

import com.alal.yft.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(themeMode: ThemeMode)

    /** "Check copied links when YFT opens" (decision D1): on unless the user turns it off. */
    val checkCopiedLinks: Flow<Boolean>
    suspend fun setCheckCopiedLinks(enabled: Boolean)
}
