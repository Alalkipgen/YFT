package com.alal.yft.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.alal.yft.core.model.settings.BrowserPreferences
import com.alal.yft.core.model.settings.SearchEngine
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Settings › Browser: the search engine (P30), saving the history (P31); later pop-ups. */
interface BrowserPreferencesRepository {
    val preferences: Flow<BrowserPreferences>
    suspend fun update(transform: (BrowserPreferences) -> BrowserPreferences)
}

/**
 * Keeps the browser's settings in the app's preferences store, each under its own key. A store
 * without a key (an install from before P30) or with a value this build does not know reads the
 * default, so an older install searches Google.
 */
@Singleton
class DataStoreBrowserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : BrowserPreferencesRepository {
    override val preferences: Flow<BrowserPreferences> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map(::read)
        .distinctUntilChanged()

    override suspend fun update(transform: (BrowserPreferences) -> BrowserPreferences) {
        dataStore.edit { stored ->
            val next = transform(read(stored))
            stored[SEARCH_ENGINE] = next.searchEngine.name
            stored[SAVE_HISTORY] = next.saveHistory
        }
    }

    private fun read(stored: Preferences): BrowserPreferences {
        val defaults = BrowserPreferences()
        return BrowserPreferences(
            searchEngine = stored[SEARCH_ENGINE]
                ?.let { name -> SearchEngine.entries.firstOrNull { it.name == name } }
                ?: defaults.searchEngine,
            saveHistory = stored[SAVE_HISTORY] ?: defaults.saveHistory,
        )
    }

    private companion object {
        val SEARCH_ENGINE = stringPreferencesKey("browser_search_engine")
        val SAVE_HISTORY = booleanPreferencesKey("browser_save_history")
    }
}
