package com.alal.yft.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.alal.yft.core.model.settings.HomeSite
import com.alal.yft.core.model.settings.HomeSites
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** The "Your sites" shortcuts on Home. */
interface HomeSitesRepository {
    val sites: Flow<List<HomeSite>>
    suspend fun update(transform: (List<HomeSite>) -> List<HomeSite>)
}

/**
 * Keeps the shortcuts in the app's preferences store. Until the user changes the list, the
 * first-run [HomeSites.DEFAULTS] are shown; after that an empty list stays empty. A list stored
 * with older defaults is moved to the current ones once ([HomeSites.migrateDefaults]); the
 * stored defaults version makes sure it never runs twice.
 */
@Singleton
class DataStoreHomeSitesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : HomeSitesRepository {
    @Volatile
    private var defaultsChecked = false

    override val sites: Flow<List<HomeSite>> = flow {
        migrateDefaultsOnce()
        emitAll(
            dataStore.data
                .catch { error ->
                    if (error is IOException) emit(emptyPreferences()) else throw error
                }
                .map(::read),
        )
    }.distinctUntilChanged()

    override suspend fun update(transform: (List<HomeSite>) -> List<HomeSite>) {
        migrateDefaultsOnce()
        dataStore.edit { stored ->
            stored[SITES] = HomeSites.encode(transform(read(stored)))
        }
    }

    private suspend fun migrateDefaultsOnce() {
        if (defaultsChecked) return
        try {
            dataStore.edit { stored ->
                if ((stored[DEFAULTS_VERSION] ?: 1) < HomeSites.DEFAULTS_VERSION) {
                    stored[SITES]?.let { encoded ->
                        val migrated = HomeSites.migrateDefaults(HomeSites.decode(encoded))
                        stored[SITES] = HomeSites.encode(migrated)
                    }
                    stored[DEFAULTS_VERSION] = HomeSites.DEFAULTS_VERSION
                }
            }
            defaultsChecked = true
        } catch (error: IOException) {
            // The list is still read; the move is tried again on the next access.
        }
    }

    private fun read(stored: Preferences): List<HomeSite> =
        stored[SITES]?.let(HomeSites::decode) ?: HomeSites.DEFAULTS

    private companion object {
        val SITES = stringPreferencesKey("home_sites")
        val DEFAULTS_VERSION = intPreferencesKey("home_sites_defaults_version")
    }
}
