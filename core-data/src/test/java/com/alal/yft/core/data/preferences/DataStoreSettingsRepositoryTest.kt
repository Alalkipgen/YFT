package com.alal.yft.core.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.alal.yft.core.model.ThemeMode
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSettingsRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun defaultsToSystemAndPersistsThemeSelection() = runTest {
        val store = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(temporaryFolder.root, "settings.preferences_pb") },
        )
        val repository = DataStoreSettingsRepository(store)

        assertEquals(ThemeMode.SYSTEM, repository.themeMode.first())

        repository.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, repository.themeMode.first())
    }

    @Test
    fun copiedLinkCheckIsOnByDefaultAndPersistsWhenTurnedOff() = runTest {
        val store = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(temporaryFolder.root, "settings.preferences_pb") },
        )
        val repository = DataStoreSettingsRepository(store)

        assertEquals(true, repository.checkCopiedLinks.first())
        repository.setCheckCopiedLinks(false)
        assertEquals(false, repository.checkCopiedLinks.first())
        assertEquals(ThemeMode.SYSTEM, repository.themeMode.first())
    }
}
