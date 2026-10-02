package com.alal.yft.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.alal.yft.core.model.settings.DownloadLocation
import com.alal.yft.core.model.settings.DownloadPreferences
import com.alal.yft.core.model.settings.QualityPreference
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

interface DownloadPreferencesRepository {
    val preferences: Flow<DownloadPreferences>
    suspend fun update(transform: (DownloadPreferences) -> DownloadPreferences)
}

/**
 * Persists download preferences next to the theme in the app's preferences store.
 *
 * Stored values are read defensively: an unknown enum name or an out-of-range number from an
 * older or newer build falls back to the default instead of failing the whole settings read.
 */
@Singleton
class DataStoreDownloadPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : DownloadPreferencesRepository {
    override val preferences: Flow<DownloadPreferences> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map(::read)
        .distinctUntilChanged()

    override suspend fun update(transform: (DownloadPreferences) -> DownloadPreferences) {
        dataStore.edit { stored ->
            val next = transform(read(stored))
            stored[QUALITY] = next.defaultQuality.name
            stored[LOCATION] = next.location.name
            stored[UNMETERED_ONLY] = next.unmeteredOnly
            stored[MAX_CONCURRENT] = next.maxConcurrentDownloads
            stored[CONFIRM_METERED] = next.confirmOnMeteredNetwork
        }
    }

    private fun read(stored: Preferences): DownloadPreferences {
        val defaults = DownloadPreferences()
        return DownloadPreferences(
            defaultQuality = stored[QUALITY]
                ?.let { name -> QualityPreference.entries.firstOrNull { it.name == name } }
                ?: defaults.defaultQuality,
            location = stored[LOCATION]
                ?.let { name -> DownloadLocation.entries.firstOrNull { it.name == name } }
                ?: defaults.location,
            unmeteredOnly = stored[UNMETERED_ONLY] ?: defaults.unmeteredOnly,
            maxConcurrentDownloads = stored[MAX_CONCURRENT]
                ?.takeIf { it in DownloadPreferences.CONCURRENT_DOWNLOAD_RANGE }
                ?: defaults.maxConcurrentDownloads,
            confirmOnMeteredNetwork = stored[CONFIRM_METERED] ?: defaults.confirmOnMeteredNetwork,
        )
    }

    private companion object {
        val QUALITY = stringPreferencesKey("download_default_quality")
        val LOCATION = stringPreferencesKey("download_location")
        val UNMETERED_ONLY = booleanPreferencesKey("download_unmetered_only")
        val MAX_CONCURRENT = intPreferencesKey("download_max_concurrent")
        val CONFIRM_METERED = booleanPreferencesKey("download_confirm_metered")
    }
}
