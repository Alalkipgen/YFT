package com.alal.yft.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.alal.yft.core.model.settings.HomeSite
import com.alal.yft.core.model.settings.HomeSites
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The "Your sites" shortcuts on Home. */
interface HomeSitesRepository {
    val sites: Flow<List<HomeSite>>
    suspend fun update(transform: (List<HomeSite>) -> List<HomeSite>)
}

/**
 * Keeps the shortcuts in the app's preferences store. Until the user changes the list, the
 * first-run [HomeSites.DEFAULTS] are shown; after that an empty list stays empty.
 */
@Singleton
class DataStoreHomeSitesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : HomeSitesRepository {
    override val sites: Flow<List<HomeSite>> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map(::read)
        .distinctUntilChanged()

    override suspend fun update(transform: (List<HomeSite>) -> List<HomeSite>) {
        dataStore.edit { stored ->
            stored[SITES] = HomeSites.encode(transform(read(stored)))
        }
    }

    private fun read(stored: Preferences): List<HomeSite> =
        stored[SITES]?.let(HomeSites::decode) ?: HomeSites.DEFAULTS

    private companion object {
        val SITES = stringPreferencesKey("home_sites")
    }
}
