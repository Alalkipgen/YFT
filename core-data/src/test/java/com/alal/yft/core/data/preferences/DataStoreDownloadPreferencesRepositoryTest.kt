package com.alal.yft.core.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.alal.yft.core.model.settings.DownloadLocation
import com.alal.yft.core.model.settings.DownloadPreferences
import com.alal.yft.core.model.settings.QualityPreference
import com.alal.yft.core.model.settings.SaveFolder
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    /** v1.0.2 item 28: the chosen folder and its name survive a restart of the app. */
    @Test
    fun `a chosen folder is kept across a restart`() = runTest {
        val file = File(temporaryFolder.root, "settings.preferences_pb")
        val firstRun = Job(backgroundScope.coroutineContext[Job])
        val first = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(backgroundScope.coroutineContext + firstRun),
            produceFile = { file },
        )
        DataStoreDownloadPreferencesRepository(first).update {
            it.copy(location = DownloadLocation.CHOSEN_FOLDER, folder = FOLDER)
        }
        // The first run's store goes, as the process does; the next run opens the same file.
        firstRun.cancelAndJoin()

        val second = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file },
        )
        val reopened = DataStoreDownloadPreferencesRepository(second).preferences.first()

        assertEquals(DownloadLocation.CHOSEN_FOLDER, reopened.location)
        assertEquals(FOLDER, reopened.folder)
    }

    /** Item 28: choosing Download/YFT again keeps the folder for the Library; null forgets it. */
    @Test
    fun `another location keeps the folder and a null folder forgets it`() = runTest {
        val store = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(temporaryFolder.root, "settings.preferences_pb") },
        )
        val repository = DataStoreDownloadPreferencesRepository(store)
        repository.update { it.copy(location = DownloadLocation.CHOSEN_FOLDER, folder = FOLDER) }

        repository.update { it.copy(location = DownloadLocation.SHARED_DOWNLOADS) }
        val shared = DataStoreDownloadPreferencesRepository(store).preferences.first()
        assertEquals(DownloadLocation.SHARED_DOWNLOADS, shared.location)
        assertEquals(FOLDER, shared.folder)

        repository.update { it.copy(folder = null) }
        assertNull(DataStoreDownloadPreferencesRepository(store).preferences.first().folder)
    }

    /** Item 28: a chosen folder whose address is missing cannot be used, so the default reads. */
    @Test
    fun `a chosen folder without its address reads as the default location`() = runTest {
        val store = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(temporaryFolder.root, "settings.preferences_pb") },
        )
        store.edit {
            it[stringPreferencesKey("download_location")] = "CHOSEN_FOLDER"
            it[stringPreferencesKey("download_folder_name")] = "YFT test"
        }

        val preferences = DataStoreDownloadPreferencesRepository(store).preferences.first()

        assertEquals(DownloadLocation.SHARED_DOWNLOADS, preferences.location)
        assertNull(preferences.folder)
    }

    private companion object {
        val FOLDER = SaveFolder(
            treeUri = "content://com.android.externalstorage.documents/tree/" +
                "primary%3AMovies%2FYFT%20test",
            name = "YFT test",
        )
    }
}
