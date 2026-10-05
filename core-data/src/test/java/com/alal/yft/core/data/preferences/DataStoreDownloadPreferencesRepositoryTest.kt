package com.alal.yft.core.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.alal.yft.core.model.settings.DownloadLocation
import com.alal.yft.core.model.settings.DownloadPreferences
import com.alal.yft.core.model.settings.QualityPreference
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreDownloadPreferencesRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `defaults are returned until something is saved, then every field persists`() = runTest {
        val store = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(temporaryFolder.root, "settings.preferences_pb") },
        )
        val repository = DataStoreDownloadPreferencesRepository(store)

        assertEquals(DownloadPreferences(), repository.preferences.first())

        repository.update {
            it.copy(
                defaultQuality = QualityPreference.UP_TO_720P,
                location = DownloadLocation.APP_STORAGE,
                unmeteredOnly = true,
                maxConcurrentDownloads = 3,
                confirmOnMeteredNetwork = false,
            )
        }

        assertEquals(
            DownloadPreferences(
                defaultQuality = QualityPreference.UP_TO_720P,
                location = DownloadLocation.APP_STORAGE,
                unmeteredOnly = true,
                maxConcurrentDownloads = 3,
                confirmOnMeteredNetwork = false,
            ),
            DataStoreDownloadPreferencesRepository(store).preferences.first(),
        )
    }

    @Test
    fun `an unset quality is 720p and every saved choice including Highest stays`() = runTest {
        val store = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(temporaryFolder.root, "settings.preferences_pb") },
        )
        val repository = DataStoreDownloadPreferencesRepository(store)
        assertEquals(QualityPreference.UP_TO_720P, repository.preferences.first().defaultQuality)
        QualityPreference.entries.forEach { quality ->
            repository.update { it.copy(defaultQuality = quality) }
            val reopened = DataStoreDownloadPreferencesRepository(store)
            assertEquals(quality, reopened.preferences.first().defaultQuality)
        }
    }

    @Test
    fun `unknown or out-of-range stored values fall back to defaults`() = runTest {
        val store = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(temporaryFolder.root, "settings.preferences_pb") },
        )
        store.edit {
            it[stringPreferencesKey("download_default_quality")] = "ULTRA_8K"
            it[stringPreferencesKey("download_location")] = "SD_CARD"
            it[intPreferencesKey("download_max_concurrent")] = 99
        }

        val preferences = DataStoreDownloadPreferencesRepository(store).preferences.first()

        assertEquals(DownloadPreferences(), preferences)
    }
}
