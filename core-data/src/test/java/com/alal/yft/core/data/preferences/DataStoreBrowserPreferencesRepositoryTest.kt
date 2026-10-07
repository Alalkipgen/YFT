package com.alal.yft.core.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.alal.yft.core.model.settings.BrowserPreferences
import com.alal.yft.core.model.settings.SearchEngine
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreBrowserPreferencesRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `an older settings file without the engine reads Google`() = runTest {
        val store = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(temporaryFolder.root, "settings.preferences_pb") },
        )
        // What an install from before P30 wrote: other settings, no browser key.
        store.edit {
            it[stringPreferencesKey("theme_mode")] = "DARK"
            it[booleanPreferencesKey("check_copied_links")] = false
            it[stringPreferencesKey("download_default_quality")] = "UP_TO_1080P"
        }

        val read = DataStoreBrowserPreferencesRepository(store).preferences.first()

        assertEquals(BrowserPreferences(), read)
        assertEquals(SearchEngine.GOOGLE, read.searchEngine)
    }

    @Test
    fun `every engine the user picks stays, and an unknown one reads Google`() = runTest {
        val store = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(temporaryFolder.root, "settings.preferences_pb") },
        )
        val repository = DataStoreBrowserPreferencesRepository(store)
        SearchEngine.entries.reversed().forEach { engine ->
            repository.update { it.copy(searchEngine = engine) }
            val reopened = DataStoreBrowserPreferencesRepository(store)
            assertEquals(engine, reopened.preferences.first().searchEngine)
        }

        store.edit { it[stringPreferencesKey("browser_search_engine")] = "ASK_JEEVES" }

        assertEquals(SearchEngine.GOOGLE, repository.preferences.first().searchEngine)
    }

    @Test
    fun `the history switch is on for an older file and stays as the user sets it`() = runTest {
        val store = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(temporaryFolder.root, "settings.preferences_pb") },
        )
        store.edit { it[stringPreferencesKey("browser_search_engine")] = "BING" }
        val repository = DataStoreBrowserPreferencesRepository(store)
        assertEquals(true, repository.preferences.first().saveHistory)

        repository.update { it.copy(saveHistory = false) }

        val reopened = DataStoreBrowserPreferencesRepository(store).preferences.first()
        assertEquals(false, reopened.saveHistory)
        assertEquals(SearchEngine.BING, reopened.searchEngine)
    }
}
