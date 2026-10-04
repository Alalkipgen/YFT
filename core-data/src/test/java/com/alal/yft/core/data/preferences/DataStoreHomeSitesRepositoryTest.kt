package com.alal.yft.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.alal.yft.core.model.settings.HomeSite
import com.alal.yft.core.model.settings.HomeSites
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreHomeSitesRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `first run shows the default sites and changes persist, including an empty list`() =
        runTest {
            val store = store(backgroundScope)
            val repository = DataStoreHomeSitesRepository(store)
            assertEquals(HomeSites.DEFAULTS, repository.sites.first())

            val added = HomeSite("Example", "https://example.com")
            repository.update { it + added }
            assertEquals(
                HomeSites.DEFAULTS + added,
                DataStoreHomeSitesRepository(store).sites.first(),
            )

            repository.update { emptyList() }
            assertEquals(emptyList<HomeSite>(), DataStoreHomeSitesRepository(store).sites.first())
        }

    @Test
    fun `a list stored with the old defaults moves to the new ones once`() = runTest {
        val store = store(backgroundScope)
        val own = HomeSite("YouTube", "https://www.youtube.com")
        store.edit { it[SITES] = HomeSites.encode(HomeSites.LEGACY_DEFAULTS + own) }

        val migrated = DataStoreHomeSitesRepository(store).sites.first()
        assertEquals(listOf(own) + HomeSites.DEFAULTS.drop(1), migrated)
        assertEquals(HomeSites.DEFAULTS_VERSION, store.data.first()[VERSION])

        // The user removes Facebook; a later start must not bring it back.
        DataStoreHomeSitesRepository(store).update { sites ->
            sites.filterNot { "facebook" in it.url }
        }
        assertEquals(
            listOf(own, HomeSites.DEFAULTS[2]),
            DataStoreHomeSitesRepository(store).sites.first(),
        )
    }

    @Test
    fun `old defaults stored after the move are the user's choice and stay`() = runTest {
        val store = store(backgroundScope)
        store.edit {
            it[SITES] = HomeSites.encode(HomeSites.LEGACY_DEFAULTS)
            it[VERSION] = HomeSites.DEFAULTS_VERSION
        }

        assertEquals(HomeSites.LEGACY_DEFAULTS, DataStoreHomeSitesRepository(store).sites.first())
    }

    @Test
    fun `an empty stored list stays empty`() = runTest {
        val store = store(backgroundScope)
        store.edit { it[SITES] = HomeSites.encode(emptyList()) }

        assertEquals(emptyList<HomeSite>(), DataStoreHomeSitesRepository(store).sites.first())
    }

    private fun store(scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(temporaryFolder.root, "settings.preferences_pb") },
        )

    private companion object {
        val SITES = stringPreferencesKey("home_sites")
        val VERSION = intPreferencesKey("home_sites_defaults_version")
    }
}
