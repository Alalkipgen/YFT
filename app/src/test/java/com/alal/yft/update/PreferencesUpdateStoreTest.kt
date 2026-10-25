package com.alal.yft.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.update.Updates.START
import com.alal.yft.update.Updates.found
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PreferencesUpdateStoreTest {
    private val preferences = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences(PreferencesUpdateStore.FILE, Context.MODE_PRIVATE)
    private val store = PreferencesUpdateStore(preferences)

    @Test
    fun anEmptyFileReadsAsNothingKept() {
        assertEquals(UpdateRecord(), store.read())
    }

    @Test
    fun everyFieldSurvivesARoundTrip() {
        val record = UpdateRecord(
            autoCheck = false,
            lastAnswerAt = START,
            lastGoodAt = START - 1,
            found = found("v1.0.1", notes = "Line one\nLine two"),
            rateLimitUntil = START + 60_000,
            openedVersion = "1.0.1",
        )

        store.write(record)

        assertEquals(record, PreferencesUpdateStore(preferences).read())
        assertEquals(
            record.copy(found = found("v1.0.0-beta.1")),
            PreferencesUpdateStore(preferences).run {
                write(record.copy(found = found("v1.0.0-beta.1")))
                read()
            },
        )
    }

    @Test
    fun clearedFieldsAreRemoved() {
        store.write(
            UpdateRecord(autoCheck = true, found = found("v1.0.1"), openedVersion = "1.0.1"),
        )
        store.write(UpdateRecord())

        assertEquals(UpdateRecord(), store.read())
        assertEquals(emptySet<String>(), preferences.all.keys)
    }

    @Test
    fun aDamagedOrForeignFoundUpdateIsDropped() {
        store.write(UpdateRecord(found = found("v1.0.1")))
        preferences.edit()
            .putString(PreferencesUpdateStore.FOUND_APK_URL, "https://example.com/yft.apk")
            .commit()
        assertNull(store.read().found)

        store.write(UpdateRecord(found = found("v1.0.1")))
        preferences.edit().putString(PreferencesUpdateStore.FOUND_TAG, "latest").commit()
        assertNull(store.read().found)

        store.write(UpdateRecord(found = found("v1.0.1")))
        preferences.edit().putString(PreferencesUpdateStore.FOUND_APK_SIZE, "big").commit()
        assertNull(store.read().found)
    }

    @Test
    fun valuesOfAnotherTypeReadAsMissing() {
        preferences.edit()
            .putString(PreferencesUpdateStore.LAST_ANSWER_AT, "yesterday")
            .putString(PreferencesUpdateStore.AUTO_CHECK, "yes")
            .commit()

        val record = store.read()

        assertNull(record.lastAnswerAt)
        assertEquals(false, record.autoCheck)
    }
}
